---
status: approved
created: 2026-06-04
updated: 2026-06-04
---
# 송수펌프 제어이력 2·3번 섹션 API — 도메인 분석

## 작업 배경

송수펌프 제어이력 화면(`backend/image/송수펌프제어이력.png`)의 2·3번 섹션 백엔드 조회 API를 개발한다. 두 섹션은 **동일한 제어이력 테이블**을 조회하므로 한 사이클에서 함께 진행한다.

- **2번 섹션 (AI 운영 현황)**: 조회기간(from~to) 동안의 제어이력을 운전모드(AI / AI추천 / AI분석)별로 카운트하고, 모드별카운트 ÷ 전체카운트 비율(%)을 차트·표로 표현. (이미지: 전체 102 / AI 68·66.7% / AI추천 34·33.3% / AI분석 0·0%)
- **3번 섹션 (제어 이력)**: 같은 조회기간의 제어이력을 시간 역순 표로 표출. 컬럼 = 제어요청시간 · 제어대상펌프명 · 펌프 제어 태그번호 · 제어요청구분(중지/가동) · 제어결과(제어완료/제어취소) · 갱신시간(제어완료시간) · 운전모드(콘트롤러).

### 데이터 소스 현황 — 고아 테이블 `pump_ctrl_h`

데이터 소스 `pump_ctrl_h`는 **dev DB에 월 파티션과 함께 물리적으로 존재**하고 컬럼이 요구사항과 정확히 일치하지만, **2026-05-12 코드 백지화로 엔티티·마이그레이션이 삭제된 고아 테이블**이며 현재 **0행**이다.

dev DB 실측 (information_schema + pg_indexes):

| 컬럼 | 타입 | NULL | 요구사항 매핑 |
|------|------|------|--------------|
| `pump_ctrl_id` | bigint | NOT NULL | 이력 PK |
| `ctrl_dtm` | timestamp (파티션키) | NOT NULL | 제어요청시간 |
| `instrument_id` | varchar(36) | NOT NULL | 제어대상 펌프 (→ `instrument_m.instrument_nm`, → `tag_m`) |
| `ctrl_div` | varchar(20) | NOT NULL | 제어요청구분 (가동/중지) |
| `ctrl_rslt` | varchar(20) | NOT NULL | 제어결과 (완료/취소) |
| `ai_drvn_mod` | varchar(20) | NULL | 운전모드/콘트롤러 (AI / AI_RECOMD / AI_ANLS, NULL=수동) |
| `updt_dtm` | timestamp | NOT NULL | 갱신시간(제어완료시간) |
| `rgstr_dtm`/`rgstr_id`/`updt_id` | — | NOT NULL | BaseEntity 메타 |

- PK: `(pump_ctrl_id, ctrl_dtm)` 복합 (파티션 키 포함)
- 인덱스: BRIN(`ctrl_dtm`), B-tree(`instrument_id, ctrl_dtm DESC`)
- 월 RANGE 파티션 `pump_ctrl_h_202604`~`202609` (0행)

### 사용자 결정 (`/dev` 진입 시 AskUserQuestion)

1. **데이터 소스**: **재도입 (5인 회의 경유)** — 제어이력 테이블을 코드 자산으로 재도입하되 **기존 `pump_ctrl_h` 컬럼 구조 최대 활용**(물리명 유지).
2. **검증**: **시드 데이터 포함** — 단위 테스트 + 화면 확인용 더미 제어이력 시드.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 제어이력 엔티티 재도입 위치·명명·API 구조
- 호출 에이전트: `wtp-backend-engineer` (Round 1)
- Round 1 답변 요약:
  - **패키지**: `com.mo.swtp.instrument` — `pump_ctrl_h`의 핵심 식별자는 `instrument_id`(FK 보유 측 SSOT), 조회 흐름도 펌프 중심. 가동이력 형제 섹션(2/3/4번)이 이미 instrument에 있어 화면 응집성 높음. `ai_drvn_mod`는 행 속성값일 뿐 소유 도메인 결정 요소 아님.
  - **엔티티**: `PumpCtrlHistory` + 복합 PK `@EmbeddedId PumpCtrlHistoryId(pumpCtrlId, ctrlDtm)` (`@IdClass`보다 `@EmbeddedId`가 프로젝트 표준). `instrument_id`는 시계열→마스터 FK 금지 정책에 따라 `@Column` 논리 참조(@ManyToOne 금지).
  - **Service/Controller**: 단일 `PumpCtrlHistoryService` + 단일 `PumpCtrlHistoryController`(엔드포인트 2개). 동일 테이블·동일 검색조건이므로 분리 시 파편화 (`coding-discipline.md §2`).
  - **DTO**: 섹션2 `PumpCtrlStatDto`(totalCount + `List<ModeStatDto>`{aiDrvnModCd, count, rate}), 섹션3 `PumpCtrlHistoryDto`(목록 행, `BaseAuditResponseDto` 미상속 — `_h` 시계열). `@JsonFormat("yyyy-MM-dd HH:mm:ss")`·enum `@Schema(implementation=...)` 의무.
  - **정량 기준**: 통계 Querydsl 집계 빌더 50줄 초과 시 `§2.5 면책(query-tuning.md §2)` 주석 명기. rate 계산은 Service private 메서드로 유지(3단 경계 회피).
