package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityOperatingStatusCompareType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityOperatingStatusTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityOperatingStatusTimeSeriesDto.TimeSeriesPoint;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityOperatingStatusTimeSeriesService} 단위 테스트 (옵션 T).
 *
 * <p>운전현황분석-5번섹션-DTO재설계 PLAN1 §성공 기준 — 시계열 4종 + 1440 고정 1종 + 예외 2종:
 * 정상 머지·빈 시계열·결측 슬롯 null·UNCERTAIN OPS 제외·1440 행 고정 경계·비활성/RSV 시설 거부.</p>
 *
 * <p>{@code series} 는 항상 1440 슬롯이며 시점 조회는 {@code slotOf(HH:mm)} = hour*60+minute 인덱스로 한다.
 * 데이터 타임스탬프의 날짜 부분은 머지 키에 무관 — "HH:mm" 만 슬롯을 결정한다 (cross-date 머지 정합).</p>
 *
 * <p>{@code findByTagSrlNosAndDtmRange} 는 today + comparison 두 번 호출되므로 mock 은 연속 응답
 * {@code willReturn(today, comparison)} 로 분리 검증한다.
 * {@code LocalDate.now()}·{@code LocalDateTime.now()} 직접 호출로 인한 시간 범위 정확값 검증은 manual.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityOperatingStatusTimeSeriesServiceTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 5, 21, 10, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 5, 21, 10, 1, 0);
    private static final LocalDateTime T_COMP = LocalDateTime.of(2026, 5, 20, 9, 0, 0);
    private static final LocalDateTime T_MIDNIGHT = LocalDateTime.of(2026, 5, 21, 0, 0, 0);
    private static final LocalDateTime T_END = LocalDateTime.of(2026, 5, 21, 23, 59, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private FacilityOperatingStatusTimeSeriesService service;

    @Test
    void 정상_시계열_조회_시_today_comparison_이_시간분_키로_머지되고_미겹침_슬롯은_null() {
        // 펌프 3대 (P1·P2·P3) + 유량계 1대.
        // today — T1(10:00): P1·P2 On (PWI 50+30=80), FRI=400 → todayElpwrUnitQty=0.2, cnt=2
        //         T2(10:01): P1 만 On (PWI=50), FRI=500 → todayElpwrUnitQty=0.1, cnt=1
        // comparison(YESTERDAY) — T_COMP(09:00): P1 만 On (PWI=70), FRI=350 → comparisonElpwrUnitQty=0.2
        // today 와 comparison 의 시간:분이 겹치지 않으므로 각 슬롯은 한쪽 컬럼만 값, 반대 컬럼 null.
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Pump p2 = mockPump("I-P-2", "P#2");
        Pump p3 = mockPump("I-P-3", "P#3");
        FlowMeter fm = mockFlowMeter("I-FM-1", "유량계1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag opsP2 = mockTag("T-OPS-P2", p2, TagMeasurementType.OPS);
        Tag opsP3 = mockTag("T-OPS-P3", p3, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);
        Tag pwiP2 = mockTag("T-PWI-P2", p2, TagMeasurementType.PWI);
        Tag pwiP3 = mockTag("T-PWI-P3", p3, TagMeasurementType.PWI);
        Tag friFM = mockTag("T-FRI-FM", fm, TagMeasurementType.FRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, p2, p3, fm));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, opsP2, opsP3, pwiP1, pwiP2, pwiP3, friFM));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        goodLatest("T-OPS-P1", BigDecimal.ONE, T1),
                        goodLatest("T-OPS-P2", BigDecimal.ONE, T1),
                        goodLatest("T-OPS-P3", BigDecimal.ZERO, T1),
                        goodLatest("T-PWI-P1", new BigDecimal("50.0"), T1),
                        goodLatest("T-PWI-P2", new BigDecimal("30.0"), T1),
                        goodLatest("T-PWI-P3", new BigDecimal("40.0"), T1),
                        goodLatest("T-FRI-FM", new BigDecimal("400.0"), T1),
                        goodLatest("T-OPS-P1", BigDecimal.ONE, T2),
                        goodLatest("T-OPS-P2", BigDecimal.ZERO, T2),
                        goodLatest("T-OPS-P3", BigDecimal.ZERO, T2),
                        goodLatest("T-PWI-P1", new BigDecimal("50.0"), T2),
                        goodLatest("T-FRI-FM", new BigDecimal("500.0"), T2)),
                        List.of(
                                goodLatest("T-OPS-P1", BigDecimal.ONE, T_COMP),
                                goodLatest("T-OPS-P2", BigDecimal.ZERO, T_COMP),
                                goodLatest("T-OPS-P3", BigDecimal.ZERO, T_COMP),
                                goodLatest("T-PWI-P1", new BigDecimal("70.0"), T_COMP),
                                goodLatest("T-FRI-FM", new BigDecimal("350.0"), T_COMP)));

        FacilityOperatingStatusTimeSeriesDto result = service.findFacilityOperatingStatusTimeSeries(
                "F1", FacilityOperatingStatusCompareType.YESTERDAY);

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getCompareType()).isEqualTo(FacilityOperatingStatusCompareType.YESTERDAY);
        assertThat(result.getBaseDate()).isEqualTo(LocalDate.now());
        assertThat(result.getComparisonDate()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(result.getSeries()).hasSize(1440);

        TimeSeriesPoint t1 = result.getSeries().get(slotOf(T1));
        assertThat(t1.getTime()).isEqualTo("10:00");
        assertThat(t1.getTodayOnPumpCnt()).isEqualTo(2);
        assertThat(t1.getTodayElpwrUnitQty()).isEqualByComparingTo("0.2");
        assertThat(t1.getComparisonElpwrUnitQty()).isNull();

        TimeSeriesPoint t2 = result.getSeries().get(slotOf(T2));
        assertThat(t2.getTime()).isEqualTo("10:01");
        assertThat(t2.getTodayOnPumpCnt()).isEqualTo(1);
        assertThat(t2.getTodayElpwrUnitQty()).isEqualByComparingTo("0.1");
        assertThat(t2.getComparisonElpwrUnitQty()).isNull();

        TimeSeriesPoint comp = result.getSeries().get(slotOf(T_COMP));
        assertThat(comp.getTime()).isEqualTo("09:00");
        assertThat(comp.getComparisonElpwrUnitQty()).isEqualByComparingTo("0.2");
        assertThat(comp.getTodayElpwrUnitQty()).isNull();
        assertThat(comp.getTodayOnPumpCnt()).isNull();
    }

    @Test
    void 빈_시계열_조회_시_series_1440_고정이고_전_슬롯_3컬럼_null() {
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

        FacilityOperatingStatusTimeSeriesDto result = service.findFacilityOperatingStatusTimeSeries(
                "F1", FacilityOperatingStatusCompareType.LAST_WEEK);

        assertThat(result.getCompareType()).isEqualTo(FacilityOperatingStatusCompareType.LAST_WEEK);
        assertThat(result.getComparisonDate()).isEqualTo(LocalDate.now().minusDays(7));
        assertThat(result.getSeries()).hasSize(1440);
        assertThat(result.getSeries()).allSatisfy(p -> {
            assertThat(p.getTodayElpwrUnitQty()).isNull();
            assertThat(p.getComparisonElpwrUnitQty()).isNull();
            assertThat(p.getTodayOnPumpCnt()).isNull();
        });
    }

    @Test
    void 결측_시점은_해당_슬롯_컬럼이_null로_채워진다() {
        // today 는 T1(10:00) 만 존재, T2(10:01) 미수신, comparison 전체 부재
        // → slotOf(T1) today 값 존재, slotOf(T2) 및 comparison 전부 null
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
                        goodLatest("T-OPS-P1", BigDecimal.ONE, T1),
                        goodLatest("T-PWI-P1", new BigDecimal("50.0"), T1),
                        goodLatest("T-FRI-FM", new BigDecimal("250.0"), T1)),
                        List.of());

        FacilityOperatingStatusTimeSeriesDto result = service.findFacilityOperatingStatusTimeSeries(
                "F1", FacilityOperatingStatusCompareType.YESTERDAY);

        assertThat(result.getSeries()).hasSize(1440);

        TimeSeriesPoint t1Slot = result.getSeries().get(slotOf(T1));
        assertThat(t1Slot.getTodayOnPumpCnt()).isEqualTo(1);
        assertThat(t1Slot.getTodayElpwrUnitQty()).isEqualByComparingTo("0.2");

        TimeSeriesPoint t2Slot = result.getSeries().get(slotOf(T2));
        assertThat(t2Slot.getTodayElpwrUnitQty()).isNull();
        assertThat(t2Slot.getTodayOnPumpCnt()).isNull();
        assertThat(t2Slot.getComparisonElpwrUnitQty()).isNull();
    }

    @Test
    void UNCERTAIN_OPS_펌프는_onPumpCnt와_PWI합산_모두에서_제외된다() {
        // 펌프 2대 중 P1 OPS=GOOD+1.0, P2 OPS=UNCERTAIN+1.0. PWI P1=50, P2=60. FRI=250.
        // → onPumpCnt=1 (P1), totalElpwr=50 (P2 PWI 제외), elpwrUnitQty=50/250=0.2
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Pump p2 = mockPump("I-P-2", "P#2");
        FlowMeter fm = mockFlowMeter("I-FM-1", "유량계1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag opsP2 = mockTag("T-OPS-P2", p2, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);
        Tag pwiP2 = mockTag("T-PWI-P2", p2, TagMeasurementType.PWI);
        Tag friFM = mockTag("T-FRI-FM", fm, TagMeasurementType.FRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, p2, fm));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, opsP2, pwiP1, pwiP2, friFM));
        given(rawDataRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        goodLatest("T-OPS-P1", BigDecimal.ONE, T1),
                        new RawDataLatestDto("T-OPS-P2", BigDecimal.ONE, null, T1, QualityCode.UNCERTAIN),
                        goodLatest("T-PWI-P1", new BigDecimal("50.0"), T1),
                        goodLatest("T-PWI-P2", new BigDecimal("60.0"), T1),
                        goodLatest("T-FRI-FM", new BigDecimal("250.0"), T1)),
                        List.of());

        FacilityOperatingStatusTimeSeriesDto result = service.findFacilityOperatingStatusTimeSeries(
                "F1", FacilityOperatingStatusCompareType.YESTERDAY);

        TimeSeriesPoint point = result.getSeries().get(slotOf(T1));
        assertThat(point.getTodayOnPumpCnt()).isEqualTo(1);
        // P2 UNCERTAIN → P2 PWI(60) 합산 제외, 50/250 = 0.2. 만약 P2 포함 시 110/250=0.44.
        assertThat(point.getTodayElpwrUnitQty()).isEqualByComparingTo("0.2");
    }

    @Test
    void series_는_00시00분부터_23시59분까지_1440_고정이고_경계_슬롯이_정확하다() {
        // today 데이터를 자정(00:00)·종일(23:59) 두 경계 시점에 배치, comparison 부재.
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
                        goodLatest("T-OPS-P1", BigDecimal.ONE, T_MIDNIGHT),
                        goodLatest("T-PWI-P1", new BigDecimal("50.0"), T_MIDNIGHT),
                        goodLatest("T-FRI-FM", new BigDecimal("250.0"), T_MIDNIGHT),
                        goodLatest("T-OPS-P1", BigDecimal.ONE, T_END),
                        goodLatest("T-PWI-P1", new BigDecimal("100.0"), T_END),
                        goodLatest("T-FRI-FM", new BigDecimal("400.0"), T_END)),
                        List.of());

        FacilityOperatingStatusTimeSeriesDto result = service.findFacilityOperatingStatusTimeSeries(
                "F1", FacilityOperatingStatusCompareType.YESTERDAY);

        assertThat(result.getSeries()).hasSize(1440);

        TimeSeriesPoint first = result.getSeries().get(0);
        assertThat(first.getTime()).isEqualTo("00:00");
        assertThat(first.getTodayOnPumpCnt()).isEqualTo(1);
        assertThat(first.getTodayElpwrUnitQty()).isEqualByComparingTo("0.2");

        TimeSeriesPoint last = result.getSeries().get(1439);
        assertThat(last.getTime()).isEqualTo("23:59");
        assertThat(last.getTodayOnPumpCnt()).isEqualTo(1);
        assertThat(last.getTodayElpwrUnitQty()).isEqualByComparingTo("0.25");

        TimeSeriesPoint noon = result.getSeries().get(720);
        assertThat(noon.getTime()).isEqualTo("12:00");
        assertThat(noon.getTodayElpwrUnitQty()).isNull();
        assertThat(noon.getComparisonElpwrUnitQty()).isNull();
        assertThat(noon.getTodayOnPumpCnt()).isNull();
    }

    @Test
    void 비활성_시설_조회_시_FACILITY_NOT_FOUND_예외() {
        Facility inactive = Mockito.mock(Facility.class);
        Mockito.lenient().when(inactive.getUseYn()).thenReturn(YnType.N);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.findFacilityOperatingStatusTimeSeries(
                "F1", FacilityOperatingStatusCompareType.YESTERDAY))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void RSV_시설_조회_시_UNSUPPORTED_FACILITY_TYPE_예외() {
        Facility rsv = mockActiveFacility("F-RSV", "저수조A", FacilityType.RSV);
        given(facilityRepository.findById("F-RSV")).willReturn(Optional.of(rsv));

        assertThatThrownBy(() -> service.findFacilityOperatingStatusTimeSeries(
                "F-RSV", FacilityOperatingStatusCompareType.LAST_WEEK))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    /** 1분 슬롯 인덱스 — series 는 00:00=0 … 23:59=1439 의 1440 고정 배열. */
    private int slotOf(LocalDateTime dtm) {
        return dtm.getHour() * 60 + dtm.getMinute();
    }

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

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }

    private RawDataLatestDto goodLatest(String tagSrlNo, BigDecimal rawVal, LocalDateTime acqDtm) {
        return new RawDataLatestDto(tagSrlNo, rawVal, null, acqDtm, QualityCode.GOOD);
    }
}
