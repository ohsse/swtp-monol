---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — 도메인 분석

## 작업 배경

사용자가 송수펌프제어분석 화면 (`backend/image/송수펌프제어분석.png`) 의 **2번 섹션 — AI 운전모드 (AI / AI추천 / AI분석) 표출 + 변경 이력 관리** 기능을 작업한다. 이미지 분석:

- 화면 상단 토글: `AI` · `AI추천` · `AI분석` 3개 (사용자 의도 단일축)
- 본 운전모드는 **공정/제어대상 단위 시스템 전역 설정** — 모든 사용자가 동일 화면에서 동일 모드 상태 공유
- 변경 시 이력 행 추가 의무 — 사용자 명시 컬럼: 공정/제어대상ID·AI운전모드유형·설정시작시간·설정종료시간·rgstr/updt 4

### 사이클 컨텍스트

본 작업은 **pump+AI 백지화 사이클 1 (2026-05-12) 이후 사이클 2 재설계** 의 일부다. 사이클 1 백지화로 다음 폐기됨:
- 비즈니스 도메인 약어 `ai` (도입 예정 상태 보존, 패키지 미존재)
- 표준 용어 `ai_drvn_mod`·`ai_mode_cd`·`expire_dtm`·`last_rcv_dtm` 폐기
- 테이블 `ai_drvn_mod_p`·`ai_drvn_mod_h` 폐기
- `ot-integration.md §5` AI 운전 모드 이중 체계 + 강제 전환 정책 절 보류 마커 표기

사용자 메모리 정책 ("사이클 간 자산 자동 원용 금지") 에 따라 백지화 이전 어휘·구조의 자동 재사용을 금지하고 회의에서 처음부터 재평가했다.

### 사용자 사전 결정 사항 (`/dev` AskUserQuestion 답변)

| 항목 | 사용자 답변 | 영향 |
|------|----------|------|
| 작업 범위 | 표출 + 이력 + 변경 API **만** | 강제 전환 정책 (SCADA 5분 초과·OUTBOUND_FAIL·SYSTEM_INIT 등 `ot-integration.md §5` 보류 영역) 은 별도 사이클로 분리 |
| 적용 단위 | **업무 화면 카테고리 단위 마스터** | facility_m 과 별개의 신규 마스터. 송수펌프제어·정수공정 등 화면/업무 카테고리 단위 |
| 마스터 vs 이력 SSOT | ANALYZE 5인 회의 정식 평가 위임 | 사용자 명시 평가 요청 — 안건 2 ⭐ |

### 외부 산출물
- `backend/image/송수펌프제어분석.png` — 화면 모형 (Read 도구 시각 로드 완료)
- 사용자 요청문 (`/dev` 명령어 인자) — 마스터·이력 컬럼 직접 명시

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: "공정/제어대상" 신규 비즈니스 도메인 약어 결정 + `ctrl` 재평가

- 호출 에이전트: `wtp-glossary-manager` (Round 1·Round 2)
- 키워드: 비즈니스 도메인 약어 / 폐기·통합 / 백지화 사이클
- Round 1 답변 요약:
  - **wtp-glossary-manager (Round 1)**: 후보 4건 (`ctrlscope`·`proc`·`ctrl`·`tgt`) 모두 충돌 검토. `ctrl` 마스터 도메인 섹션의 "사이클 2 재평가 대상" 표기 → 본 사이클에서 폐기 vs 의미 재정의 결정 필요. **블로커 명시 — 사용자 또는 회의 결론 필요**
- Round 2 답변 요약:
  - **wtp-glossary-manager (Round 2)**: 3경로 정식 평가:
    - 경로 1 (`ctrlscope`): 8자 합성어로 컨벤션 초과 + `scope` 가 정수장 업무 어휘 아님 → 거부
    - 경로 2 (`ctrl` 의미 재정의): 어근 `control` 충돌 + 향후 제어 명령/로그 도메인 재도입 시 재충돌 + 백지화 철학 위반 → 거부
    - **경로 3 (`ctrl` 폐기 + `proc` 신규)**: `proc` 4자, 정수장 업무 어휘 "공정" 직관 대응, SQL 예약어 충돌 없음 (`PROCEDURE` 와 철자 다름), 사용자 메모리 정합 → **채택**
- **결론**: `ctrl` 완전 폐기 (사용처 0 확정) + `proc` (process) 비즈니스 도메인 약어 신규 등록 (도입 예정 섹션, `com.mo.swtp.proc` 패키지 신설 시 마스터 도메인 승격)

### 안건 2: AI 운전모드 마스터+이력 vs 이력 단일 SSOT 정합성 평가 ⭐ (사용자 명시 평가)

