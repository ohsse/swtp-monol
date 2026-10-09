package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.repository.FacilityRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 시설별 사용량 2번섹션 — 운영시설 + 재귀 하위 시설을 "최근접 운영시설 조상" 으로 귀속시키는 롤업 컴포넌트.
 *
 * <p>운영시설({@link com.mo.swtp.facility.domain.enumtype.FacilityGroup#OPERATION}) 각각을 루트로, 그
 * 재귀 하위({@code parent_facility_id} self-FK) 전체를 멀티 소스 BFS 로 수집하면서 각 시설을 가장 가까운
 * 운영시설 조상으로 매핑한다. 산출 맵 {@code facilityId → 운영루트 facilityId} 는 태그→루트 매핑의 재료가 되어,
 * 운영시설의 전력 사용량 합산 대상(자신 + 하위 시설의 모든 계측기) 을 결정한다 (시설별사용량-2번섹션 PLAN1
 * §확정된 설계 결정 2).</p>
 *
 * <p><strong>최근접 운영 조상 귀속</strong> — 운영시설 A 의 하위에 또 다른 운영시설 B 가 있으면, B 와 B 의
 * 하위는 B 에 귀속된다 (A 중복 합산 방지). 구현상 모든 운영시설을 초기 visited 집합에 넣으므로, A 에서 BFS
 * 하다 B 를 자식으로 만나면 이미 방문 처리되어 가지가 종단되고, B 는 자신의 루트로 별도 처리된다. 운영시설이
 * 아닌 시설은 BFS 경로상 부모가 전달한 루트(= 가장 가까운 운영시설 조상) 를 상속한다.</p>
 *
 * <p>구현은 {@link FacilityDownstreamTreeResolver#resolve} 의 앱 레벨 BFS (레벨당 SQL 1회 —
 * {@link FacilityRepository#findByParentFacilityIdInAndUseYn}, visited-set 순환 방어 + {@link #MAX_DEPTH}
 * 백스톱) 패턴을 미러링한다. 단일 루트 하향 수집과 달리 멀티 루트 + 운영시설 경계 종단 로직이 추가되어
 * 동형 복제로 신규 작성한다 (사이클 간 자산 자동 원용 금지 정합, 시설별사용량-2번섹션 PLAN1 §구현 메모).</p>
 */
@Component
@RequiredArgsConstructor
public class FacilityOperatingRollupResolver {

    /** 재귀 하위 BFS 최대 깊이 — 순환·과도 깊이 방어 백스톱 (visited-set 이 1차 방어). */
    static final int MAX_DEPTH = 10;

    private final FacilityRepository facilityRepository;

    /**
     * 운영시설 루트 목록을 받아 각 시설(루트 + 재귀 하위) 을 최근접 운영시설 조상으로 귀속시킨 맵을 반환한다.
     *
     * <p>반환 맵의 key 집합({@code keySet()}) 이 전력 사용량 합산 대상 시설 전체이며, value 가 그 시설이 귀속될
     * 운영시설 루트 ID 다. 운영시설 루트는 자기 자신을 value 로 가진다.</p>
     *
     * @param operatingRoots 운영시설 루트 목록 (호출 전 활성·운영시설 종류 검증 완료 전제)
     * @return {@code facilityId → 운영루트 facilityId} 매핑 (빈 입력 시 빈 맵)
     */
    public Map<String, String> resolveRootByFacility(List<Facility> operatingRoots) {
        Map<String, String> facilityToRoot = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> currentLevel = new LinkedHashMap<>();

        for (Facility root : operatingRoots) {
            if (visited.add(root.getFacilityId())) {
                facilityToRoot.put(root.getFacilityId(), root.getFacilityId());
                currentLevel.put(root.getFacilityId(), root.getFacilityId());
            }
        }

        int depth = 0;
        while (!currentLevel.isEmpty() && depth < MAX_DEPTH) {
            List<String> parentIds = new ArrayList<>(currentLevel.keySet());
            List<Facility> children = facilityRepository.findByParentFacilityIdInAndUseYn(parentIds, YnType.Y);
            Map<String, String> nextLevel = new LinkedHashMap<>();
            for (Facility child : children) {
                if (!visited.add(child.getFacilityId())) {
                    continue;
                }
                String rootId = currentLevel.get(child.getParentFacilityId());
                if (rootId == null) {
                    continue;
                }
                facilityToRoot.put(child.getFacilityId(), rootId);
                nextLevel.put(child.getFacilityId(), rootId);
            }
            currentLevel = nextLevel;
            depth++;
        }
        return facilityToRoot;
    }
}
