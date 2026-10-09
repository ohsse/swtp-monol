---
status: approved
created: 2026-06-04
updated: 2026-06-04
---
# 송수펌프 제어이력 2·3번 섹션 API

## 목적

송수펌프 제어이력 화면(`backend/image/송수펌프제어이력.png`)의 2·3번 섹션 백엔드 **조회 전용** API를 개발한다. 두 섹션은 동일한 제어이력 테이블(`pump_ctrl_h`)을 동일 조회기간(from~to)으로 조회하므로 한 사이클에서 함께 구현한다.

- **2번 섹션 (AI 운영 현황)**: 조회기간 제어이력을 운전모드(AI / AI추천 / AI분석)별 카운트 + 비율(모드카운트 ÷ 전체카운트, %)로 집계. 차트·표 표출용.
- **3번 섹션 (제어 이력)**: 같은 조회기간 제어이력을 시간 역순 목록으로 표출. 컬럼 = 제어요청시간 · 제어대상펌프명 · 제어 태그번호 · 제어요청구분(가동/중지) · 제어결과(제어완료/제어취소) · 갱신시간(제어완료시간) · 운전모드.

## 배경

- 데이터 소스 `pump_ctrl_h`는 dev DB에 월 파티션과 함께 물리적으로 존재하나 2026-05-12 백지화로 엔티티·마이그레이션이 삭제된 **고아 테이블**(현재 0행)이다. ANALYZE1(`docs/analyze/20260604/송수펌프제어이력_2_3번섹션/ANALYZE1.md`) 5인 회의 결정으로 **기존 컬럼 구조 최대 활용·물리명 유지** 재도입한다.
- 사용자 정정으로 **제어 태그는 가동상태(OPS) 태그와 별개**임이 확정 — 제어 태그 = `tag_se_cd='CMD'(신규) + io_cd='OUTPUT'`. ANALYZE1 안건 5.
- 형제 사례인 송수펌프 가동이력 2·3·4번 섹션(`instrument` 패키지)의 통계·이력 조회 패턴, `opt` 도메인 `predc_1m_h`(BIGINT SEQUENCE + dtm 복합 PK + 월 파티션)의 `@IdClass` + DDL 패턴을 인용한다.

## 범위

| 구분 | 포함 | 제외 |
|------|------|------|
| 모듈 | `common`(엔티티·enum), `api`(Repository·Service·Controller·DTO) | `scheduler` |
| 도메인 | `com.mo.swtp.instrument`(제어이력) + `com.mo.swtp.tag`(CMD 코드값 추가) | OT 아웃바운드·제어 발행·인터록·AI 운전모드 쓰기 |
| API | 섹션2 통계 조회, 섹션3 목록 조회 (GET 2건) | 쓰기 API |
| DDL | `pump_ctrl_h` 재도입(`V3_1` patch + docs/ddl) | `tag_m` 스키마 변경 없음(CMD는 enum 값) |
| 검증 | Mockito 단위 테스트 + 화면 확인용 시드 | 통합·E2E(미도입) |

## 구현 방향

### Phase 1 — 엔티티·enum·CMD 코드값·DDL (common + migration)

1. **enum 2종 신규** (`common/.../instrument/domain/enumtype/`):
   - `ControlCommand` — `START`("가동") / `STOP`("중지"). `ctrl_div` 매핑.
   - `ControlResult` — `COMPLETED`("제어완료") / `CANCELLED`("제어취소"). `ctrl_rslt` 매핑.
