---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입 — Fix Cycle 2 (블로커 2건 해소)

## 배경

- [이전 리뷰](../../../reviews/20260504/권한메뉴/REVIEW1.md) 블로커 2건 해소
- 직전 사이클 산출물: [PLAN1](PLAN1.md) (approved) · [TASK1](../../../tasks/20260504/권한메뉴/TASK1.md) (completed) · [RESULT1](../../../results/20260504/권한메뉴/RESULT1.md) (completed) · [REVIEW1](../../../reviews/20260504/권한메뉴/REVIEW1.md) (draft, 블로커 2건)

### Fix Cycle 진입 근거

`process/doc-harness/README.md` §수정 사이클 의 Fix Cycle 감지 알고리즘 결과:

1. REVIEW1.md `status = draft` ✓
2. 블로커 (높음) 2건 — "성능 (N+1 DELETE)" / "DDL vs PLAN 본문 표기 불일치"
3. 도메인 정합성 키워드 (용어·약어·중복 정의·네이밍 충돌·엔티티 통합) **미포함** → ANALYZE 재진입 스킵, **PLAN 직행**

### 해소 대상 블로커 요약

| # | 카테고리 | 위치 | 내용 |
|---|---------|------|------|
| 1 | 성능 (N+1 DELETE) | `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java:29` | Spring Data 파생 `deleteByIdMenuId` — `SimpleJpaRepository` 가 SELECT 후 `em.remove()` 반복 호출 (N+1). `db/query-tuning.md §N+1 방지 원칙` 위반. swtp 첫 N:M 매핑 표준 선례에서 잘못된 패턴 정착 위험 |
| 2 | 문서 정합 (DDL vs PLAN 본문 표기) | `docs/plan/20260504/권한메뉴/PLAN1.md:129-132` | DDL (V6_6 SQL) 은 BaseEntity 4 컬럼 NOT NULL 정합 (실구현 정합). PLAN1 본문 코드 예시는 NOT NULL 미표기 — 사용자 검토자가 PLAN 만 보고는 정확히 인지 불가. 코드 결함 아닌 문서 표기 누락 |

## 목적

REVIEW1 의 블로커 2건만 해소하여 REVIEW2 `status: approved` 전환을 가능하게 한다. REVIEW1 의 중간 4건 + 낮음 3건은 본 사이클 범위 외 — 별도 후속 사이클로 분리.

## 범위

### 포함 (in scope)

**코드 변경 1건:**
- `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java` — `deleteByIdMenuId(String)` 시그니처를 Spring Data 파생 메서드에서 `@Modifying @Query` JPQL 벌크 DELETE 로 교체

**문서 보강 1건:**
- `docs/plan/20260504/권한메뉴/PLAN1.md` — §DB 설계 변경 §`menu_m` DDL 코드 예시의 BaseEntity 4 컬럼 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) 에 `NOT NULL` 표기 추가

**테스트:**
- 기존 `MenuServiceTest`·`MenuQueryServiceTest`·`LoginFacadeServiceTest`·`MenuRoleTest` 회귀 PASS 확인 — 본 변경은 시그니처 미변화 (호출처 미사용 반환값) 이라 회귀 영향 0

### 제외 (out of scope)

REVIEW1 의 중간·낮음 발견 사항 7건은 본 사이클에서 미처리. 별도 후속 사이클로 분리:

| 심각도 | 항목 | 분리 사유 |
|--------|------|---------|
| 중간 | createMenu 응답 DTO 누락 (현재 Void) | UX 결정 필요 — `MenuTreeDto.from()` vs 별도 `MenuDto` vs Void 유지 |
| 중간 | `MenuUpsertDto.useYn` 필드 누락 | 재활성화 경로 설계 결정 필요 — UPSERT vs 별도 엔드포인트 |
| 중간 | `MyMenuController.UserRole.valueOf` IllegalArgumentException 처리 | 보안 흐름 검토 필요 — Auth 도메인 ErrorCode 분류 |
| 중간 | `LoginFacadeService` 트랜잭션 경계 누락 | Service 트랜잭션 정책 — `api/CLAUDE.md` Service 규칙 정합 검토 |
| 낮음 | `LoginFacadeService` `@Slf4j` 누락 | 관례 정리 |
| 낮음 | `MenuService.computeDepth` 의도된 N+1 Javadoc 주석 | 문서 정리 |
| 낮음 | 테스트 픽스처 중복 (Reflection 기반 ID 강제 주입) | `MenuTestFixture` 추출 — 별도 리팩토링 |

