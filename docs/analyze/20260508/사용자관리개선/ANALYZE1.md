---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# 사용자관리개선 — 도메인 분석

## 작업 배경

기존 `UserController` (`api/src/main/java/com/mo/swtp/user/web/UserController.java`) 가 단건 조회 (`GET /api/users/{userId}`) 와 ADMIN 전용 등록·수정·비활성화·물리삭제만 제공한다. 두 가지 운영상 누락 기능을 추가한다:

1. **사용자 전체 목록 조회 부재** — ADMIN 이 등록된 사용자 현황을 확인하거나 비활성 사용자를 식별·복원할 수단이 없다.
2. **본인 정보 수정 부재** — 로그인한 사용자가 자신의 이름·비밀번호를 변경할 경로가 없다. 비밀번호는 ADMIN 의 `PUT /api/users/{userId}` 로도 갱신 불가 (`updateUser` 가 `userNm`·`userRole` 만 변경, `User.changePw` 는 별도 메서드).

**외부 산출물**: 없음 (요구사항 명세서·다이어그램 미첨부).

**사용자 사전 결정 사항** (요청 구체화 단계):

| 항목 | 결정 |
|------|------|
| 전체 목록 조회 권한 | ADMIN 전용 |
| 전체 목록 조회 옵션 | 단순 전체 조회 (`List<UserDto>` 반환, 페이징·검색 없음) |
| 전체 목록 조회 비활성 포함 | 활성 + 비활성 모두 — 정렬 `useYn DESC, userId ASC` |
| 본인 정보 수정 범위 | 이름 + 비밀번호 (현재 비밀번호 검증 후 새 비밀번호 변경, `userRole` 본인 변경 불가) |

## 회의록 (5인 회의 토픽 주도)

### 안건 1: `My*` 클래스 명명 + `/me` URL 사전 등록 + 신규 표준 용어 점검

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**:
    - **1-A**: `MyUserController` 는 `naming.md §Java 클래스 네이밍` 의 `{도메인명}Controller` 패턴으로 해석 시 도메인명 = `MyUser`. `MyMenuController` 선례가 동일 구조이므로 `My*` prefix 허용. **그러나 `*UpdateDto` suffix 는 컨벤션 위반** — `naming.md` §Java 클래스 네이밍 표는 생성/수정 요청을 `{도메인명}UpsertDto` 로 정의. `update` suffix 는 미등록 → `MyUserProfileUpsertDto` · `MyUserPasswordUpsertDto` 로 변경 권고
    - **1-B**: `/me` URL 키워드 표준 단어 사전 등록 **불요** — 표준 단어는 "DB 컬럼명·테이블명 조합 재료" (standard-words.md §사용 규칙). `/me` 는 URL 라우팅 전용, DB 컬럼 비사용. `MyMenuController` 선례에서도 `/me` 미등록
    - **1-C**: 신규 표준 용어 0건 — 신규 DB 컬럼 0건. `currentPw`·`newPw` 는 Java DTO 필드명(camelCase) 으로 DB 컬럼 비대상
- **결론**: `My*` prefix 허용. **DTO suffix 는 `UpsertDto` 재사용** (`MyUserProfileUpsertDto`·`MyUserPasswordUpsertDto`). `/me` 사전 등록 불요. 신규 사전 등록 0건.

### 안건 2: ErrorCode 신규 vs 통합 + Controller 분리 + RT 폐기 정책 + 정량 기준

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**:
    - **2-A**: `INVALID_CURRENT_PASSWORD(400)` **신규 도입 권고**. `INVALID_USER_PW` 는 grep 결과 어디서도 참조되지 않는 **기존 데드 코드** — 의미 충돌 자체는 없으나 재사용 비권장. `errorCode.name()` 이 frontend 명세 키이므로 맥락이 정확해야 함 (`exception-patterns.md §1`). `LOGIN_FAILED(401)` 과 HTTP 상태·의미 모두 분리
    - **2-B**: `MyUserController` **분리 권고** — `MyMenuController` 선례 정합 + SRP + Swagger Tag 분리 + 인가 경계 격리. ADMIN 전용 + 본인 전용 혼재 시 보안 경계 혼선 위험
    - **2-C**: **옵션 B (별도 보안 사이클 분리)** 권고 — `coding-discipline.md §2 단순성 우선` + `§3 정밀한 수정`. RT 폐기는 본 작업 요구사항에 명시되지 않은 보안 정책 확장. `UserPasswordChangedEvent` + 핸들러 + 이벤트 클래스 신규 도입은 작업 범위 상당 확장. PLAN 가정 섹션에 "RT 폐기 정책은 별도 사이클 위임" 명기 권고
    - **2-D**: 정량 기준 위반 없음 — Controller 메서드 15줄 이내, Service 메서드 25줄 이내 예상. Controller→Service→Repository 2단 추상화 (`coding-discipline.md §2.1` 3단 초과 미해당)
    - **발견 사항 (낮음)**: `INVALID_USER_PW` 미사용 데드 코드 보고만 (`coding-discipline.md §3.1` — 본 작업과 무관한 기존 데드 코드 직접 삭제 금지)
