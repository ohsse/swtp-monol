---
status: completed
created: 2026-05-07
updated: 2026-05-07
---
# `@Schema(implementation)` 명시 의무화 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260507/swagger_schema_implementation/PLAN1.md)
- [도메인 분석](../../../analyze/20260507/swagger_schema_implementation/ANALYZE1.md)

## Phase

> ROOT [`coding-discipline.md §4.1`](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: 첫 백틱 쌍에 파일 경로, `→ 검증:` 다음에 확인 방법 (백틱 사용 금지). 본 작업은 룰/하네스 변경 (메타 케이스) 으로 backend 소스 컴파일 영향 없음.

### Phase 1: `api-patterns.md` 신규 절 추가

- [x] `backend/.claude/rules/api-patterns.md` L64 직후 (§DTO 패턴 끝, §Swagger/OpenAPI 패턴 앞) 신규 절 `## DTO @Schema(implementation) 명시 패턴` 본문 삽입 → 검증: grep -n "## DTO @Schema(implementation) 명시 패턴" backend/.claude/rules/api-patterns.md → 라인 번호가 §DTO 패턴과 §Swagger/OpenAPI 패턴 사이임을 확인
- [x] `backend/.claude/rules/api-patterns.md` 신규 절 본문에 적용 대상 표 + 적용 제외 표 모두 포함 → 검증: grep -E "사용자 정의 enum|사용자 정의 참조형 DTO|@ArraySchema|Map<K, V>" backend/.claude/rules/api-patterns.md 결과 4건 모두 매칭 PASS
- [x] `backend/.claude/rules/api-patterns.md` 신규 절에 올바른 예 5종 (UserRole / YnType / AiDrvnMode / UserAddressDto / List<UserRole> with @ArraySchema) + 위반 예 1종 포함 → 검증: grep -E "implementation = UserRole.class|implementation = YnType.class|implementation = AiDrvnMode.class|implementation = UserAddressDto.class|@ArraySchema" backend/.claude/rules/api-patterns.md 결과 5종 모두 매칭 PASS
- [x] `backend/.claude/rules/api-patterns.md` 신규 절에 §`YnType` 패턴과의 양립 서브섹션 포함 (allowableValues·@Pattern·example 중복 금지 정책 그대로 유지 명시) → 검증: grep "YnType.*@Schema(allowableValues)" backend/.claude/rules/api-patterns.md 매칭 PASS
- [x] `backend/.claude/rules/api-patterns.md` 본문 어휘 "enum 타입" 사용 (단독 "열거형" 표기 금지) → 검증: 신규 절 본문 한정 grep "열거형" 결과가 "enum 타입 (열거형)" 같은 병기 형태 외에 단독 등장 0건

### Phase 2: `wtp-backend-engineer.md` 항목 6 인용 근거 확장

- [x] `backend/.claude/agents/wtp-backend-engineer.md` L33 항목 6 (`Swagger / OpenAPI`) 인용 근거에 `§DTO @Schema(implementation) 명시 패턴` 추가 + DTO 사용자 정의 클래스 implementation 명시 점검 의무 1줄 추가 → 검증: grep "§DTO @Schema(implementation) 명시 패턴" backend/.claude/agents/wtp-backend-engineer.md 매칭 PASS

### Phase 3: `entity-patterns.md` 양방향 교차 참조 추가

- [x] `backend/.claude/rules/entity-patterns.md` §여부(Y/N) 필드 패턴 끝에 양방향 교차 참조 bullet 1줄 추가 ("DTO 의 *Yn 필드는 @Schema implementation = YnType.class 명시 — api-patterns.md §DTO @Schema(implementation) 명시 패턴 참조") → 검증: grep "implementation = YnType.class.*api-patterns.md §DTO @Schema(implementation) 명시 패턴" backend/.claude/rules/entity-patterns.md 매칭 PASS

### Phase 4: ANALYZE1 룰 갱신 지시서 체크박스 [x] 전환 (메타 케이스)

- [x] `backend/docs/analyze/20260507/swagger_schema_implementation/ANALYZE1.md` "## 룰 갱신 지시서" 절의 3건 체크박스 모두 [x] 로 전환 → 검증: grep -c "^- \[x\] \`backend/.claude" backend/docs/analyze/20260507/swagger_schema_implementation/ANALYZE1.md 결과 3건 이상

### Phase 5: 빌드 검증

- [x] `./gradlew.bat build` 실행하여 BUILD SUCCESSFUL 출력 확인 → 검증: 빌드 표준 출력 마지막에 BUILD SUCCESSFUL 문자열 포함 (룰 변경만이므로 컴파일·테스트 영향 0 예상)

## 비고

### 메타 케이스 안내

본 작업은 **룰 변경 자체** 가 IMPL 산출물이다. Phase 4 의 ANALYZE1.md 룰 갱신 지시서 체크박스 [x] 전환은 일반 사이클의 ANALYZE→PLAN 경계 적용 패턴과 다른 본 사이클 IMPL 단계 적용 결과를 기록하는 메타 동작이다.

### 검증 패턴 일관성

모든 Phase 의 검증 영역에 백틱 사용 금지 — `check-task-unstage.sh` 훅의 `sed -n 's/^- \[.\] \`\([^\`]*\)\`.*/\1/p'` 파싱 호환성 위해. 검증 명령은 일반 텍스트로 기록 (예: `./gradlew.bat build` → `./gradlew.bat build`).

### 후속 사이클 (범위 외)

24건 위반 필드 일괄 마이그레이션은 본 작업 범위 외 — 별도 `/dev swagger_schema_migration` 사이클 분리. 본 작업 IMPL 완료 후 grep 기반 정확한 위반 목록을 후속 사이클 ANALYZE 의 "## 작업 배경" 으로 인계.

## 산출물

본 작업은 Medium 규모이므로 RESULT/REVIEW 단계 면제. `/dev:commit` 직후 후속 사이클 안내로 종료.

- 커밋 메시지 (한국어): `chore: DTO @Schema(implementation) 명시 의무화 룰 신설`
