---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# TASK1-1 커맨드/하네스/에이전트 — `/dev:analyze` 신규(5인 팀 회의 포함) + dev 진입점·plan·review 수정 + 신규 에이전트 2개

## 관련 계획
- [계획안](../../../plan/20260423/analyze_phase_도입/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 커맨드/하네스](TASK1-1.md)
- [TASK1-2 룰/문서](TASK1-2.md)
- [TASK1-3 검증](TASK1-3.md)

## 작업 범위
이 묶음은 슬래시 커맨드 정의, 5인 팀 회의 오케스트레이션 로직, 신규 에이전트 정의 2개에 한정한다.
룰 파일과 산출물 디렉토리 컨벤션 변경은 TASK1-2 에서, 시나리오 드라이런 검증은 TASK1-3 에서 처리한다.

## Phase

### Phase 1: 신규 에이전트 정의 작성 (회의 멤버 인프라)

PLAN1.md §구현 방향 §3.1 팀 구성에 따라 신규 페르소나 2개를 정의한다.
기존 `wtp-domain-expert.md`, `wtp-dba-reviewer.md` 의 형식·디렉티브 구조를 참고해 일관성 유지.

- [x] `.claude/agents/wtp-backend-engineer.md` 신규 작성
  - description: Spring Boot 4 / JPA / Querydsl / MyBatis 패턴, 계층 책임(Controller·Service·Repository), SOLID 원칙, Lombok 활용, 패키지 구조(feature-based) 정합성 검토
  - 검토 기준 참조: `.claude/rules/api-patterns.md`, `.claude/rules/entity-patterns.md`, `.claude/rules/exception-patterns.md`, `.claude/rules/naming.md`
  - 출력 형식: 안건당 200~400단어 단답
- [x] `.claude/agents/wtp-glossary-manager.md` 신규 작성
  - description: 신규 등장 용어/약어 ↔ 기존 사전(`.claude/rules/domain-abbreviations.md`, `naming.md`, `ot-integration.md`, `multi-tenant.md`) 대조, 충돌 분류(신규/기존재사용/유사충돌/폐기·통합), 룰 갱신 지시서 초안 작성
  - 검토 기준 참조: `.claude/rules/domain-abbreviations.md`(TASK1-2 에서 신규 작성됨), `naming.md`
  - 출력 형식: 충돌 판정 표 + 룰 갱신 지시서 체크박스 초안

### Phase 2: `/dev:analyze` 신규 커맨드 작성 (5인 팀 오케스트레이션 포함)

PLAN1.md §구현 방향 §3.2 동작 절차 6단계를 모두 포함해 신규 커맨드 정의를 작성한다.

- [x] `.claude/commands/dev/analyze.md` 신규 작성 — 전제조건 검증 블록
  - 입력 `$ARGUMENTS` (슬러그) 검증 (비면 중단)
  - Fix Cycle 분기: 직전 REVIEW 블로커에 도메인 정합성 키워드(`용어`/`약어`/`중복 정의`/`네이밍 충돌`/`엔티티 통합`) 포함 여부 판정
  - 키워드 미포함 → ANALYZE 스킵 후 즉시 `/dev:plan` 전이
  - 키워드 포함 또는 1차 사이클 → ANALYZE{N}.md 생성·업데이트로 진입
  - 기존 ANALYZE 가 `approved` 면 "이미 승인됨, `/dev:plan` 진행" 안내
- [x] `.claude/commands/dev/analyze.md` 외부 산출물 수집 블록
  - `docs/analyze/{YYYYMMDD}/{슬러그}/` 디렉토리 스캔
  - `.png`/`.jpg` → Read 도구 자동 호출
  - `.docx`/`.xlsx`/`.pdf` → 사용자 개입 안내 메시지 출력 후 대기 (사전 변환 .md 있으면 자동 진행)
  - `.md`/`.txt` → Read 자동 로드