- 호출 에이전트: `wtp-dba-reviewer` · `wtp-domain-expert` · `wtp-backend-engineer` (Round 1 병렬)
- 키워드: DB 패턴 / 동시성 / 도메인 안전성
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 옵션 A (마스터+이력) 채택. 4축 비교 — 도메인 안전성 (UNIQUE 1행 강제 명확) · 동시성 (UPSERT 단일 문장 원자적) · 조회 성능 (PK 직접 SELECT O(1)) · 운영 단순성 (책임 분리 명확). 옵션 B 의 부분 UNIQUE 인덱스는 INSERT 차단만 가능, UPDATE 순서 역전 미차단. 변경 빈도 매우 낮음 (시간~일 단위) → 파티셔닝 불필요
  - **wtp-domain-expert**: 옵션 A 우위 일치. `ot-integration.md §5` "사용자 의도 보존 원칙" 정렬 — 마스터에 현재 상태 단일 SSOT 보유가 도메인 의미상 명확. 옵션 B 는 "이력 최신 행" 묵시적 도출 → 도메인 의미 약화
  - **wtp-backend-engineer**: 마스터 UPSERT + 직전 행 end_dtm UPDATE + 신규 INSERT 3 작업 단일 트랜잭션 가능 (정합)
- **결론**: **옵션 A (마스터+이력 분리) 채택**. 4축 (도메인 안전성·동시성·조회 성능·운영 단순성) 모두 우위. 사용자 명시 평가 요청에 대한 회의 결론: 본 사례는 "현재 상태 단일 SSOT 명확성 + 동시성 안전" 이 핵심 요구이므로 마스터+이력 분리가 정합. 이력 단일 SSOT 는 순수 시계열 (`rawdata_1m_h`) 패턴이며 상태 추적 (state with effective dates) 의미가 약함

### 안건 3: 이력 테이블 `end_dtm` 처리 정책

- 호출 에이전트: `wtp-dba-reviewer` (Round 1·Round 2) · `wtp-backend-engineer` (Round 1)
- 키워드: DB DDL / immutable 이력 / BaseEntity 정책
- Round 1 답변 요약:
  - **wtp-dba-reviewer (Round 1)**: 이력 `_h` immutable INSERT-only 정책 (`indexing-and-migration.md §4.3`) 적용 시 `end_dtm` 갱신은 immutable 원칙과 충돌. "이력은 `start_dtm` 만 보유, 마스터에서 최신 `start_dtm` 관리" 방향 제시
  - **wtp-backend-engineer (Round 1)**: `end_dtm` 갱신 시나리오 = UPDATE 발생 → BaseEntity 4 상속 필수
- Round 2 답변 요약:
  - **wtp-dba-reviewer (Round 2)**: 3경로 평가 결과 **경로 A (`_h` + BaseEntity 4 상속 + `end_dtm` UPDATE 허용) 채택**. 근거 — `rawdata_1m_h.corr_val` 갱신 선례 (마스터도메인설계 ANALYZE1 Round 3, 2026-05-03) 가 이미 "`_h` BaseEntity 4 상속 + UPDATE 허용" 패턴을 확립. `_h` suffix 의미를 "INSERT-only 기본, 업무 요건 갱신 컬럼 존재 시 BaseEntity 4 상속 허용" 으로 재해석. 부분 UNIQUE 인덱스 `(proc_id) WHERE end_dtm IS NULL` 동시성 안전망 권고 (UPDATE-then-INSERT 동일 트랜잭션 의무)
- **결론**: 경로 A 채택. 이력 테이블 BaseEntity 4 상속 + `end_dtm` UPDATE 허용. 부분 UNIQUE 인덱스 적용 (PLAN 명시). `rawdata_1m_h.corr_val` 선례 인용으로 `indexing-and-migration.md §4.3` 의 "변경 추적 컬럼 존재 시 BaseEntity 4 상속 허용" 조건 명문화 권고 (룰 갱신 지시서)

### 안건 4: AI 운전모드 단일축 vs 이중 체계 + 강제 전환 정책 경계

- 호출 에이전트: `wtp-domain-expert` (Round 1)
- 키워드: AI 운전 모드 / `ot-integration.md §5` 보류 영역
- Round 1 답변 요약:
  - **wtp-domain-expert**: **옵션 A (사용자 의도 단일축) 채택**. 근거 3종: (1) `ot-integration.md §5` 보류 마커가 강제 전환 정책 전체 무효화 — `ai_mode_cd` 변경 주체 (스케줄러·장애 대응) 없음. 도입 시 데드 컬럼. `coding-discipline.md §2` "요청되지 않은 유연성 금지" 위반. (2) 향후 강제 전환 사이클에서 `ai_mode_cd` 컬럼 추가 가능 (`indexing-and-migration.md §2` 3단계 무중단 마이그레이션). 백지화 사이클 정신 = 처음부터 결정. (3) 본 작업 제어 명령 발행 없음 → `ot-integration.md §5 ⚠️ 절대 금지` 직접 영향 없음
