---
status: approved
created: 2026-05-18
updated: 2026-05-20
---
# 송수펌프제어분석 — 7번 섹션 (시설 예측 데이터 표출)

## 목적

이미지 `swtp/backend/image/송수펌프제어분석.png` 의 **7번 섹션**(우측 "분석 결과") 구현. 1번 섹션에서 선택한 시설의 **AI 예측 데이터**를 3번 섹션(현황)과 동일한 형태·이벤트 흐름으로 표출한다. 예측 시계열 태그 예측값 테이블(`predc_1m_h`) 스키마 신설 + 섹션 3 미러링 조회 API 까지가 범위이며, AI 추론 파이프라인(예측값 INSERT)은 본 사이클 제외.

## 배경

- ANALYZE 게이트 기준 문서: [ANALYZE2](../../../analyze/20260518/송수펌프제어분석-7번섹션/ANALYZE2.md) (status approved — ANALYZE1 전체 결정 승계 + §5b 게이트 통과 근거 확립). 1차 분석: [ANALYZE1](../../../analyze/20260518/송수펌프제어분석-7번섹션/ANALYZE1.md) (status approved, 룰 갱신 6건 전부 적용 완료).
- §5b "도메인 룰 4영역 점검" 자율 차단은 ANALYZE2 의 `## §5b 게이트 통과 근거` (사용자 결정 + wtp-domain-expert 테이블 관점 4구조근거 직접 인증 + 게이트 설계 의도 충족) 로 투명 해소됨 — 본 PLAN 은 ANALYZE2 를 게이트 기준 문서로 진행.
- 섹션 3 은 이미 구현됨 (`FacilityStateService`·`GET /api/facility/{facilityId}/state`·`RawDataCustomRepositoryImpl` DISTINCT ON). 7번 = 같은 시설 예측 데이터 — **섹션 3 보존 + 섹션 7 신규 병렬** (메모리 `feedback_section_cycle_discard_policy` 공존 섹션 예외 정합 — 예측은 현황과 다른 관심사 공존).

## 범위

### 포함
- 예측 시계열 테이블 `predc_1m_h` DDL (`V9_3`) + JPA 엔티티 + 복합키
- 태그별 근접매칭 CustomRepository (`latest_meas` CTE + `CROSS JOIN LATERAL`)
- `FacilityPredictionService` (섹션 3 미러 별도 클래스, 4-step)
- `FacilityController` 에 `GET /api/facility/{facilityId}/prediction` 엔드포인트 추가
- 응답 DTO 3종 (`FacilityPredictionDto`·`FlwmtrPredictionDto`·`PumpPredictionDto`)
- 단위 테스트 + 근접매칭 Repository 통합 테스트

### 제외 사항
- AI 추론 파이프라인 (Python) — `predc_1m_h` INSERT 경로 본 사이클 미구현 (조회 전용)
- 예측 신뢰도/유효성 컬럼 — `quality_cd` 제외 결정(ANALYZE1 안건 5), 사이클 2 결정 대기
- 섹션 3 전용 자산(`FacilityStateService`·`RawDataCustomRepository`·State DTO 3종) 수정 — 무수정 병렬
- 활성 태그 N=500 운영 SLA 캐시 — 본 사이클 N=100 fixture 측정 한정

## 구현 방향

### 1. 예측 시계열 테이블 — `predc_1m_h` (신규)

`rawdata_1m_h` 구조 미러링하되 `quality_cd` 제외 + immutable 이력 예외(`updt_*` 데드 컬럼 회피).

DDL 위치: `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` (현 최신 `V9_2` 다음).

