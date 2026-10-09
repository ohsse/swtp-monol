package com.mo.swtp.opt.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link TagPrediction} 의 복합키 ID 클래스.
 *
 * <p>{@code (predc_id, predc_dtm)} 복합 PK 를 식별한다. {@code predc_dtm} 은 월 RANGE 파티션 키이기도 하므로
 * PK 에 포함되어 파티션 프루닝과 일관성을 보장한다 ({@code db/partitioning-and-retention.md §1}).</p>
 *
 * <p>{@link Serializable} 구현은 JPA {@code @IdClass} 사용 시 필수 요건이다. {@code RawDataId} 선례 동일 패턴 —
 * {@code @SequenceGenerator(allocationSize=100)} 자동 채번 호환을 위해 {@code @EmbeddedId} 대신 {@code @IdClass}
 * 채택 (송수펌프제어분석-7번섹션 TASK1 Phase 2 구현 시 RawData 선례 정합).</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TagPredictionId implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 예측 결과 시퀀스 ID. */
    private Long predcId;

    /** 예측 대상 일시 — 월 RANGE 파티션 키. */
    private LocalDateTime predcDtm;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TagPredictionId other)) {
            return false;
        }
        return Objects.equals(predcId, other.predcId)
                && Objects.equals(predcDtm, other.predcDtm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predcId, predcDtm);
    }
}
