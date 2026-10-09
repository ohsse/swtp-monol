---
status: approved
created: 2026-05-18
updated: 2026-05-18
---
# 송수펌프제어분석 — 7번섹션 도메인 분석 (ANALYZE2 — 4영역 테이블 관점 재검증)

## 작업 배경

- 본 ANALYZE2 는 **Fix Cycle 아님** — REVIEW 블로커가 아닌 **PLAN §5b 자율 차단 게이트 트리거**에 의한 재진입이다.
- 트리거: [ANALYZE1](ANALYZE1.md) `status: approved` 후 `/dev:plan` 진입 시 §5b "도메인 룰 4영역 점검" 자율 차단 발동 — *4영역 전부 "비해당" + `## 신규 엔티티/DB 컬럼` 섹션이 "없음" 아님(신규 테이블 `predc_1m_h` 1건)* → 차단 해제 2조건 중 (2) 미충족.
- 사용자 결정 (2026-05-18): **ANALYZE2 재진입 (wtp-domain-expert 재검증)** — 거짓 "해당" 표기(역방향 형식적 충족) 배제, 게이트 우회 배제. 신규 테이블 `predc_1m_h` **자체**(조회 쿼리가 아닌 영속 예측 저장소)가 4영역과 무접촉인지 테이블 관점 정밀 재검증.
- 직전 ANALYZE: [ANALYZE1](ANALYZE1.md) (status approved — 본 ANALYZE2 는 ANALYZE1 의 모든 결정·룰 갱신 6건을 **승계**하며 변경하지 않는다. 재검증 범위 = "## 도메인 룰 4영역 점검" 섹션 한정).

### 외부 산출물
- ANALYZE1 과 동일 (`swtp/backend/image/송수펌프제어분석.png`). 본 ANALYZE2 는 신규 외부 산출물 없음.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 신규 테이블 `predc_1m_h` 4영역 테이블 관점 재검증

- 호출 에이전트: `wtp-domain-expert` (Round 1)
- 재검증 질문: ANALYZE1 안건 5 판정은 **조회·표출** 관점이었음. 본 안건은 신규 영속 테이블 `predc_1m_h` **자체**(`_h` immutable, INSERT-only, `predc_id`·`predc_dtm`·`tag_srl_no`·`predc_val`, `quality_cd` 제외, 본 사이클 INSERT 경로 없음, `com.mo.swtp.opt`)가 4영역과 테이블 관점에서 접촉하는지 — 특히 "AI 예측 산출물 저장소" 성격상 AI 운전 모드·이력 기록 접촉 여부.
- Round 1 답변 요약 (wtp-domain-expert):
  - **알람 4단계 — 비해당 유지**: 예측값(`predc_val`) 저장만, 알람 임계 비교·`alarm_h` INSERT·UNCERTAIN 격상 경로 부재. `quality_cd` 제외로 §3 GOOD/BAD/UNCERTAIN 전이 접촉면이 스키마 수준 구조적 차단.
  - **인터록 선행조건 — 비해당 유지**: INSERT-only 적재 저장소, 본 사이클 INSERT 경로조차 없음(조회 전용). `sendControlCommand`·`pump_interlock_p`·기동 차단 접촉이 스키마 수준 불성립.
  - **AI 운전 모드 — 비해당 유지** (핵심): §5 전체가 pump+AI 백지화 사이클 1(2026-05-12) 이후 **보류 마커**. `ai_drvn_mod_p`·`ai_mode_cd`·`ai_drvn_mod_h` 가 현 코드베이스 부재 → 연결될 구체 도메인 객체 없음. "예측 산출물 = AI 자동 운전 입력" 일반 연관은 사이클 2 재설계에서 구체화될 의존 경로이며 본 사이클(표출 전용·INSERT 제외·§5 보류)에서 "해당" 격상 도메인 근거 없음.
  - **이력 기록 의무 — 비해당 유지**: §5 "이력 기록 의무"는 `ai_drvn_mod_h.transition_reason` 5종·`pump_ctrl_h` 제어 로그라는 특정 도메인 행위 감사 이력 지칭. `predc_1m_h` 는 AI 파이프라인 생산 예측 데이터 적재 시계열 측정값 저장소 — 도메인 행위 감사 기록 아님. `rawdata_1m_h` 가 §5 이력 의무 비해당인 것과 동일 논리(예측 적재 ≠ 도메인 행위 감사 이력).
  - **최종 결론 (wtp-domain-expert)**: 테이블 관점에서도 4영역 전부 비해당이 도메인적으로 정당. 4개 구조적 근거 동시 성립 — (1) §5 보류 마커, (2) `quality_cd` 제외, (3) INSERT 경로 부재(조회 전용), (4) 감사 이력 ≠ 예측 적재 의미 분리. 본 케이스는 §5b 게이트가 선제 차단하려는 "형식적 충족 은폐"(INSERT 경로·알람 트리거 은폐 후 표출 전용 주장)가 아니라 **"정당한 표출 전용 예측 저장소"** — §5 전체 보류 + 조회 전용 + 도메인 행위 무접촉이 복수 근거로 동시 입증된 경우. ANALYZE2 재검증으로 게이트 통과 근거 충분 확립.
