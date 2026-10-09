---
status: approved
created: 2026-04-30
updated: 2026-04-30
---
# §1 5번째 항목 (도구 출력 경로 해석) 점검 항목 도입 — 4 에이전트 갱신

## 작업 배경

ROOT [`coding-discipline.md` §1`](../../../../../.claude/rules/coding-discipline.md) 5번째 항목 (2026-04-29 신설) — "**도구 출력의 경로를 해석할 때 모듈 경계를 절대 경로로 확인한다**. Glob·LS·find 등이 반환하는 경로는 작업 디렉토리(`pwd`) 기준의 cwd 상대 표기일 수 있다. 모노레포에서는 결과 경로의 첫 컴포넌트가 ROOT(`swtp/.claude/...`) 인지 모듈(`swtp/{module}/.claude/...`) 인지 절대 경로로 한 번 더 검증한 뒤 가정으로 기재한다."

본 항목이 **backend 4 에이전트의 검토 항목에 반영되지 않은 거버넌스 공백** 확인 (Phase 1 점검 결과). 메인 Claude session 의 자기 점검에만 의존 → 회의 단계에서 발견 보장 안 됨. 사용자 결정 (plan: 블로커 강도) 에 따라 본 ANALYZE 진행.

본 cycle 은 ROOT 룰 변경이 아닌 backend 에이전트 정의 변경 — §5.1 본 룰 갱신 절차 미적용. medium 분류 (5인 회의 + ANALYZE/PLAN/TASK + impl + 커밋) 로 진행.

외부 산출물: 없음. 관련 plan: `~\.claude\plans\sunny-zooming-goblet.md` (Cycle 2-B). Cycle 2-A 산출물: `backend/docs/analyze/20260430/wtp_governance_clarity/ANALYZE1.md`.

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 4 에이전트에 §1 5번째 항목 점검 항목 신설 (블로커 강도)

- 키워드 분류: 거버넌스 메타 / 점검 항목 강도
- 호출 에이전트: `wtp-backend-engineer` (필수 — 점검 항목 추가 영향) · `wtp-domain-expert` (필수 — 도메인 정합성 추가)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 본 점검 항목 추가는 자기 검토 항목 §11 신설 형태로 수용 가능. 다른 §검토 항목 (§7 단순성 위반·§8 TASK 외 파일 변경 감지·§9 체크박스 검증 기준 누락) 과 의미 영역 구분됨 — §11 은 "문서에 기재된 도구 출력 경로의 모듈 경계 명시 정합성" 으로 좁게 정의. 블로커 강도 적용 시 ANALYZE/PLAN/REVIEW 모든 단계에서 cwd 상대 경로 발견 → Fix Cycle 진입. **권고**: 도입 후 1주 false positive 모니터링 필요 (의도적 cwd 상대 경로 사례 — 예: backend 모듈 내부 작업 시 `.claude/rules/` 첫 컴포넌트 누락 사용처 — 발견 시 점검 항목 강도 재검토).
  - **wtp-domain-expert**: 본 항목은 도메인 4영역 (알람·인터록·운전 모드·이력) 본문 정책과 무관. 그러나 §검토 항목 4 (가정 섹션 도메인 충돌 점검) 와 **간접 보완 관계** — 가정 섹션의 "외부 산출물 경로" 또는 "참조 룰 경로" 가 cwd 상대 표기로 작성된 경우 도메인 충돌 점검의 정확도가 떨어질 수 있음. §5 신설 후 §4 와 합산하여 가정 섹션 점검 강도 ↑. 블로커 강도 적용 동의.
- Round 2: 미실행 (이견 없음).
- **결론**: 4 에이전트에 §검토 항목 신설 (각 에이전트의 마지막 항목 다음 위치). 강도 = 블로커. 본문 형식 — "ANALYZE/PLAN/REVIEW 의 경로 표기 정합성 (블로커) — ROOT `coding-discipline.md` §1 5번째 항목 적용. 문서에 등장하는 도구 출력 경로 (Glob·LS·find 결과) 가 모듈 경계를 절대 경로 또는 명시적 모듈 prefix 로 기술하는지 점검. 모호한 cwd 상대 경로 발견 시 블로커 — Fix Cycle 진입." 모니터링: 도입 후 1주 false positive 빈도 측정 (PLAN1 §"## 가정 및 미해결 질문" 결정으로 변환).

## 표준 사전 카탈로그

신규 표준 단어·표준 데이터 도메인·표준 용어 모두 없음. 본 안건은 에이전트 정의 변경 — DB 컬럼·테이블·Java 패키지명 조합 재료 변경 0건.

## 신규 엔티티/DB 컬럼

없음.

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 (해소책) |
|----------|------------------|
| wtp-backend-engineer 의 §검토 항목 7~9 (단순성 위반·TASK 외 파일 변경·체크박스 검증) 와 §11 신설의 의미 영역 중복 가능성 | §11 은 "**문서에 기재된 도구 출력 경로의 모듈 경계 명시**" 로 좁게 정의 — §7~§9 와 의미 분리 ✅ |
| wtp-domain-expert §검토 항목 4 (가정 섹션 도메인 충돌) 와 §5 신설 (경로 표기 정합성) 의 의미 영역 중복 | §5 는 "도구 출력 경로의 모듈 경계", §4 는 "가정 섹션의 도메인 4영역 충돌" — 서로 다른 점검 영역. 단 가정 섹션의 경로 인용에서는 §5 가 §4 의 정확도를 보완 (간접 보완 관계) ✅ |
| Fix Cycle 키워드 (`용어`·`약어`·`중복 정의`·`네이밍 충돌`·`엔티티 통합`) 와 본 점검 항목의 키워드 무관 | 도메인 정합성 키워드 무관 → REVIEW 블로커 시 ANALYZE 스킵 + PLAN2 직행 (`process/doc-harness/README.md` §수정 사이클) |

## PLAN 으로 전달할 결정 사항

- **4 에이전트 검토 항목 신설** — 각 에이전트의 마지막 §검토 항목 다음 위치에 신규 항목 추가. 각 에이전트의 항목 번호:
  - wtp-backend-engineer: §11 (현 §1~§10 다음)
  - wtp-dba-reviewer: §7 (현 §1~§6 다음)
  - wtp-domain-expert: §5 (현 §1~§4 다음)
  - wtp-glossary-manager: §9 (현 §1~§8 다음)
- **본문 통일 형식** — 다음 본문을 4 에이전트에 동일 적용 (각 에이전트의 §검토 항목 번호만 N 으로 치환):

```
N. **ANALYZE/PLAN/REVIEW 의 경로 표기 정합성** (블로커) — ROOT [`coding-discipline.md` §1`](../../.claude/rules/coding-discipline.md) 5번째 항목 적용. 문서에 등장하는 도구 출력 경로 (Glob·LS·find 결과) 가 모듈 경계를 절대 경로 또는 명시적 모듈 prefix 로 기술하는지 점검. 모호한 cwd 상대 경로 (`{모듈}/...` 첫 컴포넌트 누락) 발견 시 **블로커** — Fix Cycle 진입 (도메인 정합성 키워드 무관 → ANALYZE 스킵, PLAN{N+1} 직행). 발견 사항은 REVIEW 의 "## 발견 사항" 표에 심각도 "높음" 으로 등록.
```

- **§출력 형식 미변경** — 발견 사항 표의 형식·심각도 라벨은 기존 그대로. 본 점검 항목 발견 시 표 행 1건 추가만.

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1`](../../../../../.claude/rules/coding-discipline.md) 적용. 본 ANALYZE 의 가정·미해결 질문. **최소 1건 이상 기재 의무**.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 4 에이전트의 §검토 항목 마지막 위치에 신규 항목을 추가해도 기존 §검토 항목의 번호·순서·우선순위가 변경되지 않는다 (점검 영역 의미 분리 결론에 따라) | 가정 | wtp-backend-engineer §11 / wtp-domain-expert §5 / wtp-dba-reviewer §7 / wtp-glossary-manager §9 — 모두 마지막 위치 추가 |
| 도입 후 1주 false positive 빈도 모니터링 결과 잦은 의도적 cwd 상대 경로 사례 발견 시 점검 항목 강도 재검토 (권고로 강등) | 미해결 → PLAN 결정 | wtp-backend-engineer Round 1 답변에서 제기. PLAN1 의 §"## 가정 및 미해결 질문" 에서 결정으로 변환 |
| 본 항목의 점검 영역이 wtp-backend-engineer §7~§9 + wtp-domain-expert §4 와 의미 분리됨 (회의 결론) — 분리 정확성에 의존 | 가정 | 충돌 표 §1·§2 행 결론과 일치. 도입 후 운영 사례에서 분리 모호 발견 시 §검토 항목 본문 갱신 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/backend/.claude/agents/wtp-backend-engineer.md` — §11 검토 항목 신설 ("ANALYZE/PLAN/REVIEW 의 경로 표기 정합성" 블로커) → 검증: grep "5번째 항목|경로 표기 정합성" wtp-backend-engineer.md 매칭 1건 이상
- [x] `swtp/backend/.claude/agents/wtp-dba-reviewer.md` — §7 검토 항목 신설 (동일 본문) → 검증: grep "5번째 항목|경로 표기 정합성" wtp-dba-reviewer.md 매칭 1건 이상
- [x] `swtp/backend/.claude/agents/wtp-domain-expert.md` — §5 검토 항목 신설 (동일 본문) → 검증: grep "5번째 항목|경로 표기 정합성" wtp-domain-expert.md 매칭 1건 이상
- [x] `swtp/backend/.claude/agents/wtp-glossary-manager.md` — §9 검토 항목 신설 (동일 본문) → 검증: grep "5번째 항목|경로 표기 정합성" wtp-glossary-manager.md 매칭 1건 이상

## 산출물

- [계획안](../../../plan/20260430/wtp_path_resolution_check/PLAN1.md)
