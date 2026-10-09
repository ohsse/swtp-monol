---
status: completed
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 3번섹션 — 전력 설비(계측기) 목록 조회 API 태스크

## 관련 계획
- [계획안](../../../plan/20260609/설비별사용량-3번섹션/PLAN1.md)

## Phase

### Phase 1: 응답 DTO 작성
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityPowerInstrumentDto.java` 신규 작성 — @Getter + private 기본생성자 + 정적팩토리 of(), 필드 instrumentId/instrumentNm/equipTypeCd(EquipType @Schema implementation)/facilityId/facilityNm/tags(@ArraySchema), 중첩 정적클래스 PowerTagDto(tagSrlNo/tagSeCd TagMeasurementType @Schema implementation) → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 2: 서비스 작성
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityPowerInstrumentService.java` 신규 작성 — @Service @RequiredArgsConstructor @Transactional(readOnly=true), 의존 FacilityRepository/InstrumentRepository/TagRepository, 상수 POWER_TAG_TYPES=EnumSet.of(PWI,PWQ)·MAX_DEPTH=10, public findPowerInstruments(facilityId) + private 헬퍼 findActiveFacilityOrThrow/collectSubtree/조립 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 3: 컨트롤러 엔드포인트 추가
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — FacilityPowerInstrumentService 주입 + GET /api/facility/{facilityId}/power-instruments 엔드포인트(@Operation·@ApiResponses 200/400/401/403/404/500, 404=FACILITY_NOT_FOUND) 추가 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 4: 단위 테스트 작성
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityPowerInstrumentServiceTest.java` 신규 작성 — @ExtendWith(MockitoExtension), 6 시나리오(미존재·비활성 루트 404 / 재귀 하위 BFS 포함 / 비전력 태그 제외·PWI-only·PWQ-only 포함 / PWI+PWQ 동시보유 1행 2태그 / 전력태그 0건 빈목록 / facilityNm 매핑 정확성) → 검증: ./gradlew.bat :api:test --tests *FacilityPowerInstrumentServiceTest PASS

### Phase 5: 빌드 검증
- [x] `./gradlew.bat :api:test` 실행 성공 확인
- [x] `./gradlew.bat clean build` 실행 성공 확인

## 산출물
- Medium 작업 — RESULT/REVIEW 면제. Phase 5 검증 통과 후 `/dev:commit 설비별사용량-3번섹션` 안내
