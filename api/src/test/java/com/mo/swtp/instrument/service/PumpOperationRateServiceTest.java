package com.mo.swtp.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.dto.PumpOperationRateDto;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PumpOperationRateService} 단위 테스트.
 *
 * <p>송수펌프가동이력_2번섹션 PLAN1 §성공 기준 7 케이스 — 정격 4종(On→100·Off→0·BAD→null+quality·태그없음→null)
 * + 인버터 3종(GOOD→effectiveVal%·BAD→null+quality·태그없음→null).</p>
 */
@ExtendWith(MockitoExtension.class)
class PumpOperationRateServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 2, 10, 30, 0);
    private static final BigDecimal RATED_HEAD = new BigDecimal("65.0");
    private static final BigDecimal RATED_FLWRT = new BigDecimal("250.0");

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private PumpOperationRateService pumpOperationRateService;

    @Test
    void 정격펌프_OPS_GOOD_On_이면_가동률_100_을_반환한다() {
        Pump p1 = mockPump("I-P-1", "송수1호기", PumpDriveType.RATED_DRIVE);
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ONE, NOW)));

        List<PumpOperationRateDto> result = pumpOperationRateService.findPumpOperationRates();

        assertThat(result).hasSize(1);
        PumpOperationRateDto card = result.get(0);
        assertThat(card.getPumpId()).isEqualTo("I-P-1");
        assertThat(card.getPumpNm()).isEqualTo("송수1호기");
        assertThat(card.getDriveType()).isEqualTo(PumpDriveType.RATED_DRIVE);
        assertThat(card.getRatedHead()).isEqualByComparingTo(RATED_HEAD);
        assertThat(card.getRatedFlwrt()).isEqualByComparingTo(RATED_FLWRT);
        assertThat(card.getOprtngRate()).isEqualByComparingTo("100");
        assertThat(card.getQualityCd()).isEqualTo(QualityCode.GOOD);
        assertThat(card.getAcqDtm()).isEqualTo(NOW);
    }

    @Test
    void 정격펌프_OPS_GOOD_Off_이면_가동률_0_을_반환한다() {
        Pump p1 = mockPump("I-P-1", "송수1호기", PumpDriveType.RATED_DRIVE);
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                goodLatest("T-OPS-P1", BigDecimal.ZERO, NOW)));

        List<PumpOperationRateDto> result = pumpOperationRateService.findPumpOperationRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOprtngRate()).isEqualByComparingTo("0");
        assertThat(result.get(0).getQualityCd()).isEqualTo(QualityCode.GOOD);
    }

    @Test
    void 정격펌프_OPS_BAD_이면_가동률_null_이고_품질_수집시각을_동봉한다() {
        Pump p1 = mockPump("I-P-1", "송수1호기", PumpDriveType.RATED_DRIVE);
        Tag opsP1 = mockTag("T-OPS-P1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(opsP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                new RawDataLatestDto("T-OPS-P1", BigDecimal.ONE, null, NOW, QualityCode.BAD)));

        List<PumpOperationRateDto> result = pumpOperationRateService.findPumpOperationRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOprtngRate()).isNull();
        assertThat(result.get(0).getQualityCd()).isEqualTo(QualityCode.BAD);  // 판정 태그 품질 동봉
        assertThat(result.get(0).getAcqDtm()).isEqualTo(NOW);
    }

    @Test
    void 정격펌프_OPS_태그가_없으면_가동률_null_이고_품질_수집시각도_null() {
        Pump p1 = mockPump("I-P-1", "송수1호기", PumpDriveType.RATED_DRIVE);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of());   // OPS 태그 부재
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of());

        List<PumpOperationRateDto> result = pumpOperationRateService.findPumpOperationRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOprtngRate()).isNull();
        assertThat(result.get(0).getQualityCd()).isNull();
        assertThat(result.get(0).getAcqDtm()).isNull();
    }

    @Test
    void 인버터펌프_FQI_GOOD_이면_현재_주파수값을_그대로_가동률로_반환한다() {
        // corrVal(46.0) 우선 — effectiveVal(corrVal ?? rawVal) 검증
        Pump p1 = mockPump("I-P-1", "송수2호기", PumpDriveType.INVERTER_DRIVE);
        Tag fqiP1 = mockTag("T-FQI-P1", p1, TagMeasurementType.FQI);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(fqiP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                new RawDataLatestDto("T-FQI-P1", new BigDecimal("45.0"), new BigDecimal("46.0"),
                        NOW, QualityCode.GOOD)));

        List<PumpOperationRateDto> result = pumpOperationRateService.findPumpOperationRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriveType()).isEqualTo(PumpDriveType.INVERTER_DRIVE);
        assertThat(result.get(0).getOprtngRate()).isEqualByComparingTo("46.0");  // corrVal 우선
        assertThat(result.get(0).getQualityCd()).isEqualTo(QualityCode.GOOD);
        assertThat(result.get(0).getAcqDtm()).isEqualTo(NOW);
    }

    @Test
    void 인버터펌프_FQI_BAD_이면_가동률_null_이고_품질_수집시각을_동봉한다() {
        Pump p1 = mockPump("I-P-1", "송수2호기", PumpDriveType.INVERTER_DRIVE);
        Tag fqiP1 = mockTag("T-FQI-P1", p1, TagMeasurementType.FQI);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(fqiP1));
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                new RawDataLatestDto("T-FQI-P1", new BigDecimal("45.0"), null, NOW, QualityCode.BAD)));

        List<PumpOperationRateDto> result = pumpOperationRateService.findPumpOperationRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOprtngRate()).isNull();
        assertThat(result.get(0).getQualityCd()).isEqualTo(QualityCode.BAD);
        assertThat(result.get(0).getAcqDtm()).isEqualTo(NOW);
    }

    @Test
    void 인버터펌프_FQI_태그가_없으면_가동률_null_이고_품질_수집시각도_null() {
        Pump p1 = mockPump("I-P-1", "송수2호기", PumpDriveType.INVERTER_DRIVE);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of());   // FQI 태그 부재
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of());

        List<PumpOperationRateDto> result = pumpOperationRateService.findPumpOperationRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOprtngRate()).isNull();
        assertThat(result.get(0).getQualityCd()).isNull();
        assertThat(result.get(0).getAcqDtm()).isNull();
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private Pump mockPump(String instrumentId, String instrumentNm, PumpDriveType driveType) {
        Pump pump = Mockito.mock(Pump.class);
        Mockito.lenient().when(pump.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(pump.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(pump.getDriveType()).thenReturn(driveType);
        Mockito.lenient().when(pump.getRatedHead()).thenReturn(RATED_HEAD);
        Mockito.lenient().when(pump.getRatedFlwrt()).thenReturn(RATED_FLWRT);
        return pump;
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
