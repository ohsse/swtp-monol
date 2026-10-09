---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 표출·이력 관리 + SSE 실시간 전파 (사이클 2)

## 목적

PLAN1 폐기 후 ANALYZE2 (5인 회의 사이클 2) 결정에 따라 다음 두 변경을 통합한 재계획.

1. **`proc_id` 외부 할당 PK 변경** — UUID 자동 생성 (DOM_ID_36) → 사용자 입력 코드값 (DOM_ID_50, 예: `PUMP_CONTROL`). 사업장 간 공통 코드값 정렬
2. **SSE (Server-Sent Events) 실시간 전파 추가** — AI 운전모드 변경을 모든 subscribe 사용자에게 push (트랜잭션 AFTER_COMMIT 이벤트 → SseEmitter.send())

PLAN1 의 기능 범위 (표출 + 이력 + 변경 API) 와 마스터+이력 분리 (옵션 A) 결정은 유지된다.

## 배경

- [ANALYZE2](../../../analyze/20260520/송수펌프제어분석-2번섹션/ANALYZE2.md) (status: approved) 7개 안건 결론 반영
- 직전 PLAN: [PLAN1](PLAN1.md) (status: review — 본 PLAN2 로 대체)
- PLAN1 폐기 사유:
  - 안건 1 (proc_id 데이터 도메인) — DOM_ID_36 결정 폐기 → DOM_ID_50 + Persistable<String>
  - SSE 인프라 4 클래스 (`AiDrvnModeChangedEvent`·Listener·SseService·SseController) 신규 추가
  - JWT 인증 우회 설정 (`/api/proc/*/ai-mode/subscribe`) 신규 추가
- Fix Cycle 아님 — REVIEW 부재. 사용자 요구사항 추가에 의한 정상 사이클 재진입 (사이클 번호 자연 증가)
- 사용자 결정 사항 (plan 모드 AskUserQuestion 게이트 완료):
  - SSE 본 사이클 포함 (PLAN2 재작성)
  - proc_id DOM_ID_50 재사용 (표준 데이터 도메인 신규 등록 불요)
  - SSE 인증: 1안 (JWT 필터 우회) — payload 비민감화 + excludePaths 추가
  - Emitter 수명: 타임아웃 30분 + heartbeat 15초
  - 옵션 B (`ApplicationEventPublisher` 직접 주입) 채택 (단순성 우선)
  - 패키지 구조 후보 1 (`com.mo.swtp.proc` 내 `event/`·`sse/` 서브패키지)

## 범위

### 포함 범위
- 비즈니스 도메인 `proc` 신규 등록 + 단일 패키지 `com.mo.swtp.proc` (PLAN1 유지)
- 엔티티 3종: `Process` (외부 할당 PK + `Persistable<String>`) · `AiDrvnMode` (현재 활성, `@MapsId` 1:1) · `AiDrvnModeHistory` (BaseEntity 4 상속, `end_dtm` UPDATE 허용)
- enum `AiDrvnModeCode` (`AI`·`AI_RECOMD`·`AI_ANLS` 3종)
- ErrorCode `ProcErrorCode` 3종 (`PROC_NOT_FOUND`·`INVALID_AI_DRVN_MOD`·`AI_MODE_CONCURRENT_UPDATE`)
- Repository (3종) · Service (Process · AiDrvnMode 2종) · Controller (`ProcController`)
- **SSE 인프라 4 클래스 (신규)**:
  - `AiDrvnModeChangedEvent` (record, payload: procId+aiDrvnModCd+startDtm 3 필드)
  - `AiDrvnModeChangedEventListener` (`@TransactionalEventListener(phase = AFTER_COMMIT)` + `fallbackExecution = false`)
  - `AiDrvnModeSseService` (Emitter 보관소 `ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` + 등록/제거/send/heartbeat)
  - `AiDrvnModeSseController` (`GET /api/proc/{procId}/ai-mode/subscribe`)
- DDL 마이그레이션 SQL — 신규 테이블 3종 (proc_m·ai_drvn_mod_p·ai_drvn_mod_h) + 시퀀스 + 인덱스 + 시드 데이터 (proc_id = `'PUMP_CONTROL'` 코드값)
- JWT 인증 설정 변경 — `auth.jwt.exclude-paths` 에 `/api/proc/*/ai-mode/subscribe` 추가 (`*` 와일드카드 AntPathMatcher 호환 의무)
- 단위 테스트 (Service · Listener 단위) + 통합 테스트 (변경 트랜잭션 정합성·동시성 시나리오 + SSE 이벤트 발행/수신 검증)
- Swagger 명세 자동 노출 (4 REST 엔드포인트 + 1 SSE 엔드포인트)

### 제외 범위 (별도 사이클로 분리)
- 강제 전환 정책 (SCADA 5분 초과 자동 전환·OUTBOUND_FAIL 사유 등 `ot-integration.md §5` 보류 영역)
- AI 시스템 상태 컬럼 (`ai_mode_cd`)·`last_rcv_dtm`·`expire_dtm`·`transition_reason` 5종 사유
- 스케줄러 기반 자동 모드 전환 로직
- 운영 화면에서 `proc_m` 신규 카테고리 등록 기능 (CRUD UI) — 본 사이클은 DDL 시드만, 추가는 별도 작업
- 멀티 인스턴스 SSE 분산 (Redis pub/sub 등) — 본 사이클은 단일 인스턴스 가정
- SSE 쿠키 인증 통합 — 향후 별도 사이클 (excludePaths 1줄 제거로 롤백 가능)

