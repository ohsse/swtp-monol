---
status: approved
created: 2026-04-30
updated: 2026-04-30
---
# 거버넌스 명확화 — ROOT CLAUDE.md 신설 + "5인 팀" 명칭 정렬

## 목적

모노레포 ROOT 의 분산 SSOT 모델을 외부 검토자에게 가시화하고 (B1), 6개 룰 파일에 잔존하는 "5인 팀" 표기와 실제 4명 에이전트 구조의 불일치를 해소하여 5번째 facilitator (메인 Claude 오케스트레이터) 책임을 명문화한다 (B2).

## 배경

- 관련 ANALYZE: [`ANALYZE1`](../../../analyze/20260430/wtp_governance_clarity/ANALYZE1.md)
- 관련 plan (사용자 승인): `~\.claude\plans\sunny-zooming-goblet.md`
- 5인 회의 결론 (안건 1·2 모두 통과 권고) — 도메인 정책 본문 복제 금지 + "데이터 도메인" 정의 링크만 처리 (제약 2건 추가)

## 범위

### 포함

- `swtp/CLAUDE.md` 신설 (5섹션 + 폐기·갱신 이력)
- ROOT `coding-discipline.md` §5 표·§5.1·§1 박스의 "5인 팀 회의" 정렬 (ROOT 룰 변경 — §5.1 별도 절차 준수)
- backend 5 파일 (`dict/README.md`·`dict/standard-terms.md`·`process/README.md`·`process/doc-harness/templates.md`·`commands/dev/analyze.md`) 의 "5인 팀" 정렬

### 제외

- backend `CLAUDE.md` (이미 풀 표기 보유 — 모범 사례 유지)
- ai-server·frontend `.claude/` (사용자 지시로 점검 대상 외, 본 cycle 영향 없음)
- frontend `.claude/` 의 향후 도입 시 ROOT CLAUDE.md 모듈 매핑 표 갱신 절차 (ANALYZE1 의 미해결 질문 — 도입 시 별도 §5.1 절차)
- 도메인 4영역 정책 본문 (알람 4단계·인터록·운전 모드·이력 기록 의무 — backend `ot-integration.md` SSOT 유지)
- `read-docx` 스킬 ROOT 승격 (모니터 등급 — ai-server 자체 워크플로우 도입 시점에 재검토)

## 구현 방향

### Phase 1: ROOT `swtp/CLAUDE.md` 신설

5섹션 본문 + 폐기·갱신 이력 표를 한 번에 작성. 각 섹션 내용은 ANALYZE1 §"PLAN 으로 전달할 결정 사항" 의 §1~§5 직접 반영. 도메인 정책·"데이터 도메인" 정의 본문 복제 금지 (링크만).

### Phase 2: ROOT `coding-discipline.md` "5인 팀" 정렬

§5 표 (라인 154 근처)·§5.1 (라인 159)·§1 backend 적용 박스 (라인 34) 의 "5인 팀 회의" 표기를 풀 표기 (첫 등장 1회) + "5인 회의" (이후) 로 일괄 치환. ROOT 룰 변경이지만 본 ANALYZE 가 §5.1 절차로 진행 중이므로 별도 회의 불필요.

### Phase 3: backend 5 파일 명칭 정렬

`dict/README.md`·`dict/standard-terms.md`·`process/README.md`·`process/doc-harness/templates.md`·`commands/dev/analyze.md` 일괄 치환. 각 파일에서 "5인 팀" → 풀 표기/약식 적용.

### Phase 4: 검증 + 커밋

grep 으로 "5인 팀" 잔존 0건 (backend `CLAUDE.md` 의 풀 표기 패턴은 잔존 가능 — 이미 풀 표기). swtp/CLAUDE.md 5섹션 + 도메인 정책 본문 부재 확인. 사용자 명시 승인 후 커밋.

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md` §4.2`](../../../../.claude/rules/coding-discipline.md) 적용. "성능 개선" 같은 모호 목표 금지. 각 기준에 검증 명령·테스트·조회 명시.

