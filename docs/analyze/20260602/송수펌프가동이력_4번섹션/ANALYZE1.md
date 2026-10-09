---
status: approved
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 4번섹션 — 펌프 가동상태 막대(타임라인) 차트 API 도메인 분석

## 작업 배경

- **요청 요약**: "송수펌프 가동이력" 대시보드 **4번 섹션**(우하단) — 조회기간 펌프 가동상태 **읽기 전용 조회 API**.
  - 입력: 1번섹션 파라미터 중 **from~to 날짜만** 사용 (3번섹션과 달리 `inqUnit` 미사용).
  - 처리: 조회기간 동안 각 펌프의 **가동상태(OPS) 1분 시계열**을 조회 → **가동(ON) 구간을 세그먼트(startDtm~endDtm)로 런렝스 압축**.
  - 표출: 프론트(**Chart.js floating bar**, `indexAxis: 'y'` + `x축 type: 'time'`)가 펌프별 1행(track)에 가동 구간 막대를 표출. 같은 펌프가 조회기간 중 여러 번 껐다 켜지면 한 행에 다수 막대로 표시.
- **외부 산출물**:
  - `backend/image/송수펌프가동이력.png` (대시보드 와이어프레임 — 4번섹션 = 우하단 펌프별 가로 타임라인 막대 영역).
- **사용자 확정 결정** (plan 승인):
  - **응답 형식 = 세그먼트(가동 구간) 압축** — 펌프별 DTO + `segments:[{startDtm, endDtm}]`. 1분 원시 포인트 전체 전달 방식은 배제(응답 비대·프론트 런렝스 부담). 백엔드는 차트 라이브러리 비종속, 프론트가 `{ x: [startDtm, endDtm], y: pumpNm }` floating bar 포인트로 변환.
  - **상태 범위 = ON 구간만** — 가동(ON) 세그먼트만 반환. OFF·BAD·UNCERTAIN·결측은 세그먼트 미생성(화면상 빈 공간).
  - **검색 DTO = from~to 공통 부모 + 3·4번섹션 자식 정렬** — from~to 공통 부모 SearchDto 신규 + 4번섹션 자식 신규 + 기존 3번섹션 `PumpTimeSeriesSearchDto`도 부모 상속으로 정렬. (사용자가 "부모 신규 + 3·4번섹션 모두 정렬"을 명시 선택)
