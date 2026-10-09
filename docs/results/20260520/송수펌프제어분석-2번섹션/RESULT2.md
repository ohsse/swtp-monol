---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 도메인 이벤트 패턴 정렬 (RESULT2)

## 관련 작업
- [계획안](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN3.md) (status: approved)
- [태스크](../../../tasks/20260520/송수펌프제어분석-2번섹션/TASK2.md) (status: completed)
- [이전 리뷰](../../../reviews/20260520/송수펌프제어분석-2번섹션/REVIEW1.md) (블로커 3건)
- [이전 결과](RESULT1.md)

## 작업 요약

REVIEW1 블로커 3건 해소. `AiDrvnMode` 의 `BaseEntity → DomainEventEntity` 상속 전환 + `change()`/`create()` 내부 `registerEvent()` 추가 + `AiDrvnModeEventPublisher` 신규 컴포넌트 + `AiDrvnModeService` 의 `ApplicationEventPublisher` 직접 주입 제거 + UPSERT 분기에 따른 `changeAndPublish`/`createAndPublish` 위임으로 모듈 SSOT 패턴 (`DomainEventEntity` + `AbstractDomainEventPublisher` 선례 `UserEventPublisher`) 정렬.

PLAN3 §범위 외 추가 변경 1건 발생 — `AiDrvnModeChangedEvent` 가 `api` 모듈에서 `common` 모듈로 이동 (Phase 2 조건부 트리거). `common.AiDrvnMode` 가 `api.event.AiDrvnModeChangedEvent` 를 import 할 수 없는 모듈 경계 제약 때문 (`backend/CLAUDE.md §모듈 경계 원칙`).

## TASK 규모
| 항목 | 값 |
|------|----|
| Phase 수 | 6 |
| 체크박스 수 | 26 |
| 분할 여부 | N |
| 분할 근거 | — (5개 파일 정정 + 신규, 단일 도메인 응집) |

## 변경 사항

### 의도된 변경

- `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java`
  - `extends BaseEntity` → `extends DomainEventEntity` 전환
  - `create(...)` 정적 팩토리 내부 `registerEvent(new AiDrvnModeChangedEvent(...))` 추가 (최초 설정도 SSE 통지 대상)
  - `change(...)` 메서드 내부 `registerEvent(...)` 추가
  - Javadoc 도메인 룰 섹션 갱신 ("registerEvent 로 이벤트 축적, Publisher 가 publishAndClear 위임")
- `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisher.java` 신규 작성
  - `extends AbstractDomainEventPublisher<AiDrvnMode>`
  - `changeAndPublish(mode)` — `super.publishAndClear(mode)` 위임 (dirty checking)
  - `createAndPublish(mode)` — `super.saveAndPublish(mode, null)` 위임
- `api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java`
  - `ApplicationEventPublisher eventPublisher` 의존성 제거 → `AiDrvnModeEventPublisher aiDrvnModeEventPublisher` 주입
  - `changeAiDrvnMode` 메서드 5단계: `eventPublisher.publishEvent(...)` 직접 호출 → UPSERT 분기 `isUpdate ? changeAndPublish : createAndPublish` 위임
  - 클래스/메서드 Javadoc 갱신 (옵션 B 문구 제거 + `DomainEventEntity` 패턴 정합 명시)
- `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisherTest.java` 신규 작성
  - 케이스 1: `changeAndPublish_엔티티의_축적된_이벤트가_발행되고_클리어된다` — 2건 축적 → `times(2)` publish + `repository.save` never + `getDomainEvents` 비어있음
  - 케이스 2: `createAndPublish_엔티티가_저장되고_축적된_이벤트가_발행된다` — 1건 축적 → save 1회 + publish 1회 + clear

### 계획 외 변경

