# 거버넌스 진단 슬래시 명령 (`/governance`)

ROOT/backend 분산 SSOT 모델의 **정량 진단** + **정합 진단** 을 자동화하는 사용자 주기 호출 슬래시 명령이다.

---

## 핵심 정책

- **사용자 주기 호출** — 자동 실행 없음. 사용자가 명시적으로 `/governance` 를 호출해야 진단이 시작된다
- **자동 수정 없음** — 발견사항 보고만. 실제 수정은 사용자가 별도 `/dev` 사이클로 결정한다
- **응답 시간 60초 이내** — grep 명령 16~20건 실행 후 보고서 출력
- **거짓 양성 구분 정책** 적용 (아래 §거짓 양성 구분 참조)

## 산출물 위치

```
backend/docs/governance/{YYYYMMDD}/REPORT.md
```

- 1뎁스 `governance` 는 5종 산출물(`plan/tasks/results/reviews/analyze`) 외 **6번째 카테고리**
- **status frontmatter 미사용** — 사이클 외 진단 산출물이므로 doc-harness 5종 라이프사이클 적용 외, 단순 마크다운 보고서
- **재호출 시 누적** — 동일 날짜 재호출 시 동일 파일 덮어쓰기, 다른 날짜 호출 시 새 디렉토리 생성

상세 라이프사이클은 [`process/doc-harness/README.md` §디렉토리 구조](../rules/process/doc-harness/README.md) 참조.

---

## 진입 절차

### 1. 사용자 호출 확인

`$ARGUMENTS` 가 비어있어도 진행 (슬러그 인자 불필요). 사용자가 `/governance` 단독 호출 시 시작.

### 2. 보고서 디렉토리 확보

```bash
mkdir -p backend/docs/governance/{YYYYMMDD}
```

### 3. 정량 진단 4항목 (Q1~Q4) 실행

#### Q1 — CLAUDE.md 줄 수 측정 + 매핑 표 vs 직접 정책 기술 분리

```bash
wc -l swtp/CLAUDE.md backend/CLAUDE.md
```

**섹션별 분류 알고리즘**:
- `## §` 헤더 단위로 본문 추출
- 본문의 **표 형식 라인 (`|...|`) 비율 80% 이상** → "매핑 표 전용 섹션"
- 80% 미만 → "직접 정책 기술 섹션"
- 직접 정책 기술 섹션이 다른 룰 파일에 동일 내용 1차 정의가 있으면 **"복제 의심" 플래그**

> 임계값 80% 는 dogfood 결과로 조정 가능 (낮은 거짓 양성률 vs 누락 균형).

#### Q2 — 룰 파일 줄 수 분포 5구간

```bash
find backend/.claude/rules -name "*.md" -exec wc -l {} \;
```

분포 카테고리:
- `< 30` — 진입점 인덱스 stub (과다 증가 별도 감지)
- `30 ~ 50` — 소형 단일 패턴
- `50 ~ 200` — 중형 단일 주제
- `200 ~ 500` — 분리 검토 후보
- `500 +` — 분리 의무 후보 (현재 0건, 미래 경보용)

#### Q3 — 300줄 이상 단일 파일 식별 + 단일 관심사 vs 복합 관심사 구분

```bash
find backend/.claude/rules -name "*.md" -exec wc -l {} \; | awk '$1 >= 300'
```

각 후보 파일에 대해 **본문 구조 분석**:
- 파일 내 `## ` 헤더 수가 5개 이상 + 헤더 간 주제가 이질적 → **복합 관심사** (분리 후보)
- `## ` 헤더 수가 5개 미만 또는 단일 주제 심화 → **단일 관심사** (분리 강제 시 on-demand 참조 파편화 위험)

현 시점 후보 3건:
- `templates.md` 376줄 → 5종 템플릿(이질적 관심사) → **복합 관심사** (분리 후보)
- `test-strategy.md` 343줄 → §1~§6 단계적 심화 → **단일 관심사** (분리 보류 권고)
- `entity-patterns.md` 318줄 → 패턴 계층 누적 → **단일 관심사** (분리 보류 권고)

#### Q4 — Redirect stub 외부 참조 grep

