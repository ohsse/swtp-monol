---
status: completed
created: 2026-05-14
updated: 2026-05-14
---
# 송수펌프제어분석 4번 섹션 (배수지 DWT 현황 표출) — 태스크

## 관련 계획
- [계획안](../../../plan/20260514/송수펌프제어분석-4번섹션/PLAN1.md)

## 작업 개요

`GET /api/facility/{parentFacilityId}/dwts/states` 엔드포인트 신규 구현. 응답 DTO 6건 + Service 1건 + Controller 메서드 1건 + 테스트 1건 + 마이그레이션 SQL 1건. 단일 TASK (분할 미적용 — 체크박스 약 28건 / 6 Phase, 분할 기준 미달).

## Phase

### Phase 1: DB 마이그레이션 SQL 작성

- [x] `common/src/main/resources/db/migration/V9_2__facility_instrument_lookup_indexes.sql` 신규 작성 (R1·R2 인덱스 2건, CREATE INDEX CONCURRENTLY IF NOT EXISTS, 한국어 주석 포함) → 검증: 파일 존재 + grep idx_facility_m_parent_type_yn + grep idx_instrument_m_facility_equip 매칭 2건

### Phase 2: 응답 DTO 6건 신규 작성

- [x] `api/src/main/java/com/mo/swtp/facility/dto/InletFlwmtrStateDto.java` 신규 작성 (FRI 4필드 + PRI 4필드 = 8필드, @Schema implementation = QualityCode.class, @JsonFormat LocalDateTime 초 단위, @Getter 정적 팩토리 from(Instrument, Map<TagSrlNo, RawDataLatestDto>) 패턴) → 검증: 컴파일 통과 (./gradlew :api:compileJava BUILD SUCCESSFUL)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/OutletFlwmtrStateDto.java` 신규 작성 (FRI 4필드만, PRI 미포함, 동일 어노테이션 패턴) → 검증: 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/facility/dto/ValveStateDto.java` 신규 작성 (instrumentId/instrumentNm + VOI 4필드 opngRawVal·opngCorrVal·opngAcqDtm·opngQualityCd) → 검증: 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/facility/dto/LvmtrStateDto.java` 신규 작성 (instrumentId/instrumentNm + LEI 4필드 wtlvRawVal·wtlvCorrVal·wtlvAcqDtm·wtlvQualityCd) → 검증: 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DwtStateDto.java` 신규 작성 (facilityId/facilityNm + inFlwmtr/outFlwmtr + multipleInFlwmtrDetected/multipleOutFlwmtrDetected + valves @ArraySchema + lvmtrs @ArraySchema, 모든 사용자 정의 타입 @Schema implementation 명시) → 검증: 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DwtGroupStateDto.java` 신규 작성 (parentFacilityId/parentFacilityNm + dwts @ArraySchema, 정적 팩토리 of(Facility parent, List<DwtStateDto> dwts)) → 검증: 컴파일 통과

### Phase 3: DwtStateService 신규 작성

- [x] `api/src/main/java/com/mo/swtp/facility/service/DwtStateService.java` 신규 작성 (@Service + @Transactional(readOnly=true) + @RequiredArgsConstructor + @Slf4j, 상수 DWT_EQUIP_TYPES + DWT_TAG_TYPES, public findDwtStates(parentFacilityId) + private 매핑 헬퍼 4개 mapInletFlwmtr/mapOutletFlwmtr/mapValve/mapLvmtr) → 검증: 컴파일 통과 + grep mapInletFlwmtr/mapOutletFlwmtr/mapValve/mapLvmtr 4건 매칭
- [x] DwtStateService findDwtStates 메서드 Step 1 — 부모 facility 조회 + 미존재/useYn=N → RestApiException(FACILITY_NOT_FOUND) 구현 → 검증: 컴파일 통과 + grep FacilityErrorCode.FACILITY_NOT_FOUND 매칭
- [x] DwtStateService findDwtStates 메서드 Step 2~5 — 자식 DWT 조회 + 다건 계측기 조회 + 다건 태그 조회 + 최신 측정값 다건 조회 구현 (모두 기존 Repository 메서드 재사용, 신규 메서드 추가 0건) → 검증: 컴파일 통과
- [x] DwtStateService findDwtStates 메서드 Step 6 — 메모리 그룹화 (instrumentsByDwt / tagsByInstrument) + inletList/outletList 추출 + multipleIn/multipleOut 플래그 + WARN 로그 구현 → 검증: 컴파일 통과 + grep multipleInFlwmtrDetected 매칭 + grep log.warn 매칭
- [x] DwtStateService 매핑 헬퍼 4개 (mapInletFlwmtr/mapOutletFlwmtr/mapValve/mapLvmtr) 구현 — 각 DTO 의 정적 팩토리 호출 패턴 → 검증: 컴파일 통과 + 각 메서드 50줄 이내 (ROOT coding-discipline.md §2.1)

### Phase 4: FacilityController 수정

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 에 findDwtStates(parentFacilityId) 메서드 추가 (@Operation summary + @ApiResponses 200/400/401/403/404/500 + @GetMapping /{parentFacilityId}/dwts/states + getResponseEntity(data) 래핑) → 검증: 컴파일 통과 + grep findDwtStates 매칭 1건 + grep /dwts/states 매칭 1건

### Phase 5: 단위 테스트 작성

- [x] `api/src/test/java/com/mo/swtp/facility/service/DwtStateServiceTest.java` 신규 작성 (@ExtendWith MockitoExtension + @Mock FacilityRepository·InstrumentCustomRepository·TagRepository·RawDataCustomRepository + @InjectMocks DwtStateService, 픽스처 빌더 헬퍼 메서드 분리) → 검증: 컴파일 통과
- [x] DwtStateServiceTest 정상응답 케이스 (DWT 2건 · 각 inlet/outlet/valve/lvmtr 보유) 작성 → 검증: ./gradlew :api:test --tests DwtStateServiceTest.findDwtStates_정상응답 GREEN
- [x] DwtStateServiceTest 부모미존재 케이스 (assertThatThrownBy + errorCode FACILITY_NOT_FOUND) 작성 → 검증: ./gradlew :api:test --tests DwtStateServiceTest.findDwtStates_부모미존재 GREEN
- [x] DwtStateServiceTest 자식DWT0건 케이스 (응답 dwts empty + 부모 필드 채워짐) 작성 → 검증: ./gradlew :api:test --tests DwtStateServiceTest.findDwtStates_자식DWT0건_빈리스트 GREEN
- [x] DwtStateServiceTest 유입중복등록 케이스 (DWT 1건 · 유입 FLWMTR 2건 · multipleInFlwmtrDetected==true + inFlwmtr 첫 매치) 작성 → 검증: ./gradlew :api:test --tests DwtStateServiceTest.findDwtStates_유입중복등록_플래그TRUE GREEN

### Phase 6: 빌드·Swagger 검증

- [x] `./gradlew.bat :api:test --tests DwtStateServiceTest` 실행 → 검증: 4개 테스트 케이스 GREEN + 실패 0건
- [x] `./gradlew.bat clean build` 실행 → 검증: api:compileJava BUILD SUCCESSFUL. 본 작업 단위 테스트 GREEN. 통합 테스트 (`@SpringBootTest`) 17건 실패는 본 작업 무관 — 로컬 PostgreSQL 미기동 환경 의존 (test-strategy.md §2.2 DB 전제조건 명시). 추가 검증으로 facility 도메인 단위 테스트 (DwtStateServiceTest + FacilityStateServiceTest + FacilityServiceTest) 모두 GREEN 재확인
- [~] `./gradlew.bat :api:bootRun` 기동 후 Swagger UI 접속 → 검증: 인터랙티브 검증 단계 (로컬 PostgreSQL + bootRun 환경 필요) — backend 자동화 검증 범위 외, 사용자 환경에서 별도 검증. DTO 6건의 `@Schema implementation` 어노테이션은 모두 작성 완료 + 컴파일 GREEN

## 산출물
- [결과](../../../results/20260514/송수펌프제어분석-4번섹션/RESULT1.md) — Medium 작업은 `/dev:result` 단계 면제 (Large 전용). 본 항목은 라이프사이클 표 정합 목적의 자리표시자
