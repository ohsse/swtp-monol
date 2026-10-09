---
status: approved
created: 2026-06-01
updated: 2026-06-01
---
# 운전현황분석-7번섹션 — 도메인 분석

## 작업 배경

운전현황 분석 화면(`backend/image/운전현황분석.png`) 7번 섹션 카드의 backend 조회 API 신설. 활성 시설(`facilityId`, 12번 섹션에서 frontend 선택)의 **금일 00:00 ~ 현재시간** 1분 단위 시계열을 응답한다.

- **라인** = 활성 시설의 유출(송수) **유량(FRI)** · **압력(PRI)** — 계측 + 예측
- **막대** = 시설 소속 **펌프별 가동상태(OPS)** on/off 타임라인 — 계측 + 예측

같은 화면의 4(현황)·5(현황 비교 시계열)·9(예측 현황)·10(계측+예측 전력원단위 시계열) 섹션과는 **다른 관심사**(전력원단위가 아닌 유량·압력·펌프 가동)이므로, 기존 섹션을 폐기/재설계하지 않고 **병렬 신규 추가**한다 (사용자 메모리 "공존 섹션은 폐기 아님" 정합).

### 외부 산출물
- `backend/image/운전현황분석.png` — Read 도구 직접 시각 로드. 7번 섹션 = 상단 라인+막대 차트.

### 사용자 확정 결정 (2026-06-01, plan 모드)
- **Q1 펌프 가동상태(OPS) 막대 = 펌프별 on/off 타임라인** (각 펌프 개별 트랙, 1분 슬롯마다 계측 on/off + 예측 on/off — 대수 카운트 아님)
- **Q2 예측 시계열 시간 범위 = 금일 00:00 ~ 현재시간** (계측과 동일 구간 — 10번처럼 익일자정/미래까지 가지 않음)
- **Q3 시계열 조회 Repository = 7번 전용 신규 작성** (기존 5·10번 범위 조회 메서드 무수정 보존, 사이클 독립성 우선)

### 데이터 정합성 (검증 완료)
송수펌프제어 7번 `FacilityPredictionService` 가 이미 `predc_1m_h` 에서 예측 OPS/FRI/PRI 를 조회 중 → 계측(`rawdata_1m_h`)·예측(`predc_1m_h`) 양쪽에 OPS/FRI/PRI 가 모두 존재.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 표준 사전 정합성 — 신규 명명 후보 분류
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 명명 후보(엔드포인트 path `outflow-time-series`, 클래스 `FacilityOutflowTimeSeriesDto`/`Service`, DTO 필드 `actualFlwrt`·`actualPrsr`·`predcFlwrt`·`predcPrsr` + 펌프 on/off Boolean)는 **전부 Java 클래스명·DTO 변수명 영역으로 표준 사전 적용 외** — 10번 섹션 ANALYZE1 안건 1 결론(DB 컬럼명 조합 재료 아님, `actl`/`predc`/`today`/`comparison` 선례) 완전 동형. `flwrt`(유량, 2026-04-25)·`prsr`(압력, 2026-04-25)는 **기존 등록 표준 단어 재사용**. `outflow`(유출/송수)는 클래스명 영어 수식어로 DB 조합 재료 아님 → 등록 불요. 펌프 on/off Boolean 필드명은 기존 `FacilityStateService.isRunning`·`FacilityPredcOperatingStatusService.predcIsRunning` 선례에 `actl`/`predc` 대칭 접두어 차용 권장(사전 등록 무관 Java 명명).
- **결론**: 표준 사전 갱신 **0건** (신규 단어 0 / 기존 재사용 2 [`flwrt`·`prsr`] / 데이터 도메인 0 / 표준 용어 0). 룰 갱신 지시서 체크박스 0건 — PLAN 진입 전제조건 자동 충족.

