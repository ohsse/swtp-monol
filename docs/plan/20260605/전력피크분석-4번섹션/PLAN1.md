---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-4번섹션 — 시설물 펌프 전력량 예측 시계열 API

## 목적

전력피크분석 화면(`backend/image/전력피크분석.png`)의 **4번 섹션**("송수펌프단계예측 임시제어 전력량 예측", 단일 추세선 "전력예측값추세") 백엔드 API 를 구현한다.

- **기능1**: 펌프를 소유한 시설물 목록 조회 (프론트 택일용) — **기존 엔드포인트 재사용**, 신규 코드 0건
- **기능2**: 택일된 시설물이 보유한 펌프들의 **전력량 예측값**(PWQ 적산전력량 기반 kWh)을 현재시간 ~ +24시간 **시설물 합산 단일 시계열**(1시간 버킷 24개)로 표출 — `opt` 도메인 신규 자산

## 배경

- 1·2·3번 섹션(목표값 마스터 + SSE + 5지표 집계)은 완료 상태.
- 도메인 분석 완료: [ANALYZE1](../../../analyze/20260605/전력피크분석-4번섹션/ANALYZE1.md) (`status: approved`). 5인 회의 블로커 0건, 어휘 사전·DDL 변경 0건.
- 사용자 확정 결정(plan 단계 AskUserQuestion): ① 기능1 기존 API 재사용 ② PWQ 적산전력량(kWh) ③ 시설물 합산 단일 시계열 ④ 1시간 버킷 24개.

## 범위

| 모듈 | 변경 |
|------|------|
| `common` | **없음** (엔티티·DB 변경 0건, `TagPrediction`/`predc_1m_h` 재사용) |
| `api` | 신규 — opt 도메인 Service/응답 DTO/내부 record/Repository 트리플 + 기존 컨트롤러 메서드 추가 + 단위 테스트 |
| `scheduler` | 없음 |

## 도메인 모델

**변경 없음** — 신규 엔티티·테이블·컬럼·인덱스 0건. 기존 자산 조회 경로만 조합한다.

| 엔티티/테이블 | 역할 | 본 작업에서의 사용 |
|------|------|------|
| `Instrument`(`instrument_m`) / `Pump` 자식 | 계측기 다형성 | `equip_type_cd='PUMP'` + `use_yn='Y'` 활성 펌프 식별 (조회) |
| `Tag`(`tag_m`) | 측정 태그 | 펌프별 `tag_se_cd='PWQ'` + `use_yn='Y'` 적산전력량 태그 (조회) |
| `TagPrediction`(`predc_1m_h`) | AI 예측 시계열 | `tag_srl_no` IN + `predc_dtm` 범위 예측값 (`predc_val`) 버킷 차분 (조회) |
| `Facility`(`facility_m`) | 시설 마스터 | `facilityId` 활성 검증 (조회) |

> `predc_1m_h` 컬럼 구조(확인): `predc_id`(seq PK) · `predc_dtm`(PK+파티션키) · `tag_srl_no`(논리 참조) · `predc_val`(NULL 허용 BigDecimal) · `rgstr_dtm` · `rgstr_id`. **`quality_cd`·`corr_val`·`raw_val` 컬럼 부재** → 네이티브 쿼리는 `predc_val IS NOT NULL` 단독 필터(품질 필터 없음).

## DB 설계 변경

**없음** — `predc_1m_h` 재사용. `docs/ddl/`·`common/src/main/resources/db/migration/` 무변경. 인덱스 `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` 기존 활용.

## 구현 방향

### 기능1 — 펌프 소유 시설물 목록 (신규 코드 없음)

기존 엔드포인트를 프론트가 그대로 호출한다.

- **엔드포인트**: `GET /api/facility?hasPump=true`
- **구현 위치(기존)**: `FacilityController.findAllFacilities` → `FacilitySearchDto.hasPump` → `FacilityCustomRepositoryImpl` 의 `equip_type_cd='PUMP' AND use_yn='Y'` EXISTS 서브쿼리
- `/dev:spec` 단계에서 프론트 SPEC 에 "기능1은 기존 facility 목록 API 재사용" 으로 명시

### 기능2 — 시설물 펌프 전력량 예측 시계열 (신규)

#### 조회·산정 경로

