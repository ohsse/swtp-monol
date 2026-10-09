# 문서 템플릿 (ANALYZE / PLAN / TASK / RESULT / REVIEW)

> **참조**: 본 파일은 [`README.md`](README.md) 가 정의한 디렉토리·상태 흐름·Fix Cycle·번호 증가·상호 참조 규칙을 전제로 한다. 각 템플릿의 `RESULT{N}` 의 `N` 등 변수 표기는 README §번호 증가 규칙 참조. 체크박스 파일 경로 기록 규칙은 [`checkbox-rules.md`](checkbox-rules.md) 참조.

---

**ANALYZE{n}.md** (Medium/Large 작업 — PLAN 직전 단계 산출물, `/dev:analyze` 가 작성)
```markdown
---
status: draft
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# {제목} — 도메인 분석

## 작업 배경
- 요청 요약
- 외부 산출물: `요구사항.docx`, `도메인모델링.png`, `클래스다이어그램.png` 등

## 회의록 (5인 회의 토픽 주도)

### 안건 1: {제목}
- 호출 에이전트: `wtp-glossary-manager`, `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: ...
  - **wtp-domain-expert**: ...
- Round 2 (이견 시): ...
- **결론**: ...

### 안건 2: {제목}
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약: ...
- **결론**: ...

(안건 N 까지 반복)

## 표준 사전 카탈로그

> **⚠️ 하위 호환**: 신규 항목이 없는 층위는 "없음" 으로 표기하고 해당 표 생략 가능.
> 과거 `status: approved` ANALYZE 문서(단일 "## 신규 용어 카탈로그" 표) 도 그대로 유효하며, 본 3표 구조로 재작성할 필요 없다.