확정된 redirect stub **2건**:
- `backend/.claude/rules/db-patterns.md`
- `backend/.claude/rules/doc-harness.md`

```bash
# 현행 룰 스코프 참조
grep -rn "db-patterns\.md\|doc-harness\.md" backend/.claude/rules/ \
    --exclude="db-patterns.md" --exclude="doc-harness.md"

# docs/ 이력 산출물 스코프 참조
grep -rn "db-patterns\.md\|doc-harness\.md" backend/docs/
```

**이중 표 출력**:
| Redirect Stub | 현행 룰 참조 | docs 이력 참조 | 폐기 후보 여부 |
|--------------|------------|--------------|--------------|
| `db-patterns.md` | N건 | M건 | M=0 일 때만 후보 |
| `doc-harness.md` | N건 | M건 | M=0 일 때만 후보 |

> **docs 이력 참조 N>0 인 한 redirect stub 호환 유지 필요** — 폐기 후보 단정 부적절 (immutable 이력 산출물).

---

### 4. 정합 진단 12항목 (R1~R12) 실행

#### R1 — 도메인 4영역 SSOT 정책 본문 고유 문구 grep

`ot-integration.md §5` 가 SSOT 인 4영역 정책 본문이 외부 등장 시 복제 후보:

```bash
grep -rn "SCADA_TIMEOUT\|OUTBOUND_FAIL\|MANUAL_EXPIRE\|SYSTEM_INIT\|hold last value\|last_rcv_dtm" \
    backend/.claude/rules/ \
    --exclude="ot-integration.md"
```

**거짓 양성 구분**:
- 짧은 레이블 (`알람 4단계`·`인터록 선행조건`) 은 **인용** 으로 분류 (거짓 양성 높음)
- 정책 본문 수준 문구 (위 6 키워드) 가 본문에 등장 시 **복제 후보**

#### R2 — Cycle 1 산출물 정합 (templates.md ANALYZE 3섹션 ↔ dev/plan.md §5b 1:1)

```bash
# templates.md 3섹션 존재 확인
grep -E "## 가정 및 미해결 질문|## 성공 기준 후보|## 도메인 룰 4영역 점검" \
    backend/.claude/rules/process/doc-harness/templates.md

# dev/plan.md §5b 자율 차단 3개 항목 존재 확인
grep -E "가정.*없음 단독|성공 기준 후보.*모호|비해당.*차단 해제" \
    backend/.claude/commands/dev/plan.md
```

매칭 카운트가 **양쪽 모두 3건 이상** 시 정합. 미달 시 발견사항 보고.

#### R3 — REVIEW1 §개선 제안 #1 자동 식별 (templates.md 깨진 인용 경로)

```bash
grep -n "\.\./\.\./ot-integration" backend/.claude/rules/process/doc-harness/templates.md
```

`templates.md` 위치 기준 `../../ot-integration.md` 는 `backend/.claude/ot-integration.md` (미존재) 를 가리키므로 깨진 경로. 매칭 시 발견사항 (낮음 심각도) 보고.

#### R4 — REVIEW1 §개선 제안 #3 자동 식별 (impl.md vs commit.md 규모 판단 갭)

```bash
grep -nE "규모 판단|Large|RESULT|예상 산출물" \
    backend/.claude/commands/dev/impl.md \
    backend/.claude/commands/dev/commit.md
```

**비교 알고리즘**:
- `impl.md` 의 규모 판단: "PLAN/TASK 둘 다 없으면 Small, 있고 RESULT 가 예상 산출물에 포함되면 Large"
- `commit.md` 의 규모 판단: "RESULT 또는 REVIEW 문서가 있으면 Large"
- 두 기준 텍스트가 다르면 **갭 발견** 으로 보고 (낮음 심각도)

#### R5 — §2.5 면책 인용 근거 ↔ 실사용 매칭

```bash
grep -rn "§2\.5 면책" backend/
```

