package com.mo.swtp.facility.domain;

import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저수지 자식 엔티티 — JPA JOINED 자식 ({@code facility_type_cd = 'RSV'}).
 *
 * <p>자식 PK는 부모 {@link Facility#getFacilityId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다.</p>
 *
 * <p>본 PLAN 단계에서는 자식 전용 컬럼 0건 skeleton 만 정의한다.</p>
 */
@Entity
@Table(name = "rsv_m")
@DiscriminatorValue("RSV")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservoir extends Facility {

    /**
     * 신규 저수지를 생성한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID (NULL 허용)
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     * @return 생성된 저수지 ({@code use_yn = Y})
     */
    public static Reservoir create(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        return new Reservoir(facilityNm, parentFacilityId, dispOrd, mainYn);
    }

    private Reservoir(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        super(facilityNm, parentFacilityId, dispOrd, mainYn, YnType.Y);
    }
}
