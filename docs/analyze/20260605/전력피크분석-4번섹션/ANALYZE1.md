---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-4번섹션 — 도메인 분석

## 작업 배경

전력피크분석 화면(`backend/image/전력피크분석.png`)의 **4번 섹션**("송수펌프단계예측 임시제어 전력량 예측") 백엔드 API 구현. 1·2·3번 섹션(목표값 마스터 + 5지표 집계)은 완료 상태이며, 4번 섹션은 두 기능으로 구성된다.

- **기능1**: 펌프를 소유한 시설물 목록 조회 (프론트 택일용)
- **기능2**: 택일된 시설물이 보유한 펌프들의 **전력량 예측값**(PWQ 적산전력량 기반 kWh)을 현재시간 ~ +24시간 단일 시계열로 표출

### 사용자 확정 결정 (plan 단계 AskUserQuestion)
1. 기능1: 기존 `GET /api/facility?hasPump=true` **재사용** → 신규 백엔드 코드 0건
2. 기능2 측정유형: **PWQ 적산전력량 (kWh)** (PWI 순시전력 아님)
3. 기능2 응답형태: **시설물 합산 단일 시계열**
4. 기능2 시간단위: **1시간 버킷 (24개 포인트)**

### 외부 산출물
- `backend/image/전력피크분석.png` — 4번 섹션 단일 추세선("전력예측값추세") 차트

---

## 회의록 (5인 회의 — Round 1 종결, 블로커 0건)

### 안건 1: 응답 DTO·클래스 어휘 정합성
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 DB 컬럼 없음(`predc_1m_h` 재사용) → 표준 용어 등록 0건. 응답 DTO 필드는 기존 `PumpPowerTimeSeriesDto.PumpPowerTimeSeriesPoint`(실측 PWQ 에너지) 선례대로 `baseDtm`(버킷 시작) + `elcegVal`(kWh 차분) **재사용**. `predc` 접두(`predcDtm`)는 표준 용어 `predc_dtm`(`predc_1m_h` 파티션 키)과 **동의어 충돌**이므로 금지 — 예측 맥락은 클래스명 `PumpEnergyPrediction*` 이 이미 담음. "energy"는 Java 클래스명 전용으로 표준 단어 등록 대상 외(DB 컬럼은 `elceg`).
- **결론**: 어휘 룰 갱신 **0건**. 필드명 `baseDtm` + `elcegVal` 기존 재사용. 클래스명 영문 자유 표기.

### 안건 2: 계층 구조·ErrorCode·컨트롤러 배치
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: (Q1) opt Service 의 facilityId 검증 실패 시 `FacilityErrorCode.FACILITY_NOT_FOUND` **교차 참조 허용** — `exception-patterns.md §5`는 네이밍만 규정·교차 참조 금지 조항 없음, `FacilityOperatingStatusService` 선례 일치. `OptErrorCode` 신규 추가는 동의어 enum·프론트 명세 이중 소스. (Q2) **기존 `PeakPowerAnalysisController` 메서드 추가** 권장 — 동일 화면·동일 `@Tag "14. 전력피크 분석"`·동일 경로 prefix, SRP 위반 아님(동일 변경 이유). 신규 컨트롤러 분리는 `@Tag` 중복·경로 분기로 Swagger 혼선. (Q3) 4-SELECT + private 헬퍼 분리로 50줄/3단 충족 가능 — 단계 ②③⑤ 헬퍼 분리 + 음수차분 제외는 `filterValidDelta` 헬퍼 추출 권고. (Q4) Repository 트리플 신설은 opt 관행(PeakPredc/TagPredcRange/...) 정합.
- **결론**: ErrorCode 교차 참조 채택 / 컨트롤러는 기존 `PeakPowerAnalysisController` 메서드 추가 / 헬퍼 분리로 정량 기준 충족 / Repository 트리플 신설.

### 안건 3: predc_1m_h 신규 native 쿼리 성능·정합성
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: (Q1) `tag_srl_no IN` 등가 선두 → `idx_predc_1m_h_tag_time` Index Scan, `predc_dtm` 범위 → 24h 윈도우 최대 2개 파티션 프루닝 강제. 스테이징 `EXPLAIN (ANALYZE, BUFFERS)` "Partitions selected" 1~2 검증 권고. (Q2) **펌프별 `MAX−MIN` 후 버킷 합산이 수학적으로 정확** — `SUM(MAX−MIN) ≠ SUM(MAX)−SUM(MIN)`, 펌프별 baseline 상쇄 오류 방지. (Q3) `predc_val IS NOT NULL` 단독 충분(quality_cd는 예측에 의미 없음). 음수 차분은 Service `aggrVal<=0 → 버킷 생략` + **로그 권고**(DB `HAVING`보다 가독성 우수). (Q4) 현재 시 부분 버킷은 과소 산정 — 실측 버전 동일 특성, 완성 버킷만 반환 vs 부분값 포함 표시는 **사용자 결정 위임**. (Q5) §2.5 면책(query-tuning.md §2) 적정.
- **결론**: 인덱스·프루닝·차분합산 정합 통과. 음수차분 Service 생략+로그. 현재 시 부분버킷 정책은 가정 섹션 결정(아래).

