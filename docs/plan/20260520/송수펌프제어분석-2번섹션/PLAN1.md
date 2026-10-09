---
status: review
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 표출·이력 관리

## 목적

송수펌프제어분석 화면 (`backend/image/송수펌프제어분석.png`) 의 **2번 섹션 — AI 운전모드 (AI / AI추천 / AI분석) 표출 + 변경 이력 관리** 기능을 구현한다. 공정/제어대상 단위 시스템 전역 설정 + 사용자 의도 변경 API + 이력 감사 추적을 도입하여 운영자가 AI 운전 의도를 명시적으로 선택·기록할 수 있도록 한다.

## 배경

- [ANALYZE1](../../../analyze/20260520/송수펌프제어분석-2번섹션/ANALYZE1.md) 5인 회의 9개 안건 결론 반영
- pump+AI 백지화 사이클 1 (2026-05-12) 후 사이클 2 재설계의 일부 — 백지화 이전 자산 (`com.mo.swtp.ai.*`·`ai_drvn_mod_p`/`_h` 테이블) 의 자동 원용 없이 처음부터 재설계 (사용자 메모리 정책 정합)
- 사용자 평가 요청 핵심 안건 (마스터+이력 vs 이력 단일 SSOT) 의 회의 결론 = **옵션 A (마스터+이력 분리)** — 4축 모두 우위
- 사용자 결정 (ANALYZE 검토 시점):
  - 작업 범위: 표출 + 이력 + 변경 API 만 (강제 전환 정책 별도 사이클)
  - 적용 단위: 업무 화면 카테고리 단위 마스터 (`proc_m`)
  - 테이블 prefix: `ai_drvn_mod_p` / `ai_drvn_mod_h` (짧은 이름)

## 범위

### 포함 범위
- 비즈니스 도메인 `proc` 신규 등록 + 단일 패키지 `com.mo.swtp.proc`
- 엔티티 3종: `Process` (공정/제어대상 카테고리 마스터) + `AiDrvnMode` (현재 활성 모드 명세) + `AiDrvnModeHistory` (변경 이력)
- enum `AiDrvnModeCode` (`AI`·`AI_RECOMD`·`AI_ANLS` 3종)
- ErrorCode `ProcErrorCode` 3종 (`PROC_NOT_FOUND`·`INVALID_AI_DRVN_MOD`·`AI_MODE_CONCURRENT_UPDATE`)
- Repository (3종) · Service (Process · AiDrvnMode 2종) · Controller (`ProcController`)
- DDL 마이그레이션 SQL — 신규 테이블 3종 + 초기 시드 데이터 1건 ("송수펌프제어")
- 단위 테스트 (Service 단위) + 통합 테스트 (변경 트랜잭션 정합성·동시성 시나리오)
- Swagger 명세 자동 노출 (4 엔드포인트)

### 제외 범위 (별도 사이클로 분리)
- 강제 전환 정책 (SCADA 5분 초과 자동 전환·OUTBOUND_FAIL 사유 등 `ot-integration.md §5` 보류 영역)
- AI 시스템 상태 컬럼 (`ai_mode_cd`)·`last_rcv_dtm`·`expire_dtm`·`transition_reason` 5종 사유
- 스케줄러 기반 자동 모드 전환 로직
- 운영 화면에서 `proc_m` 신규 카테고리 등록 기능 (CRUD UI) — 본 사이클은 DDL 시드만, 추가는 별도 작업

## 구현 방향

