package com.mo.swtp.tag.dto;

import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 태그 등록/수정 요청 DTO.
 *
 * <p>{@code useYn} 필드는 보유하지 않는다 — 등록 시 {@link com.mo.swtp.tag.domain.Tag#create} 정적
 * 팩토리에서 {@code YnType.Y} 로 강제 할당, 비활성화는 별도 DELETE 엔드포인트로 처리한다.</p>
 *
 * <p>수정 요청 시 {@code tagSrlNo} · {@code instrumentId} 는 변경되지 않으며 무시된다 (서버 계층).
 * 계측기 교체는 deactivate + create 플로우로 처리한다 (태그관리 ANALYZE1 안건 4 블로커).</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "태그 등록/수정 요청 DTO")
public class TagUpsertDto {

    @NotBlank
    @Schema(description = "태그 시리얼번호 (등록 시 자연키 PK, 수정 시 무시)", example = "706-FRI-001-001")
    private String tagSrlNo;

    @NotBlank
    @Schema(description = "소속 계측기 ID (등록 시 instrument_m FK, 수정 시 무시)")
    private String instrumentId;

    @NotNull
    @Schema(description = "측정 유형 코드 (단위는 enum 매핑)", implementation = TagMeasurementType.class)
    private TagMeasurementType tagSeCd;

    @Schema(description = "태그 자유형 설명 (NULL 허용)")
    private String tagDesc;

    @NotNull
    @Schema(description = "입출력 구분 코드", implementation = IoCode.class)
    private IoCode ioCd;
}