## 구현 방향

### 패키지 구조 (안건 7 후보 1 채택)

```
com.mo.swtp.proc
├── controller
│   └── ProcController.java
├── service
│   ├── ProcessService.java                (마스터 조회)
│   └── AiDrvnModeService.java             (현재 모드 조회·변경, 이력 조회)
├── repository
│   ├── ProcessRepository.java
│   ├── AiDrvnModeRepository.java
│   └── AiDrvnModeHistoryRepository.java
├── domain
│   ├── Process.java                       (proc_m, Persistable<String>)
│   ├── AiDrvnMode.java                    (ai_drvn_mod_p — 1:1 @MapsId)
│   └── AiDrvnModeHistory.java             (ai_drvn_mod_h — BaseEntity 4)
├── enumtype
│   └── AiDrvnModeCode.java                (AI · AI_RECOMD · AI_ANLS)
├── dto
│   ├── ProcDto.java                       (BaseAuditResponseDto 상속)
│   ├── ProcUpsertDto.java                 (마스터 등록·수정 — proc_id 외부 입력)
│   ├── AiDrvnModeDto.java                 (현재 모드 응답, BaseAuditResponseDto 상속)
│   ├── AiDrvnModeUpsertDto.java           (변경 요청)
│   └── AiDrvnModeHistoryDto.java          (이력 응답 — 4컬럼 직접 선언)
├── event                                  (신규 — SSE 인프라)
│   ├── AiDrvnModeChangedEvent.java        (record)
│   └── AiDrvnModeChangedEventListener.java (@TransactionalEventListener)
├── sse                                    (신규 — SSE 인프라)
│   ├── AiDrvnModeSseService.java          (Emitter 보관소 + heartbeat)
│   └── AiDrvnModeSseController.java       (GET /api/proc/{procId}/ai-mode/subscribe)
└── exception
    └── ProcErrorCode.java                 (httpStatus 단일 필드)
```

### proc_id 외부 할당 PK 패턴 (안건 1 채택)

`Process` 엔티티는 `Persistable<String>` 구현 + `@GeneratedValue` 제거 (`user_id`·`tag_srl_no` 선례 정합):

```java
@Entity
@Table(name = "proc_m", uniqueConstraints = @UniqueConstraint(name = "uk_proc_m_proc_nm", columnNames = "proc_nm"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Process extends BaseEntity implements Persistable<String> {

    @Id
    @Column(name = "proc_id", length = 50, nullable = false)
    private String procId;

    @Column(name = "proc_nm", length = 100, nullable = false)
    private String procNm;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", length = 1, nullable = false)
    private YnType useYn;

    @Column(name = "disp_ord")
    private Integer dispOrd;

    public static Process create(String procId, String procNm, Integer dispOrd) {
        // 코드값 컨벤션 검증 (정규식 ^[A-Z][A-Z0-9_]*$)
        if (!procId.matches("^[A-Z][A-Z0-9_]*$")) {
            throw new RestApiException(ProcErrorCode.INVALID_PROC_ID_FORMAT);
        }
        return new Process(procId, procNm, YnType.Y, dispOrd);
    }

    @Override
    public String getId() {
        return procId;
    }
    // isNew()는 BaseEntity 의 newEntity 플래그에 위임 — 별도 구현 불필요
}
```

- **코드값 형식 검증**: 정규식 `^[A-Z][A-Z0-9_]*$` (대문자 시작 + 대문자/숫자/언더스코어). 검증 책임은 Service 정적 팩토리 (DDL CHECK 미설정 — Java 검증 단일 방어선)
- **시드 데이터**: `INSERT INTO proc_m (proc_id, proc_nm, ...) VALUES ('PUMP_CONTROL', '송수펌프제어', ...)` (DDL 마이그레이션)
- **`AiDrvnMode`·`AiDrvnModeHistory` 의 `proc_id`** VARCHAR(50) 정렬

### 트랜잭션·동시성 전략 (PLAN1 유지 + 이벤트 발행 추가 — 안건 2/5)

`AiDrvnModeService#changeAiDrvnMode(...)`:
- 클래스 레벨 `@Transactional(readOnly = true)` + 메서드 레벨 `@Transactional` (쓰기)
- **단일 트랜잭션 내 행 잠금 1단계 + 데이터 변경 3 작업 + 이벤트 발행 1 작업 원자성**:

  ```java
  @Transactional
  public AiDrvnModeDto changeAiDrvnMode(String procId, AiDrvnModeUpsertDto dto) {
      LocalDateTime now = LocalDateTime.now();
      // 0. 사전 단계 — 직전 이력 행 SELECT FOR UPDATE 행 잠금 (동시 변경 차단)
      AiDrvnModeHistory prevActive = historyRepository
          .findActiveByProcIdForUpdate(procId)
          .orElse(null);
      // 1. 직전 이력 행 end_dtm = NOW() UPDATE
      if (prevActive != null) {
          prevActive.close(now);
      }
      // 2. 마스터 ai_drvn_mod_p UPSERT
      AiDrvnMode currentMode = modeRepository.findById(procId)
          .map(existing -> { existing.change(dto.getAiDrvnModCd(), now); return existing; })
          .orElseGet(() -> modeRepository.save(AiDrvnMode.create(processRef, dto.getAiDrvnModCd(), now)));
      // 3. 신규 이력 행 INSERT (end_dtm = NULL)
      historyRepository.save(AiDrvnModeHistory.create(procId, dto.getAiDrvnModCd(), now));
      // 4. 이벤트 발행 (옵션 B — ApplicationEventPublisher 직접 주입)
      eventPublisher.publishEvent(
          new AiDrvnModeChangedEvent(procId, dto.getAiDrvnModCd(), now)
      );
      // 5. 트랜잭션 commit
      // 6. @TransactionalEventListener(AFTER_COMMIT) 실행 → SseEmitter.send() (Listener 내부)
      return AiDrvnModeDto.from(currentMode);
  }
  ```

