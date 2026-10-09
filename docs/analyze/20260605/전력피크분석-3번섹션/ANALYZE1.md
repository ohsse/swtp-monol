---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-3번섹션 — 주요내역 송수펌프 순시전력 추가 도메인 분석

## 작업 배경

전력피크 분석 화면(`backend/image/전력피크분석.png`)의 **가운데 3번 섹션**(`주요내역`)을 백엔드로 구현한다. 3번 섹션은 3지표를 표출하며, 그중 2지표(총순시전력·요금적용전력피크)는 직전 2번섹션(`docs/analyze/20260605/전력피크분석-2번섹션/ANALYZE1.md`, status: approved)에서 이미 구현한 값과 동일하다. 신규 지표는 **송수펌프 순시전력** 1개뿐이다.

화면 3번 섹션 3지표:

| 지표 | 정의 | 2번섹션 대비 |
|------|------|------------|
| 총순시전력 | 전체 활성 PWI 태그 GOOD 최신값 합산 (kW) | **동일** (`totalElpwr` 재사용) |
| **송수펌프 순시전력** | 펌프(`equip_type_cd='PUMP'`) 매핑 PWI 태그 GOOD 최신값 합산 (kW) | **신규** (`pumpElpwr`) |
| 요금적용전력피크 | 최근 12개월 분단위 PWI 합산값 중 MAX (kW) | **동일** (`billingPeakElpwr` 재사용) |

본 작업은 신규 테이블·DDL·컬럼이 **0건**이며, 송수펌프 순시전력은 응답 DTO 필드 1개로만 표출되는 합성 뷰 추가다.

### 사용자 확정 사항 (요건 잠금, 2026-06-05)
| 항목 | 결정 |
|------|------|
| 송수펌프 순시전력 범위 | **전체 펌프 PWI 합산 (On/Off 무관)** — `equip_type_cd='PUMP'` 매핑 PWI GOOD 최신값 합산. 총순시전력의 펌프 부분집합 |
| API 구조 | **2번섹션 엔드포인트 확장** — 신규 엔드포인트 미생성. 기존 `GET /api/opt/peak-power-analysis` 응답 DTO `PeakPowerAnalysisDto` 에 필드 1개 추가 (4→5필드) |
| 산출 재사용 | **2번 Service 재사용** — `PeakPowerAnalysisService` 확장. total·billing·predc 기존 로직 유지, 송수펌프 PWI 합산만 추가 |