> 위 7건은 본 PLAN2 가 블로커 해소 단순성 우선 (`coding-discipline.md §2`) 원칙으로 분리. REVIEW1 의 권고 ("중간 4건은 Fix Cycle 사이클에서 함께 해소 권고") 는 강제가 아니므로 본 PLAN 의 단순성 원칙이 우선.

## 도메인 모델

신규 엔티티·테이블·필드 변경 **없음**. 본 사이클은 Repository 메서드 시그니처 변경 1건 + PLAN 문서 보강 1건이며 도메인 모델 변경 없음.

## DB 설계 변경

스키마 변경 **없음**. V6_6 DDL 은 이미 BaseEntity 4 컬럼 NOT NULL 정합 (REVIEW1 §통과 항목 확인 완료). 본 사이클의 블로커 #2 는 PLAN1 본문 표기 누락이며 DDL 자체는 변경 불요.

## 구현 방향

### 1. `MenuRoleRepository.deleteByIdMenuId` 벌크 DELETE 전환

**현재 코드** (블로커 #1):

```java
package com.mo.swtp.menu.repository;

import com.mo.swtp.menu.domain.MenuRole;
import com.mo.swtp.menu.domain.MenuRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRoleRepository extends JpaRepository<MenuRole, MenuRoleId> {

    List<MenuRole> findByIdMenuId(String menuId);

    void deleteByIdMenuId(String menuId);   // Spring Data 파생 — SELECT 후 em.remove() 반복 (N+1 DELETE)
}
```

**변경 후 코드:**

```java
package com.mo.swtp.menu.repository;

import com.mo.swtp.menu.domain.MenuRole;
import com.mo.swtp.menu.domain.MenuRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuRoleRepository extends JpaRepository<MenuRole, MenuRoleId> {

    List<MenuRole> findByIdMenuId(String menuId);

    /**
     * 특정 메뉴의 모든 권한 매핑을 단일 DELETE 로 일괄 삭제한다 (권한 갱신 1단계).
     *
     * <p>Spring Data 파생 메서드 ({@code SimpleJpaRepository}) 의 SELECT-then-REMOVE
     * N+1 DELETE 패턴을 회피하기 위해 {@code @Modifying @Query} 벌크 DELETE 로 정의한다.
     * 본 메서드는 {@code MenuService.changeMenuRoles} 의 일괄 삭제 후 {@code saveAll}
     * 배치 INSERT 패턴 1 단계에서 사용된다.</p>
     *
     * @param menuId 메뉴 ID
     * @return 삭제된 매핑 행 수
     */
    @Modifying
    @Query("DELETE FROM MenuRole mr WHERE mr.id.menuId = :menuId")
    int deleteByIdMenuId(@Param("menuId") String menuId);
}
```

**변경 영향 분석:**
- 반환 타입 `void` → `int` (영향 행 수 노출, 호출처 미사용이라 시그니처 호환 — 변수 할당 없음)
- 호출처 `MenuService.changeMenuRoles:98` 는 `menuRoleRepository.deleteByIdMenuId(menuId);` 형태 (반환값 미사용) — 컴파일·동작 영향 0
- `@Modifying` 의 `clearAutomatically`·`flushAutomatically` 기본값 (false) 유지 — 호출 후 즉시 `saveAll` INSERT 가 영속성 컨텍스트 자동 flush 보장
- `entity-patterns.md §N:M 매핑 엔티티 패턴` 의 첫 N:M 표준 선례 정합 — 향후 `user_facility_r`·`role_instrument_r` 등이 본 패턴을 모방

### 2. PLAN1 본문 BaseEntity 4 컬럼 NOT NULL 표기 추가

**현재 PLAN1 본문** (블로커 #2 — `docs/plan/20260504/권한메뉴/PLAN1.md` L122~134):

```sql
CREATE TABLE menu_m (
    menu_id        VARCHAR(36) NOT NULL,
    menu_nm        VARCHAR(100) NOT NULL,
    menu_url       VARCHAR(255),
    menu_desc      TEXT,
    disp_ord       INTEGER NOT NULL,
    parent_menu_id VARCHAR(36),
    use_yn         VARCHAR(1) NOT NULL,
    rgstr_dtm      TIMESTAMP,                ← NOT NULL 미표기
    updt_dtm       TIMESTAMP,                ← NOT NULL 미표기
    rgstr_id       VARCHAR(50),              ← NOT NULL 미표기
    updt_id        VARCHAR(50),              ← NOT NULL 미표기
    ...
);
```

**변경 후 PLAN1 본문:**

```sql
CREATE TABLE menu_m (
    menu_id        VARCHAR(36) NOT NULL,
    menu_nm        VARCHAR(100) NOT NULL,
    menu_url       VARCHAR(255),
    menu_desc      TEXT,
    disp_ord       INTEGER NOT NULL,
    parent_menu_id VARCHAR(36),
    use_yn         VARCHAR(1) NOT NULL,
    rgstr_dtm      TIMESTAMP NOT NULL,
    updt_dtm       TIMESTAMP NOT NULL,
    rgstr_id       VARCHAR(50) NOT NULL,
    updt_id        VARCHAR(50) NOT NULL,
    ...
);
```

**변경 영향 분석:**
- DDL 자체 (V6_6 SQL) 는 이미 NOT NULL 정합 — 본 변경 불요
- PLAN1 본문 표기와 V6_6 SQL 의 정합 확보 — 검토자 혼선 방지
- PLAN1 의 `status: approved` 는 유지 (본 변경은 사후 보강 표기 정정이며 정책·결정 사항 변경 없음)
- 사후 보강 표기에 대한 이력은 본 PLAN2 의 §변경 사항에 명시 (RESULT2 단계)

## 성공 기준 (검증 가능 형태)

### 빌드 / 컴파일

- [ ] `./gradlew.bat :api:compileJava` BUILD SUCCESSFUL — `MenuRoleRepository` 시그니처 변경 후 컴파일 성공
- [ ] `./gradlew.bat clean build` BUILD SUCCESSFUL — 전체 모듈 빌드 + 회귀 테스트 통과

### 테스트 회귀

- [ ] `./gradlew.bat :api:test --tests *MenuServiceTest` PASS — `changeMenuRoles` 시나리오 (deleteByIdMenuId 호출) 회귀 PASS
- [ ] `./gradlew.bat :api:test` PASS — api 모듈 전체 회귀 0건 실패
- [ ] `./gradlew.bat test` PASS — 전체 모듈 (common·api·scheduler) 회귀 0건 실패

### 코드 검증

- [ ] `MenuRoleRepository.java` 의 `deleteByIdMenuId` 가 `@Modifying`·`@Query` 어노테이션 보유 → 검증: grep "@Modifying" MenuRoleRepository.java 매칭 + grep "DELETE FROM MenuRole" 매칭
- [ ] `MenuRoleRepository.java` import 4건 추가 — `Modifying`·`Query`·`Param`·(`JpaRepository` 기존) → 검증: grep "import org.springframework.data.jpa.repository.Modifying" 매칭

### 문서 검증

- [ ] `PLAN1.md` L129~132 모두 `NOT NULL` 표기 → 검증: grep "rgstr_dtm.*TIMESTAMP NOT NULL" PLAN1.md 매칭 + 4행 동일

### REVIEW2 단계 게이트

- [ ] REVIEW2.md `## 발견 사항` 표 — 심각도 "높음" 0건 → REVIEW1 의 블로커 2건 해소 확인
- [ ] REVIEW2.md `status: approved` 전환 → `/dev:commit 권한메뉴` 진입 가능

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| Fix Cycle 범위 — REVIEW1 중간 4건 포함 여부 | **결정** | 미포함. 블로커 2건만 본 사이클에서 해소 (단순성 우선). 중간·낮음 7건은 별도 후속 사이클 |
| `@Modifying` 의 `clearAutomatically`/`flushAutomatically` 옵션 | **결정** | 기본값 false 유지. 호출 후 즉시 `saveAll` INSERT 가 영속성 컨텍스트 자동 flush 보장 |
| `deleteByIdMenuId` 반환 타입 변경 (void → int) | **결정** | int 채택. 호출처 미사용이라 시그니처 호환. 영향 행 수 노출은 향후 디버깅·모니터링에 유용 |
| PLAN1 본문 보강의 위치 (PLAN1 직접 수정 vs RESULT2 §계획 외 변경 명시) | **결정** | PLAN1 직접 수정 채택. 표기 정합성이 더 명료. PLAN1 의 `status: approved` 는 유지 (정책·결정 변경 없음) |
| `entity-patterns.md` §N:M 매핑 엔티티 패턴 의 매핑 갱신 정책 본문에 벌크 DELETE 권고 추가 여부 | **미해결** | 본 사이클에서 룰 갱신 지시서로 등록 후 사용자 결정. 향후 N:M 패턴 SSOT 보강 가치 있음 |

## 룰 갱신 지시서

본 사이클은 신규 어휘·도메인 모델·DB 설계 변경 없음. 단 REVIEW1 §개선 제안 #1 ("entity-patterns.md §N:M 매핑 엔티티 패턴 §매핑 갱신 (정책 본문) 에 벌크 DELETE 권고 한 줄 추가도 함께 검토") 의 룰 본문 보강 여부를 사용자 결정에 맡긴다.

- [ ] `.claude/rules/entity-patterns.md` — §N:M 매핑 엔티티 패턴 의 핵심 규칙 표 또는 코드 예시에 "Spring Data 파생 `deleteByXxx` 메서드 대신 `@Modifying @Query` 벌크 DELETE 권고" 한 줄 추가 (선택 — 사용자 결정 후 진행)

> 위 항목이 미체크 상태로 본 PLAN2 가 approved 되어도 무방. TASK2 단계에서 사용자 결정 후 처리하거나, 별도 후속 사이클에서 다룬다 (룰 본문 변경은 ROOT 자산이 아니므로 Medium/Large 갱신 절차 외).

## 제외 사항

- REVIEW1 중간 4건 + 낮음 3건 — 별도 후속 사이클
- 메뉴 도메인 신규 기능 추가 — 본 사이클은 블로커 해소 한정
- 메뉴 트리 캐싱·SSE/WebSocket·`user_role_c` 코드 마스터 등 RESULT1 §후속 작업 권고 — 본 사이클 범위 외

## 예상 산출물

- [태스크](../../../tasks/20260504/권한메뉴/TASK2.md) — 단일 파일 (체크박스 < 10건 예상, 분할 기준 미달)
- 코드 변경 1건 (`MenuRoleRepository.java` — 메서드 시그니처 + Javadoc + import 4행)
- 문서 보강 1건 (`PLAN1.md` BaseEntity 4 컬럼 NOT NULL 표기)
- 룰 갱신 0~1건 (선택 사항, `entity-patterns.md` §N:M 매핑 엔티티 패턴 본문 보강 — 사용자 결정 시)

---

## 부록: 도메인/DB 검토 게이트 결과

본 PLAN2 는 §도메인 모델 변경 없음 + §DB 설계 변경 없음 → **검토 게이트 스킵** (PLAN 단계 검토 게이트 조건 미해당). 메서드 시그니처 변경 1건은 일반 코드 패턴 영역이며 도메인·DB 영향 없음.
