# OT(Operational Technology) 연동 가이드

현재 OT 연동(SCADA 수신, PLC 제어)은 **미구현** 상태다.
이 문서는 향후 도입 시 지켜야 할 구조·패턴·안전 기준을 사전 확립한다.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`db/partitioning-and-retention.md`](db/partitioning-and-retention.md) | 수집 데이터 보존 기간·파티션 정책 (§1·§2) |
| `legacy/ems/src/main/java/kr/co/mindone/ems/kafka/` | Kafka 기반 SCADA 수집 레거시 참고 |

> 본 문서는 기술 계층(채널·회복성)과 비즈니스 계층(품질·장애 동작)을 함께 다룬다. OT 도입 ANALYZE 단계에서 분리 검토.

---

## 1. 인바운드 어댑터 (SCADA → Spring) `[기술 계층]`

### 지원 채널

| 채널 | 사용 시점 | 레거시 선례 |
|------|----------|------------|
| **Apache Kafka** | 기존 SCADA 미들웨어가 Kafka 브로커에 발행하는 경우 | `ems/kafka/KafkaConsumerService` |
| **MQTT** | 경량 IoT 브로커(Mosquitto·EMQ X) 직접 연동 시 | 신규 도입 |
| **OPC-UA Subscribe** | OPC-UA 서버를 운영하는 SCADA 연동 시 | 신규 도입 |

### 패키지 구조

```
com.mo.swtp.scada
├── inbound
│   ├── kafka
│   │   ├── ScadaKafkaConsumer.java          ← @KafkaListener, raw 메시지 수신
│   │   └── ScadaKafkaConsumerConfig.java
│   ├── mqtt
│   │   └── ScadaMqttInboundAdapter.java
│   └── dto
│       └── ScadaRawMessageDto.java          ← 수신 원시 메시지 매핑
├── processor
│   └── ScadaMessageProcessor.java          ← 품질 검사 → 저장 파이프라인
└── outbound
    └── ...
```

### 수신 파이프라인

```java
// 원시 수신 → 품질 검사 → 정규화 → rawdata_1m_h 저장
@KafkaListener(topics = "${scada.kafka.topic}")
public void consume(ScadaRawMessageDto msg) {
    ScadaQualityResult qr = qualityChecker.check(msg);
    if (qr.isRejected()) return;          // 이상치 기각
    ScadaNormalizedDto normalized = normalizer.normalize(msg, qr);
    rawDataRepository.save(normalized);
}
```

> 저장 대상 테이블: `rawdata_1m_h` (`acq_dtm` 파티션 키, `db/partitioning-and-retention.md §1` 참조).

---

## 2. 아웃바운드 어댑터 (Spring → PLC) `[기술 계층]`

> **⚠️ 본 절 보류** (pump+AI 백지화 사이클 1 — 2026-05-12)
> `ScadaOutboundPort` 인터페이스 + Modbus/OpcUa/NoOp 어댑터 3종 + `ScadaControlService` + `ScadaOutboundConfig` + `ScadaErrorCode` 모두 백지화 완료. 본 절 본문의 `PumpControlService` 호출자 흐름 · `@Profile` 분기 구현 · 인터록 선행조건 의무 체크 코드 예시 · `pump_interlock_p` 참조는 무효. OT 아웃바운드 구조의 재설계는 사이클 2 (`/dev:analyze`) 의 5인 회의에서 처음부터 결정한다. 정수장 안전 도메인 패턴 (`coding-discipline.md §2.5` 면책 영역) 으로 사이클 2 가 본 절을 재작성한다.

> **구현 상태**: pumpcontrol 작업(ANALYZE1, 2026-04-25) 에서 **최초 도입**. `ScadaOutboundPort` 인터페이스 + `@Profile` 분기 구현(`multi-tenant.md §4` 전략 2). 한국 지자체 PLC 환경 — LS Electric(Modbus TCP) · Siemens S7(OPC-UA) — 혼재 대응.

### 지원 채널

| 채널 | 사용 시점 |
|------|----------|
| **Modbus TCP** | PLC가 Modbus 레지스터를 노출하는 경우 |
| **OPC-UA Write** | OPC-UA 서버가 Write 노드를 허용하는 경우 |

### 패키지 구조

