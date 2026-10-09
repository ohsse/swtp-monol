package com.mo.swtp.raw.dto;

import java.time.LocalDateTime;

/**
 * SCADA 원시 데이터 가동(ON) 상태 시점 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataCustomRepository#findOnStateByTagSrlNosAndDtmRange} 의
 * 반환 element 로, 가동상태(OPS) 태그의 {@code quality_cd = 'GOOD'} + {@code raw_val = 1} (ON) 행만
 * 투영한다 (방안B — 쿼리 레벨 ON 필터, 송수펌프가동이력_4번섹션 ANALYZE1 안건 4). 응답 DTO 가 아닌 Service 내부
 * 런렝스 인코딩 입력 전용이므로 Swagger 노출 대상 외 ({@link RawDataBucketDto} 선례 동형).</p>
 *
 * <p>OPS 는 Hold Last Value 미적용 측정유형이므로 {@code corr_val} 을 투영하지 않는다 — 통신단절(BAD)·OFF·결측
 * 시점은 ON 행이 아니므로 조회되지 않으며, 결과 시각열의 1분 간극(gap) 이 자연 세그먼트 경계가 된다
 * ({@code .claude/rules/ot-integration.md §3} OPS 즉시 BAD 격상 정책).</p>
 *
 * @param tagSrlNo 태그 시리얼번호 ({@code tag_m.tag_srl_no} 논리 참조)
 * @param acqDtm   수집 일시 (ON 상태 시점, {@code acq_dtm})
 */
public record RawDataOnStateDto(
        String tagSrlNo,
        LocalDateTime acqDtm
) {
}
