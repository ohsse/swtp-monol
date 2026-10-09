---
status: approved
created: 2026-05-13
updated: 2026-05-13
---
# 송수펌프제어분석 — 3번섹션 (시설 실시간 상태 표출) 도메인 분석

## 작업 배경

이미지 `swtp/backend/image/송수펌프제어분석.png` 의 **3번 섹션** 구현. 1번 섹션 (commit `58cbf0c feat: 시설 목록 조회에 hasPump 필터 추가 (송수펌프제어 1번 섹션)`) 에서 활성화시킨 시설(`facility_m`) 에 등록된 유량계·펌프 목록의 실시간 상태를 표출하는 섹션이다.

### 요구사항 (사용자 명시)
- 유량계(`Flwmtr` 자식 instrument) 에 매핑된 유량태그(FRI) + 압력태그(PRI) 의 최신 계측값을 **유량·관압** 으로 표출
- 펌프(`Pump` 자식 instrument) 에 매핑된 펌프가동상태 태그(OPS) 의 최신값으로 **On/Off** 표출
- 펌프의 자동/반자동 (`oprtngType` = AUTO_CAPABLE / SEMI_AUTO_CAPABLE) 은 **정적 펌프 제원** 으로 함께 노출 — 사용자 명시 "AI 모드와는 전혀 상관 없음"

### 외부 산출물
- `swtp/backend/image/송수펌프제어분석.png` (HMI 화면 디자인)

### 사용자 의논 결정 (2026-05-13, plan 모드)
사용자 원안 ("`tag_m` 에 최근 계측시간 컬럼 추가 + 수집부 갱신") 과 3개 대안을 비교한 결과:

**방안 D 채택** — `rawdata_1m_h` DISTINCT ON 즉석 조회 (`tag_m` 스키마 변경 0건, 수집부 코드 변경 0건).

근거 (plan 파일 `~\.claude\plans\lexical-weaving-metcalfe.md` 참조):
1. SCADA 수집 어댑터 백지화 상태 (pump+AI 백지화 사이클 1, 2026-05-12) — A/B/C 안은 수집부 재설계 사이클 2 에 종속
2. SSOT 정합 — `rawdata_1m_h` 가 계측값 1차 정의
3. 마이그레이션 0건 — `tag_m` 안정성 유지
4. 성능 종합 우위 — 매분 N 마스터 UPDATE 부담 회피
5. 측정 후 단방향 확장 가능 (D → B/C)

결측 표시 정책 (`acq_dtm` 오래된 경우) 은 **추후 사이클에서 결정** 으로 사용자 명시.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 응답 DTO 구조

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **옵션 A 채택** — `FacilityStateDto { facilityId, facilityNm, List<FlwmtrStateDto>, List<PumpStateDto> }` 단일 DTO. 기존 마스터 DTO (`PumpDto`·`FlwmtrDto`) 재사용 금지 (감사 메타 4컬럼은 실시간 상태 응답에 무의미 → 관심사 혼재). 3단 상속 패턴 (옵션 B) 은 `@Inheritance(JOINED)` 마스터 다형성 한정 — `coding-discipline.md §2.1` DTO 상속 깊이 3단 임계 도달 + 적용 근거 부재.
- **결론**: 옵션 A 채택. `FlwmtrStateDto { instrumentId, instrumentNm, rawVal, corrVal, acqDtm, qualityCd }` (유량태그/압력태그 각각 노출 — PLAN 단계에서 단일 DTO vs 2개 분리 결정 — 가정 섹션), `PumpStateDto { instrumentId, instrumentNm, oprtngType, isRunning, acqDtm, qualityCd }`.

### 안건 2: `Pump.oprtngType` 노출 필드명

