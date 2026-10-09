---
status: completed
created: 2026-04-23
updated: 2026-04-23
---

# 하네스 레거시·glossary 정리

## 목적

스마트정수장 하네스(`.claude/` 및 `CLAUDE.md`)에서 레거시 EMS/PMS 매핑 규칙 문서와 도메인 용어 사전 문서를 전면 제거하고, 두 문서를 참조하던 에이전트·규칙·명령어·상위 CLAUDE.md 를 현재 하네스 상태에 맞게 정리한다. 이를 통해 신규 개발 흐름을 코드 작성·엔티티 설계·API 문서화 중심으로 단순화하고, 용어 사전을 추후 재정제할 수 있는 공간을 확보한다.

## 배경

2026-04-20 "harness_도메인_DB_보강" 작업에서 `.claude/rules/legacy-mapping.md`(197줄)와 `.claude/rules/domain-glossary.md`(179줄)를 하네스에 추가했고, 같은 흐름에서 `wtp-domain-expert` 에이전트·`dev/plan.md`·`dev/review.md` 체크리스트가 이 두 문서를 전제로 작성되었다. 현재 시점에서는 레거시 매핑표가 신규 개발 흐름에 과도하게 결합되어 있고, 용어 사전을 처음부터 다시 정제할 필요가 있다고 판단되어 두 문서와 관련 참조를 일괄 제거한다.

단, 알람 4단계·인터록·운전 모드 같은 도메인 규칙 본문은 `ot-integration.md`·`test-strategy.md`·`review.md` 에 자기완결적으로 남겨두고, 출처 표기(`domain-glossary.md §X 참조`)만 정리한다. `wtp-domain-expert` 에이전트 파일도 유지하되 "도메인 규칙(알람 4단계·인터록·운전 모드) 정합성 리뷰어"로 용도를 축소한다.

## 범위

### 포함

- `.claude/rules/legacy-mapping.md`, `.claude/rules/domain-glossary.md` 물리 삭제
- `docs/analyze/20260422/pumpcontrol/bash.exe.stackdump` 물리 삭제
- `.claude/agents/{wtp-domain-expert,wtp-dba-reviewer}.md` 내용 조정 (전자는 축소 재작성, 후자는 한 줄 수정)
- `.claude/rules/{naming,ot-integration,multi-tenant,test-strategy}.md` 참조 테이블·본문 출처 표기 정리
- `.claude/commands/dev/{plan,review}.md` 레거시·glossary 체크리스트 정리
- 루트 `CLAUDE.md` 및 `common/CLAUDE.md` 규칙 인덱스·예시 링크 정리
- `docs/plan/20260421/refresh_token_naming/PLAN1.md`, `docs/plan|tasks|results|reviews/20260421/user_jwt_인증/*`, `docs/plan/20260422/yn_type_enum_표준/PLAN1.md` 내 경로 참조 텍스트 정리
- 신규 산출물(PLAN1·TASK1·RESULT1·REVIEW1) 작성

### 제외

- `docs/plan|tasks|results|reviews/20260420/harness_도메인_DB_보강/` 산출물 (이력 보존 목적으로 손대지 않음)
- `.claude/settings.local.json` 의 MCP·훅·권한 설정 (편집 불필요)
- `legacy/` 디렉토리 원본 파일 (레거시 소스 자체는 이 작업과 무관)
- MCP 서버 설정(`legacy-mariadb`, `swtp-postgres`) — 별도 요청 없음
- Gradle 빌드·자바 소스 수정 — 문서 정비만 수행

## 구현 방향

### A. 에이전트 (2개 파일)

- `wtp-domain-expert.md`: description 과 본문을 "도메인 규칙(알람 4단계·인터록·운전 모드) 정합성 리뷰어"로 재작성. `§1 용어 사전 정합성`, `§5 KWS-GS 추적·§3 매핑표` 섹션을 완전 제거하고 `§2/§3/§4` 만 유지.
- `wtp-dba-reviewer.md`: description 및 본문에서 `legacy-mapping.md §3` 참조 한 줄만 제거. DBA 리뷰 본문 유지.

### B. rules (4개 파일)

- `naming.md`: "_d·_l·_p·_c 경계 해석" 블록 뒤의 `legacy-mapping.md §1` 참조 한 줄 제거.
- `ot-integration.md`: 참조 문서 관계 테이블에서 domain-glossary·legacy-mapping 행 3개 삭제, 본문에서 `pump_interlock_p` 출처 문구를 "향후 pump 도메인 구현 시 테이블 명세 확정" 유보 문구로 교체, 알람 4단계 정의 한 줄을 문서 내부에 흡수 (`> 알람 4단계: 0:정상 · 1:주의 · 2:경보 · 3:위험/TRIP`).
- `multi-tenant.md`: 참조 테이블 legacy-mapping 행 제거, 신규 지자체 추가 절차의 "5단계(레거시 매핑표 갱신)" 제거 → 4단계 절차로 축약.
- `test-strategy.md`: 참조 테이블 domain-glossary 행 제거, 본문 알람 4단계·운전 모드 시나리오 테스트 절의 출처 표기 제거. 시나리오 본문 유지.

### C. commands (2개 파일)