```
입력 facilityId
 → Facility 활성 검증 (findById + use_yn='Y', 실패 시 FACILITY_NOT_FOUND 404)
 → instrument_m: findByFacilityFacilityId(facilityId) → equipType==PUMP && useYn==Y 필터  # 활성 펌프
 → tag_m: findByInstrumentInstrumentIdInAndUseYn(IN, Y) → tagSeCd==PWQ 필터              # 펌프별 적산전력량 태그
 → predc_1m_h: tag_srl_no IN, predc_dtm ∈ [start, start+24h]                            # 예측 시계열
 → 펌프별 date_trunc('hour', predc_dtm) 별 MAX(predc_val)-MIN(predc_val)                 # 펌프별 시간버킷 에너지 차분
 → 버킷(시각)별 펌프 합산 (음수 차분 버킷 생략 + WARN 로그)                              # 시설물 단일 시계열(kWh)
```

- 시간창: `start = date_trunc('hour', now)`, `end = start + 24h` → 24개 시간 버킷. 현재 시(時) 버킷은 부분 집계 포함(예측 차트 자연 특성, 실측 버전 동형).
- PWQ 는 적산(누적) 미터값이므로 에너지(kWh)는 버킷별 `MAX−MIN` 차분으로 산정. **펌프별 차분 후 버킷 합산**(DBA Q2 — `SUM(MAX−MIN) ≠ SUM(MAX)−SUM(MIN)`, 펌프별 baseline 상쇄 오류 방지).
- 데이터 소스만 `rawdata_1m_h`(실측) → `predc_1m_h`(예측)로 바뀌며, 예측 테이블은 `quality_cd`·`corr_val` 부재 → GOOD 필터·HLV 분기 미적용(`predc_val IS NOT NULL` 단독).
- 빈 버킷(데이터 0)은 응답에서 생략(24슬롯 null 고정 채움 아님). 펌프/태그/예측 0건은 빈 시계열(200).

#### 신규 파일 (모두 `api` 모듈)

1. **내부 전송 record** — `api/src/main/java/com/mo/swtp/opt/dto/PredcEnergyBucketDto.java`
   - `record PredcEnergyBucketDto(String tagSrlNo, LocalDateTime baseDtm, BigDecimal aggrVal)`
   - `RawDataBucketDto` 동형(Swagger 비노출, Service 내부 전송 전용). 사이클 간 자산 자동 원용 금지 정합 — opt 도메인 신규 record(`RawDataBucketDto` 직접 재사용 아님)

2. **응답 DTO** — `api/src/main/java/com/mo/swtp/opt/dto/PumpEnergyPredictionDto.java`
   - `@Getter` + 정적 팩토리 `of(...)`, Swagger `@Schema`
   - 필드: `facilityId`, `facilityNm`, `unit`("kWh"), `points: List<PumpEnergyPredictionPoint>` (`@ArraySchema`)
   - 중첩 `PumpEnergyPredictionPoint { baseDtm(버킷 시작), elcegVal(BigDecimal kWh) }` — `@JsonFormat` 초 단위 (`PumpPowerTimeSeriesDto.PumpPowerTimeSeriesPoint` 필드명 재사용 — glossary 안건 1 결론: `predc` 접두는 `predc_dtm` 표준용어 동의어 충돌이므로 금지)
   - `BaseAuditResponseDto` 미상속(합성/집계 뷰 — `PeakPowerAnalysisDto`·`PumpPowerTimeSeriesDto` 선례)

3. **Repository 트리플** — `api/src/main/java/com/mo/swtp/opt/repository/`
   - `PumpEnergyPredcRepository` — `extends JpaRepository<TagPrediction, TagPredictionId>, PumpEnergyPredcCustomRepository` (`PeakPredcRepository` 선례 동형)
   - `PumpEnergyPredcCustomRepository` — `List<PredcEnergyBucketDto> findEnergyDeltaBuckets(List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm)` (시간 단위 고정이라 unit 파라미터 없음)
   - `PumpEnergyPredcCustomRepositoryImpl` — native SQL, `RawDataCustomRepositoryImpl.findEnergyDeltaBuckets` 미러링하되 `predc_1m_h` 대상 · `quality_cd`/`raw_val` 제거 · `date_trunc('hour')` 고정 · timestamp 매핑은 `JdbcTimestamps.toLocalDateTime`:
     ```sql
     SELECT tag_srl_no,
            date_trunc('hour', predc_dtm) AS base_dtm,
            MAX(predc_val) - MIN(predc_val) AS aggr_val
     FROM predc_1m_h
     WHERE tag_srl_no IN (:tagSrlNos)
       AND predc_dtm >= :startDtm AND predc_dtm < :endDtm
       AND predc_val IS NOT NULL
     GROUP BY tag_srl_no, date_trunc('hour', predc_dtm)
     ORDER BY tag_srl_no, base_dtm
     ```
   - `predc_dtm` 범위로 월 RANGE 파티션 프루닝 강제(24h 윈도우 1~2개 파티션). 빈 입력(`tagSrlNos` empty)은 `List.of()` early return. `§2.5` 면책 인용(`query-tuning.md §2`) 주석 명기

