package com.mo.swtp.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.InqUnit;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.dto.PumpFrequencyTimeSeriesDto;
import com.mo.swtp.instrument.dto.PumpTimeSeriesSearchDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PumpFrequencyTimeSeriesService} 단위 테스트.
 *
 * <p>송수펌프가동이력_3번섹션 PLAN1 §성공 기준 4 케이스 — 인버터만포함(정격제외) / AVG버킷 펌프별매핑 /
 * from&gt;to 예외 / 인버터0대 빈리스트.</p>
 */
@ExtendWith(MockitoExtension.class)
class PumpFrequencyTimeSeriesServiceTest {

    private static final LocalDateTime D1 = LocalDateTime.of(2024, 7, 1, 0, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2024, 7, 2, 0, 0);

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private PumpFrequencyTimeSeriesService pumpFrequencyTimeSeriesService;

    @Test
    void 인버터_펌프만_포함하고_정격_펌프는_제외한다() {
        Pump inverter = mockPump("I-P-1", "송수1호기", PumpDriveType.INVERTER_DRIVE);
        Pump rated = mockPump("I-P-2", "송수2호기", PumpDriveType.RATED_DRIVE);
        Tag fqiInverter = mockTag("T-FQI-1", inverter, TagMeasurementType.FQI);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(inverter, rated));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(fqiInverter));
        given(rawDataRepository.findAvgValueBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(bucket("T-FQI-1", D1, "45.0")));

        List<PumpFrequencyTimeSeriesDto> result =
                pumpFrequencyTimeSeriesService.findPumpFrequencyTimeSeries(validSearch());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPumpId()).isEqualTo("I-P-1");
        assertThat(result.get(0).getUnit()).isEqualTo("Hz");
    }

    @Test
    void 인버터_펌프의_FQI_AVG_버킷이_시계열_포인트로_매핑된다() {
        Pump inverter = mockPump("I-P-1", "송수1호기", PumpDriveType.INVERTER_DRIVE);
        Tag fqi = mockTag("T-FQI-1", inverter, TagMeasurementType.FQI);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(inverter));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(fqi));
        given(rawDataRepository.findAvgValueBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-FQI-1", D1, "45.2"),
                        bucket("T-FQI-1", D2, "46.8")));

        List<PumpFrequencyTimeSeriesDto> result =
                pumpFrequencyTimeSeriesService.findPumpFrequencyTimeSeries(validSearch());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoints()).hasSize(2);
        assertThat(result.get(0).getPoints().get(0).getBaseDtm()).isEqualTo(D1);
        assertThat(result.get(0).getPoints().get(0).getFreqVal()).isEqualByComparingTo("45.2");
        assertThat(result.get(0).getPoints().get(1).getFreqVal()).isEqualByComparingTo("46.8");
    }

    @Test
    void from_이_to_보다_늦으면_INVALID_INQ_PERIOD_예외가_발생한다() {
        PumpTimeSeriesSearchDto invalid = new PumpTimeSeriesSearchDto();
        invalid.setInqUnit(InqUnit.DAY);
        invalid.setFromDt(LocalDate.of(2024, 7, 10));
        invalid.setToDt(LocalDate.of(2024, 7, 1));

        assertThatThrownBy(() -> pumpFrequencyTimeSeriesService.findPumpFrequencyTimeSeries(invalid))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
    }

    @Test
    void 인버터_펌프가_0대면_빈_리스트를_반환한다() {
        Pump rated = mockPump("I-P-1", "송수1호기", PumpDriveType.RATED_DRIVE);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(rated));

        List<PumpFrequencyTimeSeriesDto> result =
                pumpFrequencyTimeSeriesService.findPumpFrequencyTimeSeries(validSearch());

        assertThat(result).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private PumpTimeSeriesSearchDto validSearch() {
        PumpTimeSeriesSearchDto search = new PumpTimeSeriesSearchDto();
        search.setInqUnit(InqUnit.DAY);
        search.setFromDt(LocalDate.of(2024, 7, 1));
        search.setToDt(LocalDate.of(2024, 7, 9));
        return search;
    }

    private Pump mockPump(String instrumentId, String instrumentNm, PumpDriveType driveType) {
        Pump pump = Mockito.mock(Pump.class);
        Mockito.lenient().when(pump.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(pump.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(pump.getDriveType()).thenReturn(driveType);
        return pump;
    }

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }

    private RawDataBucketDto bucket(String tagSrlNo, LocalDateTime baseDtm, String aggrVal) {
        return new RawDataBucketDto(tagSrlNo, baseDtm, new BigDecimal(aggrVal));
    }
}
