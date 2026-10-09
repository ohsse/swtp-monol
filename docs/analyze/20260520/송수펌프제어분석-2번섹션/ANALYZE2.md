---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — 도메인 분석 (사이클 2)

## 작업 배경

- 직전 ANALYZE: [ANALYZE1](ANALYZE1.md) (status: approved, 2026-05-20)
- 직전 PLAN (폐기 예정): [PLAN1](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN1.md) (status: review)
- 사용자 추가 요구사항으로 PLAN1 의 핵심 결정 2건이 정정 필요해진 정상 사이클 재진입 (Fix Cycle 아님 — REVIEW 부재):
  1. **`proc_id` 외부 할당 PK 변경** — UUID 자동 생성 (DOM_ID_36) → 사용자 입력 코드값 (DOM_ID_50, 예: `PUMP_CONTROL`). 사업장 간 공통 코드값 정렬이 목적
  2. **SSE (Server-Sent Events) 실시간 전파 추가** — AI 운전모드 변경을 모든 subscribe 사용자에게 push (트랜잭션 AFTER_COMMIT 이벤트 → SseEmitter.send())
- 사용자 결정 사항 (plan 모드 AskUserQuestion 게이트 완료):
  - SSE 본 사이클 포함 (PLAN2 재작성)
  - proc_id 데이터 도메인: DOM_ID_50 재사용 + `Persistable<String>` 구현
  - SSE 인증: **1안 (JWT 필터 우회)** — `auth.jwt.exclude-paths` 추가 + 응답 payload 3필드 제한 (procId·aiDrvnModeCd·startDtm)
  - Emitter 수명: 타임아웃 30분 + heartbeat 15초
- 외부 산출물: `backend/image/송수펌프제어분석.png` (PLAN1 인용 — ANALYZE1 의 §작업 배경 동일)

## 회의록 (5인 회의 토픽 주도 — Round 1 종결)

### 안건 1: `proc_id` 외부 할당 PK 변경 — DOM_ID_50 정정
- 호출 에이전트: `wtp-glossary-manager` (표준 용어 갱신) + `wtp-dba-reviewer` (인덱스·UNIQUE 영향)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `tag_srl_no`(별도 DOM_TAG_SRL_NO_50)·`user_id`(DOM_ID_50) 선례 교차 검토. `proc_id` 는 SCADA 태그명 같은 의미 분리 필요 없는 내부 정책 코드값이므로 **DOM_ID_50 재사용**이 적합. 별도 데이터 도메인 신설 불필요. 코드값 형식 (대문자+언더스코어) 컨벤션은 **애플리케이션 레벨 검증** (Service 정규식 `^[A-Z][A-Z0-9_]*$`) 권장 — DDL CHECK 추가 시 값 패턴 변경마다 ALTER TABLE 부담
  - **wtp-dba-reviewer**: VARCHAR(36)→VARCHAR(50) 변경은 PostgreSQL 가변 길이 저장 방식 동일이라 인덱스 동작 정확성 무영향. 부분 UNIQUE 인덱스 `(proc_id) WHERE end_dtm IS NULL` 도 컬럼 길이 무관. 운영 데이터 0건 신규 도입이라 무중단 마이그레이션 부담 없음. `gen_random_uuid()` 제거로 PostgreSQL 13+/pgcrypto 가정 자체 삭제 가능 (PLAN1 가정 1건 정리). 외부 할당 PK 분포(코드값 vs UUID)는 BRIN 미적용 마스터/명세 테이블이라 영향 없음. **블로커 (높음) 2건**: (a) DDL 3곳 proc_id COMMENT `DOM_ID_36`→`DOM_ID_50` 수정 누락 시 `check-ddl-column-comment.sh` 정합성 훼손, (b) `Process` 엔티티 `Persistable<String>` 구현 필수 — PLAN2 작성 시 자동 해소
- Round 2: 불필요 (이견 없음)
- **결론**: `proc_id` 데이터 도메인 정정 — `DOM_ID_36` → `DOM_ID_50` + `Persistable<String>` 구현 의무. 코드값 형식 컨벤션은 Service 정규식. ANALYZE1 결정 1건 정정 (UUID 자동 생성 폐기)

