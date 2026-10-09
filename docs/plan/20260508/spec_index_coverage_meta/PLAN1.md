---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# spec_index_coverage_meta — INDEX.md `covers_*` 메타 도입

## 목적

backend↔frontend 통신 스펙 변경 시 영향 SPEC 탐색 비효율 해소. frontend `swtp/frontend/docs/api-specs/{슬러그}/INDEX.md` 에 `covers_dto` / `covers_controller` / `covers_errorcode` frontmatter 메타를 도입하여 향후 backend 변경 시 영향 슬러그 탐색을 O(전체 SPEC 수) → O(슬러그 수) 로 단순화한다. `/dev:spec` 단계에 자동 채우기를 추가하여 stale 위험을 완화한다.

## 배경

- **직전 작업**: `swagger_schema_migration` (commit `464d52f`) — 13개 DTO 변경 → 4개 영향 슬러그 식별을 위해 frontend 전체 SPEC 본문 grep 필요. 슬러그 누적 시 비용 선형 증가 우려.
- **사용자 결정**: 옵션 1 (INDEX.md 메타 확장) + 자동 채우기 (stale 완화) 트레이드오프 채택. 옵션 2 (SPEC frontmatter 메타) · 옵션 3 (자동화 스크립트) 미선택.
- **ANALYZE1 결론** ([../../../analyze/20260508/spec_index_coverage_meta/ANALYZE1.md](../../../analyze/20260508/spec_index_coverage_meta/ANALYZE1.md)): 5인 회의 블로커 0건. frontmatter 단독 / `covers_dto·covers_controller·covers_errorcode` / `cover` 표준 단어 등록 면제 / ROOT 어휘 갱신 불필요 / 5건 backfill 일괄 / 도메인 4영역 비해당.

## 범위

| 변경 영역 | 파일 | 변경 유형 |
|---------|------|---------|
| backend 룰 (모듈 SSOT) | `swtp/backend/.claude/rules/process/doc-harness/templates.md` | INDEX.md 템플릿 블록에 YAML frontmatter (`covers_dto` / `covers_controller` / `covers_errorcode` 3개 키 + 작성 기준 주석) 추가 |
| backend 커맨드 | `swtp/backend/.claude/commands/dev/spec.md` | §INDEX.md 갱신 절에 `covers_*` 자동 채우기 단계 추가 (Java 소스 grep SSOT, A안 분리 단계) + 부분 실패 안내 문구 |
| frontend 산출물 (5건 backfill) | `swtp/frontend/docs/api-specs/{로그인및토큰관리·사용자관리·송수펌프제어·권한메뉴·마스터도메인설계}/INDEX.md` | frontmatter 3 키 추가 + covers_* 값 backfill |

## 구현 방향

### Phase 1 — `templates.md` INDEX 템플릿 갱신

기존 INDEX.md 템플릿 (templates.md L379~L397) 본문 구조 (`## 최신 명세` + `## 이력` + `## 관련 backend 작업`) 보존, frontmatter 만 신규 추가.

**갱신 예시**:
```markdown
---
covers_dto:
  - LoginResponseDto
  - TokenResponseDto
  - MenuTreeDto
covers_controller:
  - AuthController
covers_errorcode:
  - AuthErrorCode
---
# {슬러그} API 명세 인덱스

## 최신 명세
...
```

**작성 기준 주석** (templates.md INDEX 블록 본문에 추가):
- 클래스 단명 사용 (FQCN 금지) — 패키지 이동 시 obsolete 부담 회피
- 통지 SPEC (Controller·DTO 가 없는 SPEC) 은 `covers_dto: []` 빈 배열 명시
- `/dev:spec` 자동 채우기 사용 권장 — Java 소스 grep SSOT 기반

### Phase 2 — `dev/spec.md` 자동 채우기 로직 추가

기존 `## INDEX.md 갱신` 절 (현 절차서 §"INDEX.md 갱신") 을 확장. SPEC 작성 후 별도 단계로 INDEX 갱신 (A안 분리 단계).