- `dev/plan.md`: "레거시 산출물 참조 목록 확인" 섹션 전체 제거, "도메인 모델 섹션" 주석에서 glossary 문구 제거, 검토 기준 행에서 glossary 제거.
- `dev/review.md`: "도메인 규칙 정합성" 체크리스트 출처 표기 제거(본문 유지), "레거시 동등성" 체크 항목 전체 제거.

### D. CLAUDE.md (2개 파일)

- 루트 `CLAUDE.md`: 규칙 문서 인덱스 표에서 domain-glossary·legacy-mapping 행 제거, 그 아래 필수 안내 문구("세 문서를 먼저 확인한다")를 `db-patterns.md` 단독 언급으로 축약.
- `common/CLAUDE.md`: Persistable 규칙 문장 뒤의 상세 괄호 `(상세: entity-patterns.md, 대상 도메인 예시: legacy-mapping.md §3)` 전체를 제거.

### E. docs/ 이전 산출물 (7개 파일)

approved/completed 상태 문서의 `legacy-mapping`·`domain-glossary` 경로 참조 텍스트를 현재 하네스와 일치시킨다. 파일 삭제가 아니라 텍스트 교체이며, 원본 이력은 git log 에 보존된다.

| 파일 | 수정 방향 |
|------|----------|
| `docs/plan/20260421/refresh_token_naming/PLAN1.md` | 명명규칙 열거에서 `legacy-mapping.md` 항목 제거 |
| `docs/plan/20260421/user_jwt_인증/PLAN1.md` | 도메인 용어 사전 언급 문장 정리 |
| `docs/plan/20260421/user_jwt_인증/PLAN2.md` | `도메인 용어 사전 등록` 체크 항목 → `네이밍 합의` 로 교체 |
| `docs/tasks/20260421/user_jwt_인증/TASK1.md` | `domain-glossary.md §9 _pw` 참조 → `naming.md 접미사 규칙 반영` |
| `docs/results/20260421/user_jwt_인증/RESULT1.md` | 동일 교체 |
| `docs/reviews/20260421/user_jwt_인증/REVIEW1.md` | `domain-glossary.md §9 expr_` 참조 → `naming.md 접미사` |
| `docs/plan/20260422/yn_type_enum_표준/PLAN1.md` | 참조 문서 표·검토 기준에서 domain-glossary 행 제거 |

### F. 물리 삭제 (3개 파일)

- `.claude/rules/legacy-mapping.md`
- `.claude/rules/domain-glossary.md`
- `docs/analyze/20260422/pumpcontrol/bash.exe.stackdump`

## 테스트 전략

코드 변경이 없으므로 단위·통합 테스트는 해당 없음. 아래 6개 검증 명령으로 정비 완결성을 확인한다.

```bash
# 1차 — 활성 하네스 참조 0건
grep -rn "legacy-mapping\|domain-glossary" .claude CLAUDE.md common/CLAUDE.md

# 2차 — docs/ 에서 20260420 제외하고 참조 확인
grep -rn "legacy-mapping\|domain-glossary" docs/ \
  | grep -v "docs/plan/20260420" \
  | grep -v "docs/tasks/20260420" \
  | grep -v "docs/results/20260420" \
  | grep -v "docs/reviews/20260420" \
  | grep -v "docs/plan/20260423" \
  | grep -v "docs/tasks/20260423" \
  | grep -v "docs/results/20260423" \
  | grep -v "docs/reviews/20260423"

# 3차 — KWS-GS 참조 (settings.local.json·legacy/docs 원본 제외)
grep -rn "KWS-GS" .claude CLAUDE.md common/CLAUDE.md \
  | grep -v "settings.local.json" \
  | grep -v "legacy/docs"

# 4차 — 물리 삭제 확인
ls .claude/rules/legacy-mapping.md \
   .claude/rules/domain-glossary.md \
   docs/analyze/20260422/pumpcontrol/bash.exe.stackdump 2>&1

# 5차 — 도메인 규칙 본문 유지 확인
grep -n "알람 4단계\|인터록\|운전 모드\|AI_MODE" \
  .claude/rules/ot-integration.md \
  .claude/rules/test-strategy.md \
  .claude/commands/dev/review.md \
  .claude/agents/wtp-domain-expert.md

# 6차 — settings.local.json 유효성
python -m json.tool < .claude/settings.local.json > /dev/null && echo OK
```

각 명령의 기대 결과는 TASK1.md 체크리스트에 명시한다.

## 제외 사항

- 용어 사전 재정제(신규 `domain-glossary.md` 재작성): 별도 후속 PLAN 으로 분리.
- `docs/analyze/20260422/pumpcontrol/` 디렉토리 내 다른 분석 산출물(docx·png)의 PLAN 정식화: 별도 후속 작업.
- `legacy/` 디렉토리 원본 문서(EMS·PMS 스키마 md 등)의 삭제 또는 이동.
- `wtp-domain-expert` 에이전트의 추가 재확장(예: 용어 사전 재도입 후 정합성 검증 복구) — 용어 사전 재정제 이후 별도 하네스 보강 PLAN 으로 처리.

## 예상 산출물

- [태스크](../../../tasks/20260423/harness_레거시_glossary_정리/TASK1.md)
- [결과](../../../results/20260423/harness_레거시_glossary_정리/RESULT1.md)
- [리뷰](../../../reviews/20260423/harness_레거시_glossary_정리/REVIEW1.md)
