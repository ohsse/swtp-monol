package com.mo.swtp.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.mo.swtp.common.enumtype.InqUnit;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.dto.PumpPowerTimeSeriesDto;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PumpPowerTimeSeriesService} 단위 테스트.
 *
 * <p>송수펌프가동이력_3번섹션 PLAN1 §성공 기준 6 케이스 — 활성0대 빈리스트 / 2펌프 계열분리 / 음수버킷 생략 /
 * from&gt;to 예외 / PWQ태그없는펌프 points빈배열 / inqUnit=MONTH→repo dateTruncUnit "month" 전달.</p>
 */
@ExtendWith(MockitoExtension.class)
class PumpPowerTimeSeriesServiceTest {

    private static final LocalDateTime D1 = LocalDateTime.of(2024, 7, 1, 0, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2024, 7, 2, 0, 0);
    private static final LocalDateTime D3 = LocalDateTime.of(2024, 7, 3, 0, 0);

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private PumpPowerTimeSeriesService pumpPowerTimeSeriesService;

    @Test
    void 활성_펌프가_0대면_빈_리스트를_반환한다() {
        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of());

        List<PumpPowerTimeSeriesDto> result =
                pumpPowerTimeSeriesService.findPumpPowerTimeSeries(validSearch());

        assertThat(result).isEmpty();
    }

    @Test
    void 두_펌프의_PWQ_버킷이_펌프별_계열로_분리된다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Pump p2 = mockPump("I-P-2", "송수2호기");
        Tag pwq1 = mockTag("T-PWQ-1", p1, TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("T-PWQ-2", p2, TagMeasurementType.PWQ);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1, p2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-PWQ-1", D1, "100"),
                        bucket("T-PWQ-1", D2, "120"),
                        bucket("T-PWQ-2", D1, "80")));

        List<PumpPowerTimeSeriesDto> result =
                pumpPowerTimeSeriesService.findPumpPowerTimeSeries(validSearch());

        assertThat(result).hasSize(2);
        PumpPowerTimeSeriesDto dto1 = result.get(0);
        assertThat(dto1.getPumpId()).isEqualTo("I-P-1");
        assertThat(dto1.getPumpNm()).isEqualTo("송수1호기");
        assertThat(dto1.getUnit()).isEqualTo("kWh");
        assertThat(dto1.getPoints()).hasSize(2);
        assertThat(dto1.getPoints().get(0).getBaseDtm()).isEqualTo(D1);
        assertThat(dto1.getPoints().get(0).getElcegVal()).isEqualByComparingTo("100");
        assertThat(dto1.getPoints().get(1).getElcegVal()).isEqualByComparingTo("120");

        PumpPowerTimeSeriesDto dto2 = result.get(1);
        assertThat(dto2.getPumpId()).isEqualTo("I-P-2");
        assertThat(dto2.getPoints()).hasSize(1);
        assertThat(dto2.getPoints().get(0).getElcegVal()).isEqualByComparingTo("80");
    }

    @Test
    void 음수_차분_버킷은_생략하고_0_이상_버킷만_포함한다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag pwq1 = mockTag("T-PWQ-1", p1, TagMeasurementType.PWQ);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-PWQ-1", D1, "100"),
                        bucket("T-PWQ-1", D2, "-5"),   // 적산 리셋·롤오버 — 생략
                        bucket("T-PWQ-1", D3, "0")));   // 0 은 포함 (signum >= 0)

        List<PumpPowerTimeSeriesDto> result =
                pumpPowerTimeSeriesService.findPumpPowerTimeSeries(validSearch());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoints()).hasSize(2);
        assertThat(result.get(0).getPoints().get(0).getBaseDtm()).isEqualTo(D1);
        assertThat(result.get(0).getPoints().get(0).getElcegVal()).isEqualByComparingTo("100");
        assertThat(result.get(0).getPoints().get(1).getBaseDtm()).isEqualTo(D3);
        assertThat(result.get(0).getPoints().get(1).getElcegVal()).isEqualByComparingTo("0");
    }

    @Test
    void from_이_to_보다_늦으면_INVALID_INQ_PERIOD_예외가_발생한다() {
        PumpTimeSeriesSearchDto invalid = new PumpTimeSeriesSearchDto();
        invalid.setInqUnit(InqUnit.DAY);
        invalid.setFromDt(LocalDate.of(2024, 7, 10));
        invalid.setToDt(LocalDate.of(2024, 7, 1));

        assertThatThrownBy(() -> pumpPowerTimeSeriesService.findPumpPowerTimeSeries(invalid))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
    }

    @Test
    void PWQ_태그가_없는_펌프는_points_빈_배열을_반환한다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of());   // PWQ 태그 부재
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of());

        List<PumpPowerTimeSeriesDto> result =
                pumpPowerTimeSeriesService.findPumpPowerTimeSeries(validSearch());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPumpId()).isEqualTo("I-P-1");
        assertThat(result.get(0).getPoints()).isEmpty();
    }

    @Test
    void 조회단위가_MONTH면_repo에_dateTruncUnit_month와_기간_경계를_전달한다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag pwq1 = mockTag("T-PWQ-1", p1, TagMeasurementType.PWQ);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of());

        PumpTimeSeriesSearchDto search = new PumpTimeSeriesSearchDto();
        search.setInqUnit(InqUnit.MONTH);
        search.setFromDt(LocalDate.of(2024, 1, 1));
        search.setToDt(LocalDate.of(2024, 12, 31));

        pumpPowerTimeSeriesService.findPumpPowerTimeSeries(search);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> unitCaptor = ArgumentCaptor.forClass(String.class);
        then(rawDataRepository).should().findEnergyDeltaBuckets(
                anyList(), startCaptor.capture(), endCaptor.capture(), unitCaptor.capture());

        assertThat(unitCaptor.getValue()).isEqualTo("month");
        assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
        // 종료일 포함 → 익일 자정 배타적 상한
        assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));
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

    private Pump mockPump(String instrumentId, String instrumentNm) {
        Pump pump = Mockito.mock(Pump.class);
        Mockito.lenient().when(pump.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(pump.getInstrumentNm()).thenReturn(instrumentNm);
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
