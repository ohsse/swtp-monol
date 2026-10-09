---
status: approved
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 (Tier 1 + Tier 2) — 계획안

## 목적

스마트정수장 백엔드의 `.claude/` 자산 (CLAUDE.md · 14개 룰 · 8개 dev 스킬 · 4개 사용자 정의 에이전트 · 2개 훅) 의 동일 규칙 산재·집약 과다·부재 항목을 정리한다.

- **SSOT 강화** — Fix Cycle, 자동 전이, YnType, "도메인 용어 구분", PLAN/TASK 템플릿의 1차 정의 위치를 단일화
- **집약 과다 해소** — `db-patterns.md` 3분리, `test-strategy.md` 부록 A 분리, `ot-integration.md` 계층 태그 보강
- **부재 항목 신규** — 커밋 컨벤션 룰, 훅 동작 가이드 신규 작성
- **CLAUDE.md 축약 + 인덱스 보강** — "탐색 인덱스 + 최소 원칙" 정착

## 배경

- 사용자 요청: "현재 프로젝트의 CLAUDE.md 및 규칙파일·스킬들을 확인하고 중복·집약적인 내용을 분리해서 관리할 수 있을지 검토하고 계획안을 세워달라"
- Plan mode Phase 1 Explore 3개 에이전트 병렬 진단 완료 → 5개 상위 중복, 3개 집약 과다, 2개 부재 항목 식별
- 사용자 결정: **Tier 1+2 범위** (Tier 3 항목은 후속 PLAN 으로 분리)
- ANALYZE1.md (`docs/analyze/20260424/rules_dedup_정리/ANALYZE1.md`) 에서 5인 팀 회의 생략 사유, 확정 메타-결정, 신규 파일 명세, 룰 갱신 지시서 52개 체크박스, ⚠️ 예외 처리 (메타 작업 체크박스 면제) 정식 기록 완료
- 외부 plan 파일: `~\.claude\plans\claude-md-polymorphic-badger.md` (Plan mode 산출, 본 PLAN 의 결정 근거)

## 범위

### 포함 (Tier 1 + Tier 2)

**Tier 1 — 상위 중복 정리 + CLAUDE.md 축약**
- T1-A Fix Cycle 알고리즘 단일화 (`doc-harness.md` SSOT, dev.md / dev:analyze.md / dev:plan.md 참조 단축)
- T1-B 상태 전이·자동 실행 규칙 단일화 (`doc-harness.md` 신규 섹션 + 9곳 참조 단축)
- T1-C YnType / Persistable 패턴 참조 정리 (`entity-patterns.md` SSOT 유지, 4파일 링크 단축)
- T1-D "도메인 용어 구분" 경고 SSOT 화 (`dict/README.md` 유지, 3파일 링크 단축)
- T1-E PLAN/TASK 템플릿 단일 게시 (`doc-harness.md` 유지, dev:plan/dev:task 참조 단축)
- T1-F CLAUDE.md 축약 + 인덱스 보강

**Tier 2 — 분리 + 신규 파일**
- T2-F `db-patterns.md` 3분리 (옵션 1 — 인덱스 파일로 전환) + 모든 외부 참조 갱신
- T2-G `test-strategy.md` 부록 A 분리 (`test-strategy-e2e-roadmap.md` 신규)
- T2-H `ot-integration.md` 기술/비즈니스 계층 헤더 태그 보강 (분리 보류)
- T2-I 커밋 컨벤션 룰 신규 (`commit-convention.md`)
- T2-J 훅 동작 가이드 신규 (`hooks-guide.md`)

### 제외 (Tier 3 — 후속 PLAN 으로 분리)

