package com.mo.swtp.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.dto.PumpOperationHistoryDto;
import com.mo.swtp.instrument.dto.PumpOperationHistoryDto.OperationSegment;
import com.mo.swtp.instrument.dto.PumpPeriodSearchDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataOnStateDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
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
 * {@link PumpOperationHistoryService} 단위 테스트.
 *
 * <p>송수펌프가동이력_4번섹션 PLAN1 §성공 기준 8 케이스 — 활성0대 빈리스트 / 전구간 연속ON 단일세그먼트 /
 * 다회 껐다켜기(gap) 다수세그먼트 / 중간 결측·BAD(ON행 부재) split / 전구간 OFF(ON행 0건) 세그먼트0건 /
 * 단일 ON endDtm=acq+1분 경계 / OPS태그없는펌프 segments빈배열 / from&gt;to 예외.</p>
 *
 * <p>방안B 검증 — Service 는 {@code findOnStateByTagSrlNosAndDtmRange} 가 ON 행만 반환한다고 전제하고
 * 시각열 간극을 세그먼트 경계로 인코딩한다 ({@code .claude/rules/ot-integration.md §3} OPS HLV 미적용).</p>
 */
@ExtendWith(MockitoExtension.class)
class PumpOperationHistoryServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private PumpOperationHistoryService pumpOperationHistoryService;

    @Test
    void 활성_펌프가_0대면_빈_리스트를_반환한다() {
        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of());

        List<PumpOperationHistoryDto> result =
                pumpOperationHistoryService.findPumpOperationHistory(validSearch());

        assertThat(result).isEmpty();
    }

    @Test
    void 전구간_연속_ON이면_단일_세그먼트로_압축된다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag ops1 = mockTag("T-OPS-1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(ops1));
        given(rawDataRepository.findOnStateByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        onState("T-OPS-1", dtm(8, 0)),
                        onState("T-OPS-1", dtm(8, 1)),
                        onState("T-OPS-1", dtm(8, 2))));

        List<PumpOperationHistoryDto> result =
                pumpOperationHistoryService.findPumpOperationHistory(validSearch());

        assertThat(result).hasSize(1);
        PumpOperationHistoryDto dto = result.get(0);
        assertThat(dto.getPumpId()).isEqualTo("I-P-1");
        assertThat(dto.getPumpNm()).isEqualTo("송수1호기");
        assertThat(dto.getSegments()).hasSize(1);
        assertThat(dto.getSegments().get(0).getStartDtm()).isEqualTo(dtm(8, 0));
        // 마지막 ON(08:02) + 1분 수집 주기
        assertThat(dto.getSegments().get(0).getEndDtm()).isEqualTo(dtm(8, 3));
    }

    @Test
    void 다회_껐다_켜면_간극마다_세그먼트가_분리된다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag ops1 = mockTag("T-OPS-1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(ops1));
        given(rawDataRepository.findOnStateByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        onState("T-OPS-1", dtm(8, 0)),
                        onState("T-OPS-1", dtm(8, 1)),   // 간극 (08:02 OFF)
                        onState("T-OPS-1", dtm(10, 0)),
                        onState("T-OPS-1", dtm(10, 1)),
                        onState("T-OPS-1", dtm(10, 2)),  // 간극 (이후 OFF)
                        onState("T-OPS-1", dtm(14, 0))));

        List<PumpOperationHistoryDto> result =
                pumpOperationHistoryService.findPumpOperationHistory(validSearch());

        List<OperationSegment> segments = result.get(0).getSegments();
        assertThat(segments).hasSize(3);
        assertThat(segments.get(0).getStartDtm()).isEqualTo(dtm(8, 0));
        assertThat(segments.get(0).getEndDtm()).isEqualTo(dtm(8, 2));
        assertThat(segments.get(1).getStartDtm()).isEqualTo(dtm(10, 0));
        assertThat(segments.get(1).getEndDtm()).isEqualTo(dtm(10, 3));
        assertThat(segments.get(2).getStartDtm()).isEqualTo(dtm(14, 0));
        assertThat(segments.get(2).getEndDtm()).isEqualTo(dtm(14, 1));
    }

    @Test
    void 중간_결측_또는_BAD로_ON행이_부재하면_세그먼트가_분리된다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag ops1 = mockTag("T-OPS-1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(ops1));
        // 08:03~08:04 는 BAD/결측 → 방안B 쿼리가 ON 행 미반환 → 시각열 간극으로 세그먼트 분리
        given(rawDataRepository.findOnStateByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        onState("T-OPS-1", dtm(8, 0)),
                        onState("T-OPS-1", dtm(8, 1)),
                        onState("T-OPS-1", dtm(8, 2)),
                        onState("T-OPS-1", dtm(8, 5)),
                        onState("T-OPS-1", dtm(8, 6))));

        List<PumpOperationHistoryDto> result =
                pumpOperationHistoryService.findPumpOperationHistory(validSearch());

        List<OperationSegment> segments = result.get(0).getSegments();
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getStartDtm()).isEqualTo(dtm(8, 0));
        assertThat(segments.get(0).getEndDtm()).isEqualTo(dtm(8, 3));
        assertThat(segments.get(1).getStartDtm()).isEqualTo(dtm(8, 5));
        assertThat(segments.get(1).getEndDtm()).isEqualTo(dtm(8, 7));
    }

    @Test
    void 전구간_OFF로_ON행이_0건이면_세그먼트가_0건이다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag ops1 = mockTag("T-OPS-1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(ops1));
        given(rawDataRepository.findOnStateByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of());   // ON 행 0건 (전구간 OFF)

        List<PumpOperationHistoryDto> result =
                pumpOperationHistoryService.findPumpOperationHistory(validSearch());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSegments()).isEmpty();
    }

    @Test
    void 단일_ON_시각이면_종료시각은_수집_시각_더하기_1분이다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag ops1 = mockTag("T-OPS-1", p1, TagMeasurementType.OPS);

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(ops1));
        given(rawDataRepository.findOnStateByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(onState("T-OPS-1", dtm(9, 30))));

        List<PumpOperationHistoryDto> result =
                pumpOperationHistoryService.findPumpOperationHistory(validSearch());

        List<OperationSegment> segments = result.get(0).getSegments();
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).getStartDtm()).isEqualTo(dtm(9, 30));
        assertThat(segments.get(0).getEndDtm()).isEqualTo(dtm(9, 31));
    }

    @Test
    void OPS_태그가_없는_펌프는_segments_빈_배열을_반환한다() {
        Pump p1 = mockPump("I-P-1", "송수1호기");
        Tag pwi1 = mockTag("T-PWI-1", p1, TagMeasurementType.PWI);   // OPS 아님 → 필터 제외

        given(instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(any(), any()))
                .willReturn(List.of(p1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwi1));
        given(rawDataRepository.findOnStateByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of());

        List<PumpOperationHistoryDto> result =
                pumpOperationHistoryService.findPumpOperationHistory(validSearch());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPumpId()).isEqualTo("I-P-1");
        assertThat(result.get(0).getSegments()).isEmpty();
    }

    @Test
    void from이_to보다_늦으면_INVALID_INQ_PERIOD_예외가_발생한다() {
        PumpPeriodSearchDto invalid = new PumpPeriodSearchDto();
        invalid.setFromDt(LocalDate.of(2024, 7, 10));
        invalid.setToDt(LocalDate.of(2024, 7, 1));

        assertThatThrownBy(() -> pumpOperationHistoryService.findPumpOperationHistory(invalid))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private PumpPeriodSearchDto validSearch() {
        PumpPeriodSearchDto search = new PumpPeriodSearchDto();
        search.setFromDt(LocalDate.of(2024, 7, 1));
        search.setToDt(LocalDate.of(2024, 7, 9));
        return search;
    }

    private LocalDateTime dtm(int hour, int minute) {
        return LocalDateTime.of(2024, 7, 1, hour, minute);
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

    private RawDataOnStateDto onState(String tagSrlNo, LocalDateTime acqDtm) {
        return new RawDataOnStateDto(tagSrlNo, acqDtm);
    }
}
