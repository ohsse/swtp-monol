package com.mo.swtp.instrument.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.QInstrument;
import com.mo.swtp.instrument.domain.QPumpCtrlHistory;
import com.mo.swtp.instrument.dto.PumpCtrlHistoryDto;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import com.mo.swtp.tag.domain.QTag;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * {@link PumpCtrlHistoryCustomRepository} Querydsl 구현.
 *
 * <p>{@code JPAQueryFactory} 빈은 {@code ApiQuerydslConfig} 에서 제공된다.</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용} (EXPLAIN ANALYZE 파티션 프루닝 검증
 * 대상). 두 메서드 모두 {@code ctrl_dtm} 범위 조건으로 월 RANGE 파티션 프루닝을 강제하며, 집계·상관 서브쿼리
 * 의도 단일 흐름 보존이 목적이다.</p>
 */
@RequiredArgsConstructor
public class PumpCtrlHistoryCustomRepositoryImpl implements PumpCtrlHistoryCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<AiDrvnModeCode, Long> countByAiDrvnMode(LocalDateTime startDtm, LocalDateTime endDtm) {
        // §2.5 면책 (query-tuning.md §2) — ctrl_dtm 범위로 월 RANGE 파티션 프루닝 강제, 수동(NULL) 제외 모드별 집계
        QPumpCtrlHistory pch = QPumpCtrlHistory.pumpCtrlHistory;
        List<Tuple> rows = queryFactory
                .select(pch.aiDrvnMod, pch.count())
                .from(pch)
                .where(
                        pch.ctrlDtm.goe(startDtm),
                        pch.ctrlDtm.lt(endDtm),
                        pch.aiDrvnMod.isNotNull())
                .groupBy(pch.aiDrvnMod)
                .fetch();

        Map<AiDrvnModeCode, Long> counts = new EnumMap<>(AiDrvnModeCode.class);
        for (Tuple row : rows) {
            AiDrvnModeCode mode = row.get(pch.aiDrvnMod);
            Long count = row.get(pch.count());
            counts.put(mode, count != null ? count : 0L);
        }
        return counts;
    }

    @Override
    public List<PumpCtrlHistoryDto> findCtrlHistoryList(LocalDateTime startDtm, LocalDateTime endDtm) {
        // §2.5 면책 (query-tuning.md §2) — pch ⋈ instrument(펌프명) + 제어태그(CMD/OUTPUT) 상관 서브쿼리,
        //                                  ctrl_dtm 범위 파티션 프루닝, 시간 역순 정렬
        QPumpCtrlHistory pch = QPumpCtrlHistory.pumpCtrlHistory;
        QInstrument instrument = QInstrument.instrument;
        QTag tag = QTag.tag;
        return queryFactory
                .select(Projections.constructor(PumpCtrlHistoryDto.class,
                        pch.ctrlDtm,
                        pch.instrumentId,
                        instrument.instrumentNm,
                        JPAExpressions.select(tag.tagSrlNo.min())
                                .from(tag)
                                .where(
                                        tag.instrument.instrumentId.eq(pch.instrumentId),
                                        tag.tagSeCd.eq(TagMeasurementType.CMD),
                                        tag.ioCd.eq(IoCode.OUTPUT),
                                        tag.useYn.eq(YnType.Y)),
                        pch.ctrlDiv,
                        pch.ctrlRslt,
                        pch.updtDtm,
                        pch.aiDrvnMod))
                .from(pch)
                .leftJoin(instrument).on(instrument.instrumentId.eq(pch.instrumentId))
                .where(
                        pch.ctrlDtm.goe(startDtm),
                        pch.ctrlDtm.lt(endDtm))
                .orderBy(pch.ctrlDtm.desc())
                .fetch();
    }
}
