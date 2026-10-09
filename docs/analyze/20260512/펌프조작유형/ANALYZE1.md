---
status: approved
created: 2026-05-12
updated: 2026-05-12
---
# 펌프조작유형 — 도메인 분석

## 작업 배경

- 직전 세션 검증 결과 (2026-05-12) — 사용자 주장 "펌프의 자동/반자동은 사용자가 설정하는 값이 아니라 펌프의 물리적 설계값이며, 마스터 데이터가 관리해야 한다. 자동 조작 가능 펌프와 반자동 조작 가능 펌프는 **상호 배타** (한 펌프는 하나의 조작유형만 보유)" 검증 완료.
- 갭 식별: 현재 `pump_m` (`instrument_m` JOINED 자식, `equip_type_cd='PUMP'`) 자식 전용 컬럼은 `rated_head`·`rated_flwrt`·`tag_nm` 3건뿐. 펌프 조작유형 표현 컬럼 부재. 모든 펌프가 자동/반자동 모두 지원 가정으로 운영 중.
- 관련 미결 안건: [송수펌프제어분석 ANALYZE1 안건 7](../../20260508/송수펌프제어분석/ANALYZE1.md) — "화면 §2 자동/반자동 표시 기준 모호" 가 2026-05-08 사용자 결정으로 차후 ANALYZE 이관. 본 사이클이 마스터 도메인 측 상위 결정을 담당.
- 외부 산출물: 없음 (직전 세션 검증 보고서 = 본 사이클의 입력 컨텍스트).

> **백지화 컨텍스트 인지** (2026-05-12 IMPL 단계 발견): 본 사이클 진행 중 backend 의 `com.mo.swtp.pump`·`com.mo.swtp.ai` 패키지가 백지화된 상태임이 확인되었다 ([`PumpOprtngType.java`](../../../../common/src/main/java/com/mo/swtp/instrument/domain/PumpOprtngType.java) 사용자 직접 수정으로 명시 — "pump+AI 도메인 백지화 사이클 1"). 본 ANALYZE 의 안건 5·6 답변에서 인용한 `ot-integration.md §5` AI 운전 모드 이중 체계·`ai_mode_cd`/`ai_drvn_mod` 인프라는 **백지화 이전 컨텍스트** 기준이며, 결론 "별도 사이클 분리" 는 **사이클 2 에서 시스템 상태 enum 신규 정의 후 재인용** 예정. 본 사이클 (사이클 1) 의 마스터 도메인 측 결정 (안건 1·2·3·4) 은 백지화 영향 없이 유효.

## 회의록 (5인 회의 토픽 주도)

### 안건 1: `type` 표준 단어 등록 모순 해소

- 호출 에이전트: `wtp-glossary-manager`, `wtp-domain-expert`
- 배경: `standard-words.md` 에 `type` 단독 entry 부재. 그러나 `standard-terms.md` 에 `equip_type_cd`·`facility_type_cd`·`dwld_format_cd` 등 `type` 조합 컬럼 다수 등록·운영. 동의어 표는 `tag_se_cd` 권장 사유로 "`type` 미등록 단어" 명기 — 모순 상태.
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `type` 정식 등록 + `se` 와 의미 경계 명문화 권고. `type` = 자식 종류·Discriminator 성격 (`facility_type_cd`·`equip_type_cd` 선례), `se` = 같은 마스터 내 세부 구분 (`tag_se_cd` FRI/PRI/LEI 선례). 본 사이클 컬럼은 펌프 능력 분류 → **`oprtng_type_cd`** 채택. `oprtng_se_cd` 는 `se` 의미 패밀리와 부적합.
  - **wtp-domain-expert**: 동의 — `se`(세부)=측정/관측 대상의 분류축, `type`(종류)=구조적·기능적 실체 분류 (상속·구분). 펌프 조작유형은 펌프 실체의 능력 분류이므로 `oprtng_type_cd` 정합. 도메인 4영역 충돌: 비해당. 등급: 참고.
- **결론**: `type` 표준 단어 ROOT 등록 + `oprtng_type_cd` 채택 + `tag_se_cd` 동의어 표 비고 갱신.

### 안건 2: 조작유형 컬럼 위치 — `pump_m` 자식 vs `instrument_m` 부모

