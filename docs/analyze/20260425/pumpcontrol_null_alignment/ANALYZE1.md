---
status: approved
created: 2026-04-25
updated: 2026-04-25
---
# pumpcontrol 도메인 DDL NULL 정책 정합성 — 도메인 분석

## 작업 배경

- **요청 요약**: 직전 검토 리포트(`~\.claude\plans\playwright-swagger-crispy-meadow.md` §3) 에서 직전 작업의 ANALYZE1 (`docs/analyze/20260422/pumpcontrol/ANALYZE1.md`, status: approved) 이 일부 컬럼의 NULL 정책을 명시하지 않은 채 PLAN 으로 진행되었고, V1/V2 SQL 작성자가 보수적으로 NULL 허용을 선택했음을 식별. 본 ANALYZE 는 누락된 NULL 정책을 명시적으로 결정하고, NOT NULL 전환이 필요한 컬럼에 대한 무중단 마이그레이션 절차를 PLAN 으로 전달한다.
- **작업 성격**: 정합성 충돌 해소가 아니라 **ANALYZE1 누락 결정의 후속 보강**. 신규 엔티티·신규 용어·신규 도메인 등록 없음. 기존 컬럼 4종 NULL 정책 변경 + `transition_reason` enum 값 1종 추가가 핵심.
- **외부 산출물**: 없음 (직전 ANALYZE1 / PLAN1 / PLAN2 / V1~V5 SQL 만 참조)
- **관련 문서**:
  - 직전 분석 — `docs/analyze/20260422/pumpcontrol/ANALYZE1.md` (status: approved)
  - 직전 계획 — `docs/plan/20260425/pumpcontrol/PLAN1.md` · `PLAN2.md`
  - DDL 현행 — `common/src/main/resources/db/init/V1__pumpcontrol_master_tables.sql`, `V2__pumpcontrol_partition_tables.sql`
  - 룰 — `.claude/rules/db-indexing-and-migration.md §2` (NOT NULL 추가 3단계), `.claude/rules/ot-integration.md §5` (운전 모드 전환 정책)

## 회의록 (5인 팀 토픽 주도)

### 안건 1: `pump_m.rated_head` · `pump_m.rated_flwrt` NULL 정책

- **호출 에이전트**: `wtp-dba-reviewer`, `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: NULL 허용 유지 권고 (중간). 시운전 단계 미입력이 운영상 실재. AI 예측 모델이 정격값을 사용하더라도 NULL 행에 대한 예측 제외 로직은 애플리케이션이 담당하는 것이 DDL 보다 유연. 단 예측 모델이 정격값을 필수 입력 변수로 확정하는 시점에 NOT NULL 전환 필요.
  - **wtp-domain-expert**: NOT NULL 강제 권장. 정수장에서 펌프 마스터 행은 기계 납품 전 운전 절차서에 설계 정격값이 명시되며, 실측 전이라도 제조사 명판(Nameplate) 기준값이 항상 존재. AI 예측 모델(`pump_predc_h`) 의 Python 서비스가 NULL 행을 받으면 예측 정확도 저하 또는 예외 반환. CircuitBreaker OPEN 시 SCADA_TIMEOUT 강제 전환 연쇄로 `ot-integration.md §5` 운전 모드 안전성 침해 가능.
- **Round 2 (사용자 결정)**: **NOT NULL 강제 채택**. 도메인 전문가 권고 우위. 운영 절차서에 명판값 입력 의무 명시. 시운전 단계 미입력 사례는 명판 기준값 입력으로 해소.
- **결론**: `rated_head`, `rated_flwrt` 모두 NOT NULL 전환. 무중단 마이그레이션 3단계(`db-indexing-and-migration.md §2`) — 1단계는 본 케이스 해당 없음(컬럼 이미 존재) → 2단계 백필(현 시점 운영 데이터 없음, Flyway 단일 스크립트 가능) + 3단계 `ALTER COLUMN SET NOT NULL` 적용.

### 안건 2: `dwt_m.min_req_prsr` NULL 정책

- **호출 에이전트**: `wtp-dba-reviewer`, `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: NOT NULL 강제 권고 (높음). NULL 인 경우 `InterlockValidator` 가 임계값 비교를 수행할 수 없어 "규칙 미등록 시 통과" 분기와 구별 불가. NULL(미입력) 과 "제약 없음(0)" 이 동일 값이 되어 운영 사고 위험. `min_req_prsr = 0` 을 명시적 룩아웃으로 채택 권고.
  - **wtp-domain-expert**: NULL 허용 유지 + InterlockValidator fail-safe 차단 권장. 운전 중 배수지 압력 하한 위반은 수용가 단수·역류 오염 위험. NULL 인 배수지를 스킵하면 무압력 상태에서도 펌프 기동 가능 시나리오 성립. NULL 인 배수지에 대한 인터록 평가는 "스킵" 이 아닌 "검사 불가 = 차단" 으로 처리.