### 패키지 구조
```
com.mo.swtp.proc
├── controller
│   └── ProcController.java
├── service
│   ├── ProcessService.java          (마스터 조회)
│   └── AiDrvnModeService.java       (현재 모드 조회·변경, 이력 조회)
├── repository
│   ├── ProcessRepository.java
│   ├── AiDrvnModeRepository.java
│   └── AiDrvnModeHistoryRepository.java
├── domain
│   ├── Process.java                 (proc_m)
│   ├── AiDrvnMode.java              (ai_drvn_mod_p — 현재 활성 1행)
│   └── AiDrvnModeHistory.java       (ai_drvn_mod_h — 변경 이력)
├── enumtype
│   └── AiDrvnModeCode.java          (AI·AI_RECOMD·AI_ANLS)
├── dto
│   ├── ProcDto.java                          (BaseAuditResponseDto 상속)
│   ├── AiDrvnModeDto.java                    (현재 모드 응답)
│   ├── AiDrvnModeUpsertDto.java              (변경 요청)
│   └── AiDrvnModeHistoryDto.java             (이력 응답 — 4컬럼 직접 선언)
└── exception
    └── ProcErrorCode.java                    (httpStatus 단일 필드)
```

### 트랜잭션·동시성 전략
- 변경 API `AiDrvnModeService#changeAiDrvnMode(...)`:
  - 클래스 레벨 `@Transactional(readOnly = true)` + 메서드 레벨 `@Transactional` (쓰기)
  - **단일 트랜잭션 내 행 잠금 1단계 (사전 단계) + 데이터 변경 3 작업 원자성**:
    - **사전 단계** (잠금 획득, 트랜잭션 내 필수 — 외부 분리 금지):
      0. 직전 이력 행 (`end_dtm IS NULL`) `SELECT FOR UPDATE` 행 잠금 — 동시 변경 차단용. **본 단계는 데이터 변경이 아니라 잠금 획득 목적**이며, 동일 `@Transactional` 메서드 내에서 반드시 수행
    - **데이터 변경 3 작업** (잠금 보유 상태에서 원자적 적용):
      1. 직전 이력 행 `end_dtm = NOW()` UPDATE (사전 단계의 잠금 대상 행)
      2. 마스터 (`ai_drvn_mod_p`) UPSERT (`ON CONFLICT (proc_id) DO UPDATE`)
      3. 신규 이력 행 INSERT (`end_dtm = NULL`)
  - 부분 UNIQUE 인덱스 위반 시 `AI_MODE_CONCURRENT_UPDATE` (409) — Spring DataIntegrityViolationException 매핑
  - **⚠️ 절대 금지**: `SELECT FOR UPDATE` 를 외부 메서드/외부 트랜잭션에서 분리 호출하면 잠금 효력이 사라져 부분 UNIQUE 인덱스 위반 가능성 — TASK 체크박스 검증 항목으로 흡수

### `_h` BaseEntity 4 상속 정책 (안건 3 적용)
- `ai_drvn_mod_h` 는 BaseEntity 4 상속 + `end_dtm` UPDATE 허용 — `rawdata_1m_h.corr_val` 선례 정합
- INSERT-only 컬럼 (`proc_id`·`ai_drvn_mod_cd`·`start_dtm`) 의 immutable 보장은 **애플리케이션 레벨** 에서 보호 (`update` 메서드 호출 제한)

### 응답 DTO 패턴 (안건 9 적용)
- `ProcDto` — `BaseAuditResponseDto` 옵트인 (마스터 표준)
- `AiDrvnModeDto` (현재 모드) — `BaseAuditResponseDto` 옵트인 (마스터 `_p` 의 의미)
- `AiDrvnModeHistoryDto` (이력) — **`BaseAuditResponseDto` 미상속 + 4컬럼 직접 선언** (`api-patterns.md §적용 범위 표` "시계열 `_h` 적용 외" 정렬)

### Swagger 패턴
- `@Tag(name = "10. 송수펌프제어 AI 운전모드")` (기존 Tag 번호 (`pump_drive_type` ANALYZE 의 08·09 다음) 이어가기)
- 모든 enum 필드에 `@Schema(implementation = AiDrvnModeCode.class)` 명시 의무

## 도메인 모델

