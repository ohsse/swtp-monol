---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# 송수펌프제어_운전현황분석 — 신규 도입 (FR-PMP-002)

## 목적

스마트정수장 백엔드에 송수펌프제어 > 운전현황 분석 화면 (FR-PMP-002) 의 조회·다운로드 API 를 신규 도입한다. [ANALYZE1](../../../analyze/20260507/송수펌프제어_운전현황분석/ANALYZE1.md) (approved) 의 도메인 모델 결정 — 신규 영속화 엔티티 2개 (`drvn_anls_dwld_h` + facility 자식 'POINT') + 응답 DTO 4종 — 을 backend 코드·DDL·엔드포인트로 변환한다. AI 운전모드 변경 (명세서 6.2)·EPANET 분석 이력 다운로드 (명세서 6.7) 는 별도 사이클로 분리.

## 배경

- ANALYZE: [ANALYZE1](../../../analyze/20260507/송수펌프제어_운전현황분석/ANALYZE1.md) (approved, 2026-05-08 사용자 승인)
- AGENDA: [AGENDA](../../../analyze/20260506/송수펌프제어_운전현황분석/AGENDA.md)
- 명세서: `docs/analyze/20260506/송수펌프제어_운전현황분석/02.운전현황 분석 요구사항 명세서_v0.2.docx`
- 의존 작업:
  - [마스터도메인설계 PLAN2](../../../plan/20260506/마스터도메인설계/PLAN2.md) (approved) — `facility_m`·`instrument_m`·`tag_m` 신 구조 사용
  - [pumpcontrol ANALYZE1](../../../analyze/20260422/pumpcontrol/ANALYZE1.md) — `ai_drvn_mod_p`·`pump_predc_h`·`pump_ctrl_h` 사용
  - [권한메뉴 PLAN2](../../../plan/20260504/권한메뉴/PLAN2.md) (approved) — `UserRole` enum (ADMIN/USER) 사용

### PLAN 단계 사용자 결정 (2026-05-08)

- ANALYZE status `review → approved` 전환 후 PLAN1 작성 진행
- 다운로드 라이브러리 = **Apache POI 단독** (XLSX 표준 + 자체 CSV writer)
- `drvn_anls_dwld_h` 파티션 = **월 RANGE 파티셔닝** ([`partitioning-and-retention.md §1·§2`](../../../../.claude/rules/db/partitioning-and-retention.md))

## 범위

### 포함

- **신규 영속화 엔티티 2개**:
  - `DrvnAnlsDwldHistory` (`drvn_anls_dwld_h`) — 다운로드 감사 시계열
  - `SensorPoint` (`point_m`) — facility 자식 'POINT' (관로 계측 분기점)
- **응답 DTO 4종 (영속화 0)**: `DrvnSttsInqDto`·`DrvnAnlsRsltDto`·`PumpOprtngDto`·`BranchMeasPredcDto`
- **REST API 엔드포인트 4건**: 운전현황 통합 조회·펌프 가동·분기 지점·다운로드
- **DDL 2개 파일 신규**: `V7_1__point_master_table.sql` + `V7_2__drvn_anls_dwld_history.sql` (월 파티션 12개월 선행)
- **`FacilityType` enum 확장**: PWTF/DWT/RSV → PWTF/DWT/RSV/POINT
- **Apache POI 의존성 추가**: `org.apache.poi:poi-ooxml:5.3.0` (api 모듈 build.gradle)
- **`com.mo.swtp.pump` 패키지 확장**: 운전현황 분석 Controller·Service·Repository·DTO 추가

### 제외

- **AI 운전모드 변경 기능 (명세서 6.2)** — ANALYZE Q7 결정에 따라 별도 사이클 분리. 본 작업은 read-only 표시만
- **EPANET 분석 이력 다운로드 (명세서 6.7)** — ANALYZE Q3.2·A8 결정에 따라 별도 사이클 분리
- **`com.mo.swtp.pump` → `com.mo.swtp.instrument` 패키지 이관** — 마스터도메인설계 후속 작업
- **AI 추론 서버 호출** — 본 작업은 기존 `pump_predc_h` 조회만, 신규 추론 트리거 없음
- **알람 평가·인터록 평가** — 조회 화면이므로 4영역 점검 결과 비해당 ([ANALYZE 도메인 룰 4영역 점검](../../../analyze/20260507/송수펌프제어_운전현황분석/ANALYZE1.md))
- **`tag_m`·`Tag` 엔티티 신규 도입** — 마스터도메인설계 PLAN2 의 결과 재사용

