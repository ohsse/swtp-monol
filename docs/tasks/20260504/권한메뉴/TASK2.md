---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입 — Fix Cycle 2 (블로커 2건 해소)

## 관련 계획

- [계획안](../../../plan/20260504/권한메뉴/PLAN2.md)
- [이전 리뷰](../../../reviews/20260504/권한메뉴/REVIEW1.md) (해소 대상 블로커 2건)
- [PLAN1](../../../plan/20260504/권한메뉴/PLAN1.md) (1차 사이클 — 본 사이클이 본문 §DB 설계 변경 보강 대상)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. **검증 영역 백틱 사용 금지** (`check-task-unstage.sh` 훅 파싱 충돌).

### Phase 1: MenuRoleRepository 벌크 DELETE 전환 (블로커 #1 해소)

- [x] `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java` 수정 — `deleteByIdMenuId` 메서드를 Spring Data 파생 메서드에서 @Modifying @Query JPQL 벌크 DELETE 로 교체 (반환 void → int) + import 3건 추가 (Modifying·Query·Param) + Javadoc 갱신 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL + grep @Modifying MenuRoleRepository.java 매칭 + grep "DELETE FROM MenuRole" MenuRoleRepository.java 매칭

### Phase 2: PLAN1.md 본문 NOT NULL 표기 보강 (블로커 #2 해소)

- [x] `docs/plan/20260504/권한메뉴/PLAN1.md` 수정 — §DB 설계 변경 §menu_m DDL 코드 예시의 BaseEntity 4 컬럼 (rgstr_dtm·updt_dtm·rgstr_id·updt_id) 에 NOT NULL 표기 추가 → 검증: grep "rgstr_dtm.*TIMESTAMP NOT NULL" PLAN1.md 매칭 + grep "updt_dtm.*TIMESTAMP NOT NULL" PLAN1.md 매칭 + grep "rgstr_id.*VARCHAR(50) NOT NULL" PLAN1.md 매칭 + grep "updt_id.*VARCHAR(50) NOT NULL" PLAN1.md 매칭

### Phase 3: 빌드 + 회귀 테스트 검증

- [x] `./gradlew.bat :api:test --tests *MenuServiceTest` 실행 → 검증: PASS, changeMenuRoles 시나리오 회귀 0건 실패 (deleteByIdMenuId 호출 후 saveAll 정상 동작)
- [x] `./gradlew.bat :api:test` 실행 → 검증: PASS, api 모듈 전체 회귀 0건 실패
- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL, 전체 모듈 (common·api·scheduler) 회귀 0건 실패

## 산출물

- [결과](../../../results/20260504/권한메뉴/RESULT2.md) — Large 작업 RESULT 문서 (impl 후 자동 작성)
- [리뷰](../../../reviews/20260504/권한메뉴/REVIEW2.md) — Large 작업 REVIEW 문서 (RESULT 후 자동 작성)
