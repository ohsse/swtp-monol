package com.mo.swtp.opt.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.opt.dto.PeakPowerAnalysisDto;
import com.mo.swtp.opt.repository.PeakPredcRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전력피크분석 2·3번섹션 — 공용 5지표 집계 조회 서비스 (읽기 전용).
 *
 * <p>화면 좌측 2번 섹션 4지표 + 3번섹션 주요내역 신규 1지표(송수펌프 순시전력)를 단일 응답으로 구성한다
 * (전력피크분석-2번섹션 PLAN1, 2026-06-05 / 전력피크분석-3번섹션 PLAN1, 2026-06-05):</p>
 * <ol>
 *   <li><b>총순시전력</b> — 전체 활성 PWI 태그 최신값 GOOD 합산 (kW)</li>
 *   <li><b>송수펌프 순시전력</b> — 펌프({@code equip_type_cd='PUMP'}) 매핑 PWI 태그 최신값 GOOD 합산
 *       (kW, On/Off 무관). 총순시전력의 부분집합 — 동일 {@code latest} fetch 결과를 멤버십 필터로 분할</li>
 *   <li><b>목표피크전력</b> — 1번섹션 {@link PeakTargetService#getPeakTarget()} 재사용 (kW, 0 = 미설정)</li>
 *   <li><b>요금적용전력피크</b> — 최근 12개월 분단위 PWI 합산값의 MAX (kW)</li>
 *   <li><b>전력피크예상시간</b> — 예측 PWI 합이 목표 초과하는 최근접 미래 시각 (null = 없음)</li>
 * </ol>
 *
 * <p>결측 합산 정책 — 그 시각에 존재하는 GOOD 값만 합산(부분합 허용, 사용자 결정 1). 값 선택은
 * corrVal 우선·NULL 시 rawVal ({@link #effectiveVal}, {@code FacilityOperatingStatusService} 동형 복제 —
 * 사이클 간 자산 자동 원용 금지 정합). 헬퍼 위임으로 공개 메서드 본문 50줄 이내
 * ({@code coding-discipline.md §2.1}). {@code now} 는 1회 호출로 지표 일관 기준 시각 보장.
 * {@link RawDataRepository#findLatestByTagSrlNos} 도 1회만 호출하여 그 결과를 총순시전력·송수펌프
 * 순시전력이 공유한다 — 2회 fetch 시 발생하는 전체↔펌프 합산 시점 불일치를 방지한다.</p>
 *
 * <p>지표 ③ 은 시드 부재 시 {@link com.mo.swtp.opt.exception.OptErrorCode#PEAK_TARGET_NOT_INITIALIZED}
 * (500) 를 1번섹션과 동일하게 전파한다 (silent self-heal 미적용).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeakPowerAnalysisService {

    /** 요금적용전력피크 산정 구간 — 최근 12개월 (1년). */
    private static final int BILLING_LOOKBACK_MONTHS = 12;

    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;
    private final PeakPredcRepository peakPredcRepository;
    private final PeakTargetService peakTargetService;

    /** 예측 지평 상한 (시간) — {@code predc_1m_h} 월 RANGE 파티션 스캔을 1~2개로 제한 (DBA 블로커 해소). */
    @Value("${opt.peak.predc-horizon-hours:48}")
    private long predcHorizonHours;

    /**
     * 전력피크분석 2·3번섹션 5지표를 통합 조회한다.
     *
     * @return 5지표 응답 (총순시전력·송수펌프 순시전력·목표피크전력·요금적용전력피크·전력피크예상시간)
     * @throws com.mo.swtp.common.exception.RestApiException PEAK_TARGET_NOT_INITIALIZED — 목표값 시드 부재
     */
    public PeakPowerAnalysisDto getPeakPowerAnalysis() {
        LocalDateTime now = LocalDateTime.now();
        List<String> pwiTagSrlNos = pwiTagSrlNos();
        Set<String> pumpPwiSrlNos = pumpPwiTagSrlNos();
        BigDecimal targetPeakElpwr = peakTargetService.getPeakTarget().getTargetPeakElpwr();

        List<RawDataLatestDto> latest = rawDataRepository.findLatestByTagSrlNos(pwiTagSrlNos);
        BigDecimal totalElpwr = sumGoodPwi(latest);
        BigDecimal pumpElpwr = sumGoodPwi(pumpLatest(latest, pumpPwiSrlNos));

        BigDecimal billingPeakElpwr = billingPeak(pwiTagSrlNos, now);
        LocalDateTime predcPeakDtm = expectedPeakDtm(pwiTagSrlNos, now, targetPeakElpwr);

        return PeakPowerAnalysisDto.of(
                totalElpwr, pumpElpwr, targetPeakElpwr, billingPeakElpwr, predcPeakDtm);
    }

    /** 시스템 전역 활성 PWI(순시전력) 태그 시리얼번호 수집. */
    private List<String> pwiTagSrlNos() {
        return tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y).stream()
                .map(Tag::getTagSrlNo)
                .toList();
    }

    /**
     * 펌프({@code equip_type_cd='PUMP'}) 매핑 활성 PWI 태그 시리얼번호 집합.
     * 전체 PWI fetch 결과({@code latest})에서 송수펌프 순시전력 부분합을 멤버십 필터로 분할하기 위한 식별 집합.
     */
    private Set<String> pumpPwiTagSrlNos() {
        return tagRepository
                .findByTagSeCdAndUseYnAndInstrument_EquipType(
                        TagMeasurementType.PWI, YnType.Y, EquipType.PUMP)
                .stream()
                .map(Tag::getTagSrlNo)
                .collect(Collectors.toSet());
    }

    /**
     * 펌프 시리얼번호 집합 멤버십으로 최신값 리스트의 펌프 부분집합을 추출한다.
     * 동일 {@code latest} 를 재사용하므로 추가 DB 조회 0회 (총순시전력과 시점 일관).
     */
    private List<RawDataLatestDto> pumpLatest(
            List<RawDataLatestDto> latest, Set<String> pumpPwiSrlNos) {
        return latest.stream()
                .filter(r -> pumpPwiSrlNos.contains(r.tagSrlNo()))
                .toList();
    }

    /**
     * PWI 최신값 GOOD 합산 (kW) — 총순시전력·송수펌프 순시전력 공용 헬퍼.
     * GOOD 만 합산, null/BAD/UNCERTAIN 전액 제외 (부분합 허용). 빈 입력 시 ZERO.
     */
    private BigDecimal sumGoodPwi(List<RawDataLatestDto> values) {
        return values.stream()
                .filter(r -> r.qualityCd() == QualityCode.GOOD)
                .map(this::effectiveVal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 요금적용전력피크 — 최근 12개월 분단위 PWI 합산값의 MAX (kW). 발생 시각 미포함(사용자 결정 3).
     * 데이터 부재(MAX NULL) 또는 PWI 태그 0개 시 ZERO.
     */
    private BigDecimal billingPeak(List<String> pwiTagSrlNos, LocalDateTime now) {
        BigDecimal max = rawDataRepository.findMaxMinuteSumElpwr(
                pwiTagSrlNos, now.minusMonths(BILLING_LOOKBACK_MONTHS), now);
        return max != null ? max : BigDecimal.ZERO;
    }

    /**
     * 전력피크예상시간 — 예측 PWI 합이 목표 초과하는 최근접 미래 시각 (null = 없음).
     * 목표 미설정(null·0 이하) 또는 PWI 태그 0개 시 쿼리 생략하고 null (무의미 매칭 방지).
     */
    private LocalDateTime expectedPeakDtm(
            List<String> pwiTagSrlNos, LocalDateTime now, BigDecimal targetPeakElpwr) {
        if (pwiTagSrlNos.isEmpty()
                || targetPeakElpwr == null
                || targetPeakElpwr.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return peakPredcRepository.findEarliestPredcDtmOverTarget(
                pwiTagSrlNos, now, now.plusHours(predcHorizonHours), targetPeakElpwr);
    }

    /** SCADA 값 선택 정책 — corrVal 우선, NULL 시 rawVal (FacilityOperatingStatusService 동형 복제). */
    private BigDecimal effectiveVal(RawDataLatestDto r) {
        if (r == null) {
            return null;
        }
        return r.corrVal() != null ? r.corrVal() : r.rawVal();
    }
}
