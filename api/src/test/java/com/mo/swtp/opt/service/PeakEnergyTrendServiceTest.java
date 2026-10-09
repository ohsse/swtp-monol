package com.mo.swtp.opt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.opt.dto.PeakEnergyTrendDto;
import com.mo.swtp.opt.dto.PeakTargetDto;
import com.mo.swtp.opt.dto.PredcEnergyBucketDto;
import com.mo.swtp.opt.exception.OptErrorCode;
import com.mo.swtp.opt.repository.PumpEnergyPredcRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PeakEnergyTrendService} 단위 테스트.
 *
 * <p>전력피크분석-5번섹션 PLAN1 §성공 기준 — 윈도우 분할 경계(ArgumentCaptor) / 버킷별 전역 합산 + 음수 차분
 * 제외 / PWQ·PWI 태그 0개 빈 시계열 + ZERO billing + target / 예측·실측 0행 빈 시계열 + 스칼라 정상 /
 * 목표 시드 부재 전파.</p>
 */
@ExtendWith(MockitoExtension.class)
class PeakEnergyTrendServiceTest {

    private static final LocalDateTime H1 = LocalDateTime.of(2026, 6, 5, 9, 0);
    private static final LocalDateTime H2 = LocalDateTime.of(2026, 6, 5, 10, 0);
    private static final LocalDateTime H3 = LocalDateTime.of(2026, 6, 5, 11, 0);
    private static final LocalDateTime H4 = LocalDateTime.of(2026, 6, 5, 12, 0);

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @Mock
    private PumpEnergyPredcRepository pumpEnergyPredcRepository;

    @Mock
    private PeakTargetService peakTargetService;

    @InjectMocks
    private PeakEnergyTrendService peakEnergyTrendService;