| 엔티티/테이블 | 역할 | 주요 필드 |
|------------|------|---------|
| `Process` / `proc_m` | 공정/제어대상 카테고리 마스터 (업무 화면 단위) | `procId` (UUID PK) · `procNm` (UNIQUE) · `useYn` (YnType, NOT NULL) · `dispOrd` (INTEGER) · BaseEntity 4 |
| `AiDrvnMode` / `ai_drvn_mod_p` | 공정별 현재 활성 AI 운전모드 (1:1 with Process) | `procId` (PK + FK → proc_m, `@MapsId`) · `aiDrvnModCd` (AiDrvnModeCode enum, NOT NULL) · `startDtm` (NOT NULL) · BaseEntity 4 |
| `AiDrvnModeHistory` / `ai_drvn_mod_h` | AI 운전모드 변경 이력 (INSERT + end_dtm UPDATE) | `aiDrvnModId` (BIGINT SEQUENCE PK) · `procId` (NOT NULL, 논리 참조 — FK 금지) · `aiDrvnModCd` (enum, NOT NULL) · `startDtm` (NOT NULL) · `endDtm` (NULL 허용) · BaseEntity 4 |
| `AiDrvnModeCode` enum | AI 운전모드 코드 (사용자 의도 단일축) | `AI` · `AI_RECOMD` · `AI_ANLS` (Korean 라벨: AI / AI추천 / AI분석) |
| `ProcErrorCode` enum | proc 도메인 에러 코드 | `PROC_NOT_FOUND` (404) · `INVALID_AI_DRVN_MOD` (400) · `AI_MODE_CONCURRENT_UPDATE` (409) |

### 1:1 관계 매핑 패턴 (안건 9 PLAN 결정)
- `AiDrvnMode.process` (부모 ↔ 자식): `@OneToOne(fetch = FetchType.LAZY)` + `@MapsId` + `@JoinColumn(name = "proc_id")`
- `Process.aiDrvnMode` (역방향): 미정의 (양방향 매핑 불필요 — 단방향만)
- 자식 PK = 부모 PK 강제로 공정/제어대상별 1행 자동 보장

## DB 설계 변경

### 신규 테이블 3종

#### `proc_m` (공정/제어대상 카테고리 마스터)

```sql
CREATE TABLE proc_m (
    proc_id      VARCHAR(36)  NOT NULL,
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
COMMENT ON COLUMN proc_m.proc_id  IS '공정/제어대상 ID (DOM_ID_36, UUID 자동 생성)';
COMMENT ON COLUMN proc_m.proc_nm  IS '공정/제어대상명 (DOM_NAME_100, 시스템 전체 UNIQUE — 예: 송수펌프제어)';
COMMENT ON COLUMN proc_m.use_yn   IS '사용 여부 (DOM_YN, Y·N enum 매핑)';
COMMENT ON COLUMN proc_m.disp_ord IS '표시 순서 (화면 표시 정렬 순서, NULL 허용)';
COMMENT ON COLUMN proc_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN proc_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN proc_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN proc_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- 초기 시드 데이터 (사용자 명시 카테고리 1건)
INSERT INTO proc_m (proc_id, proc_nm, use_yn, disp_ord, rgstr_dtm, rgstr_id)
VALUES (
    gen_random_uuid()::TEXT,
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
    proc_id          VARCHAR(36)  NOT NULL,
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
COMMENT ON COLUMN ai_drvn_mod_p.proc_id         IS '공정/제어대상 ID (DOM_ID_36, PK + FK → proc_m, @MapsId)';
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
    proc_id          VARCHAR(36)   NOT NULL,
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
COMMENT ON COLUMN ai_drvn_mod_h.proc_id         IS '공정/제어대상 ID (DOM_ID_36, 마스터 참조이지만 FK 금지 — partitioning-and-retention.md §1 시계열 → 마스터 FK 금지 정책)';
COMMENT ON COLUMN ai_drvn_mod_h.ai_drvn_mod_cd  IS 'AI 운전모드 코드 (DOM_CODE_20, AiDrvnModeCode enum 매핑)';
COMMENT ON COLUMN ai_drvn_mod_h.start_dtm       IS '시작 일시 (DOM_DTM, INSERT-only — 애플리케이션 immutable 검증)';
COMMENT ON COLUMN ai_drvn_mod_h.end_dtm         IS '종료 일시 (DOM_DTM, NULL 허용 — 현재 활성 행 표현. 부분 UNIQUE 인덱스로 1행 강제)';
COMMENT ON COLUMN ai_drvn_mod_h.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN ai_drvn_mod_h.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입 — end_dtm UPDATE 시점 추적)';
COMMENT ON COLUMN ai_drvn_mod_h.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN ai_drvn_mod_h.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
```

