---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-5번섹션 — 도메인 분석

## 작업 배경

전력피크분석 화면(`backend/image/전력피크분석.png`)의 **5번 섹션**("전력 피크 예상 시간" — 화면 하단 추세 차트) 백엔드 API 구현. 1·2·3·4번 섹션(목표값 마스터+SSE / 5지표 집계 / 펌프 순시전력 / 시설 펌프 전력량 예측 시계열)은 완료 상태다.

5번 섹션은 현재시각 기준 **±12시간(총 24시간)** 윈도우 단일 추세 차트로, 다음을 표출한다:
- **발생 전력량**(12h전~현재): 실측 적산전력량(PWQ) 1시간 버킷 차분 — 시스템 전역 합산 (kWh, 파란 영역)
- **예측 전력량**(현재~12h후): 예측 적산전력량(PWQ) 1시간 버킷 차분 — 시스템 전역 합산 (kWh, 라인)
- **요금적용전력피크**(kW) · **목표피크**(kW): 차트 가로 기준선 스칼라

> 예: 현재 16시이면 04시~익일 04시. 계측값은 12h전~현재, 예측값은 현재~12h후.

### 사용자 확정 결정 (plan + analyze 단계 AskUserQuestion)
1. **지표** = 적산전력량 PWQ (kWh) — 섹션4 동형 (`MAX(val)-MIN(val)` 버킷 차분). PWI 순시전력 아님.
2. **시간 해상도** = 1시간 버킷.
3. **조회 범위** = 시스템 전역 — **전체 활성 PWQ 태그 합산** (`tag_se_cd='PWQ' AND use_yn='Y'` 전부). analyze 단계 재확인 (이중계상 컨텍스트 명시 후 사용자 재확정).
4. 요금/목표 피크 스칼라는 차트 기준선 — kW (시계열 kWh 와 단위 다름, 프론트 이중축).

### dev DB 데이터 현황 (read-only 조회로 확인)
- 활성 PWQ 태그 8개 **전부 PUMP 서브미터**. 메인 인입 ELCMTR 적산미터 **0개** → "전체 PWQ 합산" = "펌프 PWQ 합산" (현 시점 이중계상 없음).
- 실측 PWQ 직전 12h: 5,760행 (8태그 × 720분, 분당 1행 — 시간 버킷 충분). 예측 PWQ 향후 12h: 존재.

### 외부 산출물
- `backend/image/전력피크분석.png` — 5번 섹션 하단 "전력 피크 예상 시간" 차트 (발생전력 영역 + 예측전력 라인 + 요금피크/목표 기준선).

---

## 회의록 (5인 회의 — Round 1 종결, 블로커 1건 → 사용자 결정으로 해소)

### 안건 1: 응답 DTO·클래스 어휘 정합성
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 DB 컬럼 0건(`predc_1m_h`·`rawdata_1m_h`·`opt_peak_target_p` 재사용) → 표준 용어/단어/데이터 도메인 등록 **0건**. 발생/예측 시계열 필드명은 `measuredPoints`/`predictedPoints` **영문 서술형(풀네임)** 채택 — `predc` 접두는 4번섹션에서 `predc_dtm`(DB 표준용어) 동의어 충돌로 필드명 금지 판정됐으나 `predictedPoints` 풀네임은 표기 계층이 달라 회피 가능. `actl`(실제, 단일 측정값 수식어)은 시계열 버킷 집계 구분자로 의미 층위가 달라 부적합 → `measured` 의미가 정확. 단 `measured` 신규 단어 등록은 불요(DB 컬럼 없는 Java DTO 필드 전용, `actl` 유사 충돌 회피). 중첩 Point 필드 `baseDtm`+`elcegVal` 는 4번섹션 재사용 확정 조합.
- **결론**: 어휘 룰 갱신 **0건**. 시계열 컬렉션 필드 `measuredPoints`/`predictedPoints`, Point 필드 `baseDtm`/`elcegVal` 재사용.