- 부분 UNIQUE 인덱스 위반 시 `AI_MODE_CONCURRENT_UPDATE` (409) — Spring DataIntegrityViolationException 매핑
- **⚠️ 절대 금지**: `SELECT FOR UPDATE` 를 외부 메서드/외부 트랜잭션에서 분리 호출하면 잠금 효력이 사라져 부분 UNIQUE 인덱스 위반 가능성 — TASK 체크박스 검증 항목으로 흡수

### SSE 인프라 설계 (신규 — 안건 2/5/6/7)

#### `AiDrvnModeChangedEvent` (record)

```java
public record AiDrvnModeChangedEvent(
    String procId,
    AiDrvnModeCode aiDrvnModCd,
    LocalDateTime startDtm
) {}
```

- 3 필드만 — payload 비민감화 (1안 한계 완화 조건 강제)
- record (Java 16+) — immutable + 자동 equals/hashCode/toString

#### `AiDrvnModeChangedEventListener`

```java
@Component
@RequiredArgsConstructor
public class AiDrvnModeChangedEventListener {

    private final AiDrvnModeSseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void handleAiDrvnModeChanged(AiDrvnModeChangedEvent event) {
        sseService.send(event);
    }
}
```

- `phase = AFTER_COMMIT` — DB 커밋 후 실행 (이력 영속화 완료 후 push, 도메인 정합성 보장)
- `fallbackExecution = false` (기본값 명시) — 롤백 시 리스너 미실행 (사용자 통지 일관성)
- 단일 흐름 (Service → Listener → SseService) — `coding-discipline.md §2.5` 면책 비적용 (50줄 미만)

#### `AiDrvnModeSseService`

```java
@Service
@Slf4j
public class AiDrvnModeSseService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;  // 30분
    private static final long HEARTBEAT_INTERVAL_MS = 15 * 1000L;  // 15초

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitterStore = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String procId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitterStore.computeIfAbsent(procId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(procId, emitter));
        emitter.onTimeout(() -> remove(procId, emitter));
        emitter.onError(throwable -> remove(procId, emitter));

        // 최초 연결 직후 ping 1회 (proxy idle timeout 회피)
        sendPing(emitter);
        return emitter;
    }

    public void send(AiDrvnModeChangedEvent event) {
        List<SseEmitter> emitters = emitterStore.get(event.procId());
        if (emitters == null || emitters.isEmpty()) return;
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("ai-drvn-mod-changed").data(event));
            } catch (IOException e) {
                log.warn("SSE send 실패 procId={}, emitter 제거", event.procId(), e);
                remove(event.procId(), emitter);
            }
        });
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void heartbeat() {
        emitterStore.forEach((procId, emitters) ->
            emitters.forEach(this::sendPing)
        );
    }

    private void sendPing(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("ping").comment("keep-alive"));
        } catch (IOException e) {
            // 자기 정리 — onError 콜백이 처리
        }
    }

    private void remove(String procId, SseEmitter emitter) {
        Optional.ofNullable(emitterStore.get(procId)).ifPresent(list -> list.remove(emitter));
    }
}
```

- `ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` — procId 별 다중 subscriber 동시 안전
- 타임아웃 30분 + heartbeat 15초 (사용자 결정값)
- onCompletion/onTimeout/onError 콜백 → Emitter 자동 제거 (자기 정리)
- `coding-discipline.md §2.5` 면책 비적용 — 50줄 초과해도 SSE 인프라는 정수장 안전 도메인 패턴 아님. 단, 본 클래스는 책임 단일 (Emitter 보관소 + 통지) 이라 50줄 근접 시점에서도 분해 부담 적음

#### `AiDrvnModeSseController`

```java
@RestController
@RequestMapping("/api/proc")
@RequiredArgsConstructor
@Tag(name = "10. 송수펌프제어 AI 운전모드 — SSE")
public class AiDrvnModeSseController extends CommonController {

    private final AiDrvnModeSseService sseService;

    @GetMapping(value = "/{procId}/ai-mode/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI 운전모드 변경 실시간 구독 (SSE)")
    public SseEmitter subscribe(@PathVariable String procId) {
        return sseService.subscribe(procId);
    }
}
```

- `produces = MediaType.TEXT_EVENT_STREAM_VALUE` — SSE 응답
- `@PathVariable` 만 — JWT 인증 비적용 (excludePaths 우회)

### JWT 인증 우회 설정 (안건 4 — 1안 채택)

**`api/src/main/resources-env/{profile}/application.yml`** 또는 **`api/src/main/resources/application.yml`** 의 `auth.jwt.exclude-paths` 에 다음 패턴 추가:

```yaml
auth:
  jwt:
    exclude-paths:
      - /api/auth/login
      - /api/auth/refresh
      - /swagger-ui/**
      - /v3/api-docs/**
      - /api/proc/*/ai-mode/subscribe   # ⭐ 추가 — SSE 전용
```

