---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 도메인 이벤트 패턴 정렬 (TASK2)

## 관련 계획
- [계획안](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN3.md) (status: approved)
- [이전 리뷰](../../../reviews/20260520/송수펌프제어분석-2번섹션/REVIEW1.md) (블로커 3건)
- 직전 TASK: [TASK1](TASK1.md) (status: completed — PLAN2 범위 구현 완료)

## TASK 분할 정책
- **단일 TASK2.md 채택** (분할 미적용) — Medium 등급 + 5개 파일 (엔티티 1·Publisher 1·Service 1·테스트 2) 만 정정. PLAN3 §가정 결정 정합

## Phase

### Phase 1: 엔티티 정렬 (AiDrvnMode → DomainEventEntity)

- [x] `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` 상위 클래스 변경 — `extends BaseEntity` → `extends DomainEventEntity` (import 정정 포함) → 검증: grep "extends DomainEventEntity" 1건 매칭 + grep "extends BaseEntity" 0건
- [x] `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` `change(...)` 메서드 내부에 registerEvent(new AiDrvnModeChangedEvent(this.procId, aiDrvnModCd, startDtm)) 호출 추가 → 검증: grep "registerEvent" AiDrvnMode.java 매칭 2건 이상 (change + create)
- [x] `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` `create(...)` 정적 팩토리 내부에 registerEvent 추가 (최초 설정도 SSE 통지 대상 — PLAN3 §가정 결정) → 검증: AiDrvnMode.create 호출 시 도메인 이벤트 1건 축적
- [x] `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` Javadoc 도메인 룰 섹션 갱신 — "registerEvent 로 AiDrvnModeChangedEvent 축적, AiDrvnModeEventPublisher 가 publishAndClear 위임" 명시 → 검증: grep "DomainEventEntity" Javadoc 본문 매칭
- [x] AiDrvnModeChangedEvent import 추가 (api 측 event 패키지 참조) — common 측에서 api 측 event 클래스 참조 가능 여부 확인 → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL

### Phase 2: AiDrvnModeChangedEvent 패키지 검토 + 이동 (조건부)

- [x] AiDrvnModeChangedEvent 현재 위치 `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEvent.java` 가 common 측 AiDrvnMode.java 에서 import 가능한지 확인 → 검증: common 모듈은 api 모듈에 역의존 금지 (backend/CLAUDE.md §모듈 경계 원칙) → api → common 단방향만 허용
- [x] **조건부**: api → common 역의존 발생 시 AiDrvnModeChangedEvent 를 common 측으로 이동 — `common/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEvent.java` 신규 작성 + 기존 api 측 파일 삭제 + Listener·Service·SseService import 경로 갱신 → 검증: ./gradlew :common:compileJava :api:compileJava BUILD SUCCESSFUL + grep "com.mo.swtp.proc.event.AiDrvnModeChangedEvent" 매칭 위치 일관

### Phase 3: AiDrvnModeEventPublisher 신규 작성

- [x] `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisher.java` 신규 작성 — extends AbstractDomainEventPublisher<AiDrvnMode> + 생성자 (AiDrvnModeRepository + ApplicationEventPublisher) → 검증: grep "extends AbstractDomainEventPublisher<AiDrvnMode>" 1건 매칭
- [x] AiDrvnModeEventPublisher#changeAndPublish(AiDrvnMode mode) 메서드 — super.publishAndClear(mode) 위임 (dirty checking flush + 축적 이벤트 발행) → 검증: grep "publishAndClear" AiDrvnModeEventPublisher.java 매칭
- [x] AiDrvnModeEventPublisher#createAndPublish(AiDrvnMode mode) 메서드 — super.saveAndPublish(mode, null) 위임 (신규 INSERT + 축적 이벤트 발행, eventFunction null 이면 추가 등록 없음 — AbstractDomainEventPublisher.java 동작) → 검증: grep "saveAndPublish" AiDrvnModeEventPublisher.java 매칭
- [x] AiDrvnModeEventPublisher Javadoc 작성 — UserEventPublisher 선례 정합 + changeAndPublish/createAndPublish 메서드 의도 명시 → 검증: grep "AbstractDomainEventPublisher" Javadoc 본문 매칭

### Phase 4: AiDrvnModeService 재구성

