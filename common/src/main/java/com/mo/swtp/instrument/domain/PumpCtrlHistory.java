package com.mo.swtp.instrument.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.instrument.domain.enumtype.ControlCommand;
import com.mo.swtp.instrument.domain.enumtype.ControlResult;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 펌프 제어 이력 엔티티 — 월 RANGE 파티션 ({@code pump_ctrl_h}).
 *
 * <p>송수펌프 제어이력 화면 2·3번 섹션 (AI 운영 현황 통계 + 제어 이력 목록) 의 데이터 소스. 조회기간 동안의
 * 펌프 제어 요청·결과·운전모드를 시계열로 보관하는 이력 테이블이다. 본 사이클은 <strong>조회 전용</strong> —
 * INSERT 경로(제어 명령 발행)는 OT 아웃바운드 재설계 사이클 결정 대기 ({@code ot-integration.md §2·§5} 보류).
 * 따라서 정적 팩토리·변경 메서드를 노출하지 않는다.</p>
 *
 * <p><strong>폐기 후 재도입</strong> — 2026-05-12 백지화로 엔티티·마이그레이션이 삭제된 고아 테이블 (dev DB 에
 * 월 파티션과 함께 물리 존재, 0행) 을 기존 컬럼 구조·물리명 그대로 코드 자산화한다 (제어이력 재도입 ANALYZE1,
 * 2026-06-04). 비즈니스 도메인 약어 {@code ctrl} 는 폐기 유지 — 물리명·컬럼명에 역사 흔적으로만 잔존.</p>
 *
 * <p>복합 PK {@code (pump_ctrl_id, ctrl_dtm)} — {@link PumpCtrlHistoryId} 식별. {@code ctrl_dtm} 은 파티션
 * 키 역할도 한다. {@code pump_ctrl_id} 는 {@code seq_pump_ctrl_id} 시퀀스 ({@code allocationSize=100}) 로
 * 자동 할당. {@code TagPrediction}({@code predc_1m_h}) 의 {@code @IdClass} + {@code @SequenceGenerator}
 * 선례 동형.</p>
 *
 * <p>{@code BaseEntity} 4 상속 — {@code updt_dtm}(갱신시간/제어완료시간) 이 의미상 갱신되는 컬럼이므로
 * immutable 이력 예외가 아니다 ({@code db/indexing-and-migration.md §4.3} "변경 추적 컬럼 존재 시 BaseEntity
 * 4 상속 허용", {@code AiDrvnModeHistory} 선례). {@code updt_dtm}·{@code updt_id} 는 AuditingEntityListener
 * 가 자동 주입한다.</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code instrument_id} 는
 * {@code instrument_m.instrument_id} 의 논리 참조이며 {@code @ManyToOne}·FK 제약이 없다. 펌프명·제어태그
 * 조인은 조회 쿼리(Querydsl)에서 {@code instrument_id} 동등 조건으로 수행한다.</p>
 *
 * <p>{@code ai_drvn_mod} 는 운전모드 (AI/AI추천/AI분석) — {@code proc} 도메인의 {@link AiDrvnModeCode} 를
 * 재사용한다. <strong>NULL 허용 = 수동 제어</strong> (DBA 2차 승인, ANALYZE1 안건 3). 섹션2 AI 운영 현황
 * 집계는 {@code ai_drvn_mod IS NOT NULL} 행만 카운트한다.</p>
 *
 * <p>참조: {@code docs/plan/20260604/송수펌프제어이력_2_3번섹션/PLAN1.md} §도메인 모델.</p>
 */
@Entity
@Table(name = "pump_ctrl_h")
@IdClass(PumpCtrlHistoryId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PumpCtrlHistory extends BaseEntity {

    /** 펌프 제어 이력 ID — {@code seq_pump_ctrl_id} 시퀀스 (DOM_SEQ_BIGINT, 복합 PK 1/2). */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqPumpCtrlIdGenerator")
    @SequenceGenerator(
            name = "seqPumpCtrlIdGenerator",
            sequenceName = "seq_pump_ctrl_id",
            allocationSize = 100
    )
    @Column(name = "pump_ctrl_id", nullable = false)
    private Long pumpCtrlId;

    /** 제어 일시 (제어요청시간) — 파티션 키 + 복합 PK 2/2. */
    @Id
    @Column(name = "ctrl_dtm", nullable = false)
    private LocalDateTime ctrlDtm;

    /** 제어대상 계측기 ID — {@code instrument_m.instrument_id} 논리 참조 (시계열 → 마스터 FK 금지). */
    @Column(name = "instrument_id", nullable = false, length = 36)
    private String instrumentId;

    /** 제어요청구분 (가동/중지) — {@link ControlCommand} 매핑. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ctrl_div", nullable = false, length = 20)
    private ControlCommand ctrlDiv;

    /** 제어결과 (제어완료/제어취소) — {@link ControlResult} 매핑. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ctrl_rslt", nullable = false, length = 20)
    private ControlResult ctrlRslt;

    /** 운전모드 (AI/AI추천/AI분석) — {@link AiDrvnModeCode} 재사용. NULL = 수동 제어. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_drvn_mod", length = 20)
    private AiDrvnModeCode aiDrvnMod;
}
