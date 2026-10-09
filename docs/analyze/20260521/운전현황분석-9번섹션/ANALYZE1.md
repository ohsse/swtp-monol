---
status: approved
created: 2026-05-21
updated: 2026-05-21
---
# 운전현황분석-9번섹션 — 도메인 분석

## 작업 배경

운전현황분석 페이지(`swtp/backend/image/운전현황분석.png`) 9번 섹션 카드 backend API 신설.
4번 섹션(`FacilityOperatingStatusService`, 실측값 `rawdata_1m_h` 기반 시설 운영 현황 카드)과 **동일 구조**이나 데이터 소스가 `predc_1m_h`(AI 예측 시계열, 송수펌프제어분석-7번섹션에서 도입)로 변경된 예측값 버전.

### 사용자 결정 (Round 0 — `/dev` 단계 4질문)

- **D1**: PWI 예측 태그 존재 — `tag_se_cd = PWI` 태그에도 `predc_1m_h` 예측값이 INSERT 된다는 전제. 4번 산출물(`totalElpwrAmt`·`elpwrUnitQty`) 모두 예측 버전으로 그대로 산출
- **D2**: 시점 = 시설 산하 사용 태그(OPS/PWI/FRI)들의 **시스템 전체 `max(predc_dtm)` 동일 시점** — 전 태그 동기화 (7번 섹션의 `acq_dtm + 1h ±W분` 근접매칭과는 다른 정책)
- **D3**: OPS On 판정 = `predc_val == 1.0` 단순 동등 — null/그 외 값은 On 제외. 7번 섹션 `FacilityPredictionService.toBoolean` (1.0=true / 0.0=false / 그 외=null) 패턴 정합
- **D4**: 이미지상 9번 = 4번과 동일 카드 구조의 별도 영역 (예측 버전 카드)

### 외부 산출물

- `swtp/backend/image/운전현황분석.png` (4번·9번 섹션) — Read 도구로 직접 시각 로드 완료
- 이전 사이클 docx `02.운전현황 분석 요구사항 명세서 v0.2.docx` **미참조** (4번 사이클 D1 + 사용자 메모리 정합)

---

## 회의록 (5인 회의 — 라운드 알고리즘)

### 안건 1: 표준 사전 정합성 (신규 어휘 분류 + 클래스/엔드포인트 명명)

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - `predicted`/`predict`/`prediction` 모두 `predc`(표준 단어 사전 2026-04-25 등록) 어근 파생형 → 동의어 동시 등록 금지 룰 위반 → 신규 등록 부적합. `predc` 기존 재사용.
  - DTO 클래스명 3 후보 비교 — **C안 `FacilityPredcOperatingStatusDto`** 권고 (`predc` 표준 단어 직접 사용 + 7번 `FacilityPredictionDto` 접두사 충돌 회피).
  - 응답 측정시각 필드 = **`predcDtm`** (7번 `PumpPredictionDto.predcDtm` 선례 정합, `predc_dtm` 표준 용어의 camelCase 변환).
  - 엔드포인트 경로 = **`/api/facility/{facilityId}/operating-status/prediction`** (4번 `/operating-status` 의 중첩 변형으로 의미 명확 + 7번 `/prediction` 와 분리).
- Round 2: 없음 (Round 1 단일 응답)
- **결론**:
  - 신규 표준 단어 / 데이터 도메인 / 표준 용어 / 비즈니스 약어 = **모두 0건** (기존 재사용 `predc`·`predc_dtm`·`predc_val`)
  - DTO 명: `FacilityPredcOperatingStatusDto`
  - Service 명: `FacilityPredcOperatingStatusService`
  - Repository 신규 메서드명: `findLatestPredictionByTagSrlNos(List<String>)`
  - 응답 측정시각 필드명: `predcDtm`
  - 엔드포인트: `GET /api/facility/{facilityId}/operating-status/prediction`

