package com.mo.swtp.facility.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시설 단일 마스터 엔티티 (JPA JOINED 다형성 부모).
 *
 * <p>정수조({@link PurifiedWaterTank})·배수지({@link DistributionWaterTank})·저수지({@link Reservoir})
 * 3종 자식을 {@code facility_type_cd} DiscriminatorColumn 으로 식별한다.
 * 마스터도메인설계 ANALYZE1 Round 2 (2026-05-02) 결정으로 종전 {@code pwtf_m}·{@code dwt_m} 별도 마스터를
 * 본 단일 마스터로 통합하였다.</p>
 *
 * <p>PK는 {@code @GeneratedValue(GenerationType.UUID)} 로 자동 생성하며 {@code Persistable} 미구현
 * (Round 3 결정, 2026-05-03). 사용자 식별은 {@code facility_nm} 시스템 전체 UNIQUE 비즈니스 키에 의존한다.</p>
 *
 * <p>도메인 룰: AI 운전 모드 평가·제어 명령 발행 등 자식 종류별 도메인 룰이 다른 시나리오에서는
 * 부모 다형성 조회 시 반드시 {@code facility_type_cd} 필터를 명시한다.
 * 1차 정의: {@code .claude/rules/entity-patterns.md} §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰 —
 * {@code facility_type_cd} 필터 강제.</p>
 *
 * <p>참조: {@code docs/plan/20260503/마스터도메인설계/PLAN1.md} §도메인 모델, §도메인 룰.</p>
 */
@Entity
@Table(
        name = "facility_m",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_facility_m_facility_nm",
                columnNames = "facility_nm"
        )
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
        name = "facility_type_cd",
        discriminatorType = DiscriminatorType.STRING,
        length = 20
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Facility extends BaseEntity {

    /** 시설 ID — UUID 자동 생성 PK (DOM_ID_36). */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "facility_id", nullable = false, length = 36)
    private String facilityId;

    /** 시설명 — 시스템 전체 UNIQUE (DOM_NAME_100, 사용자 식별 비즈니스 키). */
    @Column(name = "facility_nm", nullable = false, length = 100)
    private String facilityNm;

    /**
     * 시설 유형 코드 — DiscriminatorColumn (PWTF·DWT·RSV).
     * 영속 컨텍스트가 자동 관리하므로 {@code insertable=false, updatable=false}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type_cd", nullable = false, length = 20, insertable = false, updatable = false)
    private FacilityType facilityType;

    /** 상위 시설 ID — self-FK (NULL 허용, 재귀 깊이 무제한). */
    @Column(name = "parent_facility_id", length = 36)
    private String parentFacilityId;

    /** 표시 순서 — 화면 표시 정렬 순번. */
    @Column(name = "disp_ord", nullable = false)
    private Integer dispOrd;

    /** 주요 시설 여부 (Y/N, DOM_YN). */
    @Enumerated(EnumType.STRING)
    @Column(name = "main_yn", nullable = false, length = 1)
    private YnType mainYn;

    /** 사용 여부 (Y/N, DOM_YN). */
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;

    /**
     * 자식 클래스가 신규 시설을 생성할 때 호출하는 protected 생성자.
     * 외부에서 직접 호출하지 않으며, 각 자식의 정적 팩토리에서 사용한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID (NULL 허용)
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     * @param useYn             사용 여부
     */
    protected Facility(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn,
            YnType useYn) {
        this.facilityNm = facilityNm;
        this.parentFacilityId = parentFacilityId;
        this.dispOrd = dispOrd;
        this.mainYn = mainYn;
        this.useYn = useYn;
    }

    /**
     * 시설 기본 정보를 변경한다. null 인자는 기존 값을 유지한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     */
    public void changeInfo(String facilityNm, String parentFacilityId, Integer dispOrd, YnType mainYn) {
        if (facilityNm != null) {
            this.facilityNm = facilityNm;
        }
        if (parentFacilityId != null) {
            this.parentFacilityId = parentFacilityId;
        }
        if (dispOrd != null) {
            this.dispOrd = dispOrd;
        }
        if (mainYn != null) {
            this.mainYn = mainYn;
        }
    }

    /** 시설을 비활성화(논리 삭제) 한다. */
    public void deactivate() {
        this.useYn = YnType.N;
    }
}