```sql
CREATE TABLE predc_1m_h (
    predc_id    BIGINT       NOT NULL,   -- DOM_SEQ_BIGINT, PK 1/2
    predc_dtm   TIMESTAMP    NOT NULL,   -- DOM_DTM, PK 2/2, 예측 대상 시각, 월 RANGE 파티션 키
    tag_srl_no  VARCHAR(50)  NOT NULL,   -- DOM_TAG_SRL_NO_50, tag_m 논리 참조 (시계열 → 마스터 FK 금지)
    predc_val   NUMERIC(15,4),           -- DOM_QTY_15_4, 예측값 (NULL 허용)
    rgstr_dtm   TIMESTAMP    NOT NULL,   -- immutable 이력 예외 (indexing-and-migration.md §4.3)
    rgstr_id    VARCHAR(50)  NOT NULL,   -- immutable 이력 예외 — INSERT-only
    PRIMARY KEY (predc_id, predc_dtm)
) PARTITION BY RANGE (predc_dtm);

CREATE SEQUENCE seq_predc_id;            -- allocationSize=100 (엔티티 @SequenceGenerator)

-- 복합 B-Tree: 근접 대칭 범위 스캔 — predc_dtm ASC 강제 (ANALYZE1 안건 3 블로커)
CREATE INDEX idx_predc_1m_h_tag_time ON predc_1m_h (tag_srl_no, predc_dtm);

-- 월별 파티션 최소 6개월 선행 (현재 2026-05 → 202605 ~ 202611 이상)
CREATE TABLE predc_1m_h_202605 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
-- ... 202606 ~ 202611 (TASK 에서 6개월분 명시)
```