- [x] `api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java` 의존성 변경 — ApplicationEventPublisher eventPublisher 제거 → AiDrvnModeEventPublisher aiDrvnModeEventPublisher 추가 → 검증: grep "ApplicationEventPublisher" AiDrvnModeService.java 0건 + grep "AiDrvnModeEventPublisher" 매칭
- [x] `api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java` changeAiDrvnMode 메서드의 5단계 publishEvent 직접 호출 제거 → UPSERT 분기에 따라 changeAndPublish (existing) / createAndPublish (신규) 위임 → 검증: grep "eventPublisher.publishEvent" 0건 + grep "changeAndPublish\\|createAndPublish" 매칭
- [x] AiDrvnModeService Javadoc 6단계 트랜잭션 흐름 갱신 — "5단계: Publisher 위임 (엔티티 change/create 가 registerEvent 한 이벤트를 publishAndClear)" 로 정정 → 검증: grep "Publisher 위임" Javadoc 본문 매칭
- [x] AiDrvnModeService 클래스 Javadoc 의 "옵션 B 채택" 문구 제거 → DomainEventEntity 패턴 정합 문구로 대체 → 검증: grep "옵션 B" AiDrvnModeService.java 0건

### Phase 5: 테스트 신규 + 갱신

- [x] `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeEventPublisherTest.java` 신규 작성 — @ExtendWith(MockitoExtension.class) + @Mock JpaRepository · ApplicationEventPublisher + @InjectMocks AiDrvnModeEventPublisher → 검증: ./gradlew :api:test --tests AiDrvnModeEventPublisherTest BUILD SUCCESSFUL
- [x] AiDrvnModeEventPublisherTest 케이스 1: changeAndPublish_엔티티의_축적된_이벤트가_발행되고_클리어된다 — given registerEvent 1건 축적된 AiDrvnMode + when changeAndPublish + then ApplicationEventPublisher.publishEvent 1회 호출 + getDomainEvents 비어있음 → 검증: BDDMockito then(...).should() 검증 PASS
- [x] AiDrvnModeEventPublisherTest 케이스 2: createAndPublish_엔티티가_저장되고_이벤트가_발행된다 — given registerEvent 1건 축적된 신규 AiDrvnMode + when createAndPublish + then JpaRepository.save 1회 + publishEvent 1회 + clearDomainEvents → 검증: BDDMockito 검증 PASS
- [ ] **사용자 작업 필요**: `api/src/test/java/com/mo/swtp/proc/service/AiDrvnModeServiceIntegrationTest.java` 흐름 검증 — 외부 관찰 행동 (Listener → SseService.broadcast) 무변경이라 기존 검증 유지. PostgreSQL + V6 마이그레이션 적용 후 검증 필요 (TASK1 Phase 12 미해결 항목 상속) → 검증: ./gradlew :api:test --tests AiDrvnModeServiceIntegrationTest BUILD SUCCESSFUL
- [ ] **사용자 작업 필요**: `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeChangedEventListenerTest.java` 변경 없음 확인 — Listener 자체 책임 (AFTER_COMMIT phase + SseService 위임) 무변경. Spring Context 부팅 의존 → 검증: ./gradlew :api:test --tests AiDrvnModeChangedEventListenerTest BUILD SUCCESSFUL
- [ ] **사용자 작업 필요**: `api/src/test/java/com/mo/swtp/proc/service/AiDrvnModeServiceConcurrencyTest.java` 변경 없음 확인 — 동시성 시나리오는 UNIQUE INDEX 위반 매핑이라 Publisher 경로 무관. PostgreSQL 의존 → 검증: ./gradlew :api:test --tests AiDrvnModeServiceConcurrencyTest BUILD SUCCESSFUL

### Phase 6: 빌드 검증

- [x] `./gradlew.bat :common:compileJava :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL
- [x] `./gradlew.bat :common:test` 실행 → 검증: BUILD SUCCESSFUL (ProcessTest 등 common 전체 PASS)
- [ ] **사용자 작업 필요**: `./gradlew.bat :api:test --tests com.mo.swtp.proc.*` 실행 — Publisher 단위 + SseService 단위는 본 세션 PASS (4건). Integration·Concurrency·Listener·SseController 4건은 PostgreSQL + V6 적용 후 검증 → 검증: BUILD SUCCESSFUL (proc 패키지 테스트 7건 PASS)
- [x] check-errorcode-contract.sh 훅 실행 (AiDrvnModeEventPublisher.java 저장 시) → 검증: 차단 없음 (ErrorCode 미관련 파일이므로 무관 통과)
- [ ] **사용자 작업 필요**: `./gradlew.bat :common:build :api:build` 실행 — V6 DB 적용 + 통합 테스트 PASS 후 실행 → 검증: BUILD SUCCESSFUL (QClass 재생성 정상)

## 산출물
- [결과](../../../results/20260520/송수펌프제어분석-2번섹션/RESULT2.md) — `/dev:impl` 완료 후 작성