### 안건 2: SSE 도입의 도메인 정합성 평가
- 호출 에이전트: `wtp-domain-expert` (도메인 룰 4영역 영향) + `wtp-backend-engineer` (이벤트 패턴·계층 책임)
- Round 1 답변 요약:
  - **wtp-domain-expert**: SSE 단방향 push 가 `ot-integration.md §5` AI 운전 모드 정책과 **충돌 없이 양립**. 본 사이클은 사용자 의도 (`ai_drvn_mod_cd`) 만 다루므로 강제 전환 정책 부재. SSE 는 읽기 전용 통지 채널 — 시스템 상태 `ai_mode_cd` 변경 주체 아님. AFTER_COMMIT 시점 보장으로 이력 영속화 완료 후 push (DB ↔ SSE payload 일관성). **블로커 (높음) 1건**: 내부망 전제 가정 ANALYZE2 미기재 시 `coding-discipline.md §1` 위반 — ANALYZE2 가정 섹션에 명시 의무 (자동 해소)
  - **wtp-backend-engineer**: AFTER_COMMIT 이 의미상 정확 — BEFORE_COMMIT 에서 send() 하면 트랜잭션 롤백 시 이미 나간 SSE 메시지 회수 불가. 계층 책임: `Service` → `publishEvent` → `Listener` → `SseService.send` 위임 구조 (Handler 에서 직접 SseEmitter 조작 시 계층 오염). `ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` 수용 가능 (구독자 소수 가정). `onError`·`onTimeout`·`onCompletion` 콜백은 SseService (Emitter 보관소) 책임 — 자기 정리 패턴
- Round 2: 불필요
- **결론**: SSE 도입은 도메인 룰 4영역과 양립. 계층 책임 = Service → Listener → SseService 위임. Emitter 보관소 자기 정리 패턴 의무. 내부망 전제 가정 §가정 섹션 명기

### 안건 3: 신규 표준 단어 등록 (SSE 인프라 도입)
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 4개 후보 판정 — (a) `event` **신규 등록**: 어근 충돌 없음, `DomainEventEntity` 클래스명과 추상화 계층 달라 의미 충돌 없음 (`type` 단어 vs `equip_type_cd` 공존 선례 동형), SSE 페이로드·이력 컬럼 재사용 가능. (b) `subscr` **신규 등록**: 어근 충돌 없음, 6자는 `format`·`branch` 풀네임 채택 선례 정합 (`subscribe` 9자는 `quality`(7자) 초과 부담), `subscr_id`·`subscr_dtm` 컬럼 재사용 가능. (c) `sse` **보류**: HTTP 프로토콜 명칭, DB 컬럼 직접 사용 사례 미확인 — DB 컬럼 등장 시 재평가. (d) `emitter` **거부**: 표준 단어 목적(DB 컬럼 조합 재료) 과 불일치, Java 프레임워크 클래스명 전용
- Round 2: 불필요
- **결론**: `event`·`subscr` 2건 신규 등록. `sse`·`emitter` 0건 등록. 신규 표준 데이터 도메인 0건. 비즈니스 도메인 약어 0건

### 안건 4: 인증 우회 결정의 보안 평가
- 호출 에이전트: `wtp-backend-engineer` (필터 우회·인프라 영향) + `wtp-domain-expert` (운영 데이터 노출 위험)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **블로커 (높음) 1건** — `AntPathMatcher` 는 `{procId}` 리터럴 패턴 미지원 (변수 처리 아닌 문자열 매칭). 반드시 `*` 와일드카드 사용 (`/api/proc/*/ai-mode/subscribe`). PLAN2 미명시 시 SSE 엔드포인트가 JWT 필터에 차단됨. Spring Boot 4 의 `PathPatternParser` 는 MVC 기본이나 `JwtAuthenticationFilter` 는 Servlet 필터 계층이라 `AntPathMatcher` 그대로 작동. Controller 진입 후 `Process.findById` 검증은 Service 책임 (PROC_NOT_FOUND 404)
  - **wtp-domain-expert**: 1안 (필터 우회) + payload 3필드 제한 + OT/IT 분리망 가정 결합 = **허용 가능 위험 수준**. SSE 는 제어 명령 발행 경로 없으므로 `ot-integration.md §5 ⚠️ 절대 금지` (인터록 우회) 미해당. **`coding-discipline.md §2.5` 면책 영역 비해당** — 보안 결정이지 정수장 안전 도메인 패턴 아님. 향후 쿠키 인증 통합 사이클 도입 시 `excludePaths` 제거 1줄로 롤백 가능 (읽기 전용 채널이라 도메인 안전 영향 없음). **블로커 (높음) 1건**: 내부망 전제 가정 ANALYZE2 미기재 (안건 2 와 동일 블로커, ANALYZE2 작성 시 자동 해소)