- [x] `.claude/commands/dev/analyze.md` 사전 분석 블록
  - 사용자 요청문 + 외부 산출물에서 명사구·약어 후보 추출
  - 기존 사전 1차 대조 (`.claude/rules/domain-abbreviations.md`, `naming.md`, `ot-integration.md`, `multi-tenant.md`, `common/src/main/java/com/mo/swtp/` 패키지 트리, 기존 엔티티 클래스명 grep)
  - 안건 후보 목록 생성
- [x] `.claude/commands/dev/analyze.md` 회의 진행 블록 — 토픽 주도 라운드 알고리즘
  - 안건 키워드 → 호출 에이전트 매핑 표 (PLAN1 §구현 방향 §3.2 4번 그대로 옮김)
  - Round 1: 판단 권한자 Agent 호출 (단답형 200~400단어 강제 프롬프트)
  - Round 2: 이견·블로커 발견 시만 추가 호출
  - 에이전트 간 직접 대화 없음, 항상 오케스트레이터(메인 Claude)가 중계
  - 외부 산출물 정합성 안건은 4개 에이전트 모두 1라운드 호출
- [x] `.claude/commands/dev/analyze.md` 결론 종합 + ANALYZE{N}.md 작성 블록
  - 회의록 섹션에 안건별 라운드/답변 요약
  - 신규 용어 카탈로그 표 채우기 (분류값: 신규/기존재사용/유사충돌/폐기·통합)
  - 룰 갱신 지시서 작성 (체크박스, 룰 파일 전체 경로)
  - PLAN 으로 전달할 결정 사항 정리
- [x] `.claude/commands/dev/analyze.md` 사용자 승인 블록
  - `status: approved` + 룰 갱신 지시서 모든 체크박스 `- [x]` 확인
  - 승인 후 → `/dev:plan {슬러그}` 자동 전이

### Phase 3: `dev.md` 진입점 변경

- [x] `.claude/commands/dev.md` 의 "5. 워크플로우 안내 출력" 섹션의 Medium/Large 흐름 코드블록에 `/dev:analyze {슬러그}` 행 추가 (Small 흐름은 변경 없음)
- [x] `.claude/commands/dev.md` 의 "6. 자동 전이 — 첫 번째 단계 즉시 실행" 표에서 Medium/Large 의 자동 전이 대상을 `/dev:plan` → `/dev:analyze` 로 변경 (Small 은 `/dev:impl` 유지)
- [x] `.claude/commands/dev.md` 의 "7. 단계 완료 시 자동 전이" 표에 `/dev:analyze` 행 추가 (승인 필요: `status: approved` + 룰 갱신 지시서 완료 / 완료 후 → `/dev:plan` 자동 실행)
- [x] `.claude/commands/dev.md` 의 "Fix Cycle 감지" 섹션에 "ANALYZE 조건부 재진입" 분기 보강 (도메인 정합성 키워드 판정 결과에 따라 ANALYZE{N+1} 작성 또는 PLAN{N+1} 직행)

### Phase 4: 기존 단계 커맨드 수정

- [x] `.claude/commands/dev/plan.md` 의 전제조건 검증 섹션에 ANALYZE 검증 블록 추가
  - Medium/Large 작업에서 `docs/analyze/{날짜}/{슬러그}/ANALYZE{N}.md` 존재 확인
  - `status: approved` 여야 함
  - "## 룰 갱신 지시서" 섹션의 모든 체크박스가 `- [x]` 로 완료
  - 미완료 시 차단 메시지 출력 후 중단
- [x] `.claude/commands/dev/review.md` 의 자동 점검 섹션에 ANALYZE-룰 정합성 점검 추가
  - ANALYZE.md "## 룰 갱신 지시서" 에 명시된 룰 파일 목록 추출
  - `git diff` 로 작업 브랜치에서 실제 수정 여부 대조
  - 명시됐으나 미수정인 파일이 있으면 REVIEW 의 발견 사항(중간 우선순위) 으로 자동 추가

## 산출물
- [결과](../../../results/20260423/analyze_phase_도입/RESULT1.md)