- **결론**: 사용자 의도 단일축만 도입. 컬럼 `ai_drvn_mod_cd` (DOM_CODE_20, `AI`·`AI_RECOMD`·`AI_ANLS` 3종 enum). 시스템 상태 (`ai_mode_cd`) · `last_rcv_dtm` · `expire_dtm` · 강제 전환 사유 4종 (`SCADA_TIMEOUT`·`MANUAL_EXPIRE`·`OUTBOUND_FAIL`·`SYSTEM_INIT`) 본 사이클 제외 — 별도 사이클 분리

### 안건 5: AI 운전모드 enum + 폐기 컬럼 재등록 vs 신규 어휘

- 호출 에이전트: `wtp-glossary-manager` (Round 1)
- 키워드: 폐기 컬럼 재등록 / 동의어 금지
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 옵션 2 (신규 어휘 `ai_drvn_mod_cd` 등록, `_cd` suffix 추가) 채택. 근거 — 폐기 이력 2026-05-12 의 "대체: 사이클 2 결정" 항목은 동일 컬럼명 자동 복구 아니라 재설계 절차 요구. `_cd` suffix 는 코드값 명시 표준 패턴 (`tag_se_cd`·`oprtng_type_cd`·`drive_type_cd` 선례)
- **결론**: `ai_drvn_mod_cd` 신규 등록 (DOM_CODE_20). `ai_drvn_mod` 재등록 거부

### 안건 6: 신규 표준 단어 등록 검토 (`start`·`end`)

- 호출 에이전트: `wtp-glossary-manager` (Round 1)
- 키워드: 표준 단어 / 유사 충돌
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `start` 신규 채택 (5자, `drive`·`branch` 풀네임 선례 정합, 충돌 없음). `end` 조건부 채택 (`expire` 동의어 회피 — 의미 경계: `end`=명시적 종료, `expire`=시스템 판정 만료. SQL 예약어 `END` 주의 비고 의무)
- **결론**: 표준 단어 `start`·`end` 신규 등록 (비고: `end` 는 SQL 예약어 주의)

### 안건 7: `transition_reason` 컬럼 처리

- 호출 에이전트: `wtp-domain-expert` (Round 1)
- 키워드: 이력 기록 의무 / 발생 불가능 시나리오
- Round 1 답변 요약:
  - **wtp-domain-expert**: 옵션 A (`USER_SELECT` 단일 사유만) 채택. `coding-discipline.md §2` "발생 불가능한 시나리오에 대한 예외 처리 금지" — 4종 사유 (`SCADA_TIMEOUT`·`MANUAL_EXPIRE`·`OUTBOUND_FAIL`·`SYSTEM_INIT`) 의 트리거 로직 자체가 미구현 (보류). 미구현 사유 enum 선제 선언 금지
- **결론**: 본 사이클은 `transition_reason` **컬럼 자체 도입 보류**. 모든 변경이 사용자 변경 API 단일 경로이므로 사유 컬럼 없음으로 의미 손실 없음. 향후 강제 전환 사이클에서 enum 도입 + 컬럼 추가 (3단계 무중단 마이그레이션 적용)

### 안건 8: 패키지 구조 + 계층 책임

- 호출 에이전트: `wtp-backend-engineer` (Round 1)
- 키워드: 패키지 구조 / 단순성
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 옵션 A (단일 패키지 `com.mo.swtp.proc`) 채택. `coding-discipline.md §2` 단순성 우선 — 단일 도메인 작업에 패키지 선제 분리는 과잉 추상화. 비즈니스 도메인 약어 2개 동시 등록 부담 회피
- **결론**: 단일 패키지 `com.mo.swtp.proc` (안건 1 결정 약어). 마스터·이력·서비스·컨트롤러 모두 동일 패키지. AI 운전 모드 도메인 향후 별도 분리 필요 시 재구조화 (현 시점 미결정)

### 안건 9: API 명세 (Controller + DTO + ErrorCode) + 이력 응답 DTO 충돌 해소

