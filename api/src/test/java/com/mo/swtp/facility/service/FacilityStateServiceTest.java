package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityStateDto;
import com.mo.swtp.facility.dto.FlwmtrStateDto;
import com.mo.swtp.facility.dto.PumpStateDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpOprtngType;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityStateService} 단위 테스트.
 *
 * <p>송수펌프제어분석-3번섹션 PLAN1 §테스트 전략 §단위 테스트 6 시나리오:</p>
 * <ol>
 *   <li>시설 미존재 → FACILITY_NOT_FOUND</li>
 *   <li>비활성 시설 (use_yn = N) → FACILITY_NOT_FOUND</li>
 *   <li>BAD QUALITY 태그도 그대로 반환</li>
 *   <li>활성 instrument 없으면 빈 List 반환</li>
 *   <li>FRI/PRI 분리 매핑</li>
 *   <li>OPS 1.0/0.0 → isRunning true/false 변환</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class FacilityStateServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private FacilityStateService facilityStateService;

    @Test
    void 시설_미존재시_FACILITY_NOT_FOUND_예외가_발생한다() {
        given(facilityRepository.findById("unknown"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityStateService.findFacilityState("unknown"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 비활성_시설은_FACILITY_NOT_FOUND_예외가_발생한다() {
        Facility inactive = mockFacility("F1", "정수지A", YnType.N);
        given(facilityRepository.findById("F1"))
                .willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityStateService.findFacilityState("F1"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void BAD_QUALITY_태그도_그대로_반환한다() {
        Facility active = mockFacility("F1", "정수지A", YnType.Y);
        Pump pump = mockPump("I-PUMP-1", "송수펌프1", PumpOprtngType.AUTO_CAPABLE);
        Tag opsTag = mockTag("T-OPS-1", pump, TagMeasurementType.OPS);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(pump));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsTag));
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(new RawDataLatestDto(
                        "T-OPS-1", BigDecimal.ONE, null,
                        LocalDateTime.of(2026, 5, 13, 10, 30, 0), QualityCode.BAD)));

        FacilityStateDto result = facilityStateService.findFacilityState("F1");

        assertThat(result.getPumps()).hasSize(1);
        PumpStateDto pumpState = result.getPumps().get(0);
        assertThat(pumpState.getQualityCd()).isEqualTo(QualityCode.BAD);
        assertThat(pumpState.getIsRunning()).isTrue();
    }

    @Test
    void 활성_instrument가_없으면_빈_List를_반환한다() {
        Facility active = mockFacility("F1", "정수지A", YnType.Y);
        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of());

        FacilityStateDto result = facilityStateService.findFacilityState("F1");

        assertThat(result.getFacilityId()).isEqualTo("F1");
        assertThat(result.getFacilityNm()).isEqualTo("정수지A");
        assertThat(result.getFlwmtrs()).isEmpty();
        assertThat(result.getPumps()).isEmpty();
    }

    @Test
    void 유량계의_FRI와_PRI는_별도_필드로_매핑된다() {
        Facility active = mockFacility("F1", "정수지A", YnType.Y);
        FlowMeter flwmtr = mockFlowMeter("I-FLWMTR-1", "유량계1");
        Tag friTag = mockTag("T-FRI-1", flwmtr, TagMeasurementType.FRI);
        Tag priTag = mockTag("T-PRI-1", flwmtr, TagMeasurementType.PRI);

        given(facilityRepository.findById("F1")).willReturn(Optional.of(active));
        given(instrumentRepository.findByFacilityIdAndEquipType(any(), anyList()))
                .willReturn(List.of(flwmtr));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friTag, priTag));
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(
                        new RawDataLatestDto("T-FRI-1",
                                new BigDecimal("245.3"), new BigDecimal("245.5"),
                                LocalDateTime.of(2026, 5, 13, 10, 30, 0), QualityCode.GOOD),
                        new RawDataLatestDto("T-PRI-1",
                                new BigDecimal("2.45"), new BigDecimal("2.46"),
                                LocalDateTime.of(2026, 5, 13, 10, 30, 0), QualityCode.GOOD)));

        FacilityStateDto result = facilityStateService.findFacilityState("F1");

        assertThat(result.getFlwmtrs()).hasSize(1);
        FlwmtrStateDto fm = result.getFlwmtrs().get(0);
        assertThat(fm.getFlwrtRawVal()).isEqualByComparingTo("245.3");
        assertThat(fm.getFlwrtCorrVal()).isEqualByComparingTo("245.5");
        assertThat(fm.getFlwrtQualityCd()).isEqualTo(QualityCode.GOOD);
        assertThat(fm.getPrsrRawVal()).isEqualByComparingTo("2.45");
        assertThat(fm.getPrsrCorrVal()).isEqualByComparingTo("2.46");
        assertThat(fm.getPrsrQualityCd()).isEqualTo(QualityCode.GOOD);
    }

    @Test
    void OPS_가동상태_1과_0은_isRunning_true와_false로_변환된다() {
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
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(
                        new RawDataLatestDto("T-OPS-1", BigDecimal.ONE, null,
                                LocalDateTime.of(2026, 5, 13, 10, 30, 0), QualityCode.GOOD),
                        new RawDataLatestDto("T-OPS-2", BigDecimal.ZERO, null,
                                LocalDateTime.of(2026, 5, 13, 10, 30, 0), QualityCode.GOOD)));

        FacilityStateDto result = facilityStateService.findFacilityState("F1");

        assertThat(result.getPumps()).hasSize(2);
        assertThat(result.getPumps())
                .extracting(PumpStateDto::getInstrumentId, PumpStateDto::getIsRunning)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("I-PUMP-1", Boolean.TRUE),
                        org.assertj.core.groups.Tuple.tuple("I-PUMP-2", Boolean.FALSE));
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
