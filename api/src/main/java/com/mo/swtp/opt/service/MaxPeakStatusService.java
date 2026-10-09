package com.mo.swtp.opt.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.opt.dto.MaxPeakStatusDto;
import com.mo.swtp.opt.dto.MaxPeakStatusDto.MaxPeakStatusPoint;
import com.mo.swtp.raw.dto.RawDataBucketPeakDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용량트렌드 3번섹션 — 최대 피크 현황 조회 서비스 (읽기 전용).
 *
 * <p>현재 월 포함 최근 6개월 동안, 전체 활성 PWI(순시전력) 태그를 분(分)별로 합산한 값 중 그 달의 최댓값
 * ({@code MAX_over_month(SUM_over_facilities(PWI per minute))}) 을 6개 월 슬롯으로 반환한다
 * (사용량트렌드-3번섹션 PLAN1). 흐름: (1) {@link Clock} 기준 6개 월 키 산출, (2) 전역 활성 PWI 태그 수집,
 * (3) 월별 최대 피크 쿼리 호출, (4) 6슬롯 병합(결측 월 {@code peakVal=null}).</p>
 *
 * <p>전체 설비 합산 = 전체 활성 PWI 태그({@code tag_se_cd='PWI' AND use_yn='Y'}, 태양광 발전 포함). 현재 펌프
 * 서브미터만 존재하고 메인 총괄 미터 부재라 이중계상 없음 — 향후 메인 미터 또는 발전/소비 구분 필요 시 별도
 * 사이클 재검토 (사용량트렌드-3번섹션 ANALYZE1, 2번섹션 {@link EnergyUsageTrendService} 기록 동형).</p>
 *
 * <p>윈도우·6개 월 키는 {@code LocalDate.now(clock)} 기준으로 산출하며 입력 파라미터가 없다. 시각 의존을
 * {@link Clock} 주입으로 제거해 단위 테스트가 고정 시점({@code Clock.fixed})으로 결정적이다 (PLAN1 §구현 방향 3·5).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaxPeakStatusService {

    /** 순시전력 피크 응답 단위. */
    private static final String UNIT_KW = "kW";

    /** 반환 월 슬롯 수 (현재 월 포함 최근 6개월). */
    private static final int MONTH_SLOTS = 6;

    private final Clock clock;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 현재 월 포함 최근 6개월의 월별 최대 순시전력 피크를 조회한다.
     *
     * @return 6개 월 슬롯의 최대 피크 응답 (월 시작 일시 오름차순, 데이터 없는 달 {@code peakVal=null})
     */
    public MaxPeakStatusDto getMaxPeakStatus() {
        List<LocalDateTime> monthKeys = buildMonthKeys();
        List<String> pwiTags = pwiTagSrlNos();
        Map<LocalDateTime, BigDecimal> peakByMonth = pwiTags.isEmpty()
                ? Map.of()
                : rawDataRepository.findMonthlyMaxMinuteSumElpwr(
                                pwiTags, monthKeys.get(0), endExclusive(monthKeys)).stream()
                        .filter(b -> b.peakVal() != null)   // 극단 케이스(전월 GOOD 값 전무) toMap NPE 방어 → null 슬롯 귀결
                        .collect(Collectors.toMap(
                                RawDataBucketPeakDto::baseDtm, RawDataBucketPeakDto::peakVal));
        return MaxPeakStatusDto.of(UNIT_KW, mergeToSlots(monthKeys, peakByMonth));
    }

    /** {@link Clock} 기준 현재 월부터 5개월 전까지의 월 시작 일시 6건을 오름차순으로 산출한다. */
    private List<LocalDateTime> buildMonthKeys() {
        YearMonth cur = YearMonth.from(LocalDate.now(clock));
        return IntStream.rangeClosed(0, MONTH_SLOTS - 1)
                .mapToObj(i -> cur.minusMonths(MONTH_SLOTS - 1L - i).atDay(1).atStartOfDay())
                .toList();
    }

    /** 윈도우 배타적 상한 — 마지막(현재) 월 키의 익월 1일 00:00. */
    private LocalDateTime endExclusive(List<LocalDateTime> monthKeys) {
        return monthKeys.get(monthKeys.size() - 1).plusMonths(1);
    }

    /** 시스템 전역 활성 PWI(순시전력) 태그 시리얼번호 수집 (태양광 발전 포함). */
    private List<String> pwiTagSrlNos() {
        return tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y).stream()
                .map(Tag::getTagSrlNo)
                .toList();
    }

    /** 6개 월 키를 순회하며 sparse 쿼리 결과를 병합한다 — 결측 월은 {@code peakVal=null}. */
    private List<MaxPeakStatusPoint> mergeToSlots(
            List<LocalDateTime> monthKeys, Map<LocalDateTime, BigDecimal> peakByMonth) {
        return monthKeys.stream()
                .map(key -> MaxPeakStatusPoint.of(key, peakByMonth.get(key)))
                .toList();
    }
}