### 안건 2: 자산 재사용(예측/실측 Repository) + 스칼라 출처 + 계층 구조
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약 (블로커 0 / 권고 1 / 참고 2):
  - **Q1 예측 PWQ 조회**: 섹션4 `PumpEnergyPredcRepository.findEnergyDeltaBuckets(tagSrlNos, start, end)` **재사용 권고**. 쿼리는 `predc_1m_h` 임의 `tag_srl_no IN` 으로 동작하므로 "펌프 특화"는 명칭 문제일 뿐 도메인 중립. 신규 트리플 신설은 동일 SQL 복제 = 데드 코드 대칭(`coding-discipline.md §2`). "사이클 간 자산 자동 원용 금지" 메모리는 섹션 경계 무검증 재사용 차단이 목적 — 동일 opt 도메인 회의 결정 재사용은 섹션2의 `PeakTargetService` 재사용 선례가 지지. 단 Javadoc 스코프를 "시설 PWQ → 전역 PWQ 추세 확장" 이력으로 갱신.
  - **Q2 실측 PWQ 조회**: raw 공용 `findEnergyDeltaBuckets(..., "hour")` **재사용 적정**. `dateTruncUnit` 파라미터화·`GOOD`+`raw_val` 단독 필터가 `ot-integration.md §3` PWQ 정책 정합. 단 섹션5는 `facilityId` 없이 `tagRepository.findByTagSeCdAndUseYn(PWQ, Y)` 직접 전역 수집 — PLAN 가정 명기.
  - **Q3 요금/목표 스칼라 출처**: **(a) 섹션5 독립 재계산 권고** (`findMaxMinuteSumElpwr` + `getPeakTarget`). (b) 섹션2 `getPeakPowerAnalysis()` 호출 후 2개 추출 = 불필요 연산+결합도(`§2` 위반). (c) 응답 제외 = API 계약 오염. 섹션5 DB 조회 4회(실측 PWQ + 예측 PWQ + PWI MAX + 목표피크) 는 `PeakPowerAnalysisService` 4-SELECT 선례 동형.
  - **Q4 서비스 구조**: 50줄/3단 충족 가능. 버킷 합산 `aggregateByBucket` private 헬퍼 2개(실측·예측)로 분리 권고 (섹션4 선례).
  - **Q5 DTO**: `PeakEnergyTrendDto` plain `@Getter`+정적팩토리, `BaseAuditResponseDto` 미상속 적정(시계열·집계는 적용 외, `api-patterns.md`). **권고(중간)**: 시계열 리스트가 `List<사용자정의Point>` 이므로 `@ArraySchema(schema=@Schema(implementation=Point.class))` 명기 **의무**.
- **결론**: 예측 repo 섹션4 재사용 + Javadoc 갱신 / 실측 raw 공용 재사용 / 스칼라 독립 재계산(응답 포함) / 헬퍼 2분리 50줄 충족 / `@ArraySchema` 명기 의무.

### 안건 3: DB 쿼리 성능·파티션 프루닝·요금피크 반복 스캔
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약 (블로커 0 / 권고 1 / 참고 2):
  - 인덱스 `idx_rawdata_1m_h_tag_time`·`idx_predc_1m_h_tag_time` 등가 선두·범위 후위 정합. `acq_dtm`/`predc_dtm` 범위로 월 RANGE 파티션 프루닝 작동. 펌프별 `MAX-MIN` 후 버킷 합산 수학적 정확(섹션4 동형). DDL 0건 → 표준 데이터 도메인 2차 승인 불요. §2.5 면책(query-tuning.md §2) 적정.
  - **권고(중간)**: `findMaxMinuteSumElpwr` 12개월 분별 SUM MAX 스캔이 섹션2·5 동시 폴링 시 반복. 폴링 주기 수초면 부하 비선형 증가 → 섹션5 응답에서 요금피크 제외(프론트 섹션2 재사용) 또는 `@Cacheable`(TTL 1분) 권고.
  - **참고(낮음)**: 전역 PWQ `tag_srl_no IN` 리스트 — 100 미만 무시 가능, 500 초과 시 대체 검토. 현재 시 부분 버킷 과소산정 — 프론트 주석 여부 PLAN 명시.