- **`*` 와일드카드 의무** — `{procId}` literal 형식 금지. Spring `AntPathMatcher` 는 `{procId}` 를 변수 placeholder 가 아닌 리터럴 문자열로 해석 → 매칭 실패
- 응답 payload 비민감화 강제 (`AiDrvnModeChangedEvent` 3 필드 + heartbeat ping comment 만)
- 향후 쿠키 인증 통합 시 본 1줄 제거로 롤백

### `_h` BaseEntity 4 상속 정책 (PLAN1 유지 — 안건 5)
- `ai_drvn_mod_h` 는 BaseEntity 4 상속 + `end_dtm` UPDATE 허용 — `rawdata_1m_h.corr_val` 선례 정합
- INSERT-only 컬럼 (`proc_id`·`ai_drvn_mod_cd`·`start_dtm`) 의 immutable 보장은 **애플리케이션 레벨** 에서 보호 (`update` 메서드 호출 제한 + Repository custom 메서드만 `end_dtm` 갱신 허용)

### 응답 DTO 패턴 (PLAN1 유지)
- `ProcDto` — `BaseAuditResponseDto` 옵트인 (마스터 표준)
- `AiDrvnModeDto` (현재 모드) — `BaseAuditResponseDto` 옵트인 (마스터 `_p` 의 의미)
- `AiDrvnModeHistoryDto` (이력) — `BaseAuditResponseDto` 미상속 + 4컬럼 직접 선언 (`api-patterns.md §적용 범위 표` 시계열 `_h` 적용 외 정렬)

### Swagger 패턴
- `@Tag(name = "10. 송수펌프제어 AI 운전모드")` (REST) + `@Tag(name = "10. 송수펌프제어 AI 운전모드 — SSE")` (SSE 구독)
- 모든 enum 필드에 `@Schema(implementation = AiDrvnModeCode.class)` 명시 의무

## 도메인 모델

| 엔티티/테이블 | 역할 | 주요 필드 |
|------------|------|---------|
| `Process` / `proc_m` | 공정/제어대상 카테고리 마스터 (업무 화면 단위) | `procId` (**DOM_ID_50 외부 할당 PK + Persistable<String>**) · `procNm` (UNIQUE) · `useYn` (YnType, NOT NULL) · `dispOrd` (INTEGER) · BaseEntity 4 |
| `AiDrvnMode` / `ai_drvn_mod_p` | 공정별 현재 활성 AI 운전모드 (1:1 with Process) | `procId` (PK + FK → proc_m, `@MapsId`, **VARCHAR(50)**) · `aiDrvnModCd` (AiDrvnModeCode enum, NOT NULL) · `startDtm` (NOT NULL) · BaseEntity 4 |
| `AiDrvnModeHistory` / `ai_drvn_mod_h` | AI 운전모드 변경 이력 (INSERT + end_dtm UPDATE) | `aiDrvnModId` (BIGINT SEQUENCE PK) · `procId` (**VARCHAR(50)**, NOT NULL, 논리 참조 — FK 금지) · `aiDrvnModCd` (enum, NOT NULL) · `startDtm` (NOT NULL) · `endDtm` (NULL 허용) · BaseEntity 4 |
| `AiDrvnModeCode` enum | AI 운전모드 코드 (사용자 의도 단일축) | `AI` · `AI_RECOMD` · `AI_ANLS` (Korean 라벨: AI / AI추천 / AI분석) |
| `ProcErrorCode` enum | proc 도메인 에러 코드 | `PROC_NOT_FOUND` (404) · `INVALID_AI_DRVN_MOD` (400) · `AI_MODE_CONCURRENT_UPDATE` (409) · `INVALID_PROC_ID_FORMAT` (400, 신규 — 코드값 정규식 위반) |
| `AiDrvnModeChangedEvent` (record) | 운전모드 변경 이벤트 payload | `procId` (String) · `aiDrvnModCd` (AiDrvnModeCode) · `startDtm` (LocalDateTime) — 3 필드만 |
| `AiDrvnModeChangedEventListener` | AFTER_COMMIT 이벤트 처리 (SseService 위임) | `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)` |
| `AiDrvnModeSseService` | Emitter 보관소 + send + heartbeat | `ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` · subscribe / send / heartbeat |
| `AiDrvnModeSseController` | SSE 구독 엔드포인트 | `GET /api/proc/{procId}/ai-mode/subscribe` (produces TEXT_EVENT_STREAM_VALUE) |

### 1:1 관계 매핑 패턴 (PLAN1 유지)
- `AiDrvnMode.process` (부모 ↔ 자식): `@OneToOne(fetch = FetchType.LAZY)` + `@MapsId` + `@JoinColumn(name = "proc_id")`
- `Process.aiDrvnMode` (역방향): 미정의 (양방향 매핑 불필요 — 단방향만)
- 자식 PK = 부모 PK 강제로 공정/제어대상별 1행 자동 보장

## DB 설계 변경

### 신규 테이블 3종 + 시퀀스 + 인덱스 (PLAN1 대비 변경 사항: proc_id VARCHAR(36) → VARCHAR(50), 시드 데이터 코드값 변경)

#### `proc_m` (공정/제어대상 카테고리 마스터)

