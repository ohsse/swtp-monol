---
status: approved
created: 2026-05-21
updated: 2026-05-21
---
# 운전현황분석-4번섹션 — 도메인 분석

## 작업 배경

운전현황 분석 페이지(`swtp/backend/image/운전현황분석.png`) 의 4번 섹션 "운영 현황" 카드의 backend API 신설.
12번 섹션(활성화된 시설 선택) 은 **본 사이클 스코프 외** — frontend 가 `FacilityController.findAllFacilities()` 결과에서 시설을 선택 후 본 4번 섹션 API 에 식별자 전달.

### 카드 표출 4항목 (사용자 설명)

| 위치 | 표출 내용 | 산출 방법 |
|------|---------|---------|
| 좌측 타이틀 "운영 현황" 옆 시간 | LocalDateTime | 사용된 모든 태그 중 `max(acq_dtm)` |
| 카드 내부 `P#1, P#3` 형식 | 펌프 이름 열거 | 활성 시설의 펌프 중 `OPS = On` 인 펌프 이름 (`Instrument.instrumentNm`) |
| 우측 전력값 | BigDecimal (kW) | On 펌프들의 `PWI` 측정값 합산 |
| 전력원단위 | BigDecimal | (On 펌프 PWI 합산) / (활성 시설의 `FRI` 유출유량 순시값) |

### 사용자 결정 (Plan 모드 진입 전 확정)

- D1: 이전 사이클(2026-05-06/07 `송수펌프제어_운전현황분석`) 의 `02.운전현황 분석 요구사항 명세서_v0.2.docx` **미참조** — 본 메시지 사용자 설명만으로 진행 (백지화 이후 무효 + 사이클 간 자산 자동 원용 금지 메모리 정합)
- D2: 활성 시설 식별자는 **API 파라미터로 frontend 가 전달** (`FacilityController.findAllFacilities()` 결과에서 선택 후 본 API 호출)
- D3: 갱신 방식 = **단발 HTTP GET + 클라이언트 polling**. SSE 미적용
- D4: 카드 시간 표출 = **사용된 태그들 중 `max(acq_dtm)`**, 값 계산은 각 태그의 최신 측정값 사용

### 외부 산출물

- `swtp/backend/image/운전현황분석.png` (4번 섹션) — Read 도구로 직접 시각 로드 완료
- 이전 사이클 `02.운전현황 분석 요구사항 명세서_v0.2.docx` **미참조** (사용자 D1)

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 표준 사전 정합성 (신규 어휘 후보 분류)

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 본 사이클 사용 후보 어휘 8건 검토 결과 **신규 단어 0건 / 신규 데이터 도메인 0건 / 신규 표준 용어 0건**. `stts`(상태)·`running`/`on`·`pwr`·"유출"·"순시"·`sum`/`total` 등은 **등록 거부** 또는 **유사 충돌** — 모두 `oprtng`·`elpwr`·`flwrt`·`acq`·`val` 등 기등록 표준 단어 + Java PascalCase/camelCase 자유 명명 조합으로 충분. `last` 는 `last_rcv_dtm` 일반어 선례 동형 — 등록 불필요. DTO 클래스명은 `naming.md` 의 `{도메인명}Dto` 패턴 정합으로 **`FacilityOperatingStatusDto`** 권장 (`facility` 비즈니스 도메인 prefix 명시).
- **결론**: 표준 사전 갱신 없음. 룰 갱신 지시서 체크박스 0건. DTO 명 `FacilityOperatingStatusDto` 채택.

### 안건 2: OPS 가동상태 On 판정 + 품질코드 처리 정책

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: `raw_val == 1.0` 을 On 판정 (송수펌프제어분석-3번섹션 `PumpStateDto.isRunning` 1.0/0.0/그 외=null 패턴 정합. 본 사이클은 자동 원용이 아닌 `ot-integration.md §3` 직접 인용). **`quality_cd = BAD` OPS 는 On 합산 대상에서 제외 의무** — §3 의 "OPS 즉시 BAD 격상 (Hold Last Value 미적용)" 정책 직결. BAD OPS 를 On 으로 포함하면 운전원 오인 위험. **`UNCERTAIN` OPS 는 §3 의 "가중치 0.5" 정책이 이진 신호에 부적합** — 본 사이클 적용 여부 미해결. 보수적 처리는 **제외** 권고. 1시간 윈도우 내 OPS 데이터 부재 펌프는 On 합산 대상 제외.
- **결론**: OPS 판정 = `raw_val == 1.0 AND quality_cd = GOOD`. `BAD`/`UNCERTAIN`/데이터 부재 펌프는 On 합산에서 제외. `UNCERTAIN` 명시적 처리는 가정 섹션에 미해결로 기재 후 PLAN 단계 결정.

