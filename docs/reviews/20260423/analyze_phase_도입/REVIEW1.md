---
status: draft
created: 2026-04-23
updated: 2026-04-23
---
# 하네스에 분석(analyze) 단계 도입 — 코드 리뷰

## 관련 결과
- [결과](../../../results/20260423/analyze_phase_도입/RESULT1.md)

## 리뷰 범위

본 리뷰는 `feature-dev:code-reviewer` 서브에이전트 자동 리뷰 기반이며, 메타 워크플로우 변경 작업이라 일반 코드 체크리스트(Lombok·Swagger·ErrorCode 등) 대신 다음 7개 관점으로 수행했다:

1. 자동 전이 그래프 닫힘 (`/dev` → `/dev:analyze` → ... → `/dev:commit`)
2. PLAN 게이트 정합성 (analyze.md ↔ plan.md)
3. ANALYZE 템플릿 단일 진실 소스 (doc-harness.md ↔ PLAN1 §5)
4. 신규 에이전트 정의 형식 일관성 (기존 wtp-* 와 비교)
5. 약어 사전 인덱스 관계 (domain-abbreviations.md ↔ naming.md / ot-integration.md / multi-tenant.md)
6. CLAUDE.md 인덱스 정확성
7. 체크박스 경로 규칙 적용 (TASK / ANALYZE 룰 갱신 지시서)

검토 파일 수: 14개 (신규 4 + 수정 5 + 컨텍스트 5)

**ANALYZE-룰 정합성 점검 (자동, dev/review.md 신규 섹션):** 본 작업 슬러그(`analyze_phase_도입`) 자체는 ANALYZE 단계가 없는 메타 작업(PLAN1 §8 자기 참조 회피)이므로 본 점검은 해당 없음으로 스킵.

## 발견 사항

