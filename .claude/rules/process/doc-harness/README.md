# 문서 하네스 — 인덱스 (디렉토리·상태 흐름·Fix Cycle)

> **본 디렉토리는 SSOT 다.** `/dev` 워크플로우의 모든 문서 템플릿(ANALYZE·PLAN·TASK·RESULT·REVIEW), 상태 전이·자동 실행 규칙, Fix Cycle 감지 알고리즘은 본 디렉토리(`process/doc-harness/`) 가 1차 정의한다. `.claude/commands/dev*.md` 등 호출처 문서는 본 디렉토리를 **참조**하며 동일 내용을 복제하지 않는다.

> **이동 이력**:
> - ~2026-04-25 — 단일 파일 `.claude/rules/doc-harness.md` (402줄) 형태로 운영.
> - 2026-04-26 — 4분할: README(디렉토리·상태 흐름·Fix Cycle) / [`templates.md`](templates.md) (5종 템플릿) / [`transitions.md`](transitions.md) (상태 전이) / [`checkbox-rules.md`](checkbox-rules.md) (체크박스 경로 규칙). 구 root `doc-harness.md` 는 redirect 잔존.

---

## 자식 문서

| 문서 | 역할 |
|------|------|
| (본 README) | docs/ 디렉토리 구조 · 네이밍 · 상태 흐름 · 수정 사이클(Fix Cycle) · 번호 증가 · 상호 참조 규칙 · frontend 명세 라이프사이클 |
| [`templates.md`](templates.md) | ANALYZE·PLAN·TASK·RESULT·REVIEW 5종 문서 + SPEC·INDEX (frontend 명세) 템플릿 + TASK 분할 기준 |
| [`transitions.md`](transitions.md) | `/dev:*` 단계 상태 전이·자동 실행 표 (SSOT) — `/dev:spec` 포함 |
| [`checkbox-rules.md`](checkbox-rules.md) | TASK / ANALYZE 체크박스 파일 경로 기록 규칙 (pre-commit 훅 파싱 호환). SPEC 는 본 규칙 적용 대상 외 |

---

### backend 작업 산출물 (5종)

backend 워킹 디렉토리(`swtp/backend/`) 기준 `docs/` 하위에 역할/날짜/작업목적 3뎁스 구조로 관리한다.

```
docs/
├── analyze/
│   └── {YYYYMMDD}/
│       └── {작업목적}/
│           ├── ANALYZE1.md         ← 본문 (Fix Cycle + 도메인 정합성 블로커 시 ANALYZE2.md ...)
│           ├── 요구사항.docx       ← 외부 산출물 그대로 보관
│           └── 도메인모델링.png
├── plan/
│   └── {YYYYMMDD}/
│       └── {작업목적}/
│           └── PLAN1.md
├── tasks/
│   └── {YYYYMMDD}/
│       └── {작업목적}/
│           └── TASK1.md           ← 단일 또는 TASK1-1.md/TASK1-2.md (분할 시)
├── results/
│   └── {YYYYMMDD}/
│       └── {작업목적}/
│           └── RESULT1.md
└── reviews/
    └── {YYYYMMDD}/
        └── {작업목적}/
            └── REVIEW1.md
```

### 거버넌스 진단 산출물 (REPORT — 5종 외 6번째 카테고리)

`/governance` 슬래시 명령 (`backend/.claude/commands/governance.md`) 호출 시 작성되는 진단 산출물은 backend `docs/` 하위 6번째 카테고리로 관리한다.

```
docs/governance/
└── {YYYYMMDD}/
    └── REPORT.md          ← status frontmatter 미사용 (사이클 외 진단 보고서)
```

> backend 5종 산출물과 다른 라이프사이클: **status frontmatter 미사용**. 단순 마크다운 진단 보고서이므로 `draft → review → approved → completed` 흐름 적용 외. 재호출 시 동일 날짜 디렉토리는 덮어쓰기, 다른 날짜는 새 디렉토리 생성. 슬러그 단위 관리 아님 (전체 backend 리포지토리 진단). 자동 수정 없음 — 발견사항을 사용자가 별도 `/dev` 사이클로 처리한다.