- Round 2: 불요 (단일 에이전트 권위 영역, 이견 없음).
- **결론**: 신규 테이블 `predc_1m_h` 4영역 전부 **비해당** 유지가 테이블 관점에서도 도메인적으로 정당. 4개 구조적 근거로 §5b 게이트의 설계 의도(은폐 선제 차단)는 본 ANALYZE2 재검증으로 충족 — 게이트 통과 근거 확립(아래 §§5b 게이트 통과 근거).

---

## 표준 사전 카탈로그

> 본 ANALYZE2 는 **4영역 테이블 관점 재검증 한정** — 신규 표준 단어/데이터 도메인/표준 용어 **0건**. ANALYZE1 의 어휘 카탈로그·룰 갱신 6건(`val` 단어·`opt` 설명 확장·`predc_id`/`predc_dtm` 재등록·`predc_val` 신규·`predc_1m_h` 보존 3년)은 [ANALYZE1](ANALYZE1.md) 에서 이미 적용 완료(전부 `[x]`). 재기재하지 않는다.

### 신규 표준 단어
없음 (ANALYZE2 신규 0건).

### 신규 표준 데이터 도메인
없음 (ANALYZE2 신규 0건).

### 신규 표준 용어
없음 (ANALYZE2 신규 0건).

---

## 신규 엔티티/DB 컬럼

ANALYZE1 과 **동일·무변경** — 신규 테이블 `predc_1m_h` 1건 (스펙은 [ANALYZE1 §신규 엔티티/DB 컬럼](ANALYZE1.md) 참조). 본 ANALYZE2 는 스키마를 변경하지 않으며, 해당 테이블의 4영역 무접촉을 테이블 관점에서 재인증한 것이다.

---

## 기존 사전·패턴과의 충돌

없음. 본 ANALYZE2 는 재검증 산출물 — ANALYZE1 의 충돌 해소책(`com.mo.swtp.opt` 배치·`val` 등록·`quality_cd` 제외·예측 OPS `predc` 접두어·섹션7 전용 상수)은 모두 유효하며 변경하지 않는다.

---

## PLAN 으로 전달할 결정 사항

ANALYZE1 의 [§PLAN 으로 전달할 결정 사항](ANALYZE1.md) 전체를 **무변경 승계**한다 (도메인 모델 초안·DB 설계 변경 초안·적용할 패턴 — `predc_1m_h` 테이블, `latest_meas` CTE + CROSS JOIN LATERAL §2.5 면책, `FacilityPredictionService` 별도 클래스, `GET /api/facility/{facilityId}/prediction`, 3 예측 DTO, 섹션 3 자산 무수정).