- **결론**: 인덱스·프루닝·차분합산 정합. 요금피크 반복 스캔은 권고 — 본 사이클은 **응답 포함 + 독립 재계산(캐싱 미도입)** 채택, 캐싱은 섹션2·5 횡단 최적화로 향후 별도 검토 (가정 명시, `§2` 추측 인프라 미도입 정합).

### 안건 4: 시스템 전역 PWQ 합산 도메인 규칙 + 4영역 점검
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약 (블로커 1 / 권고 1 / 참고 1):
  - **Q1 (블로커)**: PWQ 는 PWI 와 물리 구조가 다름 — 메인 인입 ELCMTR 적산미터 + 펌프별 서브미터가 모두 활성 PWQ 태그면 합산 시 **이중계상**. PWI(동시각 값 합)는 이 문제 없음. `ot-integration.md §3` 은 집계 방법만 정의·합산 대상 태그는 미정의. **구현이 임의 확정 금지 — 사용자 결정 필요**(`coding-discipline.md §1`).
  - **Q2 4영역**: 알람/인터록/운전모드/이력 **전부 비해당** — 읽기 전용 집계, 쓰기 경로 없음. 근거 구체적, 차단 해제 조건 충족.
  - **Q3 경계**: `base=date_trunc('hour',now)` 분할(발생 `[base-12h,base)` 완전 12버킷 / 예측 `[base,base+12h)` 현재 시 부분) 도메인 정합(섹션4 동형). 예측 현재 시 버킷 과소 가능 — 가정 명기.
  - **Q4 단위**: 발생/예측(kWh) vs 요금/목표(kW) 단위 혼재는 집계 조회 목적상 문제없음. 백엔드 단위 변환 금지(물리 부정확), 프론트 이중축 — 가정 명기.
- **블로커 해소 (사용자 결정)**: AskUserQuestion 으로 "**전체 활성 PWQ 태그 합산**" 재확정. dev DB 현황(메인미터 0개·펌프 8개)상 현 시점 이중계상 없음. 향후 ELCMTR 메인미터 추가 시 이중계상 위험 → 그 시점 별도 사이클 재검토(가정 명시).
- **결론**: 전역 PWQ 합산(전체 활성 태그) 확정 + 이중계상 잠재 위험 가정 명기. 4영역 전부 비해당. 경계 `base` 분할 + 단위 변환 금지 가정 명기.

---

## 표준 사전 카탈로그

> 본 사이클은 **신규 DB 테이블·컬럼 없음**(read-only 조회, 3종 테이블 재사용). 3층 어휘 신규 등록 0건. (안건 1 결론)

### 신규 표준 단어
없음

### 신규 표준 데이터 도메인
없음

### 신규 표준 용어
없음

---

## 신규 엔티티/DB 컬럼

없음 — 기존 `predc_1m_h`(`TagPrediction`)·`rawdata_1m_h`(`RawData`)·`opt_peak_target_p`(`PeakTarget`) 재사용. 신규 테이블·컬럼·인덱스·DDL 변경 없음.

신규 자산은 모두 `api` 모듈 코드 (`common` 엔티티·DB 변경 0건):
- `PeakEnergyTrendDto` (응답, 중첩 `Point` — 필드 `baseDtm`+`elcegVal`) — `measuredPoints`/`predictedPoints` 2 시계열 + `unit`/`targetPeakElpwr`/`billingPeakElpwr` 스칼라
- `PeakEnergyTrendService` (신규 서비스, `@Transactional(readOnly = true)`)
- `PeakPowerAnalysisController` 신규 GET 메서드 (기존 컨트롤러 확장)
- **재사용**: `PumpEnergyPredcRepository`(예측 PWQ, Javadoc 스코프 갱신) · `RawDataRepository.findEnergyDeltaBuckets`(실측 PWQ) · `RawDataRepository.findMaxMinuteSumElpwr`(요금피크) · `PeakTargetService.getPeakTarget`(목표피크) · `TagRepository.findByTagSeCdAndUseYn`(태그 조회)

