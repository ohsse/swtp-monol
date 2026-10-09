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
import com.mo.swtp.facility.dto.FacilityPredcOperatingStatusDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcLatestDto;
import com.mo.swtp.opt.repository.TagPredcLatestRepository;
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
 * {@link FacilityPredcOperatingStatusService} 단위 테스트.
 *
 * <p>운전현황분석-9번섹션 PLAN1 §성공 기준 14종 중 단위 테스트 대상 11종
 * (#1·#2·#3·#6·#7·#8·#9·#10·#11) + OPS predc_val 4 케이스 파라미터화 1종
 * + FRI 분모 무효 3 케이스 파라미터화 1종 = 총 13 테스트 메서드.</p>
 *
 * <p>4번 섹션 {@link FacilityOperatingStatusServiceTest} 동형이나 다음 차이를 반영:
 * <ul>
 *   <li>{@code QualityCode} 분기 0건 — {@code predc_1m_h} 컬럼 부재. UNCERTAIN/BAD 케이스 미테스트</li>
 *   <li>OPS On 판정 케이스는 {@code predc_val} 4 케이스 (null·0.0·0.7·1.5) 모두 On 제외</li>
 *   <li>PWI 부분 결측 ({@code predc_val IS NULL}) 시 해당 펌프만 합산 제외 + 다른 펌프 유지</li>
 *   <li>FRI 분모 무효는 3 케이스 (null·0·부재) — BAD/UNCERTAIN 제거</li>
 *   <li>{@code measurementDtm} → {@code predcDtm}, {@code RawDataLatestDto} → {@link TagPredcLatestDto}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FacilityPredcOperatingStatusServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 21, 10, 30, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagPredcLatestRepository tagPredcLatestRepository;

    @InjectMocks
    private FacilityPredcOperatingStatusService facilityPredcOperatingStatusService;

    @Test
    void 예측운영현황_정상_조회_시_예측On펌프이름_예측PWI합산_예측전력원단위_predcDtm_반환() {
        // PUMP 3대 중 2대 예측 On (P1·P2), PWI 예측 합산 50.0+30.0=80.0, FRI 예측=400.0 → elpwrUnitQty=0.2
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
        given(tagPredcLatestRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                latest("T-OPS-P1", BigDecimal.ONE, NOW),
                latest("T-OPS-P2", BigDecimal.ONE, NOW),
                latest("T-OPS-P3", BigDecimal.ZERO, NOW),
                latest("T-PWI-P1", new BigDecimal("50.0"), NOW),
                latest("T-PWI-P2", new BigDecimal("30.0"), NOW),
                latest("T-PWI-P3", new BigDecimal("40.0"), NOW),
                latest("T-FRI-FM", new BigDecimal("400.0"), NOW)));

        FacilityPredcOperatingStatusDto result =
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getOnPumpNms()).containsExactlyInAnyOrder("P#1", "P#2");
        assertThat(result.getTotalElpwrAmt()).isEqualByComparingTo("80.0");
        assertThat(result.getElpwrUnitQty()).isEqualByComparingTo("0.2");
        assertThat(result.getPredcDtm()).isEqualTo(NOW);
    }

    static List<Arguments> OPS_predc_val_4_케이스() {
        return List.of(
                Arguments.of("predc_val_NULL", (BigDecimal) null),
                Arguments.of("predc_val_0_0", BigDecimal.ZERO),
                Arguments.of("predc_val_0_7", new BigDecimal("0.7")),
                Arguments.of("predc_val_1_5", new BigDecimal("1.5")));
    }

    @ParameterizedTest(name = "[{0}] OPS 예측 On 제외")
    @MethodSource("OPS_predc_val_4_케이스")
    void OPS_predc_val_1_0_이외의_4_케이스는_onPumpNms_에서_제외된다(String caseName, BigDecimal opsVal) {
        // PLAN1 §OPS On 판정 정책 — null·0.0·0.7·1.5 모두 predc_val != 1.0 → On 제외
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(tagPredcLatestRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                new TagPredcLatestDto("T-OPS-P1", NOW, opsVal)));

        FacilityPredcOperatingStatusDto result =
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1");

        assertThat(result.getOnPumpNms()).isEmpty();
    }

    @Test
    void PWI_부분_결측_시_다른_펌프_합산_유지() {
        // P1·P2 모두 OPS 예측 On 이지만 P2 의 PWI 예측은 NULL → 해당 펌프만 제외, totalElpwrAmt = 50.0 (P1) 만
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
        given(tagPredcLatestRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                latest("T-OPS-P1", BigDecimal.ONE, NOW),
                latest("T-OPS-P2", BigDecimal.ONE, NOW),
                latest("T-PWI-P1", new BigDecimal("50.0"), NOW),
                latest("T-PWI-P2", null, NOW)));  // P2 의 PWI 예측 결측

        FacilityPredcOperatingStatusDto result =
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1");

        assertThat(result.getOnPumpNms()).containsExactlyInAnyOrder("P#1", "P#2");
        assertThat(result.getTotalElpwrAmt()).isEqualByComparingTo("50.0");
    }

    static List<Arguments> FRI_분모_무효_3_케이스() {
        return List.of(
                Arguments.of("FRI_predc_val_NULL", new TagPredcLatestDto("T-FRI-FM", NOW, null)),
                Arguments.of("FRI_predc_val_0", new TagPredcLatestDto("T-FRI-FM", NOW, BigDecimal.ZERO)),
                Arguments.of("FRI_부재", null));
    }

    @ParameterizedTest(name = "[{0}] 분모 무효 시 elpwrUnitQty=null")
    @MethodSource("FRI_분모_무효_3_케이스")
    void elpwrUnitQty_분모_무효_시_null_반환(String caseName, TagPredcLatestDto invalidFri) {
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
        // FRI 부재 케이스는 List 에 미포함, 결측은 null predc_val 포함
        List<TagPredcLatestDto> predcResult = invalidFri == null
                ? List.of(
                        latest("T-OPS-P1", BigDecimal.ONE, NOW),
                        latest("T-PWI-P1", new BigDecimal("50.0"), NOW))
                : List.of(
                        latest("T-OPS-P1", BigDecimal.ONE, NOW),
                        latest("T-PWI-P1", new BigDecimal("50.0"), NOW),
                        invalidFri);
        given(tagPredcLatestRepository.findLatestByTagSrlNos(anyList())).willReturn(predcResult);

        FacilityPredcOperatingStatusDto result =
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1");

        assertThat(result.getElpwrUnitQty()).isNull();
    }

    @Test
    void 다른_자식_종류_인스트루먼트는_onPumpNms_에_포함되지_않는다() {
        // VALVE 자식이 OPS predc_val=1.0 을 가져도 onPumpNms 에 포함되지 않음 — equip_type_cd=PUMP 필터 강제
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "P#1");
        Valve v1 = mockValve("I-V-1", "밸브1");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, v1));  // VALVE 가 List 에 포함되어도
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(tagPredcLatestRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                latest("T-OPS-P1", BigDecimal.ZERO, NOW)));

        FacilityPredcOperatingStatusDto result =
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1");

        // VALVE 자식은 filterPumps() 에서 걸러져 onPumpNms 에 절대 포함되지 않음
        assertThat(result.getOnPumpNms()).isEmpty();
    }

    @Test
    void RSV_시설은_UNSUPPORTED_FACILITY_TYPE_거부() {
        Facility rsv = mockActiveFacility("F-RSV", "저수조A", FacilityType.RSV);
        given(facilityRepository.findById("F-RSV")).willReturn(Optional.of(rsv));

        assertThatThrownBy(() -> facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F-RSV"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }

    @Test
    void POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부() {
        Facility point = mockActiveFacility("F-POINT", "분기점A", FacilityType.POINT);
        given(facilityRepository.findById("F-POINT")).willReturn(Optional.of(point));

        assertThatThrownBy(() -> facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F-POINT"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }

    @Test
    void predcDtm_은_사용된_태그들의_max_predc_dtm_으로_산출된다() {
        // 3개 태그가 각각 다른 predc_dtm 보유 → max(predc_dtm) 반환
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
        given(tagPredcLatestRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                latest("T-OPS-P1", BigDecimal.ONE, earlier),
                latest("T-PWI-P1", new BigDecimal("50.0"), latest),
                latest("T-FRI-FM", new BigDecimal("400.0"), middle)));

        FacilityPredcOperatingStatusDto result =
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1");

        assertThat(result.getPredcDtm()).isEqualTo(latest);
    }

    @Test
    void 예측데이터_부재_시_빈_응답_반환() {
        // instruments 빈 경우 — 펌프·유량계 모두 미존재
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of());

        FacilityPredcOperatingStatusDto result =
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getOnPumpNms()).isEmpty();
        assertThat(result.getTotalElpwrAmt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getElpwrUnitQty()).isNull();
        assertThat(result.getPredcDtm()).isNull();
    }

    @Test
    void 비활성_시설은_FACILITY_NOT_FOUND_거부() {
        Facility inactive = Mockito.mock(Facility.class);
        Mockito.lenient().when(inactive.getUseYn()).thenReturn(YnType.N);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("F1"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 미존재_시설은_FACILITY_NOT_FOUND_거부() {
        given(facilityRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus("unknown"))
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

    private TagPredcLatestDto latest(String tagSrlNo, BigDecimal predcVal, LocalDateTime predcDtm) {
        return new TagPredcLatestDto(tagSrlNo, predcDtm, predcVal);
    }
}