### 안건 3: PWI 합산 + FRI 매핑 위치 + 전력원단위 분모 처리

- 호출 에이전트: `wtp-domain-expert` · `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-domain-expert**: PWI 합산 시 `quality_cd = BAD` PWI 는 §3 "null 저장 + 집계 제외" 정책 직결 — 제외. `UNCERTAIN` PWI 는 §3 가중치 0.5 정책이 순시전력 합산에 오차 유발 위험 — 처리 정책 미해결 (가정 섹션). FRI 매핑 위치는 시설 직접 자식 `equip_type_cd = FLWMTR` 인스트루먼트의 FRI 태그 가정 (`parent_facility_id` 재귀 본 사이클 미적용). 단 실제 현장 배선 확인은 가정 섹션 의무. 분모 0/NULL/BAD/부재 시 응답 필드 `null` 반환 (0 나누기 방어 + 의미 혼동 회피).
  - **wtp-dba-reviewer**: 4회 SELECT 구조 (Facility 1 + Instrument 1 + Tag 1 + RawData 1) — N+1 없음, 송수펌프제어분석-3번섹션 PLAN1 패턴 동일. `tag_m` 의 `WHERE instrument_id IN (...) AND tag_se_cd IN (...)` 조건은 `(instrument_id, tag_se_cd)` 복합 인덱스 활용 권장. 인덱스 존재 여부 PLAN 단계 확인 후 `CREATE INDEX CONCURRENTLY idx_tag_m_instrument_id` 추가 권고(중간).
- **결론**:
  - PWI 합산: `quality_cd = GOOD` PWI 만 합산. `BAD`/`UNCERTAIN`/부재 PWI 는 제외 (단, `UNCERTAIN` 명시 가정 섹션).
  - FRI 매핑: 시설 직접 자식 `FLWMTR` 의 FRI 태그 (현장 배선 가정 — 가정 섹션).
  - 분모 처리: `elpwrUnitQty` 필드 `null` 반환 (FRI = 0/NULL/BAD/부재 시).
  - DBA 권고: `idx_tag_m_instrument_id` 확인 후 미존재 시 PLAN 에 인덱스 추가 TASK 포함.

### 안건 4: 값 선택 정책 (corr_val 우선 vs raw_val 우선)

- 호출 에이전트: `wtp-domain-expert` · `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-domain-expert**: `COALESCE(corr_val, raw_val)` 패턴 — `corr_val IS NOT NULL` 시 보정값 우선, NULL 시 raw_val. Hold Last Value 정책 (§3) 직접 인용 — 보정값이 더 신뢰도 높음. PWI BAD 시 `corr_val = null` + `raw_val` 도 불신뢰값이라 결과적으로 §3 정책대로 합산 제외 도달.
  - **wtp-dba-reviewer**: `RawDataLatestDto` 가 (rawVal, corrVal, acqDtm, qualityCd) 모두 보유하므로 Service 메모리 처리가 자연. DB 측 `COALESCE` 강제 시 DTO 의미 혼탁 + 3번섹션 패턴 불일치.
- **결론**: Service private 헬퍼 `effectiveVal(corrVal, rawVal)` 메서드로 처리. `corrVal != null ? corrVal : rawVal` 단순 분기.

### 안건 5: 시설 종류 범위 + 펌프 연결 경로