| 기준 | 검증 명령 / 조회 |
|------|---------------|
| `swtp/CLAUDE.md` 파일 신설 + 5섹션 (`## ` 헤더 5개 이상) | `ls swtp/CLAUDE.md && grep -c "^## " swtp/CLAUDE.md` 결과 5 이상 |
| `swtp/CLAUDE.md` 본문에 "데이터 도메인 vs 비즈니스 도메인" 정의 본문 부재 (라벨 인용은 허용) | `grep -E "값의 형식.*SQL 타입|업무 영역.*Java 패키지|업무 영역.*com\.mo" swtp/CLAUDE.md` 매칭 0건. dict/README.md 링크 매칭 1건 이상 |
| `swtp/CLAUDE.md` 본문에 도메인 4영역 정책 본문 (임계값·트리거·시퀀스) 복제 부재 (라벨 인용은 허용) | `grep -E "0:정상|1:주의|2:경보|3:위험|5분 초과|TRIP|Hold Last Value" swtp/CLAUDE.md` 매칭 0건. ot-integration.md 링크 매칭 1건 이상 |
| `swtp/CLAUDE.md` "5인 회의 정의" 섹션에 4 에이전트 이름 모두 명시 | `grep -E "wtp-(dba-reviewer|backend-engineer|domain-expert|glossary-manager)" swtp/CLAUDE.md` 매칭 4건 이상 |
| 6 파일에서 "5인 팀" 잔존 0건 (backend CLAUDE.md 의 풀 표기 패턴 제외) | `grep -rn "5인 팀" swtp/.claude/ swtp/backend/.claude/` 매칭 0건 (또는 backend CLAUDE.md 의 풀 표기 1건만 매칭 — 명시적 제외) |
| 변경 파일 목록이 TASK 체크박스 백틱 경로와 100% 일치 | `git diff --name-only` 결과 7파일 (신설 1 + 갱신 6) — TASK1 체크박스와 교차 비교 |

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1`](../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE1 의 가정·미해결 질문을 PLAN 단계 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| Claude Code autoload 동작 (외부 검토자가 모노레포 루트 진입 시 `swtp/CLAUDE.md` 자동 로드) | 가정 | 결정 — autoload 미동작 환경에서도 ROOT CLAUDE.md 가 명시 진입점 역할을 함 (사용자가 직접 Read 가능). 검증 불요 |
| ai-server·frontend 의 ROOT 룰 변경은 backend `/dev:analyze` 5인 회의 단일 진입점 사용 | 가정 | 결정 — ROOT CLAUDE.md §2 (분산 SSOT 모델) + §3 (모듈별 진입점) 에 명문화 |
| ROOT CLAUDE.md 본문은 도메인 4영역 정책을 본문에 복제하지 않고 backend `ot-integration.md` §3·§4·§5 를 링크로만 참조 | 가정 (블로커 제약) | 결정 — Phase 1 작성 시 의무 적용. 성공 기준 §3 검증으로 보장 |
| frontend `.claude/` 가 향후 도입될 경우 ROOT CLAUDE.md 모듈 매핑 표 갱신 | 미해결 → 결정 | 결정 — 도입 시점에 별도 §5.1 절차 (5인 회의 + 사용자 승인). 본 cycle 의 ROOT CLAUDE.md "## 폐기·갱신 이력" 표에 본 결정 명기 |

## 제외 사항

- 도메인 4영역 정책 본문 변경 (backend `ot-integration.md` 1차 정의 SSOT 유지)
- backend `CLAUDE.md` 갱신 (이미 풀 표기 보유 — 모범 사례)
- ai-server `coding-discipline.md` 변경 (사용자 지시로 점검 대상 외)
- 자동 차단 훅 신설 (50줄·3단·DTO 3단 등 — `hooks-guide.md §7.3` 트리거 조건 미충족)

## 예상 산출물

- [태스크](../../../tasks/20260430/wtp_governance_clarity/TASK1.md)
