---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# 하네스에 분석(analyze) 단계 도입 — 도메인 사전 점진적 확립 (실행 결과)

## 관련 작업
- [계획안](../../../plan/20260423/analyze_phase_도입/PLAN1.md)
- [태스크 1-1 커맨드/하네스/에이전트](../../../tasks/20260423/analyze_phase_도입/TASK1-1.md)
- [태스크 1-2 룰/문서](../../../tasks/20260423/analyze_phase_도입/TASK1-2.md)
- [태스크 1-3 검증](../../../tasks/20260423/analyze_phase_도입/TASK1-3.md)

## 작업 요약

`/dev` 워크플로우에 **분석(analyze) 단계** 를 정식 도입했다. Medium/Large 작업의 PLAN 직전에서 신규 등장 용어·엔티티·DB 컬럼이 기존 도메인 사전·패턴과 정합성을 갖는지 5인 팀(오케스트레이터/DBA/Backend 개발자/도메인 전문가/용어 관리자)이 토픽 주도 방식으로 회의하여 ANALYZE 문서를 산출하고, 결정 사항을 룰 파일에 점진적으로 반영하는 흐름을 구축했다.

핵심 설계 결정은 PLAN1.md 의 §사용자 결정 사항 1차/2차 표에 기록됐으며, 본 작업은 그 결정에 따른 9개 파일(신규 4 + 수정 5) 의 변경으로 구성된다. 메타 워크플로우 변경 작업이라 gradle 모듈 코드는 미수정이며, 검증은 명세 정합성 trace 7개 시나리오로 수행했다.

## 변경 사항

### 신규 파일 (4개)

| 파일 | 역할 |
|------|------|
| `.claude/commands/dev/analyze.md` | `/dev:analyze {슬러그}` 신규 슬래시 커맨드 — 5인 팀 회의 오케스트레이션 본문 (전제조건 / 외부 산출물 수집 / 사전 분석 / 토픽 주도 회의 / 결론 종합 / 사용자 승인+자동 전이의 6단계) |
| `.claude/agents/wtp-backend-engineer.md` | Backend 페르소나 신규 — Spring Boot 4 / JPA / Querydsl 패턴, 계층 책임, SOLID, Lombok, feature-based 패키지 구조 정합성 검토 (200~400단어 단답형 강제) |
| `.claude/agents/wtp-glossary-manager.md` | 용어 관리자 페르소나 신규 — 신규 등장 용어 ↔ 기존 사전 4종 대조, 충돌 분류(신규/기존재사용/유사충돌/폐기·통합), 룰 갱신 지시서 초안 작성 |
| `.claude/rules/domain-abbreviations.md` | 도메인명 약어 단일 진실 소스 신규 — 마스터(user/auth) + 도입 예정(pump/raw/ctrl/alarm/diag/opt) + 다른 사전과의 관계 명시 + 폐기 이력 섹션 |

### 수정 파일 (5개)

| 파일 | 변경 |
|------|------|
| `.claude/commands/dev.md` | Fix Cycle 감지에 ANALYZE 조건부 재진입 분기 추가 / §5 워크플로우 안내 Medium·Large 흐름 첫 단계 `/dev:analyze` 추가 / §6 자동 전이 표 Medium·Large 첫 단계 `/dev:plan` → `/dev:analyze` 변경 / §7 단계 완료 표에 `/dev:analyze` 행 추가 |
| `.claude/commands/dev/plan.md` | 전제조건 §5 ANALYZE 게이트 추가 — ANALYZE 문서 존재 + status: approved + "## 룰 갱신 지시서" 섹션 모든 체크박스 `- [x]` 완료 검증 |
| `.claude/commands/dev/review.md` | "## ANALYZE-룰 정합성 점검 (자동)" 섹션 신규 — ANALYZE 의 룰 갱신 지시서 ↔ git diff 자동 대조, 누락 시 중간 우선순위로 발견 사항 자동 추가 |
| `.claude/rules/doc-harness.md` | (1) 디렉토리 트리 `analyze/` 추가 (2) 네이밍 규칙 표에 `analyze` 행 + ANALYZE 첨부물 행 (3) Fix Cycle 다이어그램에 ANALYZE 조건부 재진입 분기 + 케이스 A/B 예시 (4) ANALYZE{n}.md 템플릿 섹션 신규 (회의록·PLAN 전달 사항 포함) (5) 체크박스 경로 규칙에 ANALYZE 룰 갱신 지시서 행 (6) 상호 참조 규칙에 ANALYZE{n} 헤더 양식 |
| `CLAUDE.md` | 규칙 문서 인덱스 표에 `domain-abbreviations.md` 행 추가 (naming.md 다음) + 문서 하네스 행에 `ANALYZE` 추가 / 작업 흐름 문단에 분석 단계 + 5인 팀 회의 설명 추가 |

### git diff --stat 요약

```
.claude/commands/dev.md          | +10
.claude/commands/dev/plan.md     |  +8
.claude/commands/dev/review.md   | +18
.claude/rules/doc-harness.md     | +122
CLAUDE.md                        |  +7
신규: 4개 파일 (commands/dev/analyze.md, agents/wtp-backend-engineer.md, agents/wtp-glossary-manager.md, rules/domain-abbreviations.md)
산출물: docs/{plan, tasks, results}/20260423/analyze_phase_도입/
```