- 호출 에이전트: `wtp-backend-engineer` (Round 1)
- 키워드: API 패턴 / DTO 상속
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 엔드포인트 4개 (조회·변경·이력조회·마스터목록) + ErrorCode 3종 (`PROC_NOT_FOUND` 404 · `INVALID_AI_DRVN_MOD` 400 · `AI_MODE_CONCURRENT_UPDATE` 409) 정합. **중간 권고 1건** — 이력 응답 DTO `BaseAuditResponseDto` 상속 충돌: `api-patterns.md §적용 범위 표` "시계열(`_h`) 응답 DTO — 적용 외" 와 충돌. 해소책: 이력 응답 DTO 는 `BaseAuditResponseDto` 상속 금지, 4컬럼 (`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) 을 DTO 에 직접 선언 (immutable 이력 예외의 2컬럼 선례 → 본 사례 4컬럼 확장)
- **결론**: API 명세 초안 PLAN 단계 확정. 이력 응답 DTO 는 `BaseAuditResponseDto` 미상속 + 4컬럼 직접 선언 패턴. DTO `@Schema(implementation = AiDrvnMode.class)` 명시 의무 (`api-patterns.md §DTO @Schema(implementation) 명시 패턴`)

---

## 표준 사전 카탈로그

### 신규 표준 단어

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `start` | 시작 | 신규 | `standard-words.md` 미등록. `begin` 동의어 없음. 풀네임 5자 — `drive`·`branch` 풀네임 선례 정합. `start_dtm` 컬럼 조합 재료. 의미 경계: `rgstr`(등록 동사) 와 다름 — `start`=기간 시작 시점 |
| `end` | 종료 | 신규 | `standard-words.md` 미등록. `expire`(만료, 일반어 사용) 와 의미 경계 분리 — `end`=명시적 사용자 설정 종료, `expire`=시스템 판정 만료. SQL 예약어 `END` 주의 비고 (PostgreSQL 컬럼명 소문자 사용 시 문제 없으나 DDL 작성 시 주의). `end_dtm` 컬럼 조합 재료 |

### 신규 표준 데이터 도메인

없음 — 기존 `DOM_ID_36`·`DOM_NAME_100`·`DOM_CODE_20`·`DOM_YN`·`DOM_DTM`·`DOM_SEQ_BIGINT` 모두 재사용 (DBA 2차 승인 — `DOM_AI_DRVN_MOD_20` 등 신규 등록 거부, 동일 타입·길이 중복 등록 금지 룰 적용)

### 신규 표준 용어

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `proc_id` | `proc`(비즈니스 도메인, 신규) + `id` | `DOM_ID_36` | 신규 | UUID 자동 생성 PK — `facility_id`·`instrument_id`·`menu_id` 선례 동일 패턴. `Persistable` 미구현 |
| `proc_nm` | `proc` + `nm` | `DOM_NAME_100` | 신규 | UNIQUE 인덱스 (시스템 전체) — `facility_nm`·`menu_nm` 선례 |
| `ai_drvn_mod_cd` | `ai`(비즈니스 도메인, 도입 예정) + `drvn` + `mod` + `cd` | `DOM_CODE_20` | 신규 | 폐기된 `ai_drvn_mod`(DOM_CODE_20) 와 데이터 도메인 동일하나 `_cd` suffix 추가로 코드값 명시. `AiDrvnMode` enum 매핑 — `AI`·`AI_RECOMD`·`AI_ANLS` 3종. `@Enumerated(EnumType.STRING)` |
| `start_dtm` | `start`(신규) + `dtm` | `DOM_DTM` | 신규 | 모드 적용 시작 시각. NOT NULL (활성 시점 항상 존재) |
| `end_dtm` | `end`(신규) + `dtm` | `DOM_DTM` | 신규 | 모드 종료 시각. **NULL 허용** (현재 활성 행 표현). 부분 UNIQUE 인덱스 `WHERE end_dtm IS NULL` 적용 |
| `ai_drvn_mod_id` | `ai` + `drvn` + `mod` + `id` | `DOM_SEQ_BIGINT` | 신규 | 이력 테이블 PK — `GenerationType.SEQUENCE` allocationSize=100. 시계열 BIGINT PK 정책 (`standard-data-domains.md` `DOM_SEQ_BIGINT`) |
| `use_yn` | `use` + `yn` | `DOM_YN` | 기존 재사용 | `proc_m` 추가 — 비활성 운영 시나리오 (공정 카테고리 비활성화) |

---

## 신규 엔티티/DB 컬럼

### 엔티티 1: `proc_m` (공정/제어대상 카테고리 마스터)

- 비즈니스 도메인: `proc` (신규 등록 예정)
- 패키지: `com.mo.swtp.proc.domain.Process`
- suffix: `_m` (마스터)
- PK: `proc_id` (UUID 자동 생성, `Persistable` 미구현)
- 컬럼:
  - `proc_id` (DOM_ID_36, PK, UUID 자동)
  - `proc_nm` (DOM_NAME_100, UNIQUE)
  - `use_yn` (DOM_YN, NOT NULL, YnType.Y 기본 — 정적 팩토리 명시)
  - `disp_ord` (INTEGER, NULL 허용 — 화면 표시 순서, `facility_m` 선례)
  - BaseEntity 4 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`)
