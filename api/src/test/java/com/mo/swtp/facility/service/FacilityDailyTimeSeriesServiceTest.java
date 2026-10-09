package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityDailyTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityDailyTimeSeriesDto.DailyTimeSeriesPoint;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcRangeDto;
import com.mo.swtp.opt.repository.TagPredcRangeRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityDailyTimeSeriesService} 단위 테스트.
 *
 * <p>운전현황분석-10번섹션 PLAN1 §성공 기준 9종 시나리오:
 * <ol>
 *   <li>정상 시계열 (PUMP·FLWMTR 모두 정상)</li>
 *   <li>자정~현재 actual+predc 공존 검증</li>
 *   <li>현재~익일자정 predc 단독 검증</li>
 *   <li>양쪽 모두 부재 슬롯 생략 검증</li>
 *   <li>OPS predc_val 4 경계값 parametrized</li>
 *   <li>FRI predc 분모 무효 3 케이스 parametrized</li>
 *   <li>VALVE 자식 제외 (equip_type_cd=PUMP 필터)</li>
 *   <li>RSV/POINT 시설 거부</li>
 *   <li>비활성·미존재 시설 거부</li>
 * </ol>
 *
 * <p>5번 섹션 / 9번 섹션 단위 테스트 동형이나 본 사이클은 actual·predc 합본 응답이라 시계열 포인트 단위
 * 검증을 핵심으로 한다 (사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합 — 헬퍼·픽스처 재구현).</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityDailyTimeSeriesServiceTest {

    private static final LocalDateTime SLOT_A = LocalDate.now().atStartOfDay();
    private static final LocalDateTime SLOT_B = SLOT_A.plusMinutes(1);
    private static final LocalDateTime SLOT_FUTURE = SLOT_A.plusHours(20);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @Mock
    private TagPredcRangeRepository tagPredcRangeRepository;

    @InjectMocks
    private FacilityDailyTimeSeriesService facilityDailyTimeSeriesService;

    @Test
    void 정상_시계열_actual_predc_합본_응답() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        FlowMeter fm = mockFlowMeter("I-FM-1", "유량계1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);
        Tag friFM = mockTag("T-FRI-FM", fm, TagMeasurementType.FRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, fm));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, pwiP1, friFM));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        actual("T-PWI-P1", new BigDecimal("50.0"), SLOT_A),
                        actual("T-FRI-FM", new BigDecimal("400.0"), SLOT_A)));
        given(tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        predc("T-PWI-P1", new BigDecimal("60.0"), SLOT_A),
                        predc("T-FRI-FM", new BigDecimal("300.0"), SLOT_A)));

        FacilityDailyTimeSeriesDto result =
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getPoints()).hasSize(1);
        DailyTimeSeriesPoint pt = result.getPoints().get(0);
        assertThat(pt.getDtm()).isEqualTo(SLOT_A);
        assertThat(pt.getActualElpwrAmt()).isEqualByComparingTo("50.0");
        assertThat(pt.getActualFlwrt()).isEqualByComparingTo("400.0");
        assertThat(pt.getActualUnitQty()).isEqualByComparingTo("0.125");
        assertThat(pt.getPredcElpwrAmt()).isEqualByComparingTo("60.0");
        assertThat(pt.getPredcFlwrt()).isEqualByComparingTo("300.0");
        assertThat(pt.getPredcUnitQty()).isEqualByComparingTo("0.2");
        assertThat(pt.getPredcPumpOnCnt()).isEqualTo(1);
    }

    @Test
    void 동일_슬롯에_actual_predc_공존_시_7_항목_모두_채워짐() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, pwiP1));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        actual("T-PWI-P1", new BigDecimal("50.0"), SLOT_A)));
        given(tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        predc("T-PWI-P1", new BigDecimal("60.0"), SLOT_A)));

        FacilityDailyTimeSeriesDto result =
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1");

        DailyTimeSeriesPoint pt = result.getPoints().get(0);
        // actual 필드: PWI 합산만 (FRI 부재라 actualFlwrt null, unitQty null)
        assertThat(pt.getActualElpwrAmt()).isEqualByComparingTo("50.0");
        assertThat(pt.getActualFlwrt()).isNull();
        assertThat(pt.getActualUnitQty()).isNull();
        // predc 필드: PWI 합산 + predcPumpOnCnt = 1
        assertThat(pt.getPredcElpwrAmt()).isEqualByComparingTo("60.0");
        assertThat(pt.getPredcFlwrt()).isNull();
        assertThat(pt.getPredcUnitQty()).isNull();
        assertThat(pt.getPredcPumpOnCnt()).isEqualTo(1);
    }

    @Test
    void predc_단독_슬롯은_actual_필드_모두_null() {
        // 미래 슬롯 — actual 부재, predc 만 존재
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, pwiP1));
        // actual 부재
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of());
        given(tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_FUTURE),
                        predc("T-PWI-P1", new BigDecimal("60.0"), SLOT_FUTURE)));

        FacilityDailyTimeSeriesDto result =
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1");

        assertThat(result.getPoints()).hasSize(1);
        DailyTimeSeriesPoint pt = result.getPoints().get(0);
        assertThat(pt.getDtm()).isEqualTo(SLOT_FUTURE);
        assertThat(pt.getActualElpwrAmt()).isNull();
        assertThat(pt.getActualFlwrt()).isNull();
        assertThat(pt.getActualUnitQty()).isNull();
        assertThat(pt.getPredcElpwrAmt()).isEqualByComparingTo("60.0");
        assertThat(pt.getPredcPumpOnCnt()).isEqualTo(1);
    }

    @Test
    void 양쪽_모두_부재_슬롯은_응답에서_생략() {
        // SLOT_A 는 actual·predc 둘 다 부재. SLOT_B 만 predc 존재.
        // 응답에는 SLOT_B 만 포함되어야 함.
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of());
        given(tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(predc("T-OPS-P1", BigDecimal.ONE, SLOT_B)));

        FacilityDailyTimeSeriesDto result =
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1");

        // SLOT_A 미포함, SLOT_B 만 응답
        assertThat(result.getPoints()).hasSize(1);
        assertThat(result.getPoints().get(0).getDtm()).isEqualTo(SLOT_B);
    }

    static List<Arguments> OPS_predc_val_4_케이스() {
        return List.of(
                Arguments.of("predc_val_NULL", (BigDecimal) null, 0),
                Arguments.of("predc_val_0_0", BigDecimal.ZERO, 0),
                Arguments.of("predc_val_1_0", BigDecimal.ONE, 1),
                Arguments.of("predc_val_1_5", new BigDecimal("1.5"), 0));
    }

    @ParameterizedTest(name = "[{0}] predcPumpOnCnt = {2}")
    @MethodSource("OPS_predc_val_4_케이스")
    void OPS_predc_val_경계값_predcPumpOnCnt_검증(String caseName, BigDecimal opsVal, int expectedCnt) {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of());
        given(tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(new TagPredcRangeDto("T-OPS-P1", SLOT_A, opsVal)));

        FacilityDailyTimeSeriesDto result =
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1");

        assertThat(result.getPoints()).hasSize(1);
        assertThat(result.getPoints().get(0).getPredcPumpOnCnt()).isEqualTo(expectedCnt);
    }

    static List<Arguments> FRI_분모_무효_3_케이스() {
        return List.of(
                Arguments.of("FRI_predc_val_NULL", new TagPredcRangeDto("T-FRI-FM", SLOT_A, null)),
                Arguments.of("FRI_predc_val_0", new TagPredcRangeDto("T-FRI-FM", SLOT_A, BigDecimal.ZERO)),
                Arguments.of("FRI_부재", null));
    }

    @ParameterizedTest(name = "[{0}] predcUnitQty = null")
    @MethodSource("FRI_분모_무효_3_케이스")
    void FRI_분모_무효_predcUnitQty_null_반환(String caseName, TagPredcRangeDto invalidFri) {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        FlowMeter fm = mockFlowMeter("I-FM-1", "유량계1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);
        Tag friFM = mockTag("T-FRI-FM", fm, TagMeasurementType.FRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, fm));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, pwiP1, friFM));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of());
        List<TagPredcRangeDto> predcResult = invalidFri == null
                ? List.of(
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        predc("T-PWI-P1", new BigDecimal("50.0"), SLOT_A))
                : List.of(
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        predc("T-PWI-P1", new BigDecimal("50.0"), SLOT_A),
                        invalidFri);
        given(tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(predcResult);

        FacilityDailyTimeSeriesDto result =
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1");

        assertThat(result.getPoints()).hasSize(1);
        assertThat(result.getPoints().get(0).getPredcUnitQty()).isNull();
    }

    @Test
    void VALVE_자식은_predcPumpOnCnt_에_포함되지_않음() {
        // VALVE 가 OPS predc_val=1.0 을 가져도 predcPumpOnCnt 에 포함되지 않음 — equip_type_cd=PUMP 필터 강제
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Valve v1 = mockValve("I-V-1", "밸브1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, v1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of());
        given(tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(predc("T-OPS-P1", BigDecimal.ZERO, SLOT_A)));

        FacilityDailyTimeSeriesDto result =
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1");

        // PUMP 자식만 평가 → predcPumpOnCnt = 0
        assertThat(result.getPoints().get(0).getPredcPumpOnCnt()).isEqualTo(0);
    }

    @Test
    void RSV_POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부() {
        Facility rsv = mockActiveFacility("F-RSV", "저수조A", FacilityType.RSV);
        given(facilityRepository.findById("F-RSV")).willReturn(Optional.of(rsv));

        assertThatThrownBy(() -> facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F-RSV"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);

        Facility point = mockActiveFacility("F-POINT", "분기점A", FacilityType.POINT);
        given(facilityRepository.findById("F-POINT")).willReturn(Optional.of(point));

        assertThatThrownBy(() -> facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F-POINT"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }

    @Test
    void 비활성_또는_미존재_시설은_FACILITY_NOT_FOUND_거부() {
        // 비활성
        Facility inactive = Mockito.mock(Facility.class);
        Mockito.lenient().when(inactive.getUseYn()).thenReturn(YnType.N);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("F1"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);

        // 미존재
        given(facilityRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityDailyTimeSeriesService.findFacilityDailyTimeSeries("unknown"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private Facility mockActiveFacility(String facilityId, String facilityNm, FacilityType facilityType) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(facilityNm);
        Mockito.lenient().when(facility.getUseYn()).thenReturn(YnType.Y);
        Mockito.lenient().when(facility.getFacilityType()).thenReturn(facilityType);
        return facility;
    }

    private Pump mockPump(String instrumentId, String instrumentNm) {
        Pump pump = Mockito.mock(Pump.class);
        Mockito.lenient().when(pump.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(pump.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(pump.getEquipType()).thenReturn(EquipType.PUMP);
        return pump;
    }

    private FlowMeter mockFlowMeter(String instrumentId, String instrumentNm) {
        FlowMeter fm = Mockito.mock(FlowMeter.class);
        Mockito.lenient().when(fm.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(fm.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(fm.getEquipType()).thenReturn(EquipType.FLWMTR);
        return fm;
    }

    private Valve mockValve(String instrumentId, String instrumentNm) {
        Valve valve = Mockito.mock(Valve.class);
        Mockito.lenient().when(valve.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(valve.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(valve.getEquipType()).thenReturn(EquipType.VALVE);
        return valve;
    }

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }

    private RawDataLatestDto actual(String tagSrlNo, BigDecimal val, LocalDateTime acqDtm) {
        return new RawDataLatestDto(tagSrlNo, val, val, acqDtm, QualityCode.GOOD);
    }

    private TagPredcRangeDto predc(String tagSrlNo, BigDecimal predcVal, LocalDateTime predcDtm) {
        return new TagPredcRangeDto(tagSrlNo, predcDtm, predcVal);
    }
}
