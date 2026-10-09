---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# tag_frequency_추가 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260520/tag_frequency_추가/PLAN1.md)

## Phase

### Phase 1: TagMeasurementType enum 값 추가 (common 모듈)

- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 의 VOI 다음에 `FQI("주파수", "Hz")` 8번째 enum 값 추가 → 검증: grep FQI 파일 매칭 + Javadoc 주석 "주파수 (Frequency Indicator — 가변속 인버터 펌프 운전 주파수, Hz)" 동행
- [x] 동일 파일 클래스 Javadoc 도입 이력 절에 "tag_frequency_추가 ANALYZE1 (2026-05-20) — FQI 추가, 결측 대체값 정책은 ot-integration.md §3 참조" 한 줄 추가 → 검증: grep tag_frequency_추가 파일 매칭

### Phase 2: TagErrorCode 신규 코드 추가 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/tag/exception/TagErrorCode.java` 에 `FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP(400)` enum 값 추가 → 검증: grep FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP 파일 매칭 + httpStatus 400 동행
- [x] 동일 파일 신규 enum 값 Javadoc 으로 "FQI 측정 유형은 PumpDriveType.INVERTER_DRIVE 펌프 계측기에만 허용. RATED_DRIVE 펌프 + 비-Pump 계측기 모두 차단 (ot-integration.md §5 ⚠️ 절대 금지 직결)" 명시 → 검증: 해당 enum 값 위 Javadoc 4줄 이상 grep 매칭

### Phase 3: TagService 검증 로직 추가 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/tag/service/TagService.java` 에 `private static void validateFqiTagAllowance(Instrument instrument, TagMeasurementType tagSeCd)` 보조 메서드 추가 → 검증: 메서드 시그니처 grep 매칭 + Java 21 pattern matching (`instanceof Pump pump && pump.getDriveType() == INVERTER_DRIVE`) 사용
- [x] 동일 파일 `registerTag` 메서드의 `Tag.create(...)` 호출 직전에 `validateFqiTagAllowance(instrument, dto.getTagSeCd())` 호출 추가 → 검증: registerTag 본문에 검증 호출 grep
- [x] 동일 파일 `modifyTag` 메서드의 `tag.changeInfo(...)` 호출 직전에 `validateFqiTagAllowance(tag.getInstrument(), dto.getTagSeCd())` 호출 추가 → 검증: modifyTag 본문에 검증 호출 grep + 기존 tag.getInstrument() 호출 정합
- [x] `validateFqiTagAllowance` Javadoc 으로 "FQI 측정 유형은 INVERTER_DRIVE 펌프에만 허용. PLAN1 §구현 방향 Phase 3" 명시 → 검증: 메서드 Javadoc grep 매칭

### Phase 4: 테스트 갱신 (common + api)

- [x] `common/src/test/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementTypeTest.java` 의 `assertThat(TagMeasurementType.values()).hasSize(7)` 을 `hasSize(8)` 로 변경 → 검증: ./gradlew.bat :common:test --tests TagMeasurementTypeTest PASS
- [x] 동일 테스트 파일 `측정_유형별_단위가_고정값으로_매핑된다` 메서드에 `assertThat(TagMeasurementType.FQI.getUnit()).isEqualTo("Hz")` assertion 추가 → 검증: 동일 테스트 PASS
- [x] 동일 테스트 파일 `측정_유형별_한글_설명이_매핑된다` 메서드에 `assertThat(TagMeasurementType.FQI.getDescription()).isEqualTo("주파수")` assertion 추가 → 검증: 동일 테스트 PASS
- [x] `api/src/test/java/com/mo/swtp/tag/service/TagServiceTest.java` 에 `RATED_DRIVE_펌프에_FQI_태그_등록_시_예외가_발생한다` 단위 테스트 추가 → 검증: ./gradlew.bat :api:test --tests TagServiceTest 신규 테스트 PASS, FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP ErrorCode 발생 확인
- [x] 동일 테스트 파일에 `비_Pump_계측기에_FQI_태그_등록_시_예외가_발생한다` 단위 테스트 추가 (예: Valve 자식 instrument 사용) → 검증: 동일 ErrorCode 발생 확인 PASS
- [x] 동일 테스트 파일에 `INVERTER_DRIVE_펌프에_FQI_태그_등록은_허용된다` positive 단위 테스트 추가 → 검증: tagRepository.save 호출 verify PASS
- [x] 동일 테스트 파일에 `FQI_외_측정유형은_RATED_DRIVE_펌프에도_허용된다` 회귀 방지 단위 테스트 추가 (예: PWI 등록) → 검증: 등록 성공 PASS
- [x] 동일 테스트 파일에 `modifyTag_가_FQI_변경_시_RATED_DRIVE_펌프면_차단한다` 단위 테스트 추가 → 검증: ErrorCode 발생 확인 PASS

### Phase 5: docs/ddl/tag.sql SSOT 사본 갱신

- [x] `docs/ddl/tag.sql` 의 `tag_m.tag_se_cd` COMMENT ON COLUMN 문에서 enum 코드값 목록 "FRI/PRI/LEI/PWI/RMS/OPS/VOI" 를 "FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI" 로 갱신 → 검증: grep FQI docs/ddl/tag.sql 매칭 + 동일 COMMENT 행에 8종 enum 코드값 동행

### Phase 6: 통합 빌드·테스트 검증

- [x] `./gradlew.bat :common:test --tests TagMeasurementTypeTest` 실행 성공 확인 → 검증: 4 테스트 (3 기존 + 1 boundary) GREEN
- [x] `./gradlew.bat :api:test --tests TagServiceTest` 실행 성공 확인 → 검증: 기존 시나리오 + 신규 5건 GREEN
- [x] `./gradlew.bat clean build` 전체 빌드 성공 확인 → 검증: BUILD SUCCESSFUL 출력 확인 + common·api·scheduler 3 모듈 모두 PASS

## 산출물
- [결과](../../../results/20260520/tag_frequency_추가/RESULT1.md) (예정)
