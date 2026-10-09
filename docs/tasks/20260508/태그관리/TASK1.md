---
status: completed
created: 2026-05-08
updated: 2026-05-11
---
# 태그관리 — CRUD API 구현 작업 분해

## 관련 계획
- [계획안](../../../plan/20260508/태그관리/PLAN1.md)

## Phase

### Phase 1: DDL 마이그레이션 (V9_1 신규)

- [x] `common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql` 신규 작성 (3단계 use_yn ADD + unit_cd DROP + COMMENT) → 검증: 파일 생성 + check-ddl-column-comment.sh 훅 통과 (ALTER TABLE 만 포함, CREATE TABLE 인식 대상 외)
- [x] (운영 적용 시 주의) DROP COLUMN ACCESS EXCLUSIVE 락 영향 — 운영 환경 적용 시 별도 유지보수 창 권고 → 검증: TASK 내 본 항목 명시 확인 (운영 데이터 0건 + 코드 도입 전 환경 전제)

### Phase 2: 도메인 계층 — TagMeasurementType enum 확장

- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 수정 — description+unit String 두 필드 추가 (생성자 매개변수, @Getter @RequiredArgsConstructor) + 기존 5개 값 매핑 (FRI=m³/h·PRI=kgf/cm²·LEI=m·PWI=kW·RMS=빈문자열) + OPS("가동상태", "")·VOI("개도율", "%") 신규 추가 → 검증: ./gradlew.bat :common:build BUILD SUCCESSFUL
- [x] `common/src/test/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementTypeTest.java` 신규 — 7개 enum 값별 getUnit() / getDescription() 단언 + values().length == 7 단언 → 검증: ./gradlew.bat :common:test PASS

### Phase 3: 도메인 계층 — Tag 엔티티 변경

- [x] `common/src/main/java/com/mo/swtp/tag/domain/Tag.java` 수정 (1) useYn 필드 추가 (@Enumerated(EnumType.STRING) @Column(name="use_yn", nullable=false, length=1) private YnType useYn) → 검증: ./gradlew.bat :common:build PASS + Q파일 재생성에 useYn 매핑 포함 확인
- [x] `common/src/main/java/com/mo/swtp/tag/domain/Tag.java` 수정 (2) unitCd 필드 제거 → 검증: grep "unitCd" Tag.java 매칭 0건
- [x] `common/src/main/java/com/mo/swtp/tag/domain/Tag.java` 수정 (3) Tag.create(...) 정적 팩토리 — unitCd 인자 제거 + useYn = YnType.Y 명시 할당 → 검증: 시그니처 (tagSrlNo, instrument, tagSeCd, tagDesc, ioCd) 5인자 확인
- [x] `common/src/main/java/com/mo/swtp/tag/domain/Tag.java` 수정 (4) Tag.changeInfo(...) — unitCd 인자 제거, instrument 인자 미포함 유지 → 검증: 시그니처 (tagSeCd, tagDesc, ioCd) 3인자 확인
- [x] `common/src/main/java/com/mo/swtp/tag/domain/Tag.java` 수정 (5) Tag.deactivate() 메서드 추가 (this.useYn = YnType.N) → 검증: 메서드 존재 확인

### Phase 4: 영속 계층 — TagRepository 메서드 추가

- [x] `api/src/main/java/com/mo/swtp/tag/repository/TagRepository.java` 수정 — findAllByOrderByUseYnDescTagSrlNoAsc() 메서드 추가 (Spring Data JPA 메서드명 규약 — User 선례 정합) → 검증: 컴파일 통과 + ./gradlew.bat :api:build PASS

### Phase 5: 서비스 계층 — TagService + TagErrorCode

- [x] `api/src/main/java/com/mo/swtp/tag/exception/TagErrorCode.java` 신규 — implements ErrorCode, httpStatus(int) 만 보유, 항목 3건 (TAG_NOT_FOUND(404), DUPLICATE_TAG_SRL_NO(409), INVALID_INSTRUMENT_ID(400)) → 검증: check-errorcode-contract.sh 훅 통과 (String 필드 0건, getMessage() 미정의)
- [x] `api/src/main/java/com/mo/swtp/tag/service/TagService.java` 신규 — @Service @RequiredArgsConstructor @Transactional(readOnly=true). 의존성: TagRepository + InstrumentRepository → 검증: 컴파일 통과
- [x] TagService.registerTag(TagUpsertDto dto) — @Transactional. (1) tagRepository.existsById 중복 검증 → DUPLICATE_TAG_SRL_NO. (2) instrumentRepository.findById FK 검증 → INVALID_INSTRUMENT_ID. (3) Tag.create 호출 → tagRepository.save → 검증: 단위 테스트 GREEN
- [x] TagService.findAllTags() — Repository.findAllByOrderByUseYnDescTagSrlNoAsc() 결과를 TagDto.from 매핑 List 반환 → 검증: 단위 테스트 GREEN
- [x] TagService.findTag(String tagSrlNo) — Repository.findById empty 시 TAG_NOT_FOUND. 활성·비활성 모두 반환 (use_yn 필터 미적용) → 검증: 단위 테스트 GREEN + 주석에 도메인 검토 권고 1 반영 사유 명기
- [x] TagService.modifyTag(String tagSrlNo, TagUpsertDto dto) — @Transactional. Repository.findById empty 시 TAG_NOT_FOUND. instrument_id 변경은 무시 (안건 4 — 계측기 교체는 deactivate+create 플로우). tag.changeInfo(tagSeCd, tagDesc, ioCd) 호출 → 검증: 단위 테스트 GREEN
- [x] TagService.deactivateTag(String tagSrlNo) — @Transactional. Repository.findById empty 시 TAG_NOT_FOUND. tag.deactivate() 호출 → 검증: 단위 테스트 GREEN

