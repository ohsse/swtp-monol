package com.mo.swtp.opt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.opt.dto.MaxPeakStatusDto;
import com.mo.swtp.raw.dto.RawDataBucketPeakDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MaxPeakStatusService} 단위 테스트.
 *
 * <p>사용량트렌드-3번섹션 PLAN1 §성공 기준 — 6개 월 모두 데이터 시 6 포인트 오름차순 / 일부 월 결측 시 해당 슬롯
 * null·항상 6슬롯 / PWI 태그 0건 시 쿼리 미호출 + 6슬롯 null / 쿼리 빈 결과 시 6슬롯 null /
 * {@code findByTagSeCdAndUseYn(PWI, Y)} 필터 + 윈도우(당월-5 ~ 익월 00시) 계약.</p>
 *
 * <p>{@link Clock#fixed}(2026-06-11 UTC) 주입으로 시점 의존 제거 — 윈도우 = 2026-01 ~ 2026-06, 6개 월 키
 * (2026-01-01 ~ 2026-06-01 00:00), 배타적 상한 2026-07-01 00:00.</p>
 */
@ExtendWith(MockitoExtension.class)
class MaxPeakStatusServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 6, 11).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private static final LocalDateTime M1 = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime M2 = LocalDateTime.of(2026, 2, 1, 0, 0);
    private static final LocalDateTime M3 = LocalDateTime.of(2026, 3, 1, 0, 0);
    private static final LocalDateTime M4 = LocalDateTime.of(2026, 4, 1, 0, 0);
    private static final LocalDateTime M5 = LocalDateTime.of(2026, 5, 1, 0, 0);
    private static final LocalDateTime M6 = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime END_EXCLUSIVE = LocalDateTime.of(2026, 7, 1, 0, 0);

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    private MaxPeakStatusService service() {
        return new MaxPeakStatusService(FIXED_CLOCK, tagRepository, rawDataRepository);
    }

    @Test
    void 모든_월에_데이터가_있으면_6개_포인트를_오름차순으로_반환한다() {
        List<Tag> pwiTags = List.of(mockTag("T-PWI-1"), mockTag("T-PWI-2"));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(pwiTags);
        given(rawDataRepository.findMonthlyMaxMinuteSumElpwr(anyList(), any(), any()))
                .willReturn(List.of(
                        peak(M1, "1010.0000"), peak(M2, "1020.0000"), peak(M3, "1030.0000"),
                        peak(M4, "1040.0000"), peak(M5, "1050.0000"), peak(M6, "1047.4000")));

        MaxPeakStatusDto result = service().getMaxPeakStatus();

        assertThat(result.getUnit()).isEqualTo("kW");
        assertThat(result.getPoints()).hasSize(6);
        assertThat(result.getPoints()).extracting(p -> p.getBaseDtm())
                .containsExactly(M1, M2, M3, M4, M5, M6);   // 오름차순
        assertThat(result.getPoints()).allSatisfy(p -> assertThat(p.getPeakVal()).isNotNull());
        assertThat(result.getPoints().get(0).getPeakVal()).isEqualByComparingTo("1010.0000");
        assertThat(result.getPoints().get(5).getPeakVal()).isEqualByComparingTo("1047.4000");
    }

    @Test
    void 일부_월이_결측이면_해당_슬롯은_null이고_항상_6슬롯이다() {
        List<Tag> pwiTags = List.of(mockTag("T-PWI-1"));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(pwiTags);
        // dev 환경처럼 2026-05·06 만 데이터 존재 — 나머지 4개월 결측(sparse)
        given(rawDataRepository.findMonthlyMaxMinuteSumElpwr(anyList(), any(), any()))
                .willReturn(List.of(peak(M5, "1050.0000"), peak(M6, "990.2500")));

        MaxPeakStatusDto result = service().getMaxPeakStatus();

        assertThat(result.getPoints()).hasSize(6);
        assertThat(result.getPoints().get(0).getPeakVal()).isNull();   // 2026-01 결측
        assertThat(result.getPoints().get(3).getPeakVal()).isNull();   // 2026-04 결측
        assertThat(result.getPoints().get(4).getPeakVal()).isEqualByComparingTo("1050.0000"); // 2026-05
        assertThat(result.getPoints().get(5).getPeakVal()).isEqualByComparingTo("990.2500");  // 2026-06
    }

    @Test
    void PWI태그가_0개면_쿼리를_호출하지_않고_6슬롯_전부_null을_반환한다() {
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(List.of());

        MaxPeakStatusDto result = service().getMaxPeakStatus();

        assertThat(result.getUnit()).isEqualTo("kW");
        assertThat(result.getPoints()).hasSize(6);
        assertThat(result.getPoints()).allSatisfy(p -> assertThat(p.getPeakVal()).isNull());
        Mockito.verifyNoInteractions(rawDataRepository);
    }

    @Test
    void 쿼리_결과가_빈_리스트면_6슬롯_전부_null을_반환한다() {
        List<Tag> pwiTags = List.of(mockTag("T-PWI-1"));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(pwiTags);
        given(rawDataRepository.findMonthlyMaxMinuteSumElpwr(anyList(), any(), any()))
                .willReturn(List.of());

        MaxPeakStatusDto result = service().getMaxPeakStatus();

        assertThat(result.getPoints()).hasSize(6);
        assertThat(result.getPoints()).allSatisfy(p -> assertThat(p.getPeakVal()).isNull());
    }

    @Test
    void PWI_활성_태그_필터와_당월포함_6개월_윈도우를_쿼리에_전달한다() {
        List<Tag> pwiTags = List.of(mockTag("T-PWI-1"));
        given(tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y))
                .willReturn(pwiTags);
        given(rawDataRepository.findMonthlyMaxMinuteSumElpwr(anyList(), any(), any()))
                .willReturn(List.of());

        service().getMaxPeakStatus();

        verify(tagRepository).findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y);
        ArgumentCaptor<List<String>> tags = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(rawDataRepository)
                .findMonthlyMaxMinuteSumElpwr(tags.capture(), start.capture(), end.capture());

        assertThat(tags.getValue()).containsExactly("T-PWI-1");
        assertThat(start.getValue()).isEqualTo(M1);                 // 당월-5개월 1일 00:00 (inclusive)
        assertThat(end.getValue()).isEqualTo(END_EXCLUSIVE);        // 당월+1개월 1일 00:00 (exclusive)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private Tag mockTag(String tagSrlNo) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        return tag;
    }

    private RawDataBucketPeakDto peak(LocalDateTime baseDtm, String peakVal) {
        return new RawDataBucketPeakDto(baseDtm, new BigDecimal(peakVal));
    }
}