- **직전 사이클**: 2번섹션(`PumpOperationRateController`/`Service`/`Dto`)·3번섹션(`PumpTimeSeriesController`/`PumpPowerTimeSeriesService`/`PumpFrequencyTimeSeriesService`)이 직전 커밋으로 완료. 모두 `com.mo.swtp.instrument` 패키지·`@Tag "11. 송수펌프 가동이력"`·`/api/instrument` 경로.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 검색 DTO 공통 부모 상속 도입 + API 계층 책임(컨트롤러/엔드포인트/서비스/응답 DTO)
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약 (블로커 0 / 권고 1 / 참고 2):
  - **(Q1 룰 충돌 판정)** `api-patterns.md`("검색 조건 DTO 공통 부모 추상화 도입 보류 — 3건 누적 시 ANALYZE 결정", `coding-discipline.md §2` 근거)가 `api/CLAUDE.md`("공통 검색 조건은 부모 클래스로")보다 나중 작성된 **하위 SSOT**다. 현재 from~to SearchDto는 `PumpTimeSeriesSearchDto` 1개 + 4번 신규 = 2건(3건 미달). **그러나 사용자 명시 결정이므로 룰 예외 도입 가능** — RESULT "계획 외 변경 — 의도된 필수 부수 변경"에 명기 의무. 부모 클래스명 `PumpPeriodSearchDto` 권장(현재 100% instrument 전용 → `instrument.dto` 패키지, 공통 위치는 타 도메인 재사용 시 이관).
  - **(Q2 isValid 분담)** 부모 = from~to 검증(`fromDt!=null && toDt!=null && !fromDt.isAfter(toDt)`) + `toStartDtm()`·`toEndExclusiveDtm()` SSOT. 3번 자식 = `@Override isValid()` → `inqUnit != null && super.isValid()`. 4번 자식 = override 없이 부모 `isValid()` 그대로 사용.
  - **(Q3 정밀 수정 영향)** `@ModelAttribute` 바인딩·Service 호출부 무변경. **단 Lombok `@Getter @Setter`는 부모·자식 각각 선언 필수**(Lombok은 선언 클래스 필드만 생성 — 부모만 선언 시 자식 필드 바인딩 누락). 시그니처(`getFromDt`/`getToDt`/`getInqUnit`/`isValid`/`toStartDtm`/`toEndExclusiveDtm`) 유지 시 `PumpPowerTimeSeriesService`·`PumpFrequencyTimeSeriesService` 무변경.
  - **(Q4 컨트롤러)** 신규 `PumpOperationHistoryController` 분리(3번섹션 선례 — 기존 컨트롤러 확장은 `§3` 위반). 경로 `/api/instrument/pump-operation-history`(리터럴 세그먼트 → `/{instrumentId}` path-variable보다 PathPattern 특이도 높아 충돌 없음). `@Tag` 문자열 재사용.
  - **(Q5 서비스/DTO)** 런렝스 인코딩 = Service `private` 헬퍼, 50줄 준수 가능(루프 1개 + 세그먼트 누적). `private boolean isOnState(rawVal, qualityCd)` 판정 헬퍼 분리 권고. 응답 DTO `PumpOperationHistoryDto{pumpId,pumpNm,segments:List<OperationSegment>}` / `OperationSegment{startDtm,endDtm}` — `BaseAuditResponseDto` 미상속(시계열 응답 규칙)·정적팩토리 `of()`·`@ArraySchema(schema=@Schema(implementation=OperationSegment.class))`. 검색 DTO 상속 깊이 = `PumpPeriodSearchDto → 자식` **2단**(`§2.1` 3단 미달, 통과).
- **결론**: 부모 `PumpPeriodSearchDto`(instrument.dto) 신규 + 3·4번 자식 정렬(예외 도입, RESULT 명기). 신규 `PumpOperationHistoryController`(`/api/instrument/pump-operation-history`) + `PumpOperationHistoryService` + `PumpOperationHistoryDto`/`OperationSegment`. 부모·자식 각각 `@Getter @Setter`. 런렝스 로직 Service private 헬퍼.

### 안건 2: 신규 어휘 (응답 DTO 변수명·중첩 클래스명·검색 DTO 클래스명)
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **(Q1 `segment`)** **신규 표준 단어 등록 대상**. 응답 DTO 중첩 클래스명 `OperationSegment`·필드 `segments`에 직접 사용 + 향후 `segment_start_dtm` 류 컬럼 조합 가능성. `hr`·`actl`·`inq`·`rate` 응답 DTO 변수명 등록 선례 정합. 기존 `se`(세부)·`div`(구분)와 어근·의미 충돌 없음. 약어 `seg`는 `seq`(순번)와 철자 혼동 → **풀네임 `segment` 7자 채택**(`format`·`quality`·`branch` 풀네임 선례).
  - **(Q2 `period`)** 등록 불요 — 검색 DTO 클래스명(`PumpPeriodSearchDto`)에만 사용, DB 컬럼 조합 재료 아님. `start`·`end`·`dt`·`dtm`이 기간 경계 컬럼 재료 담당. 명칭은 PLAN 확정(Java 일반어 허용 범위).
  - **(Q3 `OperationHistory`)** Java 클래스명 사용 가능 — `hstry` 비고 "컬럼 조합어로만 제한"은 **DB 컬럼명 맥락** 제한, Java 클래스명은 `naming.md` PascalCase 자유 영문. 2번섹션 `PumpOperationRate`·3번섹션 `PumpPowerTimeSeries` 선례 동형.
  - **(Q4 `startDtm`/`endDtm`)** 응답 DTO 변수(camelCase, DB 컬럼 아님) — `start`·`end` 표준 단어 기등록 재사용. `standard-terms.md` 사용 테이블 갱신 불요(표준 용어는 DB 컬럼 SSOT).