### DDL 도메인 SSOT 사본 (docs/ddl — 5종 외 7번째 카테고리)

backend SQL 자산의 사람이 읽는 도메인 단위 통합본을 backend `docs/` 하위 7번째 카테고리로 관리한다.

```
docs/ddl/
├── auth.sql           ← com.mo.swtp.auth 도메인 (refresh_token_p 등)
├── facility.sql       ← com.mo.swtp.facility 도메인 (facility_m + 자식 5종)
├── instrument.sql     ← com.mo.swtp.instrument 도메인 (instrument_m + 자식 6종)
├── menu.sql           ← com.mo.swtp.menu 도메인 (menu_m + menu_role_r)
├── opt.sql            ← com.mo.swtp.opt 도메인 (predc_1m_h)
├── proc.sql           ← com.mo.swtp.proc 도메인 (proc_m + ai_drvn_mod_p + ai_drvn_mod_h)
├── raw.sql            ← com.mo.swtp.raw 도메인 (rawdata_1m_h)
├── tag.sql            ← com.mo.swtp.tag 도메인 (tag_m)
└── user.sql           ← com.mo.swtp.user 도메인 (user_m)
```

> backend 5종 산출물과 다른 라이프사이클: **status frontmatter 미사용**. governance/REPORT.md 와 동일한 사이클 외 영구 누적 자산. 파일명은 backend 도메인 패키지명 (`com.mo.swtp.{도메인}`) 과 **1:1 일치**. 도메인 패키지 신설·폐기 시 본 디렉토리 파일도 함께 관리. 슬러그 단위 아님 (전체 backend 도메인 단위).
>
> **SSOT 관계**: 운영본은 `common/src/main/resources/db/migration/V{N}__{도메인}.sql` (V{N} 동결 + `V{N}_{연번}__patch.sql` 분리), 본 `docs/ddl/{도메인}.sql` 은 도메인 SSOT 사본 (ALTER 누적 가독성 우선). 양쪽 동시 갱신 의무 — 상세: [`../../db/indexing-and-migration.md §5`](../../db/indexing-and-migration.md). REVIEW 권고(중간) 자동 점검 위임.

### frontend 명세 산출물 (SPEC / INDEX)

backend 의 작업이 끝난 뒤 `/dev:spec` 단계가 작성하는 frontend 용 API 명세 산출물은 모노레포의 frontend 디렉토리 하위 (`swtp/frontend/docs/api-specs/`) 에 작업목적 단위로 관리한다.

```
swtp/frontend/docs/api-specs/
└── {작업목적}/                     # backend 의 슬러그와 동일 (예: 송수펌프제어)
    ├── INDEX.md                   # 최신 SPEC 가리킴 + 이력 표
    ├── SPEC1.md                   # 1차 명세 (최초 작성)
    ├── SPEC2.md                   # 변경 시 누적
    └── SPEC{N}.md
```

> 위치가 backend `docs/` 와 다른 이유: frontend 개발자가 자기 워크스페이스에서 직접 접근하도록 같은 모노레포의 frontend 디렉토리 하위에 둔다. backend 의 작업 cycle 번호(PLAN/TASK/RESULT/REVIEW) 와 SPEC 번호는 **독립적으로 증가**한다 (§frontend 명세 라이프사이클 참조).

## 디렉토리 및 파일 네이밍 규칙

### backend 작업 산출물 (5종)
| 뎁스 | 형식 | 예시 |
|------|------|------|
| 1뎁스 | 역할명 | `analyze`, `plan`, `tasks`, `results`, `reviews` |
| 2뎁스 | `YYYYMMDD` | `20260416` |
| 3뎁스 | 작업목적 (스네이크케이스) | `legacy_재개발` |
| 파일명 | `{역할명대문자}{cycle}.md` | `ANALYZE1.md`, `PLAN1.md`, `TASK1.md` |
| 파일명 (TASK 분할) | `TASK{cycle}-{split}.md` | `TASK1-1.md`, `TASK1-2.md` |
| ANALYZE 첨부물 | 파일명 자유 (한국어 허용) | `요구사항.docx`, `도메인모델링.png`, `클래스다이어그램.png` |

