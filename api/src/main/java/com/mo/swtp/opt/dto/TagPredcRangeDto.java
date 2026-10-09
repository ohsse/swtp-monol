package com.mo.swtp.opt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 예측 시계열 태그 시점 범위 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.opt.repository.TagPredcRangeCustomRepository#findByTagSrlNosAndPredcDtmRange(java.util.List,
 * java.time.LocalDateTime, java.time.LocalDateTime)} 의 반환 element 로, Querydsl 범위 쿼리 결과 행을 1:1
 * 매핑한다. 응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외
 * (운전현황분석-10번섹션 PLAN1 §Repository 신규).</p>
 *
 * <p>9번 섹션 {@link TagPredcLatestDto} (단일 시점 DISTINCT ON 최신값) 와 의도·SQL 흐름이 다르다 —
 * 본 사이클은 시점 범위 ({@code predc_dtm >= startDtm AND predc_dtm < endDtm}) 시계열이며 별도 Repository
 * + 별도 record 로 분리한다 (사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합).</p>
 *
 * <p>{@code predc_1m_h} 는 {@code corr_val}·{@code quality_cd} 컬럼 부재이므로 {@code corrVal}·{@code rawVal}·
 * {@code qualityCd} 3필드 없이 {@code predcVal} 단일 측정값만 보유. SCADA QUALITY 분기 미적용
 * ({@code ot-integration.md §3} OPS BAD 즉시 격상은 실측 전용).</p>
 *
 * @param tagSrlNo 태그 시리얼번호 ({@code tag_m.tag_srl_no} 논리 참조)
 * @param predcDtm 예측 대상 일시
 * @param predcVal 예측 측정값 (NULL 허용 — 결측 표현)
 */
public record TagPredcRangeDto(
        String tagSrlNo,
        LocalDateTime predcDtm,
        BigDecimal predcVal
) {
}
