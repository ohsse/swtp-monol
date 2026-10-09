package com.mo.swtp.opt.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.opt.dto.EnergyUsageTrendDto;
import com.mo.swtp.opt.dto.EnergyUsageTrendDto.EnergyUsageTrendPoint;
import com.mo.swtp.opt.dto.EnergyUsageTrendSearchDto;
import com.mo.swtp.opt.exception.OptErrorCode;
import com.mo.swtp.raw.dto.RawDataBucketSumDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용량트렌드 2번섹션 — 정수장 전체 전력량 추이 조회 서비스 (읽기 전용).
 *
 * <p>사용자 지정 기간({@code fromDt~toDt} 익일 00시 이전)·집계단위(시/일/월) 로 정수장을 구성하는 모든 계측기가
 * 보유한 적산전력량(PWQ) 태그의 버킷별 전력량(kWh) 시계열을 산정한다 (사용량트렌드-2번섹션 PLAN1). 흐름:
 * (1) 파라미터 유효성 검증, (2) 전역 활성 PWQ 태그 수집, (3) 버킷별 전역 합산 쿼리 호출, (4) sparse 시계열 매핑.</p>
 *
 * <p>전역 합산 = 전체 활성 PWQ 태그({@code tag_se_cd='PWQ' AND use_yn='Y'}) 차분의 버킷별 합산. 현재 펌프
 * 서브미터만 존재하고 메인 적산미터(ELCMTR) 부재라 이중계상 없음 — 향후 메인 적산미터 추가 시 이중계상 위험은
 * 별도 사이클 재검토 (5번섹션 {@link PeakEnergyTrendService} 기록 동형, 사용량트렌드-2번섹션 ANALYZE1).</p>
 *
 * <p>각 버킷 전력량은 태그별 적산값 차분({@code MAX-MIN}) 후 버킷별 합산이다
 * ({@code SUM(MAX-MIN) != MAX(SUM)-MIN(SUM)} — 태그별 선차분 후 합산). 본 서비스는 합산을 DB 에서 수행하므로
 * ({@link RawDataRepository#findEnergyDeltaBucketsTotal}) 5번섹션의 Java 음수 가드가 불필요하다 —
 * {@code MAX-MIN} 은 그룹 내 항상 ≥0이며 GOOD 데이터 없는 버킷은 자연 sparse 다
 * ({@code .claude/rules/ot-integration.md §3} PWQ 적산값 차분 정책).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnergyUsageTrendService {

    /** 시계열 전력량 응답 단위. */
    private static final String UNIT_KWH = "kWh";

    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 사용자 지정 기간·집계단위로 정수장 전체 적산전력량(PWQ) 추이를 조회한다.
     *
     * @param searchDto 집계단위·시작일·종료일 검색 조건
     * @return 버킷별 전역 합산 전력량 시계열 (sparse, 버킷 시작 일시 오름차순). 태그·데이터 부재 시 빈 시계열
     * @throws RestApiException INVALID_SEARCH_PERIOD — 파라미터 null·기간 역전·YEAR 미지원·13개월 초과
     */
    public EnergyUsageTrendDto getEnergyUsageTrend(EnergyUsageTrendSearchDto searchDto) {
        if (!searchDto.isValid()) {
            throw new RestApiException(OptErrorCode.INVALID_SEARCH_PERIOD);
        }
        List<RawDataBucketSumDto> buckets = rawDataRepository.findEnergyDeltaBucketsTotal(
                pwqTagSrlNos(),
                searchDto.toStartDtm(),
                searchDto.toEndExclusiveDtm(),
                searchDto.getInqUnit().getDateTruncUnit());
        List<EnergyUsageTrendPoint> points = buckets.stream()
                .map(b -> EnergyUsageTrendPoint.of(b.baseDtm(), b.totalVal()))
                .toList();
        return EnergyUsageTrendDto.of(UNIT_KWH, points);
    }

    /** 시스템 전역 활성 PWQ(적산전력량) 태그 시리얼번호 수집. */
    private List<String> pwqTagSrlNos() {
        return tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y).stream()
                .map(Tag::getTagSrlNo)
                .toList();
    }
}
