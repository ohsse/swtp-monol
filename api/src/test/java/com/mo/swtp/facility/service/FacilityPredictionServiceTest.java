package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityPredictionDto;
import com.mo.swtp.facility.dto.FlwmtrPredictionDto;
import com.mo.swtp.facility.dto.PumpPredictionDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpOprtngType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredictionMatchDto;
import com.mo.swtp.opt.repository.TagPredictionRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link FacilityPredictionService} 단위 테스트.
 *
 * <p>송수펌프제어분석-7번섹션 PLAN1 §테스트 전략 §단위 테스트 시나리오:</p>
 * <ol>
 *   <li>시설 미존재 → FACILITY_NOT_FOUND</li>
 *   <li>비활성 시설 (use_yn = N) → FACILITY_NOT_FOUND</li>
 *   <li>활성 instrument 없으면 빈 List 반환</li>
 *   <li>유량계의 FRI/PRI 예측값 분리 매핑</li>
 *   <li>OPS 예측 1.0/0.0 → predcIsRunning true/false 변환</li>
 *   <li>근접매칭 윈도우 밖 (Repository 미반환) → 해당 필드 null</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class FacilityPredictionServiceTest {

    private static final LocalDateTime FIXED_PREDC_DTM = LocalDateTime.of(2026, 5, 18, 11, 30, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagPredictionRepository tagPredictionRepository;

    @InjectMocks
    private FacilityPredictionService facilityPredictionService;

    @BeforeEach
    void setUp() {
        // @Value 주입 필드를 단위 테스트용 5분 윈도우로 고정.
        ReflectionTestUtils.setField(facilityPredictionService, "matchWindowMinutes", 5);
    }

    @Test
    void 시설_미존재시_FACILITY_NOT_FOUND_예외가_발생한다() {
        given(facilityRepository.findById("unknown"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityPredictionService.findFacilityPrediction("unknown"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 비활성_시설은_FACILITY_NOT_FOUND_예외가_발생한다() {
        Facility inactive = mockFacility("F1", "정수지A", YnType.N);
        given(facilityRepository.findById("F1"))
                .willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityPredictionService.findFacilityPrediction("F1"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 활성_instrument가_없으면_빈_List를_반환한다() {
        Facility active = mockFacility("F1", "정수지A", YnType.Y);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of());

        FacilityPredictionDto result = facilityPredictionService.findFacilityPrediction("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getFlwmtrs()).isEmpty();
        assertThat(result.getPumps()).isEmpty();
    }

    @Test
    void 유량계의_FRI와_PRI_예측값은_별도_필드로_매핑된다() {
        Facility active = mockFacility("F1", "정수지A", YnType.Y);
        FlowMeter flwmtr = mockFlowMeter("I-FLWMTR-1", "유량계1");
        Tag friTag = mockTag("T-FRI-1", flwmtr, TagMeasurementType.FRI);
        Tag priTag = mockTag("T-PRI-1", flwmtr, TagMeasurementType.PRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(flwmtr));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friTag, priTag));
        given(tagPredictionRepository.findNearestByTagSrlNos(anyList(), anyInt()))
                .willReturn(List.of(
                        new TagPredictionMatchDto("T-FRI-1", FIXED_PREDC_DTM,
                                new BigDecimal("248.7")),
                        new TagPredictionMatchDto("T-PRI-1", FIXED_PREDC_DTM,
                                new BigDecimal("2.48"))));

        FacilityPredictionDto result = facilityPredictionService.findFacilityPrediction("F1");

        assertThat(result.getFlwmtrs()).hasSize(1);
        FlwmtrPredictionDto fm = result.getFlwmtrs().get(0);
        assertThat(fm.getFlwrtPredcVal()).isEqualByComparingTo("248.7");
        assertThat(fm.getFlwrtPredcDtm()).isEqualTo(FIXED_PREDC_DTM);
        assertThat(fm.getPrsrPredcVal()).isEqualByComparingTo("2.48");
        assertThat(fm.getPrsrPredcDtm()).isEqualTo(FIXED_PREDC_DTM);
    }

    @Test
    void OPS_예측_1과_0은_predcIsRunning_true와_false로_변환된다() {
        Facility active = mockFacility("F1", "정수지A", YnType.Y);
        Pump pumpOn = mockPump("I-PUMP-1", "송수펌프1", PumpOprtngType.AUTO_CAPABLE);
        Pump pumpOff = mockPump("I-PUMP-2", "송수펌프2", PumpOprtngType.AUTO_CAPABLE);
        Tag opsOnTag = mockTag("T-OPS-1", pumpOn, TagMeasurementType.OPS);
        Tag opsOffTag = mockTag("T-OPS-2", pumpOff, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(pumpOn, pumpOff));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsOnTag, opsOffTag));
        given(tagPredictionRepository.findNearestByTagSrlNos(anyList(), anyInt()))
                .willReturn(List.of(
                        new TagPredictionMatchDto("T-OPS-1", FIXED_PREDC_DTM, BigDecimal.ONE),
                        new TagPredictionMatchDto("T-OPS-2", FIXED_PREDC_DTM, BigDecimal.ZERO)));

        FacilityPredictionDto result = facilityPredictionService.findFacilityPrediction("F1");

        assertThat(result.getPumps()).hasSize(2);
        assertThat(result.getPumps())
                .extracting(PumpPredictionDto::getInstrumentId, PumpPredictionDto::getPredcIsRunning)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("I-PUMP-1", Boolean.TRUE),
                        org.assertj.core.groups.Tuple.tuple("I-PUMP-2", Boolean.FALSE));
    }

    @Test
    void 윈도우_밖_태그는_predcVal과_predcDtm이_null로_반환된다() {
        // FRI 만 근접행 반환, PRI 는 윈도우 밖이라 Repository 결과에서 누락.
        Facility active = mockFacility("F1", "정수지A", YnType.Y);
        FlowMeter flwmtr = mockFlowMeter("I-FLWMTR-1", "유량계1");
        Tag friTag = mockTag("T-FRI-1", flwmtr, TagMeasurementType.FRI);
        Tag priTag = mockTag("T-PRI-1", flwmtr, TagMeasurementType.PRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(flwmtr));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friTag, priTag));
        // PRI 누락 — 윈도우 밖 결측 시뮬레이션 (LATERAL 미반환).
        given(tagPredictionRepository.findNearestByTagSrlNos(anyList(), anyInt()))
                .willReturn(List.of(
                        new TagPredictionMatchDto("T-FRI-1", FIXED_PREDC_DTM,
                                new BigDecimal("248.7"))));

        FacilityPredictionDto result = facilityPredictionService.findFacilityPrediction("F1");

        assertThat(result.getFlwmtrs()).hasSize(1);
        FlwmtrPredictionDto fm = result.getFlwmtrs().get(0);
        assertThat(fm.getFlwrtPredcVal()).isEqualByComparingTo("248.7");
        assertThat(fm.getFlwrtPredcDtm()).isEqualTo(FIXED_PREDC_DTM);
        assertThat(fm.getPrsrPredcVal()).isNull();
        assertThat(fm.getPrsrPredcDtm()).isNull();
    }

    private Facility mockFacility(String facilityId, String facilityNm, YnType useYn) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(facilityNm);
        Mockito.lenient().when(facility.getUseYn()).thenReturn(useYn);
        return facility;
    }

    private Pump mockPump(String instrumentId, String instrumentNm, PumpOprtngType oprtngType) {
        Pump pump = Mockito.mock(Pump.class);
        Mockito.lenient().when(pump.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(pump.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(pump.getEquipType()).thenReturn(EquipType.PUMP);
        Mockito.lenient().when(pump.getOprtngType()).thenReturn(oprtngType);
        return pump;
    }

    private FlowMeter mockFlowMeter(String instrumentId, String instrumentNm) {
        FlowMeter flwmtr = Mockito.mock(FlowMeter.class);
        Mockito.lenient().when(flwmtr.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(flwmtr.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(flwmtr.getEquipType()).thenReturn(EquipType.FLWMTR);
        return flwmtr;
    }

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }
}