### 안건 2: OPS 예측 On 판정 + 신뢰도 결측 표현

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - 사용자 D3 (`predc_val == 1.0` 단순 동등) 도메인 안전성 통과. `ot-integration.md §3` OPS BAD 즉시 격상은 **실측 전용** (`quality_cd` SSOT) — 예측은 `quality_cd` 컬럼 부재로 구조적 적용 불가. 도메인 위반 아닌 설계 범위 차이.
  - **중간 권고**: `predc_val IS NULL`(예측 결측·모델 실패) vs `predc_val == 0.0`(예측 Off 확신) 의 응답 표현 구별 정책을 PLAN "가정 및 미해결 질문" 에 명기. 운전원이 두 상태를 구별 못 함. 서비스 로직(`toBoolean`)은 이미 null 반환하므로 변경 불필요, 응답 DTO Swagger `@Schema(description)` + SPEC 명기로 frontend 전파.
- **결론**: D3 채택. PLAN 가정 섹션에 "null = 예측 결측·신뢰도 불명 / false = 예측 Off 로 확신" 명기 의무. 본 사이클 `onPumpNms` 리스트는 둘 다 On 제외로 합쳐지므로 응답 스키마 변경 없음.

### 안건 3: SQL 패턴 (시스템 전체 `max(predc_dtm)` 일괄 조회)

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - 3 패턴 비교 — **패턴 A (서브쿼리) 권장**:
    ```sql
    SELECT tag_srl_no, predc_dtm, predc_val
    FROM predc_1m_h
    WHERE tag_srl_no IN (:tagSrlNos)
      AND predc_dtm = (SELECT MAX(predc_dtm) FROM predc_1m_h WHERE tag_srl_no IN (:tagSrlNos));
    ```
    옵티마이저 InitPlan 처리 + `idx_predc_1m_h_tag_time` 직접 활용 + 외부 쿼리 `predc_dtm =` 등가 → 단일 월 파티션 프루닝.
  - 패턴 B(CTE): PG 12+ 인라인화 — 패턴 A 와 동등. 가독성 우위.
  - 패턴 C(Window function): 태그 집합 전체 행 스캔 → **기피**.
  - **추가 인덱스 불필요** — `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` Index Scan Backward 자동 활용. 슬로우 발생 시점에 `EXPLAIN ANALYZE` 보고 재검토 (4번 `idx_tag_m_instrument_id_tag_se_cd` 부분 인덱스 선례 동형).
  - 7번 섹션 `findNearestByTagSrlNos` 와의 분리 정당 — 시점 정책(7번: `acq_dtm + 1h ±W분` LATERAL / 9번: 시스템 전체 max 등가) 완전 상이. 매개변수 분기 통합은 SRP 위반.
  - JPQL/Querydsl 표현 가능 — `§2.5` 면책 적용 불필요. native SQL 사용 시 7번 선례 동일 인용 근거 명기.
- **결론**: 패턴 A 채택 (Querydsl 또는 JPQL 표현 우선, 옵티마이저 분석 결과 native SQL 필요 시 `§2.5` 면책 인용). 추가 인덱스 0건.

### 안건 4: 클래스 분리 vs 공통 추상화

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **옵션 A (독립 클래스 `FacilityPredcOperatingStatusService`) 권장**.
    - 옵션 B(한 클래스 안 2메서드): Step 4 가 `RawDataRepository` vs 신규 `predc_1m_h` Repository — 의존성 완전히 다름. SRP 경계 약화.
    - 옵션 C(추상 부모 + 자식 2): `coding-discipline.md §2` "요청되지 않은 추상화 계층 금지" 위반.
    - 7번 선례 (`FacilityPredictionService` Javadoc "공통 추상화는 §2 위반" 명기) 동형 + 사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합.
  - Service Step 1~3 + 5~6 은 4번과 동형 구조 **재구현** (자동 원용 아님 — 인용 근거로만 사용).
  - `computeUnitConsumption` 단순화 — `quality_cd` 분기 제거 (3 케이스: 분모 0 / 분자 null / 분모 null). Javadoc 에 "predc_1m_h 는 quality_cd 미보유 — SCADA QUALITY 분기 미적용" 한 줄로 4번과의 차이 명기.
  - Controller 확장: 기존 `FacilityController` 에 4번(`findFacilityOperatingStatus`)·7번(`findFacilityPrediction`) 공존 중 → 9번도 동일 컨트롤러에 `findFacilityPredcOperatingStatus` 추가.