- Round 2: 불필요
- **결론**: 1안 채택. PLAN2 에 `*` 와일드카드 명시 의무 + Controller→Service procId 검증. ANALYZE2 §2.5 면책 비해당 명기 + §가정 섹션 내부망·payload 비민감화 명시

### 안건 5: PLAN2 구현 방향 (변경 API 트랜잭션 + 이벤트 발행)
- 호출 에이전트: `wtp-backend-engineer` (구현 방향) + `wtp-dba-reviewer` (트랜잭션·이벤트 순서)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `publishEvent` 호출은 데이터 변경 3 작업 (UPDATE→UPSERT→INSERT) 완료 직후, 트랜잭션 메서드 종료 직전. `AbstractDomainEventPublisher.publishAndClear()` 구현상 `publishEvent` 는 핸들러 예약만 수행 (실제 실행은 AFTER_COMMIT). 이벤트 페이로드 `record AiDrvnModeChangedEvent(String procId, AiDrvnModeCode aiDrvnModCd, LocalDateTime startDtm)` — Java 16+ record 불변성 적합. 패키지 `com.mo.swtp.proc.event` 는 User 도메인 `user.event.UserDeactivatedEvent` 선례와 동형
  - **wtp-dba-reviewer**: `@TransactionalEventListener(AFTER_COMMIT)` + `fallbackExecution = false` 기본값 → 트랜잭션 롤백 시 리스너 미실행 (SSE 미발행 자동 보장). `publishEvent` 자체는 동기 In-memory 호출이라 트랜잭션 길이 추가 비용 나노초 수준 (`query-tuning.md §1` "장시간 트랜잭션 최소 범위 유지" 무영향). 다중 subscriber 순회는 AFTER_COMMIT 리스너 = 트랜잭션 외부라 VACUUM 지연·테이블 팽창 영향 없음. **PLAN2 명시 의무**: `phase = TransactionPhase.AFTER_COMMIT` 명시 (기본값이나 가독성·감사 추적), `fallbackExecution = false` 동작 명문화, SseEmitter.send() 실패 시 Emitter 만료 처리 (`complete()`·`completeWithError()`) 누락 시 메모리 누수 가능
- Round 2: 불필요
- **결론**: 트랜잭션 흐름 6단계 확정 — (1) SELECT FOR UPDATE → (2) UPDATE prev end_dtm → (3) UPSERT master → (4) INSERT new history → (5) `applicationEventPublisher.publishEvent(new AiDrvnModeChangedEvent(...))` → (6) 트랜잭션 commit → (7) `@TransactionalEventListener(AFTER_COMMIT)` 실행 → SseEmitter.send()

### 안건 6: 트랜잭션 이벤트 패턴 — 옵션 A vs 옵션 B
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **옵션 B 권장** — Service 가 `ApplicationEventPublisher` 직접 주입 후 호출. 옵션 A (`AiDrvnMode` `DomainEventEntity` 상속 + `AiDrvnModeEventPublisher` 추가) 는 `coding-discipline.md §2` "일회성 코드를 위해 추상화 계층 만들지 않는다" 위반 (User 도메인의 비활성화·삭제 두 이벤트 경로 + Repository.delete 연동 같은 정당화 부재). `AiDrvnModeService.changeAiDrvnMode` 는 단일 변경 경로, 삭제 이벤트 없음. 옵션 B 채택 시 파일 추가 0건. **블로커 (중간) 1건**: 옵션 A 채택 시 복잡도 과잉
- Round 2: 불필요
- **결론**: 옵션 B 채택. `AiDrvnModeService` 에 `ApplicationEventPublisher` `@RequiredArgsConstructor` 주입 + 직접 호출. `DomainEventEntity` 상속 미적용 사유 1행 주석 (`coding-discipline.md §2` 인용)