- 호출 에이전트: `wtp-backend-engineer` + `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `oprtngType` (enum 직접). `userRole`·`equipType` 선례 정합. `@Schema(description = "펌프 조작유형", implementation = PumpOprtngType.class)` 명시 의무.
  - **wtp-glossary-manager**: `oprtngTypeCd` 권고 — DB 컬럼 `oprtng_type_cd` camelCase 1:1 정렬 + `pump` 비즈니스 도메인 약어 폐기로 prefix 불가.
- 이견 발견 — 오케스트레이터 선례 재확인 (Grep 결과):
  - `tag_se_cd` → `tagSeCd` / `io_cd` → `ioCd` / `quality_cd` → `qualityCd` (`Cd` 보존)
  - `equip_type_cd` → `equipType` / `oprtng_type_cd` → `oprtngType` (`Cd` 생략 — 필드 타입이 `*Type` 으로 끝나는 enum)
- **결론**: **`oprtngType` 채택** — `Pump` 엔티티 필드명 (`com.mo.swtp.instrument.domain.Pump.oprtngType`) 과 동일. `equipType` 선례 정합. `*Type` enum 의 경우 `Cd` 중복 의미라 생략하는 패턴이 본 프로젝트 관례. wtp-domain-expert 의 추가 권고 — `@Schema(description = "펌프 물리 조작 가능 유형 (AI 운전 모드와 무관)", implementation = PumpOprtngType.class)` 명시.

### 안건 3: `BaseAuditResponseDto` 미적용 판단

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 미적용 타당. `api-patterns.md §BaseAuditResponseDto 패턴 §적용 범위` 의 "실시간 통지 응답 DTO — 적용 외" 행 정렬. `tag_m.rgstr_dtm`·`instrument_m.rgstr_dtm` 등 마스터 등록 일시는 실시간 상태 표출에 무의미. 핵심 데이터는 `rawdata_1m_h.acq_dtm` (측정 수집 일시) 으로 별도 노출.
- **결론**: 미적용. `FacilityStateDto`·`FlwmtrStateDto`·`PumpStateDto` 모두 `BaseAuditResponseDto` 상속 안 함.

### 안건 4: 컨트롤러 위치

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `FacilityController` 에 엔드포인트 추가 권고. 1번섹션 (`hasPump` 필터) 선례 정합. `FacilityStateController` 신설은 `coding-discipline.md §2` "일회성 코드를 위해 추상화 계층을 만들지 않는다" 위반. Service 측은 `FacilityStateService` 신규 분리 권고 — DISTINCT ON 조회는 50줄 초과 가능성 있어 `RawDataCustomRepository` 위임 (`coding-discipline.md §2.5` 면책 + `query-tuning.md §2` 인용 근거 주석 의무).
- **결론**: `FacilityController` 에 `GET /api/facilities/{facilityId}/state` 추가. Service 는 `FacilityStateService` 신규 분리. DISTINCT ON 쿼리는 `RawDataCustomRepository` 위임.

### 안건 5: DISTINCT ON 인덱스 매칭 + EXPLAIN ANALYZE 시뮬레이션

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 인덱스가 DISTINCT ON 의 `ORDER BY tag_srl_no, acq_dtm DESC` 와 정확히 정합. **PostgreSQL 은 MySQL 의 "Loose Index Scan" 미구현** — `tag_srl_no = ANY(?)` 다중 값은 Bitmap Index Scan 또는 Index Scan 순차 실행. 기대 출력: `Unique → Index Scan Backward using idx_rawdata_1m_h_tag_time`. `Sort` 노드가 나타나면 인덱스 방향 불일치 신호 (즉시 재검토).
- **결론**: 인덱스 활용 정합. EXPLAIN ANALYZE 검증 항목 — (1) `Index Scan Backward using idx_rawdata_1m_h_tag_time`, (2) `Unique` 노드 존재, (3) `Sort` 노드 부재. 본 항목은 PLAN 의 성공 기준으로 변환.

### 안건 6: 파티션 프루닝 작동 조건

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: `NOW()` 는 stable 함수 → **execution-time partition pruning** 적용 (planning-time 불가). 월 13개 파티션 규모에서 실용적 차이 없음. EXPLAIN ANALYZE 에서 `Append` 하위에 현재 월 파티션 1~2개만 표시되면 정상. `db/partitioning-and-retention.md §1` "파티션 키 조건 반드시 포함" 충족.
- **결론**: `WHERE acq_dtm >= NOW() - INTERVAL '1 hour'` 유지. 검증 항목 — `Append` 노드 하위 파티션 1~2개 확인.

### 안건 7: Querydsl 에서 DISTINCT ON 구현 방법

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: **(b) `EntityManager.createNativeQuery` + `*CustomRepositoryImpl` 패턴 권장**. 본 프로젝트 `api-patterns.md §Repository 패턴` 정합. PostgreSQL 배열 파라미터 (`= ANY(?)`) 는 `String[]` 또는 `Array.createArrayOf` 로 명시 제어. (a) `@Query(nativeQuery = true)` 는 배열 바인딩 방언 의존, (c) `JPASQLQuery` 는 미사용 의존성 도입 부담, (d) ROW_NUMBER Window function 은 실행 계획 복잡 + 인덱스 활용도 저하 — 차선.
- **결론**: `RawDataCustomRepositoryImpl.findLatestByTagSrlNos(List<String> tagSrlNos)` 신규 메서드. `createNativeQuery` + `Object[]` 매핑 + `@Transactional(readOnly = true)` + `coding-discipline.md §2.5` 면책 (`query-tuning.md §2` 인용 근거) 주석 명시.

### 안건 8: SLA + p6spy 임계

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: **SLA 200ms / p6spy `executionThreshold=100`** 권고. 정량 평가:
    - N=100 태그 × 60행/1시간 = 6,000행 인덱스 접근 → 50~150ms (cold) / 10~30ms (shared_buffers hit)
    - N=500 태그 × 60행 = 30,000행 → 150~400ms → **SLA 미달 가능성 존재**
- **결론**: SLA 200ms 가정 (PLAN 단계 성공 기준 변환). N=500 케이스는 가정 섹션에 미해결로 명시 → 운영 측정 후 캐시 도입 별도 사이클.

### 안건 9: 도메인 룰 4영역 점검 (블로커 2건)

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert (블로커 1)**: 알람 4영역을 "비해당" 으로 단정하면 블로커. `quality_cd` UNCERTAIN/BAD 데이터를 응답 DTO 에 노출하는 행위가 운전원 오인 방지 의무 (`ot-integration.md §3` "운전원 오인 위험") 를 수반. 본 표출 API 자체가 알람을 발생시키지 않더라도 4영역 표 "알람 4단계" 행에 명기 의무.
  - **wtp-domain-expert (블로커 2)**: OPS 즉시 BAD 격상 정책 (`ot-integration.md §3` "Hold Last Value 미적용") 이 5분 미만 결측에도 적용. 본 사이클이 OPS 결측 처리 방식 미결정 상태를 가정 섹션에 반드시 명시.
- **결론**: 두 블로커 모두 본 ANALYZE 의 "## 가정 및 미해결 질문" + "## 도메인 룰 4영역 점검" 섹션에 반영하여 해소.

### 안건 10: `quality_cd` 응답 DTO 노출 정책

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: **enum name 문자열** (GOOD/BAD/UNCERTAIN) 직렬화 권고. 코드 매핑 표 (0/1/2) 는 frontend 가 한국어 레이블 매핑. `exception-patterns.md` 의 "코드만 전달, 표기는 frontend 책임" 패턴 정렬. `quality_cd` 노출은 운전원 오인 방지 의무 직결 — **노출 의무**.
- **결론**: `QualityCode` enum name 직렬화. `@Schema(description = "SCADA 품질 코드 — GOOD/BAD/UNCERTAIN", implementation = QualityCode.class)` 명시.

### 안건 11: 어휘 — `acq` 단어 + `acq_dtm` 용어 신규 등록

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `acq` (수집/취득) 표준 단어가 **ROOT `standard-words.md` 미등록** 상태. `rawdata_1m_h.acq_dtm` 파티션 키로 이미 사용 중인데 어휘 사전에 근거가 없는 공백 상태 — 본 사이클이 응답 DTO `acqDtm` 변수명을 사용하므로 등록 의무 발생. `rcv`(수신, 2026-04-25 등록) 와 의미 분리 성립 (`acq`=SCADA 데이터 수집 시점, `rcv`=프로토콜 수신 이벤트 시점). `acq_dtm` 표준 용어도 `standard-terms.md` 미등록 → 동반 등록 필요.
  - `state` 등록 보류 (DTO 클래스명 접미사만 사용 — 표준 단어 등록 의무 외).
  - `latest`/`last` 등록 불필요 (D안으로 `last_*` 컬럼 도입 없음).
- **결론**: `acq` 표준 단어 + `acq_dtm` 표준 용어 신규 등록. 본 사이클 룰 갱신 지시서에 포함.

---

## 표준 사전 카탈로그

### 신규 표준 단어
(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `acq` | 수집(취득) | 신규 | `standard-words.md` 미등록 공백 해소. `rawdata_1m_h.acq_dtm` 파티션 키 이미 사용 중. `rcv`(수신)과 의미 분리 — `acq`=SCADA 데이터 수집·취득 시점, `rcv`=프로토콜 수신 이벤트 시점 |

### 신규 표준 데이터 도메인

없음 (본 사이클 신규 데이터 도메인 발생 없음 — 모든 컬럼 기존 `DOM_DTM`·`DOM_QTY_15_4`·`DOM_CODE_20` 재사용).

### 신규 표준 용어
(단어 + 데이터 도메인 → DB 컬럼명. 1차 정의: `.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `acq_dtm` | `acq`(신규) + `dtm` | `DOM_DTM` | 신규 | `rawdata_1m_h` 파티션 키로 기 사용 중이나 표준 용어 사전 미등록. 본 사이클 응답 DTO `acqDtm` 변수명 사용 전제로 등록 |

