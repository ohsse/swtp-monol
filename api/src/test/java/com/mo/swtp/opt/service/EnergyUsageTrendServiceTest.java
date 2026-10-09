package com.mo.swtp.opt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mo.swtp.common.enumtype.InqUnit;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.opt.dto.EnergyUsageTrendDto;
import com.mo.swtp.opt.dto.EnergyUsageTrendSearchDto;
import com.mo.swtp.opt.exception.OptErrorCode;
import com.mo.swtp.raw.dto.RawDataBucketSumDto;
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
 * {@link EnergyUsageTrendService} 단위 테스트.
 *
 * <p>사용량트렌드-2번섹션 PLAN1 §성공 기준 — 유효 파라미터 버킷별 시계열 매핑(delta=0 포함) / YEAR·기간역전·
 * 13개월초과·null 시 INVALID_SEARCH_PERIOD + 쿼리 미호출 / PWQ 태그 0개 빈 시계열 / 종료일 익일 00시 배타적
 * 상한 계약(ArgumentCaptor).</p>
 */
@ExtendWith(MockitoExtension.class)
class EnergyUsageTrendServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);
    private static final LocalDateTime D1 = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2026, 1, 2, 0, 0);
    private static final LocalDateTime D3 = LocalDateTime.of(2026, 1, 3, 0, 0);

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private EnergyUsageTrendService energyUsageTrendService;

    @Test
    void 유효_파라미터면_PWQ태그를_수집해_버킷별_전역합산_시계열을_반환한다() {
        List<Tag> pwqTags = List.of(mockTag("T-PWQ-1"), mockTag("T-PWQ-2"));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y)).willReturn(pwqTags);
        given(rawDataRepository.findEnergyDeltaBucketsTotal(anyList(), any(), any(), any()))
                .willReturn(List.of(
                        sumBucket(D1, "1240.5000"),
                        sumBucket(D2, "0"),            // delta=0 버킷 — 死코드 필터 없이 포함됨을 검증
                        sumBucket(D3, "880.2500")));

        EnergyUsageTrendDto result = energyUsageTrendService.getEnergyUsageTrend(searchDto(InqUnit.DAY, FROM, TO));

        assertThat(result.getUnit()).isEqualTo("kWh");
        assertThat(result.getPoints()).hasSize(3);
        assertThat(result.getPoints().get(0).getBaseDtm()).isEqualTo(D1);
        assertThat(result.getPoints().get(0).getElcegVal()).isEqualByComparingTo("1240.5000");
        assertThat(result.getPoints().get(1).getBaseDtm()).isEqualTo(D2);
        assertThat(result.getPoints().get(1).getElcegVal()).isEqualByComparingTo("0");
        assertThat(result.getPoints().get(2).getBaseDtm()).isEqualTo(D3);
        assertThat(result.getPoints().get(2).getElcegVal()).isEqualByComparingTo("880.2500");
    }

    @Test
    void 집계단위가_YEAR면_INVALID_SEARCH_PERIOD_예외가_발생하고_쿼리를_호출하지_않는다() {
        assertThatThrownBy(() ->
                energyUsageTrendService.getEnergyUsageTrend(searchDto(InqUnit.YEAR, FROM, TO)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(OptErrorCode.INVALID_SEARCH_PERIOD);

        Mockito.verifyNoInteractions(tagRepository, rawDataRepository);
    }

    @Test
    void 시작일이_종료일보다_늦으면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        assertThatThrownBy(() -> energyUsageTrendService.getEnergyUsageTrend(
                searchDto(InqUnit.DAY, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(OptErrorCode.INVALID_SEARCH_PERIOD);

        Mockito.verifyNoInteractions(tagRepository, rawDataRepository);
    }

    @Test
    void 조회기간이_13개월_396일을_초과하면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        // 2026-01-01 ~ 2027-02-02 = 397일 (396일 초과)
        assertThatThrownBy(() -> energyUsageTrendService.getEnergyUsageTrend(
                searchDto(InqUnit.DAY, FROM, LocalDate.of(2027, 2, 2))))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(OptErrorCode.INVALID_SEARCH_PERIOD);

        Mockito.verifyNoInteractions(tagRepository, rawDataRepository);
    }

    @Test
    void 집계단위가_null이면_INVALID_SEARCH_PERIOD_예외가_발생한다() {
        assertThatThrownBy(() ->
                energyUsageTrendService.getEnergyUsageTrend(searchDto(null, FROM, TO)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(OptErrorCode.INVALID_SEARCH_PERIOD);

        Mockito.verifyNoInteractions(tagRepository, rawDataRepository);
    }

    @Test
    void PWQ태그가_0개면_빈_시계열을_반환한다() {
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y))
                .willReturn(List.of());
        given(rawDataRepository.findEnergyDeltaBucketsTotal(anyList(), any(), any(), any()))
                .willReturn(List.of());

        EnergyUsageTrendDto result = energyUsageTrendService.getEnergyUsageTrend(searchDto(InqUnit.DAY, FROM, TO));

        assertThat(result.getUnit()).isEqualTo("kWh");
        assertThat(result.getPoints()).isEmpty();
    }

    @Test
    void 종료일_익일_00시를_배타적_상한으로_시작일_자정과_함께_쿼리에_전달한다() {
        List<Tag> pwqTags = List.of(mockTag("T-PWQ-1"));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y))
                .willReturn(pwqTags);
        given(rawDataRepository.findEnergyDeltaBucketsTotal(anyList(), any(), any(), any()))
                .willReturn(List.of());

        EnergyUsageTrendSearchDto searchDto = searchDto(InqUnit.DAY, FROM, TO);
        // 종료일 익일 00시 계약 — DTO 변환 직접 단언
        assertThat(searchDto.toEndExclusiveDtm()).isEqualTo(TO.plusDays(1).atStartOfDay());

        energyUsageTrendService.getEnergyUsageTrend(searchDto);

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> unit = ArgumentCaptor.forClass(String.class);
        verify(rawDataRepository)
                .findEnergyDeltaBucketsTotal(anyList(), start.capture(), end.capture(), unit.capture());

        assertThat(start.getValue()).isEqualTo(FROM.atStartOfDay());          // 시작일 자정 (inclusive)
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0)); // 종료일 익일 00시 (exclusive)
        assertThat(unit.getValue()).isEqualTo("day");
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private EnergyUsageTrendSearchDto searchDto(InqUnit inqUnit, LocalDate fromDt, LocalDate toDt) {
        EnergyUsageTrendSearchDto dto = new EnergyUsageTrendSearchDto();
        dto.setInqUnit(inqUnit);
        dto.setFromDt(fromDt);
        dto.setToDt(toDt);
        return dto;
    }

    private Tag mockTag(String tagSrlNo) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        return tag;
    }

    private RawDataBucketSumDto sumBucket(LocalDateTime baseDtm, String totalVal) {
        return new RawDataBucketSumDto(baseDtm, new BigDecimal(totalVal));
    }
}
