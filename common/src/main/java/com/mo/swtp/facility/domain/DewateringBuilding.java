package com.mo.swtp.facility.domain;

import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탈수기동 자식 엔티티 — JPA JOINED 자식 ({@code facility_type_cd = 'DEWB'}).
 *
 * <p>자식 PK는 부모 {@link Facility#getFacilityId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다
 * ({@code @Id} 재선언 금지).</p>
 *
 * <p>시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설({@code FacilityGroup.OPERATION}).
 * 본 사이클은 자식 전용 컬럼 0건 skeleton 만 정의하며, 자식 전용 컬럼은 추후 요구사항명세서 기반
 * 차기 PLAN 에서 결정한다.</p>
 */
@Entity
@Table(name = "dewb_m")
@DiscriminatorValue("DEWB")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DewateringBuilding extends Facility {

    /**
     * 신규 탈수기동을 생성한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID (NULL 허용)
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     * @return 생성된 탈수기동 ({@code use_yn = Y})
     */
    public static DewateringBuilding create(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        return new DewateringBuilding(facilityNm, parentFacilityId, dispOrd, mainYn);
    }

    private DewateringBuilding(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        super(facilityNm, parentFacilityId, dispOrd, mainYn, YnType.Y);
    }
}