---

## 신규 엔티티/DB 컬럼

**0건** (방안 D 채택 — DB 변경 없음).

신규 응답 DTO 클래스만 추가:
- `FacilityStateDto` (`com.mo.swtp.facility.dto`) — 시설 단위 상태 (자식 상태 목록 보유)
- `FlwmtrStateDto` (`com.mo.swtp.facility.dto`) — 유량계 상태 (유량태그·압력태그 최신값)
- `PumpStateDto` (`com.mo.swtp.facility.dto`) — 펌프 상태 (가동상태 최신값 + 정적 `oprtngType`)

> DTO 패키지 위치는 PLAN 단계 결정 — `facility/dto` vs `facility/state/dto` 분리 검토 (가정 섹션).

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 (회의 결론과 일치) |
|---------|----------------------|
| `acq` 표준 단어 ROOT 사전 미등록 공백 — `rawdata_1m_h.acq_dtm` 컬럼이 어휘 근거 없이 사용 중 | 본 사이클 룰 갱신 지시서로 `acq` 단어 + `acq_dtm` 용어 동시 등록 (안건 11) |
| `Pump.oprtngType` DTO 노출 필드명 이견 (`oprtngType` vs `oprtngTypeCd`) | 엔티티 필드명 + `equipType` 선례 정렬로 `oprtngType` 채택 (안건 2) |
| 알람 4영역 "비해당" 단정 시 `quality_cd` 표출의 운전원 오인 방지 의무 누락 | 4영역 표에서 알람 4단계 "해당" 으로 명기 + 가정 섹션에 OPS 결측 처리 미결 명시 (안건 9) |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- 신규 엔티티: 0건
- 신규 응답 DTO 3건: `FacilityStateDto` · `FlwmtrStateDto` · `PumpStateDto`
- 기존 응답 DTO 재사용 금지 — 마스터 DTO (`PumpDto`·`FlwmtrDto`) 의 감사 메타가 실시간 상태 응답에 무의미