### 마이그레이션 전략 (무중단)
- 신규 테이블 도입이므로 `partitioning-and-retention.md §2` 의 무중단 마이그레이션 3단계 (NULL 허용 → 백필 → NOT NULL) 불필요 — 즉시 적용 가능
- 적용 시점: 마이그레이션 V_ {다음번호}_ai_drvn_mod_도입.sql (Flyway 또는 init SQL — `common/src/main/resources/db/init/` 또는 `db/migration/` 디렉토리 정책에 따라)
- 보존 정책: 마스터 영구·이력 5년 (`partitioning-and-retention.md §2` 기존 정책 행 비고 갱신 완료)
- 파티셔닝: 변경 빈도 극히 낮음 → 불필요. 단일 테이블 + B-Tree 복합 인덱스 + 부분 UNIQUE 인덱스로 5년 치 조회 성능 충족

### 인덱스 정합성 (`indexing-and-migration.md §1`)
- `idx_ai_drvn_mod_h_proc_start` (proc_id, start_dtm DESC): 등가(proc_id) → 범위·정렬(start_dtm) 순서 — `indexing-and-migration.md §1` 컬럼 순서 규칙 정합
- `uk_ai_drvn_mod_h_proc_active` (proc_id) WHERE end_dtm IS NULL: 부분 UNIQUE — PostgreSQL 부분 인덱스 지원, 동시성 안전망

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 명령 / 테스트 / 조회 |
|---------|----------------------|
| `Process` 마스터 CRUD 단위 테스트 — 조회 + `use_yn` 비활성화 | `./gradlew.bat :api:test --tests com.mo.swtp.proc.service.ProcessServiceTest` PASS |
| `AiDrvnModeService#changeAiDrvnMode` 변경 트랜잭션 3 작업 원자성 — UPSERT + UPDATE(직전 end_dtm) + INSERT | 신규 통합 테스트 `AiDrvnModeServiceIntegrationTest#변경_트랜잭션은_세_작업이_원자적으로_적용된다` PASS |
| 부분 UNIQUE 인덱스 동시성 안전망 — 2개 동시 변경 시 1건은 `AI_MODE_CONCURRENT_UPDATE` (409) | 신규 동시성 시나리오 테스트 `AiDrvnModeServiceConcurrencyTest#동시_변경_시_부분_UNIQUE_인덱스가_409를_반환한다` PASS |
| 변경 API 응답 — `CommonResponseDto<AiDrvnModeDto>` (`code = "SUCCESS"`, `data.aiDrvnModCd = "AI_RECOMD"` 등) | `./gradlew.bat :api:bootRun -Pprofile=local` + curl `PUT /api/proc/{procId}/ai-mode` 응답 검증 |
| Swagger 명세 4 엔드포인트 + DTO `@Schema(implementation = AiDrvnModeCode.class)` 노출 | 로컬 실행 후 `http://localhost:8080/swagger-ui.html` 의 "10. 송수펌프제어 AI 운전모드" Tag 4 엔드포인트 존재 + AiDrvnModeCode allowable values `[AI, AI_RECOMD, AI_ANLS]` 노출 검증 |
| ErrorCode 3종 `httpStatus` 단일 필드 | `check-errorcode-contract.sh` 훅 저장 시 차단 없음 + grep 검증 `private final String` 매칭 없음 |
| 이력 응답 DTO `BaseAuditResponseDto` 미상속 + 4컬럼 직접 선언 | grep 검증 `AiDrvnModeHistoryDto.*extends BaseAuditResponseDto` 매칭 없음 + `private LocalDateTime rgstrDtm` 직접 선언 매칭 |
| DDL 마이그레이션 SQL `COMMENT ON COLUMN` 누락 0건 | `check-ddl-column-comment.sh` 훅 저장 시 차단 없음 |
| 초기 시드 데이터 — `proc_m` 에 "송수펌프제어" 1행 INSERT | SQL `SELECT proc_nm FROM proc_m WHERE proc_nm = '송수펌프제어'` 1행 반환 |