### frontend 명세 산출물 (SPEC / INDEX)
| 뎁스 | 형식 | 예시 |
|------|------|------|
| 모노레포 루트 기준 1뎁스 | `frontend` | `swtp/frontend/` |
| 2뎁스 | `docs/api-specs` | `swtp/frontend/docs/api-specs/` |
| 3뎁스 | 작업목적 (backend 슬러그와 동일) | `송수펌프제어` |
| 파일명 (명세) | `SPEC{N}.md` (N 은 슬러그별 독립 카운터) | `SPEC1.md`, `SPEC2.md` |
| 파일명 (인덱스) | 슬러그당 1개 고정 | `INDEX.md` |

- 동일 작업목적에 문서가 여러 개면 번호를 증가한다. (`PLAN1.md`, `PLAN2.md`)
- 동일 흐름의 문서는 같은 날짜/작업목적 경로를 사용한다.
- TASK 분할은 LARGE 작업에서 컨텍스트 관리를 위해 선택적으로 사용하며, `split` 번호는 1부터 시작한다.

```
docs/analyze/20260416/legacy_재개발/ANALYZE1.md
docs/plan/20260416/legacy_재개발/PLAN1.md
docs/tasks/20260416/legacy_재개발/TASK1.md
docs/results/20260416/legacy_재개발/RESULT1.md
docs/reviews/20260416/legacy_재개발/REVIEW1.md
```

## 문서 상태 흐름

모든 문서는 프론트매터로 상태를 관리한다.

### backend 작업 산출물 (ANALYZE·PLAN·TASK·RESULT·REVIEW)
```
draft → review → approved → completed
```

| 상태 | 의미 |
|------|------|
| `draft` | 작성 중 |
| `review` | 검토 요청 상태 |
| `approved` | 사용자 승인 완료 (PLAN에서 다음 단계 진입 조건) |
| `completed` | 작업 완료 |

### frontend 명세 (SPEC)
```
draft → completed
```

| 상태 | 의미 |
|------|------|
| `draft` | 자동 추출 직후 상태 — frontend·QA 가 의미 변경·한글 의미 보충 검토 중 |
| `completed` | 검토 완료. 사용자가 직접 전환 (자동 전환 없음) |

> SPEC 는 `review`·`approved` 단계를 거치지 않는다. 이유: backend 의 작업 사이클은 이미 `/dev:review` 에서 검증되었으므로, SPEC 는 그 결과를 frontend 에 전파하는 산출물 역할만 수행한다.

## 수정 사이클 (Fix Cycle)

REVIEW에서 블로커(높음)가 발견되면, **동일 슬러그에 번호를 증가**한 새 문서를 생성하여 fix cycle을 수행한다.

```
# 단일 TASK
1차 사이클: ANALYZE1 → PLAN1 → TASK1            → impl → RESULT1 → REVIEW1 (블로커 발견)
                                                                    ↓
2차 사이클: (ANALYZE 조건부) → PLAN2 → TASK2    → impl → RESULT2 → REVIEW2 (승인)

# TASK 분할 (LARGE 작업)
1차 사이클: ANALYZE1 → PLAN1 → TASK1-1·1-2·1-3 → impl → RESULT1 → REVIEW1 (블로커)
                                                                    ↓
2차 사이클: (ANALYZE 조건부) → PLAN2 → TASK2-1·2-2 → impl → RESULT2 → REVIEW2 (승인)
```

### Fix Cycle 감지 알고리즘 (의사 코드)

