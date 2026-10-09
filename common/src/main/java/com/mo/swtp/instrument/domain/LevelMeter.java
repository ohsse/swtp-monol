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
 * 수위계 자식 엔티티 — JPA JOINED 자식 ({@code equip_type_cd = 'LVMTR'}).
 *
 * <p>자식 PK는 부모 {@link Instrument#getInstrumentId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다.</p>
 *
 * <p>본 PLAN 단계에서는 자식 전용 컬럼 0건 skeleton 만 정의한다.</p>
 */
@Entity
@Table(name = "lvmtr_m")
@DiscriminatorValue("LVMTR")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelMeter extends Instrument {

    /**
     * 신규 수위계를 생성한다.
     *
     * @param instrumentNm  계측기명
     * @param facility      소속 시설
     * @param dispOrd       표시 순서
     * @return 생성된 수위계 ({@code use_yn = Y})
     */
    public static LevelMeter create(String instrumentNm, Facility facility, Integer dispOrd) {
        return new LevelMeter(instrumentNm, facility, dispOrd);
    }

    private LevelMeter(String instrumentNm, Facility facility, Integer dispOrd) {
        super(instrumentNm, facility, dispOrd, YnType.Y);
    }
}
