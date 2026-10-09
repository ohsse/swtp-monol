---
status: draft
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 표출·이력 관리 + SSE 실시간 전파 (REVIEW1)

## 관련 결과
- [결과](../../../results/20260520/송수펌프제어분석-2번섹션/RESULT1.md)
- [태스크](../../../tasks/20260520/송수펌프제어분석-2번섹션/TASK1.md)
- [계획안](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN2.md)

## 리뷰 범위

PLAN2 §사용자 결정 사항의 옵션 B 채택 (`ApplicationEventPublisher` Service 직접 주입) 결정 — 도메인 이벤트 패턴 정합성 점검. 본 사이클은 SSE 통지·이력 관리라는 도메인 이벤트 발행 책임을 갖는 엔티티 (`AiDrvnMode`) 가 신규 등장한 첫 사이클이므로 모듈 표준 패턴 (`DomainEventEntity` + 전용 `EventPublisher`) 적용 여부를 핵심 점검 대상으로 한다.

## 발견 사항

| 심각도 | 카테고리 | 위치 | 발견 |
|------|---------|-----|------|
| 높음(블로커) | 도메인 룰 위반 (이벤트 패턴) | `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java:42` | `AiDrvnMode extends BaseEntity` 인데 모드 변경 시 `AiDrvnModeChangedEvent` 를 발행하는 도메인 이벤트 발행 엔티티. `common/CLAUDE.md` Entity 규칙 "이벤트 기능이 필요하면 `DomainEventEntity` 상속" 위반. 표준 패턴은 [`entity-patterns.md` §여부(Y/N) 필드 패턴 아래 도메인 이벤트 절](../../../../.claude/rules/entity-patterns.md) 및 `User extends DomainEventEntity` (`common/src/main/java/com/mo/swtp/user/domain/User.java`) 선례 |
| 높음(블로커) | 도메인 룰 위반 (이벤트 패턴) | `api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java:56,123` | Service 가 `ApplicationEventPublisher` 를 직접 주입받아 `publishEvent(new AiDrvnModeChangedEvent(...))` 호출. 표준 패턴은 `extends AbstractDomainEventPublisher<T>` 전용 컴포넌트 (`UserEventPublisher` 선례: `api/src/main/java/com/mo/swtp/user/event/UserEventPublisher.java`) 를 통한 `saveAndPublish` / `publishAndClear` 캡슐화. Service 의 `ApplicationEventPublisher` 직접 주입은 도메인 모델의 자기 책임성 (엔티티가 자기 상태 변경 이벤트를 표현) 을 무너뜨림 |
| 높음(블로커) | 도메인 룰 위반 (PLAN 결정 오류) | `docs/plan/20260520/송수펌프제어분석-2번섹션/PLAN2.md:31,167` | "옵션 B (`ApplicationEventPublisher` 직접 주입) 채택 (단순성 우선)" 결정이 모듈 SSOT 패턴을 위반. ANALYZE2 5인 회의 `wtp-backend-engineer` 라운드 1 결론 (PLAN2 §부록 519행) 이 "옵션 B 단순성 채택 정합" 으로 통과시킨 부분이 점검 누락 — `DomainEventEntity` + `AbstractDomainEventPublisher` 패턴이 결합도를 이미 통제하므로 "단순성" 명분의 우회 불성립 |
| 중간(권고) | 테스트 누락 | `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeChangedEventListenerTest.java` | Listener AFTER_COMMIT 검증 자체는 존재하나, `EventPublisher` 컴포넌트 단위 테스트 (`saveAndPublish` / `publishAndClear` 위임) 가 부재. PLAN3 단계에서 신규 `AiDrvnModeEventPublisher` 추가 시 단위 테스트 1건 동반 |
| 낮음(참고) | 비고 | `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEvent.java` | record + 3 필드 (procId·aiDrvnModCd·startDtm) 자체는 유지. 단 발행 경로만 `DomainEventEntity#registerEvent()` → `AbstractDomainEventPublisher#publishAndClear()` 로 변경 |

## 개선 제안

### 1. `AiDrvnMode` → `DomainEventEntity` 상속 전환

```java
// Before
public class AiDrvnMode extends BaseEntity implements Persistable<String> {

// After
public class AiDrvnMode extends DomainEventEntity implements Persistable<String> {
```

`change()` 메서드 내부에서 이벤트 등록:

```java
public void change(AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm) {
    this.aiDrvnModCd = aiDrvnModCd;
    this.startDtm = startDtm;
    registerEvent(new AiDrvnModeChangedEvent(this.procId, aiDrvnModCd, startDtm));
}
```

`create()` 정적 팩토리도 동일 패턴 적용 (최초 설정 시점 이벤트).

### 2. `AiDrvnModeEventPublisher` 신규 컴포넌트

`UserEventPublisher` 선례 정합:

```java
@Component
public class AiDrvnModeEventPublisher extends AbstractDomainEventPublisher<AiDrvnMode> {

    public AiDrvnModeEventPublisher(AiDrvnModeRepository repository,
                                    ApplicationEventPublisher publisher) {
        super(repository, publisher);
    }

    public AiDrvnMode changeAndPublish(AiDrvnMode mode) {
        // dirty checking 으로 flush, 축적된 도메인 이벤트만 발행 후 클리어
        return saveAndPublish(mode, null);
    }
}
```

### 3. `AiDrvnModeService` 재구성

- `ApplicationEventPublisher` 의존성 제거
- `AiDrvnModeEventPublisher` 컴포넌트 주입
- 6단계 트랜잭션 흐름 재정렬 (publishEvent 직접 호출 → Publisher 위임)

```java
// 5단계 (재정렬): Publisher 위임 — saveAndPublish 가 dirty checking flush + 축적 이벤트 발행
aiDrvnModeEventPublisher.changeAndPublish(mode);
```

### 4. PLAN3 작성 (Fix Cycle)

본 REVIEW1 블로커 텍스트는 도메인 정합성 키워드 (`용어` / `약어` / `중복 정의` / `네이밍 충돌` / `엔티티 통합`) 를 포함하지 않으므로 ANALYZE 재진입 스킵. `doc-harness/README.md §수정 사이클 — Fix Cycle 감지 알고리즘` 정합 — PLAN3 직행.

PLAN3 범위:
- 옵션 B 결정 폐기 + `DomainEventEntity` 패턴 채택 명시
- TASK2 에서 위 3건 (엔티티 + Publisher + Service) 정정 + 테스트 갱신
- PLAN2 의 옵션 B 인용 문구 (`PLAN2.md:31,167`) 는 PLAN3 의 "## 배경" 에서 폐기 명시 (PLAN2 자체는 수정 금지 — 사이클 산출물 immutable)

## 결론

**블로커 3건 발견. Fix Cycle 진입 필요.** PLAN2 옵션 B 결정은 모듈 SSOT 패턴 (`common/CLAUDE.md` + `entity-patterns.md` + `User`/`UserEventPublisher` 선례) 을 위반했으며, ANALYZE2 5인 회의의 `wtp-backend-engineer` 라운드 1 통과는 점검 누락. PLAN3 작성으로 `DomainEventEntity` 패턴 정렬 + TASK2 구현.

ANALYZE 재진입 불요 (도메인 정합성 키워드 미포함). PLAN3 직행.