- **결론**: instrument 패키지 배치. `PumpCtrlHistory`(+`PumpCtrlHistoryId`), 단일 Service/Controller(엔드포인트 2개), DTO 2종. 집계 빌더 50줄 면책 주석 적용.

### 안건 2: 표준 용어 재도입(폐기 번복) 분류 + 코드값 enum
- 호출 에이전트: `wtp-glossary-manager` (Round 1 + Round 2)
- Round 1 답변 요약: `ctrl` 약어 재등록 금지(물리명 잔존만). `ctrl_div` 의미 충돌(구 MANUAL/AUTO vs 신 START/STOP) — 재명명 vs 물리명 유지+코드값 재정의 사용자 결정 필요(블로커 제기). `ai_drvn_mod`→`ai_drvn_mod_cd` 정렬 권장.
- Round 2 답변 요약 (사용자 "최대 활용" 결정 전제 + 0행 = 구 코드값 실데이터 없음):
  - `ctrl` 비즈니스 도메인 약어 **재등록 금지 확정** — 폐기 이력 유지 + 비고 주석만 보강.
  - 표준 용어 `pump_ctrl_id`·`ctrl_dtm`·`ctrl_div`·`ctrl_rslt`·`ai_drvn_mod` 5건 **폐기 후 재도입**(`predc_id`·`predc_dtm` 선례 패턴 — 폐기 표 유지 + 용어 표 복원 + 비고 구/신 코드값 대비).
  - `ai_drvn_mod` **물리명 유지 허용**(0행, 사용자 최대 활용 SSOT, `proc` 도메인의 `ai_drvn_mod_cd`와 별개 테이블 컬럼 — 비고로 혼동 차단).
  - 코드값: `ctrl_div` = `START`/`STOP`, `ctrl_rslt` = `COMPLETED`/`CANCELLED`, `ai_drvn_mod` = AiDrvnModeCode 재사용(NULL=수동).
  - enum **`ControlCommand`(START/STOP), `ControlResult`(COMPLETED/CANCELLED)** 확정. `cmd` 표준 단어 등록 **불필요**(`div` 기존 재사용).
- **결론**: 물리명 전부 유지. 표준용어 5건 폐기 후 재도입(비고에 구/신 코드값 대비). enum ControlCommand/ControlResult 신규. `ctrl` 약어 재등록·`cmd` 단어 등록 모두 불필요.

