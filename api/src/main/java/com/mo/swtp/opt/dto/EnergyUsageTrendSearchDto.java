package com.mo.swtp.opt.dto;

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
 * 사용량트렌드 2번섹션 — 정수장 전체 전력량 추이 조회 검색 DTO.
 *
 * <p>사용량트렌드 대시보드 1번섹션 파라미터 3종을 수신한다: 집계단위({@link #inqUnit} — 시/일/월) ·
 * 검색시작일자({@link #fromDt}) · 검색종료일자({@link #toDt}). 시설별사용량-5번섹션
 * {@code FacilityEnergyTrendSearchDto} 와 파라미터·검증 규칙이 100% 동일하나, 화면·SPEC 경계 분리를 위해
 * 상속·공유 대신 동형 복제한다 (사이클 간 자산 자동 원용 금지 — {@code @Schema} 가 5번섹션 맥락으로 frontend
 * SPEC 을 오염시키지 않도록 본 섹션 전용 신규 자산으로 둔다, 사용량트렌드-2번섹션 ANALYZE1).</p>
 *
 * <p>{@code @ModelAttribute} GET 쿼리 바인딩 — {@code @Getter @Setter @NoArgsConstructor}
 * ({@code FacilityEnergyTrendSearchDto} 선례 동형). 기간 검증 실패 시 예외 throw 는 Service 가 수행하며,
 * 본 DTO 는 {@link #isValid()} boolean 판정 + 일시 변환 메서드만 제공한다 (DTO 예외 throw 회피).</p>
 *
 * <p><strong>집계단위 제약</strong> — 본 화면은 시/일/월 3종만 허용한다. {@link InqUnit#YEAR} 는 거부
 * ({@link #isValid()} false). <strong>조회기간 상한</strong> — {@code fromDt~toDt} 간격
 * {@value #MAX_PERIOD_DAYS}일 이내 (13개월 — {@code rawdata_1m_h} 13개월 롤링 보존 정합, 보존 한계 초과 구간
 * 무의미 조회 차단). <strong>종료일 처리</strong> — {@link #toEndExclusiveDtm()} 가 {@code toDt} 익일 자정을
 * 배타적 상한으로 반환하여 종료일 당일 데이터를 모두 포함시킨다 ("종료일자는 익일 00시 이전까지" 요건 충족).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "사용량트렌드 2번섹션 — 정수장 전체 전력량 추이 조회 검색 조건")
public class EnergyUsageTrendSearchDto {

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
     * 조회 종료 일시 (exclusive) — {@code toDt} 익일 자정. 종료일 당일 데이터를 모두 포함시키기 위한 배타적 상한
     * ("종료일자는 익일 00시 이전까지의 모든 데이터를 취합" 요건 정확 충족).
     *
     * @return {@code toDt.plusDays(1).atStartOfDay()}
     */
    public LocalDateTime toEndExclusiveDtm() {
        return toDt.plusDays(1).atStartOfDay();
    }
}