```
com.mo.swtp.scada.outbound
├── modbus
│   ├── ModbusTcpOutboundAdapter.java
│   └── ModbusTcpConfig.java
├── opcua
│   └── OpcUaWriteAdapter.java
└── dto
    └── ControlCommandDto.java             ← 제어 명령 모델
```

### 인터록 선행조건 의무 체크

아웃바운드 제어 명령 발행 전 **반드시** 인터록 선행조건을 검사해야 한다.

```java
public void sendControlCommand(ControlCommandDto cmd) {
    // 1. 인터록 선행조건 검사 (pump_interlock_p 테이블 기준)
    interlockValidator.validateOrThrow(cmd.getEquipmentId(), cmd.getCommandType());
    // 2. 회복성 래퍼를 통한 송신
    circuitBreaker.executeRunnable(() -> plcAdapter.send(cmd));
}
```

> 인터록 규칙 테이블: `pump_interlock_p` (pumpcontrol 작업 ANALYZE1 — 2026-04-25 — 에서 최소 stub 구조로 도입). **규칙 미등록 시 통과(빈 테이블 허용)** — `InterlockValidator` 는 "규칙 미등록 시 통과, 있으면 평가" 로직으로 구현. 실제 규칙 데이터 투입은 `ot_integration_inbound` 별도 작업 범위.

### Pump = Instrument 자식 정합성 (마스터도메인설계 ANALYZE1 Round 2/3, 2026-05-02/03)

마스터도메인설계 ANALYZE1 결정으로 `pump_m` 단일 마스터는 `instrument_m` (단일 마스터) 의 JPA JOINED 자식 (`equip_type_cd = 'PUMP'`) 으로 통합 예정 (`com.mo.swtp.instrument` 패키지). 이에 따라:

- **`InterlockValidator` 의 `equipmentId`** 는 `instrument_id` 를 받는다. 자식 종류는 `equip_type_cd` 필터로 제한 — 펌프 인터록 평가 시 `equip_type_cd = 'PUMP'` 필터 강제 (자식 종류별 도메인 룰 혼선 방지)
- **`pump_interlock_p` 의 `pump_id` FK** → `instrument_id` FK 로 변경 예정 (PLAN approved 후 마이그레이션)
- **센서 태그 참조** (`sensor_tag_*` 패턴 도입 시) 는 `tag_srl_no` (마스터도메인설계 ANALYZE1 Round 3 결정 자연키 PK) 를 논리 참조 — `tag_id` 폐기

> 본 절은 `entity-patterns.md` §JPA JOINED + DiscriminatorColumn 다형성 패턴 의 도메인 룰 — `equip_type_cd` 필터 강제 와 정합한다.

---

## 3. 센서 품질 관리 `[데이터 처리 정책]`

SCADA 수신 데이터는 QUALITY 필드를 기준으로 3단계 처리를 거친다.

> **측정 유형 코드 SSOT 안내** (FRI·PRI·LEI·PWI·RMS·OPS·VOI·FQI): 본 문서는 결측 대체값 정책·품질 관리 정책의 SSOT 다. 컬럼·코드값의 enum 정의는 **표준 용어 사전 `tag_se_cd`** ([`dict/standard-terms.md`](dict/standard-terms.md)) 와 **`TagMeasurementType` Java enum** (`com.mo.swtp.tag.domain.enumtype.TagMeasurementType`) 에서 관리한다. 본 문서는 enum 정의를 중복하지 않고 정책만 기술한다.

### QUALITY 코드

| 코드 | 의미 | 처리 |
|------|------|------|
| `GOOD` (0) | 정상 측정값 | 그대로 저장 |
| `BAD` (1) | 센서·통신 장애 | 결측 처리, 대체값 적용 |
| `UNCERTAIN` (2) | 신뢰도 낮음 (전환 중·범위 경계) | 로그 기록 후 저장 (집계 시 가중치 0.5) |

### 결측 대체값 기준