## 도메인 모델

### 신규 영속화 엔티티 2개

| 엔티티 / 테이블 | 역할 | 주요 필드 |
|--------------|-----|---------|
| `DrvnAnlsDwldHistory` / `drvn_anls_dwld_h` | 다운로드 감사 시계열 (예측 자료 + 분석 이력 통합) — **immutable 이력 테이블** | `dwld_id` (BIGINT, SEQUENCE PK) · `data_div_cd` (PRDC/ANLS) · `dwld_file_nm` · `dwld_format_cd` (CSV/XLSX) · `rgstr_dtm`·`rgstr_id` 만 직접 선언 (BaseEntity 미상속, `indexing-and-migration.md §4.3` 이력 immutable 테이블 예외 적용 — `updt_*` 데드 컬럼 회피) |
| `SensorPoint` / `point_m` | facility 자식 'POINT' (관로 계측 분기점) | `facility_id` (부모 PK 자동 상속, UUID) · 자식 전용 컬럼 0건 (skeleton — facility_m 공통 컬럼만 사용) |

### 응답 DTO 4종 (영속화 0)

| DTO | 역할 | 매핑 명세서 항목 |
|----|-----|--------------|
| `DrvnSttsInqDto` | 조회 파라미터 (요청) — `from`·`to`·`inq_hr`·`inq_min`·`facility_id` | DRVN_STTS_INQ |
| `DrvnAnlsRsltDto` | 통합 응답 — 조회 결과 메타 + AI 운전모드 + 운영 현황 + 그래프 | DRVN_ANLS_RSLT + PREDC_ANLS_CMP_DATA |
| `PumpOprtngDto` | 펌프 가동 상태 — `actl_oprtng_yn`·`predc_oprtng_yn`·`actl_flwrt_val`·`predc_flwrt_val`·`pump_oprtng_cnt`·`pump_cmbn_cd` | PMP_OPRTNG_HSTRY |
| `BranchMeasPredcDto` | 분기 계측·예측 — `point_id`·`actl_*`·`predc_*` (수요량·관압·수위) | BRANCH_MEAS_PREDC_DATA |

### 적용 패턴

- **JPA JOINED 다형성** — facility 자식 POINT 추가 ([`entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴`](../../../../.claude/rules/entity-patterns.md))
- **`equip_type_cd = 'PUMP'` 필터 강제** — 본 작업의 모든 펌프 조회 ([`ot-integration.md §5`](../../../../.claude/rules/ot-integration.md), ANALYZE Q5.3 결정)
- **`facility_type_cd = 'POINT'` 필터 강제** — 분기 지점 조회 시 부모 다형성 전체 조회 금지
- **시계열 → 마스터 FK 금지** — `drvn_anls_dwld_h` 마스터 FK 미생성 ([`partitioning-and-retention.md §1`](../../../../.claude/rules/db/partitioning-and-retention.md))
- **`@Schema(implementation)` 명시** — 사용자 정의 enum / 중첩 DTO 필드 ([`api-patterns.md §DTO @Schema(implementation) 명시 패턴`](../../../../.claude/rules/api-patterns.md))
- **`AuditingEntityListener` 적용 (BaseEntity 미상속, `rgstr_*` 만 직접 선언)** — 다운로드 감사 INSERT 시 `rgstr_id`·`rgstr_dtm` 자동 기록 (`@CreatedDate`·`@CreatedBy` + `@EntityListeners(AuditingEntityListener.class)`)

## DB 설계 변경

### 1. `drvn_anls_dwld_h` 신설 (월 RANGE 파티셔닝, 보존 5년)

```sql
CREATE TABLE drvn_anls_dwld_h (
    dwld_id          BIGINT NOT NULL,
    rgstr_dtm        TIMESTAMP NOT NULL,
    data_div_cd      VARCHAR(20) NOT NULL,
    dwld_file_nm     VARCHAR(100) NOT NULL,
    dwld_format_cd   VARCHAR(20) NOT NULL,
    rgstr_id         VARCHAR(50) NOT NULL,
    PRIMARY KEY (dwld_id, rgstr_dtm)
) PARTITION BY RANGE (rgstr_dtm);

-- 12개월 선행 파티션 (2026-05 ~ 2027-04)
-- ... 12개 PARTITION OF 정의

CREATE INDEX idx_drvn_anls_dwld_h_rgstr_id_dtm
    ON drvn_anls_dwld_h (rgstr_id, rgstr_dtm DESC);

-- 시퀀스
CREATE SEQUENCE seq_drvn_anls_dwld_h_id INCREMENT BY 100;

-- COMMENT ON COLUMN — 6개 컬럼 의무 (immutable 이력 테이블 표준 라벨 — `indexing-and-migration.md §4.3`)
```