4. **Service** — `api/src/main/java/com/mo/swtp/opt/service/PumpEnergyPredictionService.java`
   - `@Service @RequiredArgsConstructor @Transactional(readOnly = true) @Slf4j`
   - 주입: `FacilityRepository`, `InstrumentRepository`, `TagRepository`, `PumpEnergyPredcRepository`
   - 공개 메서드 `getPumpEnergyPrediction(String facilityId)` 흐름(4-SELECT, `FacilityOperatingStatusService` 동형):
     1. `findActiveFacilityOrThrow(facilityId)` — `findById` + `useYn==Y` 검증, 실패 시 `FacilityErrorCode.FACILITY_NOT_FOUND`(404)
     2. 활성 PUMP 조회 — `findByFacilityFacilityId` + `equipType==PUMP && useYn==Y` 필터 헬퍼. 0대 → 빈 시계열 DTO(200)
     3. PWQ 활성 태그 조회 — `findByInstrumentInstrumentIdInAndUseYn` + `tagSeCd==PWQ` 필터 헬퍼(`loadPwqTagSrlNos`, 펌프당 다건 시 첫 태그). 0개 → 빈 시계열 DTO(200)
     4. `findEnergyDeltaBuckets(tagSrlNos, start, end)` 호출
     5. 버킷(`baseDtm`)별 펌프 합산 — `aggrVal <= 0`(음수 차분) 버킷 제외 + WARN 로그, `baseDtm` 오름차순 단일 시계열로 합산(`Map<LocalDateTime, BigDecimal>` reduce → 정적 팩토리)
   - 시간창 헬퍼: `now = LocalDateTime.now()` 1회 호출, `start = now.truncatedTo(ChronoUnit.HOURS)`, `end = start.plusHours(24)`
   - private 헬퍼 분리(`findActiveFacilityOrThrow`·`loadActivePumpIds`·`loadPwqTagSrlNos`·`aggregateByBucket`·`filterValidDelta`)로 공개 메서드 본문 50줄 이내 + 추상화 3단 이하(`coding-discipline.md §2.1`)

5. **컨트롤러 메서드 추가(기존 파일 수정)** — `api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java`
   - 기존 `PeakPowerAnalysisController`(`@Tag "14. 전력피크 분석"`, `@RequestMapping "/api/opt/peak-power-analysis"`)에 GET 메서드 추가(신규 컨트롤러 분리 아님 — backend-engineer 권고: 동일 화면·동일 Tag·동일 경로 prefix)
   - `@GetMapping("/pump-energy-prediction")` + `@RequestParam String facilityId` → `ResponseEntity<CommonResponseDto<PumpEnergyPredictionDto>>`, `getResponseEntity(...)` 래핑
   - `PumpEnergyPredictionService` 주입 추가. Swagger `@Operation`/`@ApiResponses`(200/400/401/404/500), "현재 시 버킷 부분 집계" 명기
   - 클래스 javadoc "2·3번섹션" → "2·3·4번섹션" 확장

#### 재사용 (변경 없음)

