---
status: draft
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입 — 코드 리뷰

## 관련 결과

- [결과](../../../results/20260504/권한메뉴/RESULT1.md)
- [계획안](../../../plan/20260504/권한메뉴/PLAN1.md)
- [태스크](../../../tasks/20260504/권한메뉴/TASK1.md)
- [ANALYZE1](../../../analyze/20260504/권한메뉴/ANALYZE1.md)
- [ANALYZE2](../../../analyze/20260504/권한메뉴/ANALYZE2.md) (N:M 관계 룰 정합 재검토)

## 리뷰 범위

- 신규 클래스 21개 + DDL 1개 + 기존 수정 1건 + 룰 갱신 6건
- `feature-dev:code-reviewer` 자동 리뷰 수행
- **ANALYZE-룰 정합성 점검 수행** — ANALYZE1 (10건) + ANALYZE2 (6건) = 총 16건 룰 갱신 지시서 모두 실제 변경에 반영됨 (누락 0건)
- 검토 기준:
  - `swtp/backend/CLAUDE.md`·`api/CLAUDE.md`·`common/CLAUDE.md`
  - `swtp/backend/.claude/rules/{naming,api-patterns,entity-patterns,exception-patterns}.md`
  - `swtp/backend/.claude/rules/db/README.md` 자식 3 룰
  - `swtp/.claude/rules/coding-discipline.md` (§1·§2·§3·§4 4원칙)

## 발견 사항

> ROOT [`coding-discipline.md` §2.1](../../../../.claude/rules/coding-discipline.md) 적용. 카테고리: 복잡도 과잉·도메인 룰 위반·보안·성능·테스트 누락·기타.

