---
status: approved
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 (FR-PMP-001) — 구현 계획

## 목적

ANALYZE1 (2026-04-25 갱신, status: approved) 의 결정 사항을 구현 계획으로 변환한다. 정수조(`pwtf`) → 송수펌프(`pump`) → 배수지(`dwt`) 의 물리적 흐름을 관리하는 송수펌프 제어 도메인을 최초 도입한다.

핵심 산출물:
- 신규 비즈니스 도메인 패키지 3개 (`com.mo.swtp.pump`·`com.mo.swtp.ai`·`com.mo.swtp.scada.outbound`)
- 신규 엔티티 10건 (마스터 4 + 상세 1 + 명세 2 + 시계열 파티션 3)
- 3개 사용자 API (대시보드 / AI 운전모드 설정 / 자동 제어 실행)
- 3-Service 오케스트레이터 패턴 (`PumpControlService` → `AiPredictionService` + `ScadaControlService`)
- OT 아웃바운드 어댑터 최초 도입 (`ScadaOutboundPort` + `@Profile` 분기)
- SCADA 5분 초과 강제 모드 전환 스케줄러

## 배경

- **요청**: 정수장 운영 핵심 기능 — AI 예측 기반 자동 제어 + 수동 On/Off 제어 + 운전 현황 대시보드
- **ANALYZE 산출물**: [`docs/analyze/20260422/pumpcontrol/ANALYZE1.md`](../../../analyze/20260422/pumpcontrol/ANALYZE1.md) (status: approved, 2026-04-25 재분석 반영)
- **선행 룰 변경 (2026-04-25 적용)**:
  - `db-partitioning-and-retention.md §1` 시계열 파티션 마스터 FK 금지 원칙 (커밋 53d8dc8) 반영 → 3개 파티션 테이블 FK 미생성, JPA 매핑은 컬럼 only
  - `dict/standard-data-domains.md` `DOM_TAG_NM_50` 신규 등록 → `pump_m.tag_nm` VARCHAR(50) NOT NULL
  - 비즈니스 도메인 약어 (pump/ctrl 마스터 승격, pwtf/dwt/ai 도입 예정) + 표준 단어 27건 + 표준 용어 26건 + 동의어·금지 패턴 8건 모두 사전 등록 완료
- **현재 코드 상태**: `com.mo.swtp.user`·`com.mo.swtp.auth` 만 구현. 본 작업이 펌프/정수조/배수지/AI/SCADA 어댑터 전부 최초 도입

## 범위

### 포함
- 비즈니스 도메인 패키지 3개 신규 생성
- 엔티티 10건 (`pump_m`·`pwtf_m`·`dwt_m`·`pump_cmbn_m`·`pump_cmbn_d`·`pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_p`·`ai_drvn_mod_h`·`pump_interlock_p`)
- API 3건 — `GET /api/pump/dashboard` · `PUT /api/pump/ai-mode` · `POST /api/pump/auto-control`
- 3-Service 오케스트레이터: `PumpControlService` · `AiPredictionService`(HTTP RestClient + resilience4j) · `ScadaControlService`(인터페이스 + `@Profile` 분기)
- 인터록 최소 stub: `pump_interlock_p` 빈 테이블 + `InterlockValidator`("규칙 미등록 시 통과")
- SCADA 5분 초과 감지 + 강제 모드 전환 스케줄러
- 파티션 선행 생성 배치 (월 1일 자정, 6개월 선행)
- ErrorCode enum 3건 (`PumpErrorCode`·`AiErrorCode`·`ScadaErrorCode`) — `httpStatus(int)` 만 허용
- 멀티테넌트 `resources-env/{프로파일}/application.yml` 에 AI 서버 URL · PLC 엔드포인트 · SCADA 타임아웃 분리
- 단위 테스트 (Mockito) + 도메인 시나리오 테스트 3종 (`PumpInterlockScenarioTest`·`PumpOperationModeScenarioTest`·`AlarmEscalationScenarioTest` 중 본 작업 직접 영향 2건)

### 제외 (별도 작업)
- 센서 품질 관리·알람 4단계 체계·실제 인터록 규칙 데이터 — `ot_integration_inbound`
- 배수지 분기별 요구 압력 이력 (`dwt_prsr_setn_h`) — `dwt_pressure_history`
- `pump_m.tag_se_cd` 컬럼 — `tag_m` 마스터 도입 시 별도 ANALYZE
- `TagMeasurementType` Java enum 위치 결정 — `tag_m` 도입 시
- Flyway 마이그레이션 도구 도입 — 본 작업은 JPA `ddl-auto=validate` + 수동 SQL 스크립트로 진행, Flyway 도입은 후속 작업