- 호출 에이전트: `wtp-dba-reviewer`, `wtp-backend-engineer`
- 배경: `pump_m` 자식 전용 vs `instrument_m` 부모 공통. 펌프 한정 속성 (사용자 명시) vs 향후 valve/flwmtr 도입 시 일반화 가능성.
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: `pump_m.oprtng_type_cd` 자식 전용 채택. valve/flwmtr 도입 시 각 자식 독립 컬럼 추가 권고 (instrument 부모 이관은 자식 6종 중 3건 이상 도입 시 ANALYZE 재논의). 단독 인덱스 미적용 — 카디널리티 2 단독 B-Tree 는 Seq Scan 선호 (`db/indexing-and-migration.md §3.4 DOM_YN` 정책 유추).
  - **wtp-backend-engineer**: 자식 전용 컬럼 + `@Enumerated(EnumType.STRING)` enum 필드가 `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴` §자식 전용 컬럼 도입 시점 정합. `Pump.create()` 정적 팩토리는 private 생성자 감싸므로 호출처 영향 없음 (호출자 PLAN 일괄 갱신).
- **결론**: `pump_m.oprtng_type_cd` 자식 전용 + `@Enumerated(EnumType.STRING)` enum 필드. 단독 인덱스 미적용.

### 안건 3: 펌프 조작유형 enum 코드값 명명 + 클래스 위치

- 호출 에이전트: `wtp-glossary-manager`, `wtp-backend-engineer`
- 배경: 코드값 후보 3종 — (1) `AUTO_CAPABLE`/`SEMI_AUTO_CAPABLE`, (2) `AUTO_OP`/`SEMI_AUTO_OP`, (3) `FULL_AUTO`/`SEMI_AUTO`. 클래스명·패키지 결정.
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 후보 1 `AUTO_CAPABLE`/`SEMI_AUTO_CAPABLE` 채택. 후보 3 `SEMI_AUTO` 는 `ai_mode_cd` 시스템 상태 `SEMI_AUTO` 와 문자열 동일 → enum 충돌, 즉시 탈락 (ROOT `dict/README.md` §유사 충돌 판정 기준 적용). 후보 2 `op` 는 표준 단어 미등록 + `oprtng` 동의어 충돌 → 탈락. `_CAPABLE` 접미사는 enum 코드값 내부 표현이므로 표준 단어 등록 대상 외.
  - **wtp-backend-engineer**: 클래스명 `PumpOprtngType`, 패키지 `com.mo.swtp.pump.domain` (Pump 와 동거). 풀네임 `Operating` 은 swtp 4-5자 약어 컨벤션 (`prsr`·`flwrt`·`elpwr`·`srl` 선례) 외. `Control` 은 `ctrl`(제어 명령/로그) 비즈니스 도메인 약어와 의미 충돌. `Capability` 등 suffix 는 `coding-discipline.md §2` "요청되지 않은 추상화 금지" 적용으로 불필요.
- **결론**: `PumpOprtngType { AUTO_CAPABLE, SEMI_AUTO_CAPABLE }` enum + `com.mo.swtp.pump.domain` 패키지 (향후 instrument 이관 시 동행).

### 안건 4: NOT NULL 정책 + 기존 행 backfill + DDL CHECK

- 호출 에이전트: `wtp-dba-reviewer`
- 배경: `rated_head`·`rated_flwrt` 선례 (`pumpcontrol_null_alignment ANALYZE1` 2026-04-25 — 제조사 명판값 항상 존재로 NOT NULL). 조작유형도 펌프 설치 시 결정. 기존 V8_2 적용 운영 환경의 행 backfill 전략 필요.
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: (a) NOT NULL 정합 — 물리적 설계값 NULL 허용 시 평가 로직 NULL/미입력 구별 불가. (b) `db/indexing-and-migration.md §2` 3단계 무중단 마이그레이션: ① NULL 허용 ADD COLUMN ② 배치 백필 `AUTO_CAPABLE` ③ SET NOT NULL. ⚠️ `ADD COLUMN ... NOT NULL DEFAULT` 금지. 백필 기본값 `AUTO_CAPABLE` 사유: 한국 지자체 송수펌프 실무상 자동 제어 회로가 표준 설치, 반자동은 노후/특수 예외. (c) DDL CHECK 제약 미적용 — `@Enumerated(EnumType.STRING)` + enum 단일 방어선 (`db/indexing-and-migration.md §3.2 DOM_YN` 정책 유추).
- **결론**: NOT NULL + 3단계 무중단 마이그레이션 + 백필 `AUTO_CAPABLE` + DDL CHECK 미적용. **백필 후 운영자 반자동 펌프 식별 누락 시 도메인 룰 평가 오작동 위험** — PLAN 에 운영자 검토 체크리스트 의무화.

### 안건 5: ai_mode_cd 와의 도메인 제약 + 시설 내 펌프 혼재 시나리오

