package com.mo.swtp.opt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.PredcEnergyBucketDto;
import com.mo.swtp.opt.dto.PumpEnergyPredictionDto;
import com.mo.swtp.opt.repository.PumpEnergyPredcRepository;
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
 * {@link PumpEnergyPredictionService} 단위 테스트.
 *
 * <p>전력피크분석-4번섹션 PLAN1 §성공 기준 — 다펌프 버킷 합산 / 시설 미존재·비활성 404 / 펌프 0대 빈 시계열 /
 * PWQ 태그 0개 빈 시계열 / 음수차분 버킷 제외 / 예측 0행 빈 시계열 + 2펌프 차분 후 합산 정확성.</p>
 */
@ExtendWith(MockitoExtension.class)
class PumpEnergyPredictionServiceTest {

    private static final String FACILITY_ID = "F-1";
    private static final LocalDateTime H1 = LocalDateTime.of(2026, 6, 5, 10, 0);
    private static final LocalDateTime H2 = LocalDateTime.of(2026, 6, 5, 11, 0);
    private static final LocalDateTime H3 = LocalDateTime.of(2026, 6, 5, 12, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PumpEnergyPredcRepository pumpEnergyPredcRepository;

    @InjectMocks
    private PumpEnergyPredictionService pumpEnergyPredictionService;

    @Test
    void 다펌프_버킷이_시설_단일_시계열로_합산되고_비펌프_비활성은_제외된다() {
        Facility facility = mockFacility(FACILITY_ID, "1정수지", YnType.Y);
        Instrument p1 = mockInstrument("I-P-1", EquipType.PUMP, YnType.Y);
        Instrument p2 = mockInstrument("I-P-2", EquipType.PUMP, YnType.Y);
        Instrument valve = mockInstrument("I-V-1", EquipType.VALVE, YnType.Y);         // PUMP 아님 — 제외
        Instrument inactivePump = mockInstrument("I-P-3", EquipType.PUMP, YnType.N);   // 비활성 — 제외
        Tag pwq1 = mockTag("T-PWQ-1", TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("T-PWQ-2", TagMeasurementType.PWQ);

        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.of(facility));
        given(instrumentRepository.findByFacilityFacilityId(FACILITY_ID))
                .willReturn(List.of(p1, p2, valve, inactivePump));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        given(pumpEnergyPredcRepository.findEnergyDeltaBuckets(anyList(), any(), any()))
                .willReturn(List.of(
                        bucket("T-PWQ-1", H1, "10"),
                        bucket("T-PWQ-1", H2, "12"),
                        bucket("T-PWQ-2", H1, "5")));   // H1 만 중첩, H2 는 p1 단독

        PumpEnergyPredictionDto result = pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID);

        assertThat(result.getFacilityId()).isEqualTo(FACILITY_ID);
        assertThat(result.getFacilityNm()).isEqualTo("1정수지");
        assertThat(result.getUnit()).isEqualTo("kWh");
        assertThat(result.getPoints()).hasSize(2);
        assertThat(result.getPoints().get(0).getBaseDtm()).isEqualTo(H1);
        assertThat(result.getPoints().get(0).getElcegVal()).isEqualByComparingTo("15");   // 10 + 5
        assertThat(result.getPoints().get(1).getBaseDtm()).isEqualTo(H2);
        assertThat(result.getPoints().get(1).getElcegVal()).isEqualByComparingTo("12");   // p1 단독
    }

    @Test
    void 시설이_존재하지_않으면_FACILITY_NOT_FOUND_예외가_발생한다() {
        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 비활성_시설이면_FACILITY_NOT_FOUND_예외가_발생한다() {
        Facility inactive = mockFacility(FACILITY_ID, "폐쇄정수지", YnType.N);
        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 활성_펌프가_0대면_빈_시계열을_반환한다() {
        Facility facility = mockFacility(FACILITY_ID, "1정수지", YnType.Y);
        Instrument valve = mockInstrument("I-V-1", EquipType.VALVE, YnType.Y);

        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.of(facility));
        given(instrumentRepository.findByFacilityFacilityId(FACILITY_ID)).willReturn(List.of(valve));

        PumpEnergyPredictionDto result = pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID);

        assertThat(result.getFacilityId()).isEqualTo(FACILITY_ID);
        assertThat(result.getPoints()).isEmpty();
    }

