package com.mo.swtp.instrument.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.dto.InstrumentEnergyTrendDto;
import com.mo.swtp.instrument.dto.InstrumentEnergyTrendDto.EnergyTrendPoint;
import com.mo.swtp.instrument.dto.InstrumentEnergyTrendSearchDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설비별 사용량 4번섹션 — 계측기 전력량 트렌드 조회 서비스 (읽기 전용).
 *
 * <p>3번섹션에서 선택한 단일 계측기({@code instrumentId})와 1번섹션 파라미터 3종(집계단위·시작/종료일자)을 받아,
 * 조회기간 동안 그 계측기의 전력량(kWh) 시계열을 단일 계열로 반환한다. 각 버킷 전력량은 PWQ(적산전력량) 태그의
 * GOOD 품질 {@code raw_val} 차분({@code MAX-MIN}, {@code corr_val} 미사용)으로 산정한다
 * (설비별사용량-4번섹션 PLAN1 §구현 방향 4).</p>
 *
 * <p>흐름 — 기간 검증 → 활성 계측기 검증 → 그 계측기의 PWQ 태그 수집 → 버킷 집계(다중 태그 합산) → 단일 DTO 매핑.
 * SQL 호출은 {@code findById} 1 + 태그 IN 1 + 버킷 1 로 고정한다. 추상화 깊이 3단 이하 + 메서드 본문 50줄 이내
 * ({@code .claude/rules/coding-discipline.md §2.1}).</p>
 *
 * <p><strong>다중 PWQ 태그 합산</strong> — 한 계측기가 PWQ 태그를 다건 보유하면(예: ELCMTR 다채널 적산) 동일
 * {@code baseDtm} 버킷의 차분값을 합산한다. {@link PumpPowerTimeSeriesService}(펌프당 1건 가정·첫 태그 채택)와
 * 의도적으로 다른 정책이며, 본 4번섹션은 계측기 단일 스코프이므로 보유 PWQ 태그 전체를 합산해 누락을 방지한다
 * ({@code .claude/rules/coding-discipline.md §1} 가정 명시).</p>
 *
 * <p>도메인 룰 인용: {@code .claude/rules/ot-integration.md §3} — PWQ 는 Hold Last Value 미적용, GOOD only
 * {@code raw_val} 차분이며 음수 차분(적산 리셋·롤오버) 버킷은 생략한다 (해당 차분·필터 SQL 은
 * {@link RawDataRepository#findEnergyDeltaBuckets} 의 {@code §2.5} 면책 영역 — 본 서비스 추가 인용 불필요).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentEnergyTrendService {

    /** 전력량(적산) 태그 유형 — PWQ. */
    private static final TagMeasurementType POWER_ENERGY_TYPE = TagMeasurementType.PWQ;

    /** 전력량 응답 단위. */
    private static final String UNIT_KWH = "kWh";

    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 단일 계측기의 조회기간 전력량 시계열을 조회한다.
     *
     * @param instrumentId 3번섹션에서 선택한 계측기 ID
     * @param search       조회 단위 + from~to 검색 조건
     * @return 계측기 전력량 시계열 (PWQ 태그·데이터 부재 시 points 빈 배열)
     * @throws RestApiException {@link InstrumentErrorCode#INVALID_INQ_PERIOD} — 조회 단위·기간 결측, from &gt; to,
     *                          YEAR 단위, 또는 396일 초과
     * @throws RestApiException {@link InstrumentErrorCode#INSTRUMENT_NOT_FOUND} — 미존재·비활성 계측기
     */
    public InstrumentEnergyTrendDto findEnergyTrend(
            String instrumentId, InstrumentEnergyTrendSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
        }
        Instrument instrument = findActiveInstrumentOrThrow(instrumentId);
        List<String> pwqTags = collectPwqTags(instrumentId);
        List<EnergyTrendPoint> points = pwqTags.isEmpty()
                ? List.of()
                : aggregateBuckets(pwqTags, search);
        return InstrumentEnergyTrendDto.of(
                instrument.getInstrumentId(), instrument.getInstrumentNm(), UNIT_KWH, points);
    }

    /**
     * 활성 계측기를 조회하거나 미존재·비활성 시 INSTRUMENT_NOT_FOUND 예외를 던진다.
     * 계측기 종류({@code equip_type_cd})는 제한하지 않는다 — instrumentId 단일 물리 식별자 스코프.
     */
    private Instrument findActiveInstrumentOrThrow(String instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new RestApiException(InstrumentErrorCode.INSTRUMENT_NOT_FOUND));
        if (instrument.getUseYn() != YnType.Y) {
            throw new RestApiException(InstrumentErrorCode.INSTRUMENT_NOT_FOUND);
        }
        return instrument;
    }

    /** 계측기의 활성 PWQ 태그 시리얼번호를 수집한다 (종류 무필터 — 단일 계측기 스코프). */
    private List<String> collectPwqTags(String instrumentId) {
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(List.of(instrumentId), YnType.Y).stream()
                .filter(t -> t.getTagSeCd() == POWER_ENERGY_TYPE)
                .map(Tag::getTagSrlNo)
                .toList();
    }

    /**
     * PWQ 태그들의 버킷별 전력량 차분을 IN 절 단일 쿼리로 조회한 뒤 동일 {@code baseDtm} 버킷끼리 합산한다.
     * {@code null}·음수 차분(적산 리셋·롤오버) 버킷은 제외하며, {@link TreeMap} 자연 순서로 baseDtm 오름차순 정렬된다.
     */
    private List<EnergyTrendPoint> aggregateBuckets(
            List<String> pwqTags, InstrumentEnergyTrendSearchDto search) {
        Map<LocalDateTime, BigDecimal> sumByBucket = new TreeMap<>();
        for (RawDataBucketDto b : rawDataRepository.findEnergyDeltaBuckets(
                pwqTags, search.toStartDtm(), search.toEndExclusiveDtm(),
                search.getInqUnit().getDateTruncUnit())) {
            if (b.aggrVal() == null || b.aggrVal().signum() < 0) {
                continue;
            }
            sumByBucket.merge(b.baseDtm(), b.aggrVal(), BigDecimal::add);
        }
        return sumByBucket.entrySet().stream()
                .map(e -> EnergyTrendPoint.of(e.getKey(), e.getValue()))
                .toList();
    }
}