- 호출 에이전트: `wtp-domain-expert`
- 배경: 펌프 단위 `oprtng_type_cd` (마스터) vs 시설 단위 `ai_mode_cd` (시스템 상태). 시설 내 펌프 혼재·강제 전환 시나리오 검증.
- Round 1 답변 요약:
  - **wtp-domain-expert**: 시나리오 1 혼재 허용 (현장 정수지 실무상 AUTO/SEMI 펌프 혼재 빈번). 시나리오 2 `ai_mode_cd=AI_AUTO` + `SEMI_AUTO_CAPABLE` 펌프 → AI 제어 대상에서 제외 (`ot-integration.md §5 ⚠️ 절대 금지` AI 권고 무단 적용 회피). 시나리오 3 강제 전환 SEMI_AUTO → `AUTO_CAPABLE` 펌프도 시설 모드 우선 반자동 운영 (펌프 능력은 상한 의미). 도메인 4영역 충돌: **인터록 영역 직결** (선행조건 검사 단일 흐름에 펌프 능력 체크 추가 필요). 등급: 권고.
- **결론**: 시설 내 펌프 혼재 허용. `ai_mode_cd` × `oprtng_type_cd` 인터록 제약 검증 로직은 본 사이클 범위 외 — **별도 ANALYZE 사이클로 분리** (본 사이클은 마스터 컬럼 추가까지).

### 안건 6: 화면 표시 기준 (송수펌프제어분석 ANALYZE1 안건 7 연장)

- 호출 에이전트: `wtp-domain-expert`, `wtp-backend-engineer`
- 배경: 송수펌프제어분석 ANALYZE1 안건 7 미결 (2026-05-08 차후 사이클 이관). 본 사이클로 펌프 조작유형 마스터 추가 시 화면 표출 3축 충돌 — 펌프 능력 / `ai_mode_cd` 시스템 상태 / `ai_drvn_mod` 사용자 의도.
- Round 1 답변 요약:
  - **wtp-domain-expert**: 화면 §2 "자동/반자동" 은 **현재 시스템 상태** (`ai_mode_cd`) 1순위 표시 기준. 펌프 능력 (`oprtng_type_cd`) 은 펌프 카드 보조 라벨, 사용자 의도 (`ai_drvn_mod`) 는 별도 영역. 운영자 안전 판단에 직결 — `ai_mode_cd` 미표시 시 강제 전환 (SCADA 5분 초과) 인지 지연 위험. 도메인 4영역: 운전 모드 영역 직결. 등급: 권고.
  - **wtp-backend-engineer**: 응답 DTO 는 3 컬럼 모두 노출 (frontend 가 화면 §2 외 다른 화면 활용 가능 — 수동 모드 시 조작 가능 펌프 필터링 등). 단일 응답 충분, 별도 엔드포인트 분리는 `coding-discipline.md §2` 단순성 위반. 3 enum 필드 모두 `@Schema(implementation = X.class)` 명시 의무 (`api-patterns.md §DTO @Schema(implementation) 명시 패턴`).
- **결론**: 화면 §2 표출 매핑은 **송수펌프제어분석 차후 사이클 결정 사항**으로 유지 (본 사이클은 마스터 컬럼만 확정). 응답 DTO 설계 시 3 컬럼 모두 노출 + `@Schema(implementation)` 명시 권고 — 송수펌프제어분석 후속 PLAN 에서 채택.

## 표준 사전 카탈로그

### 신규 표준 단어

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `type` | 종류 | 신규 | `standard-words.md` 미등록 + `standard-terms.md` 다수 사용 (`equip_type_cd`·`facility_type_cd`·`dwld_format_cd`) 모순 해소. 의미 경계: `type`=Discriminator/자식 종류, `se`=내부 분류 (안건 1 결론) |

### 신규 표준 데이터 도메인

없음 (`DOM_CODE_20` 재사용).

### 신규 표준 용어

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `oprtng_type_cd` | `oprtng` + `type` + `cd` | `DOM_CODE_20` | 신규 | 펌프 조작유형 (자동 조작 가능 ⊕ 반자동 조작 가능, 상호 배타). `PumpOprtngType` enum (AUTO_CAPABLE / SEMI_AUTO_CAPABLE) 매핑. 사용 테이블: `pump_m` (instrument JOINED 자식) (안건 2·3 결론) |

## 신규 엔티티/DB 컬럼

