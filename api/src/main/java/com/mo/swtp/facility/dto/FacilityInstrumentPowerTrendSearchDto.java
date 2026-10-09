package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 설비별 사용량 7번섹션 — 설비별 순시전력 트렌드 조회 검색 DTO.
 *
 * <p>대시보드 1번섹션 파라미터 2종을 수신한다: 검색시작일자({@link #fromDt}) · 검색종료일자({@link #toDt}).
 * 2번섹션 시설ID 는 경로변수로 별도 수신하므로 본 DTO 에 포함하지 않는다. 7번섹션은 순시전력(PWI) 1분 시계열을
 * 분(分) 단위 전량 반환하므로 집계단위(inqUnit)가 없다 (설비별사용량-7번섹션 PLAN1 §가정 결정).</p>
 *
 * <p>{@link FacilityInstrumentEnergyUsageSearchDto}(5·6번섹션) 에서 조회기간 상한만 31일로 바꾼 from/to 전용
 * 동형 복제다 (상속 아님 — {@code @Schema} 맥락 분리·frontend SPEC 오염 회피, 사이클 간 자산 자동 원용 금지 정합).
 * {@code @ModelAttribute} GET 쿼리 바인딩 — {@code @Getter @Setter @NoArgsConstructor}. 기간 검증 실패 시
 * 예외 throw 는 Service 가 수행하며, 본 DTO 는 {@link #isValid()} boolean 판정 + 일시 변환 메서드만 제공한다
 * (DTO 예외 throw 회피).</p>
 *
 * <p><strong>조회기간 상한</strong> — {@code fromDt~toDt} 간격 {@value #MAX_PERIOD_DAYS}일 이내. 순시전력은
 * 설비당 1일 1440포인트(1분)의 raw 데이터를 분 단위 전량 반환하므로, 5·6번섹션(누적 전력량 396일) 대비 대폭 짧은
 * 상한으로 차트 가독성·응답속도를 보장한다 (PLAN1 §가정 — 사용자 확정 31일). 초과 시 {@code INVALID_SEARCH_PERIOD}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "설비별 사용량 7번섹션 — 설비별 순시전력 트렌드 조회 검색 조건")
public class FacilityInstrumentPowerTrendSearchDto {

    /** 조회기간 상한 — 31일. 순시 1분 raw 데이터 대용량(설비당 1일 1440포인트)·차트 가독성 정합. */
    private static final long MAX_PERIOD_DAYS = 31L;

    @Schema(description = "검색 시작일 (포함 — 당일 00:00:00 부터)", example = "2026-01-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDt;

    @Schema(description = "검색 종료일 (포함 — 당일 23:59:59 까지)", example = "2026-01-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDt;

    /**
     * 조회 파라미터 유효성을 판정한다.
     *
     * <p>다음 3조건을 모두 만족해야 {@code true}: (1) 시작일·종료일 모두 non-null, (2) 시작일이 종료일을
     * 초과하지 않음, (3) 시작~종료 간격이 {@value #MAX_PERIOD_DAYS}일 이내.</p>
     *
     * @return 3조건을 모두 만족하면 {@code true}
     */
    public boolean isValid() {
        if (fromDt == null || toDt == null) {
            return false;
        }
        if (fromDt.isAfter(toDt)) {
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
     * 조회 종료 일시 (exclusive) — {@code toDt} 익일 자정. 종료일 당일 23:59:59 데이터를 포함시키기 위한 배타적 상한.
     *
     * @return {@code toDt.plusDays(1).atStartOfDay()}
     */
    public LocalDateTime toEndExclusiveDtm() {
        return toDt.plusDays(1).atStartOfDay();
    }
}