| 측정 항목 | 결측 대체값 | 근거 |
|-----------|-----------|------|
| 유량 (FRI) | 직전 Good 값 (Hold Last Value) | 순간 중단 시 운전에 영향 없음 |
| 압력 (PRI) | 직전 Good 값 | 동일 |
| 수위 (LEI) | 직전 Good 값 | 동일 |
| 전력 (PWI) | `null` 저장 + 집계 제외 | 전력 집계 오염 방지 |
| 진동 (RMS) | `null` 저장 | PMS 진단 모델에 미수신 전파 |
| 펌프 가동상태 (OPS) | **즉시 BAD 격상** (Hold Last Value 미적용) | on/off DI 신호 — 직전 Good 값 유지 시 가동 중인 펌프가 통신 단절 후 정지해도 ON 으로 표시되어 운전원 오인 위험. UNCERTAIN 알람 즉시 발생 후 BAD 격상 (송수펌프제어분석 ANALYZE1, 2026-05-08) |
| 밸브 개도율 (VOI) | 직전 Good 값 (Hold Last Value) | FRI/PRI 와 동일 — 개도 변화는 액추에이터 명령 → 모터 회전 시간 (수초~수십 초) 누적이라 통신 단절 1분 미만 동안의 값은 직전 값과 거의 동일. 5분 초과 시 BAD 격상 (송수펌프제어분석 ANALYZE1, 2026-05-08) |
| 적산전력량 (PWQ) | `null` 저장 + 집계 제외 (**Hold Last Value 미적용**) | 적산 누적 미터값 — 직전 Good 값으로 채우면 버킷 내 MAX−MIN 차분이 0 으로 왜곡되어 전력량(kWh) 과소 산정. 전력량 시계열 집계는 **GOOD 품질만 `raw_val` 차분** (corr_val 미사용). PWI(순시전력 kW) 의 "전력 집계 오염 방지" 와 동일 결로이나 적산값 차분 특성상 HLV 가 더 치명적 (송수펌프가동이력_3번섹션 ANALYZE1 안건 4, 2026-06-02) |

> Hold Last Value 지속 시간 한계: 5분 초과 시 BAD로 격상 처리. **단 OPS 는 1분 미만에서도 즉시 BAD 격상** (위 표 참조). **PWQ(적산전력량) 는 HLV 자체를 적용하지 않는다** (차분 왜곡 방지 — 위 표 참조).

### 이상치 기각 기준

물리적 불가능값 또는 설비 정격 ±30% 초과값은 이상치로 기각한다.

```java
boolean isOutlier(String tagNm, double value) {
    TagRangeConfig range = tagRangeConfigRepo.findByTagNm(tagNm);
    return value < range.getPhysicalMin() || value > range.getPhysicalMax();
}
```

이상치 기각 시 `alarm_h`에 `UNCERTAIN` 알람을 기록한다 (알람 1단계).

---

## 4. 회복성 패턴 `[기술 계층 — 회복성]`

### 의존성 도입

OT 연동 모듈 구현 시 다음 의존성을 추가한다.

```groovy
// build.gradle (common 또는 신규 scada 모듈)
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.2.0'
implementation 'io.github.resilience4j:resilience4j-retry:2.2.0'
```

### 적용 계층

| 패턴 | 적용 위치 | 설정 권장값 |
|------|----------|-----------|
| **Retry** | 아웃바운드 어댑터 (PLC 송신) | maxAttempts=3, waitDuration=500ms |
| **CircuitBreaker** | 아웃바운드 어댑터 (PLC 송신) | failureRateThreshold=50%, waitDurationInOpenState=30s |
| **Bulkhead** | 인바운드 처리 스레드 풀 | maxConcurrentCalls=20 |
| **TimeLimiter** | OPC-UA Write (응답 대기) | timeoutDuration=3s |

### 설정 예시

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      plcOutbound:
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        slidingWindowSize: 10
  retry:
    instances:
      plcOutbound:
        maxAttempts: 3
        waitDuration: 500ms
