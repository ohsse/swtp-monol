# 1.5단계: 도메인 분석 (ANALYZE 문서 작성, 5인 회의 오케스트레이션)

목적 슬러그: $ARGUMENTS

---

이 단계는 Medium/Large 작업의 `/dev:plan` 직전에 수행되며, 신규 등장하는 용어·엔티티·DB 컬럼이
기존 도메인 사전·룰과 정합성을 갖는지 5인 회의 (메인 Claude 오케스트레이터 + 4 에이전트: wtp-dba-reviewer · wtp-backend-engineer · wtp-domain-expert · wtp-glossary-manager)가
회의 형식으로 검토하여 ANALYZE 문서를 산출한다.

---

## 1. 전제조건 검증

1. `$ARGUMENTS`가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:analyze pump_basic_master`" 안내 후 중단
2. 오늘 날짜를 YYYYMMDD 형식으로 확인
3. **Fix Cycle 분기 (조건부 재진입)**:
   - 분기 알고리즘은 [`.claude/rules/process/doc-harness/README.md` §수정 사이클](../../rules/process/doc-harness/README.md#수정-사이클-fix-cycle) 의 "Fix Cycle 감지 알고리즘 (의사 코드)" 박스 단일 정의를 따른다
   - 알고리즘 결과가 `PLAN` → "ANALYZE 재진입 불필요. `/dev:plan $ARGUMENTS` 로 진행" 안내 후 자동 전이
   - 알고리즘 결과가 `ANALYZE` → 본 단계를 계속 진행하여 ANALYZE{N+1} 작성
4. **기존 ANALYZE 문서 탐색**:
   - `docs/analyze/` 하위에서 슬러그 일치 디렉토리 탐색 (날짜 불문)
   - `status: approved` 문서가 이미 존재하면 → "이미 승인됨, `/dev:plan $ARGUMENTS` 진행" 안내 후 자동 전이
   - `status: draft` 또는 `status: review` 문서가 존재하면 → 해당 문서를 이어서 작성
   - 문서가 없으면 → ANALYZE1.md 신규 작성

## 2. 외부 산출물 수집

`docs/analyze/{YYYYMMDD}/$ARGUMENTS/` 디렉토리를 스캔하여 외부 산출물(요구사항 명세서, 도메인 모델링 다이어그램,
시퀀스/클래스 다이어그램 등)을 수집한다. 디렉토리가 없으면 즉시 생성한다.

| 파일 형식 | 처리 방법 |
|----------|----------|
| `.png`, `.jpg`, `.jpeg`, `.gif` | Read 도구로 자동 시각 로드 (Claude 가 직접 해석) |
| `.md`, `.txt` | Read 도구로 자동 텍스트 로드 |
| `.pdf` | Read 도구로 자동 로드 (10페이지 초과 시 `pages` 인자 명시) |
| `.docx` | **`read-docx` 스킬 자동 호출** — `python .claude/skills/read-docx/scripts/extract_docx.py "{파일경로}"` 를 Bash 로 실행하여 Markdown 으로 추출한 뒤 ANALYZE "## 작업 배경" 에 인용. 종료 코드가 `2` (파일 손상·미지원 형식) 이면 아래 사용자 개입 안내로 폴백 |
| `.xlsx`, `.pptx` | **사용자 개입 지점** — 아래 안내 후 대기 (전용 읽기 스킬 미도입) |

**사용자 개입 안내 메시지 예시 (`.xlsx` / `.pptx` 또는 `.docx` 추출 실패 시):**

```
다음 외부 산출물은 자동 읽기 도구가 직접 지원하지 않습니다:
  - docs/analyze/{YYYYMMDD}/{슬러그}/엑셀명세.xlsx

다음 중 한 가지 방법으로 입력해 주세요:
  (a) 파일을 .md 로 변환해 같은 디렉토리에 저장 (예: 엑셀명세.md)
  (b) 파일의 핵심 내용을 채팅창에 텍스트로 paste

