---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# spec_index_coverage_meta — INDEX.md `covers_*` 메타 도입

## 작업 배경

- **요청 요약**: backend↔frontend 통신 스펙 변경 시 영향 SPEC 탐색 비효율 해소. 직전 작업 `swagger_schema_migration` (commit `464d52f`) 에서 13개 DTO 변경 → 4개 영향 슬러그 식별을 위해 frontend 전체 SPEC 본문 grep 필요. 슬러그가 누적될수록 O(전체 SPEC 수) → O(슬러그 수) 단순화 목표.
- **트레이드오프 결정**: 사용자가 옵션 1 (INDEX.md 메타 확장) + 자동 채우기 (stale 완화) 방안 채택. 옵션 2 (SPEC frontmatter 메타) 와 옵션 3 (자동화 스크립트) 는 미선택.
- **외부 산출물**: 없음 (룰/커맨드/메타 영역 변경 — 도메인 다이어그램·요구사항 명세서 없음).

## 회의록 (5인 회의 토픽 주도)

### 안건 1: INDEX.md `covers_*` 메타 도입의 어휘 사전 정합성

- **호출 에이전트**: `wtp-glossary-manager` (Round 1 단독 — 이견 없음)
- **검토 사항**: 메타 위치 (frontmatter vs 본문) / 키 명명 / 표준 단어 등록 의무 / 분산 SSOT 정합
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**:
    - 메타 위치 = **(A) frontmatter 단독**. 근거: ROOT `coding-discipline.md §2 단순성 우선` (형식 2개 동시 관리 부담 2배), 기존 SPEC 템플릿이 이미 `status`·`spec_version`·`slug` frontmatter 사용 선례 (`templates.md` 257~272행), 자동 grep 목적은 frontmatter 단독으로 충족. INDEX 본문은 포인터 문서이므로 표 추가 시 섹션 목적 분산.
    - 키 이름 = **`covers_dto` / `covers_controller` / `covers_errorcode`**. `dtos`/`controllers` (단순 복수) 는 의미 불명, `provides_dto`/`exposes_dto` 는 API 계약(공개 여부) 혼동. swtp 4-5자 약어 컨벤션은 DB 컬럼명 한정.
    - 표준 단어 등록 = **면제**. `cover`/`covers` 는 (a) DB 컬럼명 재료 아님 (`standard-words.md §사용 규칙`), (b) 비즈니스 도메인 코드 아님 (`domain-abbreviations.md §사용 규칙`), (c) ROOT 어휘 적용 컨텍스트 (PostgreSQL 컬럼명 + Pydantic 필드명) 바깥. 향후 DB 컬럼 조합 가능성 점검 시 정수장 도메인 무관 확인.
    - 분산 SSOT = **모듈 룰 단독 갱신**. backend `.claude/rules/process/doc-harness/templates.md` 만 갱신. ROOT 어휘 SSOT 갱신 불필요. `coding-discipline.md §5.1` (ROOT 자산 갱신 5인 회의 의무) 본 변경 비적용.
- **Round 2**: 없음 (이견 없음)
- **결론**: frontmatter 단독, 키 이름 `covers_dto`/`covers_controller`/`covers_errorcode`, 표준 단어 등록 면제, ROOT 어휘 갱신 불필요. 4층 사전 충돌 0건.

### 안건 2: `/dev:spec` 자동 채우기 알고리즘 + Stale 위험 완화