- `pump_m.oprtng_type_cd` — `pump_m` 자식 전용 컬럼 추가. `DOM_CODE_20` (`VARCHAR(20) NOT NULL`). `@Enumerated(EnumType.STRING)` + `PumpOprtngType` enum 매핑. 단독 인덱스 미적용 (카디널리티 2, `db/indexing-and-migration.md §3.4 DOM_YN` 정책 유추).
- `PumpOprtngType` enum 신설 — 패키지 `com.mo.swtp.pump.domain`. 값: `AUTO_CAPABLE` (자동 조작 가능 펌프), `SEMI_AUTO_CAPABLE` (반자동 조작 가능 펌프). 상호 배타 단일 값 (사용자 명시).
- `Pump` 자식 엔티티 — 자식 전용 필드 `private PumpOprtngType oprtngType` 추가. `Pump.create()` 정적 팩토리 시그니처에 `PumpOprtngType` 인자 추가 (호출처 PLAN 일괄 갱신).

## 기존 사전·패턴과의 충돌

| 항목 | 위치 | 해소책 |
|------|------|--------|
| `standard-terms.md` 동의어 표 `tag_se_cd` 행 비고 "`type` 미등록 단어" 명기 | `swtp/backend/.claude/rules/dict/standard-terms.md` | `type` 정식 등록 후 비고 갱신 — "`tag_se_cd` 는 측정 항목 내부 분류이므로 `se` 사용. Discriminator 성격은 `type` 사용 — `equip_type_cd`·`facility_type_cd` 선례 참조" (안건 1 결론) |
| 송수펌프제어분석 ANALYZE1 안건 7 — 화면 §2 표시 기준 모호 | `backend/docs/analyze/20260508/송수펌프제어분석/ANALYZE1.md` | 본 사이클은 마스터 도메인 측 상위 결정만 확정 (펌프 능력 `oprtng_type_cd`). 화면 §2 표출 매핑 (`ai_mode_cd` vs `ai_drvn_mod` vs `oprtng_type_cd`) 은 송수펌프제어분석 차후 사이클 범위 유지 (안건 6 결론) |
| `ai_mode_cd` × `oprtng_type_cd` 인터록 제약 | `pump_interlock_p` (예정) | 시설 `ai_mode_cd=AI_AUTO` 시 `SEMI_AUTO_CAPABLE` 펌프 AI 제어 제외 룰 — **별도 ANALYZE 사이클로 분리** (안건 5 결론). 본 사이클 범위 외 |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

1. `PumpOprtngType` enum 신설 (`com.mo.swtp.pump.domain`) — `AUTO_CAPABLE`, `SEMI_AUTO_CAPABLE`. 상호 배타.
2. `Pump` 자식 엔티티에 `@Enumerated(EnumType.STRING) @Column(name="oprtng_type_cd", nullable=false, length=20)` private `PumpOprtngType` 필드 추가.
3. `Pump.create()` 정적 팩토리 시그니처에 `PumpOprtngType` 인자 추가. 호출처 PLAN 단계 일괄 갱신.
4. 응답 DTO 도입 시 `@Schema(description="펌프 조작유형", implementation = PumpOprtngType.class)` 명시 — 송수펌프제어분석 후속 PLAN 에 권고.

### DB 설계 변경 초안

- 마이그레이션 SQL: `V8_3__pump_m_oprtng_type.sql` (또는 다음 사용 가능 번호 — PLAN 단계 확정).
- 3단계 무중단 마이그레이션 (`db/indexing-and-migration.md §2`):
  1. `ALTER TABLE pump_m ADD COLUMN oprtng_type_cd VARCHAR(20);` (NULL 허용, 즉시 완료)
  2. 배치 백필: `UPDATE pump_m SET oprtng_type_cd = 'AUTO_CAPABLE' WHERE oprtng_type_cd IS NULL;`
  3. `ALTER TABLE pump_m ALTER COLUMN oprtng_type_cd SET NOT NULL;`
- DDL CHECK 미적용 (enum 단일 방어선).
- `COMMENT ON COLUMN pump_m.oprtng_type_cd IS '펌프 조작유형 (DOM_CODE_20, AUTO_CAPABLE/SEMI_AUTO_CAPABLE — 펌프의 물리적 설계값, PumpOprtngType enum 매핑)';` 의무 (`db/indexing-and-migration.md §4`).
- **운영자 백필 검토 체크리스트 PLAN 명시 의무**: 백필 후 운영자가 반자동 회로 펌프 식별 시 `UPDATE pump_m SET oprtng_type_cd = 'SEMI_AUTO_CAPABLE' WHERE pump_id IN (...)` 실행. 누락 시 도메인 룰 평가 오작동 위험.

### 적용할 패턴