---

## 기존 사전·패턴과의 충돌

블로커 1건 (사용자 결정으로 해소). 검토된 충돌과 해소:
- **PWQ 전역 합산 이중계상** (domain-expert 블로커) → 사용자 "전체 활성 PWQ 합산" 재확정 + dev DB 메인미터 0개 확인 + 잠재 위험 가정 명기로 해소.
- 필드명 `predc` 접두 → `predc_dtm` 동의어 충돌 → `predictedPoints` 풀네임·`baseDtm`/`elcegVal` 재사용으로 회피 (안건 1).
- 요금피크 12개월 반복 스캔 (dba 권고) → 응답 포함+독립 재계산, 캐싱 향후 횡단 검토 (안건 3).

---

## PLAN 으로 전달할 결정 사항

### 데이터 흐름 (변경 없음, 조회 경로만)
```
now = LocalDateTime.now();  base = now.truncatedTo(ChronoUnit.HOURS)
pwqSrl = tagRepository.findByTagSeCdAndUseYn(PWQ, Y)  # 전역 활성 PWQ 태그
pwiSrl = tagRepository.findByTagSeCdAndUseYn(PWI, Y)  # 요금피크용 전역 PWI 태그

발생 : rawDataRepository.findEnergyDeltaBuckets(pwqSrl, base-12h, base, "hour")
        → baseDtm별 태그 합산(음수차분 제외) → measuredPoints (12버킷)
예측 : pumpEnergyPredcRepository.findEnergyDeltaBuckets(pwqSrl, base, base+12h)
        → baseDtm별 태그 합산(음수차분 제외) → predictedPoints (12버킷, 현재 시 부분)
목표 : peakTargetService.getPeakTarget().getTargetPeakElpwr()   # kW
요금 : rawDataRepository.findMaxMinuteSumElpwr(pwiSrl, now-12mo, now) ?? ZERO  # kW
```

