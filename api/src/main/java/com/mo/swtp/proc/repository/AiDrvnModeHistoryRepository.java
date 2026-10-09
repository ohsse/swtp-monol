package com.mo.swtp.proc.repository;

import com.mo.swtp.proc.domain.AiDrvnModeHistory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AI 운전모드 변경 이력 JPA 리포지토리.
 *
 * <p>BIGINT PK ({@link AiDrvnModeHistory#getAiDrvnModId()}) 시퀀스 기반.
 * 활성 행 조회 + 페이지네이션 이력 조회 두 메서드만 노출한다.</p>
 */
public interface AiDrvnModeHistoryRepository extends JpaRepository<AiDrvnModeHistory, Long> {

    /**
     * 공정/제어대상의 활성 이력 행 ({@code end_dtm IS NULL}) 을 조회한다.
     *
     * <p>변경 트랜잭션에서 직전 활성 행을 식별해 {@code end_dtm} 갱신 대상으로 사용한다.
     * 부분 UNIQUE INDEX 가 활성 행 1건을 강제하므로 결과는 0건 또는 1건.</p>
     *
     * @param procId 공정/제어대상 ID
     * @return 활성 이력 행 (최초 설정 전이면 Optional.empty)
     */
    @Query("SELECT h FROM AiDrvnModeHistory h WHERE h.procId = :procId AND h.endDtm IS NULL")
    Optional<AiDrvnModeHistory> findActiveByProcId(@Param("procId") String procId);

    /**
     * 공정/제어대상별 변경 이력 페이지네이션 조회 (최신순).
     *
     * @param procId   공정/제어대상 ID
     * @param pageable 페이지 정렬 (start_dtm DESC 권장)
     * @return 페이지 결과
     */
    Page<AiDrvnModeHistory> findByProcIdOrderByStartDtmDesc(String procId, Pageable pageable);
}