- **결론**: 신규 표준 단어 **1건(`segment`)**. 표준 데이터 도메인 0건 / 표준 용어(DB 컬럼) 0건 / 비즈니스 도메인 약어 0건. `period`·`OperationHistory`는 클래스명(등록 불요). `startDtm`/`endDtm` 재사용.

### 안건 3: OPS 가동상태 도메인 룰 정합성 (ON 판정·세그먼트 경계·결측 처리·4영역)
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약 (블로커 1 / 권고 0 / 참고 1):
  - **(Q1 ON 판정)** `GOOD + raw_val == 1` 단독 판정은 `ot-integration.md §3` + 2번섹션 `PumpOperationRateService.computeOprtngRate` 완전 정합. OPS는 HLV 미적용 → `corr_val` 참조 금지. `raw_val==0`(OFF)·BAD·UNCERTAIN·NULL은 ON 아님(세그먼트 미생성). 통과.
  - **(Q2 세그먼트 경계 — 참고)** `acq_dtm`은 수집 "시작" 시각 → 00:00 ON = "00:00~00:01 가동 중". **`endDtm = 마지막 ON acq_dtm + 1분` 채택**해야 막대가 빈틈없이 연결. PLAN 가정 섹션 명시.
  - **(Q3 결측·BAD 중간 끼임 — 블로커)** **무조건 split. gap 허용 임계(bridge) 적용 불가.** 근거: `ot-integration.md §3` OPS "통신 단절 후 정지해도 ON 표시 → 운전원 오인 위험" — HLV 금지의 이유가 곧 결측·BAD를 ON으로 채우면 안 된다는 안전 원칙. bridge는 HLV 금지 우회 → 도메인 룰 위반. 1분이라도 결측·BAD 끼이면 세그먼트 분리.
  - **(Q4 UNCERTAIN)** 배제(ON 아님) 정합. UNCERTAIN(전환 중·범위 경계)은 가동 확신 불가. 2번섹션 GOOD only 정합.
  - **(Q5 4영역)** 알람/인터록/AI운전모드/이력기록 모두 **비해당**(읽기 전용 SELECT). OPS 결측 정책(즉시 BAD 격상·HLV 미적용)은 `ot-integration.md §3` **기등재** → 룰 갱신 불요(기존 룰 소비).
- **결론**: ON = GOOD + raw_val=1(corr_val 미사용). **결측·BAD 무조건 split**(bridge 금지). `endDtm = 마지막 ON acq_dtm + 1분`. UNCERTAIN 배제. 4영역 비해당. `ot-integration.md` 갱신 0건.

### 안건 4: OPS 시계열 조회 쿼리 (재사용 vs 신규·파티션·정렬·응답량)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약 (블로커 1 / 권고 1 / 참고 1):
  - **(Q1 재사용 vs 신규 — 블로커)** **방안B 신규 메서드 권장.** 쿼리 단에서 `quality_cd='GOOD' AND raw_val=1` 필터 → ON 행만 반환, 전송 행 수가 가동률에 비례 감소 + Service 런렝스 단순화. 방안A(기존 `findByTagSrlNosAndDtmRange` 재사용 + Service 필터)는 OFF·BAD 행 전체 로딩(가동률 30~50% 시 절반 이상 폐기). 3번섹션(쿼리 내 GOOD 필터) 정합. 기존 메서드는 범용으로 존치.
  - **(Q2 응답량 — 참고)** 방안B + 가동률 50% 기준 1개월 ≈ 9만 행(실용 범위). 방안A는 18.7만 행 힙 상주(심각). 세그먼트 변환은 전 구간 일괄 처리 필요 → 페이지네이션/스트리밍 불필요. **API 레벨 최대 31일 상한 권고**.
  - **(Q3 정렬 — 권고)** 신규 메서드 `ORDER BY tag_srl_no ASC, acq_dtm ASC`(런렝스는 태그별 시간순 순회). 인덱스 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 역방향 스캔 활용 또는 `EXPLAIN ANALYZE` 실측 후 Sort 노드 회피 확인.
  - **(Q4 인덱스)** 신규 복합/부분 인덱스 불필요. `quality_cd`·`raw_val`은 인덱스 비포함 → `tag_srl_no`(등가)+`acq_dtm`(범위)로 좁혀진 결과셋에 heap filter. `raw_val=1` 부분 인덱스는 실익 미미, 기존 인덱스 + 실측 후 결정.
  - **(Q5 DDL)** 신규 DDL·인덱스·마이그레이션 **0건** 확인.