### 적용 패턴 (회의 결론)
- **컨트롤러**: 기존 `PeakPowerAnalysisController`(`@Tag "14. 전력피크 분석"`) GET 메서드 추가. 경로 `GET /api/opt/peak-power-analysis/energy-trend` (파라미터 없음 — 시스템 전역).
- **Service**: 신규 `PeakEnergyTrendService`. 공개 메서드는 태그 조회 + 4 조회 + 버킷 합산 위임. `aggregateByBucket` private 헬퍼 2개(실측 `RawDataBucketDto` / 예측 `PredcEnergyBucketDto`)로 분리, 음수 차분(적산 리셋) 제외 + WARN 로그. 50줄/3단 충족.
- **Repository**: 예측 PWQ = 섹션4 `PumpEnergyPredcRepository` 재사용(Javadoc 스코프 이력 갱신). 실측 PWQ·요금피크 = raw 공용 메서드 재사용. 신규 Repository **0건**.
- **DTO 필드**: `unit`(="kWh"), `targetPeakElpwr`/`billingPeakElpwr`(BigDecimal, kW), `measuredPoints`/`predictedPoints`(`List<Point>`). Point: `baseDtm`+`elcegVal`. `BaseAuditResponseDto` 미상속. `List<Point>` 필드에 `@ArraySchema(schema=@Schema(implementation=Point.class))` 명기 의무.
- **스칼라**: 응답 포함 + 독립 재계산. 캐싱 미도입(향후 섹션2·5 횡단 최적화 검토).
- **단위**: 백엔드 변환 금지(kWh/kW 원단위 응답), 프론트 이중축.
- **DDL/사전**: 변경 0건.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 발생/예측 전력량 = 적산전력량 PWQ 기반 kWh (PWI 아님) | 결정 | plan 단계 사용자 확정. domain-expert 타당 |
| 전역 PWQ 합산 대상 = 전체 활성 PWQ 태그(`tag_se_cd='PWQ' AND use_yn='Y'`). 현재 펌프 8개·메인미터 0개라 이중계상 없음. **향후 ELCMTR 메인 적산미터 추가 시 펌프 서브미터와 이중계상 발생 가능 → 그 시점 별도 사이클 재검토** | 결정 | analyze AskUserQuestion 재확정. domain-expert 블로커 해소 |
| 윈도우 = `base=date_trunc('hour',now)` 기준, 발생 `[base-12h, base)` 완전 12버킷 / 예측 `[base, base+12h)`. 현재 시 버킷은 예측에 포함(부분 집계 — 미래 분 미산정 시 과소 가능) | 결정 | domain-expert·dba Q 위임. 섹션4 동형. Swagger 에 "현재 시 버킷 예측 부분집계" 명기 |
| 빈 버킷(데이터 0)은 응답 생략 (24슬롯 null 고정 채움 아님). 태그/데이터 0건은 빈 시계열(200) | 결정 | 섹션4 동형 |
| 요금/목표 스칼라(kW)는 섹션5 응답 포함 + 독립 재계산. 12개월 스캔 캐싱은 미도입(향후 횡단 최적화) | 결정 | backend-engineer (a) 채택, dba 권고는 향후 검토 |
| kWh(발생·예측) / kW(요금·목표) 단위 혼재 — 백엔드 단위 변환 금지, 프론트 이중축 처리 | 결정 | domain-expert·dba 명시 의무 |
| 요금피크 = 전역 PWI 분별 SUM의 12개월 MAX (섹션2 동형) | 결정 | 섹션2 `billingPeak` 동형 |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `PeakEnergyTrendServiceTest` 신규 케이스 GREEN | 윈도우 분할(발생 `[base-12h,base)`·예측 `[base,base+12h)` 경계 인자) / 버킷별 태그 합산 + 음수차분 제외 / PWQ·PWI 태그 0개 빈 시계열+ZERO 스칼라 / 예측·실측 0행 빈 시계열 / 목표 시드 부재 PEAK_TARGET_NOT_INITIALIZED 전파 |
| 버킷 합산 정확성 | 2태그 서로 다른 적산 baseline mock → `SUM(태그별 MAX-MIN)` 일치 단위 테스트 |
| 신규 GET 엔드포인트 응답 | dev DB 기동 상태 `GET /api/opt/peak-power-analysis/energy-trend` → measuredPoints(최대 12) + predictedPoints(최대 12) + targetPeakElpwr/billingPeakElpwr 확인 |
| 빌드·회귀 통과 | `./gradlew.bat :api:test` BUILD SUCCESSFUL, 기존 테스트 회귀 0건 |

---

## 도메인 룰 4영역 점검

> 본 작업은 **읽기 전용 조회 API** 이며 "## 신규 엔티티/DB 컬럼" 없음. 4영역 전부 비해당 + 구체 사유 명기(차단 해제 조건 충족). (안건 4 `wtp-domain-expert` 판정)

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | PWQ 차분·요금/목표 피크 조회는 알람 임계값·전이·복귀 조건에 접촉 없음. 알람 트리거·쓰기 경로 부재 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 발행 없는 읽기 전용 조회. 선행조건 검사·기동 차단·복구 재검사 의무 무관, `⚠️ 절대 금지` 규정 접촉 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` READ/WRITE 없음. SCADA 5분 초과 강제 전환 트리거 무관 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 읽기 전용 — `ai_drvn_mod_h`·`pump_ctrl_h` 이력 쓰기 경로 없음 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 어휘 사전·룰 갱신 **0건** (안건 1 `wtp-glossary-manager` 확정 — 신규 DB 컬럼 없음, 기존 표준 단어 조합 재사용). 갱신 대상 룰 파일 없음.

- [x] 룰 갱신 없음 — `swtp/.claude/rules/dict/*` 및 `.claude/rules/dict/standard-terms.md` 변경 0건 (glossary-manager 확정)

---

## 산출물
- [계획안](../../../plan/20260605/전력피크분석-5번섹션/PLAN1.md)