2. **`TagMeasurementType.CMD` 추가** (`common/.../tag/domain/enumtype/TagMeasurementType.java`): `CMD("운전제어", "")` 10번째 코드값 추가. enum javadoc "측정유형→신호유형(Signal Type)" 재해석 (OPS 디지털 DI 상태가 이미 혼재하므로 명령 DO 추가는 자연 확장) — javadoc 문구 갱신. **DDL 변경 없음**(기존 `tag_m.tag_se_cd VARCHAR(20)` 저장).
3. **엔티티 `PumpCtrlHistory` + `PumpCtrlHistoryId`** (`common/.../instrument/domain/`): `predc_1m_h`(`TagPrediction`) 선례 — `@IdClass(PumpCtrlHistoryId.class)` + `@SequenceGenerator(seq_pump_ctrl_id, allocationSize=100)`. `pump_ctrl_h`는 `updt_dtm`(갱신시간/제어완료시간) 갱신이 의미상 발생하므로 `BaseEntity` 4컬럼 상속(`AiDrvnModeHistory` 선례 + `db/indexing-and-migration.md §4.3` "변경 추적 컬럼 존재 시 BaseEntity 4 상속 허용"). `instrument_id`는 시계열→마스터 FK 금지(`db/partitioning-and-retention.md §1`)로 `@Column` 논리 참조(@ManyToOne 금지). `ai_drvn_mod`는 `AiDrvnModeCode`(proc 도메인) 재사용 + nullable. 본 사이클 조회 전용이라 영속 메서드 미정의(create/change 없음).
4. **DDL 사전 조사 (선행 의무)** — `pg_indexes WHERE tablename='pump_ctrl_h'` + `pg_class`/`pg_inherits`(파티션 실명) 조회로 dev DB 기존 인덱스·파티션 **이름**을 확인하고 V3_1 DDL의 `IF NOT EXISTS` 인덱스·파티션명을 동일하게 정합한다(이름 불일치 시 중복 생성 발생 — DBA 권고). TASK 체크박스 선행 단계로 명시.
5. **DDL 재도입** — `common/src/main/resources/db/migration/V3_1__instrument_patch.sql`(신규 patch) + `backend/docs/ddl/instrument.sql`(말미 ALTER/CREATE 누적) 양쪽 동시(`db/indexing-and-migration.md §5.3`):
   - `CREATE TABLE IF NOT EXISTS pump_ctrl_h (...)` — dev DB 실측 컬럼과 정확 일치(10컬럼), `PRIMARY KEY (pump_ctrl_id, ctrl_dtm)`, `PARTITION BY RANGE (ctrl_dtm)`.
   - `CREATE SEQUENCE IF NOT EXISTS seq_pump_ctrl_id` (`allocationSize=100` 정합).
   - 인덱스 `IF NOT EXISTS`: BRIN(`ctrl_dtm`) + B-tree(`instrument_id, ctrl_dtm DESC`) — 위 §4 실명 조회 결과와 동일명 사용(중복 생성 방지).
   - 파티션 `IF NOT EXISTS` `pump_ctrl_h_202604`~`pump_ctrl_h_202703`(202610~202703 신규 선행 — `partitioning-and-retention.md §1` "운영 기간 6개월 선행" 충족, 오늘 2026-06 기준 ≥6개월).
   - 전 컬럼 `COMMENT ON COLUMN`(훅 `check-ddl-column-comment.sh` 의무). `ai_drvn_mod` COMMENT에 "NULL=수동 제어, WHERE IS NOT NULL 로 AI 집계" 명기. BaseEntity 4컬럼 표준 라벨(`§4.3`).
   - `migration/README.md` §3 도메인-V 매핑은 instrument(V3) 유지 — patch 추가만 반영(표 갱신 불요, V3 도메인 내 patch).

### Phase 2 — Repository (api, Querydsl)

`PumpCtrlHistoryRepository`(JpaRepository) + `PumpCtrlHistoryCustomRepository` + `...Impl`(Querydsl, `ApiQuerydslConfig` `JPAQueryFactory` 주입). 두 조회 메서드(`§2.5` 면책 — `query-tuning.md §2`, 파티션 프루닝 의도 단일 흐름 보존 주석):

- **섹션2 집계**: `select(pch.aiDrvnMod, pch.count()).from(pch).where(ctrlDtm goe start, lt end, aiDrvnMod isNotNull).groupBy(pch.aiDrvnMod)` → `List<Tuple>`(모드, 카운트). `ctrl_dtm` 범위로 월 RANGE 파티션 프루닝 강제.
- **섹션3 목록**: `pch` ⋈ `instrument`(`on pch.instrumentId == instrument.instrumentId`, `instrumentNm`) + 제어 태그번호는 **상관 스칼라 서브쿼리** `JPAExpressions.select(tag.tagSrlNo.min()).from(tag).where(tag.instrument.instrumentId == pch.instrumentId, tagSeCd==CMD, ioCd==OUTPUT, useYn==Y)` (행 증식 회피 + 0/복수 안전, 미존재 시 null). `where(ctrlDtm goe start, lt end)`, `orderBy(pch.ctrlDtm.desc())`. `Projections.constructor(PumpCtrlHistoryDto.class, ...)`.

