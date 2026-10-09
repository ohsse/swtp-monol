package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.PumpSummaryDto;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PumpSummaryService} 단위 테스트.
 *
 * <p>송수펌프제어분석-6번섹션 PLAN1 §성공 기준:</p>
 * <ol>
 *   <li>hasPump=true 시설 전체를 정렬 순서 보존하여 요약 응답 (SQL 4회)</li>
 *   <li>OPS 품질별 운전중/신뢰불가 대수 분류 (GOOD&amp;1.0 / GOOD&amp;0.0 / BAD / UNCERTAIN / null / 판정불가)</li>
 *   <li>토출관압 FLWMTR/PRI 다중 등록 시 첫 매치 + multiplePrsrDetected=true</li>
 *   <li>빈 시설 목록 시 하위 조회 미호출 + 빈 리스트 반환</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PumpSummaryServiceTest {

    private static final LocalDateTime ACQ = LocalDateTime.of(2026, 5, 18, 10, 30, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private PumpSummaryService pumpSummaryService;

    @Test
    void hasPump_시설_전체를_정렬순서_보존하여_요약하고_SQL은_4회_발행한다() {
        Facility f1 = mockFacility("F1", "성주정수장");
        Facility f2 = mockFacility("F2", "월항가압장");
        FlowMeter fm1 = mockFlowMeter("I-FM-1", f1);
        Pump pump1 = mockPump("I-PUMP-1", f1);
        FlowMeter fm2 = mockFlowMeter("I-FM-2", f2);
        Pump pump2 = mockPump("I-PUMP-2", f2);
        Tag pri1 = mockTag("T-PRI-1", fm1, TagMeasurementType.PRI);
        Tag ops1 = mockTag("T-OPS-1", pump1, TagMeasurementType.OPS);
        Tag pri2 = mockTag("T-PRI-2", fm2, TagMeasurementType.PRI);
        Tag ops2 = mockTag("T-OPS-2", pump2, TagMeasurementType.OPS);

        given(facilityRepository.findFacilities(any())).willReturn(List.of(f1, f2));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fm1, pump1, fm2, pump2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pri1, ops1, pri2, ops2));
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(
                        new RawDataLatestDto("T-PRI-1",
                                new BigDecimal("2.45"), new BigDecimal("2.46"), ACQ, QualityCode.GOOD),
                        new RawDataLatestDto("T-OPS-1", BigDecimal.ONE, null, ACQ, QualityCode.GOOD),
                        new RawDataLatestDto("T-PRI-2",
                                new BigDecimal("3.10"), null, ACQ, QualityCode.GOOD),
                        new RawDataLatestDto("T-OPS-2", BigDecimal.ZERO, null, ACQ, QualityCode.GOOD)));

        List<PumpSummaryDto> result = pumpSummaryService.findPumpSummaries();

        assertThat(result).extracting(PumpSummaryDto::getFacilityId)
                .containsExactly("F1", "F2");
        PumpSummaryDto s1 = result.get(0);
        assertThat(s1.getFacilityNm()).isEqualTo("성주정수장");
        assertThat(s1.getPrsrRawVal()).isEqualByComparingTo("2.45");
        assertThat(s1.getPrsrCorrVal()).isEqualByComparingTo("2.46");
        assertThat(s1.getPrsrQualityCd()).isEqualTo(QualityCode.GOOD);
        assertThat(s1.isMultiplePrsrDetected()).isFalse();
        assertThat(s1.getOprtngPumpCnt()).isEqualTo(1);
        assertThat(s1.getUnknownPumpCnt()).isZero();
        PumpSummaryDto s2 = result.get(1);
        assertThat(s2.getPrsrRawVal()).isEqualByComparingTo("3.10");
        assertThat(s2.getOprtngPumpCnt()).isZero();
        assertThat(s2.getUnknownPumpCnt()).isZero();

        verify(facilityRepository, times(1)).findFacilities(any());
        verify(instrumentRepository, times(1))
                .findByFacilityIdInAndEquipType(anyList(), anyList());
        verify(tagRepository, times(1))
                .findByInstrumentInstrumentIdInAndUseYn(anyList(), any());
        verify(rawDataRepository, times(1)).findLatestByTagSrlNos(anyList());
    }

    @Test
    void OPS_품질별로_운전중과_신뢰불가_대수가_정확히_분류된다() {
        Facility f1 = mockFacility("F1", "성주정수장");
        Pump pOperating = mockPump("P-1", f1);   // GOOD & 1.0  → 운전중
        Pump pStopped = mockPump("P-2", f1);     // GOOD & 0.0  → 미증가
        Pump pBad = mockPump("P-3", f1);         // BAD         → 신뢰불가
        Pump pUncertain = mockPump("P-4", f1);   // UNCERTAIN   → 신뢰불가
        Pump pMissing = mockPump("P-5", f1);     // 결측         → 신뢰불가
        Pump pIndeterminate = mockPump("P-6", f1); // GOOD & 2.0 → 판정불가 → 신뢰불가
        Tag t1 = mockTag("T-1", pOperating, TagMeasurementType.OPS);
        Tag t2 = mockTag("T-2", pStopped, TagMeasurementType.OPS);
        Tag t3 = mockTag("T-3", pBad, TagMeasurementType.OPS);
        Tag t4 = mockTag("T-4", pUncertain, TagMeasurementType.OPS);
        Tag t5 = mockTag("T-5", pMissing, TagMeasurementType.OPS);
        Tag t6 = mockTag("T-6", pIndeterminate, TagMeasurementType.OPS);

        given(facilityRepository.findFacilities(any())).willReturn(List.of(f1));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(pOperating, pStopped, pBad, pUncertain, pMissing, pIndeterminate));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(t1, t2, t3, t4, t5, t6));
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(
                        new RawDataLatestDto("T-1", BigDecimal.ONE, null, ACQ, QualityCode.GOOD),
                        new RawDataLatestDto("T-2", BigDecimal.ZERO, null, ACQ, QualityCode.GOOD),
                        new RawDataLatestDto("T-3", BigDecimal.ONE, null, ACQ, QualityCode.BAD),
                        new RawDataLatestDto("T-4", BigDecimal.ONE, null, ACQ, QualityCode.UNCERTAIN),
                        // T-5 결측 (응답 목록에 없음)
                        new RawDataLatestDto("T-6",
                                new BigDecimal("2.0"), null, ACQ, QualityCode.GOOD)));

        List<PumpSummaryDto> result = pumpSummaryService.findPumpSummaries();

        assertThat(result).hasSize(1);
        PumpSummaryDto s = result.get(0);
        assertThat(s.getOprtngPumpCnt()).isEqualTo(1);
        assertThat(s.getUnknownPumpCnt()).isEqualTo(4);
    }

    @Test
    void 토출관압_다중_등록시_첫매치값과_multiplePrsrDetected_true를_반환한다() {
        Facility f1 = mockFacility("F1", "성주정수장");
        FlowMeter fm1 = mockFlowMeter("I-FM-1", f1);
        FlowMeter fm2 = mockFlowMeter("I-FM-2", f1);
        Tag pri1 = mockTag("T-PRI-1", fm1, TagMeasurementType.PRI);
        Tag pri2 = mockTag("T-PRI-2", fm2, TagMeasurementType.PRI);

        given(facilityRepository.findFacilities(any())).willReturn(List.of(f1));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fm1, fm2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pri1, pri2));
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(
                        new RawDataLatestDto("T-PRI-1",
                                new BigDecimal("2.45"), null, ACQ, QualityCode.GOOD),
                        new RawDataLatestDto("T-PRI-2",
                                new BigDecimal("9.99"), null, ACQ, QualityCode.GOOD)));

        List<PumpSummaryDto> result = pumpSummaryService.findPumpSummaries();

        assertThat(result).hasSize(1);
        PumpSummaryDto s = result.get(0);
        assertThat(s.isMultiplePrsrDetected()).isTrue();
        assertThat(s.getPrsrRawVal()).isEqualByComparingTo("2.45");
        assertThat(s.getOprtngPumpCnt()).isZero();
        assertThat(s.getUnknownPumpCnt()).isZero();
    }

    @Test
    void 빈_시설목록이면_하위조회를_호출하지_않고_빈_리스트를_반환한다() {
        given(facilityRepository.findFacilities(any())).willReturn(List.of());

        List<PumpSummaryDto> result = pumpSummaryService.findPumpSummaries();

        assertThat(result).isEmpty();
        verify(instrumentRepository, never())
                .findByFacilityIdInAndEquipType(anyList(), anyList());
        verify(tagRepository, never())
                .findByInstrumentInstrumentIdInAndUseYn(anyList(), any());
        verify(rawDataRepository, never()).findLatestByTagSrlNos(anyList());
    }

    private Facility mockFacility(String facilityId, String facilityNm) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(facilityNm);
        return facility;
    }

    private Pump mockPump(String instrumentId, Facility facility) {
        Pump pump = Mockito.mock(Pump.class);
        Mockito.lenient().when(pump.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(pump.getEquipType()).thenReturn(EquipType.PUMP);
        Mockito.lenient().when(pump.getFacility()).thenReturn(facility);
        return pump;
    }

    private FlowMeter mockFlowMeter(String instrumentId, Facility facility) {
        FlowMeter flwmtr = Mockito.mock(FlowMeter.class);
        Mockito.lenient().when(flwmtr.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(flwmtr.getEquipType()).thenReturn(EquipType.FLWMTR);
        Mockito.lenient().when(flwmtr.getFacility()).thenReturn(facility);
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
