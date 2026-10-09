package com.mo.swtp.opt.domain;

import com.mo.swtp.common.domain.DomainEventEntity;
import com.mo.swtp.opt.event.PeakTargetChangedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 전력피크 목표값 엔티티 (시스템 전역 단일 행).
 *
 * <p>정수장(테넌트) 전체가 공유하는 단일 목표 피크 전력값(kW) 을 보존한다. PK 는 고정 코드값
 * {@code 'PEAK_TARGET'} ({@link #PEAK_TARGET_CD}) 이며 DB {@code CHECK} 제약으로 2행 생성이 차단된다
 * (전력피크분석-1번섹션 ANALYZE1 + PLAN1, 2026-06-04).</p>
 *
 * <p>외부 할당 PK 패턴 — {@code @GeneratedValue} 미사용, {@link Persistable} 구현으로 {@code getId()}
 * override, {@code isNew()} 는 {@link com.mo.swtp.common.domain.BaseEntity} 의 {@code newEntity}
 * 플래그에 위임. 단 시드 1행이 DDL INSERT 로 항상 존재하므로 JPA 영속 경로 (persist) 는 미경유 —
 * {@code create} 정적 팩토리는 생성하지 않고 {@link #change} 변경 메서드만 보유한다 (저장 = 항상 UPDATE).</p>
 *
 * <p>{@link DomainEventEntity} 상속 — {@link #change} 내부에서 {@link #registerEvent} 로
 * {@link PeakTargetChangedEvent} 를 축적한다. {@code PeakTargetEventPublisher} 가
 * {@code publishAndClear} 로 발행하며, AFTER_COMMIT Listener 에서 SSE 전파한다.</p>
 */
@Entity
@Table(name = "opt_peak_target_p")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PeakTarget extends DomainEventEntity implements Persistable<String> {

    /** 고정 코드값 PK — 단일 전역 행 강제 (DDL CHECK 제약과 정합). */
    public static final String PEAK_TARGET_CD = "PEAK_TARGET";

    @Id
    @Column(name = "peak_cd", nullable = false, length = 20)
    private String peakCd;

    /** 목표 피크 전력값 (kW) — 0 = 미설정 sentinel (운전원 최초 저장 전). */
    @Column(name = "target_peak_elpwr", nullable = false)
    private BigDecimal targetPeakElpwr;

    /**
     * {@inheritDoc} — PK 를 반환한다. 시드 행이 DB 에 항상 존재하므로 실제 조회 경로는 {@code @PostLoad}
     * 로 {@code newEntity=false} 전환되며, {@code save()} 는 {@code merge}(UPDATE) 경로를 따른다.
     */
    @Override
    public String getId() {
        return peakCd;
    }

    /**
     * 목표 피크 전력값을 변경한다.
     *
     * <p>{@link PeakTargetChangedEvent} 1건을 축적한다 — {@code PeakTargetEventPublisher} 가
     * 트랜잭션 내 마지막 단계에서 발행 + 클리어하고, AFTER_COMMIT Listener 가 SSE 전파한다.
     * {@code updtDtm} 은 {@code AuditingEntityListener} 가 flush 시점에 주입하므로 이벤트 페이로드의
     * 일시는 변경 요청 시각 ({@code now}) 을 인자로 받아 채운다.</p>
     *
     * @param targetPeakElpwr 새 목표 피크 전력값 (kW)
     * @param updtDtm         변경 일시 (이벤트 페이로드용)
     */
    public void change(BigDecimal targetPeakElpwr, LocalDateTime updtDtm) {
        this.targetPeakElpwr = targetPeakElpwr;
        registerEvent(new PeakTargetChangedEvent(targetPeakElpwr, updtDtm));
    }
}
