package com.mo.swtp.proc.domain;

import com.mo.swtp.common.domain.DomainEventEntity;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import com.mo.swtp.proc.event.AiDrvnModeChangedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * AI 운전모드 현재 상태 엔티티 (공정/제어대상 1:1).
 *
 * <p>{@link Process} 와 1:1 매핑이며 PK 는 {@link Process#getProcId()} 와 공유한다
 * ({@link MapsId} 패턴). 모드 변경 시 UPDATE 로 처리되며 이력은 {@link AiDrvnModeHistory} 가 보존한다.</p>
 *
 * <p>도메인 룰 (송수펌프제어분석-2번섹션 ANALYZE2 안건 5, 2026-05-20):</p>
 * <ul>
 *   <li>본 엔티티는 <b>사용자 의도</b> 명세 — 시스템 상태 (강제 모드 전환) 와 분리</li>
 *   <li>변경 트랜잭션은 6단계 (FOR UPDATE → UPDATE end_dtm → UPSERT _p → INSERT _h → Publisher 위임 → commit)</li>
 *   <li>{@link DomainEventEntity} 상속 — {@link #change}/{@link #create} 메서드 내부에서
 *       {@link #registerEvent} 로 {@link AiDrvnModeChangedEvent} 를 축적한다.
 *       {@code AiDrvnModeEventPublisher} 가 {@code publishAndClear} 로 발행하며,
 *       AFTER_COMMIT Listener 에서 SSE 전파한다.</li>
 * </ul>
 *
 * <p>도입: 송수펌프제어분석-2번섹션 ANALYZE2 + PLAN2 (2026-05-20).
 * PLAN3 (2026-05-20) — {@link DomainEventEntity} 패턴 정렬 (옵션 B 폐기, REVIEW1 블로커 해소).</p>
 */
@Entity
@Table(name = "ai_drvn_mod_p")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiDrvnMode extends DomainEventEntity implements Persistable<String> {

    @Id
    @Column(name = "proc_id", nullable = false, length = 50)
    private String procId;

    /** 공정/제어대상 — 1:1, PK 공유 ({@link MapsId}) */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proc_id")
    private Process process;

    /** AI 운전모드 코드 — 사용자 의도 */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_drvn_mod_cd", nullable = false, length = 20)
    private AiDrvnModeCode aiDrvnModCd;

    /** 현재 모드 시작 일시 */
    @Column(name = "start_dtm", nullable = false)
    private LocalDateTime startDtm;

    /**
     * {@inheritDoc} — PK 를 반환한다. {@code Persistable} 구현으로 {@code save()} 가
     * {@code em.persist()} 경로를 따르도록 한다 ({@code @MapsId + LAZY} OneToOne 의
     * {@code merge()} 경로 충돌 회피).
     */
    @Override
    public String getId() {
        return procId;
    }

    /**
     * 신규 AI 운전모드 행을 생성한다 (공정/제어대상별 최초 설정).
     *
     * <p>{@link AiDrvnModeChangedEvent} 1건을 축적한다 — 최초 설정 시점도 SSE 구독자가
     * 즉시 현재 모드를 인지하도록 통지 대상 (PLAN3 §가정 결정).</p>
     *
     * @param process     공정/제어대상 엔티티 ({@link Process})
     * @param aiDrvnModCd AI 운전모드 코드
     * @param startDtm    시작 일시
     * @return 신규 AiDrvnMode 엔티티 (procId 는 process.getProcId() 자동 매핑)
     */
    public static AiDrvnMode create(Process process, AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm) {
        AiDrvnMode mode = new AiDrvnMode(process.getProcId(), process, aiDrvnModCd, startDtm);
        mode.registerEvent(new AiDrvnModeChangedEvent(process.getProcId(), aiDrvnModCd, startDtm));
        return mode;
    }

    /**
     * AI 운전모드를 변경한다.
     *
     * <p>{@link AiDrvnModeChangedEvent} 1건을 축적한다 —
     * {@code AiDrvnModeEventPublisher} 가 트랜잭션 내 마지막 단계에서 발행 + 클리어한다.</p>
     *
     * @param aiDrvnModCd 새 운전모드 코드
     * @param startDtm    새 시작 일시
     */
    public void change(AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm) {
        this.aiDrvnModCd = aiDrvnModCd;
        this.startDtm = startDtm;
        registerEvent(new AiDrvnModeChangedEvent(this.procId, aiDrvnModCd, startDtm));
    }
}
