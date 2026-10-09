---
status: approved
created: 2026-05-07
updated: 2026-05-07
---
# `@Schema(implementation)` 명시 의무화 — 도메인 분석

## 작업 배경

DTO 필드 타입이 사용자 정의 클래스 (enum / 참조형 DTO) 인 경우 `@Schema(... , implementation = X.class)` 명시를 룰로 의무화한다. SpringDoc/Swagger 자동 추출기가 frontend SPEC 명세 (`/dev:spec` 단계) 에 enum 허용값·중첩 DTO 스키마를 정확히 노출하기 위함이다.

- **사용자 결정 (Plan Phase 3)**: 적용 범위 = **표준 권고** (enum + 참조형 DTO + List/Set element 의무, Map value 권고), 마이그레이션 처리 = **룰만 신설** (24건 위반은 후속 사이클 분리)
- **작업 유형**: 룰/하네스 강화 (backend 코드 직접 변경 없음 — `.claude/rules/`·`.claude/agents/` 만 수정)
- **외부 산출물**: 없음 (요구사항 docx·도메인모델링 png 등 부재)
- **참조 문서**: `~\.claude\plans\schema-implementation-schema-twinkling-swing.md` (Plan Mode 산출물, ExitPlanMode 승인됨)

## 회의록 (5인 회의 Round 1)

### 안건 1: 룰 신설 위치 (api-patterns.md L64 직후) 적정성

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: L64 (§DTO 패턴 끝) 직후 배치는 자연스러움 — DTO 필드 선언 시점 의무이므로 §DTO 패턴 직후가 독자 흐름 정합. `naming.md` 와 충돌 없음. 단 `entity-patterns.md` L119 의 단방향 링크 (현재 entity → api-patterns 만 존재) 만으로는 entity-patterns.md 만 읽은 독자가 `implementation` 의무를 모르므로 **양방향 교차 참조 추가 필요** (권고 등급)
- **결론**: L64 직후 배치 + `entity-patterns.md` 양방향 교차 참조 1줄 추가 채택

### 안건 2: 표준 권고 범위 (enum + 참조형 + List/Set 의무, Map 권고) 정합성

- 호출 에이전트: `wtp-backend-engineer`, `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: SpringDoc 동작 근거 (타입 파라미터 소거 시 참조형 추론 불가) 와 정합. `YnType` 정책 (`@Schema(allowableValues)·@Pattern·example` 중복 금지) 과 충돌 없음 — `implementation = YnType.class` 는 SpringDoc 이 enum 허용값을 자동 추출하는 수단으로 `allowableValues` 중복과 별개. 단 신설 절 본문에 양립 관계를 "YnType 은 `implementation` 의무 적용 대상이며, `allowableValues`·`@Pattern`·`example` 은 별도 기술 금지" 로 명시 필요 (참고 등급)
  - **wtp-glossary-manager**: ROOT 어휘 사전 (단어/데이터 도메인/비즈니스 약어) 및 backend 표준 용어 4층 모두 신규 등록·폐기·통합 **없음**. SpringDoc 어노테이션 (`@Schema`·`@ArraySchema`·`implementation`) 은 외부 라이브러리 식별자로 ROOT 어휘 사전 등록 대상 외
- **결론**: 표준 권고 범위 정합. 신설 절 본문에 `YnType` 양립 관계 명시 추가

### 안건 3: REVIEW 자동 점검 항목 추가 위치 (항목 12 신설 vs 항목 6 확장)

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **항목 12 신설보다 항목 6 (`Swagger / OpenAPI`) 확장이 책임 경계 정합**. 현재 항목 6 인용 근거가 `api-patterns.md §Swagger/OpenAPI 패턴` 인데 신설 절도 동일 파일 내 배치되므로, 인용 근거를 `§Swagger/OpenAPI 패턴 + §DTO @Schema(implementation) 명시 패턴` 으로 확장하면 항목 체계가 단순. 항목 12 별도 신설 시 §Swagger/OpenAPI 와 §DTO @Schema 가 분리 관리되어 리뷰어가 중복 점검하거나 한쪽 누락 위험
- **결론**: **항목 6 확장 채택** (Plan 의 "항목 12 추가 또는 항목 6 확장" 옵션 중 후자 선택). 인용 근거를 `api-patterns.md §Swagger/OpenAPI 패턴 + §DTO @Schema(implementation) 명시 패턴` 두 절로 확장

### 안건 4: 자동 차단 훅 도입 보류 정합성

- 호출 에이전트: `wtp-backend-engineer`, `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: ROOT `coding-discipline.md §7.2` 보류 항목 + §7.3 트리거 ("도메인 안전·보안에 영향을 준 경우") 미해당. `@Schema(implementation)` 누락은 SpringDoc 문서화 품질 문제로 정수장 안전·제어 로직 직결 아님 — 훅 보류 판정 정합. REVIEW 자동 점검 등급 (권고) 으로 충분
  - **wtp-domain-expert**: 도메인 4영역 (알람·인터록·운전 모드·이력 기록) 직접 영향 없음. §2.5 면책 영역 (정수장 안전 도메인 / DB 쿼리 빌더) 과 범위 비중복 — 어노테이션 추가는 줄 수 정량 기준에 산입되지 않으므로 면책 조항 소비 없음
