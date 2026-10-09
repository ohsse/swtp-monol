---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 도메인 이벤트 패턴 정렬 (PLAN3)

## 목적

PLAN2 §사용자 결정 사항의 옵션 B (`ApplicationEventPublisher` Service 직접 주입) 결정을 폐기하고, 모듈 SSOT 패턴 (`DomainEventEntity` 상속 + 전용 `AbstractDomainEventPublisher` 서브클래스) 으로 `AiDrvnMode` 이벤트 발행 경로를 정렬한다.

## 배경

- [이전 리뷰](../../../reviews/20260520/송수펌프제어분석-2번섹션/REVIEW1.md) (status: draft) 블로커 3건 해소:
  - `AiDrvnMode` `BaseEntity` 상속 → `DomainEventEntity` 전환
  - `AiDrvnModeService` `ApplicationEventPublisher` 직접 주입 → `AiDrvnModeEventPublisher` 컴포넌트 위임
  - PLAN2 옵션 B 결정 + ANALYZE2 `wtp-backend-engineer` 라운드 1 (PLAN2.md 519행) 점검 누락 정정
- 직전 PLAN: [PLAN2](PLAN2.md) (status: approved — 본 PLAN3 가 옵션 B 결정만 폐기, 나머지 SSE 인프라·DDL·Controller·테스트 구조는 PLAN2 유지)
- Fix Cycle 진입 — `doc-harness/README.md §수정 사이클` 알고리즘 정합. ANALYZE 재진입 스킵 (REVIEW1 블로커에 도메인 정합성 키워드 미포함)
- 표준 패턴 SSOT:
  - `common/CLAUDE.md` Entity 규칙: "이벤트 기능이 필요하면 `DomainEventEntity` 상속"
  - `.claude/rules/entity-patterns.md` 도메인 이벤트 절
  - 선례: `common/src/main/java/com/mo/swtp/user/domain/User.java` (extends DomainEventEntity) + `api/src/main/java/com/mo/swtp/user/event/UserEventPublisher.java` (extends AbstractDomainEventPublisher<User>)

## 범위

### 포함 범위 (PLAN3 정정 대상만)

- `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` — 상위 클래스 `BaseEntity` → `DomainEventEntity` 변경 + `change()` / `create()` 내부 `registerEvent()` 호출 추가
- `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisher.java` — 신규 작성 (`extends AbstractDomainEventPublisher<AiDrvnMode>`)
- `api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java` — `ApplicationEventPublisher` 의존성 제거 + `AiDrvnModeEventPublisher` 주입 + 6단계 트랜잭션 흐름의 5단계 재정렬
- `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisherTest.java` — 신규 단위 테스트 (`changeAndPublish` 위임 검증)
- `api/src/test/java/com/mo/swtp/proc/service/AiDrvnModeServiceIntegrationTest.java` — 통합 테스트 갱신 (`ApplicationEventPublisher` mock 검증 → `AiDrvnModeEventPublisher` mock 위임 검증 또는 도메인 이벤트 축적·발행 흐름 검증으로 변경)
- `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeChangedEventListenerTest.java` — Listener AFTER_COMMIT 검증 흐름 유지 (Publisher 경유로만 변경 — Listener 자체 책임 무변경)

### 제외 범위 (PLAN2 유지 — 본 PLAN3 미수정)

- DDL 마이그레이션 SQL (`V6__proc.sql` + `docs/ddl/proc.sql`) — 무변경
- `Process` 엔티티 — `BaseEntity` 유지 (도메인 이벤트 없음)
- `AiDrvnModeHistory` 엔티티 — `BaseEntity` 유지 (INSERT-only + `end_dtm` UPDATE 만, 이벤트 발행 없음)
- enum `AiDrvnModeCode` · ErrorCode `ProcErrorCode` · DTO 5종 — 무변경
- Repository 3종 — 무변경
- SSE 인프라 4 클래스 (`AiDrvnModeChangedEvent` record · `AiDrvnModeChangedEventListener` · `AiDrvnModeSseService` · `AiDrvnModeSseController`) — record 정의·Listener AFTER_COMMIT 위임·SSE Service 보관소·Controller 엔드포인트 모두 무변경
- JWT 인증 우회 설정 — 무변경
- `ProcController` 4 REST 엔드포인트 — 무변경
- 기타 테스트 4종 (`ProcessTest` · `AiDrvnModeServiceConcurrencyTest` · `AiDrvnModeSseServiceTest` · `AiDrvnModeSseControllerIntegrationTest`) — 무변경

## 구현 방향

### 1. `AiDrvnMode` 엔티티 정렬

