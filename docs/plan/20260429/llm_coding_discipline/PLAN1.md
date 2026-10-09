---
status: approved
created: 2026-04-29
updated: 2026-04-29
---
# LLM 코딩 디시플린 룰 도입 — 계획

## 목적

LLM 코딩 가이드라인 4원칙(구현 전 사고 / 단순성 우선 / 정밀한 수정 / 목표 중심 실행) 을 ROOT 보편 룰(`coding-discipline.md`) + backend 워크플로우 특화 보강으로 분산 도입한다. 미래 작업의 코드 품질·검증 명확성을 REVIEW 단계 자동 점검 가능한 형태로 강화하고, 5인 팀 회의가 도메인 가정·정량 기준 충돌을 조기에 잡도록 한다.

## 배경

- 외부 가이드라인 (americanopeople.tistory.com/514) 검토 → 사전 plan (`~\.claude\plans\llm-virtual-lake.md`) 으로 12개 보강 후보 도출 → 사용자 결정 (옵션 C 전부 + REVIEW 자동 점검 + ROOT/backend 분리)
- [ANALYZE1](../../../analyze/20260429/llm_coding_discipline/ANALYZE1.md) 5인 팀 회의 검증 결과:
  - **블로커 1건 발견 → 해소 방향 확정**: 정량 기준 vs 정수장 안전 도메인 패턴 충돌 → `coding-discipline.md` §2.5 면책 조항 신설
  - 표준 사전 4층 신규 등록 0건 (룰 변경이라 코드/DB 식별자 신규 등장 없음)
  - 기존 backend 코드 회귀 영향 거의 없음 (50줄 초과 메서드·DTO 상속·추상화 3단 초과 모두 0건) → 신규 코드부터 적용 (grandfather clause)

## 범위

### 포함

| 분류 | 파일 | 변경 유형 |
|------|------|---------|
| ROOT 신규 | `swtp/.claude/rules/coding-discipline.md` | 신규 작성 (§1~§5 + §2.5 면책) |
| backend 룰 | `.claude/rules/process/doc-harness/templates.md` | 5종 템플릿 보강 |
| backend 룰 | `.claude/rules/process/doc-harness/checkbox-rules.md` | 검증 형식 절 신설 + 백틱 금지 |
| backend 룰 | `.claude/rules/process/hooks-guide.md` | 자동 차단 훅 보류 절 신설 |
| backend 룰 | `.claude/rules/test-strategy.md` | 버그 수정 첫 체크박스 의무 |
| backend 인덱스 | `CLAUDE.md` | §규칙 문서 인덱스 행 추가 |
| ROOT 에이전트 | `swtp/.claude/agents/wtp-backend-engineer.md` | 점검 항목 4개 |
| ROOT 에이전트 | `swtp/.claude/agents/wtp-domain-expert.md` | 점검 항목 1개 (4영역 명시) |

### 제외

- **ai-server 의 `wtp-domain-expert` 카피 동기화** — 별도 안건 (`docs/analyze/.../ai_server_agent_sync` 등 후속)
- **신규 자동 차단 훅 도입** — 별도 ANALYZE (운영 사례 누적 후 결정)
- **기존 코드 소급 리팩토링** — grandfather clause 적용 (현 코드 50줄 초과 0건이므로 실질 영향 없음)
- **기존 RESULT 문서 소급 재작성** — `status: completed` 문서는 그대로 유지

## 구현 방향

### Phase 구성 (예상 5개, 단일 TASK1 으로 운영)

| Phase | 작업 | 산출 파일 |
|------|------|---------|
| 1 | ROOT 신규 룰 작성 | `swtp/.claude/rules/coding-discipline.md` |
| 2 | 5종 템플릿 보강 | `templates.md` |
| 3 | checkbox-rules / hooks-guide / test-strategy 갱신 | 3개 파일 |
| 4 | 에이전트 점검 항목 보강 | 2개 파일 |
| 5 | backend CLAUDE.md 인덱스 갱신 + 빌드 검증 | 1개 파일 + `./gradlew.bat build` |

체크박스 분할 기준 (Phase 10 / 체크박스 60) 모두 미달 → 단일 TASK1 로 진행 예정.

### 핵심 설계 결정

1. **§2.5 면책 조항** (블로커 해소): coding-discipline.md 본문에 면책 대상 ① 정수장 안전 도메인 패턴 (`ot-integration.md §3·§4·§5` 인용), ② DB 쿼리 빌더·튜닝 코드 (`db/query-tuning.md §2` 인용) 명시. **면책 사용 시 인용 근거 명기 의무화** (REVIEW 단계 자동 점검에서 인용 누락 = 블로커)

2. **분리 인덱스 패턴 vs 단일 파일** (안건 1 결론): coding-discipline.md 는 §1~§5 단일 책임이므로 단일 파일 유지. 200줄 + 주제 1개 초과 시 `coding-discipline/` 디렉토리 분할 검토

