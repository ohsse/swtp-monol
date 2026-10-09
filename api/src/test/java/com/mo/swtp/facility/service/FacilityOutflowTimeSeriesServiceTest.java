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
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto.LinePoint;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto.PumpPoint;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto.PumpSeries;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcOutflowDto;
import com.mo.swtp.opt.repository.TagPredcOutflowRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataOutflowDto;
import com.mo.swtp.raw.repository.RawDataOutflowRepository;
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
 * {@link FacilityOutflowTimeSeriesService} 단위 테스트.
 *
 * <p>운전현황분석-7번섹션 PLAN1 §성공 기준 10종 시나리오:
 * <ol>
 *   <li>계측+예측 라인(FRI·PRI) 공존 응답</li>
 *   <li>펌프 계측 가동상태 on/off 판정</li>
 *   <li>펌프 계측 BAD/UNCERTAIN·결측 → 불명(null) 분리 (정지 오인 방지)</li>
 *   <li>펌프 예측 가동상태 on/off/불명 tri-state</li>
 *   <li>라인 FRI·PRI 결측 시 해당 항목 null</li>
 *   <li>라인 4값 양쪽 부재 슬롯 생략</li>
 *   <li>주 FLWMTR 첫 매치 고정 (FRI·PRI 동일 유량계 기준)</li>
 *   <li>RSV/POINT 시설 400 거부</li>
 *   <li>비활성·미존재 시설 404 거부</li>
 *   <li>VALVE 비대상 제외 + OPS 부재 펌프 빈 points 전수 포함</li>
 * </ol>
 *
 * <p>10번 섹션 단위 테스트 동형이나 본 사이클은 라인+막대 2-시리즈 + OPS tri-state 검증을 핵심으로 한다
 * (사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합 — 헬퍼·픽스처 재구현).</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityOutflowTimeSeriesServiceTest {

    private static final LocalDateTime SLOT_A = LocalDate.now().atStartOfDay();
    private static final LocalDateTime SLOT_B = SLOT_A.plusMinutes(1);
    private static final LocalDateTime SLOT_C = SLOT_A.plusMinutes(2);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataOutflowRepository rawDataOutflowRepository;

    @Mock
    private TagPredcOutflowRepository tagPredcOutflowRepository;

    @InjectMocks
    private FacilityOutflowTimeSeriesService facilityOutflowTimeSeriesService;

    @Test
    void 계측_예측_라인_FRI_PRI_공존_응답() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        FlowMeter fm = mockFlowMeter("I-FM-1", "유량계1");
        Tag friFM = mockTag("T-FRI-FM", fm, TagMeasurementType.FRI);
        Tag priFM = mockTag("T-PRI-FM", fm, TagMeasurementType.PRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(fm));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friFM, priFM));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-FRI-FM", new BigDecimal("400.0"), SLOT_A),
                        actual("T-PRI-FM", new BigDecimal("2.5"), SLOT_A)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-FRI-FM", new BigDecimal("390.0"), SLOT_A),
                        predc("T-PRI-FM", new BigDecimal("2.4"), SLOT_A)));

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getPumpSeries()).isEmpty();
        assertThat(result.getLinePoints()).hasSize(1);
        LinePoint lp = result.getLinePoints().get(0);
        assertThat(lp.getDtm()).isEqualTo(SLOT_A);
        assertThat(lp.getActualFlwrt()).isEqualByComparingTo("400.0");
        assertThat(lp.getActualPrsr()).isEqualByComparingTo("2.5");
        assertThat(lp.getPredcFlwrt()).isEqualByComparingTo("390.0");
        assertThat(lp.getPredcPrsr()).isEqualByComparingTo("2.4");
    }

    @Test
    void 펌프_계측_가동상태_on_off_판정() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "1호펌프");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        actual("T-OPS-P1", BigDecimal.ZERO, SLOT_B)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of());

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        assertThat(result.getPumpSeries()).hasSize(1);
        List<PumpPoint> points = result.getPumpSeries().get(0).getPoints();
        assertThat(points).hasSize(2);
        assertThat(points.get(0).getDtm()).isEqualTo(SLOT_A);
        assertThat(points.get(0).getActualOps()).isEqualTo(1);
        assertThat(points.get(1).getDtm()).isEqualTo(SLOT_B);
        assertThat(points.get(1).getActualOps()).isEqualTo(0);
    }

    @Test
    void 펌프_계측_BAD_UNCERTAIN_결측은_불명_null_정지_오인_방지() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "1호펌프");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        // SLOT_A: BAD 계측 + 예측 1.0 (예측 동반으로 슬롯 생존) → actualOps 불명(null)
        // SLOT_B: UNCERTAIN 계측 + 예측 1.0 → actualOps 불명(null)
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actualQ("T-OPS-P1", null, SLOT_A, QualityCode.BAD),
                        actualQ("T-OPS-P1", BigDecimal.ONE, SLOT_B, QualityCode.UNCERTAIN)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_B)));

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        List<PumpPoint> points = result.getPumpSeries().get(0).getPoints();
        assertThat(points).hasSize(2);
        // BAD·UNCERTAIN 은 0(정지) 아닌 null(불명) — 운전원 오인 방지
        assertThat(points.get(0).getActualOps()).isNull();
        assertThat(points.get(1).getActualOps()).isNull();
        assertThat(points.get(0).getPredcOps()).isEqualTo(1);
    }

    @Test
    void 펌프_예측_가동상태_on_off_불명_tri_state() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "1호펌프");
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        // SLOT_C: 예측 null + 계측 GOOD 1.0 (계측 동반으로 슬롯 생존) → predcOps 불명(null)
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(actual("T-OPS-P1", BigDecimal.ONE, SLOT_C)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-OPS-P1", BigDecimal.ONE, SLOT_A),
                        predc("T-OPS-P1", BigDecimal.ZERO, SLOT_B),
                        predc("T-OPS-P1", null, SLOT_C)));

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        List<PumpPoint> points = result.getPumpSeries().get(0).getPoints();
        assertThat(points).hasSize(3);
        assertThat(points.get(0).getPredcOps()).isEqualTo(1);   // SLOT_A 1.0
        assertThat(points.get(1).getPredcOps()).isEqualTo(0);   // SLOT_B 0.0
        assertThat(points.get(2).getPredcOps()).isNull();       // SLOT_C null (불명)
        assertThat(points.get(2).getActualOps()).isEqualTo(1);
    }

    @Test
    void 라인_FRI_PRI_결측시_해당_항목_null() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        FlowMeter fm = mockFlowMeter("I-FM-1", "유량계1");
        Tag friFM = mockTag("T-FRI-FM", fm, TagMeasurementType.FRI);
        Tag priFM = mockTag("T-PRI-FM", fm, TagMeasurementType.PRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(fm));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friFM, priFM));
        // FRI 는 GOOD, PRI 는 BAD → actualPrsr null
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-FRI-FM", new BigDecimal("400.0"), SLOT_A),
                        actualQ("T-PRI-FM", null, SLOT_A, QualityCode.BAD)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of());

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        assertThat(result.getLinePoints()).hasSize(1);
        LinePoint lp = result.getLinePoints().get(0);
        assertThat(lp.getActualFlwrt()).isEqualByComparingTo("400.0");
        assertThat(lp.getActualPrsr()).isNull();
        assertThat(lp.getPredcFlwrt()).isNull();
        assertThat(lp.getPredcPrsr()).isNull();
    }

    @Test
    void 라인_4값_양쪽_부재_슬롯은_응답에서_생략() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        FlowMeter fm = mockFlowMeter("I-FM-1", "유량계1");
        Tag friFM = mockTag("T-FRI-FM", fm, TagMeasurementType.FRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(fm));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friFM));
        // SLOT_A FRI BAD → actualFlwrt null, PRI·예측 전부 부재 → 4값 모두 null → 슬롯 생략
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(actualQ("T-FRI-FM", null, SLOT_A, QualityCode.BAD)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of());

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        assertThat(result.getLinePoints()).isEmpty();
    }

    @Test
    void 주_FLWMTR_첫_매치_고정_FRI_동일_유량계_기준() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        FlowMeter fm1 = mockFlowMeter("I-FM-1", "유량계1");
        FlowMeter fm2 = mockFlowMeter("I-FM-2", "유량계2");
        Tag friFM1 = mockTag("T-FRI-1", fm1, TagMeasurementType.FRI);
        Tag friFM2 = mockTag("T-FRI-2", fm2, TagMeasurementType.FRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        // 첫 매치 = fm1 (목록 선두)
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(fm1, fm2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friFM1, friFM2));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-FRI-1", new BigDecimal("400.0"), SLOT_A),
                        actual("T-FRI-2", new BigDecimal("999.0"), SLOT_A)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of());

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        assertThat(result.getLinePoints()).hasSize(1);
        // fm1 의 FRI(400) 만 사용 — fm2 의 999 는 무시
        assertThat(result.getLinePoints().get(0).getActualFlwrt()).isEqualByComparingTo("400.0");
    }

    @Test
    void RSV_POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부() {
        Facility rsv = mockActiveFacility("F-RSV", "저수조A", FacilityType.RSV);
        given(facilityRepository.findById("F-RSV")).willReturn(Optional.of(rsv));

        assertThatThrownBy(() -> facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F-RSV"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);

        Facility point = mockActiveFacility("F-POINT", "분기점A", FacilityType.POINT);
        given(facilityRepository.findById("F-POINT")).willReturn(Optional.of(point));

        assertThatThrownBy(() -> facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F-POINT"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }

    @Test
    void 비활성_또는_미존재_시설은_FACILITY_NOT_FOUND_거부() {
        Facility inactive = Mockito.mock(Facility.class);
        Mockito.lenient().when(inactive.getUseYn()).thenReturn(YnType.N);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);

        given(facilityRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("unknown"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void VALVE_비대상_제외_및_OPS부재_펌프_빈_points_포함() {
        Facility active = mockActiveFacility("F1", "정수지A", FacilityType.PWTF);
        Pump p1 = mockPump("I-P-1", "1호펌프");   // OPS 데이터 보유
        Pump p2 = mockPump("I-P-2", "2호펌프");   // OPS 태그·데이터 부재 → 빈 points
        Valve v1 = mockValve("I-V-1", "밸브1");    // 비대상 — pumpSeries 제외
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);
        Tag opsV1 = mockTag("T-OPS-V1", v1, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(p1, p2, v1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1, opsV1));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(actual("T-OPS-P1", BigDecimal.ONE, SLOT_A)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of());

        FacilityOutflowTimeSeriesDto result =
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries("F1");

        // VALVE 제외 → PUMP 2대만, 전수 포함
        assertThat(result.getPumpSeries()).hasSize(2);
        assertThat(result.getPumpSeries())
                .extracting(PumpSeries::getInstrumentId)
                .containsExactlyInAnyOrder("I-P-1", "I-P-2");
        PumpSeries p1Series = findSeries(result.getPumpSeries(), "I-P-1");
        PumpSeries p2Series = findSeries(result.getPumpSeries(), "I-P-2");
        assertThat(p1Series.getPoints()).hasSize(1);
        assertThat(p1Series.getPoints().get(0).getActualOps()).isEqualTo(1);
        // OPS 부재 펌프도 트랙 포함 (빈 points)
        assertThat(p2Series.getPoints()).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private PumpSeries findSeries(List<PumpSeries> series, String instrumentId) {
        return series.stream()
                .filter(s -> s.getInstrumentId().equals(instrumentId))
                .findFirst()
                .orElseThrow();
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

    /** GOOD 계측 행 — corrVal·rawVal 동일값. */
    private RawDataOutflowDto actual(String tagSrlNo, BigDecimal val, LocalDateTime acqDtm) {
        return actualQ(tagSrlNo, val, acqDtm, QualityCode.GOOD);
    }

    /** QUALITY 명시 계측 행 — BAD/UNCERTAIN tri-state 검증용. */
    private RawDataOutflowDto actualQ(String tagSrlNo, BigDecimal val, LocalDateTime acqDtm, QualityCode quality) {
        return new RawDataOutflowDto(tagSrlNo, acqDtm, val, val, quality);
    }

    private TagPredcOutflowDto predc(String tagSrlNo, BigDecimal predcVal, LocalDateTime predcDtm) {
        return new TagPredcOutflowDto(tagSrlNo, predcDtm, predcVal);
    }
}