## 도메인 모델

### 엔티티 표 (10건)

| 엔티티 / 테이블 | 패키지 | suffix | 역할 | 주요 필드 (PK / 핵심 컬럼) |
|---------------|--------|--------|------|---------------------------|
| `Pump` / `pump_m` | `com.mo.swtp.pump.domain` | `_m` | 송수펌프 마스터 | `pump_id`(PK, 외부할당) · `pump_nm` · `pwtf_id`(FK) · `rated_head` · `rated_flwrt` · `tag_nm`(VARCHAR(50) NOT NULL, `DOM_TAG_NM_50`) · `use_yn` |
| `PurifiedWaterTank` / `pwtf_m` | `com.mo.swtp.pump.domain` | `_m` | 정수조 마스터 (잠정 — pump 패키지 귀속) | `pwtf_id`(PK, 외부할당) · `pwtf_nm` |
| `DistributionWaterTank` / `dwt_m` | `com.mo.swtp.pump.domain` | `_m` | 배수지 마스터 (잠정) | `dwt_id`(PK, 외부할당) · `dwt_nm` · `min_req_prsr` |
| `PumpCmbn` / `pump_cmbn_m` | `com.mo.swtp.pump.domain` | `_m` | 펌프 조합 헤더 | `pump_cmbn_cd`(PK) · `pwtf_id`(FK) · `pump_cmbn_nm` |
| `PumpCmbnDetail` / `pump_cmbn_d` | `com.mo.swtp.pump.domain` | `_d` | 펌프 조합 상세 (1:N) | 복합 PK `(pump_cmbn_cd, pump_id)`(둘 다 FK) · `ord` |
| `PumpInterlock` / `pump_interlock_p` | `com.mo.swtp.pump.domain` | `_p` | 인터록 규칙 (최소 stub, 빈 테이블 허용) | `pump_interlock_id`(PK) · `pump_id`(FK) · `sensor_tag` · `min_val` · `max_val` · `use_yn` |
| `PumpControlHistory` / `pump_ctrl_h` | `com.mo.swtp.pump.domain` | `_h` (파티션) | 펌프 제어 이력 | 복합 PK `(pump_ctrl_id, ctrl_dtm)`. `pump_id`(논리 참조) · `ctrl_div`(MANUAL/AUTO) · `ctrl_rslt`(SUCCESS/WAITING/FAIL) · `ai_drvn_mod` |
| `PumpPredictionResult` / `pump_predc_h` | `com.mo.swtp.pump.domain` | `_h` (파티션) | 펌프 운전 예측 결과 | 복합 PK `(predc_id, predc_base_dtm)`. `pwtf_id`(논리 참조) · `predc_dtm` · `pump_cmbn_cd`(논리 참조) · `predc_elpwr_amt` · `predc_flwrt` · `predc_prsr` · `ai_drvn_mod` |
| `AiDrvnMode` / `ai_drvn_mod_p` | `com.mo.swtp.ai.domain` | `_p` | AI 운전모드 설정 (정수조 1:1) | `pwtf_id`(PK, 외부할당) · `ai_drvn_mod`(사용자 의도) · `ai_mode_cd`(시스템 상태) · `expire_dtm` · `last_rcv_dtm` |
| `AiDrvnModeHistory` / `ai_drvn_mod_h` | `com.mo.swtp.ai.domain` | `_h` (파티션) | 운전모드 전환 이력 | 복합 PK `(ai_drvn_mod_h_id, rgstr_dtm)`. `pwtf_id`(논리 참조) · `prev_*`/`new_*` · `transition_reason` |

### Persistable<String> 구현 필수 (외부 할당 PK 6건)
- `Pump`·`PurifiedWaterTank`·`DistributionWaterTank`·`PumpCmbn`·`AiDrvnMode`·`PumpInterlock`
- 패턴: [`entity-patterns.md §외부 할당 PK 엔티티 패턴`](../../../../.claude/rules/entity-patterns.md). `getId()` override + `BaseEntity.newEntity` 플래그 위임

### 시계열 시퀀스 PK (3건, `DOM_SEQ_BIGINT`)