- **immutable 이력 테이블** — INSERT-only 시계열 (`indexing-and-migration.md §4.3` 이력 immutable 테이블 예외 패턴). BaseEntity 미상속, `rgstr_dtm`·`rgstr_id` 만 직접 선언. `updt_*` 데드 컬럼 회피.
- 보존 기간: **5년** — `partitioning-and-retention.md §2` 다운로드 감사 일반 기준 적용
- PK: `(dwld_id, rgstr_dtm)` 복합 — 파티션 키 포함 ([`partitioning-and-retention.md §1`](../../../../.claude/rules/db/partitioning-and-retention.md) 표준)
- `seq_drvn_anls_dwld_h_id` (`@GeneratedValue(SEQUENCE)` + `allocationSize=100`)
- 모든 컬럼에 `COMMENT ON COLUMN` 의무 ([`indexing-and-migration.md §4`](../../../../.claude/rules/db/indexing-and-migration.md))

### 2. `point_m` 자식 테이블 신설 + `facility_type_cd` enum 확장

```sql
CREATE TABLE point_m (
    facility_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_point_m_facility
        FOREIGN KEY (facility_id) REFERENCES facility_m(facility_id) ON DELETE RESTRICT
);

COMMENT ON COLUMN point_m.facility_id IS '시설 ID (DOM_ID_36, facility_m 부모 PK 상속, FK ON DELETE RESTRICT)';
```

- `FacilityType` Java enum 에 `POINT` 추가
- 자식 전용 컬럼 0건 (skeleton — 후속 명세서에 분기 위치·관경 등 추가 시 별도 사이클)
- `facility_m` 부모의 `facility_type_cd` 컬럼 + DDL CHECK 제약은 enum 검증으로 위임 (CHECK 제약 미설정 — `entity-patterns.md` 표준)
- **FK ON DELETE = RESTRICT** — facility_m 마스터 삭제 시 POINT 자식 자동 삭제 차단. 도메인 안전 영역 (관로 계측 분기점은 시계열 데이터 논리 참조처) 이므로 CASCADE 미채택. 다른 facility 자식 (PWTF/DWT/RSV) 의 FK 정책과 일관성 검토 필요 — 기존 자식의 FK 정책이 RESTRICT 면 정합. 향후 통합 변경 시 별도 사이클

### 3. JOIN 조회 패턴 (영속화 없음 — 매 조회 산출)

운전현황 통합 조회 시 다음 4개 시계열을 시간 윈도우로 JOIN:

- `rawdata_1m_h` (실제 측정값 — `raw_val` 또는 `corr_val`)
- `pump_predc_h` (예측 — `predc_elpwr_amt`·`predc_flwrt`·`predc_prsr`·`predc_oprtng_yn`·`pump_oprtng_cnt`)
- `pump_ctrl_h` (제어 이력 — 가동 조합 추적)
- `ai_drvn_mod_p` (AI 운전모드 read-only 표시)

QueryDSL Custom Repository 패턴 — `PumpControlHistoryCustomRepository` 의 LocalDateTime 범위 조회 패턴 + `MenuCustomRepository` 의 JOIN 패턴 합성. 시계열 시간 윈도우 조회 시 `acq_dtm` (또는 `predc_base_dtm`·`ctrl_dtm`) 조건 의무로 파티션 프루닝 작동.

## 구현 방향

### Phase 1: ANALYZE status 전환 + 의존성 추가
- `docs/analyze/20260507/송수펌프제어_운전현황분석/ANALYZE1.md` frontmatter `status: review → approved` (본 PLAN 작성과 함께 완료)
- `api/build.gradle` 에 `implementation 'org.apache.poi:poi-ooxml:5.3.0'` 추가
- `./gradlew.bat clean build` 통과 확인

### Phase 2: facility 자식 POINT 추가
- `common/src/main/java/com/mo/swtp/facility/domain/FacilityType.java` enum 에 `POINT` 추가
- `common/src/main/java/com/mo/swtp/facility/domain/SensorPoint.java` 신규 (`@DiscriminatorValue("POINT")` + `@Table(name="point_m")`)
- `common/src/main/resources/db/init/V7_1__point_master_table.sql` 신규 (스키마 + COMMENT)
- 단위 테스트 1건: `SensorPointTest` — `create()` 정적 팩토리 + `Facility` 상속 검증