### 안건 4: PWQ 예측 에너지 산정 도메인 규칙 + 4영역 점검
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: (Q1) `ot-integration.md §3` PWQ 정책(HLV 미적용·GOOD only 차분)의 **의도(차분 왜곡 방지)** 는 실측 전용. `predc_1m_h`는 `quality_cd` 부재 → `predc_val IS NOT NULL` 단독이 도메인 정합. (Q2) 음수 차분 = AI 모델 이상, 해당 버킷 생략 + 로그(읽기 API라 알람 의무 없음, 모델 진단 근거). (Q3) **도메인 4영역 전부 비해당** — 읽기 전용 조회, 알람·인터록·운전모드·이력 쓰기 경로 없음. (Q4) "전력량"=에너지(kWh) → PWQ 차분 해석 타당.
- **결론**: PWQ 차분 산정 도메인 정합. 4영역 전부 비해당(블로커 아님). 음수차분 생략+로그.

---

## 표준 사전 카탈로그

> 본 사이클은 **신규 DB 테이블·컬럼 없음**(`predc_1m_h` 재사용). 3층 어휘 신규 등록 0건. (안건 1 결론)

### 신규 표준 단어
없음

### 신규 표준 데이터 도메인
없음

### 신규 표준 용어
없음

---

## 신규 엔티티/DB 컬럼

없음 — 기존 `predc_1m_h`(`TagPrediction` 엔티티) 재사용. 신규 테이블·컬럼·인덱스·DDL 변경 없음.

신규 자산은 모두 `api` 모듈 코드(웹/서비스/DTO/Repository)이며 `common` 엔티티·DB 변경 없음:
- `PumpPowerEnergyPredictionDto` (응답, 중첩 `Point` — 필드 `baseDtm`+`elcegVal`)
- `PredcEnergyBucketDto` (내부 전송 record — `RawDataBucketDto` 동형)
- `PumpEnergyPredcRepository` + `PumpEnergyPredcCustomRepository` + `PumpEnergyPredcCustomRepositoryImpl` (native, predc_1m_h 버킷 차분)
- `PumpEnergyPredictionService`
- `PeakPowerAnalysisController` 신규 GET 메서드 (기존 컨트롤러 확장)

---

## 기존 사전·패턴과의 충돌

없음. 4개 에이전트 모두 블로커 0건. 검토된 잠재 충돌과 해소:
- 필드명 `predc` 접두 → `predc_dtm` 표준 용어와 동의어 충돌 위험 → `baseDtm`/`elcegVal` 재사용으로 회피 (안건 1)
- ErrorCode 교차 도메인 참조 → `exception-patterns.md` 금지 조항 없음, 선례 존재 → 허용 (안건 2)

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 / 데이터 흐름 (변경 없음, 조회 경로만)
```
facilityId
 → instrument_m (equip_type_cd='PUMP', use_yn='Y')          # 활성 펌프
 → tag_m (instrument_id IN, tag_se_cd='PWQ', use_yn='Y')     # 펌프별 적산전력량 태그
 → predc_1m_h (tag_srl_no IN, predc_dtm ∈ [start, start+24h]) # 예측 시계열
 → 펌프별 date_trunc('hour') MAX(predc_val)-MIN(predc_val)    # 펌프별 에너지 차분
 → 버킷별 펌프 합산(음수 차분 버킷 생략+로그)                 # 시설 단일 시계열(kWh)
```
- `start = date_trunc('hour', now)`, `end = start + 24h` → 24 시간 버킷