- **결론**: **방안B 신규 메서드**(`findOnStateByTagSrlNosAndDtmRange` 류 — 쿼리 내 `quality_cd='GOOD' AND raw_val=1` 필터, `(tag_srl_no, acq_dtm)` 정렬). 신규 인덱스·DDL 0건. 31일 상한 권고(PLAN 결정). EXPLAIN ANALYZE 실측.

> **Round 2 미실시**: 두 블로커(domain Q3 bridge 금지 / dba Q1 방안B)는 상호 보완 수렴한다 — 방안B로 ON 행만(GOOD+raw_val=1) 가져오면 Service는 연속 ON 행의 `acq_dtm` 간격이 수집주기(1분)면 같은 세그먼트, gap이 벌어지면(=중간에 OFF/BAD/결측) split. 즉 **OFF 행 미조회로도 결측·BAD split이 자연 구현**되어 두 결론이 단일 알고리즘으로 결합. 에이전트 간 이견 없음.

---

## 표준 사전 카탈로그

### 신규 표준 단어

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `segment` | 구간 | 신규 | `standard-words.md` 미등록. 응답 DTO 중첩 클래스명 `OperationSegment`·필드 `segments` 사용. `se`(세부)·`div`(구분)와 어근·의미 충돌 없음. `seg` 대신 풀네임 7자 채택(`seq`(순번) 철자 혼동 회피, `format`·`quality`·`branch` 풀네임 선례). 향후 구간 시작·종료 컬럼 조합 재료 (송수펌프가동이력_4번섹션 ANALYZE1 안건 2) |

### 신규 표준 데이터 도메인

없음 — 조회 전용, DB 영속 신규 0건. `startDtm`·`endDtm`은 응답 DTO `LocalDateTime`(기존 `DOM_DTM` 의미 정합), DB 컬럼 아님.

### 신규 표준 용어 (DB 컬럼명)

없음 — 신규 DB 컬럼 0건. `start_dtm`·`end_dtm`은 `standard-terms.md`에 DB 컬럼(`ai_drvn_mod_h` 용)으로 기등록 — 4번섹션 응답 DTO 변수(`startDtm`/`endDtm`)는 재사용이며 사용 테이블 갱신 불요.

---

## 신규 엔티티/DB 컬럼

**신규 엔티티·DB 컬럼·인덱스·마이그레이션 0건.** (읽기 전용 조회 API + 인메모리 런렝스 인코딩)

