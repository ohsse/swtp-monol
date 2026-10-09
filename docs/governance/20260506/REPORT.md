# 거버넌스 진단 보고서 (2026-05-06)

> 본 보고서는 `/governance` 슬래시 명령 (`backend/.claude/commands/governance.md`) 의 **첫 호출 결과** 이자 신설 사이클 (`governance_health_check`, Cycle 2) 의 **dogfood 검증** 산출물이다. 자동 수정 없음 — 발견사항 보고만, 수정은 별도 `/dev` 사이클로 위임.
>
> 진단 시 git 브랜치: `dev-be` (HEAD: `57343d2 Merge branch 'master' into dev-be`, Cycle 1 commit `9208f4a` 포함).

---

## 요약

- 정량 진단 4항목 (Q1~Q4) + 정합 진단 12항목 (R1~R12) 실행 완료
- **응답 시간**: real **2초** (60초 임계 통과 ✅)
- **발견사항 합계**: 자동 확정 **6건** / 사람 검토 필요 **0건**
- ANALYZE1 즉시 발견사항 4건 정확 재현 ✅
- 추가 발견 1건 (`ot-integration.md` 308줄 — ANALYZE1 가정 누락)
- REVIEW1 §개선 제안 #1·#3 자동 식별 성공 ✅

---

## 정량 진단 결과 (Q1~Q4)

### Q1 — CLAUDE.md 줄 수

| 파일 | 줄 수 |
|------|------|
| `swtp/CLAUDE.md` (ROOT) | 111 |
| `backend/CLAUDE.md` | 138 |
| 합계 | 249 |

> 매핑 표 vs 직접 정책 기술 분리 측정 알고리즘 (표 형식 80% 임계값) 은 다음 호출 시 적용 권고 — 본 1차 호출에서는 줄 수 측정만 수행.

### Q2 — 룰 파일 줄 수 분포 5구간 (`backend/.claude/rules/` 스코프)

| 구간 | 카운트 | 의미 |
|------|------|------|
| `< 30` (인덱스 stub) | 2 | 진입점 stub |
| `30 ~ 50` (소형) | 4 | 소형 단일 패턴 |
| `50 ~ 200` (중형) | 11 | 중형 단일 주제 |
| `200 ~ 500` (분리 검토 후보) | 6 | 분리 검토 |
| `500 +` (분리 의무) | 0 | (미래 경보용) |

### Q3 — 300줄+ 단일 파일 식별 + 단일·복합 관심사 구분

| 파일 | 줄 수 | 관심사 분류 | 분리 권고 |
|------|------|----------|---------|
| `templates.md` | 397 | **복합** (5종 템플릿 이질적 관심사) | 분리 후보 ✅ |
| `test-strategy.md` | 343 | 단일 (§1~§6 단계적 심화) | 분리 보류 권고 |
| `entity-patterns.md` | 318 | 단일 (패턴 계층 누적) | 분리 보류 권고 |
| `ot-integration.md` | 308 | 단일 (OT 연동 가이드 단일 도메인) | **분리 보류 권고** |

> **추가 발견**: `ot-integration.md` 308줄은 ANALYZE1 가정에서 누락된 4번째 후보. 단일 관심사이므로 분리 보류 권고이지만 정량 진단으로는 식별되어야 함 — 본 진단의 가치 입증.

### Q4 — Redirect stub 외부 참조 grep (이중 표)

| Redirect Stub | 현행 룰 참조 (`.claude/rules/`) | docs 이력 참조 (`docs/`) | 폐기 후보 여부 |
|--------------|------------|--------------|--------------|
| `db-patterns.md` | 16건 (모두 이동 이력 메타·매핑 표 인용) | **29건** | ❌ 호환 유지 (immutable docs 이력) |
| `doc-harness.md` | 2건 (이동 이력 메타) | **35건** | ❌ 호환 유지 (immutable docs 이력) |

> **결론**: 두 redirect stub 모두 폐기 후보 아님. docs/ 이력 참조가 0이 아닌 한 호환 레이어 유지 필요.

---