- `InstrumentRepository.findByFacilityFacilityId`(`:57`), `TagRepository.findByInstrumentInstrumentIdInAndUseYn`
- `FacilityRepository.findById`, `FacilityErrorCode.FACILITY_NOT_FOUND`(404) 교차 참조(backend-engineer 허용 — `exception-patterns.md` 교차 참조 금지 조항 없음)
- `TagPrediction` 엔티티 / `predc_1m_h` / `TagPredictionId` / `JdbcTimestamps`

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 다펌프 시간버킷 합산 정상 | `PumpEnergyPredictionServiceTest` 다펌프 정상 케이스 — 2펌프 서로 다른 적산 baseline mock → 시각별 합산값이 펌프별 `MAX−MIN` 합과 일치 GREEN (`./gradlew.bat :api:test --tests *PumpEnergyPredictionServiceTest`) |
| 시설 미존재/비활성 404 | `PumpEnergyPredictionServiceTest` — `findById` empty / `useYn=N` mock → `RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND)` assert GREEN |
| 펌프 0대 빈 시계열 | `PumpEnergyPredictionServiceTest` — 펌프 0대 mock → `points` empty + 200(예외 미발생) GREEN |
| PWQ 태그 0개 빈 시계열 | `PumpEnergyPredictionServiceTest` — PWQ 태그 0개 mock → `points` empty GREEN |
| 음수 차분 버킷 제외 | `PumpEnergyPredictionServiceTest` — `aggrVal<0`(적산 리셋) 버킷 포함 mock → 해당 버킷 응답 생략 검증 GREEN |
| 예측 0행 빈 시계열 | `PumpEnergyPredictionServiceTest` — `findEnergyDeltaBuckets` empty mock → `points` empty GREEN |
| 전체 빌드·회귀 통과 | `./gradlew.bat :api:test` BUILD SUCCESSFUL, 기존 테스트 회귀 0건 |
| Swagger 노출 | `:api:bootRun` 기동 후 `/swagger-ui` "14. 전력피크 분석" 에 `GET /api/opt/peak-power-analysis/pump-energy-prediction` 노출·`PumpEnergyPredictionDto` 스키마 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 측정 단위는 PWQ 적산전력량 기반 kWh (PWI 아님) | 결정 | plan 단계 AskUserQuestion 사용자 확정 + domain-expert 타당 판정 |
| 현재 시(時) 버킷은 부분 집계로 포함 — 윈도우 `[truncatedTo(HOURS, now), +24h)` 24버킷 | 결정 | DBA Q4 위임 → 부분 집계 포함 채택. Swagger description 에 "현재 시 버킷 부분 집계" 명기 |
| 빈 버킷(데이터 0)은 응답 생략 (24슬롯 null 고정 아님) | 결정 | `PumpPowerTimeSeriesDto` 선례 동형 |
| 컨트롤러는 기존 `PeakPowerAnalysisController` 메서드 추가 (신규 분리 아님) | 결정 | backend-engineer 권고. javadoc "2·3번섹션"→"2·3·4번섹션" |
| 시설 미존재/비활성 시 `FacilityErrorCode.FACILITY_NOT_FOUND`(404) 교차 참조 | 결정 | backend-engineer 허용 판정. `OptErrorCode` 신규 추가 안 함(동의어 enum 회피) |
| 펌프당 PWQ 태그 다건 시 첫 태그 사용 | 가정 | `PumpPowerTimeSeriesService.loadPwqTagByInstrument` 동형(toMap merge 첫 태그). 운영상 펌프당 PWQ 1건 전제 |
| 활성 펌프는 `findByFacilityFacilityId` + 서비스단 PUMP·useYn 필터 (신규 repo 메서드 없이) | 결정 | 기존 메서드 재사용으로 repo 표면 최소화. `findByFacilityIdAndEquipType`(useYn 미필터)는 활성 필터 부재로 미채택 |
| 음수 차분(적산 리셋/롤오버) 버킷은 Service 생략 + WARN 로그 | 결정 | DBA Q3 + domain-expert — 읽기 API 라 알람 의무 없음, 모델 품질 진단 근거 로그만 |

## 제외 사항

- AI 예측 INSERT 파이프라인(`predc_1m_h` 적재) — 본 작업은 조회 전용. AI 추론 클라이언트는 별도 사이클(`ot-integration.md §6` 보류)
- 기능1 신규 구현 — 기존 `GET /api/facility?hasPump=true` 재사용
- 시설 종류(PWTF/DWT/RSV/...) 제한 — 펌프 0대 시 빈 시계열로 자연 처리(타입 거부 분기 없음)
- SSE 실시간 전파 — 4번 섹션은 GET 폴링(1번섹션 SSE 와 의미 분리, 2·3번섹션 동일 정책)
- DDL·어휘 사전 갱신 — 0건 (ANALYZE1 룰 갱신 지시서 0건)

## 부록: 도메인/DB 검토 결과

`## 도메인 모델` 변경 없음 + `## DB 설계 변경` 없음 → PLAN 단계 검토 게이트 생략(스킬 §도메인·DB 검토 게이트). 신규 `predc_1m_h` 네이티브 쿼리 성능·PWQ 차분 도메인 규칙은 ANALYZE1 5인 회의에서 `wtp-dba-reviewer`(안건 3)·`wtp-domain-expert`(안건 4) 가 이미 블로커 0건 통과.

## 예상 산출물

- [태스크](../../../tasks/20260605/전력피크분석-4번섹션/TASK1.md)