- 인덱스: PK + `(proc_nm) UNIQUE`

### 엔티티 2: `ai_drvn_mod_p` (AI 운전모드 현재 활성 행)

- 비즈니스 도메인: `proc` (단일 패키지 — 안건 8). 테이블명 prefix `ai` (사용자 결정 2026-05-20 — 짧은 이름 채택)
- 패키지: `com.mo.swtp.proc.domain.AiDrvnMode` (또는 PLAN 결정 클래스명)
- suffix: `_p` (명세 — 운전 규칙/상태값 보관, `naming.md` 정합)
- PK: `proc_id` (FK → `proc_m.proc_id` 자체가 PK = 1:1 관계, 공정/제어대상별 1행 강제)
- 컬럼:
  - `proc_id` (DOM_ID_36, PK + FK → `proc_m`)
  - `ai_drvn_mod_cd` (DOM_CODE_20, NOT NULL, `AiDrvnMode` enum)
  - `start_dtm` (DOM_DTM, NOT NULL — 현재 활성 모드 시작 시각)
  - BaseEntity 4
- 인덱스: PK
- 동작: UPSERT (`ON CONFLICT (proc_id) DO UPDATE`) — 모드 변경 시 갱신

### 엔티티 3: `ai_drvn_mod_h` (AI 운전모드 변경 이력)

- 비즈니스 도메인: `proc` (단일 패키지). 테이블명 prefix `ai` (사용자 결정 2026-05-20)
- 패키지: `com.mo.swtp.proc.domain.AiDrvnModeHistory` (또는 PLAN 결정 클래스명)
- suffix: `_h` (이력)
- PK: `ai_drvn_mod_id` (DOM_SEQ_BIGINT)
- 컬럼:
  - `ai_drvn_mod_id` (DOM_SEQ_BIGINT, PK)
  - `proc_id` (DOM_ID_36, NOT NULL, **논리 참조 — 시계열 → 마스터 FK 금지** `partitioning-and-retention.md §1` 정렬)
  - `ai_drvn_mod_cd` (DOM_CODE_20, NOT NULL, `AiDrvnMode` enum)
  - `start_dtm` (DOM_DTM, NOT NULL)
  - `end_dtm` (DOM_DTM, NULL 허용 — 현재 활성 행 표현, UPDATE 허용)
  - BaseEntity 4 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) — `end_dtm` UPDATE 시 `updt_*` 자동 갱신
- 인덱스:
  - PK
  - `(proc_id, start_dtm DESC)` B-Tree 복합 — 이력 조회 + 페이지네이션
  - **`(proc_id) WHERE end_dtm IS NULL` 부분 UNIQUE 인덱스** — 동시성 안전망 (공정/제어대상별 활성 행 1건 강제)
- 보존 정책: 5년 (`partitioning-and-retention.md §2` 기존 AI 운전 모드 이력 정책 정합)
- 파티셔닝: 불필요 (변경 빈도 시간~일 단위, 연간 수천 건 이하)

### 신규 enum: `AiDrvnMode`

- 위치: `com.mo.swtp.common.enumtype.AiDrvnMode` (또는 `com.mo.swtp.proc.enumtype.AiDrvnMode` — PLAN 결정)
- 값: `AI` · `AI_RECOMD` · `AI_ANLS`
- `@Enumerated(EnumType.STRING)` 매핑

### 신규 ErrorCode: `ProcErrorCode`

- 위치: `com.mo.swtp.proc.exception.ProcErrorCode`
- 값:
  - `PROC_NOT_FOUND` (404)
  - `INVALID_AI_DRVN_MOD` (400)
  - `AI_MODE_CONCURRENT_UPDATE` (409) — 부분 UNIQUE 인덱스 위반 시
- 필드: `httpStatus` 단일 (`exception-patterns.md §2 ErrorCode 구현 enum 필드 규약` 준수)

---

## 기존 사전·패턴과의 충돌

### 충돌 1: `ctrl` 비즈니스 도메인 약어 의미 충돌

- **충돌**: `ctrl` 마스터 도메인 (제어 명령/로그, 2026-04-25 등록) — 본 작업의 "공정/제어대상 마스터" 와 의미 충돌 가능성
- **해소**: 안건 1 결론 — `ctrl` 폐기 (사용처 0 확정) + `proc` 신규 도입. `domain-abbreviations.md` 마스터 도메인 섹션에서 폐기 이력 섹션으로 이동

