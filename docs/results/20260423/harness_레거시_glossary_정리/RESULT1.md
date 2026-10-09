---
status: completed
created: 2026-04-23
updated: 2026-04-23
---

# 하네스 레거시·glossary 정리

## 관련 작업
- [계획안](../../../plan/20260423/harness_레거시_glossary_정리/PLAN1.md)
- [태스크](../../../tasks/20260423/harness_레거시_glossary_정리/TASK1.md)

## 작업 요약

스마트정수장 하네스에서 레거시 EMS/PMS 매핑 규칙 문서(`.claude/rules/legacy-mapping.md`)와 도메인 용어 사전 문서(`.claude/rules/domain-glossary.md`)를 제거하고, 두 문서를 참조하던 에이전트·규칙·명령어·상위 CLAUDE.md 를 일괄 정리했다. `wtp-domain-expert` 에이전트는 파일을 유지하되 "도메인 규칙(알람 4단계·인터록·운전 모드) 정합성 리뷰어"로 용도를 축소했고, `ot-integration.md`·`test-strategy.md`·`review.md` 의 알람·운전 모드·인터록 규칙 본문은 그대로 유지한 채 glossary 출처 링크만 제거했다. 이전 작업(`refresh_token_naming`, `user_jwt_인증`, `yn_type_enum_표준`) 산출물의 참조 텍스트도 현재 하네스와 일치하도록 정리했다. `docs/plan|tasks|results|reviews/20260420/harness_도메인_DB_보강/` 는 이력 보존을 위해 그대로 두었다.

## 변경 사항

### 삭제 파일 (3개)

| 파일 | 유형 |
|------|------|
| `.claude/rules/legacy-mapping.md` | 규칙 문서 |
| `.claude/rules/domain-glossary.md` | 규칙 문서 |
| `docs/analyze/20260422/pumpcontrol/bash.exe.stackdump` | 임시 덤프 파일 |

### 수정 파일 — .claude/

| 파일 | 변경 요약 |
|------|---------|
| `.claude/agents/wtp-domain-expert.md` | description 재작성 + §1(용어 사전)·§5(KWS-GS·§3 매핑) 섹션 제거. §2 알람 4단계·§3 인터록·§4 운전 모드 유지 |
| `.claude/agents/wtp-dba-reviewer.md` | §1 파티셔닝 전략 제목에서 `legacy-mapping.md §3` 참조 제거 |
| `.claude/rules/naming.md` | _d·_l·_p·_c 경계 해석 블록의 legacy-mapping 링크 한 줄 제거 |
| `.claude/rules/ot-integration.md` | 참조 문서 테이블 3행(glossary·legacy 관련) 제거, `pump_interlock_p` 출처 유보 문구 교체, 알람 4단계 정의를 인라인 한 줄(`0:정상·1:주의·2:경보·3:위험/TRIP`)로 흡수 |
| `.claude/rules/multi-tenant.md` | 참조 테이블 legacy-mapping 행 제거, 신규 지자체 추가 절차의 5단계(레거시 매핑표 갱신) 제거 → 4단계로 축약 |
| `.claude/rules/test-strategy.md` | 참조 테이블 glossary 행 제거, 5-1·5-3 시나리오 섹션의 glossary 출처 표기 제거(본문 0~3·AI_MODE=0/1/2 값은 유지) |
| `.claude/commands/dev/plan.md` | "레거시 산출물 참조 목록 확인" 섹션 전체 제거, 도메인 모델 섹션 주석의 glossary 필수 문구 제거, 검토 기준 행에서 glossary 제거 |
| `.claude/commands/dev/review.md` | 도메인 규칙 정합성 체크리스트 출처 표기 제거(본문 유지), 레거시 동등성 체크 항목 전체 제거 |

### 수정 파일 — CLAUDE.md

| 파일 | 변경 요약 |
|------|---------|
| `CLAUDE.md` | 규칙 문서 인덱스 표에서 domain-glossary·legacy-mapping 2행 제거, 필수 안내 문구를 `db-patterns.md` 단독 언급으로 축약 |
| `common/CLAUDE.md` | Persistable 규칙 문장의 `(상세: entity-patterns.md, 대상 도메인 예시: legacy-mapping.md §3)` 괄호 전체 제거 |

### 수정 파일 — docs/ 이전 산출물