```sql
CREATE TABLE proc_m (
    proc_id      VARCHAR(50)  NOT NULL,
    proc_nm      VARCHAR(100) NOT NULL,
    use_yn       VARCHAR(1)   NOT NULL,
    disp_ord     INTEGER,
    rgstr_dtm    TIMESTAMP,
    updt_dtm     TIMESTAMP,
    rgstr_id     VARCHAR(50),
    updt_id      VARCHAR(50),
    CONSTRAINT pk_proc_m PRIMARY KEY (proc_id),
    CONSTRAINT uk_proc_m_proc_nm UNIQUE (proc_nm)
);

COMMENT ON TABLE proc_m IS '공정/제어대상 카테고리 마스터 (업무 화면 단위)';
COMMENT ON COLUMN proc_m.proc_id  IS '공정/제어대상 ID (DOM_ID_50, 외부 할당 PK — Persistable<String>, 대문자+언더스코어 코드값 예: PUMP_CONTROL)';
COMMENT ON COLUMN proc_m.proc_nm  IS '공정/제어대상명 (DOM_NAME_100, 시스템 전체 UNIQUE — 예: 송수펌프제어)';
COMMENT ON COLUMN proc_m.use_yn   IS '사용 여부 (DOM_YN, Y·N enum 매핑)';
COMMENT ON COLUMN proc_m.disp_ord IS '표시 순서 (화면 표시 정렬 순서, NULL 허용)';
COMMENT ON COLUMN proc_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN proc_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN proc_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN proc_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- 초기 시드 데이터 (사용자 명시 코드값)
INSERT INTO proc_m (proc_id, proc_nm, use_yn, disp_ord, rgstr_dtm, rgstr_id)
VALUES (
    'PUMP_CONTROL',
    '송수펌프제어',
    'Y',
    1,
    CURRENT_TIMESTAMP,
    'system'
);
```

#### `ai_drvn_mod_p` (AI 운전모드 현재 활성 행)

```sql
CREATE TABLE ai_drvn_mod_p (
    proc_id          VARCHAR(50)  NOT NULL,
    ai_drvn_mod_cd   VARCHAR(20)  NOT NULL,
    start_dtm        TIMESTAMP    NOT NULL,
    rgstr_dtm        TIMESTAMP,
    updt_dtm         TIMESTAMP,
    rgstr_id         VARCHAR(50),
    updt_id          VARCHAR(50),
    CONSTRAINT pk_ai_drvn_mod_p PRIMARY KEY (proc_id),
    CONSTRAINT fk_ai_drvn_mod_p_proc_id FOREIGN KEY (proc_id)
        REFERENCES proc_m (proc_id) ON DELETE RESTRICT
);

COMMENT ON TABLE ai_drvn_mod_p IS 'AI 운전모드 현재 활성 행 (proc_m 과 1:1 관계, UPSERT 운영)';
COMMENT ON COLUMN ai_drvn_mod_p.proc_id         IS '공정/제어대상 ID (DOM_ID_50, PK + FK → proc_m, @MapsId)';
COMMENT ON COLUMN ai_drvn_mod_p.ai_drvn_mod_cd  IS 'AI 운전모드 코드 (DOM_CODE_20, AiDrvnModeCode enum 매핑 — AI/AI_RECOMD/AI_ANLS)';
COMMENT ON COLUMN ai_drvn_mod_p.start_dtm       IS '시작 일시 (DOM_DTM, 현재 활성 모드 시작 시각, NOT NULL)';
COMMENT ON COLUMN ai_drvn_mod_p.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN ai_drvn_mod_p.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN ai_drvn_mod_p.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN ai_drvn_mod_p.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
```

#### `ai_drvn_mod_h` (AI 운전모드 변경 이력)

```sql
CREATE TABLE ai_drvn_mod_h (
    ai_drvn_mod_id   BIGINT        NOT NULL,
    proc_id          VARCHAR(50)   NOT NULL,
    ai_drvn_mod_cd   VARCHAR(20)   NOT NULL,
    start_dtm        TIMESTAMP     NOT NULL,
    end_dtm          TIMESTAMP,
    rgstr_dtm        TIMESTAMP,
    updt_dtm         TIMESTAMP,
    rgstr_id         VARCHAR(50),
    updt_id          VARCHAR(50),
    CONSTRAINT pk_ai_drvn_mod_h PRIMARY KEY (ai_drvn_mod_id)
);

CREATE SEQUENCE seq_ai_drvn_mod_h_id START 1 INCREMENT 100;

-- 이력 조회 + 페이지네이션 인덱스
CREATE INDEX idx_ai_drvn_mod_h_proc_start ON ai_drvn_mod_h (proc_id, start_dtm DESC);

-- ⭐ 부분 UNIQUE 인덱스 — 공정/제어대상별 활성 행 1건 강제 (이력 동시성 안전망)
CREATE UNIQUE INDEX uk_ai_drvn_mod_h_proc_active ON ai_drvn_mod_h (proc_id) WHERE end_dtm IS NULL;

COMMENT ON TABLE ai_drvn_mod_h IS 'AI 운전모드 변경 이력 (INSERT + end_dtm UPDATE 허용, BaseEntity 4 상속 — rawdata_1m_h.corr_val 선례 정합)';
COMMENT ON COLUMN ai_drvn_mod_h.ai_drvn_mod_id  IS 'AI 운전모드 이력 ID (DOM_SEQ_BIGINT, GenerationType.SEQUENCE allocationSize=100)';
COMMENT ON COLUMN ai_drvn_mod_h.proc_id         IS '공정/제어대상 ID (DOM_ID_50, 마스터 참조이지만 FK 금지 — partitioning-and-retention.md §1 시계열 → 마스터 FK 금지 정책)';
COMMENT ON COLUMN ai_drvn_mod_h.ai_drvn_mod_cd  IS 'AI 운전모드 코드 (DOM_CODE_20, AiDrvnModeCode enum 매핑)';
COMMENT ON COLUMN ai_drvn_mod_h.start_dtm       IS '시작 일시 (DOM_DTM, INSERT-only — 애플리케이션 immutable 검증)';
COMMENT ON COLUMN ai_drvn_mod_h.end_dtm         IS '종료 일시 (DOM_DTM, NULL 허용 — 현재 활성 행 표현. 부분 UNIQUE 인덱스로 1행 강제)';
COMMENT ON COLUMN ai_drvn_mod_h.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN ai_drvn_mod_h.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입 — end_dtm UPDATE 시점 추적)';
COMMENT ON COLUMN ai_drvn_mod_h.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN ai_drvn_mod_h.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
```