| 엔티티 | 시퀀스명 | allocationSize | 근거 (DBA 권고 — 2026-04-25) |
|-------|---------|----------------|---------------------------|
| `PumpControlHistory.pump_ctrl_id` | `seq_pump_ctrl_id` | 100 | 분당 다수 INSERT — 배치 효율, gap 무관 |
| `PumpPredictionResult.predc_id` | `seq_predc_id` | 100 | 시간당 수 건 — 배치 효율 |
| `AiDrvnModeHistory.ai_drvn_mod_h_id` | `seq_ai_drvn_mod_h_id` | **10** | 모드 전환 단건 트랜잭션 — JVM 재시작 시 gap 손실 최소화. 이력 순번 연속성 우선 |

### 파티션 마스터 FK 금지 원칙 (3건)
- `pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_h` — DB FK 미생성, JPA `@ManyToOne` 미사용, `@Column` 단독
- 마스터 존재 검증: 서비스 계층에서 `existsById`
  - `pump_ctrl_h` 는 `@Cacheable("pumpMasterExists")` 5분 TTL — **stale 방지를 위해 `PumpService.deactivatePump()` (`use_yn=N` 변경) 와 마스터 삭제 시 `@CacheEvict(value="pumpMasterExists", key="#pumpId")` 동시 트리거** (DBA 권고 — 2026-04-25)
  - `pump_predc_h` 는 `saveAll` 배치 1회 검증 (배치 진입 시점 한 번, 같은 `pwtf_id` 묶음). 트랜잭션 경계는 `PumpPredictionService.savePredictions()` 메서드 내 `@Transactional` 단일 범위
  - `ai_drvn_mod_h` 는 단건 호출 시 `existsById` 1회. 캐시 불필요 (단건 트랜잭션 빈도 낮음)
- **DDL 주석 의무 (DBA 권고 — 2026-04-25)**: 파티션 테이블 DDL 스크립트 작성 시 `pump_predc_h.pump_cmbn_cd` 등 마스터 참조 컬럼 옆에 "-- 파티션 테이블의 마스터 참조이므로 FK 금지 (db-partitioning-and-retention.md §1)" 주석 명시. 후속 개발자가 마스터-마스터 관계 가능성을 오해하지 않도록

## DB 설계 변경

### 1. 신규 테이블 10건 (DDL 신설)

| 테이블 | 변경 유형 | 비고 |
|--------|----------|------|
| `pump_m`·`pwtf_m`·`dwt_m`·`pump_cmbn_m`·`pump_cmbn_d`·`pump_interlock_p`·`ai_drvn_mod_p` | CREATE TABLE | 일반 테이블. FK 정상 생성 (마스터 간·마스터-상세·명세) |
| `pump_ctrl_h` | CREATE TABLE PARTITION BY RANGE(`ctrl_dtm`) | **마스터 FK 금지** — 컬럼만 보관 |
| `pump_predc_h` | CREATE TABLE PARTITION BY RANGE(`predc_base_dtm`) | **마스터 FK 금지** |
| `ai_drvn_mod_h` | CREATE TABLE PARTITION BY RANGE(`rgstr_dtm`) | **마스터 FK 금지** |

### 2. FK 생성 정책

**FK 생성 (마스터·상세·명세)**:
- `pump_m.pwtf_id` → `pwtf_m.pwtf_id`
- `pump_cmbn_m.pwtf_id` → `pwtf_m.pwtf_id`
- `pump_cmbn_d.pump_cmbn_cd` → `pump_cmbn_m.pump_cmbn_cd`
- `pump_cmbn_d.pump_id` → `pump_m.pump_id`
- `pump_interlock_p.pump_id` → `pump_m.pump_id`
- `ai_drvn_mod_p.pwtf_id` → `pwtf_m.pwtf_id`

**FK 미생성 (파티션 4건 — `db-partitioning-and-retention.md §1`)**:
- `pump_ctrl_h.pump_id` (논리 참조)
- `pump_predc_h.pwtf_id` (논리 참조)
- `pump_predc_h.pump_cmbn_cd` (논리 참조)
- `ai_drvn_mod_h.pwtf_id` (논리 참조)

### 3. 인덱스

| 테이블 | 인덱스 | 종류 | 컬럼 순서 근거 (`db-indexing-and-migration.md §1`) |
|--------|-------|------|---------------------------------------------------|
| `pump_m` | `pump_m_pwtf_id_idx` | B-Tree | 정수조별 펌프 조회 |
| `pump_cmbn_m` | `pump_cmbn_m_pwtf_id_idx` | B-Tree | 정수조별 조합 조회 |
| `pump_ctrl_h` | `pump_ctrl_h_pump_dtm_idx` | B-Tree `(pump_id, ctrl_dtm DESC)` | 등가→범위·정렬, 카디널리티 높은 컬럼 우선 |
| `pump_ctrl_h` | `pump_ctrl_h_dtm_brin_idx` | BRIN `(ctrl_dtm)` | 분당 다수 INSERT — B-Tree 대비 1/100 크기, 쓰기 오버헤드 최소 |
| `pump_predc_h` | `pump_predc_h_pwtf_dtm_idx` | B-Tree `(pwtf_id, predc_base_dtm DESC)` | 시간당 수 건이라 BRIN 불필요 |
| `ai_drvn_mod_h` | `ai_drvn_mod_h_pwtf_dtm_idx` | B-Tree `(pwtf_id, rgstr_dtm DESC)` | 정수조별 전환 이력 조회 |

