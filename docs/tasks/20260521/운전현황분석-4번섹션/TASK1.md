---
status: completed
created: 2026-05-21
updated: 2026-05-21
impl_completed_at: Phase 6 완료 시점 (2026-05-21) — BUILD SUCCESSFUL + 16 테스트 GREEN. 인덱스 DB 적용은 backend 재기동 시 Flyway 자동 적용 (V8_2 신규 마이그레이션). tag_fqi_enum_누락 사이클 (5526c00) 머지 회귀 복원 후 재개 완료
---
# 운전현황분석-4번섹션 — 태스크 분해

## 관련 계획
- [계획안](../../../plan/20260521/운전현황분석-4번섹션/PLAN1.md)
- [도메인 분석](../../../analyze/20260521/운전현황분석-4번섹션/ANALYZE1.md)

## Phase

> 본 TASK 는 단일 파일 (`TASK1.md`) — PLAN1 §성공 기준 12종 + 신규 코드 변경 5종 (DTO·ErrorCode·Service·Controller·테스트) + 인덱스 확인 1종 = 6 Phase × 약 35 체크박스 규모로 분할 기준 (Phase 10 / 체크박스 60) 미달. 분할 미적용.
>
> 검증 영역 백틱 금지 — `check-task-unstage.sh` 훅 파싱 호환 ([`checkbox-rules.md`](../../../../.claude/rules/process/doc-harness/checkbox-rules.md)).

### Phase 1: 인덱스 확인 및 (필요 시) 추가

PLAN1 가정 5 결정 — `tag_m (instrument_id, tag_se_cd)` 부분 인덱스 존재 여부 확인 후 미존재 시 추가.

- [ ] `swtp/backend/common/src/main/resources/db/migration/V8__tag.sql` 읽기 → 검증: 기존 인덱스 정의 확인 (idx_tag_m_instrument_id_tag_se_cd 또는 유사 명칭 존재 여부)
- [ ] `swtp/backend/docs/ddl/tag.sql` 읽기 → 검증: 인덱스 정의 확인
- [ ] (조건부) `swtp/backend/common/src/main/resources/db/migration/V8_2__tag_patch.sql` 생성 → 검증: V8_1 존재 여부에 따라 V8_N 다음 번호 결정 후 파일 생성. 부재 시 본 Phase 의 이후 체크박스 모두 스킵
- [ ] (조건부) `swtp/backend/docs/ddl/tag.sql` ALTER 누적 → 검증: 본 patch 파일과 동일한 CREATE INDEX 구문이 docs/ddl/tag.sql 하단에 추가됨
- [ ] (조건부) CREATE INDEX 구문 작성 → 검증: 패치 SQL 에 다음 정확 구문 포함 — CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tag_m_instrument_id_tag_se_cd ON tag_m (instrument_id, tag_se_cd) WHERE use_yn = 'Y';

### Phase 2: ErrorCode 추가 + DTO 신규 작성

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/exception/FacilityErrorCode.java` 수정 → 검증: UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS(400) enum 값 추가 + Javadoc 4종 분기를 5종 분기로 갱신 (FACILITY_NOT_FOUND·DUPLICATE_FACILITY_NM·FACILITY_TYPE_MISMATCH·INVALID_PARENT_FACILITY_ID·UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS)
- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusDto.java` 신규 작성 → 검증: 6필드 (facilityId·facilityNm·measurementDtm·onPumpNms·totalElpwrAmt·elpwrUnitQty) + 정적 팩토리 of(...) + @Getter + private 기본 생성자 + @Schema 어노테이션 + @JsonFormat (measurementDtm). 계획 외 모듈 변경 — common→api (api/CLAUDE.md 응답 DTO 정합 + common/CLAUDE.md "API 전용 request/response DTO" 금지 정합)
- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusDto.java` Swagger description → 검증: elpwrUnitQty 필드 @Schema description 에 "전력원단위 (kWh/m³)" 포함
- [ ] `./gradlew.bat :common:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (DTO 컴파일 확인)

### Phase 3: Service 신규 작성

