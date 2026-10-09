package com.mo.swtp.instrument.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 계측기 부모 마스터 JPA 리포지토리.
 *
 * <p>JPA JOINED 다형성 부모 ({@link Instrument}) 의 단순 CRUD 를 담당한다.
 * 인터록 평가·제어 명령 발행 시 자식 종류별 도메인 룰이 다르므로 {@link #findByEquipType(EquipType)} 로
 * 자식 종류 필터를 강제 적용한다 — {@code .claude/rules/entity-patterns.md} §JPA JOINED 도메인 룰
 * §{@code equip_type_cd} 필터 강제 정합 + {@code .claude/rules/ot-integration.md} §2 인터록 평가 정합.</p>
 *
 * <p>N+1 방지: 부모 다형성 목록 조회 시 자식 테이블 LEFT OUTER JOIN 이 자식 종류 수만큼 발생할 수 있으므로
 * {@link BatchSize} 적용 (PLAN1 §Phase 3 의무, {@code db/query-tuning.md §2} 정합).</p>
 */
public interface InstrumentRepository extends JpaRepository<Instrument, String>, InstrumentCustomRepository {

    /**
     * 자식 종류별 계측기 목록 조회 — 부모 다형성 전체 조회 시 도메인 룰 필터 강제.
     *
     * @param equipType 장비 유형 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR)
     * @return 해당 종류 계측기 목록
     */
    @BatchSize(size = 100)
    List<Instrument> findByEquipType(EquipType equipType);

    /**
     * 자식 종류 + 사용 여부 필터 계측기 목록 조회 (표시 순서 → 계측기명 정렬).
     *
     * <p>송수펌프 가동이력 2번섹션 — 전체 활성 송수펌프({@code equip_type_cd = 'PUMP'} + {@code use_yn = Y}) 를
     * {@code disp_ord ASC}, 동률 시 {@code instrument_nm ASC} 로 조회한다. {@link #findByEquipType(EquipType)} 의
     * discriminator 파생 쿼리에 {@code use_yn} 필터 + 정렬을 더한 동형 확장 ({@code equip_type_cd} 필터 강제 —
     * {@code .claude/rules/entity-patterns.md} §JPA JOINED 도메인 룰). 반환 element 는 모두 해당 자식 인스턴스
     * (PUMP → {@link com.mo.swtp.instrument.domain.Pump}) 이므로 안전 캐스팅 가능
     * (송수펌프가동이력_2번섹션 PLAN1 §Phase 2).</p>
     *
     * @param equipType 장비 유형 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR)
     * @param useYn     사용 여부 (보통 {@link YnType#Y})
     * @return 해당 종류 + 사용 여부 계측기 목록 (표시 순서 → 계측기명 정렬)
     */
    @BatchSize(size = 100)
    List<Instrument> findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(
            EquipType equipType, YnType useYn);

    /**
     * 시설별 계측기 목록 조회 — FK 관계를 통한 조회.
     *
     * @param facilityId 소속 시설 ID
     * @return 해당 시설 소속 계측기 목록
     */
    @BatchSize(size = 100)
    List<Instrument> findByFacilityFacilityId(String facilityId);

    /**
     * 시설 ID 묶음의 활성 계측기를 한 번의 IN 절로 조회한다 — N+1 회피.
     *
     * <p>시설별사용량-2번섹션 — 운영시설 + 재귀 하위 롤업으로 도출된 시설 집합의 전체 계측기를 일괄 조회한다
     * (PLAN1 §Service 흐름 5). 자식 종류 필터는 적용하지 않는다 — PWI/PWQ 태그를 보유한 모든 계측기(펌프·전력계
     * 등) 가 합산 대상이므로 ({@code .claude/rules/entity-patterns.md} §JPA JOINED 도메인 룰 — 종류 무관 합산
     * 시나리오). 반환된 계측기의 {@code facility.facilityId} 는 프록시 식별자 접근으로 추가 쿼리 없이 읽는다.
     * {@code use_yn = Y} 필터로 논리 삭제 계측기는 제외한다.</p>
     *
     * @param facilityIds 시설 ID 묶음 (운영시설 + 롤업 하위)
     * @param useYn       사용 여부 (보통 {@link YnType#Y})
     * @return 활성 계측기 목록 (빈 리스트 가능)
     */
    @BatchSize(size = 100)
    List<Instrument> findByFacilityFacilityIdInAndUseYn(List<String> facilityIds, YnType useYn);

    /**
     * 시설별 계측기명 존재 여부 — {@code (facility_id, instrument_nm)} 복합 UNIQUE 보장.
     *
     * @param facilityId    소속 시설 ID
     * @param instrumentNm  계측기명
     * @return 1건 이상 존재 시 true
     */
    boolean existsByFacilityFacilityIdAndInstrumentNm(String facilityId, String instrumentNm);

    /**
     * 시설별 계측기명 존재 여부 — 자기 자신을 제외한 UNIQUE 검사 (수정 시).
     *
     * <p>계측기관리CRUD PLAN1 (2026-05-12) — PUT 수정 시 계측기명 변경이 같은 시설 내 다른 계측기와
     * 충돌하는지만 검사. 자기 자신은 같은 계측기명 유지가 정상 케이스이므로 제외. FacilityRepository 의
     * {@code existsByFacilityNmAndFacilityIdNot} 동일 패턴.</p>
     *
     * @param facilityId    소속 시설 ID
     * @param instrumentNm  계측기명
     * @param instrumentId  자기 자신 제외 대상 계측기 ID
     * @return 자기 자신을 제외하고 1건 이상 존재 시 true
     */
    boolean existsByFacilityFacilityIdAndInstrumentNmAndInstrumentIdNot(
            String facilityId, String instrumentNm, String instrumentId);
}
