---
status: approved
created: 2026-06-04
updated: 2026-06-04
---
# 전력피크분석-1번섹션 — 전력피크 목표값 수정·저장 + SSE 실시간 전파

## 목적

전력피크 분석 화면 1번 섹션 "피크치 설정" 박스의 백엔드를 구현한다. 운전원이 **목표 피크 전력값(kW)** 하나를 입력·저장하면, 같은 화면을 구독 중인 **모든 사용자**가 SSE 단방향 push 로 즉시 변경을 인지한다. 값은 정수장(테넌트) 전체가 공유하는 **시스템 전역 단일 값**이다.

- 신규 설정 테이블 1개 (`opt_peak_target_p`) 신설 — 현재 저장 위치 부재.
- 조회(GET) / 수정·저장(PUT) / 구독(SSE GET) 3 엔드포인트.
- 직전 사이클(송수펌프제어분석-2번섹션, proc 도메인 AI 운전모드)에서 확립한 `DomainEventEntity` + 전용 `EventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `SseService` 패턴을 **clean-break 병렬 복제**한다(재사용·일반화 금지 — 사이클 간 자산 자동 원용 금지 메모리 정합).

> 선행 분석: [ANALYZE1](../../../analyze/20260604/전력피크분석-1번섹션/ANALYZE1.md) (status: approved). 4층 사전 갱신 4건 반영 완료(`peak`·`target` 표준 단어, `peak_cd`·`target_peak_elpwr` 표준 용어).

## 배경

ANALYZE1 5인 회의 결정을 그대로 계승한다.

| 항목 | 결정 (ANALYZE1) |
|------|----------------|
| 적용 범위 | 시스템 전역 단일 값 → 단일 행, SSE 전역 채널 1개 |
| 변경 이력 | 마스터만 갱신 + 감사메타 (이력 `_h` 없음) |
| 도메인 배치 | 기존 `opt` 도메인(`com.mo.swtp.opt`) 확장 — opt 도메인 첫 write 경로(Controller/Service/ErrorCode/event/sse) |
| 테이블 | `opt_peak_target_p` (`_p` 명세/설정값), PK `peak_cd`(`DOM_CODE_20`) 고정 코드값 `'PEAK_TARGET'` + `CHECK` 제약 |
| 값 컬럼 | `target_peak_elpwr` (`DOM_QTY_15_4`, NOT NULL) |
| 단일 행 강제 | 고정 코드값 PK + `CHECK (peak_cd = 'PEAK_TARGET')` (DB 레벨 2행 차단) |
| 동시성 | DDL **시드 1행**으로 행 부재 제거 → 저장은 항상 UPDATE → `SELECT FOR UPDATE` 직렬화 + JPA `change()`→`registerEvent()` 도메인 이벤트 정상 발화 |

> ANALYZE1 "비해당 4건 + 신규 테이블" §5b 게이트 형식 충돌은 PLAN 부록 §게이트 판단 기록에 투명 기재(실제 `wtp-domain-expert` 호출로 substantive 점검 완료, 순수 설정값 테이블).

## 범위

### 포함
- `opt` 도메인 신규: 엔티티(`PeakTarget`), 이벤트 record(`PeakTargetChangedEvent`), Publisher, Listener, SseService, SseController, Service, Repository, DTO 2종, ErrorCode.
- DDL `V5_1__opt_patch.sql` (운영본) + `docs/ddl/opt.sql` 갱신(SSOT 사본) — 테이블 + `CHECK` + 시드 1행 + 전 컬럼 COMMENT.
- 인증 우회 경로 1줄 추가(`application-common.yml`).
- 단위·통합 테스트.

### 제외 사항
- 변경 이력 `_h` 테이블 (요건 잠금 — 마스터만 갱신).
- 목표값을 알람 임계값·피크 제어 명령에 사용하는 로직 (추후 사이클 — 본 사이클은 표출·저장·전파만).
- 멀티 인스턴스 SSE 브로드캐스트(Redis pub/sub 등) — 지자체별 단일 인스턴스 가정.
- 쿠키 기반 SSE 인증 통합 — proc 선례 동일 인증 우회 채택, 통합은 별도 사이클.

## 구현 방향

### 모듈 경계 (backend-engineer 블로커 해소 — ANALYZE1 안건 3)
- **`common` 모듈**: 엔티티 `PeakTarget`, 이벤트 record `PeakTargetChangedEvent` 만 (common→api 의존 불가).
- **`api` 모듈**: `PeakTargetEventPublisher`·`PeakTargetChangedEventListener`·`PeakTargetSseService`·`PeakTargetSseController`·`PeakTargetService`·`PeakTargetRepository`·`PeakTargetDto`·`PeakTargetUpsertDto`·`OptErrorCode`.

### proc 대비 단순화 (시드 + UPDATE-only)
시드 행이 항상 존재하므로 proc 의 6단계 트랜잭션 중 INSERT(`create`/`createAndPublish`)·부분 UNIQUE 인덱스·`DataIntegrityViolationException` 동시성 충돌 분기가 **불필요**하다. 저장 흐름은 3단계로 축약:

```
1. PeakTargetRepository#findByPeakCdForUpdate('PEAK_TARGET')  ← SELECT FOR UPDATE (시드 보장 → 항상 존재)
2. entity.change(targetPeakElpwr)                              ← registerEvent(PeakTargetChangedEvent) 축적, dirty checking UPDATE
3. peakTargetEventPublisher.changeAndPublish(entity)           ← publishAndClear
   → commit → AFTER_COMMIT Listener → sseService.broadcast()
