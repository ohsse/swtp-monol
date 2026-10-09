package com.mo.swtp.instrument.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.dto.PumpPowerTimeSeriesDto;
import com.mo.swtp.instrument.dto.PumpPowerTimeSeriesDto.PumpPowerTimeSeriesPoint;
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
 * 송수펌프 가동이력 3번섹션 — 펌프별 전력량 시계열 조회 서비스.
 *
 * <p>전체 활성 송수펌프({@code equip_type_cd = 'PUMP'} + {@code use_yn = Y}) 의 조회기간 전력량(kWh)을
 * 펌프별 계열로 구성한다. 각 펌프의 PWQ(적산전력량) 태그를 버킷별로 차분({@code MAX(raw_val)-MIN(raw_val)})하여
 * 시계열 포인트로 매핑한다 (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 6).</p>
 *
 * <p>4단 흐름 — 기간 검증 → 활성 PUMP 조회 → PWQ 태그 IN 일괄 조회·버킷 집계 → 펌프별 DTO 매핑
 * ({@link PumpOperationRateService} 동형). 시계열 마스터 FK 금지 정책상 태그 → 측정값은 {@code tag_srl_no}
 * 논리 참조로 IN 절 일괄 조회한다. 추상화 깊이 3단 이하 + 메서드 본문 50줄 이내
 * ({@code .claude/rules/coding-discipline.md §2.1}).</p>
 *
 * <p>도메인 룰 인용: {@code .claude/rules/ot-integration.md §3} (PWQ Hold Last Value 미적용 — GOOD only
 * {@code raw_val} 차분, {@code corr_val} 미사용) · {@code .claude/rules/entity-patterns.md §JPA JOINED 다형성
 * §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PumpPowerTimeSeriesService {

    /** 전력량 응답 단위. */
    private static final String UNIT_KWH = "kWh";

    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 전체 활성 송수펌프의 조회기간 전력량 시계열을 조회한다.
     *
     * @param search 조회 단위 + from~to 검색 조건
     * @return 펌프별 전력량 시계열 목록 (표시 순서 → 계측기명 정렬). 활성 펌프 0대 시 빈 목록
     * @throws RestApiException {@link InstrumentErrorCode#INVALID_INQ_PERIOD} — 조회 단위·기간 결측 또는 from &gt; to
     */
    public List<PumpPowerTimeSeriesDto> findPumpPowerTimeSeries(PumpTimeSeriesSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
        }
        List<Instrument> pumps = instrumentRepository
                .findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType.PUMP, YnType.Y);
        if (pumps.isEmpty()) {
            return List.of();
        }

        Map<String, String> tagByPump = loadPwqTagByInstrument(pumps);
        Map<String, List<RawDataBucketDto>> bucketsByTag = loadEnergyBucketsByTag(tagByPump, search);

        return pumps.stream()
                .map(i -> (Pump) i)   // equip_type_cd = 'PUMP' 파생 쿼리 보장 — 안전 캐스팅
                .map(p -> toDto(p, tagByPump, bucketsByTag))
                .toList();
    }

    /** 펌프들의 PWQ 활성 태그를 계측기 ID → 태그 시리얼번호로 매핑 (펌프당 1건 가정 — 다건 시 첫 태그). */
    private Map<String, String> loadPwqTagByInstrument(List<Instrument> pumps) {
        List<String> instrumentIds = pumps.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> t.getTagSeCd() == TagMeasurementType.PWQ)
                .collect(Collectors.toMap(
                        t -> t.getInstrument().getInstrumentId(),
                        Tag::getTagSrlNo,
                        (first, second) -> first));
    }

    /** PWQ 태그들의 버킷별 전력량 차분을 IN 절 단일 쿼리로 조회 후 태그별 그룹화. */
    private Map<String, List<RawDataBucketDto>> loadEnergyBucketsByTag(
            Map<String, String> tagByPump, PumpTimeSeriesSearchDto search) {
        List<String> tagSrlNos = List.copyOf(tagByPump.values());
        return rawDataRepository.findEnergyDeltaBuckets(
                        tagSrlNos, search.toStartDtm(), search.toEndExclusiveDtm(),
                        search.getInqUnit().getDateTruncUnit()).stream()
                .collect(Collectors.groupingBy(RawDataBucketDto::tagSrlNo));
    }

    /** 펌프 1대의 PWQ 버킷을 전력량 시계열 DTO 로 매핑. 음수 차분(적산 리셋·롤오버) 버킷은 생략. */
    private PumpPowerTimeSeriesDto toDto(
            Pump pump,
            Map<String, String> tagByPump,
            Map<String, List<RawDataBucketDto>> bucketsByTag) {
        String tagSrlNo = tagByPump.get(pump.getInstrumentId());
        List<PumpPowerTimeSeriesPoint> points = tagSrlNo == null
                ? List.of()
                : bucketsByTag.getOrDefault(tagSrlNo, List.of()).stream()
                        .filter(b -> b.aggrVal() != null && b.aggrVal().signum() >= 0)
                        .map(b -> PumpPowerTimeSeriesPoint.of(b.baseDtm(), b.aggrVal()))
                        .toList();
        return PumpPowerTimeSeriesDto.of(
                pump.getInstrumentId(), pump.getInstrumentNm(), UNIT_KWH, points);
    }
}
