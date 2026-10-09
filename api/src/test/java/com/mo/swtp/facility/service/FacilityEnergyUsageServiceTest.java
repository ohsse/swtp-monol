package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.InqUnit;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityEnergyUsageDto;
import com.mo.swtp.facility.dto.FacilityEnergyUsageSearchDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.dto.RawDataFacilitySumDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityEnergyUsageService} 단위 테스트 — 시설별 사용량 2번섹션.
 *
 * <p>롤업({@link FacilityOperatingRollupResolver}) 과 native 집계(repository) 는 mock 으로 격리하고, Service 의
 * 오케스트레이션(태그→루트 라우팅·DTO 조립·null 처리·음수 차분 제외·OPERATION 필터·정렬·검증) 을 검증한다.
 * 분(分)별 시설합 SUM({@code SUM(MAX)≠MAX(SUM)})·동률 argmax·unnest 바인드·파티션 프루닝은 native SQL 영역으로
 * {@code RawDataCustomRepositoryImplIntegrationTest} 가 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityEnergyUsageServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);
    private static final LocalDateTime LAST_DTM = LocalDateTime.of(2026, 1, 31, 23, 59, 0);
    private static final LocalDateTime PEAK_DTM = LocalDateTime.of(2026, 1, 15, 14, 0, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityOperatingRollupResolver rollupResolver;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private FacilityEnergyUsageService facilityEnergyUsageService;

    @Test
    void 운영시설별_4지표_정상_매핑() {
        Facility f1 = mockOperatingFacility("F1", "1단계 송수동");
        Facility f2 = mockOperatingFacility("F2", "약품동");
        Instrument i1 = mockInstrument("I1", "F1");
        Instrument i2 = mockInstrument("I2", "F2");
        Tag pwi1 = mockTag("PWI1", i1, TagMeasurementType.PWI);
        Tag pwq1 = mockTag("PWQ1", i1, TagMeasurementType.PWQ);
        Tag pwi2 = mockTag("PWI2", i2, TagMeasurementType.PWI);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1, f2));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1", "F2", "F2"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwi1, pwq1, pwi2));
        given(rawDataRepository.findFacilityLatestMinuteSumElpwr(anyList(), anyList(), any(), any()))
                .willReturn(List.of(
                        new RawDataFacilitySumDto("F1", LAST_DTM, new BigDecimal("120.0")),
                        new RawDataFacilitySumDto("F2", LAST_DTM, new BigDecimal("80.0"))));
        given(rawDataRepository.findFacilityBucketPeakElpwr(anyList(), anyList(), any(), any(), any()))
                .willReturn(List.of(
                        new RawDataFacilitySumDto("F1", PEAK_DTM, new BigDecimal("150.0")),
                        new RawDataFacilitySumDto("F2", PEAK_DTM, new BigDecimal("90.0"))));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ1", PEAK_DTM, new BigDecimal("2840.0"))));

        List<FacilityEnergyUsageDto> result =
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.HOUR, FROM, TO));

        assertThat(result).hasSize(2);
        FacilityEnergyUsageDto dto1 = result.get(0);
        assertThat(dto1.getFacilityId()).isEqualTo("F1");
        assertThat(dto1.getFacilityNm()).isEqualTo("1단계 송수동");
        assertThat(dto1.getElpwr()).isEqualByComparingTo("120.0");
        assertThat(dto1.getElceg()).isEqualByComparingTo("2840.0");
        assertThat(dto1.getPeakElpwr()).isEqualByComparingTo("150.0");
        assertThat(dto1.getPeakElpwrDtm()).isEqualTo(PEAK_DTM);
        FacilityEnergyUsageDto dto2 = result.get(1);
        assertThat(dto2.getFacilityId()).isEqualTo("F2");
        assertThat(dto2.getElpwr()).isEqualByComparingTo("80.0");
        assertThat(dto2.getElceg()).isNull();   // F2 는 PWQ 태그 없음
        assertThat(dto2.getPeakElpwr()).isEqualByComparingTo("90.0");
    }

    @Test
    void 데이터_부재_시_4지표_모두_null() {
        Facility f1 = mockOperatingFacility("F1", "1단계 송수동");
        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any())).willReturn(List.of());
        // 계측기 0건 → 태그·집계 모두 미수집 (raw repo 미스텁 → 기본 빈 결과)

        List<FacilityEnergyUsageDto> result =
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.HOUR, FROM, TO));

        assertThat(result).hasSize(1);
        FacilityEnergyUsageDto dto = result.get(0);
        assertThat(dto.getFacilityId()).isEqualTo("F1");
        assertThat(dto.getElpwr()).isNull();
        assertThat(dto.getElceg()).isNull();
        assertThat(dto.getPeakElpwr()).isNull();
        assertThat(dto.getPeakElpwrDtm()).isNull();
    }

    @Test
    void PWI만_또는_PWQ만_보유_시_해당_지표만_산출() {
        Facility f1 = mockOperatingFacility("F1", "송수동");   // PWI 만
        Facility f2 = mockOperatingFacility("F2", "약품동");   // PWQ 만
        Instrument i1 = mockInstrument("I1", "F1");
        Instrument i2 = mockInstrument("I2", "F2");
        Tag pwi1 = mockTag("PWI1", i1, TagMeasurementType.PWI);
        Tag pwq2 = mockTag("PWQ2", i2, TagMeasurementType.PWQ);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1, f2));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1", "F2", "F2"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwi1, pwq2));
        given(rawDataRepository.findFacilityLatestMinuteSumElpwr(anyList(), anyList(), any(), any()))
                .willReturn(List.of(new RawDataFacilitySumDto("F1", LAST_DTM, new BigDecimal("120.0"))));
        given(rawDataRepository.findFacilityBucketPeakElpwr(anyList(), anyList(), any(), any(), any()))
                .willReturn(List.of(new RawDataFacilitySumDto("F1", PEAK_DTM, new BigDecimal("150.0"))));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ2", PEAK_DTM, new BigDecimal("500.0"))));

        List<FacilityEnergyUsageDto> result =
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.HOUR, FROM, TO));

        FacilityEnergyUsageDto dto1 = result.get(0);   // PWI 만 → elpwr·peak 있고 elceg null
        assertThat(dto1.getElpwr()).isEqualByComparingTo("120.0");
        assertThat(dto1.getPeakElpwr()).isEqualByComparingTo("150.0");
        assertThat(dto1.getElceg()).isNull();
        FacilityEnergyUsageDto dto2 = result.get(1);   // PWQ 만 → elceg 있고 elpwr·peak null
        assertThat(dto2.getElpwr()).isNull();
        assertThat(dto2.getPeakElpwr()).isNull();
        assertThat(dto2.getPeakElpwrDtm()).isNull();
        assertThat(dto2.getElceg()).isEqualByComparingTo("500.0");
    }

    @Test
    void 하위시설_계측기는_운영시설_루트로_귀속되어_합산된다() {
        // F1(운영) → C1(하위). C1 의 계측기 PWI 가 F1 루트로 매핑되는지 (pwiRoots 캡처) 검증
        Facility f1 = mockOperatingFacility("F1", "송수동");
        Instrument iF1 = mockInstrument("I-F1", "F1");
        Instrument iC1 = mockInstrument("I-C1", "C1");   // 하위 시설 C1 소속
        Tag pwiF1 = mockTag("PWI-F1", iF1, TagMeasurementType.PWI);
        Tag pwiC1 = mockTag("PWI-C1", iC1, TagMeasurementType.PWI);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1", "C1", "F1"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(iF1, iC1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwiF1, pwiC1));
        given(rawDataRepository.findFacilityLatestMinuteSumElpwr(anyList(), anyList(), any(), any()))
                .willReturn(List.of(new RawDataFacilitySumDto("F1", LAST_DTM, new BigDecimal("200.0"))));

        List<FacilityEnergyUsageDto> result =
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.HOUR, FROM, TO));

        // 두 PWI 태그 모두 운영루트 F1 로 매핑되어 native 입력에 전달
        ArgumentCaptor<List<String>> tagsCaptor = captor();
        ArgumentCaptor<List<String>> rootsCaptor = captor();
        Mockito.verify(rawDataRepository).findFacilityLatestMinuteSumElpwr(
                tagsCaptor.capture(), rootsCaptor.capture(), any(), any());
        assertThat(tagsCaptor.getValue()).containsExactlyInAnyOrder("PWI-F1", "PWI-C1");
        assertThat(rootsCaptor.getValue()).containsExactly("F1", "F1");   // 둘 다 F1 로 귀속
        assertThat(result.get(0).getElpwr()).isEqualByComparingTo("200.0");
    }

    @Test
    void 음수_차분_버킷은_전력량_합산에서_제외된다() {
        Facility f1 = mockOperatingFacility("F1", "송수동");
        Instrument i1 = mockInstrument("I1", "F1");
        Tag pwq1 = mockTag("PWQ1", i1, TagMeasurementType.PWQ);
        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any())).willReturn(List.of(i1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ1", LocalDateTime.of(2026, 1, 10, 0, 0), new BigDecimal("100.0")),
                new RawDataBucketDto("PWQ1", LocalDateTime.of(2026, 1, 11, 0, 0), new BigDecimal("-5.0")),
                new RawDataBucketDto("PWQ1", LocalDateTime.of(2026, 1, 12, 0, 0), new BigDecimal("50.0"))));

        List<FacilityEnergyUsageDto> result =
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.DAY, FROM, TO));

        // 음수(-5.0) 제외 → 100 + 50 = 150
        assertThat(result.get(0).getElceg()).isEqualByComparingTo("150.0");
    }

    @Test
    void 운영시설_0건이면_빈_목록_반환() {
        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of());

        List<FacilityEnergyUsageDto> result =
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.HOUR, FROM, TO));

        assertThat(result).isEmpty();
    }

    @Test
    void 카드_조회는_OPERATION_8종으로_필터된다() {
        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of());

        facilityEnergyUsageService.findEnergyUsage(search(InqUnit.HOUR, FROM, TO));

        ArgumentCaptor<List<FacilityType>> typesCaptor = captor();
        Mockito.verify(facilityRepository)
                .findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(typesCaptor.capture(), eq(YnType.Y));
        assertThat(typesCaptor.getValue()).containsExactlyInAnyOrder(
                FacilityType.PRSF, FacilityType.WTBLD, FacilityType.CHMB, FacilityType.ACFB,
                FacilityType.POZB, FacilityType.FLTB, FacilityType.DEWB, FacilityType.SOLAR);
    }

    @Test
    void 응답은_카드_조회_정렬_순서를_보존한다() {
        Facility f2 = mockOperatingFacility("F2", "약품동");
        Facility f1 = mockOperatingFacility("F1", "송수동");
        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f2, f1));   // 리포지토리 정렬 순서 = F2, F1
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F2", "F2", "F1", "F1"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any())).willReturn(List.of());

        List<FacilityEnergyUsageDto> result =
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.HOUR, FROM, TO));

        assertThat(result).extracting(FacilityEnergyUsageDto::getFacilityId).containsExactly("F2", "F1");
    }

    @Test
    void YEAR_집계단위는_INVALID_SEARCH_PERIOD_거부() {
        assertThatThrownBy(() ->
                facilityEnergyUsageService.findEnergyUsage(search(InqUnit.YEAR, FROM, TO)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void 조회기간_13개월_초과는_INVALID_SEARCH_PERIOD_거부() {
        // 2026-01-01 ~ 2027-03-01 = 424일 > 396일
        assertThatThrownBy(() -> facilityEnergyUsageService.findEnergyUsage(
                search(InqUnit.MONTH, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 3, 1))))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    @Test
    void 시작일이_종료일보다_늦으면_INVALID_SEARCH_PERIOD_거부() {
        assertThatThrownBy(() -> facilityEnergyUsageService.findEnergyUsage(
                search(InqUnit.DAY, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1))))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private FacilityEnergyUsageSearchDto search(InqUnit unit, LocalDate from, LocalDate to) {
        FacilityEnergyUsageSearchDto dto = new FacilityEnergyUsageSearchDto();
        dto.setInqUnit(unit);
        dto.setFromDt(from);
        dto.setToDt(to);
        return dto;
    }

    private Facility mockOperatingFacility(String id, String nm) {
        Facility f = Mockito.mock(Facility.class);
        Mockito.lenient().when(f.getFacilityId()).thenReturn(id);
        Mockito.lenient().when(f.getFacilityNm()).thenReturn(nm);
        return f;
    }

    private Instrument mockInstrument(String instrumentId, String facilityId) {
        Facility f = Mockito.mock(Facility.class);
        Mockito.lenient().when(f.getFacilityId()).thenReturn(facilityId);
        Instrument i = Mockito.mock(Instrument.class);
        Mockito.lenient().when(i.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(i.getFacility()).thenReturn(f);
        return i;
    }

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        return tag;
    }

    @SuppressWarnings("unchecked")
    private <T> ArgumentCaptor<List<T>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
