package com.mo.swtp.raw.dto;

import com.mo.swtp.raw.domain.enumtype.QualityCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCADA 원시 데이터 유출 시계열 Service 내부 전송 DTO — 운전현황분석 7번 섹션.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataOutflowCustomRepository#findByTagSrlNosAndDtmRange(java.util.List,
 * java.time.LocalDateTime, java.time.LocalDateTime)} 의 반환 element 로, Querydsl 범위 쿼리 결과 행을 1:1
 * 매핑한다. 응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외
 * (운전현황분석-7번섹션 PLAN1 §Repository 신규).</p>
 *
 * <p>5번 섹션 {@link RawDataLatestDto} (시점 범위 조회 결과를 공용) 와 의도가 다르다 — 7번 섹션은 유출 유량(FRI)·
 * 압력(PRI)·펌프 가동상태(OPS) 라인+막대 표출 전용이며, 5·10번 Repository 자산을 재사용하지 않고 별도 record +
 * 별도 Repository 로 분리한다 (사용자 결정 2026-06-01 "사이클 독립성 우선" · 사용자 메모리 "사이클 간 자산
 * 자동 원용 금지" 정합).</p>
 *
 * @param tagSrlNo   태그 시리얼번호 ({@code tag_m.tag_srl_no} 논리 참조)
 * @param acqDtm     수집 일시 (1분 슬롯 키)
 * @param rawVal     SCADA 원본 측정값 (BAD QUALITY 시 NULL)
 * @param corrVal    보정/수정 측정값 (Hold Last Value 적용 결과 또는 운영자 보정, NULL 허용)
 * @param qualityCd  SCADA QUALITY 코드 (GOOD/BAD/UNCERTAIN)
 */
public record RawDataOutflowDto(
        String tagSrlNo,
        LocalDateTime acqDtm,
        BigDecimal rawVal,
        BigDecimal corrVal,
        QualityCode qualityCd
) {
}
