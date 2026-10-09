---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석 — 7번 섹션 (시설 예측 데이터 표출) — TASK1

## 관련 계획
- [계획안](../../../plan/20260518/송수펌프제어분석-7번섹션/PLAN1.md)

## Phase

### Phase 1: 예측 시계열 테이블 DDL (`predc_1m_h`)
- [ ] `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` 신규 작성 — `CREATE TABLE predc_1m_h` (PK `(predc_id, predc_dtm)`, `PARTITION BY RANGE(predc_dtm)`, 6컬럼: `predc_id BIGINT NOT NULL`·`predc_dtm TIMESTAMP NOT NULL`·`tag_srl_no VARCHAR(50) NOT NULL`·`predc_val NUMERIC(15,4)`·`rgstr_dtm TIMESTAMP NOT NULL`·`rgstr_id VARCHAR(50) NOT NULL`) → 검증: psql \d+ predc_1m_h 출력 시 6컬럼 + PK 복합키 + 파티션 정의 확인
- [ ] `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` SEQUENCE 추가 — `CREATE SEQUENCE seq_predc_id` (allocationSize=100 정합용 INCREMENT BY 1·CACHE 100 옵션) → 검증: psql \ds seq_predc_id 출력 확인
- [ ] `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` 인덱스 추가 — `CREATE INDEX idx_predc_1m_h_tag_time ON predc_1m_h (tag_srl_no, predc_dtm)` ASC 강제 (DESC 금지, PLAN §1 ANALYZE1 안건 3) → 검증: psql \di idx_predc_1m_h_tag_time 출력 + ASC 확인
- [ ] `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` 월 RANGE 파티션 6개월 선행 생성 — 202605~202610 (`predc_1m_h_202605` ~ `predc_1m_h_202610`) → 검증: psql 명령 SELECT count(*) FROM pg_inherits WHERE inhparent='predc_1m_h'::regclass 결과 6
- [ ] `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` COMMENT ON COLUMN 6건 작성 — `predc_id`·`predc_dtm`·`tag_srl_no`·`predc_val` 도메인 컬럼 라벨 + `rgstr_dtm`·`rgstr_id` immutable 이력 표준 라벨 (indexing-and-migration.md §4.3) → 검증: check-ddl-column-comment.sh 훅 통과 + psql \d+ predc_1m_h 컬럼 Description 6컬럼 모두 출력

### Phase 2: 예측 엔티티 (`com.mo.swtp.opt.domain`)
- [ ] `common/src/main/java/com/mo/swtp/opt/domain/TagPredictionId.java` 신규 작성 — `@Embeddable` + `Serializable` + `predcId(Long)`·`predcDtm(LocalDateTime)` 복합키 + `@EqualsAndHashCode` → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL
- [ ] `common/src/main/java/com/mo/swtp/opt/domain/TagPrediction.java` 신규 작성 — `@Entity @Table(name="predc_1m_h")` + `@EntityListeners(AuditingEntityListener.class)` + `@EmbeddedId TagPredictionId` + `@SequenceGenerator(name="seqPredcId", sequenceName="seq_predc_id", allocationSize=100)` + `@GeneratedValue(strategy=SEQUENCE, generator="seqPredcId")` (Embeddable PK 일부에 SEQUENCE 매핑 — `predcId` 필드 또는 정적 팩토리에서 채번 후 Id 합성) + `tagSrlNo(String)`·`predcVal(BigDecimal)` + `@CreatedDate rgstrDtm`·`@CreatedBy rgstrId` + immutable 이력 (`updt_*` 미정의) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL + QClass 생성 확인

### Phase 3: 근접매칭 Repository (`com.mo.swtp.opt.repository`)
- [ ] `api/src/main/java/com/mo/swtp/opt/dto/TagPredictionMatchDto.java` 신규 작성 — Repository→Service 내부 전송 (`tagSrlNo`·`predcDtm`·`predcVal`) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionRepository.java` 신규 작성 — `JpaRepository<TagPrediction, TagPredictionId>, TagPredictionCustomRepository` 결합 (fixture/테스트 INSERT 용) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionCustomRepository.java` 신규 작성 — `List<TagPredictionMatchDto> findNearestByTagSrlNos(List<String> tagSrlNos, int windowMinutes)` 인터페이스 선언 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionCustomRepositoryImpl.java` 신규 작성 — `EntityManager.createNativeQuery` + PLAN §2 의 `latest_meas` CTE + `CROSS JOIN LATERAL` 단일 흐름 + `// §2.5 면책 (query-tuning.md §2)` 주석 (누락 시 REVIEW 블로커) + 인덱스 방향 혼재 의도(ASC predc / DESC rawdata) 주석 명기 + LATERAL Nested Loop 의도 주석 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL + grep "§2.5 면책" TagPredictionCustomRepositoryImpl.java 매칭