```java
// Before (PLAN2)
public class AiDrvnMode extends BaseEntity implements Persistable<String> {
    public void change(AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm) {
        this.aiDrvnModCd = aiDrvnModCd;
        this.startDtm = startDtm;
    }
}

// After (PLAN3)
public class AiDrvnMode extends DomainEventEntity implements Persistable<String> {

    public static AiDrvnMode create(Process process, AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm) {
        AiDrvnMode mode = new AiDrvnMode(process.getProcId(), process, aiDrvnModCd, startDtm);
        mode.registerEvent(new AiDrvnModeChangedEvent(process.getProcId(), aiDrvnModCd, startDtm));
        return mode;
    }

    public void change(AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm) {
        this.aiDrvnModCd = aiDrvnModCd;
        this.startDtm = startDtm;
        registerEvent(new AiDrvnModeChangedEvent(this.procId, aiDrvnModCd, startDtm));
    }
}
```

- `BaseEntity` 의 AuditingEntityListener 4컬럼 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) 자동 주입은 `DomainEventEntity` 가 `BaseEntity` 를 확장하므로 그대로 보존
- 정적 팩토리 `create()` 도 동일 이벤트 등록 — 최초 설정 시점도 SSE 통지 대상 (PLAN2 도메인 의도와 동일)

### 2. `AiDrvnModeEventPublisher` 신규

```java
package com.mo.swtp.proc.event;

@Component
public class AiDrvnModeEventPublisher extends AbstractDomainEventPublisher<AiDrvnMode> {

    public AiDrvnModeEventPublisher(AiDrvnModeRepository repository,
                                    ApplicationEventPublisher publisher) {
        super(repository, publisher);
    }

    /**
     * 모드 변경 후 축적된 도메인 이벤트를 발행한다.
     * <p>JPA dirty checking 으로 flush 처리되므로 별도 save() 호출 불필요.</p>
     */
    public void changeAndPublish(AiDrvnMode mode) {
        super.publishAndClear(mode);
    }

    /**
     * 최초 설정 시 신규 엔티티 저장 + 도메인 이벤트 발행.
     */
    public AiDrvnMode createAndPublish(AiDrvnMode mode) {
        return super.saveAndPublish(mode, null);
    }
}
```

- `UserEventPublisher` 의 `deactivateAndPublish` / `deleteAndPublish` 메서드 명명 선례 정합 (도메인 의도가 메서드명에 드러남)
- `publishAndClear(entity)` 시그니처 사용 — 엔티티가 이미 `registerEvent()` 로 이벤트를 축적했으므로 추가 등록 불요
- `createAndPublish` 의 `saveAndPublish(entity, null)` — eventFunction 이 null 이면 신규 등록 없이 축적된 이벤트만 발행 (`AbstractDomainEventPublisher.java:80-89` 동작)

### 3. `AiDrvnModeService` 재구성

```java
// Before (PLAN2)
@Service
public class AiDrvnModeService {
    private final ApplicationEventPublisher eventPublisher;
    // ...
    @Transactional
    public AiDrvnModeDto changeAiDrvnMode(String procId, AiDrvnModeCode newModeCd) {
        // ... 1~4 단계
        // 5단계
        eventPublisher.publishEvent(new AiDrvnModeChangedEvent(procId, newModeCd, now));
        return AiDrvnModeDto.from(mode);
    }
}

// After (PLAN3)
@Service
public class AiDrvnModeService {
    private final AiDrvnModeEventPublisher aiDrvnModeEventPublisher;  // ← 변경
    // ...
    @Transactional
    public AiDrvnModeDto changeAiDrvnMode(String procId, AiDrvnModeCode newModeCd) {
        // ... 1~4 단계
        // 5단계: Publisher 위임 (엔티티 change() 가 registerEvent() 한 이벤트를 발행 + 클리어)
        if (currentOpt.isPresent()) {
            aiDrvnModeEventPublisher.changeAndPublish(mode);
        } else {
            aiDrvnModeEventPublisher.createAndPublish(mode);
        }
        return AiDrvnModeDto.from(mode);
    }
}
```

- 의존성 4개 → 4개 동일 (`ApplicationEventPublisher` → `AiDrvnModeEventPublisher` 1:1 치환)
- 변경 트랜잭션 6단계 흐름 자체는 유지 — 5단계 publish 호출만 Publisher 경유
- `changeAndPublish` vs `createAndPublish` 분기는 UPSERT 분기 (currentOpt 존재 여부) 와 1:1

### 4. 변경 트랜잭션 흐름 (재정렬)

