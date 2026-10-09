package com.mo.swtp.opt.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.PredcEnergyBucketDto;
import com.mo.swtp.opt.dto.PumpEnergyPredictionDto;
import com.mo.swtp.opt.dto.PumpEnergyPredictionDto.PumpEnergyPredictionPoint;
import com.mo.swtp.opt.repository.PumpEnergyPredcRepository;
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
 * 전력피크분석 4번섹션 — 시설 펌프 전력량 예측 시계열 조회 서비스 (기능2).
 *
 * <p>선택 시설({@code facilityId}, {@code use_yn = Y}) 이 보유한 활성 송수펌프({@code equip_type_cd = 'PUMP'})
 * 들의 적산전력량(PWQ) 예측값을 현재 시(時)부터 24시간까지 1시간 버킷 단위로 합산한 단일 시계열로 구성한다.
 * 각 버킷 전력량은 펌프별 예측 적산값의 버킷별 차분({@code MAX(predc_val)-MIN(predc_val)}) 후 시설 합산이다
 * (전력피크분석-4번섹션 PLAN1 §기능2).</p>
 *
 * <p>4-SELECT 패턴 — Facility 1 + Instrument 1 + Tag 1 + Prediction 1
 * ({@link com.mo.swtp.facility.service.FacilityOperatingStatusService} 동형, 인용 근거). 추상화 깊이는 호출
 * 스택 3단 (Controller → Service → private 헬퍼) 이하 유지 + 메서드 본문 50줄 이내
 * ({@code .claude/rules/coding-discipline.md §2.1} 정량 기준).</p>
 *
 * <p>적산값 차분 정합성: {@code SUM(MAX-MIN) != MAX(SUM)-MIN(SUM)} 이므로 펌프별로 먼저 버킷 차분한 뒤
 * (Repository SQL) Service 가 버킷별 시설 합산을 수행한다 (전력피크분석-4번섹션 ANALYZE1 안건 3 DBA 확인).
 * 음수 차분(적산 카운터 리셋·롤오버) 버킷은 단조증가 가정 위반으로 합산에서 제외한다
 * ({@code .claude/rules/ot-integration.md §3} PWQ 적산값 차분 정책 정합).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PumpEnergyPredictionService {

    /** 전력량 응답 단위. */
    private static final String UNIT_KWH = "kWh";

    /** 예측 지평 — 현재 시부터 24시간. */
    private static final int HORIZON_HOURS = 24;

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final PumpEnergyPredcRepository pumpEnergyPredcRepository;

    /**
     * 선택 시설의 펌프 전력량 예측 시계열을 조회한다.
     *
     * @param facilityId 활성 시설 ID
     * @return 시설 합산 전력량 예측 시계열 (현재 시 ~ +24h, 1시간 버킷). 펌프·PWQ 태그·예측 부재 시 빈 시계열
     * @throws RestApiException {@link FacilityErrorCode#FACILITY_NOT_FOUND} — 미존재 또는 비활성 시설
     */
    public PumpEnergyPredictionDto getPumpEnergyPrediction(String facilityId) {
        Facility facility = findActiveFacilityOrThrow(facilityId);

        List<String> pumpIds = loadActivePumpIds(facilityId);
        if (pumpIds.isEmpty()) {
            return emptyResponse(facility);
        }

        List<String> tagSrlNos = loadPwqTagSrlNos(pumpIds);
        if (tagSrlNos.isEmpty()) {
            return emptyResponse(facility);
        }

        LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime end = start.plusHours(HORIZON_HOURS);
        List<PredcEnergyBucketDto> buckets =
                pumpEnergyPredcRepository.findEnergyDeltaBuckets(tagSrlNos, start, end);

        return PumpEnergyPredictionDto.of(
                facility.getFacilityId(), facility.getFacilityNm(), UNIT_KWH, aggregateByBucket(buckets));
    }

    /**
     * 활성 시설 검증.
     *
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재·비활성 시설
     */
    private Facility findActiveFacilityOrThrow(String facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
        if (facility.getUseYn() != YnType.Y) {
            throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
        }
        return facility;
    }

    /** 시설 직속 계측기 중 활성 PUMP 의 ID 목록 — equip_type_cd='PUMP' + use_yn='Y' 필터 강제. */
    private List<String> loadActivePumpIds(String facilityId) {
        return instrumentRepository.findByFacilityFacilityId(facilityId).stream()
                .filter(i -> i.getEquipType() == EquipType.PUMP)
                .filter(i -> i.getUseYn() == YnType.Y)
                .map(Instrument::getInstrumentId)
                .toList();
    }

    /** 펌프들의 PWQ 활성 태그 시리얼번호 목록 — IN 절 단일 조회 후 PWQ 필터. */
    private List<String> loadPwqTagSrlNos(List<String> pumpIds) {
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(pumpIds, YnType.Y).stream()
                .filter(t -> t.getTagSeCd() == TagMeasurementType.PWQ)
                .map(Tag::getTagSrlNo)
                .toList();
    }

    /**
     * 펌프별 버킷 차분을 버킷(시) 단위로 시설 합산한다 — 음수 차분 버킷은 제외.
     * {@link TreeMap} 으로 버킷 시작 일시 오름차순 정렬을 보장한다.
     */
    private List<PumpEnergyPredictionPoint> aggregateByBucket(List<PredcEnergyBucketDto> buckets) {
        Map<LocalDateTime, BigDecimal> sumByBucket = new TreeMap<>();
        for (PredcEnergyBucketDto bucket : buckets) {
            BigDecimal delta = validDeltaOrNull(bucket);
            if (delta != null) {
                sumByBucket.merge(bucket.baseDtm(), delta, BigDecimal::add);
            }
        }
        return sumByBucket.entrySet().stream()
                .map(e -> PumpEnergyPredictionPoint.of(e.getKey(), e.getValue()))
                .toList();
    }

    /** 버킷 차분값 유효성 — null 또는 음수(적산 리셋·롤오버)면 null 반환 + 음수는 WARN 로그. */
    private BigDecimal validDeltaOrNull(PredcEnergyBucketDto bucket) {
        BigDecimal aggrVal = bucket.aggrVal();
        if (aggrVal == null) {
            return null;
        }
        if (aggrVal.signum() < 0) {
            log.warn("음수 예측 전력량 차분 버킷 제외 — tagSrlNo={}, baseDtm={}, aggrVal={}",
                    bucket.tagSrlNo(), bucket.baseDtm(), aggrVal);
            return null;
        }
        return aggrVal;
    }

    /** 펌프·PWQ 태그 부재 시 빈 시계열 응답. */
    private PumpEnergyPredictionDto emptyResponse(Facility facility) {
        return PumpEnergyPredictionDto.of(
                facility.getFacilityId(), facility.getFacilityNm(), UNIT_KWH, List.of());
    }
}
