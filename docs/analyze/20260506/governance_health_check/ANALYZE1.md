---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 거버넌스 진단 슬래시 명령 (`/governance`) 신설 — 도메인 분석

## 작업 배경

ROOT/backend 거버넌스 강화 plan (`~/.claude/plans/quiet-hugging-bachman.md`) §Cycle 2 의 산출물 작성 사이클. Cycle 1 (`analyze_section_enforcement`, 커밋 `9208f4a`) 종료 후 사용자가 본 Cycle 2 진행 결정.

**목적**: 사용자 주기 호출 슬래시 명령 `/governance` 를 신설하여 ROOT/backend 분산 SSOT 모델의 (1) 정량 진단 + (2) 정합 진단을 자동화한다. **자동 수정 없음** — 발견사항 보고만, 실제 수정은 사용자가 별도 `/dev` 사이클로 결정.

**외부 산출물**:
- `~/.claude/plans/quiet-hugging-bachman.md` §Cycle 2 (라인 66~110) — `/governance` 점검 항목 2계층 정의 + 변경 대상 파일 + 검증 방법
- Cycle 1 산출물 (`backend/docs/{plan,tasks,results,reviews,analyze}/20260506/analyze_section_enforcement/`) — 본 Cycle 2 의 정합 진단 입력

**환경 적용**:
- 본 환경은 `feature-dev` 플러그인 미설치 (Cycle 1 dogfood 시 발견, 메인 Claude 메모리 등록). `dev/plan.md`·`dev/review.md` 의 `Agent(subagent_type="feature-dev:code-reviewer", ...)` 호출 패턴은 본 사이클 5인 회의 진행 시 `wtp-domain-expert` / `wtp-backend-engineer` / `wtp-dba-reviewer` / `wtp-glossary-manager` 직접 호출로 대체. 룰 본문 갱신은 별도 사이클 위임 (`/governance` 정합 진단 항목으로 자동 식별).

## 회의록 (5인 회의 토픽 주도)

### 안건 1: `/governance` 정량 진단 항목 정의

- **호출 에이전트**: `wtp-backend-engineer` (Round 1)
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**: 300줄+ 임계값 적합. 단, 단독 기준이 아닌 "단일 관심사 vs 복합 관심사" 구분 컬럼 병기 권고 — `templates.md` 376줄은 5종 템플릿(이질적 관심사) 으로 분리 후보지만 `test-strategy.md` 343줄·`entity-patterns.md` 318줄은 단일 관심사 풍부 예제 집합으로 분리 강제 시 on-demand 참조 경로 파편화 위험. 줄 수 분포 5구간 권고 (`<30/30~50/50~200/200~500/500+`) — 30줄 이하 인덱스 stub 과다 증가 별도 감지 목적. **redirect stub 실존 2건 확정** (`db-patterns.md`·`doc-harness.md`) — `commit-convention.md`·`hooks-guide.md` 는 root 부재이므로 `process/README.md` "구 root 3개 파일은 redirect 잔존" 문구는 현 상태 불일치 (낮음 심각도 발견사항). CLAUDE.md 인덱스 비율은 "매핑 표 전용 섹션 줄 수" vs "직접 정책 기술 섹션 줄 수" 분리 측정 권고.
- Round 2: 미실행 (Round 1 블로커 0건)
- **결론**: 정량 진단 4항목 — (1) ROOT + backend CLAUDE.md 줄 수 + 매핑 표 vs 직접 정책 기술 분리, (2) `backend/.claude/rules/` 줄 수 분포 5구간, (3) 300줄+ 식별 + 단일·복합 관심사 구분, (4) redirect stub 2건 외부 참조 grep.

### 안건 2: `/governance` 정합 진단 항목 정의 (도메인 4영역 SSOT 관점)

