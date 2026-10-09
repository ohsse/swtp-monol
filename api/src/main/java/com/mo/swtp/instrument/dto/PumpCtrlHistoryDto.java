package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.instrument.domain.enumtype.ControlCommand;
import com.mo.swtp.instrument.domain.enumtype.ControlResult;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 송수펌프 제어이력 3번섹션 — 제어 이력 목록 행 응답 DTO (읽기 전용).
 *
 * <p>조회기간 동안의 펌프 제어 이력을 시간 역순으로 표출하는 목록의 단일 행이다. {@code pump_ctrl_h} ⋈
 * {@code instrument_m}(펌프명) + 제어 태그번호 상관 서브쿼리 결과를 Querydsl {@code Projections.constructor}
 * 로 1:1 매핑한다 (제어이력 재도입 PLAN1 §구현 방향 Phase 2·4).</p>
 *
 * <p>컬럼 매핑 — 제어요청시간({@code ctrlDtm}) · 제어대상펌프명({@code pumpNm}) · 제어 태그번호
 * ({@code ctrlTagSrlNo} — {@code tag_se_cd='CMD' AND io_cd='OUTPUT'}, 미존재 시 null) · 제어요청구분
 * ({@code ctrlDiv} 가동/중지) · 제어결과({@code ctrlRslt} 제어완료/제어취소) · 갱신시간/제어완료시간
 * ({@code updtDtm}) · 운전모드({@code aiDrvnMod} — NULL=수동).</p>
 *
 * <p>읽기 전용 목록 응답이므로 {@code BaseAuditResponseDto} 를 상속하지 않는다
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 패턴 §적용 범위} — 시계열(_h) 응답 미적용 정합).
 * Querydsl 프로젝션 대상이므로 인자 순서가 프로젝션 표현식 순서와 일치하는 public 생성자를 보유한다
 * (요청 역직렬화 대상 아님 — {@code @NoArgsConstructor} 불요).</p>
 */
@Getter
@Schema(description = "송수펌프 제어이력 목록 행 응답 DTO — 3번섹션")
public class PumpCtrlHistoryDto {

    @Schema(description = "제어요청시간", example = "2026-06-01 08:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime ctrlDtm;

    @Schema(description = "제어대상 펌프 ID (instrument_id)", example = "I-PUMP-001")
    private final String pumpId;

    @Schema(description = "제어대상 펌프명", example = "송수1호기")
    private final String pumpNm;

    @Schema(description = "제어대상 펌프 제어 태그번호 (tag_se_cd=CMD, io_cd=OUTPUT). 제어 태그 미존재 시 null",
            example = "706-CMD-001-001")
    private final String ctrlTagSrlNo;

    @Schema(description = "제어요청구분 (가동/중지)", implementation = ControlCommand.class)
    private final ControlCommand ctrlDiv;

    @Schema(description = "제어결과 (제어완료/제어취소)", implementation = ControlResult.class)
    private final ControlResult ctrlRslt;

    @Schema(description = "갱신시간 (제어완료시간)", example = "2026-06-01 08:30:05")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime updtDtm;

    @Schema(description = "운전모드 (AI/AI추천/AI분석). 수동 제어 시 null", implementation = AiDrvnModeCode.class)
    private final AiDrvnModeCode aiDrvnMod;

    /**
     * Querydsl {@code Projections.constructor} 매핑 생성자 — 인자 순서가 프로젝션 표현식 순서와 일치한다.
     *
     * @param ctrlDtm      제어요청시간
     * @param pumpId       제어대상 펌프 ID (instrument_id 논리 참조)
     * @param pumpNm       제어대상 펌프명 (instrument_m 조인 — 미매칭 시 null)
     * @param ctrlTagSrlNo 제어 태그번호 (CMD/OUTPUT 상관 서브쿼리 — 미존재 시 null)
     * @param ctrlDiv      제어요청구분 (가동/중지)
     * @param ctrlRslt     제어결과 (제어완료/제어취소)
     * @param updtDtm      갱신시간 (제어완료시간)
     * @param aiDrvnMod    운전모드 (NULL=수동 제어)
     */
    public PumpCtrlHistoryDto(
            LocalDateTime ctrlDtm,
            String pumpId,
            String pumpNm,
            String ctrlTagSrlNo,
            ControlCommand ctrlDiv,
            ControlResult ctrlRslt,
            LocalDateTime updtDtm,
            AiDrvnModeCode aiDrvnMod) {
        this.ctrlDtm = ctrlDtm;
        this.pumpId = pumpId;
        this.pumpNm = pumpNm;
        this.ctrlTagSrlNo = ctrlTagSrlNo;
        this.ctrlDiv = ctrlDiv;
        this.ctrlRslt = ctrlRslt;
        this.updtDtm = updtDtm;
        this.aiDrvnMod = aiDrvnMod;
    }
}
