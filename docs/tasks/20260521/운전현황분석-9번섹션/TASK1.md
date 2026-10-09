---
status: completed
created: 2026-05-21
updated: 2026-05-21
impl_completed_at: Phase 7 완료 시점 (2026-05-21) — BUILD SUCCESSFUL + 단위 테스트 GREEN. PredcData1Min 신설 미실시 (TagPrediction 7번 섹션 산출물 재사용) — PLAN1 가정 결정에 따라 자연 처리. DDL·인덱스 변경 0건 (idx_predc_1m_h_tag_time 기존 활용).
---
# 운전현황분석-9번섹션 — 태스크 분해

## 관련 계획
- [계획안](../../../plan/20260521/운전현황분석-9번섹션/PLAN1.md)
- [도메인 분석](../../../analyze/20260521/운전현황분석-9번섹션/ANALYZE1.md)

## Phase

> 본 TASK 는 단일 파일 (`TASK1.md`) — PLAN1 §성공 기준 14종 + 신규 코드 변경 6종 (Repository·내부 DTO·응답 DTO·Service·Controller·테스트) + 엔티티 매핑 확인 1종 = 7 Phase × 약 38 체크박스 규모로 분할 기준 (Phase 10 / 체크박스 60) 미달. 분할 미적용.
>
> 검증 영역 백틱 금지 — `check-task-unstage.sh` 훅 파싱 호환 ([`checkbox-rules.md`](../../../../.claude/rules/process/doc-harness/checkbox-rules.md)).
>
> 9번 섹션은 4번 섹션 동형 재구현이나 데이터 소스 (`rawdata_1m_h` → `predc_1m_h`) 와 결측·품질 분기 (제거) 가 다르다. 4번 헬퍼 직접 재사용 금지 — 9번 자체 private 메서드로 재작성 (사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합).

### Phase 1: 엔티티 매핑 확인 + 인덱스 존재 확인

PLAN1 가정 — `com.mo.swtp.opt.domain.PredcData1Min` (또는 유사 명칭) 엔티티 매핑 클래스 존재 여부 확인 + `idx_predc_1m_h_tag_time` 인덱스 존재 확인.

- [ ] `swtp/backend/common/src/main/java/com/mo/swtp/opt/domain/` 디렉토리 글롭 검사 → 검증: Glob 결과로 PredcData1Min.java 또는 유사 명칭 엔티티 존재 여부 확인 (있으면 본 사이클 재사용, 없으면 신설 결정)
- [ ] `swtp/backend/common/src/main/resources/db/migration/V5__opt.sql` 읽기 → 검증: predc_1m_h 테이블 정의 + idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm) 인덱스 정의 존재 확인
- [ ] `swtp/backend/docs/ddl/opt.sql` 읽기 → 검증: predc_1m_h 테이블·인덱스 정의 sync 확인
- [ ] (조건부 — 엔티티 미존재 시) `swtp/backend/common/src/main/java/com/mo/swtp/opt/domain/PredcData1Min.java` 신규 작성 → 검증: @Entity + @Table(name="predc_1m_h") + @Id Long predcId + LocalDateTime predcDtm + String tagSrlNo + BigDecimal predcVal + BaseEntity 4 상속 + Javadoc 명시 (predc_1m_h immutable INSERT-only)

### Phase 2: 내부 DTO 신설 — TagPredcLatestDto

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/opt/dto/TagPredcLatestDto.java` 신규 작성 → 검증: Java record 3필드 (String tagSrlNo, LocalDateTime predcDtm, BigDecimal predcVal) + Javadoc 명시 (4번 RawDataLatestDto 5필드 대비 corrVal·rawVal·qualityCd 3필드 제거)
- [ ] `./gradlew.bat :api:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (record 컴파일 확인)

### Phase 3: Repository 신설 — TagPredcLatestRepository + Custom + Impl