- 사용 엔티티 (모두 기존): `Instrument`/`Pump`(`instrument_m`/`pump_m`), `Tag`(`tag_m`), `RawData`(`rawdata_1m_h`).
- 사용 enum (모두 기존): `TagMeasurementType`(OPS), `QualityCode`(GOOD), `EquipType`(PUMP), `YnType`(Y).
- 사용 인덱스 (모두 기존): `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)`, `idx_tag_m_instrument_id_tag_se_cd`.
- 신규 코드 (비-DDL): 검색 DTO 부모 `PumpPeriodSearchDto` + 4번섹션 자식 + 응답 DTO + 컨트롤러 + 서비스 + raw 도메인 조회 메서드 1건.

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 판정 | 해소책 (회의 결론 일치) |
|----------|------|----------------------|
| 검색 DTO 공통 부모 도입 — `api-patterns.md`(3건 보류) vs `api/CLAUDE.md`(상속 권장) | **사용자 결정 예외 도입 (권고)** | 현재 2건(3건 미달)이나 사용자 명시 결정. RESULT "계획 외 변경 — 의도된 필수 부수 변경" 명기 (안건 1) |
| 3번섹션 `PumpTimeSeriesSearchDto` 부모 상속 정렬 = 기존 코드 변경 | **정밀 수정 예외 (참고)** | `coding-discipline.md §3` "계획 외 변경" 절에 사유 명기. 시그니처 유지로 호출부 무변경 (안건 1) |
| OPS 결측·BAD 구간 ON 연결(bridge) 가능 여부 | **무조건 split (블로커→구현 방침 확정)** | bridge = `ot-integration.md §3` OPS HLV 금지 원칙 위반. gap 임계 미적용 (안건 3) |
| OPS 시계열 전체 행 로딩(방안A) vs ON 행만(방안B) | **방안B 신규 메서드 (블로커→채택)** | 쿼리 내 `GOOD + raw_val=1` 필터로 메모리·전송량 가동률 비례 감소. OFF 행 미조회로 split 자연 구현 (안건 4) |
| `segment` 약어 vs 풀네임 | **풀네임 `segment` 채택** | `seg`는 `seq`(순번) 철자 혼동. `format`·`quality` 풀네임 선례 (안건 2) |
| UNCERTAIN 집계 (§3 가중치 0.5 vs 배제) | **배제 (ON 아님)** | 가동 확신 불가. 2번·3번섹션 GOOD only 정합 (안건 3) |

---

## PLAN 으로 전달할 결정 사항

- **패키지**: `com.mo.swtp.instrument` (`web`/`service`/`dto`). 조회 메서드는 `com.mo.swtp.raw`(raw 도메인 소유).
- **검색 DTO 부모**: `PumpPeriodSearchDto`(가칭, `instrument.dto`) — `fromDt`(LocalDate)·`toDt`(LocalDate) + `isValid()`(from~to 검증)·`toStartDtm()`(fromDt 자정)·`toEndExclusiveDtm()`(toDt+1일 자정) SSOT. `@Getter @Setter @NoArgsConstructor`.
  - 3번 자식 `PumpTimeSeriesSearchDto`: 부모 상속, `inqUnit` 필드 유지 + `@Override isValid()` = `inqUnit != null && super.isValid()`. `@Getter @Setter` 자체 선언.
  - 4번 자식 `PumpOperationHistorySearchDto`(가칭): 부모 상속, 추가 필드 없음, override 없음. `@Getter @Setter`.
  - 부모 클래스명·자식 클래스명 최종 PLAN 확정.
- **컨트롤러**: 신규 `PumpOperationHistoryController extends CommonController`, `@Tag("11. 송수펌프 가동이력")` 재사용. 엔드포인트:
  - `GET /api/instrument/pump-operation-history` → `ResponseEntity<CommonResponseDto<List<PumpOperationHistoryDto>>>`, `@ModelAttribute PumpOperationHistorySearchDto`.
  - `@Operation`/`@ApiResponses`(200·400·401·403·404·500).
