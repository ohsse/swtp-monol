---
status: approved
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 — 코드 리뷰

## 관련 결과
- [결과](../../../results/20260424/rules_dedup_정리/RESULT1.md)

## 리뷰 범위

본 작업은 메타 룰 재정렬 (`.claude/` 룰 14개 + `CLAUDE.md` + dev 스킬 8개 + 사용자 정의 에이전트 4개) 로 **코드 변경 0건** 이다. 따라서 일반 코드 리뷰 체크리스트(Lombok / Setter / 생성자 주입 / Swagger / RestApiException / CommonResponseDto 등) 는 모두 NA 다.

리뷰는 다음 메타 정합성 8영역으로 수행했다:
1. SSOT 일관성 (Fix Cycle / 자동 전이 / PLAN·TASK 템플릿 / 도메인 용어 / db-patterns 인덱스 / 신규 룰 6개)
2. 신규 룰 6개 본문 의도 부합 (commit-convention, hooks-guide, db 분리 3개, e2e-roadmap)
3. db-patterns.md 인덱스 전환 후 자식 매핑 정확성
4. ot-integration.md 헤더 태그 5개 + 참조표 1문장 적용
5. db-patterns 분리에 따른 외부 참조 정합성 (5건 명시 + 파생 2곳)
6. CLAUDE.md 인덱스 표 등록 / 위임 구조 일관성
7. **ANALYZE-룰 정합성 점검 수행** — `docs/analyze/20260424/rules_dedup_정리/ANALYZE1.md` §"룰 갱신 지시서" 24개 항목 vs 실제 git 변경 대조
8. TASK 분할 적정성 (Phase 5 / 체크박스 52 / 분할 Y, 분할 기준 대비)

자체 검증은 [TASK1-4 검증 결과](../../../tasks/20260424/rules_dedup_정리/TASK1-4.md) (링크 무결성 0건 깨짐 / 빌드 exit 0 / 훅 동작 OK / SSOT 6영역 OK) 를 사전 수행했고, 이를 `feature-dev:code-reviewer` 서브에이전트가 독립적으로 재검증했다.

## 발견 사항

| 심각도 | 항목 | 위치 | 내용 | 처리 |
|--------|------|------|------|------|
| 중간 | hooks-guide SSOT 미정착 | `CLAUDE.md` §작업 흐름 line 48 | `hooks-guide.md` 신규 등록에도 "동작 상세·우회 방법은 `.claude/hooks/check-task-unstage.sh` 상단 주석을 참조" 잔존. T2-J 핵심 목적과 직접 충돌 | **본 사이클 즉시 보완** — `hooks-guide.md` 1줄 링크로 갱신 |
| 중간 | db-patterns 인덱스 전환 미반영 | `CLAUDE.md` §규칙 문서 인덱스 line 86 "필수" 각주 | `db-patterns.md` 가 인덱스 파일로 전환된 사실이 "필수" 각주에 미반영. 신규 설계자 혼동 가능 | **본 사이클 즉시 보완** — "(분리 인덱스 — 자식 3개 진입점)" 라벨 + 자식 파일 명시 추가 |
| 낮음 | TASK1-3 체크박스 "5곳" vs 실측 | `TASK1-3.md` T2-F 외부 참조 행 | TASK 명시 "5곳" 이 실측 4곳 + 파생 2곳 = 6곳과 불일치. 추적성 약함 | RESULT1.md §비고에 이미 명시됨 — 정보성 기록만 |
| 낮음 | RESULT 분할 근거 표현 정확성 | `RESULT1.md` §TASK 규모 표 | doc-harness §TASK 분할 기준 3번째 조건("계층 경계 명확") 명시 누락 | **본 사이클 즉시 보완** — 분할 기준 3번째 조건 명시 + 양적 기준 미달 사실 함께 기록 |

### 통과 항목 요약

- SSOT 단일화 6건 정착 — 의사 코드 박스·상태 전이 표·문서 템플릿·도메인 용어 안내·db-patterns 인덱스·신규 룰 6개 모두 단일 정의 + N곳 참조 구조
- db-patterns 인덱스 매핑 — 구 §1~§6 → 신규 자식 §번호 매핑이 자식 3개 실제 섹션 번호와 정확히 일치
- ot-integration.md — §1~§5 헤더 태그 5개 + 참조표 아래 1문장 + (§3) 오타 정정 모두 정확
- 외부 참조 — db-patterns 분리 후 모든 호출처 (test-strategy.md 4곳 / ot-integration.md 3곳 / multi-tenant.md / CLAUDE.md / wtp-dba-reviewer.md + 파생 2곳) 갱신 정확
- 훅 정합성 — `hooks-guide.md` §3.1 / §4.1 / §5.1 동작 흐름이 실제 `.claude/hooks/*.sh` 스크립트 로직과 1:1 일치
- ANALYZE 룰 갱신 지시서 24개 항목 vs 실제 변경 — **누락 0건**
- 검증 항목 — 빌드 exit 0 / 링크 깨짐 0건 / 훅 bash 구문 OK / TASK 파싱 정확

## 개선 제안

본 사이클에서 보완하지 않은 후속 작업 (별도 PLAN 으로 이월):

- `exception-patterns.md` 가 CLAUDE.md §규칙 문서 인덱스 표에 미등록 — 다음 정비 PLAN 시 추가
- 모든 룰 파일에 "참조 문서 관계" 표 일관 추가 (현재 일부만 보유)
- `wtp-*` 4개 에이전트의 검토 항목 텍스트를 룰 파일 자동 첨부로 대체 검토
- `multi-tenant.md §4` (프로파일 분기 / Bean / 별도 모듈 판정) 의 별도 파일 분리 가능성
- `dict/standard-words.md` vs `domain-abbreviations.md` 경계 재정리
- `naming.md §Java 필드 타입 매핑` 의 `dict/` 로 이전 가능성 재평가
- `dev:analyze.md §1.3` 의 Fix Cycle 분기 외 "사전 판별 분기 §5a" 추가 단순화

## 결론

- **블로커(높음)**: 0건
- **권고(중간)**: 2건 — **모두 본 사이클에서 즉시 보완 완료** (CLAUDE.md line 48 / line 86)
- **참고(낮음)**: 2건 — #1 정보성 기록, #2 본 사이클 즉시 보완

블로커 0건 + 중간 권고 2건 모두 본 사이클에서 보완 완료 → `status: approved`. Fix Cycle 진입 사유 없음.

본 작업은 ANALYZE 룰 갱신 지시서 24개 항목 모두 실제 변경 반영 완료 + SSOT 단일화 6건 정착 + 분리 후 외부 참조 정합성 보장 + 검증 항목 모두 통과로 메타 정비 작업의 핵심 목표를 달성했다. 사용자 승인 후 `/dev:commit rules_dedup_정리` 진행.
