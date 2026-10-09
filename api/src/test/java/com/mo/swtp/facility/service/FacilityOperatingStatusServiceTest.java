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
import com.mo.swtp.facility.dto.FacilityOperatingStatusDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
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
 * {@link FacilityOperatingStatusService} 단위 테스트.
 *
 * <p>운전현황분석-4번섹션 PLAN1 §성공 기준 12종 중 단위 테스트 대상 9종 (#1·#2·#3·#4·#5·#6·#7·#8·#9)
 * + FACILITY_NOT_FOUND 2종 = 총 11 테스트 메서드 (5번 분모 무효 4 케이스는 파라미터화).</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityOperatingStatusServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 21, 10, 30, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private FacilityOperatingStatusService facilityOperatingStatusService;

    @Test
    void 운영현황_정상_조회_시_On펌프이름_PWI합산_전력원단위_측정시간_반환() {
        // PUMP 3대 중 2대 On (P1·P2), PWI 합산 50.0+30.0=80.0, FRI=400.0 → elpwrUnitQty=80/400=0.2
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
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ONE, NOW),
                goodLatest("T-OPS-P2", BigDecimal.ONE, NOW),
                goodLatest("T-OPS-P3", BigDecimal.ZERO, NOW),
                goodLatest("T-PWI-P1", new BigDecimal("50.0"), NOW),
                goodLatest("T-PWI-P2", new BigDecimal("30.0"), NOW),
                goodLatest("T-PWI-P3", new BigDecimal("40.0"), NOW),
                goodLatest("T-FRI-FM", new BigDecimal("400.0"), NOW)));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getOnPumpNms()).containsExactlyInAnyOrder("P#1", "P#2");
        assertThat(result.getTotalElpwrAmt()).isEqualByComparingTo("80.0");
        assertThat(result.getElpwrUnitQty()).isEqualByComparingTo("0.2");
        assertThat(result.getMeasurementDtm()).isEqualTo(NOW);
    }

    @Test
    void BAD_OPS_펌프는_onPumpNms_에서_제외된다() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                new RawDataLatestDto("T-OPS-P1", BigDecimal.ONE, null, NOW, QualityCode.BAD)));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getOnPumpNms()).isEmpty();
    }

    @Test
    void UNCERTAIN_OPS_펌프는_onPumpNms_에서_제외된다() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                new RawDataLatestDto("T-OPS-P1", BigDecimal.ONE, null, NOW, QualityCode.UNCERTAIN)));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getOnPumpNms()).isEmpty();
    }

    @Test
    void UNCERTAIN_PWI_는_totalElpwrAmt_합산에서_전액_제외된다() {
        // P1·P2 모두 GOOD OPS On 이지만 P2 의 PWI 는 UNCERTAIN → 합산 전액 제외, totalElpwrAmt = 50.0 만
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Pump p2 = mockPump("I-P-2", "P#2");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag opsP2 = mockTag("T-OPS-P2", p2, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);
        Tag pwiP2 = mockTag("T-PWI-P2", p2, TagMeasurementType.PWI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, p2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, opsP2, pwiP1, pwiP2));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ONE, NOW),
                goodLatest("T-OPS-P2", BigDecimal.ONE, NOW),
                goodLatest("T-PWI-P1", new BigDecimal("50.0"), NOW),
                new RawDataLatestDto("T-PWI-P2", new BigDecimal("60.0"), null, NOW, QualityCode.UNCERTAIN)));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getOnPumpNms()).containsExactlyInAnyOrder("P#1", "P#2");
        assertThat(result.getTotalElpwrAmt()).isEqualByComparingTo("50.0");
    }

    static List<Arguments> 분모_무효_4_케이스() {
        return List.of(
                Arguments.of("FRI_0",
                        new RawDataLatestDto("T-FRI-FM", BigDecimal.ZERO, null, NOW, QualityCode.GOOD)),
                Arguments.of("FRI_NULL",
                        new RawDataLatestDto("T-FRI-FM", null, null, NOW, QualityCode.GOOD)),
                Arguments.of("FRI_BAD",
                        new RawDataLatestDto("T-FRI-FM", new BigDecimal("400.0"), null, NOW, QualityCode.BAD)),
                Arguments.of("FRI_UNCERTAIN",
                        new RawDataLatestDto("T-FRI-FM", new BigDecimal("400.0"), null, NOW, QualityCode.UNCERTAIN)));
    }

    @ParameterizedTest(name = "[{0}] 분모 무효 시 elpwrUnitQty=null")
    @MethodSource("분모_무효_4_케이스")
    void elpwrUnitQty_분모_무효_시_null_반환(String caseName, RawDataLatestDto invalidFri) {
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
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ONE, NOW),
                goodLatest("T-PWI-P1", new BigDecimal("50.0"), NOW),
                invalidFri));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getElpwrUnitQty()).isNull();
    }

    @Test
    void FRI_부재_시_elpwrUnitQty_null_반환() {
        // FLWMTR 자체가 없어 FRI 측정값 부재 케이스
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag pwiP1 = mockTag("T-PWI-P1", p1, TagMeasurementType.PWI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, pwiP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ONE, NOW),
                goodLatest("T-PWI-P1", new BigDecimal("50.0"), NOW)));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getOnPumpNms()).containsExactly("P#1");
        assertThat(result.getTotalElpwrAmt()).isEqualByComparingTo("50.0");
        assertThat(result.getElpwrUnitQty()).isNull();
    }

    @Test
    void 다른_자식_종류_인스트루먼트는_onPumpNms_에_포함되지_않는다() {
        // VALVE 자식이 OPS 측정값 GOOD 1.0 을 가져도 onPumpNms 에 포함되지 않음 — equip_type_cd=PUMP 필터 강제
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Valve v1 = mockValve("I-V-1", "밸브1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, v1));  // VALVE 가 List 에 포함되어도
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ZERO, NOW)));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        // VALVE 자식은 filterPumps() 에서 걸러져 onPumpNms 에 절대 포함되지 않음
        assertThat(result.getOnPumpNms()).isEmpty();
    }

    @Test
    void RSV_시설은_UNSUPPORTED_FACILITY_TYPE_거부() {
        Facility rsv = mockActiveFacility("F-RSV", "저수조A", FacilityType.RSV);
        given(facilityRepository.findById("F-RSV")).willReturn(Optional.of(rsv));

        assertThatThrownBy(() -> facilityOperatingStatusService.findFacilityOperatingStatus("F-RSV"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }

    @Test
    void POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부() {
        Facility point = mockActiveFacility("F-POINT", "분기점A", FacilityType.POINT);
        given(facilityRepository.findById("F-POINT")).willReturn(Optional.of(point));

        assertThatThrownBy(() -> facilityOperatingStatusService.findFacilityOperatingStatus("F-POINT"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }

    @Test
    void measurementDtm_은_사용된_태그들의_max_acq_dtm_으로_산출된다() {
        // 3개 태그가 각각 다른 acq_dtm 보유 → max(acq_dtm) 반환
        LocalDateTime earlier = LocalDateTime.of(2026, 5, 21, 9, 0, 0);
        LocalDateTime middle = LocalDateTime.of(2026, 5, 21, 9, 30, 0);
        LocalDateTime latest = LocalDateTime.of(2026, 5, 21, 10, 30, 0);
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
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ONE, earlier),
                goodLatest("T-PWI-P1", new BigDecimal("50.0"), latest),
                goodLatest("T-FRI-FM", new BigDecimal("400.0"), middle)));

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getMeasurementDtm()).isEqualTo(latest);
    }

    @Test
    void OPS_태그_부재_시_빈_응답_반환() {
        // instruments 빈 경우 — 펌프·유량계 모두 미존재
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of());

        FacilityOperatingStatusDto result =
                facilityOperatingStatusService.findFacilityOperatingStatus("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getOnPumpNms()).isEmpty();
        assertThat(result.getTotalElpwrAmt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getElpwrUnitQty()).isNull();
        assertThat(result.getMeasurementDtm()).isNull();
    }

    @Test
    void 비활성_시설은_FACILITY_NOT_FOUND_거부() {
        Facility inactive = Mockito.mock(Facility.class);
        Mockito.lenient().when(inactive.getUseYn()).thenReturn(YnType.N);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityOperatingStatusService.findFacilityOperatingStatus("F1"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 미존재_시설은_FACILITY_NOT_FOUND_거부() {
        given(facilityRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityOperatingStatusService.findFacilityOperatingStatus("unknown"))
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

    private RawDataLatestDto goodLatest(String tagSrlNo, BigDecimal rawVal, LocalDateTime acqDtm) {
        return new RawDataLatestDto(tagSrlNo, rawVal, null, acqDtm, QualityCode.GOOD);
    }
}
