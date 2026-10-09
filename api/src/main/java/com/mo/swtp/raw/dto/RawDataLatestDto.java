package com.mo.swtp.raw.dto;

import com.mo.swtp.raw.domain.enumtype.QualityCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCADA 원시 데이터 최신값 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataCustomRepository#findLatestByTagSrlNos(java.util.List)}
 * 의 반환 element 로, native DISTINCT ON 쿼리 결과 행을 1:1 매핑한다.
 * 응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외
 * (송수펌프제어분석-3번섹션 PLAN1 §구현 방향).</p>
 *
 * @param tagSrlNo   태그 시리얼번호 ({@code tag_m.tag_srl_no} 논리 참조)
 * @param rawVal     SCADA 원본 측정값 (BAD QUALITY 시 NULL)
 * @param corrVal    보정/수정 측정값 (Hold Last Value 적용 결과 또는 운영자 보정, NULL 허용)
 * @param acqDtm     수집 일시
 * @param qualityCd  SCADA QUALITY 코드 (GOOD/BAD/UNCERTAIN)
 */
public record RawDataLatestDto(
        String tagSrlNo,
        BigDecimal rawVal,
        BigDecimal corrVal,
        LocalDateTime acqDtm,
        QualityCode qualityCd
) {
}