### DB 설계 변경 초안
**0건** — `tag_m` / `rawdata_1m_h` / `instrument_m` 스키마 변경 없음.

### 적용할 패턴
- **DISTINCT ON 즉석 조회**: `RawDataCustomRepositoryImpl.findLatestByTagSrlNos(List<String>)` 신규 메서드
- 구현: `EntityManager.createNativeQuery` + `Object[]` 매핑 + `@Transactional(readOnly = true)`
- `coding-discipline.md §2.5` 면책 적용 — `query-tuning.md §2` 인용 근거 주석 명시 (단일 메서드 50줄 자연 초과 시)
- 컨트롤러: `FacilityController` 에 `GET /api/facilities/{facilityId}/state` 추가
- 서비스: `FacilityStateService` 신규 분리 (계측기 → 태그 매핑 + 최신값 결합 책임)
- `BaseAuditResponseDto` 미적용 (실시간 통지 DTO 분류)
- `oprtngType` enum 노출 + `@Schema(description = "펌프 물리 조작 가능 유형 (AI 운전 모드와 무관)", implementation = PumpOprtngType.class)` 명시
- `qualityCd` enum name 직렬화 + `@Schema(description = "SCADA 품질 코드", implementation = QualityCode.class)` 명시
- 인덱스 활용: 기존 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 그대로