- **서비스**: `PumpOperationHistoryService` `@Transactional(readOnly=true)`. 흐름: 활성 PUMP 조회(`findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(PUMP, Y)`) → OPS 태그 `tag_srl_no` IN 일괄 조회(`findByInstrumentInstrumentIdInAndUseYn` + `TagMeasurementType.OPS` 필터) → ON 시계열 조회(방안B) → 펌프별 ON 세그먼트 런렝스 인코딩 → DTO 매핑. 메서드 50줄·추상화 3단 이내(`private` 헬퍼: 런렝스 인코딩 + `isOnState` 판정).
- **응답 DTO** (BaseAuditResponseDto 미상속, 정적팩토리 `of()`):
  - `PumpOperationHistoryDto` { `pumpId`, `pumpNm`, `segments`: `List<OperationSegment>` } + `@ArraySchema(schema=@Schema(implementation=OperationSegment.class))`.
  - `OperationSegment` { `startDtm`(LocalDateTime), `endDtm`(LocalDateTime) } — `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")`.
  - OPS 태그 부재·ON 데이터 없는 펌프는 `segments` 빈 배열.
- **조회 메서드** (`RawDataCustomRepository`/`Impl`, 방안B — `§2.5` 면책, `query-tuning.md §2` 인용): 신규 `findOnStateByTagSrlNosAndDtmRange`(가칭) — `WHERE tag_srl_no IN(...) AND acq_dtm ∈ [from,to) AND quality_cd='GOOD' AND raw_val=1`, `ORDER BY tag_srl_no ASC, acq_dtm ASC`. 반환은 `(tagSrlNo, acqDtm)` 중심(경량) 또는 기존 `RawDataLatestDto` 재사용 — PLAN 결정. `acq_dtm` 범위로 월 RANGE 파티션 프루닝 강제. `EXPLAIN ANALYZE` 실측(Sort 노드 회피 확인).
- **런렝스 인코딩 규칙**: 태그별 ON 행 `acq_dtm` 오름차순 순회. 첫 ON → 세그먼트 시작(`startDtm = acq_dtm`). 다음 ON `acq_dtm`이 직전 ON `acq_dtm + 1분`(수집주기)이면 연속, gap 벌어지면 split(직전 세그먼트 `endDtm = 직전 ON acq_dtm + 1분`, 새 세그먼트 시작). 마지막 ON → `endDtm = 마지막 ON acq_dtm + 1분`.
- **조회기간 상한**: dba 권고 31일. 적용 여부·방식(에러 vs 자동 클램프) PLAN 결정.
- **DB**: DDL·마이그레이션·docs/ddl 영향 **0건**.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| **ON 판정 = `GOOD 품질 + raw_val == 1`**, corr_val 미사용 | 결정 | OPS HLV 미적용. 2번섹션 `computeOprtngRate` 정합 (안건 1·3) |
| **결측·BAD·UNCERTAIN 구간 무조건 split** (gap bridge 미적용) | 결정 | `ot-integration.md §3` OPS HLV 금지 원칙 직결. 1분이라도 비ON 끼이면 세그먼트 분리 (안건 3 블로커) |
| **`endDtm = 마지막 ON acq_dtm + 1분`** (수집 시작 시각 + 수집주기) | 가정 | 막대 빈틈없는 연결. `acq_dtm`=수집 시작 시각 해석. UI 요건 (안건 3 참고) |
| **연속성 판정 = 다음 ON `acq_dtm` == 직전 ON `acq_dtm + 1분`** | 가정 | 수집주기 1분(`rawdata_1m_h`). 분 단위 정렬 전제 — 초 단위 지터 존재 시 PLAN/구현에서 정규화 검토 |
| **조회 메서드 = 방안B 신규**(쿼리 `GOOD + raw_val=1` 필터, ON 행만 반환) | 결정 | 메모리·전송량 가동률 비례 감소. OFF 행 미조회로 split 자연 구현 (안건 4 블로커) |
| 신규 메서드 정렬 `(tag_srl_no ASC, acq_dtm ASC)` + EXPLAIN ANALYZE Sort 노드 회피 확인 | 미해결 | 인덱스 `(tag_srl_no, acq_dtm DESC)` 역방향 스캔 실측 (안건 4 권고) → PLAN/impl 확정 |
| 조회기간 상한 31일 적용 여부·방식(에러 vs 클램프) | 미해결 | dba 참고. frontend UX 연계 — PLAN 결정 |
| 검색 DTO 부모 도입 = `api-patterns.md` 3건 보류 조항 예외 (현재 2건) | 결정 | 사용자 명시 결정. RESULT "계획 외 변경 — 의도된 필수 부수 변경" 명기 (안건 1) |
| 부모·자식 클래스명(`PumpPeriodSearchDto`/`PumpOperationHistorySearchDto`) 최종 확정 | 미해결 | ANALYZE 후보. PLAN 확정 (안건 1·2) |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| ON 세그먼트 런렝스 인코딩 단위 테스트 GREEN (Mockito) | 신규 `PumpOperationHistoryServiceTest`: ①하루 중 여러 번 껐다 켜기 → 다수 세그먼트 분리 ②중간 BAD/UNCERTAIN 끼임 → split(미연결) ③데이터 결측(행 없음) gap → split ④전 구간 ON → 단일 세그먼트 ⑤전 구간 OFF → 세그먼트 0건 ⑥endDtm = 마지막 ON acq_dtm + 1분 |
| 검색 DTO 부모 상속 정렬 후 3번섹션 회귀 무영향 | `./gradlew.bat :api:test` PASS (기존 `PumpPowerTimeSeriesServiceTest`·`PumpFrequencyTimeSeriesServiceTest` GREEN 유지) |
| 엔드포인트가 펌프별 ON 세그먼트 반환 | Swagger `CommonResponseDto<List<PumpOperationHistoryDto>>` 형태 확인 + `GET /api/instrument/pump-operation-history?fromDt=..&toDt=..` 응답 |
| 전체 빌드 무결성 (부모/자식 DTO·신규 컨트롤러·서비스·조회 메서드) | `./gradlew.bat :api:build` BUILD SUCCESSFUL |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `rawdata_1m_h` OPS SELECT + 세그먼트 응답만. `alarm_h` 무접촉. 임계값·전이·복귀 무관 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령(아웃바운드) 미발행. PLC 통신 없음. `pump_interlock_p` 무접촉. 조회 전용 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 무접촉. 모드 변경·SCADA 강제전환 로직 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `transition_reason`·`pump_ctrl_h` 생성 없음. 신규 엔티티/DB 컬럼 없는 SELECT 전용 |