- **호출 에이전트**: `wtp-backend-engineer` (Round 1 단독 — 이견 없음)
- **검토 사항**: 알고리즘 분리 방식 / 누락 경로 (Stale 위험) / 회귀 검증 시나리오
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**:
    - 알고리즘 = **A안 (SPEC 작성 후 INDEX 갱신 분리 단계)**. 근거: `spec.md` 가 이미 `## SPEC 문서 작성` → `## INDEX.md 갱신` 두 절 분리. B안 (단일 트랜잭션) 은 부분 실패 시 SPEC 만 쓰이고 INDEX 미갱신 → 역방향 stale 위험. A안에서 INDEX 갱신 절에 `covers_*` 작성 단계 추가가 SRP 부합.
    - SSOT = **backend Java 소스 grep**. `spec.md §코드 산출물 수집` 단계가 이미 Controller·DTO·ErrorCode 클래스명을 추출하므로 그 결과를 `covers_*` 에 그대로 반영. SPEC 본문 grep 은 자동 추출 결과에 다시 의존하는 2차 파생으로 오류 전파 위험.
    - Stale 완화책 (3 경로):
      - (a) 사용자 수동 편집: "참고용 인덱스" 명시 + 안내 문구. 훅 강제 과잉 (Claude Code 세션 외 미적용으로 실효 없음).
      - (b) `/dev:spec` 미호출: `covers_*` 결핍 = "미전파" 신호 — 의도된 부재 허용.
      - (c) Java 소스 vs SPEC 본문: Java 소스가 SSOT.
    - 회귀 검증 = `grep -l "LoginResponseDto\|TokenResponseDto" INDEX.md` 한 번으로 로그인및토큰관리 슬러그 식별 가능. 5개 슬러그 규모에서도 효과 즉시 발현.
- **Round 2**: 없음
- **결론**: A안 채택, Java 소스 grep SSOT, 안내 문구 + 의도된 부재 허용으로 stale 위험 수용. §2.1 정량 기준 위반 없음, §2.5 면책 비해당.

### 안건 3: 기존 INDEX 5건 backfill 범위

- **호출 에이전트**: `wtp-backend-engineer` (Round 1 단독)
- **검토 사항**: 대상 슬러그 / `swagger_schema_migration` 처리 / 자동 vs 수동 backfill
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**:
    - 대상 슬러그 = **5건** (로그인및토큰관리·사용자관리·송수펌프제어·권한메뉴·마스터도메인설계). `swagger_schema_migration` 은 전용 INDEX.md 미존재 확인 (`Grep` 결과 — 4개 SPEC 본문에 슬러그 문자열만 언급) → backfill 대상 제외.
    - 마스터도메인설계 처리 = backfill 포함 (`covers_dto` 공란 + 의도 주석 — 통지 SPEC 표기). 단, 이 결정은 안건 4 의 "통지 SPEC 처리" 가정과 연결 — PLAN 단계에서 공란/생략/문자열 중 확정.
    - 방식 = **A안 (본 작업 TASK 일괄 수행)**. B안 (자연 backfill) 은 다음 SPEC 갱신 시점 불확정으로 stale 기간 수개월 발생 가능 — 커버리지 탐색 효율화 목표 미달성.
- **Round 2**: 없음
- **결론**: 5건 backfill 일괄 (마스터도메인설계 포함), `swagger_schema_migration` 제외 (전용 INDEX 미존재).

### 안건 4: 도메인 4영역 / frontend 자동 코드 생성 도구 영향

- **호출 에이전트**: `wtp-domain-expert` (Round 1 단독 — 이견 없음)
- **검토 사항**: 도메인 4영역 영향 / frontend 자동 도구 영향 / 가정·미해결 질문
- **Round 1 답변 요약**:
  - **wtp-domain-expert**:
    - 도메인 4영역 (알람·인터록·운전 모드·이력 기록) 모두 **비해당**. 본 작업은 INDEX.md 메타 + `/dev:spec` 자동 채우기 + 5건 backfill 한정. DB 컬럼·엔티티·SCADA·PLC 변경 0건. INDEX 메타에 `AlarmDto`·`PumpControlRequestDto`·`AiDrvnMode` 클래스명을 문자열로 기재하는 행위는 알람 평가·인터록 검사·운전 모드 전환 흐름 미트리거.
    - frontend 자동 도구 영향 = **없음**. INDEX.md 는 사람이 읽는 마크다운 인덱스. Orval / OpenAPI Generator 입력은 backend `/api/v3/api-docs` JSON 또는 Swagger UI — INDEX.md 무관.
    - 가정·미해결 질문 2건:
      1. **클래스명 기재 형식** (가정): 단명 (`PumpControlRequestDto`) vs FQCN (`com.mo.swtp.pump.dto.PumpControlRequestDto`). 단명 권장 — FQCN 시 패키지 이동 (마스터도메인설계 이관 계획: `com.mo.swtp.pump` → `com.mo.swtp.instrument`) 발생 시 obsolete 부담. PLAN 확정.
      2. **통지 SPEC `covers_*` 처리** (미해결): 빈 배열 (`covers_dto: []`) vs 필드 생략 vs `"해당 없음"` 문자열. PLAN 확정.