- 모든 룰 파일에 "참조 문서 관계" 표 일관 추가 (현재 5개 파일 미포함)
- `wtp-backend-engineer.md` 등 에이전트의 검토 항목 텍스트를 룰 파일 자동 첨부로 대체
- `multi-tenant.md` §4 (프로파일 분기/Bean/별도 모듈 판정) 의 별도 파일 분리
- `dict/standard-words.md` vs `domain-abbreviations.md` 경계 재정리
- `naming.md` §Java 필드 타입 매핑 의 dict/ 로 이전 가능성 재평가
- `dev/analyze.md` §1.3 의 "사전 판별 분기 §5a" 추가 단순화

## 구현 방향

### Phase 1 — SSOT 강화 (의미 변경 0, 가장 안전)

순서: T1-D → T1-A → T1-B (T1-D 는 가장 단순한 1줄 링크 작업으로 시작 → 점진적 복잡도)

**T1-D — "도메인 용어 구분" 경고 4중 → SSOT 1곳**
- `.claude/rules/dict/README.md` §⚠️ "도메인" 용어 충돌 방지 — 변경 없음, SSOT 확정
- `CLAUDE.md` "⚠️ '도메인' 용어 구분" 각주 → "데이터 도메인 vs 비즈니스 도메인 구분: `.claude/rules/dict/README.md` 참조" 1줄
- `.claude/rules/dict/standard-data-domains.md` 헤더 ⚠️ 박스 → 동일 1줄
- `.claude/rules/domain-abbreviations.md` 헤더 ⚠️ 박스 → 동일 1줄

**T1-A — Fix Cycle 알고리즘 3중 → `doc-harness.md` SSOT**
- `.claude/rules/doc-harness.md` §수정 사이클 — 기존 §ANALYZE 조건부 재진입 표 위에 "Fix Cycle 감지 알고리즘 (의사 코드)" 박스 신규 추가. 입력 슬러그·출력 진입단계, REVIEW status·블로커 텍스트 분기, 키워드 매칭 → ANALYZE 진입 / 불일치 → PLAN 직행
- `.claude/commands/dev.md` §2.2 → "Fix Cycle 감지 로직: `.claude/rules/doc-harness.md` §수정 사이클 참조" 1줄 + 자동 전이 핸드오프만 남김
- `.claude/commands/dev/analyze.md` §1.3 → 동일 단축
- `.claude/commands/dev/plan.md` §1.3 → 동일 단축. §5a (사전 판별 분기) 는 PLAN 단계 고유 책임이므로 유지

**T1-B — 상태 전이·자동 실행 9곳 분산 → `doc-harness.md` 신규 섹션**
- `.claude/rules/doc-harness.md` 말미에 §상태 전이·자동 실행 신규 섹션 추가. 표 컬럼: 현재 단계 / 승인 필요 여부 / 다음 단계 / 자동 실행 여부 / Small·Medium·Large 차이
- `.claude/commands/dev.md` §7 표 → 위 섹션 참조 1줄
- 각 `.claude/commands/dev/*.md` (analyze/plan/task/impl/result/review/commit) 마지막 전이 안내 → 동일 참조 1줄

### Phase 2 — 참조 정리 (의미 변경 0)

**T1-C — YnType / Persistable 패턴 5중 → `entity-patterns.md` 단독 정의 유지**
- `.claude/rules/naming.md` §Java 필드 타입 매핑 — 주요 매핑 원칙 안의 YnType·Persistable 줄을 한 표로 통합하고 entity-patterns 링크만 유지 (재설명 제거)
- `.claude/rules/api-patterns.md` DTO 섹션의 YnType 문단 (`@Schema(allowableValues)` · `@Pattern` · `example` 중복 명시 금지) → "DTO 의 `*Yn` 필드도 YnType 사용. 패턴: entity-patterns.md §여부(Y/N)" 1줄
- `.claude/rules/dict/standard-data-domains.md` `DOM_YN` · `DOM_ID_50` 행 비고 → entity-patterns 링크 1줄 단축
- `.claude/rules/dict/standard-words.md` `yn` 행 비고 → 동일 단축