```

---

## 5. 장애 시 동작 기준 `[비즈니스 계층 — 장애 시 운전 모드]`

> **⚠️ 본 절 일부 보류** (pump+AI 백지화 사이클 1 — 2026-05-12)
> AI 운전 모드 이중 체계 (사용자 의도 `ai_drvn_mod` + 시스템 상태 `ai_mode_cd`) · 강제 전환 정책 · `ai_drvn_mod_p`/`ai_drvn_mod_h` 테이블 · `transition_reason` 5종 사유 · 시설 종류별 별도 평가 (PRSF/PWTF) · `facility_type_cd`/`equip_type_cd` 필터 강제 · 모드 전환 이력 기록 의무 등 본 절의 AI 운전 모드 관련 내용은 모두 무효. 인바운드 중단 시 알람 격상 정책 (위 표) · OT 안전 정지 시퀀스 · 인터록 재검사 의무 (`⚠️ 절대 금지` 규정) 는 사이클 2 의 재설계 기준선으로 유지된다. 정수장 안전 도메인 패턴 (`coding-discipline.md §2.5` 면책 영역) 으로 사이클 2 가 본 절을 재작성한다.

### 인바운드 중단 (SCADA 수신 불가)

| 지속 시간 | 처리 |
|----------|------|
| < 1분 | Hold Last Value, 정상 운전 유지 |
| 1분 ~ 5분 | UNCERTAIN 알람 발생 (알람 1단계), HMI 경고 표시 |
| > 5분 | BAD 알람 격상 (알람 2단계), AI 자동 운전 → 반자동 강제 전환 |

> 알람 4단계: `0` 정상 · `1` 주의 · `2` 경보 · `3` 위험/TRIP.

### AI 운전 모드 이중 체계와 강제 전환 정책 (pumpcontrol ANALYZE1, 2026-04-25)

AI 운전 모드는 **사용자 의도** 와 **시스템 상태** 두 축을 분리하여 관리한다. 두 축은 `ai_drvn_mod_p` 테이블에 함께 저장된다.

| 축 | 컬럼 | 값 | 변경 주체 |
|----|------|-----|----------|
| 사용자 의도 | `ai_drvn_mod` | `AI` / `AI_RECOMD` / `AI_ANLS` | 사용자 API 만 변경 |
| 시스템 상태 | `ai_mode_cd` | `0`(수동) / `1`(AI자동) / `2`(반자동) | 스케줄러·장애 대응만 변경 |

**강제 전환 정책**:
- SCADA 수신 5분 초과 중단 시 → `ai_mode_cd` 만 `1`(AI자동) → `2`(반자동) 강제 전환. **`ai_drvn_mod` 는 불변** (사용자 의도 보존, 복구 시 자동 환원)
- `AI_ANLS`(분석 모드) 선택 중에도 `ai_mode_cd` 는 영향 없음 — 분석 모드는 AI 제어 명령 억제만 수행
- `ai_drvn_mod_p.last_rcv_dtm` (마지막 SCADA 수신 시각) 을 스케줄러가 주기 비교하여 강제 전환 판정. heartbeat 별도 구현 불필요.

**시설 종류별 별도 평가** (송수펌프제어분석 ANALYZE1, 2026-05-08):
- `ai_drvn_mod_p` 행은 **시설 단위** 로 분리되어 있으며, **PRSF 가압장도 PWTF 정수지와 동일한 절차** 로 별도 평가된다. 정수지/가압장이 같은 SCADA 수신 경로를 공유하더라도 시설 단위 `last_rcv_dtm` 기준 강제 전환 판정 + `ai_drvn_mod_h` 이력 기록은 시설별로 독립.
- PRSF 자식 마스터 (`prsf_m`) 가 본 사이클에서 신규 도입되며, 기존 PWTF 시설 단위 ai_drvn_mod_p 처리 코드가 `facility_type_cd IN ('PWTF', 'PRSF')` 필터로 PRSF 도 동일하게 평가하도록 PLAN 단계에서 검토.
- **PRSF 의 `pump_interlock_p` 룰이 PWTF 와 동일한지 여부는 미결정** — 시설 종류별 룰 분리 필요 시 별도 사이클에서 `pump_interlock_p.facility_type_cd` 컬럼 신설 또는 룰 ID 명명 규칙으로 분리 검토. 본 사이클은 시설 종류별 룰 분리 없이 진행.

**`facility_type_cd` / `equip_type_cd` 필터 강제** (마스터도메인설계 ANALYZE1 Round 3, 2026-05-03):
- AI 운전 모드 평가·제어 명령 발행 등 자식 종류별 도메인 룰이 다른 시나리오에서는 부모 다형성 조회 시 반드시 `facility_type_cd` (`facility_m`) 또는 `equip_type_cd` (`instrument_m`) 필터를 명시한다 — 자식 종류 (PWTF/DWT/RSV/PUMP/VALVE/...) 마다 인터록 룰·운전 모드 평가 기준이 다르므로 부모 전체 조회는 도메인 룰 위반 위험.
- `ai_drvn_mod_p` 의 `pwtf_id` 컬럼은 PLAN approved 후 `facility_id` 로 정렬 (자식 PK 부모 PK 동일, JPA JOINED 표준).
- 본 룰의 1차 정의처: [`entity-patterns.md`](entity-patterns.md) §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰 — `facility_type_cd` 필터 강제

### 아웃바운드 실패 (PLC 제어 불가)

1. CircuitBreaker OPEN 상태에서 제어 명령 수신 시 즉시 `RestApiException` 반환 (사용자에게 에러 응답)
2. 진행 중인 AI 자동 운전 세션은 **안전 정지(Safe Stop)** 시퀀스 실행 후 수동 모드 전환
3. 장애 이벤트를 `pump_ctrl_h`에 기록 (`ctrl_rslt = 'FAIL'`, `db/partitioning-and-retention.md §2` 제어 로그 2년 보존)
4. **모드 전환 이력 기록 의무** (pumpcontrol ANALYZE1, 2026-04-25): 강제 전환·복구·만료 등 `ai_mode_cd` / `ai_drvn_mod` 변경 시 반드시 `ai_drvn_mod_h` 에 행 추가. `transition_reason` 컬럼에 `USER_SELECT` / `SCADA_TIMEOUT` / `MANUAL_EXPIRE` / `OUTBOUND_FAIL` / `SYSTEM_INIT` 사유 기록 (5종 — pumpcontrol_null_alignment ANALYZE1, 2026-04-25 추가). `SYSTEM_INIT` 은 시스템 최초 등록 시 첫 전환 행 표현 — `prev_ai_drvn_mod` / `prev_ai_mode_cd` NULL 허용 (이후 전환은 4종 사유 모두 `prev_*` 채워짐)

> **⚠️ 절대 금지**: 인터록 검사를 건너뛴 채 재시도하는 로직. 장애 복구 후에도 인터록 선행조건을 반드시 재검사해야 한다.

---

## 6. AI 추론 서버 호출 (Spring → Python AI 서버) `[기술 계층 — IT 연동]`

> **⚠️ 본 절 보류** (pump+AI 백지화 사이클 1 — 2026-05-12)
> `AiServerClient` · `AiServerClientConfig` · `AiModeService` · `AiPredictionService` · `AiPredictionRequestDto` · `pump_predc_h` 테이블 모두 백지화 완료. 본 절 본문의 디렉토리 분리 · 엔드포인트 명세 · 호출 패턴 (RestClient + CircuitBreaker + Retry) · 에러 처리 4분기 · 데이터 I/O 책임 분담은 무효. AI 추론 서버 연동 구조의 재설계는 사이클 2 (`/dev:analyze`) 의 5인 회의에서 처음부터 결정한다. ai-server 측 FastAPI 엔드포인트·Pydantic 스키마 SSOT 는 별개 (`swtp/ai-server/` 모듈) — 백엔드 측 호출 클라이언트만 재설계 대상.

> **OT 영역 외부**: 본 절은 SCADA·PLC 와 무관한 AI 마이크로서비스 연동을 다룬다. §1·§2 와 같은 결로 패키지 분리·엔드포인트·회복성을 정의한다.

### 6.1 디렉토리 분리

스마트정수장 monorepo 의 AI 서버는 `swtp/ai-server/` 에 위치한다 (백엔드 Gradle 빌드 컨텍스트 외부, 별도 컨테이너로 배포).

| 경로 | 역할 |
|------|------|
| `ai-server/app/routers/{pms,flow_pressure,power}.py` | FastAPI 라우터 — 도메인별 추론 엔드포인트 |
| `ai-server/app/inference/` | 추론 함수 (모델 로드·전처리·예측, **DB I/O 없음**) |
| `ai-server/app/schemas/` | Pydantic 스키마 — 백엔드 Java DTO 와 1:1 대응 (요청·응답 SSOT) |
| `ai-server/models/` | 학습된 모델 산출물 (`.keras`·`.h5`·`.pkl`) |
| `ai-server/training/` | 학습 코드 — 운영 컨테이너 제외, GPU 환경 별도 실행 |

### 6.2 엔드포인트 명세 (Phase 3 에서 schema 확정 예정)

| 도메인 | HTTP | 경로 | 결과 → 백엔드 저장 테이블 |
|--------|------|------|-------------------------|
| PMS 진단 | `POST` | `/predict/pms/diagnose` | 알람·진단 이력 (테이블 결정 예정) |
| 유량/압력 예측 | `POST` | `/predict/flow_pressure/predict` | `pump_predc_h` |
| 전력 예측 | `POST` | `/predict/power/predict` | `pump_predc_h` (`predc_elpwr_amt`) |
| 헬스체크 | `GET` | `/health` | (저장 없음) |

> 요청·응답 schema 의 SSOT 는 `ai-server/app/schemas/` Pydantic 정의이며, 백엔드 Java DTO(`api/src/main/java/com/mo/swtp/ai/dto/`) 는 이와 1:1 매칭. 양측 변경 시 **단일 PR 로 동기화** (monorepo 의 핵심 이점).

### 6.3 호출 패턴

```java
// Java 측 — RestClient 기반
@CircuitBreaker(name = "aiPrediction", fallbackMethod = "predictFallback")
@Retry(name = "aiPrediction")
public PumpPredictionResponse predictFlowPressure(PumpPredictionRequest req) {
    return restClient.post()
        .uri(aiServerProperties.getBaseUrl() + "/predict/flow_pressure/predict")
        .body(req)
        .retrieve()
        .body(PumpPredictionResponse.class);
}
```

- 호출 클라이언트: `api/src/main/java/com/mo/swtp/ai/client/AiServerClient.java`
- 설정 키: `ai.server.base-url` — 기본값 `${AI_SERVER_BASE_URL:http://localhost:8000}`. 지자체별 차이가 있으면 [`multi-tenant.md`](multi-tenant.md) 의 `resources-env/{profile}/application.yml` 에 오버라이드
- 회복성 인스턴스: `aiPrediction` (failure-rate 50% / wait-duration-in-open-state 30s / retry maxAttempts 3 · waitDuration 500ms)

### 6.4 에러 처리

| 상태 | 처리 |
|------|------|
| `200 OK` | 결과를 도메인 엔티티(`PumpPredictionResult` 등) 로 매핑·저장 |
| `4xx` | 요청 오류 — 즉시 `RestApiException(AI_PREDICTION_BAD_REQUEST)` |
| `5xx` / 타임아웃 | CircuitBreaker 카운터 증가 후 fallback → `RestApiException(AI_PREDICTION_FAILED)` |
| OPEN 상태 | fallback 즉시 호출 — 진행 중 AI 자동 운전은 §5 안전 정지 시퀀스로 진입, `ai_drvn_mod_h` 에 `OUTBOUND_FAIL` 사유 기록 |

### 6.5 학습 vs 추론 분리

- 운영 컨테이너(`ai-server/Dockerfile`) 는 `app/` + `models/` 만 포함. `training/` 은 빌드 컨텍스트에서 제외 (`.dockerignore`).
- 모델 재훈련은 GPU 워크스테이션 등 별도 환경에서 `training/` 하위 스크립트·노트북으로 수행. 산출물(`*.keras`·`*.h5`·`*.pkl`)을 `models/` 에 배치 후 컨테이너 재빌드.
- 모델 버저닝·롤백 정책 (예: MLflow·DVC·S3 모델 레지스트리 도입) 은 별도 ANALYZE 로 결정.

### 6.6 데이터 I/O 책임 분담

| 책임 | 담당 |
|------|------|
| DB SELECT (모델 입력 데이터 조회) | **Java 백엔드** — 표준 컬럼(`raw_val`·`corr_val`·`predc_*` 등) 으로 매핑 후 HTTP body 로 전달. SCADA 원본값 `raw_val` 또는 보정값 `corr_val` 중 추론 입력 정책은 마스터도메인설계 ANALYZE1 Round 3 결정에 따라 PLAN 단계에서 명시 (`tag_val` 폐기) |
| 추론 (모델 로드 → 전처리 → 예측) | **Python AI 서버** — 입력 dict/list 받고 결과 dict 반환, DB 접근 금지 |
| DB INSERT (예측 결과 영속화) | **Java 백엔드** — `AiPredictionService` 에서 `pump_predc_h` 등에 저장 |
| 스케줄링 (주기 추론 트리거) | **Java 백엔드** `scheduler` 모듈 — Python `schedule` 라이브러리·무한 루프 사용 금지 |
