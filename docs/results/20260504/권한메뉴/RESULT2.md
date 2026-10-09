---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입 — Fix Cycle 2 결과 (블로커 2건 해소)

## 관련 작업

- [계획안](../../../plan/20260504/권한메뉴/PLAN2.md)
- [태스크](../../../tasks/20260504/권한메뉴/TASK2.md)
- [이전 리뷰](../../../reviews/20260504/권한메뉴/REVIEW1.md) (해소 대상 블로커 2건)
- [PLAN1](../../../plan/20260504/권한메뉴/PLAN1.md) (1차 사이클 — 본 사이클 §변경 사항 #2 의 보강 대상)
- [RESULT1](RESULT1.md) (1차 사이클 결과)

## 작업 요약

REVIEW1 의 블로커 2건을 해소했다. 코드 변경 1건 (`MenuRoleRepository.deleteByIdMenuId` 벌크 DELETE 전환) + 문서 보강 1건 (`PLAN1.md` BaseEntity 4 컬럼 NOT NULL 표기). 도메인 모델·DB 스키마 변경 없음. 회귀 테스트 0건 실패.

`./gradlew.bat clean build` **BUILD SUCCESSFUL** (49s) — 전체 모듈 (common·api·scheduler) 22 tasks 모두 PASS. 1차 사이클의 신규 테스트 19건 + 본 사이클의 회귀 영향 검증 모두 통과.

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 3 |
| 체크박스 수 | 5 |
| 분할 여부 | N |
| 분할 근거 | — (Phase 10+ / 체크박스 60+ 모두 미달) |

## 변경 사항

### 의도된 변경

#### 1. `MenuRoleRepository.deleteByIdMenuId` 벌크 DELETE 전환 (블로커 #1 해소)

**파일**: `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java` (수정)

- 메서드 시그니처: `void deleteByIdMenuId(String menuId)` → `int deleteByIdMenuId(@Param("menuId") String menuId)`
- 어노테이션 추가: `@Modifying` + `@Query("DELETE FROM MenuRole mr WHERE mr.id.menuId = :menuId")`
- import 3건 추가: `org.springframework.data.jpa.repository.Modifying`·`org.springframework.data.jpa.repository.Query`·`org.springframework.data.repository.query.Param`
- Javadoc 갱신: Spring Data 파생 메서드의 N+1 DELETE 회피 사유 + `MenuService.changeMenuRoles` 의 일괄 삭제 후 `saveAll` 1단계 사용 명시

**효과**: 권한 갱신 1단계가 단일 DELETE 쿼리로 처리되어 SELECT-then-REMOVE N+1 패턴 회피. swtp 첫 N:M 매핑 표준 선례에서 잘못된 패턴 정착 위험 제거.

**호출처 영향**: `MenuService.changeMenuRoles:98` 의 `menuRoleRepository.deleteByIdMenuId(menuId);` 형태 (반환값 미사용) 라 컴파일·동작 영향 0.

#### 2. PLAN1.md 본문 BaseEntity 4 컬럼 NOT NULL 표기 추가 (블로커 #2 해소)

**파일**: `docs/plan/20260504/권한메뉴/PLAN1.md` (수정)

- §DB 설계 변경 §`menu_m` DDL 코드 예시 L129~132 의 BaseEntity 4 컬럼 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) 에 `NOT NULL` 표기 추가
- DDL 자체 (V6_6 SQL) 변경 없음 — 이미 NOT NULL 정합 상태
- PLAN1 frontmatter `status: approved`·`updated: 2026-05-06` 유지 — 정책·결정 사항 변경 없는 사후 보강 표기 정정. 보강 이력은 본 RESULT2 에 기록

**효과**: PLAN1 본문 표기와 V6_6 DDL 의 정합 확보. 검토자가 PLAN 만 보고도 BaseEntity 4 컬럼 NOT NULL 정책을 정확히 인지 가능.

### 계획 외 변경

> ROOT [`coding-discipline.md` §3](../../../../.claude/rules/coding-discipline.md) 적용. TASK 체크박스 외 변경의 의도(필수 부수 변경) vs 우연(범위 이탈) 구분 명시.

**없음**. TASK2 의 5 체크박스 외 변경 0건. PLAN2 §룰 갱신 지시서의 `entity-patterns.md` 본문 보강 항목은 사용자 결정 ("승인 → /dev:task 자동 전이" — 룰 갱신 미포함 옵션 선택) 으로 본 사이클 범위 외.