- **Round 2**: 없음
- **결론**: 도메인 4영역 모두 비해당, frontend 도구 영향 없음, 가정 2건 PLAN 단계 결정.

## 표준 사전 카탈로그

### 신규 표준 단어
없음 — `cover`/`covers` 는 DB 컬럼명 재료 범위 외 (등록 의무 면제).

### 신규 표준 데이터 도메인
없음 — DB 컬럼 변경 0건 (DBA 2차 승인 비필요).

### 신규 표준 용어
없음 — DB 컬럼 변경 0건.

### 신규 비즈니스 도메인 약어
없음 — process/doc-harness 메타 영역 변경 (비즈니스 도메인 무관).

## 신규 엔티티/DB 컬럼

없음 — 본 작업은 doc-harness 메타·산출물 영역 한정 (`templates.md` + `dev/spec.md` + frontend `INDEX.md` × 5).

## 기존 사전·패턴과의 충돌

| 항목 | 충돌 여부 | 근거 |
|------|---------|------|
| ROOT `standard-words.md` | 없음 | `cover` 등록 의무 면제 (DB 컬럼명 재료 아님) |
| ROOT `domain-abbreviations.md` | 없음 | 비즈니스 도메인 약어 신규 등록 0건 |
| backend `standard-terms.md` | 없음 | DB 컬럼명 신규 0건 |
| `templates.md` INDEX 템플릿 | 호환 (확장) | 기존 본문 구조 (`## 최신 명세` + `## 이력` + `## 관련 backend 작업`) 보존, frontmatter 추가만 |
| `dev/spec.md` 절차 | 호환 (확장) | 기존 `## INDEX.md 갱신` 절에 `covers_*` 작성 단계 추가만 |

## PLAN 으로 전달할 결정 사항

