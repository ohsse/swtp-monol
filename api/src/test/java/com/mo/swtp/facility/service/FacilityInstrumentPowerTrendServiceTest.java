package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendDto;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendDto.InstrumentPowerSeries;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendDto.PowerTrendPoint;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendSearchDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataInstrumentSumDto;
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
 * {@link FacilityInstrumentPowerTrendService} 단위 테스트.
 *
 * <p>설비별사용량-7번섹션 PLAN1 §성공 기준 — 기간 무효 3종(null·역전·32일)→INVALID_SEARCH_PERIOD /
 * 미존재·비활성 시설 2종→FACILITY_NOT_FOUND / 재귀 하위 BFS 다단계 수집 → 설비별 분 시계열 조립(acqDtm 오름차순) /
 * 다중 PWI 태그 동일설비 평행배열(tags·instruments 캡처)·단일 시리즈 / PWI 보유·데이터 0 설비 빈 points 시리즈 포함 /
 * 계측기 0건·PWI 0건 → 빈 series 래퍼 / 시설dispOrd→계측기dispOrd→계측기명 정렬.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityInstrumentPowerTrendServiceTest {

    private static final String ROOT_ID = "F-ROOT";
    private static final LocalDateTime M1 = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime M2 = LocalDateTime.of(2026, 1, 1, 0, 1);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private FacilityInstrumentPowerTrendService facilityInstrumentPowerTrendService;

    @Test
    void 시작일이_null이면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        FacilityInstrumentPowerTrendSearchDto search = new FacilityInstrumentPowerTrendSearchDto();
        search.setFromDt(null);
        search.setToDt(LocalDate.of(2026, 1, 9));

        assertThatThrownBy(() ->
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void from이_to보다_늦으면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        FacilityInstrumentPowerTrendSearchDto search = new FacilityInstrumentPowerTrendSearchDto();
        search.setFromDt(LocalDate.of(2026, 1, 10));
        search.setToDt(LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() ->
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void 조회기간이_31일을_초과하면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        FacilityInstrumentPowerTrendSearchDto search = new FacilityInstrumentPowerTrendSearchDto();
        search.setFromDt(LocalDate.of(2026, 1, 1));
        search.setToDt(LocalDate.of(2026, 2, 2));   // 32일 — 31일 상한 초과

        assertThatThrownBy(() ->
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY_NOT_FOUND_예외가_발생한다() {
        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch()))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 비활성_시설이면_FACILITY_NOT_FOUND_예외가_발생한다() {
        Facility inactive = mockFacility(ROOT_ID, "루트", YnType.N, 1);
        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() ->
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch()))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")   // ArgumentCaptor.forClass(List.class) 제네릭 캡처 경고 한정 억제
    void 재귀_하위_BFS로_손자_설비의_시계열을_acqDtm_오름차순으로_조립한다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Facility child = mockFacility("F-CHILD", "자식", YnType.Y, 1);
        Facility grandchild = mockFacility("F-GC", "손자", YnType.Y, 1);
        Instrument iGc = mockInstrument("I-GC", "손자전력계", EquipType.ELCMTR, grandchild, 1);
        Tag pwi = mockTag("T-GC", TagMeasurementType.PWI, iGc);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(facilityRepository.findByParentFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(child))        // 레벨0: 루트의 자식
                .willReturn(List.of(grandchild))   // 레벨1: 자식의 손자
                .willReturn(List.of());            // 레벨2: 손자의 자식 없음
        ArgumentCaptor<List<String>> facilityIdsCaptor = ArgumentCaptor.forClass(List.class);
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(facilityIdsCaptor.capture(), eq(YnType.Y)))
                .willReturn(List.of(iGc));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwi));
        given(rawDataRepository.findInstrumentMinuteSumElpwr(anyList(), anyList(), any(), any()))
                .willReturn(List.of(instSum("I-GC", M1, "100"), instSum("I-GC", M2, "120")));

        FacilityInstrumentPowerTrendDto result =
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch());

        assertThat(facilityIdsCaptor.getValue()).contains(ROOT_ID, "F-CHILD", "F-GC");
        assertThat(result.getUnit()).isEqualTo("kW");
        assertThat(result.getSeries()).hasSize(1);
        InstrumentPowerSeries series = result.getSeries().get(0);
        assertThat(series.getInstrumentId()).isEqualTo("I-GC");
        assertThat(series.getFacilityNm()).isEqualTo("손자");
        assertThat(series.getPoints()).extracting(PowerTrendPoint::getAcqDtm).containsExactly(M1, M2);
        assertThat(series.getPoints().get(0).getElpwrVal()).isEqualByComparingTo("100");
        assertThat(series.getPoints().get(1).getElpwrVal()).isEqualByComparingTo("120");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 한_설비의_다중_PWI_태그는_평행배열로_전달되어_단일_시리즈가_된다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "1호기전력계", EquipType.ELCMTR, root, 1);
        Tag t1a = mockTag("T-1A", TagMeasurementType.PWI, i1);
        Tag t1b = mockTag("T-1B", TagMeasurementType.PWI, i1);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1a, t1b));
        ArgumentCaptor<List<String>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> instrumentsCaptor = ArgumentCaptor.forClass(List.class);
        given(rawDataRepository.findInstrumentMinuteSumElpwr(
                tagsCaptor.capture(), instrumentsCaptor.capture(), any(), any()))
                .willReturn(List.of(instSum("I-1", M1, "150")));   // SQL 이 동일 acq_dtm 합산한 결과

        FacilityInstrumentPowerTrendDto result =
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch());

        // 태그 2건이 같은 설비 I-1 로 매핑된 평행 배열 — SQL GROUP BY instrument_id, acq_dtm 으로 동시각 합산 위임
        assertThat(tagsCaptor.getValue()).containsExactly("T-1A", "T-1B");
        assertThat(instrumentsCaptor.getValue()).containsExactly("I-1", "I-1");
        assertThat(result.getSeries()).hasSize(1);
        assertThat(result.getSeries().get(0).getInstrumentId()).isEqualTo("I-1");
        assertThat(result.getSeries().get(0).getPoints()).hasSize(1);
        assertThat(result.getSeries().get(0).getPoints().get(0).getElpwrVal()).isEqualByComparingTo("150");
    }

    @Test
    void PWI보유_설비는_데이터가_0이어도_빈_points_시리즈로_포함된다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "1호기전력계", EquipType.ELCMTR, root, 1);
        Instrument i2 = mockInstrument("I-2", "2호기전력계", EquipType.ELCMTR, root, 2);
        Tag t1 = mockTag("T-1", TagMeasurementType.PWI, i1);
        Tag t2 = mockTag("T-2", TagMeasurementType.PWI, i2);   // 시계열 데이터 없음

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1, t2));
        given(rawDataRepository.findInstrumentMinuteSumElpwr(anyList(), anyList(), any(), any()))
                .willReturn(List.of(instSum("I-1", M1, "100")));   // I-1 만 데이터

        FacilityInstrumentPowerTrendDto result =
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch());

        assertThat(result.getSeries()).hasSize(2);
        InstrumentPowerSeries zero = result.getSeries().get(1);
        assertThat(zero.getInstrumentId()).isEqualTo("I-2");
        assertThat(zero.getPoints()).isEmpty();
        assertThat(result.getSeries().get(0).getPoints()).hasSize(1);
    }

    @Test
    void 하위_트리에_계측기가_없으면_빈_series_래퍼를_반환한다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of());

        FacilityInstrumentPowerTrendDto result =
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch());

        assertThat(result.getUnit()).isEqualTo("kW");
        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    void PWI_태그가_없으면_빈_series_래퍼를_반환한다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument i1 = mockInstrument("I-1", "전력량계", EquipType.ELCMTR, root, 1);
        Tag pwq = mockTag("T-PWQ", TagMeasurementType.PWQ, i1);   // 적산전력량만 보유 (순시 아님)

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(i1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq));

        FacilityInstrumentPowerTrendDto result =
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch());

        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    void 시리즈는_시설dispOrd_계측기dispOrd_계측기명_순으로_정렬된다() {
        Facility root = mockFacility(ROOT_ID, "루트", YnType.Y, 1);
        Instrument iC = mockInstrument("I-C", "C전력계", EquipType.ELCMTR, root, 2);
        Instrument iA = mockInstrument("I-A", "A전력계", EquipType.ELCMTR, root, 1);
        Instrument iB = mockInstrument("I-B", "B전력계", EquipType.ELCMTR, root, 1);   // iA 와 동일 dispOrd → 이름 정렬
        Tag tC = mockTag("T-C", TagMeasurementType.PWI, iC);
        Tag tA = mockTag("T-A", TagMeasurementType.PWI, iA);
        Tag tB = mockTag("T-B", TagMeasurementType.PWI, iB);

        given(facilityRepository.findById(ROOT_ID)).willReturn(Optional.of(root));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), eq(YnType.Y)))
                .willReturn(List.of(iC, iA, iB));   // 의도적 비정렬 입력
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(tC, tA, tB));
        given(rawDataRepository.findInstrumentMinuteSumElpwr(anyList(), anyList(), any(), any()))
                .willReturn(List.of(
                        instSum("I-A", M1, "10"),
                        instSum("I-B", M1, "20"),
                        instSum("I-C", M1, "30")));

        FacilityInstrumentPowerTrendDto result =
                facilityInstrumentPowerTrendService.findPowerTrend(ROOT_ID, validSearch());

        assertThat(result.getSeries()).extracting(InstrumentPowerSeries::getInstrumentId)
                .containsExactly("I-A", "I-B", "I-C");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private FacilityInstrumentPowerTrendSearchDto validSearch() {
        FacilityInstrumentPowerTrendSearchDto search = new FacilityInstrumentPowerTrendSearchDto();
        search.setFromDt(LocalDate.of(2026, 1, 1));
        search.setToDt(LocalDate.of(2026, 1, 9));
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

    private RawDataInstrumentSumDto instSum(String instrumentId, LocalDateTime dtm, String value) {
        return new RawDataInstrumentSumDto(instrumentId, dtm, new BigDecimal(value));
    }
}