### 안건 7: 신규 패키지 구조
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **후보 1 (`com.mo.swtp.proc` 단일 패키지 내 `event/`·`sse/` 서브패키지)** 권장. `common.sse` 분리는 (a) `common` 모듈에 Service·@RestController 등 실행 계층 금지 (모듈 경계 규칙 위반), (b) `coding-discipline.md §2` "요청되지 않은 유연성 고려 금지" 위반 (단일 사용처). 추후 alarm·diag 등 3건 이상 누적 시 별도 `/dev:analyze` 에서 공통 인프라 분리 결정 (`api-patterns.md` 검색 조건 DTO 3건 기준 선례 정합). `event/` 서브패키지 = `AiDrvnModeChangedEvent` record + `AiDrvnModeChangedEventHandler`. `sse/` 서브패키지 = `AiDrvnModeSseService` + `AiDrvnModeSseController`
- Round 2: 불필요
- **결론**: 후보 1 채택. `com.mo.swtp.proc` 단일 패키지 + `event/`·`sse/` 서브패키지

## 표준 사전 카탈로그

> 본 사이클은 **상위 사이클 (ANALYZE1) 의 표준 사전 결정 + 본 사이클 추가/정정** 누적이다. ANALYZE1 의 신규 표준 단어 (`drive`·`start`·`end`) + 표준 데이터 도메인 (없음) + 표준 용어 (`proc_id`·`proc_nm`·`ai_drvn_mod_cd`·`start_dtm`·`end_dtm`·`ai_drvn_mod_id`) 는 이미 룰 파일에 반영 완료 (ANALYZE1 룰 갱신 지시서 [x]). 본 ANALYZE2 는 그 위에 추가/정정만 명시한다.

### 신규 표준 단어 (ANALYZE2 추가)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `event` | 이벤트 | 신규 | `standard-words.md` 미등록. `DomainEventEntity` 클래스명과 추상화 계층 달라 의미 충돌 없음 (`type` 단어 vs `equip_type_cd` 공존 선례 동형). SSE 페이로드·이력 컬럼 재사용 가능 |
| `subscr` | 구독 | 신규 | `standard-words.md` 미등록. 어근 충돌 없음. 6자 — `format`·`branch` 풀네임 채택 선례 정합 (`subscribe` 9자는 `quality`(7자) 초과 부담). `subscr_id`·`subscr_dtm` 컬럼 조합 재료 후보 |

> 거부/보류: `sse` (보류 — HTTP 프로토콜 명칭, DB 컬럼 직접 사용 사례 미확인), `emitter` (거부 — 표준 단어 목적 불일치)

### 신규 표준 데이터 도메인

없음 (DOM_ID_50 재사용 — `tag_srl_no`·`user_id` 선례 동형)

### 신규 표준 용어 (ANALYZE2 정정)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `proc_id` | `proc`(비즈니스 도메인) + `id` | `DOM_ID_50` (**정정** — ANALYZE1 `DOM_ID_36` 폐기) | 유사 충돌·정정 | 외부 할당 PK (대문자+언더스코어 코드값, 예: `PUMP_CONTROL`). `user_id`·`tag_srl_no` 선례 동일. `Persistable<String>` 구현 의무. 사용 테이블: `proc_m` (PK), `ai_drvn_mod_p`·`ai_drvn_mod_h` (FK) |

## 신규 엔티티/DB 컬럼 (ANALYZE2 추가/변경)

### 엔티티 변경

| 엔티티/테이블 | 변경 사항 |
|------------|---------|
| `Process` / `proc_m` | PK 정정: `@GeneratedValue(GenerationType.UUID)` 제거 + `implements Persistable<String>` 추가 + `getId()` override. `proc_id` 컬럼 `VARCHAR(36)` → `VARCHAR(50)`, COMMENT `DOM_ID_36` → `DOM_ID_50, 외부 할당 PK` |
| `AiDrvnMode` / `ai_drvn_mod_p` | `proc_id` FK 컬럼 `VARCHAR(36)` → `VARCHAR(50)`, COMMENT 동일 갱신 |
| `AiDrvnModeHistory` / `ai_drvn_mod_h` | `proc_id` 컬럼 `VARCHAR(36)` → `VARCHAR(50)`, COMMENT 동일 갱신 |