### 안건 3: DDL 재작성 전략 + 인덱스 + ai_drvn_mod NULL 2차 승인
- 호출 에이전트: `wtp-dba-reviewer` (Round 1 + Round 2)
- Round 1 답변 요약: 소속 도메인 미확정 → V번호·migration 배치 불가(블로커). `ai_drvn_mod` NULL 허용은 `DOM_CODE_20` NOT NULL 완화 → ANALYZE 명시 필요(블로커). BRIN 단독은 ORDER BY DESC 정렬 직접 미제공 / `ai_drvn_mod` 인덱스 불필요(저카디널리티). 파티션 202612까지 확장 필요.
- Round 2 답변 요약 (오케스트레이터 해소안 승인):
  - **DDL 배치**: instrument 도메인(V3) patch 귀속 **승인**. `common/.../db/migration/V3_1__instrument_pump_ctrl_h.sql` + `backend/docs/ddl/instrument.sql` 양쪽 동시(§5.3). `CREATE TABLE IF NOT EXISTS` + 파티션 `IF NOT EXISTS`(dev DB skip, 신환경 생성, Flyway 신규 V3_1 체크섬). 조건: PLAN "가정"에 "OT 아웃바운드 재설계 시 이관 검토" 1건 명기.
  - **NULL 2차 승인**: `ai_drvn_mod` NULL 허용 **승인** — DDL COMMENT에 "NULL=수동 제어, WHERE IS NOT NULL로 AI 집계" 명기 의무.
  - **인덱스**: `B-tree(ctrl_dtm DESC)` 단독 추가 **보류**(0행·OT writer 보류·`coding-discipline.md §2`, 누적 후 EXPLAIN 측정 후 CONCURRENTLY 추가).
  - **파티션**: `pump_ctrl_h_202610`~`202612` 3건 `IF NOT EXISTS` 추가 **확정**.
- **결론**: V3_1 patch(IF NOT EXISTS) + docs/ddl 양쪽. ai_drvn_mod NULL 승인(COMMENT 명기). 추가 인덱스 보류. 파티션 202612까지 선행 생성.

### 안건 4: 도메인 4영역 점검 + 운전모드 NULL 처리
- 호출 에이전트: `wtp-domain-expert` (Round 1)
- Round 1 답변 요약:
  - **4영역 모두 비해당** — 조회 전용, 쓰기·제어 발행 없음. `alarm_h`·인터록·`ai_drvn_mod_p/h`·신규 INSERT 모두 비접촉. AI 운전모드는 사용자 의도 축(`ai_drvn_mod`) READ만 — `ot-integration.md §5` 변경 주체 규제와 충돌 없음.
  - **NULL 처리**: 섹션2 NULL 제외(`WHERE ai_drvn_mod IS NOT NULL` = AI 제어만 집계)는 도메인상 타당. 수동 제어(NULL)는 화면 집계 대상 아님.
  - **시맨틱**: AI_ANLS(분석 모드)는 제어 명령 억제(`§5`)이므로 이력 0건이 도메인상 타당(이미지 AI분석=0 정상).
- **결론**: 4영역 비해당. 섹션2 NULL 제외(AI 제어만). AI/AI추천/AI분석 3종 모드는 0건이라도 응답에 포함(이미지 정합).

### 안건 5: 제어 태그 식별 — 신규 제어 코드값 도입 (사용자 정정 반영)
- 호출 에이전트: `wtp-domain-expert` · `wtp-backend-engineer` · `wtp-glossary-manager` (Round 1) + 사용자 결정 (Round 2)
- 배경: 초기 가정(제어 태그 = `OPS` 가동상태 태그 대표)을 **사용자 정정으로 폐기** — "펌프 가동상태 태그와 펌프 제어 태그는 별개다. 펌프는 (1)가동상태 태그 + (2)제어 태그를 따로 갖는다". dev DB 실측: 전 태그 `io_cd=INPUT`, OUTPUT 제어 태그 0건, `TagMeasurementType`(FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI/PWQ)에 제어/명령 코드 없음.
- Round 1 답변 요약:
  - **wtp-domain-expert**: `io_cd=OUTPUT` 식별은 OT 표준 정합(가동상태 DI=INPUT ⊥ 제어 DO=OUTPUT, `ot-integration.md §1·§2`). 조회 전용이라 `§5 ⚠️ 절대 금지`(제어 발행 경로 한정) 충돌 없음 — 화면 표출도 직접 PLC 명령 경로 부재로 위험 없음. 펌프당 OUTPUT 태그 복수 가능(가동/중지 DO + 주파수설정 AO 등) → 대표(`ctrl_div` 대응 가동/중지 DO 태그) 선택 가정 명기 필요.
  - **wtp-backend-engineer**: `io_cd=OUTPUT` 필터만으로 식별 충분(N+1 없음 — 단일 Querydsl). 복수 OUTPUT → 중복행 위험 → `MIN(tag_srl_no)`/`LIMIT 1` 단일화 + "펌프당 OUTPUT 1개" 가정 의무. 0개 → LEFT JOIN NULL 허용(`tagSrlNo` nullable + `@Schema` 명기). `pump_ctrl_h` 태그 컬럼 직접 추가는 역방향 중복(`entity-patterns.md §FK 보유 측 SSOT`) 위반 → 금지. `TagMeasurementType`(javadoc 인바운드 측정 전용)에 제어 코드 추가는 SRP 의미 위반 경고.
  - **wtp-glossary-manager**: `OPS` 재사용(io_cd만 구분)은 유사충돌(`tag_se_cd=OPS` 단독으로 상태/명령 구별 불가, `se`=세부 의미 소실) → 거부. 신규 코드값 권장. `TagMeasurementType`은 이미 `OPS`(디지털 DI 상태)를 포함하므로 "측정유형 전용"이 아닌 "신호유형(Signal Type)"으로 재해석하면 별도 enum 분리 불필요. 후보 코드 `CMD`(Command, 3자 대문자 SCADA 약어 — 폐기 `ctrl` 어근 혼선 회피).