## 가정 및 미해결 질문 (PLAN 단계 결정)

| 가정 / 질문 (ANALYZE 미해결) | 분류 | PLAN 단계 결정 |
|--------------------------|------|------------|
| 마스터 `proc_m` 초기 시드 데이터 — DDL INSERT vs 운영 화면 등록 | 미해결 → 결정 | **DDL 마이그레이션에서 INSERT** ("송수펌프제어" 1건). 향후 카테고리 추가 (정수공정·약품투입 등) 는 별도 사이클 (운영 CRUD UI) |
| `AiDrvnModeCode` enum 패키지 위치 — `common.enumtype` vs `proc.enumtype` | 미해결 → 결정 | **`com.mo.swtp.proc.enumtype.AiDrvnModeCode`** — `proc` 도메인 전용 (전사 공통 의미 아님, 향후 분리 가능성 대비). `YnType`·`UserRole` 같은 전사 공통 enum 과 의미 다름 |
| 강제 전환 정책 별도 사이클 분리 — `ai_mode_cd`·`transition_reason` 등 | 가정 → 결정 | **본 사이클 완전 제외**. 향후 사이클은 `indexing-and-migration.md §2` 3단계 무중단 마이그레이션 (NULL 허용 컬럼 추가 → 백필 → NOT NULL 전환) 으로 컬럼 추가 가능 |
| 이력 보존 5년 + 파티셔닝 불필요 | 가정 → 결정 | **확정**. `partitioning-and-retention.md §2` 행 비고 갱신 완료 (2026-05-20 사이클 2 재도입 명시) |
| 부분 UNIQUE 인덱스 — UPDATE-then-INSERT 동일 트랜잭션 의무 | 가정 → 결정 | **`AiDrvnModeService#changeAiDrvnMode` 메서드 레벨 `@Transactional` 강제**. 트랜잭션 외부 호출 시 `AI_MODE_CONCURRENT_UPDATE` (409) 매핑 |
| `Process` ↔ `AiDrvnMode` 1:1 관계 PK 적용 패턴 | 미해결 → 결정 | **`@MapsId` + `@OneToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = "proc_id")`**. `AiDrvnMode.procId` 자체 PK, `process` 필드는 lazy 로드 |
| 새 가정 (PLAN 단계 추가): `Hibernate` UPSERT 구현 | 신규 | Hibernate 6 의 `JpaRepository.save()` 는 `Persistable.isNew()` 기반 INSERT/UPDATE 분기. `AiDrvnMode` 의 PK 가 외부 할당이므로 `Persistable<String>` 구현 + `BaseEntity.newEntity` 플래그 활용. 또는 명시적 `EntityManager.find()` + `null` 분기 후 `persist`/`merge` |
| 새 가정 (PLAN 단계 추가): DDL 마이그레이션 파일 위치 | 신규 | `common/src/main/resources/db/init/` 디렉토리에 신규 SQL 파일 추가 (Flyway 또는 init 정책에 따라 — `multi-tenant.md` 확인). 파일명 형식 `V{다음번호}__proc_ai_drvn_mod_도입.sql` (Flyway 컨벤션) 또는 init 디렉토리 정책 — TASK 단계 확정 |
| 새 가정 (PLAN 단계 추가): PostgreSQL 13+ 또는 `pgcrypto` 활성화 (DBA 권고 반영) | 신규 | `proc_m` 시드 INSERT 의 `gen_random_uuid()` 는 PostgreSQL 13+ 의 내장 함수 (`pg_catalog`). 12 이하 버전이거나 환경에 따라 `pgcrypto` 확장이 필요할 수 있다. TASK 단계 DDL 작성 시 `CREATE EXTENSION IF NOT EXISTS pgcrypto;` 선행 라인 추가 검토. 운영 PostgreSQL 버전 확인 필요 |
| 새 가정 (PLAN 단계 추가): 시드 데이터 `proc_id` 운영 환경 고정 UUID 필요 여부 (도메인 전문가 권고 반영) | 신규 | 멀티테넌트 지자체별 동일 `procId` 참조 필요성 — 본 사이클은 단일 지자체 가정 (현 운영 형태). `gen_random_uuid()` 결과를 그대로 사용하되, 지자체별 동일 UUID 가 필요해질 경우 TASK 단계에서 고정 UUID 상수 변경 가능. 본 사이클 범위 밖 |