PLAN1 §Repository 신규. native SQL DISTINCT ON + 1시간 윈도우 (`coding-discipline.md §2.5` 면책 영역 적용).

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/opt/repository/TagPredcLatestCustomRepository.java` 신규 작성 → 검증: interface + List<TagPredcLatestDto> findLatestByTagSrlNos(List<String> tagSrlNos) 시그니처 + Javadoc 명시 (DISTINCT ON + NOW()-1h + idx_predc_1m_h_tag_time 강제 + 4번 RawDataCustomRepository 동형 정책)
- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/opt/repository/TagPredcLatestRepository.java` 신규 작성 → 검증: interface extends JpaRepository<PredcData1Min, Long>, TagPredcLatestCustomRepository
- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/opt/repository/TagPredcLatestCustomRepositoryImpl.java` 신규 작성 → 검증: @Repository + @RequiredArgsConstructor + EntityManager 주입 + findLatestByTagSrlNos 구현 + 빈 리스트 가드 (tagSrlNos.isEmpty() → return List.of()) + Javadoc §2.5 면책 인용 근거 (db/query-tuning.md §2) 명기
- [ ] native SQL 구문 작성 → 검증: SELECT DISTINCT ON (tag_srl_no) tag_srl_no, predc_dtm, predc_val FROM predc_1m_h WHERE tag_srl_no IN (:tagSrlNos) AND predc_dtm >= NOW() - INTERVAL '1 hour' ORDER BY tag_srl_no, predc_dtm DESC 정확 일치
- [ ] `./gradlew.bat :api:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (Repository 컴파일 확인)

### Phase 4: 응답 DTO 신설 — FacilityPredcOperatingStatusDto

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityPredcOperatingStatusDto.java` 신규 작성 → 검증: 6필드 (facilityId·facilityNm·predcDtm·onPumpNms·totalElpwrAmt·elpwrUnitQty) + 정적 팩토리 of(...) + @Getter + private 기본 생성자 + @Schema 어노테이션 + @JsonFormat (predcDtm shape=STRING pattern="yyyy-MM-dd HH:mm:ss") + BaseAuditResponseDto 미상속
- [ ] FacilityPredcOperatingStatusDto Swagger description → 검증: elpwrUnitQty 필드 @Schema description 에 "예측 전력원단위 (kWh/m³)" 포함 + onPumpNms 필드 @Schema description 에 "predc_val IS NULL (결측·신뢰도 불명) 과 predc_val == 0.0 (예측 Off 확신) 두 케이스 모두 본 목록에서 제외" 구별 정책 명시
- [ ] `./gradlew.bat :api:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (DTO 컴파일 확인)

### Phase 5: Service 신설 — FacilityPredcOperatingStatusService

PLAN1 §Service 책임 분담의 직렬 6단계를 private 헬퍼로 분해 (정량 기준 §2.1 50줄/3단 준수).

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/service/FacilityPredcOperatingStatusService.java` 신규 작성 → 검증: @Service + @RequiredArgsConstructor + @Transactional(readOnly = true) + 클래스 javadoc 명시 (운전현황분석 9번 섹션 + PLAN1 인용 + 4번 동형 재구현 + predc_1m_h 데이터 소스 + quality_cd·corr_val 컬럼 부재)
- [ ] findFacilityPredcOperatingStatus(facilityId) public 메서드 작성 → 검증: 6 Step 직렬 호출 (findActiveFacilityOrThrow → instrumentRepository → tagRepository → tagPredcLatestRepository → collectOnPumps → DTO 조립) + 메서드 본문 50줄 이하
- [ ] findActiveFacilityOrThrow(facilityId) private 메서드 작성 → 검증: facility.useYn != Y 시 FACILITY_NOT_FOUND + facilityType 이 SUPPORTED_TYPES(PWTF·DWT·PRSF) 미포함 시 UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS 던짐
- [ ] SUPPORTED_TYPES 상수 정의 → 검증: EnumSet.of(PWTF, DWT, PRSF) — RSV·POINT 제외
- [ ] predcVal(TagPredcLatestDto p) private 메서드 작성 → 검증: p==null → null, return p.predcVal() 단순 분기 (PLAN1 §값 선택 정책 코드 일치) + Javadoc 에 predc_1m_h corr_val 컬럼 부재 명기
- [ ] isPumpRunning(TagPredcLatestDto ops) private 메서드 작성 → 검증: null OR predcVal != 1.0 → false (PLAN1 §OPS On 판정 정책 코드 일치) + Javadoc 에 quality_cd 컬럼 부재 명기
- [ ] sumOnPumpPwr(List<Pump> onPumps, Map<...>) private 메서드 작성 → 검증: stream filter non-null PWI + predcVal 매핑 + filter non-null + reduce(ZERO, BigDecimal::add) + quality 분기 부재
- [ ] computeUnitConsumption(BigDecimal totalElpwrAmt, TagPredcLatestDto fri) private 메서드 작성 → 검증: 3 케이스 분기 (분자 null/0, FRI null, friVal null/0) 모두 null 반환 + 정상 시 totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP) + quality 분기 부재
- [ ] selectMaxPredcDtm(Collection<TagPredcLatestDto>) private 메서드 작성 → 검증: stream filter non-null + map predcDtm + filter non-null + max(naturalOrder) + orElse(null)
- [ ] selectFacilityFri / pickLatest / loadTagsByInstrument / loadLatestByTag / collectUsedTags / filterPumps 등 4번 동형 헬퍼 재작성 → 검증: 4번 산출물 코드 직접 복사 금지 + 9번 자체 private 메서드 명명·시그니처로 재작성 (TagPredcLatestDto 타입 사용)
- [ ] `./gradlew.bat :api:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (Service 컴파일 확인)

