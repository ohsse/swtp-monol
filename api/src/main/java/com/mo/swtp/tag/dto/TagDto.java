package com.mo.swtp.tag.dto;

import com.mo.swtp.common.dto.BaseAuditResponseDto;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 태그 조회 응답 DTO.
 *
 * <p>측정 유형별 한글 설명({@link #description})과 단위({@link #unit})는
 * {@link TagMeasurementType} enum 으로부터 매핑한다 — {@code tag_m.unit_cd} 컬럼 폐기 결과
 * (태그관리 ANALYZE1 안건 6).</p>
 *
 * <p>enum 필드는 {@code @Schema(implementation = X.class)} 명시 의무
 * ({@code .claude/rules/api-patterns.md} §DTO @Schema(implementation) 명시 패턴).</p>
 *
 * <p>{@link BaseAuditResponseDto} 를 상속하여 공통 메타 4컬럼
 * ({@code rgstrDtm}·{@code updtDtm}·{@code rgstrId}·{@code updtId}) 을 부모로부터 일관 노출한다
 * — 기존 2컬럼 노출에서 4컬럼으로 확장
 * (base_audit_response_dto ANALYZE1, 2026-05-11).</p>
 */
@Getter
@NoArgsConstructor
@Schema(description = "태그 조회 응답 DTO")
public class TagDto extends BaseAuditResponseDto {

    @Schema(description = "태그 시리얼번호 (자연키 PK)", example = "706-FRI-001-001")
    private String tagSrlNo;

    @Schema(description = "소속 계측기 ID (instrument_m FK)")
    private String instrumentId;

    @Schema(description = "측정 유형 코드", implementation = TagMeasurementType.class)
    private TagMeasurementType tagSeCd;

    @Schema(description = "측정 유형 한글 설명 (enum 매핑)", example = "유량")
    private String description;

    @Schema(description = "측정 단위 (enum 매핑, 빈 문자열은 단위 없음)", example = "m³/h")
    private String unit;

    @Schema(description = "태그 자유형 설명")
    private String tagDesc;

    @Schema(description = "입출력 구분 코드", implementation = IoCode.class)
    private IoCode ioCd;

    @Schema(description = "사용 여부", implementation = YnType.class)
    private YnType useYn;

    private TagDto(Tag tag) {
        this.tagSrlNo = tag.getTagSrlNo();
        this.instrumentId = tag.getInstrument().getInstrumentId();
        this.tagSeCd = tag.getTagSeCd();
        this.description = tag.getTagSeCd().getDescription();
        this.unit = tag.getTagSeCd().getUnit();
        this.tagDesc = tag.getTagDesc();
        this.ioCd = tag.getIoCd();
        this.useYn = tag.getUseYn();
        applyAuditMeta(tag);
    }

    /**
     * Tag 엔티티로부터 조회 응답 DTO 를 생성한다.
     *
     * @param tag 태그 엔티티
     * @return enum 단위/설명 매핑이 적용된 응답 DTO
     */
    public static TagDto from(Tag tag) {
        return new TagDto(tag);
    }
}
