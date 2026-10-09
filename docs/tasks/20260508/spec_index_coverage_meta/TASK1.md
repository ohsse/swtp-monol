---
status: completed
created: 2026-05-08
updated: 2026-05-08
---
# spec_index_coverage_meta — INDEX.md `covers_*` 메타 도입

## 관련 계획
- [계획안](../../../plan/20260508/spec_index_coverage_meta/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. **검증 영역에 백틱 사용 금지** (`check-task-unstage.sh` 훅 파싱 충돌).

### Phase 1: `templates.md` INDEX 템플릿 갱신

- [x] `swtp/backend/.claude/rules/process/doc-harness/templates.md` INDEX.md 템플릿 블록에 YAML frontmatter 3 키 (covers_dto / covers_controller / covers_errorcode) + 작성 기준 주석 (클래스 단명 / 통지 SPEC 빈 배열 / 자동 채우기 권장) 추가 → 검증: grep covers_dto templates.md 출력에 3 키 모두 매칭 + INDEX 블록 본문에 작성 기준 3 항목 모두 존재 확인

### Phase 2: `dev/spec.md` 자동 채우기 로직 추가

- [x] `swtp/backend/.claude/commands/dev/spec.md` §INDEX.md 갱신 절에 covers_* 자동 채우기 단계 추가 (Java 소스 grep SSOT 활용, A안 분리 단계) + 절 시작에 idempotent 안내 문구 1~2 문장 추가 (부분 실패 시 재호출 idempotent 복구 명시) → 검증: grep covers_dto spec.md 출력 매칭 + grep idempotent 또는 재호출 spec.md 출력 매칭

### Phase 3: 5건 INDEX backfill

- [x] `swtp/frontend/docs/api-specs/로그인및토큰관리/INDEX.md` frontmatter backfill (covers_dto = LoginResponseDto / TokenResponseDto / MenuTreeDto, covers_controller / covers_errorcode 는 SPEC 본문 추출) → 검증: frontmatter 3 키 모두 존재 + covers_dto 명단이 SPEC2.md "## DTO 스키마" 섹션 헤딩과 1:1 일치 수동 확인
- [x] `swtp/frontend/docs/api-specs/사용자관리/INDEX.md` frontmatter backfill (covers_dto = UserDto / UserUpsertDto, covers_controller / covers_errorcode 는 SPEC 본문 추출) → 검증: frontmatter 3 키 존재 + covers_dto 명단 SPEC1.md DTO 섹션 1:1 일치 수동 확인
- [x] `swtp/frontend/docs/api-specs/송수펌프제어/INDEX.md` frontmatter backfill (covers_dto = PumpDashboardDto / PumpStateDto / PumpControlRequestDto / PumpControlResultDto / AiModeDto / AiModeUpsertDto / AiPredictionResponseDto, covers_controller / covers_errorcode 는 SPEC 본문 추출) → 검증: frontmatter 3 키 존재 + covers_dto 7건 명단 SPEC2.md DTO 섹션 1:1 일치 수동 확인
- [x] `swtp/frontend/docs/api-specs/권한메뉴/INDEX.md` frontmatter backfill (covers_dto = MenuTreeDto / MenuRoleUpsertDto / LoginResponseDto, covers_controller / covers_errorcode 는 SPEC 본문 추출) → 검증: frontmatter 3 키 존재 + covers_dto 명단 SPEC1.md DTO 섹션 1:1 일치 수동 확인
- [x] `swtp/frontend/docs/api-specs/마스터도메인설계/INDEX.md` frontmatter backfill (통지 SPEC — covers_dto: [] / covers_controller: [] / covers_errorcode: [] 빈 배열 3 키 명시 + INDEX 본문 상단에 통지 SPEC 표기 주석 1줄 추가) → 검증: frontmatter 3 키 모두 빈 배열 + 통지 SPEC 표기 주석 존재 수동 확인

### Phase 4: 검증

- [x] 13개 DTO grep 회귀 검증 (직전 swagger_schema_migration 시나리오) → 검증: grep -l 명령으로 13개 DTO 클래스명을 5건 INDEX 에서 검색 시 로그인및토큰관리·사용자관리·송수펌프제어·권한메뉴 4 슬러그 모두 포함, 마스터도메인설계 미포함 (빈 배열) 확인
- [x] `swtp/backend/.claude/rules/process/doc-harness/templates.md` INDEX 블록 frontmatter 형식 vs 5건 INDEX frontmatter 형식 1:1 일치 확인 → 검증: 키 이름·들여쓰기·YAML 배열 형식 수동 diff, 차이 0건
- [x] `swtp/backend/.claude/commands/dev/spec.md` 절차서 dry-run (covers_* 자동 채우기 단계 시뮬레이션) → 검증: spec.md §INDEX.md 갱신 절을 1회 시뮬레이션 후 INDEX frontmatter 3 키가 자동 채워지는 절차 흐름 정합성 확인
- [x] ./gradlew.bat build 무회귀 → 검증: BUILD SUCCESSFUL 출력 확인 (사전 실패 6건 — PumpDrvnStatusIntegrationTest 4건·DrvnAnlsDwldHistoryRepositoryTest 2건 — 은 본 작업 변경 영역(룰·커맨드·INDEX.md, Java 소스 0건) 외부의 송수펌프제어_운전현황분석 미커밋 DB DDL 의존 사전 실패. 본 작업 회귀 0건)

## 산출물
- [결과](../../../results/20260508/spec_index_coverage_meta/RESULT1.md)
