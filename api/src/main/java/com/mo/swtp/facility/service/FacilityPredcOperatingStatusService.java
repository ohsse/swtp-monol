package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityPredcOperatingStatusDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcLatestDto;
import com.mo.swtp.opt.repository.TagPredcLatestRepository;
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
 * 운전현황분석 9번 섹션 — 시설 단위 예측 운영 현황 조회 서비스.
 *
 * <p>활성 시설({@code facilityId}, {@code use_yn = Y}) 의 PUMP 자식 인스트루먼트 중 OPS 예측이 On
 * ({@code predc_val = 1.0}) 인 펌프 이름, 해당 펌프들의 PWI 예측 합산, 시설 직속 FLWMTR 의 FRI 예측값과의
 * 예측 전력원단위(kWh/m³), 사용된 모든 예측 태그의 max(predc_dtm) 을 단일 응답으로 구성한다
 * (운전현황분석-9번섹션 PLAN1 §구현 방향 §Service 책임 분담).</p>
 *
 * <p>4-SELECT 패턴 — Facility 1 + Instrument 1 + Tag 1 + TagPredcLatest 1 (4번 섹션
 * {@link FacilityOperatingStatusService} 동형, 인용 근거). 4번 헬퍼 직접 재사용 금지 — 본 사이클 자체
 * private 메서드로 재작성 ({@code coding-discipline.md §3} 정밀한 수정 + 사용자 메모리 "사이클 간 자산
 * 자동 원용 금지" 정합). 추상화 깊이는 호출 스택 3단 (Controller → Service → private 헬퍼) 이하 유지 +
 * 메서드 본문 50줄 이내 (coding-discipline.md §2.1 정량 기준).</p>
 *
 * <p>4번 대비 차이 — {@code predc_1m_h} 는 {@code corr_val}·{@code quality_cd} 컬럼 부재이므로
 * {@code effectiveVal(corrVal ?: rawVal)} 분기 + {@code quality_cd = GOOD} 분기 모두 제거 →
 * {@code predc_val} 단일값 직접 사용. SCADA QUALITY 분기 미적용 ({@code ot-integration.md §3} OPS BAD
 * 즉시 격상은 실측 전용 — 예측에는 구조적 적용 불가, 도메인 위반 아닌 설계 범위 차이).</p>
 *
 * <p>도메인 룰 인용:
 * {@code entity-patterns.md §JPA JOINED 다형성 §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityPredcOperatingStatusService {

    /** 본 카드 지원 시설 종류 — RSV(저수조 펌프 미보유) · POINT(관로 분기점) 거부 (4번 동일). */
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
    private final TagPredcLatestRepository tagPredcLatestRepository;

    /**
     * 활성 시설의 예측 운영 현황을 통합 응답한다.
     *
     * @param facilityId 활성 시설 ID
     * @return 시설 단위 예측 운영 현황 (예측 On 펌프명·PWI 예측 합산·예측 전력원단위·예측 시점)
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재 또는 비활성 시설
     * @throws RestApiException UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류
     */
    public FacilityPredcOperatingStatusDto findFacilityPredcOperatingStatus(String facilityId) {
        Facility facility = findActiveFacilityOrThrow(facilityId);

        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdAndEquipType(facilityId, TARGET_EQUIP_TYPES);
        if (instruments.isEmpty()) {
            return FacilityPredcOperatingStatusDto.of(
                    facility.getFacilityId(), facility.getFacilityNm(),
                    null, List.of(), BigDecimal.ZERO, null);
        }

        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(instruments);
        Map<String, TagPredcLatestDto> latestByTag = loadLatestByTag(tagsByInstrument);

        List<Pump> pumps = filterPumps(instruments);
        List<Pump> onPumps = pumps.stream()
                .filter(p -> isPumpRunning(
                        pickLatest(tagsByInstrument, p.getInstrumentId(), TagMeasurementType.OPS, latestByTag)))
                .toList();

        BigDecimal totalElpwrAmt = sumOnPumpPwr(onPumps, tagsByInstrument, latestByTag);
        TagPredcLatestDto fri = selectFacilityFri(instruments, tagsByInstrument, latestByTag);
        BigDecimal elpwrUnitQty = computeUnitConsumption(totalElpwrAmt, fri);
        LocalDateTime predcDtm = selectMaxPredcDtm(
                collectUsedTags(pumps, tagsByInstrument, latestByTag, fri));

        List<String> onPumpNms = onPumps.stream().map(Pump::getInstrumentNm).toList();
        return FacilityPredcOperatingStatusDto.of(
                facility.getFacilityId(), facility.getFacilityNm(),
                predcDtm, onPumpNms, totalElpwrAmt, elpwrUnitQty);
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

    /** 태그별 최신 예측값 일괄 조회 — IN 절 단일 native SQL (DISTINCT ON + 1시간 윈도우). */
    private Map<String, TagPredcLatestDto> loadLatestByTag(Map<String, List<Tag>> tagsByInstrument) {
        List<String> tagSrlNos = tagsByInstrument.values().stream()
                .flatMap(List::stream)
                .map(Tag::getTagSrlNo)
                .toList();
        return tagPredcLatestRepository.findLatestByTagSrlNos(tagSrlNos).stream()
                .collect(Collectors.toMap(TagPredcLatestDto::tagSrlNo, r -> r));
    }

    /** equip_type_cd = 'PUMP' 필터 강제 — 다형성 부모 List<Instrument> 에서 Pump 자식만 추출. */
    private List<Pump> filterPumps(List<Instrument> instruments) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.PUMP)
                .map(i -> (Pump) i)
                .toList();
    }

    /** 인스트루먼트의 지정 측정 유형 최신 예측값 1건 추출 (없으면 null). */
    private TagPredcLatestDto pickLatest(
            Map<String, List<Tag>> tagsByInstrument,
            String instrumentId,
            TagMeasurementType targetType,
            Map<String, TagPredcLatestDto> latestByTag) {
        return tagsByInstrument.getOrDefault(instrumentId, List.of()).stream()
                .filter(t -> t.getTagSeCd() == targetType)
                .map(t -> latestByTag.get(t.getTagSrlNo()))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 예측 측정값 추출 — {@code predc_1m_h} 는 {@code corr_val} 컬럼 부재이므로 4번의
     * {@code effectiveVal(corrVal ?: rawVal)} 분기 제거. {@code predc_val} 단일값 직접 사용.
     */
    private BigDecimal predcVal(TagPredcLatestDto p) {
        return p == null ? null : p.predcVal();
    }

    /**
     * 펌프 예측 On 판정 — null OR predcVal != 1.0 → false.
     *
     * <p>4번의 {@code qualityCd != GOOD → false} 분기 제거 — {@code predc_1m_h} 는 {@code quality_cd}
     * 컬럼 부재. {@code ot-integration.md §3} OPS BAD 즉시 격상은 실측 전용 (SCADA QUALITY 코드 부재로
     * 예측에는 구조적 적용 불가). {@code predc_val IS NULL} (결측·신뢰도 불명) 과 {@code predc_val == 0.0}
     * (예측 Off 확신) 둘 다 On 제외 합쳐짐 (PLAN1 §OPS On 판정 정책).</p>
     */
    private boolean isPumpRunning(TagPredcLatestDto ops) {
        if (ops == null) {
            return false;
        }
        BigDecimal val = predcVal(ops);
        return val != null && val.compareTo(BigDecimal.ONE) == 0;
    }

    /**
     * 예측 On 펌프들의 PWI 예측 합산 — null 만 제외 (PLAN1 §PWI 합산 정책).
     * 예측 On 펌프 0대 또는 모든 PWI 결측 시 BigDecimal.ZERO 반환.
     * 4번 대비 {@code qualityCd} 분기 제거.
     */
    private BigDecimal sumOnPumpPwr(
            List<Pump> onPumps,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, TagPredcLatestDto> latestByTag) {
        return onPumps.stream()
                .map(p -> pickLatest(tagsByInstrument, p.getInstrumentId(),
                        TagMeasurementType.PWI, latestByTag))
                .filter(pwi -> pwi != null)
                .map(this::predcVal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 시설 직속 FLWMTR 의 첫 FRI 예측 태그 측정값 — PLAN1 §FRI 매핑 정책
     * (parent_facility_id 재귀 미적용, {@code quality_cd} 분기 부재).
     */
    private TagPredcLatestDto selectFacilityFri(
            List<Instrument> instruments,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, TagPredcLatestDto> latestByTag) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .map(i -> pickLatest(tagsByInstrument, i.getInstrumentId(),
                        TagMeasurementType.FRI, latestByTag))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 예측 전력원단위 (kWh/m³) — 3 케이스 분기 (분자 0/null · FRI 부재 · 분모 null/0) 모두 null.
     * 정상 시 totalElpwrAmt / friVal (scale=4, RoundingMode.HALF_UP).
     * 4번 대비 {@code quality_cd} 분기 2건 (분자 PWI BAD·분모 FRI BAD) 제거.
     */
    private BigDecimal computeUnitConsumption(BigDecimal totalElpwrAmt, TagPredcLatestDto fri) {
        if (totalElpwrAmt == null || totalElpwrAmt.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (fri == null) {
            return null;
        }
        BigDecimal friVal = predcVal(fri);
        if (friVal == null || friVal.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP);
    }

    /** 사용된 모든 예측 태그(OPS+PWI+FRI) 의 TagPredcLatestDto 수집 — predcDtm 산출용. */
    private Collection<TagPredcLatestDto> collectUsedTags(
            List<Pump> pumps,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, TagPredcLatestDto> latestByTag,
            TagPredcLatestDto fri) {
        Collection<TagPredcLatestDto> used = new ArrayList<>();
        for (Pump pump : pumps) {
            used.add(pickLatest(tagsByInstrument, pump.getInstrumentId(),
                    TagMeasurementType.OPS, latestByTag));
            used.add(pickLatest(tagsByInstrument, pump.getInstrumentId(),
                    TagMeasurementType.PWI, latestByTag));
        }
        used.add(fri);
        return used;
    }

    /** 사용된 예측 태그들의 max(predc_dtm) — 빈 데이터 시 null (PLAN1 §시점 산출). */
    private LocalDateTime selectMaxPredcDtm(Collection<TagPredcLatestDto> usedTags) {
        return usedTags.stream()
                .filter(r -> r != null)
                .map(TagPredcLatestDto::predcDtm)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
