package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityPowerInstrumentDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시설별 사용량 3번섹션 — 전력 계측기 목록 조회 서비스 (읽기 전용).
 *
 * <p>2번섹션에서 선택한 운영시설({@code facilityId}) 을 루트로 하여 {@code parent_facility_id} self-FK 를 따라
 * 재귀 하위 트리 전체(루트 inclusive)를 수집한 뒤, 그 시설들의 활성 계측기 중 전력관련 태그
 * (PWI 순시전력 ∪ PWQ 적산전력량)를 1건 이상 보유한 계측기 목록을 반환한다 (시설별사용량-3번섹션 PLAN1 §목적).</p>
 *
 * <p>아키텍처: "시설 → 계측기 → 태그 IN절 일괄 조회 + 인메모리 멤버십 필터" ({@link FacilityStateService}·
 * {@link FacilityEnergyUsageService} 선례) + "앱 레벨 BFS 재귀 하위 수집" ({@link FacilityDownstreamTreeResolver}
 * 선례) 두 패턴의 결합이다. SQL 호출은 시설 수와 무관하게 고정 — 루트 Facility 1 + BFS(레벨당 1회) +
 * 계측기 IN 1 + 태그 IN 1. {@link #collectSubtree} 는 {@link FacilityDownstreamTreeResolver#collectSubtree} 의
 * 동형 복제다 (working code 비침습 — {@code .claude/rules/coding-discipline.md §3} 정밀한 수정 +
 * ANALYZE1 wtp-backend-engineer 결론, 공통 추출은 사본 3건 이상 누적 시 별도 ANALYZE).</p>
 *
 * <p>{@code instrument.getFacility().getFacilityId()} 는 프록시 식별자 접근(추가 쿼리 0) 이며, 응답 {@code facilityNm}
 * 과 정렬 {@code dispOrd} 는 사전 수집한 {@link Facility} 맵에서 조회해 N+1 을 회피한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityPowerInstrumentService {

    /** 재귀 하위 BFS 최대 깊이 — 순환·과도 깊이 방어 백스톱 (visited-set 이 1차 방어). */
    static final int MAX_DEPTH = 10;

    /** 전력관련 태그 유형 — PWI(순시전력) · PWQ(적산전력량). */
    private static final Set<TagMeasurementType> POWER_TAG_TYPES =
            EnumSet.of(TagMeasurementType.PWI, TagMeasurementType.PWQ);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;

    /**
     * 루트 시설의 재귀 하위 트리 전체에서 전력관련 태그 보유 계측기 목록을 조회한다.
     *
     * @param facilityId 루트 시설 ID (2번섹션에서 선택한 운영시설)
     * @return 전력태그 보유 계측기 목록 (시설 disp_ord → 계측기 disp_ord → 계측기명 정렬, 부재 시 빈 목록)
     * @throws RestApiException FACILITY_NOT_FOUND — 존재하지 않거나 비활성 루트 시설
     */
    public List<FacilityPowerInstrumentDto> findPowerInstruments(String facilityId) {
        Facility root = findActiveFacilityOrThrow(facilityId);
        List<Facility> subtree = collectSubtree(root);
        Map<String, Facility> facilityById = subtree.stream()
                .collect(Collectors.toMap(Facility::getFacilityId, facility -> facility));

        List<Instrument> instruments = instrumentRepository
                .findByFacilityFacilityIdInAndUseYn(new ArrayList<>(facilityById.keySet()), YnType.Y);
        if (instruments.isEmpty()) {
            return List.of();
        }

        Map<String, List<Tag>> powerTagsByInstrument = loadPowerTagsByInstrument(instruments);
        return instruments.stream()
                .filter(instrument -> powerTagsByInstrument.containsKey(instrument.getInstrumentId()))
                .sorted(displayOrder(facilityById))
                .map(instrument -> toDto(instrument, facilityById, powerTagsByInstrument))
                .toList();
    }

    /**
     * 활성 시설을 조회하거나 미존재·비활성 시 FACILITY_NOT_FOUND 예외를 던진다
     * ({@link FacilityStateService#findActiveFacilityOrThrow} 동형 복제).
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
     * 루트를 포함한 재귀 하위 전체를 BFS 로 수집한다 — 레벨당 SQL 1회, visited-set 순환 방어 + depth 백스톱
     * ({@link FacilityDownstreamTreeResolver#collectSubtree} 동형 복제).
     *
     * @param root 루트 시설
     * @return 루트 + 활성 재귀 하위 시설 전체 (방문 순서)
     */
    private List<Facility> collectSubtree(Facility root) {
        List<Facility> allNodes = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        allNodes.add(root);
        visited.add(root.getFacilityId());

        List<Facility> currentLevel = List.of(root);
        int depth = 0;
        while (!currentLevel.isEmpty() && depth < MAX_DEPTH) {
            List<String> parentIds = currentLevel.stream().map(Facility::getFacilityId).toList();
            List<Facility> children = facilityRepository.findByParentFacilityIdInAndUseYn(parentIds, YnType.Y);
            List<Facility> nextLevel = new ArrayList<>();
            for (Facility child : children) {
                if (visited.add(child.getFacilityId())) {
                    allNodes.add(child);
                    nextLevel.add(child);
                }
            }
            currentLevel = nextLevel;
            depth++;
        }
        return allNodes;
    }

    /** 계측기 묶음의 활성 태그를 IN 절로 일괄 조회 → 전력태그만 필터 → 계측기 ID 로 그룹핑. */
    private Map<String, List<Tag>> loadPowerTagsByInstrument(List<Instrument> instruments) {
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(tag -> POWER_TAG_TYPES.contains(tag.getTagSeCd()))
                .collect(Collectors.groupingBy(tag -> tag.getInstrument().getInstrumentId()));
    }

    /** 정렬 비교자 — 소속 시설 disp_ord → 계측기 disp_ord → 계측기명. 시설 disp_ord 는 사전 수집 맵에서 조회. */
    private Comparator<Instrument> displayOrder(Map<String, Facility> facilityById) {
        return Comparator
                .comparingInt((Instrument instrument) ->
                        facilityById.get(instrument.getFacility().getFacilityId()).getDispOrd())
                .thenComparingInt(Instrument::getDispOrd)
                .thenComparing(Instrument::getInstrumentNm);
    }

    /** 계측기 1건 + 보유 전력태그를 응답 DTO 로 조립 — 소속 시설명은 사전 수집 맵에서 조회. */
    private FacilityPowerInstrumentDto toDto(
            Instrument instrument,
            Map<String, Facility> facilityById,
            Map<String, List<Tag>> powerTagsByInstrument) {
        Facility facility = facilityById.get(instrument.getFacility().getFacilityId());
        List<FacilityPowerInstrumentDto.PowerTagDto> tags =
                powerTagsByInstrument.get(instrument.getInstrumentId()).stream()
                        .map(tag -> FacilityPowerInstrumentDto.PowerTagDto.of(tag.getTagSrlNo(), tag.getTagSeCd()))
                        .toList();
        return FacilityPowerInstrumentDto.of(
                instrument.getInstrumentId(),
                instrument.getInstrumentNm(),
                instrument.getEquipType(),
                facility.getFacilityId(),
                facility.getFacilityNm(),
                tags);
    }
}