- **결론**: `INVALID_CURRENT_PASSWORD` 신규 도입. `MyUserController` 신규 분리. RT 폐기 정책은 안건 3-B 의견 차이로 가정/미해결 질문 등록 후 PLAN 단계 사용자 결정 위임. 정량 기준 사전 점검 통과. 기존 데드 코드 보고만.

### 안건 3: 도메인 4영역 점검 + RT 폐기 보안 정책 + 권한 격상 차단 + 비활성 정보 노출

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**:
    - **3-A**: 도메인 4영역 (알람·인터록·운전 모드·이력 기록) **모두 비해당**. 본 작업은 `user_m` CRUD + 인증 계층만 변경, SCADA·PLC·정수장 OT 영역과 분리. 사유 구체 명기 (아래 §도메인 룰 4영역 점검 표 참조)
    - **3-B**: **옵션 A (본 작업에 RT 폐기 포함)** 권고 — `ot-integration.md §5` 의 "장애 시 보수적 안전 기본값" 사상과 동일 맥락. `deactivateUser` 선례가 이미 이벤트 기반 RT 폐기 패턴을 확립했으므로, 비밀번호 변경에서만 생략하면 정책 불일치. NIST 800-63B 권고 (비밀번호 변경 후 모든 세션 무효화) 는 업계 표준
    - **3-C**: DTO 레이어 차단 (`MyUserProfileUpsertDto` 가 `userRole` 필드 미포함) **타당**. **단 ADMIN 마지막 1인 소실 위험 미해결 질문 등록 권고** — ADMIN 본인이 자신을 USER 로 격하 시 `userRepository.countByUserRoleAndUseYn(ADMIN, Y) >= 2` 검증 필요. (단, 본 작업은 `userRole` 변경을 본인 수정 범위에서 제외하므로 이 우려는 ADMIN 의 `PUT /api/users/{userId}` 자기 호출 시나리오 — 별도 문제. 본 작업 범위는 안전)
    - **3-D**: 비활성 사용자 포함 ADMIN 전용 조회 — 정보 노출 우려 없음. `userPw` 미포함 (`UserDto` 구조). PLAN 의 성공 기준에 "ADMIN 아닌 사용자가 호출 시 403 반환" 검증 명시 권고
- **이견 (안건 3-B vs 안건 2-C)**: Backend 엔지니어 (옵션 B) vs 도메인 전문가 (옵션 A) 의견 갈림.
- Round 2 (이견 종합): 두 의견 모두 합리적 — **단순성 우선** vs **보안 보수성**. 작업 범위 결정 사항이므로 사용자 결정 영역. 가정/미해결 질문으로 등록 후 PLAN 단계에서 사용자가 결정.
- **결론**: 4영역 모두 비해당 (사유 명기 충분). RT 폐기 정책 옵션 A·B 선택은 PLAN 단계로 위임 (가정/미해결 질문). 권한 격상 본 작업 범위 안전. 정보 노출 우려 없음.