| 심각도 | 카테고리 | 위치 (파일:라인) | 내용 | 개선 제안 |
|--------|---------|----------------|------|---------|
| **높음** | 성능 (N+1 DELETE) | `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java:29` | Spring Data JPA `deleteByIdMenuId` 파생 메서드는 `SimpleJpaRepository` 가 내부적으로 SELECT 후 `em.remove()` 반복 호출 — N+1 DELETE 패턴. `db/query-tuning.md §N+1 방지 원칙` 위반. 매핑 카디널리티가 작아 (현재 2종) 영향은 적으나 권한 종류 확장 시 영향. 또 swtp 첫 N:M 매핑 표준 선례라는 점에서 잘못된 패턴 정착 위험. | `@Modifying @Query("DELETE FROM MenuRole mr WHERE mr.id.menuId = :menuId") int deleteByIdMenuId(@Param("menuId") String menuId);` 벌크 DELETE JPQL 로 교체. `entity-patterns.md §N:M 매핑 엔티티 패턴` 의 권장 패턴으로 룰 본문에도 추가 검토 |
| **높음** | DDL vs PLAN 본문 표기 불일치 | `common/src/main/resources/db/init/V6_6__menu_master_table.sql:37-40` vs `docs/plan/20260504/권한메뉴/PLAN1.md` §DB 설계 변경 코드 예시 | DDL 의 `rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id` 가 NOT NULL (BaseEntity 정합). PLAN1 본문 코드 예시는 NOT NULL 미표기. 실제로는 V6_5 (`rawdata_1m_h.sql`) 등 기존 BaseEntity 상속 테이블 모두 NOT NULL 적용이 표준이고 `BaseEntity.java:40,45,50,55` 가 `nullable = false` 라 구현이 정합. PLAN 코드 예시 단순화 표기 누락. | DDL 자체는 표준 정합으로 변경 불요. **PLAN1.md 본문의 BaseEntity 4 컬럼 표기에 NOT NULL 명시 추가** (`rgstr_dtm TIMESTAMP NOT NULL,` 등) — 사용자 검토자가 PLAN 만 보고도 정확히 인지하도록 |
| 중간 | 응답 DTO 누락 | `api/src/main/java/com/mo/swtp/menu/web/MenuController.java:55-61` `createMenu` | PLAN1 §3.5 표 "메뉴 생성" 응답이 PLAN 본문 §도메인 모델 코드 예시상 `MenuDto` 였으나 실구현은 `Void`. 클라이언트가 별도 조회 없이 메뉴 ID 알 수 없음. 일관성 차원에서 `MenuTreeDto.from(menu)` 또는 신규 `MenuDto` 응답이 더 사용성 우수 | `createMenu` 가 `MenuTreeDto.from(savedMenu)` 반환하도록 변경 (children 빈 트리 노드). 또는 RESULT §계획 외 변경에 "Void 채택 — 메뉴 생성 후 GET /api/menus 또는 GET /api/menus/me 재조회로 확인" 명시 |
| 중간 | DTO 필드 누락 | `api/src/main/java/com/mo/swtp/menu/dto/MenuUpsertDto.java` | PLAN1 §도메인 모델 §`MenuUpsertDto` 에 `useYn(YnType)` 필드 명시되어 있으나 실구현 누락. 결과적으로 비활성화된 메뉴를 다시 활성화 (Y 로 전환) 할 경로 없음. `Menu.changeInfo()` 에도 `useYn` 파라미터 없음 | (a) `MenuUpsertDto` 에 `useYn` 추가 + `Menu.changeInfo()` 에 `useYn` 파라미터 추가. (b) 또는 재활성화 전용 엔드포인트 분리 (`PUT /{menuId}/activate`). 본 작업 범위에서 의도적으로 비활성화 단방향만 지원했다면 RESULT §계획 외 변경에 명시 |
| 중간 | 예외 처리 누락 | `api/src/main/java/com/mo/swtp/menu/web/MyMenuController.java:61` | `UserRole.valueOf(roleClaim.toString())` 이 유효하지 않은 role 값 (손상된 토큰·레거시 코드) 시 `IllegalArgumentException` 던짐 → `RestApiAdvice` 미캐치 시 500 응답 | `try { return UserRole.valueOf(...); } catch (IllegalArgumentException e) { throw new RestApiException(AuthErrorCode.UNAUTHORIZED); }` 추가. JWT 발급 시점에서 enum 직접 직렬화로 보장도 추가 안전망 |
| 중간 | 트랜잭션 경계 누락 | `api/src/main/java/com/mo/swtp/auth/service/LoginFacadeService.java:21` | PLAN1 §6 본문에 "@Transactional(readOnly = true) 단일 경계" 명시되었으나 실구현 누락. `AuthService.login()` 과 `MenuQueryService.findMenuTreeByRole()` 두 트랜잭션이 분리 처리. `api/CLAUDE.md` Service 규칙 (클래스 레벨 readOnly 트랜잭션 기본) 위반 | 클래스 레벨 `@Transactional(readOnly = true)` 추가 |
| 낮음 | 관례 누락 (@Slf4j) | `api/src/main/java/com/mo/swtp/auth/service/LoginFacadeService.java` | `api-patterns.md §Service 패턴` 의 표준은 `@Slf4j` 보유. 동일 패키지 `AuthService` 와 불일치 | `@Slf4j` 추가 (현재 로그 호출 없어도 관례 유지) |
| 낮음 | 의도된 N+1 명시 누락 | `api/src/main/java/com/mo/swtp/menu/service/MenuService.java:127-140` `computeDepth` | while 루프 내 `findById()` — 깊이 N=3 제약하 최대 3 SELECT. `validateParent` 가 부모 1회 조회 후 `computeDepth(parent)` 재사용으로 1 절약. 의도된 루프이나 `db/query-tuning.md §N+1 방지 원칙` 관점에서 주석 부재 | `computeDepth` 메서드 Javadoc 에 "최대 N=3 SELECT — 의도된 부모 체인 순회. 깊이 제약으로 N+1 문제 무시 가능" 주석 추가. 또는 재귀 CTE 단일 쿼리화 검토 (현 시점 과도) |
| 낮음 | 테스트 픽스처 중복 | `api/src/test/java/com/mo/swtp/menu/service/MenuServiceTest.java:184-197` + `MenuQueryServiceTest.java:84-91` | Reflection 으로 `Menu.menuId` 필드 강제 주입하는 픽스처가 두 테스트에 복제. 필드명 변경 시 런타임 오류 + IDE 리팩토링 안전망 부재 | (a) 공통 헬퍼 `MenuTestFixture` 추출 (test 소스셋 공유). (b) 또는 공통 부모 테스트 클래스. 단기적으로 (a) 권고 |

심각도: **높음(블로커) 2건** / 중간 4건 / 낮음 3건

## 통과 항목 (요약)