### 조사로 확정된 사실 (live DB, 2026-06-05 확인)
- 활성 PWI 태그 = **8개** (`tag_m.tag_se_cd='PWI' AND use_yn='Y'`) — **전부 PUMP 계측기 매핑** (정수조#1·#2 의 펌프 4기씩, FLWMTR 매핑 PWI 0개).
- 현재 시드 기준 `송수펌프순시전력 = 총순시전력` (8/8 모두 펌프). 단 화면 이미지는 총순시전력(778) ≠ 송수펌프순시전력(558) → **실 운영에는 비(非)펌프 PWI(시설 전력계 등)가 존재** → 설계는 `equip_type_cd='PUMP'` 필터로 둘을 구분해야 한다.
- `tag_m.instrument_id` FK → `instrument_m`. `instrument_m.equip_type_cd` 는 JPA JOINED DiscriminatorColumn (`@Column(insertable=false,updatable=false) EquipType equipType` 매핑).
- `rawdata_1m_h` 월 RANGE 파티션 + `idx_rawdata_1m_h_tag_time(tag_srl_no, acq_dtm DESC)`. `tag_m` 소규모(수십 행).

> 외부 산출물: `backend/image/전력피크분석.png` (3번 섹션 = 주요내역 3지표 표출).

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 송수펌프 순시전력 응답 DTO 필드 어휘 (DB 컬럼 미생성)
- 호출 에이전트: `wtp-glossary-manager` (Round 1)
- **Round 1 결론**:
  - **DTO 필드명 `pumpElpwr` 확정** — `pump`(신규 표준 단어) + `elpwr`(기존 재사용). `totalElpwr`(2번섹션) 조합 패턴 동형.
  - **`pump` 표준 단어 신규 등록 가능** — `domain-abbreviations.md` 의 `pump` 비즈니스 도메인 약어 폐기(2026-05-12)는 **단어 층위와 별개**. 폐기 명시문("`pump` 만 폐기, 자식 코드값 `PUMP` 는 instrument 자산")이 비즈니스 도메인 약어 층위 한정임을 확인. 층위 교차 공존 선례: `raw`(단어)+`raw`(비즈니스 약어), `role`(단어)+`auth`(비즈니스 약어), `use`(단어)+`user`(비즈니스 약어). `equip`(장비, 등록됨)와 어근 충돌 없음.
  - `transmission`(송수) 대안 **불요** — 본 지표는 송수 공정 전용이 아니라 `equip_type_cd='PUMP'` 필터 기준 집합 의미이므로 설비 종류명 `pump` 가 더 정확.
  - `elpwr`(전력 순시, kW) **기존 재사용** (2026-04-25 등록).
  - **데이터 도메인 신규 0건** (`pumpElpwr` = kW 순시전력 → `DOM_QTY_15_4` 재사용, DBA 2차 승인 불요). **표준 용어(DB 컬럼) 신규 0건** (DTO 필드 전용, `totalElpwr` 선례 동형 — `standard-terms.md` 미등록).

### 안건 2: 펌프 매핑 PWI 식별 쿼리 (instrument JOIN)
- 호출 에이전트: `wtp-dba-reviewer` (Round 1)
- **Round 1 결론**:
  - **블로커(높음) → Round 1 내 해소**: 옵션 B(`Tag.getInstrument().getEquipType()` lazy 접근)는 8건 N+1 → `query-tuning.md §2` N+1 금지 위반. **배제**.
  - **옵션 A 채택** — 신규 파생쿼리 `findByTagSeCdAndUseYnAndInstrument_EquipType(PWI, Y, PUMP)`. `tag_m` ↔ `instrument_m` INNER JOIN 1회로 펌프 PWI `tag_srl_no` set 확정. `instrument_m` JPA JOINED 상속이므로 부모 테이블 JOIN 1회로 `equip_type_cd` 필터 처리.
  - **`findLatestByTagSrlNos` 단일 재사용 가능** — pump PWI ⊆ all PWI 이므로 전체 PWI srlNo 1회 호출 → 최신값 일괄 조회 → service 가 펌프 srlNo set 멤버십으로 분기 합산. rawdata 쿼리 횟수 유지. 향후 비펌프 PWI 추가 시에도 로직 무변경.
  - **신규 인덱스 불요** — `tag_m` 소규모(수십 행) Seq Scan 허용. `idx_rawdata_1m_h_tag_time` 그대로 적중.
  - **신규 성능 리스크 없음** — 본 작업은 최신값 조회 경로만 추가(billing 12개월 MAX·predc 는 2번섹션 그대로 유지).

### 안건 3: 2번섹션 Service/DTO 확장 클래스 배치
- 호출 에이전트: `wtp-backend-engineer` (Round 1)
- **Round 1 결론**:
  - **권고(중간) ② [핵심]**: `findLatestByTagSrlNos` **2회 호출 금지** — 같은 최신값을 두 번 조회하면 `totalElpwr` 과 송수펌프합산이 시점 불일치 가능. `getPeakPowerAnalysis()` 에서 `List<RawDataLatestDto> latest = rawDataRepository.findLatestByTagSrlNos(pwiTagSrlNos)` **1회 호출** 후 `sumLatestPwi(latest)` / `sumPumpLatestPwi(latest, pumpSrlNos)` 헬퍼가 동일 리스트 공유. → 기존 `sumLatestPwi(List<String>)` 시그니처를 `List<RawDataLatestDto>` 수용 형태로 변경 (내부 private, 외부 영향 0). 본문 ~12줄 — 50줄 임계(§2.1) 위반 없음.
  - **권고(중간) ①**: 펌프 srlNo set 조회 경로 — (a) 신규 파생쿼리 1개(DB 왕복 2회: 전체 PWI + 펌프 PWI) vs (b) 기존 `InstrumentRepository.findByEquipType...` + `findByInstrumentInstrumentIdInAndUseYn`(왕복 3회). **(a) 더 단순** → 안건 2 DBA 옵션 A 와 합치. **결론: 옵션 A(파생쿼리 1개) 채택**.
  - **참고(낮음)**: DTO 5필드 확장 — 송수펌프순시전력 필드는 `totalElpwr` 직후 배치 권고, `@Schema(description, example)` 기존 4필드 패턴 동형(`BigDecimal` → `implementation` 생략 적법), 정적팩토리 `of(...)` 인자 5개. **기존 4필드의 `@Schema`·`@JsonFormat` 수정 금지**(§3 정밀한 수정). 2번섹션 class Javadoc·`@Schema(description="...4지표")` 만 "2·3번섹션 공용/5지표" 로 최소 갱신.
  - **참고(낮음)**: 테스트 — 펌프/비펌프 PWI 혼재 `latest` 에서 펌프 부분집합만 합산 검증 1건 추가. 기존 5케이스 stub 확장 불요.

### 안건 4: 도메인 룰 4영역 점검 + 송수펌프순시전력 정의 검증
- 호출 에이전트: `wtp-domain-expert` (Round 1)
- **Round 1 결론**:
  - 도메인 4영역(알람 4단계·인터록·AI 운전모드·이력 기록) **모두 비해당** — 읽기 전용 집계, 조회 대상 `rawdata_1m_h`·`tag_m`·`instrument_m` 한정. 각 구체 사유 아래 "## 도메인 룰 4영역 점검" 표.
  - **핵심 — "On/Off 무관 전체 펌프 PWI 합산" 도메인 타당성 = 목적 다른 별개 지표로 정합**. 운전현황 4번섹션 `FacilityOperatingStatusService` 의 "On 펌프 PWI 합산"은 **현재 운전 중 펌프의 실제 소비 전력**(운전 효율·부하 분석 목적, OPS=GOOD+1.0 필터). 본 지표는 **설비군 전체 관점의 펌프 기여 전력**(전력피크 분석 맥락). 집계 목적이 다르므로 도메인 혼선 아님. 총순시전력(전체 PWI)의 부분집합으로 중복 정의 아님.
  - GOOD 필터 + effectiveVal(corrVal 우선)가 `ot-integration.md §3` PWI 결측 정책("GOOD만 집계, BAD/UNCERTAIN null 저장+제외")과 **완전 정합**. PWI 는 Hold Last Value 미적용이라 이전 Good 값 오염 위험 없음.
  - **참고(낮음) → 가정 섹션 기재**: ① Off 상태 펌프 PWI GOOD 수신값이 항상 0/극소(대기 전력)라는 가정 — 현장별 Off 시 양수 잔존 시 과대계상 위험 ② 태그 미등록 펌프 자동 제외(부분합 반환을 의도된 동작으로 간주) ③ UI 주석 권고: `@Schema` description 에 "On/Off 무관, 운전현황 On펌프 합산과 혼동 주의" 명기.

---

## 표준 사전 카탈로그

### 신규 표준 단어
(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `pump` | 펌프 | 신규 | `standard-words.md` 미등록. 비즈니스 도메인 약어 `pump` 폐기(2026-05-12)는 **단어 층위와 별개** — `raw`(단어)+`raw`(비즈니스 약어), `use`(단어)+`user`(비즈니스 약어), `role`(단어)+`auth`(비즈니스 약어) 층위 공존 선례 동형. DTO 필드 `pumpElpwr` 조합 재료. `equip`(장비, 등록됨)와 어근 충돌 없음. `total`·`billing`(2번섹션 DTO 필드 전용 단어 등록) 선례 정합 |
| `elpwr` | 전력(순시) | 기존 재사용 | 2026-04-25 등록. `pumpElpwr`(`pump`+`elpwr`) 조합에 직접 사용 |

### 신규 표준 데이터 도메인
없음 — `pumpElpwr` = kW 순시전력 합산값 → `DOM_QTY_15_4`(기존) 재사용. DBA 2차 승인 불요.

### 신규 표준 용어 (DB 컬럼)
없음 — **DB 테이블·컬럼 미생성 확정**. `pumpElpwr` 는 응답 DTO 필드 전용 (`totalElpwr` 선례 동형). `standard-terms.md` 등록 불요.

| DTO 필드명 | 조합 | 데이터 도메인(형식) | 비고 |
|-----------|------|------------------|------|
| `pumpElpwr` | `pump`(신규) + `elpwr`(기존) | (DTO, `DOM_QTY_15_4` 형식) | 송수펌프 순시전력 — 펌프 매핑 PWI GOOD 최신값 합산 (On/Off 무관) |

분류값: **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

---

## 신규 엔티티/DB 컬럼

없음 — 본 작업은 신규 엔티티·테이블·컬럼·DDL·인덱스를 생성하지 않는다. 기존 `tag_m`·`instrument_m`·`rawdata_1m_h`·`opt_peak_target_p`·`predc_1m_h` 를 조회·집계하는 합성 뷰에 필드 1개를 추가한다.

추가/변경되는 코드 자산(전부 `api` 모듈, 엔티티 무변경):
- `PeakPowerAnalysisDto` (`com.mo.swtp.opt.dto`) — 필드 `pumpElpwr` 1개 추가 (4→5필드), 정적팩토리 `of(...)` 인자 5개 확장
- `PeakPowerAnalysisService` (`com.mo.swtp.opt.service`) — 펌프 PWI srlNo set 조회 + 송수펌프 합산 헬퍼 추가, `findLatestByTagSrlNos` 1회 호출 공유 리팩터 (`sumLatestPwi` 시그니처 `List<RawDataLatestDto>` 변경)
- `TagRepository` (`com.mo.swtp.tag.repository`) — 파생쿼리 `findByTagSeCdAndUseYnAndInstrument_EquipType(TagMeasurementType, YnType, EquipType)` 1개 추가
- `PeakPowerAnalysisServiceTest` — 펌프 부분집합 합산 케이스 1건 추가
- (변경 없음) `PeakPowerAnalysisController` — 동일 엔드포인트, 응답 DTO 필드 1개 증가만

---

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소 |
|----------|------|------|
| `pump` 표준 단어 신규 등록 vs `pump` 비즈니스 도메인 약어 폐기(2026-05-12) | 층위 별개 — 충돌 아님 | 단어 층위 vs 비즈니스 도메인 층위 (`raw`/`raw`·`use`/`user`·`role`/`auth` 선례). 폐기는 비즈니스 약어 한정 |
| 펌프-PWI 식별 — lazy 접근 N+1 | DBA 블로커(높음) → 해소 | 옵션 A 파생쿼리(INNER JOIN 1회) — `Tag.getInstrument()` lazy 접근(옵션 B) 배제 |
| `findLatestByTagSrlNos` 2회 호출 시 total↔pump 시점 불일치 | backend 권고(중간) → 해소 | 1회 호출 결과 `List<RawDataLatestDto>` 공유 — `sumLatestPwi` 시그니처 변경 |
| 송수펌프순시전력(On/Off 무관) vs 운전현황 4번섹션 On펌프 합산 | 도메인 충돌 아님 | 집계 목적 다른 별개 지표 (설비군 전체 기여 전력 vs 운전 중 실소비) |
| 송수펌프순시전력 vs 총순시전력 | 중복 정의 아님 | 전체 PWI 의 펌프 부분집합 (`equip_type_cd='PUMP'` 필터로 구분) |

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델**: 신규 엔티티 없음. 2번섹션 `opt` 자산 확장 — `PeakPowerAnalysisDto` 5필드(`totalElpwr`·**`pumpElpwr`**·`targetPeakElpwr`·`billingPeakElpwr`·`predcPeakDtm`), `PeakPowerAnalysisService` 송수펌프 합산 추가, `TagRepository` 파생쿼리 1개.
- **쿼리 설계**:
  - 송수펌프순시전력(`pumpElpwr`): `TagRepository.findByTagSeCdAndUseYnAndInstrument_EquipType(PWI, Y, PUMP)` → 펌프 PWI `tag_srl_no` set. 기존 `findByTagSeCdAndUseYn(PWI, Y)`(전체 PWI) 유지. `findLatestByTagSrlNos(전체 PWI srlNos)` **1회 호출** → service 가 전체합(`totalElpwr`)·펌프 부분합(`pumpElpwr`) 분할 산출 (GOOD 필터 + effectiveVal + reduce add). 펌프 set 멤버십 필터.
  - 총순시전력·요금적용전력피크·전력피크예상시간: 2번섹션 로직 **무변경 재사용**.
- **Service 리팩터**: `getPeakPowerAnalysis()` 에서 `latest` 1회 fetch 후 전체합·펌프합 공유. `sumLatestPwi(List<String>)` → `List<RawDataLatestDto>` 수용으로 시그니처 변경(내부 private). 50줄 이내 유지.
- **DB 설계 변경**: 없음(읽기 전용). 신규 DDL·인덱스 0건.
- **적용 패턴**: api-patterns Service/Repository 3계층, `@JsonFormat` 초단위(기존 `predcPeakDtm` 유지), Swagger `@Schema` 신규 필드 1개(기존 4필드 수정 금지 §3).
- **정밀한 수정(§3)**: 2번섹션 class Javadoc·`@Schema(description)` 만 "2·3번섹션 공용/5지표" 최소 갱신. 기존 필드 어노테이션 불변.
- **frontend SPEC 영향**: 2번섹션 슬러그(`전력피크분석-2번섹션`)의 응답 DTO 가 5필드로 변경 → `/dev:spec` 단계에서 해당 슬러그 SPEC 갱신 대상 (PLAN 명시).
- **엣지 fallback**: 펌프 PWI 0개 → `pumpElpwr` = ZERO. 비펌프 PWI 만 존재 시 `totalElpwr` > 0, `pumpElpwr` = ZERO. 기타 fallback 은 2번섹션과 동일.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 송수펌프 순시전력 = "전체 펌프 PWI 합산 (On/Off 무관)" | 결정 (사용자 2026-06-05) | 운전현황 4번섹션 On펌프 합산과 목적 다른 별개 지표 |
| API 구조 = 2번섹션 엔드포인트 확장 (신규 엔드포인트 미생성) | 결정 (사용자 2026-06-05) | `PeakPowerAnalysisDto` 4→5필드 |
| 산출 재사용 = 2번 Service 확장 (total·billing·predc 무변경) | 결정 (사용자 2026-06-05) | `PeakPowerAnalysisService` 확장 |
| Off 상태 펌프 PWI GOOD 수신값이 항상 0/극소(대기 전력) 가정 — 현장별 Off 시 양수 잔존 시 송수펌프순시전력 과대계상 위험 | 가정 | domain-expert 참고(낮음) ① — UI 주석 권고 |
| 펌프 매핑 PWI 태그 미등록 펌프는 합산 자동 제외 — 부분합 반환을 의도된 동작으로 간주 | 가정 | domain-expert 참고(낮음) ② |
| 펌프 srlNo set 조회 = 신규 파생쿼리 1개(옵션 A) — DBA·backend 합치 | 결정 | 안건 2·3 합치. backend 권고① 해소 |
| `findLatestByTagSrlNos` 1회 호출 결과 공유 (`sumLatestPwi` 시그니처 변경) | 결정 → PLAN | backend 권고② — total↔pump 시점 일관성 |
| 현재 시드 PWI 8개 전부 펌프 매핑 → `pumpElpwr = totalElpwr` (실 운영은 비펌프 PWI 존재) | 가정 | live DB 확인. 설계는 `equip_type_cd='PUMP'` 필터로 구분 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 송수펌프순시전력 = 펌프 PWI 부분집합 GOOD 합 (비펌프 PWI·BAD/UNCERTAIN/null 제외, corrVal 우선) | `PeakPowerAnalysisServiceTest` 단위 — 펌프 PWI 2건(GOOD) + 비펌프 PWI 1건(GOOD) + 펌프 PWI BAD 1건 혼재 `latest` stub 시 펌프 GOOD 2건만 `pumpElpwr` 합산, `totalElpwr` 은 GOOD 3건 합산 검증 (Mockito) |
| `findLatestByTagSrlNos` 1회 호출 (total·pump 동일 리스트 공유) | 단위 — `findLatestByTagSrlNos` 호출 횟수 1회 verify |
| 펌프 PWI 0개 시 `pumpElpwr`=ZERO | 단위 — `findByTagSeCdAndUseYnAndInstrument_EquipType` 빈 리스트 stub 시 `pumpElpwr`=ZERO, `totalElpwr` 은 정상 |
| 기존 4지표 회귀 무변경 | 기존 `PeakPowerAnalysisServiceTest` 5케이스 GREEN 유지 |
| 빌드·기동 | `./gradlew.bat :api:test` GREEN, `./gradlew.bat build` BUILD SUCCESSFUL, GET 응답 5필드 + Swagger 노출 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `rawdata_1m_h` GOOD 최신값 SELECT 합산만 — 알람 임계값·전이·복귀 조건 무접촉, `alarm_h` 기록 없음. 알람은 수신 파이프라인(`ScadaMessageProcessor`) 책임 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 기동 명령 없음. 읽기 전용 집계로 아웃바운드 어댑터·PLC 제어 경로 무접촉. 인터록 검사 트리거(제어 명령 발행) 미해당 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`/`ai_drvn_mod_h` 읽기·쓰기 없음. 사용자 의도·시스템 상태 변경 또는 SCADA 5분 강제 전환 로직 미포함. 조회 대상 `rawdata_1m_h`·`tag_m`·`instrument_m` 한정 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 모드 전환 이력(`transition_reason`)·제어 로그(`pump_ctrl_h`) 기록 조건 미발생. INSERT 없음 (읽기 전용 응답 DTO 반환만) |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `pump`(펌프) 표준 단어 신규 등록 (조합 재료, `pumpElpwr` DTO 필드. 비즈니스 도메인 약어 `pump` 폐기(2026-05-12)와 층위 별개 — `raw`/`use`/`role` 단어·비즈니스도메인 공존 선례 동형. 기본 데이터 도메인 미지정) — 완료 2026-06-05 (사용자 승인)

> 표준 데이터 도메인 신규 0건(`DOM_QTY_15_4` 재사용). 표준 용어(DB 컬럼) 신규 0건(DB 미생성 — `standard-terms.md` 미갱신). 비즈니스 도메인 약어 신규 0건(`opt` 재사용, `pump` 약어 재등록 아님). `elpwr`·`total`·`billing`·`peak`·`target`·`predc`·`dtm` 기등록.

## 산출물
- [계획안](../../../plan/20260605/전력피크분석-3번섹션/PLAN1.md)
