package com.mo.swtp.instrument.repository;

import com.mo.swtp.instrument.domain.PumpCtrlHistory;
import com.mo.swtp.instrument.domain.PumpCtrlHistoryId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 펌프 제어 이력 JPA 저장소.
 *
 * <p>{@link PumpCtrlHistory} ({@code pump_ctrl_h}, 복합 PK {@link PumpCtrlHistoryId}) 의 기본 CRUD +
 * {@link PumpCtrlHistoryCustomRepository} Querydsl 조회를 결합한다. 본 사이클은 조회 전용이므로
 * 쓰기 메서드는 사용하지 않는다.</p>
 */
public interface PumpCtrlHistoryRepository
        extends JpaRepository<PumpCtrlHistory, PumpCtrlHistoryId>, PumpCtrlHistoryCustomRepository {
}
