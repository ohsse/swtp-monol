package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.DistributionWaterTank;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.DwtGroupStateDto;
import com.mo.swtp.facility.dto.DwtStateDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.LevelMeter;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
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
 * {@link DwtStateService} 단위 테스트.
 *
 * <p>송수펌프제어분석-4번섹션 PLAN1 §성공 기준 + 5번섹션 PLAN1 §성공 기준:</p>
 * <ol>
 *   <li>정상응답 — DWT 2건, 각 inlet/outlet/valve/lvmtr 보유 + 5번섹션 최소요구관압 2필드</li>
 *   <li>부모미존재 → FACILITY_NOT_FOUND</li>
 *   <li>자식 DWT 0건 → 200 + 빈 리스트</li>
 *   <li>유입 FLWMTR 2건 등록 → multipleInFlwmtrDetected = true + 첫 매치 사용</li>
 *   <li>5번섹션 최소요구관압 응답 매핑 — DistributionWaterTank.minReqPrsr·minReqBranchPrsr 노출</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class DwtStateServiceTest {

    private static final LocalDateTime FIXED_DTM = LocalDateTime.of(2026, 5, 14, 10, 30, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private DwtStateService dwtStateService;

    @Test
    void findDwtStates_정상응답() {
        Facility parent = mockFacility("P1", "성주정수장", YnType.Y);
        DistributionWaterTank dwt1 = mockDwt("D1", "배수지A");
        DistributionWaterTank dwt2 = mockDwt("D2", "배수지B");

        FlowMeter inlet1 = mockFlwmtr("I-IN-1", "유입유량계1", dwt1);
        FlowMeter outlet1 = mockFlwmtr("I-OUT-1", "유출유량계1", dwt1);
        Valve valve1 = mockValve("I-VAL-1", "밸브1", dwt1);
        LevelMeter lvmtr1 = mockLvmtr("I-LV-1", "수위계1", dwt1);

        FlowMeter inlet2 = mockFlwmtr("I-IN-2", "유입유량계2", dwt2);
        FlowMeter outlet2 = mockFlwmtr("I-OUT-2", "유출유량계2", dwt2);
        Valve valve2 = mockValve("I-VAL-2", "밸브2", dwt2);
        LevelMeter lvmtr2 = mockLvmtr("I-LV-2", "수위계2", dwt2);

        Tag friInlet1 = mockTag("T-IN-FRI-1", inlet1, TagMeasurementType.FRI, IoCode.INPUT);
        Tag priInlet1 = mockTag("T-IN-PRI-1", inlet1, TagMeasurementType.PRI, IoCode.INPUT);
        Tag friOutlet1 = mockTag("T-OUT-FRI-1", outlet1, TagMeasurementType.FRI, IoCode.OUTPUT);
        Tag voiValve1 = mockTag("T-VOI-1", valve1, TagMeasurementType.VOI, IoCode.OUTPUT);
        Tag leiLvmtr1 = mockTag("T-LEI-1", lvmtr1, TagMeasurementType.LEI, IoCode.INPUT);

        Tag friInlet2 = mockTag("T-IN-FRI-2", inlet2, TagMeasurementType.FRI, IoCode.INPUT);
        Tag priInlet2 = mockTag("T-IN-PRI-2", inlet2, TagMeasurementType.PRI, IoCode.INPUT);
        Tag friOutlet2 = mockTag("T-OUT-FRI-2", outlet2, TagMeasurementType.FRI, IoCode.OUTPUT);
        Tag voiValve2 = mockTag("T-VOI-2", valve2, TagMeasurementType.VOI, IoCode.OUTPUT);
        Tag leiLvmtr2 = mockTag("T-LEI-2", lvmtr2, TagMeasurementType.LEI, IoCode.INPUT);

        given(facilityRepository.findById("P1")).willReturn(Optional.of(parent));
        given(facilityRepository.findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(
                eq("P1"), eq(FacilityType.DWT), eq(YnType.Y)))
                .willReturn(List.of(dwt1, dwt2));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(inlet1, outlet1, valve1, lvmtr1,
                        inlet2, outlet2, valve2, lvmtr2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friInlet1, priInlet1, friOutlet1, voiValve1, leiLvmtr1,
                        friInlet2, priInlet2, friOutlet2, voiValve2, leiLvmtr2));
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(
                        new RawDataLatestDto("T-IN-FRI-1", new BigDecimal("245.3"),
                                new BigDecimal("245.5"), FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-IN-PRI-1", new BigDecimal("2.45"),
                                new BigDecimal("2.46"), FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-OUT-FRI-1", new BigDecimal("180.7"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-VOI-1", new BigDecimal("75.2"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-LEI-1", new BigDecimal("5.32"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-IN-FRI-2", new BigDecimal("210.1"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-IN-PRI-2", new BigDecimal("2.10"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-OUT-FRI-2", new BigDecimal("160.4"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-VOI-2", new BigDecimal("60.0"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-LEI-2", new BigDecimal("4.80"),
                                null, FIXED_DTM, QualityCode.GOOD)));

        DwtGroupStateDto result = dwtStateService.findDwtStates("P1");

        assertThat(result.getParentFacilityId()).isEqualTo("P1");
        assertThat(result.getParentFacilityNm()).isEqualTo("성주정수장");
        assertThat(result.getDwts()).hasSize(2);

        DwtStateDto first = result.getDwts().get(0);
        assertThat(first.getFacilityId()).isEqualTo("D1");
        // 5번섹션 최소요구관압 2필드 응답 매핑 검증 (mockDwt 기본값)
        assertThat(first.getMinReqPrsr()).isEqualByComparingTo("1.5000");
        assertThat(first.getMinReqBranchPrsr()).isEqualByComparingTo("0.8000");
        assertThat(first.getInFlwmtr()).isNotNull();
        assertThat(first.getInFlwmtr().getFlwrtRawVal()).isEqualByComparingTo("245.3");
        assertThat(first.getInFlwmtr().getPrsrRawVal()).isEqualByComparingTo("2.45");
        assertThat(first.getOutFlwmtr()).isNotNull();
        assertThat(first.getOutFlwmtr().getFlwrtRawVal()).isEqualByComparingTo("180.7");
        assertThat(first.isMultipleInFlwmtrDetected()).isFalse();
        assertThat(first.isMultipleOutFlwmtrDetected()).isFalse();
        assertThat(first.getValves()).hasSize(1);
        assertThat(first.getValves().get(0).getOpngRawVal()).isEqualByComparingTo("75.2");
        assertThat(first.getLvmtrs()).hasSize(1);
        assertThat(first.getLvmtrs().get(0).getWtlvRawVal()).isEqualByComparingTo("5.32");
    }

    @Test
    void findDwtStates_부모미존재() {
        given(facilityRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> dwtStateService.findDwtStates("unknown"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void findDwtStates_자식DWT0건_빈리스트() {
        Facility parent = mockFacility("P1", "성주정수장", YnType.Y);
        given(facilityRepository.findById("P1")).willReturn(Optional.of(parent));
        given(facilityRepository.findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(
                eq("P1"), eq(FacilityType.DWT), eq(YnType.Y)))
                .willReturn(List.of());

        DwtGroupStateDto result = dwtStateService.findDwtStates("P1");

        assertThat(result.getParentFacilityId()).isEqualTo("P1");
        assertThat(result.getParentFacilityNm()).isEqualTo("성주정수장");
        assertThat(result.getDwts()).isEmpty();
    }

    @Test
    void findDwtStates_유입중복등록_플래그TRUE() {
        Facility parent = mockFacility("P1", "성주정수장", YnType.Y);
        DistributionWaterTank dwt = mockDwt("D1", "배수지A");

        FlowMeter inletA = mockFlwmtr("I-IN-A", "유입유량계A", dwt);
        FlowMeter inletB = mockFlwmtr("I-IN-B", "유입유량계B", dwt);

        Tag friA = mockTag("T-FRI-A", inletA, TagMeasurementType.FRI, IoCode.INPUT);
        Tag friB = mockTag("T-FRI-B", inletB, TagMeasurementType.FRI, IoCode.INPUT);

        given(facilityRepository.findById("P1")).willReturn(Optional.of(parent));
        given(facilityRepository.findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(
                eq("P1"), eq(FacilityType.DWT), eq(YnType.Y)))
                .willReturn(List.of(dwt));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(inletA, inletB));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friA, friB));
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(
                        new RawDataLatestDto("T-FRI-A", new BigDecimal("100.0"),
                                null, FIXED_DTM, QualityCode.GOOD),
                        new RawDataLatestDto("T-FRI-B", new BigDecimal("200.0"),
                                null, FIXED_DTM, QualityCode.GOOD)));

        DwtGroupStateDto result = dwtStateService.findDwtStates("P1");

        assertThat(result.getDwts()).hasSize(1);
        DwtStateDto state = result.getDwts().get(0);
        assertThat(state.isMultipleInFlwmtrDetected()).isTrue();
        assertThat(state.isMultipleOutFlwmtrDetected()).isFalse();
        assertThat(state.getInFlwmtr()).isNotNull();
        assertThat(state.getInFlwmtr().getInstrumentId()).isEqualTo("I-IN-A");
        assertThat(state.getInFlwmtr().getFlwrtRawVal()).isEqualByComparingTo("100.0");
        assertThat(state.getOutFlwmtr()).isNull();
    }

    @Test
    void findDwtStates_5번섹션_최소요구관압_필드가_응답에_노출된다() {
        Facility parent = mockFacility("P1", "성주정수장", YnType.Y);
        BigDecimal minReqPrsr = new BigDecimal("2.3000");
        BigDecimal minReqBranchPrsr = new BigDecimal("1.7000");
        DistributionWaterTank dwt = mockDwt("D1", "배수지A", minReqPrsr, minReqBranchPrsr);

        given(facilityRepository.findById("P1")).willReturn(Optional.of(parent));
        given(facilityRepository.findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(
                eq("P1"), eq(FacilityType.DWT), eq(YnType.Y)))
                .willReturn(List.of(dwt));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of());

        DwtGroupStateDto result = dwtStateService.findDwtStates("P1");

        assertThat(result.getDwts()).hasSize(1);
        DwtStateDto state = result.getDwts().get(0);
        assertThat(state.getFacilityNm()).isEqualTo("배수지A");
        assertThat(state.getMinReqPrsr()).isEqualByComparingTo(minReqPrsr);
        assertThat(state.getMinReqBranchPrsr()).isEqualByComparingTo(minReqBranchPrsr);
    }

    private Facility mockFacility(String facilityId, String facilityNm, YnType useYn) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(facilityNm);
        Mockito.lenient().when(facility.getUseYn()).thenReturn(useYn);
        return facility;
    }

    private DistributionWaterTank mockDwt(String facilityId, String facilityNm) {
        return mockDwt(facilityId, facilityNm, new BigDecimal("1.5000"), new BigDecimal("0.8000"));
    }

    private DistributionWaterTank mockDwt(
            String facilityId, String facilityNm, BigDecimal minReqPrsr, BigDecimal minReqBranchPrsr) {
        DistributionWaterTank dwt = Mockito.mock(DistributionWaterTank.class);
        Mockito.lenient().when(dwt.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(dwt.getFacilityNm()).thenReturn(facilityNm);
        Mockito.lenient().when(dwt.getFacilityType()).thenReturn(FacilityType.DWT);
        Mockito.lenient().when(dwt.getMinReqPrsr()).thenReturn(minReqPrsr);
        Mockito.lenient().when(dwt.getMinReqBranchPrsr()).thenReturn(minReqBranchPrsr);
        return dwt;
    }

    private FlowMeter mockFlwmtr(String instrumentId, String instrumentNm, Facility facility) {
        FlowMeter flwmtr = Mockito.mock(FlowMeter.class);
        Mockito.lenient().when(flwmtr.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(flwmtr.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(flwmtr.getEquipType()).thenReturn(EquipType.FLWMTR);
        Mockito.lenient().when(flwmtr.getFacility()).thenReturn(facility);
        return flwmtr;
    }

    private Valve mockValve(String instrumentId, String instrumentNm, Facility facility) {
        Valve valve = Mockito.mock(Valve.class);
        Mockito.lenient().when(valve.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(valve.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(valve.getEquipType()).thenReturn(EquipType.VALVE);
        Mockito.lenient().when(valve.getFacility()).thenReturn(facility);
        return valve;
    }

    private LevelMeter mockLvmtr(String instrumentId, String instrumentNm, Facility facility) {
        LevelMeter lvmtr = Mockito.mock(LevelMeter.class);
        Mockito.lenient().when(lvmtr.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(lvmtr.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(lvmtr.getEquipType()).thenReturn(EquipType.LVMTR);
        Mockito.lenient().when(lvmtr.getFacility()).thenReturn(facility);
        return lvmtr;
    }

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd, IoCode ioCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        Mockito.lenient().when(tag.getIoCd()).thenReturn(ioCd);
        return tag;
    }
}