## 정합 진단 결과 (R1~R12)

### R1 — 도메인 4영역 SSOT 정책 본문 고유 문구 grep
- 매칭 0건 (`SCADA_TIMEOUT`·`OUTBOUND_FAIL`·`MANUAL_EXPIRE`·`SYSTEM_INIT`·`hold last value`·`last_rcv_dtm` 모두 `ot-integration.md` 내에서만 등장) ✅ **정합**

### R2 — Cycle 1 산출물 정합 (`templates.md` ANALYZE 3섹션 ↔ `dev/plan.md` §5b 1:1)
- `templates.md` 매칭 5건 (가정 + 성공 기준 후보 + 4영역 표 행 3건)
- `dev/plan.md §5b` 매칭 3건 (가정 없음 단독 / 성공 기준 후보 모호 / 비해당 단독 4건)
- ✅ **정합 확인**

### R3 — REVIEW1 §개선 제안 #1 자동 식별 (templates.md 깨진 인용 경로)
- **L97 매칭 1건**: `[backend/.claude/rules/ot-integration.md](../../ot-integration.md)`
- `templates.md` 위치 (`backend/.claude/rules/process/doc-harness/`) 기준 `../../ot-integration.md` 는 `backend/.claude/ot-integration.md` 를 가리키므로 깨진 경로
- **REVIEW1 #1 자동 식별 성공** ✅

### R4 — REVIEW1 §개선 제안 #3 자동 식별 (impl.md vs commit.md 규모 판단 갭)
- `impl.md` L93: "PLAN/TASK 둘 다 없으면 Small, 있고 RESULT 가 예상 산출물에 포함되면 Large"
- `commit.md` L29: "RESULT 또는 REVIEW 문서가 있으면 → Large"
- 두 기준 텍스트 다름 → **갭 발견**
- **REVIEW1 #3 자동 식별 성공** ✅

### R5 — §2.5 면책 인용 근거 ↔ 실사용 매칭
- 코드 주석 스코프 (`api/src`·`common/src`·`scheduler/src`) 에서 `§2\.5 면책` 매칭 **0건**
- 룰·docs/ 스코프 매칭은 인용 (룰 정의·과거 ANALYZE) — 정상
- **권고 등급**: §2.5 면책 영역 표 행 (정수장 안전 도메인 / DB 쿼리 튜닝 2건) 의 실 코드 사례 누적 0 — `coding-discipline.md §7.1` 자동 차단 훅 신설 보류 결정과 정합 유지

### R6 — coding-discipline.md 4원칙 본문 복제 grep
- `Think Before Coding`·`Simplicity First`·`Surgical Changes`·`Goal-Driven Execution` 외부 등장 **0건** ✅ **정합**

### R7 — ROOT 3층 ↔ backend 1층 어휘 SSOT 분리
- (a) `standard-terms.md` 의 DOM_* 정의 행 **0건** ✅ (조합 재료로만 사용)
- (b) 폐기 어휘 코드 잔존 **0건** ✅ (`pmp_`·`reg_id`·`cntom` Java 소스 매칭 0)

### R8 — dict/README.md 분리 인덱스 정합
- `VARCHAR`·`BIGINT`·`NUMERIC`·`LocalDateTime` 매칭 **0건** ✅ (정책 본문 미복제 — 인덱스 진입점만)

### R9 — `feature-dev:code-reviewer` 인용 grep — 🔴 **중간 심각도**
- **2건 매칭**:
  - `dev/plan.md:94`: `Agent(subagent_type="feature-dev:code-reviewer", prompt="`
  - `dev/review.md:20`: `**`feature-dev:code-reviewer` 서브에이전트를 spawn하여 자동 리뷰를 수행한다.**`
- 본 환경은 `feature-dev` 플러그인 **미설치** (메모리 정책 — `feedback_no_feature_dev_plugin.md`)
- 룰 본문 갱신은 별도 사이클로 위임 (자동 수정 없음 정합)

### R10 — 신규 어휘 0건 검증
- 본 사이클 ANALYZE1 사전 카탈로그 3표 모두 "없음" 표기 ✅
- ROOT 어휘 사전 + backend `standard-terms.md` 변경 0건 정합