### 신규 클래스 (Java)

| 패키지 / 클래스 | 역할 |
|-------------|------|
| `com.mo.swtp.proc.event.AiDrvnModeChangedEvent` | record 이벤트 — `(String procId, AiDrvnModeCode aiDrvnModeCd, LocalDateTime startDtm)` |
| `com.mo.swtp.proc.event.AiDrvnModeChangedEventHandler` | `@TransactionalEventListener(phase = AFTER_COMMIT)` — `SseService.send(procId, payload)` 위임 |
| `com.mo.swtp.proc.sse.AiDrvnModeSseService` | Emitter 보관소 `ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` + 등록·제거·send 메서드. heartbeat 스케줄 (15초). onError/onTimeout/onCompletion 자기 정리 |
| `com.mo.swtp.proc.sse.AiDrvnModeSseController` | `GET /api/proc/{procId}/ai-mode/subscribe` SSE 엔드포인트 (timeout 30분) |

### 시드 데이터 변경

| 변경 | 내용 |
|------|------|
| `proc_m` INSERT | `gen_random_uuid()::TEXT` → `'PUMP_CONTROL'` 고정값. pgcrypto 가정 제거 |

### 설정 변경

| 파일 | 변경 |
|------|------|
| `application.yml` 또는 `application-common.yml` | `auth.jwt.exclude-paths` 에 `/api/proc/*/ai-mode/subscribe` 패턴 추가 (`*` 와일드카드 — `{procId}` 리터럴 미지원) |

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 |
|---------|------|
| ANALYZE1 의 `proc_id` 결정 (DOM_ID_36 UUID) vs 사용자 요구 (DOM_ID_50 외부 할당) | ANALYZE1 결정 정정 — `standard-terms.md` 의 `proc_id` 행 데이터 도메인 + 비고 갱신. 룰 갱신 지시서에 명시 |
| 본 사이클 SSE 인증 우회 결정 vs `JwtAuthenticationFilter` 의 모든 API 토큰 검증 원칙 | `auth.jwt.exclude-paths` 메커니즘으로 이미 우회 경로 존재 (필터 자체 변경 불필요). payload 3필드 제한으로 노출 위험 완화 |
| `AntPathMatcher` `{procId}` 리터럴 미지원 | `*` 와일드카드 사용 (`/api/proc/*/ai-mode/subscribe`) — PLAN2 명시 의무 |
| 옵션 A (DomainEventEntity 상속) 의 기존 User 패턴 일관성 vs 옵션 B 단순성 | 옵션 B 채택 — 본 사이클 단일 이벤트 경로 + 삭제 이벤트 없음 = `coding-discipline.md §2` 단순성 우선. User 패턴은 비활성화·삭제 2 경로 + Repository.delete 연동 정당화 |

## PLAN2 으로 전달할 결정 사항

- 도메인 모델 변경: 위 §신규 엔티티/DB 컬럼 표 적용
- DB 설계 변경 (PLAN1 → PLAN2):
  - 3 테이블 `proc_id` 컬럼 `VARCHAR(50)` 정렬 (DDL 3곳)
  - COMMENT 3곳 `DOM_ID_36` → `DOM_ID_50, 외부 할당 PK` 갱신
  - 시드 INSERT `gen_random_uuid()` → `'PUMP_CONTROL'` 고정값
  - PLAN1 가정 항목 "PostgreSQL 13+/pgcrypto 전제" 삭제 (pgcrypto 의존성 0건)
