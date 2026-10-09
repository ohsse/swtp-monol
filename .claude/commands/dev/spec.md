# 8단계: API 명세 전파 (frontend 용 SPEC 문서 작성)

목적 슬러그: $ARGUMENTS

> backend 의 기능 구현이 끝난 뒤 frontend 가 소비할 API 엔드포인트·요청·응답·ErrorCode 명세를 정리하여 모노레포 `swtp/frontend/docs/api-specs/{슬러그}/SPEC{N}.md` 로 전파한다.
> 이 단계는 **선택적·후속** 작업이다. `/dev:commit` 후 안내만 출력되며, 자동 실행되지 않는다.

---

## 전제조건 검증

1. `$ARGUMENTS` 가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:spec 송수펌프제어`" 안내 후 중단
2. **TASK 문서 탐색** — `docs/tasks/` 하위에서 슬러그 일치 디렉토리를 찾는다 (날짜 불문):
   - 가장 큰 cycle 번호(N) 의 TASK 그룹 (`TASK{N}.md` 또는 `TASK{N}-1.md`·`TASK{N}-2.md` ...) 식별
   - TASK 문서가 없으면 → **"TASK 문서를 찾을 수 없습니다. 슬러그를 다시 확인하세요." 안내 후 중단**
   - TASK 문서는 있으나 모두 `status: completed` 가 아니면 → "구현이 완료되지 않은 TASK 가 있습니다. 명세 작성을 진행하시겠습니까?" 사용자에게 확인 후 진행 (강제 중단 X)
3. **frontend 디렉토리 존재 확인** — `swtp/frontend/` 디렉토리 존재 검증 (backend 기준 `../frontend/`)
   - backend 워킹 디렉토리(`swtp/backend/`) 에서 실행되므로 Glob 가 cwd 외부를 보지 못할 수 있다. `Bash ls /c/dev/workspace/swtp/frontend/` 또는 `PowerShell Test-Path ../frontend` 로 확인한다.
   - 미존재 시 → **"frontend 디렉토리가 없습니다. 모노레포 구조를 확인하세요." 안내 후 중단**
   - `swtp/frontend/docs/api-specs/` 디렉토리는 존재하지 않을 수 있음 — Write 도구가 부모 디렉토리를 자동 생성하므로 별도 생성 단계 불필요

---

## 코드 산출물 수집

### 1. TASK 체크박스 파싱
TASK 그룹의 모든 파일을 읽어 다음 패턴의 체크박스 백틱 경로를 수집한다 (`check-task-unstage.sh` 와 동일 파싱 규칙):

```
- [x] `api/src/main/java/com/mo/swtp/{도메인}/controller/{도메인}Controller.java` ...
- [x] `api/src/main/java/com/mo/swtp/{도메인}/dto/{도메인}Dto.java` ...
- [x] `common/src/main/java/com/mo/swtp/{도메인}/exception/{도메인}ErrorCode.java` ...
```

수집 대상은 다음 3개 카테고리로 분류:
- **Controller**: 파일명에 `Controller.java` 포함
- **DTO**: `*Dto.java` (Request/Response 양쪽)
- **ErrorCode**: `*ErrorCode.java`

체크박스가 아닌 일반 텍스트의 경로는 무시한다.

### 2. ANALYZE 산출물 보강 (선택)
`docs/analyze/{*}/{슬러그}/ANALYZE*.md` 가 존재하면 다음 섹션을 추가로 읽어 SPEC 작성의 보조 컨텍스트로 사용한다:
- "## 신규 엔티티/DB 컬럼"
- "## PLAN 으로 전달할 결정 사항"

### 3. 누락 보강
체크박스만으로 추출이 부족하다고 판단되면 (예: Controller 0개), `Glob` 으로 `api/src/main/java/com/mo/swtp/{관련도메인}/controller/*.java` 추가 탐색 후 사용자에게 확인.

---

## SPEC 번호 결정

`swtp/frontend/docs/api-specs/{$ARGUMENTS}/` 디렉토리를 확인한다 (backend 기준 `../frontend/docs/api-specs/{$ARGUMENTS}/`):

- 디렉토리 미존재 → 신규 생성, **N = 1**
- `SPEC{K}.md` 파일들 존재 → 가장 큰 K 발견, **N = K + 1** (이전 버전 SPEC{N-1}.md 와 diff 추출 대상)

---

## SPEC 문서 작성

수집한 Controller·DTO·ErrorCode 소스를 분석하여 명세를 작성한다.

### Controller 분석
각 Controller 파일에서 다음을 추출:
- 클래스 레벨 `@RequestMapping` (base path)
- 클래스 레벨 `@Tag(name=, description=)`
- 각 메서드의 HTTP 메서드(`@GetMapping`·`@PostMapping`·`@PutMapping`·`@DeleteMapping`·`@PatchMapping`)
- 메서드 레벨 `@Operation(summary=, description=)`
- 메서드 레벨 `@ApiResponses` (responseCode + description)
- 파라미터: `@PathVariable`·`@RequestParam`·`@RequestBody`·`@ModelAttribute`
- 반환 타입: `ResponseEntity<CommonResponseDto<T>>` 의 `T`
- 인증 필요 여부: `SecurityFilterChain` 의 permitAll/authenticated 패턴 추정 (불확실하면 "추정" 표시)

### DTO 분석
각 DTO 파일에서:
- 클래스 레벨 `@Schema(description=)`
- 각 필드의 타입·이름·`@Schema(description=, example=, requiredMode=)`·`@NotNull`·`@NotBlank`·`@Size` 등 검증 어노테이션
- enum 타입 필드는 enum 값 목록 포함 (`YnType` 등)
- 상속 관계 (`extends BaseDto` 등) — 상위 필드도 명시

### ErrorCode 분석
각 ErrorCode enum 파일에서:
- 모든 enum 상수의 `name()` + `httpStatus`
- enum 상수의 한글 의미 — 소스에 주석이 있으면 사용, 없으면 빈 칸으로 두고 "사용자 보충 필요" 표시

---

## 산출물 위치

```
swtp/frontend/docs/api-specs/
└── {$ARGUMENTS}/                  # 예: 송수펌프제어
    ├── INDEX.md                   # 최신 SPEC 가리킴 + 이력 표
    ├── SPEC1.md                   # 1차 명세
    ├── SPEC2.md                   # 변경 시
    └── SPEC{N}.md                 # 본 단계에서 새로 작성
```

backend 워킹 디렉토리 기준 절대/상대 경로 모두 허용 (`Write` 도구는 절대 경로 권장):
- 절대: `C:\dev\workspace\swtp\frontend\docs\api-specs\{$ARGUMENTS}\SPEC{N}.md`
- 상대: `../frontend/docs/api-specs/{$ARGUMENTS}/SPEC{N}.md`

---

## SPEC{N}.md 템플릿

[`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) 의 `SPEC{n}.md` 블록(SSOT) 을 그대로 사용한다. 본 커맨드에서는 다음 치환만 적용:

- `{슬러그}` → `$ARGUMENTS`
- `YYYYMMDD` → 본 단계 실행 시점의 오늘 날짜 (TASK 날짜와 별개 — SPEC 는 작업 흐름과 다른 라이프사이클)
- `{N}` → 본 단계에서 결정한 SPEC 번호
- `{N-1}` → 이전 SPEC 번호 (N≥2 일 때만 사용, N=1 이면 변경 이력 섹션 생략)
- TASK·PLAN·ANALYZE·RESULT·REVIEW 링크는 backend 기준 상대 경로로 작성: `../../../../backend/docs/{role}/{date}/{슬러그}/{문서}.md`

---

## INDEX.md 갱신

> **idempotent 안내** — SPEC 작성 후 INDEX 갱신은 분리 단계로 진행된다. SPEC 작성 후 INDEX 갱신 도중 부분 실패 (예: `covers_*` frontmatter 채우기 미수행 / 이력 표 갱신 누락) 가 발생해도 동일 슬러그로 `/dev:spec` 을 재호출하면 본 절차가 idempotent 하게 복구한다 — Java 소스 SSOT 가 항상 최신 기준이며, 자동 채우기 단계 (아래 §자동 채우기) 가 누락된 키만 채우거나 기존 키를 동일 결과로 덮어쓴다.

`swtp/frontend/docs/api-specs/{$ARGUMENTS}/INDEX.md` 가 없으면 신규 작성, 있으면 이력 표에 행 추가.

INDEX.md 템플릿은 [`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) 의 `INDEX.md` 블록(SSOT) 을 사용한다. 갱신 시:
- "최신 명세" 섹션 링크를 `SPEC{N}.md` 로 교체
- "## 이력" 표에 신규 행 추가 — `| {N} | YYYY-MM-DD | {요약} | [SPEC{N}.md](SPEC{N}.md) |`
- 요약은 변경 이력 섹션의 핵심 한 줄 (신규 작성이면 "최초 작성")

### `covers_*` frontmatter 자동 채우기

§코드 산출물 수집 단계에서 추출한 Controller·DTO·ErrorCode 클래스명을 INDEX.md 의 YAML frontmatter `covers_*` 3 키에 자동 채운다 (분리 단계 — A안):

1. §코드 산출물 수집 §1·§3 의 추출 결과 (TASK 체크박스 + Glob 보강) 에서 다음 클래스명을 분류:
   - **DTO**: `*Dto.java` 파일의 클래스 단명 (FQCN 금지) → `covers_dto` 배열
   - **Controller**: `*Controller.java` 파일의 클래스 단명 → `covers_controller` 배열
   - **ErrorCode**: `*ErrorCode.java` 파일의 클래스 단명 → `covers_errorcode` 배열

2. INDEX.md frontmatter 작성·갱신:
   - **신규 INDEX**: 위 분류 결과로 frontmatter 3 키를 포함하여 작성
   - **기존 INDEX**: frontmatter 가 없으면 추가, 있으면 3 키 값을 위 분류 결과로 **덮어쓰기** (Java 소스 SSOT 우선)

3. **통지 SPEC 처리** (Controller·DTO·ErrorCode 추출 결과가 없는 SPEC — 예: 도메인 분석 통지):
   - `covers_dto: []` / `covers_controller: []` / `covers_errorcode: []` 빈 배열 3 키 명시 (키 생략 금지)
   - INDEX 본문 상단에 `> 본 SPEC 은 backend 도메인 분석 통지로 자체 DTO/Controller/ErrorCode 가 없습니다.` 한 줄 주석 추가

4. **클래스 단명 충돌 검사** — 다른 슬러그 INDEX 의 `covers_dto` 와 동명 클래스 (다른 패키지) 가 있으면 사용자에게 안내 후 명명 규칙 보강 ANALYZE 권고. 현재까지 단명 충돌 사례 0건 (마스터도메인설계 ANALYZE1 Round 1 확인).

작성 기준 (클래스 단명 / 빈 배열 / 충돌 검사) 의 1차 정의는 [`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) §INDEX.md 템플릿 §`covers_*` frontmatter 작성 기준 참조.

---

## 변경 이력 추출 (N ≥ 2)

이전 `SPEC{N-1}.md` 와 비교하여 다음 항목 변경을 자동 추출:

| 카테고리 | 비교 대상 | 변경 분류 |
|---------|---------|---------|
| 엔드포인트 | HTTP 메서드 + 경로 + Controller 메서드 | 추가 / 제거 / 수정 (요청·응답 시그니처 변경 포함) |
| Request DTO | 클래스명 + 필드 (이름·타입·필수 여부) | 필드 추가 / 필드 제거 / 타입 변경 / 필수 여부 변경 |
| Response DTO | 동일 | 동일 |
| ErrorCode | enum 상수 이름·httpStatus | 추가 / 제거 / httpStatus 변경 |

추출 결과를 SPEC{N}.md 의 "## 변경 이력" 섹션에 표 형태로 작성. **소스 코드 기반 정적 비교** 만 수행하며, 의미 변경(예: 같은 필드명이지만 의미가 바뀜) 은 자동 감지 불가 — 작성자가 보충하도록 "수동 보충" 마커 사용.

---

## 사용자 검토

문서 작성 완료 후 사용자에게 다음을 보고:

```
=== SPEC 명세 작성 완료 ===

위치: swtp/frontend/docs/api-specs/{슬러그}/SPEC{N}.md
INDEX: swtp/frontend/docs/api-specs/{슬러그}/INDEX.md

[추출 요약]
- Controller: N개
- 엔드포인트: N개
- DTO: N개
- ErrorCode: N개

[변경 이력 — N ≥ 2 일 때만]
- 엔드포인트 추가: N건
- 엔드포인트 제거: N건
- DTO 필드 변경: N건
- ErrorCode 변경: N건

문서를 검토하시고 의미 변경·도메인 컨텍스트가 필요한 항목은 직접 보충하세요.
```

`status` 는 작성 직후 `draft` 로 두며, 사용자가 검토 후 직접 `completed` 로 전환한다 (자동 전환 없음 — 다른 doc-harness 문서와 다름).

---

## 완료 후 자동 전이

자동 전이 규칙은 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:spec` 행을 따른다.

요약: `/dev:spec` 은 종료 단계로 다음 단계 자동 전이 없음. 작성 완료 후 → "명세 전파가 완료되었습니다." 안내.
