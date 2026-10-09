package com.mo.swtp.instrument.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import com.mo.swtp.instrument.exception.PumpErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@link Pump} 의 {@code driveType + oprtngType} 4 조합 유효성 단위 테스트.
 *
 * <p>pump_drive_type ANALYZE1 안건 5 (2026-05-20) — 4 조합 중 {@code RATED_DRIVE + AUTO_CAPABLE}
 * 만 물리 제약상 무효 (정격 펌프 가변속 불가 → AI 자동 제어 명령 송신 불능). 도메인 안전 직결
 * ({@code .claude/rules/ot-integration.md §5 ⚠️ 절대 금지} — 검증 건너뛴 채 재시도 금지).</p>
 *
 * <p>{@link Pump#create} + {@link Pump#changePumpSelfColumns} 양 경로 모두 검증한다.
 * 또한 마이그레이션 백필 직후 무효 조합 행이 잠재할 수 있으므로
 * {@code changePumpSelfColumns(null, null, null, null)} 호출 시에도 종료 직전 조합 검증이
 * 생략되지 않음을 검증한다 (wtp-domain-expert PLAN 검토 블로커 해소).</p>
 */
class PumpDriveOprtngCombinationTest {

    private static final BigDecimal RATED_HEAD = new BigDecimal("65.0000");
    private static final BigDecimal RATED_FLWRT = new BigDecimal("250.0000");

    private Pump newPump(PumpOprtngType oprtngType, PumpDriveType driveType) {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-001", null, 1, YnType.Y);
        return Pump.create("펌프-001", pwtf, 1, RATED_HEAD, RATED_FLWRT, oprtngType, driveType);
    }

    // ========== create() 4 조합 ==========

    @Test
    void create_INVERTER_DRIVE_AUTO_CAPABLE_은_유효() {
        assertThatCode(() -> newPump(PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE))
                .doesNotThrowAnyException();
    }

    @Test
    void create_INVERTER_DRIVE_SEMI_AUTO_CAPABLE_은_유효() {
        assertThatCode(() -> newPump(PumpOprtngType.SEMI_AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE))
                .doesNotThrowAnyException();
    }

    @Test
    void create_RATED_DRIVE_SEMI_AUTO_CAPABLE_은_유효() {
        assertThatCode(() -> newPump(PumpOprtngType.SEMI_AUTO_CAPABLE, PumpDriveType.RATED_DRIVE))
                .doesNotThrowAnyException();
    }

    @Test
    void create_RATED_DRIVE_AUTO_CAPABLE_은_차단() {
        assertThatThrownBy(() -> newPump(PumpOprtngType.AUTO_CAPABLE, PumpDriveType.RATED_DRIVE))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PumpErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION);
    }

    // ========== changePumpSelfColumns() — 정상 조합으로 시작 후 부분 갱신 ==========

    @Test
    void changePumpSelfColumns_INVERTER_AUTO_시작_RATED_갱신_시_차단() {
        Pump pump = newPump(PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);

        assertThatThrownBy(() -> pump.changePumpSelfColumns(
                null, null, null, PumpDriveType.RATED_DRIVE))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PumpErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION);
        // 갱신 실패 시 상태는 변화 없음 — JPA dirty checking 트랜잭션 롤백 의도
        // 단, 본 단위 테스트는 트랜잭션 없이 메서드만 호출하므로 부분 갱신된 상태로 남을 수 있음.
        // Service 계층에서 @Transactional 으로 롤백 보장.
    }

    @Test
    void changePumpSelfColumns_RATED_SEMI_AUTO_시작_AUTO_CAPABLE_갱신_시_차단() {
        Pump pump = newPump(PumpOprtngType.SEMI_AUTO_CAPABLE, PumpDriveType.RATED_DRIVE);

        assertThatThrownBy(() -> pump.changePumpSelfColumns(
                null, null, PumpOprtngType.AUTO_CAPABLE, null))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PumpErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION);
    }

    @Test
    void changePumpSelfColumns_INVERTER_AUTO_유지_부분_갱신_시_유효() {
        Pump pump = newPump(PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);

        assertThatCode(() -> pump.changePumpSelfColumns(
                new BigDecimal("70.0"), null, null, null))
                .doesNotThrowAnyException();
        assertThat(pump.getRatedHead()).isEqualByComparingTo("70.0");
    }

    // ========== changePumpSelfColumns() — 모든 인자 null 시도 검증 강제 ==========

    @Test
    void changePumpSelfColumns_모든_인자_null_INVERTER_AUTO_시작_은_유효_조합으로_통과() {
        Pump pump = newPump(PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);

        assertThatCode(() -> pump.changePumpSelfColumns(null, null, null, null))
                .doesNotThrowAnyException();
    }

    /**
     * 마이그레이션 백필 직후 무효 조합 행 잠재 시나리오 — reflection 으로 무효 조합 강제 주입 후
     * 모든 인자 null 호출. 종료 직전 검증이 생략되면 통과되어 OT 안전 위협이므로 차단되어야 한다.
     *
     * <p>wtp-domain-expert PLAN 검토 블로커 (2026-05-20) — 핵심 회귀 방지 케이스.</p>
     */
    @Test
    void changePumpSelfColumns_모든_인자_null_무효_조합_상태_는_차단() throws Exception {
        Pump pump = newPump(PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        // reflection 으로 driveType 만 RATED_DRIVE 로 강제 주입 (마이그레이션 백필 직후 상태 재현)
        java.lang.reflect.Field driveTypeField = Pump.class.getDeclaredField("driveType");
        driveTypeField.setAccessible(true);
        driveTypeField.set(pump, PumpDriveType.RATED_DRIVE);

        assertThatThrownBy(() -> pump.changePumpSelfColumns(null, null, null, null))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PumpErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION);
    }
}
