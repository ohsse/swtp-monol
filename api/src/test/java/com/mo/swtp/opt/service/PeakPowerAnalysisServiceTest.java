package com.mo.swtp.opt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.opt.dto.PeakPowerAnalysisDto;
import com.mo.swtp.opt.dto.PeakTargetDto;
import com.mo.swtp.opt.repository.PeakPredcRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link PeakPowerAnalysisService} 단위 테스트 — 전력피크분석 2·3번섹션 5지표 집계.
 *
 * <p>Mockito 격리 단위 테스트 (test-strategy.md §1). 결측 합산 정책(GOOD 부분합)·목표 미설정 시 예측 쿼리
 * 생략·PWI 태그 0개·billing MAX null fallback·예측 최근접 시각 매핑 (2번섹션 5케이스) + 송수펌프 순시전력
 * 부분집합 합산(펌프 BAD 제외·비펌프 제외·latest 1회 호출)·펌프 0개 fallback (3번섹션 2케이스) 총 7케이스를
 * 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class PeakPowerAnalysisServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @Mock
    private PeakPredcRepository peakPredcRepository;

    @Mock
    private PeakTargetService peakTargetService;

    @InjectMocks
    private PeakPowerAnalysisService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "predcHorizonHours", 48L);
    }

    @Test
    void 정상_4지표가_모두_채워진다() {
        // given: PWI 태그 1개, 최신값 GOOD(corrVal 우선 100), 목표 900, billing 1024, 예측 14:30
        LocalDateTime expected = LocalDateTime.of(2026, 6, 5, 14, 30);
        Tag tagA = pwiTag("706-PWI-001");
        PeakTargetDto target = targetDto(bd(900));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of(tagA));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(latest("706-PWI-001", bd(999), bd(100), QualityCode.GOOD)));
        given(rawDataRepository.findMaxMinuteSumElpwr(anyList(), any(), any())).willReturn(bd(1024));
        given(peakPredcRepository.findEarliestPredcDtmOverTarget(anyList(), any(), any(), any()))
                .willReturn(expected);

        // when
        PeakPowerAnalysisDto result = service.getPeakPowerAnalysis();

        // then: corrVal 우선 100, 목표 900, billing 1024, 예측 14:30
        assertThat(result.getTotalElpwr()).isEqualByComparingTo(bd(100));
        assertThat(result.getTargetPeakElpwr()).isEqualByComparingTo(bd(900));
        assertThat(result.getBillingPeakElpwr()).isEqualByComparingTo(bd(1024));
        assertThat(result.getPredcPeakDtm()).isEqualTo(expected);
    }

    @Test
    void 총순시전력은_GOOD만_합산하고_BAD_UNCERTAIN_은_제외한다() {
        // given: 4개 태그 — GOOD(corr 100)+GOOD(raw 50)+BAD(30)+UNCERTAIN(20)
        Tag tagA = pwiTag("A");
        Tag tagB = pwiTag("B");
        Tag tagC = pwiTag("C");
        Tag tagD = pwiTag("D");
        PeakTargetDto target = targetDto(bd(900));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of(tagA, tagB, tagC, tagD));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                latest("A", null, bd(100), QualityCode.GOOD),
                latest("B", bd(50), null, QualityCode.GOOD),
                latest("C", bd(30), bd(30), QualityCode.BAD),
                latest("D", bd(20), bd(20), QualityCode.UNCERTAIN)));
        given(rawDataRepository.findMaxMinuteSumElpwr(anyList(), any(), any())).willReturn(bd(500));
        given(peakPredcRepository.findEarliestPredcDtmOverTarget(anyList(), any(), any(), any()))
                .willReturn(null);

        // when
        PeakPowerAnalysisDto result = service.getPeakPowerAnalysis();

        // then: GOOD 만 100 + 50 = 150 (corrVal 우선·rawVal fallback, BAD/UNCERTAIN 전액 제외)
        assertThat(result.getTotalElpwr()).isEqualByComparingTo(bd(150));
    }

    @Test
    void 목표가_미설정_0이면_예측쿼리를_호출하지_않고_예상시간은_null이다() {
        // given: 목표 0 (미설정)
        Tag tagA = pwiTag("706-PWI-001");
        PeakTargetDto target = targetDto(BigDecimal.ZERO);
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of(tagA));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(latest("706-PWI-001", bd(300), null, QualityCode.GOOD)));
        given(rawDataRepository.findMaxMinuteSumElpwr(anyList(), any(), any())).willReturn(bd(500));

        // when
        PeakPowerAnalysisDto result = service.getPeakPowerAnalysis();

        // then: total/billing 정상 산정, 목표 0, 예상시간 null + 예측 쿼리 미호출
        assertThat(result.getTotalElpwr()).isEqualByComparingTo(bd(300));
        assertThat(result.getTargetPeakElpwr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getBillingPeakElpwr()).isEqualByComparingTo(bd(500));
        assertThat(result.getPredcPeakDtm()).isNull();
        verify(peakPredcRepository, never())
                .findEarliestPredcDtmOverTarget(anyList(), any(), any(), any());
    }

    @Test
    void PWI_태그가_0개면_total과_billing은_ZERO이고_예측을_호출하지_않는다() {
        // given: 활성 PWI 태그 0개
        PeakTargetDto target = targetDto(bd(900));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of());
        given(peakTargetService.getPeakTarget()).willReturn(target);

        // when
        PeakPowerAnalysisDto result = service.getPeakPowerAnalysis();

        // then: total/billing ZERO (빈 입력 단락), 예상시간 null + 예측 쿼리 미호출
        assertThat(result.getTotalElpwr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getBillingPeakElpwr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPredcPeakDtm()).isNull();
        verify(peakPredcRepository, never())
                .findEarliestPredcDtmOverTarget(anyList(), any(), any(), any());
    }

    @Test
    void billing_MAX가_null이면_ZERO로_fallback하고_예측_0행이면_예상시간은_null이다() {
        // given: billing MAX null (12개월 GOOD 데이터 부재), 예측 0행
        Tag tagA = pwiTag("706-PWI-001");
        PeakTargetDto target = targetDto(bd(900));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of(tagA));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(latest("706-PWI-001", bd(200), null, QualityCode.GOOD)));
        given(rawDataRepository.findMaxMinuteSumElpwr(anyList(), any(), any())).willReturn(null);
        given(peakPredcRepository.findEarliestPredcDtmOverTarget(anyList(), any(), any(), any()))
                .willReturn(null);

        // when
        PeakPowerAnalysisDto result = service.getPeakPowerAnalysis();

        // then: total 200, billing ZERO(null fallback), 예상시간 null
        assertThat(result.getTotalElpwr()).isEqualByComparingTo(bd(200));
        assertThat(result.getBillingPeakElpwr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPredcPeakDtm()).isNull();
    }

    @Test
    void 송수펌프순시전력은_펌프매핑PWI_부분집합의_GOOD만_합산한다() {
        // given: 전체 PWI 4개 — 펌프 A(GOOD 100)·B(GOOD 50)·D(BAD 999) + 비펌프 C(GOOD 200)
        Tag pumpA = pwiTag("A");
        Tag pumpB = pwiTag("B");
        Tag pumpD = pwiTag("D");
        Tag nonPumpC = pwiTag("C");
        PeakTargetDto target = targetDto(bd(900));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of(pumpA, pumpB, pumpD, nonPumpC));
        given(tagRepository.findByTagSeCdAndUseYnAndInstrument_EquipType(
                TagMeasurementType.PWI, YnType.Y, EquipType.PUMP))
                .willReturn(List.of(pumpA, pumpB, pumpD));
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(rawDataRepository.findLatestByTagSrlNos(anyList())).willReturn(List.of(
                latest("A", null, bd(100), QualityCode.GOOD),
                latest("B", bd(50), null, QualityCode.GOOD),
                latest("D", bd(999), bd(999), QualityCode.BAD),
                latest("C", bd(200), null, QualityCode.GOOD)));
        given(rawDataRepository.findMaxMinuteSumElpwr(anyList(), any(), any())).willReturn(bd(500));
        given(peakPredcRepository.findEarliestPredcDtmOverTarget(anyList(), any(), any(), any()))
                .willReturn(null);

        // when
        PeakPowerAnalysisDto result = service.getPeakPowerAnalysis();

        // then: total = 펌프 GOOD 150 + 비펌프 GOOD 200 = 350, pump = 펌프 GOOD 100+50 = 150 (BAD·비펌프 제외)
        assertThat(result.getTotalElpwr()).isEqualByComparingTo(bd(350));
        assertThat(result.getPumpElpwr()).isEqualByComparingTo(bd(150));
        // findLatestByTagSrlNos 1회만 호출 — total·pump 가 동일 fetch 결과 공유 (시점 일관성)
        verify(rawDataRepository, times(1)).findLatestByTagSrlNos(anyList());
    }

    @Test
    void 펌프매핑PWI가_0개면_송수펌프순시전력은_ZERO이고_총순시전력은_정상이다() {
        // given: 전체 PWI 1개(비펌프 C GOOD 200), 펌프 매핑 PWI 0개
        Tag nonPumpC = pwiTag("C");
        PeakTargetDto target = targetDto(bd(900));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of(nonPumpC));
        given(tagRepository.findByTagSeCdAndUseYnAndInstrument_EquipType(
                TagMeasurementType.PWI, YnType.Y, EquipType.PUMP))
                .willReturn(List.of());
        given(peakTargetService.getPeakTarget()).willReturn(target);
        given(rawDataRepository.findLatestByTagSrlNos(anyList()))
                .willReturn(List.of(latest("C", bd(200), null, QualityCode.GOOD)));
        given(rawDataRepository.findMaxMinuteSumElpwr(anyList(), any(), any())).willReturn(bd(500));
        given(peakPredcRepository.findEarliestPredcDtmOverTarget(anyList(), any(), any(), any()))
                .willReturn(null);

        // when
        PeakPowerAnalysisDto result = service.getPeakPowerAnalysis();

        // then: total 200(비펌프 포함), pump ZERO(펌프 set 빈 멤버십)
        assertThat(result.getTotalElpwr()).isEqualByComparingTo(bd(200));
        assertThat(result.getPumpElpwr()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- 헬퍼 ---

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private Tag pwiTag(String tagSrlNo) {
        Tag tag = mock(Tag.class);
        given(tag.getTagSrlNo()).willReturn(tagSrlNo);
        return tag;
    }

    private PeakTargetDto targetDto(BigDecimal targetPeakElpwr) {
        PeakTargetDto dto = mock(PeakTargetDto.class);
        given(dto.getTargetPeakElpwr()).willReturn(targetPeakElpwr);
        return dto;
    }

    private RawDataLatestDto latest(
            String tagSrlNo, BigDecimal rawVal, BigDecimal corrVal, QualityCode qualityCd) {
        return new RawDataLatestDto(tagSrlNo, rawVal, corrVal,
                LocalDateTime.of(2026, 6, 5, 12, 0), qualityCd);
    }
}