| 파일 | 변경 요약 |
|------|---------|
| `docs/plan/20260421/refresh_token_naming/PLAN1.md` | 명명규칙 열거에서 `legacy-mapping.md` 항목 제거 |
| `docs/plan/20260421/user_jwt_인증/PLAN1.md` | 도메인 용어 사전 언급을 "네이밍 규칙" 으로 완화 |
| `docs/plan/20260421/user_jwt_인증/PLAN2.md` | `L1 expr_ 접미사 도메인 용어 사전 등록` → `L1 expr_ 접미사 네이밍 합의` |
| `docs/tasks/20260421/user_jwt_인증/TASK1.md` | Phase 1 제목·체크 항목의 glossary §9 참조를 naming.md 접미사 규칙으로 교체 |
| `docs/results/20260421/user_jwt_인증/RESULT1.md` | 작업 요약·변경 사항 표의 glossary 참조를 naming.md 로 교체 |
| `docs/reviews/20260421/user_jwt_인증/REVIEW1.md` | L1 지적 사항·개선 제안의 glossary 참조를 naming.md 접미사 규칙으로 교체 |
| `docs/plan/20260422/yn_type_enum_표준/PLAN1.md` | 배경·도메인 모델 섹션의 glossary §2·§4 인용을 제거, 레거시 EMS/PMS 컬럼 수 예시 삭제 |

### 신규 파일

- `docs/plan/20260423/harness_레거시_glossary_정리/PLAN1.md`
- `docs/tasks/20260423/harness_레거시_glossary_정리/TASK1.md`
- `docs/results/20260423/harness_레거시_glossary_정리/RESULT1.md` (본 문서)
- `docs/reviews/20260423/harness_레거시_glossary_정리/REVIEW1.md`

## 테스트 결과

코드 변경이 없어 Gradle·JUnit 실행은 해당 없음. 하네스 정비 검증 6건 모두 통과.

| # | 검증 | 결과 |
|---|------|------|
| 1 | `.claude/`·`CLAUDE.md`·`common/CLAUDE.md` 에 legacy-mapping·domain-glossary 참조 0건 | ✅ no matches |
| 2 | docs/ (20260420·20260423 제외) 에 legacy-mapping·domain-glossary 참조 0건 | ✅ 0건 |
| 3 | 활성 하네스에 KWS-GS 참조 0건 (settings.local.json·legacy/docs 제외) | ✅ 0건 |
| 4 | 3개 삭제 대상 파일 물리 삭제 확인 | ✅ "No such file" 3건 |
| 5 | 도메인 규칙 본문 유지 (`알람 4단계`·`인터록`·`운전 모드`·`AI_MODE` 어휘) | ✅ ot-integration 6건 / test-strategy 8건 / review 1건 / wtp-domain-expert 9건 |
| 6 | `.claude/settings.local.json` 유효한 JSON | ✅ `python -m json.tool` 성공 |

## 비고

- 사용자 결정에 따라 `docs/plan|tasks|results|reviews/20260420/harness_도메인_DB_보강/` 산출물은 이력 보존 목적으로 편집하지 않았다. 이 디렉토리에는 여전히 `legacy-mapping`·`domain-glossary` 문자열이 남아 있지만 작업 당시 하네스 상태를 기록한 것이므로 의도된 상태다.
- `.claude/settings.local.json` 의 `legacy/docs/algorithm/KWS-GS-EM-DG16-00*` pdftotext 허용 경로는 레거시 원본 PDF 파일 접근 권한이므로 그대로 유지했다. MCP 서버(`legacy-mariadb`, `swtp-postgres`) 설정도 편집하지 않았다.
- `pump_interlock_p` 테이블 명세의 출처였던 legacy-mapping.md §3 이 사라졌으므로 `ot-integration.md` line 100 에 "향후 pump 도메인 구현 시 테이블 명세 확정" 유보 문구를 남겼다. 실제 pump 도메인 구현 시 이 테이블을 설계한다.
- 용어 사전(`domain-glossary.md`)을 재정제하는 것은 별도 후속 PLAN 으로 진행한다.
- `docs/analyze/20260422/pumpcontrol/` 의 요구사항·다이어그램 파일(docx·png)은 보존했다. 해당 산출물을 PLAN 단계로 정식화하는 작업은 별도 후속 작업으로 남긴다.
