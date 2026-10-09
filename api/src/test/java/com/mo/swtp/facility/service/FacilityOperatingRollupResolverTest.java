package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.repository.FacilityRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityOperatingRollupResolver} 단위 테스트 — 시설별 사용량 2번섹션 롤업 귀속.
 *
 * <p>시설별사용량-2번섹션 PLAN1 §성공 기준 — 최근접 운영시설 조상 귀속 시나리오:
 * <ol>
 *   <li>단일 운영시설 + 하위(STORAGE/NETWORK) 전부 운영시설로 귀속</li>
 *   <li>운영시설 중첩 시 최근접 조상 귀속 — 상위 운영시설 중복 합산 없음</li>
 *   <li>순환 self-FK 방어 — visited-set 으로 무한 루프 차단</li>
 *   <li>depth 백스톱 — MAX_DEPTH 초과 깊이 종단</li>
 * </ol>
 *
 * <p>리졸버는 {@code facility_type} 을 읽지 않는다 — 운영시설 판별은 입력 {@code operatingRoots} 목록에
 * 전적으로 의존하며 (Service 가 OPERATION 종류만 필터해 전달), BFS 는 레벨당
 * {@link FacilityRepository#findByParentFacilityIdInAndUseYn} 1회 호출이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityOperatingRollupResolverTest {

    @Mock
    private FacilityRepository facilityRepository;

    @InjectMocks
    private FacilityOperatingRollupResolver facilityOperatingRollupResolver;

    @Test
    void 단일_운영시설_하위_STORAGE_NETWORK_전부_운영시설로_귀속() {
        // A(운영) → X(STORAGE) → Z(STORAGE), A → B(NETWORK)
        Facility a = mockFacility("A", null);
        Facility x = mockFacility("X", "A");
        Facility z = mockFacility("Z", "X");
        Facility b = mockFacility("B", "A");
        stubChildren(Map.of(
                "A", List.of(x, b),
                "X", List.of(z)));

        Map<String, String> rollup = facilityOperatingRollupResolver.resolveRootByFacility(List.of(a));

        // 루트 + 모든 하위가 A 로 귀속
        assertThat(rollup).containsOnlyKeys("A", "X", "Z", "B");
        assertThat(rollup).containsEntry("A", "A");
        assertThat(rollup).containsEntry("X", "A");
        assertThat(rollup).containsEntry("Z", "A");
        assertThat(rollup).containsEntry("B", "A");
    }

    @Test
    void 운영시설_중첩_시_최근접_조상_귀속_상위_중복_없음() {
        // A(운영) → X(STORAGE) → B(운영) → Y(STORAGE)
        // 기대: X→A, B→B(자기 자신), Y→B (A 가 아님 — 최근접 운영 조상 B)
        Facility a = mockFacility("A", null);
        Facility x = mockFacility("X", "A");
        Facility b = mockFacility("B", "X");
        Facility y = mockFacility("Y", "B");
        stubChildren(Map.of(
                "A", List.of(x),
                "X", List.of(b),
                "B", List.of(y)));

        // Service 는 모든 운영시설을 전달 — A, B 둘 다 루트
        Map<String, String> rollup =
                facilityOperatingRollupResolver.resolveRootByFacility(List.of(a, b));

        assertThat(rollup).containsEntry("A", "A");
        assertThat(rollup).containsEntry("X", "A");
        assertThat(rollup).containsEntry("B", "B");   // 운영시설 B 는 자기 자신으로 귀속 (A 중복 없음)
        assertThat(rollup).containsEntry("Y", "B");   // Y 는 최근접 운영 조상 B 로 귀속
        assertThat(rollup).containsOnlyKeys("A", "X", "B", "Y");
    }

    @Test
    void 순환_self_FK_는_visited_set_으로_무한루프_차단() {
        // A(운영) → C(STORAGE) → A (C 의 자식이 루트 A 로 되돌아오는 순환)
        Facility a = mockFacility("A", null);
        Facility c = mockFacility("C", "A");
        Map<String, List<Facility>> graph = new HashMap<>();
        graph.put("A", List.of(c));
        graph.put("C", List.of(a)); // 순환 — A 는 이미 visited → 스킵
        stubChildren(graph);

        Map<String, String> rollup = facilityOperatingRollupResolver.resolveRootByFacility(List.of(a));

        // 무한 루프 없이 종료 + A, C 만 귀속
        assertThat(rollup).containsOnlyKeys("A", "C");
        assertThat(rollup).containsEntry("A", "A");
        assertThat(rollup).containsEntry("C", "A");
    }

    @Test
    void MAX_DEPTH_초과_깊이는_종단된다() {
        // A(운영) → n1 → n2 → ... → n12 선형 체인 (깊이 12)
        Facility a = mockFacility("A", null);
        Map<String, List<Facility>> graph = new HashMap<>();
        String parentId = "A";
        for (int d = 1; d <= 12; d++) {
            String id = "n" + d;
            Facility node = mockFacility(id, parentId);
            graph.put(parentId, List.of(node));
            parentId = id;
        }
        stubChildren(graph);

        Map<String, String> rollup = facilityOperatingRollupResolver.resolveRootByFacility(List.of(a));

        // MAX_DEPTH=10 — 깊이 10(n10)까지 수집, 깊이 11·12(n11·n12)는 종단
        assertThat(rollup).containsKey("n10");
        assertThat(rollup).doesNotContainKey("n11");
        assertThat(rollup).doesNotContainKey("n12");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    /** 부모 ID → 자식 목록 맵으로 BFS 레벨 조회를 stub (레벨·ID 순서 비의존). */
    private void stubChildren(Map<String, List<Facility>> childrenByParent) {
        given(facilityRepository.findByParentFacilityIdInAndUseYn(anyList(), any()))
                .willAnswer(invocation -> {
                    List<String> parentIds = invocation.getArgument(0);
                    List<Facility> result = new ArrayList<>();
                    for (String parentId : parentIds) {
                        result.addAll(childrenByParent.getOrDefault(parentId, List.of()));
                    }
                    return result;
                });
    }

    private Facility mockFacility(String id, String parentId) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(id);
        Mockito.lenient().when(facility.getParentFacilityId()).thenReturn(parentId);
        return facility;
    }
}