### 신규 표준 단어
(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| (예) `oper` | 운전(상태) | 신규 | `standard-words.md` 미등록, `domain-abbreviations.md` 충돌 없음 |

### 신규 표준 데이터 도메인
(값 형식 — SQL 타입·길이·Java 타입. 1차 정의: `swtp/.claude/rules/dict/standard-data-domains.md` — ROOT. **`wtp-dba-reviewer` 2차 승인 필수**)

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| (예) `DOM_OPER_STAT_20` | VARCHAR(20) | String | NOT NULL | 신규 | 기존 `DOM_CODE_20` 일반 코드와 구분 (DBA 승인) |

### 신규 표준 용어
(단어 + 데이터 도메인 → DB 컬럼명. 1차 정의: `.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| (예) `pump_oper_dtm` | `pump`(비즈니스 도메인) + `oper` + `dtm` | `DOM_DTM` | 신규 | 의미 중복 없음, 패턴 일관 |

분류값 (3층 공통): **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

## 신규 엔티티/DB 컬럼
- 엔티티: 도메인 패키지, 외부 PK 여부, suffix(`_m`/`_d`/...), 인덱스 후보
- 컬럼: 타입, NULL 정책, 약어 적합성

## 기존 사전·패턴과의 충돌
- 충돌 항목 + 권장 해소책 (회의 결론과 일치해야 함)

## PLAN 으로 전달할 결정 사항
- 도메인 모델 초안: ...
- DB 설계 변경 초안: ...
- 적용할 패턴: ...

## 가정 및 미해결 질문

> 본 ANALYZE 의 가정·미해결 질문을 명시한다. **최소 1건 이상 기재 의무** — "없음" 기재로 형식적 충족 금지. ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. `wtp-domain-expert` 가 REVIEW 단계에서 4영역 섹션 교차 검토 — 본 섹션 + 신규 "## 도메인 룰 4영역 점검" 섹션 (가정의 4영역 충돌 ↔ 4영역 표 "해당" 일관성) 을 수행하며 도메인 4영역 (알람 4단계·인터록·운전 모드·이력 기록 의무) 충돌 여부를 점검한다.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| (예) 인터록 규칙 미등록 시 통과 가정 | 가정 | `wtp-domain-expert` 검토 필요 |

분류값: 가정 / 미해결 / 결정 (미해결은 PLAN 단계에서 결정으로 변환)

## 성공 기준 후보 (PLAN 변환 대상)

> 본 섹션의 후보 기준은 PLAN 단계에서 ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 의 "성공 기준 (검증 가능 형태)" 로 변환된다. 검증 명령 초안은 PLAN 에서 명령·테스트·조회로 확정한다. **§2.5 면책 적용 배제** — 본 섹션의 검증 가능성 요건은 §2.1 정량 기준 면책 영역이 아니다. **최소 1건 이상 기재 의무** — "없음" 단독 금지. 모호 표현 정규식 8건 (`^성능 개선$`·`^안정성 향상$`·`^개선$`·`^향상$`·`^기능 추가$`·`^코드 개선$`·`^리팩토링$`·`^문서화$`) 단독 행은 PLAN 진입 시 자율 차단된다 (`backend/.claude/commands/dev/plan.md §5b`).

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| (예) AlarmEvaluator 가 4단계 전이를 모두 처리 | 신규 단위 테스트 5건 GREEN — 정상→주의·주의→경보·경보→위험·위험→정상 복귀·이상치 기각 |

## 도메인 룰 4영역 점검

> 본 ANALYZE 의 도메인 4영역 (알람 4단계 / 인터록 / AI 운전 모드 / 이력 기록) 해당 여부를 표시한다. 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../ot-integration.md) — 알람 4단계 → §5 단독, 인터록 선행조건 → §2·§5, AI 운전 모드 이중 체계 + 강제 전환 → §5, 이력 기록 의무 → §5.
>
> **"비해당" 단독 4건 차단 해제 조건**: (1) 각 행에 구체 사유 명기 (어떤 영역도 "해당 없음" 한 줄이 아님), (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족. `wtp-domain-expert` 가 REVIEW 단계에서 형식적 충족 패턴 2종 — (a) "비해당" 단독 + 사유 부재, (b) "해당" 표기 + "근거 또는 영향" 컬럼 공란/`확인 필요` 수준 — 을 블로커 등급으로 점검한다.

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 해당/비해당 | 임계값·전이 조건·복귀 조건 변경 여부 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 해당/비해당 | 선행조건 검사·기동 차단·복구 후 재검사 영향 |
| AI 운전 모드 (`ot-integration.md §5`) | 해당/비해당 | 사용자 의도 (`ai_drvn_mod`) / 시스템 상태 (`ai_mode_cd`) 변경 또는 강제 전환 (SCADA 5분 초과) 영향 |
| 이력 기록 의무 (`ot-integration.md §5`) | 해당/비해당 | 모드 전환 이력 (`ai_drvn_mod_h.transition_reason`) / 제어 로그 (`pump_ctrl_h`) 영향 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)
- [ ] `swtp/.claude/rules/dict/standard-words.md` — `oper` 단어 신규 등록 (있다면)
- [ ] `swtp/.claude/rules/dict/standard-data-domains.md` — `DOM_OPER_STAT_20` 데이터 도메인 신규 등록 (있다면, DBA 승인 후)
- [ ] `.claude/rules/dict/standard-terms.md` — `pump_oper_dtm` 용어 신규 등록 (있다면, backend 표준 용어)
- [ ] `swtp/.claude/rules/dict/domain-abbreviations.md` — 비즈니스 도메인 약어 추가 (있다면)
- [ ] `.claude/rules/naming.md` — DB suffix 표 변경 (있다면)
- [ ] `.claude/rules/entity-patterns.md` — 신규 패턴 추가 (있다면)

## 산출물
- [계획안](../../../plan/YYYYMMDD/작업목적/PLAN1.md)
```

> Fix Cycle 2차 사이클 이상 (n ≥ 2) 에서는 헤더에 직전 REVIEW 링크를 추가한다.
> 양식은 [README §상호 참조 규칙](README.md#상호-참조-규칙) 의 ANALYZE{n} 헤더 섹션 참조.

**PLAN{n}.md**
```markdown
---
status: draft
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# {제목}
## 목적
## 배경
## 범위
## 구현 방향
## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 적용. "성능 개선" 같은 모호 목표 금지. 각 기준에 **검증 명령·테스트·조회** 를 명시한다.

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 의 가정·미해결 질문을 PLAN 단계 결정으로 변환한다. **최소 1건 이상 기재 의무**.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|

분류값: 가정 / 미해결 → 결정

## 제외 사항
## 예상 산출물
- [태스크](../../../tasks/YYYYMMDD/작업목적/TASK1.md)
```

**TASK{n}.md**
```markdown
---
status: draft
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# {제목}
## 관련 계획
- [계획안](../../../plan/YYYYMMDD/작업목적/PLAN1.md)
## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. **검증 영역에 백틱 사용 금지** (`check-task-unstage.sh` 훅 파싱 충돌 — 두 번째 백틱이 첫 백틱의 닫는 쌍으로 오인).

### Phase 1: {이름}
- [ ] Task 1 → 검증: {확인 명령}
- [ ] Task 2 → 검증: {확인 명령}
### Phase 2: {이름}
- [ ] Task 1 → 검증: {확인 명령}
## 산출물
- [결과](../../../results/YYYYMMDD/작업목적/RESULT1.md)
```

### TASK 분할 기준 (LARGE 전용)

LARGE 작업이 다음 조건 중 하나 이상이면 `/dev:task` 단계에서 분할을 사용자에게 제안한다:
- Phase가 10개 이상
- 예상 체크박스가 60개 이상
- 데이터 계층·애플리케이션 계층·테스트 등 계층 경계가 명확히 구분됨

분할된 각 파일은 **"## 관련 분할 TASK"** 섹션으로 형제 TASK를 상호 링크한다:
```markdown
## 관련 분할 TASK
- [TASK{n}-1 {묶음명}](TASK{n}-1.md)
- [TASK{n}-2 {묶음명}](TASK{n}-2.md)
- [TASK{n}-3 {묶음명}](TASK{n}-3.md)
```

> TASK·ANALYZE 체크박스 파일 경로 기록 규칙은 [`checkbox-rules.md`](checkbox-rules.md) 참조 (pre-commit 훅 파싱 호환을 위한 필수 규칙).

**RESULT{n}.md**
```markdown
---
status: draft
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# {제목}
## 관련 작업
- [계획안](../../../plan/YYYYMMDD/작업목적/PLAN{N}.md)
- [태스크](../../../tasks/YYYYMMDD/작업목적/TASK{N}.md)
  ← TASK 분할 시: TASK{N}-1.md, TASK{N}-2.md 등 분할 파일을 각각 링크
## 작업 요약
## TASK 규모
<!-- 분할 기준(Phase 10 / 체크박스 60) 대비 적정성 관찰용. 작성 시 표 그대로 유지 -->
| 항목 | 값 |
|------|----|
| Phase 수 | N |
| 체크박스 수 | N |
| 분할 여부 | Y/N |
| 분할 근거 | (Y인 경우 사유 한 줄, N이면 "—") |
## 변경 사항

### 의도된 변경

(TASK 체크박스에 명시된 변경)

### 계획 외 변경

> ROOT [`coding-discipline.md` §3](../../../../../.claude/rules/coding-discipline.md) 적용. TASK 체크박스 외 파일 변경이 있으면 **의도(필수 부수 변경) vs 우연(범위 이탈)** 을 구분 명기. 없으면 **"없음" 명시 의무**.

## 테스트 결과
## 비고
```

**REVIEW{n}.md**
```markdown
---
status: draft
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# {제목}
## 관련 결과
- [결과](../../../results/YYYYMMDD/작업목적/RESULT1.md)
## 리뷰 범위
## 발견 사항

> ROOT [`coding-discipline.md` §2.1](../../../../../.claude/rules/coding-discipline.md) 적용. 발견 사항은 다음 카테고리로 분류한다:
> - **복잡도 과잉 (Overengineering)** — 메서드 50줄 / 추상화 3단 / DTO 상속 3단 초과. §2.5 면책 영역 해당 여부 + 인용 근거 누락 여부 점검
> - **도메인 룰 위반** — 알람 4단계·인터록·운전 모드·이력 기록 의무
> - **보안** — JWT secret·DB 계정 하드코딩, OWASP Top 10
> - **성능** — N+1·슬로우 쿼리·파티션 프루닝 미적용
> - **테스트 누락** — `test-strategy.md` §5.2 도메인 시나리오 필수 3종
> - **기타**

심각도: 높음(블로커) / 중간(권고) / 낮음(참고)

## 개선 제안
## 결론
```

---

## frontend 명세 전파 템플릿 (SPEC / INDEX)

> **위치**: `swtp/frontend/docs/api-specs/{슬러그}/` (backend 산출물이 아닌 frontend 디렉토리 하위 — `/dev:spec` 단계가 작성).
> **번호 규칙**: `SPEC{N}.md` 는 `/dev` 워크플로우의 cycle 번호와 **독립적으로** 증가한다 (backend 의 PLAN/TASK/RESULT/REVIEW 와 다른 라이프사이클). 슬러그별로 1부터 시작하여 명세 변경 발생 시마다 +1.
> **상태 흐름**: `draft → completed` (review·approved 단계 없음. 사용자가 검토 완료 시 직접 `completed` 전환).

### SPEC{n}.md 템플릿

```markdown
---
status: draft
created: YYYY-MM-DD
updated: YYYY-MM-DD
spec_version: {N}
slug: {슬러그}
---
# {슬러그} API 명세 v{N}

## 관련 작업
- [PLAN](../../../../backend/docs/plan/YYYYMMDD/{슬러그}/PLAN{N}.md)
- [TASK](../../../../backend/docs/tasks/YYYYMMDD/{슬러그}/TASK{N}.md)
- [RESULT](../../../../backend/docs/results/YYYYMMDD/{슬러그}/RESULT{N}.md) (Large 작업만)
- [REVIEW](../../../../backend/docs/reviews/YYYYMMDD/{슬러그}/REVIEW{N}.md) (Large 작업만)
- [ANALYZE](../../../../backend/docs/analyze/YYYYMMDD/{슬러그}/ANALYZE{N}.md) (Medium/Large 작업만)

## 공통 응답 구조

모든 응답은 `CommonResponseDto<T>` 로 래핑된다:

```json
{
  "code": "SUCCESS" | "{ERROR_CODE_NAME}",
  "data": T | null
}
```

- 성공 시: `code = "SUCCESS"`, `data = T`
- 실패 시: `code = ErrorCode.name()`, `data = null`. HTTP 상태 코드는 ErrorCode 의 `httpStatus` 값을 따른다.

## 엔드포인트 목록

| HTTP | 경로 | 요약 | 인증 | ErrorCode |
|------|------|------|------|----------|
| POST | `/api/{도메인}/{action}` | {summary} | 필요 | `{도메인}_NOT_FOUND` 외 |

## 엔드포인트별 상세

### 1. {summary} — `POST /api/{도메인}/{action}`

- **Tag**: `{@Tag.name}`
- **인증**: 필요 / 불필요
- **요청**:
  - Path Variables: (있다면 표)
  - Query Parameters: (있다면 표)
  - Request Body: `{도메인}UpsertDto` (아래 §DTO 스키마 참조)
- **응답 (200)**: `CommonResponseDto<{도메인}Dto>`
- **에러 응답**:

  | HTTP | code | 의미 |
  |------|------|-----|
  | 400 | `INVALID_REQUEST` | 요청 형식 오류 |
  | 404 | `{도메인}_NOT_FOUND` | 리소스 없음 |
  | 500 | (서버 오류) | 서버 내부 오류 |

(엔드포인트 N 까지 반복)

## DTO 스키마

### {도메인}UpsertDto

| 필드 | 타입 | 필수 | 설명 | 예시 |
|------|------|------|------|------|
| `pumpId` | string | ✅ | 펌프 ID | `P-001` |
| `pumpNm` | string | ❌ | 펌프 명칭 | `송수1호기` |
| `useYn` | enum (`Y` / `N`) | ✅ | 사용 여부 | `Y` |

### {도메인}Dto (응답)

| 필드 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `pumpId` | string | 펌프 ID | `P-001` |
| `rgstrDtm` | string (ISO-8601) | 등록 일시 | `2026-04-28T10:30:00` |

(DTO N 까지 반복)

## ErrorCode 표

`com.mo.swtp.{도메인}.exception.{도메인}ErrorCode` enum 정의:

| name | httpStatus | 의미 (한글) |
|------|-----------|-----------|
| `{도메인}_NOT_FOUND` | 404 | (사용자 보충 필요) |
| `DUPLICATE_{도메인}_ID` | 409 | (사용자 보충 필요) |

> ErrorCode 의 한글 의미는 backend 소스에 주석이 없으므로 frontend·QA 가 명세를 검토하며 보충한다.

## 변경 이력 (N ≥ 2)

> 직전 [SPEC{N-1}.md](SPEC{N-1}.md) 대비 변경점. 자동 추출은 소스 코드 정적 비교 기반이며, 의미 변경(필드명 동일·의미 변화 등) 은 작성자가 "수동 보충" 마커로 보강한다.

### 엔드포인트 변경
| 변경 | HTTP | 경로 | 비고 |
|------|------|------|------|
| 추가 | POST | `/api/{도메인}/new` | 신규 엔드포인트 |
| 제거 | DELETE | `/api/{도메인}/old` | (수동 보충: 마이그레이션 안내 필요) |

### DTO 필드 변경
| DTO | 필드 | 변경 | 이전 | 이후 |
|-----|------|------|-----|-----|
| `{도메인}UpsertDto` | `useYn` | 추가 | — | enum (Y/N), 필수 |
| `{도메인}Dto` | `oldField` | 제거 | string | — |

### ErrorCode 변경
| 변경 | name | httpStatus | 비고 |
|------|------|-----------|------|
| 추가 | `INVALID_{도메인}_STATE` | 400 | — |

## 비고
- 소스 SSOT: `swtp/backend/api/src/main/java/com/mo/swtp/{도메인}/...`
- 본 명세는 backend Swagger 어노테이션 기반 자동 추출 결과를 사람이 검토·보충한 산출물이다.
```

### INDEX.md 템플릿

```markdown
---
covers_dto:
  - {DtoClassName1}
  - {DtoClassName2}
covers_controller:
  - {ControllerClassName1}
covers_errorcode:
  - {ErrorCodeEnumName1}
---
# {슬러그} API 명세 인덱스

## 최신 명세
[SPEC{N}.md](SPEC{N}.md) ({YYYY-MM-DD} 작성)

## 이력

| 버전 | 작성일 | 요약 | 링크 |
|-----|--------|------|------|
| {N} | YYYY-MM-DD | {핵심 변경 한 줄 — 신규 작성 시 "최초 작성"} | [SPEC{N}.md](SPEC{N}.md) |
| {N-1} | YYYY-MM-DD | {이전 변경 요약} | [SPEC{N-1}.md](SPEC{N-1}.md) |

## 관련 backend 작업
- 슬러그: `{슬러그}`
- 산출물 위치: `swtp/backend/docs/{plan,tasks,results,reviews,analyze}/{YYYYMMDD}/{슬러그}/`
```

#### `covers_*` frontmatter 작성 기준

`covers_dto` / `covers_controller` / `covers_errorcode` 3 키는 backend↔frontend 통신 스펙 변경 시 영향 SPEC 슬러그를 O(슬러그 수) lookup 으로 식별하기 위한 메타다 (탐색 비용 O(전체 SPEC 수) → O(슬러그 수) 단축, `swtp/backend/docs/plan/20260508/spec_index_coverage_meta/PLAN1.md` 결정).

- **클래스 단명 사용** (FQCN 금지) — 예: `PumpControlRequestDto` (O), `com.mo.swtp.pump.dto.PumpControlRequestDto` (X). 패키지 이동 시 (예: `com.mo.swtp.pump` → `com.mo.swtp.instrument` 마스터도메인설계 이관 계획) INDEX 메타 obsolete 일괄 갱신 부담 회피
- **통지 SPEC 빈 배열 명시** — 자체 DTO/Controller/ErrorCode 가 없는 통지 SPEC (예: 마스터도메인설계 같은 도메인 분석 통지) 은 `covers_dto: []` / `covers_controller: []` / `covers_errorcode: []` 빈 배열을 명시한다. 키 생략 금지 (`grep "covers_dto:"` 일관 매칭 보장)
- **`/dev:spec` 자동 채우기 사용 권장** — Java 소스 grep SSOT 기반 자동 채우기로 stale 위험 완화 (수동 작성 시 backend 변경 누락 위험). 절차 상세: [`backend/.claude/commands/dev/spec.md`](../../../commands/dev/spec.md) §INDEX.md 갱신
