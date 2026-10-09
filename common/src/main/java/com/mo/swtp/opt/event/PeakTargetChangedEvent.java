package com.mo.swtp.opt.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 전력피크 목표값 변경 도메인 이벤트 (record).
 *
 * <p>변경 트랜잭션 commit 후 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 발화되어
 * SSE 구독 emitter 들에게 동일 페이로드가 전파된다. 정수장(테넌트) 전역 단일 목표 피크치이므로
 * 같은 화면을 구독하는 모든 사용자가 변경을 즉시 인지한다
 * (전력피크분석-1번섹션 ANALYZE1 + PLAN1, 2026-06-04).</p>
 *
 * <p>payload 비민감화 — SSE 구독 인증 우회 결정 (PLAN1 §SSE 인프라) 의 한계 완화 조건으로
 * {@code targetPeakElpwr}·{@code updtDtm} 2 필드만 허용. 사용자 정보·시설 운영 데이터 포함 금지.</p>
 *
 * <p>위치 — common 모듈. {@link com.mo.swtp.opt.domain.PeakTarget} 가 {@code DomainEventEntity}
 * 패턴에 따라 자기 상태 변경 메서드 ({@code change}) 내부에서 {@code registerEvent()} 호출 시
 * 본 이벤트를 참조한다. common 모듈 엔티티에서 api 모듈 클래스를 import 할 수 없으므로
 * (backend/CLAUDE.md §모듈 경계 원칙) 본 이벤트는 common 측에 배치한다.</p>
 *
 * @param targetPeakElpwr 변경된 목표 피크 전력값 (kW)
 * @param updtDtm         변경 일시
 */
public record PeakTargetChangedEvent(
        BigDecimal targetPeakElpwr,
        LocalDateTime updtDtm
) {
}
