package com.mo.swtp.opt.service;

import com.mo.swtp.common.enumtype.InqUnit;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.opt.dto.PeakEnergyTrendDto;
import com.mo.swtp.opt.dto.PeakEnergyTrendDto.PeakEnergyTrendPoint;
import com.mo.swtp.opt.dto.PredcEnergyBucketDto;
import com.mo.swtp.opt.repository.PumpEnergyPredcRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전력피크분석 5번섹션 — 전력량 추이(발생·예측) 조회 서비스 (읽기 전용).
 *
 * <p>현재 시각 기준 ±12시간(총 24시간) 윈도우의 시스템 전역 적산전력량(PWQ) 추세를 단일 응답으로 구성한다
 * (전력피크분석-5번섹션 PLAN1). 기준 시각 {@code base = date_trunc('hour', now)} 를 분기로:</p>
 * <ul>
 *   <li><b>발생</b> {@code [base-12h, base)} — 실측 {@code rawdata_1m_h} PWQ 1시간 버킷 차분 후 전역 합산</li>
 *   <li><b>예측</b> {@code [base, base+12h)} — 예측 {@code predc_1m_h} PWQ 1시간 버킷 차분 후 전역 합산
 *       (현재 시 버킷 부분 집계)</li>
 * </ul>
 *
 * <p>전역 합산 = 전체 활성 PWQ 태그({@code tag_se_cd='PWQ' AND use_yn='Y'}) 차분의 버킷별 합산. 현재 펌프
 * 서브미터만 존재하고 메인 적산미터(ELCMTR) 부재라 이중계상 없음 — 향후 메인 적산미터 추가 시 이중계상 위험은
 * 별도 사이클 재검토 (전력피크분석-5번섹션 ANALYZE1 안건 4).</p>
 *
 * <p>각 버킷 전력량은 태그별 적산값 차분({@code MAX-MIN}) 후 Service 가 버킷별 합산한다
 * ({@code SUM(MAX-MIN) != MAX(SUM)-MIN(SUM)} — 태그별 선차분 후 합산). 음수 차분(적산 카운터 리셋·롤오버)은
 * 단조증가 가정 위반으로 합산에서 제외한다 ({@code .claude/rules/ot-integration.md §3} PWQ 적산값 차분 정책).
 * 4번섹션 {@link PumpEnergyPredictionService} 와 구조 동형이나 시설 단위가 아닌 전역 + 발생 시계열 동봉 +
 * 요금/목표 스칼라 동봉 — 동형 패턴만 미러링한 섹션5 전용 신규 자산 (사이클 간 자산 자동 원용 금지 정합).</p>
 *
 * <p>요금/목표 스칼라는 시계열(kWh)과 단위가 다른 순시전력(kW) 기준선 — 2번섹션 산정식 동형 독립 재계산
 * ({@link #billingPeak} = 최근 12개월 분단위 PWI 합산의 MAX, 목표값은 {@link PeakTargetService#getPeakTarget()}).
 * 목표값 시드 부재 시 {@link com.mo.swtp.opt.exception.OptErrorCode#PEAK_TARGET_NOT_INITIALIZED}(500) 를
 * 1번섹션과 동일하게 전파한다 (silent self-heal 미적용).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PeakEnergyTrendService {

    /** 시계열 전력량 응답 단위. */
    private static final String UNIT_KWH = "kWh";

    /** 윈도우 반경 — 발생(과거)·예측(미래) 각 12시간. */
    private static final int WINDOW_HOURS = 12;

    /** 요금적용전력피크 산정 구간 — 최근 12개월 (1년). */
    private static final int BILLING_LOOKBACK_MONTHS = 12;

    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;
    private final PumpEnergyPredcRepository pumpEnergyPredcRepository;
    private final PeakTargetService peakTargetService;

    /**
     * 현재 시각 기준 ±12시간 전력량 추이(발생·예측)와 요금/목표 피크 스칼라를 통합 조회한다.
     *
     * @return 전력량 추이 응답 (발생·예측 시계열 + 요금적용전력피크 + 목표피크). 태그·데이터 부재 시 빈 시계열·ZERO
     * @throws com.mo.swtp.common.exception.RestApiException PEAK_TARGET_NOT_INITIALIZED — 목표값 시드 부재
     */
    public PeakEnergyTrendDto getEnergyTrend() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = now.truncatedTo(ChronoUnit.HOURS);
        BigDecimal targetPeakElpwr = peakTargetService.getPeakTarget().getTargetPeakElpwr();

        List<String> pwqSrlNos = pwqTagSrlNos();
        List<String> pwiSrlNos = pwiTagSrlNos();

        List<PeakEnergyTrendPoint> measuredPoints = aggregateMeasured(
                rawDataRepository.findEnergyDeltaBuckets(
                        pwqSrlNos, base.minusHours(WINDOW_HOURS), base, InqUnit.HOUR.getDateTruncUnit()));
        List<PeakEnergyTrendPoint> predictedPoints = aggregatePredicted(
                pumpEnergyPredcRepository.findEnergyDeltaBuckets(
                        pwqSrlNos, base, base.plusHours(WINDOW_HOURS)));
        BigDecimal billingPeakElpwr = billingPeak(pwiSrlNos, now);

        return PeakEnergyTrendDto.of(
                UNIT_KWH, targetPeakElpwr, billingPeakElpwr, measuredPoints, predictedPoints);
    }

    /** 시스템 전역 활성 PWQ(적산전력량) 태그 시리얼번호 수집. */
    private List<String> pwqTagSrlNos() {
        return tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y).stream()
                .map(Tag::getTagSrlNo)
                .toList();
    }

    /** 시스템 전역 활성 PWI(순시전력) 태그 시리얼번호 수집 — 요금적용전력피크 산정 입력. */
    private List<String> pwiTagSrlNos() {
        return tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y).stream()
                .map(Tag::getTagSrlNo)
                .toList();
    }

    /**
     * 발생 실측 버킷 차분을 버킷(시) 단위로 전역 합산한다 — 음수 차분 버킷은 제외.
     * {@link TreeMap} 으로 버킷 시작 일시 오름차순 정렬을 보장한다.
     */
    private List<PeakEnergyTrendPoint> aggregateMeasured(List<RawDataBucketDto> buckets) {
        Map<LocalDateTime, BigDecimal> sumByBucket = new TreeMap<>();
        for (RawDataBucketDto bucket : buckets) {
            BigDecimal delta = validDeltaOrNull(bucket.aggrVal(), bucket.tagSrlNo(), bucket.baseDtm());
            if (delta != null) {
                sumByBucket.merge(bucket.baseDtm(), delta, BigDecimal::add);
            }
        }
        return toPoints(sumByBucket);
    }

    /**
     * 예측 버킷 차분을 버킷(시) 단위로 전역 합산한다 — 음수 차분 버킷은 제외.
     * {@link TreeMap} 으로 버킷 시작 일시 오름차순 정렬을 보장한다.
     */
    private List<PeakEnergyTrendPoint> aggregatePredicted(List<PredcEnergyBucketDto> buckets) {
        Map<LocalDateTime, BigDecimal> sumByBucket = new TreeMap<>();
        for (PredcEnergyBucketDto bucket : buckets) {
            BigDecimal delta = validDeltaOrNull(bucket.aggrVal(), bucket.tagSrlNo(), bucket.baseDtm());
            if (delta != null) {
                sumByBucket.merge(bucket.baseDtm(), delta, BigDecimal::add);
            }
        }
        return toPoints(sumByBucket);
    }

    /** 버킷별 합산 맵을 시작 일시 오름차순 시계열 포인트 목록으로 변환한다 (발생·예측 공용). */
    private List<PeakEnergyTrendPoint> toPoints(Map<LocalDateTime, BigDecimal> sumByBucket) {
        return sumByBucket.entrySet().stream()
                .map(e -> PeakEnergyTrendPoint.of(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * 버킷 차분값 유효성 — null 또는 음수(적산 리셋·롤오버)면 null 반환 + 음수는 WARN 로그.
     * 발생({@link RawDataBucketDto})·예측({@link PredcEnergyBucketDto}) 버킷 공유 단일 소스.
     */
    private BigDecimal validDeltaOrNull(BigDecimal aggrVal, String tagSrlNo, LocalDateTime baseDtm) {
        if (aggrVal == null) {
            return null;
        }
        if (aggrVal.signum() < 0) {
            log.warn("음수 전력량 차분 버킷 제외 — tagSrlNo={}, baseDtm={}, aggrVal={}", tagSrlNo, baseDtm, aggrVal);
            return null;
        }
        return aggrVal;
    }

    /**
     * 요금적용전력피크 — 최근 12개월 분단위 PWI 합산값의 MAX (kW).
     * 데이터 부재(MAX NULL) 또는 PWI 태그 0개 시 ZERO (2번섹션 {@code billingPeak} 동형).
     */
    private BigDecimal billingPeak(List<String> pwiSrlNos, LocalDateTime now) {
        BigDecimal max = rawDataRepository.findMaxMinuteSumElpwr(
                pwiSrlNos, now.minusMonths(BILLING_LOOKBACK_MONTHS), now);
        return max != null ? max : BigDecimal.ZERO;
    }
}
