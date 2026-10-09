---
status: draft
created: 2026-04-20
updated: 2026-04-20
---
# 레거시 EMS/PMS → 신규 분류 체계 매핑 설계 리뷰

## 관련 결과
- [결과](../../../results/20260420/harness_도메인_DB_보강/RESULT2.md)

## 리뷰 범위

| 파일 | 유형 |
|------|------|
| `.claude/rules/legacy-mapping.md` | 신설 |
| `CLAUDE.md` | 수정 |
| `.claude/rules/naming.md` | 수정 |
| `.claude/agents/wtp-domain-expert.md` | 수정 |
| `.claude/agents/wtp-dba-reviewer.md` | 수정 |

Java 코드 변경 없음. 문서 정합성·내부 참조 일관성 중심으로 검토.

## 발견 사항

| 심각도 | 항목 | 위치 | 내용 |
|--------|------|------|------|
| **높음** | 섹션 번호 자기 참조 오류 | `.claude/rules/legacy-mapping.md` 서문·참조 표 (5곳) | 문서 내 `§4 매핑표`로 참조하고 있으나 실제 "도메인별 매핑표 seed"는 `## 3.`(§3). `§4`는 "안티패턴 해소 가이드"임. 에이전트 파일(§3 올바름)과 불일치 발생 |
| **높음** | CLAUDE.md "두 문서" 표현 불일치 | `CLAUDE.md` line 111, 120 | "아래 **두 문서**를 먼저 확인한다" / "**두 문서**를 참조한다" — 표에 3개 항목이 있어 불일치. "세 문서" 또는 "관련 문서"로 수정 필요 |
| 중간 | §3 seed 행 수 미명시 | `.claude/rules/legacy-mapping.md` §3 헤더 | PLAN2.md에서 "12행"으로 명시했으나 문서 헤더에 행 수가 없어 기대치 차이 발생. seed 행 수를 괄호로 표기하거나 PLAN2.md 표현과 통일 필요 |
| 중간 | PLAN 섹션 번호 off-by-one | `.claude/rules/legacy-mapping.md` 전반 | PLAN2.md 섹션 1(목적)이 서문으로 통합되어 실제 문서 섹션 번호가 PLAN 대비 1씩 앞당겨짐. 향후 혼용 방지를 위해 서문에 한 줄 비고 추가 권장 |
| 낮음 | 참조 문서 표 파일명 표기 불일치 | `.claude/rules/legacy-mapping.md` 참조 문서 관계 표 | 동일 `rules/` 디렉토리 파일인 `domain-glossary.md`, `db-patterns.md`가 `rules/` 접두어 포함 표기. 다른 항목(`dev/plan.md` 등)과 표기 방식 불일치 |

## 개선 제안

- **블로커 B1**: `legacy-mapping.md` 내 `§4 매핑표` 5곳을 `§3 매핑표`로 일괄 수정
- **블로커 B2**: `CLAUDE.md` line 111 "두 문서" → "세 문서", line 120 "두 문서를 참조" → "위 문서들을 참조"
- **권고 R1**: `§3` 헤더에 `(초기 seed: 13행)` 추가
- **권고 R2**: 서문에 `> 참고: PLAN2.md 섹션 1(목적)은 서문으로 통합되어 섹션 번호가 PLAN 대비 1씩 앞당겨집니다.` 한 줄 추가
- **참고 P1**: 참조 문서 표에서 `rules/domain-glossary.md`, `rules/db-patterns.md` → `domain-glossary.md`, `db-patterns.md`로 표기 통일

## 결론

- **블로커(높음): 2건** — 수정 사이클 필요
- **권고(중간): 2건**
- **참고(낮음): 1건**
