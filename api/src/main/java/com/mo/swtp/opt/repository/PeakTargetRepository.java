package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.PeakTarget;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 전력피크 목표값 JPA 리포지토리.
 *
 * <p>단일 전역 행 ({@link PeakTarget#PEAK_TARGET_CD}) 만 보유하므로 ID 타입은 {@link String}.</p>
 */
public interface PeakTargetRepository extends JpaRepository<PeakTarget, String> {

    /**
     * 고정 코드값으로 목표 피크치 행을 {@code PESSIMISTIC_WRITE} 락으로 조회한다
     * (저장 트랜잭션 직렬화 — 전력피크분석-1번섹션 PLAN1 §저장 흐름 1단계).
     *
     * <p>SELECT FOR UPDATE 로 동시 저장 시도를 락으로 직렬화한다. 시드 1행이 항상 존재하므로
     * 정상 경로에서는 Optional.present — 부재 시 배포 누락·픽스처 결손 신호다.</p>
     *
     * @param peakCd 고정 코드값 ({@code 'PEAK_TARGET'})
     * @return 목표 피크치 행 (시드 보장 시 항상 존재)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PeakTarget p WHERE p.peakCd = :peakCd")
    Optional<PeakTarget> findByPeakCdForUpdate(@Param("peakCd") String peakCd);
}