### Phase 3 — 섹션2 통계 Service·DTO·Controller (api)

- DTO `PumpCtrlStatDto`(응답): `totalCount`(long) + `List<ModeStatDto>`(중첩 정적 클래스 — `PumpOperationHistoryDto.OperationSegment` 선례). `ModeStatDto`: `aiDrvnMod`(AiDrvnModeCode, `@Schema(implementation)`), `count`(long), `rate`(BigDecimal, scale 1 %).
  - **필드명 정합 (도메인 검토 권고 반영)**: 응답 DTO·엔티티 모두 물리 컬럼명 `pump_ctrl_h.ai_drvn_mod`(`_cd` 없음)를 따라 `aiDrvnMod` 로 통일한다. proc 도메인의 현행 활성 컬럼 `ai_drvn_mod_cd`(`_cd` 있음, 별개 테이블)와 필드명까지 분리하여 매핑 혼선을 차단한다.
- Service `PumpCtrlHistoryService#findCtrlStat(PumpPeriodSearchDto)`: 기간 검증(`isValid()` false → `INVALID_INQ_PERIOD`) → 집계 조회 → 모드별 카운트 Map → **3종(AI/AI_RECOMD/AI_ANLS) 전부**(0건 포함) `ModeStatDto` 구성. `total = Σcount`, `rate = count/total*100`(HALF_UP scale 1), **total=0 시 rate=0**(분모 0 방어).

### Phase 4 — 섹션3 목록 Service·DTO·Controller (api)

- DTO `PumpCtrlHistoryDto`(응답 행): `ctrlDtm`·`pumpId`·`pumpNm`·`ctrlTagSrlNo`(nullable)·`ctrlDiv`(ControlCommand)·`ctrlRslt`(ControlResult)·`updtDtm`·`aiDrvnMod`(AiDrvnModeCode nullable). `LocalDateTime`은 `@JsonFormat("yyyy-MM-dd HH:mm:ss")`, enum은 `@Schema(implementation=...)`.
- Service `PumpCtrlHistoryService#findCtrlHistory(PumpPeriodSearchDto)`: 기간 검증 → Querydsl 목록 조회 결과 반환(매핑은 Projection에서 완료).
- Controller `PumpCtrlHistoryController extends CommonController` (`@Tag("12. 송수펌프 제어이력")`, `@RequestMapping("/api/instrument")`, 엔드포인트 2개):
  - `GET /api/instrument/pump-ctrl-stat` → `CommonResponseDto<PumpCtrlStatDto>`
  - `GET /api/instrument/pump-ctrl-history` (`@ModelAttribute PumpPeriodSearchDto`) → `CommonResponseDto<List<PumpCtrlHistoryDto>>`
  - 리터럴 세그먼트가 `/{instrumentId}` path-variable보다 PathPattern 특이도 높아 우선 매칭(가동이력 선례). `@Operation`·`@ApiResponses` 전건.
- `InstrumentErrorCode.INVALID_INQ_PERIOD`(기존) 재사용 — 신규 ErrorCode 불요.

### Phase 5 — 시드 데이터 + 단위 테스트

- **시드**: ① 활성 PUMP들에 제어 태그(`tag_se_cd='CMD'`, `io_cd='OUTPUT'`, `use_yn='Y'`) 1건씩 INSERT, ② `pump_ctrl_h` 더미 제어이력 INSERT(이미지 분포 모사 — AI 다수·AI_RECOMD 일부·AI_ANLS 0 + 수동 NULL 일부, ctrl_div/ctrl_rslt 다양, ctrl_dtm 기간 분산, updt_dtm 채움). 적재 경로(스크립트 위치·대상 DB local/dev)는 구현 단계 사용자 확인(`feedback_runtime_artifact_path`).
- **단위 테스트(Mockito)** `PumpCtrlHistoryServiceTest`: CustomRepository mock.
  - 섹션2: 전체=모드합 / 비율합≈100 / total=0 시 rate=0(분모 0 방어) / NULL(수동) 제외 / AI_ANLS 0건도 응답 포함(3종).
  - 섹션3: 기간 미입력·역전 시 `INVALID_INQ_PERIOD` / 정상 시 Repository 위임 결과 반환(정렬·매핑은 Querydsl 책임이라 Repository 결과 통과 검증).

## 도메인 모델

