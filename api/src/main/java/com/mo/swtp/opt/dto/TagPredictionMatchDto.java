package com.mo.swtp.opt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 근접매칭 Repository → Service 내부 전송 DTO.
 *
 * <p>{@code TagPredictionCustomRepository.findNearestByTagSrlNos(...)} 가 태그별 근접행 1건씩을 반환할 때
 * 사용하는 단순 레코드. 응답 DTO 매핑은 {@code FacilityPredictionService} 가 본 결과를
 * {@code Map<tagSrlNo, _>} 그룹화 후 {@code FlwmtrPredictionDto}·{@code PumpPredictionDto} 로 변환한다.</p>
 *
 * <p>송수펌프제어분석-7번섹션 PLAN1 §5 — 내부 전송 DTO.</p>
 *
 * @param tagSrlNo 태그 시리얼번호 (논리 참조)
 * @param predcDtm 예측 대상 일시 (근접매칭 결과의 실제 시각)
 * @param predcVal 예측 측정값 (NULL 허용 — 결측 표현)
 */
public record TagPredictionMatchDto(
        String tagSrlNo,
        LocalDateTime predcDtm,
        BigDecimal predcVal) {
}