## 테스트 결과

### 빌드
- `./gradlew.bat clean build` **BUILD SUCCESSFUL** (49s)
- 22 actionable tasks: 22 executed
- 모듈별 결과:
  - `:common:test` PASS (회귀)
  - `:api:test` PASS (회귀, `MenuServiceTest`·`MenuQueryServiceTest`·`LoginFacadeServiceTest` 등 1차 사이클 신규 테스트 19건 모두 통과)
  - `:scheduler:test` PASS (회귀)

### 회귀 테스트 영향 분석
본 사이클의 `MenuRoleRepository.deleteByIdMenuId` 시그니처 변경 (`void` → `int`) 은 호출처 미사용 (변수 할당 없음) 이므로 컴파일·동작 영향 0. 1차 사이클의 신규 테스트 + 기존 회귀 테스트 모두 PASS.

### 룰 통과 자동 점검
- `check-errorcode-contract.sh` 통과 (변경 파일 `MenuRoleRepository.java` 는 ErrorCode 미관련)
- `check-ddl-column-comment.sh` 통과 (DDL 변경 없음)
- `check-task-unstage.sh` 미적용 (커밋 시점 훅 — 본 RESULT 단계 외)

## 비고

### 본 사이클의 한정 범위

PLAN2 의 §제외 사항대로 REVIEW1 중간 4건 + 낮음 3건은 본 사이클 미처리:

| 심각도 | 항목 | 분리 사유 |
|--------|------|---------|
| 중간 | createMenu 응답 DTO 누락 (현재 Void) | UX 결정 필요 |
| 중간 | `MenuUpsertDto.useYn` 필드 누락 | 재활성화 경로 설계 결정 필요 |
| 중간 | `MyMenuController.UserRole.valueOf` IllegalArgumentException 처리 | 보안 흐름 검토 필요 |
| 중간 | `LoginFacadeService` 트랜잭션 경계 누락 | Service 트랜잭션 정책 검토 필요 |
| 낮음 | `LoginFacadeService` `@Slf4j` 누락 | 관례 정리 |
| 낮음 | `MenuService.computeDepth` 의도된 N+1 Javadoc 주석 누락 | 문서 정리 |
| 낮음 | 테스트 픽스처 중복 (Reflection ID 강제 주입) | `MenuTestFixture` 추출 — 별도 리팩토링 |

위 7건은 별도 후속 사이클에서 다룬다. 본 사이클은 REVIEW1 의 블로커 (높음) 2건 한정 해소가 목적이며 단순성 우선 원칙 (`coding-discipline.md §2`) 적용.

### 룰 갱신 미진행

PLAN2 §룰 갱신 지시서의 `entity-patterns.md` §N:M 매핑 엔티티 패턴 본문에 "벌크 DELETE 권고" 한 줄 추가 항목은 사용자 결정 (PLAN2 승인 옵션 첫 번째 — 룰 갱신 미포함) 에 따라 본 사이클 미진행. 향후 N:M 매핑 추가 도입 시점에 별도 ANALYZE 또는 PLAN 단계에서 다룰 수 있다.

### REVIEW2 게이트 통과 예상

REVIEW2 단계에서 본 사이클의 변경이 다음을 만족하는지 점검 예정:
- 블로커 (높음) 0건 — REVIEW1 의 2건 모두 해소 검증
- 중간 4건 + 낮음 3건은 본 사이클 범위 외이므로 REVIEW2 의 발견 사항으로 다시 등장 가능 (REVIEW1 인용 재기재). 단 본 사이클의 추가 발견은 0건 예상

### 후속 작업 권고 (RESULT1 §비고 인용 + 본 사이클 추가)

1. **REVIEW1 중간 4건 + 낮음 3건 해소 사이클** — 별도 ANALYZE 또는 단일 PLAN 사이클로 묶어 처리. RESULT1 §비고 §1~5 의 후속 작업 권고와 결합 가능 (특히 §1 stale JWT 무효화)
2. **`entity-patterns.md` 룰 본문 벌크 DELETE 권고 추가** — 향후 N:M 매핑 추가 시점에 SSOT 보강
3. **frontend SPEC1 작성** — `/dev:spec 권한메뉴` 명시 호출 시 진행 (RESULT1 §비고 §2 인용 — 커밋 후 진행)
