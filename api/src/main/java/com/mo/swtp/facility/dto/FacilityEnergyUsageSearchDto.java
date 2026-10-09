package com.mo.swtp.facility.dto;

import com.mo.swtp.common.enumtype.InqUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 시설별 사용량 2번섹션 — 운영시설 전력 사용량 조회 검색 DTO.
 *
 * <p>대시보드 1번섹션 파라미터 3종을 수신한다: 집계단위({@link #inqUnit} — 시/일/월) · 검색시작일자
 * ({@link #fromDt}) · 검색종료일자({@link #toDt}). {@link com.mo.swtp.instrument.dto.PumpPeriodSearchDto}
 * 의 기간 변환 규칙({@link #toStartDtm()} · {@link #toEndExclusiveDtm()}) 을 동형 복제하고 집계단위 + 기간
 * 상한 검증을 더한다 (사이클 간 자산 자동 원용 금지 — 상속 대신 동형 복제, 시설별사용량-2번섹션 PLAN1
 * §재사용 자산).</p>
 *
 * <p>{@code @ModelAttribute} GET 쿼리 바인딩 — {@code @Getter @Setter @NoArgsConstructor}
 * ({@code FacilitySearchDto} 선례 동형). 기간 검증 실패 시 예외 throw 는 Service 가 수행하며, 본 DTO 는
 * {@link #isValid()} boolean 판정 + 일시 변환 메서드만 제공한다 (DTO 예외 throw 회피).</p>
 *
 * <p><strong>집계단위 제약</strong> — 본 화면은 시/일/월 3종만 허용한다. {@link InqUnit#YEAR} 는 거부
 * ({@link #isValid()} false). <strong>조회기간 상한</strong> — {@code fromDt~toDt} 간격 {@value #MAX_PERIOD_DAYS}일
 * 이내 (13개월 — {@code rawdata_1m_h} 13개월 롤링 보존 정합, 보존 한계 초과 구간 무의미 조회 차단,
 * 시설별사용량-2번섹션 PLAN1 §가정 결정).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "시설별 사용량 2번섹션 — 운영시설 전력 사용량 조회 검색 조건")
public class FacilityEnergyUsageSearchDto {

    /** 조회기간 상한 — 13개월(396일). {@code rawdata_1m_h} 13개월 롤링 보존 한계 정합. */
    private static final long MAX_PERIOD_DAYS = 396L;

    @Schema(description = "집계 단위 — 시(HOUR)/일(DAY)/월(MONTH). YEAR 는 본 화면 미지원",
            implementation = InqUnit.class)
    private InqUnit inqUnit;

    @Schema(description = "검색 시작일 (포함)", example = "2026-01-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDt;

    @Schema(description = "검색 종료일 (포함 — 당일 23:59:59 까지)", example = "2026-01-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDt;

    /**
     * 조회 파라미터 유효성을 판정한다.
     *
     * <p>다음 4조건을 모두 만족해야 {@code true}: (1) 집계단위·시작일·종료일 모두 non-null, (2) 시작일이
     * 종료일을 초과하지 않음, (3) 집계단위가 {@link InqUnit#YEAR} 아님 (시/일/월만 허용), (4) 시작~종료
     * 간격이 {@value #MAX_PERIOD_DAYS}일 이내.</p>
     *
     * @return 4조건을 모두 만족하면 {@code true}
     */
    public boolean isValid() {
        if (inqUnit == null || fromDt == null || toDt == null) {
            return false;
        }
        if (fromDt.isAfter(toDt)) {
            return false;
        }
        if (inqUnit == InqUnit.YEAR) {
            return false;
        }
        return ChronoUnit.DAYS.between(fromDt, toDt) <= MAX_PERIOD_DAYS;
    }

    /**
     * 조회 시작 일시 (inclusive) — {@code fromDt} 자정.
     *
     * @return {@code fromDt.atStartOfDay()}
     */
    public LocalDateTime toStartDtm() {
        return fromDt.atStartOfDay();
    }

    /**
     * 조회 종료 일시 (exclusive) — {@code toDt} 익일 자정. 종료일 당일 데이터를 포함시키기 위한 배타적 상한.
     *
     * @return {@code toDt.plusDays(1).atStartOfDay()}
     */
    public LocalDateTime toEndExclusiveDtm() {
        return toDt.plusDays(1).atStartOfDay();
    }
}