- **도메인 모델 초안**: 해당 없음 (DB·엔티티 변경 없음)
- **DB 설계 변경 초안**: 해당 없음
- **적용할 패턴**:
  1. INDEX.md frontmatter 에 `covers_dto` / `covers_controller` / `covers_errorcode` 3개 키 추가 (YAML 배열 형식, 클래스 단명 권장)
  2. `/dev:spec` §INDEX.md 갱신 절에 `covers_*` 자동 채우기 단계 추가 — Java 소스 grep SSOT 활용 (기존 §코드 산출물 수집 결과 재사용)
  3. 5건 INDEX backfill 일괄 수행 (로그인및토큰관리·사용자관리·송수펌프제어·권한메뉴·마스터도메인설계)
  4. 통지 SPEC (`마스터도메인설계` 등) 의 `covers_*` 처리: PLAN 단계 확정 (빈 배열 vs 생략 vs 문자열)
  5. 클래스명 기재 형식: 단명 (FQCN 아님) — PLAN 단계 확정

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `covers_*` 메타 값은 Java 클래스 단명 (`PumpControlRequestDto`) 으로 기재 — FQCN 채택 시 패키지 이동 (마스터도메인설계 이관 계획 `com.mo.swtp.pump` → `com.mo.swtp.instrument`) 발생 시 obsolete 부담 발생 | 가정 | PLAN 단계에서 단명 vs FQCN 선택 확정 의무. `wtp-glossary-manager` Round 1 결정 (frontmatter 단독·키 이름) 과 별개 항목 |
| 통지 SPEC (Controller·DTO 가 없는 SPEC — 마스터도메인설계 등) 의 `covers_*` 처리: 빈 배열 (`covers_dto: []`) vs 필드 생략 vs `"해당 없음"` 문자열 | 미해결 | PLAN 단계 확정. `templates.md` INDEX 템플릿 갱신 시 함께 명문화 |
| `/dev:spec` 자동 채우기 시 SPEC{N+1} 작성과 INDEX `covers_*` 갱신의 트랜잭션 경계: 부분 실패 시 SPEC 만 작성되고 INDEX 미갱신 시나리오에 대한 사용자 안내 문구 위치·형식 | 가정 | PLAN 단계에서 안내 문구 (Markdown 절차서 1~2 문장) 결정. A안 채택으로 분리 단계이므로 부분 실패 자체는 허용 가능 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| INDEX.md backfill 5건의 `covers_dto` 값과 각 슬러그 SPEC 본문 "## DTO 스키마" 섹션이 1:1 매칭 | 5건 INDEX `covers_dto` 값 vs SPEC DTO 표 헤딩 명단 비교 — 누락 0건 확인 |
| 회귀 시나리오: 13개 DTO 변경 시나리오에서 INDEX `covers_dto` 단일 grep 으로 영향 4 슬러그 식별 가능 | `grep -l "LoginResponseDto\|TokenResponseDto\|UserDto\|PumpDashboardDto\|MenuTreeDto" swtp/frontend/docs/api-specs/*/INDEX.md` 출력에 swagger_schema_migration 제외 4 슬러그 모두 포함 |
| `/dev:spec` 신규 호출 시 covers_* 자동 채우기 동작 (예시 슬러그로 dry-run) | `dev/spec.md` 절차 한 차례 시뮬레이션 — INDEX `covers_*` 3개 키 모두 자동 채워짐 확인 |
| backend 빌드·테스트 무회귀 | `./gradlew.bat build` BUILD SUCCESSFUL 출력 확인 |
| `templates.md` INDEX 템플릿 갱신 후 기존 5건 backfill 결과가 신규 템플릿 형식과 일치 | `templates.md` 의 INDEX 블록 frontmatter 예시 vs 5건 INDEX frontmatter 비교 — 키 이름·형식 일치 확인 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 알람 임계값·전이 조건·복귀 조건 (`alarm_h`·`AlarmEvaluator`) 정의·변경 코드 경로 0건. INDEX 메타에 `AlarmDto` 류 클래스명을 문자열로 기재하는 행위는 알람 4단계 평가 흐름 미경유 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | `pump_interlock_p` 평가 로직·`InterlockValidator.validateOrThrow(...)` 호출 경로 무접촉. INDEX 메타에 `PumpControlRequestDto` 클래스명을 문자열로 기재 = 인터록 검사 미트리거. `ot-integration.md §5 ⚠️ 절대 금지` 해당 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod` (사용자 의도) / `ai_mode_cd` (시스템 상태) 컬럼 변경 0건. `ai_drvn_mod_p` 테이블·스케줄러 강제 전환 로직 무접촉. INDEX 메타의 `AiDrvnMode` enum 클래스명 기재는 정적 문서 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason` 5종 (`USER_SELECT`·`SCADA_TIMEOUT`·`MANUAL_EXPIRE`·`OUTBOUND_FAIL`·`SYSTEM_INIT`) 기록 의무 / `pump_ctrl_h` 제어 로그 보존 정책 무접촉. 신규 DB 컬럼·엔티티 0건 |

> **차단 해제 조건 충족**: (1) 4행 모두 구체 사유 명기 완료, (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족.

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

(해당 없음) — 본 작업은 표준 사전 갱신 0건 (안건 1 결론 — `cover` 표준 단어 등록 면제 + ROOT 어휘 갱신 불필요). 본 작업의 핵심 변경 7건 (`templates.md` INDEX 템플릿 frontmatter 추가 + `dev/spec.md` covers_* 자동 채우기 + INDEX 5건 backfill) 은 ANALYZE 의 룰 메타 갱신이 아니라 본 작업 자체의 구현 대상이므로, PLAN 의 "## 변경 예상 파일" + TASK 체크박스로 전달된다. PLAN 진입 전제조건 (모든 룰 갱신 지시서 체크박스 `- [x]`) 은 본 섹션이 빈 항목이므로 즉시 충족된다.

## 산출물

- [계획안](../../../plan/20260508/spec_index_coverage_meta/PLAN1.md) (작성 예정)