- 신규 SSE 인프라:
  - 클래스 4종 (`AiDrvnModeChangedEvent`·`AiDrvnModeChangedEventHandler`·`AiDrvnModeSseService`·`AiDrvnModeSseController`)
  - `AiDrvnModeService.changeAiDrvnMode` 트랜잭션 마지막 단계에 `applicationEventPublisher.publishEvent(new AiDrvnModeChangedEvent(...))` 추가
  - `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 명시 (기본값이나 가독성)
  - `application.yml` `auth.jwt.exclude-paths` 패턴 1줄 추가 (`*` 와일드카드)
  - Emitter 보관소 자기 정리 패턴 (onError/onTimeout/onCompletion 콜백)
- 패키지: `com.mo.swtp.proc` 단일 패키지 + `event/`·`sse/` 서브패키지

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 내부망 (OT/IT 분리, VPN 또는 물리 네트워크 격리) 전제 가정 — 외부 인터넷 노출 환경에서는 SSE 인증 우회가 운전 상태 노출 위협. 본 결정의 전제 조건으로 명시 | 가정 | `coding-discipline.md §1` 의무 — wtp-domain-expert 블로커 해소 |
| SSE 응답 payload 비민감화 3필드 제한 (`procId`+`aiDrvnModeCd`+`startDtm`) 의 충분성 — `aiDrvnModeCd` 값만으로 운영 행동 패턴 추론 가능 여부를 보안 담당자와 확인 필요 | 미해결 | 운영 배포 전 보안 담당자 확인 |
| 향후 쿠키 인증 통합 사이클 도입 시 `auth.jwt.exclude-paths` 패턴 제거만으로 롤백 가능 — 제거 후 기존 SSE 구독자는 연결 종료 후 재인증 필요 (클라이언트 재연결 로직 의무). 도메인 안전 영향 없음 (읽기 전용 채널) | 가정 | 롤백 경로 명시 |
| 단일 인스턴스 가정 (`multi-tenant.md` 지자체별 단일 backend) — 멀티 인스턴스 환경에서는 SseEmitter 가 인스턴스 로컬 상태라 같은 procId 구독 클라이언트가 다른 인스턴스 연결 시 push 누락. 본 사이클은 단일 인스턴스 가정으로 진행, 향후 Scale-out 사이클 분리 (Redis pub/sub 등) | 가정 | wtp-domain-expert·wtp-dba-reviewer 공통 지적 |
| AFTER_COMMIT 발화 실패 시 (SseEmitter.send IOException 등) 정책 — 이력은 이미 커밋되어 영속화 완료. push 실패 무시 vs 재시도 중 결정 필요. 본 사이클은 무시 (Emitter 자기 정리 + 클라이언트 재연결 의존) | 미해결 → 결정 | wtp-domain-expert 참고 사항. 본 사이클 결정: **무시** (Emitter onError 콜백에서 보관소 제거만 수행) |
| `coding-discipline.md §2.5` 면책 영역 비해당 명시 — SSE 인증 우회는 보안 결정이며 정수장 안전 도메인 패턴 (인터록·알람·운전 모드·이력 기록) 면책 영역 아님. 코드 주석 인용 근거 사용 금지 | 결정 | wtp-domain-expert 권고 |
| 옵션 B 채택 (`ApplicationEventPublisher` 직접 주입) 정당화 — 단일 이벤트 경로 + 삭제 이벤트 없음. User 도메인 옵션 A 패턴 (비활성화·삭제 2 경로) 과 사용 맥락 다름. `coding-discipline.md §2` 단순성 적용 | 결정 | wtp-backend-engineer 권고 |
| Emitter 보관소 자료구조 — `ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` 채택. 구독자 수 증가 (예: 100+ subscriber/proc) 시 `CopyOnWriteArrayList` 쓰기 비용 (배열 복사) 부담 → `ConcurrentHashMap` + `Collections.newSetFromMap` 교체 검토 | 가정 | 본 사이클 구독자 소수 가정 (운영자 1~10명/proc) — 충분 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `Process` 엔티티가 `Persistable<String>` 구현 + `getId()` override 작성 + `@GeneratedValue` 미적용 | grep "implements Persistable<String>" Process.java 매칭 + `./gradlew :api:test --tests ProcessTest` 신규 PASS |
| 3 DDL 컬럼 `proc_id` VARCHAR(50) 정렬 + COMMENT `DOM_ID_50` 명기 + 시드 `'PUMP_CONTROL'` | grep "proc_id VARCHAR(50)" V{N}.sql 3건 매칭 + `check-ddl-column-comment.sh` 훅 PASS |
| 변경 API 트랜잭션 (`AiDrvnModeService.changeAiDrvnMode`) 가 `applicationEventPublisher.publishEvent(AiDrvnModeChangedEvent)` 호출 — INSERT new history 직후 위치 | `./gradlew :api:test --tests AiDrvnModeServiceTest` 신규 PASS (이벤트 발행 verify) |
| `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 명시 + 트랜잭션 롤백 시 SSE 미발행 | 통합 테스트 `AiDrvnModeEventTransactionRollbackTest` — 변경 API 강제 예외 후 SseService.send 미호출 verify |
| `auth.jwt.exclude-paths` 에 `/api/proc/*/ai-mode/subscribe` 패턴 추가 + Controller 진입 시 `Process.findById` 검증 | application.yml grep + 통합 테스트 `SseSubscribeAuthBypassTest` — 토큰 없이 200 응답 + 존재 안 하는 procId → 404 PROC_NOT_FOUND |
| Emitter 보관소 자기 정리 — onError/onTimeout/onCompletion 콜백 등록 + 보관소 제거 verify | 단위 테스트 `AiDrvnModeSseServiceTest` — emitter 만료 후 ConcurrentHashMap 에서 제거 검증 |
| SSE subscribe 통합 테스트 — MockMvc + AsyncContext + 30분 timeout + 15초 heartbeat ping | 통합 테스트 `AiDrvnModeSseIntegrationTest` — subscribe 후 변경 API 호출 시 SSE 이벤트 수신 verify |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 사이클은 AI 운전모드 표출/이력/변경 + SSE 실시간 전파 — 임계값·전이·복귀 조건 부재. 알람 이력 (`alarm_h`) 미관여 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 본 사이클은 운전모드 사용자 의도 변경 + SSE 통지 — PLC 제어 명령 발행 경로 부재 (`pump_interlock_p` 미관여). `ScadaOutboundPort` 백지화 영향 외 |
| AI 운전 모드 (`ot-integration.md §5`) | 해당 | 본 사이클 핵심 — `ai_drvn_mod_cd` (사용자 의도 단일축, AI/AI_RECOMD/AI_ANLS) 변경 API + 이력. **시스템 상태 `ai_mode_cd` 강제 전환 정책 본 사이클 명시 제외** (별도 사이클로 분리). SSE 는 사용자 의도 변경 push 만 — 시스템 상태 영역 무관 |
| 이력 기록 의무 (`ot-integration.md §5`) | 해당 | `ai_drvn_mod_h` UPDATE end_dtm + INSERT new row 트랜잭션 원자성. 변경 시점 직전 행 종료 + 신규 행 시작. BaseEntity 4 (`rgstr_dtm/updt_dtm/rgstr_id/updt_id`) 자동 주입 + `end_dtm` UPDATE 허용 (`db/indexing-and-migration.md §4.3` 변경 추적 컬럼 존재 시 BaseEntity 4 상속 허용 패턴). **SSE AFTER_COMMIT 이 이력 영속화 완료 후 push 보장 — DB ↔ SSE payload 일관성 구조적 보장** |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `proc_id` 행 정정 (DOM_ID_36 → DOM_ID_50, 비고 "UUID 자동 생성" → "외부 할당 PK — `Persistable<String>` 구현 필수, 대문자+언더스코어 코드값 형식 예: PUMP_CONTROL") — ANALYZE1 결정 폐기 (송수펌프제어분석-2번섹션 ANALYZE2 안건 1, 2026-05-20)
- [x] `swtp/.claude/rules/dict/standard-words.md` — `event` (이벤트, 풀네임 `event`, 기본 데이터 도메인 미지정) 신규 등록 — SSE 페이로드 타입·이력 컬럼 조합 재료 (송수펌프제어분석-2번섹션 ANALYZE2 안건 3, 2026-05-20)
- [x] `swtp/.claude/rules/dict/standard-words.md` — `subscr` (구독, 풀네임 약어 `subscribe` 6자 절삭, 기본 데이터 도메인 미지정) 신규 등록 — `subscr_id`·`subscr_dtm` 컬럼 조합 재료 후보. `format`·`branch` 풀네임 채택 선례 정합 (송수펌프제어분석-2번섹션 ANALYZE2 안건 3, 2026-05-20)

> ANALYZE1 의 14건 룰 갱신 지시서는 이미 [x] 완료 상태 (실제 룰 파일 수정 완료). ANALYZE2 는 추가/정정 3건만 명시한다 (proc_id 정정 1건 + event·subscr 신규 등록 2건).

## 산출물

- [PLAN2 (PLAN1 폐기 후 작성)](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN2.md)