| 엔티티/테이블 | 역할 | 주요 필드 |
|------|------|----------|
| `PumpCtrlHistory` (`pump_ctrl_h`) | 제어이력 시계열(조회 전용 재도입), `BaseEntity` 4 상속 | `@IdClass(pumpCtrlId:Long SEQUENCE, ctrlDtm:LocalDateTime)`, `instrumentId`(논리참조 String), `ctrlDiv`(ControlCommand), `ctrlRslt`(ControlResult), `aiDrvnMod`(AiDrvnModeCode, nullable), rgstr/updt 4 |
| `PumpCtrlHistoryId` | 복합 PK ID 클래스(`@IdClass`, Serializable) | `pumpCtrlId:Long`, `ctrlDtm:LocalDateTime` |
| `ControlCommand` (enum) | 제어요청구분(`ctrl_div`) | `START`("가동") / `STOP`("중지") |
| `ControlResult` (enum) | 제어결과(`ctrl_rslt`) | `COMPLETED`("제어완료") / `CANCELLED`("제어취소") |
| `TagMeasurementType.CMD` | 제어 태그 식별 코드값(기존 enum 확장) | `CMD`("운전제어","") — `io_cd='OUTPUT'`와 결합 |
| `PumpCtrlStatDto` / `.ModeStatDto` (api) | 섹션2 응답 | `totalCount`, `List<ModeStatDto>{aiDrvnMod, count, rate}` |
| `PumpCtrlHistoryDto` (api) | 섹션3 응답 행 | `ctrlDtm·pumpId·pumpNm·ctrlTagSrlNo(nullable)·ctrlDiv·ctrlRslt·updtDtm·aiDrvnMod(nullable)` |
| `PumpPeriodSearchDto` (기존 재사용) | from~to 검색 | `fromDt`, `toDt`, `isValid()`, `toStartDtm()`, `toEndExclusiveDtm()` |

## DB 설계 변경

- **대상**: `pump_ctrl_h` 재도입(고아 테이블 코드 자산화). **무중단 전략**: dev DB는 IF NOT EXISTS로 기존 스키마 보존(no-op), 신환경은 `V3__instrument.sql`(facility/instrument 마스터) 적용 후 `V3_1` patch로 전체 생성. `instrument_id` 논리 참조(FK 금지 — 시계열 대용량 INSERT 잠금 회피).
- **인덱스**: 기존 BRIN(`ctrl_dtm`) + B-tree(`instrument_id, ctrl_dtm DESC`) 유지. 추가 인덱스(전용 `ctrl_dtm DESC` 단독) **보류**(0행·`coding-discipline.md §2`, 누적 후 EXPLAIN 측정 후 CONCURRENTLY).
- **파티션**: 월 RANGE, `202604`~`202703` `IF NOT EXISTS`(202610~202703 신규 선행 생성 — `partitioning-and-retention.md §1` 6개월 선행 충족, DBA 권고 반영).
- **NULL 정책**: `ai_drvn_mod` NULL 허용(`DOM_CODE_20` NOT NULL 완화) — DBA 2차 승인(ANALYZE1 안건 3), DDL COMMENT 명기.
- **보존**: 2년(제어 로그, `db/partitioning-and-retention.md §2`).
- **이중 정책**: `V3_1` patch + `docs/ddl/instrument.sql` 동일 커밋 동시 갱신(`§5.3`).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 빌드·QClass·DDL 훅 통과 | ./gradlew.bat clean build → BUILD SUCCESSFUL + check-ddl-column-comment.sh 전 컬럼 COMMENT 통과 |
| 섹션2 통계 산정 정확 | PumpCtrlHistoryServiceTest GREEN — 전체=모드합 / 비율합 100±0.1 / total=0 시 rate=0 / NULL 제외 / AI·AI_RECOMD·AI_ANLS 3종 0건 포함 |
| 섹션3 기간 검증·위임 | PumpCtrlHistoryServiceTest GREEN — fromDt/toDt 결측·역전 시 INVALID_INQ_PERIOD throw / 정상 시 Repository 결과 그대로 반환 |
| 시드 후 실조회 정합 | 시드 INSERT 후 MCP 조회 — 섹션2 모드 카운트 합=전체·비율 합 100% / 섹션3 ctrl_dtm DESC 순서 + 펌프명·제어태그(CMD/OUTPUT) 매핑(미존재 시 null) 확인 |
| 파티션 프루닝 | 섹션2/3 쿼리 EXPLAIN (ANALYZE) 에서 ctrl_dtm 범위 파티션 프루닝 동작 확인(db/query-tuning.md §2) |
| Swagger 노출 | /api/instrument/pump-ctrl-stat·pump-ctrl-history 2건 + DTO @Schema·enum implementation 노출 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 제어 태그 = `tag_se_cd='CMD' + io_cd='OUTPUT' + use_yn='Y'`, 펌프당 1개 가정 | 결정 | 복수 시 상관 서브쿼리 `MIN(tag_srl_no)`로 단일화, 미존재 시 ctrlTagSrlNo=null(LEFT 의미) |
| 섹션2는 AI 제어(`ai_drvn_mod IS NOT NULL`)만 집계, 수동(NULL) 제외 | 결정 | WHERE aiDrvnMod isNotNull. 이미지 전체=3개 AI모드 합 정합 |
| 응답은 AI/AI_RECOMD/AI_ANLS 3종 0건이라도 포함 | 결정 | Service에서 enum 전체 순회 + 카운트 default 0 |
| `pump_ctrl_h` 엔티티는 `BaseEntity` 4 상속(updt_dtm 갱신 의미 존재) | 결정 | `db/indexing-and-migration.md §4.3` + AiDrvnModeHistory 선례. 본 사이클 조회 전용이라 영속 경로 미사용 |
| dev DB 기존 인덱스·파티션 **이름**을 V3_1 IF NOT EXISTS와 일치시켜 중복 생성 방지 | 미해결 | 구현 단계 `pg_indexes`/`pg_class` 조회로 실명 확인 후 DDL 인덱스명 정합 |
| `pump_ctrl_h`의 V3(instrument) 귀속은 OT 아웃바운드 재설계 시 이관 검토 | 미해결 | DBA Round 2 조건 — 본 PLAN 명기. 재설계 사이클에서 재평가 |
| 시드 스크립트 위치·대상 DB(local vs dev) | 미해결 | 구현 단계 사용자 확인(`feedback_runtime_artifact_path`) |