- **호출 에이전트**: `wtp-domain-expert` (Round 1)
- **Round 1 답변 요약**:
  - **wtp-domain-expert**: 4영역 SSOT 복제 탐지에는 **정책 본문 고유 문구** grep 이 유효. `ot-integration.md §5` 의 `SCADA_TIMEOUT`·`OUTBOUND_FAIL`·`MANUAL_EXPIRE`·`SYSTEM_INIT` 5종 사유, `last_rcv_dtm`, `hold last value`, `ai_mode_cd 는 불변` 같은 본문 수준 문구가 `ot-integration.md` 외 등장 시 복제 후보. 짧은 레이블 (`알람 4단계`·`인터록 선행조건`) 은 거짓 양성 높음 — **거짓 양성 구분 기준**: "레이블 + 파일 링크만 있으면 인용 / 임계값·값·시퀀스 본문이 복사되면 복제". Cycle 1 ANALYZE 3섹션 ↔ §5b 1:1 정합 확인. REVIEW1 §개선 제안 1·3 모두 자동 식별 가능 — #1 `grep -n "\\.\\./\\.\\./ot-integration" templates.md`, #3 `grep -n "규모 판단\\|Large" impl.md commit.md` 비교. §2.5 면책 인용 근거 표 행 ↔ 실사용 사례는 `grep -rn "§2\\.5 면책" backend/` 로 검출, 인용 근거 명기 누락은 블로커.
- Round 2: 미실행
- **결론**: 정합 진단 6항목 — (1) 4영역 SSOT 정책 본문 고유 문구 grep, (2) Cycle 1 산출물 정합 (3섹션↔§5b), (3) REVIEW1 #1 경로 깨짐 자동 식별, (4) REVIEW1 #3 규모 판정 갭 자동 식별, (5) §2.5 면책 인용 근거 ↔ 실사용 매칭, (6) `coding-discipline.md` 4원칙 본문 복제 grep.

### 안건 3: 어휘 사전 SSOT 분리 정합 + `feature-dev:code-reviewer` 인용 진단

- **호출 에이전트**: `wtp-glossary-manager` (Round 1)
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: 신규 어휘 0건 — `governance_health_check` 는 슬래시 명령명, 비즈니스 도메인 약어·표준 단어·데이터 도메인·표준 용어 신규 등록 사항 0건. 룰 갱신 지시서 초안 0건. 사전 카탈로그 3표 모두 "없음" + 표 생략 정당 (`templates.md` 하위 호환 조항 적용). ROOT 3층 ↔ backend 1층 SSOT 분리 진단: (a) `DOM_*` 정의 행이 backend `standard-terms.md` 에 등장하지 않는지 grep, (b) 폐기 어휘 (예: `pmp_`·`reg_id`·`tag_id`·`tag_val`) 의 코드 잔존 grep — 매칭 시 블로커. `dict/README.md` 2개(ROOT+backend) 의 정책 본문 복제 grep — `VARCHAR/BIGINT/NUMERIC/LocalDateTime` 매칭 시 본문 복제 신호. **`feature-dev:code-reviewer` 미사용 환경** 진단: `grep -rn "feature-dev:code-reviewer" backend/.claude/commands/` 매칭 시 중간 심각도 보고 — 룰 본문 갱신은 별도 사이클 위임 (자동 수정 없음 정합).
- Round 2: 미실행
- **결론**: 정합 진단 추가 4항목 — (7) ROOT 3층 ↔ backend 1층 SSOT 분리 (DOM_* 정의 행·폐기 어휘 잔존), (8) `dict/README.md` 분리 인덱스 정합 (정책 본문 키워드 미존재), (9) `feature-dev:code-reviewer` 인용 grep, (10) 본 사이클 신규 어휘 0건 검증.

### 안건 4: DB 룰 분리 인덱스 정합 + redirect stub 폐기 후보 진단

- **호출 에이전트**: `wtp-dba-reviewer` (Round 1)
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: `db/README.md` 정책 본문 복제 0건 확인. 자식 3 룰 §번호 ↔ 매핑 표 1:1 6행 정합. `PARTITION BY/BRIN/CONCURRENTLY/executionThreshold/EXPLAIN ANALYZE` 키워드는 자식 3 룰에만 위치 (`entity-patterns.md` 의 `CREATE INDEX CONCURRENTLY` 1건은 N:M 매핑 인덱스 예시 코드, 정책 복제 아님). **`db-patterns.md` redirect stub 폐기 후보 판정 정정**: 현행 `.claude/rules/` 스코프에서 `db-patterns.md §X` 참조 0건이지만 **`docs/` 이력 산출물에 다수 잔존** (PLAN/REVIEW 등 — `docs/plan/20260421/user_jwt_인증/PLAN2.md:68` 외 다수). docs/ 는 immutable 이력이므로 redirect stub 호환 레이어 유지 필요. `/governance` 는 "현행 룰 참조 0건 / docs 이력 참조 N건" **이중 표** 출력 권고 — docs 0 아닌 한 호환 유지 보고. 표준 데이터 도메인 미사용 진단: `DOM_*` 코드 순회 grep, `DOM_AMT_15_2` 가 `standard-terms.md` 사용 사례 0건으로 첫 검출 대상.
- Round 2: 미실행
- **결론**: 정합 진단 추가 2항목 — (11) DB 룰 정책 본문 복제 grep (`PARTITION BY` 등 키워드, 자식 3 외 등장 시 복제 후보 + 정책 지침 vs 예시 코드 구분), (12) redirect stub **현행 룰 참조 / docs 이력 참조 이중 표** 보고. 표준 데이터 도메인 미사용 진단도 추가 (`DOM_*` 코드 순회 grep).