- 호출 에이전트: `wtp-domain-expert` · `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 본 카드가 적용될 시설 종류 (PWTF/DWT/RSV/PRSF/POINT 중) 는 UI 컨텍스트에 의존 — frontend 가 어떤 시설 화면에서 본 API 를 호출하는지 명시 부재. 가정 섹션 의무. 펌프 연결 경로 = `instrument_m.facility` FK (직접 자식) — `parent_facility_id` 재귀 본 사이클 미적용. `equip_type_cd = 'PUMP'` 필터 강제 의무 (`entity-patterns.md` §JPA JOINED 다형성 §도메인 룰).
  - **wtp-backend-engineer**: 패키지 `com.mo.swtp.facility.*` 확장 권장 — 별도 `operating` 신설은 단일 카드 대응 추상화 과잉 (`coding-discipline.md §2`).
- **결론**: 펌프 연결 경로 = `instrument_m.facility` 직접 자식 (`equip_type_cd = PUMP` 필터 강제). 본 사이클 시설 종류 범위는 가정 섹션 (PWTF·DWT·PRSF 셋 모두 적용 가능 가정 — PLAN 단계 확정).

### 안건 6: 측정시간(`measurementDtm`) 산출 위치 + 빈 데이터 처리

- 호출 에이전트: `wtp-backend-engineer` · `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: Service 정적 팩토리 조립 단계에서 `stream().map(RawDataLatestDto::getAcqDtm).max(Comparator.naturalOrder()).orElse(null)` 로 산출. Repository max 위임 시 쿼리 수정 불가능한 강결합. 빈 리스트 (OPS 태그 0건 또는 1시간 윈도우 내 부재) → `measurementDtm = null`, `onPumpNms = []`, `totalElpwrAmt = null`, `elpwrUnitQty = null` 응답 (Swagger `@Schema(description)` 에 null 케이스 명시 권고).
  - **wtp-dba-reviewer**: On 펌프 최대 10대 + FRI 1건 = 태그 최대 31건(OPS 10 + PWI 10 + FRI 1) 의 acq_dtm 비교 — Service 메모리 처리 부담 무시 가능.
- **결론**: Service 메모리에서 max 산출. 빈 데이터 시 응답 필드 모두 null 또는 빈 리스트 — Swagger 명세에 null 케이스 명시.

### 안건 7: DTO 구조 + Service 책임 분리 + 정량 기준 점검

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `FacilityOperatingStatusDto` 응답 (facilityId·facilityNm·measurementDtm·onPumpNms·totalElpwrAmt·elpwrUnitQty 6필드) + `FacilityOperatingStatusService` 신규 분리 + `FacilityController.findFacilityOperatingStatus(facilityId)` 메서드 추가. 기존 `FacilityStateService` 메서드 추가는 SRP 위반 — 서비스가 두 가지 응답 조합 책임 (raw 목록 + 합산·계산). `BaseAuditResponseDto` 미상속 (실시간 통지성 — `api-patterns.md` "적용 외" 정합). §2.1 정량 기준 — Service `getOperatingStatus()` 가 6단계 (펌프 조회·태그 조회·rawdata 조회·On 필터링·합산·단위 계산) 직렬 작성 시 50줄 초과 가능 — PLAN 단계 private 메서드 분해 명시 의무. §2.5 면책 미적용 (OT 안전 직결 단일 흐름 아님).
- **결론**:
  - DTO: `FacilityOperatingStatusDto` 6필드 (단층, BaseAuditResponseDto 미상속, `@JsonFormat "yyyy-MM-dd HH:mm:ss"`).
  - Service: `FacilityOperatingStatusService` 신규 분리 — `FacilityStateService` 메서드 추가 거부 (SRP).
  - Controller: `FacilityController` 에 `findFacilityOperatingStatus(facilityId)` 메서드 추가.
  - 엔드포인트: `GET /api/facility/{facilityId}/operating-status`.
  - 패키지: `com.mo.swtp.facility.*` 확장 (별도 `operating` 패키지 신설 거부).
  - 정량 기준: PLAN 단계에서 6단계 private 메서드 분해 명시.

### 안건 8: 도메인 룰 4영역 점검

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4영역 모두 비해당 — 본 사이클은 조회 전용 API. 단 OPS BAD 품질 처리(`§3`) 가 On 판정 로직에 직접 영향 → 안건 2 결론 의무. `ot-integration.md §5 ⚠️ 절대 금지` 위반 경로 없음 — 제어 명령 미발행.
- **결론**: 4영역 모두 비해당 + 각 영역에 구체 사유 명기 (차단 해제 조건 충족).

---

## 표준 사전 카탈로그

> 신규 항목 없음 — 본 사이클은 기등록 어휘 재사용으로 충분 (안건 1 결론).

### 신규 표준 단어
없음.

### 신규 표준 데이터 도메인
없음.

### 신규 표준 용어
없음. (DB 컬럼 신설 0건 — 조회 전용)

---

## 신규 엔티티/DB 컬럼

없음.

