package com.mo.swtp.tag.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 태그 마스터 JPA 리포지토리.
 *
 * <p>{@link Tag} 의 단순 CRUD 를 담당한다. PK 는 자연키 ({@code tag_srl_no}) 이므로
 * {@link Tag#isNew()} 가 {@code BaseEntity.newEntity} 플래그에 위임되어 신규 INSERT 경로를 보장한다
 * ({@code .claude/rules/entity-patterns.md} §외부 할당 PK 엔티티 패턴).</p>
 */
public interface TagRepository extends JpaRepository<Tag, String> {

    /**
     * 계측기별 태그 목록 조회 — FK 관계를 통한 조회.
     *
     * @param instrumentId 소속 계측기 ID
     * @return 해당 계측기 소속 태그 목록
     */
    List<Tag> findByInstrumentInstrumentId(String instrumentId);

    /**
     * 계측기 ID 목록의 활성 태그를 한 번의 IN 절로 조회한다 — N+1 회피.
     *
     * <p>송수펌프제어분석 PLAN1 §호출 흐름 — Service 가 instrument 목록 추출 후 본 메서드 1회 호출로
     * 태그 시리얼번호 묶음을 수집한다. {@code use_yn = Y} 필터로 논리 삭제된 태그는 제외한다
     * ({@code rawdata_1m_h.tag_srl_no} 논리 참조 보존 정책).</p>
     *
     * @param instrumentIds 계측기 ID 리스트
     * @param useYn         사용 여부 (보통 {@link YnType#Y})
     * @return 활성 태그 목록
     */
    List<Tag> findByInstrumentInstrumentIdInAndUseYn(List<String> instrumentIds, YnType useYn);

    /**
     * 측정 유형 + 사용 여부로 활성 태그를 시스템 전역에서 조회한다.
     *
     * <p>전력피크분석-2번섹션 PLAN1 (2026-06-05) — 시설·계측기 무관 전체 PWI(순시전력) 태그를 한 번의
     * 파생 쿼리로 수집한다. {@code FacilityOperatingStatusService} 의 시설 단위 PWI 수집
     * ({@link #findByInstrumentInstrumentIdInAndUseYn}) 과 달리, 본 메서드는 시스템 전역 PWI 합산
     * (총순시전력·요금적용전력피크) 입력을 구성한다. {@code use_yn = Y} 필터로 논리 삭제 태그는 제외한다.</p>
     *
     * @param tagSeCd 태그 측정 유형 (보통 {@link TagMeasurementType#PWI})
     * @param useYn   사용 여부 (보통 {@link YnType#Y})
     * @return 해당 측정 유형의 활성 태그 목록
     */
    List<Tag> findByTagSeCdAndUseYn(TagMeasurementType tagSeCd, YnType useYn);

    /**
     * 측정 유형 + 사용 여부 + 계측기 종류로 활성 태그를 시스템 전역에서 조회한다.
     *
     * <p>전력피크분석-3번섹션 ANALYZE1 안건 2 (2026-06-05) — 펌프({@code equip_type_cd='PUMP'}) 에 매핑된
     * PWI(순시전력) 태그만 선별하여 송수펌프 순시전력 합산 입력을 구성한다. {@link #findByTagSeCdAndUseYn}
     * (전체 PWI — 총순시전력) 의 부분집합으로, {@code tag_m ↔ instrument_m} INNER JOIN 1회로
     * {@code equip_type_cd} 필터를 처리한다 — {@code instrument} lazy 접근 후 종류 판별 시 발생하는 N+1
     * ({@code .claude/rules/db/query-tuning.md} §2) 을 회피한다. {@code instrument_m} 은 JPA JOINED 상속이라
     * 부모 테이블 단일 JOIN 으로 종류 코드를 얻는다. {@code use_yn = Y} 필터로 논리 삭제 태그는 제외한다.</p>
     *
     * @param tagSeCd   태그 측정 유형 (보통 {@link TagMeasurementType#PWI})
     * @param useYn     사용 여부 (보통 {@link YnType#Y})
     * @param equipType 계측기 종류 (보통 {@link EquipType#PUMP})
     * @return 해당 측정 유형 + 계측기 종류의 활성 태그 목록
     */
    List<Tag> findByTagSeCdAndUseYnAndInstrument_EquipType(
            TagMeasurementType tagSeCd, YnType useYn, EquipType equipType);

    /**
     * 전체 태그 목록을 활성/비활성 모두 포함하여 정렬 조회한다.
     *
     * <p>정렬 기준은 {@code use_yn} 내림차순(활성 우선) 후 {@code tag_srl_no} 오름차순.
     * ADMIN 전용 태그 관리 화면에서 호출하며, 비활성 태그도 함께 반환하여 ADMIN 이
     * 메타 검토 또는 재활성화 결정을 내릴 수 있도록 한다 (사용자 패턴 정합 —
     * {@code UserRepository#findAllByOrderByUseYnDescUserIdAsc()} 선례).</p>
     *
     * @return 정렬된 태그 목록 (활성 우선 + 시리얼번호 오름차순)
     */
    List<Tag> findAllByOrderByUseYnDescTagSrlNoAsc();
}