### Phase 3: drvn_anls_dwld_h 엔티티 + DDL + 파티션 자동 DROP 스케줄러
- `common/src/main/java/com/mo/swtp/pump/domain/DrvnAnlsDwldHistory.java` 신규 — **immutable 이력 패턴** (BaseEntity 미상속, `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate rgstrDtm` + `@CreatedBy rgstrId` 직접 선언, 시퀀스 PK)
- `common/src/main/java/com/mo/swtp/pump/enumtype/DataDivType.java` (PRDC/ANLS) + `FileFormatType.java` (CSV/XLSX) 신규
- `api/src/main/java/com/mo/swtp/pump/repository/DrvnAnlsDwldHistoryRepository.java` 신규
- `common/src/main/resources/db/init/V7_2__drvn_anls_dwld_history.sql` 신규 (테이블 + 12개월 파티션 + 인덱스 + 시퀀스 + COMMENT 6컬럼)
- **파티션 자동 DROP 스케줄러 확장**: `scheduler` 모듈의 `SchedulerService` 에 5년 이전 `drvn_anls_dwld_h_*` 파티션 자동 DROP 잡 추가 (`partitioning-and-retention.md §2` 정합)
- 통합 테스트 1건: `DrvnAnlsDwldHistoryRepositoryTest` — INSERT + `AuditingEntityListener` 자동 주입 검증 + `updt_*` 컬럼 부재 검증

### Phase 4: 응답 DTO 4종 + Service + Custom Repository
- `api/src/main/java/com/mo/swtp/pump/dto/DrvnSttsInqDto.java` (요청) + `DrvnAnlsRsltDto`·`PumpOprtngDto`·`BranchMeasPredcDto` (응답)
- `api/src/main/java/com/mo/swtp/pump/service/PumpDrvnStatusService.java` (`@Transactional(readOnly = true)`)
- `api/src/main/java/com/mo/swtp/pump/repository/PumpDrvnStatusCustomRepository.java` + `Impl` (QueryDSL JOIN)
- 모든 DTO 사용자 정의 필드는 `@Schema(implementation)` 의무

### Phase 5: 다운로드 API + Apache POI
- `api/src/main/java/com/mo/swtp/pump/service/PumpDrvnStatusDownloadService.java` 신규
  - `XSSFWorkbook` 으로 XLSX 생성, `BufferedWriter` 로 CSV 작성
  - `drvn_anls_dwld_h` INSERT 1건 (BaseEntity 자동 주입)
  - `HttpServletResponse.getOutputStream()` 직접 스트리밍
- 권한: `request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)` 로 사용자 ID 추출 — ADMIN/USER 모두 다운로드 허용 (가정 섹션 명기)

### Phase 6: Controller + ErrorCode
- `api/src/main/java/com/mo/swtp/pump/web/PumpDrvnStatusController.java` 신규
  - `GET /api/pump-control/drvn-status` — 통합 조회
  - `GET /api/pump-control/drvn-status/operation` — 펌프 가동
  - `GET /api/pump-control/drvn-status/branch-measure` — 분기 지점
  - `POST /api/pump-control/drvn-status/download` — 다운로드
- `common/src/main/java/com/mo/swtp/pump/exception/PumpDrvnStatusErrorCode.java` 신규
  - `INVALID_INQUIRY_RANGE`(400) · `FACILITY_NOT_FOUND`(404) · `DOWNLOAD_FORMAT_NOT_SUPPORTED`(400) · `DOWNLOAD_FAILED`(500)
- 모든 엔드포인트 Swagger `@Tag` + `@Operation` + `@ApiResponses` 6종 (200/400/401/403/404/500)