(a) 를 선택하셨다면 변환 완료 후 "계속" 이라고 입력해 주세요.
```

사용자 응답 대기 → 변환된 .md 가 있으면 자동 진행 / paste 된 텍스트는 ANALYZE 의 "## 작업 배경" 섹션에 인용.

## 3. 사전 분석 (메인 Claude 단독 수행)

회의 호출 전에 메인 Claude(오케스트레이터) 가 단독으로 안건 후보를 추출한다.

1. **명사구·약어 후보 추출** — 사용자 요청문 + 외부 산출물에서 다음 후보를 **3층 구조** 로 식별:
   - 신규 **비즈니스 도메인명** (업무 영역 — 예: `pump`, `scada`, `alarm`)
   - 신규 **표준 단어** (일반 의미 단위 — 예: `nm`, `dt`, `oper`, `stat`)
   - 신규 **표준 데이터 도메인** (값 형식 — 예: `DOM_OPER_STAT_20`, 또는 신규 SQL 타입·길이 조합)
   - 신규 **표준 용어** (DB 컬럼명 — 예: `pump_oper_dtm`, `interlock_rule_p.suction_pressure_min`)
   - 신규 비즈니스 용어 (도메인 개념 — 예: "흡입 압력", "기동 인터록")

   > **"도메인" 용어 구분 필수**: "비즈니스 도메인"(업무 영역) 과 "데이터 도메인"(값 형식) 은 다른 개념. 혼용 금지.

2. **기존 사전 1차 대조** — 다음 위치를 grep/Read 로 대조:
   - `swtp/.claude/rules/dict/standard-words.md` (ROOT 표준 단어)
   - `swtp/.claude/rules/dict/standard-data-domains.md` (ROOT 표준 데이터 도메인)
   - `swtp/.claude/rules/dict/domain-abbreviations.md` (ROOT 비즈니스 도메인 약어)
   - `.claude/rules/dict/standard-terms.md` (backend 표준 용어 — DB 컬럼명)
   - `.claude/rules/naming.md` (DB suffix 규칙)
   - `.claude/rules/ot-integration.md` (센서 코드)
   - `.claude/rules/multi-tenant.md` (지자체 코드)
   - `common/src/main/java/com/mo/swtp/` 패키지 디렉토리 트리 (기존 비즈니스 도메인)
   - 기존 엔티티 클래스명 (`@Entity` 또는 `@Table` grep)

3. **안건 후보 목록 생성** — 안건 1건은 다음 세 요소로 구성:
   ```
   안건 N: {제목 한 줄}
   - 키워드 분류: 용어/DB/패턴/도메인 (호출 에이전트 결정용)
   - 배경: {왜 안건이 필요한지 한두 줄}
   - 질문: {판단 권한자에게 물어볼 구체 질문}
   ```

## 4. 5인 회의 진행 (토픽 주도)

각 안건마다 다음 라운드를 실행한다.

### 4.1 안건 키워드 → 호출 에이전트 매핑

| 안건 키워드 | 필수 호출 | 선택 호출 |
|------------|----------|----------|
| 신규 표준 단어 / 표준 용어 / 비즈니스 도메인 약어 / 네이밍 충돌 | `wtp-glossary-manager` | `wtp-domain-expert` |
| 신규 표준 데이터 도메인 (SQL 타입·길이·NULL 정책) | `wtp-glossary-manager` + `wtp-dba-reviewer` (2차 승인) | — |
| DB 테이블 / 컬럼 / 인덱스 / 파티션 | `wtp-dba-reviewer` | — |
| 엔티티 패턴 / Service 구조 / 계층 책임 / Lombok | `wtp-backend-engineer` | — |
| 알람 4단계 / 인터록 / 운전 모드 / SCADA | `wtp-domain-expert` | — |
| 외부 산출물(요구사항·다이어그램) 정합성 | 4개 에이전트 모두 | — |

### 4.2 라운드 알고리즘

```
[Round 1]
  오케스트레이터(메인 Claude):
    안건 N 의 키워드 분류 확인
    → 매핑 표에서 필수 호출 에이전트 선택
    → Agent 도구로 호출 (subagent_type 에 에이전트 name 지정)
    → 프롬프트에 "단답형 200~400단어, 결론과 근거만" 명시
    → 외부 산출물 인용 필요 시 파일 경로 첨부
    → 응답 수집