매칭된 코드 주석 각각에 대해:
- 인용 근거 (`ot-integration.md §X` 또는 `query-tuning.md §X`) **명기 여부** 확인
- 명기 누락 시 **블로커** 등급 보고
- 면책 영역 표 행 (총 2건) 이 실사용 사례 0건이면 **권고** 등급 보고 (`coding-discipline.md §7.1` 트리거 누적 카운트)

#### R6 — coding-discipline.md 4원칙 본문 복제 grep

```bash
grep -rn "Think Before Coding\|Simplicity First\|Surgical Changes\|Goal-Driven Execution" \
    backend/.claude/rules/ \
    --exclude="coding-discipline.md"
```

ROOT `coding-discipline.md` §1~§4 의 영문 원칙명이 backend 룰 파일에 등장 시 복제 후보. 인용 (링크 포함) 은 거짓 양성, 본문 복사는 복제.

#### R7 — ROOT 3층 ↔ backend 1층 어휘 SSOT 분리

**(7-a) DOM_* 정의 행 검출**:
```bash
grep -nE "^\| \`DOM_[A-Z_0-9]+\`" backend/.claude/rules/dict/standard-terms.md
```
backend `standard-terms.md` 에 `DOM_*` **정의 행** (표 첫 컬럼이 `\`DOM_*\``) 이 등장하면 SSOT 이탈 (ROOT `standard-data-domains.md` 만 정의 가능). 정의 행 0건 = 정합.

**(7-b) 폐기 어휘 코드 잔존 grep**:
```bash
grep -rnE "\\bpmp_|\\breg_id\\b|\\btag_id\\b|\\btag_val\\b|\\bcntom\\b" \
    backend/api/src backend/common/src backend/scheduler/src
```
폐기 어휘 5건 (`pmp_`·`reg_id`·`tag_id`·`tag_val`·`cntom`) 의 코드 잔존 매칭 시 **블로커** 보고.

#### R8 — dict/README.md 분리 인덱스 정합

```bash
grep -nE "VARCHAR|BIGINT|NUMERIC|LocalDateTime|TIMESTAMP" \
    swtp/.claude/rules/dict/README.md \
    backend/.claude/rules/dict/README.md
```

ROOT 와 backend `dict/README.md` 모두 진입점·매핑 표만 보유해야 함. 위 5 키워드 (정책 본문 시그니처) 매칭 시 정책 복제 신호 → 권고 등급 보고.

#### R9 — feature-dev:code-reviewer 인용 grep

```bash
grep -rn "feature-dev:code-reviewer" backend/.claude/commands/
```

본 환경은 `feature-dev` 플러그인 **미설치** (메모리 정책 — `feedback_no_feature_dev_plugin.md`). 매칭 시 **중간 심각도** 발견사항 보고. 룰 본문 갱신은 별도 사이클로 위임 (자동 수정 없음 정합).

#### R10 — 신규 어휘 0건 검증 (ANALYZE 사전 카탈로그 정합)

```bash
# 최신 ANALYZE 의 사전 카탈로그 3표 "없음" 표기 정합
ls -t backend/docs/analyze/*/*/ANALYZE*.md | head -1 | xargs grep -A2 "신규 표준 단어\|신규 표준 데이터 도메인\|신규 표준 용어"
```

ANALYZE 의 사전 카탈로그 3표가 모두 "없음" 으로 표기된 경우, 해당 사이클 코드 변경에서 새 어휘 등록이 발생하지 않았는지 git diff 로 교차 검증 권고.

#### R11 — DB 룰 정책 본문 복제 grep

```bash
grep -rnE "PARTITION BY|BRIN|CONCURRENTLY|executionThreshold|EXPLAIN ANALYZE" \
    backend/.claude/rules/ \
    --exclude-dir=db
```

`db/` 자식 3 룰 외 위 5 키워드 매칭 시 정책 복제 후보. **거짓 양성 구분**:
- **정책 지침 문맥** ("BRIN 인덱스 사용", "CONCURRENTLY 로 무중단 생성" 등 지침형) → **복제 후보**
- **예시 코드 주석** (`entity-patterns.md` 의 `// CREATE INDEX CONCURRENTLY idx_{테이블}_{컬럼}` 같은 코드 예시) → **인용**
- **테스트 도입 트리거 언급** (`test-strategy.md` 의 "BRIN 인덱스 실효성 검증" 등) → **인용**