### 4. 파티션 운영

- **선행 생성**: 6개월치 사전 생성 + 매월 1일 자정 스케줄러로 자동 추가 (`scheduler` 모듈)
- **보존 정책** (`db-partitioning-and-retention.md §2` 갱신 반영):
  - `pump_ctrl_h`: 2년 → 파티션 DROP
  - `pump_predc_h`: 3년 → 파티션 DROP
  - `ai_drvn_mod_h`: 5년 → 파티션 DROP
- **파티션 삭제 스케줄러**: 매일 자정 보존 기간 초과 파티션 DROP
- **장애 복구 절차 (DBA 참고 — 2026-04-25)**: 파티션 선행 생성 스케줄러 장애 발생 시 다음 명령으로 수동 복구. TASK 의 운영 체크리스트로 등록.

  ```sql
  -- 다음 달 파티션 수동 생성 예시 (pump_ctrl_h)
  CREATE TABLE pump_ctrl_h_202607 PARTITION OF pump_ctrl_h
      FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
  CREATE INDEX idx_pump_ctrl_h_202607_pump_dtm ON pump_ctrl_h_202607 (pump_id, ctrl_dtm DESC);
  CREATE INDEX idx_pump_ctrl_h_202607_brin ON pump_ctrl_h_202607 USING BRIN (ctrl_dtm);
  ```

- **`ai_drvn_mod_h` BRIN 후속 판단 (DBA 참고 — 2026-04-25)**: 본 작업은 B-Tree `(pwtf_id, rgstr_dtm DESC)` 만 적용. 5년 누적 후 파티션당 행이 많아지면 `rgstr_dtm` 단독 BRIN 추가 검토 (현 시점 블로커 아님)

### 5. 무중단 마이그레이션

본 작업은 신규 테이블 생성만 다루며 기존 테이블 변경 없음. `db-indexing-and-migration.md §2` NOT NULL 컬럼 추가 3단계 절차 해당 없음.

## 구현 방향

### 1. 패키지 구조

```
common/src/main/java/com/mo/swtp/
├── pump/
│   └── domain/
│       ├── Pump.java                  ← Persistable<String>
│       ├── PurifiedWaterTank.java
│       ├── DistributionWaterTank.java
│       ├── PumpCmbn.java
│       ├── PumpCmbnDetail.java
│       ├── PumpControlHistory.java    ← 시계열 (FK 없음)
│       ├── PumpPredictionResult.java  ← 시계열
│       ├── PumpInterlock.java
│       ├── PumpControlDivision.java   ← enum: MANUAL/AUTO
│       └── PumpControlResult.java     ← enum: SUCCESS/WAITING/FAIL
└── ai/
    └── domain/
        ├── AiDrvnMode.java
        ├── AiDrvnModeHistory.java     ← 시계열
        ├── AiDrvnModeType.java        ← enum: AI/AI_RECOMD/AI_ANLS
        └── AiSystemModeCode.java      ← enum: 0/1/2

api/src/main/java/com/mo/swtp/
├── pump/
│   ├── web/PumpControlController.java
│   ├── service/
│   │   ├── PumpControlService.java       ← 오케스트레이터
│   │   ├── PumpDashboardService.java     ← 대시보드 조회
│   │   └── InterlockValidator.java       ← 규칙 미등록 시 통과
│   ├── repository/
│   │   ├── PumpRepository.java
│   │   ├── PurifiedWaterTankRepository.java
│   │   ├── DistributionWaterTankRepository.java
│   │   ├── PumpCmbnRepository.java
│   │   ├── PumpControlHistoryRepository.java
│   │   └── PumpPredictionResultRepository.java
│   ├── dto/
│   │   ├── PumpDashboardDto.java
│   │   ├── PumpControlRequestDto.java
│   │   └── PumpControlResultDto.java
│   └── exception/PumpErrorCode.java
├── ai/
│   ├── service/
│   │   ├── AiModeService.java            ← 모드 변경·조회
│   │   └── AiPredictionService.java      ← Python AI HTTP 연동
│   ├── repository/
│   │   ├── AiDrvnModeRepository.java
│   │   └── AiDrvnModeHistoryRepository.java
│   ├── dto/
│   │   ├── AiModeUpsertDto.java
│   │   └── AiPredictionResponseDto.java
│   ├── client/AiServerClient.java        ← RestClient + resilience4j
│   └── exception/AiErrorCode.java
└── scada/
    └── outbound/
        ├── ScadaOutboundPort.java        ← 인터페이스
        ├── ModbusTcpScadaAdapter.java    ← @Profile("modbus") 또는 지자체별
        ├── OpcUaScadaAdapter.java        ← @Profile("opcua")
        ├── dto/ControlCommandDto.java
        └── exception/ScadaErrorCode.java

scheduler/src/main/java/com/mo/swtp/scheduler/
├── pump/
│   ├── PumpPartitionScheduler.java      ← 월 1일 파티션 선행 생성
│   ├── PumpPartitionDropScheduler.java  ← 매일 자정 보존 초과 DROP
│   └── AiModeTransitionScheduler.java   ← SCADA 5분 초과 감지 → ai_mode_cd 강제 전환
└── config/PumpSchedulerConfig.java
```