- **Round 2 (사용자 결정)**: **NOT NULL 강제 채택**. DBA 권고 우위. 두 의견 모두 "운영 사고 위험" 을 인정하나, 도메인 의견의 fail-safe 차단은 애플리케이션 코드 분기 의존도가 높음. NOT NULL + 명시적 0 룰은 DB 레벨에서 의미를 일관되게 강제. 분기별 이력 도입(별도 작업 `dwt_pressure_history`) 시 마스터 단일 필드는 "현재 유효 압력 기준값" 캐시로 동작.
- **결론**: `min_req_prsr` NOT NULL 전환. 백필 후 `SET NOT NULL`. 추가 검토: `InterlockValidator` 가 본 작업 범위 외이므로 NULL 처리 로직 자체는 별도 ANALYZE 의 `ot_integration_inbound` 작업에서 결정.

### 안건 3: `ai_drvn_mod_h` 의 prev/new 4컬럼 NULL 정책

- **호출 에이전트**: `wtp-dba-reviewer`, `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: prev_* (2건) NULL 허용 유지, new_* (2건) NOT NULL 전환 권고 (중간). 시스템 초기 진입(prev 없음) 시나리오를 NULL 로 표현하면 `WHERE prev_ai_drvn_mod IS NULL` 조건이 자연스럽게 "초기 진입" 필터링. 정상 전환 트랜잭션에서 `new_*` 가 비어있는 케이스는 존재하지 않으므로 NOT NULL 전환은 무비용.
  - **wtp-domain-expert**: prev_* NULL 허용 유지 적절. 첫 행은 `prev_*` NULL 처리하되 운영자 조회 명확성을 위해 `transition_reason` 에 `SYSTEM_INIT` enum 값 추가 권장. SCADA 표준 관행상 최초 부팅 시점 이전 상태는 "미정의" 로 표현. `OUTBOUND_FAIL` 의 경우 `ai_mode_cd` 만 강제 전환되고 `ai_drvn_mod` 불변이므로 `prev_ai_drvn_mod = new_ai_drvn_mod` 인 행이 정상.
- **Round 2**: 두 에이전트 의견 합치 영역 확인. DBA 의 `new_*` NOT NULL 전환 + 도메인의 `SYSTEM_INIT` 추가 모두 채택 (안건 4 와 결합).
- **결론**:
  - `prev_ai_drvn_mod`, `prev_ai_mode_cd`: NULL 허용 유지 (시스템 초기 진입 표현)
  - `new_ai_drvn_mod`, `new_ai_mode_cd`: **NOT NULL 전환** (정상 전환 트랜잭션에서 항상 채워지므로 무비용)
  - 마이그레이션은 백필 후 `SET NOT NULL`

### 안건 4: `transition_reason` enum 값 `SYSTEM_INIT` 추가

- **호출 에이전트**: `wtp-domain-expert` (안건 3 답변에 포함)
- **Round 1 답변 요약**: 시스템 최초 부팅 시 첫 전환 행을 운영자가 조회할 때 NULL `prev_*` 만으로는 "초기화" 의미가 명확하지 않음. `USER_SELECT` / `SCADA_TIMEOUT` / `MANUAL_EXPIRE` / `OUTBOUND_FAIL` 4종은 모두 기존 운전 상태에서 전환하므로 `prev_*` 가 채워짐. `SYSTEM_INIT` 은 전환 사유가 "시스템 첫 등록" 임을 명시적으로 표현하여 운영자 UI 라벨로 "초기화" 표시 가능.
- **결론**: `transition_reason` enum 값 5종으로 확장 — `USER_SELECT` / `SCADA_TIMEOUT` / `MANUAL_EXPIRE` / `OUTBOUND_FAIL` / **`SYSTEM_INIT`(신규)**. 룰 `ot-integration.md §5` 갱신 필요. Java `TransitionReason` enum 도 동일 변경.

### 안건 5: `use_yn` DB 표현 일관성 — 본 ANALYZE 분리 결정

- **호출 에이전트**: `wtp-glossary-manager`, `wtp-dba-reviewer`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: SQL 정정 (사전 정의 옳음 — `DOM_YN = VARCHAR(1)`). `user_m.use_yn` `CHAR(1)` 비표준 — `VARCHAR(1)` 로 정정. CHECK 제약은 `DOM_YN` 표준 정의에 포함시켜야 함. DEFAULT 'Y' 는 의미론적 맥락 의존이므로 `standard-terms.md` 의 `use_yn` 비고란에만 명시. DB CHECK 는 직접 SQL·외부 도구 우회 차단의 안전망. **DDL 마이그레이션 작업은 표준 사전 범위 외 → 별도 작업으로 분리 권고**.
  - **wtp-dba-reviewer**: `user_m` CHAR→VARCHAR 정정 + `pump_m`/`pump_interlock_p` CHECK·DEFAULT 추가 권고. CHECK 제약은 잘못된 직접 INSERT 방어, DEFAULT 'Y' 는 신규 등록 기본값 명시.
- **Round 2 (사용자 결정)**: **별도 ANALYZE 로 분리**. 본 ANALYZE 는 pumpcontrol 도메인 NULL 정책 정합성에 집중. `use_yn` 안건은 도메인 범위 밖 (`user_m` 포함, `auth` 도메인 영향) 이며 DDL 마이그레이션·룰 갱신 분량이 독립적. 별도 슬러그 `use_yn_consistency` 로 후속 작업 등록.
- **결론**: 본 ANALYZE 의 룰 갱신 지시서·PLAN 결정 사항에서 `use_yn` 관련 항목 **제외**. 다만 본 문서 §후속 작업 항목에 `use_yn_consistency` 별도 ANALYZE 등록 필요성을 기록.

## 표준 사전 카탈로그

### 신규 표준 단어

없음. 본 ANALYZE 는 NULL 정책 결정이 핵심이므로 단어 신규 등록 사유 없음.

### 신규 표준 데이터 도메인

없음. `DOM_QTY_15_4` (NULL 허용 기본) 와 `DOM_CODE_20` (NOT NULL 기본) 의 개별 컬럼 단위 더 엄격한 NULL 적용 — `standard-data-domains.md §기본 NULL 정책 해설` 의 "개별 엔티티에서 NULL 정책을 더 엄격하게 (`NULL 허용` → `NOT NULL`) 설정할 수 있다" 조항에 부합.

### 신규 표준 용어

없음. 다만 기존 `transition_reason` 컬럼의 enum 값 집합에 `SYSTEM_INIT` 추가 — 표준 용어 표 자체는 변경 없음, 룰 `ot-integration.md §5` 만 갱신.

## 신규 엔티티/DB 컬럼

본 ANALYZE 는 신규 엔티티·신규 컬럼 등록 없음. 기존 컬럼 5종의 NULL 정책 변경 + 1개 enum 값 추가만 발생.

### NULL 정책 변경 컬럼 5종

| 테이블 | 컬럼 | 현행(SQL V1/V2) | 변경 후 | 마이그레이션 비용 |
|--------|------|--------------|--------|---------------|
| `pump_m` | `rated_head` | `NUMERIC(15,4)` NULL | `NUMERIC(15,4)` **NOT NULL** | 백필 (운영 데이터 없음 가정 — Flyway 단일 스크립트) → `SET NOT NULL` |
| `pump_m` | `rated_flwrt` | `NUMERIC(15,4)` NULL | `NUMERIC(15,4)` **NOT NULL** | 동일 |
| `dwt_m` | `min_req_prsr` | `NUMERIC(15,4)` NULL | `NUMERIC(15,4)` **NOT NULL** | 동일 |
| `ai_drvn_mod_h` | `new_ai_drvn_mod` | `VARCHAR(20)` NULL | `VARCHAR(20)` **NOT NULL** | 백필 (현 시점 이력 행 없음) → `SET NOT NULL` |
| `ai_drvn_mod_h` | `new_ai_mode_cd` | `VARCHAR(20)` NULL | `VARCHAR(20)` **NOT NULL** | 동일 |

> `prev_ai_drvn_mod`, `prev_ai_mode_cd` 는 NULL 허용 유지(시스템 초기 진입 표현 의미 보존).

### enum 값 추가 1종

| 위치 | 컬럼 | 현행 enum 값 (4종) | 변경 후 enum 값 (5종) |
|------|------|------------------|---------------------|
| `ai_drvn_mod_h.transition_reason` | VARCHAR(30), Java `TransitionReason` enum | `USER_SELECT` / `SCADA_TIMEOUT` / `MANUAL_EXPIRE` / `OUTBOUND_FAIL` | + **`SYSTEM_INIT`** (시스템 최초 등록 시 첫 행 표현) |

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 |
|----------|--------|
| 직전 ANALYZE1 §pump_m·§dwt_m·§ai_drvn_mod_h 절의 NULL 정책 미명시 | 본 ANALYZE 가 명시적 결정 — `rated_head`/`rated_flwrt`/`min_req_prsr`/`new_ai_drvn_mod`/`new_ai_mode_cd` NOT NULL, `prev_*` NULL 유지 |
| V1/V2 SQL 의 NULL 허용 보수적 선택 | PLAN 단계에서 `V6__pumpcontrol_null_policy.sql` (또는 동등) 신규 마이그레이션 작성, 기존 V1/V2 직접 수정 금지 (배포 불변 원칙) |
| `ot-integration.md §5` transition_reason 4종 enum 명시 | `SYSTEM_INIT` 추가하여 5종으로 갱신 |
| `standard-terms.md` `transition_reason` 비고 미존재 | 본 ANALYZE 결정 사항을 비고로 명시 — 5종 enum 값 + `prev_*` NULL 허용 정책 차이 설명 |
| 안건 5 `use_yn` DB 표현 불일치 (`user_m` CHAR vs `pump_m` VARCHAR, CHECK·DEFAULT 비대칭) | 본 ANALYZE 범위 분리. 별도 슬러그 `use_yn_consistency` 작업 등록 — §후속 작업 항목 참조 |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 변경 (코드 레벨)

- **엔티티 클래스 `@Column` 속성 변경 3건** (5컬럼):
  - `Pump.java` (`pump_m`): `rated_head`, `rated_flwrt` 에 `nullable = false` 추가
  - `DistributionWaterTank.java` (`dwt_m`): `min_req_prsr` 에 `nullable = false` 추가
  - `AiDrvnModeHistory.java` (`ai_drvn_mod_h`): `new_ai_drvn_mod`, `new_ai_mode_cd` 에 `nullable = false` 추가 (단 `prev_*` 는 현행 nullable 유지)
- **enum 클래스 변경 1건**:
  - `TransitionReason.java`: `SYSTEM_INIT` 값 추가 (5종)
- **Service 레벨 영향 점검**:
  - `PumpService` 등록·수정 메서드: `rated_head`/`rated_flwrt` null 입력 시 예외 응답 (Validator 에 `@NotNull` 추가)
  - `DwtService` 등록·수정 메서드: `min_req_prsr` 동일
  - `AiModeTransitionService`: 시스템 초기 등록 시 `transition_reason = SYSTEM_INIT` + `prev_*` null 로 INSERT 하는 시드 로직 검토 (별도 작업 가능성 — PLAN 단계 결정)

### DB 설계 변경

- **신규 마이그레이션 SQL 1건 작성** — 경로: `common/src/main/resources/db/init/V6__pumpcontrol_null_policy.sql` (또는 PLAN 에서 V 번호 재결정)
- **마이그레이션 절차** (`db-indexing-and-migration.md §2` 3단계 중 2~3단계만 적용 — 컬럼 이미 존재):
  ```sql
  -- 2단계: 백필 (현 시점 운영 데이터 없음 — 안전 기본값 적용)
  UPDATE pump_m
     SET rated_head  = COALESCE(rated_head, 0),
         rated_flwrt = COALESCE(rated_flwrt, 0)
   WHERE rated_head IS NULL OR rated_flwrt IS NULL;

  UPDATE dwt_m
     SET min_req_prsr = COALESCE(min_req_prsr, 0)
   WHERE min_req_prsr IS NULL;

  UPDATE ai_drvn_mod_h
     SET new_ai_drvn_mod = COALESCE(new_ai_drvn_mod, 'AI'),
         new_ai_mode_cd  = COALESCE(new_ai_mode_cd, '0')
   WHERE new_ai_drvn_mod IS NULL OR new_ai_mode_cd IS NULL;

  -- 3단계: NOT NULL 제약 추가
  ALTER TABLE pump_m         ALTER COLUMN rated_head      SET NOT NULL;
  ALTER TABLE pump_m         ALTER COLUMN rated_flwrt     SET NOT NULL;
  ALTER TABLE dwt_m          ALTER COLUMN min_req_prsr    SET NOT NULL;
  ALTER TABLE ai_drvn_mod_h  ALTER COLUMN new_ai_drvn_mod SET NOT NULL;
  ALTER TABLE ai_drvn_mod_h  ALTER COLUMN new_ai_mode_cd  SET NOT NULL;
  ```
- **백필 기본값 결정 근거**:
  - `rated_head` / `rated_flwrt` = 0 — 운영 데이터 없음 가정. 명판값 미입력 사유로 임시 0 입력. 운영 도입 시 정확값으로 UPDATE 필요 (운영 절차서에 명시)
  - `min_req_prsr` = 0 — "제약 없음" 의미. NOT NULL 강제 후 운영 절차서에 실제값 입력 의무 명시
  - `new_ai_drvn_mod` = `'AI'`, `new_ai_mode_cd` = `'0'` — 시스템 초기 등록 시 운전 모드 기본값. 단 현 시점 이력 행 없음 가정으로 백필 미수행 가능성 높음

### 적용할 패턴

- **무중단 마이그레이션**: `db-indexing-and-migration.md §2` 3단계 절차 준수. `ALTER TABLE ... ADD COLUMN col NOT NULL DEFAULT val` 절대 금지 (대용량 테이블 전체 락).
- **enum 값 추가 패턴**: Java enum 신규 값 추가 + 룰 문서 동기 갱신 + DB CHECK 제약 사용 안 함 (현행 정책). 운영자 UI 매핑은 프론트엔드 명세에서 담당.
- **PLAN 단계 시드 데이터 검토**: 시스템 최초 등록 시 `ai_drvn_mod_h` 에 `transition_reason = 'SYSTEM_INIT'` 행 1건 자동 INSERT 여부 결정. 정수조별 `ai_drvn_mod_p` 행이 처음 생성될 때 트리거되는 형태로 구현 가능.

### 본 작업 범위 경계

- **포함**:
  - 엔티티 5컬럼 `nullable = false` 변경 + `TransitionReason` enum 값 1건 추가
  - 신규 마이그레이션 SQL 1건 (백필 + `SET NOT NULL` 5건)
  - 룰 갱신 4건 (`ot-integration.md §5` enum 5종, `standard-terms.md` 비고 3건)
- **분리** (별도 작업 `use_yn_consistency` — §후속 작업 참조):
  - `user_m.use_yn` `CHAR(1)` → `VARCHAR(1)` 정정
  - `pump_m.use_yn`, `pump_interlock_p.use_yn` 에 CHECK 제약·DEFAULT 'Y' 추가
  - `DOM_YN` 표준 정의에 CHECK 제약 명시 추가
- **분리** (별도 작업 `pump_rated_history` — 도메인 전문가 안건 1 답변에 언급):
  - 정격값 변경 이력 보존을 위한 별도 이력 테이블 도입 검토
- **분리** (별도 작업 `dwt_pressure_history`):
  - 배수지 분기별 압력 이력 (직전 ANALYZE1 §본 작업 범위 경계 에서 이미 분리)

## 후속 작업

본 ANALYZE 가 식별했으나 범위 외로 분리한 작업:

1. **`use_yn_consistency`** (필수): `user_m.use_yn` CHAR→VARCHAR + CHECK·DEFAULT 일관 적용 + `DOM_YN` 사전 비고 보강. wtp-glossary-manager 권고 반영. ANALYZE 별도 작성 필요.
2. **`pump_rated_history`** (선택): 펌프 정격값 변경 이력 보존. AI 예측 모델 재학습 시 과거 정격값 추적 필요성 발생 시 도입.
3. **`ot_integration_inbound`** (이미 식별됨): `InterlockValidator` NULL 처리·센서 품질·알람 4단계. 직전 ANALYZE1 §안건 7 에서 분리.

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 작업 착수 전 모든 체크박스를 완료해야 한다.

- [x] `.claude/rules/ot-integration.md` — §5 "**모드 전환 이력 기록 의무**" 절의 transition_reason enum 값 목록을 4종 → 5종으로 갱신 (`USER_SELECT` / `SCADA_TIMEOUT` / `MANUAL_EXPIRE` / `OUTBOUND_FAIL` / **`SYSTEM_INIT`**). `SYSTEM_INIT` 정의 한 줄 추가: "시스템 최초 등록 시 첫 전환 행 표현 — `prev_*` 컬럼 NULL 허용"
- [x] `.claude/rules/dict/standard-terms.md` — `min_req_prsr` 행 비고란에 "NOT NULL 정책 — DOM_QTY_15_4 기본 NULL 정책에서 더 엄격하게 적용 (인터록 평가 NULL/미입력 구별 불가 방지, pumpcontrol_null_alignment ANALYZE1)" 명시
- [x] `.claude/rules/dict/standard-terms.md` — `rated_head` 행 비고란에 "NOT NULL 정책 — 제조사 명판값 항상 존재, AI 예측 모델 정규화 인자 (pumpcontrol_null_alignment ANALYZE1)" 명시
- [x] `.claude/rules/dict/standard-terms.md` — `rated_flwrt` 행 비고란에 "NOT NULL 정책 — 동일 사유 (pumpcontrol_null_alignment ANALYZE1)" 명시

## 산출물

- [계획안](../../../plan/20260425/pumpcontrol_null_alignment/PLAN1.md)
