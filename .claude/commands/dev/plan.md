# 2단계: 계획 수립 (PLAN 문서 작성)

목적 슬러그: $ARGUMENTS

---

## 전제조건 검증
1. `$ARGUMENTS`가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:plan jwt_인증_추가`" 안내 후 중단
2. 오늘 날짜를 YYYYMMDD 형식으로 확인
3. **Fix Cycle 모드 감지** — `docs/reviews/` 하위 슬러그 일치 가장 최신 REVIEW 의 status·블로커 분석:
   - 분기 알고리즘은 [`.claude/rules/process/doc-harness/README.md` §수정 사이클](../../rules/process/doc-harness/README.md#수정-사이클-fix-cycle) 의 "Fix Cycle 감지 알고리즘 (의사 코드)" 박스 단일 정의를 따른다
   - Fix Cycle 모드 진입 시: 기존 PLAN 중 가장 큰 번호 N 파악 → PLAN{N+1}.md 생성, "배경" 섹션에 REVIEW{N} 블로커 참조 → 아래 "계획 문서 작성" 단계로 바로 진행
   - **§5a 사전 판별**(Small 작업 보호 / Fix Cycle 키워드 미포함 스킵)은 PLAN 단계 고유 책임으로 유지된다 (아래 §5a 참조)
4. `docs/plan/` 하위에서 슬러그 일치 디렉토리 탐색 (Fix Cycle 모드가 아닌 경우):
   - `status: approved` 또는 `status: completed` 문서가 이미 존재하면 → "계획이 이미 승인되었습니다. `/dev:task $ARGUMENTS`를 실행하세요." 안내 후 중단
   - `status: draft` 또는 `status: review` 문서가 존재하면 → 해당 문서를 이어서 작성
   - 다른 날짜 디렉토리에 같은 슬러그의 문서가 있는지도 확인

5. **ANALYZE 게이트 검증** (Medium/Large 작업의 필수 전제조건)

   ### 5a. 사전 판별 — 게이트 스킵 분기

   본 §5 게이트 본문(§5b) 으로 진입하기 전에 다음 두 분기를 먼저 평가한다. 어느 하나라도 매칭되면 §5b 를 스킵한다.

   - **분기 A (Small 작업 보호)**: `docs/analyze/` 하위에서 슬러그 일치 디렉토리를 탐색하여 디렉토리가 없고 같은 슬러그의 ANALYZE 이력도 없으면 → Small 작업으로 간주하고 본 §5 게이트 스킵.
     - 정상 자동 전이 흐름은 항상 `/dev:analyze` 가 선행되므로 ANALYZE 디렉토리가 존재한다. 본 분기는 사용자가 `/dev:plan` 을 직접 호출하거나 Fix Cycle 에서 Small 로 재분류된 엣지 케이스를 보호하기 위함.
   - **분기 B (Fix Cycle 키워드 미포함 스킵)**: Fix Cycle 모드(상기 §3) 이고 직전 REVIEW 블로커 텍스트에 도메인 정합성 키워드([`.claude/rules/process/doc-harness/README.md` §ANALYZE 조건부 재진입](../../rules/process/doc-harness/README.md#analyze-조건부-재진입) 의 키워드 목록) 가 하나도 포함되지 않으면 → ANALYZE 재작성 불요로 간주하고 본 §5 게이트 스킵.
     - 본 로직은 `dev.md` §Fix Cycle 감지 의 "ANALYZE 조건부 재진입" 분기와 동일하다.

   ### 5b. ANALYZE 게이트 본문 (§5a 분기 미해당 시)

   - `docs/analyze/` 하위에서 슬러그 일치 디렉토리 탐색
   - ANALYZE 문서가 없으면 → "ANALYZE 문서가 없습니다. `/dev:analyze $ARGUMENTS`를 먼저 실행하세요." 안내 후 중단
   - 가장 최신 ANALYZE 문서가 `status: approved` 가 아니면 → "ANALYZE 문서가 승인되지 않았습니다. 검토 후 `status: approved` 로 변경해 주세요." 안내 후 중단
   - **룰 갱신 지시서 체크박스 검증**: ANALYZE 문서의 "## 룰 갱신 지시서" 섹션에서 모든 체크박스가 `- [x]` 로 완료되었는지 확인
     - 미체크(`- [ ]`) 항목이 하나라도 있으면 → "ANALYZE 의 룰 갱신을 먼저 완료하세요. 미체크 항목: {목록}" 안내 후 중단
   - 본 게이트는 Fix Cycle 모드에서도 동일하게 적용한다 (직전 REVIEW 의 블로커가 도메인 정합성 항목이라 ANALYZE{N+1} 이 추가 작성된 경우 그 문서 기준)
   - **ANALYZE 3섹션 자율 차단** (메인 Claude 자율 차단 — 자동 차단 훅 미신설, ROOT [`coding-discipline.md §7.1`](../../../../.claude/rules/coding-discipline.md) 보류 결정 유지):
     - **"## 가정 및 미해결 질문"** 섹션이 부재하거나 표 본문이 비어있거나 "없음" 단독 행만 있으면 → "ANALYZE 의 가정 섹션에 최소 1건 이상 기재가 필요합니다 (없음 단독 금지)" 안내 후 중단
     - **"## 성공 기준 후보 (PLAN 변환 대상)"** 섹션이 부재하거나 표 본문 행이 1건 미만이면 → "ANALYZE 의 성공 기준 후보 섹션이 필요합니다" 안내 후 중단. "후보 기준" 컬럼 행 중 다음 모호 표현 정규식 8건 단독 행이 1건이라도 매칭되면 → "성공 기준 후보가 모호합니다 — 검증 명령·테스트·조회를 동반 기재해야 합니다" 안내 후 중단:
       - `^성능 개선$`
       - `^안정성 향상$`
       - `^개선$`
       - `^향상$`
       - `^기능 추가$`
       - `^코드 개선$`
       - `^리팩토링$`
       - `^문서화$`
     - **"## 도메인 룰 4영역 점검"** 섹션이 부재하거나 4행 (알람 4단계 / 인터록 / AI 운전 모드 / 이력 기록) 모두 채워지지 않았으면 → "ANALYZE 의 4영역 점검 섹션 4행이 모두 필요합니다" 안내 후 중단. 4행 모두 "비해당" 단독이면 **차단 해제 조건** — (1) "근거 또는 영향" 컬럼 4행 모두 구체 사유 명기 (공란·`확인 필요` 수준 아님), (2) "## 신규 엔티티/DB 컬럼" 섹션 본문이 "없음" 으로 시작 — 동시 충족 시에만 통과. 미충족 시 → "비해당 단독 4건은 사유 병기 + '## 신규 엔티티/DB 컬럼' 섹션 '없음' 동시 충족 시에만 통과합니다" 안내 후 중단
   - **PLAN 자율 차단 vs REVIEW 자동 점검 책임 경계** (중복 검증 회피): PLAN 자율 차단 책임 = ANALYZE 3섹션 존재 + 모호 표현 단독 행 + "비해당" 차단 해제 분기. REVIEW 자동 점검 책임 (`wtp-backend-engineer` / `wtp-domain-expert`) = 각 에이전트의 §검토 항목 SSOT 참조 — [`wtp-backend-engineer.md`](../../agents/wtp-backend-engineer.md) §검토 항목 **7·8·9·10·11번** (정량 기준 / TASK 외 파일 변경 / 체크박스 검증 누락 / 데드 코드 직접 삭제 / 경로 표기 정합성) + [`wtp-domain-expert.md`](../../agents/wtp-domain-expert.md) §검토 항목 **4·5·6번** (가정 섹션 도메인 가정 점검 / 경로 표기 정합성 / 4영역 섹션 자체 점검). 같은 문서 같은 항목을 양쪽에서 검증하지 않는다 — 같은 항목 중복 검증 시 Fix Cycle 불필요 유발 위험.

## 계획 문서 작성

**plan 모드**에서 코드베이스를 충분히 분석한 뒤 다음 경로에 문서를 생성한다:
- 경로: `docs/plan/{YYYYMMDD}/$ARGUMENTS/PLAN1.md`
- 기존 문서가 있다면 번호를 증가 (PLAN2.md, PLAN3.md...)

**⚠️ 필수: PLAN 문서는 반드시 `docs/plan/` 경로에 파일로 생성해야 한다.**

plan 모드(Claude의 내장 plan 파일)는 대화 내 임시 작업 도구이다.
`docs/plan/` 경로의 PLAN 문서는 프로젝트에 영구 보존되는 공식 산출물이다.

- plan 모드를 사용하여 계획을 수립하는 것은 자유이다.
- 그러나 plan 모드의 결과물을 **반드시 `docs/plan/{YYYYMMDD}/$ARGUMENTS/PLAN1.md`에 작성**해야 한다.
- plan 모드 파일에만 내용을 남기고 `docs/plan/` 파일을 생성하지 않으면 **후속 단계에서 차단**된다.

**작성 전 분석 내용:**
- 관련 도메인의 기존 코드 구조 파악 (Controller/Service/Repository/Entity)
- 영향받는 모듈(common/api/scheduler) 식별
- 유사 구현이 이미 존재하는지 확인 (재사용 가능한 코드)
- DB 스키마 변경 필요 여부

**PLAN 문서 템플릿**: 단일 게시 위치는 [`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) 의 `PLAN{n}.md` 블록(SSOT) 이다. 본 스킬은 그 템플릿을 그대로 사용하며 내용을 복제하지 않는다.