### 2. API 명세 (3건)

| Method | Path | 요청 | 응답 | 책임 |
|--------|------|------|------|------|
| GET | `/api/pump/dashboard?pwtfId={id}` | — | `PumpDashboardDto` (펌프 운전 상태·예측·정수조 수위·배수지 압력) | `PumpDashboardService.getDashboard()` |
| PUT | `/api/pump/ai-mode` | `AiModeUpsertDto`(pwtfId, aiDrvnMod) | `CommonResponseDto<Void>` | `AiModeService.changeUserIntent()` — `ai_drvn_mod` 만 변경, `ai_mode_cd` 불변 |
| POST | `/api/pump/auto-control` | `PumpControlRequestDto`(pwtfId, pumpCmbnCd, ctrlDiv) | `PumpControlResultDto`(ctrlRslt) | `PumpControlService.executeControl()` — 인터록 검사 → AI 예측(필요 시) → SCADA 송신 → 이력 저장 |

### 3. 3-Service 오케스트레이터 흐름

```
PumpControlController.executeControl()
  ↓
PumpControlService.executeControl(req)
  ├→ InterlockValidator.validateOrThrow(pumpId, cmd)  ← 규칙 없으면 통과 (§6 stub)
  ├→ AiPredictionService.requestPrediction(pwtfId)   ← AUTO 모드일 때만
  │     └→ AiServerClient.fetch(req)                 ← RestClient + @CircuitBreaker + @Retry
  │     └→ (CircuitBreaker OPEN 시 fallback) → 안전 정지 트리거
  ├→ ScadaControlService.send(cmd)                   ← @Profile 분기 어댑터
  │     └→ ScadaOutboundPort.send(cmd)
  │     └→ try/catch: ScadaOutboundException 발생 시
  │           ├→ AiModeService.forceTransition(pwtfId, OUTBOUND_FAIL)  ← ai_mode_cd 1→0(수동) 강제 전환
  │           │     └→ AiDrvnModeRepository 갱신 (ai_drvn_mod 불변, ai_mode_cd 만 변경)
  │           │     └→ AiDrvnModeHistoryRepository.save(transition_reason='OUTBOUND_FAIL')
  │           └→ ScadaErrorCode.SCADA_OUTBOUND_FAILED 재던지기
  └→ PumpControlHistoryRepository.save(history)      ← 결과 기록 (ctrl_rslt='SUCCESS' or 'FAIL')
```

**SCADA 실패 처리 책임 (도메인 expert 검토 — 블로커 1 해소)**:
- `ScadaControlService.send()` 가 예외 던지면 `PumpControlService` 의 catch 블록에서 **반드시** `AiModeService.forceTransition()` 호출
- `AiModeService.forceTransition(pwtfId, reason)` 내부:
  1. `ai_drvn_mod_p` 의 `ai_mode_cd` 강제 갱신 (1→0 또는 1→2). `ai_drvn_mod` 불변 (사용자 의도 보존)
  2. `ai_drvn_mod_h` 행 추가 (`prev_*`/`new_*` + `transition_reason`)