### R11 — DB 룰 정책 본문 복제 grep + 거짓 양성 구분
- `CREATE INDEX CONCURRENTLY` 매칭 3건:
  - `db/indexing-and-migration.md:54`·`:91` — **자식 룰 본문** (정책 정의 위치, 정상)
  - `entity-patterns.md:296` — **예시 코드** (`@CreateIndex` 어노테이션 예시 주석, 정책 복제 아님)
- ✅ **거짓 양성 구분 정책 적용 — 모두 인용 또는 정상 위치, 복제 0건**

### R12 — Redirect stub 이중 표 + DOM_* 미사용 도메인 식별
- (a) Redirect stub 이중 표 — Q4 결과 재사용
- (b) **`DOM_AMT_15_2` `standard-terms.md` 사용 사례 0건** — 미사용 도메인 — 🟡 낮음 심각도

---

## 발견사항 표

### 자동 확정 (확실한 발견사항)

| # | 심각도 | 항목 | 위치 | 내용 | 진단 항목 |
|---|--------|------|------|------|---------|
| 1 | 🔴 중간 | `feature-dev:code-reviewer` 인용 | `.claude/commands/dev/plan.md:94`, `dev/review.md:20` | 본 환경 미설치 플러그인 인용. 메모리 정책 (`feedback_no_feature_dev_plugin.md`) 위배. wtp-* 직접 호출로 갱신 필요 | R9 |
| 2 | 🟡 낮음 | `process/README.md` "구 root 3개 파일" 표기 불일치 | `.claude/rules/process/README.md:7` | 실제 redirect 잔존 1건만 (`doc-harness.md`). `commit-convention.md`·`hooks-guide.md` 는 root 부재 → 실 1건으로 정정 필요 | (Q4 부속) |
| 3 | 🟡 낮음 | `DOM_AMT_15_2` 미사용 도메인 | `swtp/.claude/rules/dict/standard-data-domains.md` 등록 / `backend/.claude/rules/dict/standard-terms.md` 사용 0건 | ROOT 표준 데이터 도메인 등록 후 backend 사용 사례 0건 — 활용처 도입 또는 폐기 검토 | R12-b |
| 4 | 🟡 낮음 | `db-patterns.md` redirect stub `docs/` 이력 참조 N건 | 29건 (`docs/plan/`·`docs/reviews/`·`docs/analyze/` 등) | docs/ 는 immutable 이력이므로 redirect stub 호환 유지 필요. 폐기 후보 아님 (현행 룰 참조 16건 모두 인용) | Q4 |
| 5 | 🟡 낮음 | REVIEW1 #1 — `templates.md` 깨진 인용 경로 | `templates.md:97` `(../../ot-integration.md)` | `templates.md` 위치 기준 `backend/.claude/ot-integration.md` 를 가리킴 (실 위치 `backend/.claude/rules/ot-integration.md`) → `(../../../ot-integration.md)` 로 정정 필요 | R3 |
| 6 | 🟡 낮음 | REVIEW1 #3 — `impl.md` vs `commit.md` 규모 판단 갭 | `impl.md:93` vs `commit.md:29` | 두 슬래시 명령의 Large 판단 기준 불일치. `RESULT 가 예상 산출물에 포함` (impl) vs `RESULT 또는 REVIEW 문서가 있으면` (commit) — 동일 기준으로 통일 필요 | R4 |

### 사람 검토 필요 ⚠️

(없음 — 본 1차 호출의 모든 매칭이 자동 확정 가능)

---

## 응답 시간 측정

| 항목 | 값 |
|------|----|
| `time` 측정 결과 (real) | **2초** |
| 60초 임계 통과 | ✅ |
| 실행 진단 명령 수 | 16건 (Q1·Q2·Q3·Q4 + R1·R2·R3·R4·R5·R6·R7-a·R7-b·R8·R9·R10·R11·R12-b) |