- **결론**: 본 시점 자동 차단 훅 신설 보류, REVIEW 자동 점검만으로 강제

### 안건 5: 도메인 룰 4영역 영향 평가

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4영역 모두 **비해당**. 단 도메인 안전 관련 enum (`AiDrvnMode`·`AiModeCode`·`AlarmSeverityType`·`InterlockType` 등) 이 DTO 필드로 노출될 때 본 룰이 적용되어 **frontend 가 `AI_RECOMD`/`AI` 혼용·`ai_mode_cd` 숫자 코드를 문자열 오매핑하는 위험을 사전 차단** — 긍정 효과. `ai_drvn_mod`/`ai_mode_cd` 두 축 분리 설계의 SPEC 노출 정확도 향상 (참고 등급)
- **결론**: 도메인 4영역 직접 영향 없음. 신설 절 본문의 올바른 예에 도메인 안전 enum (`AiDrvnMode`) 1건 예시 포함 권장

### 안건 6: DB / 표준 데이터 도메인 영향 평가

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: PostgreSQL DDL·인덱스·파티션·무중단 변경·N+1·표준 데이터 도메인 (`DOM_*`) 모두 **비해당**. `@Schema(implementation)` 어노테이션은 SpringDoc 런타임 메타데이터로 DB 매핑과 무관. DBA 2차 승인 대상 신규 데이터 도메인 0건. 후속 마이그레이션 사이클 시 신규 enum 이 도입되면 `@Enumerated(EnumType.STRING)` 정합성을 기존 절차로 점검 (추가 조치 없음)
- **결론**: DB 영향 없음. PLAN approved 진행 가능

### 안건 7: 신설 절 본문 어휘 일관성

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: "열거형" 단독 표기보다 "enum" 또는 "enum 타입" 이 기존 `entity-patterns.md` §여부(Y/N) 필드 패턴 / `api-patterns.md` DTO 패턴 표기 방식과 정합. 어휘 사전 충돌 아닌 **문서 내 표기 일관성** 권고 (낮음 등급)
- **결론**: 신설 절 본문에서 "열거형" 표기 사용 시 "enum 타입" 으로 정렬

## 표준 사전 카탈로그

### 신규 표준 단어
없음. (4층 어휘 사전 모두 신규 등록 0건 — `wtp-glossary-manager` 판정)

### 신규 표준 데이터 도메인
없음. (`DOM_*` 신설·변경·폐기 0건 — `wtp-dba-reviewer` 2차 승인 대상 없음)

### 신규 표준 용어
없음. (DB 컬럼명 신설 0건 — 본 작업은 SpringDoc 어노테이션 표준만 다룸)

## 신규 엔티티/DB 컬럼

없음. 본 작업은 룰/하네스 변경만 다루며 backend 소스 코드·DB 스키마 변경 없음.

## 기존 사전·패턴과의 충돌

5인 회의 Round 1 결과 **충돌 0건**.

