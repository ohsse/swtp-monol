package com.mo.swtp.proc.repository;

import com.mo.swtp.proc.domain.AiDrvnMode;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AI 운전모드 현재 상태 JPA 리포지토리.
 *
 * <p>1:1 매핑 ({@link AiDrvnMode#getProcId()} = {@code proc_m.proc_id}) 이므로 ID 타입은 {@link String}.</p>
 */
public interface AiDrvnModeRepository extends JpaRepository<AiDrvnMode, String> {

    /**
     * 공정/제어대상 ID 로 현재 모드 행을 {@code PESSIMISTIC_WRITE} 락으로 조회한다
     * (변경 트랜잭션 직렬화 — 송수펌프제어분석-2번섹션 PLAN2 §변경 트랜잭션 흐름 1단계).
     *
     * <p>SELECT FOR UPDATE 로 동일 procId 에 대한 동시 변경 시도를 락으로 직렬화한다.
     * 부분 UNIQUE INDEX 와 함께 이중 안전망을 형성한다.</p>
     *
     * @param procId 공정/제어대상 ID
     * @return 현재 모드 행 (없으면 Optional.empty — 최초 설정 시나리오)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM AiDrvnMode m WHERE m.procId = :procId")
    Optional<AiDrvnMode> findByProcIdForUpdate(@Param("procId") String procId);
}