---

## 가정 및 미해결 질문

> 본 ANALYZE 의 가정·미해결 질문 (ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용).

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| OPS (`quality_cd = BAD`) 결측 시 표출 방식 미결정 — Hold Last Value 미적용 정책 (`ot-integration.md §3`) 직결, 운전원 오인 위험 동급 | 미해결 | 사용자 명시: 추후 사이클에서 결정. 본 사이클은 raw 결과 그대로 노출 (frontend 가 `qualityCd` 보고 표시 분기). PLAN 단계 결정 또는 별도 ANALYZE 진입 |
| `acq_dtm` 5분 초과 결측 표시 정책 (5분 초과 시 "—" vs Hold Last Value) | 미해결 | 사용자 명시 (2026-05-13, plan 모드 의논): 추후 사이클에서 결정. 본 사이클은 `acq_dtm` 을 응답 DTO 에 포함하여 frontend 가 자체 정책 적용 가능하게 함 |
| 활성 태그 N=500 시 SLA 200ms 미달 가능성 | 미해결 | wtp-dba-reviewer 정량 평가 (150~400ms). 본 사이클 D안 채택 + PLAN 단계 N=100 fixture 측정 → 운영 측정 후 캐시 도입 여부 별도 사이클 결정 |
| `FlwmtrStateDto` 가 유량태그·압력태그를 단일 DTO 에 포함할지, 별도 필드로 분리할지 | 가정 | 가정: 단일 DTO 에 `flwrtRawVal`·`prsrRawVal` 분리 필드 보유 (FRI/PRI 측정 유형이 다르므로 의미 분리). PLAN 단계 확정 |
| `FacilityStateDto`·`FlwmtrStateDto`·`PumpStateDto` 패키지 위치 — `facility/dto` 단일 vs `facility/state/dto` 분리 | 가정 | 가정: `facility/dto` 단일 패키지 (1번섹션 선례 정합). PLAN 단계 확정 |
| 활성 시설의 식별 방법 — `facility_id` 단건 vs 사용자 선택 시설 목록 | 가정 | 가정: `facility_id` 단건 (PathVariable). 1번섹션이 `hasPump` 필터로 시설 목록을 제공하면 frontend 가 사용자 선택 후 3번섹션 API 호출. PLAN 단계 확정 |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| DISTINCT ON 쿼리가 인덱스 활용 + 파티션 프루닝 정상 작동 | 로컬 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS) SELECT DISTINCT ON (tag_srl_no) ... WHERE acq_dtm >= NOW() - INTERVAL '1 hour'` 실행 결과 (1) `Index Scan Backward using idx_rawdata_1m_h_tag_time` 출력 (2) `Append` 하위 파티션 1~2개 (3) `Sort` 노드 부재 모두 확인 |
| N=100 활성 태그 fixture 기준 응답 시간 200ms 이내 | `./gradlew.bat :api:test` 의 통합 테스트 RawData fixture 100건 INSERT 후 `FacilityStateService.findFacilityState(facilityId)` 호출 응답 시간 측정 |
| `FacilityStateService` 가 `quality_cd` 가 BAD/UNCERTAIN 인 태그의 최신값도 그대로 반환 (frontend 가 표시 분기) | 단위 테스트 — `RawDataCustomRepository.findLatestByTagSrlNos` mock 으로 `QualityCode.BAD` 행 주입 시 응답 DTO `qualityCd = BAD` 노출 확인 |
| 응답 DTO 에 `oprtngType` 노출 + `@Schema(description=..., implementation=PumpOprtngType.class)` 명시 | Swagger UI (`http://localhost:8080/swagger-ui.html`) 에서 `PumpStateDto` 스키마에 `oprtngType` 의 `enum: [AUTO_CAPABLE, SEMI_AUTO_CAPABLE]` 노출 확인 |
| 신규 엔드포인트 `GET /api/facilities/{facilityId}/state` 가 1번섹션 hasPump 필터와 일관된 응답 구조 | Postman 또는 curl 로 활성 시설 ID 호출 시 `CommonResponseDto<FacilityStateDto>` 응답 + `code: "SUCCESS"` 확인 |