## 제외 사항

- AI 시스템 상태 (`ai_mode_cd`) 컬럼 도입
- `transition_reason` 컬럼 + 5종 사유 (`SCADA_TIMEOUT`·`MANUAL_EXPIRE`·`OUTBOUND_FAIL`·`SYSTEM_INIT`·`USER_SELECT`)
- 스케줄러 기반 강제 전환 로직
- `last_rcv_dtm` · `expire_dtm` 컬럼
- 운영 화면에서 `proc_m` 카테고리 CRUD UI (시드 외 추가는 별도 사이클)
- AI 추론 서버 호출 클라이언트 (사이클 2 별도 안건)
- 인터록 검사 · 제어 명령 발행 · 모드 전환 사유 분기

## 부록: 도메인/DB 검토 결과 (2026-05-20)

- **wtp-domain-expert**: 블로커 0건, 권고 1건 (트랜잭션 작업 순서 명확화 — 본 PLAN `## 트랜잭션·동시성 전략` 에 SELECT FOR UPDATE 사전 단계 + 데이터 변경 3 작업 구조 명시로 해소), 참고 2건. 종합: 본 사이클은 사용자 의도 단일축 표출·이력 도입에 한정하여 `ot-integration.md §5` 보류 영역 (강제 전환·시스템 상태) 과 명확히 경계를 설정했으며, 이력 기록 의무를 단일 트랜잭션 원자성으로 구현하는 구조가 도메인 정합성을 충족
- **wtp-dba-reviewer**: 블로커 0건, 권고 1건 (`gen_random_uuid()` PostgreSQL 버전 전제 — 본 PLAN `## 가정 및 미해결 질문` 에 추가로 해소), 참고 2건. 종합: 3종 신규 테이블의 컬럼 타입·NULL 정책·인덱스 설계 (B-Tree 복합 + 부분 UNIQUE)·FK 정책 (시계열→마스터 FK 금지 정합)·DDL COMMENT·트랜잭션 격리·보존 정책 모두 룰 정합

## 예상 산출물

- [태스크](../../../tasks/20260520/송수펌프제어분석-2번섹션/TASK1.md) — 본 PLAN approved 후 자동 생성
- 신규 Java 파일 약 14개:
  - 엔티티 3 (`Process`·`AiDrvnMode`·`AiDrvnModeHistory`)
  - DTO 4 (`ProcDto`·`AiDrvnModeDto`·`AiDrvnModeUpsertDto`·`AiDrvnModeHistoryDto`)
  - Repository 3 (`ProcessRepository`·`AiDrvnModeRepository`·`AiDrvnModeHistoryRepository`)
  - Service 2 (`ProcessService`·`AiDrvnModeService`)
  - Controller 1 (`ProcController`)
  - enum 1 (`AiDrvnModeCode`)
  - ErrorCode 1 (`ProcErrorCode`)
- 신규 SQL 파일 1개 (DDL + 시드)
- 신규 테스트 파일 약 3개:
  - `ProcessServiceTest` (단위)
  - `AiDrvnModeServiceIntegrationTest` (변경 트랜잭션 통합)
  - `AiDrvnModeServiceConcurrencyTest` (동시성 시나리오)