**추가 절차**:
1. 기존 §"코드 산출물 수집" 단계의 추출 결과 (Controller·DTO·ErrorCode 클래스명) 를 그대로 활용
2. INDEX.md frontmatter 의 `covers_*` 3 키에 클래스 단명 배열 작성
3. 신규 INDEX 생성 시 frontmatter 포함, 기존 INDEX 갱신 시 frontmatter 추가/병합

**부분 실패 안내 문구** (절 시작 추가):
> "SPEC 작성 후 INDEX 갱신은 분리 단계로 진행됩니다. 부분 실패 (예: SPEC 작성 후 INDEX 갱신 도중 중단) 시 동일 슬러그로 `/dev:spec` 재호출하면 자동 채우기가 idempotent 하게 적용됩니다 — Java 소스 SSOT 가 항상 최신 기준."

### Phase 3 — 5건 INDEX backfill 일괄 수행

각 INDEX.md 의 SPEC 본문 "## DTO 스키마" / "## 엔드포인트 목록" / "## ErrorCode 표" 섹션에서 클래스명을 추출하여 frontmatter 에 backfill.

**대상 5건 + 영향 클래스 명단** (ANALYZE1 §룰 갱신 지시서 기반, 이전 swagger_schema_migration commit `464d52f` 반영):

| INDEX.md | covers_dto |
|---------|----------|
| 로그인및토큰관리 | `LoginResponseDto` / `TokenResponseDto` / `MenuTreeDto` |
| 사용자관리 | `UserDto` / `UserUpsertDto` |
| 송수펌프제어 | `PumpDashboardDto` / `PumpStateDto` / `PumpControlRequestDto` / `PumpControlResultDto` / `AiModeDto` / `AiModeUpsertDto` / `AiPredictionResponseDto` |
| 권한메뉴 | `MenuTreeDto` / `MenuRoleUpsertDto` / `LoginResponseDto` |
| 마스터도메인설계 | (PLAN 결정 — 통지 SPEC 처리 정책 적용. 빈 배열 + 통지 SPEC 표기 주석) |

`covers_controller` 와 `covers_errorcode` 는 TASK 단계에서 SPEC 본문 grep + backend Java 소스 검증으로 추출.

`swagger_schema_migration` 슬러그는 전용 INDEX.md 미존재 (`wtp-backend-engineer` Round 1 확인) → backfill 대상 제외.

## 도메인 모델

해당 없음 — 본 작업은 doc-harness 메타·산출물 영역 한정. 신규 엔티티·DTO·컬럼 0건. 도메인 4영역 (알람·인터록·운전 모드·이력 기록) 비해당 (ANALYZE1 §도메인 룰 4영역 점검 결론).

## DB 설계 변경