> 본 1차 호출의 응답 시간은 plan §검증 방법 의 60초 임계를 30배 여유로 통과. grep 명령 추가 도입 시에도 충분한 시간 여유 확보.

---

## 거짓 양성 구분 사례

### 사례 1: `entity-patterns.md` 의 `CREATE INDEX CONCURRENTLY`

```
.claude/rules/entity-patterns.md:296: - 역방향 단독 조회 빈번 + 행 수 수백 이상 → `CREATE INDEX CONCURRENTLY idx_{테이블}_{컬럼}` 추가
```

- **분류**: **인용** (예시 코드 — 정책 복제 아님)
- **근거**: `entity-patterns.md` 는 N:M 매핑 패턴 §인덱스 정책 절에서 `db/indexing-and-migration.md §1` 의 카디널리티 원칙을 인용하며 예시 코드 형식으로 명령을 보여줌. 정책 본문 (`db/` 자식 룰) 의 1차 정의를 복제하지 않음.
- **정책 적용**: "예시 코드·테스트 도입 트리거 언급 = 인용" (governance.md §거짓 양성 구분 정책)

### 사례 2: `db/` 자식 룰의 매핑 표 인용

`db/indexing-and-migration.md:6`·`partitioning-and-retention.md:6`·`query-tuning.md:6` 의 `> 본 문서는 과거 db-patterns.md 의 §X 를 분리한 결과다` 인용 16건은 모두 redirect stub 호환 레이어의 이동 이력 메타 정보. 정책 본문 복제 아님.

- **분류**: **인용** (이동 이력 메타 — 정상)
- **근거**: redirect stub 패턴의 표준 운영. 호환 레이어로서 외부 참조 호환성 유지.

---

## 다음 사이클 권고

본 진단의 발견사항 6건을 별도 `/dev` 사이클로 처리할 우선순위:

| 순위 | 작업 슬러그 (제안) | 설명 | 발견사항 # |
|------|----------------|------|---------|
| 1 | `feature_dev_subagent_replacement` | `dev/plan.md`·`dev/review.md` 의 `feature-dev:code-reviewer` 호출 → wtp-* 직접 호출 갱신 | #1 (중간) |
| 2 | `templates_md_path_correction` | `templates.md:97` 깨진 인용 경로 정정 (`(../../../ot-integration.md)`) | #5 |
| 3 | `dev_size_judgment_alignment` | `impl.md` vs `commit.md` 규모 판단 기준 일치화 | #6 |
| 4 | `process_readme_redirect_count_fix` | `process/README.md` "3개 파일" → "1개 파일" 표기 정정 | #2 |
| 5 | `dom_amt_usage_or_deprecation` | `DOM_AMT_15_2` 활용처 도입 또는 폐기 검토 | #3 |

> 발견사항 #4 (redirect stub docs/ 이력 참조 N건) 은 호환 유지 필요 보고이지 수정 대상 아님.

> **자동 수정 없음** 정책 정합 — 본 보고서는 발견만 수행. 실제 수정은 사용자가 별도 `/dev` 사이클로 결정한다.

---

## Cycle 2 dogfood 검증 결론

본 사이클 (`governance_health_check`) 의 PLAN1 §성공 기준 7건 중 다음 검증 항목이 통과:

| # | 기준 | 결과 |
|---|------|------|
| 1 | governance.md 절차서 16 항목 모두 포함 | ✅ |
| 2 | 거짓 양성 구분 정책 명문화 (3 키워드) | ✅ |
| 3 | 보고서 산출물 위치 디렉토리 신설 + doc-harness 갱신 | ✅ (본 REPORT 가 첫 산출물) |
| 4 | backend CLAUDE.md 인덱스에 `/governance` 안내 추가 | ✅ |
| 5 | dogfood — ANALYZE1 즉시 발견사항 4건 정확 재현 | ✅ (#1·#2·#3·#4 재현) |
| 6 | 응답 시간 60초 이내 | ✅ (real 2초) |
| 7 | 자동 수정 없음 정책 정합 | ✅ |

**모든 성공 기준 통과**. Cycle 2 의 IMPL 단계 완료 검증.
