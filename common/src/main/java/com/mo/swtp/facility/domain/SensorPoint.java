package com.mo.swtp.facility.domain;

import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관로 계측 분기점 자식 엔티티 — JPA JOINED 자식 ({@code facility_type_cd = 'POINT'}).
 *
 * <p>FR-PMP-002 (운전현황 분석) 의 분기 지점 측정·예측 (BRANCH_MEAS_PREDC_DATA) 응답에서 사용되는
 * 관로 계측 분기점을 표현한다. 자식 PK는 부모 {@link Facility#getFacilityId()} 와 동일하며 JPA JOINED 표준 동작으로
 * 자동 상속된다 ({@code @Id} 재선언 금지).</p>
 *
 * <p>본 단계에서는 자식 전용 컬럼 0건 skeleton 만 정의하며, 자식 전용 컬럼 (예: 분기 위치·관경 등) 은
 * 추후 요구사항명세서 기반 차기 PLAN 에서 결정한다 (송수펌프제어_운전현황분석 ANALYZE1 §신규 엔티티/DB 컬럼).</p>
 */
@Entity
@Table(name = "point_m")
@DiscriminatorValue("POINT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorPoint extends Facility {

    /**
     * 신규 관로 계측 분기점을 생성한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID (NULL 허용)
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     * @return 생성된 분기점 ({@code use_yn = Y})
     */
    public static SensorPoint create(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        return new SensorPoint(facilityNm, parentFacilityId, dispOrd, mainYn);
    }

    private SensorPoint(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn) {
        super(facilityNm, parentFacilityId, dispOrd, mainYn, YnType.Y);
    }
}