### 충돌 2: `ai_drvn_mod` 폐기 컬럼 재등록 vs 신규 어휘

- **충돌**: `standard-terms.md` 폐기 이력 표 (2026-05-12) 의 `ai_drvn_mod` 컬럼 동일 의미 재등록 가능성
- **해소**: 안건 5 결론 — `_cd` suffix 추가 (`ai_drvn_mod_cd`) 로 코드값 명시 + 폐기 컬럼과 의미 미세 차이 표시. 동의어 금지 룰 회피

### 충돌 3: 이력 `_h` immutable 정책 vs `end_dtm` UPDATE 요구

- **충돌**: `indexing-and-migration.md §4.3` 이력 immutable INSERT-only 예외 조건과 사용자 명시 `end_dtm` 갱신 요구 충돌
- **해소**: 안건 3 결론 — `rawdata_1m_h.corr_val` 선례 (마스터도메인설계 ANALYZE1 Round 3, 2026-05-03) 인용. `_h` 의미 재해석 — "INSERT-only 기본, 업무 요건 갱신 컬럼 존재 시 BaseEntity 4 상속 허용". 룰 갱신 지시서로 본 조항 명문화 권고

### 충돌 4: 이력 응답 DTO `BaseAuditResponseDto` 상속 vs 시계열 적용 외 룰