### 적용 패턴 (회의 결론)
- **ErrorCode**: `FacilityErrorCode.FACILITY_NOT_FOUND`(404) 교차 참조 — 활성 시설 미존재 시
- **컨트롤러**: 기존 `PeakPowerAnalysisController`(`@Tag "14. 전력피크 분석"`)에 GET 메서드 추가. 경로 `GET /api/opt/peak-power-analysis/pump-energy-prediction?facilityId={id}`
- **Service**: `FacilityOperatingStatusService` 동형 4-SELECT + private 헬퍼 분리(②PUMP필터·③PWQ필터·⑤버킷합산·`filterValidDelta`). 공개 메서드 50줄/추상화 3단 충족
- **Repository 트리플**: opt 전용 신설(native, predc_1m_h `MAX-MIN` per `date_trunc('hour')`). §2.5 면책(query-tuning.md §2) 주석 명기
- **DTO 필드**: `baseDtm` + `elcegVal` 재사용 (`PumpPowerTimeSeriesDto` 선례). `unit="kWh"`. `BaseAuditResponseDto` 미상속(합성 뷰)
- **음수 차분**: Service 가 `aggrVal <= 0` 버킷 생략 + WARN 로그(모델 품질 진단 근거)
- **DDL/사전**: 변경 0건

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 측정 단위는 PWQ 적산전력량 기반 kWh (PWI 아님) | 결정 | plan 단계 AskUserQuestion 사용자 확정. domain-expert 타당 판정 |
| 현재 시(時) 버킷은 부분 집계로 포함 — 윈도우 `[date_trunc('hour',now), +24h)` 24버킷 고정 | 결정 | DBA Q4 위임 사항. 예측 차트 특성상 현재 시 부분값 자연 발생, 실측 버전 동일 구조. Swagger 에 "현재 시 버킷 부분 집계" 명기 |
| 빈 버킷(데이터 0)은 응답에서 생략 (24슬롯 null 고정 채움 아님) | 결정 | `PumpPowerTimeSeriesDto` 선례 동형. 펌프/태그/예측 0건은 빈 시계열(200) |
| 컨트롤러는 기존 `PeakPowerAnalysisController` 메서드 추가 (신규 컨트롤러 분리 아님) | 결정 | backend-engineer 권고. 컨트롤러 javadoc "2·3번섹션" → "2·3·4번섹션" 확장 |
| 시설 미존재/비활성 시 `FacilityErrorCode.FACILITY_NOT_FOUND`(404) 교차 참조 | 결정 | backend-engineer 허용 판정 |
| 펌프당 PWQ 태그 다건 시 첫 태그 사용 가정 | 가정 | `PumpPowerTimeSeriesService.loadPwqTagByInstrument` 동형 (toMap merge 첫 태그) |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `PumpEnergyPredictionServiceTest` 6 케이스 GREEN | 신규 단위 테스트 — 다펌프 정상 합산 / 시설 미존재 404 / 펌프 0대 빈 시계열 / PWQ 태그 0개 빈 시계열 / 음수차분 버킷 생략 / 예측 0행 빈 시계열 |
| 펌프별 차분 후 합산 정확성 검증 | 2펌프 서로 다른 적산 baseline mock → 버킷 합산값이 `SUM(펌프별 MAX-MIN)` 과 일치하는지 단위 테스트 GREEN |
| 신규 GET 엔드포인트 24버킷 응답 | dev 더미데이터(pgAgent PWQ 우상향 누적) 기동 상태에서 `GET /api/opt/peak-power-analysis/pump-energy-prediction?facilityId={펌프보유시설}` → 24개 버킷·`elcegVal` 양수 확인 |
| 빌드·회귀 통과 | `./gradlew.bat :api:test` BUILD SUCCESSFUL, 기존 테스트 회귀 0건 |

---

## 도메인 룰 4영역 점검

> 본 작업은 **읽기 전용 조회 API** 이며 "## 신규 엔티티/DB 컬럼" 없음. 4영역 전부 비해당 + 구체 사유 명기(차단 해제 조건 충족). (안건 4 `wtp-domain-expert` 판정)

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `predc_1m_h` 예측값 차분 산정은 알람 임계값·전이·복귀 조건에 접촉 없음. 알람 트리거 경로 부재 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 발행 없는 읽기 전용 조회. 선행조건 검사·기동 차단·복구 재검사 의무 무관, `⚠️ 절대 금지` 규정 접촉 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음. SCADA 5분 초과 강제 전환 트리거 무관. 예측값 조회는 운전 모드 전환 경로 외 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 읽기 전용 — `ai_drvn_mod_h`·`pump_ctrl_h` 이력 쓰기 경로 없음 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 어휘 사전·룰 갱신 **0건** (안건 1 `wtp-glossary-manager` 확정 — 신규 DB 컬럼 없음, 기존 표준 단어 조합 재사용). 갱신 대상 룰 파일 없음.

- [x] 룰 갱신 없음 — `swtp/.claude/rules/dict/*` 및 `.claude/rules/dict/standard-terms.md` 변경 0건 (glossary-manager 확정)

---

## 산출물
- [계획안](../../../plan/20260605/전력피크분석-4번섹션/PLAN1.md)