> 비해당 단독 4건 차단 해제 조건 자가 점검: (1) 각 행 구체 사유 명기 완료, (2) "## 신규 엔티티/DB 컬럼" 신규 0건 — 두 조건 동시 충족. **OPS 결측 정책**(즉시 BAD 격상·HLV 미적용)은 `ot-integration.md §3`에 **기등재** — 4번섹션은 기존 룰을 소비할 뿐 룰 갱신 불요(도메인 전문가 안건 3 확인).

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `segment` 신규 등록 (영문 약어: `segment`, 한글 논리명: 구간, 풀네임: segment, 기본 데이터 도메인: (조합), 비고: 응답 DTO 중첩 클래스명 `OperationSegment`·필드 `segments` 사용. `seq`(순번) 철자 혼동 회피로 `seg` 약어 대신 풀네임 7자 채택. `format`·`quality`·`branch` 풀네임 선례 정합. `se`(세부)·`div`(구분)와 어근·의미 충돌 없음. 향후 구간 시작·종료 컬럼 조합 재료. 송수펌프가동이력_4번섹션 ANALYZE1 안건 2)

> 본 작업은 신규 표준 데이터 도메인·DB 컬럼(표준 용어)·비즈니스 도메인 약어·DDL·엔티티 패턴 변경 0건. ROOT 어휘 사전 중 `standard-words.md` 1건만 갱신(`segment`). backend 모듈 룰(`standard-terms.md`·`ot-integration.md`·`naming.md`) 갱신 0건.

## 산출물
- [계획안](../../../plan/20260602/송수펌프가동이력_4번섹션/PLAN1.md) (PLAN 단계 생성 예정)
