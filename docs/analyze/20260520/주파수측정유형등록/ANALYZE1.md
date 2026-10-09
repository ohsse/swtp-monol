---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 주파수 측정 유형 enum 정식 등록 — 도메인 분석

## 작업 배경

본 사이클 직전의 펌프 시드 데이터 등록 작업에서 인버터 펌프(`pump_m.drive_type_cd = INVERTER_DRIVE`) 4기에 주파수(Hz) 측정 태그를 등록할 필요가 발생했으나, `TagMeasurementType` enum (`common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java`) 에 주파수 측정 유형이 등재되어 있지 않았다. 사용자 임시 결정으로 `tag_se_cd = 'SPI'` 코드값을 양쪽 DB (local + dev) tag_m 에 INSERT (각 4건). 본 ANALYZE 는 enum 정식 등록을 위한 5인 회의 산출물.

- 외부 산출물: 없음 (직전 시드 작업 결과 + 기존 enum/sensor 정책 검토)
- 본 사이클 진입 사유: 사용자 메모리 "사이클 간 자산 자동 원용 금지" + "기존 결정 비판적 재평가" — 임시 채택 `SPI` 코드값을 5인 회의로 재평가

## 회의록 (5인 회의 토픽 주도)

### 안건 1: enum 코드값 명칭 후보 선정 (이견 발생 — 오케스트레이터 중재)

- 호출 에이전트: `wtp-glossary-manager` (Round 1) · `wtp-domain-expert` (Round 1)
- 후보: `SPI` (Speed Indicator) / `FRQ` (Frequency) / `FQI` (Frequency Quantity Indicator) / `HZI` (Hertz Indicator)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: **`FQI` 권장 (1순위)**. ISA S5.1 `{측정량 첫 글자}{기능}I` 형식과 정합 (`FRI`·`PRI`·`LEI`·`PWI`·`VOI` 5종 선례). `SPI` 는 **유사 충돌** 판정 — ISA S=Speed(회전속도 RPM) 와 주파수(Hz) 는 다른 물리량 (인버터는 Hz 입력 → RPM 출력). `standard-words.md` 미래 `speed_*` 컬럼 도입 시 `SPI` 가 어근 선점 유발. `FRQ`/`HZI` 는 기존 ISA 체계 이탈
  - **wtp-domain-expert**: **`SPI` 권장**. ISA S=Speed 인버터 펌프 회전속도 ∝ 주파수 정합. 3자 `{기능}{I}` 패턴 일치. `FQI` 는 "Frequency-Quality-Indicator" 로 혼동 여지
- **오케스트레이터 중재**: 두 에이전트의 결정 기준이 다른 층위 — wtp-glossary-manager 는 **어휘 사전 충돌 분류**(미래 `speed_*` 컬럼 선점 회피) 관점, wtp-domain-expert 는 **SCADA 명명 관습**(ISA Speed 정합) 관점. swtp 분산 SSOT 모델은 **어휘 사전 충돌이 도메인 명명 관습보다 우선**(`swtp/.claude/rules/dict/README.md` §유사 충돌 판정 기준 — "동의어·유사 의미 약어 동시 등록 금지"). 또한 인버터 펌프의 측정 대상은 **인버터 출력 주파수(Hz)** 자체이며, 회전속도(RPM)는 미측정(별도 RMS 센서 없음). 측정 대상 = 주파수 라는 사실은 `FQI` 우위.
- **결론**: **`FQI` 권장** (오케스트레이터 결정). 단 SCADA 운영 SI 표기법 정합 우선 시 `SPI` 채택 가능성 존재 — **사용자 최종 결정** "## 가정 및 미해결 질문" Q1 으로 위임.

### 안건 2: 신규 표준 단어 등록 필요성

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약: enum 코드값(`FQI`/`SPI` 등) 자체는 `tag_se_cd` 코드값이라 **표준 단어 사전 등록 대상 아님** (`FRI`·`PRI`·`LEI`·`PWI`·`RMS`·`OPS`·`VOI` 모두 미등록 선례). 단, 향후 컬럼 조합(`predc_frq_val`·`frq_raw_val`) 재료로 **`frq` 표준 단어 1건 등록 권장** — `flwrt`·`prsr`·`elpwr`·`opng` 측정량 단어 선례 동형, swtp 4-5자 약어 컨벤션 정합(3자, 경계).
- **결론**: `frq` (주파수) 표준 단어 신규 등록.

