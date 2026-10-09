package com.mo.swtp.proc.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.proc.domain.Process;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공정/제어대상 JPA 리포지토리.
 *
 * <p>외부 할당 PK ({@link Process#getProcId()}) 이므로 {@link JpaRepository} 의 ID 타입은 {@link String}.</p>
 */
public interface ProcessRepository extends JpaRepository<Process, String> {

    /**
     * 사용 여부로 필터한 공정/제어대상 목록을 표시 순서로 조회한다.
     *
     * @param useYn 사용 여부
     * @return 정렬된 공정/제어대상 목록
     */
    List<Process> findByUseYnOrderByDispOrdAsc(YnType useYn);

    /**
     * 공정/제어대상명 중복 여부 (UNIQUE 사전 검증용).
     *
     * @param procNm 공정/제어대상명
     * @return 동일 명칭 존재 여부
     */
    boolean existsByProcNm(String procNm);
}