PLAN1 §Service 책임 분담의 직렬 6단계를 private 헬퍼로 분해 (정량 기준 §2.1 50줄/3단 준수).

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusService.java` 신규 작성 → 검증: @Service + @RequiredArgsConstructor + @Transactional(readOnly = true) + 클래스 javadoc 명시 (운전현황분석 4번 섹션 + PLAN1 인용)
- [ ] FacilityOperatingStatusService.findFacilityOperatingStatus(facilityId) public 메서드 작성 → 검증: 6 Step 직렬 호출 (findActiveFacilityOrThrow → instrumentRepository → tagRepository → rawDataRepository → collectOnPumps → DTO 조립) + 메서드 본문 50줄 이하
- [ ] findActiveFacilityOrThrow(facilityId) private 메서드 작성 → 검증: facility.useYn != Y 시 FACILITY_NOT_FOUND + facilityType 이 SUPPORTED_TYPES(PWTF·DWT·PRSF) 미포함 시 UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS 던짐
- [ ] SUPPORTED_TYPES 상수 정의 → 검증: EnumSet.of(PWTF, DWT, PRSF) — RSV·POINT 제외
- [ ] effectiveVal(RawDataLatestDto r) private 메서드 작성 → 검증: r==null → null, r.corrVal() != null ? corrVal : rawVal 분기 (PLAN1 §값 선택 정책 코드 일치)
- [ ] isPumpRunning(RawDataLatestDto ops) private 메서드 작성 → 검증: null OR qualityCd != GOOD OR effectiveVal != 1.0 → false (PLAN1 §OPS On 판정 정책 코드 일치)
- [ ] sumOnPumpPwr(List<Pump> onPumps, Map<String, RawDataLatestDto> pwiByInstrumentId) private 메서드 작성 → 검증: GOOD 만 합산 + null/BAD/UNCERTAIN 전액 제외 + reduce(ZERO, BigDecimal::add)
- [ ] computeUnitConsumption(BigDecimal totalElpwrAmt, RawDataLatestDto fri) private 메서드 작성 → 검증: 분자/분모 0/NULL/BAD/부재 4 케이스 모두 null 반환 + 정상 시 totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP)
- [ ] latestMeasurementDtm(Collection<RawDataLatestDto>) private 메서드 작성 → 검증: stream filter non-null + map acqDtm + filter non-null + max(naturalOrder) + orElse(null)
- [ ] `./gradlew.bat :api:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (Service 컴파일 확인)

