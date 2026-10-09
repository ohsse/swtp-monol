package com.mo.swtp.opt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 예측 시계열 태그 최신값 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.opt.repository.TagPredcLatestCustomRepository#findLatestByTagSrlNos(java.util.List)}
 * 의 반환 element 로, native DISTINCT ON 쿼리 결과 행을 1:1 매핑한다. 응답 DTO 가 아닌 Service 내부
 * 전송 전용이므로 Swagger 노출 대상 외 (운전현황분석-9번섹션 PLAN1 §내부 DTO).</p>
 *
 * <p>4번 섹션 {@code com.mo.swtp.raw.dto.RawDataLatestDto} (5필드: tagSrlNo·rawVal·corrVal·acqDtm·qualityCd)
 * 대비 차이 — {@code predc_1m_h} 는 {@code corr_val}·{@code quality_cd} 컬럼 부재이므로
 * {@code corrVal}·{@code rawVal}·{@code qualityCd} 3필드 제거 + {@code acqDtm} → {@code predcDtm} 명칭 변경.
 * Hold Last Value 적용 결과 또는 운영자 보정 개념이 예측 데이터에는 적용되지 않음 +
 * SCADA QUALITY 분기 미적용 ({@code ot-integration.md §3} OPS BAD 즉시 격상은 실측 전용).</p>
 *
 * @param tagSrlNo  태그 시리얼번호 ({@code tag_m.tag_srl_no} 논리 참조)
 * @param predcDtm  예측 대상 일시
 * @param predcVal  예측 측정값 (NULL 허용 — 결측 표현)
 */
public record TagPredcLatestDto(
        String tagSrlNo,
        LocalDateTime predcDtm,
        BigDecimal predcVal
) {
}
