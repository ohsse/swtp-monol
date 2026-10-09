package com.mo.swtp.tag.service;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.dto.TagDto;
import com.mo.swtp.tag.dto.TagUpsertDto;
import com.mo.swtp.tag.exception.TagErrorCode;
import com.mo.swtp.tag.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 마스터 CRUD 서비스 (ADMIN 전용 — Controller 계층에서 권한 강제).
 *
 * <p>{@link TagRepository} 와 {@link InstrumentRepository} 를 협력자로 사용하며,
 * 등록·수정 시 {@code instrument_id} FK 를 명시적으로 검증하여 DB FK 위반(500)을 사전 차단한다
 * (태그관리 ANALYZE1 안건 4 권고 1 정합).</p>
 *
 * <p>모든 단건 조회는 활성·비활성 태그를 모두 반환한다 — ADMIN 이 비활성 태그 메타를 검토하거나
 * 재활성화 결정을 내릴 수 있도록 한다 (도메인 검토 권고 1 정합).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    private final InstrumentRepository instrumentRepository;

    /**
     * 신규 태그를 등록한다. 등록자·수정자는 JPA Auditing 이 자동 주입하며,
     * 초기 {@code useYn} 은 {@link Tag#create} 정적 팩토리에서 {@code Y} 로 명시 할당된다.
     *
     * @param dto 등록 요청 DTO
     * @throws RestApiException DUPLICATE_TAG_SRL_NO — 동일 시리얼번호가 이미 존재하는 경우
     * @throws RestApiException INVALID_INSTRUMENT_ID — 지정한 instrument_id 가 instrument_m 에 미존재
     * @throws RestApiException FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP — FQI 측정 유형을 INVERTER_DRIVE 외 계측기에 등록 시도
     */
    @Transactional
    public void registerTag(TagUpsertDto dto) {
        if (tagRepository.existsById(dto.getTagSrlNo())) {
            throw new RestApiException(TagErrorCode.DUPLICATE_TAG_SRL_NO);
        }
        Instrument instrument = instrumentRepository.findById(dto.getInstrumentId())
                .orElseThrow(() -> new RestApiException(TagErrorCode.INVALID_INSTRUMENT_ID));
        validateFqiTagAllowance(instrument, dto.getTagSeCd());
        Tag tag = Tag.create(
                dto.getTagSrlNo(),
                instrument,
                dto.getTagSeCd(),
                dto.getTagDesc(),
                dto.getIoCd());
        tagRepository.save(tag);
    }

    /**
     * 전체 태그 목록을 활성·비활성 모두 포함하여 정렬 조회한다.
     *
     * <p>정렬은 {@code use_yn} 내림차순(활성 우선) 후 {@code tag_srl_no} 오름차순.
     * ADMIN 전용 태그 관리 화면에서 호출한다.</p>
     *
     * @return 정렬된 태그 응답 DTO 목록
     */
    public List<TagDto> findAllTags() {
        return tagRepository.findAllByOrderByUseYnDescTagSrlNoAsc().stream()
                .map(TagDto::from)
                .toList();
    }

    /**
     * 태그 단건을 조회한다. 활성·비활성 모두 반환한다.
     *
     * <p>비활성 태그도 반환하는 이유: ADMIN 이 비활성 태그 메타를 검토하거나 재활성화 결정을 내릴 수 있도록
     * 한다 (태그관리 PLAN1 §가정 결정 + 도메인 검토 권고 1 반영). use_yn 필터를 적용하지 않는다.</p>
     *
     * @param tagSrlNo 태그 시리얼번호
     * @return 태그 응답 DTO
     * @throws RestApiException TAG_NOT_FOUND — 존재하지 않는 시리얼번호
     */
    public TagDto findTag(String tagSrlNo) {
        Tag tag = tagRepository.findById(tagSrlNo)
                .orElseThrow(() -> new RestApiException(TagErrorCode.TAG_NOT_FOUND));
        return TagDto.from(tag);
    }

    /**
     * 태그 메타정보를 수정한다. {@code instrument_id} 변경 요청은 무시한다 — 계측기 교체는
     * {@link #deactivateTag(String)} + {@link #registerTag(TagUpsertDto)} 플로우로 처리한다
     * (태그관리 ANALYZE1 안건 4 블로커, {@code .claude/rules/ot-integration.md} §5 이력 기록 의무 정합).
     *
     * @param tagSrlNo 수정 대상 태그 시리얼번호
     * @param dto      수정 요청 DTO (tagSeCd · tagDesc · ioCd 만 적용)
     * @throws RestApiException TAG_NOT_FOUND — 존재하지 않는 시리얼번호
     * @throws RestApiException FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP — FQI 측정 유형으로 변경 시 INVERTER_DRIVE 외 계측기 보유 태그
     */
    @Transactional
    public void modifyTag(String tagSrlNo, TagUpsertDto dto) {
        Tag tag = tagRepository.findById(tagSrlNo)
                .orElseThrow(() -> new RestApiException(TagErrorCode.TAG_NOT_FOUND));
        validateFqiTagAllowance(tag.getInstrument(), dto.getTagSeCd());
        tag.changeInfo(dto.getTagSeCd(), dto.getTagDesc(), dto.getIoCd());
    }

    /**
     * 태그를 논리 삭제한다 ({@code use_yn = N}).
     *
     * <p>물리 삭제 대신 논리 삭제를 사용하는 이유: {@code rawdata_1m_h.tag_srl_no} 가 시계열 데이터의
     * 논리 참조(시계열 → 마스터 FK 금지 정책 정합)이므로, 물리 삭제 시 시계열 데이터 고아 위험이 있다.</p>
     *
     * @param tagSrlNo 삭제 대상 태그 시리얼번호
     * @throws RestApiException TAG_NOT_FOUND — 존재하지 않는 시리얼번호
     */
    @Transactional
    public void deactivateTag(String tagSrlNo) {
        Tag tag = tagRepository.findById(tagSrlNo)
                .orElseThrow(() -> new RestApiException(TagErrorCode.TAG_NOT_FOUND));
        tag.deactivate();
    }

    /**
     * FQI 측정 유형 등록·변경 시 계측기 종류·구동 방식 적합성을 검증한다.
     *
     * <p>허용 조건: {@code instrument instanceof Pump && pump.driveType == INVERTER_DRIVE}.
     * 그 외 모든 조합 ({@code RATED_DRIVE} 펌프, 비-Pump 계측기) 은
     * {@link TagErrorCode#FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP} 로 차단한다
     * ({@code ot-integration.md §5 ⚠️ 절대 금지} 직결 — 정격 펌프 가변속 명령 송신 불능).</p>
     *
     * <p>FQI 외 측정 유형 ({@code tagSeCd != FQI}) 은 항상 통과 — 본 검증은 FQI 단독 적용.
     * 도입: tag_frequency_추가 ANALYZE1 안건 4 + PLAN1 §구현 방향 Phase 3 (2026-05-20).</p>
     *
     * @param instrument 소속 계측기 (NOT NULL — 호출자가 보장)
     * @param tagSeCd    태그 측정 유형 코드
     * @throws RestApiException FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP — 위 허용 조건 미충족 시
     */
    private static void validateFqiTagAllowance(Instrument instrument, TagMeasurementType tagSeCd) {
        if (tagSeCd != TagMeasurementType.FQI) {
            return;
        }
        if (instrument instanceof Pump pump && pump.getDriveType() == PumpDriveType.INVERTER_DRIVE) {
            return;
        }
        throw new RestApiException(TagErrorCode.FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP);
    }
}