- ROOT 어휘 사전 4층 — 신규/충돌/폐기 모두 비해당
- `entity-patterns.md` §여부(Y/N) 필드 패턴 — `YnType` 정책 (`allowableValues`·`@Pattern`·`example` 중복 금지) 과 양립 (해소책: 신설 절 본문에 양립 관계 명시 + 양방향 교차 참조 1줄 추가)
- `api-patterns.md` §DTO 패턴 / §Swagger/OpenAPI 패턴 — 신설 절이 두 기존 절 사이에 배치되어 흐름 정합
- ROOT `coding-discipline.md` — §1·§3·§7 모두 정합. ROOT 자산 변경 아니므로 ROOT 어휘 사전 갱신 무관

## PLAN 으로 전달할 결정 사항

1. **변경 파일 3개**:
   - `backend/.claude/rules/api-patterns.md` — L64 직후 신규 절 `## DTO @Schema(implementation) 명시 패턴` 삽입. 본문 = 적용 대상 표 + 적용 제외 표 + 올바른 예 (enum / `YnType` / 참조형 DTO / `@ArraySchema`) + 위반 예 + `YnType` 양립 관계 명시 + 도메인 안전 enum (`AiDrvnMode`) 예시 1건
   - `backend/.claude/agents/wtp-backend-engineer.md` — **L33 항목 6 (`Swagger / OpenAPI`) 확장** (항목 12 별도 신설 아님). 인용 근거를 `api-patterns.md §Swagger/OpenAPI 패턴 + §DTO @Schema(implementation) 명시 패턴` 두 절로 확장
   - `backend/.claude/rules/entity-patterns.md` — §여부(Y/N) 필드 패턴 끝에 양방향 교차 참조 1줄 추가 ("DTO 의 `*Yn` 필드는 `@Schema(... , implementation = YnType.class)` 명시 — `api-patterns.md §DTO @Schema(implementation) 명시 패턴` 참조")

2. **본문 어휘 일관성**: 신설 절 본문은 "열거형" 단독 표기 대신 "enum 타입" 사용

3. **자동 차단 훅 도입 보류**: 본 시점 미도입 (ROOT `coding-discipline.md §7.2` 정합). REVIEW 자동 점검만으로 강제

4. **마이그레이션 처리**: 룰만 신설 — 24건 위반 필드 일괄 수정은 본 사이클 범위 외. 위반 목록은 RESULT/PLAN 비고에 표 형태로 보고

5. **후속 사이클 안건**: `swagger_schema_migration` (가칭) 슬러그로 별도 `/dev` 사이클 분리. 24건 (enum 17건 + 참조형 7건+) 일괄 수정

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `Map<K, V>` value 가 사용자 정의 클래스인 경우 권고 등급 (의무 미적용) — 향후 적용 사례 누적 시 의무 격상 검토 | 결정 | Plan Phase 3 사용자 결정 (적용 범위 = 표준 권고) 으로 변환됨 |
| `@ArraySchema(schema = @Schema(implementation = E.class))` 사용법이 SpringDoc 표준 동작 | 가정 | `wtp-backend-engineer` Round 1 검토에서 SpringDoc 타입 파라미터 소거 케이스에 정합 확인 |
| 24건 위반 필드 마이그레이션은 후속 별도 `/dev` 사이클로 분리 | 결정 | Plan Phase 3 사용자 결정 (마이그레이션 처리 = 룰만 신설) 으로 변환됨 |
| 본 사이클의 IMPL 결과는 룰 자체이므로 ANALYZE의 "## 룰 갱신 지시서" 체크박스가 곧 IMPL 단계의 변경 파일과 동일 (메타 사이클) | 가정 | PLAN approved 의 전제조건은 본 ANALYZE 의 다른 절 (변경 파일 결정) 만족 시 충족 — 본 작업의 IMPL 단계에서 체크박스 [x] 전환 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `api-patterns.md` 신규 절이 §DTO 패턴 끝과 §Swagger/OpenAPI 패턴 사이에 배치 | grep "## DTO @Schema(implementation) 명시 패턴" `backend/.claude/rules/api-patterns.md` 매칭 + 라인 번호가 §DTO 패턴과 §Swagger/OpenAPI 패턴 사이 |
| 신설 절 본문에 4표 (적용 대상 / 적용 제외) + 올바른 예 (enum/YnType/참조형 DTO/`@ArraySchema`/도메인 enum) + 위반 예 + `YnType` 양립 관계 + 본문 "enum 타입" 표기 모두 포함 | grep 패턴 매칭 5건 (적용 대상 / 적용 제외 / @ArraySchema / YnType 양립 / AiDrvnMode) 모두 PASS |
| `wtp-backend-engineer.md` L33 항목 6 인용 근거 확장 | grep "api-patterns.md §Swagger/OpenAPI 패턴 + §DTO @Schema(implementation) 명시 패턴" `backend/.claude/agents/wtp-backend-engineer.md` 매칭 |
| `entity-patterns.md` §여부(Y/N) 필드 패턴 끝에 교차 참조 1줄 추가 | grep "DTO 의 `\*Yn` 필드는 `@Schema(... , implementation = YnType.class)` 명시" `backend/.claude/rules/entity-patterns.md` 매칭 |
| 빌드 영향 없음 (rule 변경만이므로 컴파일·테스트 영향 0) | ./gradlew.bat build 결과 BUILD SUCCESSFUL 출력 확인 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 룰은 SpringDoc 어노테이션 표준만 다룸 — 알람 임계값·전이 조건·복귀 조건 무관. 단 `AlarmSeverityType` enum 이 DTO 필드로 노출 시 본 룰 적용 — frontend SPEC 정확도 향상 (긍정 효과, 직접 영향 없음) |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 인터록 선행조건 검사·기동 차단·복구 후 재검사 로직 무관 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`/`ai_mode_cd` 두 축 분리 설계의 도메인 enum (`AiDrvnMode`·`AiModeCode`) 이 DTO 필드로 노출 시 본 룰 적용 — frontend 가 `AI_RECOMD`/`AI` 혼용·숫자 코드를 문자열 오매핑하는 위험 사전 차단 (긍정 효과, 직접 영향 없음) |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason` 등 이력 기록 로직 무관 |