**T1-E — PLAN/TASK 템플릿 2중 게시 → `doc-harness.md` 단독 게시**
- `.claude/commands/dev/plan.md` §계획 문서 작성 의 PLAN 템플릿 코드 블록 → "PLAN 템플릿: doc-harness.md §PLAN{n}.md" 참조로 대체
- `.claude/commands/dev/task.md` §템플릿 의 TASK 템플릿 코드 블록 + TASK 분할 기준 → 동일 참조로 단축
- `.claude/rules/doc-harness.md` 상단에 "본 파일이 모든 문서 템플릿·상태 전이·Fix Cycle 의 SSOT 임을 명시" 1문단 추가

**T1-F — CLAUDE.md 축약 + 인덱스 보강**
- §예외 및 응답 규칙 (4줄) → 2줄 + "상세: `.claude/rules/exception-patterns.md`" 위임
- §패키지 규칙 (5줄) → 2줄 + "클래스/필드/DB 컬럼 명명 상세: `.claude/rules/naming.md`, 비즈니스 도메인 약어: `.claude/rules/domain-abbreviations.md`"
- §코드 작성 규칙 — Lombok·Javadoc·Swagger 줄에 "패턴 상세: entity-patterns.md / api-patterns.md" 링크 추가
- §규칙 문서 인덱스 표 — 신규 6행 추가 (commit-convention · hooks-guide · db 분리 3행 · test-strategy-e2e-roadmap)
- §"⚠️ 도메인 용어 구분" 각주 → 1줄로 축약 (T1-D 와 동기)
- §작업 흐름 — 변경 없음

### Phase 3 — 신규 파일 도입 (Tier 2)

순서: T2-I → T2-J → T2-G (단순 신규 → 훅 분석 → 분량 큰 이동)

**T2-I — `commit-convention.md` 신규**
- `.claude/rules/commit-convention.md` 신규 작성 (~50줄)
  - 타입 정의 + 사용 예 (feat/fix/refactor/docs/chore/test)
  - 한국어 메시지 정책 (사용자 전역 + 프로젝트 CLAUDE.md 모두 한국어)
  - 본문/제목/푸터 구조 (Co-Authored-By 정책 — CLAUDE.md 본문 git 안전 프로토콜 기반 실측)
  - 브레이킹 체인지 — "도입 보류, 필요 시 별도 ANALYZE" 명시
  - pre-commit 훅 상호작용 (`check-task-unstage.sh` 동작 요약 + `hooks-guide.md` 링크)
- `CLAUDE.md` §커밋 규칙 → 2줄 + "타입: feat/fix/refactor/docs/chore/test, 한국어. 상세: `.claude/rules/commit-convention.md`" 위임

**T2-J — `hooks-guide.md` 신규**
- `.claude/rules/hooks-guide.md` 신규 작성 (~60줄)
  - 훅 인덱스 — `check-task-unstage.sh`(PreToolUse, Bash matcher) · `check-errorcode-contract.sh`(PostToolUse, Write/Edit matcher)
  - 각 훅의 트리거·매처·차단 동작 (exit 0 = 허용, exit 2 = 차단)
  - TASK 체크박스 경로 파싱 규칙 — `doc-harness.md` §TASK/ANALYZE 체크박스 파일 경로 기록 규칙 과 1:1 대응표
  - 우회 방법 — `GIT_SKIP_DOC_CHECK=1 git commit ...` (사용자 명시 시에만, CLAUDE.md git 안전 프로토콜과 정합)
  - 신규 훅 추가 절차 — `.claude/settings.local.json` 등록 + 본 가이드 갱신
- `.claude/commands/dev/commit.md` 의 훅 언급 → hooks-guide 참조로 단순화

**T2-G — `test-strategy.md` 부록 A 분리**
- `.claude/rules/test-strategy-e2e-roadmap.md` 신규 (~130줄) — 부록 A 9소섹션 (A.1 ~ A.8) 그대로 이동, 헤더에 "현 시점 미도입 — 도입 시 별도 ANALYZE 필요" 명시
- `.claude/rules/test-strategy.md` 본문 §2 의 "Testcontainers 미도입 상태" 안내 박스 → "도입 로드맵: test-strategy-e2e-roadmap.md" 로 갱신
- `test-strategy.md` 부록 A 섹션 제거, 본문만 유지