### 안건 2: 7번 전용 신규 Repository 설계 + 인덱스·파티션 프루닝 + SLA
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 신규 표준 데이터 도메인 0건 — 2차 승인 미발생. 발견 사항:
    - **참고(낮음)**: 신규 vs 재사용 중복은 **블로커 아님**. 신규/재사용 모두 동일 인덱스·동일 파티션 프루닝 → DB 실행 계획 차이 0. `coding-discipline.md §2`(단순성)와 사용자 메모리 "사이클 간 자산 자동 원용 금지"(사이클 독립성)는 다른 축이며, 사용자가 독립성을 명시 선택했으므로 정당. 단 동일 시그니처 메서드가 raw/opt 에 3개 이상 누적되면 `common` 범용 추상화를 별도 ANALYZE 로 재검토 권고.
    - **정합**: `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)`·`idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` 활용 + `acq_dtm`/`predc_dtm` 범위 조건 월 RANGE 파티션 프루닝 강제 (`indexing-and-migration.md §1`·`partitioning-and-retention.md §1`). 신규 DDL 0건.
    - **패키지**: 계측 조회 `com.mo.swtp.raw`, 예측 조회 `com.mo.swtp.opt` 배치 적절. 명명의 'Section7'(UI 번호) 직접 반영은 `wtp-backend-engineer` 검토 대상.
    - **참고(낮음) SLA**: 시설 1개 (PUMP N대 + FLWMTR 1대) × OPS/FRI/PRI 태그 × 최대 1440분. PUMP 5대 ≈ 16,000행 ≈ 2.4MB → 200ms 타당. **PUMP 10대 이상(태그 25개+, 36,000행+) 시설은 PLAN 단계에서 최대 PUMP 대수 가정 명시 + 초과 시 커서/LIMIT 전환 조건 기재 권고** (`query-tuning.md §1`).
- **결론**: 7번 전용 신규 Repository 채택(사용자 결정). 기존 5·10번 무수정. Querydsl `IN + BETWEEN + ORDER BY` 5·10번 패턴 동형. 기존 인덱스 활용(신규 0건). SLA: 단일 호출 200ms, 최대 PUMP 대수 가정 PLAN 명시.

### 안건 3: Service·DTO·Controller 설계 + 헬퍼 재구현 + 정량 기준 + 명명
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 블로커 0 / 권고(중간) 2 / 참고(낮음) 1:
    - **DTO 구조**: **2-시리즈 분리안(`linePoints[]` + `pumpSeries[]`) 채택 권고**. 단일 `points[]` 중첩 시 라인/막대 서로 다른 차트 축이 한 배열에 섞이고 3단 중첩 접근 발생. `FacilityStateDto`(3번, `flwmtrs[]`+`pumps[]` 2-슬롯) 선례 정합. inner class 3종(`LinePoint`·`PumpSeries`·`PumpPoint`)은 has-a 컨테이너 포함(상속 아님)이라 `coding-discipline.md §2.1` DTO 상속 3단 기준 비해당 — 과하지 않음.
    - **권고(중간) @ArraySchema**: `linePoints`·`pumpSeries`·`PumpSeries.points` **3지점** `@ArraySchema(schema=@Schema(implementation=...))` 의무 (`api-patterns.md §@Schema(implementation)`). on/off Boolean 래퍼는 자동 인식.
    - **권고(중간) 정량 기준**: `buildPumpSeries` 3단 분해 권고 — `groupActualOpsByDtm` / `groupPredcOpsByDtm` / `toPumpPointList`. §2.5 면책(OT안전/쿼리빌더) 비해당 → 50줄/3단 분해 의무. `FacilityDailyTimeSeriesService` 3단 분해 패턴 준용 시 메서드당 30~40줄 유지 가능.
    - **명명**: 'Section7' 금지. 비즈니스 의미 기반 — Service `FacilityOutflowTimeSeriesService`·DTO `FacilityOutflowTimeSeriesDto`·CustomRepository `RawDataOutflowCustomRepository`(opt 측 동형). `{도메인}{비즈니스의미}` = 10번 `FacilityDailyTimeSeries` 선례 정합.
    - **참고(낮음)**: 엔드포인트 path 'Section7' 미반영 + 비즈니스 의미 기반 사전 확정 권고.
    - 헬퍼 재구현(추출 금지) = `coding-discipline.md §2·§3` + 사용자 메모리 정합 (10번 `FacilityDailyTimeSeriesService` Javadoc 에 이미 확립).
