package com.mo.swtp.proc.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.proc.domain.Process;
import com.mo.swtp.proc.dto.ProcDto;
import com.mo.swtp.proc.exception.ProcErrorCode;
import com.mo.swtp.proc.repository.ProcessRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공정/제어대상 마스터 조회 서비스.
 *
 * <p>본 사이클 (송수펌프제어분석-2번섹션 PLAN2) 범위는 시드 데이터 기반 조회 전용.
 * 신규 등록/수정/비활성화 기능은 별도 사이클에서 다룬다 (ROOT coding-discipline.md §2 정합).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessService {

    private final ProcessRepository processRepository;

    /**
     * 활성 공정/제어대상 목록을 표시 순서로 조회한다.
     */
    public List<ProcDto> findAllActive() {
        return processRepository.findByUseYnOrderByDispOrdAsc(YnType.Y).stream()
                .map(ProcDto::from)
                .toList();
    }

    /**
     * 공정/제어대상 단건 조회.
     *
     * @throws RestApiException {@link ProcErrorCode#PROC_NOT_FOUND}
     */
    public ProcDto findById(String procId) {
        Process process = processRepository.findById(procId)
                .orElseThrow(() -> new RestApiException(ProcErrorCode.PROC_NOT_FOUND));
        return ProcDto.from(process);
    }
}