- `transition_reason` 값 4종: `USER_SELECT`(API 호출) / `SCADA_TIMEOUT`(스케줄러 5분 초과) / `MANUAL_EXPIRE`(수동 만료) / `OUTBOUND_FAIL`(PLC 송신 실패)
- 진행 중 AI 자동 운전 세션은 안전 정지(Safe Stop) 시퀀스 호출 후 수동 모드 (`ai_mode_cd=0`) 로 전환

**제어 실패 → 알람 발행 경계 (도메인 expert 참고 — 알람 도메인 분리)**:
- 본 작업은 `pump_ctrl_h.ctrl_rslt='FAIL'` 기록까지만 수행 (알람 발행 미포함)
- 별도 작업 `ot_integration_inbound` 의 알람 4단계 도입 시 `PumpControlHistory` 저장 이벤트(`DomainEventEntity`) 를 구독하여 알람 발행. 본 작업은 이벤트 발행 인프라(`@DomainEvents` + `ApplicationEventPublisher`) 만 준비하고 구독자는 후속 작업이 추가

### 3.5. AI 운전 모드 별 허용 오퍼레이션 (도메인 expert 권고 해소)

`ai_mode_cd` (시스템 상태) 별로 사용자 API 의 허용·차단 오퍼레이션을 명확히 한다. 본 정책은 `PumpControlService.executeControl()` 진입 시 모드 검증으로 강제한다.

| 시스템 상태 | 허용 오퍼레이션 | 차단 오퍼레이션 (`MANUAL_INPUT_DENIED_IN_AUTO_MODE` 등) |
|-----------|---------------|---------------------------------------------------|
| `0` 수동 | 직접 펌프 ON/OFF, 직접 주파수 입력, 펌프 조합 선택 | — |
| `1` AI 자동 | AI 모드 변경 (`AI`/`AI_RECOMD`/`AI_ANLS`), 펌프 조합 조회 | 직접 펌프 ON/OFF, 직접 주파수 입력 (AI 가 송신 권한 보유) |
| `2` 반자동 | 펌프 조합 선택 (AI 추천 또는 수동 선택), AI 모드 변경 | 직접 주파수 입력, 단일 펌프 직접 제어 (조합 단위만 허용) |

**검증 위치**: `PumpControlService.executeControl()` 진입부에 `AiModeService.assertOperationAllowed(pwtfId, requestedOperation)` 호출. 위반 시 `PumpErrorCode.MANUAL_INPUT_DENIED_IN_AUTO_MODE` 또는 `PumpErrorCode.OPERATION_NOT_ALLOWED_IN_SEMI_AUTO` 던짐.

**`expire_dtm` 만료 복구 시 `ai_mode_cd` 목표 값 (도메인 expert 참고 — 낮음)**:
- 만료 시 복구 값은 `ai_drvn_mod` 기반 재결정 — `AI`/`AI_RECOMD` 면 `ai_mode_cd=1`, `AI_ANLS` 면 `ai_mode_cd=2` 로 복구. 만료 직전 값 환원이 아님.
- `ai_drvn_mod_h` 의 `prev_*`/`new_*` 에 만료 직전과 복구 후 값 모두 기록

### 4. Python AI 연동

- `RestClient` (Spring 6.1+) 사용
- 회복성: `resilience4j-spring-boot3` 의존성 추가
  - `@CircuitBreaker(name="aiPrediction", fallbackMethod="fallbackPrediction")` failureRateThreshold=50%, waitDurationInOpenState=30s
  - `@Retry(name="aiPrediction")` maxAttempts=3, waitDuration=500ms
- 설정: `resources-env/{지자체}/application.yml` 의 `ai.server.base-url` 주입
- Fallback: `AiPredictionService.fallbackPrediction()` → 직전 마지막 예측 반환 또는 안전 정지 트리거

### 5. SCADA 아웃바운드 어댑터 (`@Profile` 분기)

- `ScadaOutboundPort` 인터페이스 정의 (`send(cmd)`, `query(tag)` 등)
- `@Profile("modbus")` `ModbusTcpScadaAdapter`
- `@Profile("opcua")` `OpcUaScadaAdapter`
- 로컬 개발용 `@Profile("dev")` `LoggingNoOpScadaAdapter` (실제 송신 없이 로그만)
- `multi-tenant.md §4` 전략 2 적용 — 지자체별 빌드 시 `-Pprofile=gs` 와 별개로 `spring.profiles.active=modbus|opcua` 로 어댑터 선택

### 6. 인터록 stub 구조

