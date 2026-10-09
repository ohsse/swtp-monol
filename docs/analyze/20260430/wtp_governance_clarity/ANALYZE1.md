---
status: approved
created: 2026-04-30
updated: 2026-04-30
---
# 거버넌스 명확화 — ROOT CLAUDE.md 신설 + "5인 팀" 명칭 정렬

## 작업 배경

스마트정수장 모노레포의 Claude Code 하네스 점검 결과 (사용자 요청 — ROOT/backend 점검, ai-server·frontend 제외) **거버넌스 공백 2건** 확인.

- **B1**: 모노레포 ROOT (`C:\dev\workspace\swtp\`) 에 CLAUDE.md 부재. ROOT `.claude/` 는 의도적 minimalist (rules/coding-discipline.md + dict/ 만, agents·commands·hooks 부재 — 분산 SSOT 모델). 그러나 이 의도가 외부 검토자에게 가시화될 메커니즘이 0개. backend CLAUDE.md 가 ROOT 자산을 가리키지만 **역방향(ROOT → 모듈) 매핑은 어디에도 없음**.
- **B2**: 6개 룰 파일에 "5인 팀 회의" 표기 잔존. 그러나 backend `.claude/agents/` 정의 에이전트는 4개 (wtp-backend-engineer · wtp-dba-reviewer · wtp-domain-expert · wtp-glossary-manager). **5번째 facilitator 책임 주체 (메인 Claude session 인지 별도 에이전트인지) 가 어디에서도 명문화되지 않은 거버넌스 공백**.

본 안건은 ROOT 룰 (`coding-discipline.md` §5 표·§5.1·§1 박스) 갱신을 포함하므로 §5.1 "본 룰 갱신 절차" 적용 — 5인 회의 + 사용자 승인 의무.

외부 산출물: 없음 (룰·하네스 메타 작업).

관련 이전 점검 결과: `~\.claude\plans\sunny-zooming-goblet.md` (사용자 승인 plan).

## 회의록 (5인 회의 토픽 주도)

> 본 회의는 **5인 회의 (메인 Claude 오케스트레이터 + 4 에이전트)** 형식으로 진행됨. 본 안건 자체가 이 명칭의 명문화 안건이므로, 본 ANALYZE 부터 신 명칭 사용.

### 안건 1: ROOT `swtp/CLAUDE.md` 신설 (B1)

- 키워드 분류: 거버넌스 메타 / 어휘 일관성 / 도메인 안전 영향
- 호출 에이전트: `wtp-glossary-manager` (필수) + `wtp-domain-expert` (필수)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 어휘 SSOT 안전 — 신규 표준 단어·데이터 도메인·용어 0건. "분산 SSOT", "오케스트레이터", "facilitator" 등 거버넌스 메타 용어는 본 사전(3층) 등재 대상 아님 (DB 컬럼·테이블·Java 패키지명 조합 재료에 미등장). **단 "데이터 도메인 vs 비즈니스 도메인" 정의 중복 기재 금지 — `dict/README.md` ⚠️ 섹션 링크 1줄로 처리**. 본문에 정의 본문 복제 시 dict/ SSOT 위상 분산.
  - **wtp-domain-expert**: 도메인 4영역 (알람·인터록·운전 모드·이력) 본문 정책 변경 0건. 그러나 ROOT CLAUDE.md 표기 방식에 따라 **SSOT 위상 약화 위험** 존재. Q1 절차 안내 위치 = (c) 양쪽 다 (ai-server 자체 룰 + backend `/dev:analyze` 5인 회의), Q2 가정 섹션 점검 책임 = wtp-domain-expert 단독 유지, Q3 = ROOT CLAUDE.md 모듈별 진입점 섹션에 **"도메인 4영역 SSOT = backend `ot-integration.md` §3·§4·§5"** 명시 의무 + **본문 복제 금지**.
- Round 2: 미실행 (이견·블로커 없음, 두 답변 상호 보완적).
- **결론**: ROOT `swtp/CLAUDE.md` 신설 진행. 단 본문 작성 시 (a) "데이터 도메인 vs 비즈니스 도메인" 정의 중복 기재 금지, (b) 도메인 4영역 정책 본문 복제 금지 — 각각 `dict/README.md` ⚠️ 섹션 / backend `ot-integration.md` §3·§4·§5 링크 1줄로 처리. ai-server 룰 갱신 진입은 (c) 양쪽 다 명시 (ai-server 자체 룰 1차 + ROOT 룰 변경 시 backend 5인 회의).

### 안건 2: "5인 팀" 명칭 일괄 정렬 (B2)

- 키워드 분류: 어휘 일관성 / 거버넌스 명문화
- 호출 에이전트: `wtp-glossary-manager` (필수)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 표준 단어 사전 영향 0건 — "팀" 은 한국어 일반 명사이며, ROOT 표준 단어 사전은 영문 약어 기반(`nm`·`dt`·`amt` 등) 이라 한국어 단어 미관리. "오케스트레이터"·"facilitator" 도 등재 거부 (코드/DB 비노출). **체크박스 분해 권고** (6개) — 근거 1: `coding-discipline.md` §4.1 검증 기준 명시 의무, 근거 2: `check-task-unstage.sh` 훅이 백틱 경로별 unstage 매칭 (묶음 시 일부 미수정 미검출), 근거 3: ROOT 룰 1건은 §5.1 별도 절차 / backend 룰 5건은 일반 갱신 — 절차 차이 명시 가능.
- Round 2: 미실행.
- **결론**: 명칭 정렬은 어휘 사전 갱신 0건, 산문 일관성 개선만 발생. 6개 체크박스로 분해. 풀 표기 ("5인 회의 (메인 Claude 오케스트레이터 + 4 에이전트: wtp-dba-reviewer · wtp-backend-engineer · wtp-domain-expert · wtp-glossary-manager)") 첫 등장 시 사용, 이후 "5인 회의" 약식.

## 표준 사전 카탈로그

> 본 안건은 거버넌스 메타 / 산문 표현 변경이며 코드·DB·Java 패키지명 조합 재료 변경 0건. 3개 층위 모두 신규 항목 없음.

### 신규 표준 단어

없음.

### 신규 표준 데이터 도메인

없음.

### 신규 표준 용어

없음.

분류값 (3층 공통): **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

## 신규 엔티티/DB 컬럼

없음 — 본 안건은 룰·하네스 메타 작업으로 엔티티·DB 변경 0건.

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 (해소책) |
|----------|------------------|
| ROOT `coding-discipline.md` §5 표·§5.1·§1 박스의 "5인 팀 회의" 표기와 backend `CLAUDE.md` 의 풀 표기 ("5인 팀 회의(오케스트레이터/DBA/Backend 개발자/도메인 전문가/용어 관리자)") 공존 | 풀 표기 1회 + "5인 회의" 약식으로 통일. backend CLAUDE.md 의 풀 표기 패턴은 모범 사례로 유지 (갱신 대상 외) |
| 5번째 facilitator 책임 주체가 어디에도 명문화되지 않음 (4개 에이전트만 정의) | ROOT CLAUDE.md "5인 회의 정의" 섹션에 **메인 Claude (오케스트레이터, 별도 에이전트 정의 없음) + 4 에이전트** 명문화. 메인 Claude 책임 = `/dev:analyze` 슬래시 명령 실행·회의 진행·결론 기록 |
| ROOT `dict/` SSOT 위상 가시화 메커니즘 0개 (외부 검토자 진입 시) | ROOT CLAUDE.md 모듈 매핑 표에 `dict/README.md` 진입점 명시 + "데이터 도메인 vs 비즈니스 도메인" 구분은 **링크만** (정의 본문 복제 금지) |
| backend 도메인 4영역 (알람·인터록·운전 모드·이력) SSOT 가 ROOT 자산으로 오해될 위험 | ROOT CLAUDE.md 본문에 **"도메인 4영역 SSOT = backend `.claude/rules/ot-integration.md` §3·§4·§5"** 명시 + 본문 복제 금지 |
| (impl 단계 추가 발견) ANALYZE1 의 "이미 풀 표기 보유" 분류로 backend `CLAUDE.md` 라인 46 갱신 제외했으나, 단어 "5인 팀" 잔존 — 풀 표기는 형식만 5명 명시이고 단어는 미정렬 | impl 진행 중 grep 검증으로 발견. backend `CLAUDE.md` 라인 46 도 일괄 정렬 적용. 추가 발견 위치 — ROOT `dict/README.md` 라인 47, backend `commands/dev.md` 라인 59·68·87, backend `process/doc-harness/README.md` 라인 170. 본 ANALYZE1 의 6 파일 가정이 8 파일로 확장 (dict/standard-terms.md 는 grep 매칭 0건이라 갱신 대상 외 정정) |

## PLAN 으로 전달할 결정 사항

### B1 — ROOT `swtp/CLAUDE.md` 신설 5섹션

- **§1 모노레포 개요** — backend (Java/Spring Boot 4) · ai-server (Python/FastAPI) · frontend (미정의) 3 모듈, 각각 자체 `.claude/` 보유
- **§2 ROOT `.claude/` 의 역할 (분산 SSOT 모델)** — ROOT 는 모노레포 공통 어휘 사전 (dict/) 과 LLM 행동 규율 (coding-discipline.md) 의 SSOT. 각 모듈은 자체 `.claude/` 에서 ROOT 를 참조하며 모듈 특수 룰을 추가. ROOT 자산 갱신은 backend `/dev:analyze` 5인 회의를 단일 진입점으로 사용 (§5.1)
- **§3 모듈별 진입점** — backend `swtp/backend/CLAUDE.md` (도메인 4영역 SSOT = `ot-integration.md` §3·§4·§5 강조) · ai-server `swtp/ai-server/.claude/rules/coding-discipline.md` · frontend (현재 backend 가 spec 전파, frontend 자체 `.claude/` 미정의)
- **§4 5인 회의 정의** (B2 흡수) — 5명 = 메인 Claude (오케스트레이터/facilitator, 별도 에이전트 정의 없음) + wtp-dba-reviewer + wtp-backend-engineer + wtp-domain-expert + wtp-glossary-manager. 메인 Claude 는 `/dev:analyze` 슬래시 명령 실행 주체이며 회의 진행·결론 기록 책임. ROOT 룰 갱신 시 본 회의 + 사용자 승인 의무 (§5.1)
- **§5 신규 작업자/외부 검토자 입문 가이드** — 첫 작업 시 읽을 순서: ROOT CLAUDE.md → backend CLAUDE.md → ROOT coding-discipline.md → `/dev`. "데이터 도메인 vs 비즈니스 도메인" 구분은 `dict/README.md` ⚠️ 섹션 링크만
- **§폐기·갱신 이력** — coding-discipline.md 패턴 모방, 본 ANALYZE 인용

### B2 — "5인 팀" 명칭 정렬 (6파일)

- 풀 표기: "5인 회의 (메인 Claude 오케스트레이터 + 4 에이전트: wtp-dba-reviewer · wtp-backend-engineer · wtp-domain-expert · wtp-glossary-manager)" — 각 파일 첫 등장 1회
- 약식: "5인 회의" — 이후
- backend `CLAUDE.md` 의 풀 표기는 모범 사례로 유지 (갱신 대상 외)

## 가정 및 미해결 질문

> 본 ANALYZE 의 가정·미해결 질문. ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. wtp-domain-expert 가 §검토 항목 4 에 따라 도메인 4영역 충돌 여부 점검 완료. 누락 가정 1건 (도메인 정책 본문 복제 금지) 추가됨.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| Claude Code autoload 동작 — 외부 검토자가 모노레포 루트 (`swtp/`) 진입 시 `swtp/CLAUDE.md` 가 자동 로드된다 | 가정 | Claude Code 표준 동작. backend `swtp/backend/CLAUDE.md` 도 동일 패턴이라 일관성 있음 |
| ai-server·frontend 의 ROOT 룰 변경은 backend `/dev:analyze` 5인 회의 단일 진입점 사용 | 가정 | 현 §5.1 정책과 일치. ai-server `coding-discipline.md` 신설 (§5 표 갱신, 2026-04-29) 사례에서 검증됨 |
| ROOT CLAUDE.md 본문은 도메인 4영역 (알람·인터록·운전 모드·이력) 정책을 **본문에 복제하지 않고** backend `ot-integration.md` §3·§4·§5 를 링크로만 참조 | 가정 (블로커 — wtp-domain-expert 추가) | 누락 시 이중 SSOT 발생 — `coding-discipline.md` §3 "정밀한 수정" + §5.1 "본 룰 갱신 절차" 위반 위험. PLAN 단계에서 본 가정의 검증 명령 명시 의무 |
| frontend `.claude/` 가 향후 도입될 경우 ROOT CLAUDE.md 모듈 매핑 표 갱신 절차 | 미해결 → PLAN 결정 | 현재 frontend 는 자체 `.claude/` 미정의. 향후 도입 시 본 CLAUDE.md 의 갱신은 §5.1 적용 (5인 회의 + 사용자 승인) — PLAN1 의 "## 제외 사항" 에 명기 |

분류값: 가정 / 미해결 / 결정 (미해결은 PLAN 단계에서 결정으로 변환)

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/CLAUDE.md` — 신설, 5섹션 (모노레포 개요·분산 SSOT 모델·모듈별 진입점·5인 회의 정의·입문 가이드) + 폐기·갱신 이력 → 검증: 파일 존재 + grep 으로 "데이터 도메인" 정의 본문 부재 확인 (링크 1건만 존재)
- [x] `swtp/CLAUDE.md` — 도메인 4영역 정책 본문 복제 금지, backend `.claude/rules/ot-integration.md` §3·§4·§5 링크만 → 검증: grep "ot-integration" swtp/CLAUDE.md 매칭 1건 이상, "알람 4단계" "인터록" 등 정책 키워드 본문 정의 부재
- [x] `swtp/.claude/rules/coding-discipline.md` — §5 표·§5.1·§1 backend 적용 박스의 "5인 팀 회의" → "5인 회의 (메인 Claude 오케스트레이터 + 4 에이전트)" 첫 등장 풀 표기 + 이후 약식. ROOT 룰이므로 §5.1 별도 5인 회의 + 사용자 승인 절차 준수 → 검증: grep "5인 팀" swtp/.claude/rules/coding-discipline.md 결과 0건
- [x] `swtp/backend/.claude/rules/dict/README.md` 라인 23 — 동일 치환 → 검증: grep "5인 팀" 결과 0건
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` 라인 22 — 동일 치환 → 검증: grep "5인 팀" 결과 0건
- [x] `swtp/backend/.claude/rules/process/README.md` 라인 25 — 동일 치환 (process 진입점이므로 풀 표기 우선 권고) → 검증: 풀 표기 1건 이상 존재
- [x] `swtp/backend/.claude/rules/process/doc-harness/templates.md` ANALYZE 회의록 헤더 — "5인 팀 토픽 주도" → "5인 회의 토픽 주도" → 검증: grep "5인 팀" 결과 0건
- [x] `swtp/backend/.claude/commands/dev/analyze.md` 회의 정의 섹션 (라인 1·8·87) — 동일 치환, 풀 표기 (4 에이전트 이름 모두 명시) 필수 → 검증: 명령 본문에서 4 에이전트 이름 모두 명시 + grep "5인 팀" 결과 0건

## 산출물

- [계획안](../../../plan/20260430/wtp_governance_clarity/PLAN1.md)