### 안건 3: `ot-integration.md §3` 결측 대체값 정책

- 호출 에이전트: `wtp-domain-expert`
- 후보: (a) Hold Last Value (FRI·PRI·VOI 동형) / (b) null 저장 (PWI 동형) / (c) 즉시 BAD 격상 (OPS 동형)
- Round 1 답변 요약: **(a) Hold Last Value 권장**. 인버터 출력 주파수는 AI 제어 명령 → 인버터 주파수 출력 → 모터 회전수의 결과값으로 1분 주기 SCADA 수집 구간 내 점진 변화 (연속 아날로그 AI 신호). VOI 와 물리 메커니즘 동일 — 액추에이터 명령 → 결과 누적 시간(수초~수십초). 5분 초과 시 BAD 격상.
- (b) PWI 동형 null 저장은 집계 오염 방지 목적인데 주파수는 현시점 집계 대상 아님 — 근거 부족.
- (c) OPS 동형 즉시 BAD 격상은 DI on/off 신호 이산 특성 전용 — 아날로그 AI 신호와 충돌.
- **결론**: (a) Hold Last Value 적용 + 5분 초과 시 BAD 격상.

### 안건 4: DB 데이터 마이그레이션 전략

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **마이그레이션 파일 분리 의무 (블로커)**: `V8__tag.sql` 수정 절대 금지 (§5.4 V{N} 동결 정책). `V8_1__tag_patch.sql` 신규 분리 필수.
  - **docs/ddl 동기화 의무 (중간)**: `docs/ddl/tag.sql` 하단 ALTER/UPDATE 누적 의무 (§5.3 양쪽 동시 갱신).
  - 12행 UPDATE → `ROW EXCLUSIVE` 락, 밀리초 단위, `CONCURRENTLY` 불필요.
  - **DOM_CODE_20 2차 승인 불필요** — 데이터 도메인 자체 변경 아님 (enum 구성원만 변경).
  - 운영본 마이그레이션 + 운영자 `psql -f` 직접 적용이 표준 (Claude 세션 직접 적용은 §5.1 운영본 경유 원칙 위반).
  - 롤백: 역방향 UPDATE 한 줄, 별도 백업 불필요. 파일 내 주석 명시 권고 (낮음).
- **결론**: `V8_1__tag_patch.sql` 신규 분리 + `docs/ddl/tag.sql` 동기화 의무. UPDATE 마이그레이션 코드값은 안건 1 결정에 종속.

### 안건 5: enum 코드 변경 영향 범위 + 패턴 정합

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - enum Lombok 패턴 100% 정합 (5줄 미만 추가). `@Enumerated(EnumType.STRING)` 정합. §2.1 정량 기준 무영향.
  - Service 4종 (`FacilityStateService`·`PumpSummaryService`·`FacilityPredictionService`·`DwtStateService`) 의 `EnumSet.of(...)` 필터는 **화이트리스트** 방식 — 신규 enum 자동 미포함, **기존 Service 코드 변경 불필요**.
  - **중간 블로커 후보 2건**:
    - `TagMeasurementTypeTest:42` `hasSize(7)` → `hasSize(8)` 즉시 RED. enum 변경과 동일 커밋 의무.
    - DDL COMMENT 본문 `FRI/PRI/LEI/PWI/RMS/OPS/VOI` 7종 열거 — 신규 값 추가 시 두 파일(V8 + docs/ddl) COMMENT 갱신 의무. `check-ddl-column-comment.sh` 훅은 COMMENT 존재 여부만 검사 → 내용 갱신 누락 수동 확인 의무.
  - 낮음: PLAN 가정 섹션에 `drive_type_cd = INVERTER_DRIVE` 필터 강제 여부 명기 / Javadoc 도입 이력 항목 추가.
- **결론**: enum 값 추가 자체는 단순. 테스트 + DDL COMMENT 갱신을 동일 커밋 포함 의무.

## 표준 사전 카탈로그

### 신규 표준 단어 (ROOT — `swtp/.claude/rules/dict/standard-words.md`)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `frq` | 주파수 | 신규 | `standard-words.md` 미등록 (grep 0 hits). `flwrt`·`prsr`·`elpwr`·`opng` 측정량 단어 선례 동형. 3자 약어 — swtp 4-5자 약어 컨벤션 경계 허용 (`io` 2자 선례). 향후 `predc_frq_val`·`frq_raw_val` 컬럼 조합 재료 (안건 2 결정) |

