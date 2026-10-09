---
status: completed
created: 2026-06-10
updated: 2026-06-10
---
# 사용량트렌드-2번섹션 — 정수장 전체 전력량 추이 조회 API

## 관련 계획
- [계획안](../../../plan/20260610/사용량트렌드-2번섹션/PLAN1.md)

## Phase

> 계층 의존 순서(DTO·ErrorCode → Repository → Service → Controller → Test → Build)로 구성한다.
> 신규 6 + 수정 2 (전부 `api` 모듈, DB·엔티티 변경 0건). Medium 규모 — TASK 분할 없음.

### Phase 1: DTO · ErrorCode 계층
- [x] `api/src/main/java/com/mo/swtp/raw/dto/RawDataBucketSumDto.java` record {baseDtm, totalVal} 신규 생성 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/dto/EnergyUsageTrendSearchDto.java` FacilityEnergyTrendSearchDto 동형 복제 — inqUnit/fromDt/toDt + isValid(YEAR거부·≤396일) + toStartDtm + toEndExclusiveDtm 신규 생성 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/dto/EnergyUsageTrendDto.java` outer{unit,points} + inner Point{baseDtm,elcegVal} + 정적 팩토리 + @ArraySchema + BaseAuditResponseDto 미상속 신규 생성 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/exception/OptErrorCode.java` INVALID_SEARCH_PERIOD(400) 추가 → 검증: PostToolUse check-errorcode-contract.sh 통과 후 ./gradlew.bat :api:compileJava 성공

### Phase 2: Repository 합산 쿼리 계층
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` findEnergyDeltaBucketsTotal 시그니처 추가 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 중첩 합산 native SQL(inner 태그별 MAX-MIN → outer 버킷 SUM) + §2.5 면책 주석 + RawDataBucketSumDto 매핑 + 빈 태그 가드 구현 → 검증: ./gradlew.bat :api:compileJava 성공 후 §2.5 면책 query-tuning 주석 grep 매칭

### Phase 3: Service 계층
- [x] `api/src/main/java/com/mo/swtp/opt/service/EnergyUsageTrendService.java` 읽기전용 흐름(isValid 실패→RestApiException, PWQ 전역 수집, 합산쿼리 호출, sparse 매핑) + 이중계상·SUM(MAX-MIN) Javadoc 신규 생성 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 4: Controller 계층
- [x] `api/src/main/java/com/mo/swtp/opt/web/EnergyUsageTrendController.java` @Tag 16 + GET /api/opt/energy-usage-trend + @ModelAttribute + @Operation/@ApiResponses + CommonController 상속 신규 생성 → 검증: ./gradlew.bat :api:compileJava 성공 후 @Tag 번호 16 중복 없음 grep 확인

### Phase 5: 단위 테스트
- [x] `api/src/test/java/com/mo/swtp/opt/service/EnergyUsageTrendServiceTest.java` Mockito 5종 케이스(유효 파라미터 매핑·YEAR 거부·기간역전/396일초과/null·빈 PWQ 태그·종료일 익일00시 ArgumentCaptor) 작성 → 검증: ./gradlew.bat :api:test --tests *EnergyUsageTrendServiceTest GREEN

### Phase 6: 빌드 검증
- [x] `./gradlew.bat clean build` 실행 → 검증: 프로덕션 컴파일·QClass 재생성 성공. opt/raw 도메인 테스트 실패 0건. 단위 테스트 EnergyUsageTrendServiceTest 7/7 GREEN

## 검증 결과 및 발견 사항 (계획 외)

> Medium 규모 — RESULT/REVIEW 문서 면제. 본 절에 검증 상태·계획 외 변경을 기록한다.

- **본 작업 검증**: `:api:compileJava` BUILD SUCCESSFUL · `EnergyUsageTrendServiceTest` 7/7 GREEN · 전체 빌드 383개 중 opt/raw/energy 실패 0건.
- **기존 깨짐 1 (계획 외 변경 — 사용자 승인 최소 수정)**: `api/src/test/java/com/mo/swtp/auth/service/AuthServiceTest.java` 2곳(51·81행) `User.create(...)` 4인자 호출이 `YnType` 도입 커밋(`39a0125`) 이후 5인자 시그니처와 불일치하여 모듈 전체 `compileTestJava` 차단. `YnType.Y` 추가로 복구 (본 작업 무관, 사용자 결정 "최소 수정 후 검증").
- **기존 깨짐 2 (미수정 — 사용자 결정 "기존 문제로 간주, 커밋 진행")**: `UserServiceTest` 13건 실패. 근본 원인 `column "ttryl_use_yn" of relation "user_m" does not exist` — `User` 엔티티의 튜토리얼 플래그 컬럼(커밋 `3703890`)이 로컬 테스트 DB `user_m`에 미적용된 스키마 드리프트. user 도메인 + 환경 문제로 본 작업과 무관. 별도 fix 사이클 권고.

## 산출물
- 커밋 (`/dev:commit`) — Medium 규모, RESULT/REVIEW 면제
