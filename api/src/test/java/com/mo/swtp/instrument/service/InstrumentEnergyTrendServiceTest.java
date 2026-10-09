package com.mo.swtp.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.mo.swtp.common.enumtype.InqUnit;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.dto.InstrumentEnergyTrendDto;
import com.mo.swtp.instrument.dto.InstrumentEnergyTrendSearchDto;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link InstrumentEnergyTrendService} 단위 테스트.
 *
 * <p>설비별사용량-4번섹션 PLAN1 §성공 기준 — 기간 무효 4종(null·역전·YEAR·397일)→INVALID_INQ_PERIOD /
 * 미존재·비활성 계측기 2종→INSTRUMENT_NOT_FOUND / PWQ 버킷 baseDtm 오름차순 매핑 / 음수 차분 제외 /
 * 다중 PWQ 동일 baseDtm 합산 / PWQ 무보유(PWI만)·데이터 0건→빈 points(200).</p>
 */
@ExtendWith(MockitoExtension.class)
class InstrumentEnergyTrendServiceTest {

    private static final String INSTRUMENT_ID = "I-1";
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
    private InstrumentEnergyTrendService instrumentEnergyTrendService;

    @Test
    void 집계단위가_null이면_INVALID_INQ_PERIOD_예외가_발생한다() {
        InstrumentEnergyTrendSearchDto search = new InstrumentEnergyTrendSearchDto();
        search.setInqUnit(null);
        search.setFromDt(LocalDate.of(2024, 7, 1));
        search.setToDt(LocalDate.of(2024, 7, 9));

        assertThatThrownBy(() -> instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
    }

    @Test
    void from이_to보다_늦으면_INVALID_INQ_PERIOD_예외가_발생한다() {
        InstrumentEnergyTrendSearchDto search = new InstrumentEnergyTrendSearchDto();
        search.setInqUnit(InqUnit.DAY);
        search.setFromDt(LocalDate.of(2024, 7, 10));
        search.setToDt(LocalDate.of(2024, 7, 1));

        assertThatThrownBy(() -> instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
    }

    @Test
    void 집계단위가_YEAR이면_INVALID_INQ_PERIOD_예외가_발생한다() {
        InstrumentEnergyTrendSearchDto search = new InstrumentEnergyTrendSearchDto();
        search.setInqUnit(InqUnit.YEAR);
        search.setFromDt(LocalDate.of(2024, 1, 1));
        search.setToDt(LocalDate.of(2024, 1, 31));

        assertThatThrownBy(() -> instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
    }

    @Test
    void 조회기간이_396일을_초과하면_INVALID_INQ_PERIOD_예외가_발생한다() {
        InstrumentEnergyTrendSearchDto search = new InstrumentEnergyTrendSearchDto();
        search.setInqUnit(InqUnit.DAY);
        search.setFromDt(LocalDate.of(2024, 1, 1));
        search.setToDt(LocalDate.of(2025, 2, 1));   // 397일 — 396일 상한 초과

        assertThatThrownBy(() -> instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, search))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
    }

    @Test
    void 존재하지_않는_계측기면_INSTRUMENT_NOT_FOUND_예외가_발생한다() {
        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, validSearch()))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INSTRUMENT_NOT_FOUND);
    }

    @Test
    void 비활성_계측기면_INSTRUMENT_NOT_FOUND_예외가_발생한다() {
        Instrument inactive = mockInstrument(INSTRUMENT_ID, "송수1호기 전력량계", YnType.N);
        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, validSearch()))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INSTRUMENT_NOT_FOUND);
    }

    @Test
    void PWQ_버킷이_baseDtm_오름차순_시계열로_매핑된다() {
        Instrument instrument = mockInstrument(INSTRUMENT_ID, "송수1호기 전력량계", YnType.Y);
        Tag pwq = mockTag("T-PWQ-1", TagMeasurementType.PWQ);

        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(instrument));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(   // 의도적 역순 반환 → TreeMap 자연 정렬 검증
                        bucket("T-PWQ-1", D2, "120"),
                        bucket("T-PWQ-1", D1, "100"),
                        bucket("T-PWQ-1", D3, "130")));

        InstrumentEnergyTrendDto result =
                instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, validSearch());

        assertThat(result.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(result.getInstrumentNm()).isEqualTo("송수1호기 전력량계");
        assertThat(result.getUnit()).isEqualTo("kWh");
        assertThat(result.getPoints()).hasSize(3);
        assertThat(result.getPoints().get(0).getBaseDtm()).isEqualTo(D1);
        assertThat(result.getPoints().get(0).getElcegVal()).isEqualByComparingTo("100");
        assertThat(result.getPoints().get(1).getBaseDtm()).isEqualTo(D2);
        assertThat(result.getPoints().get(1).getElcegVal()).isEqualByComparingTo("120");
        assertThat(result.getPoints().get(2).getBaseDtm()).isEqualTo(D3);
        assertThat(result.getPoints().get(2).getElcegVal()).isEqualByComparingTo("130");
    }

    @Test
    void 음수_차분_버킷은_생략하고_0_이상_버킷만_포함한다() {
        Instrument instrument = mockInstrument(INSTRUMENT_ID, "송수1호기 전력량계", YnType.Y);
        Tag pwq = mockTag("T-PWQ-1", TagMeasurementType.PWQ);

        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(instrument));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-PWQ-1", D1, "100"),
                        bucket("T-PWQ-1", D2, "-5"),   // 적산 리셋·롤오버 — 생략
                        bucket("T-PWQ-1", D3, "0")));   // 0 은 포함 (signum >= 0)

        InstrumentEnergyTrendDto result =
                instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, validSearch());

        assertThat(result.getPoints()).hasSize(2);
        assertThat(result.getPoints().get(0).getBaseDtm()).isEqualTo(D1);
        assertThat(result.getPoints().get(0).getElcegVal()).isEqualByComparingTo("100");
        assertThat(result.getPoints().get(1).getBaseDtm()).isEqualTo(D3);
        assertThat(result.getPoints().get(1).getElcegVal()).isEqualByComparingTo("0");
    }

    @Test
    void 다중_PWQ_태그의_동일_baseDtm_버킷은_합산된다() {
        Instrument instrument = mockInstrument(INSTRUMENT_ID, "ELCMTR 다채널", YnType.Y);
        Tag pwq1 = mockTag("T-PWQ-1", TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("T-PWQ-2", TagMeasurementType.PWQ);

        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(instrument));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of(
                        bucket("T-PWQ-1", D1, "100"),
                        bucket("T-PWQ-2", D1, "80"),    // 동일 D1 → 합산 180
                        bucket("T-PWQ-1", D2, "120")));

        InstrumentEnergyTrendDto result =
                instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, validSearch());

        assertThat(result.getPoints()).hasSize(2);
        assertThat(result.getPoints().get(0).getBaseDtm()).isEqualTo(D1);
        assertThat(result.getPoints().get(0).getElcegVal()).isEqualByComparingTo("180");
        assertThat(result.getPoints().get(1).getBaseDtm()).isEqualTo(D2);
        assertThat(result.getPoints().get(1).getElcegVal()).isEqualByComparingTo("120");
    }

    @Test
    void PWQ_태그가_없으면_빈_points를_반환한다() {
        Instrument instrument = mockInstrument(INSTRUMENT_ID, "유량계", YnType.Y);
        Tag pwi = mockTag("T-PWI-1", TagMeasurementType.PWI);   // 순시전력만 보유

        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(instrument));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwi));

        InstrumentEnergyTrendDto result =
                instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, validSearch());

        assertThat(result.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(result.getPoints()).isEmpty();
        then(rawDataRepository).shouldHaveNoInteractions();   // PWQ 0건 → 버킷 쿼리 미호출
    }

    @Test
    void 데이터가_0건이면_빈_points를_반환한다() {
        Instrument instrument = mockInstrument(INSTRUMENT_ID, "송수1호기 전력량계", YnType.Y);
        Tag pwq = mockTag("T-PWQ-1", TagMeasurementType.PWQ);

        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(instrument));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of());

        InstrumentEnergyTrendDto result =
                instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, validSearch());

        assertThat(result.getPoints()).isEmpty();
    }

    @Test
    void 조회단위가_MONTH면_repo에_dateTruncUnit_month와_기간_경계를_전달한다() {
        Instrument instrument = mockInstrument(INSTRUMENT_ID, "송수1호기 전력량계", YnType.Y);
        Tag pwq = mockTag("T-PWQ-1", TagMeasurementType.PWQ);

        given(instrumentRepository.findById(INSTRUMENT_ID)).willReturn(Optional.of(instrument));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), anyString()))
                .willReturn(List.of());

        InstrumentEnergyTrendSearchDto search = new InstrumentEnergyTrendSearchDto();
        search.setInqUnit(InqUnit.MONTH);
        search.setFromDt(LocalDate.of(2024, 1, 1));
        search.setToDt(LocalDate.of(2024, 12, 31));

        instrumentEnergyTrendService.findEnergyTrend(INSTRUMENT_ID, search);

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

    private InstrumentEnergyTrendSearchDto validSearch() {
        InstrumentEnergyTrendSearchDto search = new InstrumentEnergyTrendSearchDto();
        search.setInqUnit(InqUnit.DAY);
        search.setFromDt(LocalDate.of(2024, 7, 1));
        search.setToDt(LocalDate.of(2024, 7, 9));
        return search;
    }

    private Instrument mockInstrument(String instrumentId, String instrumentNm, YnType useYn) {
        Instrument instrument = Mockito.mock(Instrument.class);
        Mockito.lenient().when(instrument.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(instrument.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(instrument.getUseYn()).thenReturn(useYn);
        return instrument;
    }

    private Tag mockTag(String tagSrlNo, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }

    private RawDataBucketDto bucket(String tagSrlNo, LocalDateTime baseDtm, String aggrVal) {
        return new RawDataBucketDto(tagSrlNo, baseDtm, new BigDecimal(aggrVal));
    }
}
