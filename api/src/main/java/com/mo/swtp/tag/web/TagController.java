package com.mo.swtp.tag.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.auth.guard.RoleGuard;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.tag.dto.TagDto;
import com.mo.swtp.tag.dto.TagUpsertDto;
import com.mo.swtp.tag.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 태그 마스터 관리 API 컨트롤러.
 *
 * <p>SCADA 태그 ({@code tag_m}) 의 CRUD 5개 엔드포인트를 제공한다. 모든 엔드포인트는 ADMIN
 * 전용으로, 첫 줄에서 {@link RoleGuard#requireAdmin(HttpServletRequest)} 호출로 권한을 강제한다
 * (사용자 결정 — GET 도 ADMIN 전용 적용).</p>
 *
 * <p>HMI 대시보드의 일반 USER 가 태그 메타에 의존하는 운영 시나리오는 측정값 API DTO 임베드
 * (Option A) 또는 단건 분리 (Option B) 로 별도 사이클에서 보강 가능하다 (PLAN1 §제외 사항).</p>
 */
@Tag(name = "08. 태그 관리")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController extends CommonController {

    private final TagService tagService;
    private final RoleGuard roleGuard;

    @Operation(summary = "태그 등록 (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 instrument_id 무효"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "중복 태그 시리얼번호"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<CommonResponseDto<Void>> registerTag(
            @Valid @RequestBody TagUpsertDto dto,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        tagService.registerTag(dto);
        return getResponseEntity();
    }

    @Operation(summary = "태그 전체 목록 조회 (ADMIN 전용)",
               description = "활성·비활성 태그를 모두 반환한다. 정렬은 활성 우선(useYn DESC) 후 시리얼번호 오름차순.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<List<TagDto>>> findAllTags(HttpServletRequest request) {
        roleGuard.requireAdmin(request);
        return getResponseEntity(tagService.findAllTags());
    }

    @Operation(summary = "태그 단건 조회 (ADMIN 전용)",
               description = "활성·비활성 태그를 모두 반환한다. ADMIN 이 비활성 태그 메타 검토·재활성화 결정 시 사용.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "태그 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{tagSrlNo}")
    public ResponseEntity<CommonResponseDto<TagDto>> findTag(
            @PathVariable String tagSrlNo,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        return getResponseEntity(tagService.findTag(tagSrlNo));
    }

    @Operation(summary = "태그 정보 수정 (ADMIN 전용)",
               description = "tagSrlNo · instrumentId 는 변경되지 않으며 서버 계층에서 무시된다. 계측기 교체는 deactivate + 신규 등록 플로우 사용.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "태그 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{tagSrlNo}")
    public ResponseEntity<CommonResponseDto<Void>> modifyTag(
            @PathVariable String tagSrlNo,
            @Valid @RequestBody TagUpsertDto dto,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        tagService.modifyTag(tagSrlNo, dto);
        return getResponseEntity();
    }

    @Operation(summary = "태그 논리 삭제 (ADMIN 전용)",
               description = "use_yn = N 으로 전환한다. rawdata_1m_h.tag_srl_no 논리 참조 보존을 위해 물리 삭제 미사용.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "태그 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{tagSrlNo}")
    public ResponseEntity<CommonResponseDto<Void>> deactivateTag(
            @PathVariable String tagSrlNo,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        tagService.deactivateTag(tagSrlNo);
        return getResponseEntity();
    }
}