```
1. AiDrvnModeRepository#findByProcIdForUpdate — SELECT FOR UPDATE 락
2. 직전 활성 이력 행 end_dtm UPDATE (+ historyRepo.flush)
3. 마스터 UPSERT — currentOpt.map(existing -> existing.change(...))  ← change() 내부 registerEvent()
                  .orElseGet(() -> AiDrvnMode.create(...))             ← create() 내부 registerEvent()
4. 신규 이력 INSERT (historyRepo.flush 강제)
5. aiDrvnModeEventPublisher.changeAndPublish(mode) OR createAndPublish(mode) — 축적 이벤트 publish + clear
6. 트랜잭션 commit → AFTER_COMMIT Listener → SSE 전파
```

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 / 테스트 / 조회 |
|------|----------------------|
| `AiDrvnMode` 가 `DomainEventEntity` 상속 | `grep "extends DomainEventEntity" common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` 1건 매칭 |
| `AiDrvnMode` 가 `BaseEntity` 직접 상속 안 함 | `grep "extends BaseEntity" common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` 0건 |
| `AiDrvnModeEventPublisher` 신규 컴포넌트 존재 | `grep "extends AbstractDomainEventPublisher<AiDrvnMode>" api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisher.java` 1건 |
| `AiDrvnModeService` 의 `ApplicationEventPublisher` 직접 주입 제거 | `grep "ApplicationEventPublisher" api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java` 0건 |
| `AiDrvnModeService` 가 `AiDrvnModeEventPublisher` 주입 | `grep "AiDrvnModeEventPublisher" api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java` 매칭 |
| `AiDrvnModeEventPublisherTest` 신규 테스트 PASS | `./gradlew :api:test --tests AiDrvnModeEventPublisherTest` BUILD SUCCESSFUL |
| 기존 `AiDrvnModeServiceIntegrationTest` PASS 유지 | `./gradlew :api:test --tests AiDrvnModeServiceIntegrationTest` BUILD SUCCESSFUL |
| 기존 `AiDrvnModeChangedEventListenerTest` AFTER_COMMIT 검증 PASS 유지 | `./gradlew :api:test --tests AiDrvnModeChangedEventListenerTest` BUILD SUCCESSFUL |
| 컴파일 정합 | `./gradlew :common:compileJava :api:compileJava` BUILD SUCCESSFUL |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN3 단계 결정 |
|-----------|------|--------------|
| `AiDrvnMode.create()` 시점도 도메인 이벤트 발행 대상인지 | 가정 | 결정 — 발행 (최초 설정 시 SSE 구독자가 즉시 현재 모드 인지 필요. PLAN2 의 `changeAiDrvnMode` 분기에서 신규 INSERT 경로도 publish 했던 의도 유지) |
| `AbstractDomainEventPublisher#publishAndClear(entity)` 메서드 접근성 | 미해결 → 결정 | `protected` 이므로 자식 클래스 (`AiDrvnModeEventPublisher`) 에서 호출 가능. `super.publishAndClear(mode)` 직접 호출. `AbstractDomainEventPublisher.java:121-124` 확인 |
| Fix Cycle 진입에 따른 TASK2 분할 여부 | 가정 | 결정 — 분할 미적용. 5개 파일 (엔티티 1·Publisher 1·Service 1·테스트 2) 만 정정 + 신규, Phase 5 이하 예상 |

## 제외 사항

- PLAN2 의 모든 결정 (DDL · SSE 인프라 · Controller · 시드 데이터 · JWT 우회 · 보존 정책) 은 무변경. 본 PLAN3 는 옵션 B 결정만 폐기
- PLAN2 의 `wtp-backend-engineer` 라운드 1 통과 사유 (PLAN2.md:519 "옵션 B 단순성 채택 정합") 는 본 사이클의 점검 누락 사례로 기록만 — ANALYZE 재진입 트리거 아님 (REVIEW1 §결론 참조)
- 강제 전환 정책 (SCADA 5분 초과 자동 전환 등 `ot-integration.md §5` 보류) 은 PLAN2 와 동일하게 본 사이클 제외

## 예상 산출물

- [태스크](../../../tasks/20260520/송수펌프제어분석-2번섹션/TASK2.md) — 본 PLAN3 approved 후 작성
- 신규 Java 파일 1개: `AiDrvnModeEventPublisher.java`
- 정정 Java 파일 2개: `AiDrvnMode.java` · `AiDrvnModeService.java`
- 신규 테스트 파일 1개: `AiDrvnModeEventPublisherTest.java`
- 정정 테스트 파일 1개: `AiDrvnModeServiceIntegrationTest.java` (또는 흐름 검증만 갱신)