### Phase 4 — 분리 + 링크 갱신 (Tier 2, 영향 범위 가장 큼)

**T2-F — `db-patterns.md` 3분리 + 모든 참조 링크 갱신**
- `.claude/rules/db-partitioning-and-retention.md` 신규 (~70줄) — `db-patterns.md` §1 (시계열 파티셔닝) + §6 (데이터 보존) 이동
- `.claude/rules/db-indexing-and-migration.md` 신규 (~80줄) — §2 (인덱스) + §3 (스키마 무중단 변경) 이동
- `.claude/rules/db-query-tuning.md` 신규 (~50줄) — §4 (트랜잭션 격리) + §5 (슬로우 쿼리) 이동
- `.claude/rules/db-patterns.md` → **인덱스 파일로 전환** (옵션 1 채택 — 외부 참조 호환성 우선). 3개 자식 파일 링크 + 1줄 요약만 보유
- 외부 참조 갱신 (영향 범위)
  - `.claude/rules/test-strategy.md` (`db-patterns.md §1`·§2·§5 참조 5곳)
  - `.claude/rules/ot-integration.md` (§1 저장 대상·§6 보존 정책 참조 2곳)
  - `.claude/rules/multi-tenant.md` 참조 표
  - `CLAUDE.md` §규칙 문서 인덱스 표 — db 행을 3행으로 분리 (T1-F 의 인덱스 보강과 통합 적용)
  - `.claude/agents/wtp-dba-reviewer.md` §1 의 `db-patterns.md` 참조
  - 기타 3개 에이전트 (wtp-glossary-manager / wtp-backend-engineer / wtp-domain-expert) 참조 검수 — 변경 필요 없음 예상

**T2-H — `ot-integration.md` 헤더 태그 보강 (분리 보류)**
- §1·§2 헤더에 `[기술 계층]` 태그 추가
- §3 헤더에 `[데이터 처리 정책]` 태그 추가
- §4 헤더에 `[기술 계층 — 회복성]` 태그 추가
- §5 헤더에 `[비즈니스 계층 — 장애 시 운전 모드]` 태그 추가
- 문서 상단 "참조 문서 관계" 표 아래에 1문장: "본 문서는 기술 계층(채널·회복성)과 비즈니스 계층(품질·장애 동작)을 함께 다룬다. OT 도입 ANALYZE 단계에서 분리 검토."

### Phase 5 — 검증 (파일 수정 없음, 실행 검증만)

- 링크 무결성 점검 — `.claude/` 와 `CLAUDE.md` 내 모든 마크다운 링크의 경로 유효성 (Glob + Grep 으로 깨진 링크 검출)
- 훅 동작 검증 — 임의 TASK 문서 작성 후 pre-commit 시 `check-task-unstage.sh` 가 정상 unstage / 경로 경고 출력
- 빌드 검증 — `./gradlew.bat build` 통과 (룰 변경은 코드 무영향이지만 안전 차원)
- /dev 워크플로우 종합 테스트 — 작은 슬러그(`smoke_test`) 로 `/dev` → `/dev:plan` → `/dev:task` → `/dev:impl` → `/dev:commit` 일주 시도. 단일화된 SSOT (Fix Cycle / 상태 전이 / 템플릿) 가 정상 참조되는지 확인
- 에이전트 정의 검토 — 4개 wtp-* 에이전트가 분리·이동된 룰 파일을 정확한 경로로 참조하는지 점검

## 도메인 모델

