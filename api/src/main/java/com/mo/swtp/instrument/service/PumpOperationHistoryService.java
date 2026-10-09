package com.mo.swtp.instrument.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.dto.PumpOperationHistoryDto;
import com.mo.swtp.instrument.dto.PumpOperationHistoryDto.OperationSegment;
import com.mo.swtp.instrument.dto.PumpPeriodSearchDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataOnStateDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프 가동이력 4번섹션 — 펌프별 가동상태 타임라인 조회 서비스.
 *
 * <p>전체 활성 송수펌프({@code equip_type_cd = 'PUMP'} + {@code use_yn = Y}) 의 조회기간 가동상태(OPS) 시계열을
 * 가동(ON) 구간 세그먼트로 압축하여 펌프별 계열로 구성한다 (송수펌프가동이력_4번섹션 PLAN1 §구현 방향 4).</p>
 *
 * <p>4단 흐름 — 기간 검증 → 활성 PUMP 조회 → OPS 태그 IN 일괄 조회·ON 시각 그룹화 → 펌프별 런렝스 인코딩 DTO 매핑
 * ({@link PumpPowerTimeSeriesService} 동형). 시계열 마스터 FK 금지 정책상 태그 → 측정값은 {@code tag_srl_no}
 * 논리 참조로 IN 절 일괄 조회한다. 추상화 깊이 3단 이하 + 메서드 본문 50줄 이내
 * ({@code .claude/rules/coding-discipline.md §2.1}).</p>
 *
 * <p>도메인 룰 인용: {@code .claude/rules/ot-integration.md §3} (OPS Hold Last Value 미적용 — ON 행만 조회,
 * BAD/OFF/결측 구간은 세그먼트 미생성으로 통신단절 펌프 ON 오인 금지) · {@code .claude/rules/entity-patterns.md
 * §JPA JOINED 다형성 §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PumpOperationHistoryService {

    /** SCADA 수집 주기(분) — 런렝스 인코딩 시 연속 ON 판정 + 세그먼트 종료 경계 산정 기준. */
    private static final long COLLECTION_INTERVAL_MINUTES = 1L;

    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 전체 활성 송수펌프의 조회기간 가동상태 타임라인을 조회한다.
     *
     * @param search from~to 조회기간 검색 조건
     * @return 펌프별 가동 구간 세그먼트 목록 (표시 순서 → 계측기명 정렬). 활성 펌프 0대 시 빈 목록
     * @throws RestApiException {@link InstrumentErrorCode#INVALID_INQ_PERIOD} — 기간 결측 또는 from &gt; to
     */
    public List<PumpOperationHistoryDto> findPumpOperationHistory(PumpPeriodSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
        }
        List<Instrument> pumps = instrumentRepository
                .findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType.PUMP, YnType.Y);
        if (pumps.isEmpty()) {
            return List.of();
        }

        Map<String, String> tagByPump = loadOpsTagByInstrument(pumps);
        Map<String, List<LocalDateTime>> onAcqByTag = loadOnAcqByTag(tagByPump, search);

        return pumps.stream()
                .map(i -> (Pump) i)   // equip_type_cd = 'PUMP' 파생 쿼리 보장 — 안전 캐스팅
                .map(p -> toDto(p, tagByPump, onAcqByTag))
                .toList();
    }

    /** 펌프들의 OPS 활성 태그를 계측기 ID → 태그 시리얼번호로 매핑 (펌프당 1건 가정 — 다건 시 첫 태그). */
    private Map<String, String> loadOpsTagByInstrument(List<Instrument> pumps) {
        List<String> instrumentIds = pumps.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> t.getTagSeCd() == TagMeasurementType.OPS)
                .collect(Collectors.toMap(
                        t -> t.getInstrument().getInstrumentId(),
                        Tag::getTagSrlNo,
                        (first, second) -> first));
    }

    /** OPS 태그들의 가동(ON) 시각을 IN 절 단일 쿼리(방안B)로 조회 후 태그별 시각 목록으로 그룹화 (acq_dtm 오름차순 보존). */
    private Map<String, List<LocalDateTime>> loadOnAcqByTag(
            Map<String, String> tagByPump, PumpPeriodSearchDto search) {
        List<String> tagSrlNos = List.copyOf(tagByPump.values());
        return rawDataRepository.findOnStateByTagSrlNosAndDtmRange(
                        tagSrlNos, search.toStartDtm(), search.toEndExclusiveDtm()).stream()
                .collect(Collectors.groupingBy(RawDataOnStateDto::tagSrlNo,
                        Collectors.mapping(RawDataOnStateDto::acqDtm, Collectors.toList())));
    }

    /** 펌프 1대의 ON 시각 목록을 가동 구간 세그먼트로 압축하여 타임라인 DTO 로 매핑. OPS 태그 부재 펌프는 세그먼트 빈 배열. */
    private PumpOperationHistoryDto toDto(
            Pump pump,
            Map<String, String> tagByPump,
            Map<String, List<LocalDateTime>> onAcqByTag) {
        String tagSrlNo = tagByPump.get(pump.getInstrumentId());
        List<OperationSegment> segments = tagSrlNo == null
                ? List.of()
                : encodeSegments(onAcqByTag.getOrDefault(tagSrlNo, List.of()));
        return PumpOperationHistoryDto.of(pump.getInstrumentId(), pump.getInstrumentNm(), segments);
    }

    /**
     * 오름차순 ON 시각 목록을 가동 구간 세그먼트로 런렝스 인코딩한다.
     *
     * <p>직전 시각 + 수집 주기(1분) 와 일치하면 같은 구간으로 확장하고, 간극(OFF·BAD·결측으로 인한 ON 행 부재)
     * 이 발생하면 직전 구간을 종료하고 새 구간을 시작한다. 각 구간의 종료 시각은 마지막 ON 시각 + 1분(수집 주기)
     * 으로 가동 유지 우상한을 표현한다 (송수펌프가동이력_4번섹션 PLAN1 §구현 방향 4).</p>
     *
     * @param onTimes 오름차순 정렬된 가동(ON) 수집 시각 목록 (빈 목록 시 빈 세그먼트)
     * @return 가동 구간 세그먼트 목록
     */
    private List<OperationSegment> encodeSegments(List<LocalDateTime> onTimes) {
        if (onTimes.isEmpty()) {
            return List.of();
        }
        List<OperationSegment> segments = new ArrayList<>();
        LocalDateTime segStart = onTimes.get(0);
        LocalDateTime prev = segStart;
        for (int i = 1; i < onTimes.size(); i++) {
            LocalDateTime cur = onTimes.get(i);
            if (cur.equals(prev.plusMinutes(COLLECTION_INTERVAL_MINUTES))) {
                prev = cur;
            } else {
                segments.add(OperationSegment.of(segStart, prev.plusMinutes(COLLECTION_INTERVAL_MINUTES)));
                segStart = cur;
                prev = cur;
            }
        }
        segments.add(OperationSegment.of(segStart, prev.plusMinutes(COLLECTION_INTERVAL_MINUTES)));
        return segments;
    }
}