| 심각도 | 항목 | 위치 | 내용 | 권장 조치 |
|--------|------|------|------|----------|
| **높음 (블로커)** | PLAN 게이트의 Small 작업 오진단 가능성 | `.claude/commands/dev/plan.md` §전제조건 §5 | §5 ANALYZE 게이트가 "Medium/Large 작업의 필수 전제조건"이라고 선언하지만 plan.md 자체에 작업 규모를 판별하는 로직이 없다. 사용자가 `/dev:plan` 을 직접 호출하거나 Fix Cycle 에서 Small 로 재분류된 케이스에서 ANALYZE 미존재로 오차단. 정상 자동 전이(`/dev` → `/dev:analyze` → `/dev:plan`) 에서는 발생 안 하지만 수동 호출/엣지 케이스에서 발생. | `plan.md §5` 첫 줄에 분기 추가: "`docs/analyze/` 하위에 슬러그 일치 디렉토리가 없으면 Small 작업으로 간주하고 본 게이트 스킵. (정상 워크플로우는 `/dev:analyze` 가 항상 선행되므로 디렉토리는 존재함)" |
| **중간 (권고)** | Fix Cycle 진입 시 plan.md 의 ANALYZE 키워드 분기 미수행 | `.claude/commands/dev/plan.md` §3, §5 | dev.md §Fix Cycle 감지는 ANALYZE 조건부 재진입 키워드 매칭 로직을 포함하지만, 사용자가 `/dev` 우회로 `/dev:plan` 을 직접 호출 시 plan.md 가 직접 키워드 체크를 하지 않는다. 키워드 미포함 Fix Cycle 에서 ANALYZE 미존재로 오차단 가능. | `plan.md §5` 앞에 "Fix Cycle 모드이고 직전 REVIEW 블로커에 도메인 정합성 키워드(`용어`/`약어`/`중복 정의`/`네이밍 충돌`/`엔티티 통합`) 가 없으면 ANALYZE 게이트 스킵" 분기 추가. 위 블로커 1 fix 와 같은 sweep 으로 처리 권장. |
| **중간 (권고)** | doc-harness.md 디렉토리 네이밍 예시 블록에 `analyze/` 누락 | `.claude/rules/doc-harness.md` §디렉토리 및 파일 네이밍 규칙 | 디렉토리 트리 다이어그램(상단)과 네이밍 규칙 표에는 `analyze/` 가 추가되었으나, 표 다음의 예시 경로 4줄(`docs/plan/...`, `docs/tasks/...`, `docs/results/...`, `docs/reviews/...`) 에는 `docs/analyze/...` 예시가 빠져 있음. 참조 시 혼란. | 예시 블록 맨 앞에 `docs/analyze/20260416/legacy_재개발/ANALYZE1.md` 한 줄 추가. |
| **중간 (권고)** | ANALYZE 템플릿의 산출물 링크 플레이스홀더 표기 불일치 | `.claude/rules/doc-harness.md` ANALYZE{n}.md 템플릿 §산출물 | ANALYZE 템플릿 산출물 링크가 `../../../plan/YYYYMMDD/{슬러그}/PLAN1.md` 형식 (`{슬러그}`) 인 반면, 같은 파일 내 PLAN/TASK/RESULT/REVIEW 템플릿은 `{작업목적}` 표기 사용. 동일 문서 내 플레이스홀더 혼재. | ANALYZE 템플릿의 산출물 링크를 `../../../plan/YYYYMMDD/작업목적/PLAN1.md` 로 통일. |
| **낮음 (참고)** | CLAUDE.md 작업 흐름 단계 나열이 Large 흐름 기준 | `CLAUDE.md` 작업 흐름 문단 | "요청→분석→계획→분해→구현→결과→리뷰→커밋" 나열이 Large 기준이라 Small 작업이 본 단계를 모두 거치는 것처럼 오해 가능. 다음 문장에서 "Small 면제" 명시되어 있어 컨텍스트는 충분. | 참고 수준. 강제 수정 불필요. |
| **낮음 (참고)** | domain-abbreviations.md "1차 정의 파일" 표현이 일부 과장 | `.claude/rules/domain-abbreviations.md` §다른 약어 사전과의 관계 | 센서 코드(FRI/PRI/LEI/PWI/RMS) 가 ot-integration.md 의 §3 품질 관리 섹션에 내재된 표인데, 이를 "1차 정의 파일" 로 표현. 참조 방향은 맞으나 표현 강도가 다소 강함. | 참고 수준. "관련 정의 포함 파일" 등으로 완화 가능. |

## 후속 작업 트래킹 (이번 사이클 deferred — 블로커 아님)

| 항목 | 근거 | 처리 시점 |
|------|------|----------|
| 기존 에이전트 2개(`wtp-domain-expert.md`, `wtp-dba-reviewer.md`) description/본문에 "단답형 200~400단어" 강제 문구 추가 | PLAN §제외 사항에 "wtp-domain-expert 에이전트 정의 변경 — 다음 사이클로 분리" 명시. analyze.md §4.2 가 호출 시 프롬프트에 직접 삽입하므로 런타임 동작은 보장됨. 비대칭 명세 상태이지만 의도적 deferred. | 다음 사이클의 별도 작업 (예: `/dev 에이전트_단답형_보완`) |
| 기존 룰 파일 본문 대규모 리팩터링 (센서 코드 표를 domain-abbreviations.md 로 이동 등) | PLAN §제외 사항 명시 | 별도 사이클 |
| pre-commit 훅 추가 (룰 갱신 강제) | PLAN §사용자 결정 사항 — 본 사이클 미도입 결정 | 별도 사이클 (필요 시) |
| 도메인 약어의 코드 강제력 (lint, ArchUnit) | PLAN §제외 사항 | 별도 사이클 |
| `domain-abbreviations.md` 도입 예정 약어(pump/raw/ctrl/alarm/diag/opt) 의 마스터 섹션 승격 | 실제 코드 도입 시점 | 각 도메인 도입 ANALYZE 단계에서 |

## 통과 항목