- **인덱스 ASC 강제**: 근접매칭은 `predc_dtm BETWEEN target±W` 후 `ORDER BY ABS(predc_dtm - target) LIMIT 1` 의 **대칭 범위 스캔**. `rawdata` 의 `(tag_srl_no, acq_dtm DESC)`("최신 1건" 단방향) 선례와 목적 상이 — DESC 시 forward scan 포기 (ANALYZE1 안건 3 블로커).
- **BRIN(`predc_dtm`) 생략** (ANALYZE1 미해결 #6 결정): 복합 B-Tree 후위 컬럼과 역할 중복, `predc_dtm` 단독 대용량 범위 시나리오(파티션 스케줄러 외) 없음 → 미생성.
- **immutable 이력 예외**: `BaseEntity` 미상속, `rgstr_dtm`·`rgstr_id` 만 (`@CreatedDate`·`@CreatedBy` + `@EntityListeners(AuditingEntityListener.class)`), `updt_*` 미정의 (`indexing-and-migration.md §4.3`).
- **시계열 → 마스터 FK 금지**: `tag_srl_no` 논리 참조만.
- **COMMENT ON COLUMN 의무**: `check-ddl-column-comment.sh` 훅 대상(`db/migration/*.sql`) — 6개 컬럼 전부 COMMENT. `rgstr_dtm`·`rgstr_id` 는 immutable 이력 표준 라벨(`indexing-and-migration.md §4.3` 예외 라벨).
- **보존 정책**: `partitioning-and-retention.md §2` 예측 시계열 3년/파티션 DROP (ANALYZE1 룰 갱신 적용 완료).

### 2. 근접매칭 CustomRepository (신규, `com.mo.swtp.opt`)

`api/src/main/java/com/mo/swtp/opt/repository/TagPredictionCustomRepository(Impl).java`. 섹션 3 의 단순 DISTINCT ON 보다 복잡(태그별 기준시각 산정 + 근접 매칭) — `latest_meas` CTE 가 `rawdata_1m_h` 를 **직접 참조**하여 섹션 7 자체 완결(섹션 3 자산 무참조).

```java
// §2.5 면책 (query-tuning.md §2) — latest_meas CTE + CROSS JOIN LATERAL 근접매칭 단일 흐름 보존
public List<TagPredictionMatchDto> findNearestByTagSrlNos(List<String> tagSrlNos, int windowMinutes)
```

```sql
WITH latest_meas AS (
  SELECT DISTINCT ON (tag_srl_no) tag_srl_no, acq_dtm
  FROM rawdata_1m_h
  WHERE tag_srl_no IN (:tagSrlNos) AND acq_dtm >= NOW() - INTERVAL '1 hour'
  ORDER BY tag_srl_no, acq_dtm DESC
)
SELECT lm.tag_srl_no, p.predc_dtm, p.predc_val
FROM latest_meas lm
CROSS JOIN LATERAL (
  SELECT predc_dtm, predc_val
  FROM predc_1m_h
  WHERE tag_srl_no = lm.tag_srl_no
    AND predc_dtm BETWEEN (lm.acq_dtm + INTERVAL '1 hour') - (:windowMinutes * INTERVAL '1 minute')
                      AND (lm.acq_dtm + INTERVAL '1 hour') + (:windowMinutes * INTERVAL '1 minute')
  ORDER BY ABS(EXTRACT(EPOCH FROM (predc_dtm - (lm.acq_dtm + INTERVAL '1 hour'))))
  LIMIT 1
) p
```

- `EntityManager.createNativeQuery` + 인터페이스 기본 readOnly. `// §2.5 면책 (query-tuning.md §2)` 주석 의무(누락 시 REVIEW 블로커 — ANALYZE1 안건 4).
- 근접 윈도우 = `windowMinutes` 파라미터(하드코딩 금지). 설정 키 `opt.prediction.match-window-minutes` 기본값 **5** — `predc_1m_h` 명칭("1m" = 1분 그리드 정합, rawdata 1분 그리드 미러)에 따라 dba 권고 "1분 그리드 가정 ±5분" 채택(ANALYZE1 미해결 #1 결정). 윈도우 과대 시 파티션 경계 교차 + 잘못된 시간대 매칭 위험 — `application-common.yml` 관리.
- `enable_partition_pruning=on` execution-time pruning 확인 의무(ANALYZE1 안건 3).
- 윈도우 내 예측행 결측 → LATERAL 미반환(해당 태그 행 없음) → Service 가 null 매핑(ANALYZE1 미해결 #2 결정 — 200 + 필드 null).
- **인덱스 방향 혼재 의도 명기**(dba 참고): `predc_1m_h` 인덱스는 ASC(근접 대칭 범위 스캔), `rawdata_1m_h.idx_rawdata_1m_h_tag_time` 은 DESC(`latest_meas` DISTINCT ON 최신 1건 forward scan) — 목적 상이로 의도된 혼재. `TagPredictionCustomRepositoryImpl` 쿼리 주석에 명기.
- **LATERAL 동작 특성**(dba 참고): `CROSS JOIN LATERAL` 은 `latest_meas` IN 절 일괄 조회 후 태그별 1회 인덱스 스캔(N=태그수) — JPA 루프 N+1 과 구별되는 정상 동작. 성공 기준 1 EXPLAIN 에 Nested Loop/Hash Join 선택 함께 기록.
- `TagPredictionRepository extends JpaRepository<TagPrediction, TagPredictionId>, TagPredictionCustomRepository` — fixture/테스트 INSERT 용 기본 Repository 동반.

### 3. `FacilityPredictionService` (신규, 섹션 3 미러 별도 클래스)

`api/src/main/java/com/mo/swtp/facility/service/FacilityPredictionService.java`. `FacilityStateService` 와 동일 4-step 골격이나 **별도 클래스 독립 유지**(공통 추상화는 `coding-discipline §2` 위반 — ANALYZE1 안건 4).

1. `FacilityRepository.findById` + `use_yn=Y` 검증 → 미존재/비활성 `FacilityErrorCode.FACILITY_NOT_FOUND`. `findActiveFacilityOrThrow` 는 본 클래스 private 재구현(섹션 3 private 헬퍼 재사용 금지 — ANALYZE1 안건 4 크로스사이클 경계).
2. `InstrumentRepository.findByFacilityIdAndEquipType(facilityId, PREDICTION_EQUIP_TYPES)` (공유 메서드).
3. `TagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y)` + `PREDICTION_TAG_TYPES` 필터(공유 메서드).
4. `TagPredictionCustomRepository.findNearestByTagSrlNos(tagSrlNos, windowMinutes)` → `Map<String, TagPredictionMatchDto>` 그룹화 → DTO 매핑.

- 섹션 7 전용 상수 **별도 선언**(섹션 3 `STATE_*` 공유 금지 — `feedback_no_auto_reuse_cross_cycle`):
  - `PREDICTION_EQUIP_TYPES = List.of(EquipType.PUMP, EquipType.FLWMTR)`
  - `PREDICTION_TAG_TYPES = EnumSet.of(TagMeasurementType.FRI, TagMeasurementType.PRI, TagMeasurementType.OPS)`
- `@Service`·`@RequiredArgsConstructor`·`@Transactional(readOnly = true)`. 본문 50줄 초과 시 `mapFlwmtrPrediction`·`mapPumpPrediction` private 분해(섹션 3 선례).

### 4. `FacilityController` 엔드포인트 추가

기존 `com.mo.swtp.facility.web.FacilityController` 에 메서드 추가(단일 진입점 — 신규 컨트롤러 미생성).

```java
@Operation(summary = "시설 예측 데이터 조회",
    description = "송수펌프제어분석 7번 섹션 — 활성 시설의 유량계(FLWMTR) FRI/PRI 예측값 + "
        + "펌프(PUMP) OPS 예측 가동상태를 각 태그 현황 최신 계측시각+1시간 근접 예측행으로 반환한다.")
@ApiResponses({ /* 200/400/401/403/404/500 — /state 동일 */ })
@GetMapping("/{facilityId}/prediction")
public ResponseEntity<CommonResponseDto<FacilityPredictionDto>> findFacilityPrediction(
    @PathVariable String facilityId) {
  return getResponseEntity(facilityPredictionService.findFacilityPrediction(facilityId));
}
```

`@Tag(name = "06. 시설물 관리")` 기존 정합. `/prediction` 단수형 = `/state` 선례.

### 5. 응답 DTO 3종 (신규 병렬, `com.mo.swtp.facility.dto`)

`@Getter` + private 생성자 + 정적 팩토리 `of(...)`(섹션 3 State DTO 패턴 동형). `BaseAuditResponseDto` 미적용(실시간/예측 통지성 — `api-patterns.md §BaseAuditResponseDto 적용 범위`). `quality*` 필드 없음(`quality_cd` 제외).

| 클래스 | 필드 | 비고 |
|--------|------|------|
| `FacilityPredictionDto` | `facilityId`·`facilityNm`·`List<FlwmtrPredictionDto> flwmtrs`·`List<PumpPredictionDto> pumps` | `@ArraySchema(schema=@Schema(implementation=...))` |
| `FlwmtrPredictionDto` | `instrumentId`·`instrumentNm`·`flwrtPredcVal`(BigDecimal)·`flwrtPredcDtm`(LocalDateTime)·`prsrPredcVal`·`prsrPredcDtm` | `*PredcDtm` `@JsonFormat(shape=STRING, pattern="yyyy-MM-dd HH:mm:ss")`. raw/corr 분리 없음(예측 단일값) |
| `PumpPredictionDto` | `instrumentId`·`instrumentNm`·`oprtngType`(PumpOprtngType)·`predcIsRunning`(Boolean)·`predcDtm`(LocalDateTime) | `oprtngType` `@Schema(implementation=PumpOprtngType.class, description="펌프 물리 조작 가능 유형 (AI 운전 모드와 무관)")`. `predcIsRunning` `@Schema(description="예측 가동상태(미래 시점) — true=ON·false=OFF·null=결측")`(Boolean 타입 — 부동소수 코드값 오인 방지, domain-expert 참고). `predcDtm` `@JsonFormat` |

- 예측 OPS 필드명 `predcIsRunning` 확정(ANALYZE1 가정 #4 — 섹션 3 `isRunning` 미러 + `predc` 접두어 강제). 현황 DTO(`PumpStateDto`) 재사용 금지 — 별도 DTO(ANALYZE1 안건 5 블로커 해소).
- 내부 전송 DTO: `com.mo.swtp.opt.dto.TagPredictionMatchDto`(`tagSrlNo`·`predcDtm`·`predcVal`) — CustomRepository 반환, Service 가 `Map<String,_>` 그룹화.
- 예측 엔티티: `com.mo.swtp.opt.domain.TagPrediction` + `TagPredictionId`(복합키 `predcId`+`predcDtm`) 확정(ANALYZE1 가정 #5). `@SequenceGenerator(name=..., sequenceName="seq_predc_id", allocationSize=100)` + `@GeneratedValue(GenerationType.SEQUENCE)`.

## 도메인 모델

| 엔티티/테이블 | 역할 | 주요 필드 |
|------|------|---------|
| `TagPrediction` / `predc_1m_h` (`com.mo.swtp.opt.domain`, 신규) | 예측 시계열 태그 예측값 (immutable 이력 예외, INSERT-only) | `predcId`(BIGINT SEQUENCE PK1)·`predcDtm`(TIMESTAMP PK2/파티션키)·`tagSrlNo`(VARCHAR50 논리참조)·`predcVal`(NUMERIC15,4 NULL)·`rgstrDtm`·`rgstrId` |
| `TagPredictionId` (`com.mo.swtp.opt.domain`, 신규) | `@Embeddable` 복합키 | `predcId`·`predcDtm` |
| `FacilityPredictionDto` (`com.mo.swtp.facility.dto`, 신규) | 시설 단위 예측 응답 | `facilityId`·`facilityNm`·`List<FlwmtrPredictionDto>`·`List<PumpPredictionDto>` |
| `FlwmtrPredictionDto` (신규) | 유량계 FRI/PRI 예측 | `flwrtPredcVal`·`flwrtPredcDtm`·`prsrPredcVal`·`prsrPredcDtm` |
| `PumpPredictionDto` (신규) | 펌프 OPS 예측 가동상태 | `oprtngType`(정적)·`predcIsRunning`·`predcDtm` |
| `TagPredictionMatchDto` (`com.mo.swtp.opt.dto`, 신규) | Repository→Service 내부 전송 | `tagSrlNo`·`predcDtm`·`predcVal` |

엔티티 표준 용어 전부 ANALYZE1 적용 완료 (`predc_id`·`predc_dtm` 재등록·`predc_val` 신규·`val` 단어). 섹션 3 전용 자산 무수정.

## DB 설계 변경

- **신규 테이블** `predc_1m_h` — 월 RANGE 파티션(`predc_dtm`), 6개월 선행 생성. PK `(predc_id, predc_dtm)`. `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` ASC. BRIN 미생성. `seq_predc_id` SEQUENCE allocationSize=100. 시계열 → 마스터 FK 금지(`tag_srl_no` 논리 참조). COMMENT ON COLUMN 6컬럼 전부(`check-ddl-column-comment.sh` 훅).
- **무중단**: 신규 테이블 — 기존 테이블 ALTER 없음, 락 영향 없음. DDL `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql`.
- **immutable 이력 예외**: BaseEntity4 미적용, `rgstr_dtm`·`rgstr_id` 만(`indexing-and-migration.md §4.3`). `updt_*` 데드 컬럼 회피.
- **보존**: 예측 시계열 3년/파티션 DROP — `partitioning-and-retention.md §2` 행 이미 추가(ANALYZE1 룰 갱신 적용 완료). 추가 룰 변경 없음.
- 참조: [`db/partitioning-and-retention.md §1·§2`](../../../../.claude/rules/db/partitioning-and-retention.md) · [`db/indexing-and-migration.md §1·§4.3`](../../../../.claude/rules/db/indexing-and-migration.md) · [`db/query-tuning.md §2`](../../../../.claude/rules/db/query-tuning.md).

## 성공 기준 (검증 가능 형태)

1. **근접매칭 쿼리 인덱스·파티션 프루닝** — 로컬 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` 결과: (1) `predc_1m_h` 에 `Index Scan using idx_predc_1m_h_tag_time`, (2) `latest_meas` CTE 가 `idx_rawdata_1m_h_tag_time` 활용, (3) 예측 파티션 `Append` 하위 1~2개 + `Subplans Removed` 출력 — **윈도우가 월말 경계 교차 시 2 파티션 활성까지 통과 허용**(dba 권고: ±5분 경계 교차는 `partitioning-and-retention.md §1` "1~2 파티션 제한" 부합), (4) `SHOW enable_partition_pruning` = on 확인, (5) LATERAL join 전략(Nested Loop/Hash Join) 기록.
2. **N=100 fixture 응답 200ms 이내** — `./gradlew.bat :api:test` 통합 테스트에서 예측+rawdata fixture INSERT 후 `FacilityPredictionService.findFacilityPrediction(facilityId)` 응답 시간 측정 200ms 미만 출력 확인.
3. **근접매칭 정확/근접/윈도우밖 케이스** — `./gradlew.bat :api:test` 단위 테스트(`FacilityPredictionServiceTest`): `TagPredictionCustomRepository` mock 으로 정확 일치·근접(±윈도우 내)·윈도우 밖(미반환→null) 행 주입 시 응답 DTO 매핑 PASS.
4. **시설 미존재/비활성 예외** — `./gradlew.bat :api:test`: 미존재/비활성 시설 ID → `RestApiException` + `FacilityErrorCode.FACILITY_NOT_FOUND` 검증 PASS.
5. **예측 OPS Swagger 노출** — `./gradlew.bat :api:bootRun` 후 Swagger UI 에서 `PumpPredictionDto.predcIsRunning` description "예측 가동상태(미래 시점)" + `oprtngType` `PumpOprtngType` enum 노출 확인.
6. **신규 엔드포인트 응답 계약** — `curl GET /api/facility/{facilityId}/prediction` 응답 JSON `code` == `"SUCCESS"` + `data.facilityId` 요청값 일치 출력 확인.
7. **전체 빌드** — `./gradlew.bat clean build` BUILD SUCCESSFUL 출력 확인(QClass 재생성 포함).

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE1/2 가정·미해결을 PLAN 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 근접매칭 윈도우 크기 (예측 그리드 단위 미상) | 미해결 → 결정 | `opt.prediction.match-window-minutes` 설정 키, 기본값 **5분** (`predc_1m_h` "1m"=1분 그리드 정합, dba "1분 가정 ±5분"). 하드코딩 금지, `application-common.yml` 관리 |
| 윈도우 내 예측행 결측 응답 | 결정 | 200 + 해당 필드 null (섹션 3 철학, 사용자 결정). LATERAL 미반환 → Service null 매핑 |
| 예측 OPS 필드명 | 가정 → 결정 | `predcIsRunning` (섹션 3 `isRunning` 미러 + `predc` 접두어). `@Schema` "예측 가동상태(미래 시점)" + 별도 DTO |
| 예측 엔티티 클래스명·복합키명 | 가정 → 결정 | `com.mo.swtp.opt.domain.TagPrediction` / `TagPredictionId` |
| BRIN(`predc_dtm`) 인덱스 | 미해결 → 결정(조건부) | 본 사이클 **미생성** — INSERT 경로 미구현(조회 전용), `predc_dtm` 단독 대용량 범위 시나리오 없음. dba 정정 수용: "복합 B-Tree 역할 중복" 근거는 부정확(BRIN=물리 블록 범위 추정, 목적 상이). **AI 추론 파이프라인 도입(1분 그리드 INSERT→물리 순서 정합) 시 BRIN 추가를 별도 검토** — 사이클 2/AI 파이프라인 ANALYZE 의 명시 미해결 항목으로 인계 |
| 예측 신뢰도/유효성 컬럼 | 미해결 | 본 사이클 미포함 — `quality_cd` 제외(ANALYZE1 안건 5). 사이클 2(AI 추론 재설계) 결정 대기 |
| 활성 태그 N=500 SLA 200ms 미달 가능성 | 미해결 | 본 사이클 N=100 fixture 측정 한정. 운영 측정 후 캐시는 별도 사이클 |
| 사이클 2 도래 시 `predc_1m_h` AI 운전 모드 결합 재평가 | 미해결 | 본 사이클 §5 보류로 4영역 비해당(ANALYZE2 인증). 사이클 2 `ai_drvn_mod_p` 재도입 시 예측→AI 자동 운전 입력 경로 4영역 재평가(별도 ANALYZE) |
| 신규 테이블 4영역 무접촉 | 결정 | ANALYZE2 wtp-domain-expert 테이블 관점 4구조근거(§5 보류·`quality_cd` 제외·INSERT 경로 부재·감사 이력 의미 분리) 인증 완료 |

## 제외 사항

- AI 추론 파이프라인(Python) — `predc_1m_h` INSERT 경로 미구현(조회 전용)
- 예측 신뢰도/`quality_cd` — 사이클 2 결정 대기
- 섹션 3 전용 자산 수정 — 무수정 병렬
- N=500 운영 SLA 캐시 — 별도 사이클

## 테스트 전략

- `./gradlew.bat :api:test` — `FacilityPredictionServiceTest`(Mockito 단위, `@ExtendWith(MockitoExtension.class)`): 시설 미존재/비활성 → `FACILITY_NOT_FOUND`; FRI/PRI/OPS 예측 분리 매핑; 근접매칭 정확/근접/윈도우밖(null) 케이스; `predcIsRunning` Boolean 변환(1.0/0.0/null).
- `./gradlew.bat :api:test` — `TagPredictionCustomRepository` 통합 테스트(`@SpringBootTest(webEnvironment=NONE)`+`@ActiveProfiles("test")`+`@Transactional`): 로컬 PostgreSQL `predc_1m_h`/`rawdata_1m_h` 월 파티션 선행 생성 전제, 다수 시각 예측 fixture INSERT 후 태그별 `(최신 acq_dtm + 1h)` 근접행 1건 반환 + 윈도우 밖 미반환 검증. 파티션 미생성 시 INSERT 실패(`partitioning-and-retention.md §1`) — fixture 상수 날짜 고정(`LocalDateTime.now()` 금지, `test-strategy-e2e-roadmap.md §5`).
- `./gradlew.bat clean build` — 전체 빌드 + QClass 재생성.
- 영향 모듈: `common`(엔티티·DDL), `api`(Service·Controller·Repository·DTO). `scheduler` 무영향.

## 부록: 도메인/DB 검토 결과

- **wtp-domain-expert** (도메인 모델 신규 — 호출): 블로커 0건, 권고 0건, 참고 1건. 4영역 무접촉 ANALYZE2 인증과 정합, `quality_cd` 제외 전 계층 일관, 섹션 7 전용 상수 분리 정합. 참고 1건(`predcIsRunning` `@Schema` `1.0/0.0` → `true/false` 표기 정렬) → 본 PLAN 반영 완료.
- **wtp-dba-reviewer** (DB 설계 변경 — 호출): 블로커 0건, 권고 2건, 참고 2건. 파티션·인덱스 순서·immutable 이력 예외·SEQUENCE·무중단 전부 정합. 권고 2건(BRIN 미생성 근거 정정→AI 파이프라인 도입 시 재검토 미해결 인계 / 파티션 경계 교차 2파티션 허용 성공기준 명시) + 참고 2건(인덱스 방향 혼재 의도 주석 / LATERAL join 전략 기록) → 본 PLAN 전부 반영 완료.

블로커 0건 — 구현 진행 가능.

## 예상 산출물

- `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` (신규)
- `common/src/main/java/com/mo/swtp/opt/domain/TagPrediction.java` (신규)
- `common/src/main/java/com/mo/swtp/opt/domain/TagPredictionId.java` (신규)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionRepository.java` (신규)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionCustomRepository.java` (신규)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionCustomRepositoryImpl.java` (신규)
- `api/src/main/java/com/mo/swtp/opt/dto/TagPredictionMatchDto.java` (신규)
- `api/src/main/java/com/mo/swtp/facility/service/FacilityPredictionService.java` (신규)
- `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (엔드포인트 추가)
- `api/src/main/java/com/mo/swtp/facility/dto/FacilityPredictionDto.java` (신규)
- `api/src/main/java/com/mo/swtp/facility/dto/FlwmtrPredictionDto.java` (신규)
- `api/src/main/java/com/mo/swtp/facility/dto/PumpPredictionDto.java` (신규)
- `api/src/main/resources/application-common.yml` (`opt.prediction.match-window-minutes` 추가)
- `api/src/test/java/com/mo/swtp/facility/service/FacilityPredictionServiceTest.java` (신규)
- `api/src/test/java/com/mo/swtp/opt/repository/TagPredictionCustomRepositoryTest.java` (신규)
- [태스크](../../../tasks/20260518/송수펌프제어분석-7번섹션/TASK1.md)
