package com.mo.swtp.instrument.domain;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.Facility;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유량계 자식 엔티티 — JPA JOINED 자식 ({@code equip_type_cd = 'FLWMTR'}).
 *
 * <p>자식 PK는 부모 {@link Instrument#getInstrumentId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다.</p>
 *
 * <p>본 PLAN 단계에서는 자식 전용 컬럼 0건 skeleton 만 정의한다.
 * 자식 전용 컬럼 (예: {@code range_min}·{@code range_max}) 은 추후 요구사항명세서 기반 차기 PLAN 에서 결정한다.</p>
 */
@Entity
@Table(name = "flwmtr_m")
@DiscriminatorValue("FLWMTR")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlowMeter extends Instrument {

    /**
     * 신규 유량계를 생성한다.
     *
     * @param instrumentNm  계측기명
     * @param facility      소속 시설
     * @param dispOrd       표시 순서
     * @return 생성된 유량계 ({@code use_yn = Y})
     */
    public static FlowMeter create(String instrumentNm, Facility facility, Integer dispOrd) {
        return new FlowMeter(instrumentNm, facility, dispOrd);
    }

    private FlowMeter(String instrumentNm, Facility facility, Integer dispOrd) {
        super(instrumentNm, facility, dispOrd, YnType.Y);
    }
}