**해당 없음** — 본 작업은 룰·스킬·하네스 자체의 메타 개편이며, 신규 엔티티·DTO·DB 컬럼이 등장하지 않는다 (ANALYZE1.md §표준 사전 카탈로그 3표 모두 "없음" 으로 명시 — 신규 표준 단어 0건 / 신규 표준 데이터 도메인 0건 / 신규 표준 용어 0건).

## DB 설계 변경

**해당 없음** — 본 작업은 PostgreSQL 스키마·쿼리·파티션·인덱스를 변경하지 않는다. `db-patterns.md` 의 3분리는 **물리적 재배치만** 이며 시계열 파티셔닝·인덱스·무중단 마이그레이션·격리·슬로우 쿼리·보존 정책의 의미는 그대로다.

## 테스트 전략

- **자동 테스트 영향 없음** — 룰 파일 변경은 코드 컴파일 결과에 영향이 없다. 신규/수정 단위·통합 테스트 작성 없음.
- **회귀 검증** — `./gradlew.bat build` 통과 (안전 차원, 코드 무영향이지만 클래스패스·QClass 생성 회귀 방지)
- **링크 무결성 검증** — `.claude/` 와 `CLAUDE.md` 내 마크다운 링크 깨짐 검출 (Glob + Grep). 새 파일 경로 정합성 점검
- **하네스 동작 검증** — 작은 슬러그(`smoke_test`) 로 /dev 워크플로우 일주 시도. 단일화된 SSOT 참조 정상 동작 확인
- **훅 동작 검증** — 임의 TASK 문서 작성 후 pre-commit 훅의 차단·경로 경고 로직 정상 동작 확인
- **에이전트 정의 검수** — 룰 파일 분리·이동 후 4개 wtp-* 에이전트 정의의 경로 참조 정합성 점검

> 본 작업의 모듈별 테스트 명령은 적용되지 않는다 (`./gradlew.bat :api:test` 등). 검증은 모두 하네스·문서·도구 차원에서 수행한다.

## 제외 사항

- Tier 3 항목 (별도 후속 PLAN 으로 분리, §범위 §제외 참조)
- 룰 의미 변경 (DB 표준화 정책·테스트 커버리지 기준·알람 4단계·인터록·운전 모드 등은 그대로)
- 4개 사용자 정의 에이전트의 책임 경계 재설계 (현 분담 유지)
- `ot-integration.md` 의 5개 주제 분리 (헤더 태그만 보강)
- 코드 변경 (Java 소스·DB 스키마·application.yml 등)

## 예상 산출물

- [태스크](../../../tasks/20260424/rules_dedup_정리/TASK1.md) — 다음 단계 `/dev:task rules_dedup_정리` 에서 작성 예정

### TASK 분할 검토

ANALYZE1.md 의 룰 갱신 지시서 기준 — Phase 5 / 체크박스 52개. LARGE 분할 기준(Phase 10 / 체크박스 60) 미달이지만, **Phase 별 책임 분리** (SSOT 강화 / 참조 정리 / 신규 파일 / 분리 / 검증) 가 명확하고 영향 범위가 다르므로 4분할 후보를 권장한다.

| 분할 후보 | 묶음 | 체크박스 수 | 영향 범위 |
|---------|------|-----------|---------|
| TASK1-1 | Phase 1 + 2 (Tier 1 SSOT + 참조 정리) | 28 | doc-harness · CLAUDE.md · 8개 dev 스킬 · 5개 룰 |
| TASK1-2 | Phase 3 (신규 파일 도입) | 6 | 3개 신규 + CLAUDE.md · dev:commit.md · test-strategy.md |
| TASK1-3 | Phase 4 (분리 + 링크 갱신) | 14 | 4개 신규 (db 3 + ot 헤더) + 5개 외부 참조 + 4개 에이전트 |
| TASK1-4 | Phase 5 (검증) | 4 | 실행 검증만 |

또는 Phase 별 1:1 분할 (5분할). 단일 TASK1 으로 두는 것도 가능. 최종 분할은 `/dev:task rules_dedup_정리` 에서 사용자와 확정한다.