---

## 도메인 룰 4영역 점검

> 도메인 4영역 (알람 4단계 / 인터록 / AI 운전 모드 / 이력 기록) 해당 여부. 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../../../.claude/rules/ot-integration.md).

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | **해당** | 본 표출 API 자체는 알람을 발생시키지 않으나, 응답 DTO 에 `quality_cd` (GOOD/BAD/UNCERTAIN) 를 노출하는 행위가 `ot-integration.md §3` 의 "운전원 오인 위험" 방지 의무를 수반. UNCERTAIN/BAD 값을 운전원이 정확히 인지하도록 frontend 표시 분기 (한국어 레이블 매핑) 의무가 발생 → 본 사이클은 enum name 직렬화로 정보 전달 책임 완수 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | **비해당** | 본 사이클은 표출 전용 — 제어 명령 발행 없음. `pump_interlock_p` 평가 / 기동 차단 / 복구 후 재검사 모두 무관 |
| AI 운전 모드 (`ot-integration.md §5`) | **비해당** | 사용자 명시 — `Pump.oprtngType` (AUTO_CAPABLE/SEMI_AUTO_CAPABLE) 은 정적 펌프 제원이며 AI 모드 (`ai_mode_cd`, 백지화 상태) 와 무관. 본 사이클은 사용자 의도/시스템 상태 변경 없음. `@Schema(description=)` 에 "AI 운전 모드와 무관" 명시로 설계 의도 보존 |
| 이력 기록 의무 (`ot-integration.md §5`) | **비해당** | 본 사이클은 표출 전용 — 모드 전환 이력 (`ai_drvn_mod_h`, 백지화) / 제어 로그 (`pump_ctrl_h`, 백지화) 생성 없음. `rawdata_1m_h` 의 SELECT 만 수행 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `acq` (수집/취득, acquisition) 표준 단어 신규 등록. 기본 데이터 도메인 미지정(조합), 등록일 2026-05-13, `rcv`(수신) 와 의미 분리 비고 명시 — 표 행 추가 (2026-05-13 완료)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `acq_dtm` 표준 용어 신규 등록 (`acq` + `dtm`, `DOM_DTM`, `rawdata_1m_h` 파티션 키 사용처 + 본 사이클 `acqDtm` DTO 변수명 사용처 기재) — 표 행 추가 (2026-05-13 완료)

> 본 사이클 신규 데이터 도메인 0건 — `standard-data-domains.md` 갱신 의무 없음. 신규 비즈니스 도메인 약어 0건 — `domain-abbreviations.md` 갱신 의무 없음. DB 컬럼 신규 0건이라 backend 표준 용어는 `acq_dtm` 1건 만 (기존 사용 중 컬럼의 사전 누락 해소).

## 산출물
- [계획안](../../../plan/20260513/송수펌프제어분석-3번섹션/PLAN1.md) (생성 예정)