## 제외 사항

- OT 아웃바운드 제어 발행·인터록 검사·CircuitBreaker — 보류(`ot-integration.md §2·§5`). 본 작업은 과거 이력 SELECT만.
- AI 운전모드 쓰기·강제 전환·`ai_drvn_mod_p`/`ai_drvn_mod_h` 접촉 없음.
- 제어 태그 시드 외 `tag_m` 스키마 변경·`validateFqiTagAllowance` 등 태그 검증 로직 변경 없음(CMD는 enum 값 추가).
- `pump_ctrl_h` INSERT API(쓰기) 미포함 — 데이터는 시드/외부 적재 가정.
- 제어 태그번호(`ctrlTagSrlNo`)는 **표출 전용** — frontend가 이를 제어 입력 필드로 재사용하는 경로는 본 사이클 범위 밖. OT 아웃바운드 재설계 사이클에서 OUTPUT 태그 표출 범위를 재검토한다(도메인 검토 참고 반영).

## 부록: 도메인/DB 검토 결과

- **wtp-domain-expert**: 블로커 0건, 권고 1건(중간) + 참고 1건. 4영역 전부 비해당 정당·섹션2 NULL 제외·AI_ANLS 0건 포함 표출 도메인 정합 확인. 권고 → `ai_drvn_mod` DTO 필드명 `aiDrvnMod` 통일 반영(Phase 3). 참고 → OUTPUT 태그 표출 미래 확장 주석 반영(제외 사항).
- **wtp-dba-reviewer**: 블로커 0건, 권고 2건(중간) + 참고 2건. `instrument_id` FK 금지·BRIN+B-tree 컬럼 순서·patch 분리·BaseEntity 4(§4.3)·보존 2년 정합 확인. 권고 → 파티션 202703까지 선행 확장 + 인덱스 실명 조회 선행 단계(Phase 1.4) 반영. 참고 → patch 파일명 `V3_1__instrument_patch.sql` 단순화 반영, 섹션3 서브쿼리 `tag_m(instrument_id, tag_se_cd, io_cd)` 인덱스 부재 시 Seq Scan 가능 → 데이터 적재 후 EXPLAIN 확인(성공 기준 포함).

## 예상 산출물
- [태스크](../../../tasks/20260604/송수펌프제어이력_2_3번섹션/TASK1.md)