- 본 사이클은 **조회 전용 API** 신설. 신규 엔티티·DB 테이블·컬럼·인덱스·파티션 신설 모두 0건.
- 단, DBA 권고 1건 (안건 3): 기존 `tag_m` 의 `idx_tag_m_instrument_id` (또는 `(instrument_id, tag_se_cd)`) 복합 인덱스 존재 여부 PLAN 단계 확인 후 미존재 시 `CREATE INDEX CONCURRENTLY` 추가 (운영 안정성 강화 — 본 사이클 직접 요구 아닌 향후 확장 대비).

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론과의 정렬 |
|---------|-----------------|
| (없음) | — 표준 사전·DDL 변경 0건. 송수펌프제어분석-3번섹션 PumpStateDto·FacilityStateService 패턴과 의도적으로 다른 응답 구조 (요약·합산) 가지나, 본 사이클 결정은 `ot-integration.md §3` 직접 인용으로 정당화 — 3번섹션 자동 원용 아님 (사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합) |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- 신규 엔티티 0건. 기존 `Facility` (parent) · `Instrument`(`Pump`/`FlowMeter`) · `Tag` · `RawData` 재사용.
- 펌프 연결 경로: `instrument_m.facility` 직접 자식 (`equip_type_cd = PUMP` 필터 강제).
- FRI 매핑 위치: 시설 직접 자식 `FLWMTR` 의 FRI 태그 (가정 — PLAN 검증).
- 값 선택 정책: `effectiveVal(corrVal, rawVal) = corrVal != null ? corrVal : rawVal`.

### DB 설계 변경
- 변경 없음.
- PLAN 검증 항목: `tag_m` 의 `idx_tag_m_instrument_id` 또는 `(instrument_id, tag_se_cd)` 인덱스 존재 확인 (없으면 `CREATE INDEX CONCURRENTLY` TASK 추가).