> "비해당" 단독 4건 차단 해제 조건: (1) 각 행에 구체 사유 명기 ✅, (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 명시 ✅. 도메인 룰 직접 영향 없음 + 룰/하네스 변경 작업 특성으로 형식적 충족 패턴 회피 확인.

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**메타 케이스 안내**: 본 작업은 **룰 변경 자체** 가 IMPL 의 산출물이다. 따라서 본 룰 갱신 지시서의 체크박스는 본 작업의 IMPL 단계에서 [x] 전환된다 (후속 사이클이 아닌 본 사이클 IMPL 적용).

- [x] `backend/.claude/rules/api-patterns.md` — L64 직후 신규 절 `## DTO @Schema(implementation) 명시 패턴` 삽입 (적용 대상/제외 표 + 올바른 예 + 위반 예 + `YnType` 양립 + 도메인 enum 예시)
- [x] `backend/.claude/agents/wtp-backend-engineer.md` — L33 항목 6 (`Swagger / OpenAPI`) 인용 근거를 `§Swagger/OpenAPI 패턴 + §DTO @Schema(implementation) 명시 패턴` 으로 확장
- [x] `backend/.claude/rules/entity-patterns.md` — §여부(Y/N) 필드 패턴 끝에 양방향 교차 참조 1줄 추가

> 본 체크박스의 [x] 전환은 본 사이클 `/dev:impl` 단계에서 수행한다. PLAN approved 시점에는 [ ] 상태 유지 — Plan 단계에서는 변경 파일 목록·검증 절차의 명시성만 확인.

## 24건 위반 발견 사항 (마이그레이션 후속 사이클 대상)

본 작업 범위 외 — 후속 사이클 (`swagger_schema_migration` 가칭) 로 분리. PLAN/RESULT 의 비고 섹션에 다음 표 형태로 보고 예정.

| 분류 | 추정 건수 | 대표 위반 |
|------|---------|----------|
| 사용자 정의 enum 누락 | 17건 | `UserDto.userRole` (UserRole), `UserDto.useYn` (YnType) |
| 중첩 DTO/`List<참조형>` 누락 | 7건+ | (PLAN 단계에서 정확한 파일·라인 보충) |

> 24건의 정확한 파일·필드명·필드 타입 목록은 본 작업의 RESULT (Medium 규모 면제 대상이나 비고 섹션 활용) 또는 PLAN 의 "## 비고" 섹션에 보강.

## 산출물

- [계획안](../../../plan/20260507/swagger_schema_implementation/PLAN1.md) (다음 단계에서 작성)
