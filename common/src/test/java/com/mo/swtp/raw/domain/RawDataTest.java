package com.mo.swtp.raw.domain;

import com.mo.swtp.raw.domain.enumtype.QualityCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RawData} 엔티티의 INSERT-only immutable 검증 단위 테스트.
 *
 * <p>마스터도메인설계 사이클 2 (PLAN2) — REVIEW1 블로커 1 해소 검증.
 * INSERT-only 4 컬럼 ({@code tag_srl_no}·{@code acq_dtm}·{@code raw_val}·{@code quality_cd})
 * 의 갱신 시도가 {@code @PreUpdate} 시점에 {@link IllegalStateException} 으로 차단되는지 검증한다.
 * {@code corr_val} 만 갱신 허용 (PLAN1 §도메인 룰).</p>
 *
 * <p>JPA 컨텍스트 미사용 — {@code protected} 라이프사이클 콜백 ({@link RawData#onPrePersistSnapshot()}·
 * {@link RawData#onPreUpdateValidateImmutable()}) 을 직접 호출하여 검증한다 (같은 패키지 접근).
 * immutable 컬럼은 변경 메서드가 의도적으로 부재이므로 reflection 사용.</p>
 */
class RawDataTest {

    private static final LocalDateTime FIXED_ACQ_DTM = LocalDateTime.of(2026, 5, 6, 10, 30);
    private static final String FIXED_TAG_SRL_NO = "706-FRI-001-001";
    private static final BigDecimal FIXED_RAW_VAL = new BigDecimal("12.3456");
    private static final BigDecimal FIXED_CORR_VAL = new BigDecimal("12.5000");
    private static final QualityCode FIXED_QUALITY = QualityCode.GOOD;

    @Test
    void corrVal_갱신은_허용된다() {
        // given — 영속 상태 (스냅샷 캡처 완료) RawData
        RawData rawData = persistedRawData();

        // when — 갱신 허용 컬럼 변경
        rawData.changeCorrVal(new BigDecimal("13.0000"));

        // then — @PreUpdate 검증 통과 (예외 없음)
        assertThatCode(rawData::onPreUpdateValidateImmutable).doesNotThrowAnyException();
    }

    @Test
    void qualityCd_변경은_immutable_검증으로_차단된다() {
        // given
        RawData rawData = persistedRawData();

        // when — quality_cd 직접 변경 (변경 메서드 부재 — reflection)
        setField(rawData, "qualityCd", QualityCode.BAD);

        // then
        assertThatThrownBy(rawData::onPreUpdateValidateImmutable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quality_cd 는 INSERT-only");
    }

    @Test
    void tagSrlNo_변경은_immutable_검증으로_차단된다() {
        // given
        RawData rawData = persistedRawData();

        // when
        setField(rawData, "tagSrlNo", "999-XXX-000-000");

        // then
        assertThatThrownBy(rawData::onPreUpdateValidateImmutable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tag_srl_no 는 INSERT-only");
    }

    @Test
    void acqDtm_변경은_immutable_검증으로_차단된다() {
        // given
        RawData rawData = persistedRawData();

        // when
        setField(rawData, "acqDtm", FIXED_ACQ_DTM.plusMinutes(1));

        // then
        assertThatThrownBy(rawData::onPreUpdateValidateImmutable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acq_dtm 은 INSERT-only");
    }

    @Test
    void rawVal_변경은_immutable_검증으로_차단된다() {
        // given
        RawData rawData = persistedRawData();

        // when
        setField(rawData, "rawVal", new BigDecimal("99.9999"));

        // then
        assertThatThrownBy(rawData::onPreUpdateValidateImmutable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raw_val 은 INSERT-only");
    }

    /**
     * INSERT 직후 영속 상태로 만든 RawData — 스냅샷 캡처 완료 상태.
     */
    private RawData persistedRawData() {
        RawData rawData = RawData.create(
                FIXED_ACQ_DTM, FIXED_TAG_SRL_NO, FIXED_RAW_VAL, FIXED_CORR_VAL, FIXED_QUALITY);
        rawData.onPrePersistSnapshot();
        return rawData;
    }

    /**
     * private 필드 직접 변경 — RawData 는 immutable 컬럼 변경 메서드를 의도적으로 제공하지 않으므로
     * 검증 테스트 목적으로 reflection 사용.
     */
    private static void setField(RawData target, String fieldName, Object newValue) {
        try {
            Field field = RawData.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, newValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("필드 reflection 실패: " + fieldName, e);
        }
    }
}
