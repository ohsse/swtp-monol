package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageDto;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageDto.InstrumentEnergyUsageItem;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageSearchDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityInstrumentEnergyUsageService} 단위 테스트.
 *
 * <p>설비별사용량-5,6번섹션 PLAN1 §성공 기준 — 기간 무효 3종(null·역전·397일)→INVALID_SEARCH_PERIOD /
 * 미존재·비활성 시설 2종→FACILITY_NOT_FOUND / 재귀 하위 BFS 다단계 수집 / 2설비×다PWQ태그 instrumentId 합산 /
 * 음수·null 차분 제외 / 분포율 elceg/total×100 scale1 HALF_UP / total=0→전 ratio 0.0 / PWQ보유·전력량0 설비
 * 포함 / 계측기·PWQ 0건→빈 래퍼 / 시설dispOrd→계측기dispOrd→계측기명 정렬.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityInstrumentEnergyUsageServiceTest {

    private static final String ROOT_ID = "F-ROOT";
    private static final LocalDateTime D1 = LocalDateTime.of(2024, 7, 1, 0, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2024, 7, 2, 0, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private FacilityInstrumentEnergyUsageService facilityInstrumentEnergyUsageService;

    @Test
    void 시작일이_null이면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        FacilityInstrumentEnergyUsageSearchDto search = new FacilityInstrumentEnergyUsageSearchDto();
        search.setFromDt(null);
        search.setToDt(LocalDate.of(2024, 7, 9));

        assertThatThrownBy(() ->
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void from이_to보다_늦으면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        FacilityInstrumentEnergyUsageSearchDto search = new FacilityInstrumentEnergyUsageSearchDto();
        search.setFromDt(LocalDate.of(2024, 7, 10));
        search.setToDt(LocalDate.of(2024, 7, 1));

        assertThatThrownBy(() ->
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void 조회기간이_396일을_초과하면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        FacilityInstrumentEnergyUsageSearchDto search = new FacilityInstrumentEnergyUsageSearchDto();
        search.setFromDt(LocalDate.of(2024, 1, 1));
        search.setToDt(LocalDate.of(2025, 2, 1));   // 397일 — 396일 상한 초과

        assertThatThrownBy(() ->
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY_NOT_FOUND_예외가_발생한다() {
        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch()))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 비활성_시설이면_FACILITY_NOT_FOUND_예외가_발생한다() {
        Facility inactive = mockFacility(ROOT_ID, "루트", YnType.N, 1);
        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() ->
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch()))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")   // ArgumentCaptor.forClass(List.class) 제네릭 캡처 경고 한정 억제
    void 재귀_하위_BFS로_손자_시설의_계측기까지_집계한다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Facility child = mockFacility("F-CHILD", "자식", YnType.Y, 1);
        Facility grandchild = mockFacility("F-GC", "손자", YnType.Y, 1);
        Instrument iGc = mockInstrument("I-GC", "손자전력계", EquipType.ELCMTR, grandchild, 1);
        Tag pwq = mockTag("T-GC", TagMeasurementType.PWQ, iGc);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(facilityRepository.findByParentFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(child))        // 레벨0: 루트의 자식
                .willReturn(List.of(grandchild))   // 레벨1: 자식의 손자
                .willReturn(List.of());            // 레벨2: 손자의 자식 없음
        ArgumentCaptor<List<String>> facilityIdsCaptor = ArgumentCaptor.forClass(List.class);
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(facilityIdsCaptor.capture(), eq(YnType.Y)))
                .willReturn(List.of(iGc));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(bucket("T-GC", D1, "50")));

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(facilityIdsCaptor.getValue()).contains(ROOT_ID, "F-CHILD", "F-GC");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getInstrumentId()).isEqualTo("I-GC");
        assertThat(result.getItems().get(0).getElceg()).isEqualByComparingTo("50");
        assertThat(result.getItems().get(0).getRatio()).isEqualByComparingTo("100.0");
        assertThat(result.getTotalElceg()).isEqualByComparingTo("50");
    }

    @Test
    void 한_계측기의_다중_PWQ_태그와_다설비_버킷이_계측기별로_합산된다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "1호기전력계", EquipType.ELCMTR, root, 1);
        Instrument i2 = mockInstrument("I-2", "2호기전력계", EquipType.ELCMTR, root, 2);
        Tag t1a = mockTag("T-1A", TagMeasurementType.PWQ, i1);
        Tag t1b = mockTag("T-1B", TagMeasurementType.PWQ, i1);
        Tag t2 = mockTag("T-2", TagMeasurementType.PWQ, i2);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1a, t1b, t2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-1A", D1, "100"),
                        bucket("T-1B", D1, "50"),    // i1 다태그 합산
                        bucket("T-1A", D2, "30"),    // i1 다버킷 합산 → 180
                        bucket("T-2", D1, "200")));

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getInstrumentId()).isEqualTo("I-1");
        assertThat(result.getItems().get(0).getElceg()).isEqualByComparingTo("180");
        assertThat(result.getItems().get(1).getInstrumentId()).isEqualTo("I-2");
        assertThat(result.getItems().get(1).getElceg()).isEqualByComparingTo("200");
        assertThat(result.getTotalElceg()).isEqualByComparingTo("380");
    }

    @Test
    void 음수_및_null_차분_버킷은_제외한다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "1호기전력계", EquipType.ELCMTR, root, 1);
        Tag t1 = mockTag("T-1", TagMeasurementType.PWQ, i1);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-1", D1, "100"),
                        bucket("T-1", D2, "-5"),       // 적산 리셋·롤오버 — 제외
                        nullBucket("T-1", D2)));        // null 차분 — 제외

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getElceg()).isEqualByComparingTo("100");
        assertThat(result.getTotalElceg()).isEqualByComparingTo("100");
    }

    @Test
    void 분포율은_elceg를_total로_나눈_백분율_소수첫째자리_반올림이다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "1호기전력계", EquipType.ELCMTR, root, 1);
        Instrument i2 = mockInstrument("I-2", "2호기전력계", EquipType.ELCMTR, root, 2);
        Tag t1 = mockTag("T-1", TagMeasurementType.PWQ, i1);
        Tag t2 = mockTag("T-2", TagMeasurementType.PWQ, i2);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1, t2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-1", D1, "100"),
                        bucket("T-2", D1, "200")));   // total 300 → 33.3% / 66.7%

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getTotalElceg()).isEqualByComparingTo("300");
        assertThat(result.getItems().get(0).getRatio()).isEqualByComparingTo("33.3");
        assertThat(result.getItems().get(1).getRatio()).isEqualByComparingTo("66.7");
    }

    @Test
    void 전체_전력량이_0이면_모든_분포율이_0이다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "1호기전력계", EquipType.ELCMTR, root, 1);
        Instrument i2 = mockInstrument("I-2", "2호기전력계", EquipType.ELCMTR, root, 2);
        Tag t1 = mockTag("T-1", TagMeasurementType.PWQ, i1);
        Tag t2 = mockTag("T-2", TagMeasurementType.PWQ, i2);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1, t2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of());   // 데이터 0건 → 전 설비 전력량 0

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotalElceg()).isEqualByComparingTo("0");
        assertThat(result.getItems().get(0).getElceg()).isEqualByComparingTo("0");
        assertThat(result.getItems().get(0).getRatio()).isEqualByComparingTo("0.0");
        assertThat(result.getItems().get(1).getRatio()).isEqualByComparingTo("0.0");
    }

    @Test
    void PWQ보유_설비는_전력량이_0이어도_items에_포함된다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "1호기전력계", EquipType.ELCMTR, root, 1);
        Instrument i2 = mockInstrument("I-2", "2호기전력계", EquipType.ELCMTR, root, 2);
        Tag t1 = mockTag("T-1", TagMeasurementType.PWQ, i1);
        Tag t2 = mockTag("T-2", TagMeasurementType.PWQ, i2);   // 버킷 데이터 없음

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1, t2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(bucket("T-1", D1, "100")));   // i1 만 데이터

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getItems()).hasSize(2);
        InstrumentEnergyUsageItem zero = result.getItems().get(1);
        assertThat(zero.getInstrumentId()).isEqualTo("I-2");
        assertThat(zero.getElceg()).isEqualByComparingTo("0");
        assertThat(zero.getRatio()).isEqualByComparingTo("0.0");
        assertThat(result.getItems().get(0).getRatio()).isEqualByComparingTo("100.0");
    }

    @Test
    void 하위_트리에_계측기가_없으면_빈_래퍼를_반환한다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of());

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getUnit()).isEqualTo("kWh");
        assertThat(result.getTotalElceg()).isEqualByComparingTo("0");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void PWQ_태그가_없으면_빈_래퍼를_반환한다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "유량계", EquipType.FLWMTR, root, 1);
        Tag pwi = mockTag("T-PWI", TagMeasurementType.PWI, i1);   // 순시전력만 보유

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwi));

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalElceg()).isEqualByComparingTo("0");
    }

    @Test
    void 항목은_시설dispOrd_계측기dispOrd_계측기명_순으로_정렬된다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument iC = mockInstrument("I-C", "C전력계", EquipType.ELCMTR, root, 2);
        Instrument iA = mockInstrument("I-A", "A전력계", EquipType.ELCMTR, root, 1);
        Instrument iB = mockInstrument("I-B", "B전력계", EquipType.ELCMTR, root, 1);   // iA 와 동일 dispOrd → 이름 정렬
        Tag tC = mockTag("T-C", TagMeasurementType.PWQ, iC);
        Tag tA = mockTag("T-A", TagMeasurementType.PWQ, iA);
        Tag tB = mockTag("T-B", TagMeasurementType.PWQ, iB);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(iC, iA, iB));   // 의도적 비정렬 입력
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(tC, tA, tB));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-A", D1, "10"),
                        bucket("T-B", D1, "20"),
                        bucket("T-C", D1, "30")));

        FacilityInstrumentEnergyUsageDto result =
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(ROOT_ID, validSearch());

        assertThat(result.getItems()).extracting(InstrumentEnergyUsageItem::getInstrumentId)
                .containsExactly("I-A", "I-B", "I-C");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private FacilityInstrumentEnergyUsageSearchDto validSearch() {
        FacilityInstrumentEnergyUsageSearchDto search = new FacilityInstrumentEnergyUsageSearchDto();
        search.setFromDt(LocalDate.of(2024, 7, 1));
        search.setToDt(LocalDate.of(2024, 7, 9));
        return search;
    }

    private Facility mockFacility(String facilityId, String facilityNm, YnType useYn, int dispOrd) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(facilityNm);
        Mockito.lenient().when(facility.getUseYn()).thenReturn(useYn);
        Mockito.lenient().when(facility.getDispOrd()).thenReturn(dispOrd);
        return facility;
    }

    private Instrument mockInstrument(
            String instrumentId, String instrumentNm, EquipType equipType, Facility facility, int dispOrd) {
        Instrument instrument = Mockito.mock(Instrument.class);
        Mockito.lenient().when(instrument.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(instrument.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(instrument.getEquipType()).thenReturn(equipType);
        Mockito.lenient().when(instrument.getFacility()).thenReturn(facility);
        Mockito.lenient().when(instrument.getDispOrd()).thenReturn(dispOrd);
        return instrument;
    }

    private Tag mockTag(String tagSrlNo, TagMeasurementType tagSeCd, Instrument instrument) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        return tag;
    }

    private RawDataBucketDto bucket(String tagSrlNo, LocalDateTime baseDtm, String aggrVal) {
        return new RawDataBucketDto(tagSrlNo, baseDtm, new BigDecimal(aggrVal));
    }

    private RawDataBucketDto nullBucket(String tagSrlNo, LocalDateTime baseDtm) {
        return new RawDataBucketDto(tagSrlNo, baseDtm, null);
    }
}
