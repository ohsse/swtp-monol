---
status: completed
created: 2026-04-23
updated: 2026-04-23
---

# 하네스 레거시·glossary 정리

## 관련 계획
- [계획안](../../../plan/20260423/harness_레거시_glossary_정리/PLAN1.md)

## Phase

### Phase 1: .claude/ 에이전트 정리
- [x] `.claude/agents/wtp-domain-expert.md` 축소 재작성 (description 재작성, §1·§5 제거, §2·§3·§4 유지)
- [x] `.claude/agents/wtp-dba-reviewer.md` legacy-mapping 참조 한 줄 제거

### Phase 2: .claude/ rules 참조 정리
- [x] `.claude/rules/naming.md` legacy-mapping 링크 한 줄 제거
- [x] `.claude/rules/ot-integration.md` 참조 테이블 3행 제거 + 본문 3개소 수정 (pump_interlock 유보 문구, 알람 4단계 인라인 정의)
- [x] `.claude/rules/multi-tenant.md` 참조 테이블 1행 제거 + 5단계 절차 제거
- [x] `.claude/rules/test-strategy.md` 참조 테이블 1행 제거 + 본문 2개소 출처 표기 제거

### Phase 3: .claude/ commands 정리
- [x] `.claude/commands/dev/plan.md` 레거시 섹션·glossary 검토 기준 정리
- [x] `.claude/commands/dev/review.md` 출처 표기·레거시 동등성 항목 정리

### Phase 4: CLAUDE.md 정리
- [x] `CLAUDE.md` 규칙 인덱스 2행 제거 + 필수 안내 문구 수정
- [x] `common/CLAUDE.md` Persistable 예시 괄호 전체 제거

### Phase 5: docs/ 이전 산출물 참조 정리
- [x] `docs/plan/20260421/refresh_token_naming/PLAN1.md` 명명규칙 열거에서 legacy-mapping 제거
- [x] `docs/plan/20260421/user_jwt_인증/PLAN1.md` 도메인 용어 사전 언급 정리
- [x] `docs/plan/20260421/user_jwt_인증/PLAN2.md` `L1 expr_ 도메인 용어 사전 등록` 체크 → `네이밍 합의`
- [x] `docs/tasks/20260421/user_jwt_인증/TASK1.md` glossary §9 참조 → naming.md 접미사
- [x] `docs/results/20260421/user_jwt_인증/RESULT1.md` glossary §9 참조 → naming.md 접미사
- [x] `docs/reviews/20260421/user_jwt_인증/REVIEW1.md` glossary §9 expr_ 참조 → naming.md 접미사
- [x] `docs/plan/20260422/yn_type_enum_표준/PLAN1.md` domain-glossary §4 참조 제거

### Phase 6: 물리 삭제
- [x] `.claude/rules/legacy-mapping.md` 삭제
- [x] `.claude/rules/domain-glossary.md` 삭제
- [x] `docs/analyze/20260422/pumpcontrol/bash.exe.stackdump` 삭제

### Phase 7: 검증
- [x] 검증 1차 — 활성 하네스에 legacy-mapping·domain-glossary 참조 0건 확인
- [x] 검증 2차 — docs/ 에서 20260420/20260423 제외 시 참조 0건 확인
- [x] 검증 3차 — KWS-GS 참조 0건 (settings.local.json·legacy/docs 제외)
- [x] 검증 4차 — 삭제 대상 3개 파일 물리 삭제 확인
- [x] 검증 5차 — 도메인 규칙 본문(알람 4단계·인터록·운전 모드·AI_MODE) 유지 확인
- [x] 검증 6차 — settings.local.json 유효성 확인

### Phase 8: 산출물 문서 완료 처리
- [x] `docs/plan/20260423/harness_레거시_glossary_정리/PLAN1.md` 생성 완료 및 status 전환
- [x] `docs/tasks/20260423/harness_레거시_glossary_정리/TASK1.md` 자기자신 — 모든 체크박스 완료 후 status: completed
- [x] `docs/results/20260423/harness_레거시_glossary_정리/RESULT1.md` 작성
- [x] `docs/reviews/20260423/harness_레거시_glossary_정리/REVIEW1.md` 작성

## 검증 명령 참고

```bash
# 1차: 활성 하네스 참조 0건
grep -rn "legacy-mapping\|domain-glossary" .claude CLAUDE.md common/CLAUDE.md

# 2차: docs/ 참조 (20260420·20260423 제외)
grep -rn "legacy-mapping\|domain-glossary" docs/ \
  | grep -vE "docs/(plan|tasks|results|reviews)/(20260420|20260423)/"

# 3차: KWS-GS 참조
grep -rn "KWS-GS" .claude CLAUDE.md common/CLAUDE.md \
  | grep -v "settings.local.json" \
  | grep -v "legacy/docs"

# 4차: 삭제 확인
ls .claude/rules/legacy-mapping.md \
   .claude/rules/domain-glossary.md \
   docs/analyze/20260422/pumpcontrol/bash.exe.stackdump 2>&1

# 5차: 본문 유지 확인
grep -n "알람 4단계\|인터록\|운전 모드\|AI_MODE" \
  .claude/rules/ot-integration.md \
  .claude/rules/test-strategy.md \
  .claude/commands/dev/review.md \
  .claude/agents/wtp-domain-expert.md

# 6차: JSON 유효성
python -m json.tool < .claude/settings.local.json > /dev/null && echo OK
```

## 산출물
- [결과](../../../results/20260423/harness_레거시_glossary_정리/RESULT1.md)
- [리뷰](../../../reviews/20260423/harness_레거시_glossary_정리/REVIEW1.md)
