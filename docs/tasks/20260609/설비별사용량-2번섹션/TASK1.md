---
status: completed
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 2번섹션 — 운영시설 목록 조회 API

## 관련 계획
- [계획안](../../../plan/20260609/설비별사용량-2번섹션/PLAN1.md)

## Phase

### Phase 1: common — FacilityType 그룹 파생 헬퍼 + 단위 테스트
- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java` — `typesOf(FacilityGroup group)` 정적 메서드 추가 (Arrays.stream(values()).filter(t -> t.group == group).toList()) + java.util.Arrays·java.util.List import 추가 → 검증: ./gradlew.bat :common:compileJava 후 BUILD SUCCESSFUL 출력 확인
- [x] `common/src/test/java/com/mo/swtp/facility/domain/enumtype/FacilityTypeTest.java` — typesOf(OPERATION)=8종(PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR)·typesOf(STORAGE)=3종(PWTF·DWT·RSV)·typesOf(NETWORK)=1종(POINT) 검증 단위 테스트 신규 작성 → 검증: ./gradlew.bat :common:test 실행 후 FacilityTypeTest GREEN

> 계획 외 변경: `FacilityTypeTest.java` 가 이미 존재(시설_도메인_확장 2026-06-08)하여 신규 생성 대신 typesOf 전용 검증 메서드 4건 추가(기존 테스트 보존, §3 정밀 수정).

### Phase 2: api — 필터 확장
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilitySearchDto.java` — facilityGroupCd 필드 추가 (FacilityGroup 타입, @Schema(description, implementation=FacilityGroup.class) 명시) + FacilityGroup import 추가 → 검증: 필드 추가 후 ./gradlew.bat :api:compileJava 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` — findFacilities() if(searchDto != null) 블록에 facilityGroupCd null 아님 시 where.and(f.facilityType.in(FacilityType.typesOf(...))) 분기 추가 (기존 facilityTypeCd·useYn·hasPump 분기 동형, 정렬 불변) → 검증: ./gradlew.bat :api:compileJava 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` — findAllFacilities @Operation(description) 에 facilityGroupCd 그룹 IN 필터 + 설비별사용량 2번섹션 운영시설 목록 용도 한 줄 보강 (엔드포인트·시그니처·@ApiResponses 불변) → 검증: 변경 후 ./gradlew.bat :api:compileJava 컴파일 성공

### Phase 3: 빌드 검증
- [x] `common` 모듈 전체 빌드 → 검증: ./gradlew.bat :common:build 실행 후 BUILD SUCCESSFUL 출력 확인
- [x] `api` 모듈 전체 빌드 → 검증: ./gradlew.bat :api:build 실행 후 BUILD SUCCESSFUL 출력 확인

## 산출물
- [결과](../../../results/20260609/설비별사용량-2번섹션/RESULT1.md)