본 ANALYZE2 추가 결정:
- **§5b 게이트 통과 근거 확립** — 신규 테이블 `predc_1m_h` 의 4영역 전부 비해당이 wtp-domain-expert 테이블 관점 재검증으로 4개 구조적 근거(§5 보류·`quality_cd` 제외·INSERT 경로 부재·감사 이력 의미 분리) 동시 입증. PLAN1 작성 시 본 ANALYZE2 를 ANALYZE 게이트 기준 문서로 사용한다.

---

## §5b 게이트 통과 근거

> 본 섹션은 PLAN §5b 자율 차단(메인 Claude 판단 — 자동 차단 훅 미신설, ROOT [`coding-discipline.md §7.1`](../../../../.claude/rules/coding-discipline.md) 보류 결정 유지)의 **명시적·문서화된 해소**다. 게이트 우회(silent bypass) 아님 — 사용자 결정 + 도메인 권위자 인증 + 게이트 설계 의도 충족의 3중 근거를 기록한다.

| 근거 | 내용 |
|------|------|
| 사용자 결정 | 2026-05-18 — 거짓 "해당" 표기·게이트 우회 배제, **ANALYZE2 재진입 + wtp-domain-expert 재검증** 명시 선택 |
| 도메인 권위자 인증 | `wtp-domain-expert` (5인 회의 도메인 정합성 권위) 가 신규 테이블 `predc_1m_h` **테이블 관점** 4영역 전부 비해당을 4개 구조적 근거로 명시 인증 (안건 1 결론) |
| 게이트 설계 의도 충족 | §5b (2) "신규 엔티티 없음" 은 "은폐된 도메인 위험 부재" 의 **프록시 휴리스틱**이다. 본 케이스는 도메인 권위자가 위험 부재를 4개 구조적 근거로 **직접 인증** — 프록시보다 강한 직접 입증. 게이트가 선제 차단하려는 "형식적 충족 은폐"(INSERT·알람 트리거 은폐 후 표출 전용 주장)에 본 케이스 비해당 |

> 결론: §5b 자율 차단의 목적(형식적 충족 은폐 선제 차단)은 본 ANALYZE2 의 투명한 재검증(사용자 결정 → wtp-domain-expert 재호출 → 4구조근거 인증 → 본 섹션 문서화)으로 충족되었다. PLAN1 은 본 ANALYZE2 를 게이트 기준 문서로 진행한다.

---

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE1 의 가정·미해결 9건을 승계(PLAN1 에서 결정 변환). 본 ANALYZE2 추가 항목:

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 신규 테이블 `predc_1m_h` 의 4영역 무접촉 (ANALYZE1 가정 #9 재검증) | 결정 | wtp-domain-expert 테이블 관점 재검증으로 4구조근거(§5 보류·`quality_cd` 제외·INSERT 경로 부재·감사 이력 의미 분리) 입증 — "비해당 단독 4건"이 형식적 충족 아님이 권위자 인증으로 확정 |
| ANALYZE1 가정·미해결 8건 (윈도우 크기·결측 응답·예측 신뢰도 컬럼·예측 OPS 필드명·엔티티 클래스명·BRIN·N=500 SLA·4영역 무접촉) | 승계 | [ANALYZE1 §가정 및 미해결 질문](ANALYZE1.md) 그대로 — PLAN1 에서 결정 변환 |
| 사이클 2(AI 추론 재설계) 도래 시 `predc_1m_h` 의 AI 운전 모드 결합 재평가 | 미해결 | 본 사이클 §5 보류로 비해당. 사이클 2 에서 `ai_drvn_mod_p` 재도입 시 예측→AI 자동 운전 입력 경로의 4영역 재평가 필요 (별도 ANALYZE) |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

ANALYZE1 의 [§성공 기준 후보](ANALYZE1.md) 5건을 **무변경 승계**한다 (근접매칭 쿼리 인덱스/파티션 프루닝·N=100 SLA 200ms·정확/근접/윈도우밖 케이스·예측 OPS `predcIsRunning` Swagger 노출·신규 엔드포인트 `CommonResponseDto`). 본 ANALYZE2 는 성공 기준을 신설하지 않는다.

---

## 도메인 룰 4영역 점검

> 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../../../.claude/rules/ot-integration.md). wtp-domain-expert **테이블 관점 재검증**(안건 1) — ANALYZE1 의 조회 관점 근거에 더해 신규 테이블 `predc_1m_h` 자체의 4영역 무접촉을 4개 구조적 근거로 재인증. "비해당 단독 4건"이 형식적 충족이 아님은 본 표 각 행 구체 사유 + §§5b 게이트 통과 근거 + wtp-domain-expert 명시 인증으로 확정.

