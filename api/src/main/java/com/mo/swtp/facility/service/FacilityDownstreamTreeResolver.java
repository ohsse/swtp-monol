package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.repository.FacilityRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 운전현황분석 8번 섹션 — 활성 루트 시설의 재귀 하위 트리에서 표출 대상·수위 소스를 도출하는 컴포넌트.
 *
 * <p>12번 섹션 활성 시설(루트) 을 상위로 바라보고 {@code parent_facility_id} self-FK 를 따라 재귀 하위 전체를
 * 수집한 뒤, 다음을 인메모리로 분류한다:</p>
 * <ul>
 *   <li><b>표출 대상</b> — 재귀 하위(루트 포함) 중 {@code facility_type_cd ∈ {PWTF, POINT}} 이면서 자식 배수지(DWT)
 *       를 직속으로 1건 이상 가진 시설. 수요량/관압의 시리즈 단위이자 수위의 그룹핑 단위.</li>
 *   <li><b>수위 소스</b> — 각 표출 대상의 직속 자식 DWT 들 ({@code dwtsByTargetId}). 수위(LEVEL) 의 수위계 조회 대상.</li>
 * </ul>
 *
 * <p>루트 inclusive — 루트 자신이 PWTF·POINT 이고 DWT 자식을 가지면 표출 대상에 포함된다 (PLAN1 §가정 A-ROOT,
 * 사용자 승인 2026-06-01). DWT 의 상위 표출대상은 <b>직속 parent</b> 기준 — 직속 parent 가 PWTF·POINT 가 아닌
 * DWT 는 표출에서 제외된다 (PLAN1 §가정 A-DWT).</p>
 *
 * <p>구현은 앱 레벨 BFS (레벨당 SQL 1회 — {@link FacilityRepository#findByParentFacilityIdInAndUseYn}) 로,
 * {@code WITH RECURSIVE} 네이티브 쿼리 대신 채택 (depth 카운터 + visited-set 순환 방어, PLAN1 §가정 A-DEPTH).
 * dev DB 는 2단(PWTF→DWT) 이나 본 로직은 고령정수장 모델(3단 이상) 을 일반 처리한다.</p>
 */
@Component
@RequiredArgsConstructor
public class FacilityDownstreamTreeResolver {

    /** 재귀 하위 BFS 최대 깊이 — 순환·과도 깊이 방어 백스톱 (visited-set 이 1차 방어). */
    static final int MAX_DEPTH = 10;

    /** 표출 대상 후보 시설 종류 — 정수지(PWTF)·분기점(POINT). DWT 자식 보유 조건과 AND 결합. */
    private static final Set<FacilityType> DISPLAY_TARGET_TYPES =
            EnumSet.of(FacilityType.PWTF, FacilityType.POINT);

    private final FacilityRepository facilityRepository;

    /**
     * 활성 루트 시설의 재귀 하위에서 표출 대상과 수위 소스 DWT 를 도출한다.
     *
     * @param root 활성 루트 시설 (호출 전 활성 검증 완료 전제)
     * @return 표출 대상 목록 + 표출대상별 자식 DWT 맵
     */
    public DownstreamTopology resolve(Facility root) {
        List<Facility> allNodes = collectSubtree(root);

        Map<String, List<Facility>> dwtsByParent = allNodes.stream()
                .filter(f -> f.getFacilityType() == FacilityType.DWT)
                .filter(f -> f.getParentFacilityId() != null)
                .collect(Collectors.groupingBy(Facility::getParentFacilityId));

        List<Facility> displayTargets = allNodes.stream()
                .filter(f -> DISPLAY_TARGET_TYPES.contains(f.getFacilityType()))
                .filter(f -> dwtsByParent.containsKey(f.getFacilityId()))
                .toList();

        Map<String, List<Facility>> dwtsByTargetId = new LinkedHashMap<>();
        for (Facility target : displayTargets) {
            dwtsByTargetId.put(target.getFacilityId(), dwtsByParent.get(target.getFacilityId()));
        }
        return new DownstreamTopology(displayTargets, dwtsByTargetId);
    }

    /**
     * 루트를 포함한 재귀 하위 전체를 BFS 로 수집한다 — 레벨당 SQL 1회, visited-set 순환 방어 + depth 백스톱.
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

    /**
     * 재귀 하위 도출 결과 — 표출 대상 목록 + 표출대상별 직속 자식 DWT 맵.
     *
     * @param displayTargets  표출 대상 시설 (PWTF·POINT 중 DWT 자식 보유, 루트 inclusive)
     * @param dwtsByTargetId  표출대상 ID → 그 직속 자식 DWT 목록 (수위 소스)
     */
    public record DownstreamTopology(
            List<Facility> displayTargets,
            Map<String, List<Facility>> dwtsByTargetId) {
    }
}