### Phase 4: Controller 메서드 추가

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 → 검증: FacilityOperatingStatusService 필드 주입 + findFacilityOperatingStatus(facilityId) @GetMapping("/{facilityId}/operating-status") 메서드 추가
- [ ] Controller 메서드 Swagger 어노테이션 → 검증: @Operation(summary="활성 시설의 운영 현황 조회 (운전현황분석 4번 섹션)") + @ApiResponses 6종 (200/400/401/403/404/500) 명시 + 400 description 에 UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS 명시
- [ ] FacilityController import 정리 → 검증: FacilityOperatingStatusDto + FacilityOperatingStatusService import 추가, 미사용 import 0건 (IDE 자동 정렬)
- [ ] `./gradlew.bat :api:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (Controller 컴파일 확인)

### Phase 5: 단위 테스트 작성

PLAN1 §성공 기준 12종 중 단위 테스트 대상 9종 (#1·#2·#3·#4·#5·#6·#7·#8·#9) 을 모두 커버.

- [ ] `swtp/backend/api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingStatusServiceTest.java` 신규 작성 → 검증: @ExtendWith(MockitoExtension.class) + @Mock 4종 (FacilityRepository·InstrumentRepository·TagRepository·RawDataRepository) + @InjectMocks FacilityOperatingStatusService
- [ ] 정상 시나리오 테스트 (성공기준 #1) → 검증: 운영현황_정상_조회_시_On펌프이름_PWI합산_전력원단위_측정시간_반환 메서드 GREEN — PUMP 3대 중 2대 On, PWI 합산 50.0+30.0=80.0, FRI=400.0 → elpwrUnitQty=0.2 (80÷400)
- [ ] BAD OPS 제외 테스트 (성공기준 #2) → 검증: BAD_OPS_펌프는_onPumpNms_에서_제외된다 메서드 GREEN
- [ ] UNCERTAIN OPS 제외 테스트 (성공기준 #3) → 검증: UNCERTAIN_OPS_펌프는_onPumpNms_에서_제외된다 메서드 GREEN
- [ ] UNCERTAIN PWI 전액 제외 테스트 (성공기준 #4) → 검증: UNCERTAIN_PWI_는_totalElpwrAmt_합산에서_전액_제외된다 메서드 GREEN
- [ ] 분모 무효 4 케이스 테스트 (성공기준 #5) → 검증: elpwrUnitQty_분모_무효_시_null_반환 파라미터화 테스트 GREEN — FRI 0/NULL/BAD/부재 4 케이스 모두 elpwrUnitQty = null
- [ ] PUMP 필터 강제 테스트 (성공기준 #6) → 검증: 다른_자식_종류_인스트루먼트는_onPumpNms_에_포함되지_않는다 메서드 GREEN
- [ ] RSV/POINT 거부 테스트 (성공기준 #7) → 검증: RSV_시설은_UNSUPPORTED_FACILITY_TYPE_거부 + POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부 메서드 GREEN — RestApiException 던짐 + ErrorCode 일치
- [ ] measurementDtm max 산출 테스트 (성공기준 #8) → 검증: measurementDtm_은_사용된_태그들의_max_acq_dtm_으로_산출된다 메서드 GREEN
- [ ] 빈 데이터 테스트 (성공기준 #9) → 검증: OPS_태그_부재_시_빈_응답_반환 메서드 GREEN — onPumpNms=[] + totalElpwrAmt 결정값 (TASK 단계 결정 — ZERO 채택) + elpwrUnitQty=null + measurementDtm=null
- [ ] FACILITY_NOT_FOUND (비활성·미존재) 테스트 → 검증: 비활성_시설은_FACILITY_NOT_FOUND_거부 + 미존재_시설은_FACILITY_NOT_FOUND_거부 메서드 GREEN
- [ ] `./gradlew.bat :api:test --tests com.mo.swtp.facility.service.FacilityOperatingStatusServiceTest` 실행 → 검증: 모든 테스트 GREEN + 0 failures

### Phase 6: 통합 빌드 + Swagger 수동 확인

- [ ] `./gradlew.bat build` 실행 → 검증: BUILD SUCCESSFUL 출력 + 전체 모듈 컴파일 + 전체 테스트 통과 (성공기준 #11)
- [ ] (Phase 1 인덱스 추가한 경우) 인덱스 존재 확인 → 검증: PostgreSQL psql 또는 swtp-postgres-local MCP 도구로 SELECT indexname FROM pg_indexes WHERE tablename='tag_m' 결과에 idx_tag_m_instrument_id_tag_se_cd 포함 (성공기준 #12)
- [ ] (선택) `./gradlew.bat :api:bootRun` 실행 후 Swagger UI 수동 확인 → 검증: http://localhost:8080/swagger-ui.html 에서 GET /api/facility/{facilityId}/operating-status 응답 구조 6필드 노출 + ErrorCode UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS 400 응답 표기 (성공기준 #10)

## 산출물
- 신규 생성 5건:
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusDto.java` (계획 외 모듈 변경: common → api)
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusService.java`
  - `swtp/backend/api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingStatusServiceTest.java`
  - `swtp/backend/common/src/main/resources/db/migration/V8_2__tag_patch.sql` (Phase 1 완료)
  - `swtp/backend/docs/ddl/tag.sql` SSOT 사본 갱신 (Phase 1 완료)
- 기존 수정 2건:
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/exception/FacilityErrorCode.java`
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java`

> Medium 작업이므로 RESULT/REVIEW 면제 ([`transitions.md`](../../../../.claude/rules/process/doc-harness/transitions.md) `/dev:impl` 행). impl 단계 직후 `/dev:commit` 안내 자동 출력.