- 자동 전이 그래프 닫힘: `/dev` → `/dev:analyze` → `/dev:plan` → `/dev:task` → `/dev:impl` → (Large) `/dev:result` → `/dev:review` → `/dev:commit` 일관 명시
- Small 흐름 보호: `/dev:analyze` 미적용 (`dev.md §6` Small → `/dev:impl`)
- Fix Cycle 키워드 매칭: 5개 키워드(`용어`/`약어`/`중복 정의`/`네이밍 충돌`/`엔티티 통합`) 가 dev.md, dev/analyze.md, doc-harness.md 세 곳에서 동일하게 명시
- ANALYZE 게이트 ↔ analyze.md §6 승인 조건 일치
- ANALYZE 템플릿이 doc-harness.md 와 PLAN1 §5 두 곳에서 글자 수준 일치
- 신규 에이전트 2개 형식 (frontmatter + 역할/검토 항목/출력 형식) 이 기존 wtp-* 와 일관
- 안건 키워드 매핑 표(analyze.md §4.1) 의 4개 에이전트가 모두 실제 파일로 존재
- domain-abbreviations.md 인덱스 관계 방향 정확 — 약어 충돌 없음
- CLAUDE.md 인덱스 도메인 약어 사전 행 참조 시점 적합
- 체크박스 경로 규칙 룰 파일 경로 행 추가 + PLAN1 §5 템플릿 예시 준수
- REVIEW 자동 점검 섹션 추가 정상

## 개선 제안

1. **블로커 1 + 권고 2 (Fix Cycle ANALYZE 분기)** 를 한 번의 plan.md 수정으로 통합 처리:
   plan.md §5 의 ANALYZE 게이트 앞에 다음 두 분기 추가
   - 분기 A (Small 보호): `docs/analyze/{슬러그}` 가 없으면 Small 로 간주, 게이트 스킵
   - 분기 B (Fix Cycle 키워드 미포함): Fix Cycle 모드이고 직전 REVIEW 블로커에 도메인 정합성 키워드 없으면 게이트 스킵
2. **doc-harness.md 표현 일관성 sweep** (권고 3 + 권고 4):
   - 디렉토리 예시 블록에 `analyze/` 추가
   - ANALYZE 템플릿 산출물 링크의 `{슬러그}` → `작업목적` 통일
3. **참고 사항 (낮음 2건)** 은 본 사이클에서 별도 처리하지 않음. 차기 사이클 또는 별도 PR 에서 다듬기 가능.

## 결론

- **블로커: 1건**
- 권고: 3건 (그 중 1건은 블로커 1 의 자매 fix 로 한 sweep 처리 권장)
- 참고: 2건 (본 사이클 미반영)
- 후속 작업 트래킹: 5건 (모두 PLAN §제외 사항으로 의도적 deferred)

**다음 단계 안내:** 블로커 1건이 발견되어 fix cycle 진입이 권장됩니다.

```
/dev analyze_phase_도입
```

`/dev` 진입점이 본 REVIEW1 의 블로커 항목을 자동 감지하여 Fix Cycle (PLAN2 → TASK2 → impl → RESULT2 → REVIEW2) 로 자동 전이합니다. 다만 본 블로커는 plan.md 단일 파일의 §5 분기 추가만으로 해소되는 매우 작은 변경이므로, 사용자 판단에 따라 다음 두 옵션 중 선택할 수 있습니다:

| 옵션 | 동작 |
|------|------|
| **(a) 정식 Fix Cycle** | `/dev analyze_phase_도입` → PLAN2/TASK2/RESULT2/REVIEW2 정식 사이클 (명세 준수, 변경 추적 명확) |
| **(b) 인라인 fix + REVIEW1 재승인** | plan.md §5 분기 한 줄 추가 + 권고 3·4 동시 sweep + 본 REVIEW1 의 status 를 fix 후 approved 로 변경 (효율적이나 fix cycle 트레이서빌리티 약화) |