### 적용할 패턴
- 응답 DTO: `FacilityOperatingStatusDto` 6필드 (`facilityId`·`facilityNm`·`measurementDtm`·`onPumpNms`·`totalElpwrAmt`·`elpwrUnitQty`). 단층, `BaseAuditResponseDto` 미상속, `@JsonFormat "yyyy-MM-dd HH:mm:ss"` 적용.
- Service: `FacilityOperatingStatusService` 신규 (`com.mo.swtp.facility.service`).
- Controller: `FacilityController.findFacilityOperatingStatus(@PathVariable String facilityId)` 메서드 추가.
- 엔드포인트: `GET /api/facility/{facilityId}/operating-status`.
- Repository: `RawDataCustomRepository.findLatestByTagSrlNos` + `InstrumentRepository.findByFacilityIdAndEquipType` + `TagRepository.findByInstrumentInstrumentIdInAndUseYn` 재사용 (4회 SELECT, N+1 회피).
- 값 선택·On 판정·합산·max 산출: Service 의 private 메서드로 분해 (§2.1 50줄 초과 방지).
- 빈 데이터 케이스: 응답 필드 `null` 또는 `[]` (Swagger 명세 명시).
- 도메인 룰: `equip_type_cd = 'PUMP'` 필터 강제 + OPS/PWI `quality_cd = GOOD` 만 합산.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `UNCERTAIN` 품질 OPS 펌프를 On 합산 대상에 포함할지 여부 | 미해결 | PLAN 결정. 보수적 처리(제외) 권장 — §3 의 가중치 0.5 정책은 이진 신호에 부적합 (wtp-domain-expert) |
| `UNCERTAIN` 품질 PWI 측정값을 합산 대상에 포함할지 (가중치 0.5 vs 전액 제외) | 미해결 | PLAN 결정. 순시전력 합산에 가중치 0.5 적용 시 실제 전력 소비와 오차 유발 위험 (wtp-domain-expert) |
| FRI 매핑 위치는 시설 직접 자식 `FLWMTR` 의 FRI 태그라고 가정 | 가정 | 실제 현장 배선(시설 직접 vs SensorPoint·자식 시설) 확인 — PLAN 단계 또는 운영 확인 후 보정 |
| 본 카드 지원 시설 종류는 PWTF·DWT·PRSF 셋 모두 (RSV/POINT 제외) 라고 가정 | 가정 | UI 컨텍스트 명시 부재 — `facility_type_cd` 필터 강제 의무 (`entity-patterns.md` §도메인 룰). PLAN 단계 frontend 와 협의 후 확정 |
| `tag_m` 의 `idx_tag_m_instrument_id` 또는 `(instrument_id, tag_se_cd)` 복합 인덱스 존재 여부 | 미해결 | PLAN 단계 DDL 확인. 미존재 시 `CREATE INDEX CONCURRENTLY` TASK 추가 (wtp-dba-reviewer 권고) |
| 전력원단위 단위 표기 (kW / (m³/h) = h⁻¹·kWh/m³) Swagger 명세 표기 방식 | 미해결 | PLAN 단계 결정. `@Schema(description="전력원단위 (kWh/m³)")` 형태 권장 |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `FacilityOperatingStatusService.findFacilityOperatingStatus(facilityId)` 가 활성 시설의 On 펌프 이름 + PWI 합산 + 전력원단위 + max(acq_dtm) 을 단일 호출로 반환 | 신규 단위 테스트 PASS — `FacilityOperatingStatusServiceTest` 의 정상 케이스 (On 펌프 2대·PWI 합산·FRI 매핑·전력원단위 산출) |
| `quality_cd = BAD` OPS 펌프가 On 합산에서 제외 (`ot-integration.md §3` 정합) | 신규 단위 테스트 PASS — `BAD OPS 펌프는 onPumpNms 에서 제외` 시나리오 |
| 분모 FRI = 0 또는 NULL 또는 BAD 또는 부재 시 `elpwrUnitQty = null` 반환 (0 나누기 방어) | 신규 단위 테스트 PASS — 분모 0/NULL/BAD 4 케이스 |
| `equip_type_cd = 'PUMP'` 필터 강제 — 부모 다형성 전체 조회 금지 (`entity-patterns.md` §도메인 룰) | 신규 단위 테스트 PASS — 다른 자식(VALVE/FLWMTR/PRSMTR 등) 인스트루먼트가 onPumpNms 에 포함되지 않음 |
| `GET /api/facility/{facilityId}/operating-status` Swagger UI 호출 시 `CommonResponseDto<FacilityOperatingStatusDto>` 응답 200 OK + 필드 6개 정상 노출 | 빌드 후 Swagger UI 수동 확인 — `./gradlew.bat :api:bootRun` 후 `http://localhost:8080/swagger-ui.html` 에서 호출 |
| 빌드 + 전체 테스트 BUILD SUCCESSFUL | `./gradlew.bat build` 종료 코드 0 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 작업은 조회 전용 API 신설. 알람 임계값·전이 조건·복귀 조건 변경 없음. 화면에 알람 상태도 표시하지 않음 (4번 섹션은 운영 현황 요약 카드 — 알람 표시는 다른 섹션 책임) |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 본 작업은 제어 명령 미발행 조회 API. 기동·정지·주파수 변경 명령 없음. 인터록 선행조건 검사 경로 미접촉. `⚠️ 절대 금지` (인터록 검사 건너뛴 재시도) 위반 경로 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`/`ai_drvn_mod_h` 미접촉. 사용자 의도(`ai_drvn_mod`)·시스템 상태(`ai_mode_cd`) 변경 없음. 강제 전환 트리거(SCADA 5분 초과) 로직 미접촉 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 모드 전환 이력(`ai_drvn_mod_h.transition_reason` 5종)·제어 로그(`pump_ctrl_h`) 신규 생성 없음. 조회 전용 API 이므로 INSERT 없음 |

> "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족 — 4영역 모두 "비해당" + 구체 사유 명기 차단 해제 조건 (`backend/.claude/commands/dev/analyze.md §5b` 정합).

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 ROOT 어휘 사전·backend 표준 용어 사전·룰 파일 변경 0건 (안건 1 결론).

- [x] 신규 표준 단어 없음 — `swtp/.claude/rules/dict/standard-words.md` 갱신 불필요
- [x] 신규 표준 데이터 도메인 없음 — `swtp/.claude/rules/dict/standard-data-domains.md` 갱신 불필요
- [x] 신규 비즈니스 도메인 약어 없음 — `swtp/.claude/rules/dict/domain-abbreviations.md` 갱신 불필요
- [x] 신규 표준 용어 (DB 컬럼) 없음 — `swtp/backend/.claude/rules/dict/standard-terms.md` 갱신 불필요
- [x] DB suffix·엔티티 패턴 변경 없음 — `swtp/backend/.claude/rules/naming.md` · `swtp/backend/.claude/rules/entity-patterns.md` 갱신 불필요

> 모든 체크박스가 사전 완료 상태 (변경 없음 사이클) — PLAN 진입 전제조건 충족.

---

## 산출물

- [계획안](../../../plan/20260521/운전현황분석-4번섹션/PLAN1.md) — `/dev:plan 운전현황분석-4번섹션` 단계에서 작성 예정