## 표준 사전 카탈로그

> **하위 호환**: 본 사이클은 신규 항목 0건. `templates.md` 의 표 생략 조항 적용.

### 신규 표준 단어
없음

### 신규 표준 데이터 도메인
없음

### 신규 표준 용어
없음

분류값 (3층 공통): 신규 0건 / 기존 재사용 0건 / 유사 충돌 0건 / 폐기·통합 0건

## 신규 엔티티/DB 컬럼

**없음** — 본 사이클은 룰/명령 자산 변경 (Java 소스·DB·테스트 무접촉).

## 기존 사전·패턴과의 충돌

**없음**.

회의 4 라운드 모두 블로커 0건. Round 1 답변에서 발견된 즉시 보고 항목은 본 사이클의 `/governance` 첫 실행 결과로 자연 보고될 예정 (자동 수정 없음 정책 정합):
- (낮음) `process/README.md` "구 root 3개 파일" → 실 1건만 잔존 표기 불일치
- (중간) `dev/plan.md:94`·`dev/review.md:20` `feature-dev:code-reviewer` 인용 — 본 환경 메모리 정책 위배
- (낮음) `DOM_AMT_15_2` `standard-terms.md` 사용 사례 0건
- (낮음) `db-patterns.md` redirect stub `docs/` 이력 참조 N건 (호환 유지 필요, 폐기 후보 아님)

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- **변경 없음** (룰/명령 자산 변경, Java 소스·DB·테스트 무접촉).

### DB 설계 변경 초안
- **변경 없음**.

### 적용할 패턴
1. **`/governance` 슬래시 명령 신설** (`backend/.claude/commands/governance.md`):
   - 사용자 주기 호출 (자동 실행 없음)
   - 정량 진단 4항목 + 정합 진단 12항목 + 즉시 발견사항 4항목 = 보고서 출력
   - 산출물: `docs/governance/{YYYYMMDD}/REPORT.md` (재호출 시 비교 가능)
   - 자동 수정 없음 — 발견사항 보고만
2. **`backend/CLAUDE.md` 인덱스 갱신**:
   - `§규칙 문서 인덱스` 표 또는 별도 섹션에 `/governance` 안내 행 추가
3. **정량 진단 4항목** (안건 1 결론):
   - (Q1) CLAUDE.md 줄 수 (ROOT + backend) + 매핑 표 vs 직접 정책 기술 섹션 분리 측정
   - (Q2) `backend/.claude/rules/` 줄 수 분포 5구간 (`<30/30~50/50~200/200~500/500+`)
   - (Q3) 300줄+ 단일 파일 식별 + "단일 관심사 vs 복합 관심사" 구분 컬럼
   - (Q4) Redirect stub 외부 참조 grep — `db-patterns.md`·`doc-harness.md` 2건 확정