- `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴` §자식 전용 컬럼 도입 시점 — `pump_m` 자식 전용 컬럼.
- `entity-patterns.md §여부(Y/N) 필드 패턴` 의 `@Enumerated(EnumType.STRING)` + 전용 enum 조합 패턴 재사용 (`YnType`·`UserRole`·`AiDrvnMode` 선례).
- `api-patterns.md §DTO @Schema(implementation) 명시 패턴` — 응답 DTO 도입 시 `implementation = PumpOprtngType.class` 명시.
- `db/indexing-and-migration.md §2` 무중단 3단계 + `§3.4 DOM_YN` 인덱스 정책 유추 (단독 인덱스 미적용).

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 시설 내 `AUTO_CAPABLE` + `SEMI_AUTO_CAPABLE` 펌프 혼재가 정수지 실무상 빈번 | 가정 | `wtp-domain-expert` 권고 (안건 5). 향후 PLAN 단계 또는 별도 사이클에서 `ai_mode_cd` × `oprtng_type_cd` 인터록 제약 검증 필요 — 본 사이클 범위 외 |
| 기존 V8_2 적용 운영 환경의 펌프 마스터 행 수 (멀티테넌트 `gs`/`gm2`/`hy` 등별) | 미해결 | PLAN 단계 운영자 확인. 백필 SQL 은 Flyway 마이그레이션이 아닌 운영자 수동 실행 필요 여부 PLAN 명시 |
| valve/flwmtr 등 instrument 타 자식에도 자동/반자동 구분 필요성 | 미해결 | 현 사이클은 펌프 한정. 자식 종류 3건 이상에 동일 패턴 도입 시 instrument 부모 이관 별도 ANALYZE — `wtp-dba-reviewer` 권고 (안건 2) |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `PumpOprtngType` enum 매핑 + `Pump.create()` 정적 팩토리 호환 | 신규 단위 테스트 GREEN — `Pump.create(...)` 신규 인자 + DB INSERT 후 SELECT 매핑 검증 2건 |
| V8_3 마이그레이션 3단계 무중단 적용 (백필 포함) | 로컬 PostgreSQL 마이그레이션 SQL 순차 실행 + 백필 검증 SQL `SELECT COUNT(*) FROM pump_m WHERE oprtng_type_cd IS NULL` 결과 0 확인 |
| 응답 DTO `@Schema(implementation = PumpOprtngType.class)` Swagger 노출 (송수펌프제어분석 후속 PLAN 시점 검증) | `springdoc.api-docs.enabled=true` 환경에서 OpenAPI JSON dump → `oprtngTypeCd` enum 허용값 `AUTO_CAPABLE`·`SEMI_AUTO_CAPABLE` 노출 확인 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 펌프 능력 마스터 컬럼 추가 — 알람 임계값·전이 조건·복귀 조건 변경 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 해당 (지연) | `ai_mode_cd=AI_AUTO` + `SEMI_AUTO_CAPABLE` 펌프 시 AI 제어 제외 룰 추가 필요 — 본 사이클은 마스터 컬럼만, 인터록 강제 로직은 별도 사이클 분리 (안건 5 결론) |
| AI 운전 모드 (`ot-integration.md §5`) | 해당 (지연) | 시스템 상태 평가 시 펌프 능력 차원 추가 — 별도 사이클 분리. 본 사이클 마스터 컬럼이 후속 룰의 입력 데이터 제공 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 마스터 컬럼 변경은 `BaseEntity` audit 4컬럼 (AuditingEntityListener) 자동 주입. `pump_ctrl_h`·`ai_drvn_mod_h` 영향 없음 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `type` 표준 단어 신규 등록 (기본 데이터 도메인 `DOM_CODE_20`, 비고: "분류·종류. 의미 경계: type=Discriminator/자식 종류 (`facility_type_cd`·`equip_type_cd` 선례), se=내부 분류 (`tag_se_cd` 선례). 안건 1 결론")
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `oprtng_type_cd` 표준 용어 신규 등록 (조합: `oprtng` + `type` + `cd`, `DOM_CODE_20`, 사용 테이블: `pump_m`, 비고: "펌프 조작유형 (AUTO_CAPABLE/SEMI_AUTO_CAPABLE 상호 배타). `PumpOprtngType` enum 매핑. 펌프의 물리적 설계값. 펌프조작유형 ANALYZE1, 2026-05-12")
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — 동의어 표 `tag_se_cd` 행 비고 갱신 ("`type` 미등록 단어" 표현 제거 후 "`tag_se_cd` 는 측정 항목 내부 분류이므로 `se` 사용. Discriminator 성격은 `type` 사용 — `equip_type_cd`·`facility_type_cd` 선례 참조" 명기)

## 산출물

- [계획안](../../../plan/20260512/펌프조작유형/PLAN1.md) (PLAN approved 후 작성)