### 신규 표준 데이터 도메인

없음 — `DOM_QTY_15_4` (rawdata_1m_h.raw_val/corr_val 등 측정값) 기존 재사용. `DOM_CODE_20` (`tag_se_cd`) 기존 재사용. wtp-dba-reviewer 2차 승인 불필요 (안건 4 결정).

### 신규 표준 용어 (backend — `.claude/rules/dict/standard-terms.md`)

없음 — 본 사이클은 enum 코드값 등재이며 신규 컬럼명 신설 없음. 향후 `frq` 단어가 컬럼 조합에 처음 쓰일 시점에 별도 등록 (예: `predc_frq_val` 도입 사이클).

## 신규 엔티티/DB 컬럼

없음 — `TagMeasurementType` enum 값 1행 추가 (`FQI("주파수", "Hz")` 또는 사용자 결정 코드값) 만 발생. `Tag` 엔티티·`tag_m` 테이블·`tag_se_cd` 컬럼 정의 모두 무변경.

## 기존 사전·패턴과의 충돌

| 충돌 | 해소책 |
|------|--------|
| 임시 채택 `SPI` (양쪽 DB 4건 INSERT 완료) ↔ `FQI` 어휘 우위 결론 | 안건 1 사용자 최종 결정 후 `V8_1__tag_patch.sql` UPDATE 마이그레이션 (`SPI` → FQI) |
| `TagMeasurementTypeTest:42` `hasSize(7)` 단언 | 신규 enum 값 추가 + 테스트 `hasSize(8)` + 단위 검증 행 추가, 동일 커밋 포함 |
| DDL COMMENT `tag_se_cd` 본문 7종 열거 | `V8_1__tag_patch.sql` 의 `COMMENT ON COLUMN tag_m.tag_se_cd IS '...'` 갱신 + `docs/ddl/tag.sql` 양쪽 동시 갱신 |
| `ot-integration.md §3` 결측 대체값 표 7행 | 신규 enum 값 행 추가 (결측 대체값 = Hold Last Value, 5분 초과 BAD 격상 — 안건 3 결정) |

## PLAN 으로 전달할 결정 사항

1. **enum 값 1건 추가**: `TagMeasurementType.FQI("주파수", "Hz")` — 사용자 결정 후 확정 (Q1 결과 반영).
2. **표준 단어 `frq` 신규 등록**: ROOT `swtp/.claude/rules/dict/standard-words.md` 행 추가.
3. **DB 마이그레이션**:
   - `common/src/main/resources/db/migration/V8_1__tag_patch.sql` 신규 작성 (`UPDATE tag_m SET tag_se_cd = 'FQI' WHERE tag_se_cd = 'SPI'` + `COMMENT ON COLUMN tag_m.tag_se_cd IS '...'` 갱신).
   - `docs/ddl/tag.sql` 하단 동기 누적.
   - 운영자가 양쪽 DB `psql -f` 직접 적용.