- Round 2 (사용자 결정): **신규 제어 코드값 도입** 채택 (AskUserQuestion, 2026-06-04). 사용자의 "별개 태그" 모델에 가장 충실 — 가동상태(OPS)와 제어 태그가 `tag_se_cd` 코드값으로 명시 구별.
- **결론**: `TagMeasurementType`에 `CMD`(운전제어, 단위 없음) 코드값 신규 추가 + javadoc "측정유형→신호유형(Signal Type)" 재해석. 제어 태그 = `tag_se_cd='CMD' AND io_cd='OUTPUT' AND use_yn='Y'`. 펌프당 제어 태그 1개 가정(복수 시 `MIN(tag_srl_no)`/`LIMIT 1` 방어), 미존재 시 `tagSrlNo` nullable(LEFT JOIN). 별도 enum 분리·`pump_ctrl_h` 컬럼 추가·신규 표준단어 모두 불필요(`CMD`는 `tag_se_cd` 코드값 확장 — FQI·PWQ 추가 선례 동형). 시드에 펌프별 제어 태그(`CMD`/`OUTPUT`) 포함.

---

## 표준 사전 카탈로그

### 신규 표준 단어
없음. (`div`·`rslt`·`dtm`·`mod`·`id` 모두 기등록. `ctrl_div` 는 물리명 유지로 `div` 재사용. 제어 태그 코드값 `CMD` 는 `tag_se_cd` enum 코드값 확장이므로 신규 표준단어 불필요 — FQI·PWQ 추가 선례 동형.)

### 신규 표준 데이터 도메인
없음. (`DOM_SEQ_BIGINT`·`DOM_DTM`·`DOM_CODE_20`·`DOM_ID_36` 모두 재사용.)

### 신규 표준 용어 (폐기 후 재도입 + 기존 코드값 확장)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `pump_ctrl_id` | `pump`+`ctrl`(테이블 역사 흔적) + `id` | `DOM_SEQ_BIGINT` | 폐기 후 재도입 | 물리명 유지. 시계열 BIGINT PK |
| `ctrl_dtm` | `ctrl`(역사 흔적) + `dtm` | `DOM_DTM` | 폐기 후 재도입 | 월 RANGE 파티션 키 |
| `ctrl_div` | `ctrl`(역사 흔적) + `div` | `DOM_CODE_20` | 폐기 후 재도입 (코드값 재정의) | 구 'MANUAL'/'AUTO' → 신 'START'/'STOP'. `ControlCommand` enum. 0행이라 실데이터 영향 없음 |
| `ctrl_rslt` | `ctrl`(역사 흔적) + `rslt` | `DOM_CODE_20` | 폐기 후 재도입 (코드값 재정의) | 구 'SUCCESS'/'WAITING'/'FAIL' → 신 'COMPLETED'/'CANCELLED'. `ControlResult` enum |
| `ai_drvn_mod` | `ai`(비즈니스 도메인) + `drvn` + `mod` | `DOM_CODE_20` | 폐기 후 재도입 (NULL 정책 추가) | NULL 허용=수동 제어. `pump_ctrl_h` 전용 — `ai_drvn_mod_cd`(proc 도메인, 별개 테이블)와 혼동 주의 |
| `tag_se_cd` (코드값 `CMD` 추가) | `tag`(비즈니스 도메인) + `se` + `cd` (기존) | `DOM_CODE_20` (재사용) | 기존 재사용 (코드값 확장) | `TagMeasurementType` enum 에 `CMD`(운전제어, OUTPUT 제어 태그) 10번째 코드값 추가. 안건 5 사용자 결정. enum javadoc "측정유형→신호유형" 재해석. FQI(주파수측정유형등록)·PWQ(송수펌프가동이력_3번섹션) 추가 선례 동형 |

