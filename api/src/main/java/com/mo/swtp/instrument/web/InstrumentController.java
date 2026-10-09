package com.mo.swtp.instrument.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.instrument.dto.FlowMeterDto;
import com.mo.swtp.instrument.dto.InstrumentDto;
import com.mo.swtp.instrument.dto.InstrumentSearchDto;
import com.mo.swtp.instrument.dto.InstrumentUpsertDto;
import com.mo.swtp.instrument.dto.LevelMeterDto;
import com.mo.swtp.instrument.dto.PowerMeterDto;
import com.mo.swtp.instrument.dto.PressureMeterDto;
import com.mo.swtp.instrument.dto.PumpDto;
import com.mo.swtp.instrument.dto.ValveDto;
import com.mo.swtp.instrument.service.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계측기 관리 API 컨트롤러 — 자식 6종 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR) 통합 CRUD.
 *
 * <p>Jackson 다형성 ({@link InstrumentUpsertDto} {@code @JsonTypeInfo} + {@code @JsonSubTypes}) 으로
 * 단일 엔드포인트 (POST·PUT) 가 자식 6종 요청 본문을 자동 분기한다. 등록·수정·삭제는 향후 ADMIN 전용
 * 권한 분리 사이클에서 검토 (현재 인증 사용자 전체 허용).</p>
 *
 * <p>계측기관리CRUD ANALYZE1·PLAN1 (2026-05-12) 도입. FacilityController 패턴 재현.</p>
 */
@Tag(name = "07. 계측기 관리")
@RestController
@RequestMapping("/api/instrument")
@RequiredArgsConstructor
public class InstrumentController extends CommonController {

    private final InstrumentService instrumentService;

    @Operation(summary = "계측기 목록 조회",
            description = "equipTypeCd / useYn / facilityId 필터로 계측기 목록을 조회한다. "
                    + "세 필터 모두 NULL 허용 (NULL 시 전체 반환). "
                    + "정렬: useYn DESC (활성 우선) → dispOrd ASC → instrumentNm ASC.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — 응답 배열 element 는 equipTypeCd discriminator 로 자식 DTO 결정",
                    content = @Content(array = @ArraySchema(schema = @Schema(
                            oneOf = {PumpDto.class, ValveDto.class, FlowMeterDto.class,
                                    PressureMeterDto.class, LevelMeterDto.class, PowerMeterDto.class},
                            discriminatorProperty = "equipTypeCd")))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<List<InstrumentDto>>> findAllInstruments(
            @ModelAttribute InstrumentSearchDto searchDto) {
        return getResponseEntity(instrumentService.findAllInstruments(searchDto));
    }

    @Operation(summary = "계측기 단건 조회",
            description = "instrumentId 로 계측기 단건을 조회한다. PUMP 자식은 ratedHead·ratedFlwrt·oprtngType 값을 포함한다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — equipTypeCd discriminator 로 자식 DTO 결정",
                    content = @Content(schema = @Schema(
                            oneOf = {PumpDto.class, ValveDto.class, FlowMeterDto.class,
                                    PressureMeterDto.class, LevelMeterDto.class, PowerMeterDto.class},
                            discriminatorProperty = "equipTypeCd"))),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "계측기 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{instrumentId}")
    public ResponseEntity<CommonResponseDto<InstrumentDto>> findInstrument(@PathVariable String instrumentId) {
        return getResponseEntity(instrumentService.findInstrumentDto(instrumentId));
    }

    @Operation(summary = "계측기 등록",
            description = "equipTypeCd 필드로 자식 종류 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR) 결정. "
                    + "PUMP 는 ratedHead·ratedFlwrt·oprtngType 필수. "
                    + "요청 본문은 Jackson 다형성으로 자식 DTO 로 자동 역직렬화된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (INVALID_FACILITY_ID·검증 실패)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_INSTRUMENT_NM (시설 내 계측기명 중복)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<CommonResponseDto<String>> saveInstrument(
            @Valid @RequestBody InstrumentUpsertDto dto) {
        return getResponseEntity(instrumentService.saveInstrument(dto));
    }

    @Operation(summary = "계측기 수정",
            description = "path 의 instrumentId 와 request body 의 equipTypeCd 가 일치해야 한다. "
                    + "JPA dirty checking 으로 UPDATE 발행. PUMP 는 자체 컬럼 3건 함께 변경.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (EQUIP_TYPE_MISMATCH·INVALID_FACILITY_ID)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "계측기 없음"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_INSTRUMENT_NM (시설 내 계측기명 중복)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{instrumentId}")
    public ResponseEntity<CommonResponseDto<Void>> updateInstrument(
            @PathVariable String instrumentId,
            @Valid @RequestBody InstrumentUpsertDto dto) {
        instrumentService.updateInstrument(instrumentId, dto);
        return getResponseEntity();
    }

    @Operation(summary = "계측기 논리 삭제",
            description = "use_yn = N 으로 전환한다. 물리 삭제하지 않는 이유: tag_m.instrument_id FK + "
                    + "rawdata_1m_h 가 tag_srl_no 를 논리 참조하므로 (참조 보존 정책).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "계측기 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{instrumentId}")
    public ResponseEntity<CommonResponseDto<Void>> deactivateInstrument(@PathVariable String instrumentId) {
        instrumentService.deactivateInstrument(instrumentId);
        return getResponseEntity();
    }
}
