package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.facility.service.FacilityDownstreamTreeResolver.DownstreamTopology;
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
 * {@link FacilityDownstreamTreeResolver} 단위 테스트 — 운전현황분석 8번 섹션 재귀 하위 도출.
 *
 * <p>운전현황분석-8번섹션 PLAN1 §성공 기준 시나리오:
 * <ol>
 *   <li>다단계 트리(루트→PWTF→DWT / 루트→중간RSV→POINT→DWT) 표출대상·수위소스 분류</li>
 *   <li>루트 inclusive — 루트가 PWTF + DWT 자식 보유 시 표출대상 포함</li>
 *   <li>DWT 자식 보유 필터 — DWT 미보유 PWTF 제외</li>
 *   <li>DWT 직속 parent 비-PWTF/POINT 제외</li>
 *   <li>순환·MAX_DEPTH 방어 — visited-set 으로 무한 루프 차단</li>
 * </ol>
 *
 * <p>BFS 는 레벨당 {@link FacilityRepository#findByParentFacilityIdInAndUseYn} 1회 호출 — mock 은 부모 ID →
 * 자식 목록 맵을 willAnswer 로 응답한다 (레벨 호출 순서·ID 순서 비의존).</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityDownstreamTreeResolverTest {

    @Mock
    private FacilityRepository facilityRepository;

    @InjectMocks
    private FacilityDownstreamTreeResolver facilityDownstreamTreeResolver;

    @Test
    void 다단계_트리_표출대상_수위소스_분류() {
        // R(PRSF) ├ P1(PWTF) → D1(DWT)   └ M1(RSV) → PT1(POINT) → D2(DWT)
        Facility root = mockFacility("R", "루트가압장", FacilityType.PRSF, null);
        Facility p1 = mockFacility("P1", "1정수지", FacilityType.PWTF, "R");
        Facility m1 = mockFacility("M1", "중간저수지", FacilityType.RSV, "R");
        Facility pt1 = mockFacility("PT1", "분기점1", FacilityType.POINT, "M1");
        Facility d1 = mockFacility("D1", "1배수지", FacilityType.DWT, "P1");
        Facility d2 = mockFacility("D2", "2배수지", FacilityType.DWT, "PT1");
        stubChildren(Map.of(
                "R", List.of(p1, m1),
                "P1", List.of(d1),
                "M1", List.of(pt1),
                "PT1", List.of(d2)));

        DownstreamTopology topology = facilityDownstreamTreeResolver.resolve(root);

        // 표출대상 = PWTF·POINT 중 DWT 자식 보유 → P1, PT1 (M1=RSV·R=PRSF 제외)
        assertThat(topology.displayTargets())
                .extracting(Facility::getFacilityId)
                .containsExactlyInAnyOrder("P1", "PT1");
        assertThat(topology.dwtsByTargetId().get("P1"))
                .extracting(Facility::getFacilityId).containsExactly("D1");
        assertThat(topology.dwtsByTargetId().get("PT1"))
                .extracting(Facility::getFacilityId).containsExactly("D2");
    }

    @Test
    void 루트_inclusive_표출대상_포함() {
        // R(PWTF, 루트) → D1(DWT) : 루트 자신이 DWT 자식을 가진 PWTF → 표출대상
        Facility root = mockFacility("R", "루트정수지", FacilityType.PWTF, null);
        Facility d1 = mockFacility("D1", "1배수지", FacilityType.DWT, "R");
        stubChildren(Map.of("R", List.of(d1)));

        DownstreamTopology topology = facilityDownstreamTreeResolver.resolve(root);

        assertThat(topology.displayTargets())
                .extracting(Facility::getFacilityId).containsExactly("R");
        assertThat(topology.dwtsByTargetId().get("R"))
                .extracting(Facility::getFacilityId).containsExactly("D1");
    }

    @Test
    void DWT_미보유_PWTF_표출대상_제외() {
        // R(PRSF) ├ P1(PWTF) → D1(DWT)   └ P2(PWTF) → (DWT 자식 없음)
        Facility root = mockFacility("R", "루트가압장", FacilityType.PRSF, null);
        Facility p1 = mockFacility("P1", "1정수지", FacilityType.PWTF, "R");
        Facility p2 = mockFacility("P2", "2정수지", FacilityType.PWTF, "R");
        Facility d1 = mockFacility("D1", "1배수지", FacilityType.DWT, "P1");
        stubChildren(Map.of(
                "R", List.of(p1, p2),
                "P1", List.of(d1)));

        DownstreamTopology topology = facilityDownstreamTreeResolver.resolve(root);

        // P2 는 DWT 자식이 없으므로 표출대상 제외
        assertThat(topology.displayTargets())
                .extracting(Facility::getFacilityId).containsExactly("P1");
        assertThat(topology.dwtsByTargetId()).containsOnlyKeys("P1");
    }

    @Test
    void DWT_직속_parent_비_PWTF_POINT_제외() {
        // R(PRSF) → M1(RSV) → D1(DWT) : D1 의 직속 parent 가 RSV → 표출 제외
        Facility root = mockFacility("R", "루트가압장", FacilityType.PRSF, null);
        Facility m1 = mockFacility("M1", "중간저수지", FacilityType.RSV, "R");
        Facility d1 = mockFacility("D1", "1배수지", FacilityType.DWT, "M1");
        stubChildren(Map.of(
                "R", List.of(m1),
                "M1", List.of(d1)));

        DownstreamTopology topology = facilityDownstreamTreeResolver.resolve(root);

        assertThat(topology.displayTargets()).isEmpty();
        assertThat(topology.dwtsByTargetId()).isEmpty();
    }

    @Test
    void 순환_트리_visited_set_으로_무한루프_차단() {
        // A(PWTF, 루트) → B(DWT) → A (B 의 자식이 루트 A 로 되돌아오는 순환)
        Facility a = mockFacility("A", "루트정수지", FacilityType.PWTF, null);
        Facility b = mockFacility("B", "1배수지", FacilityType.DWT, "A");
        Map<String, List<Facility>> graph = new HashMap<>();
        graph.put("A", List.of(b));
        graph.put("B", List.of(a)); // 순환 — A 는 이미 visited → 스킵
        stubChildren(graph);

        DownstreamTopology topology = facilityDownstreamTreeResolver.resolve(a);

        // 무한 루프 없이 종료 + 정상 분류 (A=PWTF, DWT 자식 B 보유 → 표출대상)
        assertThat(topology.displayTargets())
                .extracting(Facility::getFacilityId).containsExactly("A");
        assertThat(topology.dwtsByTargetId().get("A"))
                .extracting(Facility::getFacilityId).containsExactly("B");
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

    private Facility mockFacility(String id, String nm, FacilityType type, String parentId) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(id);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(nm);
        Mockito.lenient().when(facility.getFacilityType()).thenReturn(type);
        Mockito.lenient().when(facility.getParentFacilityId()).thenReturn(parentId);
        return facility;
    }
}