```java
@Component
@RequiredArgsConstructor
public class InterlockValidator {
    private final PumpInterlockRepository interlockRepository;

    /**
     * 인터록 선행조건 검사. 모든 제어 명령(최초 송신·재시도·장애 복구 후 재시도) 진입 시 호출.
     *
     * <p><b>설계 경계 (도메인 expert 검토 — 블로커 2 해소)</b>:
     * <ul>
     *   <li>"규칙 미등록 시 통과" 는 stub 기간 한정 정책 — 빈 테이블 허용은
     *       {@code ot_integration_inbound} 후속 작업으로 인터록 규칙 데이터가
     *       투입되기 전까지의 임시 동작이다.</li>
     *   <li><b>장애 복구 후 재검사 스킵 절대 금지</b> ({@code ot-integration.md §5}):
     *       CircuitBreaker CLOSED 복구 후 재시도 흐름에서도 본 메서드는 동일하게
     *       호출되며, 규칙이 등록되어 있으면 반드시 재평가한다. 캐시 등으로 검사
     *       자체를 스킵하지 않는다.</li>
     *   <li>"규칙 미등록 통과" 와 "검사 스킵" 은 다른 개념 — 전자는 평가 대상 0건의
     *       자연 통과, 후자는 평가 자체를 건너뛰는 금지 패턴.</li>
     * </ul>
     */
    public void validateOrThrow(String pumpId, ControlCommandDto cmd) {
        List<PumpInterlock> rules = interlockRepository.findByPumpIdAndUseYn(pumpId, YnType.Y);
        if (rules.isEmpty()) return;  // 규칙 미등록 시 통과 (stub 정책)
        rules.forEach(rule -> evaluate(rule, cmd));
    }
}
```

### 7. SCADA 5분 초과 강제 전환 스케줄러

```
@Scheduled(fixedDelay = 60_000)  // 매 1분
AiModeTransitionScheduler.checkAndTransition()
  ↓
ai_drvn_mod_p 전체 조회
  ↓ 각 행마다
  if (now() - last_rcv_dtm > 5min && ai_mode_cd == 1) {
    ai_mode_cd ← 2 (반자동)
    ai_drvn_mod 불변
    ai_drvn_mod_h 행 추가 (transition_reason='SCADA_TIMEOUT')
  }
  if (expire_dtm != null && now() > expire_dtm) {
    수동 제어 만료 → ai_mode_cd 복구
    ai_drvn_mod_h 행 추가 (transition_reason='MANUAL_EXPIRE')
  }
```

### 8. 예외 처리

도메인별 ErrorCode enum 3건 생성. `httpStatus(int)` 만 허용 (`exception-patterns.md §2`). `String message` 필드 절대 금지 (`check-errorcode-contract.sh` 훅 차단).

| ErrorCode | 코드값 예시 |
|-----------|-----------|
| `PumpErrorCode` | `PUMP_NOT_FOUND(404)`, `INVALID_PUMP_STATE(400)`, `INTERLOCK_VIOLATION(400)`, `MANUAL_INPUT_DENIED_IN_AUTO_MODE(400)` |
| `AiErrorCode` | `AI_PREDICTION_FAILED(503)`, `AI_SERVER_TIMEOUT(504)`, `INVALID_AI_MODE(400)` |
| `ScadaErrorCode` | `SCADA_OUTBOUND_FAILED(503)`, `CIRCUIT_BREAKER_OPEN(503)` |

## 테스트 전략

### 단위 테스트 (Mockito 기반, `test-strategy.md §1`)

대상 모듈: `api`, `common`. 명령: `./gradlew.bat :api:test :common:test`

| 테스트 클래스 | 검증 항목 |
|-------------|----------|
| `PumpControlServiceTest` | 오케스트레이터 흐름 (인터록 → 예측 → SCADA → 이력) `verify` |
| `AiModeServiceTest` | `ai_drvn_mod` 변경 시 `ai_mode_cd` 불변 검증, `ai_drvn_mod_h` 행 추가 `verify` |
| `AiPredictionServiceTest` | RestClient mock + CircuitBreaker fallback 호출 검증 |
| `InterlockValidatorTest` | 규칙 미등록 시 통과, 규칙 위반 시 `RestApiException(INTERLOCK_VIOLATION)` |
| `AiModeTransitionSchedulerTest` | 5분 초과 시 강제 전환 + `ai_drvn_mod_h` 기록 + `ai_drvn_mod` 불변 |
| `PumpTest` / `AiDrvnModeTest` | 정적 팩토리 `create()`, 변경 메서드 `changeInfo()` |

### 도메인 시나리오 테스트 (`test-strategy.md §3`, §5.2)

본 작업으로 구현하는 필수 시나리오 2종:

