---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# TASK1-3 검증 — 시나리오 5종 드라이런

## 관련 계획
- [계획안](../../../plan/20260423/analyze_phase_도입/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 커맨드/하네스](TASK1-1.md)
- [TASK1-2 룰/문서](TASK1-2.md)
- [TASK1-3 검증](TASK1-3.md)

## 작업 범위
TASK1-1·1-2 의 변경이 실제로 동작하는지 메타 워크플로우 시나리오 드라이런으로 검증한다.
코드 단위 테스트가 아닌 슬래시 커맨드 호출 결과 확인 형태이며, 검증 산출물(임시 ANALYZE 문서, fixture 등)은 RESULT 작성 시 정리/제거한다.

## Phase

### Phase 1: 샘플 슬러그 드라이런 (정상 흐름)

임의 검증용 슬러그(예: `dryrun_pump_basic_master`) 로 `/dev` 진입점 호출 시 `/dev:analyze` 가 첫 자동 전이 단계로 호출되고 ANALYZE1.md 가 정상 생성되는지 확인한다.

- [x] 샘플 슬러그로 `/dev` 호출 — Medium/Large 분류 시 첫 단계가 `/dev:analyze` 인지 확인
- [x] docs/analyze/20260423/dryrun_pump_basic_master/ANALYZE1.md 가 생성되고 PLAN1.md §구현 방향 §5 템플릿 구조를 따르는지 확인
- [x] 신규 용어 카탈로그 표에 사용자 요청문에서 추출한 용어가 분류되어 채워지는지 확인
- [x] 기존 사전과 충돌 항목이 자동 검출되는지 확인 (의도적으로 기존 약어 충돌 케이스 포함)

### Phase 2: PLAN 차단 검증

ANALYZE 의 룰 갱신 지시서 체크박스를 일부러 미체크 상태로 둔 채 `/dev:plan` 호출 시 차단 메시지가 출력되는지 확인한다.

- [x] 룰 갱신 지시서를 미체크 상태로 두고 `/dev:plan dryrun_pump_basic_master` 호출
- [x] "ANALYZE 의 룰 갱신을 먼저 완료하세요" 차단 메시지 확인
- [x] 체크박스를 모두 `- [x]` 로 완료 후 재호출 시 PLAN 진입 정상 확인

### Phase 3: Fix Cycle 분기 검증

직전 REVIEW 의 블로커 내용에 따라 ANALYZE 가 조건부로 재진입하는지 확인한다.

- [x] 케이스 A — REVIEW 블로커 내용에 "용어 충돌" 키워드 포함된 fixture 작성 후 `/dev` 재진입 → ANALYZE2.md 자동 작성 확인
- [x] 케이스 B — 일반 코드 블로커만 있는 fixture 로 `/dev` 재진입 → ANALYZE 스킵, PLAN2.md 직행 확인
- [x] 5개 도메인 정합성 키워드 (`용어`, `약어`, `중복 정의`, `네이밍 충돌`, `엔티티 통합`) 각각 매칭 검증

### Phase 4: REVIEW 자동 점검 검증

ANALYZE 룰 갱신 지시서에 명시된 파일을 일부러 수정하지 않은 상태로 `/dev:review` 를 호출했을 때 발견 사항이 자동 추가되는지 확인한다.

- [x] ANALYZE 의 룰 갱신 지시서에 `.claude/rules/naming.md` 명시 + 실제 git diff 에는 미수정 상태 fixture 작성
- [x] `/dev:review dryrun_pump_basic_master` 호출
- [x] REVIEW 의 발견 사항에 "룰 갱신 누락" 항목이 중간 우선순위로 자동 추가되는지 확인

### Phase 5: 기존 디렉토리 회귀 호환

기존 비공식 분석 산출물 디렉토리(`docs/analyze/20260422/pumpcontrol/`) 에 ANALYZE1.md 만 보강했을 때 정상 인식되는지 확인한다.

- [x] 기존 디렉토리에 ANALYZE1.md 만 신규 작성 (외부 산출물 .docx/.png 는 그대로 유지)
- [x] `/dev:analyze pumpcontrol` 호출 — "기존 ANALYZE 발견" 메시지 후 `status: approved` 인 경우 PLAN 으로 자동 전이 확인
- [x] 기존 산출물 .docx/.png 가 ANALYZE1.md 의 "외부 산출물" 섹션에서 참조 가능한지 확인

### Phase 6: 5인 팀 회의 라운드트립 검증

신규 용어와 신규 테이블이 동시에 등장하는 fixture 로 4개 에이전트가 안건 키워드 매핑대로 호출되는지 확인한다.

- [x] fixture 작성: 신규 용어(예: `pump_combination`) + 신규 DB 테이블(예: `pump_cmb_p`) + 운전 모드 변경(예: 반자동 → AI 자동) 동시 포함
- [x] `/dev:analyze` 호출 시 안건 분류가 `용어` / `DB` / `운전모드` 3개로 분리되는지 확인
- [x] 안건별 호출 에이전트가 매핑 표(PLAN1 §구현 방향 §3.2 4번)대로 정확히 호출되는지 확인
  - 용어 안건 → wtp-glossary-manager (필수) + wtp-domain-expert (선택)
  - DB 안건 → wtp-dba-reviewer (필수)
  - 운전 모드 안건 → wtp-domain-expert (필수)
- [x] ANALYZE.md 의 "## 회의록" 섹션에 안건별 라운드/답변 요약이 채워지는지 확인
- [x] 외부 산출물 정합성 안건은 4개 에이전트 모두 1라운드 호출되는지 확인

### Phase 7: 외부 산출물 사용자 개입 검증

`.docx` 파일만 있는 fixture 로 사용자 개입 안내가 정상 동작하는지 확인한다.

- [x] fixture 작성: docs/analyze/20260423/dryrun_docx_only/요구사항.docx 만 존재
- [x] `/dev:analyze dryrun_docx_only` 호출 → "사용자가 .md 변환 또는 paste" 안내 메시지 출력 확인
- [x] 사용자가 변환 .md 를 같은 디렉토리에 추가 → 자동 진행 재개 확인
- [x] `.png` 만 있는 fixture 는 사용자 개입 없이 Read 자동 로드되는지 확인

## 산출물
- [결과](../../../results/20260423/analyze_phase_도입/RESULT1.md)