    @Test
    void 발생은_직전12시간_예측은_이후12시간_시간버킷으로_분할_조회된다() {
        LocalDateTime nowBefore = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        PeakTargetDto target = mockTarget("1500");
        List<Tag> pwqTags = List.of(mockTag("T-PWQ-1"));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y)).willReturn(pwqTags);
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any()))
                .willReturn(List.of());
        given(pumpEnergyPredcRepository.findEnergyDeltaBuckets(anyList(), any(), any()))
                .willReturn(List.of());

        peakEnergyTrendService.getEnergyTrend();
        LocalDateTime nowAfter = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

        ArgumentCaptor<LocalDateTime> mStart = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> mEnd = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> unit = ArgumentCaptor.forClass(String.class);
        verify(rawDataRepository)
                .findEnergyDeltaBuckets(anyList(), mStart.capture(), mEnd.capture(), unit.capture());

        ArgumentCaptor<LocalDateTime> pStart = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> pEnd = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(pumpEnergyPredcRepository)
                .findEnergyDeltaBuckets(anyList(), pStart.capture(), pEnd.capture());

        LocalDateTime base = mEnd.getValue();
        assertThat(base).isEqualTo(base.truncatedTo(ChronoUnit.HOURS));   // 시 단위 절삭
        assertThat(base).isBetween(nowBefore, nowAfter);                  // base = date_trunc('hour', now)
        assertThat(mStart.getValue()).isEqualTo(base.minusHours(12));     // 발생 [base-12h, base)
        assertThat(unit.getValue()).isEqualTo("hour");
        assertThat(pStart.getValue()).isEqualTo(base);                    // 예측 [base, base+12h)
        assertThat(pEnd.getValue()).isEqualTo(base.plusHours(12));
    }

    @Test
    void 발생_예측_버킷이_시각별로_전역합산되고_음수차분_버킷은_제외된다() {
        PeakTargetDto target = mockTarget("1500");
        List<Tag> pwqTags = List.of(mockTag("T-PWQ-1"), mockTag("T-PWQ-2"));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y)).willReturn(pwqTags);
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any()))
                .willReturn(List.of(
                        rawBucket("T-PWQ-1", H1, "10.2500"),
                        rawBucket("T-PWQ-2", H1, "5.7500"),     // H1 = 16.0
                        rawBucket("T-PWQ-1", H2, "-3"),         // 적산 리셋 — 제외
                        rawBucket("T-PWQ-2", H2, "8")));        // H2 = 8 (PWQ-1 제외 후 단독)
        given(pumpEnergyPredcRepository.findEnergyDeltaBuckets(anyList(), any(), any()))
                .willReturn(List.of(
                        predcBucket("T-PWQ-1", H3, "20"),
                        predcBucket("T-PWQ-2", H3, "4"),        // H3 = 24
                        predcBucket("T-PWQ-1", H4, "0")));      // H4 = 0 (signum >= 0 포함)

        PeakEnergyTrendDto result = peakEnergyTrendService.getEnergyTrend();

        assertThat(result.getMeasuredPoints()).hasSize(2);
        assertThat(result.getMeasuredPoints().get(0).getBaseDtm()).isEqualTo(H1);
        assertThat(result.getMeasuredPoints().get(0).getElcegVal()).isEqualByComparingTo("16.0000");
        assertThat(result.getMeasuredPoints().get(1).getBaseDtm()).isEqualTo(H2);
        assertThat(result.getMeasuredPoints().get(1).getElcegVal()).isEqualByComparingTo("8");

        assertThat(result.getPredictedPoints()).hasSize(2);
        assertThat(result.getPredictedPoints().get(0).getBaseDtm()).isEqualTo(H3);
        assertThat(result.getPredictedPoints().get(0).getElcegVal()).isEqualByComparingTo("24");
        assertThat(result.getPredictedPoints().get(1).getBaseDtm()).isEqualTo(H4);
        assertThat(result.getPredictedPoints().get(1).getElcegVal()).isEqualByComparingTo("0");
    }

    @Test
    void PWQ_PWI_태그가_0개면_빈_시계열과_ZERO_요금피크와_목표값을_반환한다() {
        PeakTargetDto target = mockTarget("1500");
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y))
                .willReturn(List.of());
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of());

        PeakEnergyTrendDto result = peakEnergyTrendService.getEnergyTrend();

        assertThat(result.getUnit()).isEqualTo("kWh");
        assertThat(result.getTargetPeakElpwr()).isEqualByComparingTo("1500");
        assertThat(result.getBillingPeakElpwr()).isEqualByComparingTo("0");
        assertThat(result.getMeasuredPoints()).isEmpty();
        assertThat(result.getPredictedPoints()).isEmpty();
    }

    @Test
    void 예측_실측_데이터가_0행이면_빈_시계열에_요금_목표_스칼라는_정상_반환된다() {
        PeakTargetDto target = mockTarget("1500");
        List<Tag> pwqTags = List.of(mockTag("T-PWQ-1"));
        List<Tag> pwiTags = List.of(mockTag("T-PWI-1"));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y)).willReturn(pwqTags);
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y)).willReturn(pwiTags);
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any()))
                .willReturn(List.of());
        given(pumpEnergyPredcRepository.findEnergyDeltaBuckets(anyList(), any(), any()))
                .willReturn(List.of());
        given(rawDataRepository.findMaxMinuteSumElpwr(anyList(), any(), any()))
                .willReturn(new BigDecimal("1320.5000"));

        PeakEnergyTrendDto result = peakEnergyTrendService.getEnergyTrend();

        assertThat(result.getMeasuredPoints()).isEmpty();
        assertThat(result.getPredictedPoints()).isEmpty();
        assertThat(result.getBillingPeakElpwr()).isEqualByComparingTo("1320.5000");
        assertThat(result.getTargetPeakElpwr()).isEqualByComparingTo("1500");
    }

    @Test
    void 목표값_시드가_없으면_PEAK_TARGET_NOT_INITIALIZED_예외가_전파된다() {
        given(peakTargetService.getPeakTarget())
                .willThrow(new RestApiException(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED));

        assertThatThrownBy(() -> peakEnergyTrendService.getEnergyTrend())
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private PeakTargetDto mockTarget(String targetPeakElpwr) {
        PeakTargetDto dto = Mockito.mock(PeakTargetDto.class);
        given(dto.getTargetPeakElpwr()).willReturn(new BigDecimal(targetPeakElpwr));
        return dto;
    }

    private Tag mockTag(String tagSrlNo) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        return tag;
    }

    private RawDataBucketDto rawBucket(String tagSrlNo, LocalDateTime baseDtm, String aggrVal) {
        return new RawDataBucketDto(tagSrlNo, baseDtm, new BigDecimal(aggrVal));
    }

    private PredcEnergyBucketDto predcBucket(String tagSrlNo, LocalDateTime baseDtm, String aggrVal) {
        return new PredcEnergyBucketDto(tagSrlNo, baseDtm, new BigDecimal(aggrVal));
    }
}
