# DB 파티셔닝·데이터 보존 정책

스마트정수장 PostgreSQL 시계열 데이터의 **수명주기 통합 관점** 운영 기준.
시계열 파티셔닝 규칙(생성·프루닝·관리)과 데이터 종류별 보존 기간 정책을 함께 다룬다.

> 본 문서는 과거 `db-patterns.md` 의 §1 시계열 파티셔닝 + §6 데이터 보존 정책을 분리한 결과다 (2026-04-24). 2026-04-26 디렉토리화로 `db/` 하위로 이동.
> DDL/스키마 관점은 [`indexing-and-migration.md`](indexing-and-migration.md), 런타임 성능 관점은 [`query-tuning.md`](query-tuning.md) 참조.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`README.md`](README.md) | 본 문서의 인덱스 페이지 — 진입점 (구 root `db-patterns.md` 자리) |
| [`indexing-and-migration.md`](indexing-and-migration.md) | 인덱스 설계·무중단 DDL 변경 — 본 문서와 별도 관점 |
| [`query-tuning.md`](query-tuning.md) | 트랜잭션 격리·슬로우 쿼리 분석 — 런타임 성능 관점 |
| [`../ot-integration.md`](../ot-integration.md) | 시계열 수집 파이프라인 — `rawdata_1m_h` 등 파티션 키 사용처 |

---

## 1. 시계열 파티셔닝

### 기준
- SCADA 수집 데이터, 진단 결과, 알람 이력 등 시계열 테이블은 **월 단위 RANGE 파티셔닝**을 적용한다.
- 파티션 키: `acq_dtm` (수집 일시) 또는 `rgstr_dtm` (등록 일시)

### 파티션 생성 패턴 (PostgreSQL)

```sql
-- 파티션 테이블 생성 (마스터도메인설계 ANALYZE1 Round 3, 2026-05-03 — `tag_val` → `raw_val`·`corr_val` 분리)
CREATE TABLE rawdata_1m_h (
    rawdata_id  BIGINT NOT NULL,
    acq_dtm     TIMESTAMP NOT NULL,
    tag_srl_no  VARCHAR(50) NOT NULL,            -- 논리 참조 (시계열 → 마스터 FK 금지)
    raw_val     NUMERIC(15,4),                   -- SCADA 원본 측정값
    corr_val    NUMERIC(15,4),                   -- 보정/수정값 (NULL 허용)
    quality_cd  VARCHAR(20) NOT NULL,            -- GOOD/BAD/UNCERTAIN (ot-integration.md §3)
    rgstr_dtm   TIMESTAMP,                       -- BaseEntity 4 (AuditingEntityListener 자동 주입)
    updt_dtm    TIMESTAMP,                       -- BaseEntity 4 — corr_val 갱신 시점 추적
    rgstr_id    VARCHAR(50),                     -- BaseEntity 4
    updt_id     VARCHAR(50),                     -- BaseEntity 4
    PRIMARY KEY (rawdata_id, acq_dtm)
) PARTITION BY RANGE (acq_dtm);

-- 월별 파티션 (최소 6개월 선행 생성)
CREATE TABLE rawdata_1m_h_202601 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE rawdata_1m_h_202602 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
```

### 운영 원칙
- 파티션은 **운영 기간보다 최소 6개월 앞서** 생성해야 한다. 파티션이 없으면 INSERT 실패.
- 오래된 파티션 삭제: `DROP TABLE rawdata_1m_h_202001;` — 즉시 완료, VACUUM 불필요.
- 파티션 범위에 걸친 쿼리는 파티션 프루닝이 작동하도록 `acq_dtm` 조건을 반드시 포함한다.
- **시계열 파티션 테이블(`_h` suffix) 은 마스터 테이블 FK 추가 금지**. 1분 주기 대용량 INSERT 마다 마스터 행 존재 확인 잠금이 발생하여 수집 성능에 직격 영향을 준다. 참조 무결성은 애플리케이션 레벨 검증으로 대체한다.

### `rawdata_1m_h` BaseEntity 4 적용 정책 (마스터도메인설계 ANALYZE1 Round 3, 2026-05-03)

`rawdata_1m_h` 는 **immutable 이력 테이블이 아니다** (Round 1·2 의 immutable 이력 패턴 폐기). `corr_val` 컬럼은 Hold Last Value 적용 결과 또는 운영자 보정으로 갱신 가능하므로 BaseEntity 4 컬럼 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) 적용으로 갱신 시점 추적이 필요하다.

- **AuditingEntityListener 주입**: `BaseEntity` 상속 + `@EntityListeners(AuditingEntityListener.class)` 로 자동 주입
- **시계열 → 마스터 FK 금지 정책과의 양립**: BaseEntity 4 는 audit 메타 주입이며 마스터 테이블 FK 가 아니다. 본 정책 (시계열 FK 금지) 과 충돌 없음
- **INSERT-only 컬럼의 immutable 보장**: `tag_srl_no`·`acq_dtm`·`raw_val` 은 SCADA 수집 시점 고정값으로 갱신 금지 — 애플리케이션 레벨 immutable 검증 필요 (PLAN 단계 명시). `corr_val` 만 갱신 허용

---

## 2. 데이터 보존 기간 정책

| 데이터 종류 | 보존 기간 | 삭제 방식 |
|-------------|----------|----------|
| SCADA 원시 데이터 (1분) | 13개월 (롤링) | 파티션 DROP |
| SCADA 15분 집계 | 3년 | 파티션 DROP |
| SCADA 시간·일·월 집계 | 10년 | 파티션 DROP |
| 진단 결과 (모터·펌프) | 5년 | 파티션 DROP 또는 DELETE |
| 알람 이력 | 5년 | 파티션 DROP 또는 DELETE |
| 제어 로그 (`pump_ctrl_h`) | 2년 | 파티션 DROP (pumpcontrol ANALYZE1 — 2026-04-25 — 월 RANGE 파티셔닝 적용에 따른 변경) |
| AI 예측 결과 (`opt_result_h`, `pump_prdct_h`, `pump_predc_h`) | 3년 | 파티션 DROP |
| 예측 시계열 (`predc_1m_h`) | 3년 | 파티션 DROP (송수펌프제어분석-7번섹션 ANALYZE1 — 2026-05-18 — AI 예측 결과 선례 정합, 월 RANGE 파티셔닝) |
| AI 운전 모드 전환 이력 (`ai_drvn_mod_h`) | 5년 | 파티션 DROP (pumpcontrol ANALYZE1 — 2026-04-25 — 신규 도입). **사이클 2 재도입** (송수펌프제어분석-2번섹션 ANALYZE1 — 2026-05-20) — `com.mo.swtp.proc` 단일 패키지 내 BaseEntity 4 상속 + `end_dtm` UPDATE 허용 구조로 재설계. 변경 빈도 시간~일 단위 매우 낮음 → 파티셔닝 불필요 (단일 테이블 + B-Tree 복합 인덱스). 보존 기간 5년 정책 정합 유지 |
| 마스터 데이터 | 영구 보존 | 삭제 불가 (논리 삭제만 허용) |

> 파티션 DROP은 스케줄러(`SchedulerService`)에서 자정에 자동 실행한다.
> 삭제 정책 변경 시 PLAN 문서의 `## DB 설계 변경` 섹션에 반드시 명시한다.