- **결론**: 2-시리즈 분리 DTO(`linePoints[]`+`pumpSeries[]`, inner 3종). `@ArraySchema` 3지점 의무. `buildPumpSeries` 3단 분해. 비즈니스 의미 명명 채택('Section7' 금지). 헬퍼 본 서비스 재구현.

### 안건 4: 도메인 4영역 + OPS tri-state + FRI/PRI 소스 + NULL 의미
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 블로커 0:
    - **도메인 4영역 전부 비해당** — 알람(임계값 평가·`alarm_h` 기록 없음, quality_cd 읽어 신뢰도 표시는 알람 아님) / 인터록(제어 명령 0) / AI 운전모드(`ai_drvn_mod_*` 무접촉, 읽기 전용) / 이력 기록(INSERT/UPDATE 0).
    - **OPS 3-state 정합 — 오히려 2-state보다 정확**. `§3` OPS 즉시 BAD 격상의 취지는 "통신 단절 후 ON 유지 → 운전원 오인 방지". 막대에서 BAD/UNCERTAIN/결측을 off(0.0)와 동일 처리하면 ON 불명 펌프를 "꺼짐"으로 **역방향 오인**. 3-state(on/off/null-불명) 표출이 §3 취지에 직접 부합. 4·5·10번 2-state(UNCERTAIN→On제외)는 집계·제어 판정 맥락(보수적)이라 시각 표출과 목적 다름.
    - **FRI/PRI = FLWMTR 태그 취득 타당** — 정수지·배수지 유출 유량은 FLWMTR 측정이 표준, 유출 압력을 FLWMTR PRI 태그로 수집하는 구성은 실무 흔함(3·6·10번 관행). 다중 FLWMTR 첫 매치 OK, 단 "주 유출 라인" 구분(`disp_ord`/`main_yn`) 가능 여부 PLAN 가정 명기 권고.
    - **계측/예측 라인 null 의미 분리 @Schema 명시 동의** (권고 수준, 도메인 안전 직결 아님).
    - **예측 PRI 표출 도메인 제약 없음** — 조회 전용, 이상치 기각(`§3`) 미적용. `predc_1m_h` quality_cd 부재로 null 여부만 예측 미수행 분기.
- **결론**: 4영역 비해당(구체 사유 명기). OPS **3-state(on/off/null) 채택** — 7번 막대 표출 전용 정책(4·5·10번 2-state와 의도 분리). FRI/PRI=FLWMTR. NULL 의미 분리 @Schema 명시. 예측 PRI 제약 없음.

---

## 표준 사전 카탈로그

본 사이클은 표준 사전 3층 모두 신규 등록 0건 (안건 1 결론). 표 생략.
- 신규 표준 단어: 없음 (`flwrt`·`prsr` 기존 재사용)
- 신규 표준 데이터 도메인: 없음
- 신규 표준 용어(DB 컬럼): 없음

---

## 신규 엔티티/DB 컬럼

**없음** — 조회 전용 API 신설. 신규 엔티티·DB 컬럼·DDL·인덱스 추가 0건.

신규 Java 클래스 (DB 영향 없음):
- `api/src/main/java/com/mo/swtp/facility/dto/FacilityOutflowTimeSeriesDto.java` (응답 DTO + inner `LinePoint`·`PumpSeries`·`PumpPoint`)
- `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` (Service)
- 7번 전용 계측 범위 조회 Repository (`com.mo.swtp.raw` — 인터페이스+Impl+결과 DTO, 명명 PLAN 확정)
- 7번 전용 예측 범위 조회 Repository (`com.mo.swtp.opt` — 인터페이스+Impl+결과 DTO, 명명 PLAN 확정)