4. **정합 진단 12항목** (안건 2·3·4 결론):
   - (R1) 4영역 SSOT 정책 본문 고유 문구 grep (`SCADA_TIMEOUT`·`OUTBOUND_FAIL`·`MANUAL_EXPIRE`·`SYSTEM_INIT`·`hold last value` 등)
   - (R2) Cycle 1 산출물 정합 (`templates.md` ANALYZE 3섹션 ↔ `dev/plan.md` §5b 검증 1:1)
   - (R3) REVIEW1 §개선 제안 #1 자동 식별 — `grep -n "\\.\\./\\.\\./ot-integration" templates.md`
   - (R4) REVIEW1 §개선 제안 #3 자동 식별 — `grep -n "규모 판단\\|Large" impl.md commit.md` 비교
   - (R5) §2.5 면책 인용 근거 ↔ 실사용 매칭 — `grep -rn "§2\\.5 면책" backend/` (인용 근거 누락은 블로커)
   - (R6) `coding-discipline.md` 4원칙 본문 복제 grep (`Think Before Coding`·`Simplicity First`·`Surgical Changes`·`Goal-Driven Execution` 키워드 외부 등장)
   - (R7) ROOT 3층 ↔ backend 1층 SSOT 분리 (DOM_* 정의 행 검출 + 폐기 어휘 코드 잔존 grep)
   - (R8) `dict/README.md` 2개 분리 인덱스 정합 (`VARCHAR/BIGINT/NUMERIC/LocalDateTime` 정책 본문 키워드 미존재)
   - (R9) `feature-dev:code-reviewer` 인용 grep — `backend/.claude/commands/` 매칭 시 중간 심각도
   - (R10) 본 사이클 신규 어휘 0건 검증 (ANALYZE1 사전 카탈로그 3표 "없음" 확인)
   - (R11) DB 룰 정책 본문 복제 grep (`PARTITION BY/BRIN/CONCURRENTLY/executionThreshold/EXPLAIN ANALYZE` 키워드 자식 3 외 등장 + 정책 지침 vs 예시 코드 구분)
   - (R12) Redirect stub `현행 룰 참조 0건 / docs 이력 참조 N건` 이중 표 + DOM_* 미사용 도메인 식별
5. **거짓 양성 구분 정책**:
   - 정책 본문 복제 판정: "레이블 + 파일 링크만 = 인용 / 임계값·값·시퀀스 본문 = 복제"
   - DB 키워드 복제 판정: "정책 지침 문맥 = 복제 후보 / 예시 코드·테스트 도입 트리거 언급 = 인용"

## 가정 및 미해결 질문

> 본 ANALYZE 의 가정·미해결 질문. ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. 최소 1건 이상 기재 의무.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `/governance` 응답 시간 60초 이내 (plan §검증 방법 기준) — 현 시점 grep 명령 16~20건 추정, 60초 내 충분 | 가정 | PLAN 단계에서 grep 명령 초안 확정 후 시간 측정 검증 (`time` 명령 활용) |
| 보고서 산출물 위치 `docs/governance/{YYYYMMDD}/REPORT.md` 의 디렉토리 1뎁스명 `governance` 가 기존 `docs/{plan,tasks,results,reviews,analyze}/` 5뎁스와 충돌 없음 | 결정 | 신규 디렉토리 추가, doc-harness 5종 산출물 외 6번째 카테고리. `doc-harness/README.md` §디렉토리 구조 갱신 의무 (PLAN 단계 결정) |
| 거짓 양성 구분 자동화 한계 — "정책 지침 문맥 vs 예시 코드" 자동 구분이 grep 만으로 100% 달성 불가, 사람 검토 필수 행이 일부 발생 | 가정 | REPORT 의 발견사항을 "자동 확정 / 사람 검토 필요" 두 카테고리로 분리 출력. PLAN 단계 명시 |
| `process/README.md` "구 root 3개 파일" 표기 불일치는 본 사이클 발견사항이지만 수정은 별도 `/dev` 사이클로 위임 (`/governance` 자동 수정 없음 정책) | 결정 | REPORT 출력 후 사용자 결정 |
| Cycle 1 의 `feature-dev:code-reviewer` 인용 (dev/plan.md:94·dev/review.md:20) 도 동일 — 본 사이클 발견사항으로 보고만, 룰 갱신은 별도 사이클 | 결정 | 본 환경 메모리 정책 (`feedback_no_feature_dev_plugin.md`) + REPORT 출력 |
| `DOM_*` 미사용 도메인 식별 — `DOM_AMT_15_2` 외 다른 미사용 도메인 존재 가능성 (전수 grep 필요) | 미해결 | PLAN 단계에서 grep 결과 확정 후 ANALYZE 결정으로 변환 |

분류값: 가정 / 미해결 / 결정

## 성공 기준 후보 (PLAN 변환 대상)

