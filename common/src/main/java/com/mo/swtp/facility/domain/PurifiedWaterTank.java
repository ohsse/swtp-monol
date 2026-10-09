package com.mo.swtp.facility.domain;

import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정수조 자식 엔티티 — JPA JOINED 자식 ({@code facility_type_cd = 'PWTF'}).
 *
 * <p>자식 PK는 부모 {@link Facility#getFacilityId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다
 * ({@code @Id} 재선언 금지).</p>
 *
 * <p>본 PLAN 단계에서는 자식 전용 컬럼 0건 skeleton 만 정의하며, 자식 전용 컬럼 (예: 용량·재질 등) 은
 * 추후 요구사항명세서 기반 차기 PLAN 에서 결정한다 (사용자 명시 결정 2026-05-03).</p>
 */
@Entity
@Table(name = "pwtf_m")
@DiscriminatorValue("PWTF")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurifiedWaterTank extends Facility {

    /**
     * 신규 정수조를 생성한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID (NULL 허용)
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     * @return 생성된 정수조 ({@code use_yn = Y})
     */
    public static PurifiedWaterTank create(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        return new PurifiedWaterTank(facilityNm, parentFacilityId, dispOrd, mainYn);
    }

    private PurifiedWaterTank(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        super(facilityNm, parentFacilityId, dispOrd, mainYn, YnType.Y);
    }
}
