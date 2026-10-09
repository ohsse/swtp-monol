---
status: completed
created: 2026-04-29
updated: 2026-04-29
---
# LLM 코딩 디시플린 룰 도입 — 결과

## 관련 작업
- [계획안](../../../plan/20260429/llm_coding_discipline/PLAN1.md)
- [태스크](../../../tasks/20260429/llm_coding_discipline/TASK1.md)
- [도메인 분석](../../../analyze/20260429/llm_coding_discipline/ANALYZE1.md)

## 작업 요약

LLM 코딩 가이드라인 4원칙 (구현 전 사고 / 단순성 우선 / 정밀한 수정 / 목표 중심 실행) 을 ROOT 신규 룰 1개 + backend 룰·템플릿·에이전트 7개 갱신으로 분산 도입. ANALYZE1 에서 발견된 블로커 (정량 기준 vs 정수장 안전 도메인 패턴 충돌) 는 `coding-discipline.md` §2.5 면책 조항 신설로 해소.

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 5 |
| 체크박스 수 | 12 |
| 분할 여부 | N |
| 분할 근거 | — (분할 임계 Phase 10 / 체크박스 60 모두 미달, 단일 TASK1 진행) |

## 변경 사항

### 의도된 변경

| 파일 | 위치 | 변경 종류 | 변경 라인 | 내용 |
|------|------|---------|---------|------|
| `coding-discipline.md` | swtp/.claude/rules/ (ROOT 신규) | 신규 | +176 | LLM 행동 규율 4원칙 §1~§5 + §2.5 정량 기준 면책 조항 (정수장 안전 도메인 / DB 쿼리 빌더·튜닝) |
| `templates.md` | backend/.claude/rules/process/doc-harness/ | 수정 | +53 / -5 | ANALYZE/PLAN "## 가정 및 미해결 질문" 섹션 추가, PLAN "## 테스트 전략" → "## 성공 기준 (검증 가능 형태)", TASK 체크박스 검증 형식 안내, RESULT "### 의도된 변경"/"### 계획 외 변경" 하위 절, REVIEW "## 발견 사항" 6개 카테고리 (복잡도 과잉 포함) |
| `checkbox-rules.md` | backend/.claude/rules/process/doc-harness/ | 수정 | +29 | "## 검증 기준 표기 형식" 절 신설, 검증 영역 백틱 금지 명시 |
| `hooks-guide.md` | backend/.claude/rules/process/ | 수정 | +41 | §7 "본 시점 자동 차단 훅 신설 보류" 절 신설 + §8 "신규 훅 추가 절차" (기존 §7 번호 이동) |
| `test-strategy.md` | backend/.claude/rules/ | 수정 | +12 | §1 단위 테스트 절에 "버그 수정 첫 체크박스 = 재현 테스트 RED 확인 의무" 추가 |
| `wtp-backend-engineer.md` | backend/.claude/agents/ | 수정 | +5 (목록 4개 추가) | 점검 항목 7~10 추가 (단순성 위반·TASK 외 파일 변경·체크박스 검증 누락·데드 코드 직접 삭제) + 필수 파일 읽기 5번 ROOT `coding-discipline.md` 추가 |
| `wtp-domain-expert.md` | backend/.claude/agents/ | 수정 | +11 | 검토 항목 4번 추가 (가정 섹션 도메인 4영역 점검 — 알람·인터록·운전 모드·이력 기록 의무) |
| `CLAUDE.md` | backend/ | 수정 | +1 | §규칙 문서 인덱스 표에 ROOT `coding-discipline.md` 행 추가 |

### 계획 외 변경

> ROOT [`coding-discipline.md` §3](../../../../.claude/rules/coding-discipline.md) 적용. 본 작업 중 발견된 PLAN 분석 오류를 의도적으로 정정.

| 변경 | 내용 | 의도 / 우연 | 처리 |
|------|------|-----------|------|
| 에이전트 파일 위치 정정 | PLAN1 / ANALYZE1 에서 `swtp/.claude/agents/wtp-*.md` (ROOT) 로 기재했으나 실제로는 `swtp/backend/.claude/agents/wtp-*.md` (backend) 에 위치. Glob 출력의 cwd 상대경로 표시를 ROOT 위치로 오해. | **의도** (필수 정정) | TASK1 체크박스 경로를 정정 후 backend 위치 파일에 변경 적용. ai-server 는 자체 카피를 별도 보유 — ROOT 공유 자산이 아님이 본 작업 중 확인됨 |