해당 없음 — 본 작업은 DB 스키마·엔티티·인덱스·파티션 무관. `wtp-dba-reviewer` 검토 게이트 비적용.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령·테스트·조회 |
|------|------------------|
| INDEX.md backfill 5건의 `covers_dto` 와 SPEC 본문 "## DTO 스키마" 섹션이 1:1 매칭 | 5건 INDEX `covers_dto` 값 vs SPEC DTO 표 헤딩 명단 수동 비교 — 누락 0건 / 추가 0건 확인. `git diff swtp/frontend/docs/api-specs/*/INDEX.md` 에 5 파일 변경 + 본문 무손상 |
| 회귀 시나리오: 직전 swagger_schema_migration 13개 DTO 변경 → INDEX `covers_dto` 단일 grep 으로 영향 4 슬러그 식별 | `grep -l "LoginResponseDto\|TokenResponseDto\|UserDto\|UserUpsertDto\|PumpDashboardDto\|PumpStateDto\|PumpControlRequestDto\|PumpControlResultDto\|AiModeDto\|AiModeUpsertDto\|AiPredictionResponseDto\|MenuTreeDto\|MenuRoleUpsertDto" swtp/frontend/docs/api-specs/*/INDEX.md` 출력에 로그인및토큰관리·사용자관리·송수펌프제어·권한메뉴 4 슬러그 모두 포함, swagger_schema_migration 미포함 |
| `/dev:spec` 신규 호출 시 covers_* 자동 채우기 동작 (절차서 dry-run) | `dev/spec.md` 절차 1회 시뮬레이션 — INDEX 3 키 자동 채워짐 확인 (실 호출 검증은 다음 SPEC 갱신 시점) |
| backend 빌드·테스트 무회귀 | `./gradlew.bat build` BUILD SUCCESSFUL 출력 확인 |
| `templates.md` INDEX 템플릿과 5건 INDEX frontmatter 형식 일치 | `templates.md` INDEX 블록 frontmatter 예시 vs 5건 INDEX frontmatter — 키 이름·들여쓰기·배열 형식 일치 (수동 diff) |
| ANALYZE1 룰 갱신 지시서 7건 모두 PLAN/TASK 변경 대상으로 1:1 매핑 | TASK1.md 체크박스 7건 vs ANALYZE1 §룰 갱신 지시서 7건 비교 — 누락 0건 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 클래스명 기재 형식 (단명 vs FQCN) | 가정 → 결정 | **단명 채택** (`PumpControlRequestDto`). 근거: (1) FQCN 시 패키지 이동 (마스터도메인설계 이관 계획 `com.mo.swtp.pump` → `com.mo.swtp.instrument`) 발생 시 INDEX 메타 obsolete 일괄 갱신 부담, (2) 클래스 단명 충돌 (예: 동명 클래스 다른 패키지) 발생 시 별도 ANALYZE 로 명명 규칙 보강 — 현 5건에서는 단명 충돌 0건 확인 |
| 통지 SPEC `covers_*` 처리 형식 | 미해결 → 결정 | **빈 배열 (`covers_dto: []`) 채택**. 근거: (1) YAML 표준 형식 (필드 생략보다 의도 명확), (2) `grep "covers_dto:" INDEX.md` 시 통지 SPEC 도 일관 매칭 (빈 배열 명시), (3) 마스터도메인설계 같은 통지 SPEC 은 INDEX 본문에 `> 본 SPEC 은 backend 도메인 분석 통지로 자체 DTO/Controller/ErrorCode 가 없습니다.` 한 줄 주석 추가 |
| A안 분리 단계 부분 실패 시 안내 문구 | 가정 → 결정 | **`dev/spec.md` §INDEX.md 갱신 절 시작에 idempotent 안내 문구 추가**. 위 §구현 방향 Phase 2 §부분 실패 안내 문구 참조. SPEC 작성 후 INDEX 갱신 미수행 시 `/dev:spec` 재호출로 idempotent 복구 가능 |

## 제외 사항

- **옵션 2 (SPEC frontmatter 메타)** — 미채택. INDEX 단일 위치 메타로 충분, SPEC 마다 메타 중복은 §2 단순성 위반
- **옵션 3 (자동화 스크립트)** — 미채택. Java AST 파싱 + grep 매칭 도구 도입은 §2 추상화 과잉, `/dev:spec` 자동 채우기로 동등 효과 달성
- **backend Java 소스 변경** — 0건. DB·엔티티·Service·Controller·ErrorCode 변경 없음
- **도메인 안전 영역 변경** — 0건. 알람·인터록·운전 모드·이력 기록 무영향 (ANALYZE1 §도메인 룰 4영역 점검)
- **frontend 자동 코드 생성 도구 입력 형식 변경** — 0건. INDEX.md 는 사람이 읽는 인덱스 문서, Orval / OpenAPI Generator 는 backend `/api/v3/api-docs` JSON 만 참조
- **`swagger_schema_migration` 슬러그 INDEX backfill** — 제외. 전용 INDEX.md 미존재 (`wtp-backend-engineer` Round 1 확인)

## 예상 산출물

- [태스크](../../../tasks/20260508/spec_index_coverage_meta/TASK1.md)
