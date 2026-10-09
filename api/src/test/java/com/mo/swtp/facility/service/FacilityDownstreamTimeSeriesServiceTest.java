package com.mo.swtp.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityDownstreamDataType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.DownstreamPoint;
import com.mo.swtp.facility.dto.FacilityDownstreamLevelDto;
import com.mo.swtp.facility.dto.FacilityDownstreamLevelDto.LevelSeries;
import com.mo.swtp.facility.dto.FacilityDownstreamMeasureDto;
import com.mo.swtp.facility.dto.FacilityDownstreamMeasureDto.MeasureSeries;
import com.mo.swtp.facility.dto.FacilityDownstreamTimeSeriesDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.facility.service.FacilityDownstreamTreeResolver.DownstreamTopology;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcOutflowDto;
import com.mo.swtp.opt.repository.TagPredcOutflowRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataOutflowDto;
import com.mo.swtp.raw.repository.RawDataOutflowRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FacilityDownstreamTimeSeriesService} 단위 테스트 — 운전현황분석 8번 섹션.
 *
 * <p>재귀 하위 도출은 {@link FacilityDownstreamTreeResolver} (별도 테스트) 를 mock 으로 대체하고, 본 테스트는
 * dataType 분기·io_cd 유출 필터·시계열 병합·대비율 경계를 검증한다. PLAN1 §성공 기준 9종 시나리오:</p>
 * <ol>
 *   <li>DEMAND — 표출대상별 유출 FLWMTR 의 FRI 계측+예측+대비율</li>
 *   <li>PRESSURE — 유출 FLWMTR 의 PRI</li>
 *   <li>LEVEL — 자식 DWT 의 수위계당 LEI 시리즈 + parent 표출대상 그룹핑</li>
 *   <li>유입(INPUT)계만 보유 시설 → 유출 시리즈 없음 + 시계열 조회 미발생</li>
 *   <li>다중 유출 FLWMTR → 첫 매치 + multipleOutletFlwmtrDetected=true</li>
 *   <li>대비율 경계 — 계측 0·null·BAD 또는 예측 null → null</li>
 *   <li>루트 미존재·비활성 → 404</li>
 *   <li>표출대상 0건 → 빈 series + 계측기 조회 미발생</li>
 *   <li>유출 FLWMTR 존재하나 측정 태그 부재 → 빈 points</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class FacilityDownstreamTimeSeriesServiceTest {

    private static final LocalDateTime SLOT_A = LocalDate.now().atStartOfDay();
    private static final LocalDateTime SLOT_B = SLOT_A.plusMinutes(1);
    private static final LocalDateTime SLOT_C = SLOT_A.plusMinutes(2);
    private static final LocalDateTime SLOT_D = SLOT_A.plusMinutes(3);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityDownstreamTreeResolver downstreamTreeResolver;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RawDataOutflowRepository rawDataOutflowRepository;

    @Mock
    private TagPredcOutflowRepository tagPredcOutflowRepository;

    @InjectMocks
    private FacilityDownstreamTimeSeriesService facilityDownstreamTimeSeriesService;

    @Test
    void DEMAND_표출대상별_유출_FRI_계측예측_대비율_시리즈() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        Facility p1 = mockActiveFacility("P1", "1정수지", FacilityType.PWTF);
        Facility p2 = mockActiveFacility("P2", "2분기점", FacilityType.POINT);
        Instrument fm1 = mockInstrument("FM1", "송수유량계1", EquipType.FLWMTR, "P1");
        Instrument fm2 = mockInstrument("FM2", "송수유량계2", EquipType.FLWMTR, "P2");
        Tag friFm1 = mockTag("T-FRI-1", fm1, TagMeasurementType.FRI, IoCode.OUTPUT);
        Tag friFm2 = mockTag("T-FRI-2", fm2, TagMeasurementType.FRI, IoCode.BIDIR);

        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any())).willReturn(topology(List.of(p1, p2), Map.of()));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fm1, fm2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friFm1, friFm2));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-FRI-1", new BigDecimal("400.0"), SLOT_A),
                        actual("T-FRI-2", new BigDecimal("200.0"), SLOT_A)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-FRI-1", new BigDecimal("380.0"), SLOT_A),
                        predc("T-FRI-2", new BigDecimal("210.0"), SLOT_A)));

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.DEMAND);

        assertThat(result).isInstanceOf(FacilityDownstreamMeasureDto.class);
        assertThat(result.getDataType()).isEqualTo(FacilityDownstreamDataType.DEMAND);
        assertThat(result.getFacilityId()).isEqualTo("ROOT");
        FacilityDownstreamMeasureDto measure = (FacilityDownstreamMeasureDto) result;
        assertThat(measure.getSeries()).hasSize(2);
        MeasureSeries s1 = findMeasure(measure, "P1");
        assertThat(s1.getFacilityTypeCd()).isEqualTo(FacilityType.PWTF);
        assertThat(s1.getInstrumentId()).isEqualTo("FM1");
        assertThat(s1.isMultipleOutletFlwmtrDetected()).isFalse();
        assertThat(s1.getPoints()).hasSize(1);
        assertThat(s1.getPoints().get(0).getActualVal()).isEqualByComparingTo("400.0");
        assertThat(s1.getPoints().get(0).getPredcVal()).isEqualByComparingTo("380.0");
        assertThat(s1.getPoints().get(0).getRatio()).isEqualByComparingTo("95.0"); // 380*100/400
        // BIDIR 유량계도 유출 후보 — P2 시리즈 정상 구성
        MeasureSeries s2 = findMeasure(measure, "P2");
        assertThat(s2.getFacilityTypeCd()).isEqualTo(FacilityType.POINT);
        assertThat(s2.getPoints().get(0).getRatio()).isEqualByComparingTo("105.0"); // 210*100/200
    }

    @Test
    void PRESSURE_표출대상_유출_PRI_시리즈() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        Facility p1 = mockActiveFacility("P1", "1정수지", FacilityType.PWTF);
        Instrument fm1 = mockInstrument("FM1", "송수유량계1", EquipType.FLWMTR, "P1");
        Tag priFm1 = mockTag("T-PRI-1", fm1, TagMeasurementType.PRI, IoCode.OUTPUT);

        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any())).willReturn(topology(List.of(p1), Map.of()));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fm1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(priFm1));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(actual("T-PRI-1", new BigDecimal("2.0"), SLOT_A)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(predc("T-PRI-1", new BigDecimal("2.5"), SLOT_A)));

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.PRESSURE);

        assertThat(result.getDataType()).isEqualTo(FacilityDownstreamDataType.PRESSURE);
        MeasureSeries s1 = findMeasure((FacilityDownstreamMeasureDto) result, "P1");
        assertThat(s1.getPoints().get(0).getActualVal()).isEqualByComparingTo("2.0");
        assertThat(s1.getPoints().get(0).getPredcVal()).isEqualByComparingTo("2.5");
        assertThat(s1.getPoints().get(0).getRatio()).isEqualByComparingTo("125.0"); // 2.5*100/2.0
    }

    @Test
    void LEVEL_수위계당_시리즈_parent_표출대상_그룹핑() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        Facility p1 = mockActiveFacility("P1", "1정수지", FacilityType.PWTF);
        Facility d1 = mockActiveFacility("D1", "1배수지", FacilityType.DWT);
        Facility d2 = mockActiveFacility("D2", "2배수지", FacilityType.DWT);
        Instrument lv1 = mockInstrument("LV1", "수위계1", EquipType.LVMTR, "D1");
        Instrument lv2 = mockInstrument("LV2", "수위계2", EquipType.LVMTR, "D1"); // D1 에 수위계 2대
        Instrument lv3 = mockInstrument("LV3", "수위계3", EquipType.LVMTR, "D2");
        Tag leiLv1 = mockTag("T-LEI-1", lv1, TagMeasurementType.LEI, IoCode.INPUT);
        Tag leiLv2 = mockTag("T-LEI-2", lv2, TagMeasurementType.LEI, IoCode.INPUT);
        Tag leiLv3 = mockTag("T-LEI-3", lv3, TagMeasurementType.LEI, IoCode.INPUT);

        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any()))
                .willReturn(topology(List.of(p1), Map.of("P1", List.of(d1, d2))));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(lv1, lv2, lv3));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(leiLv1, leiLv2, leiLv3));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-LEI-1", new BigDecimal("3.5"), SLOT_A),
                        actual("T-LEI-2", new BigDecimal("4.0"), SLOT_A),
                        actual("T-LEI-3", new BigDecimal("2.0"), SLOT_A)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(predc("T-LEI-1", new BigDecimal("3.4"), SLOT_A)));

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.LEVEL);

        assertThat(result).isInstanceOf(FacilityDownstreamLevelDto.class);
        assertThat(result.getDataType()).isEqualTo(FacilityDownstreamDataType.LEVEL);
        FacilityDownstreamLevelDto level = (FacilityDownstreamLevelDto) result;
        // 수위계 3대 → 시리즈 3건 (D1 에 2, D2 에 1) — 수위계당 1
        assertThat(level.getSeries()).hasSize(3);
        LevelSeries lvSeries1 = findLevel(level, "LV1");
        assertThat(lvSeries1.getFacilityId()).isEqualTo("D1");
        assertThat(lvSeries1.getFacilityTypeCd()).isEqualTo(FacilityType.DWT);
        assertThat(lvSeries1.getParentFacilityId()).isEqualTo("P1");
        assertThat(lvSeries1.getParentFacilityNm()).isEqualTo("1정수지");
        assertThat(lvSeries1.getPoints().get(0).getActualVal()).isEqualByComparingTo("3.5");
        assertThat(lvSeries1.getPoints().get(0).getRatio()).isEqualByComparingTo("97.1"); // 3.4*100/3.5
        // LV3 은 예측 없음 → 대비율 null
        LevelSeries lvSeries3 = findLevel(level, "LV3");
        assertThat(lvSeries3.getParentFacilityId()).isEqualTo("P1");
        assertThat(lvSeries3.getPoints().get(0).getPredcVal()).isNull();
        assertThat(lvSeries3.getPoints().get(0).getRatio()).isNull();
    }

    @Test
    void 유입계만_보유시_유출_시리즈_없음_시계열조회_미발생() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        Facility p1 = mockActiveFacility("P1", "1정수지", FacilityType.PWTF);
        Instrument fmIn = mockInstrument("FM-IN", "유입유량계", EquipType.FLWMTR, "P1");
        Tag friIn = mockTag("T-FRI-IN", fmIn, TagMeasurementType.FRI, IoCode.INPUT); // 유입

        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any())).willReturn(topology(List.of(p1), Map.of()));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fmIn));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friIn));

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.DEMAND);

        MeasureSeries s1 = findMeasure((FacilityDownstreamMeasureDto) result, "P1");
        // 유입계는 유출 후보(io_cd ∈ {OUTPUT,BIDIR})에서 제외 → instrumentId null, points 빈
        assertThat(s1.getInstrumentId()).isNull();
        assertThat(s1.isMultipleOutletFlwmtrDetected()).isFalse();
        assertThat(s1.getPoints()).isEmpty();
        // 유출 태그가 없으므로 계측·예측 시계열 조회 자체가 발생하지 않음
        then(rawDataOutflowRepository).should(never()).findByTagSrlNosAndDtmRange(anyList(), any(), any());
        then(tagPredcOutflowRepository).should(never()).findByTagSrlNosAndPredcDtmRange(anyList(), any(), any());
    }

    @Test
    void 다중_유출_FLWMTR_첫매치_사용_multipleOutletFlwmtrDetected_true() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        Facility p1 = mockActiveFacility("P1", "1정수지", FacilityType.PWTF);
        Instrument fm1 = mockInstrument("FM1", "송수유량계1", EquipType.FLWMTR, "P1");
        Instrument fm2 = mockInstrument("FM2", "송수유량계2", EquipType.FLWMTR, "P1");
        Tag friFm1 = mockTag("T-FRI-1", fm1, TagMeasurementType.FRI, IoCode.OUTPUT);
        Tag friFm2 = mockTag("T-FRI-2", fm2, TagMeasurementType.FRI, IoCode.OUTPUT);

        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any())).willReturn(topology(List.of(p1), Map.of()));
        // disp_ord ASC 정렬 결과 — fm1 이 첫 매치
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fm1, fm2));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friFm1, friFm2));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(actual("T-FRI-1", new BigDecimal("400.0"), SLOT_A)));
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of());

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.DEMAND);

        MeasureSeries s1 = findMeasure((FacilityDownstreamMeasureDto) result, "P1");
        assertThat(s1.getInstrumentId()).isEqualTo("FM1"); // 첫 매치 고정
        assertThat(s1.isMultipleOutletFlwmtrDetected()).isTrue();
    }

    @Test
    void 대비율_경계_계측0_null_BAD_또는_예측null_이면_null() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        Facility p1 = mockActiveFacility("P1", "1정수지", FacilityType.PWTF);
        Instrument fm1 = mockInstrument("FM1", "송수유량계1", EquipType.FLWMTR, "P1");
        Tag friFm1 = mockTag("T-FRI-1", fm1, TagMeasurementType.FRI, IoCode.OUTPUT);

        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any())).willReturn(topology(List.of(p1), Map.of()));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fm1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(friFm1));
        given(rawDataOutflowRepository.findByTagSrlNosAndDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        actual("T-FRI-1", new BigDecimal("400.0"), SLOT_A),       // 정상
                        actual("T-FRI-1", BigDecimal.ZERO, SLOT_B),               // 계측 0
                        actualQ("T-FRI-1", null, SLOT_C, QualityCode.BAD),        // BAD → 계측 null
                        actual("T-FRI-1", new BigDecimal("200.0"), SLOT_D)));     // 예측 없음
        given(tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(anyList(), any(), any()))
                .willReturn(List.of(
                        predc("T-FRI-1", new BigDecimal("380.0"), SLOT_A),
                        predc("T-FRI-1", new BigDecimal("100.0"), SLOT_B),
                        predc("T-FRI-1", new BigDecimal("100.0"), SLOT_C)));

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.DEMAND);

        MeasureSeries s1 = findMeasure((FacilityDownstreamMeasureDto) result, "P1");
        List<DownstreamPoint> points = s1.getPoints();
        assertThat(points).hasSize(4); // 4 슬롯 모두 한쪽 이상 값 존재 → 생존
        assertThat(pointAt(points, SLOT_A).getRatio()).isEqualByComparingTo("95.0");
        assertThat(pointAt(points, SLOT_B).getActualVal()).isEqualByComparingTo("0"); // 계측 0
        assertThat(pointAt(points, SLOT_B).getRatio()).isNull();                      // 0 나누기 방어
        assertThat(pointAt(points, SLOT_C).getActualVal()).isNull();                  // BAD → null
        assertThat(pointAt(points, SLOT_C).getRatio()).isNull();
        assertThat(pointAt(points, SLOT_D).getPredcVal()).isNull();                   // 예측 없음
        assertThat(pointAt(points, SLOT_D).getRatio()).isNull();
    }

    @Test
    void 루트_미존재_또는_비활성_시설_FACILITY_NOT_FOUND() {
        given(facilityRepository.findById("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("unknown", FacilityDownstreamDataType.DEMAND))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);

        Facility inactive = Mockito.mock(Facility.class);
        Mockito.lenient().when(inactive.getUseYn()).thenReturn(YnType.N);
        given(facilityRepository.findById("inactive")).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("inactive", FacilityDownstreamDataType.LEVEL))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(FacilityErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void 표출대상_0건시_빈_series_계측기조회_미발생() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any())).willReturn(topology(List.of(), Map.of()));

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.DEMAND);

        assertThat(result).isInstanceOf(FacilityDownstreamMeasureDto.class);
        assertThat(((FacilityDownstreamMeasureDto) result).getSeries()).isEmpty();
        then(instrumentRepository).should(never()).findByFacilityIdInAndEquipType(anyList(), anyList());
    }

    @Test
    void 유출_FLWMTR_존재하나_측정태그_부재시_빈_points() {
        Facility root = mockActiveFacility("ROOT", "고령정수장", FacilityType.PRSF);
        Facility p1 = mockActiveFacility("P1", "1정수지", FacilityType.PWTF);
        Instrument fm1 = mockInstrument("FM1", "송수유량계1", EquipType.FLWMTR, "P1");
        // 유출 FLWMTR 이지만 PRI 태그만 보유 → DEMAND(FRI) 요청 시 측정 태그 부재
        Tag priFm1 = mockTag("T-PRI-1", fm1, TagMeasurementType.PRI, IoCode.OUTPUT);

        given(facilityRepository.findById("ROOT")).willReturn(Optional.of(root));
        given(downstreamTreeResolver.resolve(any())).willReturn(topology(List.of(p1), Map.of()));
        given(instrumentRepository.findByFacilityIdInAndEquipType(anyList(), anyList()))
                .willReturn(List.of(fm1));
        given(tagRepository.findByInstrumentInstrumentIdInAndUseYn(anyList(), any()))
                .willReturn(List.of(priFm1));

        FacilityDownstreamTimeSeriesDto result = facilityDownstreamTimeSeriesService
                .findDownstreamTimeSeries("ROOT", FacilityDownstreamDataType.DEMAND);

        MeasureSeries s1 = findMeasure((FacilityDownstreamMeasureDto) result, "P1");
        // 유출 FLWMTR 식별됨 (PRI 태그가 OUTPUT) → instrumentId 채워짐
        assertThat(s1.getInstrumentId()).isEqualTo("FM1");
        // FRI 태그 부재 → points 빈, 시계열 조회 미발생
        assertThat(s1.getPoints()).isEmpty();
        then(rawDataOutflowRepository).should(never()).findByTagSrlNosAndDtmRange(anyList(), any(), any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private DownstreamTopology topology(List<Facility> displayTargets, Map<String, List<Facility>> dwtsByTargetId) {
        return new DownstreamTopology(displayTargets, dwtsByTargetId);
    }

    private MeasureSeries findMeasure(FacilityDownstreamMeasureDto dto, String facilityId) {
        return dto.getSeries().stream()
                .filter(s -> s.getFacilityId().equals(facilityId))
                .findFirst().orElseThrow();
    }

    private LevelSeries findLevel(FacilityDownstreamLevelDto dto, String instrumentId) {
        return dto.getSeries().stream()
                .filter(s -> s.getInstrumentId().equals(instrumentId))
                .findFirst().orElseThrow();
    }

    private DownstreamPoint pointAt(List<DownstreamPoint> points, LocalDateTime dtm) {
        return points.stream().filter(p -> p.getDtm().equals(dtm)).findFirst().orElseThrow();
    }

    private Facility mockActiveFacility(String facilityId, String facilityNm, FacilityType facilityType) {
        Facility facility = Mockito.mock(Facility.class);
        Mockito.lenient().when(facility.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(facility.getFacilityNm()).thenReturn(facilityNm);
        Mockito.lenient().when(facility.getUseYn()).thenReturn(YnType.Y);
        Mockito.lenient().when(facility.getFacilityType()).thenReturn(facilityType);
        return facility;
    }

    private Instrument mockInstrument(String instrumentId, String instrumentNm, EquipType equipType, String facilityId) {
        Instrument instrument = Mockito.mock(Instrument.class);
        Mockito.lenient().when(instrument.getInstrumentId()).thenReturn(instrumentId);
        Mockito.lenient().when(instrument.getInstrumentNm()).thenReturn(instrumentNm);
        Mockito.lenient().when(instrument.getEquipType()).thenReturn(equipType);
        Facility owner = Mockito.mock(Facility.class);
        Mockito.lenient().when(owner.getFacilityId()).thenReturn(facilityId);
        Mockito.lenient().when(instrument.getFacility()).thenReturn(owner);
        return instrument;
    }

    private Tag mockTag(String tagSrlNo, Instrument instrument, TagMeasurementType tagSeCd, IoCode ioCd) {
        Tag tag = Mockito.mock(Tag.class);
        Mockito.lenient().when(tag.getTagSrlNo()).thenReturn(tagSrlNo);
        Mockito.lenient().when(tag.getInstrument()).thenReturn(instrument);
        Mockito.lenient().when(tag.getTagSeCd()).thenReturn(tagSeCd);
        Mockito.lenient().when(tag.getIoCd()).thenReturn(ioCd);
        return tag;
    }

    /** GOOD 계측 행 — corrVal·rawVal 동일값. */
    private RawDataOutflowDto actual(String tagSrlNo, BigDecimal val, LocalDateTime acqDtm) {
        return actualQ(tagSrlNo, val, acqDtm, QualityCode.GOOD);
    }

    /** QUALITY 명시 계측 행 — BAD/UNCERTAIN 경계 검증용. */
    private RawDataOutflowDto actualQ(String tagSrlNo, BigDecimal val, LocalDateTime acqDtm, QualityCode quality) {
        return new RawDataOutflowDto(tagSrlNo, acqDtm, val, val, quality);
    }

    private TagPredcOutflowDto predc(String tagSrlNo, BigDecimal predcVal, LocalDateTime predcDtm) {
        return new TagPredcOutflowDto(tagSrlNo, predcDtm, predcVal);
    }
}