본 스킬이 추가로 보강해야 하는 섹션 (PLAN 단계 고유):
- `## 도메인 모델` — 신규 엔티티·DTO·컬럼이 있는 경우 `| 엔티티/테이블 | 역할 | 주요 필드 |` 표 작성
- `## DB 설계 변경` — 스키마 변경이 있는 경우 변경 대상 테이블·변경 유형·무중단 마이그레이션 전략 기술 ([`db/README.md`](../../rules/db/README.md) 진입 후 자식 룰 적용)

**작성 기준:**
- CLAUDE.md 코딩 규칙 반영 (Java 21, Lombok, SOLID, 패키지 구조)
- `.claude/rules/naming.md` 네이밍 컨벤션 준수
- 테스트 전략: 영향받는 모듈의 `./gradlew.bat :{module}:test` 범위 명시

## 도메인·DB 검토 게이트

PLAN 초안 작성이 완료되면, 다음 조건에 해당할 경우 서브에이전트 검토를 실행한다.

**검토 필요 조건:**
- `## 도메인 모델` 섹션에 신규 엔티티·테이블·필드가 기재된 경우 → **wtp-domain-expert** 검토
- `## DB 설계 변경` 섹션에 내용이 기재된 경우 → **wtp-dba-reviewer** 검토

**검토 실행 방법:**

