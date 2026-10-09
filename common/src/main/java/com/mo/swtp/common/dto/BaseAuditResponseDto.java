package com.mo.swtp.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.common.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * {@link BaseEntity} 의 공통 감사(Audit) 메타 4컬럼을 응답 DTO 에 일관 노출하기 위한 추상 부모 클래스.
 *
 * <p>마스터(_m) 응답 DTO 가 본 클래스를 상속하면 {@code rgstrDtm}·{@code updtDtm}·{@code rgstrId}·{@code updtId}
 * 4 컬럼이 자동으로 응답 스키마에 포함된다. 자식 DTO 는 {@link #applyAuditMeta(BaseEntity)} 헬퍼를
 * 정적 팩토리({@code from(Entity)}) 내부에서 호출하여 엔티티의 메타 4컬럼을 일괄 주입한다.</p>
 *
 * <p>직렬화 정책: {@link JsonFormat} 으로 {@code "yyyy-MM-dd HH:mm:ss"} 초 단위 SSOT 적용
 * — OT 감사 추적은 초 단위 식별이 필요하다.</p>
 *
 * <p>적용 범위: 마스터(_m) 응답 DTO 한정. 도메인 룰 명세·실시간 통지·요약·시계열(_h) 응답 DTO 는 적용 외.
 * 패턴 상세: {@code .claude/rules/api-patterns.md §BaseAuditResponseDto 패턴}.</p>
 */
@Getter
@Schema(description = "응답 메타 공통 부모 — BaseEntity 의 audit 메타 4컬럼")
public abstract class BaseAuditResponseDto {

    @Schema(description = "등록 일시", example = "2026-05-11 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rgstrDtm;

    @Schema(description = "수정 일시", example = "2026-05-11 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updtDtm;

    @Schema(description = "등록자 ID", example = "system")
    private String rgstrId;

    @Schema(description = "수정자 ID", example = "system")
    private String updtId;

    /**
     * {@link BaseEntity} 의 메타 4 컬럼을 본 응답 DTO 에 일괄 주입한다.
     *
     * <p>자식 DTO 의 정적 팩토리 메서드({@code from(Entity)}) 내부에서 호출한다.
     * 4 컬럼 매핑을 자식이 직접 setter / 생성자 인자로 받지 않도록 표준화한다.</p>
     *
     * @param entity 메타 4컬럼을 보유한 {@link BaseEntity} 자식 엔티티
     */
    protected void applyAuditMeta(BaseEntity entity) {
        this.rgstrDtm = entity.getRgstrDtm();
        this.updtDtm = entity.getUpdtDtm();
        this.rgstrId = entity.getRgstrId();
        this.updtId = entity.getUpdtId();
    }
}