본 알고리즘은 `dev.md` §2 / `dev:analyze.md` §1.3 / `dev:plan.md` §3 모두가 참조하는 **단일 정의**다.
호출처 문서들은 본 알고리즘을 복제하지 않고 결과만 받아 자동 전이한다.

```
input:  슬러그
output: 진입 단계 (ANALYZE 또는 PLAN) 또는 "Fix Cycle 아님"

1. docs/reviews/{*}/{슬러그}/REVIEW{N}.md 중 가장 큰 N 의 문서 탐색
2. 해당 REVIEW 가 없으면                  → return "Fix Cycle 아님"
3. REVIEW.status != "draft" 이면          → return "Fix Cycle 아님"
4. REVIEW 의 "## 발견 사항" 표에서 심각도 "높음" 행 텍스트 모두 추출 → blocker_text
5. blocker_text 가 비어있으면             → return "Fix Cycle 아님"
6. blocker_text 에 도메인 정합성 키워드가 하나라도 포함:
   - 키워드: 용어 / 약어 / 중복 정의 / 네이밍 충돌 / 엔티티 통합
   - YES → return ANALYZE  (ANALYZE{N+1} 작성 후 PLAN{N+1})
   - NO  → return PLAN     (ANALYZE 스킵, PLAN{N+1} 직행)
```

`PLAN` 결과는 곧바로 PLAN{N+1} 작성 단계로 진입하며, `ANALYZE` 결과는 ANALYZE{N+1} 작성 후 그 단계의 자동 전이로 PLAN{N+1} 으로 진입한다. 키워드 매칭 사례는 아래 §ANALYZE 조건부 재진입 의 케이스 A·B 참조.

### ANALYZE 조건부 재진입

Fix Cycle 진입 시 ANALYZE 단계는 **기본 스킵** 한다 (블로커 해소가 목적이므로 신규 용어가 거의 등장하지 않음).
단 직전 REVIEW 의 블로커(높음) 텍스트에 다음 도메인 정합성 키워드가 하나라도 포함된 경우만
ANALYZE{N+1} 을 추가 작성한다 — 5인 회의를 다시 소집해 충돌을 재정리해야 하기 때문.

**키워드**: `용어`, `약어`, `중복 정의`, `네이밍 충돌`, `엔티티 통합`

```
# 케이스 A: REVIEW 블로커가 도메인 정합성 항목 (예: "용어 충돌 발견")
1차: ANALYZE1 → PLAN1 → TASK1 → impl → RESULT1 → REVIEW1 (블로커: 용어 충돌)
                                                            ↓ 키워드 매칭 → ANALYZE 재진입
2차: ANALYZE2 → PLAN2 → TASK2 → impl → RESULT2 → REVIEW2 (승인)

# 케이스 B: REVIEW 블로커가 일반 코드/패턴 항목 (예: "Service 분리 누락")
1차: ANALYZE1 → PLAN1 → TASK1 → impl → RESULT1 → REVIEW1 (블로커: Service 분리)
                                                            ↓ 키워드 미포함 → ANALYZE 스킵
2차:           PLAN2 → TASK2 → impl → RESULT2 → REVIEW2 (승인)
```

### 번호 증가 규칙

- 같은 날짜, 같은 슬러그 경로에 번호를 증가하여 생성한다.
- 예: `docs/plan/20260416/p6spy_로깅정책/PLAN2.md`
- PLAN{n}, TASK{n}(또는 TASK{n}-k), RESULT{n}, REVIEW{n}은 같은 fix cycle 번호 `n`으로 대응한다.
- TASK 분할 시 `split` 번호(k)만 달라지며 `cycle` 번호(n)는 동일하다.
- Fix Cycle 진입 시 `cycle`만 +1 증가한다. 2차 사이클의 분할 여부는 독립적으로 재결정한다.

### 상호 참조 규칙

fix cycle 문서는 이전 REVIEW를 참조하여 트레이서빌리티를 유지한다.