- **ErrorCode 계약**: `MenuErrorCode` `httpStatus(int)` 단일 필드, `String message` 등 금지 패턴 0건. `check-errorcode-contract.sh` 통과
- **엔티티 패턴**: `Menu` — `@Getter`만, `@NoArgsConstructor(PROTECTED)`, `@AllArgsConstructor(PRIVATE)`, 정적 팩토리 `create()`, 변경 메서드 `changeInfo()`·`deactivate()`, `YnType` enum 패턴 모두 준수
- **N:M 매핑 패턴 (신규 룰)**: `MenuRole` — `@EmbeddedId`·`@MapsId`·`@ManyToMany` 미사용·BaseEntity 미상속(B안)·`@CreatedDate`·`@CreatedBy` 직접 선언·`updatable=false`·`nullable=false` 모두 신설 룰 정합. **첫 N:M 표준 선례 정착 양호**
- **복합 PK**: `MenuRoleId` — `@Embeddable`·`Serializable`·`@EqualsAndHashCode`·생성자 접근 레벨 정합
- **클래스 네이밍**: `MenuRole` (Mapping 접미사 금지)·`MenuRoleId` (`{엔티티명}Id`) — `naming.md` 신설 행 준수
- **DDL COMMENT 의무**: V6_6 모든 컬럼 `COMMENT ON COLUMN` 작성. BaseEntity 4 표준 라벨 준수. `check-ddl-column-comment.sh` 통과
- **Swagger**: `@Tag`·`@Operation`·`@ApiResponses` 5종 엔드포인트 + MyMenu 1종 모두 작성, 응답 코드 200/400/401/403/404/500/409 누락 0건
- **응답 형태**: `CommonController.getResponseEntity()` + `CommonResponseDto<T>` 일관
- **모듈 경계**: common 에 `@Entity`·`@Embeddable` 만, api 에 Repository·Service·Controller — `api/CLAUDE.md` 금지 범위 위반 0건
- **보안**: JWT secret·DB 계정 하드코딩 0건. `RoleGuard.requireAdmin` 5종 엔드포인트 전원 적용
- **TASK 규모**: Phase 9 / 체크박스 35 — 분할 기준 (Phase 10+ / 체크박스 60+) 미달, 단일 파일 적정
- **ANALYZE-룰 정합성 점검**: ANALYZE1 (10) + ANALYZE2 (6) = 16건 룰 갱신 지시서 모두 실제 변경에 반영됨, 누락 0건
- **테스트 시나리오**: MenuServiceTest 9건 + MenuQueryServiceTest 4건 + LoginFacadeServiceTest 2건 + MenuRoleTest 4건 = 19건 PASS, 회귀 0건 실패
- **빌드**: `./gradlew build` BUILD SUCCESSFUL (44s)

## 개선 제안

블로커 2건은 다음 방향으로 해소 권고:

1. **`deleteByIdMenuId` 벌크 DELETE 전환** (블로커 #1)
   - `MenuRoleRepository.deleteByIdMenuId` → `@Modifying @Query` JPQL 로 교체
   - `entity-patterns.md §N:M 매핑 엔티티 패턴` §매핑 갱신 (정책 본문) 에 "벌크 DELETE 권고" 한 줄 추가도 함께 검토

2. **PLAN1 본문 BaseEntity 4 컬럼 NOT NULL 표기 추가** (블로커 #2)
   - `docs/plan/20260504/권한메뉴/PLAN1.md` §DB 설계 변경 §`menu_m` DDL 코드의 4 행에 `NOT NULL` 명시
   - 또는 RESULT §계획 외 변경에 "PLAN 코드 예시 단순화 — DDL 은 BaseEntity nullable=false 정합으로 NOT NULL 적용" 명시
   - DDL 자체는 변경 불요

중간 4건은 Fix Cycle 사이클에서 함께 해소 권고. 낮음 3건은 후속 정리.

## 결론

**블로커 2건 발견 — Fix Cycle 진입 권고.**

도메인 정합성 키워드 (`용어`·`약어`·`중복 정의`·`네이밍 충돌`·`엔티티 통합`) 미포함 → ANALYZE 재진입 불요, **PLAN2 직행**.

Fix Cycle 진입 시:
- PLAN2.md 작성 (`docs/plan/20260504/권한메뉴/PLAN2.md`) — 본 REVIEW1 의 블로커 2건 + 중간 4건 (선택) 해소 방향
- TASK2.md → impl → RESULT2 → REVIEW2 사이클

본 REVIEW1 의 통과 항목 (특히 N:M 매핑 패턴 신설 룰 정합·ANALYZE-룰 정합성 점검 통과) 은 본 사이클 코드 품질이 전반적으로 양호함을 시사하며, 블로커 2건은 단순 라인 수정으로 해소 가능하므로 Fix Cycle 부담은 작다.
