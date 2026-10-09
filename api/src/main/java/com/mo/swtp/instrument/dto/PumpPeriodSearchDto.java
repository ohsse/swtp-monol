package com.mo.swtp.instrument.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 송수펌프 가동이력 — from~to 조회기간 공통 부모 검색 DTO.
 *
 * <p>1번섹션 파라미터 중 from~to 날짜 필드만 사용하는 조회들이 공유하는 공통 부모이다. 기간 변환 규칙
 * ({@link #toStartDtm()} · {@link #toEndExclusiveDtm()}) 과 기간 유효성 판정 ({@link #isValid()}) 의 SSOT 를
 * 본 클래스 1곳에 보유하여 3번섹션 시계열 차트({@link PumpTimeSeriesSearchDto}) 와 4번섹션 가동상태 타임라인이
 * 동일 변환 규칙을 공유한다 (송수펌프가동이력_4번섹션 PLAN1 §구현 방향 1, api/CLAUDE.md §DTO 규칙 "공통 검색 조건
 * 부모 클래스").</p>
 *
 * <p>{@code @ModelAttribute} GET 쿼리 바인딩 — {@code @Getter @Setter @NoArgsConstructor}
 * ({@link InstrumentSearchDto} 선례 동형). 기간 검증 실패 시 예외 throw 는 Service 가 수행하며, 본 DTO 는
 * {@link #isValid()} boolean 판정 + 일시 변환 메서드만 제공한다 (DTO 예외 throw 회피).</p>
 *
 * <p><strong>기간 변환 정책</strong> — 종료일 포함 의미: 조회 범위는 {@code [fromDt 00:00, toDt+1일 00:00)} 으로
 * 변환되어 {@code toDt} 당일 23:59:59 까지의 데이터가 포함된다. 시간 직렬화 SSOT 는 {@code "yyyy-MM-dd HH:mm:ss"}
 * (3번섹션 {@code baseDtm} 정합).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "송수펌프 가동이력 from~to 조회기간 공통 검색 조건")
public class PumpPeriodSearchDto {

    @Schema(description = "조회 시작일 (포함)", example = "2024-07-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDt;

    @Schema(description = "조회 종료일 (포함 — 당일 23:59:59 까지)", example = "2024-07-09")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDt;

    /**
     * 조회기간 유효성을 판정한다.
     *
     * @return 시작일·종료일이 모두 non-null 이고 시작일이 종료일을 초과하지 않으면 {@code true}
     */
    public boolean isValid() {
        return fromDt != null && toDt != null && !fromDt.isAfter(toDt);
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
