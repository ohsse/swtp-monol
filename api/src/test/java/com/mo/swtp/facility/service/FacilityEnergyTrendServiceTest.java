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
import com.mo.swtp.facility.dto.FacilityEnergyTrendDto;
import com.mo.swtp.facility.dto.FacilityEnergyTrendSearchDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
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
 * {@link FacilityEnergyTrendService} 단위 테스트 — 시설별 사용량 5번섹션.
 *
 * <p>롤업({@link FacilityOperatingRollupResolver}) 과 native 버킷 차분(repository) 은 mock 으로 격리하고, Service 의
 * 오케스트레이션(태그→루트 라우팅·버킷 보존 합산·baseDtm 정렬·음수 차분 제외·빈 points·OPERATION 필터·검증) 을
 * 검증한다. PWQ MAX-MIN 차분·GOOD 품질 필터·파티션 프루닝은 native SQL 영역으로
 * {@code RawDataCustomRepositoryImplIntegrationTest} 가 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityEnergyTrendServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);
    private static final LocalDateTime B10 = LocalDateTime.of(2026, 1, 10, 10, 0);
    private static final LocalDateTime B11 = LocalDateTime.of(2026, 1, 10, 11, 0);
    private static final LocalDateTime B12 = LocalDateTime.of(2026, 1, 10, 12, 0);

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
    private FacilityEnergyTrendService facilityEnergyTrendService;

    @Test
    void 운영시설별_버킷_차분이_버킷별로_합산되어_시계열로_반환() {
        Facility f1 = mockOperatingFacility("F1", "1단계 송수동");
        Facility f2 = mockOperatingFacility("F2", "약품동");
        Instrument i1 = mockInstrument("I1", "F1");
        Instrument i2 = mockInstrument("I2", "F2");
        Tag pwq1 = mockTag("PWQ1", i1, TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("PWQ2", i2, TagMeasurementType.PWQ);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1, f2));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1", "F2", "F2"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ1", B10, new BigDecimal("100.0")),
                new RawDataBucketDto("PWQ1", B11, new BigDecimal("30.0")),
                new RawDataBucketDto("PWQ2", B10, new BigDecimal("80.0"))));

        List<FacilityEnergyTrendDto> result =
                facilityEnergyTrendService.findEnergyTrend(search(InqUnit.HOUR, FROM, TO));

        assertThat(result).hasSize(2);
        FacilityEnergyTrendDto dto1 = result.get(0);
        assertThat(dto1.getFacilityId()).isEqualTo("F1");
        assertThat(dto1.getFacilityNm()).isEqualTo("1단계 송수동");
        assertThat(dto1.getPoints()).hasSize(2);
        assertThat(dto1.getPoints().get(0).getBaseDtm()).isEqualTo(B10);
        assertThat(dto1.getPoints().get(0).getElcegVal()).isEqualByComparingTo("100.0");
        assertThat(dto1.getPoints().get(1).getBaseDtm()).isEqualTo(B11);
        assertThat(dto1.getPoints().get(1).getElcegVal()).isEqualByComparingTo("30.0");
        FacilityEnergyTrendDto dto2 = result.get(1);
        assertThat(dto2.getFacilityId()).isEqualTo("F2");
        assertThat(dto2.getPoints()).hasSize(1);
        assertThat(dto2.getPoints().get(0).getElcegVal()).isEqualByComparingTo("80.0");
    }

    @Test
    void 동일_버킷의_여러_태그는_합산되고_baseDtm_오름차순_정렬() {
        Facility f1 = mockOperatingFacility("F1", "송수동");
        Instrument i1 = mockInstrument("I1", "F1");
        Tag pwq1 = mockTag("PWQ1", i1, TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("PWQ2", i1, TagMeasurementType.PWQ);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(i1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        // 입력 버킷 순서를 일부러 역순·교차로 — Service 가 baseDtm 오름차순 정렬, 동일 버킷 태그 합산
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ1", B11, new BigDecimal("30.0")),
                new RawDataBucketDto("PWQ2", B10, new BigDecimal("50.0")),
                new RawDataBucketDto("PWQ1", B10, new BigDecimal("100.0"))));

        List<FacilityEnergyTrendDto> result =
                facilityEnergyTrendService.findEnergyTrend(search(InqUnit.HOUR, FROM, TO));

        List<FacilityEnergyTrendDto.EnergyTrendPoint> points = result.get(0).getPoints();
        assertThat(points).extracting(FacilityEnergyTrendDto.EnergyTrendPoint::getBaseDtm)
                .containsExactly(B10, B11);   // 오름차순
        assertThat(points.get(0).getElcegVal()).isEqualByComparingTo("150.0");   // 100 + 50
        assertThat(points.get(1).getElcegVal()).isEqualByComparingTo("30.0");
    }

    @Test
    void 음수_차분_버킷은_시계열에서_제외된다() {
        Facility f1 = mockOperatingFacility("F1", "송수동");
        Instrument i1 = mockInstrument("I1", "F1");
        Tag pwq1 = mockTag("PWQ1", i1, TagMeasurementType.PWQ);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(i1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ1", B10, new BigDecimal("100.0")),
                new RawDataBucketDto("PWQ1", B11, new BigDecimal("-5.0")),
                new RawDataBucketDto("PWQ1", B12, new BigDecimal("50.0"))));

        List<FacilityEnergyTrendDto> result =
                facilityEnergyTrendService.findEnergyTrend(search(InqUnit.DAY, FROM, TO));

        // 음수(-5.0) 버킷 제외 → 2개 포인트만 (B11 누락)
        List<FacilityEnergyTrendDto.EnergyTrendPoint> points = result.get(0).getPoints();
        assertThat(points).extracting(FacilityEnergyTrendDto.EnergyTrendPoint::getBaseDtm)
                .containsExactly(B10, B12);
        assertThat(points.get(0).getElcegVal()).isEqualByComparingTo("100.0");
        assertThat(points.get(1).getElcegVal()).isEqualByComparingTo("50.0");
    }

    @Test
    void 측정_0건_시설도_빈_points_로_응답에_포함된다() {
        Facility f1 = mockOperatingFacility("F1", "송수동");
        Facility f2 = mockOperatingFacility("F2", "약품동");   // 데이터 없음
        Instrument i1 = mockInstrument("I1", "F1");
        Instrument i2 = mockInstrument("I2", "F2");
        Tag pwq1 = mockTag("PWQ1", i1, TagMeasurementType.PWQ);
        Tag pwq2 = mockTag("PWQ2", i2, TagMeasurementType.PWQ);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1, f2));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1", "F2", "F2"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(i1, i2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwq1, pwq2));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ1", B10, new BigDecimal("100.0"))));   // F2(PWQ2) 버킷 없음

        List<FacilityEnergyTrendDto> result =
                facilityEnergyTrendService.findEnergyTrend(search(InqUnit.HOUR, FROM, TO));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPoints()).hasSize(1);
        FacilityEnergyTrendDto dto2 = result.get(1);
        assertThat(dto2.getFacilityId()).isEqualTo("F2");
        assertThat(dto2.getPoints()).isEmpty();   // 측정 0건 → 빈 리스트, 응답에 포함
    }

    @Test
    void 하위시설_계측기는_운영루트로_귀속되어_버킷_합산된다() {
        // F1(운영) → C1(하위). C1 의 계측기 PWQ 가 F1 루트로 매핑되어 같은 버킷에 합산되는지 검증
        Facility f1 = mockOperatingFacility("F1", "송수동");
        Instrument iF1 = mockInstrument("I-F1", "F1");
        Instrument iC1 = mockInstrument("I-C1", "C1");   // 하위 시설 C1 소속
        Tag pwqF1 = mockTag("PWQ-F1", iF1, TagMeasurementType.PWQ);
        Tag pwqC1 = mockTag("PWQ-C1", iC1, TagMeasurementType.PWQ);

        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of(f1));
        given(rollupResolver.resolveRootByFacility(anyList())).willReturn(Map.of("F1", "F1", "C1", "F1"));
        given(instrumentRepository.findByFacilityFacilityIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(iF1, iC1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(pwqF1, pwqC1));
        given(rawDataRepository.findEnergyDeltaBuckets(anyList(), any(), any(), any())).willReturn(List.of(
                new RawDataBucketDto("PWQ-F1", B10, new BigDecimal("100.0")),
                new RawDataBucketDto("PWQ-C1", B10, new BigDecimal("40.0"))));

        List<FacilityEnergyTrendDto> result =
                facilityEnergyTrendService.findEnergyTrend(search(InqUnit.HOUR, FROM, TO));

        // 두 PWQ 태그가 모두 native 입력에 전달되고, 같은 버킷·같은 루트(F1)로 합산
        ArgumentCaptor<List<String>> tagsCaptor = captor();
        Mockito.verify(rawDataRepository)
                .findEnergyDeltaBuckets(tagsCaptor.capture(), any(), any(), any());
        assertThat(tagsCaptor.getValue()).containsExactlyInAnyOrder("PWQ-F1", "PWQ-C1");
        List<FacilityEnergyTrendDto.EnergyTrendPoint> points = result.get(0).getPoints();
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getElcegVal()).isEqualByComparingTo("140.0");   // 100 + 40
    }

    @Test
    void 조회는_OPERATION_8종으로_필터된다() {
        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of());

        facilityEnergyTrendService.findEnergyTrend(search(InqUnit.HOUR, FROM, TO));

        ArgumentCaptor<List<FacilityType>> typesCaptor = captor();
        Mockito.verify(facilityRepository)
                .findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(typesCaptor.capture(), eq(YnType.Y));
        assertThat(typesCaptor.getValue()).containsExactlyInAnyOrder(
                FacilityType.PRSF, FacilityType.WTBLD, FacilityType.CHMB, FacilityType.ACFB,
                FacilityType.POZB, FacilityType.FLTB, FacilityType.DEWB, FacilityType.SOLAR);
    }

    @Test
    void 운영시설_0건이면_빈_목록_반환_후속_조회_미호출() {
        given(facilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(anyList(), any()))
                .willReturn(List.of());

        List<FacilityEnergyTrendDto> result =
                facilityEnergyTrendService.findEnergyTrend(search(InqUnit.HOUR, FROM, TO));

        assertThat(result).isEmpty();
        Mockito.verifyNoInteractions(rollupResolver, instrumentRepository, tagRepository, rawDataRepository);
    }

    @Test
    void YEAR_집계단위는_INVALID_SEARCH_PERIOD_거부() {
        assertThatThrownBy(() ->
                facilityEnergyTrendService.findEnergyTrend(search(InqUnit.YEAR, FROM, TO)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.INVALID_SEARCH_PERIOD);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private FacilityEnergyTrendSearchDto search(InqUnit unit, LocalDate from, LocalDate to) {
        FacilityEnergyTrendSearchDto dto = new FacilityEnergyTrendSearchDto();
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