4. **테스트 갱신**: `TagMeasurementTypeTest` `hasSize(7)` → `hasSize(8)` + 신규 단위 검증 행 (`@Test enum_값은_주파수가_포함된다` 등).
5. **`ot-integration.md §3` 결측 대체값 표 신규 행 추가**: Hold Last Value + 5분 한계.
6. **DDL COMMENT 본문 갱신**: V8 + docs/ddl 양쪽.
7. **Javadoc 갱신**: `TagMeasurementType` 도입 이력 항목 추가.

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| **Q1**. enum 코드값 명칭: `FQI` vs `SPI` | **결정** | **`FQI` 확정** (사용자 결정, 2026-05-20). 어휘 충돌 분류 우위 + 측정 대상 = 주파수(Hz) 의미 직결. 양쪽 DB tag_m 의 SPI 4건 → FQI UPDATE 마이그레이션 PLAN 진행 |
| Q2. 인버터 펌프(`pump_m.drive_type_cd = INVERTER_DRIVE`) 한정 측정인가? 정격 펌프(`RATED_DRIVE`) 에는 주파수 태그 등록 불가 강제? | 가정 | 본 사이클 시드 데이터는 인버터 펌프 4기에만 등록. PLAN 단계에서 Service 분기 시 `drive_type_cd = INVERTER_DRIVE` 필터 강제 여부 결정 |
| Q3. 주파수 임계값 인터록 선행조건 연계 (예: 최소 회전수 보호) 가 향후 도입될 가능성? | 미해결 | `ot-integration.md §2`·`§5` 보류 상태 — 본 사이클은 측정값 등재만, 인터록 연계는 사이클 2 (`ot_integration` 별도 ANALYZE) 위임 |
| Q4. AI 운전 모드(`ot-integration.md §5`) 와 주파수 측정값 연결고리? AI 자동 제어 명령이 주파수 setpoint 송신 구조? | 미해결 | 사이클 2 결정 위임 |
| Q5. 다국가/다환경 단위 환산 (rad/s, RPM 등) 불필요 — `Hz` 고정 | 가정 | swtp 운영 시나리오 한국 지자체 한정 — 환산 요구 없음 |
| Q6. 알람 4단계 주파수 임계값 정의 향후 도입 시 `ot-integration.md §5` 임계값 표 재설계 의무 | 미해결 | 본 사이클 미포함 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `TagMeasurementType` enum 값 8종 확장 | `./gradlew.bat :common:test --tests TagMeasurementTypeTest` GREEN |
| 신규 enum 값 단위 = "Hz" | `assertThat(TagMeasurementType.FQI.getUnit()).isEqualTo("Hz")` 신규 테스트 GREEN |
| 양쪽 DB tag_m 의 SPI 4건 FQI UPDATE 완료 | `SELECT COUNT(*) FROM tag_m WHERE tag_se_cd = '{FQI}'` = 4 양쪽 일치 |
| DDL COMMENT 본문 8종 열거 갱신 | `check-ddl-column-comment.sh` 훅 PASS + 수동 grep `COMMENT ON COLUMN tag_m.tag_se_cd` 본문에 FQI 포함 |
| `ot-integration.md §3` 결측 대체값 표 8행 | `grep` 표 행 수 = 8 |
| `frq` 표준 단어 등록 | `grep "^\| \`frq\`" swtp/.claude/rules/dict/standard-words.md` 1 hit |
| 빌드 전체 통과 (테스트 + 컴파일) | `./gradlew.bat clean build` BUILD SUCCESSFUL |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | **비해당** | 본 사이클은 측정 유형 enum 등재만. 임계값·전이 조건·복귀 조건 변경 없음. 단 Q6 미해결 — 향후 주파수 임계값 도입 시 §5 재설계 의무 명시 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | **비해당** | 본 사이클 인터록 선행조건 검사 코드·테이블·임계값 변경 없음. `ot-integration.md §2` 자체 보류 상태. 단 Q3 미해결 — 주파수 인터록 연계 향후 사이클 2 결정 |
| AI 운전 모드 (`ot-integration.md §5`) | **비해당** | `ai_drvn_mod`·`ai_mode_cd` 이중 체계 본 사이클 무변경 (§5 전체 보류). 단 Q4 미해결 — AI 자동 제어 주파수 setpoint 송신 구조 향후 결정 |
| 이력 기록 의무 (`ot-integration.md §5`) | **비해당** | `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 모두 백지화 상태. 본 사이클은 `tag_m` enum 등재 + `rawdata_1m_h` 행 추가 (시드 데이터 SPI 4건 → FQI UPDATE) 뿐 — 이력 기록 의무 신규 발생 없음 |

> "비해당" 사유 4건 명기 + "## 신규 엔티티/DB 컬럼" "없음" + "## 가정 및 미해결 질문" 6건 명기 동시 충족 → "비해당" 단독 4건 차단 해제 조건 충족.

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `frq` (주파수, 영문 풀네임 `frequency`, 기본 데이터 도메인 미지정 — DOM_QTY_15_4 측정값/단위 매핑은 enum) 행 신규 등록 (안건 2 결정)
- [x] `swtp/backend/.claude/rules/ot-integration.md` §3 결측 대체값 기준 표 — 신규 코드값 행 추가 (Hold Last Value + 5분 한계 정책, 안건 3 결정)
- [x] `swtp/backend/.claude/rules/ot-integration.md` §3 측정 유형 코드 SSOT 안내 (122행) — 열거 약어 목록 갱신 `FRI·PRI·LEI·PWI·RMS·OPS·VOI` → FQI 추가
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` `tag_se_cd` 행 비고 — TagMeasurementType enum 매핑 코드값 8종으로 갱신 (FRI/PRI/LEI/PWI/RMS/OPS/VOI + FQI)

## 산출물

- [계획안](../../../plan/20260520/주파수측정유형등록/PLAN1.md) (Q1 사용자 결정 후 작성)
