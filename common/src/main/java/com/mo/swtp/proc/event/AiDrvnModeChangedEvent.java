package com.mo.swtp.proc.event;

import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import java.time.LocalDateTime;

/**
 * AI 운전모드 변경 도메인 이벤트 (record).
 *
 * <p>변경 트랜잭션 commit 후 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 발화되어
 * SSE 구독 emitter 들에게 동일 페이로드가 전파된다.</p>
 *
 * <p>payload 비민감화 — 인증 우회 결정 (PLAN2 §SSE 인증 1안) 의 한계 완화 조건으로
 * procId·aiDrvnModCd·startDtm 3 필드만 허용. 사용자 정보·시설 운영 데이터 포함 금지.</p>
 *
 * <p>위치 — common 모듈 (송수펌프제어분석-2번섹션 PLAN3·TASK2 Phase 2, 2026-05-20).
 * {@link com.mo.swtp.proc.domain.AiDrvnMode} 가 {@code DomainEventEntity} 패턴에 따라
 * 자기 상태 변경 메서드 ({@code change}/{@code create}) 내부에서 {@code registerEvent()}
 * 호출 시 본 이벤트를 참조한다. common 모듈 엔티티에서 api 모듈 클래스를 import 할 수 없으므로
 * (backend/CLAUDE.md §모듈 경계 원칙) 본 이벤트는 common 측에 배치한다.</p>
 *
 * @param procId      공정/제어대상 ID
 * @param aiDrvnModCd 새 AI 운전모드 코드
 * @param startDtm    모드 시작 일시
 */
public record AiDrvnModeChangedEvent(
        String procId,
        AiDrvnModeCode aiDrvnModCd,
        LocalDateTime startDtm
) {
}
