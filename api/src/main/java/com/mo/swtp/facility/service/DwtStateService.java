package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.DistributionWaterTank;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.DwtGroupStateDto;
import com.mo.swtp.facility.dto.DwtStateDto;
import com.mo.swtp.facility.dto.InletFlwmtrStateDto;
import com.mo.swtp.facility.dto.LvmtrStateDto;
import com.mo.swtp.facility.dto.OutletFlwmtrStateDto;
import com.mo.swtp.facility.dto.ValveStateDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프제어분석 §4 — 배수지(DWT) 실시간 상태 표출 서비스.
 *
 * <p>1번 섹션에서 활성화된 부모 시설({@code parentFacilityId}, {@code use_yn = Y}) 의 자식 DWT 들의
 * 유입/유출 유량계·밸브·수위계 최신 측정값을 단일 endpoint 응답으로 구성한다. 부모 → 자식 DWT N건 →
 * 계측기 다건 → 태그 다건 → 측정값 다건 의 6-step 체이닝을 모두 IN 절 기반 단일 SQL 로 가져온 뒤
 * Service 가 메모리에서 그룹화한다 (PLAN1 §Service 흐름).</p>
 *
 * <p>N+1 회피: SQL 5회 (Facility 부모 1 + Facility 자식 1 + Instrument 1 + Tag 1 + RawData 1).
 * §3 {@link FacilityStateService} 와 동일 패턴 — 다건 부모-자식 N건 으로 확장.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DwtStateService {

    /** §4 측정 대상 계측기 종류 — 유량계 + 밸브 + 수위계 (PLAN1 §Service 흐름 Step 3). */
    private static final List<EquipType> DWT_EQUIP_TYPES =
            List.of(EquipType.FLWMTR, EquipType.VALVE, EquipType.LVMTR);

    /** §4 응답 대상 태그 측정 유형 — FRI 유량 / PRI 압력 / VOI 개도 / LEI 수위 (PLAN1 §Service 흐름 Step 4). */
    private static final Set<TagMeasurementType> DWT_TAG_TYPES = EnumSet.of(
            TagMeasurementType.FRI, TagMeasurementType.PRI,
            TagMeasurementType.VOI, TagMeasurementType.LEI);

    /** 유입 후보 io_cd 코드 — INPUT 또는 BIDIR (PLAN1 §가정 결정 — BIDIR 양쪽 후보 허용). */
    private static final Set<IoCode> INLET_IO_CODES = EnumSet.of(IoCode.INPUT, IoCode.BIDIR);

    /** 유출 후보 io_cd 코드 — OUTPUT 또는 BIDIR. */
    private static final Set<IoCode> OUTLET_IO_CODES = EnumSet.of(IoCode.OUTPUT, IoCode.BIDIR);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 부모 시설의 자식 DWT 그룹 실시간 상태를 통합 응답한다.
     *
     * @param parentFacilityId 활성 부모 시설 ID
     * @return 자식 DWT 그룹 단위 유량계·밸브·수위계 상태
     * @throws RestApiException FACILITY_NOT_FOUND — 존재하지 않거나 비활성 부모 시설
     */
    public DwtGroupStateDto findDwtStates(String parentFacilityId) {
        // Step 1: 부모 시설 조회 + 활성 검증
        Facility parent = findActiveFacilityOrThrow(parentFacilityId);

        // Step 2: 자식 DWT 다건 조회 (disp_ord ASC)
        List<Facility> dwts = facilityRepository
                .findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(
                        parentFacilityId, FacilityType.DWT, YnType.Y);
        if (dwts.isEmpty()) {
            return DwtGroupStateDto.of(parent, List.of());
        }

        // Step 3: DWT 자식들의 계측기 다건 조회
        List<String> dwtIds = dwts.stream().map(Facility::getFacilityId).toList();
        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdInAndEquipType(dwtIds, DWT_EQUIP_TYPES);

        // Step 4: 계측기 산하 활성 태그 다건 조회
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        List<Tag> tags = instrumentIds.isEmpty()
                ? List.of()
                : tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                        .filter(t -> DWT_TAG_TYPES.contains(t.getTagSeCd()))
                        .toList();

        // Step 5: 태그 시리얼번호 묶음의 최신 측정값 다건 조회 (DISTINCT ON + 1시간 윈도우)
        List<String> tagSrlNos = tags.stream().map(Tag::getTagSrlNo).toList();
        Map<String, RawDataLatestDto> latestByTag = tagSrlNos.isEmpty()
                ? Map.of()
                : rawDataRepository.findLatestByTagSrlNos(tagSrlNos).stream()
                        .collect(Collectors.toMap(RawDataLatestDto::tagSrlNo, r -> r));

        // Step 6: 메모리 그룹화 + DTO 조립
        Map<String, List<Instrument>> instrumentsByDwt = instruments.stream()
                .collect(Collectors.groupingBy(i -> i.getFacility().getFacilityId()));
        Map<String, List<Tag>> tagsByInstrument = tags.stream()
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));

        List<DwtStateDto> dwtDtos = new ArrayList<>(dwts.size());
        for (Facility dwt : dwts) {
            dwtDtos.add(assembleDwtState(dwt, instrumentsByDwt, tagsByInstrument, latestByTag));
        }
        return DwtGroupStateDto.of(parent, dwtDtos);
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
     * 한 DWT 의 유입/유출 유량계·밸브·수위계를 조립한다.
     *
     * <p>유입 FLWMTR 는 태그 {@code io_cd} 가 INPUT·BIDIR 중 하나인 태그를 1건이라도 가진 계측기.
     * 유출은 OUTPUT·BIDIR. 다중 등록 시 첫 매치 + WARN 로그 + 응답 플래그 (PLAN1 §다중 등록 안전망).</p>
     */
    private DwtStateDto assembleDwtState(
            Facility dwt,
            Map<String, List<Instrument>> instrumentsByDwt,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        // JOINED 자식 자동 적재 — facility_type_cd = DWT 필터로 타입 보장됨 (캐스팅 실패 시 fail-fast)
        DistributionWaterTank dwtChild = (DistributionWaterTank) dwt;
        List<Instrument> dwtInstruments = instrumentsByDwt.getOrDefault(dwt.getFacilityId(), List.of());

        List<Instrument> flwmtrs = filterByEquipType(dwtInstruments, EquipType.FLWMTR);
        List<Instrument> inletList = filterByIoCode(flwmtrs, tagsByInstrument, INLET_IO_CODES);
        List<Instrument> outletList = filterByIoCode(flwmtrs, tagsByInstrument, OUTLET_IO_CODES);

        boolean multipleIn = inletList.size() > 1;
        boolean multipleOut = outletList.size() > 1;
        if (multipleIn) {
            log.warn("DWT {} 에 유입 FLWMTR 다중 등록 감지 ({}건). 첫 매치 사용.",
                    dwt.getFacilityId(), inletList.size());
        }
        if (multipleOut) {
            log.warn("DWT {} 에 유출 FLWMTR 다중 등록 감지 ({}건). 첫 매치 사용.",
                    dwt.getFacilityId(), outletList.size());
        }

        InletFlwmtrStateDto inDto = inletList.isEmpty()
                ? null
                : mapInletFlwmtr(inletList.get(0), tagsByInstrument, latestByTag);
        OutletFlwmtrStateDto outDto = outletList.isEmpty()
                ? null
                : mapOutletFlwmtr(outletList.get(0), tagsByInstrument, latestByTag);

        List<ValveStateDto> valves = filterByEquipType(dwtInstruments, EquipType.VALVE).stream()
                .map(v -> mapValve(v, tagsByInstrument, latestByTag))
                .toList();
        List<LvmtrStateDto> lvmtrs = filterByEquipType(dwtInstruments, EquipType.LVMTR).stream()
                .map(l -> mapLvmtr(l, tagsByInstrument, latestByTag))
                .toList();

        return DwtStateDto.of(
                dwt.getFacilityId(), dwt.getFacilityNm(),
                dwtChild.getMinReqPrsr(), dwtChild.getMinReqBranchPrsr(),
                inDto, outDto, multipleIn, multipleOut, valves, lvmtrs);
    }

    /** 계측기 목록에서 지정 종류만 필터링한다 ({@code disp_ord} 정렬은 상위 쿼리 결과 그대로 유지). */
    private List<Instrument> filterByEquipType(Collection<Instrument> instruments, EquipType targetType) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == targetType)
                .toList();
    }

    /** FLWMTR 들 중 산하 태그의 {@code io_cd} 가 후보 코드 집합과 1건이라도 일치하는 계측기만 추출한다. */
    private List<Instrument> filterByIoCode(
            List<Instrument> flwmtrs,
            Map<String, List<Tag>> tagsByInstrument,
            Set<IoCode> candidateCodes) {
        List<Instrument> matched = new ArrayList<>();
        for (Instrument flwmtr : flwmtrs) {
            List<Tag> instrumentTags = tagsByInstrument.getOrDefault(flwmtr.getInstrumentId(), List.of());
            Set<IoCode> instrumentIoCodes = new HashSet<>();
            for (Tag t : instrumentTags) {
                instrumentIoCodes.add(t.getIoCd());
            }
            if (!java.util.Collections.disjoint(instrumentIoCodes, candidateCodes)) {
                matched.add(flwmtr);
            }
        }
        return matched;
    }

    /** 유입 유량계 1대의 FRI + PRI 최신값을 추출하여 응답 DTO 로 매핑한다. */
    private InletFlwmtrStateDto mapInletFlwmtr(
            Instrument flwmtr,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(flwmtr.getInstrumentId(), List.of());
        RawDataLatestDto fri = pickLatest(instrumentTags, TagMeasurementType.FRI, latestByTag);
        RawDataLatestDto pri = pickLatest(instrumentTags, TagMeasurementType.PRI, latestByTag);
        return InletFlwmtrStateDto.of(
                flwmtr.getInstrumentId(), flwmtr.getInstrumentNm(),
                rawVal(fri), corrVal(fri), acqDtm(fri), qualityCd(fri),
                rawVal(pri), corrVal(pri), acqDtm(pri), qualityCd(pri));
    }

    /** 유출 유량계 1대의 FRI 최신값을 추출하여 응답 DTO 로 매핑한다 (PRI 미보유). */
    private OutletFlwmtrStateDto mapOutletFlwmtr(
            Instrument flwmtr,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(flwmtr.getInstrumentId(), List.of());
        RawDataLatestDto fri = pickLatest(instrumentTags, TagMeasurementType.FRI, latestByTag);
        return OutletFlwmtrStateDto.of(
                flwmtr.getInstrumentId(), flwmtr.getInstrumentNm(),
                rawVal(fri), corrVal(fri), acqDtm(fri), qualityCd(fri));
    }

    /** 밸브 1대의 VOI 최신값을 추출하여 응답 DTO 로 매핑한다. */
    private ValveStateDto mapValve(
            Instrument valve,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(valve.getInstrumentId(), List.of());
        RawDataLatestDto voi = pickLatest(instrumentTags, TagMeasurementType.VOI, latestByTag);
        return ValveStateDto.of(
                valve.getInstrumentId(), valve.getInstrumentNm(),
                rawVal(voi), corrVal(voi), acqDtm(voi), qualityCd(voi));
    }

    /** 수위계 1대의 LEI 최신값을 추출하여 응답 DTO 로 매핑한다. */
    private LvmtrStateDto mapLvmtr(
            Instrument lvmtr,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(lvmtr.getInstrumentId(), List.of());
        RawDataLatestDto lei = pickLatest(instrumentTags, TagMeasurementType.LEI, latestByTag);
        return LvmtrStateDto.of(
                lvmtr.getInstrumentId(), lvmtr.getInstrumentNm(),
                rawVal(lei), corrVal(lei), acqDtm(lei), qualityCd(lei));
    }

    /** 계측기 산하 태그 중 지정 측정 유형의 최신 측정값을 1건 추출한다. */
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
}