### Phase 7: 통합 테스트 + 빌드 검증
- `api/src/test/java/com/mo/swtp/pump/PumpDrvnStatusIntegrationTest.java` 신규
- `./gradlew.bat clean build` PASS

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령·테스트·조회 |
|-----|------------------|
| 운전현황 조회 API (`GET /api/pump-control/drvn-status`) — 200 응답 + `DrvnAnlsRsltDto` 정상 직렬화 + `equip_type_cd = 'PUMP'` 필터 적용 | 통합 테스트 1건 GREEN — ./gradlew.bat :api:test --tests *PumpDrvnStatusIntegrationTest* PASS |
| 다운로드 API (`POST /api/pump-control/drvn-status/download`) — XLSX·CSV 응답 200 + `drvn_anls_dwld_h` INSERT 1건 + `dwld_format_cd` 검증 (CSV/XLSX 외 거부) | 단위 테스트 3건 (XLSX 정상·CSV 정상·잘못된 format 400) GREEN |
| facility 자식 POINT 다형성 조회 — `facility_type_cd = 'POINT'` 필터 강제 검증 | 단위 테스트 1건 GREEN + REVIEW 단계 wtp-domain-expert 점검 (필터 누락 시 블로커) |
| `rawdata_1m_h` + `pump_predc_h` 시간 윈도우 1분 단위 JOIN 조회 정합성 | 단위 테스트 2건 (정상 JOIN 결과·시간 경계 케이스) GREEN |
| AI 운전모드 read-only 표시 — `ai_drvn_mod_p` 의 `ai_drvn_mod` (사용자 의도) + `ai_mode_cd` (시스템 상태) 둘 다 응답 노출 | 단위 테스트 1건 GREEN — DTO 직렬화 검증 |
| `drvn_anls_dwld_h` BaseEntity 4 컬럼 자동 주입 — `rgstr_dtm`·`rgstr_id` `AuditingEntityListener` 통합 테스트 | 통합 테스트 1건 (Phase 7) GREEN |
| Apache POI 의존성 정상 빌드 + XLSX 산출물 검증 | ./gradlew.bat :api:bootJar 빌드 성공 + 단위 테스트 1건 (XSSFWorkbook 셀 검증) GREEN |
| 컬럼 COMMENT 의무화 hook 통과 | Write 시 check-ddl-column-comment.sh 차단 0건 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|----------|------|------------|
| ANALYZE1.md status `review → approved` 사용자 승인 후 PLAN 진입 | 결정 | 사용자 응답 (2026-05-08) |
| 다운로드 라이브러리 = Apache POI 단독 (XLSX 표준 + 자체 CSV writer) | 결정 | 사용자 응답 (2026-05-08) |
| `drvn_anls_dwld_h` = 월 RANGE 파티셔닝, 보존 5년, 12개월 선행 파티션 | 결정 | 사용자 응답 (2026-05-08) |
| 운전현황 분석 화면은 **단일 facility** 조회 (Q5.4 보류 — 명세서 7.1 흐름 정합) | 가정 | 단일 `facility_id` 파라미터로 설계. 복수 facility 통합 조회는 별도 사이클 |
| DOM_ERR_RT 처리 = 거부 — `DOM_QTY_15_4` 재사용 + 백분율 0~100 애플리케이션 검증 | 결정 | ANALYZE 회의 결론 채택. AGENDA A4 후보 표 등재되어 있으나 본 작업 등록 안 함 |
| `com.mo.swtp.pump` 패키지 재사용 — 운전현황 분석은 펌프 운전 정보 조회 — `instrument` 이관은 별도 사이클 | 결정 | ANALYZE §신규 엔티티/DB 컬럼 의 PLAN 단계 위임 항목 채택 |
| 다운로드 API 권한 = ADMIN + USER 모두 허용 (명세서 미명시) | 가정 | 명세서 6.5 다운로드 형식만 규정, 권한 별도 명시 부재. ADMIN-only 제한은 별도 사이클 |
| `data_div_cd` enum = PRDC (예측조회) / ANLS (분석이력) — 본 작업은 PRDC 만 정상 구현, ANLS 는 EPANET 별도 사이클 의존 (응답에 "EPANET 데이터 미연동" 안내) | 결정 | ANALYZE A8 결정 정합 |
| 권한메뉴 PLAN2 의 `UserRole` enum 재사용 — 별도 추가 등록 없음 | 결정 | 권한메뉴 PLAN2 approved 검증 완료 |
| `actl_oprtng_yn`·`predc_oprtng_yn` 컬럼명에 `oprtng` 재사용 (Q3.1 B 결정) | 결정 | ANALYZE Q3.1 결정 정합 |
| `drvn_anls_dwld_h` BaseEntity 미상속 + `rgstr_dtm`·`rgstr_id` 직접 선언 — **immutable 이력 테이블 패턴 채택** (`indexing-and-migration.md §4.3` 정합) | 결정 | wtp-domain-expert + wtp-dba-reviewer 동일 권고 채택 (2026-05-08 PLAN 검토). 다운로드 감사는 갱신 경로 0건이므로 `updt_*` 데드 컬럼 회피. `rawdata_1m_h` 의 BaseEntity 4 적용 사유는 `corr_val` 갱신 시나리오이나 본 테이블에는 갱신 시나리오 부재 |
| `point_m` FK ON DELETE = RESTRICT — facility_m 마스터 삭제 시 POINT 자식 자동 삭제 차단 (도메인 안전) | 결정 | wtp-dba-reviewer 권고 채택. 기존 facility 자식 (PWTF/DWT/RSV) 의 FK 정책과 일관성 — 구현 단계에서 V6_1 DDL 검증 필요 (가정) |
| `drvn_anls_dwld_h` BRIN 인덱스 (`rgstr_dtm`) 본 PLAN 미적용 — B-Tree 단독 운용 | 가정 | wtp-dba-reviewer 참고. 다운로드 빈도 낮아 B-Tree 충분. 보존 5년 누적 시 인덱스 크기 절감 실익 발생하면 별도 사이클에서 BRIN 추가 검토 |
| 파티션 자동 DROP 스케줄러 확장 — `scheduler` 모듈의 `SchedulerService` 에 `drvn_anls_dwld_h_*` 5년 이전 파티션 자동 DROP 잡 추가 | 결정 | wtp-dba-reviewer 권고 채택. Phase 3 구현 범위에 포함 — TASK 단계 체크박스 의무 |