[Round 2]  ← 이견 / 블로커 발견 시만 실행
  오케스트레이터:
    Round 1 답변에서 블로커(높음) 또는 다른 에이전트 검토 필요 항목 식별
    → 매핑 표의 선택 호출 에이전트 또는 다른 필수 에이전트 추가 호출
    → 응답 수집 → 종합

[종결]
  오케스트레이터가 안건의 결론 1~2문장으로 확정
  ANALYZE.md "## 회의록" 섹션의 안건 N 블록에 라운드별 답변 요약과 결론 기록
```

### 4.3 회의 진행 원칙

- **에이전트 간 직접 대화 없음** — 항상 오케스트레이터가 중계
- **단답형 강제** — 모든 에이전트 호출 프롬프트에 "200~400단어 압축" 명시
- **외부 산출물 정합성 안건은 4개 에이전트 모두 1라운드 호출** — 각자 자신의 관점에서 외부 산출물(요구사항·다이어그램)을 평가
- **블로커가 발견되면 회의 중단 없이 ANALYZE 의 "## 기존 사전·패턴과의 충돌" 섹션에 기록** 후 다음 안건 진행
- **모든 안건이 끝나면 종합 결론** — 룰 갱신 지시서로 변환

## 5. 결론 종합 + ANALYZE{N}.md 작성

회의 결과를 ANALYZE 템플릿에 따라 정리한다. 템플릿은 [`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) 의 `ANALYZE{n}.md` 블록(SSOT) 참조.

작성 시 채워야 할 섹션:

1. **## 작업 배경** — 요청 요약 + 외부 산출물 링크
2. **## 회의록** — 안건별 라운드/답변 요약 + 결론
3. **## 표준 사전 카탈로그** — 3개 표 (신규 표준 단어 / 신규 표준 데이터 도메인 / 신규 표준 용어). 분류값: 신규/기존 재사용/유사 충돌/폐기·통합. 해당 층위에 신규 항목이 없으면 "없음" 으로 표기하고 표 생략 가능 (하위 호환 — 과거 단일 "## 신규 용어 카탈로그" 문서는 재작성 불필요)
4. **## 신규 엔티티/DB 컬럼** — 비즈니스 도메인 패키지·suffix·인덱스 후보·NULL 정책·사용 데이터 도메인(`DOM_*`)
5. **## 기존 사전·패턴과의 충돌** — 충돌 항목 + 회의 결론과 일치하는 해소책
6. **## PLAN 으로 전달할 결정 사항** — 도메인 모델 초안 / DB 설계 변경 초안 / 적용할 패턴
7. **## 룰 갱신 지시서** — `wtp-glossary-manager` 가 작성한 체크박스 초안 + 오케스트레이터 검수
   - 형식: `- [ ] {룰파일 전체경로 백틱} — {갱신 내용 한 줄}`
   - 축약 경로·글롭 패턴 금지 ([`.claude/rules/process/doc-harness/checkbox-rules.md`](../../rules/process/doc-harness/checkbox-rules.md) 준수)
   - 4층 사전 신규 등록 시 ROOT 어휘(`swtp/.claude/rules/dict/standard-words.md` · `standard-data-domains.md` · `domain-abbreviations.md`) 와 backend 표준 용어(`.claude/rules/dict/standard-terms.md`) 경로를 포함

## 6. 사용자 승인 + 자동 전이

ANALYZE{N}.md 작성 완료 후 절차는 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:analyze` 행을 따른다.

요약:
1. `status: draft` → `status: review` 로 변경
2. 사용자에게 문서 경로와 "## PLAN 으로 전달할 결정 사항" 핵심 요약을 함께 제시하여 검토 요청
3. 승인 요건 — 모든 룰 갱신 지시서 체크박스가 `- [x]` 완료 + 사용자 승인 → `status: approved` 전환 후 `/dev:plan $ARGUMENTS` **자동 실행**
4. 룰 갱신 지시서가 미완료 상태로 승인 요청 시 → "다음 항목이 미체크 상태입니다: ..." 안내 후 대기 (PLAN 의 §5b 게이트와 동일)