- **결론**: 옵션 A 채택. Step 4 만 신규 Repository 호출로 교체, 나머지 5단계 동형 재구현.

### 안건 5: 시설 종류 범위 + 빈 데이터 처리

- 호출 에이전트: `wtp-domain-expert` · `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4번과 동일 PWTF·DWT·PRSF 3종 권고. 예측 모델 학습 대상이 시설 종류에 의존하지 않음. RSV(저수조 펌프 미보유)·POINT(분기점 자체 펌프/유량계 없음) 거부 정합. `UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` 4번 ErrorCode **재사용**.
  - **wtp-backend-engineer**: 빈 데이터 = 4번 정합 — `onPumpNms = []`, `totalElpwrAmt = BigDecimal.ZERO`(On 펌프 0대 자연 결과), `elpwrUnitQty = null`(분모 0/null 방어), `predcDtm = null`.
- **결론**: 시설 종류 PWTF·DWT·PRSF 3종 + 빈 데이터 4번 정합. ErrorCode 재사용 — 신규 `FacilityErrorCode` enum 값 추가 0건.

### 안건 6: PWI/FRI 결측 시 `elpwrUnitQty` 처리

- 호출 에이전트: `wtp-domain-expert` · `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-domain-expert**: PLAN "가정 및 미해결 질문" 명기 의무 — 부분 결측 시 null(전체 포기) vs 부분 합산. 4번 가정 2 결정(BAD/UNCERTAIN 전액 제외) 의 예측 매핑.
  - **wtp-backend-engineer**: 4번 정합 — PWI predc_val null 인 펌프는 합산에서 제외 (stream filter), FRI predc_val null/0 시 `elpwrUnitQty = null` (ArithmeticException 방지).
- **결론**: PWI predc_val null → 해당 펌프만 합산 제외 (다른 On 펌프 합산 유지) / FRI predc_val null·0 → `elpwrUnitQty = null`. PLAN 가정 섹션 명기.

---

## 표준 사전 카탈로그

### 신규 표준 단어
없음 — `predc`(2026-04-25 등록) 기존 재사용.

### 신규 표준 데이터 도메인
없음.

### 신규 표준 용어
없음 — `predc_dtm`(2026-05-18 재등록)·`predc_val`(2026-05-18 등록) 기등록.

분류값 (3층 공통) 종합: **신규 0건 / 기존 재사용 3건(`predc`·`predc_dtm`·`predc_val`) / 유사 충돌 0건 / 폐기·통합 0건**.

---

## 신규 엔티티/DB 컬럼

없음 — 조회 전용 API. `predc_1m_h` + `facility_m`/`instrument_m`/`tag_m` 기존 마스터 + 인덱스 `idx_predc_1m_h_tag_time` 기존 자산만 사용.

---

## 기존 사전·패턴과의 충돌

- DTO 클래스명 `FacilityPredcOperatingStatusDto` 와 7번 `FacilityPredictionDto` 의 접두사 패턴 차이는 의도 — 7번 = AI 예측값 자체 표출, 9번 = 예측 기반 운전 현황 카드. 충돌 없음.
- Service `FacilityPredcOperatingStatusService` 와 4번 `FacilityOperatingStatusService` / 7번 `FacilityPredictionService` 공존 — 동일 컨트롤러 책임 분담 (`FacilityController` 에 3 메서드 공존).
- 엔드포인트 `/operating-status/prediction` 중첩 형태 — 4번 `/operating-status` 의 하위 변형 + 7번 `/prediction` 와 분리.

---

## PLAN 으로 전달할 결정 사항

### 신규 클래스 3종
- `com.mo.swtp.facility.service.FacilityPredcOperatingStatusService` (옵션 A 독립 클래스)
- `com.mo.swtp.facility.dto.FacilityPredcOperatingStatusDto` (6필드 단층, 4번 DTO 와 동형)
- `com.mo.swtp.opt.repository.TagPredictionCustomRepository.findLatestPredictionByTagSrlNos(List<String>)` 메서드 추가 + Impl

### 기존 클래스 확장
- `FacilityController.findFacilityPredcOperatingStatus(facilityId)` 메서드 추가 — `GET /api/facility/{facilityId}/operating-status/prediction`

