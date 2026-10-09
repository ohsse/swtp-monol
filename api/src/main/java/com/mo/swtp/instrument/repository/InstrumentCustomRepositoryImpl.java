package com.mo.swtp.instrument.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.QInstrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.dto.InstrumentSearchDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * {@link InstrumentCustomRepository} Querydsl 구현.
 *
 * <p>{@code JPAQueryFactory} 빈은 {@code ApiQuerydslConfig} 에서 제공된다.</p>
 */
@RequiredArgsConstructor
public class InstrumentCustomRepositoryImpl implements InstrumentCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Instrument> findByFacilityIdAndEquipType(String facilityId, List<EquipType> equipTypes) {
        if (facilityId == null || equipTypes == null || equipTypes.isEmpty()) {
            return List.of();
        }
        QInstrument i = QInstrument.instrument;
        return queryFactory
                .selectFrom(i)
                .where(
                        i.facility.facilityId.eq(facilityId),
                        i.equipType.in(equipTypes),
                        i.useYn.eq(YnType.Y))
                .orderBy(i.dispOrd.asc())
                .fetch();
    }

    @Override
    public List<Instrument> findByFacilityIdInAndEquipType(List<String> facilityIds, List<EquipType> equipTypes) {
        if (facilityIds == null || facilityIds.isEmpty() || equipTypes == null || equipTypes.isEmpty()) {
            return List.of();
        }
        QInstrument i = QInstrument.instrument;
        return queryFactory
                .selectFrom(i)
                .where(
                        i.facility.facilityId.in(facilityIds),
                        i.equipType.in(equipTypes),
                        i.useYn.eq(YnType.Y))
                .orderBy(i.dispOrd.asc())
                .fetch();
    }

    @Override
    public List<Instrument> findInstruments(InstrumentSearchDto searchDto) {
        QInstrument i = QInstrument.instrument;
        BooleanBuilder where = new BooleanBuilder();
        if (searchDto != null) {
            if (searchDto.getEquipTypeCd() != null) {
                where.and(i.equipType.eq(searchDto.getEquipTypeCd()));
            }
            if (searchDto.getUseYn() != null) {
                where.and(i.useYn.eq(searchDto.getUseYn()));
            }
            if (searchDto.getFacilityId() != null && !searchDto.getFacilityId().isBlank()) {
                where.and(i.facility.facilityId.eq(searchDto.getFacilityId()));
            }
        }
        return queryFactory
                .selectFrom(i)
                .where(where)
                .orderBy(i.useYn.desc(), i.dispOrd.asc(), i.instrumentNm.asc())
                .fetch();
    }
}
