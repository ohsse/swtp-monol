package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityStateDto;
import com.mo.swtp.facility.dto.FlwmtrStateDto;
import com.mo.swtp.facility.dto.PumpStateDto;
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
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프제어분석 §3 — 시설 실시간 상태 표출 서비스.
 *
 * <p>활성 시설({@code facilityId}, {@code use_yn = Y}) 의 유량계(FLWMTR) FRI/PRI 측정값 + 펌프(PUMP)
 * OPS 가동상태 + 정적 {@code oprtngType} 을 단건 endpoint 응답으로 구성한다. 시설 → 계측기 → 태그 →
 * 측정값 4-step 체이닝을 모두 IN 절 기반 단일 SQL 로 가져온 뒤 Service 가 메모리에서 그룹화한다
 * (송수펌프제어분석-3번섹션 PLAN1 §구현 방향 §Service 책임 분담).</p>
 *
 * <p>N+1 회피: SQL 4회 (Facility 1 + Instrument 1 + Tag 1 + RawData 1).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityStateService {

    /** §3 측정 대상 계측기 종류 — 펌프 + 유량계 (PLAN1 §구현 방향 §Service Step 2). */
    private static final List<EquipType> STATE_EQUIP_TYPES = List.of(EquipType.PUMP, EquipType.FLWMTR);

    /** §3 응답 대상 태그 측정 유형 — FRI 유량 / PRI 압력 / OPS 가동상태 (PLAN1 §구현 방향 §Service Step 3). */
    private static final Set<TagMeasurementType> STATE_TAG_TYPES = EnumSet.of(
            TagMeasurementType.FRI, TagMeasurementType.PRI, TagMeasurementType.OPS);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 활성 시설의 실시간 상태를 통합 응답한다.
     *
     * @param facilityId 활성 시설 ID
     * @return 시설 단위 유량계·펌프 상태
     * @throws RestApiException FACILITY_NOT_FOUND — 존재하지 않거나 비활성 시설
     */
    public FacilityStateDto findFacilityState(String facilityId) {
        Facility facility = findActiveFacilityOrThrow(facilityId);

        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdAndEquipType(facilityId, STATE_EQUIP_TYPES);
        if (instruments.isEmpty()) {
            return FacilityStateDto.of(
                    facility.getFacilityId(), facility.getFacilityNm(),
                    List.of(), List.of());
        }

        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        List<Tag> tags = tagRepository
                .findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> STATE_TAG_TYPES.contains(t.getTagSeCd()))
                .toList();

        List<String> tagSrlNos = tags.stream().map(Tag::getTagSrlNo).toList();
        Map<String, RawDataLatestDto> latestByTag = rawDataRepository
                .findLatestByTagSrlNos(tagSrlNos).stream()
                .collect(Collectors.toMap(RawDataLatestDto::tagSrlNo, r -> r));

        Map<String, List<Tag>> tagsByInstrument = tags.stream()
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));

        List<FlwmtrStateDto> flwmtrs = instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .map(i -> mapFlwmtrState(i, tagsByInstrument, latestByTag))
                .toList();
        List<PumpStateDto> pumps = instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.PUMP)
                .map(i -> mapPumpState((Pump) i, tagsByInstrument, latestByTag))
                .toList();

        return FacilityStateDto.of(
                facility.getFacilityId(), facility.getFacilityNm(), flwmtrs, pumps);
    }

    /**
     * 활성 시설을 조회하거나 미존재·비활성 시 FACILITY_NOT_FOUND 예외를 던진다.
     */
    private Facility findActiveFacilityOrThrow(String facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
        if (facility.getUseYn() != YnType.Y) {
            throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
        }
        return facility;
    }

    /**
     * 유량계 1대의 FRI/PRI 최신값을 추출하여 응답 DTO 로 매핑한다.
     */
    private FlwmtrStateDto mapFlwmtrState(
            Instrument flwmtr,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(flwmtr.getInstrumentId(), List.of());
        RawDataLatestDto fri = pickLatest(instrumentTags, TagMeasurementType.FRI, latestByTag);
        RawDataLatestDto pri = pickLatest(instrumentTags, TagMeasurementType.PRI, latestByTag);
        return FlwmtrStateDto.of(
                flwmtr.getInstrumentId(), flwmtr.getInstrumentNm(),
                rawVal(fri), corrVal(fri), acqDtm(fri), qualityCd(fri),
                rawVal(pri), corrVal(pri), acqDtm(pri), qualityCd(pri));
    }

    /**
     * 펌프 1대의 OPS 최신값 + 정적 oprtngType 을 추출하여 응답 DTO 로 매핑한다.
     */
    private PumpStateDto mapPumpState(
            Pump pump,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(pump.getInstrumentId(), List.of());
        RawDataLatestDto ops = pickLatest(instrumentTags, TagMeasurementType.OPS, latestByTag);
        Boolean isRunning = toBoolean(rawVal(ops));
        return PumpStateDto.of(
                pump.getInstrumentId(), pump.getInstrumentNm(),
                pump.getOprtngType(),
                isRunning, acqDtm(ops), qualityCd(ops));
    }

    /**
     * 계측기 산하 태그 중 지정 측정 유형의 최신 측정값을 1건 추출한다.
     */
    private RawDataLatestDto pickLatest(
            List<Tag> tags,
            TagMeasurementType targetType,
            Map<String, RawDataLatestDto> latestByTag) {
        return tags.stream()
                .filter(t -> t.getTagSeCd() == targetType)
                .map(t -> latestByTag.get(t.getTagSrlNo()))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal rawVal(RawDataLatestDto r) {
        return r == null ? null : r.rawVal();
    }

    private BigDecimal corrVal(RawDataLatestDto r) {
        return r == null ? null : r.corrVal();
    }

    private LocalDateTime acqDtm(RawDataLatestDto r) {
        return r == null ? null : r.acqDtm();
    }

    private QualityCode qualityCd(RawDataLatestDto r) {
        return r == null ? null : r.qualityCd();
    }

    /**
     * OPS 가동상태 raw_val Boolean 변환 — 1.0=true / 0.0=false / NULL 또는 그 외=null.
     */
    private Boolean toBoolean(BigDecimal val) {
        if (val == null) {
            return null;
        }
        if (val.compareTo(BigDecimal.ONE) == 0) {
            return Boolean.TRUE;
        }
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return Boolean.FALSE;
        }
        return null;
    }
}