### Phase 4: Service 레이어 (`FacilityPredictionService`)
- [ ] `api/src/main/java/com/mo/swtp/facility/service/FacilityPredictionService.java` 신규 작성 — `@Service @RequiredArgsConstructor @Transactional(readOnly=true)` + private `findActiveFacilityOrThrow` 헬퍼 재구현 (섹션 3 private 헬퍼 재사용 금지) + 4-step (Facility→Instrument→Tag→TagPrediction) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `FacilityPredictionService` 섹션 7 전용 상수 별도 선언 — `PREDICTION_EQUIP_TYPES = List.of(EquipType.PUMP, EquipType.FLWMTR)` + `PREDICTION_TAG_TYPES = EnumSet.of(TagMeasurementType.FRI, TagMeasurementType.PRI, TagMeasurementType.OPS)` (섹션 3 STATE_* 공유 금지) → 검증: grep "PREDICTION_EQUIP_TYPES" FacilityPredictionService.java 매칭
- [ ] `FacilityPredictionService` 응답 매핑 헬퍼 분리 — `mapFlwmtrPrediction`·`mapPumpPrediction` private 메서드 (본문 50줄 초과 방지, 섹션 3 선례) + `predcIsRunning` Boolean 변환 (1.0/0.0/null → true/false/null) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 5: Controller + 응답 DTO + 설정
- [ ] `api/src/main/java/com/mo/swtp/facility/dto/FacilityPredictionDto.java` 신규 작성 — `@Getter` + private 생성자 + 정적 팩토리 `of(...)` + `facilityId`·`facilityNm`·`List<FlwmtrPredictionDto> flwmtrs`·`List<PumpPredictionDto> pumps` + `@ArraySchema(schema=@Schema(implementation=...))` → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `api/src/main/java/com/mo/swtp/facility/dto/FlwmtrPredictionDto.java` 신규 작성 — `instrumentId`·`instrumentNm`·`flwrtPredcVal(BigDecimal)`·`flwrtPredcDtm(LocalDateTime)`·`prsrPredcVal`·`prsrPredcDtm` + `*PredcDtm` `@JsonFormat(shape=STRING, pattern="yyyy-MM-dd HH:mm:ss")` → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `api/src/main/java/com/mo/swtp/facility/dto/PumpPredictionDto.java` 신규 작성 — `instrumentId`·`instrumentNm`·`oprtngType(PumpOprtngType)`·`predcIsRunning(Boolean)`·`predcDtm(LocalDateTime)` + `oprtngType @Schema(implementation=PumpOprtngType.class, description="펌프 물리 조작 가능 유형 (AI 운전 모드와 무관)")` + `predcIsRunning @Schema(description="예측 가동상태(미래 시점) — true=ON·false=OFF·null=결측")` + `predcDtm @JsonFormat` → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 엔드포인트 추가 — `GET /api/facility/{facilityId}/prediction` (`findFacilityPrediction`) + `@Operation summary="시설 예측 데이터 조회"` + `@ApiResponses 200/400/401/403/404/500` + `ResponseEntity<CommonResponseDto<FacilityPredictionDto>>` 반환 → 검증: grep "/prediction" FacilityController.java 매칭 + ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [ ] `api/src/main/resources/application-common.yml` 설정 키 추가 — `opt.prediction.match-window-minutes: 5` (PLAN 가정 결정, 하드코딩 금지) → 검증: grep "match-window-minutes" application-common.yml 매칭
- [ ] `TagPredictionCustomRepositoryImpl` 의 `windowMinutes` 주입 경로 확립 — `@Value("${opt.prediction.match-window-minutes:5}")` 필드 또는 Service 가 주입 후 메서드 파라미터로 전달 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 6: 테스트
- [ ] `api/src/test/java/com/mo/swtp/facility/service/FacilityPredictionServiceTest.java` 신규 작성 — `@ExtendWith(MockitoExtension.class)` + 시설 미존재/비활성 → `FACILITY_NOT_FOUND` 검증 + FRI/PRI/OPS 분리 매핑 검증 + 근접매칭 정확/근접/윈도우밖(null) 케이스 + `predcIsRunning` Boolean 변환 (BigDecimal 1.0/0.0/null) → 검증: ./gradlew.bat :api:test PASS
- [ ] `api/src/test/java/com/mo/swtp/opt/repository/TagPredictionCustomRepositoryTest.java` 신규 작성 — `@SpringBootTest(webEnvironment=NONE)` + `@ActiveProfiles("test")` + `@Transactional` + 로컬 PostgreSQL 월 파티션 선행 생성 (`@Sql` 또는 코드) + fixture INSERT (rawdata + predc 다수 시각) + 태그별 `(최신 acq_dtm + 1h)` 근접행 1건 반환 검증 + 윈도우 밖 미반환 검증 + fixture 날짜 상수 고정 (LocalDateTime.now() 금지) → 검증: ./gradlew.bat :api:test PASS
- [ ] `./gradlew.bat clean build` 전체 빌드 — QClass 재생성 포함 → 검증: BUILD SUCCESSFUL 출력 확인
- [ ] `./gradlew.bat :api:bootRun` 후 Swagger UI 확인 — `/api/facility/{facilityId}/prediction` 노출 + `PumpPredictionDto.predcIsRunning` description + `oprtngType` enum 노출 확인 → 검증: 브라우저 http://localhost:8080/swagger-ui/index.html 에서 06. 시설물 관리 태그 하위 /prediction 엔드포인트 + DTO 스키마 표시 확인
- [ ] `EXPLAIN (ANALYZE, BUFFERS)` 실행 — `latest_meas` CTE + LATERAL 경로 + `idx_predc_1m_h_tag_time` Index Scan + `idx_rawdata_1m_h_tag_time` 활용 + 파티션 Append 1~2개 + Subplans Removed 출력 + `SHOW enable_partition_pruning` on 확인 → 검증: psql EXPLAIN 출력에 Index Scan / Append / Subplans Removed 텍스트 모두 매칭

## 산출물
- [결과](../../../results/20260518/송수펌프제어분석-7번섹션/RESULT1.md)
