---
status: completed
created: 2026-06-11
updated: 2026-06-11
---
# 사용량트렌드-3번섹션 — 월별 최대 순시전력 피크 조회 API 구현

## 관련 계획
- [계획안](../../../plan/20260611/사용량트렌드-3번섹션/PLAN1.md)

## Phase

> 검증 형식: `- [ ] {파일경로} 작업 → 검증: {확인 명령}`. 검증 영역 백틱 미사용 (훅 파싱 호환).

### Phase 1: raw 계층 — projection record + 월별 피크 native 쿼리

- [x] `api/src/main/java/com/mo/swtp/raw/dto/RawDataBucketPeakDto.java` 신규 record 생성 (`baseDtm`, `peakVal`) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 에 findMonthlyMaxMinuteSumElpwr 시그니처 추가 → 검증: 인터페이스 메서드 선언 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 에 native 중첩집계 쿼리 구현 + §2.5 면책 주석 + 빈 태그 early-return + 매퍼 → 검증: 분별 SUM 후 월별 MAX 중첩 구조 + GOOD only + COALESCE 적용 확인, ./gradlew.bat :api:compileJava 통과

### Phase 2: opt 계층 — 응답 DTO / Service / Controller

- [x] `api/src/main/java/com/mo/swtp/opt/dto/MaxPeakStatusDto.java` 신규 생성 (unit + points, 중첩 MaxPeakStatusPoint, @ArraySchema, 정적 팩토리 of) → 검증: ./gradlew.bat :api:compileJava 통과, peakVal nullable·@JsonFormat 초단위 확인
- [x] `api/src/main/java/com/mo/swtp/opt/service/MaxPeakStatusService.java` 신규 생성 (Clock 주입, 6개월 윈도우, PWI 태그 수집, 6슬롯 null 병합) → 검증: buildMonthKeys·mergeToSlots private 분해로 메서드 50줄 이내, PWI 태그 0건 시 쿼리 미호출 분기 존재
- [x] `api/src/main/java/com/mo/swtp/opt/web/MaxPeakStatusController.java` 신규 생성 (CommonController 상속, GET /api/opt/max-peak-status, 파라미터 없음, @Tag 16 사용량 트렌드) → 검증: ResponseEntity CommonResponseDto MaxPeakStatusDto 시그니처 + @ApiResponses 200·401·500

### Phase 3: api config — Clock 빈

- [x] `api/src/main/java/com/mo/swtp/api/config/ClockConfig.java` 신규 생성 (@Configuration + Clock systemDefaultZone 빈) → 검증: ./gradlew.bat :api:compileJava 통과

### Phase 4: 단위 테스트

- [x] `api/src/test/java/com/mo/swtp/opt/service/MaxPeakStatusServiceTest.java` 신규 생성 (Mockito + 고정 Clock, PLAN 성공 기준 5종) → 검증: ./gradlew.bat :api:test 신규 테스트 GREEN

### Phase 5: 빌드 검증

- [x] `api/src/test/java/com/mo/swtp/opt/service/MaxPeakStatusServiceTest.java` 전체 테스트 실행 → 검증: ./gradlew.bat :api:test --tests MaxPeakStatusServiceTest BUILD SUCCESSFUL (전체 :api:test 의 UserServiceTest 13건 실패는 ttryl_use_yn 컬럼 사전 스키마 드리프트 — 본 작업 무관)

## 산출물
- [결과](../../../results/20260611/사용량트렌드-3번섹션/RESULT1.md)