### 안건 4: 정렬 인덱스 + 표준 데이터 도메인 + 트랜잭션·N+1 + 마이그레이션 SQL

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**:
    - **4-A**: `ORDER BY use_yn DESC, user_id ASC` 신규 인덱스 **미추가 적정**. `use_yn` 카디널리티 2 (`db/indexing-and-migration.md §3.4`), `user_id` PK 자체 B-Tree 존재. 정수장 운영 시스템 100명 미만 규모 → Seq Scan 비용이 인덱스 랜덤 I/O 보다 낮음. **향후 확장 권고**: `(use_yn, user_id ASC)` 복합 인덱스는 `use_yn = 'Y'` 등가 필터 쿼리가 함께 도입될 때만 실익 — 해당 쿼리 도입 시점에 PLAN 단계 명기 후 `CREATE INDEX CONCURRENTLY`
    - **4-B**: 표준 데이터 도메인 신규 등록 **0건 통과**. `user_pw VARCHAR(255)` 는 BCrypt 해시 전용 미지정 (선례 `menu_url VARCHAR(255) NULL` 동일)
    - **4-C**: PK 단건 조회 → JPA 더티 체킹 UPDATE 흐름 **N+1 우려 없음**. `User` 엔티티는 연관 엔티티 부재. **PLAN 단계 확인 필수**: `UserService` 클래스 레벨 `@Transactional(readOnly=true)` + 쓰기 메서드 (`changeMyProfile`·`changeMyPassword`) `@Transactional` 오버라이드 누락 시 더티 체킹 억제 위험
    - **4-D**: 스키마 변경 0건 → `db/migration/` 신규 SQL 파일 **0건 통과**
    - **발견 사항 (낮음)**: 기존 `idx_user_m_use_yn` 은 `api/src/main/resources/db/migration/user_m_use_yn_alignment.sql:29` 에서 이미 DROP 처리 확인. 현 인덱스는 PK B-Tree 단독
- **결론**: 신규 인덱스 0건. 표준 데이터 도메인 0건. 마이그레이션 SQL 0건. 쓰기 메서드 `@Transactional` 오버라이드는 PLAN 의 TASK 체크박스에 명시 (회귀 방지).

## 표준 사전 카탈로그

### 신규 표준 단어

없음

### 신규 표준 데이터 도메인

없음

### 신규 표준 용어

없음

## 신규 엔티티/DB 컬럼

없음 — 본 작업은 기존 `user_m` 테이블·컬럼을 그대로 사용한다. 신규 엔티티·DB 컬럼·인덱스·마이그레이션 SQL 모두 0건.

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 분류 | 해소책 (회의 결론) |
|---------|------|-----------------|
| `MyUserProfileUpdateDto` · `MyUserPasswordUpdateDto` 의 `*UpdateDto` suffix | DTO 컨벤션 위반 | `naming.md §Java 클래스 네이밍` 표의 `*UpsertDto` 재사용 → `MyUserProfileUpsertDto` · `MyUserPasswordUpsertDto` 로 변경 (안건 1-A 결론) |
| `INVALID_CURRENT_PASSWORD` vs 기존 `INVALID_USER_PW` | ErrorCode 의미 분리 | `INVALID_CURRENT_PASSWORD(400)` 신규 도입. 기존 `INVALID_USER_PW` 는 데드 코드이나 본 작업 무관 — 직접 삭제 금지 (`coding-discipline.md §3.1`) (안건 2-A 결론) |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

- 기존 `User` 엔티티 변경 없음. 기존 `User.changeInfo(userNm, userRole)` · `User.changePw(encodedPw)` 메서드 그대로 사용.
- 신규 엔티티 0건.

### DB 설계 변경 초안

- 신규 컬럼·인덱스·마이그레이션 SQL **0건**.

### 적용할 패턴

- `MyUserController` 신규 분리 (`/api/users/me` 프리픽스) — `MyMenuController` 선례 차용. `extractSubject(HttpServletRequest)` 헬퍼로 `JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE` 추출
- `UserController` 에 `GET /api/users` 추가 — `roleGuard.requireAdmin(request)` 보호 + `userService.findAllUsers()` + `List<UserDto>` 응답
- 신규 DTO 2건: `MyUserProfileUpsertDto` (`@NotBlank String userNm`), `MyUserPasswordUpsertDto` (`@NotBlank String currentPw` + `@NotBlank String newPw`)
- 신규 ErrorCode: `INVALID_CURRENT_PASSWORD(400)` (UserErrorCode 추가)
- Service 신규 메서드 3건: `findAllUsers()`, `changeMyProfile(userId, userNm)`, `changeMyPassword(userId, currentPw, newPw)`. **쓰기 메서드 (`changeMyProfile`·`changeMyPassword`) 는 메서드 레벨 `@Transactional` 오버라이드 의무** (안건 4-C)
- Repository 신규 메서드 1건: `findAllByOrderByUseYnDescUserIdAsc()` (Spring Data 메서드명 쿼리)

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 본인 비밀번호 변경 후 RT 폐기 정책 — 옵션 B 채택 (별도 보안 사이클로 분리) | 결정 | 사용자 결정 (2026-05-08, ANALYZE1 검토 단계). Backend 엔지니어 권고 (`coding-discipline.md §2 단순성 우선` + `§3 정밀한 수정`) 채택. 본 작업에서는 `UserPasswordChangedEvent` 도입하지 않음. 향후 별도 보안 강화 ANALYZE 사이클에서 명시 결정 |
| 전체 목록 조회 향후 사용자 규모 확장 시 인덱스 전략 | 가정 | 현 시점 `(use_yn, user_id)` 복합 인덱스 미적용. `use_yn = 'Y'` 등가 필터 쿼리 도입 시점에 PLAN 단계에서 `CREATE INDEX CONCURRENTLY` 추가 검토 (안건 4-A 권고) |
| ADMIN 마지막 1인 소실 방지 로직 | 미해결 | 본 작업은 본인 수정에서 `userRole` 변경을 차단하므로 본 시나리오 발생 안 함. 그러나 ADMIN 의 `PUT /api/users/{userId}` 자기 호출 (자신의 권한 격하) 시나리오는 별도 문제 — 본 작업 범위 외, 별도 사이클 검토 권고 (안건 3-C 발견) |

