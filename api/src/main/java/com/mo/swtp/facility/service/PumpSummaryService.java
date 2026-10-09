package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilitySearchDto;
import com.mo.swtp.facility.dto.PumpSummaryDto;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프제어분석 §6 — 시설별 토출관압 + 운전중 펌프 대수 요약 서비스.
 *
 * <p>1번 섹션 시설목록(hasPump=true 활성 펌프 시설 전체)을 1번 섹션과 동일한 시설 집합·정렬로 가져온 뒤,
 * 시설별로 토출관압(FLWMTR 첫 매치 PRI 최신값) + 운전중 펌프 대수(PUMP OPS 가동상태 ON) 를 목록 응답으로
 * 구성한다. 1번 섹션에서 어떤 시설을 활성화했는지와 무관하게 전체 시설의 최신값을 항상 유지한다
 * (송수펌프제어분석-6번섹션 PLAN1 §구현 방향).</p>
 *
 * <p>N+1 회피: SQL 4회 (Facility 1 + Instrument 1 + Tag 1 + RawData 1). §3 {@link FacilityStateService}
 * 단건 패턴을 hasPump 시설 N건 으로 확장 — 동명 private 헬퍼는 이전 섹션 자산 자동 재사용 금지 정책에
 * 따라 본 서비스 전용으로 독립 보유한다 (PLAN1 §Step 5 매핑).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PumpSummaryService {

    /** §6 측정 대상 계측기 종류 — 펌프(운전 대수) + 유량계(토출관압 PRI) (PLAN1 §Service Step 2). */
    static final List<EquipType> SUMMARY_EQUIP_TYPES = List.of(EquipType.PUMP, EquipType.FLWMTR);

    /** §6 응답 대상 태그 측정 유형 — PRI 압력(토출관압) / OPS 가동상태 (PLAN1 §Service Step 3). */
    static final Set<TagMeasurementType> SUMMARY_TAG_TYPES = EnumSet.of(
            TagMeasurementType.PRI, TagMeasurementType.OPS);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * hasPump=true 활성 펌프 시설 전체의 토출관압·운전중 펌프 대수 요약을 목록 응답한다.
     *
     * @return 시설 1건 = 1 row, 1번 섹션 목록과 동일 시설 집합·정렬
     */
    public List<PumpSummaryDto> findPumpSummaries() {
        // Step 1: hasPump=true 시설 목록 — 1번 섹션 list 쿼리 정확 미러링 (추가 useYn 필터 없음)
        FacilitySearchDto search = new FacilitySearchDto();
        search.setHasPump(true);
        List<Facility> facilities = facilityRepository.findFacilities(search);
        if (facilities.isEmpty()) {
            return List.of();
        }

        // Step 2: 시설 IN → PUMP·FLWMTR 계측기 IN 절 단일 조회
        List<String> facilityIds = facilities.stream().map(Facility::getFacilityId).toList();
        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdInAndEquipType(facilityIds, SUMMARY_EQUIP_TYPES);

        // Step 3: 계측기 IN → 활성 태그 IN 절 단일 조회 (PRI·OPS 필터)
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        List<Tag> tags = instrumentIds.isEmpty()
                ? List.of()
                : tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                        .filter(t -> SUMMARY_TAG_TYPES.contains(t.getTagSeCd()))
                        .toList();

        // Step 4: 태그 IN → 최신값 단일 조회 (DISTINCT ON + 1시간 파티션 프루닝)
        List<String> tagSrlNos = tags.stream().map(Tag::getTagSrlNo).toList();
        Map<String, RawDataLatestDto> latestByTag = tagSrlNos.isEmpty()
                ? Map.of()
                : rawDataRepository.findLatestByTagSrlNos(tagSrlNos).stream()
                        .collect(Collectors.toMap(RawDataLatestDto::tagSrlNo, r -> r));

        // Step 5: 메모리 집계 — findFacilities 정렬 순서 보존하며 시설별 매핑
        Map<String, List<Instrument>> instrumentsByFacility = instruments.stream()
                .collect(Collectors.groupingBy(i -> i.getFacility().getFacilityId()));
        Map<String, List<Tag>> tagsByInstrument = tags.stream()
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));

        return facilities.stream()
                .map(f -> mapPumpSummary(f, instrumentsByFacility, tagsByInstrument, latestByTag))
                .toList();
    }

    /**
     * 시설 1건의 토출관압 + 운전중 펌프 대수를 요약 DTO 로 매핑한다.
     */
    private PumpSummaryDto mapPumpSummary(
            Facility facility,
            Map<String, List<Instrument>> instrumentsByFacility,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<Instrument> facilityInstruments =
                instrumentsByFacility.getOrDefault(facility.getFacilityId(), List.of());

        List<RawDataLatestDto> priCandidates = collectPriCandidates(
                facilityInstruments, tagsByInstrument, latestByTag);
        boolean multiplePrsrDetected = priCandidates.size() > 1;
        if (multiplePrsrDetected) {
            log.warn("시설 {} 에 토출관압(PRI) 다중 등록 감지 ({}건). 첫 매치 사용.",
                    facility.getFacilityId(), priCandidates.size());
        }
        RawDataLatestDto prsr = priCandidates.isEmpty() ? null : priCandidates.get(0);

        int oprtngPumpCnt = 0;
        int unknownPumpCnt = 0;
        for (Instrument pump : facilityInstruments) {
            if (pump.getEquipType() != EquipType.PUMP) {
                continue;
            }
            RawDataLatestDto ops = pickLatest(
                    tagsByInstrument.getOrDefault(pump.getInstrumentId(), List.of()),
                    TagMeasurementType.OPS, latestByTag);
            OpsState state = classifyOps(ops);
            if (state == OpsState.OPERATING) {
                oprtngPumpCnt++;
            } else if (state == OpsState.UNKNOWN) {
                unknownPumpCnt++;
            }
        }

        return PumpSummaryDto.of(
                facility.getFacilityId(), facility.getFacilityNm(),
                rawVal(prsr), corrVal(prsr), acqDtm(prsr), qualityCd(prsr),
                multiplePrsrDetected, oprtngPumpCnt, unknownPumpCnt);
    }

    /**
     * 시설 산하 FLWMTR 들의 PRI 최신값을 모두 수집한다 (첫 매치 + 다중 검출 판정용).
     *
     * <p>FLWMTR 2대 이상에 PRI 최신값이 있거나 한 FLWMTR 에 PRI 태그가 2개 이상이면 size > 1 이 된다.</p>
     */
    private List<RawDataLatestDto> collectPriCandidates(
            List<Instrument> facilityInstruments,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        List<RawDataLatestDto> candidates = new ArrayList<>();
        for (Instrument instrument : facilityInstruments) {
            if (instrument.getEquipType() != EquipType.FLWMTR) {
                continue;
            }
            List<Tag> instrumentTags =
                    tagsByInstrument.getOrDefault(instrument.getInstrumentId(), List.of());
            for (Tag tag : instrumentTags) {
                if (tag.getTagSeCd() != TagMeasurementType.PRI) {
                    continue;
                }
                RawDataLatestDto latest = latestByTag.get(tag.getTagSrlNo());
                if (latest != null) {
                    candidates.add(latest);
                }
            }
        }
        return candidates;
    }

    /**
     * OPS 최신값을 운전 대수 분류 상태로 변환한다 ({@code ot-integration.md §3} 정합).
     *
     * <ul>
     *   <li>{@link OpsState#OPERATING} — GOOD AND rawVal = 1.0 (운전중 확정)</li>
     *   <li>{@link OpsState#UNKNOWN} — 결측 OR BAD/UNCERTAIN OR (GOOD AND rawVal 0/1 아님 — 판정불가)</li>
     *   <li>{@link OpsState#STOPPED} — GOOD AND rawVal = 0.0 (정지 확정, 양쪽 카운트 미증가)</li>
     * </ul>
     */
    private OpsState classifyOps(RawDataLatestDto ops) {
        if (ops == null || ops.qualityCd() == QualityCode.BAD
                || ops.qualityCd() == QualityCode.UNCERTAIN) {
            return OpsState.UNKNOWN;
        }
        BigDecimal val = ops.rawVal();
        if (val == null) {
            return OpsState.UNKNOWN;
        }
        if (val.compareTo(BigDecimal.ONE) == 0) {
            return OpsState.OPERATING;
        }
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return OpsState.STOPPED;
        }
        return OpsState.UNKNOWN;
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

    private java.time.LocalDateTime acqDtm(RawDataLatestDto r) {
        return r == null ? null : r.acqDtm();
    }

    private QualityCode qualityCd(RawDataLatestDto r) {
        return r == null ? null : r.qualityCd();
    }

    /** OPS 운전 대수 분류 상태. */
    private enum OpsState {
        /** 운전중 확정 — oprtngPumpCnt 증가 */
        OPERATING,
        /** 신뢰불가 — unknownPumpCnt 증가 */
        UNKNOWN,
        /** 정지 확정 — 양쪽 미증가 */
        STOPPED
    }
}