3. **dogfooding** — 본 PLAN 자체에 새 템플릿 패턴 적용:
   - "## 가정 및 미해결 질문" 섹션 1건 이상 기재 (아래)
   - TASK 체크박스에 `→ 검증: ...` 형식 적용 (TASK1 작성 시)
   - RESULT 에 "### 계획 외 변경" 절 존재 확인 (RESULT 작성 시)

4. **참조 경로 일관성**: 모든 backend 룰에서 신규 ROOT 룰 인용 시 절대 경로 `swtp/.claude/rules/coding-discipline.md` 로 통일 (`../../../../.claude/rules/coding-discipline.md` 같은 상대 경로 지양)

## 도메인 모델

신규 엔티티·DTO 없음. 본 작업은 룰/프로세스 변경이므로 도메인 모델 변경 0건. (ANALYZE1 §신규 엔티티/DB 컬럼 = 없음 확정)

## DB 설계 변경

**없음** — 룰 파일 전용 작업. DDL·인덱스·파티션·시계열 보존 정책 변경 0건.

`check-ddl-column-comment.sh` 훅 트리거 대상 (`db/init/`·`db/migration/`) 변경 없음 → 자동 차단 미발동. RESULT 에서 동일 문구로 명시.

## 성공 기준 (검증 가능 형태)

> 본 PLAN 으로 도입할 패턴 dogfooding — 기존 "## 테스트 전략" 을 본 양식으로 작성.

1. **신규 룰 파일 정합성** — 검증: `grep -c "면책" swtp/.claude/rules/coding-discipline.md` 가 1 이상, §2.5 가 `ot-integration.md` 와 `db/query-tuning.md` 를 인용 근거로 명시
2. **참조 체계 일관성** — 검증: `grep coding-discipline swtp/backend/CLAUDE.md` 1건 매칭, 모든 보강 룰에서 인용이 ROOT 절대 경로로 통일
3. **dogfooding 자기참조 검증** — 검증:
   - 본 PLAN 에 "## 가정 및 미해결 질문" 섹션 1건 이상 기재 (✓ 본 문서)
   - TASK1 체크박스가 `- [ ] ... → 검증: ...` 형식 (TASK 단계에서 확인)
   - RESULT1 에 "### 계획 외 변경" 절 존재 (RESULT 단계에서 확인, 없으면 "없음" 명시)
4. **빌드 무영향** — 검증: `./gradlew.bat build` PASS, 기존 16개 테스트 모두 통과
5. **에이전트 점검 항목 적용** — 검증: REVIEW 단계 호출 시 `wtp-backend-engineer` 응답에 4개 신규 점검 항목, `wtp-domain-expert` 응답에 4영역 점검 항목이 포함

## 가정 및 미해결 질문

> 본 작업으로 도입할 새 섹션 dogfooding 적용. ANALYZE1 의 가정 4건을 PLAN 단계 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| ROOT `coding-discipline.md` §5 가 ai-server 등 다른 모듈(Python) 에도 적용 가능한 추상도로 작성 — 단, Python 50줄·3단 기준 동일 적용 여부는 별도 검증 필요 | 가정 | §5 는 "시스템별 적용은 각 모듈 룰에서 정의" 형태로 작성, ai-server 적용은 후속 안건 |
| §2.5 면책 조항 인용 형식 | 결정 | REVIEW 자동 점검에서 면책 사용 시 `ot-integration.md §X` / `db/query-tuning.md §Y` 인용 의무 — 인용 누락 = 블로커. coding-discipline.md §2.5 본문에 명시 |
| 본 작업의 RESULT 가 신규 패턴 자기참조 검증 충족 | 가정 | RESULT 작성 시 self-check (성공 기준 §3 확인) |
| TASK 분할 여부 | 결정 | Phase 5개·체크박스 약 25개 예상 → 단일 TASK1 로 진행 |
| `wtp-domain-expert` ROOT 갱신 vs ai-server 카피 동기화 시점 차이 | 미해결 → 결정 | 본 작업은 ROOT 만 갱신. ai-server 카피 동기화는 별도 안건 (`docs/analyze/.../ai_server_agent_sync` 후속) |

## 예상 산출물

- [태스크](../../../tasks/20260429/llm_coding_discipline/TASK1.md)

---

## 부록: 도메인/DB 검토 결과

신규 엔티티·DB 변경 0건 → 검토 게이트 생략 (PLAN 단계 §도메인·DB 검토 게이트 의 "도메인 모델과 DB 변경이 모두 없는 경우 생략" 규칙). ANALYZE1 의 wtp-dba-reviewer 결론 "DB 영향 없음 + 면책 범위 확장 권고" 를 본 PLAN §구현 방향 1에 흡수.
