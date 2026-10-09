package com.mo.swtp.instrument.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.dto.PumpFrequencyTimeSeriesDto;
import com.mo.swtp.instrument.dto.PumpFrequencyTimeSeriesDto.PumpFrequencyTimeSeriesPoint;
import com.mo.swtp.instrument.dto.PumpTimeSeriesSearchDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프 가동이력 3번섹션 — 펌프별 주파수 시계열 조회 서비스.
 *
 * <p><strong>인버터 펌프({@code drive_type_cd = INVERTER_DRIVE})만</strong> 대상으로 조회기간 가변 주파수(Hz)를
 * 펌프별 계열로 구성한다. 정격 펌프({@code RATED_DRIVE})는 주파수가 고정이라 제외한다. 각 인버터 펌프의
 * FQI 태그를 버킷별로 평균({@code AVG(COALESCE(corr_val,raw_val))})하여 시계열 포인트로 매핑한다
 * (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 6).</p>
 *
 * <p>인버터 필터는 활성 PUMP 목록을 {@code (Pump)} 캐스팅 후 {@link Pump#getDriveType()} 인메모리 필터로
 * 적용한다 — 자식 discriminator 필드 파생쿼리 추가를 회피한다 (활성 펌프 수십 대 규모,
 * {@code .claude/rules/coding-discipline.md §2} 단순성 우선). 4단 흐름은
 * {@link PumpPowerTimeSeriesService} 동형이나 음수 가드는 적용하지 않는다 (주파수는 차분값 아님).</p>
 *
 * <p>도메인 룰 인용: {@code .claude/rules/ot-integration.md §3} (FQI Hold Last Value 허용 — {@code corr_val}
 * 우선 NULL 시 {@code raw_val}, VOI 선례 동형) · {@code .claude/rules/entity-patterns.md §JPA JOINED 다형성
 * §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PumpFrequencyTimeSeriesService {

    /** 주파수 응답 단위. */
    private static final String UNIT_HZ = "Hz";

    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 활성 인버터 송수펌프의 조회기간 주파수 시계열을 조회한다.
     *
     * @param search 조회 단위 + from~to 검색 조건
     * @return 인버터 펌프별 주파수 시계열 목록 (표시 순서 → 계측기명 정렬). 인버터 펌프 0대 시 빈 목록
     * @throws RestApiException {@link InstrumentErrorCode#INVALID_INQ_PERIOD} — 조회 단위·기간 결측 또는 from &gt; to
     */
    public List<PumpFrequencyTimeSeriesDto> findPumpFrequencyTimeSeries(PumpTimeSeriesSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
        }
        List<Pump> inverterPumps = instrumentRepository
                .findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType.PUMP, YnType.Y).stream()
                .map(i -> (Pump) i)   // equip_type_cd = 'PUMP' 파생 쿼리 보장 — 안전 캐스팅
                .filter(p -> p.getDriveType() == PumpDriveType.INVERTER_DRIVE)
                .toList();
        if (inverterPumps.isEmpty()) {
            return List.of();
        }

        Map<String, String> tagByPump = loadFqiTagByInstrument(inverterPumps);
        Map<String, List<RawDataBucketDto>> bucketsByTag = loadAvgBucketsByTag(tagByPump, search);

        return inverterPumps.stream()
                .map(p -> toDto(p, tagByPump, bucketsByTag))
                .toList();
    }

    /** 인버터 펌프들의 FQI 활성 태그를 계측기 ID → 태그 시리얼번호로 매핑 (펌프당 1건 가정 — 다건 시 첫 태그). */
    private Map<String, String> loadFqiTagByInstrument(List<Pump> pumps) {
        List<String> instrumentIds = pumps.stream().map(Pump::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> t.getTagSeCd() == TagMeasurementType.FQI)
                .collect(Collectors.toMap(
                        t -> t.getInstrument().getInstrumentId(),
                        Tag::getTagSrlNo,
                        (first, second) -> first));
    }

    /** FQI 태그들의 버킷별 주파수 평균을 IN 절 단일 쿼리로 조회 후 태그별 그룹화. */
    private Map<String, List<RawDataBucketDto>> loadAvgBucketsByTag(
            Map<String, String> tagByPump, PumpTimeSeriesSearchDto search) {
        List<String> tagSrlNos = List.copyOf(tagByPump.values());
        return rawDataRepository.findAvgValueBuckets(
                        tagSrlNos, search.toStartDtm(), search.toEndExclusiveDtm(),
                        search.getInqUnit().getDateTruncUnit()).stream()
                .collect(Collectors.groupingBy(RawDataBucketDto::tagSrlNo));
    }

    /** 인버터 펌프 1대의 FQI 버킷을 주파수 시계열 DTO 로 매핑. */
    private PumpFrequencyTimeSeriesDto toDto(
            Pump pump,
            Map<String, String> tagByPump,
            Map<String, List<RawDataBucketDto>> bucketsByTag) {
        String tagSrlNo = tagByPump.get(pump.getInstrumentId());
        List<PumpFrequencyTimeSeriesPoint> points = tagSrlNo == null
                ? List.of()
                : bucketsByTag.getOrDefault(tagSrlNo, List.of()).stream()
                        .map(b -> PumpFrequencyTimeSeriesPoint.of(b.baseDtm(), b.aggrVal()))
                        .toList();
        return PumpFrequencyTimeSeriesDto.of(
                pump.getInstrumentId(), pump.getInstrumentNm(), UNIT_HZ, points);
    }
}
