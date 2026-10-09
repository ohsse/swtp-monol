---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# 주파수 측정 유형 enum 정식 등록 — 작업 분해

## 관련 계획

- [계획안](../../../plan/20260520/주파수측정유형등록/PLAN1.md)

## Phase

### Phase 1: TagMeasurementType enum 추가 + Javadoc 갱신

- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 의 VOI 행 다음에 `FQI("주파수", "Hz")` 1행 추가 → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` Javadoc 도입 이력 항목 1행 추가 (주파수측정유형등록 ANALYZE1 2026-05-20 — FQI 신규 추가) → 검증: grep "주파수측정유형등록" TagMeasurementType.java 1 hit

### Phase 2: DDL 패치 신규 작성 + docs/ddl 동기화

- [x] `common/src/main/resources/db/migration/V8_1__tag_patch.sql` 신규 작성 (상단 주석에 작업 배경 + 역방향 UPDATE 롤백 SQL 명시, 본문: UPDATE tag_se_cd SPI→FQI + COMMENT ON COLUMN 8종 열거 갱신) → 검증: ls 결과 파일 존재 + grep "FQI" 매칭 ≥ 2 (UPDATE 1 + COMMENT 1)
- [x] `backend/docs/ddl/tag.sql` 하단에 V8_1 패치 본문 누적 (구분선 + UPDATE + COMMENT) — §5.3 양쪽 동시 갱신 의무 → 검증: tail -30 docs/ddl/tag.sql 마지막 블록에 V8_1 본문 포함

### Phase 3: 테스트 갱신

- [x] `common/src/test/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementTypeTest.java` 의 `hasSize(7)` 단언을 `hasSize(8)` 로 변경 (메서드명 동시 갱신 + 기존 단위/설명 검증 메서드에 FQI 행 추가 — 의도된 부수 변경) → 검증: ./gradlew.bat :common:test --tests TagMeasurementTypeTest PASS
- [x] `common/src/test/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementTypeTest.java` 에 FQI 검증 메서드 추가 (FQI_주파수_단위는_Hz다 — description Equal 주파수 + unit Equal Hz) → 검증: ./gradlew.bat :common:test --tests TagMeasurementTypeTest 신규 메서드 GREEN

### Phase 4: 양쪽 DB UPDATE 적용 (Claude 세션 직접 적용 — PLAN Q3 결정)

- [x] 로컬 DB tag_m 의 tag_se_cd='SPI' 4건을 'FQI' 로 UPDATE (MCP pg_execute_sql via local) → 검증: SELECT COUNT(*) FROM tag_m WHERE tag_se_cd='FQI' = 4 + WHERE tag_se_cd='SPI' = 0
- [x] 개발 DB tag_m 의 tag_se_cd='SPI' 4건을 'FQI' 로 UPDATE (MCP pg_execute_sql via connectionString) → 검증: 동일 검증 쿼리 결과 일치
- [x] 로컬 DB tag_m 의 tag_se_cd 컬럼 COMMENT 갱신 (8종 열거) → 검증: SELECT description FROM pg_description JOIN pg_attribute 조회 결과에 FQI 포함
- [x] 개발 DB tag_m 의 tag_se_cd 컬럼 COMMENT 갱신 (8종 열거) → 검증: 동일 검증 쿼리 결과 일치

### Phase 5: 빌드 전체 검증

- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인 (1m 45s, 22 tasks)
- [x] `./gradlew.bat :common:test` 실행 → 검증: TagMeasurementTypeTest 8종 GREEN + 기타 테스트 무영향
- [x] `./gradlew.bat :api:test` 실행 → 검증: Service 4종 (FacilityStateService·PumpSummaryService·FacilityPredictionService·DwtStateService) EnumSet 화이트리스트 패턴이 신규 enum 자동 미포함 무영향 — 기존 테스트 모두 PASS

## 산출물

- [결과](../../../results/20260520/주파수측정유형등록/RESULT1.md)