분류값: 신규 / 기존 재사용 / 유사 충돌 / **폐기·통합(=재도입)**

---

## 신규 엔티티/DB 컬럼

- **비즈니스 도메인 패키지**: `com.mo.swtp.instrument` (엔티티는 `common` 모듈, 조회 API는 `api` 모듈)
- **엔티티**: `PumpCtrlHistory` (suffix `_h` 시계열 이력) + 복합 PK `@Embeddable PumpCtrlHistoryId(pumpCtrlId, ctrlDtm)`
- **테이블**: `pump_ctrl_h` (물리명 유지, dev DB 고아 테이블 재도입)
- **컬럼**: 위 dev DB 실측 10컬럼 (재생성). `instrument_id` 논리 참조(FK 금지 — `db/partitioning-and-retention.md §1`). `ai_drvn_mod` NULL 허용(2차 승인).
- **인덱스**: 기존 BRIN(`ctrl_dtm`) + B-tree(`instrument_id, ctrl_dtm DESC`) 유지. 추가 인덱스 보류.
- **파티션**: 월 RANGE, 202604~202612 `IF NOT EXISTS`.
- **enum** (`common`): `ControlCommand`(START/STOP), `ControlResult`(COMPLETED/CANCELLED). `AiDrvnModeCode`(AI/AI_RECOMD/AI_ANLS) 재사용.
- **보존 기간**: 2년 (제어 로그 — `db/partitioning-and-retention.md §2` 선례 유지)
- **tag 도메인 변경 (안건 5)**: `common/.../tag/domain/enumtype/TagMeasurementType.java` 에 `CMD`("운전제어", "") 코드값 추가 + javadoc "측정유형→신호유형(Signal Type)" 재해석. **DDL 변경 없음** (enum 값, 기존 `tag_m.tag_se_cd VARCHAR(20)` 저장). 제어 태그 = `tag_se_cd='CMD' AND io_cd='OUTPUT' AND use_yn='Y'`. 시드에 펌프별 제어 태그(`CMD`/`OUTPUT`) 1건씩 포함. `validateFqiTagAllowance` 등 기존 태그 검증 로직 비변경(`CMD` 는 본 조회 사이클에서 구동방식 제약 미적용 — OT 아웃바운드 재설계 시 재검토).

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 (회의 결론) |
|----------|------------------|
| `ctrl` 비즈니스 도메인 약어 폐기(2026-05-20) ↔ `pump_ctrl_h`/`ctrl_*` 물리명 사용 | 물리 테이블·컬럼명은 역사적 흔적으로 유지. 약어 사전 재등록 금지. 폐기 이력 비고에 재도입 주석 보강 |
| `ctrl_div` 구 의미('MANUAL'/'AUTO') ↔ 신 의미('START'/'STOP') | 0행이라 실데이터 없음. 코드값 재정의로 처리(용어 표 비고 구/신 대비 명기). 의미 충돌은 재도입 사이클 정의로 해소 |
| `ai_drvn_mod`(폐기 컬럼명) ↔ `ai_drvn_mod_cd`(현행 활성, proc 도메인) | 별개 테이블 컬럼. 물리명 유지(사용자 최대 활용). 비고로 혼동 차단 |
| `DOM_CODE_20` NOT NULL 기본 ↔ `ai_drvn_mod` NULL 허용 | DBA 2차 승인 — NULL=수동 제어 정당. DDL COMMENT 명기 |
| 도메인-V번호 1:1 원칙 ↔ `pump_ctrl_h` 소속 모호 | instrument(V3) patch 귀속. OT 아웃바운드 재설계 시 이관 검토 가정 명기 |
| `TagMeasurementType` javadoc "SCADA 인바운드 측정 항목" ↔ `CMD`(OUTPUT 제어 명령) 추가 | enum 의미를 "측정유형"→"신호유형(Signal Type)"으로 재해석 (이미 `OPS` 디지털 DI 상태 포함). javadoc 갱신으로 SRP 위반 해소. 별도 enum 분리는 사용처 1건이라 과잉(`coding-discipline.md §2`) — 안건 5 사용자 결정 |

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델**: `PumpCtrlHistory`(`pump_ctrl_h`) + `PumpCtrlHistoryId`(복합 PK). enum `ControlCommand`·`ControlResult` 신규, `AiDrvnModeCode` 재사용. `instrument_id` 논리 참조. `TagMeasurementType` 에 `CMD`(운전제어) 코드값 추가(+javadoc 재해석) — 제어 태그 식별용.
- **DB 설계 변경**: `common/.../db/migration/V3_1__instrument_pump_ctrl_h.sql` (`CREATE TABLE IF NOT EXISTS` + 인덱스 + 파티션 202604~202612) + `backend/docs/ddl/instrument.sql` 동시 갱신. 컬럼 COMMENT 전건(훅 `check-ddl-column-comment.sh`).
- **API**:
  - 섹션2: `GET /api/instrument/pump-ctrl-stat` — `PumpPeriodSearchDto`(from~to). 응답 `PumpCtrlStatDto`(전체 카운트 + 3개 모드별 {카운트, 비율}, 0건 모드 포함). `WHERE ctrl_dtm 범위 AND ai_drvn_mod IS NOT NULL GROUP BY ai_drvn_mod`.
  - 섹션3: `GET /api/instrument/pump-ctrl-history` — 동일 검색 DTO. 응답 `List<PumpCtrlHistoryDto>`(제어시간·펌프명·제어태그번호·구분·결과·갱신시간·운전모드), `ORDER BY ctrl_dtm DESC`. `pump_ctrl_h ⋈ instrument_m`(펌프명) `LEFT JOIN tag_m(tag_se_cd='CMD' AND io_cd='OUTPUT' AND use_yn='Y')`(제어 태그번호). 펌프당 제어 태그 1개 가정 — 복수 시 `MIN(tag_srl_no)`/`LIMIT 1` 단일화, 미존재 시 `tagSrlNo` null.
