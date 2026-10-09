package com.mo.swtp.raw.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link RawData} 의 복합키 ID 클래스.
 *
 * <p>{@code (rawdata_id, acq_dtm)} 복합 PK 를 식별한다. {@code acq_dtm} 은 월 RANGE 파티션 키이기도 하므로
 * PK 에 포함되어 파티션 프루닝과 일관성을 보장한다 ({@code db/partitioning-and-retention.md §1}).</p>
 *
 * <p>{@link Serializable} 구현은 JPA {@code @IdClass} 사용 시 필수 요건이다.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RawDataId implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 로우데이터 시퀀스 ID. */
    private Long rawdataId;

    /** SCADA 수집 일시 — 파티션 키. */
    private LocalDateTime acqDtm;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RawDataId other)) {
            return false;
        }
        return Objects.equals(rawdataId, other.rawdataId)
                && Objects.equals(acqDtm, other.acqDtm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawdataId, acqDtm);
    }
}
