package com.mo.swtp.instrument.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link PumpCtrlHistory} 의 복합키 ID 클래스.
 *
 * <p>{@code (pump_ctrl_id, ctrl_dtm)} 복합 PK 를 식별한다. {@code ctrl_dtm} 은 월 RANGE 파티션 키이기도
 * 하므로 PK 에 포함되어 파티션 프루닝과 일관성을 보장한다 ({@code db/partitioning-and-retention.md §1}).</p>
 *
 * <p>{@link Serializable} 구현은 JPA {@code @IdClass} 사용 시 필수 요건이다. {@code TagPredictionId}·
 * {@code RawDataId} 선례 동일 패턴 — {@code @SequenceGenerator(allocationSize=100)} 자동 채번 호환을 위해
 * {@code @EmbeddedId} 대신 {@code @IdClass} 채택 (제어이력 재도입 TASK1 Phase 1).</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PumpCtrlHistoryId implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 펌프 제어 이력 시퀀스 ID. */
    private Long pumpCtrlId;

    /** 제어 일시 — 월 RANGE 파티션 키. */
    private LocalDateTime ctrlDtm;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PumpCtrlHistoryId other)) {
            return false;
        }
        return Objects.equals(pumpCtrlId, other.pumpCtrlId)
                && Objects.equals(ctrlDtm, other.ctrlDtm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pumpCtrlId, ctrlDtm);
    }
}