### 마이그레이션 전략 (무중단)
- 신규 테이블 도입이므로 `partitioning-and-retention.md §2` 의 무중단 마이그레이션 3단계 불필요 — 즉시 적용 가능
- 적용 위치: `common/src/main/resources/db/init/V{다음번호}__proc_ai_drvn_mod_도입.sql`
- 보존 정책: 마스터 영구·이력 5년 (`partitioning-and-retention.md §2` 기존 정책 행 비고 갱신 완료 — PLAN1 적용분 그대로 유효)
- 파티셔닝: 변경 빈도 극히 낮음 → 불필요. 단일 테이블 + B-Tree 복합 인덱스 + 부분 UNIQUE 인덱스로 5년 치 조회 성능 충족

### 인덱스 정합성 (`indexing-and-migration.md §1`)
- `idx_ai_drvn_mod_h_proc_start` (proc_id, start_dtm DESC): 등가(proc_id) → 범위·정렬(start_dtm) 순서 정합
- `uk_ai_drvn_mod_h_proc_active` (proc_id) WHERE end_dtm IS NULL: 부분 UNIQUE — PostgreSQL 부분 인덱스 지원, 동시성 안전망

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 명령 / 테스트 / 조회 |
|---------|----------------------|
| `Process` 외부 할당 PK + `Persistable<String>` — 정규식 검증 + 시드 데이터 INSERT | `./gradlew.bat :api:test --tests com.mo.swtp.proc.domain.ProcessTest` PASS + SQL SELECT proc_nm FROM proc_m WHERE proc_id = 'PUMP_CONTROL' 1행 반환 |
| `AiDrvnModeService#changeAiDrvnMode` 변경 트랜잭션 3 작업 + 이벤트 발행 원자성 | 신규 통합 테스트 AiDrvnModeServiceIntegrationTest 변경_트랜잭션은_세_작업이_원자적으로_적용되며_이벤트가_발행된다 PASS |
| 부분 UNIQUE 인덱스 동시성 안전망 — 2개 동시 변경 시 1건은 AI_MODE_CONCURRENT_UPDATE (409) | 신규 동시성 시나리오 테스트 AiDrvnModeServiceConcurrencyTest 동시_변경_시_부분_UNIQUE_인덱스가_409를_반환한다 PASS |
| `AiDrvnModeChangedEventListener` AFTER_COMMIT phase 동작 | 신규 통합 테스트 AiDrvnModeChangedEventListenerTest 트랜잭션_커밋_후에만_SseService_send가_호출된다 PASS — 롤백 시 호출 0건 검증 |
| `AiDrvnModeSseService` Emitter 등록/제거/send 동작 | 신규 단위 테스트 AiDrvnModeSseServiceTest 4개 — subscribe / send / 다중 subscriber 동시 send / onError 자기 정리 PASS |
| `AiDrvnModeSseController` SSE 응답 + JWT 우회 통합 검증 | 신규 통합 테스트 AiDrvnModeSseControllerIntegrationTest Authorization 헤더_없이_subscribe가_200을_반환한다 PASS (`*` 와일드카드 매칭 확인) + 응답 Content-Type text/event-stream 검증 |
| 변경 API 응답 — CommonResponseDto<AiDrvnModeDto> (code SUCCESS, data.aiDrvnModCd AI_RECOMD 등) | `./gradlew.bat :api:bootRun -Pprofile=local` + curl `PUT /api/proc/PUMP_CONTROL/ai-mode` 응답 검증 |
| Swagger 명세 4 REST 엔드포인트 + 1 SSE 엔드포인트 + DTO @Schema(implementation=AiDrvnModeCode.class) | 로컬 실행 후 http://localhost:8080/swagger-ui.html 의 "10. 송수펌프제어 AI 운전모드" Tag 5 엔드포인트 존재 + AiDrvnModeCode allowable values [AI, AI_RECOMD, AI_ANLS] 노출 검증 |
| ErrorCode 4종 httpStatus 단일 필드 (PLAN1 3종 + INVALID_PROC_ID_FORMAT 신규) | `.claude/hooks/check-errorcode-contract.sh` 훅 저장 시 차단 없음 + grep 검증 `private final String` 매칭 없음 |
| 이력 응답 DTO BaseAuditResponseDto 미상속 + 4컬럼 직접 선언 | grep 검증 `AiDrvnModeHistoryDto.*extends BaseAuditResponseDto` 매칭 없음 + `private LocalDateTime rgstrDtm` 직접 선언 매칭 |
| DDL 마이그레이션 SQL COMMENT ON COLUMN 누락 0건 | `.claude/hooks/check-ddl-column-comment.sh` 훅 저장 시 차단 없음 |
| JWT excludePaths 패턴 추가 + AntPathMatcher 매칭 통과 | grep 검증 application.yml 에 `/api/proc/*/ai-mode/subscribe` 패턴 매칭 + AntPathMatcher 단위 테스트 `*` 와일드카드 매칭 검증 |