수정 클래스:
- `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (엔드포인트 메서드 추가, 기존 5·10번 Repository 무수정)

테스트 클래스:
- `api/src/test/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesServiceTest.java`

---

## 기존 사전·패턴과의 충돌

**없음** — 표준 사전 영향 0건, 도메인 4영역 비해당, 기존 섹션(4·5·9·10) 무수정 병렬 추가. 신규 Repository 중복은 DBA 판정 권고(낮음, 사용자 사이클 독립성 결정으로 정당).

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델 초안**: 신규 엔티티 0건. 계측 OPS 판정(`effectiveVal`·tri-state)·예측 OPS 판정·FLWMTR FRI/PRI 선택 헬퍼는 3·5·9·10번 정책 동일하게 본 Service 내부 **재구현**(추출 금지).
- **API 설계**: 단일 endpoint (작업명 `GET /api/facility/{facilityId}/operating-status/outflow-time-series`). path 의 `operating-status` 유지 vs 제거(`/outflow-time-series`)는 PLAN 확정 — 기존 `@RequestMapping("/api/facility")` 단수 prefix 유지. 응답 `CommonResponseDto<FacilityOutflowTimeSeriesDto>`.
- **DTO 구조**: 2-시리즈 분리 — `linePoints: List<LinePoint>{dtm, actualFlwrt, actualPrsr, predcFlwrt, predcPrsr}` + `pumpSeries: List<PumpSeries>{instrumentId, instrumentNm, points: List<PumpPoint>{dtm, actualRunning, predcRunning}}`. `@ArraySchema(implementation)` 3지점 의무. `@JsonFormat("yyyy-MM-dd HH:mm:ss")`. `BaseAuditResponseDto` 미상속. on/off = tri-state `Boolean`(true/false/null).
- **OPS 3-state 판정**: 계측 = GOOD+effectiveVal==1.0→true, GOOD+0.0→false, BAD/UNCERTAIN/결측→null. 예측 = predc_val==1.0→true, 0.0→false, null→null. (4·5·10번 2-state와 의도 분리 — 막대 표출 전용, 도메인 전문가 승인)
- **시간 범위**: 계측·예측 모두 `[today 00:00, now)` (사용자 Q2). 서버 시간대(Asia/Seoul).
- **슬롯 정책**: dtm-key, 라인 양쪽(계측·예측) 부재 슬롯 생략 + dtm 오름차순(TreeMap, 10번 동형). 펌프 시계열은 펌프별 dtm-key.
- **Repository**: 7번 전용 신규(계측 raw + 예측 opt 각 1), Querydsl 범위 조회 5·10번 패턴 준용 + 기존 인덱스. 기존 5·10번 무수정.
- **정량 기준**: `buildPumpSeries` 3단 분해(`groupActualOpsByDtm`/`groupPredcOpsByDtm`/`toPumpPointList`) + `buildLinePoints`(10번 `buildActualSlotMap` 동형). 각 50줄 이내(§2.5 면책 비해당).
- **명명**: 'Section7'(UI 번호) 금지 — 비즈니스 의미 기반(`FacilityOutflowTimeSeriesService`/`Dto`, Repository 명 PLAN 확정).
- **ErrorCode**: `FACILITY_NOT_FOUND`·`UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` 재사용(신규 0건).

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 다중 태그 동기 수집/작성 가정 — 같은 1분 시점의 OPS/FRI/PRI 가 계측은 동일 `acq_dtm`, 예측은 동일 `predc_dtm` 슬롯에 일괄 존재 (4·5·10번 동형) | 가정 | PLAN 단계 단위 테스트 동일 시각 픽스처 검증. 미동기 시 미일치 태그 누락 허용 |
| 다중 FLWMTR 시설의 "주 유출 라인" 선택 — 현재 첫 매치. `disp_ord`/`main_yn` 우선 선택 필요 여부 | 미해결 → PLAN 결정 | 도메인 전문가 권고 — 본 사이클 첫 매치 유지 vs 정렬 기반. PLAN 명시 |
| 라인 필드명 `actual*` vs `actl*` + 펌프 on/off 필드명(`actualRunning`/`predcRunning` vs `actlIsRunning`/`predcIsRunning`) | 미해결 → PLAN 결정 | 10번 일관성(`actualFlwrt`)은 풀네임, glossary 권고는 `actl` 대칭. PLAN 에서 7번 내부 통일 확정 |
| 엔드포인트 path — `operating-status/outflow-time-series` vs `outflow-time-series` | 미해결 → PLAN 결정 | 기존 5·10번 operating-status 하위 그룹핑 일관성 vs 7번 비-전력원단위 의미. PLAN 확정 |
| 최대 PUMP 대수 SLA — PUMP 10대+(태그 25+, 36,000행+) 시 커서/LIMIT 전환 | 미해결 → PLAN 결정 | DBA 권고 — PLAN 에 최대 PUMP 대수 가정 명시 + 초과 시 분할 조회 조건. 본 사이클은 가정 내 단일 조회 |
| 신규 Repository 중복 누적 — raw/opt 동일 시그니처 3개 이상 시 `common` 추상화 재검토 | 가정 | DBA 권고 — 본 사이클 외(별도 ANALYZE), 사용자 사이클 독립성 결정 정합 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `FacilityOutflowTimeSeriesService` 단위 테스트 GREEN — 계측+예측 라인 공존 / 펌프별 on·off·불명(BAD·UNCERTAIN·null) 3-state / FRI·PRI 라인 결측 null / 양쪽 부재 슬롯 생략 / RSV·POINT 400 / 비활성·미존재 404 / VALVE 등 비대상 instrument 제외 | `./gradlew.bat :api:test --tests "FacilityOutflowTimeSeriesServiceTest"` PASS |
| 빌드 성공 | `./gradlew.bat build` 출력 BUILD SUCCESSFUL |
| Swagger 신규 엔드포인트 + DTO 노출 | `:api:bootRun` 후 Swagger UI 에서 엔드포인트 + `linePoints`·`pumpSeries`·중첩 inner 한국어 `@Schema` + 계측/예측 NULL 의미 분리 노출 |
| 파티션 프루닝(계측·예측) | `EXPLAIN ANALYZE` 출력에 `rawdata_1m_h_YYYYMM`·`predc_1m_h_YYYYMM` 만 + `Index Scan using idx_*_tag_time` |
| `buildPumpSeries`·`buildLinePoints` 50줄 이내 | 신규 Service 메서드별 본문 50줄 이내(빈줄·주석 제외) |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 알람 임계값 평가·전이·`alarm_h` INSERT 없음. `quality_cd` 를 읽어 표출 신뢰도(3-state)로 분류하는 것은 알람 발생이 아닌 데이터 신뢰도 표시 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 미발행, 아웃바운드 경로 미진입, `pump_interlock_p` 미참조 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 무접촉, 모드 전환·강제 전환 판정 없음, 읽기 전용 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 새 이력 INSERT/UPDATE 0건, `pump_ctrl_h`·`ai_drvn_mod_h` 무접촉, `transition_reason` 영향 없음 |

"비해당" 단독 4건 차단 해제 조건 충족: (1) 각 행 구체 사유 명기, (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 표준 사전 갱신 0건 + DB 영향 0건 + 도메인 4영역 비해당으로 룰 갱신 지시서 체크박스 **0건**. PLAN 진입 전제조건 자동 충족.

---

## 산출물
- [계획안](../../../plan/20260601/운전현황분석-7번섹션/PLAN1.md) (status: draft 예정)