**ANALYZE{n}.md 헤더 (n ≥ 2):**
```markdown
## 작업 배경
- [이전 리뷰](../../../reviews/{YYYYMMDD}/{슬러그}/REVIEW{n-1}.md) 블로커 (도메인 정합성) 해소
- 직전 ANALYZE: [ANALYZE{n-1}](ANALYZE{n-1}.md)
```

> ANALYZE{n} (n ≥ 2) 은 직전 REVIEW 의 블로커 텍스트에 도메인 정합성 키워드(`용어`, `약어`, `중복 정의`,
> `네이밍 충돌`, `엔티티 통합`) 가 포함된 경우만 작성된다. 키워드 미포함 시 ANALYZE 는 스킵되고
> 곧바로 PLAN{n} 으로 진입한다 (§ANALYZE 조건부 재진입 참조).

**PLAN{n}.md 헤더 (n ≥ 2):**
```markdown
## 배경
- [이전 리뷰](../../../reviews/{YYYYMMDD}/{슬러그}/REVIEW{n-1}.md) 블로커 해소
```

**REVIEW{n}.md 헤더 (n ≥ 2):**
```markdown
## 관련 결과
- [결과](../../../results/{YYYYMMDD}/{슬러그}/RESULT{n}.md)
- [이전 리뷰](REVIEW{n-1}.md)
```

---

## frontend 명세 라이프사이클

`/dev:spec` 단계가 작성하는 SPEC 문서는 backend 작업 사이클과 **독립적으로** 번호가 증가한다.

### 번호 규칙
- 슬러그별로 1부터 시작 (`SPEC1.md`)
- entity·DTO·Controller 스펙이 변경되어 frontend 에 알릴 필요가 생길 때마다 +1 (`SPEC2.md`, `SPEC3.md`, ...)
- backend 의 PLAN/TASK/RESULT/REVIEW 의 cycle 번호와 **무관** — 같은 슬러그라도 backend 가 PLAN3 단계인데 SPEC 는 SPEC1 일 수 있고, backend 가 PLAN1 인데 이후 잦은 인터페이스 변경으로 SPEC5 까지 누적될 수 있음

### 트리거 시점
| 시점 | 작성 여부 |
|------|---------|
| 신규 기능의 첫 backend 구현 완료 (`/dev:commit` 직후) | SPEC1 작성 |
| backend 가 같은 슬러그를 다시 수정해 Controller·DTO·ErrorCode 가 바뀜 | SPEC{N+1} 작성 (이전 버전 대비 변경 이력 자동 추출) |
| backend 변경이 frontend 에 영향 없음 (예: 내부 리팩토링) | SPEC 갱신 불필요 — 사용자 판단 |
| Fix Cycle 진입으로 backend 가 PLAN2 단계 | 구현 결과가 frontend 인터페이스에 영향 주면 SPEC{N+1} 작성, 아니면 생략 |

### 자동 추출 vs 수동 보충
| 항목 | 자동 추출 (Claude) | 수동 보충 (frontend·QA) |
|------|------------------|----------------------|
| 엔드포인트 (HTTP·경로·요약) | ✅ Swagger `@Tag`·`@Operation` | — |
| 요청·응답 DTO 스키마 | ✅ 필드명·타입·`@Schema(description=)` | 비즈니스 의미 보충 (필요 시) |
| ErrorCode 표 | ✅ enum name·httpStatus | **한글 의미** (소스 주석 부재 시 전 케이스) |
| 변경 이력 (N≥2) | ✅ 정적 비교 (필드 추가·제거·타입 변경 등) | 의미 변경 (필드명 동일·의미 변화 등) "수동 보충" 마커로 보강 |

### 상호 참조
SPEC{N}.md 의 "## 관련 작업" 섹션은 backend 의 PLAN·TASK·RESULT·REVIEW·ANALYZE 문서를 모노레포 상대 경로로 링크한다 (`../../../../backend/docs/{role}/{date}/{슬러그}/{문서}.md`). frontend 개발자가 SPEC 만 읽고도 backend 결정 배경을 추적할 수 있도록 한다.