메인 Claude 가 Agent 도구로 다음 에이전트를 직접 호출한다 (`feature-dev` 플러그인 미설치 — wtp-\* 에이전트 직접 사용).

- 도메인 모델 신규/변경 시 → `subagent_type="wtp-domain-expert"`
- DB 설계 변경 시 → `subagent_type="wtp-dba-reviewer"`

호출 프롬프트는 PLAN 문서 경로 + 검토 대상 섹션 명시 + 단답형 200~400단어 강제. 각 에이전트는 본인의 §검토 시작 전 필수 파일 읽기 목록을 자체 Read 한다.

**검토 결과 처리:**
- 블로커(높음) 발견 시: PLAN 문서 `## 배경` 또는 해당 섹션에 지적 사항 반영 후 재작성
- 블로커 없음: PLAN 문서에 검토 결과를 **부록**으로 첨부

```markdown
## 부록: 도메인/DB 검토 결과
- wtp-domain-expert: 블로커 0건, 권고 N건 (상세 생략 또는 간략 요약)
- wtp-dba-reviewer: 블로커 0건, 권고 N건
```

도메인 모델과 DB 변경이 모두 없는 경우에는 검토 게이트를 생략하고 바로 검토 요청 단계로 진행한다.

## 검토 요청

문서 작성 완료 후 절차는 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:plan` 행을 따른다.

요약: `status: draft → review` 전환 → 사용자 검토 요청 → 승인 시 `status: approved` 전환 후 `/dev:task $ARGUMENTS` **자동 실행**.