- **충돌**: `api-patterns.md §BaseAuditResponseDto 패턴 §적용 범위 표` "시계열(`_h`) 응답 DTO — 적용 외" 와 이력 응답 DTO 의 `BaseAuditResponseDto` 상속 가능성
- **해소**: 안건 9 결론 — 이력 응답 DTO 는 `BaseAuditResponseDto` 미상속 + 4컬럼 (`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) DTO 직접 선언. immutable 이력 예외 패턴 (`rgstrDtm`·`rgstrId` 2컬럼) 의 본 사례 4컬럼 확장

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- 비즈니스 도메인 `proc` 신규 도입 + 단일 패키지 `com.mo.swtp.proc`
- 엔티티 3종: `Process` (마스터) + `ProcAiMode` (현재 활성) + `ProcAiModeHistory` (이력)
- enum `AiDrvnMode` 3종 (사용자 의도 단일축)
- ErrorCode `ProcErrorCode` 3종

### DB 설계 변경 초안
- 신규 테이블 3종: `proc_m` · `{proc_}ai_drvn_mod_p` · `{proc_}ai_drvn_mod_h` (prefix 미결, 가정 섹션 참조)
- 부분 UNIQUE 인덱스 `(proc_id) WHERE end_dtm IS NULL` 이력 동시성 안전망
- 시계열 → 마스터 FK 금지 (`partitioning-and-retention.md §1`) — 이력 → 마스터 논리 참조
- 보존 정책: 마스터 영구·이력 5년 (`partitioning-and-retention.md §2` 갱신)

### 적용할 패턴
- 마스터: `entity-patterns.md §기본 엔티티 패턴` (UUID PK, BaseEntity 상속, `@UniqueConstraint`)
- 마스터 응답 DTO: `BaseAuditResponseDto` 옵트인
- 이력: `entity-patterns.md` 의 BaseEntity 4 상속 + UPDATE 허용 (`rawdata_1m_h.corr_val` 선례)
- 이력 응답 DTO: `BaseAuditResponseDto` 미상속 + 4컬럼 DTO 직접 선언
- API: `api-patterns.md` (Service `@Transactional` 분리, Repository 커스텀, DTO `@Schema(implementation)` 명시)
- ErrorCode: `exception-patterns.md` (httpStatus 단일 필드)
- 트랜잭션: 변경 API 단일 `@Transactional` — UPSERT(마스터) + UPDATE(직전 이력 end_dtm) + INSERT(신규 이력 행) 3 작업 원자성

### API 명세 초안 (4 엔드포인트)
| HTTP | 경로 | 요약 | ErrorCode |
|------|------|------|----------|
| GET | `/api/proc` | 공정/제어대상 마스터 목록 조회 | — |
| GET | `/api/proc/{procId}/ai-mode` | 현재 활성 AI 운전모드 + 시작시간 조회 | `PROC_NOT_FOUND` |
| PUT | `/api/proc/{procId}/ai-mode` | AI 운전모드 변경 (사용자 의도) — 마스터 UPSERT + 이력 INSERT + 직전 행 end_dtm 갱신 | `PROC_NOT_FOUND` · `INVALID_AI_DRVN_MOD` · `AI_MODE_CONCURRENT_UPDATE` |
| GET | `/api/proc/{procId}/ai-mode/history` | AI 운전모드 변경 이력 조회 (페이지네이션) | `PROC_NOT_FOUND` |

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| AI 운전모드 마스터·이력 테이블 prefix — `ai_drvn_mod_p`/`ai_drvn_mod_h` (`ai` 비즈니스 도메인 약어 활용) vs `proc_ai_drvn_mod_p`/`proc_ai_drvn_mod_h` (단일 패키지 `proc` prefix 통일) | **결정** | 사용자 답변 (ANALYZE 검토 시점 2026-05-20): **`ai_drvn_mod_p` / `ai_drvn_mod_h` (짧은 이름)** 채택. 글로서리 권고 정합 — 단일 패키지 `com.mo.swtp.proc` 내 위치하되 테이블명 prefix 는 `ai` 비즈니스 도메인 약어 활용. `ai` 약어는 도입 예정 상태 유지 (마스터 도메인 승격 미실시, 패키지 미존재). 컬럼·테이블 prefix 로만 한정 사용 비고 `domain-abbreviations.md` 의 `ai` 행에 명기 |
| AI 운전 모드 시스템 상태 (`ai_mode_cd`) · `last_rcv_dtm` · `expire_dtm` · 강제 전환 사유 4종 · `transition_reason` 컬럼 도입 보류 | 가정 | 본 사이클 명시 제외 (`ot-integration.md §5` 보류 영역). 별도 사이클로 분리. 향후 도입 시 3단계 무중단 마이그레이션 (`indexing-and-migration.md §2`) 적용 |
| 이력 보존 기간 5년 (`partitioning-and-retention.md §2` 기존 정책 정합) | 가정 | 변경 빈도 매우 낮음 — 연간 수천 건 이하 추정. 파티셔닝 불필요. 단일 테이블 + B-Tree 복합 인덱스로 5년 치 조회 성능 충족 |
| 부분 UNIQUE 인덱스 `(proc_id) WHERE end_dtm IS NULL` — 단일 트랜잭션 내 UPDATE-then-INSERT 순서 의무 (애플리케이션 레벨 검증) | 가정 | PLAN 단계 명시. 트랜잭션 외부에서 동시 호출 시 `AI_MODE_CONCURRENT_UPDATE` (409) 응답 |
| 마스터 `proc_m` 의 초기 시드 데이터 (예: "송수펌프제어" 카테고리) — DDL 마이그레이션에서 INSERT vs 운영 환경 별도 등록 | 미해결 | PLAN 단계 결정. 초기 운영 시 1건 (송수펌프제어) 등록 필요 — DDL `INSERT INTO proc_m ...` 또는 운영 화면에서 등록 |
| ProcAiMode 마스터의 PK = `proc_id` 단독 (1:1 관계) vs `proc_id + ai_drvn_mod_cd` 복합 | 가정 | 결정: 단독 PK (1:1 관계, 공정/제어대상별 활성 모드 1건 강제). `@MapsId` 또는 `@PrimaryKeyJoinColumn` 적용 |
| `AiDrvnMode` enum 패키지 위치 — `common.enumtype` vs `proc.enumtype` | 미해결 | PLAN 단계 결정. `YnType`·`UserRole` 은 `common.enumtype` — 같은 정책 적용 시 `common.enumtype`. 다만 `AiDrvnMode` 가 `proc` 도메인 전용이면 `proc.enumtype` |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `Process` 마스터 CRUD 단위 테스트 — 생성·조회·변경 (`use_yn`) | `./gradlew :api:test --tests ProcessServiceTest` PASS |
| `ProcAiModeService` 변경 트랜잭션 — UPSERT + 이력 INSERT + 직전 행 end_dtm UPDATE 3 작업 원자성 | 신규 통합 테스트 PASS — 트랜잭션 롤백 시 3 작업 모두 미반영 검증 |
| 부분 UNIQUE 인덱스 동시성 — 동시 변경 시 `AI_MODE_CONCURRENT_UPDATE` (409) | 신규 동시성 시나리오 테스트 — 2개 스레드 동시 호출 → 1건 409 응답 검증 |
| Swagger 명세 노출 — 4 엔드포인트 + DTO `@Schema(implementation = AiDrvnMode.class)` | `./gradlew :api:bootRun -Pprofile=local` 후 `http://localhost:8080/swagger-ui.html` 검증 |
| ErrorCode 3종 + `httpStatus` 단일 필드 | `check-errorcode-contract.sh` 훅 통과 — `ProcErrorCode` 저장 시 차단 없음 |
| 이력 응답 DTO `BaseAuditResponseDto` 미상속 + 4컬럼 직접 선언 | grep 검증 — `class ProcAiModeHistoryDto.*extends BaseAuditResponseDto` 매칭 없음 + `rgstrDtm` 필드 직접 선언 매칭 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 작업은 임계값·전이 조건·복귀 조건에 무접촉. 표출·이력·변경 API 만 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 발행 없음. 인터록 검사 대상 없음. 향후 강제 전환 사이클에서 재검토 |
| AI 운전 모드 (`ot-integration.md §5`) | **해당** | 사용자 의도 (`ai_drvn_mod_cd`) 변경 API + 마스터 현재 상태 표출 + 이력 기록. 단일축 도입 (시스템 상태 `ai_mode_cd` 미도입 — 안건 4 결론). `ot-integration.md §5` 보류 마커 영역과 분리 |
| 이력 기록 의무 (`ot-integration.md §5`) | **해당** | AI 운전모드 변경 시 이력 행 추가 의무 (`ai_drvn_mod_h` BaseEntity 4 상속). `transition_reason` 컬럼 본 사이클 보류 — `USER_SELECT` 단일 사유로 컬럼 자체 불필요 (안건 7 결론) |

`coding-discipline.md §2.5` 면책 영역 적용 여부: AI 운전 모드 변경 API 단일 흐름 (사용자 API 수신 → 검증 → 마스터 UPSERT → 직전 이력 행 end_dtm UPDATE → 신규 이력 행 INSERT → 응답) 은 `ot-integration.md §5` 이력 기록 의무 단일 흐름이므로 **§2.5 면책 적용 가능**. 단, 강제 전환 로직 미포함으로 흐름 단순 — IMPL 단계에서 50줄 초과 여부 판정 후 필요 시 `// §2.5 면책 (ot-integration.md §5 AI 모드 이력 기록 의무 단일 흐름)` 주석 명기.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — `ctrl` 마스터 도메인 섹션에서 폐기 이력 섹션으로 이동 (사유: 사이클 2 재평가 결과 사용처 0 확정, `proc` 신규 채택으로 의미 재정의 불가) + 폐기일 2026-05-20 기록
- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — `proc` (process, 공정/제어대상) 도입 예정 섹션 신규 등록 (PLAN approved 후 `com.mo.swtp.proc` 패키지 신설 시 마스터 도메인 섹션으로 승격)
- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — `ai` 도입 예정 항목 비고에 "송수펌프제어분석-2번섹션 ANALYZE1 (2026-05-20) — 본 사이클은 컬럼·테이블 prefix 로만 한정 사용 (`ai_drvn_mod_cd`·`ai_drvn_mod_p`·`ai_drvn_mod_h`). 패키지는 `com.mo.swtp.proc` 단일 통합. 마스터 도메인 승격 미실시" 추가
- [x] `swtp/.claude/rules/dict/standard-words.md` — `start` (시작, 5자 풀네임) 신규 등록 — 컬럼 조합 재료 `start_dtm`
- [x] `swtp/.claude/rules/dict/standard-words.md` — `end` (종료, 3자 풀네임, SQL 예약어 `END` 주의 비고 포함) 신규 등록 — 컬럼 조합 재료 `end_dtm`
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `proc_id` 신규 등록 (DOM_ID_36, UUID 자동 생성 PK)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `proc_nm` 신규 등록 (DOM_NAME_100, 시스템 전체 UNIQUE)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `ai_drvn_mod_cd` 신규 등록 (DOM_CODE_20, `AiDrvnMode` enum 매핑)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `start_dtm` 신규 등록 (DOM_DTM, NOT NULL 정책)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `end_dtm` 신규 등록 (DOM_DTM, NULL 허용 — 현재 활성 행 표현)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `ai_drvn_mod_id` 신규 등록 (DOM_SEQ_BIGINT, 이력 PK)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `use_yn` 사용 테이블 목록에 `proc_m` 추가
- [x] `swtp/backend/.claude/rules/db/partitioning-and-retention.md` — §2 보존 기간 표 신규 행 추가 (`ai_drvn_mod_h` 5년 보존 — 백지화된 행 갱신 또는 신규 행)
- [x] `swtp/backend/.claude/rules/db/indexing-and-migration.md` — §4.3 immutable 이력 테이블 예외 절에 "변경 추적 컬럼 존재 시 BaseEntity 4 상속 허용 — `rawdata_1m_h.corr_val` 선례 + `ai_drvn_mod_h.end_dtm` 본 사례" 조건 명문화

---

## 산출물

- [계획안](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN1.md) (ANALYZE approved + 룰 갱신 지시서 체크박스 완료 후 자동 생성)