## 테스트 결과

본 작업은 메타 워크플로우 변경(`.claude/`/`CLAUDE.md`/`docs/` 만 수정) 으로 gradle 모듈 코드는 미수정이다. PLAN1.md §테스트 전략 결정에 따라 단위 테스트가 아닌 **명세 정합성 trace 기반 시나리오 검증** 7종으로 수행했다.

| 시나리오 | 기대 동작 | 명세 위치 | 결과 |
|---------|----------|----------|------|
| Phase 1 정상 흐름 | `/dev → /dev:analyze` 자동 전이, ANALYZE1 생성, 카탈로그 채움, 충돌 검출 | `dev.md §6 자동 전이 표`, `dev/analyze.md §1 §3 §5` | ✅ |
| Phase 2 PLAN 차단 | 룰 갱신 지시서 미체크 시 차단 메시지 + 체크 후 정상 진입 | `dev/plan.md §전제조건 §5 ANALYZE 게이트` | ✅ |
| Phase 3 Fix Cycle 분기 | 도메인 정합성 키워드 매칭 → ANALYZE{N+1}, 미포함 → PLAN{N+1} 직행 | `dev.md Fix Cycle 감지`, `dev/analyze.md §1.3`, `doc-harness.md ANALYZE 조건부 재진입` | ✅ |
| Phase 4 REVIEW 자동 점검 | 룰 갱신 지시서 ↔ git diff 대조 후 누락 시 발견 사항 자동 추가 | `dev/review.md ANALYZE-룰 정합성 점검 (자동)` | ✅ |
| Phase 5 기존 디렉토리 회귀 호환 | 기존 ANALYZE 발견 메시지 + 외부 산출물 자동 참조 | `dev/analyze.md §1.4`, `dev/analyze.md §2 외부 산출물 수집` | ✅ |
| Phase 6 5인 팀 회의 라운드트립 | 안건 분류, 매핑대로 호출, 회의록 채움, 외부 산출물은 4개 모두 호출 | `dev/analyze.md §3 §4.1 §4.2 §4.3 §5` | ✅ |
| Phase 7 외부 산출물 사용자 개입 | `.docx` 안내 메시지, `.md` 변환 후 자동 재개, `.png` 자동 로드 | `dev/analyze.md §2 외부 산출물 수집 표 + 안내 메시지 예시` | ✅ |

**검증 결과:** 7개 시나리오의 모든 기대 동작이 변경된 명세 파일에 커버됨. 명세 누락은 발견되지 않아 추가 .md 보강 없이 종결.

**시스템 인식 검증:** Skill 목록에 `dev:analyze: 1.5단계: 도메인 분석 (ANALYZE 문서 작성, 5인 팀 회의 오케스트레이션)` 가 자동 등록된 것이 즉시 확인됨 — 슬래시 커맨드 등록은 정상 동작.

## 비고

### 계획 대비 차이점
- 없음. PLAN1.md §구현 방향의 7개 파일 모두 명세대로 신규/수정. TASK 분할(1-1·1-2·1-3) 도 PLAN §예상 산출물 그대로.

### 의도적 비도입 항목 (PLAN §제외 사항 그대로)
- pre-commit 훅 추가 — 사용자 결정대로 미도입 (PLAN/REVIEW 게이트로 충분)
- 기존 룰 파일 본문 대규모 리팩터링 — 센서 코드 표를 domain-abbreviations.md 로 이동하는 등은 별도 작업으로 분리
- 도메인 약어의 코드 강제력 (lint, ArchUnit) — 별도 작업
- `wtp-domain-expert.md` 에이전트 정의 변경 — 다음 사이클로 분리

### 후속 작업 권장 항목
- **첫 실제 ANALYZE 호출** 작업에서 5인 팀 회의 흐름이 의도대로 동작하는지 실증. 가장 가까운 후보는 기존 `docs/analyze/20260422/pumpcontrol/` (요구사항·도메인 모델링·시퀀스/클래스 다이어그램 첨부 존재) 의 정식 ANALYZE1.md 보강 작업. Phase 5 (기존 디렉토리 회귀 호환) 시나리오의 실제 검증을 겸함.
- 도메인명 약어 사전의 "도입 예정" 섹션 항목들 (pump/raw/ctrl/alarm/diag/opt) 이 실제 코드 도입 시 ANALYZE 단계에서 마스터 섹션으로 승격 필요.
- 기존 룰 파일에 흩어진 약어 (센서 코드 FRI/PRI/LEI/PWI/RMS, 지자체 코드 등) 를 점진적으로 domain-abbreviations.md 의 인덱스 관계에 따라 정리할지 별도 작업 사이클에서 판단.

### 자기 참조 회피
본 작업 자체는 분석 단계가 존재하지 않는 시점에 분석 단계를 만드는 메타 작업이므로 `/dev:analyze` 미적용. 다음 작업부터 `/dev:analyze` 가 정상 동작.