- **적용 패턴**: `CommonController` 상속, Querydsl `CustomRepository`+`Impl`, 정적 팩토리 DTO, `@Schema(implementation)`.
- **검증**: Mockito 단위 테스트 2종 + 시드 데이터 스크립트(이미지 분포 모사).

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 섹션2 집계는 AI 제어(`ai_drvn_mod IS NOT NULL`)만 대상. 수동(NULL)은 미집계 | 가정 | 이미지 전체=3개 AI모드 합. `wtp-domain-expert` 확인 |
| 응답은 AI/AI_RECOMD/AI_ANLS 3종을 0건이라도 모두 포함 | 가정 | 이미지 AI분석=0 행 표출 정합 |
| 제어 태그 = 가동상태(OPS) 태그와 **별개**. `tag_se_cd='CMD'(신규) AND io_cd='OUTPUT' AND use_yn='Y'` 로 식별 | 결정 | 사용자 정정 + AskUserQuestion(신규 제어 코드값 도입) 2026-06-04 |
| `TagMeasurementType` 에 `CMD`(운전제어) 코드값 추가 + javadoc "측정유형→신호유형" 재해석 (별도 enum 미분리) | 결정 | 안건 5 — FQI·PWQ 추가 선례 동형, 사용처 1건이라 enum 분리 과잉 |
| 펌프당 제어 태그(`CMD`/`OUTPUT`)는 1개 가정. 복수 시 `MIN(tag_srl_no)`/`LIMIT 1` 단일화, 미존재 시 `tagSrlNo` null(LEFT JOIN) | 가정 | wtp-domain-expert(복수 OUTPUT 가능)·wtp-backend-engineer(중복행 방어) 권고. 사용자 "펌프제어태그" 단수 표현 정합 |
| 물리명 유지(최대 활용) — `ctrl_div`/`ctrl_rslt`/`ai_drvn_mod` 재명명 안 함, 코드값만 신규 정의 | 결정 | 사용자 Q1 결정 정합 |
| `pump_ctrl_h`의 V3(instrument) 귀속은 OT 아웃바운드 재설계 시 이관 검토 필요 | 미해결 | DBA Round 2 조건. `migration/README.md` SSOT 동반 갱신 |
| 시드 데이터 적재 경로(local vs dev DB, SQL 스크립트 위치) | 미해결 | PLAN/구현 단계 사용자 확인 (`feedback_runtime_artifact_path`) |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 섹션2 통계 산정 정확 | `PumpCtrlHistoryService` 단위 테스트 GREEN — 전체=모드합 / 비율합≈100% / 분모 0 방어(전체 0건 시 비율 0) / NULL(수동) 제외 / 3개 모드 0건 포함 |
| 섹션3 목록 정렬·매핑 정확 | 단위 테스트 GREEN — `ctrl_dtm DESC` 정렬 / 펌프명(instrument_nm)·제어 태그(`CMD`+`OUTPUT` tag_srl_no, 미존재 시 null) 매핑 / 기간 미입력·역전 시 `INVALID_INQ_PERIOD` 예외 |
| 빌드·DDL 훅 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL + `check-ddl-column-comment.sh` 통과(전 컬럼 COMMENT) |
| 시드 적재 후 실조회 정합 | 시드 INSERT 후 MCP 조회 — 섹션2 모드 카운트 합=전체·비율 합 100% / 섹션3 시간 역순·매핑 일치 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 알람 임계값·전이·복귀 변경 없음. `alarm_h` 비접촉, `pump_ctrl_h` SELECT만 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 발행 없음(조회 전용). `interlockValidator` 아웃바운드 전제 — 조회 경로 비발동 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p/h` 비접촉. `pump_ctrl_h.ai_drvn_mod`(사용자 의도 축) READ·표출만 — 변경 주체 규제와 충돌 없음. `ai_mode_cd`(시스템 상태) 미조회 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 신규 INSERT·모드 전환 이력 기록 없음. 기존 이력 READ만 (쓰기 주체=OT 아웃바운드 보류) |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — `ctrl` 폐기 이력 행 `대체` 컬럼에 "(물리 테이블 `pump_ctrl_h` 재도입 사이클에서 컬럼 구조 최대 활용, 비즈니스 도메인 약어 재등록 금지)" 주석 추가
- [x] `.claude/rules/dict/standard-terms.md` — 용어 표에 `pump_ctrl_id` 재도입 행 추가 (비고: 폐기 후 재도입 — 제어이력 재도입 ANALYZE1, 2026-06-04)
- [x] `.claude/rules/dict/standard-terms.md` — 용어 표에 `ctrl_dtm` 재도입 행 추가 (비고: 폐기 후 재도입, 월 RANGE 파티션 키)
- [x] `.claude/rules/dict/standard-terms.md` — 용어 표에 `ctrl_div` 재도입 행 추가 (비고: 코드값 재정의 구 MANUAL/AUTO → 신 START/STOP, ControlCommand enum)
- [x] `.claude/rules/dict/standard-terms.md` — 용어 표에 `ctrl_rslt` 재도입 행 추가 (비고: 코드값 재정의 구 SUCCESS/WAITING/FAIL → 신 COMPLETED/CANCELLED, ControlResult enum)
- [x] `.claude/rules/dict/standard-terms.md` — 용어 표에 `ai_drvn_mod` 재도입 행 추가 (비고: NULL 허용=수동 제어, pump_ctrl_h 전용, ai_drvn_mod_cd(proc 도메인)와 혼동 주의)
- [x] `.claude/rules/dict/standard-terms.md` — 폐기 이력 표의 제어 이력·AI 운전 모드 행 `대체` 컬럼을 "재도입 (제어이력 재도입 ANALYZE1, 2026-06-04)" 으로 갱신
- [x] `.claude/rules/dict/standard-terms.md` — `tag_se_cd` 용어 행 비고에 `CMD`(운전제어, OUTPUT 제어 태그) 코드값 추가 기록 (TagMeasurementType 10번째 코드값, 신호유형 재해석 — 제어이력 재도입 ANALYZE1, 2026-06-04)

## 산출물
- [계획안](../../../plan/20260604/송수펌프제어이력_2_3번섹션/PLAN1.md) (승인 후 작성)