#### R12 — Redirect stub 이중 표 + DOM_* 미사용 도메인 식별

**(12-a) Redirect stub 이중 표** — Q4 결과 재사용 (현행 룰 vs docs 이력 분리)

**(12-b) DOM_* 미사용 도메인 식별**:
```bash
# ROOT 표준 데이터 도메인 표의 DOM_* 코드 추출
grep -oE "DOM_[A-Z_0-9]+" swtp/.claude/rules/dict/standard-data-domains.md | sort -u

# 각 DOM_* 코드별 backend standard-terms.md 사용 사례 카운트
for dom in $(추출 결과); do
    cnt=$(grep -c "$dom" backend/.claude/rules/dict/standard-terms.md)
    echo "$dom: $cnt 사용 사례"
done
```

사용 사례 0건 = 미사용 도메인 → 권고 등급 보고. 현 시점 첫 검출 대상: `DOM_AMT_15_2`.

---

## 거짓 양성 구분 정책

진단 항목별 매칭 결과를 **두 카테고리로 분리** 출력:

### 자동 확정 (확실한 발견사항)

다음 패턴은 자동으로 발견사항 표에 기록:
- **레이블 + 파일 링크만 = 인용 (거짓 양성, 매칭 무시)**
- **임계값·값·시퀀스 본문이 복사 = 복제 (자동 확정)**
- **예시 코드·테스트 도입 트리거 언급 = 인용 (거짓 양성, 매칭 무시)**
- **정책 지침 문맥 = 복제 후보 (자동 확정)**

### 사람 검토 필요 ⚠️

자동 분류가 모호한 행은 ⚠️ 마커와 함께 별도 표로 출력:
- 짧은 매칭 컨텍스트로 인용/복제 구분 불가
- 키워드 매칭 + 주변 문맥이 양가적

REPORT 작성자(메인 Claude 또는 사용자)가 검토 후 최종 분류.

---

## 보고서 형식

`backend/docs/governance/{YYYYMMDD}/REPORT.md` 출력 형식:

```markdown
# 거버넌스 진단 보고서 ({YYYY-MM-DD})

## 요약

- 정량 진단 4항목 / 정합 진단 12항목 실행
- 발견사항: 자동 확정 N건 / 사람 검토 필요 M건
- 응답 시간: real {N}s

## 정량 진단 결과

### Q1 ~ Q4 (각 항목별 수치 + 관찰)

## 정합 진단 결과

### R1 ~ R12 (각 항목별 매칭 결과 + 거짓 양성 구분 후 발견사항)

## 발견사항 표

### 자동 확정

| 심각도 | 항목 | 위치 | 내용 |
|--------|------|------|------|

### 사람 검토 필요 ⚠️

| 심각도 | 항목 | 위치 | 내용 | 검토 권고 |
|--------|------|------|------|---------|

## 응답 시간 측정

- 명령 실행 시간: `time` 명령 결과 real {N}s

## 거짓 양성 구분 사례

(예: entity-patterns.md 의 CREATE INDEX CONCURRENTLY 1건 → 예시 코드 = 인용으로 분류)

## 다음 사이클 권고

- (필요 시) 별도 `/dev` 사이클로 처리할 항목 목록
```

---

## 진단 절차 종료

REPORT.md 작성 완료 후 사용자에게 다음 안내:

```
거버넌스 진단 보고서가 작성되었습니다: backend/docs/governance/{YYYYMMDD}/REPORT.md

발견사항 N건 — 자동 확정 X건 / 사람 검토 필요 Y건
응답 시간: {N}초

다음 단계:
- 발견사항을 별도 `/dev` 사이클로 처리하려면 슬러그를 결정한 후 호출
- 사람 검토 필요 항목을 검토 후 REPORT.md 에 분류 갱신
- 본 진단을 재호출하려면 `/governance` 직접 입력 (자동 실행 없음)
```

> **자동 수정 없음** — 발견사항을 자동으로 수정하지 않는다. 룰 갱신·코드 수정은 모두 사용자가 별도 `/dev` 사이클로 결정한다.