분류값: 가정 / 미해결 / 결정 (미해결은 PLAN 단계에서 결정으로 변환)

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `GET /api/users` ADMIN 토큰으로 호출 시 200 + `List<UserDto>` 응답 (활성+비활성 정렬 검증) | 신규 단위 테스트 GREEN — `UserService.findAllUsers` 가 useYn DESC 정렬 결과 반환 확인 |
| `GET /api/users` USER 토큰 호출 시 403 FORBIDDEN | 신규 통합/단위 테스트 GREEN — `RoleGuard.requireAdmin` 차단 검증 |
| 본인 이름 변경 시 `user_m.user_nm` 갱신 + `updt_dtm` 자동 갱신 | 신규 통합 테스트 GREEN — `changeMyProfile` 후 DB 행 검증 |
| 본인 비밀번호 변경 시 현재 비밀번호 불일치 → `INVALID_CURRENT_PASSWORD(400)` 예외 | 신규 단위 테스트 GREEN — `assertThatThrownBy` + `errorCode == INVALID_CURRENT_PASSWORD` 검증 |
| 본인 비밀번호 변경 성공 시 새 비밀번호로 `passwordEncoder.matches` 통과 | 신규 통합 테스트 GREEN — `changeMyPassword` 후 `User.userPw` 가 신규 BCrypt 해시 |
| `./gradlew.bat :api:test` 전체 GREEN (회귀 없음) | 명령 실행 후 BUILD SUCCESSFUL 확인 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `user_m` CRUD + 인증 계층만 변경. `alarm_h` 테이블·알람 임계값·전이 조건·복귀 조건 어디에도 무접촉. SCADA 계측값 비처리 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | `pump_interlock_p` 기반 설비 기동 선행조건과 무관. 사용자 계정 활성화·비밀번호 변경은 PLC 제어 명령 발행 경로 외 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p.ai_drvn_mod` (사용자 의도) · `ai_mode_cd` (시스템 상태) 변경 없음. SCADA 5분 초과 강제 전환 로직과 교차점 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason` 5종 기록 의무 해당 변경 없음. `pump_ctrl_h` 제어 로그 무관. 단, 가정 섹션의 RT 폐기 옵션 A 채택 시 `UserPasswordChangedEvent` 도입은 이력 테이블 신규 생성 없이 이벤트 핸들러로만 처리되므로 본 영역 영향 없음 |

> "비해당 단독 4건" 차단 해제 조건 충족: (1) 각 행에 구체 사유 명기 완료, (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족 (위 §신규 엔티티/DB 컬럼 참조).

## 룰 갱신 지시서

본 ANALYZE 의 신규 표준 단어 / 데이터 도메인 / 비즈니스 약어 / 표준 용어 등록 0건이므로 ROOT 어휘 사전 및 backend 표준 용어 사전 갱신 0건. naming.md / entity-patterns.md / api-patterns.md 변경 0건.

> 본 ANALYZE 는 룰 갱신 의무가 발생하지 않았다 (회의 결과 모든 명명·패턴이 기존 룰 정합으로 수렴). 따라서 룰 갱신 지시서 체크박스 0건. 승인 요건 (transitions.md §`/dev:analyze`) "모든 체크박스 - [x] 완료" 는 빈 섹션으로 자동 충족.

## 산출물

- [계획안](../../../plan/20260508/사용자관리개선/PLAN1.md) (PLAN1 작성 예정)
