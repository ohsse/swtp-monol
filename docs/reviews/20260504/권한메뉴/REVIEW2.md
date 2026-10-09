---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입 — Fix Cycle 2 코드 리뷰

## 관련 결과

- [결과](../../../results/20260504/권한메뉴/RESULT2.md)
- [계획안](../../../plan/20260504/권한메뉴/PLAN2.md)
- [태스크](../../../tasks/20260504/권한메뉴/TASK2.md)
- [이전 리뷰](REVIEW1.md) (해소 대상 블로커 2건)

## 리뷰 범위

본 REVIEW2 는 **Fix Cycle 2 의 변경 사항 (코드 1건 + 문서 1건) 한정** 으로 점검한다. REVIEW1 의 중간 4건 + 낮음 3건은 본 사이클 범위 외 (PLAN2 §제외 사항 명시) 이며, 본 REVIEW2 §개선 제안 §1 의 후속 사이클 권고로 인계.

### 검토 대상 변경

| 파일 | 성격 | 해소 대상 |
|------|------|---------|
| `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java` | 수정 | REVIEW1 블로커 #1 (성능 N+1 DELETE) |
| `docs/plan/20260504/권한메뉴/PLAN1.md` (L129~132) | 수정 | REVIEW1 블로커 #2 (DDL vs PLAN 본문 표기 불일치) |

### 검토 기준

- `swtp/backend/CLAUDE.md`·`api/CLAUDE.md`·`common/CLAUDE.md`
- `swtp/backend/.claude/rules/api-patterns.md` §Repository 패턴
- `swtp/backend/.claude/rules/db/query-tuning.md` §2 §N+1 방지 원칙
- `swtp/backend/.claude/rules/entity-patterns.md` §N:M 매핑 엔티티 패턴
- `swtp/.claude/rules/coding-discipline.md` (§1·§2·§3·§4 4원칙)

### ANALYZE-룰 정합성 점검 수행

본 사이클은 ANALYZE 미작성 (Fix Cycle 키워드 미포함 스킵 — `process/doc-harness/README.md` §수정 사이클 알고리즘 정합, PLAN 직행). PLAN2 §룰 갱신 지시서 1건 (`entity-patterns.md` §N:M 매핑 엔티티 패턴 본문 보강) 은 사용자 결정 (PLAN2 승인 옵션 첫 번째 — "룰 갱신 미포함") 으로 미진행. 따라서 본 점검 적용 외.

## 발견 사항

> ROOT [`coding-discipline.md` §2.1](../../../../.claude/rules/coding-discipline.md) 적용. 카테고리: 복잡도 과잉·도메인 룰 위반·보안·성능·테스트 누락·기타.

### 블로커 해소 검증

| REVIEW1 블로커 | 해소 여부 | 검증 결과 |
|---------------|---------|---------|
| #1 성능 (N+1 DELETE) — `MenuRoleRepository.deleteByIdMenuId` Spring Data 파생 | ✅ 해소 | `MenuRoleRepository.java:38~40` 의 `@Modifying`·`@Query("DELETE FROM MenuRole mr WHERE mr.id.menuId = :menuId")` 적용. 단일 DELETE 벌크 쿼리. 시그니처 `void` → `int` 변경 (호출처 미사용 — 영향 0). `db/query-tuning.md §N+1 방지 원칙` 정합 |
| #2 DDL vs PLAN 본문 표기 불일치 | ✅ 해소 | `PLAN1.md` L129~132 의 BaseEntity 4 컬럼 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) 모두 `NOT NULL` 표기 추가. V6_6 DDL 과 정합 확보 |

### 본 사이클 추가 발견

| 심각도 | 카테고리 | 위치 | 내용 | 개선 제안 |
|--------|---------|------|------|---------|
| — | — | — | 본 사이클의 변경 (코드 1건 + 문서 1건) 에 대한 추가 발견 **0건**. 단순 어노테이션 + 시그니처 + Javadoc + import 추가로 SOLID·Lombok·Javadoc·N+1 방지·N:M 매핑 패턴·api 모듈 경계 모두 정합 | — |

심각도: **높음(블로커) 0건** / 중간 0건 / 낮음 0건 (본 사이클 한정).

## 통과 항목 (요약)

- **블로커 #1 해소**: `MenuRoleRepository.deleteByIdMenuId` 가 `@Modifying @Query` 벌크 DELETE 로 전환됨. import 3건 (`Modifying`·`Query`·`Param`) 정렬 정합. JPQL 문법 (`DELETE FROM MenuRole mr WHERE mr.id.menuId = :menuId`) 정합 — `MenuRoleId.menuId` 복합 PK embedded 필드 접근 패턴 정확
- **블로커 #2 해소**: `PLAN1.md` 본문 BaseEntity 4 컬럼 NOT NULL 표기 정합. DDL (V6_6 SQL) 와 PLAN 본문 일치 확보
- **시그니처 변경 영향**: `void` → `int` 반환 타입 변경은 `MenuService.changeMenuRoles:98` 호출처가 반환값 미사용이라 컴파일·동작 영향 0. 회귀 테스트 통과로 검증
- **Javadoc 갱신**: 변경 메서드 Javadoc 에 N+1 회피 사유 + 호출처 `@link com.mo.swtp.menu.service.MenuService#changeMenuRoles` 명시 — `coding-discipline.md §1` 가정 명시 의무 + `api/CLAUDE.md` Javadoc 정합
- **빌드 / 회귀 테스트**: `./gradlew clean build` BUILD SUCCESSFUL (49s) — 22 tasks 모두 PASS. 1차 사이클 신규 테스트 19건 + 회귀 0건 실패
- **단순성 원칙**: PLAN2 의 §단순성 우선 (블로커만 해소, 중간·낮음 7건 제외) 결정 정합. `coding-discipline.md §2`·§3 (정밀한 수정) 정합 — 무관 인접 코드 임의 개선 0건
- **모듈 경계**: 변경 대상 `MenuRoleRepository.java` 는 `api` 모듈 — `api/CLAUDE.md` 허용 범위 (JpaRepository) 정합, `common` 모듈 침범 0건
- **TASK 규모**: Phase 3 / 체크박스 5 / 분할 N — 분할 기준 (Phase 10+ / 체크박스 60+) 미달로 단일 파일 적정
- **Fix Cycle 알고리즘 정합**: REVIEW1 블로커 키워드 (성능 / DDL vs PLAN 본문 표기) 가 도메인 정합성 키워드 (용어·약어·중복 정의·네이밍 충돌·엔티티 통합) 미포함 → ANALYZE 스킵 + PLAN 직행 정확 적용 (`process/doc-harness/README.md` §수정 사이클 알고리즘)