```

- 시드 행이 부재한 비정상 상태(배포 누락·테스트 픽스처 결손)는 `orElseThrow(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED)`(500)로 명시적 신호 — silent self-heal(`create`) 대신 배포 오류 표면화(§2 단순성: 정상 경로에 dead `create` 경로 미생성).
- `PeakTarget` 은 `create()` 정적 팩토리 불필요(시드는 SQL INSERT, JPA 영속 경로 미경유). `change(BigDecimal)` 변경 메서드만 보유.

### SSE 인프라 (전역 단일 채널 — §2 단순성)
- 보관소: proc 의 `ConcurrentHashMap<procId, List<SseEmitter>>` → 전역 단일 값이므로 **`CopyOnWriteArrayList<SseEmitter>` 단일 리스트**로 단순화.
- subscribe / broadcast / 15초 heartbeat(`@Scheduled`) / 30분 timeout / onCompletion·onTimeout·onError 자기정리 — proc 동작 동일.
- **`@EnableScheduling` 신규 config 미생성** — proc `SseConfig` 가 앱 전역 1회 활성(ANALYZE1 안건 3: opt 전용 config 신설 금지). opt `@Scheduled` 가 기존 활성에 편승.
- payload 비민감화 — 이벤트 record 는 `targetPeakElpwr`·`updtDtm` 2 필드만(사용자·시설 운영 데이터 미포함).

### API (CommonController + Swagger)
| HTTP | 경로 | 용도 | 응답 |
|------|------|------|------|
| `GET` | `/api/opt/peak-target` | 현재 목표 피크치 조회 | `CommonResponseDto<PeakTargetDto>` |
| `PUT` | `/api/opt/peak-target` | 목표 피크치 수정·저장 → SSE 전파 | `CommonResponseDto<PeakTargetDto>` |
| `GET` | `/api/opt/peak-target/subscribe` | SSE 구독 (`text/event-stream`) | `SseEmitter` |

- `PeakTargetController extends CommonController`, `@Tag(name = "11. 전력피크 목표값")`(번호는 impl 시 기존 충돌 확인 후 확정). SSE 는 응답 타입이 달라 `PeakTargetSseController` 분리(proc 선례).
- 구독 경로는 **고정 경로**(path variable 없음) → `auth.jwt.exclude-paths` 에 `/api/opt/peak-target/subscribe` 와일드카드 없이 그대로 등록.

## 도메인 모델

| 엔티티/테이블 | 역할 | 주요 필드 |
|-------------|------|----------|
| `PeakTarget` (`common`, `com.mo.swtp.opt.domain`) | 전역 목표 피크 전력값 단일 행 엔티티 | `peakCd`(String, `@Id`, 고정 `'PEAK_TARGET'`) · `targetPeakElpwr`(BigDecimal) · BaseEntity 감사 4 |
| `PeakTargetChangedEvent` (`common`, `com.mo.swtp.opt.event`) | 변경 도메인 이벤트(record) | `targetPeakElpwr`(BigDecimal) · `updtDtm`(LocalDateTime) |
| `PeakTargetDto` (`api`, `com.mo.swtp.opt.dto`) | 조회·저장 응답 | `targetPeakElpwr` + 상속 메타 4(`BaseAuditResponseDto`) |
| `PeakTargetUpsertDto` (`api`, `com.mo.swtp.opt.dto`) | 저장 요청 | `targetPeakElpwr`(BigDecimal, `@NotNull`·`@Positive`) |
| `OptErrorCode` (`api`, `com.mo.swtp.opt.exception`) | opt 도메인 첫 ErrorCode | `PEAK_TARGET_NOT_INITIALIZED(500)` |

- `PeakTarget extends DomainEventEntity implements Persistable<String>` — 외부 할당 PK 엔티티 패턴(`getId()` override, `isNew()` BaseEntity 위임). 상수 `public static final String PEAK_TARGET_CD = "PEAK_TARGET";`.
- `PeakTargetDto` 는 PK(`peak_cd`, 항상 동일 상수)를 응답에 노출하지 않음(클라이언트 무의미) — `targetPeakElpwr` + 감사 메타만.
- `PeakTargetEventPublisher extends AbstractDomainEventPublisher<PeakTarget>` — `changeAndPublish(entity)` → `super.publishAndClear(entity)`.

## DB 설계 변경

### 변경 대상
- **신규 테이블** `opt_peak_target_p` (단일 전역 행, 시계열 아님 → 파티션·BRIN·추가 인덱스 불요).

| 컬럼 | 타입 | NULL | 비고 |
|------|------|------|------|
| `peak_cd` | `VARCHAR(20)` | NOT NULL | PK, `CHECK (peak_cd = 'PEAK_TARGET')` |
| `target_peak_elpwr` | `NUMERIC(15,4)` | NOT NULL | 목표 피크 전력값 (kW) |
| `rgstr_dtm` / `updt_dtm` | `TIMESTAMP` | NOT NULL | BaseEntity 4 표준 라벨 |
| `rgstr_id` / `updt_id` | `VARCHAR(50)` | NOT NULL | BaseEntity 4 표준 라벨 |

### 마이그레이션 전략
- `V5__opt.sql` 동결 → 신규 변경은 `common/src/main/resources/db/migration/V5_1__opt_patch.sql` 분리(`indexing-and-migration.md §5.4`).
- `backend/docs/ddl/opt.sql` 하단에 `CREATE TABLE` + 시드 누적(SSOT 사본 — 양쪽 동일 커밋 의무 `§5.3`).
- 전 컬럼 `COMMENT ON COLUMN` 작성(`check-ddl-column-comment.sh` 차단 대상), BaseEntity 4 표준 라벨 사용.
- **시드 1행**: `INSERT INTO opt_peak_target_p (peak_cd, target_peak_elpwr, rgstr_dtm, updt_dtm, rgstr_id, updt_id) VALUES ('PEAK_TARGET', 0, now(), now(), 'system', 'system');`
- **무중단**: 신규 테이블 + 시드 INSERT 만(기존 테이블 ALTER 없음) → 락·백필 무관.

### 시드 초기값 결정 (ANALYZE1 미해결 → PLAN 확정)
- `target_peak_elpwr` 시드 = **`0`** ("미설정" sentinel). 근거: 이미지의 900 은 사업장별 운영값이라 스키마에 baking 시 멀티테넌트 위반(각 정수장 목표치 상이). 운전원이 PUT 으로 실제값 입력 전까지 0 유지. `PeakTargetUpsertDto` 가 `@Positive` 로 저장값을 항상 양수 강제 → `0` 은 "미설정" 으로만 회수 가능(의미 충돌 없음). NOT NULL 유지(DBA 2차 승인 — `min_req_prsr` 선례).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 목표값 저장 시 `PeakTargetChangedEvent` 1건 발행 | `PeakTargetService` 단위 테스트 — Mockito 로 `peakTargetEventPublisher.changeAndPublish` 1회 호출 verify |
| 잘못된 값(0·음수·null) 저장 거부 | `PeakTargetUpsertDto` Bean Validation — `@NotNull`·`@Positive` 위반 시 400 (RestApiAdvice MethodArgumentNotValidException 매핑) |
| 시드 부재 시 명시적 오류 | `PeakTargetService` 단위 테스트 — `findByPeakCdForUpdate` Optional.empty → `RestApiException(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED)` |
| 다중 구독자 broadcast 전파 + 자기정리 | `PeakTargetSseService` 단위 테스트 — emitter 2건 등록(count=2) 후 broadcast 시 2건 send, send 실패 emitter 자동 제거(count 감소) |
| 저장→조회 왕복 일관성 | 통합 테스트(`@SpringBootTest(NONE)`) — PUT 저장 후 GET 시 변경값 반환(시드 행 기준) |
| SSE 컨트롤러 인증 우회 + 스트림 타입 | 통합 테스트 — 토큰 없이 200 + `Content-Type: text/event-stream` |
| AFTER_COMMIT 발화 | `PeakTargetChangedEventListener` 테스트 — commit 후 `sseService.broadcast` 호출 |
| 전체 빌드 | `./gradlew.bat :common:build` → `:api:build` BUILD SUCCESSFUL (QClass 재생성 포함) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 엔티티·이벤트 record 는 `common`, 나머지(Publisher·Listener·Sse·Service·Repository·DTO·ErrorCode)는 `api` | 가정 → 결정 | backend-engineer 블로커 해소(common→api 빈 의존 불가) |
| 시드 행 항상 존재 → 저장 UPDATE-only, `create` 경로 미생성 | 가정 → 결정 | 시드 부재는 `PEAK_TARGET_NOT_INITIALIZED`(500)로 표면화 |
| 시드 `target_peak_elpwr` = 0 (미설정 sentinel) | 미해결 → 결정 | 멀티테넌트 정합 — 운영값 baking 회피, `@Positive` 가 0 저장 차단 |
| `@EnableScheduling` proc `SseConfig` 앱 전역 활성에 편승(opt 전용 config 미생성) | 가정 → 결정 | ANALYZE1 안건 3. proc 도메인 제거 시 opt 스케줄 영향 — 단일 활성 트레이드오프 수용 |
| SSE 구독자 수 수십 이하 (`CopyOnWriteArrayList` 쓰기 복사 비용 무시) | 가정 | 정수장 운영자 단말 수 개 |
| 단일 backend 인스턴스 (지자체별 단일 배포) | 가정 | SSE 인메모리 보관소 전제. 멀티 인스턴스 시 Redis pub/sub 별도 사이클 |
| 구독 고정 경로 인증 우회 (proc 선례 동일, payload 비민감화) | 가정 → 결정 | `/api/opt/peak-target/subscribe` 와일드카드 불요(고정 경로) |
| 목표값을 본 사이클에서 알람 임계값·피크 제어로 사용 안 함 | 가정 | 추후 편입 시 도메인 4영역 재점검(ANALYZE1 비해당 판정 무효화 조건) |

분류값: 가정 / 미해결 → 결정

## 부록: 도메인/DB 검토 결과

- **wtp-domain-expert**: 블로커 0건, 권고 0건, 참고 2건 — ANALYZE1 4영역 비해당 판정이 PLAN 신규 엔티티·테이블·저장 흐름 기준에서도 유효 확인. 단일 전역 행 + 고정 코드값 PK 패턴·SSE payload 2필드 비민감화 모두 도메인 정합. (참고: 이력 기록 비해당 사유 문구 보완, 도메인 모델 표의 DTO 혼재 가독성 — 차단 사유 아님)
- **wtp-dba-reviewer**: 블로커 0건, 권고 2건, 참고 1건 — 시드 + UPDATE-only + `SELECT FOR UPDATE` 동시성 직렬화 구조적 성립, CHECK + 고정 PK 2행 차단 DB 레벨 완결, `V5_1__opt_patch.sql` 신규 테이블+시드 무중단(ALTER 없음), 추가 인덱스 불요 판정 타당.

### 권고 반영 (TASK 단계 의무 명기)
1. **BaseEntity 4 NOT NULL 선언 통일** (DBA 권고 중간) → **NOT NULL 채택 확정**. 근거: `BaseEntity` 의 `@Column(nullable = false)` JPA 매핑과 정합, `predc_1m_h` DDL NOT NULL 선례 동형, 시드 SQL 이 4컬럼 명시 제공(`now()`·`'system'`). TASK DDL 체크박스에 NOT NULL 명시.
2. **`PESSIMISTIC_WRITE` 트랜잭션 최소 범위** (DBA 권고 중간) → 저장 메서드는 `@Transactional`(기본 READ_COMMITTED) + `findByPeakCdForUpdate`(`PESSIMISTIC_WRITE`) 조합, 입력 검증 완료 후 즉시 커밋되어 락 보유 시간 최소화. TASK 에 명시.
3. **시드 `0 = 미설정` 의미 노출** (DBA 참고 / 양 에이전트 공통) → `PeakTargetDto.targetPeakElpwr` `@Schema(description = "... (0 = 미설정 — 운전원 최초 저장 전)")` 명기. TASK 에 반영, `/dev:spec` SPEC 에도 전파.

## 예상 산출물
- [태스크](../../../tasks/20260604/전력피크분석-1번섹션/TASK1.md)
