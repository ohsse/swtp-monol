package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityPowerInstrumentDto;
import com.mo.swtp.facility.dto.FacilityPowerInstrumentDto.PowerTagDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityPowerInstrumentService} 단위 테스트 — 시설별 사용량 3번섹션.
 *
 * <p>리포지토리(시설·계측기·태그) 는 mock 으로 격리하고, Service 의 오케스트레이션(재귀 하위 BFS·전력태그 멤버십
 * 필터·계측기↔시설 매핑·DTO 조립·정렬) 을 검증한다. BFS 레벨 조회({@link FacilityRepository#findByParentFacilityIdInAndUseYn})
 * 는 부모 ID → 자식 목록 맵을 willAnswer 로 응답한다 ({@link FacilityDownstreamTreeResolverTest} 동형).</p>
 *
 * <p>시나리오 (PLAN1 §성공 기준):</p>
 * <ol>
 *   <li>미존재 루트 → FACILITY_NOT_FOUND</li>
 *   <li>비활성 루트 → FACILITY_NOT_FOUND</li>
 *   <li>재귀 하위 BFS — 루트 inclusive + 2단·3단 하위 전체 계측기 수집</li>
 *   <li>비전력 태그 보유 계측기 제외 + PWI 단독·PWQ 단독 포함</li>
 *   <li>PWI+PWQ 동시 보유 → 1행 + tags 2건</li>
 *   <li>전력태그 보유 계측기 0건 → 빈 목록</li>
 *   <li>하위 시설 계측기 facilityNm 매핑 정확성</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class FacilityPowerInstrumentServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private FacilityPowerInstrumentService facilityPowerInstrumentService;

    @Test
    void 미존재_루트시설은_FACILITY_NOT_FOUND() {
        given(facilityRepository.findById("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityPowerInstrumentService.findPowerInstruments("UNKNOWN"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 비활성_루트시설은_FACILITY_NOT_FOUND() {
        Facility inactive = mockFacility("ROOT", "생활송수동", 1, YnType.N);
        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityPowerInstrumentService.findPowerInstruments("ROOT"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 재귀_하위_BFS_로_루트_inclusive_전체_계측기_수집() {
        // ROOT ├ C1 → G1 (3단)   └ C2 — 각 시설에 전력 계측기 1대씩
        Facility root = mockFacility("ROOT", "생활송수동", 1, YnType.Y);
        Facility c1 = mockFacility("C1", "1송수동", 1, YnType.Y);
        Facility c2 = mockFacility("C2", "2송수동", 2, YnType.Y);
        Facility g1 = mockFacility("G1", "1송수지", 1, YnType.Y);
        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        stubChildren(Map.of(
                "ROOT", List.of(c1, c2),
                "C1", List.of(g1)));

        Instrument iRoot = mockInstrument("I-ROOT", "루트펌프", "ROOT", EquipType.PUMP, 1);
        Instrument iC1 = mockInstrument("I-C1", "C1전력계", "C1", EquipType.ELCMTR, 1);
        Instrument iC2 = mockInstrument("I-C2", "C2펌프", "C2", EquipType.PUMP, 1);
        Instrument iG1 = mockInstrument("I-G1", "G1펌프", "G1", EquipType.PUMP, 1);
        Tag tRoot = mockTag("T-ROOT", iRoot, TagMeasurementType.PWI);
        Tag tC1 = mockTag("T-C1", iC1, TagMeasurementType.PWQ);
        Tag tC2 = mockTag("T-C2", iC2, TagMeasurementType.PWI);
        Tag tG1 = mockTag("T-G1", iG1, TagMeasurementType.PWI);
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(iRoot, iC1, iC2, iG1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(tRoot, tC1, tC2, tG1));

        List<FacilityPowerInstrumentDto> result =
                facilityPowerInstrumentService.findPowerInstruments("ROOT");

        // 루트 inclusive + 2단(C1·C2)·3단(G1) 하위 전체 포함
        assertThat(result).extracting(FacilityPowerInstrumentDto::getInstrumentId)
                .containsExactlyInAnyOrder("I-ROOT", "I-C1", "I-C2", "I-G1");
    }

    @Test
    void 비전력_태그_보유_계측기는_제외되고_PWI_PWQ_단독은_포함() {
        Facility root = mockFacility("ROOT", "생활송수동", 1, YnType.Y);
        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        stubChildren(Map.of());   // 하위 시설 없음

        Instrument iPwi = mockInstrument("I-PWI", "순시전력계", "ROOT", EquipType.ELCMTR, 1);
        Instrument iPwq = mockInstrument("I-PWQ", "적산전력계", "ROOT", EquipType.ELCMTR, 2);
        Instrument iFlow = mockInstrument("I-FRI", "유량계", "ROOT", EquipType.FLWMTR, 3);
        Tag tPwi = mockTag("T-PWI", iPwi, TagMeasurementType.PWI);
        Tag tPwq = mockTag("T-PWQ", iPwq, TagMeasurementType.PWQ);
        Tag tFri = mockTag("T-FRI", iFlow, TagMeasurementType.FRI);   // 비전력 → 제외
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(iPwi, iPwq, iFlow));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(tPwi, tPwq, tFri));

        List<FacilityPowerInstrumentDto> result =
                facilityPowerInstrumentService.findPowerInstruments("ROOT");

        // FRI 만 보유한 유량계 제외, PWI·PWQ 단독 보유 계측기 포함 (계측기 dispOrd 1 < 2 정렬)
        assertThat(result).extracting(FacilityPowerInstrumentDto::getInstrumentId)
                .containsExactly("I-PWI", "I-PWQ");
    }

    @Test
    void PWI_PWQ_동시보유_계측기는_1행_2태그() {
        Facility root = mockFacility("ROOT", "생활송수동", 1, YnType.Y);
        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        stubChildren(Map.of());

        Instrument iBoth = mockInstrument("I-BOTH", "복합전력계", "ROOT", EquipType.ELCMTR, 1);
        Tag tPwi = mockTag("T-PWI", iBoth, TagMeasurementType.PWI);
        Tag tPwq = mockTag("T-PWQ", iBoth, TagMeasurementType.PWQ);
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(iBoth));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(tPwi, tPwq));

        List<FacilityPowerInstrumentDto> result =
                facilityPowerInstrumentService.findPowerInstruments("ROOT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTags())
                .extracting(PowerTagDto::getTagSeCd)
                .containsExactlyInAnyOrder(TagMeasurementType.PWI, TagMeasurementType.PWQ);
    }

    @Test
    void 전력태그_보유_계측기_0건이면_빈_목록() {
        Facility root = mockFacility("ROOT", "생활송수동", 1, YnType.Y);
        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        stubChildren(Map.of());

        Instrument iFlow = mockInstrument("I-FRI", "유량계", "ROOT", EquipType.FLWMTR, 1);
        Tag tFri = mockTag("T-FRI", iFlow, TagMeasurementType.FRI);
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(iFlow));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(tFri));

        List<FacilityPowerInstrumentDto> result =
                facilityPowerInstrumentService.findPowerInstruments("ROOT");

        assertThat(result).isEmpty();
    }

    @Test
    void 하위시설_계측기의_facilityNm_이_정확히_매핑된다() {
        Facility root = mockFacility("ROOT", "생활송수동", 1, YnType.Y);
        Facility child = mockFacility("C1", "1송수동", 1, YnType.Y);
        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        stubChildren(Map.of("ROOT", List.of(child)));

        Instrument iChild = mockInstrument("I-C1", "C1펌프", "C1", EquipType.PUMP, 1);
        Tag tChild = mockTag("T-C1", iChild, TagMeasurementType.PWI);
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(iChild));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(tChild));

        List<FacilityPowerInstrumentDto> result =
                facilityPowerInstrumentService.findPowerInstruments("ROOT");

        assertThat(result).hasSize(1);
        FacilityPowerInstrumentDto dto = result.get(0);
        assertThat(dto.getFacilityId()).isEqualTo("C1");
        assertThat(dto.getFacilityNm()).isEqualTo("1송수동");   // 루트가 아닌 하위 시설명 정확 매핑
        assertThat(dto.getEquipTypeCd()).isEqualTo(EquipType.PUMP);
        assertThat(dto.getTags()).extracting(PowerTagDto::getTagSrlNo).containsExactly("T-C1");
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

    private Facility mockFacility(String id, String nm, int dispOrd, YnType useYn) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(id);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(nm);
        Mockito.lenient().when(facility.getDispOrd()).thenReturn(dispOrd);
        Mockito.lenient().when(facility.getUseYn()).thenReturn(useYn);
        return facility;
    }

    private Instrument mockInstrument(
            String instrumentId, String instrumentNm, String facilityId, EquipType equipType, int dispOrd) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Instrument instrument = Mockito.mock(Instrument.class);
        Mockito.lenient().when(instrument.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(instrument.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(instrument.getFacility()).thenReturn(facility);
        Mockito.lenient().when(instrument.getEquipType()).thenReturn(equipType);
        Mockito.lenient().when(instrument.getDispOrd()).thenReturn(dispOrd);
        return instrument;
    }

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }
}