| 클래스 | 케이스 (모두 의무) |
|-------|------------------|
| `PumpInterlockScenarioTest` | ① 흡입압력 미충족 시 차단 ② 다중 센서(FRI·LEI) 위반 케이스 ③ 모든 선행조건 만족 시 허용 ④ 장애 복구 후 재검사 통과 ⑤ 장애 복구 후 재검사 실패 |
| `PumpOperationModeScenarioTest` | ① AI 자동 모드에서 수동 입력 거부 ② 수동 모드 전환 시 진행 중 AI 명령 취소 ③ 반자동 모드 펌프 조합 선택만 허용 ④ SCADA 5분 초과 강제 전환 |

> `AlarmEscalationScenarioTest` 는 알람 4단계 도메인이 본 작업 외(`ot_integration_inbound`) 라 본 작업 범위에서 제외.

### 통합 테스트 (`test-strategy.md §2`)

- `PumpControlIntegrationTest` (`@SpringBootTest(NONE) + @Transactional`) — 핵심 흐름 1건 (대시보드 조회 → AI 모드 변경 → 자동 제어 실행). 로컬 PostgreSQL 전제, 시계열 파티션 사전 생성 필요
- 단, 본 작업은 단위 테스트 중심. 통합은 최소 1건만.

### 검증 명령

```bash
./gradlew.bat :common:test     # 엔티티·도메인 패턴 단위 테스트
./gradlew.bat :api:test        # 서비스·시나리오·통합 테스트
./gradlew.bat :scheduler:test  # 파티션·전환 스케줄러 단위 테스트
./gradlew.bat clean build      # QClass 재생성 + 전체 빌드
```

## 제외 사항

- **`ot_integration_inbound` 별도 작업**: SCADA 인바운드 어댑터·센서 품질 관리(GOOD/BAD/UNCERTAIN)·결측 대체값(Hold Last Value)·알람 4단계 체계(`alarm_h`)·실제 인터록 규칙 데이터 투입·`AlarmEscalationScenarioTest`
- **`dwt_pressure_history` 별도 작업**: 배수지 분기별 요구 압력 이력 (`dwt_prsr_setn_h`)
- **`tag_m` 마스터 도입 작업**: `pump_m.tag_se_cd` 컬럼 추가, `TagMeasurementType` Java enum 위치 결정 (`com.mo.swtp.tag` vs `common`)
- **Flyway 마이그레이션 도구 도입**: 본 작업은 JPA `ddl-auto=validate` + 수동 SQL 스크립트
- **Testcontainers·E2E 도입**: `test-strategy-e2e-roadmap.md` 별도 ANALYZE 필요

## 예상 산출물

- [태스크](../../../tasks/20260425/pumpcontrol/TASK1.md)

## 부록: 도메인/DB 검토 결과 (2026-04-25)

### wtp-domain-expert
- **블로커(높음) 2건 → 모두 PLAN 보강으로 해소**:
  1. SCADA 실패 시 강제 모드 전환 + `ai_drvn_mod_h` 기록 흐름 누락 → §3 오케스트레이터 흐름에 SCADA 실패 catch 분기 + `AiModeService.forceTransition()` + `transition_reason='OUTBOUND_FAIL'` 명시
  2. 인터록 stub "규칙 미등록 통과" vs "장애 복구 후 재검사 절대 금지" 경계 미명시 → §6 `InterlockValidator` Javadoc 에 설계 경계 4항 명시
- **권고(중간) 1건 → 해소**: 반자동 모드 허용 오퍼레이션 목록 → §3.5 신규 추가
- **참고(낮음) 2건 → 해소**: `expire_dtm` 만료 복구 시 `ai_mode_cd` 목표 값 (§3.5 말미), 제어 실패→알람 발행 경계 (§3 말미)

### wtp-dba-reviewer
- **블로커 0건**
- **권고(중간) 3건 → 모두 PLAN 보강으로 해소**:
  1. `ai_drvn_mod_h` SEQUENCE allocationSize=10 분리 → §시계열 시퀀스 PK 표 갱신
  2. `@Cacheable` stale 방지 `@CacheEvict` 보완 → §파티션 마스터 FK 금지 원칙 항목에 명시
  3. DDL 작성 시 파티션 테이블의 마스터 참조 컬럼 옆 FK 금지 사유 주석 의무 → §파티션 마스터 FK 금지 원칙 말미 추가
- **참고(낮음) 2건 → 해소**: 파티션 스케줄러 장애 복구 체크리스트 (§4), `ai_drvn_mod_h` BRIN 후속 판단 메모 (§4)