> 이 외 TASK 체크박스 외 파일 변경은 없음 (`git diff --name-only HEAD` 결과 8개 파일 모두 TASK1 체크박스에 명시).

## 테스트 결과

### 빌드 검증

```
> Task :api:build
> Task :common:build
> Task :scheduler:build
BUILD SUCCESSFUL in 1m 13s
20 actionable tasks: 10 executed, 10 up-to-date
```

- `./gradlew.bat build` BUILD SUCCESSFUL
- 기존 16개 테스트 모두 PASS (api / common / scheduler 모듈 test 통과)
- 룰 파일 변경이라 테스트 회귀 0건 — 사실상 보장됨

### DB 영향 확인

- DB 스키마·인덱스·파티션·시계열 보존 정책 변경 0건 (룰 파일 전용 작업)
- `check-ddl-column-comment.sh` 훅 트리거 대상 (`db/init/`·`db/migration/`) 변경 없음 → 자동 차단 미발동 확인

### 자기참조 검증 (dogfooding)

본 작업으로 도입한 새 패턴이 본 작업의 산출물에 적용된 정도:

| 패턴 | 적용 위치 | 결과 |
|------|---------|------|
| ANALYZE "## 가정 및 미해결 질문" | ANALYZE1.md | ✅ 4건 기재 |
| PLAN "## 가정 및 미해결 질문" | PLAN1.md | ✅ 5건 기재 |
| PLAN "## 성공 기준 (검증 가능 형태)" | PLAN1.md | ✅ 5개 기준 모두 검증 명령 명시 |
| TASK 체크박스 `→ 검증:` | TASK1.md | ✅ 12개 모두 검증 명시 |
| TASK 검증 영역 백틱 금지 | TASK1.md | ✅ 위반 0건 |
| RESULT "### 계획 외 변경" 절 | RESULT1.md (본 문서) | ✅ 1건 명시 |

## 비고

### 후속 작업 후보

| 후보 | 우선순위 | 설명 |
|------|--------|------|
| ai-server `wtp-domain-expert` 카피 동기화 | 중 | `swtp/ai-server/.claude/agents/wtp-domain-expert.md` 가 별도 카피로 유지 중. backend 본체 갱신 후 동기화 시점 결정 필요 (별도 ANALYZE) |
| 신규 자동 차단 훅 도입 검토 | 낮음 | hooks-guide.md §7 의 trigger 조건 (REVIEW 누락 사례 3건 누적 등) 충족 시 별도 ANALYZE 진입 |
| ai-server 의 §5 시스템별 적용 도입 | 중 | Python 환경의 50줄·3단 기준 적용 가능성 별도 검증 필요. ai-server 자체 룰 디렉토리 (`swtp/ai-server/.claude/rules/`) 도입 시 함께 처리 |
| 에이전트 위치 정합성 (ROOT vs backend) | 낮음 | 본 작업 중 확인 — Claude Code 가 ROOT `.claude/agents/` 자동 로드하지 않는 가정 (검증 필요). 모노레포 공유 에이전트 패턴 도입 안건은 별도 ANALYZE |

### 발견된 데드 코드 (보고만, 직접 삭제 안 함)

본 작업 중 발견된 기존 데드 코드 없음 — ROOT [`coding-discipline.md` §3.1`] 정책에 따라 발견 시 보고 의무.

### 참고 사항

- 본 작업의 코드 회귀 영향 0건 — `wtp-backend-engineer` ANALYZE1 안건 3 결과대로 (50줄 초과·DTO 상속·추상화 3단 초과 모두 0건). Grandfather clause 자연 충족
- 룰 변경이 다음 작업부터 즉시 적용된다 — REVIEW 단계의 자동 점검은 본 작업 직후 다음 `/dev:analyze` 부터 새 점검 항목이 작동