    @Test
    void PWQ_태그가_0개면_빈_시계열을_반환한다() {
        Facility facility = mockFacility(FACILITY_ID, "1정수지", YnType.Y);
        Instrument p1 = mockInstrument("I-P-1", EquipType.PUMP, YnType.Y);
        Tag pwi = mockTag("T-PWI-1", TagMeasurementType.PWI);   // PWQ 아님

        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.of(facility));
        given(instrumentRepository.findByFacilityFacilityId(FACILITY_ID)).willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwi));

        PumpEnergyPredictionDto result = pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID);

        assertThat(result.getPoints()).isEmpty();
    }

    @Test
    void 음수_차분_버킷은_해당_펌프만_합산에서_제외된다() {
        Facility facility = mockFacility(FACILITY_ID, "1정수지", YnType.Y);
        Instrument p1 = mockInstrument("I-P-1", EquipType.PUMP, YnType.Y);
        Instrument p2 = mockInstrument("I-P-2", EquipType.PUMP, YnType.Y);
        Tag pwq1 = mockTag("T-PWQ-1", TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("T-PWQ-2", TagMeasurementType.PWQ);

        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.of(facility));
        given(instrumentRepository.findByFacilityFacilityId(FACILITY_ID)).willReturn(List.of(p1, p2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        given(pumpEnergyPredcRepository.findEnergyDeltaBuckets(anyList(), any(), any()))
                .willReturn(List.of(
                        bucket("T-PWQ-1", H1, "10"),
                        bucket("T-PWQ-2", H1, "-2"),    // 적산 리셋 — 제외, H1 은 p1 단독 10
                        bucket("T-PWQ-1", H2, "-5"),    // 적산 리셋 — 제외
                        bucket("T-PWQ-2", H2, "8"),     // H2 는 p2 단독 8
                        bucket("T-PWQ-1", H3, "0")));    // 0 은 포함 (signum >= 0)

        PumpEnergyPredictionDto result = pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID);

        assertThat(result.getPoints()).hasSize(3);
        assertThat(result.getPoints().get(0).getBaseDtm()).isEqualTo(H1);
        assertThat(result.getPoints().get(0).getElcegVal()).isEqualByComparingTo("10");
        assertThat(result.getPoints().get(1).getBaseDtm()).isEqualTo(H2);
        assertThat(result.getPoints().get(1).getElcegVal()).isEqualByComparingTo("8");
        assertThat(result.getPoints().get(2).getBaseDtm()).isEqualTo(H3);
        assertThat(result.getPoints().get(2).getElcegVal()).isEqualByComparingTo("0");
    }

    @Test
    void 예측_데이터가_0행이면_빈_시계열을_반환한다() {
        Facility facility = mockFacility(FACILITY_ID, "1정수지", YnType.Y);
        Instrument p1 = mockInstrument("I-P-1", EquipType.PUMP, YnType.Y);
        Tag pwq1 = mockTag("T-PWQ-1", TagMeasurementType.PWQ);

        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.of(facility));
        given(instrumentRepository.findByFacilityFacilityId(FACILITY_ID)).willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1));
        given(pumpEnergyPredcRepository.findEnergyDeltaBuckets(anyList(), any(), any()))
                .willReturn(List.of());

        PumpEnergyPredictionDto result = pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID);

        assertThat(result.getFacilityId()).isEqualTo(FACILITY_ID);
        assertThat(result.getPoints()).isEmpty();
    }

    @Test
    void 두_펌프의_동일_버킷_차분이_시각별로_정확히_합산된다() {
        Facility facility = mockFacility(FACILITY_ID, "1정수지", YnType.Y);
        Instrument p1 = mockInstrument("I-P-1", EquipType.PUMP, YnType.Y);
        Instrument p2 = mockInstrument("I-P-2", EquipType.PUMP, YnType.Y);
        Tag pwq1 = mockTag("T-PWQ-1", TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("T-PWQ-2", TagMeasurementType.PWQ);

        given(facilityRepository.findById(FACILITY_ID)).willReturn(Optional.of(facility));
        given(instrumentRepository.findByFacilityFacilityId(FACILITY_ID)).willReturn(List.of(p1, p2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        // 펌프별로 이미 MAX-MIN 차분된 버킷값 — Service 는 시각별 합산만 수행 (SUM(MAX-MIN) 정합)
        given(pumpEnergyPredcRepository.findEnergyDeltaBuckets(anyList(), any(), any()))
                .willReturn(List.of(
                        bucket("T-PWQ-1", H1, "10.2500"),
                        bucket("T-PWQ-2", H1, "5.7500"),
                        bucket("T-PWQ-1", H2, "12.0000"),
                        bucket("T-PWQ-2", H2, "7.5000")));

        PumpEnergyPredictionDto result = pumpEnergyPredictionService.getPumpEnergyPrediction(FACILITY_ID);

        assertThat(result.getPoints()).hasSize(2);
        assertThat(result.getPoints().get(0).getBaseDtm()).isEqualTo(H1);
        assertThat(result.getPoints().get(0).getElcegVal()).isEqualByComparingTo("16.0000");   // 10.25 + 5.75
        assertThat(result.getPoints().get(1).getBaseDtm()).isEqualTo(H2);
        assertThat(result.getPoints().get(1).getElcegVal()).isEqualByComparingTo("19.5000");   // 12.0 + 7.5
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private Facility mockFacility(String facilityId, String facilityNm, YnType useYn) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(facilityNm);
        Mockito.lenient().when(facility.getUseYn()).thenReturn(useYn);
        return facility;
    }

    private Instrument mockInstrument(String instrumentId, EquipType equipType, YnType useYn) {
        Instrument instrument = Mockito.mock(Instrument.class);
        Mockito.lenient().when(instrument.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(instrument.getEquipType()).thenReturn(equipType);
        Mockito.lenient().when(instrument.getUseYn()).thenReturn(useYn);
        return instrument;
    }

    private Tag mockTag(String tagSrlNo, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }

    private PredcEnergyBucketDto bucket(String tagSrlNo, LocalDateTime baseDtm, String aggrVal) {
        return new PredcEnergyBucketDto(tagSrlNo, baseDtm, new BigDecimal(aggrVal));
    }
}
