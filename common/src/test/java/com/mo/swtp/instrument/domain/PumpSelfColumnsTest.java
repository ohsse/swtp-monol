package com.mo.swtp.instrument.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@link Pump} 자식 전용 컬럼 (rated_head·rated_flwrt·oprtng_type_cd·drive_type_cd) 단위 테스트.
 *
 * <p>송수펌프제어분석 ANALYZE1 (2026-05-08) 도입 2건 + 펌프조작유형 ANALYZE1 (2026-05-12) 도입 1건
 * + pump_drive_type ANALYZE1 (2026-05-20) 도입 1건 — 모두 NOT NULL 컬럼이므로
 * {@code Objects.requireNonNull} 사전 검증한다.</p>
 *
 * <p>{@code tag_nm} 컬럼은 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 8·9 결정으로 폐기 — 양방향 중복 제거
 * ({@code tag_m.instrument_id} FK SSOT). 관련 테스트도 함께 제거.</p>
 */
class PumpSelfColumnsTest {

    private static final BigDecimal RATED_HEAD = new BigDecimal("65.0000");
    private static final BigDecimal RATED_FLWRT = new BigDecimal("250.0000");

    @Test
    void create_는_rated_head_가_null_이면_NullPointerException_을_던진다() {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-001", null, 1, YnType.Y);

        assertThatThrownBy(() -> Pump.create("펌프-001", pwtf, 1,
                null, RATED_FLWRT, PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ratedHead");
    }

    @Test
    void create_는_rated_flwrt_가_null_이면_NullPointerException_을_던진다() {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-002", null, 1, YnType.Y);

        assertThatThrownBy(() -> Pump.create("펌프-002", pwtf, 2,
                RATED_HEAD, null, PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ratedFlwrt");
    }

    @Test
    void create_는_oprtng_type_이_null_이면_NullPointerException_을_던진다() {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-004", null, 1, YnType.Y);

        assertThatThrownBy(() -> Pump.create("펌프-004", pwtf, 4,
                RATED_HEAD, RATED_FLWRT, null, PumpDriveType.INVERTER_DRIVE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("oprtngType");
    }

    @Test
    void create_는_drive_type_이_null_이면_NullPointerException_을_던진다() {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-005", null, 1, YnType.Y);

        assertThatThrownBy(() -> Pump.create("펌프-005", pwtf, 5,
                RATED_HEAD, RATED_FLWRT, PumpOprtngType.AUTO_CAPABLE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("driveType");
    }
}