## 제외 사항

- **AI 운전모드 변경 기능 (명세서 6.2)** — Q7 결정에 따라 별도 사이클 분리. 본 작업은 read-only 표시만
- **EPANET 분석 이력 다운로드 (명세서 6.7)** — Q3.2·A8 결정에 따라 별도 사이클 분리
- **`com.mo.swtp.pump` → `com.mo.swtp.instrument` 패키지 이관** — 마스터도메인설계 후속 작업 (별도 사이클)
- **알람 평가·인터록 평가** — 조회 화면이므로 4영역 점검 결과 비해당 (ANALYZE 4영역 점검 표 참조)
- **`tag_m`·`Tag` 엔티티 신규 도입** — 마스터도메인설계 PLAN2 의 결과 재사용 (변경 없음)

## 예상 산출물

- [태스크](../../../tasks/20260508/송수펌프제어_운전현황분석/TASK1.md) — 본 PLAN approved 후 `/dev:task` 단계에서 작성

## 부록: 도메인/DB 검토 결과 (2026-05-08)

### wtp-domain-expert 검토
- **블로커**: 0건
- **권고 (중간)**: 1건 — `drvn_anls_dwld_h` 의 `updt_*` 데드 컬럼 정책 — `indexing-and-migration.md §4.3` 이력 immutable 테이블 예외 패턴 정합 권고 → **채택** (PLAN §도메인 모델 + §DB 설계 변경 §1 + §가정 섹션 반영)
- **참고 (낮음)**: 1건 — AI 운전모드 영향 범위 PLAN 본문 명시 보강 → §제외 사항 + §적용 패턴 에 read-only 명기, AI 운전모드 변경 별도 사이클 명시로 충분

### wtp-dba-reviewer 검토
- **블로커**: 1건 — ANALYZE 산출물 경로 `20260507 → 20260508` 날짜 불일치 → **해소** (ANALYZE1.md `## 산출물` 섹션 경로 수정 완료, 2026-05-08)
- **권고 (중간)**: 3건
  - `drvn_anls_dwld_h` `updt_*` 데드 컬럼 패턴 불일치 → **채택** (domain-expert 와 동일, immutable 패턴 적용)
  - 파티션 자동 DROP 스케줄러 확장 → **채택** (Phase 3 구현 범위 추가)
  - `point_m` FK ON DELETE 정책 미명시 → **채택** (RESTRICT 명시, §DB 설계 변경 §2 반영)
- **참고 (낮음)**: 2건
  - BRIN 인덱스 미계획 → **본 PLAN 미적용** (가정 섹션 명기, 누적 시 별도 사이클 검토)
  - `allocationSize=100` 과도 예약 → 참고 수준, 변경 없음 (BIGINT 범위 내 무의미한 차이)

### 처리 요약
- 블로커 1건 해소 + 권고 3건 채택 (PLAN §도메인 모델·§DB 설계 변경·§구현 방향 Phase 3·§가정 섹션 반영)
- 본 PLAN 의 status: review 전환 후 사용자 검토 요청
