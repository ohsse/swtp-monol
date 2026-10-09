package com.mo.swtp.proc.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 운전모드 변경 이력 엔티티.
 *
 * <p>BaseEntity 4 상속 + {@code end_dtm} UPDATE 허용 구조 — 모드 변경 시 직전 활성 행의
 * {@code end_dtm} 갱신 + 신규 행 INSERT 의 2단계 처리. INSERT-only immutable 컬럼
 * ({@code ai_drvn_mod_id}·{@code proc_id}·{@code ai_drvn_mod_cd}·{@code start_dtm}) 의 immutable
 * 보장은 애플리케이션 레벨이며 본 엔티티는 별도 변경 메서드를 노출하지 않는다
 * ({@link #close(LocalDateTime)} 만 허용).</p>
 *
 * <p>부분 UNIQUE INDEX {@code uk_ai_drvn_mod_h_proc_active (proc_id) WHERE end_dtm IS NULL}
 * 가 활성 행 1건을 강제한다 (동시성 안전망 — 트랜잭션 직렬화 외 두 번째 INSERT 시 DB 제약 위반).</p>
 *
 * <p>도입: 송수펌프제어분석-2번섹션 ANALYZE2 + PLAN2 (2026-05-20).
 * 보존 5년 ({@code db/partitioning-and-retention.md §2}). 단일 테이블 + 복합 인덱스 (파티셔닝 불요).</p>
 */
@Entity
@Table(name = "ai_drvn_mod_h")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiDrvnModeHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ai_drvn_mod_h_id")
    @SequenceGenerator(
            name = "seq_ai_drvn_mod_h_id",
            sequenceName = "seq_ai_drvn_mod_h_id",
            allocationSize = 100
    )
    @Column(name = "ai_drvn_mod_id", nullable = false)
    private Long aiDrvnModId;

    /** 공정/제어대상 ID — proc_m 논리 참조 (FK 정의는 DDL) */
    @Column(name = "proc_id", nullable = false, length = 50)
    private String procId;

    /** AI 운전모드 코드 */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_drvn_mod_cd", nullable = false, length = 20)
    private AiDrvnModeCode aiDrvnModCd;

    /** 모드 시작 일시 (INSERT-only immutable) */
    @Column(name = "start_dtm", nullable = false)
    private LocalDateTime startDtm;

    /** 모드 종료 일시 (NULL = 현재 활성, 변경 시점에 UPDATE) */
    @Column(name = "end_dtm")
    private LocalDateTime endDtm;

    /**
     * 신규 이력 행을 생성한다 ({@code end_dtm} NULL 활성 상태).
     *
     * @param procId      공정/제어대상 ID
     * @param aiDrvnModCd 운전모드 코드
     * @param startDtm    시작 일시
     * @return 신규 이력 (저장 시 SEQUENCE 로 aiDrvnModId 자동 할당)
     */
    public static AiDrvnModeHistory create(String procId, AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm) {
        return new AiDrvnModeHistory(null, procId, aiDrvnModCd, startDtm, null);
    }

    /**
     * 활성 행을 종료 처리한다 ({@code end_dtm} 갱신).
     *
     * @param endDtm 종료 일시
     */
    public void close(LocalDateTime endDtm) {
        this.endDtm = endDtm;
    }
}
