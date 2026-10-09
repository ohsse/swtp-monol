package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityOperatingStatusDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운전현황분석 4번 섹션 — 시설 단위 운영 현황 조회 서비스.
 *
 * <p>활성 시설({@code facilityId}, {@code use_yn = Y}) 의 PUMP 자식 인스트루먼트 중 OPS=On(GOOD+1.0) 펌프
 * 이름, 해당 펌프들의 PWI 합산, 시설 직속 FLWMTR 의 FRI 와의 전력원단위(kWh/m³), 사용된 모든 태그의
 * max(acq_dtm) 을 단일 응답으로 구성한다 (운전현황분석-4번섹션 PLAN1 §구현 방향 §Service 책임 분담).</p>
 *
 * <p>4-SELECT 패턴 — Facility 1 + Instrument 1 + Tag 1 + RawData 1 (FacilityStateService 동형, 인용 근거).
 * 추상화 깊이는 호출 스택 3단 (Controller → Service → private 헬퍼) 이하 유지 + 메서드 본문 50줄 이내
 * (coding-discipline.md §2.1 정량 기준).</p>
 *
 * <p>도메인 룰 인용:
 * {@code ot-integration.md §3} (OPS BAD 즉시 격상 — UNCERTAIN OPS 보수적 제외) ·
 * {@code entity-patterns.md §JPA JOINED 다형성 §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityOperatingStatusService {

    /** 본 카드 지원 시설 종류 — RSV(저수조 펌프 미보유) · POINT(관로 분기점) 거부 (PLAN1 가정 4 결정). */
    private static final Set<FacilityType> SUPPORTED_TYPES = EnumSet.of(
            FacilityType.PWTF, FacilityType.DWT, FacilityType.PRSF);

    /** 측정 대상 계측기 종류 — 펌프(OPS·PWI) + 유량계(FRI). */
    private static final List<EquipType> TARGET_EQUIP_TYPES = List.of(EquipType.PUMP, EquipType.FLWMTR);

    /** 측정 대상 태그 유형 — OPS 가동상태 / PWI 순시전력 / FRI 유출유량. */
    private static final Set<TagMeasurementType> TARGET_TAG_TYPES = EnumSet.of(
            TagMeasurementType.OPS, TagMeasurementType.PWI, TagMeasurementType.FRI);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 활성 시설의 운영 현황을 통합 응답한다.
     *
     * @param facilityId 활성 시설 ID
     * @return 시설 단위 운영 현황 (On 펌프명·PWI 합산·전력원단위·측정시간)
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재 또는 비활성 시설
     * @throws RestApiException UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류
     */
    public FacilityOperatingStatusDto findFacilityOperatingStatus(String facilityId) {
        Facility facility = findActiveFacilityOrThrow(facilityId);

        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdAndEquipType(facilityId, TARGET_EQUIP_TYPES);
        if (instruments.isEmpty()) {
            return FacilityOperatingStatusDto.of(
                    facility.getFacilityId(), facility.getFacilityNm(),
                    null, List.of(), BigDecimal.ZERO, null);
        }

        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(instruments);
        Map<String, RawDataLatestDto> latestByTag = loadLatestByTag(tagsByInstrument);

        List<Pump> pumps = filterPumps(instruments);
        List<Pump> onPumps = pumps.stream()
                .filter(p -> isPumpRunning(
                        pickLatest(tagsByInstrument, p.getInstrumentId(), TagMeasurementType.OPS, latestByTag)))
                .toList();

        BigDecimal totalElpwrAmt = sumOnPumpPwr(onPumps, tagsByInstrument, latestByTag);
        RawDataLatestDto fri = selectFacilityFri(instruments, tagsByInstrument, latestByTag);
        BigDecimal elpwrUnitQty = computeUnitConsumption(totalElpwrAmt, fri);
        LocalDateTime measurementDtm = latestMeasurementDtm(
                collectUsedTags(pumps, tagsByInstrument, latestByTag, fri));

        List<String> onPumpNms = onPumps.stream().map(Pump::getInstrumentNm).toList();
        return FacilityOperatingStatusDto.of(
                facility.getFacilityId(), facility.getFacilityNm(),
                measurementDtm, onPumpNms, totalElpwrAmt, elpwrUnitQty);
    }

    /**
     * 활성 시설 검증 + 본 카드 지원 시설 종류(PWTF·DWT·PRSF) 필터.
     *
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재·비활성 시설
     * @throws RestApiException UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT
     */
    private Facility findActiveFacilityOrThrow(String facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
        if (facility.getUseYn() != YnType.Y) {
            throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
        }
        if (!SUPPORTED_TYPES.contains(facility.getFacilityType())) {
            throw new RestApiException(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
        }
        return facility;
    }

    /** 시설 직속 계측기들의 활성 태그를 OPS/PWI/FRI 필터 후 인스트루먼트별로 그룹화. */
    private Map<String, List<Tag>> loadTagsByInstrument(List<Instrument> instruments) {
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> TARGET_TAG_TYPES.contains(t.getTagSeCd()))
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));
    }

    /** 태그별 최신 측정값 일괄 조회 — IN 절 단일 쿼리. */
    private Map<String, RawDataLatestDto> loadLatestByTag(Map<String, List<Tag>> tagsByInstrument) {
        List<String> tagSrlNos = tagsByInstrument.values().stream()
                .flatMap(List::stream)
                .map(Tag::getTagSrlNo)
                .toList();
        return rawDataRepository.findLatestByTagSrlNos(tagSrlNos).stream()
                .collect(Collectors.toMap(RawDataLatestDto::tagSrlNo, r -> r));
    }

    /** equip_type_cd = 'PUMP' 필터 강제 — 다형성 부모 List<Instrument> 에서 Pump 자식만 추출. */
    private List<Pump> filterPumps(List<Instrument> instruments) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.PUMP)
                .map(i -> (Pump) i)
                .toList();
    }

    /** 인스트루먼트의 지정 측정 유형 최신값 1건 추출 (없으면 null). */
    private RawDataLatestDto pickLatest(
            Map<String, List<Tag>> tagsByInstrument,
            String instrumentId,
            TagMeasurementType targetType,
            Map<String, RawDataLatestDto> latestByTag) {
        return tagsByInstrument.getOrDefault(instrumentId, List.of()).stream()
                .filter(t -> t.getTagSeCd() == targetType)
                .map(t -> latestByTag.get(t.getTagSrlNo()))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * SCADA 값 선택 정책 — corrVal 우선, NULL 시 rawVal (PLAN1 §값 선택 정책).
     */
    private BigDecimal effectiveVal(RawDataLatestDto r) {
        if (r == null) {
            return null;
        }
        return r.corrVal() != null ? r.corrVal() : r.rawVal();
    }

    /**
     * 펌프 On 판정 — null OR qualityCd != GOOD OR effectiveVal != 1.0 → false.
     *
     * <p>UNCERTAIN OPS 도 On 제외 — {@code ot-integration.md §3} OPS BAD 즉시 격상 정책의
     * 보수적 확장 (PLAN1 가정 1 결정).</p>
     */
    private boolean isPumpRunning(RawDataLatestDto ops) {
        if (ops == null || ops.qualityCd() != QualityCode.GOOD) {
            return false;
        }
        BigDecimal val = effectiveVal(ops);
        return val != null && val.compareTo(BigDecimal.ONE) == 0;
    }

    /**
     * On 펌프들의 PWI 합산 — GOOD 만 합산, null/BAD/UNCERTAIN 전액 제외 (PLAN1 가정 2 결정).
     * On 펌프 0대 또는 모든 PWI 부재 시 BigDecimal.ZERO 반환 (TASK 단계 결정).
     */
    private BigDecimal sumOnPumpPwr(
            List<Pump> onPumps,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        return onPumps.stream()
                .map(p -> pickLatest(tagsByInstrument, p.getInstrumentId(),
                        TagMeasurementType.PWI, latestByTag))
                .filter(pwi -> pwi != null && pwi.qualityCd() == QualityCode.GOOD)
                .map(this::effectiveVal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 시설 직속 FLWMTR 의 첫 GOOD FRI 태그 측정값 — PLAN1 가정 3 결정 (parent_facility_id 재귀 미적용).
     */
    private RawDataLatestDto selectFacilityFri(
            List<Instrument> instruments,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .map(i -> pickLatest(tagsByInstrument, i.getInstrumentId(),
                        TagMeasurementType.FRI, latestByTag))
                .filter(r -> r != null && r.qualityCd() == QualityCode.GOOD)
                .findFirst()
                .orElse(null);
    }

    /**
     * 전력원단위 (kWh/m³) — 분자/분모 0/NULL/BAD/부재 4 케이스 모두 null (PLAN1 가정 3 + 분모 무효 정책).
     * 정상 시 totalElpwrAmt / friVal (scale=4, RoundingMode.HALF_UP).
     */
    private BigDecimal computeUnitConsumption(BigDecimal totalElpwrAmt, RawDataLatestDto fri) {
        if (totalElpwrAmt == null || totalElpwrAmt.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (fri == null || fri.qualityCd() != QualityCode.GOOD) {
            return null;
        }
        BigDecimal friVal = effectiveVal(fri);
        if (friVal == null || friVal.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP);
    }

    /** 사용된 모든 태그(OPS+PWI+FRI) 의 RawDataLatestDto 수집 — measurementDtm 산출용. */
    private Collection<RawDataLatestDto> collectUsedTags(
            List<Pump> pumps,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag,
            RawDataLatestDto fri) {
        Collection<RawDataLatestDto> used = new ArrayList<>();
        for (Pump pump : pumps) {
            used.add(pickLatest(tagsByInstrument, pump.getInstrumentId(),
                    TagMeasurementType.OPS, latestByTag));
            used.add(pickLatest(tagsByInstrument, pump.getInstrumentId(),
                    TagMeasurementType.PWI, latestByTag));
        }
        used.add(fri);
        return used;
    }

    /** 사용된 태그들의 max(acq_dtm) — 빈 데이터 시 null (PLAN1 §측정시간 산출). */
    private LocalDateTime latestMeasurementDtm(Collection<RawDataLatestDto> usedTags) {
        return usedTags.stream()
                .filter(r -> r != null)
                .map(RawDataLatestDto::acqDtm)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