## 가정 및 미해결 질문 (PLAN 단계 결정)

| 가정 / 질문 (ANALYZE2 미해결) | 분류 | PLAN 단계 결정 |
|--------------------------|------|------------|
| proc_id 코드값 정규식 검증 위치 — DDL CHECK vs Java | 미해결 → 결정 | **Java 정적 팩토리 (`Process.create()`)** 단일 방어선. DDL CHECK 미설정 (DOM_YN 정책 정합 — 이중 방어 회피, Java 변경 시 1곳만 갱신) |
| `AiDrvnModeCode` enum 패키지 위치 | 미해결 → 결정 | **`com.mo.swtp.proc.enumtype.AiDrvnModeCode`** — proc 도메인 전용 (PLAN1 결정 유지) |
| 시드 데이터 `proc_id` 운영 환경 고정값 필요 여부 | 미해결 → 결정 | **`'PUMP_CONTROL'` 코드값 고정 INSERT**. 사업장 간 공통 코드값 정렬이 목적이므로 멀티테넌트 일관 유지 |
| Hibernate UPSERT 구현 | 미해결 → 결정 | `JpaRepository.save()` + `Persistable.isNew()` 기반 INSERT/UPDATE 분기. `AiDrvnMode` 의 PK 가 외부 할당 (`@MapsId` 부모 PK 동일) 이므로 `Process` 와 동일 패턴. `BaseEntity.newEntity` 플래그 활용 |
| DDL 마이그레이션 파일 위치 | 미해결 → 결정 | `common/src/main/resources/db/init/V{다음번호}__proc_ai_drvn_mod_도입.sql` (TASK 단계 다음 번호 확정) |
| PostgreSQL 13+ 또는 `pgcrypto` 활성화 (UUID 생성) | 미해결 → 결정 | **불요 — UUID 생성 제거됨**. PLAN2 는 외부 할당 코드값 `'PUMP_CONTROL'` 시드이므로 `gen_random_uuid()` 사용 안 함. PostgreSQL 버전 제약 해소 |
| SSE 1안 (필터 우회) 보안 평가 — payload 비민감화 충분성 | 가정 → 결정 (조건부) | **내부망 가정 + 3 필드 payload 제한 + heartbeat ping comment 만 으로 허용 가능 위험 수준**. 보안 담당자 운영 배포 전 한 차례 확인 필요 (ANALYZE2 미해결 항목 1건 보존). 향후 쿠키 인증 통합 사이클 진입 시 excludePaths 1줄 제거로 롤백 가능 |
| AFTER_COMMIT 트랜잭션 실패 시 이벤트 발행 정책 | 가정 → 결정 | **`fallbackExecution = false` (Spring 기본값) 명시** — 롤백 시 리스너 미실행. SSE 통지는 DB 영속화된 사실만 push (도메인 정합성 보장). 코드 라인에 어노테이션 명시 + Listener 단위 테스트로 보장 |
| Emitter 보관소 자료구조 | 미해결 → 결정 | **`ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>`** (procId 별 다중 subscriber 동시 안전). 등록·제거 빈도 낮고 (사용자 화면 진입/이탈) iteration 빈도 높아 (send + heartbeat) `CopyOnWriteArrayList` 적합 |
| SSE 멀티 인스턴스 확장 한계 | 가정 → 결정 (범위 제외) | **단일 인스턴스 가정** — `multi-tenant.md` 의 지자체별 단일 backend 인스턴스 정렬. 향후 멀티 인스턴스 도입 시 Redis pub/sub 등 별도 사이클 (`ai_drvn_mod_sse_distributed` 등) |
| `coding-discipline.md §2.5` 면책 적용 여부 | 가정 → 결정 | **본 사이클 면책 비적용** — SSE 인프라는 보안 도메인 결정이지 정수장 안전 패턴 (`ot-integration.md §3·§4·§5`) 또는 DB 쿼리 빌더 (`query-tuning.md §2`) 아님. `AiDrvnModeSseService` 메서드 50줄 근접 시 분해 의무 |
| `AntPathMatcher` `*` 와일드카드 vs `{procId}` literal | 가정 → 결정 (PLAN 명시 의무) | **`/api/proc/*/ai-mode/subscribe`** 형식 사용. `{procId}` 는 AntPathMatcher 에서 변수 placeholder 가 아닌 리터럴 문자열로 해석되어 매칭 실패. TASK 체크박스 검증 항목으로 흡수 |

## 제외 사항

- AI 시스템 상태 (`ai_mode_cd`) 컬럼 도입
- `transition_reason` 컬럼 + 5종 사유 (`SCADA_TIMEOUT`·`MANUAL_EXPIRE`·`OUTBOUND_FAIL`·`SYSTEM_INIT`·`USER_SELECT`)
- 스케줄러 기반 강제 전환 로직
- `last_rcv_dtm` · `expire_dtm` 컬럼
- 운영 화면에서 `proc_m` 카테고리 CRUD UI (시드 외 추가는 별도 사이클)
- AI 추론 서버 호출 클라이언트 (사이클 2 별도 안건)
- 인터록 검사 · 제어 명령 발행 · 모드 전환 사유 분기
- SSE 쿠키 인증 통합 (향후 별도 사이클)
- 멀티 인스턴스 SSE 분산 (Redis pub/sub 등 향후 별도 사이클)
- `common.sse` 공통 인프라 추출 (사용처 2건 이상 누적 시 별도 리팩토링 사이클)

