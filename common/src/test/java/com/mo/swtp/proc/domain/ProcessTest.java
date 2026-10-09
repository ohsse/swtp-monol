package com.mo.swtp.proc.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mo.swtp.common.enumtype.YnType;
import org.junit.jupiter.api.Test;

/**
 * {@link Process} 엔티티 단위 테스트 — proc_id 정규식 검증.
 *
 * <p>Process.create 정적 팩토리의 형식 검증 (^[A-Z][A-Z0-9_]*$) 동작을 확인한다.</p>
 */
class ProcessTest {

    @Test
    void 대문자_시작_언더스코어_포함_procId는_생성에_성공한다() {
        Process process = Process.create("PUMP_CONTROL", "송수펌프제어", 1);

        assertThat(process.getProcId()).isEqualTo("PUMP_CONTROL");
        assertThat(process.getProcNm()).isEqualTo("송수펌프제어");
        assertThat(process.getDispOrd()).isEqualTo(1);
        assertThat(process.getUseYn()).isEqualTo(YnType.Y);
    }

    @Test
    void 소문자가_포함된_procId는_IllegalArgumentException을_던진다() {
        assertThatThrownBy(() -> Process.create("pump_control", "송수펌프제어", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid procId format");
    }

    @Test
    void 숫자로_시작하는_procId는_IllegalArgumentException을_던진다() {
        assertThatThrownBy(() -> Process.create("1ABC", "테스트", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_procId는_IllegalArgumentException을_던진다() {
        assertThatThrownBy(() -> Process.create(null, "테스트", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivate는_useYn을_N으로_변경한다() {
        Process process = Process.create("WTR_TREAT", "정수공정", 2);

        process.deactivate();

        assertThat(process.getUseYn()).isEqualTo(YnType.N);
    }

    @Test
    void changeInfo는_null_필드를_유지한다() {
        Process process = Process.create("PUMP_CONTROL", "송수펌프제어", 1);

        process.changeInfo("송수펌프제어 v2", null);

        assertThat(process.getProcNm()).isEqualTo("송수펌프제어 v2");
        assertThat(process.getDispOrd()).isEqualTo(1);
    }
}