| 영역 | 해당/비해당 | 근거 또는 영향 (테이블 관점 재검증) |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5·§3`) | **비해당** | `predc_1m_h` 는 `predc_val` 예측값 저장만 — 알람 임계 비교·`alarm_h` INSERT·UNCERTAIN 격상 경로 스키마 수준 부재. `quality_cd` 제외(ANALYZE1 안건 5·사용자 결정)로 §3 GOOD/BAD/UNCERTAIN 전이 접촉면이 **구조적으로 차단**(테이블에 품질 코드 컬럼 자체 없음) |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | **비해당** | INSERT-only 예측 적재 저장소이며 본 사이클 INSERT 경로조차 없음(조회 전용). `sendControlCommand`·`pump_interlock_p` 평가·기동 차단·복구 후 재검사 접촉이 테이블 스키마 수준 불성립 |
| AI 운전 모드 (`ot-integration.md §5`) | **비해당** | §5 전체가 pump+AI 백지화 사이클 1(2026-05-12) 이후 **보류 마커** — `ai_drvn_mod_p`·`ai_mode_cd`·`ai_drvn_mod_h` 현 코드베이스 부재로 연결될 구체 도메인 객체 없음. "예측 산출물 = AI 자동 운전 입력" 일반 연관은 사이클 2 재설계에서 구체화될 의존 경로(별도 ANALYZE) — 본 사이클(표출 전용·INSERT 제외·§5 보류) "해당" 격상 도메인 근거 없음. `Pump.oprtngType` 은 정적 물리 제원(운전 모드 무관) |
| 이력 기록 의무 (`ot-integration.md §5`) | **비해당** | §5 "이력 기록 의무"는 `ai_drvn_mod_h.transition_reason` 5종·`pump_ctrl_h` 제어 로그라는 **특정 도메인 행위 감사 이력** 지칭. `predc_1m_h` 는 `_h` 시계열이나 AI 파이프라인 생산 예측 데이터 적재 측정값 저장소 — 도메인 행위 감사 기록 아님(`rawdata_1m_h` 가 §5 이력 의무 비해당인 것과 동일 논리: 예측 적재 ≠ 도메인 행위 감사 이력) |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> 본 ANALYZE2 는 4영역 테이블 관점 재검증 한정 — **신규 룰 갱신 0건**. ANALYZE1 의 룰 갱신 6건(`swtp/.claude/rules/dict/standard-words.md` · `swtp/.claude/rules/dict/domain-abbreviations.md` · `swtp/backend/.claude/rules/dict/standard-terms.md` ×3 · `swtp/backend/.claude/rules/db/partitioning-and-retention.md`)은 [ANALYZE1 §룰 갱신 지시서](ANALYZE1.md) 에서 이미 전부 `[x]` 적용 완료. 본 ANALYZE2 의 룰 갱신 지시서 체크박스 0건 — PLAN §5b "모든 체크박스 `[x]`" 전제조건 공허 충족(vacuously satisfied).

(신규 룰 갱신 항목 없음)

## 산출물
- [계획안](../../../plan/20260518/송수펌프제어분석-7번섹션/PLAN1.md) (생성 예정 — 본 ANALYZE2 승인 후 `/dev:plan` 자동 전이)