## 부록: 도메인/DB 검토 결과 (PLAN2, 2026-05-20)

> ANALYZE2 단계에서 5인 회의 라운드 1 완료 (블로커 모두 자동 해소). PLAN2 도메인/DB 검토 게이트는 신규 SSE 인프라 4 클래스 + JWT 우회 설정 변경에 대한 재검토.

- **wtp-domain-expert** (ANALYZE2 라운드 1 결론): 블로커 0건, 권고 1건 (내부망 가정 명기 — 본 PLAN `## 가정 및 미해결 질문` 에 해소). 종합: SSE AFTER_COMMIT 통지는 이력 영속화 완료 후 push 일관성을 구조적으로 보장하므로 `ot-integration.md §5` 이력 기록 의무에 정합. 사용자 의도 단일축 표출·이력 도입 + SSE 통지 범위는 본 사이클의 도메인 경계 (강제 전환·시스템 상태 제외) 내 안전
- **wtp-dba-reviewer** (ANALYZE2 라운드 1 결론): 블로커 0건 (proc_id DOM_ID_50 COMMENT 3 곳 정정 + Persistable<String> 의무 — 모두 본 PLAN 본문 반영 완료), 권고 1건 (시드 데이터 코드값 `'PUMP_CONTROL'` 운영 환경 고정 — 본 PLAN `## DB 설계 변경` 시드 SQL 적용). 종합: VARCHAR(50) 타입 정렬·3종 신규 테이블 컬럼·인덱스 설계 (B-Tree 복합 + 부분 UNIQUE)·FK 정책 (시계열→마스터 FK 금지 정합)·DDL COMMENT·트랜잭션 격리·보존 정책 모두 룰 정합
- **wtp-backend-engineer** (ANALYZE2 라운드 1 결론): 블로커 0건 (AntPathMatcher `*` 와일드카드 의무 — 본 PLAN `## JWT 인증 우회 설정` 명시), 권고 2건 (옵션 B 단순성 채택 정합 + 패키지 구조 후보 1 단일 사용처 정합 — 본 PLAN 본문 반영). 종합: SSE 인프라 4 클래스 책임 분리 명확 (Event = payload + Listener = AFTER_COMMIT 위임 + SseService = 보관소 + Controller = 엔드포인트), 정량 기준 50줄/3단/3단 초과 위험 낮음
- **wtp-glossary-manager** (ANALYZE2 라운드 1 결론): 블로커 0건 (proc_id 행 정정 + `event`·`subscr` 신규 등록 — 모두 ANALYZE2 룰 갱신 지시서 실행 완료). 종합: 표준 단어 3층 (단어·데이터 도메인·비즈니스 약어) 충돌 없음, 본 PLAN 의 모든 DB 컬럼명 + Java 변수명 표준 어휘 조합 정합

## 예상 산출물

- [태스크](../../../tasks/20260520/송수펌프제어분석-2번섹션/TASK1.md) — 본 PLAN approved 후 자동 생성 (PLAN1 폐기 → TASK1 신규 생성, 분할 여부는 TASK 단계 결정)
- 신규 Java 파일 약 18개 (PLAN1 14개 + SSE 인프라 4개):
  - 엔티티 3 (`Process`·`AiDrvnMode`·`AiDrvnModeHistory`)
  - DTO 5 (`ProcDto`·`ProcUpsertDto`·`AiDrvnModeDto`·`AiDrvnModeUpsertDto`·`AiDrvnModeHistoryDto`)
  - Repository 3 (`ProcessRepository`·`AiDrvnModeRepository`·`AiDrvnModeHistoryRepository`)
  - Service 2 (`ProcessService`·`AiDrvnModeService`)
  - Controller 2 (`ProcController`·`AiDrvnModeSseController`)
  - enum 1 (`AiDrvnModeCode`)
  - ErrorCode 1 (`ProcErrorCode` 4종)
  - **SSE 인프라 신규 4** (`AiDrvnModeChangedEvent` record · `AiDrvnModeChangedEventListener` · `AiDrvnModeSseService` · `AiDrvnModeSseController`)
- 신규 SQL 파일 1개 (DDL + 시드, proc_id `'PUMP_CONTROL'` 외부 할당)
- 변경 설정 파일 1개 (`application.yml` 또는 `application-{profile}.yml` — `auth.jwt.exclude-paths` 추가)
- 신규 테스트 파일 약 6개 (PLAN1 3개 + SSE 3개):
  - `ProcessServiceTest` (단위)
  - `AiDrvnModeServiceIntegrationTest` (변경 트랜잭션 + 이벤트 발행 통합)
  - `AiDrvnModeServiceConcurrencyTest` (동시성 시나리오)
  - **`AiDrvnModeChangedEventListenerTest` 신규** (AFTER_COMMIT phase + 롤백 시 미실행 검증)
  - **`AiDrvnModeSseServiceTest` 신규** (Emitter 등록/제거/send/heartbeat 단위)
  - **`AiDrvnModeSseControllerIntegrationTest` 신규** (JWT 우회 + SSE 응답 통합)
