package com.mo.swtp.tag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.dto.TagUpsertDto;
import com.mo.swtp.tag.exception.TagErrorCode;
import com.mo.swtp.tag.repository.TagRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TagService} 단위 테스트 (Mockito 격리).
 *
 * <p>{@code .claude/rules/test-strategy.md} §1 단위 테스트 패턴 준수 —
 * {@code @ExtendWith(MockitoExtension.class)} + {@code @Mock} / {@code @InjectMocks} 격리,
 * {@code given(...).willReturn(...)} BDD 문체 + {@code AssertJ} 단언.</p>
 *
 * <p>{@link Instrument} 는 abstract class 이므로 mock 으로 처리하며, {@code getInstrumentId()} 호출 시
 * 테스트 식별자를 반환하도록 stub 한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private TagService tagService;

    private TagUpsertDto buildDto(String tagSrlNo, String instrumentId, TagMeasurementType tagSeCd, IoCode ioCd) {
        TagUpsertDto dto = new TagUpsertDto();
        dto.setTagSrlNo(tagSrlNo);
        dto.setInstrumentId(instrumentId);
        dto.setTagSeCd(tagSeCd);
        dto.setTagDesc("테스트 태그");
        dto.setIoCd(ioCd);
        return dto;
    }

    @Test
    void 정상_등록_시_save_가_호출되고_useYn_Y_로_생성된다() {
        TagUpsertDto dto = buildDto("706-FRI-001-001", "instrument-1", TagMeasurementType.FRI, IoCode.INPUT);
        Instrument instrument = mock(Instrument.class);

        given(tagRepository.existsById("706-FRI-001-001")).willReturn(false);
        given(instrumentRepository.findById("instrument-1")).willReturn(Optional.of(instrument));

        tagService.registerTag(dto);

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        then(tagRepository).should().save(captor.capture());
        Tag saved = captor.getValue();
        assertThat(saved.getTagSrlNo()).isEqualTo("706-FRI-001-001");
        assertThat(saved.getTagSeCd()).isEqualTo(TagMeasurementType.FRI);
        assertThat(saved.getIoCd()).isEqualTo(IoCode.INPUT);
        assertThat(saved.getUseYn()).isEqualTo(YnType.Y);
    }

    @Test
    void 중복_등록_시_DUPLICATE_TAG_SRL_NO_예외가_발생한다() {
        TagUpsertDto dto = buildDto("706-FRI-001-001", "instrument-1", TagMeasurementType.FRI, IoCode.INPUT);

        given(tagRepository.existsById("706-FRI-001-001")).willReturn(true);

        assertThatThrownBy(() -> tagService.registerTag(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.DUPLICATE_TAG_SRL_NO);

        then(tagRepository).should(never()).save(org.mockito.ArgumentMatchers.any(Tag.class));
    }

    @Test
    void instrument_미존재_시_INVALID_INSTRUMENT_ID_예외가_발생한다() {
        TagUpsertDto dto = buildDto("706-FRI-001-001", "instrument-missing", TagMeasurementType.FRI, IoCode.INPUT);

        given(tagRepository.existsById("706-FRI-001-001")).willReturn(false);
        given(instrumentRepository.findById("instrument-missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.registerTag(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.INVALID_INSTRUMENT_ID);

        then(tagRepository).should(never()).save(org.mockito.ArgumentMatchers.any(Tag.class));
    }

    @Test
    void 미존재_단건_조회_시_TAG_NOT_FOUND_예외가_발생한다() {
        given(tagRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.findTag("unknown"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.TAG_NOT_FOUND);
    }

    @Test
    void 수정_시_changeInfo_가_호출되어_필드가_갱신된다() {
        Instrument instrument = mock(Instrument.class);
        Tag existing = Tag.create("706-FRI-001-001", instrument, TagMeasurementType.FRI, "이전설명", IoCode.INPUT);

        given(tagRepository.findById("706-FRI-001-001")).willReturn(Optional.of(existing));

        TagUpsertDto dto = buildDto("706-FRI-001-001", "instrument-1", TagMeasurementType.PRI, IoCode.OUTPUT);
        dto.setTagDesc("새설명");
        tagService.modifyTag("706-FRI-001-001", dto);

        // changeInfo 가 호출되어 필드 갱신 (instrument 는 안건 4 — 변경 금지)
        assertThat(existing.getTagSeCd()).isEqualTo(TagMeasurementType.PRI);
        assertThat(existing.getTagDesc()).isEqualTo("새설명");
        assertThat(existing.getIoCd()).isEqualTo(IoCode.OUTPUT);
    }

    @Test
    void 논리_삭제_시_useYn_이_N_으로_변경된다() {
        Instrument instrument = mock(Instrument.class);
        Tag existing = Tag.create("706-FRI-001-001", instrument, TagMeasurementType.FRI, "설명", IoCode.INPUT);
        assertThat(existing.getUseYn()).isEqualTo(YnType.Y);

        given(tagRepository.findById("706-FRI-001-001")).willReturn(Optional.of(existing));

        tagService.deactivateTag("706-FRI-001-001");

        assertThat(existing.getUseYn()).isEqualTo(YnType.N);
    }

    @Test
    void 미존재_수정_시_TAG_NOT_FOUND_예외가_발생한다() {
        TagUpsertDto dto = buildDto("unknown", "instrument-1", TagMeasurementType.FRI, IoCode.INPUT);
        given(tagRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.modifyTag("unknown", dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.TAG_NOT_FOUND);
    }

    @Test
    void 미존재_삭제_시_TAG_NOT_FOUND_예외가_발생한다() {
        given(tagRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.deactivateTag("unknown"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.TAG_NOT_FOUND);
    }

    // ===== FQI 측정 유형 검증 시나리오 (tag_frequency_추가 ANALYZE1 안건 4, 2026-05-20) =====

    @Test
    void RATED_DRIVE_펌프에_FQI_태그_등록_시_예외가_발생한다() {
        TagUpsertDto dto = buildDto("706-FQI-001-001", "instrument-pump", TagMeasurementType.FQI, IoCode.INPUT);
        Pump pump = mock(Pump.class);
        given(pump.getDriveType()).willReturn(PumpDriveType.RATED_DRIVE);

        given(tagRepository.existsById("706-FQI-001-001")).willReturn(false);
        given(instrumentRepository.findById("instrument-pump")).willReturn(Optional.of(pump));

        assertThatThrownBy(() -> tagService.registerTag(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP);

        then(tagRepository).should(never()).save(org.mockito.ArgumentMatchers.any(Tag.class));
    }

    @Test
    void 비_Pump_계측기에_FQI_태그_등록_시_예외가_발생한다() {
        TagUpsertDto dto = buildDto("706-FQI-001-002", "instrument-valve", TagMeasurementType.FQI, IoCode.INPUT);
        Instrument instrument = mock(Instrument.class);

        given(tagRepository.existsById("706-FQI-001-002")).willReturn(false);
        given(instrumentRepository.findById("instrument-valve")).willReturn(Optional.of(instrument));

        assertThatThrownBy(() -> tagService.registerTag(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP);

        then(tagRepository).should(never()).save(org.mockito.ArgumentMatchers.any(Tag.class));
    }

    @Test
    void INVERTER_DRIVE_펌프에_FQI_태그_등록은_허용된다() {
        TagUpsertDto dto = buildDto("706-FQI-001-003", "instrument-inverter", TagMeasurementType.FQI, IoCode.INPUT);
        Pump pump = mock(Pump.class);
        given(pump.getDriveType()).willReturn(PumpDriveType.INVERTER_DRIVE);

        given(tagRepository.existsById("706-FQI-001-003")).willReturn(false);
        given(instrumentRepository.findById("instrument-inverter")).willReturn(Optional.of(pump));

        tagService.registerTag(dto);

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        then(tagRepository).should().save(captor.capture());
        assertThat(captor.getValue().getTagSeCd()).isEqualTo(TagMeasurementType.FQI);
    }

    @Test
    void FQI_외_측정유형은_RATED_DRIVE_펌프에도_허용된다() {
        // RATED_DRIVE 펌프라도 FQI 가 아닌 측정 유형은 차단되지 않음 (회귀 방지)
        TagUpsertDto dto = buildDto("706-PWI-001-001", "instrument-rated-pump", TagMeasurementType.PWI, IoCode.INPUT);
        Pump pump = mock(Pump.class);

        given(tagRepository.existsById("706-PWI-001-001")).willReturn(false);
        given(instrumentRepository.findById("instrument-rated-pump")).willReturn(Optional.of(pump));

        tagService.registerTag(dto);

        then(tagRepository).should().save(org.mockito.ArgumentMatchers.any(Tag.class));
    }

    @Test
    void modifyTag_가_FQI_변경_시_RATED_DRIVE_펌프면_차단한다() {
        Pump pump = mock(Pump.class);
        given(pump.getDriveType()).willReturn(PumpDriveType.RATED_DRIVE);
        Tag existing = Tag.create("706-FRI-001-001", pump, TagMeasurementType.FRI, "이전설명", IoCode.INPUT);

        given(tagRepository.findById("706-FRI-001-001")).willReturn(Optional.of(existing));

        TagUpsertDto dto = buildDto("706-FRI-001-001", "instrument-1", TagMeasurementType.FQI, IoCode.INPUT);

        assertThatThrownBy(() -> tagService.modifyTag("706-FRI-001-001", dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(TagErrorCode.FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP);

        // 차단되어 changeInfo 가 호출되지 않음 — 기존 필드 유지
        assertThat(existing.getTagSeCd()).isEqualTo(TagMeasurementType.FRI);
        assertThat(existing.getTagDesc()).isEqualTo("이전설명");
    }
}
