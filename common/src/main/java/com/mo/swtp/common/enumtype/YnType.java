package com.mo.swtp.common.enumtype;

/**
 * 여부 코드 (Y/N).
 *
 * <p>DB 컬럼 suffix {@code _yn} 을 가진 {@code VARCHAR(1)} 컬럼과 1:1 매핑되는 표준 타입이다.
 * {@link jakarta.persistence.EnumType#STRING} 으로 저장하므로 enum name 과 DB 값이 완전히 일치한다.</p>
 *
 * <p>엔티티 적용 예시:</p>
 * <pre>{@code
 * @Enumerated(EnumType.STRING)
 * @Column(name = "use_yn", nullable = false, length = 1)
 * private YnType useYn;
 * }</pre>
 *
 * <p>DTO 에서는 {@code @Schema(description = "...")} 만 명시하고
 * {@code allowableValues} · {@code @Pattern} · {@code example} 은 중복 작성하지 않는다.
 * Swagger/OpenAPI 가 enum 허용값을 자동으로 노출한다.</p>
 */
public enum YnType {

    /** 활성 · 사용 (DB 값: {@code 'Y'}). */
    Y,

    /** 비활성 · 미사용 (DB 값: {@code 'N'}). */
    N;

    /**
     * 활성(Y) 여부를 반환한다.
     *
     * @return {@link #Y} 이면 {@code true}
     */
    public boolean isYes() {
        return this == Y;
    }

    /**
     * Boolean 값을 {@link YnType} 으로 변환한다.
     *
     * @param flag 참이면 {@link #Y}, 거짓이면 {@link #N}
     * @return 변환된 {@link YnType}
     */
    public static YnType of(boolean flag) {
        return flag ? Y : N;
    }
}