### Phase 6: 웹 계층 — DTO + Controller

- [x] `api/src/main/java/com/mo/swtp/tag/dto/TagDto.java` 신규 — @Data @NoArgsConstructor @Schema(description="태그 응답 DTO"). 필드 (tagSrlNo, instrumentId, tagSeCd, description, unit, tagDesc, ioCd, useYn, rgstrDtm). enum 필드는 @Schema(implementation = X.class) 명시. from(Tag) 정적 팩토리에서 description = tagSeCd.getDescription(), unit = tagSeCd.getUnit() enum 매핑 → 검증: 컴파일 통과 + Swagger 노출 시 unit 필드 노출 확인
- [x] `api/src/main/java/com/mo/swtp/tag/dto/TagUpsertDto.java` 신규 — @Data @NoArgsConstructor. 필드 (tagSrlNo @NotBlank, instrumentId @NotBlank, tagSeCd @NotNull TagMeasurementType, tagDesc nullable, ioCd @NotNull IoCode). useYn 미보유 (등록 시 Y 강제). enum 필드는 @Schema(implementation = X.class) 명시 → 검증: 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/tag/web/TagController.java` 신규 — @RestController @RequestMapping("/api/tags") @Tag(name="10. 태그 관리") extends CommonController. 의존성: TagService + RoleGuard. 5개 엔드포인트 모두 첫 줄 roleGuard.requireAdmin(request) → 검증: 컴파일 통과
- [x] TagController.registerTag — POST /api/tags @Operation summary "태그 등록" + @ApiResponses (200·400·401·403·409·500) → 검증: Swagger 노출 확인
- [x] TagController.findAllTags — GET /api/tags @Operation summary "태그 목록 조회" + @ApiResponses (200·401·403·500) → 검증: Swagger 노출 확인
- [x] TagController.findTag — GET /api/tags/{tagSrlNo} @Operation summary "태그 단건 조회" + @ApiResponses (200·401·403·404·500) → 검증: Swagger 노출 확인
- [x] TagController.modifyTag — PUT /api/tags/{tagSrlNo} @Operation summary "태그 수정" + @ApiResponses (200·400·401·403·404·500) → 검증: Swagger 노출 확인
- [x] TagController.deactivateTag — DELETE /api/tags/{tagSrlNo} @Operation summary "태그 논리 삭제" + @ApiResponses (200·401·403·404·500) → 검증: Swagger 노출 확인

### Phase 7: 테스트 — TagServiceTest

- [x] `api/src/test/java/com/mo/swtp/tag/service/TagServiceTest.java` 신규 — @ExtendWith(MockitoExtension.class). @Mock TagRepository + InstrumentRepository. @InjectMocks TagService → 검증: 컴파일 통과
- [x] TagServiceTest.정상_등록_시_save_가_호출되고_useYn_Y_로_생성된다 — given existsById false + instrument 존재 → tagRepository.save verify, ArgumentCaptor 로 useYn = YnType.Y 단언 → 검증: ./gradlew.bat :api:test PASS
- [x] TagServiceTest.중복_등록_시_DUPLICATE_TAG_SRL_NO_예외가_발생한다 — given existsById true → assertThatThrownBy RestApiException with DUPLICATE_TAG_SRL_NO → 검증: ./gradlew.bat :api:test PASS
- [x] TagServiceTest.instrument_미존재_시_INVALID_INSTRUMENT_ID_예외가_발생한다 — given existsById false + instrument findById empty → assertThatThrownBy RestApiException with INVALID_INSTRUMENT_ID, tagRepository.save 미호출 verify → 검증: ./gradlew.bat :api:test PASS
- [x] TagServiceTest.미존재_단건_조회_시_TAG_NOT_FOUND_예외가_발생한다 — given findById empty → assertThatThrownBy RestApiException with TAG_NOT_FOUND → 검증: ./gradlew.bat :api:test PASS
- [x] TagServiceTest.수정_시_changeInfo_가_호출되어_필드가_갱신된다 — given findById present → tag.changeInfo verify (Spy 또는 결과값 단언) → 검증: ./gradlew.bat :api:test PASS
- [x] TagServiceTest.논리_삭제_시_useYn_이_N_으로_변경된다 — given findById present → tag.deactivate 후 tag.getUseYn() == YnType.N 단언 → 검증: ./gradlew.bat :api:test PASS

### Phase 8: 빌드 검증

- [x] ./gradlew.bat :common:build 실행 → 검증: BUILD SUCCESSFUL (Tag·TagMeasurementType QClass 재생성 정상)
- [x] ./gradlew.bat :api:test 실행 → 검증: BUILD SUCCESSFUL — TagServiceTest 6건 + TagMeasurementTypeTest 1건 GREEN
- [x] ./gradlew.bat clean build 실행 → 검증: 전체 BUILD SUCCESSFUL

## 산출물
- [결과 (작성 예정)](../../../results/20260508/태그관리/RESULT1.md)