> 본 섹션의 후보 기준은 PLAN 단계에서 ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 의 "성공 기준 (검증 가능 형태)" 로 변환. **§2.5 면책 적용 배제** — 룰/명령 자산 변경, 면책 영역 아님. 최소 1건 이상 기재 의무.

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `/governance` 슬래시 명령 정상 호출 (governance.md 절차서 작성) | `backend/.claude/commands/governance.md` 파일 존재 + 정량 진단 4항목 + 정합 진단 12항목 grep 매칭 — `grep -c "Q1\\|Q2\\|Q3\\|Q4\\|R1\\|R2\\|R3\\|R4\\|R5\\|R6\\|R7\\|R8\\|R9\\|R10\\|R11\\|R12"` 16 매칭 |
| `/governance` 호출 시 본 ANALYZE1 의 즉시 발견사항 4건 정확 재현 | REPORT 의 발견사항 표 4행 매칭 — `process/README.md` "3개 파일" 불일치 / `feature-dev:code-reviewer` 인용 / `DOM_AMT_15_2` 미사용 / `db-patterns.md` docs 이력 N건 |
| `backend/CLAUDE.md` 에 `/governance` 안내 행 추가 | `grep "/governance" backend/CLAUDE.md` 매칭 1건 이상 |
| 거짓 양성 구분 정책 명문화 (정책 지침 vs 예시 코드, 레이블 vs 본문) | `governance.md` 본문에 "레이블 + 파일 링크" / "임계값·값·시퀀스 본문" 구분 문구 명시 — `grep "레이블\\|정책 지침\\|예시 코드"` 매칭 3건 이상 |
| 보고서 산출물 위치 `docs/governance/{YYYYMMDD}/REPORT.md` 디렉토리 신설 + `doc-harness/README.md` 디렉토리 구조 갱신 | `docs/governance/` 디렉토리 존재 + `doc-harness/README.md` §디렉토리 구조 에 `governance/` 행 추가 |

## 도메인 룰 4영역 점검

> 본 ANALYZE 의 도메인 4영역 (알람 4단계 / 인터록 / AI 운전 모드 / 이력 기록) 해당 여부. 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../../.claude/rules/ot-integration.md) — 알람 4단계 → §5 단독, 인터록 선행조건 → §2·§5, AI 운전 모드 → §5, 이력 기록 의무 → §5.
>
> **"비해당" 단독 4건 차단 해제 조건**: (1) 각 행에 구체 사유 명기 ✅, (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족 ✅. 두 조건 모두 충족하여 통과.

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 사이클은 거버넌스 진단 슬래시 명령 신설 (룰/명령 자산만). 알람 임계값·전이·복귀 조건 변경 0건. SCADA 데이터·진단 모델 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | PLC 제어 명령 무접촉. `pump_interlock_p` 테이블·검사 로직·CircuitBreaker 무접촉. 룰/명령 자산만 변경 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p` 테이블·`ai_mode_cd`·`ai_drvn_mod` 컬럼 무접촉. 사용자 의도/시스템 상태 이중 체계 영향 0건. 강제 모드 전환 (SCADA 5분 초과) 영향 0건 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason` (5종 사유 — `USER_SELECT`·`SCADA_TIMEOUT`·`MANUAL_EXPIRE`·`OUTBOUND_FAIL`·`SYSTEM_INIT`) · `pump_ctrl_h` 무접촉. 본 사이클 산출물 `docs/governance/{YYYYMMDD}/REPORT.md` 는 진단 보고이지 도메인 이력 기록이 아님 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> 본 사이클은 신규 어휘 0건 — ROOT 어휘 사전 (3층) 및 backend 표준 용어 사전 갱신 0건. 변경 대상은 명령 자산 신설 + CLAUDE.md 인덱스 갱신 + doc-harness 디렉토리 구조 갱신 3건만.

- [x] `swtp/backend/.claude/commands/governance.md` — `/governance` 슬래시 명령 절차서 신규 작성 (정량 진단 4항목 + 정합 진단 12항목 + 거짓 양성 구분 정책 + 보고서 산출물 위치)
- [x] `swtp/backend/CLAUDE.md` — `§규칙 문서 인덱스` 표 또는 별도 섹션에 `/governance` 슬래시 명령 안내 행 추가
- [x] `swtp/backend/.claude/rules/process/doc-harness/README.md` — §디렉토리 구조에 `docs/governance/{YYYYMMDD}/REPORT.md` 카테고리 추가 (5종 산출물 외 6번째 카테고리, 사이클 외 진단 산출물 라이프사이클 명시)

## 산출물
- [계획안](../../../plan/20260506/governance_health_check/PLAN1.md) (PLAN 단계 작성 예정)