## 개선 제안

본 REVIEW2 의 추가 발견 사항 0건. 단 REVIEW1 의 미해소 7건 (중간 4건 + 낮음 3건) 은 본 사이클 범위 외이므로 후속 사이클 권고:

### 1. REVIEW1 미해소 항목 처리 (별도 사이클 권고)

| REVIEW1 심각도 | 항목 | 후속 사이클 처리 권고 |
|---------------|------|------------------|
| 중간 | createMenu 응답 DTO 누락 (Void) | 별도 사이클 — UX 결정 (`MenuTreeDto.from()` 또는 별도 `MenuDto`) 또는 RESULT 명시 |
| 중간 | `MenuUpsertDto.useYn` 필드 누락 | 별도 사이클 — 재활성화 경로 설계 (UPSERT vs 별도 엔드포인트) |
| 중간 | `MyMenuController.UserRole.valueOf` `IllegalArgumentException` 처리 | 별도 사이클 — Auth 도메인 ErrorCode 분류 (예: `AuthErrorCode.UNAUTHORIZED`) |
| 중간 | `LoginFacadeService` 트랜잭션 경계 누락 | 별도 사이클 — Service 트랜잭션 정책 (`api/CLAUDE.md` Service 규칙 정합 검토) |
| 낮음 | `LoginFacadeService` `@Slf4j` 누락 | 단일 정리 사이클 (위 4건과 묶음 처리 가능) |
| 낮음 | `MenuService.computeDepth` 의도된 N+1 Javadoc 주석 누락 | 단일 정리 사이클 |
| 낮음 | 테스트 픽스처 중복 (Reflection ID 강제 주입) | 단일 리팩토링 — `MenuTestFixture` 헬퍼 추출 |

위 7건은 단일 ANALYZE 또는 PLAN 사이클로 묶어 처리 가능. RESULT1 §비고 §1~5 의 후속 작업 권고 (특히 stale JWT 무효화) 와 결합 검토.

### 2. `entity-patterns.md` 룰 본문 벌크 DELETE 권고 추가 (선택)

PLAN2 §룰 갱신 지시서의 `entity-patterns.md` §N:M 매핑 엔티티 패턴 본문에 "Spring Data 파생 `deleteByXxx` 메서드 대신 `@Modifying @Query` 벌크 DELETE 권고" 한 줄 추가는 향후 N:M 매핑 SSOT 보강 가치가 있다. 본 사이클은 사용자 결정으로 미진행. 향후 N:M 매핑 추가 도입 시점 또는 별도 단일 PLAN 사이클로 처리 권고.

### 3. frontend SPEC1 작성 (RESULT1 §비고 §2 인용)

본 Fix Cycle 2 는 frontend 인터페이스 변경 없음 — 코드 변경 1건은 Repository 내부 메서드 시그니처. frontend SPEC1 은 1차 사이클 (PLAN1·TASK1·RESULT1) 결과 기반으로 작성하며 본 사이클 변경은 SPEC 영향 없음. 커밋 후 `/dev:spec 권한메뉴` 명시 호출 시 진행.

## 결론

**블로커 (높음) 0건 — `status: approved` 전환.**

본 Fix Cycle 2 는 REVIEW1 의 블로커 2건 (성능 N+1 DELETE + DDL/PLAN 본문 표기 불일치) 을 한정 해소했다. 코드 변경 1건 + 문서 보강 1건 + 회귀 테스트 0건 실패. 단순성 우선 원칙 (`coding-discipline.md §2`) 정합.

다음 단계 안내:
- `/dev:commit 권한메뉴` — **사용자 명시 승인** (`커밋` / `commit`) 시 진행 (transitions.md 핵심 원칙 — 자동 실행 금지)
- 후속 사이클 권고: REVIEW1 미해소 7건 처리 + `entity-patterns.md` 룰 본문 보강 (선택) + frontend SPEC1 작성

### 본 사이클의 트레이서빌리티 평가

- Fix Cycle 진입 → PLAN2 → TASK2 → IMPL → RESULT2 → REVIEW2 표준 흐름 완주 (분할 없음, 단일 파일 적정)
- ANALYZE 조건부 재진입 알고리즘 정확히 적용 (도메인 정합성 키워드 미포함 스킵 — 1단계 절약)
- 단순성 원칙으로 미해소 7건 분리 — 본 사이클 부담 최소화
- 첫 N:M 매핑 표준 선례 (`menu_role_r`) 의 패턴이 본 사이클의 벌크 DELETE 정합으로 보강 — 향후 `user_facility_r`·`role_instrument_r` 등 N:M 추가 시점에 `@Modifying @Query` 벌크 DELETE 가 표준