- **`AiDrvnModeChangedEvent` 모듈 이동** (Phase 2 조건부 트리거 결과)
  - 이동: `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEvent.java` 삭제 → `common/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEvent.java` 신규
  - 사유: `common.AiDrvnMode` 가 `api` 모듈 클래스를 import 할 수 없음 (`backend/CLAUDE.md §모듈 경계 원칙` — api → common 단방향 의존). PLAN3 작성 시 모듈 경계 제약 미반영 → TASK2 Phase 2 조건부 단계에서 사후 발견·정정
  - 패키지명 (`com.mo.swtp.proc.event`) 동일 → 사용처 import 변경 불요. Listener·SseService·Service·테스트 모두 그대로 컴파일 통과
  - 분류: **의도된 부수 변경** (PLAN3 §범위 의 본질 — `DomainEventEntity` 패턴 정렬 — 을 모듈 경계 룰 정합하에 완성하기 위한 필수 변경)

## 테스트 결과

| 항목 | 결과 |
|------|------|
| `./gradlew :common:compileJava :api:compileJava` | **BUILD SUCCESSFUL** |
| `./gradlew :common:test` (`AbstractDomainEventPublisherTest`·`ProcessTest` 등 포함) | **BUILD SUCCESSFUL** |
| `./gradlew :api:test --tests com.mo.swtp.proc.event.AiDrvnModeEventPublisherTest` | **BUILD SUCCESSFUL** (단위 2 PASS) |
| `./gradlew :api:test --tests com.mo.swtp.proc.sse.AiDrvnModeSseServiceTest` | **BUILD SUCCESSFUL** (단위 4 PASS — 모듈 이동 영향 없음 검증) |
| `./gradlew :api:test --tests AiDrvnModeServiceIntegrationTest` | **사용자 작업 잔존** — PostgreSQL + V6 마이그레이션 적용 후 검증 필요 (TASK1 Phase 12 미해결 항목 상속). 적용 명령: `psql -U smartwtp -d smartwtp -f common/src/main/resources/db/migration/V6__proc.sql` |
| `./gradlew :api:test --tests AiDrvnModeChangedEventListenerTest` | **사용자 작업 잔존** — Spring Context 부팅 의존 |
| `./gradlew :api:test --tests AiDrvnModeServiceConcurrencyTest` | **사용자 작업 잔존** — PostgreSQL 의존 |
| `./gradlew :api:test --tests AiDrvnModeSseControllerIntegrationTest` | **사용자 작업 잔존** — Spring Context 부팅 의존 |
| `./gradlew clean build` | **사용자 작업 잔존** — V6 DB 적용 + 통합 테스트 PASS 후 실행 |
| `check-errorcode-contract.sh` 훅 (`AiDrvnModeEventPublisher.java` 저장 시) | 차단 없음 |

### 본 세션 검증 통과 항목 요약
- 컴파일 정합: common·api 모두 BUILD SUCCESSFUL
- 단위 테스트 6건 PASS: `AbstractDomainEventPublisherTest` (common) + `ProcessTest` (common) + `AiDrvnModeEventPublisherTest` 2건 (api) + `AiDrvnModeSseServiceTest` 4건 (api)
- 모듈 경계 정합: `common` 측 신규 이벤트 클래스 + `api` 측 사용처 컴파일 일관

### 사용자 환경 의존 미검증 항목
- 통합 테스트 4건 (`AiDrvnModeServiceIntegrationTest` · `AiDrvnModeServiceConcurrencyTest` · `AiDrvnModeChangedEventListenerTest` · `AiDrvnModeSseControllerIntegrationTest`) — RESULT1 의 동일 항목 (Phase 12 미해결) 상속. 동일 절차로 사용자 환경에서 PASS 확인 필요

## 비고

- 본 RESULT2 는 REVIEW1 블로커 3건 해소 사이클의 결과. REVIEW2 단계에서 도메인 이벤트 패턴 정렬 완결성 확인 + 신규 블로커 0건 확인 후 사이클 종결
- PLAN3 §성공 기준 (검증 가능 형태) 의 9개 기준 중 5개 본 세션 PASS, 4개 사용자 환경 검증 잔존 (통합 테스트 의존)
- 정렬된 패턴은 `feedback_domain_event_entity_pattern.md` 메모리로 저장됨 — 향후 PLAN 단계에서 옵션 B 우회 자동 차단