### Phase 6: Controller 메서드 추가

- [ ] `swtp/backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 → 검증: FacilityPredcOperatingStatusService 필드 주입 (@RequiredArgsConstructor 기존 사용) + findFacilityPredcOperatingStatus(facilityId) @GetMapping("/{facilityId}/operating-status/prediction") 메서드 추가
- [ ] Controller 메서드 Swagger 어노테이션 → 검증: @Operation(summary="활성 시설의 예측 운영 현황 조회 (운전현황분석 9번 섹션)") + @ApiResponses 6종 (200/400/401/403/404/500) 명시 + 400 description 에 UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS 명시 + description 에 quality_cd·corr_val 컬럼 부재 명기
- [ ] FacilityController import 정리 → 검증: FacilityPredcOperatingStatusDto + FacilityPredcOperatingStatusService import 추가, 미사용 import 0건
- [ ] `./gradlew.bat :api:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (Controller 컴파일 확인)

### Phase 7: 단위 테스트 작성 + 통합 빌드

PLAN1 §성공 기준 14종 중 단위 테스트 대상 11종 (#1·#2·#3·#4·#5·#6·#7·#8·#9·#10·#11) 을 모두 커버.

- [ ] `swtp/backend/api/src/test/java/com/mo/swtp/facility/service/FacilityPredcOperatingStatusServiceTest.java` 신규 작성 → 검증: @ExtendWith(MockitoExtension.class) + @Mock 4종 (FacilityRepository·InstrumentRepository·TagRepository·TagPredcLatestRepository) + @InjectMocks FacilityPredcOperatingStatusService
- [ ] 정상 시나리오 테스트 (성공기준 #1) → 검증: 예측운영현황_정상_조회_시_예측On펌프이름_예측PWI합산_예측전력원단위_predcDtm_반환 메서드 GREEN — PUMP 3대 중 2대 예측 On, PWI 합산 50.0+30.0=80.0, FRI=400.0 → elpwrUnitQty=0.2 (80÷400)
- [ ] predcDtm max 산출 테스트 (성공기준 #2) → 검증: predcDtm_은_사용된_태그들의_max_predc_dtm_으로_산출된다 메서드 GREEN + native SQL grep 검증 명령 작성 (별도 grep 단계로 수행)
- [ ] OPS predc_val 4 케이스 테스트 (성공기준 #3) → 검증: OPS_predc_val_4_케이스 파라미터화 테스트 GREEN — null·0.0·0.7·1.5 모두 onPumpNms 미포함
- [ ] quality_cd 분기 부재 검증 (성공기준 #4) → 검증: grep -E "qualityCd|QualityCode" FacilityPredcOperatingStatusService.java 매칭 0
- [ ] effectiveVal·corrVal·rawVal 분기 부재 검증 (성공기준 #5) → 검증: grep -E "effectiveVal|corrVal|rawVal" FacilityPredcOperatingStatusService.java 매칭 0
- [ ] RSV/POINT 거부 테스트 (성공기준 #6) → 검증: RSV_시설은_UNSUPPORTED_FACILITY_TYPE_거부 + POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부 메서드 GREEN
- [ ] 빈 데이터 테스트 (성공기준 #7) → 검증: 예측데이터_부재_시_빈_응답_반환 메서드 GREEN — predcDtm=null + onPumpNms=[] + totalElpwrAmt=ZERO + elpwrUnitQty=null
- [ ] PWI 부분 결측 테스트 (성공기준 #8) → 검증: PWI_부분_결측_시_다른_펌프_합산_유지 메서드 GREEN — 결측 펌프만 제외, 다른 펌프 PWI 합산 유지
- [ ] FRI 분모 무효 3 케이스 테스트 (성공기준 #9) → 검증: elpwrUnitQty_분모_무효_시_null_반환 파라미터화 테스트 GREEN — FRI null·0·부재 모두 elpwrUnitQty = null
- [ ] PUMP 필터 강제 테스트 (성공기준 #10) → 검증: 다른_자식_종류_인스트루먼트는_onPumpNms_에_포함되지_않는다 메서드 GREEN
- [ ] FACILITY_NOT_FOUND 테스트 (성공기준 #11) → 검증: 비활성_시설은_FACILITY_NOT_FOUND_거부 + 미존재_시설은_FACILITY_NOT_FOUND_거부 메서드 GREEN
- [ ] `./gradlew.bat :api:test --tests com.mo.swtp.facility.service.FacilityPredcOperatingStatusServiceTest` 실행 → 검증: 모든 테스트 GREEN + 0 failures
- [ ] `./gradlew.bat build` 실행 → 검증: BUILD SUCCESSFUL 출력 + 전체 모듈 컴파일 + 전체 테스트 통과 (성공기준 #14)
- [ ] (선택) `./gradlew.bat :api:bootRun` 실행 후 Swagger UI 수동 확인 → 검증: http://localhost:8080/swagger-ui.html 에서 GET /api/facility/{facilityId}/operating-status/prediction 응답 구조 6필드 노출 + 한국어 description 노출 (성공기준 #13)

## 산출물
- 신규 생성 6건 (PredcData1Min 미존재 시 7건):
  - `swtp/backend/api/src/main/java/com/mo/swtp/opt/dto/TagPredcLatestDto.java`
  - `swtp/backend/api/src/main/java/com/mo/swtp/opt/repository/TagPredcLatestCustomRepository.java`
  - `swtp/backend/api/src/main/java/com/mo/swtp/opt/repository/TagPredcLatestRepository.java`
  - `swtp/backend/api/src/main/java/com/mo/swtp/opt/repository/TagPredcLatestCustomRepositoryImpl.java`
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityPredcOperatingStatusDto.java`
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/service/FacilityPredcOperatingStatusService.java`
  - `swtp/backend/api/src/test/java/com/mo/swtp/facility/service/FacilityPredcOperatingStatusServiceTest.java`
  - (조건부) `swtp/backend/common/src/main/java/com/mo/swtp/opt/domain/PredcData1Min.java` — Phase 1 글롭 결과 미존재 시
- 기존 수정 1건:
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java`
- DDL·마이그레이션 변경 0건 — `idx_predc_1m_h_tag_time` 기존 인덱스 활용 (PLAN1 §인덱스 신규 0건).
- `FacilityErrorCode` 변경 0건 — `UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` 4번 사이클 enum 값 재사용.

> Medium 작업이므로 RESULT/REVIEW 면제 ([`transitions.md`](../../../../.claude/rules/process/doc-harness/transitions.md) `/dev:impl` 행). impl 단계 직후 `/dev:commit` 안내 자동 출력.
