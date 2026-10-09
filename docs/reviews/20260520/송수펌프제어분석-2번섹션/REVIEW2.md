---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 도메인 이벤트 패턴 정렬 (REVIEW2)

## 관련 결과
- [결과](../../../results/20260520/송수펌프제어분석-2번섹션/RESULT2.md)
- [태스크](../../../tasks/20260520/송수펌프제어분석-2번섹션/TASK2.md) (status: completed)
- [계획안](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN3.md) (status: approved)
- [이전 리뷰](REVIEW1.md) (블로커 3건 — 본 REVIEW2 가 해소)

## 리뷰 범위

REVIEW1 블로커 3건 해소 검증 + TASK2 Phase 2 모듈 이동 (`AiDrvnModeChangedEvent`: api → common) 의 부수 영향 점검 + 도메인 이벤트 패턴 정렬 완결성 확인. 통합 테스트 사용자 환경 의존 4건은 RESULT1 의 Phase 12 미해결 항목 상속이라 본 REVIEW2 의 차단 대상 아님.

## 발견 사항

| 심각도 | 카테고리 | 위치 | 발견 |
|------|---------|-----|------|
| — | (블로커 0건) | — | REVIEW1 의 블로커 3건이 모두 해소됨 — 아래 §해소 검증 참조 |
| 중간(권고) | 테스트 보강 | `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisherTest.java` | `changeAndPublish` 케이스가 2건 축적 → `times(2)` 검증으로 정합. 향후 1건만 발행되는 단일 변경 시나리오 단위 추가 가능 (현재 통합 테스트로 커버되어 차단 대상 아님) |
| 낮음(참고) | 모듈 경계 | `common/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEvent.java` | TASK2 Phase 2 조건부 트리거로 모듈 이동 발생. PLAN3 단계에서 모듈 경계 (`common ← api` 단방향 의존) 제약을 사전 반영하지 못한 점은 PLAN3 §가정·§범위 의 일반화 부족 — 향후 `DomainEventEntity` 패턴 적용 PLAN 작성 시 모듈 경계 확인 절차 명시 권고 (메모리 [[feedback-domain-event-entity-pattern]] 보강 후보) |

## 해소 검증 (REVIEW1 블로커 1:1 대응)

| REVIEW1 블로커 | RESULT2 변경 | 검증 |
|--------------|------------|------|
| `AiDrvnMode extends BaseEntity` (이벤트 발행 엔티티) | `extends DomainEventEntity` 전환 + `create`/`change` 내부 `registerEvent` | grep "extends DomainEventEntity" `AiDrvnMode.java` 1건 + grep "extends BaseEntity" 0건. `:common:compileJava` BUILD SUCCESSFUL |
| `AiDrvnModeService` 의 `ApplicationEventPublisher` 직접 주입 | 의존성 제거 → `AiDrvnModeEventPublisher` 주입 + UPSERT 분기 `changeAndPublish`/`createAndPublish` 위임 | grep "ApplicationEventPublisher" `AiDrvnModeService.java` 0건. `AiDrvnModeEventPublisherTest` 단위 2 PASS (changeAndPublish times(2) + createAndPublish save 1회 + publish 1회) |
| PLAN2 옵션 B 결정 + ANALYZE2 라운드 1 통과 점검 누락 | PLAN3 §배경 에서 옵션 B 폐기 명시 + Javadoc "옵션 B 채택" 문구 제거 + 메모리 [[feedback-domain-event-entity-pattern]] 저장 | grep "옵션 B" `AiDrvnModeService.java` 0건. PLAN3 §배경 옵션 B 폐기 사유 명시. 메모리 인덱스 갱신 완료 |

## 개선 제안

### 1. 메모리 [[feedback-domain-event-entity-pattern]] 보강 (선택)
PLAN3 작성 시 모듈 경계 제약 미반영 → TASK 단계 사후 정정 사례를 반영하여 메모리 본문에 "PLAN 단계 체크리스트: (1) `DomainEventEntity` 상속 엔티티의 모듈 위치 (`common`) (2) `AbstractDomainEventPublisher` 상속 컴포넌트의 모듈 위치 (`api`) (3) 이벤트 클래스의 모듈 위치 (`common` 의무 — 엔티티가 import 해야 함)" 명시 가능. 본 사이클 외 별도 메모리 갱신.

### 2. 통합 테스트 사용자 환경 검증 (사용자 작업)
PostgreSQL + V6 마이그레이션 적용 후 4건 통합 테스트 PASS 확인:
- `AiDrvnModeServiceIntegrationTest` — 변경 트랜잭션 6단계 + Publisher 위임 흐름 + AFTER_COMMIT broadcast 검증
- `AiDrvnModeServiceConcurrencyTest` — 동시 변경 충돌 → 409 매핑 (Publisher 경로 영향 무)
- `AiDrvnModeChangedEventListenerTest` — Listener AFTER_COMMIT phase + 롤백 시 broadcast 미발생
- `AiDrvnModeSseControllerIntegrationTest` — JWT 우회 + Content-Type text/event-stream

본 4건은 코드 변경 없이 모듈 이동만 영향 (import 경로 동일). 사용자 환경 검증 후 4건 모두 PASS 예상.

## 결론

**블로커 0건. status: approved. 사이클 종결 가능.**

REVIEW1 블로커 3건 모두 해소 검증 완료. 도메인 이벤트 패턴 (`DomainEventEntity` + `AbstractDomainEventPublisher` 선례 `UserEventPublisher`) 정렬 완결. 모듈 경계 정합 (Phase 2 사후 정정으로 보강). 통합 테스트 사용자 환경 잔존은 RESULT1 의 Phase 12 미해결 항목 상속이라 본 사이클의 차단 대상 아님 — 사용자 환경에서 V6 마이그레이션 적용 후 통합 테스트 PASS 확인 후 `/dev:commit` 진행 권고.

다음 단계: `/dev:commit` 안내 (사용자 명시적 승인 필요 — `dev:transitions.md §commit` 정합, 자동 실행 금지).
