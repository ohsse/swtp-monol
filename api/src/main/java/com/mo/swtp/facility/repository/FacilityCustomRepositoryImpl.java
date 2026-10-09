package com.mo.swtp.facility.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.QFacility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilitySearchDto;
import com.mo.swtp.instrument.domain.QInstrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * {@link FacilityCustomRepository} Querydsl 구현.
 *
 * <p>{@code JPAQueryFactory} 빈은 {@code ApiQuerydslConfig} 에서 제공된다.</p>
 *
 * <p>"DWT 자식 보유 부모 시설 목록 조회" 메서드는 시설물응답DTO명세 ANALYZE1 (2026-05-12)
 * 안건 4 결정으로 백지화되었다 — 호출자 0건 고아 자산.</p>
 */
@RequiredArgsConstructor
public class FacilityCustomRepositoryImpl implements FacilityCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Facility> findFirstChildByParentIdAndType(
            String parentFacilityId, FacilityType childType) {
        if (parentFacilityId == null || childType == null) {
            return Optional.empty();
        }
        QFacility f = QFacility.facility;
        Facility result = queryFactory
                .selectFrom(f)
                .where(
                        f.parentFacilityId.eq(parentFacilityId),
                        f.facilityType.eq(childType),
                        f.useYn.eq(YnType.Y))
                .orderBy(f.dispOrd.asc())
                .limit(1)
                .fetchFirst();
        return Optional.ofNullable(result);
    }

    @Override
    public List<Facility> findFacilities(FacilitySearchDto searchDto) {
        QFacility f = QFacility.facility;
        BooleanBuilder where = new BooleanBuilder();
        if (searchDto != null) {
            if (searchDto.getFacilityTypeCd() != null) {
                where.and(f.facilityType.eq(searchDto.getFacilityTypeCd()));
            }
            if (searchDto.getFacilityGroupCd() != null) {
                // 설비별사용량-2번섹션 (2026-06-09) — 그룹 단위 조회를 facility_type_cd IN (소속 8종) 으로 전개.
                // facility_type_cd 필터 강제 룰 준수 (다른 그룹 자식 혼입 차단).
                where.and(f.facilityType.in(FacilityType.typesOf(searchDto.getFacilityGroupCd())));
            }
            if (searchDto.getUseYn() != null) {
                where.and(f.useYn.eq(searchDto.getUseYn()));
            }
            if (Boolean.TRUE.equals(searchDto.getHasPump())) {
                // 송수펌프_시설목록 (2026-05-13) — 활성 펌프 1대 이상 보유 시설만 응답.
                QInstrument i = QInstrument.instrument;
                where.and(JPAExpressions.selectOne()
                        .from(i)
                        .where(
                                i.facility.facilityId.eq(f.facilityId),
                                i.equipType.eq(EquipType.PUMP),
                                i.useYn.eq(YnType.Y))
                        .exists());
            }
        }
        return queryFactory
                .selectFrom(f)
                .where(where)
                .orderBy(f.useYn.desc(), f.dispOrd.asc(), f.facilityNm.asc())
                .fetch();
    }
}
