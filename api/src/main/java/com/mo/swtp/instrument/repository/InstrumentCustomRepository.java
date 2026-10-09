package com.mo.swtp.instrument.repository;

import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.dto.InstrumentSearchDto;
import java.util.List;

/**
 * 계측기 커스텀 조회 인터페이스 (Querydsl).
 *
 * <p>송수펌프제어분석 PLAN1 (2026-05-08) 도입 — 시설 + 자식 종류 결합 조회.
 * {@code equip_type_cd} 필터 강제 — 도메인 룰 인용 근거: {@code .claude/rules/entity-patterns.md}
 * §JPA JOINED §도메인 룰.</p>
 */
public interface InstrumentCustomRepository {

    /**
     * 시설 + 자식 종류 결합 조회 — 활성 계측기 목록을 {@code disp_ord} ASC 정렬로 반환.
     *
     * <p>송수펌프제어분석 §2 (펌프·유량계·압력계) 호출. 한 번의 SQL 로
     * {@code (facility_id, equip_type_cd IN (...))} 필터를 적용한다.</p>
     *
     * @param facilityId 소속 시설 ID
     * @param equipTypes 자식 종류 목록 (예: {@code [PUMP, FLWMTR, PRSMTR]})
     * @return {@code disp_ord} ASC 정렬 계측기 목록 (빈 리스트 가능)
     */
    List<Instrument> findByFacilityIdAndEquipType(String facilityId, List<EquipType> equipTypes);

    /**
     * 시설 IN + 자식 종류 결합 조회 — 활성 계측기를 한 번의 SQL 로 가져온다 (N+1 회피).
     *
     * <p>송수펌프제어분석 §3 (자식 DWT N건의 PRSMTR·FLWMTR·VALVE·LVMTR 한 번에 조회) 호출.
     * Service 는 결과를 {@code facility.getFacilityId()} 로 그룹화하여 DWT 별 측정값을 구성한다.</p>
     *
     * @param facilityIds 소속 시설 ID 리스트 (예: 활성 정수지의 자식 DWT ID 들)
     * @param equipTypes  자식 종류 목록
     * @return 계측기 목록 (빈 리스트 가능)
     */
    List<Instrument> findByFacilityIdInAndEquipType(List<String> facilityIds, List<EquipType> equipTypes);

    /**
     * 계측기 목록 조회 — {@code equipTypeCd} / {@code useYn} / {@code facilityId} 필터 + 정렬.
     *
     * <p>계측기관리CRUD PLAN1 (2026-05-12) — 세 필터 모두 NULL 허용. NULL 인 필드는 조건 미적용
     * (전체 반환). 정렬 순서: {@code use_yn} DESC (활성 우선) → {@code disp_ord} ASC →
     * {@code instrument_nm} ASC. FacilityCustomRepositoryImpl.findFacilities 동일 패턴.</p>
     *
     * @param searchDto 조회 필터 (NULL 허용)
     * @return 정렬된 계측기 목록
     */
    List<Instrument> findInstruments(InstrumentSearchDto searchDto);
}
