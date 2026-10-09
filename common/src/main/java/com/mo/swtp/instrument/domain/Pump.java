package com.mo.swtp.instrument.domain;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.instrument.exception.PumpErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 송수펌프 자식 엔티티 — JPA JOINED 자식 ({@code equip_type_cd = 'PUMP'}).
 *
 * <p>자식 PK는 부모 {@link Instrument#getInstrumentId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다.</p>
 *
 * <p>송수펌프제어분석 ANALYZE1 (2026-05-08) 도입 자식 전용 컬럼 2건 + 펌프조작유형 ANALYZE1 (2026-05-12) 도입 1건
 * + pump_drive_type ANALYZE1 (2026-05-20) 도입 1건:</p>
 * <ul>
 *   <li>{@link #ratedHead} ({@code rated_head}, DOM_QTY_15_4 NOT NULL) — 정격 양정 m (제조사 명판값, AI 예측 모델 정규화 인자)</li>
 *   <li>{@link #ratedFlwrt} ({@code rated_flwrt}, DOM_QTY_15_4 NOT NULL) — 정격 유량 m³/h (제조사 명판값, 동일 사유)</li>
 *   <li>{@link #oprtngType} ({@code oprtng_type_cd}, DOM_CODE_20 NOT NULL) — 펌프 조작유형 ({@link PumpOprtngType} 매핑, 펌프의 물리적 설계값)</li>
 *   <li>{@link #driveType} ({@code drive_type_cd}, DOM_CODE_20 NOT NULL) — 펌프 구동 방식 ({@link PumpDriveType} 매핑, 펌프의 물리적 설계값)</li>
 * </ul>
 *
 * <p>NOT NULL 컬럼 4건 ({@code ratedHead}·{@code ratedFlwrt}·{@code oprtngType}·{@code driveType}) 은
 * {@code Objects.requireNonNull} 사전 검증한다.</p>
 *
 * <p><strong>도메인 안전 조합 검증</strong> — {@link #driveType} 과 {@link #oprtngType} 은 의미 직교축이나
 * 4 조합 중 {@code RATED_DRIVE + AUTO_CAPABLE} 만 물리 제약상 무효 (정격 펌프 가변속 불가 → AI 자동 제어 명령
 * 송신 불능). {@link #create} / {@link #changePumpSelfColumns} 진입 시 {@link PumpErrorCode#INVALID_PUMP_DRIVE_OPRTNG_COMBINATION}
 * 으로 차단한다. {@code .claude/rules/ot-integration.md §5 ⚠️ 절대 금지} (검증 건너뛴 채 재시도 금지) 직결 —
 * 미차단 시 OT 안전 위협 (pump_drive_type ANALYZE1 안건 5, 2026-05-20).</p>
 *
 * <p>태그 식별명 컬럼 ({@code pump_m} 측) 은 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 8·9 결정으로 폐기되었다 —
 * 태그 마스터의 {@code instrument_id} FK 가 (계측기 ↔ 태그) 연관의 SSOT 이므로 역방향 중복 컬럼 보유는
 * {@code entity-patterns.md §FK 보유 측 SSOT — 역방향 중복 컬럼 금지} 위반.
 * DDL {@code V8_6} 마이그레이션이 컬럼 DROP 처리.</p>
 *
 * <p>마스터도메인설계 ANALYZE1 Round 2 (2026-05-02) 결정으로 종전 {@code com.mo.swtp.pump.domain.Pump}
 * 별도 마스터에서 본 위치 ({@code com.mo.swtp.instrument.domain.Pump}) 로 이관되었다.</p>
 */
@Entity
@Table(name = "pump_m")
@DiscriminatorValue("PUMP")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pump extends Instrument {

    /** 정격 양정 (m, DOM_QTY_15_4) — 제조사 명판값, AI 예측 모델 정규화 인자. */
    @Column(name = "rated_head", nullable = false, precision = 15, scale = 4)
    private BigDecimal ratedHead;

    /** 정격 유량 (m³/h, DOM_QTY_15_4) — 제조사 명판값, AI 예측 모델 정규화 인자. */
    @Column(name = "rated_flwrt", nullable = false, precision = 15, scale = 4)
    private BigDecimal ratedFlwrt;

    /** 펌프 조작유형 (DOM_CODE_20, NOT NULL) — 펌프의 물리적 설계값, AUTO_CAPABLE/SEMI_AUTO_CAPABLE 상호 배타. */
    @Enumerated(EnumType.STRING)
    @Column(name = "oprtng_type_cd", nullable = false, length = 20)
    private PumpOprtngType oprtngType;

    /** 펌프 구동 방식 (DOM_CODE_20, NOT NULL) — 펌프의 물리적 설계값, INVERTER_DRIVE/RATED_DRIVE 상호 배타. */
    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type_cd", nullable = false, length = 20)
    private PumpDriveType driveType;

    /**
     * 신규 송수펌프를 생성한다. NOT NULL 컬럼 4건 ({@code ratedHead}·{@code ratedFlwrt}·{@code oprtngType}·{@code driveType}) 은
     * {@link Objects#requireNonNull} 로 사전 검증하며, {@code driveType + oprtngType} 조합 유효성을 검증한다.
     *
     * @param instrumentNm  계측기명
     * @param facility      소속 시설
     * @param dispOrd       표시 순서
     * @param ratedHead     정격 양정 m (NOT NULL)
     * @param ratedFlwrt    정격 유량 m³/h (NOT NULL)
     * @param oprtngType    펌프 조작유형 (NOT NULL — 펌프의 물리적 설계값)
     * @param driveType     펌프 구동 방식 (NOT NULL — 펌프의 물리적 설계값)
     * @return 생성된 펌프 ({@code use_yn = Y})
     * @throws RestApiException {@link PumpErrorCode#INVALID_PUMP_DRIVE_OPRTNG_COMBINATION} —
     *         {@code RATED_DRIVE + AUTO_CAPABLE} 무효 조합 (정격 펌프 가변속 불가)
     */
    public static Pump create(
            String instrumentNm,
            Facility facility,
            Integer dispOrd,
            BigDecimal ratedHead,
            BigDecimal ratedFlwrt,
            PumpOprtngType oprtngType,
            PumpDriveType driveType) {
        Objects.requireNonNull(ratedHead, "ratedHead must not be null");
        Objects.requireNonNull(ratedFlwrt, "ratedFlwrt must not be null");
        Objects.requireNonNull(oprtngType, "oprtngType must not be null");
        Objects.requireNonNull(driveType, "driveType must not be null");
        validateDriveOprtngCombination(driveType, oprtngType);
        return new Pump(instrumentNm, facility, dispOrd, ratedHead, ratedFlwrt, oprtngType, driveType);
    }

    private Pump(
            String instrumentNm,
            Facility facility,
            Integer dispOrd,
            BigDecimal ratedHead,
            BigDecimal ratedFlwrt,
            PumpOprtngType oprtngType,
            PumpDriveType driveType) {
        super(instrumentNm, facility, dispOrd, YnType.Y);
        this.ratedHead = ratedHead;
        this.ratedFlwrt = ratedFlwrt;
        this.oprtngType = oprtngType;
        this.driveType = driveType;
    }

    /**
     * 송수펌프 자체 컬럼을 변경한다. null 인자는 기존 값을 유지한다 ({@link Instrument#changeInfo} 동일 패턴).
     *
     * <p>NOT NULL 컬럼 4건 ({@code ratedHead}·{@code ratedFlwrt}·{@code oprtngType}·{@code driveType}) 은 null 입력 시
     * 기존 값을 유지하며 폐기 (의도된 변경만 적용). 부모 공통 4필드 (instrumentNm·facility·dispOrd) 는
     * {@link Instrument#changeInfo} 호출로 처리한다 — 본 메서드는 자식 전용 컬럼 책임만 가진다.</p>
     *
     * <p><strong>종료 직전 조합 검증은 입력 인자 조합과 무관하게 항상 실행</strong> — 모든 인자 null 입력이어도
     * 종료 직전 {@code (this.driveType, this.oprtngType)} 조합 검증 생략 금지. 근거: 마이그레이션 백필 직후
     * ({@code RATED_DRIVE} 일괄 적용) {@code oprtngType = AUTO_CAPABLE} 인 기존 행 + 백필 {@code driveType =
     * RATED_DRIVE} 무효 조합이 DB 에 잠재. 운영자 수동 갱신 전 기간에 무효 행 존재 가능. 이 행에 대한 모든
     * 인자 null 호출이 검증을 우회하면 {@code .claude/rules/ot-integration.md §5 ⚠️ 절대 금지} 직결
     * (wtp-domain-expert PLAN 검토 블로커 해소, 2026-05-20).</p>
     *
     * <p>{@code tagNm} 인자는 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 8·9 결정으로 컬럼 폐기되어
     * 본 메서드 시그니처에서 제외 ({@code tag_m.instrument_id} FK SSOT).</p>
     *
     * @param ratedHead   변경할 정격 양정 m (NULL 시 기존 값 유지)
     * @param ratedFlwrt  변경할 정격 유량 m³/h (NULL 시 기존 값 유지)
     * @param oprtngType  변경할 펌프 조작유형 (NULL 시 기존 값 유지)
     * @param driveType   변경할 펌프 구동 방식 (NULL 시 기존 값 유지)
     * @throws RestApiException {@link PumpErrorCode#INVALID_PUMP_DRIVE_OPRTNG_COMBINATION} —
     *         갱신 결과값 {@code (this.driveType, this.oprtngType)} 이 {@code RATED_DRIVE + AUTO_CAPABLE} 인 경우
     */
    public void changePumpSelfColumns(
            BigDecimal ratedHead, BigDecimal ratedFlwrt,
            PumpOprtngType oprtngType, PumpDriveType driveType) {
        if (ratedHead != null) {
            this.ratedHead = ratedHead;
        }
        if (ratedFlwrt != null) {
            this.ratedFlwrt = ratedFlwrt;
        }
        if (oprtngType != null) {
            this.oprtngType = oprtngType;
        }
        if (driveType != null) {
            this.driveType = driveType;
        }
        validateDriveOprtngCombination(this.driveType, this.oprtngType);
    }

    /**
     * {@code driveType + oprtngType} 조합 유효성을 검증한다. 4 조합 중 {@code RATED_DRIVE + AUTO_CAPABLE}
     * 만 무효 — 정격 펌프 가변속 불가, AI 자동 제어 명령 송신 불능.
     *
     * @param driveType   펌프 구동 방식
     * @param oprtngType  펌프 조작유형
     * @throws RestApiException {@link PumpErrorCode#INVALID_PUMP_DRIVE_OPRTNG_COMBINATION} — 무효 조합
     */
    private static void validateDriveOprtngCombination(PumpDriveType driveType, PumpOprtngType oprtngType) {
        if (driveType == PumpDriveType.RATED_DRIVE && oprtngType == PumpOprtngType.AUTO_CAPABLE) {
            throw new RestApiException(PumpErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION);
        }
    }
}