### 정책 결정
- **SQL 패턴**: 패턴 A (서브쿼리, JPQL/Querydsl 우선 / 필요 시 native + `§2.5` 면책 인용)
- **시설 종류**: PWTF·DWT·PRSF 3종 — `FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` 재사용 (신규 enum 값 0건)
- **OPS On 판정**: `predc_val == 1.0` (null/그 외 → On 제외) — 7번 `toBoolean` 패턴 정합
- **빈 데이터**: `onPumpNms=[]` / `totalElpwrAmt=BigDecimal.ZERO` / `elpwrUnitQty=null` / `predcDtm=null`
- **`computeUnitConsumption`**: 분기 3 케이스 (분모 0 / 분자 null / 분모 null) — `quality_cd` 분기 제거, Javadoc 에 4번과의 차이 명기
- **인덱스 변경 0건** — 기존 `idx_predc_1m_h_tag_time` 활용

### 응답 DTO 구조 (6필드 단층)

| 필드 | 타입 | 4번 대응 | 설명 | 빈 데이터 시 |
|------|------|---------|------|------------|
| `facilityId` | `String` | 동일 | 활성 시설 ID | — (항상 채움) |
| `facilityNm` | `String` | 동일 | 시설명 | — (항상 채움) |
| `predcDtm` | `LocalDateTime` | `measurementDtm` 대응 | 동기화된 예측 대상 시점 (`max(predc_dtm)`) | `null` |
| `onPumpNms` | `List<String>` | 동일 | 예측 On 펌프 이름 목록 | `[]` |
| `totalElpwrAmt` | `BigDecimal` | 동일 | 예측 On 펌프 PWI 합산 (kW) | `BigDecimal.ZERO` |
| `elpwrUnitQty` | `BigDecimal` | 동일 | 예측 전력원단위 (kWh/m³) | `null` |

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| OPS 예측 결측(`predc_val IS NULL`) 과 예측 Off(`predc_val == 0.0`) 의 응답 표현 구별 정책 | 미해결 | PLAN 결정 — 본 사이클 `onPumpNms` 리스트는 둘 다 On 제외로 합쳐지나, Swagger `@Schema(description)` + SPEC 명기로 운전원 정보 결손 리스크 완화 (안건 2 도메인 권고) |
| PWI 부분 결측(특정 펌프 PWI `predc_val IS NULL`) 시 `totalElpwrAmt` 처리 | 가정 | 본 사이클 가정 = 해당 펌프만 합산 제외 + 다른 펌프 합산 유지 (4번 stream filter 패턴 정합) |
| FRI 결측(`predc_val IS NULL` or `0`) 시 `elpwrUnitQty` 처리 | 가정 | `elpwrUnitQty = null` (분모 무효 방어) — 4번 `computeUnitConsumption` 가정 3 정합 |
| 시점 동기화 — 특정 태그가 동일 `max(predc_dtm)` 시점 행을 보유하지 않는 경우 | 가정 | 해당 태그는 Map 결측 → OPS 결측 시 On 제외 / PWI 결측 시 PWI 합산 제외 / FRI 결측 시 `elpwrUnitQty = null`. 부분 결측 허용 |
| `predc_1m_h` 데이터 자체가 0건 (시스템 전체) 시점에서의 응답 | 가정 | `predcDtm = null` + `onPumpNms = []` + `totalElpwrAmt = BigDecimal.ZERO`(On 펌프 0대 자연 결과) + `elpwrUnitQty = null`. PLAN 단계 최종 확정 |
| AI 추론 파이프라인 (`predc_1m_h` INSERT 경로) — 본 사이클 미접촉 | 결정 | 사이클 2 (`/dev:analyze`) 결정 대기 — 조회 전용 사이클이므로 INSERT 시나리오 무영향 (`opt.sql` 헤더 명시) |
| `predcDtm` 직렬화 — `LocalDateTime` 정책 | 가정 | `@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 4번 동일 (`api-patterns.md §BaseAuditResponseDto 직렬화 정책` 정합) |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `FacilityPredcOperatingStatusService.findFacilityPredcOperatingStatus(facilityId)` 가 활성 시설의 예측 On 펌프 이름·예측 PWI 합산·예측 전력원단위·`predcDtm` 을 단일 호출로 반환 (정상 시나리오 — PUMP 3대 중 2대 예측 On, FRI/PWI 예측 정상) | 신규 단위 테스트 PASS: `예측운영현황_정상_조회_시_예측On펌프이름_예측PWI합산_예측전력원단위_predcDtm_반환` |
| 시스템 전체 `max(predc_dtm)` 동일 시점 동기화 — 모든 태그가 동일 시점 행으로 조회 | 단위 테스트 PASS: `predcDtm_시점_동기화_검증` |
| OPS On 판정 `predc_val == 1.0` 만 — null/0.0/0.7/1.5 모두 On 제외 | 파라미터화 테스트 PASS: 4 케이스 모두 On 제외 |
| `quality_cd` 분기 0건 — 9번 Service 메서드 grep 시 `qualityCd` 키워드 부재 | Service 메서드 grep 검증 (REVIEW 자동 점검) |
| 시설 종류 PWTF·DWT·PRSF 3종 외 거부 (RSV/POINT) — 4번 ErrorCode 재사용 | 단위 테스트 PASS: RSV/POINT → `UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` |
| 빈 데이터 (`predc_1m_h` 0건) → `predcDtm=null` / `onPumpNms=[]` / `totalElpwrAmt=ZERO` / `elpwrUnitQty=null` | 단위 테스트 PASS |
| `Service`·`Repository` 신규 메서드의 정량 기준 `§2.1` (50줄·3단·3단) 준수 | `wtp-backend-engineer` REVIEW 자동 점검 |
| `GET /api/facility/{facilityId}/operating-status/prediction` Swagger UI 6필드 정상 노출 + 한국어 description | `./gradlew.bat :api:bootRun` 후 Swagger UI 호출 |
| 빌드 + 전체 테스트 통과 | `./gradlew.bat build` BUILD SUCCESSFUL |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 조회 전용. 알람 임계값·전이 조건 무접촉. `predc_1m_h` 에 `quality_cd` 부재 — UNCERTAIN/BAD 격상 경로 자체 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 조회 화면. 제어 명령 발행 경로 없음. 인터록 선행조건 검사 대상 아님 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 무접촉. 사용자 의도/시스템 상태 변경 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 신규 엔티티/DB 컬럼 0건. `ai_drvn_mod_h.transition_reason` · `pump_ctrl_h` 무접촉 |

"비해당" 4건 모두 **조회 전용 + 신규 엔티티/컬럼 0건** 동시 충족 — 차단 해제 분기 통과 (`wtp-domain-expert` Round 1 확인).

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 룰 파일 갱신 0건 — `wtp-glossary-manager` Round 1 결론 (신규 단어/도메인/용어/약어 모두 0건).

- 없음.

---

## 산출물
- [계획안](../../../plan/20260521/운전현황분석-9번섹션/PLAN1.md) — `/dev:plan` 단계에서 작성 예정 (사용자 승인 후 자동 전이)

---

## 참조 문서

- 4번 섹션 산출물: `swtp/backend/docs/{analyze,plan,tasks}/20260521/운전현황분석-4번섹션/` (구조·정책 인용 근거)
- 7번 섹션 산출물: `swtp/backend/docs/{analyze,plan,tasks}/20260518/송수펌프제어분석-7번섹션/` (예측 시계열 도입 + `FacilityPredictionService` 선례)
- 도메인 룰: `swtp/backend/.claude/rules/ot-integration.md §3·§5`
- 다형성 룰: `swtp/backend/.claude/rules/entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰`
- DB 정책: `swtp/backend/.claude/rules/db/partitioning-and-retention.md §1`·`db/indexing-and-migration.md §1·§5`·`db/query-tuning.md §1·§2`
- DTO 패턴: `swtp/backend/.claude/rules/api-patterns.md §BaseAuditResponseDto 패턴`
- 코딩 디시플린: `swtp/.claude/rules/coding-discipline.md §2`·`§2.1`·`§2.5`·`§4.2`
- 참조 코드: `swtp/backend/api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusService.java` (4번 동형 구조 재구현 근거) · `FacilityPredictionService.java` (7번 클래스 분리 선례)
