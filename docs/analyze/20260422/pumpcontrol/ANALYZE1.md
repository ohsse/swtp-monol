---
status: approved
created: 2026-04-24
updated: 2026-04-25
---
# 송수펌프 제어 (FR-PMP-001) — 도메인 분석

## 작업 배경

- **요청 요약**: 스마트정수장 백엔드에 **송수펌프 제어 도메인** 최초 도입. 정수조(水돗물 최종 저장) → 송수펌프 → 배수지(지역별 분배) 의 물리적 흐름을 관리하며, AI 예측 기반 자동 제어와 수동 On/Off 제어를 모두 지원한다. 대시보드 · AI 운전모드 설정 · 자동 제어 실행 3개 API 를 제공한다.
- **비즈니스 도메인 범위 신규**: 현재 `com.mo.swtp.user` · `com.mo.swtp.auth` 만 구현되어 있으며 펌프/정수조/배수지/AI/SCADA 연동은 전부 최초 구현이다. 특히 `ot-integration.md §2` 의 OT 아웃바운드 어댑터는 본 작업을 통해 처음 도입된다.
- **외부 산출물**:
  - `docs/analyze/20260422/pumpcontrol/01.요구사항 명세서.docx` — 기능 요구사항 v1.0 (2026-04-16)
  - `docs/analyze/20260422/pumpcontrol/도메인모델링.png` — 6개 엔티티 관계도
  - `docs/analyze/20260422/pumpcontrol/클래스다이어그램.png` — Controller/Service/Repository 계층
  - `docs/analyze/20260422/pumpcontrol/시퀀스다이어그램.png` — 대시보드 로딩·AI 모드 설정·자동 제어 실행 3개 시나리오

## 회의록 (5인 팀 토픽 주도)

### 안건 1: 네이밍 충돌 3건 해소 (`pmp`/`pump`, `_MST`·`_HSTRY`/`_m`·`_h`, `_dt`/`_dtm`)

- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - **충돌 1** (pmp vs pump): `pump` 는 이미 `domain-abbreviations.md` 도입 예정 등재. `pmp` 는 동의어로 동시 등록 금지 규칙 위반. → `pump` 단일 채택, DB 컬럼 · Java 패키지 모두 `pump` 로 일괄 통일.
  - **충돌 2** (_MST/_HSTRY vs _m/_h): `naming.md §DB 테이블/컬럼 네이밍` 은 단일 문자 suffix 6종을 유일 규칙으로 고정. 풀네임 suffix 예외 허용 없음. → `PMP_MST → pump_m`, `PMP_CTRL_HSTRY → pump_ctrl_h`, `AI_DRVN_MOD_STNG → ai_drvn_mod_p` (운전 규칙·설정값은 `_p` 명세).
  - **충돌 3** (_dt vs _dtm): `standard-words.md` 는 `dt=DATE`, `dtm=TIMESTAMP` 엄격 구분. → 모든 TIMESTAMP 컬럼은 `_dtm` 강제 (`CTRL_DT → ctrl_dtm`, `UPDT_DT → updt_dtm`, `PREDC_BASE_DT → predc_base_dtm`, `PREDC_DT → predc_dtm`, `STNG_DT → stng_dtm`).
- **결론**: 3건 모두 프로젝트 룰 쪽 유지. 요구사항 문서의 DB 표기 전면 재명명. 동의어·금지 패턴 표에 혼용 방지용 등재.

### 안건 2: 신규 표준 단어 대량 등록

- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**: 후보 약 30종 대조. 18건 신규 등록 권고 (`rated`, `head`, `flwrt`, `ppln`, `prsr`, `wtlv`, `cmbn`, `predc`, `rslt`, `anls`, `recomd`, `div`, `stng`, `mod`, `base`, `tnk`, `prfwt`, `dstrwt`). `oprtng`/`drvn`/`updt`/`hstry` 4건은 사용처 확정 후 등록. **유사 충돌**: `reg` → 기존 `rgstr` 동의어, 즉시 폐기. **보류**: `elceg`/`tnkf`/`cntom` 3건.
- **Round 2 답변 요약 (보류 3건 확정)**:
  - `elpwr` (electric power, kW, 순시) + `elceg` (electric energy gauge, kWh, 누적) — **둘 다 등록**. 단위 분리로 동의어 아님.
  - `tnkf` — 단어 등록 **불필요**. 안건 3 의 `pwtf` 약어에 흡수됨.
  - `cntom` — 의미 불명, **폐기**. `PMP_OPRTNG_CNTOM` → `pump_oprtng_cnt` (cnt 단독 사용).
- **결론**: 신규 단어 20건 등록 (`elpwr`/`elceg` 포함). 폐기 3건 (`reg`→`rgstr`, `cntom`/`tnkf` 단어 미등록). 사용처 확정 4건(`oprtng`·`drvn`·`updt`·`hstry`) 은 본 작업의 실제 컬럼 등장 여부로 즉시 등록.

### 안건 3: 신규 비즈니스 도메인 약어 + `ai`/`opt` 관계 정립

- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**: `prfwt_tnkf`/`dstrwt_tnk` underscore 포함 → 패키지명 규칙 위반으로 재결정 필요. `drvn_anls`/`predc`/`ai`/`scada` 비즈니스 도메인 약어는 기존 `opt` · 기술 계층과 중복으로 등록 거부.
- **Round 2 답변 요약 (재결정)**:
  - **정수조**: `pwtf` 채택 (`purified water tank facility`). `pwt` 는 OT 센서 `PWI`(전력) 와 시각적 혼동 위험으로 기각.
  - **배수지**: `dwt` 채택 (`distribution water tank`).
  - **`ai`**: **독립 등록 + `opt` 병립**. `ai` 는 AI 운전모드·예측 서비스 상위 계층, `opt` 는 최적화 결과 저장 테이블 prefix 전용. `domain-abbreviations.md` 비고란에 경계 명시.
  - **`drvn_anls`·`scada`**: 등록 불필요. 예측은 `ai` 하위 개념, SCADA 는 `ot-integration.md` 의 기술 어댑터 패키지 전용.
- **결론**: 신규 비즈니스 도메인 약어 3건 (`pwtf`·`dwt`·`ai`) + 기존 재사용 2건 (`pump`·`ctrl` 마스터 승격). Java 패키지는 Backend 판정(안건 6) 과 연계하여 최종 결정.

### 안건 4: 표준 데이터 도메인 · 측정값 정밀도 · PK 타입 · 조합 코드 저장

- **호출 에이전트**: `wtp-dba-reviewer`
- **Round 1 답변 요약**:
  - **측정값** (압력 kgf/cm²·유량 m³/h·수위 m·양정 m·전력량 kW): 모두 `DOM_QTY_15_4` 재사용. 신규 데이터 도메인 불필요.
  - **코드 enum**: `DOM_CODE_20` 재사용. `AI_DRVN_MOD`(최대 9자), `CTRL_DIV`/`CTRL_RSLT`(영문화 후 최대 7자) 모두 포괄.
  - **PK 타입**:
    - 마스터 외부할당 PK 4개(`pump_id`·`pwtf_id`·`dwt_id`·설정 PK) — `DOM_ID_50` 재사용
    - 시계열 PK 2개(`pump_ctrl_id`·`predc_id`) — **신규 `DOM_SEQ_BIGINT`(BIGINT NOT NULL) 등록 권고**. UUID 는 파티션 로컬 인덱스 페이지 분열 유발.
  - **TAG_NM**: `DOM_NAME_100` 재사용. 단 레거시 태그명 실측 권고.
  - **PMP_CMBN_CD**: 콤마 구분 문자열·JSONB 모두 배제, **정규화된 별도 테이블 `pump_cmbn_m`(조합 헤더) + `pump_cmbn_d`(1:N 상세)** 구조 채택. 최대 원소 수 8 기준.
  - **한글 코드값**: 영문 enum 강제. `CTRL_DIV` → `MANUAL`/`AUTO`, `CTRL_RSLT` → `SUCCESS`/`WAITING`/`FAIL`.
- **결론**: 측정값 전용 도메인 0건. 신규 1건(`DOM_SEQ_BIGINT`). 조합 코드는 1:N 정규화로 구조 변경.

### 안건 5: 시계열 파티셔닝·보존·인덱스·BRIN

- **호출 에이전트**: `wtp-dba-reviewer`
- **Round 1 답변 요약**:
  - **파티션 키**: `pump_ctrl_h` → `ctrl_dtm`, `pump_predc_h` → `predc_base_dtm`. 둘 다 월 단위 RANGE.
  - **파티션 PK 제약**: PostgreSQL 파티션 테이블 UNIQUE 제약은 파티션 키를 포함해야 함 → **복합 PK 필수**. `(pump_ctrl_id, ctrl_dtm)`, `(predc_id, predc_base_dtm)`.
  - **suffix**: 둘 다 `_h` (이력). `_l` 은 집계·리포트 전용이므로 반복 쓰기 시계열에 부적합.
  - **인덱스**: 복합 B-Tree `(pump_id, ctrl_dtm DESC)`, `(pwtf_id, predc_base_dtm DESC)` — 등가·정렬 순서 원칙.
  - **BRIN**: `pump_ctrl_h` 만 적용 (AI 자동 시 분당 다수 삽입). `pump_predc_h` 는 시간당 수 건이라 BRIN 불필요.
  - **파티션 선행 생성**: 본 작업 범위 **포함**. `SchedulerService` 에 매월 1일 선행 생성 배치 추가.
  - **보존 정책 불일치**: `db-partitioning-and-retention.md §2` "제어 로그 2년 DELETE + VACUUM" 은 파티션 DROP 으로 **변경** 권고 (PLAN 문서에 명시).
- **결론**: 3개 시계열 테이블(안건 7 에서 `ai_drvn_mod_h` 신설 포함 총 3개) 모두 월 RANGE 파티셔닝. 파티션 생성/삭제 스케줄러 포함. 보존 정책 룰 업데이트 필요.

### 안건 6: 엔티티 패턴 · 계층 구조 · Python AI 연동 · 패키지 구조 · 멀티테넌트

- **호출 에이전트**: `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - **엔티티** (Q1): 외부 할당 PK 4개는 `Persistable<String>` 구현 필수 (getId override, isNew 는 BaseEntity 위임). 시계열 2개는 `SEQUENCE` 전략 권장 (IDENTITY 는 파티션 배치 최적화 불가). 마스터는 `BaseEntity` 직접 상속, 이벤트 필요성 미확인 상태라 `DomainEventEntity` 격상 보류.
  - **계층** (Q2): 3-Service 오케스트레이터 패턴(`PmpControlService` → `AiPredictionService`, `ScadaControlService`) 허용. **`ScadaControlService` 는 `com.mo.swtp.scada.outbound` 패키지 분리 필수** (`ot-integration.md §2` 구조 일치). `ai` 패키지는 약어 등록 블로커 → 안건 3 결과 적용.
  - **Python AI 연동** (Q3): **(A) HTTP REST(RestClient) 권장**. `RestClient` 는 `@Value` baseUrl 주입이 `multi-tenant.md` resources-env/ 와 자연스럽게 연계. ProcessBuilder 는 Python 런타임 관리 부담, gRPC 는 운영 복잡도 과도, MQ 는 실시간 대시보드에 부적합. 회복성: `resilience4j-spring-boot3` CircuitBreaker(50% / 30s) + Retry(3회/500ms) 어노테이션 적용.
  - **패키지** (Q4): **(C) 중간안** — `com.mo.swtp.pump` + `com.mo.swtp.scada.outbound` + `com.mo.swtp.ai`. 정수조·배수지 마스터(`pwtf_m`·`dwt_m`)는 현 작업 범위에서 `com.mo.swtp.pump.domain` 내부로 잠정 귀속. 독립 비즈니스 로직 생기면 분리.
  - **멀티테넌트** (Q5): AI 서버 URL·PLC 엔드포인트는 `resources-env/{프로파일}/application.yml`. 마스터 시드(정수조·배수지·펌프)는 Flyway `V{n}__{지자체}_seed.sql` 방식으로 검토.
- **발견 사항 (블로커)**: Q1 외부 할당 PK `Persistable` 미구현 위험 · Q2 `ai` 약어 미등록 — 둘 다 안건 3 결과로 해소 가능.
- **결론**: 3-Service · `com.mo.swtp.pump` + `com.mo.swtp.scada.outbound` + `com.mo.swtp.ai` 3-패키지 구조 · HTTP REST(CircuitBreaker + Retry) · multi-tenant resources-env 분리 방침 확정.

### 안건 7: AI 운전모드 이중 체계 통합 · OT 아웃바운드 채널 · 작업 범위 권고

- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약**: 블로커(높음) 3건 식별.
  1. **AI 운전모드 미통합** — `AI_DRVN_MOD`(AI/AI_RECOMD/AI_ANLS) 와 `AI_MODE`(0/1/2) 별개 체계 혼재. 매핑 테이블 확정 필요.
  2. **인터록 선행조건 누락** — `ot-integration.md §2` 강제 규정. `pump_interlock_p` 테이블 + `InterlockValidator` 호출 최소 구조 필요.
  3. **SCADA 장애 강제 전환 로직 미정의** — §5 의 5분 초과 AI자동→반자동 강제 전환 누락.
- **Round 2 답변 요약 (해소안)**:
  - **AI 모드 계층 분리**: `AI_DRVN_MOD` = 사용자 의도 (사용자 API 로만 변경) · `AI_MODE` = 시스템 상태 (스케줄러/장애 대응이 변경). 둘 다 `ai_drvn_mod_p` 에 **정수조(pwtf) 단위** 로 함께 저장. `AI_ANLS` 선택 중에도 `AI_MODE` 는 불변 (분석 모드는 AI 제어 명령 억제만). 강제 전환 시 `AI_DRVN_MOD` 불변 (사용자 의도 보존).
  - **범위 포함** (본 작업):
    - 인터록 최소 구조 (`pump_interlock_p` 테이블 + `InterlockValidator` — 규칙 미등록 시 통과, 있으면 평가)
    - `ai_drvn_mod_p` 에 `last_rcv_dtm`(마지막 SCADA 수신 시각) · `expire_dtm`(수동 만료 시각) 컬럼. 스케줄러가 주기적 비교·강제 전환. **heartbeat 직접 구현은 불필요**.
    - `ai_drvn_mod_h` (운전모드 전환 이력) 테이블 신설. 월 RANGE 파티셔닝, 5년 보존.
  - **범위 분리** (별도 작업 `ot_integration_inbound`):
    - 센서 품질 관리(GOOD/BAD/UNCERTAIN, Hold Last Value)
    - 알람 4단계 체계(`alarm_h` 테이블)
    - 실제 인터록 규칙 데이터 (`pump_interlock_p` 행 투입)
  - **OT 아웃바운드 채널**: 한국 지자체 PLC 는 LS Electric(Modbus TCP)·Siemens S7(OPC-UA) 혼재. `multi-tenant.md §4` 전략 2 (인터페이스 `ScadaOutboundPort` + `@Profile` 분기) 추상화.
  - **배수지 요구 압력 이력**: 본 작업 **분리**. `dwt_m.min_req_prsr` 단일 필드 유지, 분기별 이력은 별도 작업에서 `dwt_prsr_setn_h` 추가.
- **결론**: 3개 블로커 모두 해소 방안 확정. 본 작업 스코프가 명확해짐. `ai_drvn_mod_h` 테이블 신설로 엔티티 수 7개 → 8개(인터록 stub 포함 9개).

### 안건 8: 시계열 파티션 마스터 FK 금지 원칙(신규 룰) 반영 — 2026-04-25 추가

- **호출 에이전트**: TAG 도메인 정립 작업(`docs/analyze/20260424/tag_개념_정의/ANALYZE1.md`, status: approved) 의 `wtp-dba-reviewer` 권고 1을 본 작업 설계에 적용. 본 안건은 결정 기록만 수행 (재호출 불필요)
- **신규 룰**: `db-partitioning-and-retention.md §1` (커밋 53d8dc8) — "시계열 파티션 테이블(`_h` suffix) 은 마스터 테이블 FK 추가 금지. 1분 주기 대용량 INSERT 마다 마스터 행 존재 확인 잠금이 발생하여 수집 성능에 직격 영향을 준다. 참조 무결성은 애플리케이션 레벨 검증으로 대체한다."
- **본 ANALYZE1 와의 충돌**: 파티션 테이블 3종 (`pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_h`) 의 FK 4건 — `pump_ctrl_h.pump_id`, `pump_predc_h.pwtf_id`, `pump_predc_h.pump_cmbn_cd`, `ai_drvn_mod_h.pwtf_id` — 정면 충돌
- **해소안**:
  - **DB**: 위 4건 FK 미생성. DDL `FOREIGN KEY` 절 작성 금지. 인덱스는 유지 (조회 성능 보장)
  - **JPA 매핑**: `@ManyToOne` 매핑 없이 `@Column` 단독 보관. 근거: ① `entity-patterns.md` 기본 패턴에 `@ManyToOne` 예시 자체 없음, ② 현 `User` 엔티티도 연관관계 매핑 미사용 — 일관성 우선, ③ DB FK 가 없는 이상 `@ManyToOne` 의 무결성 가치 0, ④ PostgreSQL 순환 ON DELETE 리스크 원천 차단
  - **마스터 존재 검증**: 서비스 계층 `existsById` 호출 + `@Cacheable("pumpMasterExists")` 5분 TTL (분당 다수 INSERT 부담 완화)
  - **조회 join**: Querydsl 명시적 `.eq()` 표현 (`pumpMaster.pumpId.eq(pumpCtrlHistory.pumpId)`)
  - **영향 없음**: 마스터 간 FK (`pump_m.pwtf_id → pwtf_m`, `pump_cmbn_m.pwtf_id → pwtf_m`) · `_d` 상세 (`pump_cmbn_d.pump_cmbn_cd / pump_id`) · `_p` 명세 (`pump_interlock_p.pump_id`, `ai_drvn_mod_p.pwtf_id`) 모두 파티션 아님 → FK 유지
- **결론**: 파티션 FK 4건 제거. JPA 는 컬럼 only 패턴. 마스터·상세·명세 FK 는 영향 없음

### 안건 9: TAG 도메인 정립 후속 정합성 — 2026-04-25 추가

- **호출 에이전트**: 별도 ANALYZE (`tag_개념_정의/ANALYZE1.md`) 의 `wtp-glossary-manager` + `wtp-dba-reviewer` 결과 적용. 본 안건은 결정 기록만 수행
- **신규 룰**: `dict/standard-data-domains.md` (커밋 53d8dc8) — `DOM_TAG_NM_50`(VARCHAR(50), String, NOT NULL) 신규 등록 (DBA 승인 완료). `DOM_ID_50` 과 용도(시계열 비PK 계측 식별자) 분리
- **본 ANALYZE1 와의 충돌**:
  - `pump_m.tag_nm` 컬럼이 `DOM_NAME_100` (VARCHAR(100), NULL 허용) 으로 설계됨 → `rawdata_m.tag_nm` 과 의미상 동일한 SCADA 태그 식별명임에도 도메인 불일치
  - `tag` 비즈니스 도메인 약어, `tag_nm` 표준 용어, `se`/`desc` 표준 단어 모두 별도 작업에서 등록 완료 → 본 ANALYZE1 의 룰 갱신 지시서에 원래 없었으므로 추가 변경 없음
- **해소안**:
  - `pump_m.tag_nm` 데이터 도메인 `DOM_NAME_100` → `DOM_TAG_NM_50` 교정. VARCHAR(100) NULL 허용 → VARCHAR(50) NOT NULL
  - 표준 용어 표에서 `tag_nm` 행을 "기존 재사용" 분류로 유지하되 데이터 도메인 표기 갱신
  - **`tag_se_cd` 컬럼은 본 작업 외** (사용자 결정) — `tag_m` 마스터 테이블 도입 시 별도 ANALYZE 로 검토. 펌프는 통상 PWI(전력) + RMS(진동) 등 복수 유형을 동시 수집하므로 단일 코드 필드는 부적합
- **결론**: `pump_m.tag_nm` 도메인 교정 1건. tag 관련 룰 갱신은 추가 없음

### 안건 10: 미결 이슈 해소 + 일반어 컬럼 표준 단어 등록 선별 — 2026-04-25 추가

- **호출 에이전트**: `wtp-glossary-manager` 권고를 사용자 결정으로 확정. 본 안건은 결정 기록만 수행
- **미결 이슈**: 안건 4 의 `min_req_prsr` 용어 비고 — "min/req 는 영문 약어 사전 등록 대상 여부 차후 검토" 가 본 재분석에서 해소 대상으로 격상
- **일반어 후보**: ai_drvn_mod_p · ai_drvn_mod_h 의 `expire_dtm`/`last_rcv_dtm`/`prev_*`/`new_*`/`transition_reason` 컬럼군 — 표준 단어 사전 등록 범위 확정 필요
- **사용자 결정**:
  - 미결 이슈: **둘 다 등록** (`min` + `req` 표준 단어 신규 등록)
  - 일반어 단어: **최소 — `rcv` 만 등록** (재사용성 높음). `expire`/`last`/`prev`/`new`/`transition`/`reason` 은 ad-hoc 일반어 허용
- **해소안**:
  - `min`(minimum) 표준 단어 신규 등록 — `min_req_prsr`, 향후 수위·압력 설계치 재사용
  - `req`(require) 표준 단어 신규 등록 — `min_req_prsr`, 요구 제약 명시 재사용
  - `rcv`(receive) 표준 단어 신규 등록 — `last_rcv_dtm`, SCADA 수신 시점 표현 재사용
- **결론**: 표준 단어 신규 등록 3건 추가 (안건 2 의 24건 + 본 안건 3건 = 총 27건). 룰 갱신 지시서에 1건 항목 추가

## 표준 사전 카탈로그

### 신규 표준 단어

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `rated` | 정격 | 신규 | `standard-words.md` 미등록. 형용사 접두어 (`rated_head`, `rated_flwrt`) |
| `head` | 양정 | 신규 | 정수장 전용 물리량. `qty` 와 의미 다름(qty=범용 수량) |
| `flwrt` | 유량 | 신규 | flow rate. OT 센서 `FRI`(SCADA 수신 전용)와 역할 구분 |
| `ppln` | 배관 | 신규 | pipeline. 설비 부위 명사 |
| `prsr` | 압력 | 신규 | pressure. OT 센서 `PRI` 와 역할 구분 |
| `wtlv` | 수위 | 신규 | water level. OT 센서 `LEI` 와 역할 구분 |
| `elpwr` | 전력(순시) | 신규 | electric power, kW |
| `elceg` | 전력량(누적) | 신규 | electric energy gauge, kWh. `elpwr` 와 단위 분리로 동의어 아님 |
| `cmbn` | 조합 | 신규 | combination. `cnt` 와 의미 다름 |
| `predc` | 예측 | 신규 | prediction. 접두어(`predc_elpwr_amt` 등) |
| `rslt` | 결과 | 신규 | result. `opt_result_h` suffix 와 별개 |
| `anls` | 분석 | 신규 | analysis |
| `recomd` | 추천 | 신규 | recommend |
| `div` | 구분 | 신규 | division. `cd`(공통 코드)와 경계 주의 |
| `stng` | 설정 | 신규 | setting |
| `mod` | 모드 | 신규 | mode. `ot-integration.md §5` 운전 모드 관련 |
| `base` | 기준 | 신규 | 형용사·명사 복합 |
| `tnk` | 탱크 | 신규 | tank. 시설 명사. `pwtf` 도메인에 흡수되지만 범용 시설 단어로 별도 등록 |
| `prfwt` | 정수수 | 신규 | purified water. 수처리 공정 전용 |
| `dstrwt` | 배수수 | 신규 | distribution water |
| `oprtng` | 운전중 | 신규 | operating. `drvn`(운전됨) 과 구분 — 상태값 vs 동사 수동형 |
| `drvn` | 운전 | 신규 | driven. `oprtng` 와 구분 |
| `updt` | 갱신 | 신규 | update. `rgstr` 반대 동사. `updt_dtm` 컬럼 사용 |
| `hstry` | 이력 | 신규 | history. 테이블 suffix `_h` 와 별도 컬럼 조합어로 사용 제한 (중복 방지) |
| `min` | 최소 | 신규 (안건 10, 2026-04-25) | minimum. `min_req_prsr` 조합 재료 + 수위·압력 설계치 재사용 |
| `req` | 요구 | 신규 (안건 10, 2026-04-25) | require. `min_req_prsr` 조합 재료 + 요구 제약 명시 재사용 |
| `rcv` | 수신 | 신규 (안건 10, 2026-04-25) | receive. `last_rcv_dtm` 조합 재료 + SCADA 수신 시점 표현 재사용 |
| `reg` | (폐기) | 폐기·통합 | `rgstr` 동의어. `REG_ID` → `rgstr_id` 로 통일 |
| `tnkf` | (폐기) | 폐기·통합 | 복합어. `pwtf` 비즈니스 도메인에 흡수 |
| `cntom` | (폐기) | 폐기·통합 | `om` 의미 불명. `cnt` 단독 사용 |

### 신규 표준 데이터 도메인

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| `DOM_SEQ_BIGINT` | `BIGINT` | `Long` | NOT NULL | 신규 | 시계열 파티션 auto-increment PK. `DOM_ID_50`(VARCHAR) 는 파티션 복합 PK `(id, dtm)` 구성 시 비효율, UUID 는 BRIN 인덱스 페이지 분열 유발 (DBA 2차 승인 완료) |

### 신규 표준 용어

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `pump_id` | `pump` + `id` | `DOM_ID_50` | 신규 | 펌프 외부 할당 PK |
| `pump_nm` | `pump` + `nm` | `DOM_NAME_100` | 신규 | 펌프명 |
| `pwtf_id` | `pwtf` + `id` | `DOM_ID_50` | 신규 | 정수조 PK |
| `pwtf_nm` | `pwtf` + `nm` | `DOM_NAME_100` | 신규 | 정수조명 |
| `dwt_id` | `dwt` + `id` | `DOM_ID_50` | 신규 | 배수지 PK |
| `dwt_nm` | `dwt` + `nm` | `DOM_NAME_100` | 신규 | 배수지명 |
| `rated_head` | `rated` + `head` | `DOM_QTY_15_4` | 신규 | 정격 양정 (m) |
| `rated_flwrt` | `rated` + `flwrt` | `DOM_QTY_15_4` | 신규 | 정격 유량 (m³/h) |
| `min_req_prsr` | `min` + `req` + `prsr` | `DOM_QTY_15_4` | 신규 | 배수지 최소 요구 압력 (kgf/cm²). `min`·`req`·`prsr` 모두 표준 단어 등록 완료 (안건 10, 2026-04-25) |
| `tag_nm` | `tag` + `nm` | `DOM_TAG_NM_50` | 기존 재사용 | `rawdata_m.tag_nm` 과 공통 도메인 — TAG 도메인 정립(2026-04-24) 결과 적용 (안건 9). 길이 VARCHAR(50) · NOT NULL |
| `pump_ctrl_id` | `pump` + `ctrl` + `id` | `DOM_SEQ_BIGINT` | 신규 | 제어 이력 BIGINT PK |
| `ctrl_dtm` | `ctrl`(비즈니스 도메인) + `dtm` | `DOM_DTM` | 신규 | 제어 일시 (파티션 키) |
| `ctrl_div` | `ctrl` + `div` | `DOM_CODE_20` | 신규 | 'MANUAL'/'AUTO' |
| `ctrl_rslt` | `ctrl` + `rslt` | `DOM_CODE_20` | 신규 | 'SUCCESS'/'WAITING'/'FAIL' |
| `updt_dtm` | `updt` + `dtm` | `DOM_DTM` | 신규 | 수정 일시 (BaseEntity 자동 주입) |
| `ai_drvn_mod` | `ai` + `drvn` + `mod` | `DOM_CODE_20` | 신규 | 'AI'/'AI_RECOMD'/'AI_ANLS' |
| `ai_mode_cd` | `ai` + `mod` + `cd` | `DOM_CODE_20` | 신규 | '0'/'1'/'2' (수동/AI자동/반자동). 시스템 상태 |
| `predc_id` | `predc` + `id` | `DOM_SEQ_BIGINT` | 신규 | 예측 결과 BIGINT PK |
| `predc_base_dtm` | `predc` + `base` + `dtm` | `DOM_DTM` | 신규 | 예측 기준 일시 (파티션 키) |
| `predc_dtm` | `predc` + `dtm` | `DOM_DTM` | 신규 | 예측 대상 일시 (기준+1시간) |
| `pump_cmbn_cd` | `pump` + `cmbn` + `cd` | `DOM_CODE_20` | 신규 | 펌프 조합 식별 코드 |
| `predc_elpwr_amt` | `predc` + `elpwr` + `amt` | `DOM_QTY_15_4` | 신규 | 예측 전력 (kW, 순시) — 단위 확정 |
| `predc_flwrt` | `predc` + `flwrt` | `DOM_QTY_15_4` | 신규 | 예측 유량 |
| `predc_prsr` | `predc` + `prsr` | `DOM_QTY_15_4` | 신규 | 예측 압력 |
| `expire_dtm` | expire(일반어) + `dtm` | `DOM_DTM` | 신규 | 수동 제어 만료 시각 |
| `last_rcv_dtm` | last(일반어) + `rcv` + `dtm` | `DOM_DTM` | 신규 | 마지막 SCADA 수신 시각 (장애 측정용). `rcv` 표준 단어 등록 (안건 10, 2026-04-25) |
| `pump_oprtng_cnt` | `pump` + `oprtng` + `cnt` | INTEGER | 신규 | 동시 운전 펌프 대수. `PMP_OPRTNG_CNTOM` 폐기 후 대체 |
| `ctrl_dt` | — | — | 금지 패턴 | `ctrl_dtm` 로 대체 (TIMESTAMP 는 `_dtm`) |
| `updt_dt` | — | — | 금지 패턴 | `updt_dtm` 로 대체 |
| `predc_base_dt` | — | — | 금지 패턴 | `predc_base_dtm` 로 대체 |
| `reg_id` | — | — | 금지 패턴 | `rgstr_id` 로 대체 |
| `pmp_*` | — | — | 금지 패턴 | `pump_*` 로 대체 |
| `pmp_oprtng_cntom` | — | — | 금지 패턴 | `pump_oprtng_cnt` 로 대체 |

## 신규 엔티티/DB 컬럼

본 작업에서 신규 설계하는 엔티티 9개. 비즈니스 도메인 패키지는 `com.mo.swtp.pump` (마스터 4개 + 이력 2개 + 조합 2개) / `com.mo.swtp.ai` (설정 2개) 로 분리.

### 1. `pump_m` — 송수펌프 마스터 (신규)

- **패키지**: `com.mo.swtp.pump.domain`
- **suffix**: `_m` (마스터)
- **컬럼**:
  - `pump_id` VARCHAR(50) PK — `DOM_ID_50`, **외부 할당 PK · Persistable<String> 구현 필수**
  - `pump_nm` VARCHAR(100) — `DOM_NAME_100`
  - `pwtf_id` VARCHAR(50) FK → `pwtf_m.pwtf_id` (소속 정수조)
  - `rated_head` NUMERIC(15,4) — `DOM_QTY_15_4`
  - `rated_flwrt` NUMERIC(15,4) — `DOM_QTY_15_4`
  - `tag_nm` VARCHAR(50) NOT NULL — `DOM_TAG_NM_50` (SCADA 태그, `rawdata_m.tag_nm` 과 공통 도메인 — 안건 9)
  - `use_yn` VARCHAR(1) — `DOM_YN` (YnType enum)
  - BaseEntity 메타 (`rgstr_dtm`, `updt_dtm`, `rgstr_id`, `updt_id`)
- **인덱스**: `pump_m_pwtf_id_idx` (pwtf_id) — 정수조별 조회

### 2. `pwtf_m` — 정수조 마스터 (신규)

- **패키지**: `com.mo.swtp.pump.domain` (잠정 — 독립 로직 생기면 분리)
- **suffix**: `_m`
- **컬럼**:
  - `pwtf_id` VARCHAR(50) PK — `DOM_ID_50`, 외부 할당 PK (Persistable<String>)
  - `pwtf_nm` VARCHAR(100) — `DOM_NAME_100`
  - BaseEntity 메타

### 3. `dwt_m` — 배수지 마스터 (신규)

- **패키지**: `com.mo.swtp.pump.domain` (잠정)
- **suffix**: `_m`
- **컬럼**:
  - `dwt_id` VARCHAR(50) PK — `DOM_ID_50`, 외부 할당 (Persistable<String>)
  - `dwt_nm` VARCHAR(100) — `DOM_NAME_100`
  - `min_req_prsr` NUMERIC(15,4) — `DOM_QTY_15_4` (최소 요구 압력, 분기별 이력은 별도 작업)
  - BaseEntity 메타

### 4. `pump_cmbn_m` — 펌프 조합 헤더 (신규)

- **패키지**: `com.mo.swtp.pump.domain`
- **suffix**: `_m`
- **컬럼**:
  - `pump_cmbn_cd` VARCHAR(50) PK — `DOM_CODE_20` 혹은 `DOM_ID_50` 택일 (길이 20으로 제한 권장)
  - `pwtf_id` VARCHAR(50) FK
  - `pump_cmbn_nm` VARCHAR(100)
  - BaseEntity 메타
- **인덱스**: `pump_cmbn_m_pwtf_id_idx`

### 5. `pump_cmbn_d` — 펌프 조합 상세 (신규, 1:N)

- **패키지**: `com.mo.swtp.pump.domain`
- **suffix**: `_d` (마스터 1:N 구성요소 수직화 — `naming.md` 경계 해석 부합)
- **컬럼**:
  - `pump_cmbn_cd` VARCHAR(50) FK (+ 복합 PK 요소)
  - `pump_id` VARCHAR(50) FK (+ 복합 PK 요소)
  - `ord` INTEGER (순서, 기존 표준 단어)
  - BaseEntity 메타

### 6. `pump_ctrl_h` — 펌프 제어 이력 (신규, 파티션 테이블)

- **패키지**: `com.mo.swtp.pump.domain`
- **suffix**: `_h` (이력)
- **컬럼**:
  - `pump_ctrl_id` BIGINT — `DOM_SEQ_BIGINT` (SEQUENCE 전략, allocationSize 설정)
  - `pump_id` VARCHAR(50) — **논리 참조** (DB FK 없음 — 시계열 파티션 마스터 FK 금지 룰 적용, 안건 8)
  - `ctrl_dtm` TIMESTAMP — `DOM_DTM` (파티션 키)
  - `ctrl_div` VARCHAR(20) — `DOM_CODE_20` ('MANUAL'/'AUTO')
  - `ctrl_rslt` VARCHAR(20) — `DOM_CODE_20` ('SUCCESS'/'WAITING'/'FAIL')
  - `ai_drvn_mod` VARCHAR(20) — `DOM_CODE_20` ('AI'/'AI_RECOMD'/'AI_ANLS')
  - BaseEntity 메타 (`updt_dtm`, `rgstr_id`)
- **복합 PK**: `(pump_ctrl_id, ctrl_dtm)` — 파티션 키 포함 필수
- **파티셔닝**: 월 RANGE(`ctrl_dtm`). 최소 6개월 선행 생성. 보존 2년 → 파티션 DROP (`db-partitioning-and-retention.md §2` 룰 변경 필요)
- **인덱스**: B-Tree `(pump_id, ctrl_dtm DESC)` + BRIN `(ctrl_dtm)` (분당 다수 삽입)
- **JPA 매핑**: `@ManyToOne` 없이 `@Column` 단독 보관. 마스터 존재 검증은 서비스 계층 `existsById` + `@Cacheable("pumpMasterExists")` 5분 TTL (안건 8)

### 7. `pump_predc_h` — 펌프 운전 예측 결과 (신규, 파티션 테이블)

- **패키지**: `com.mo.swtp.pump.domain` (예측은 `ai` 서비스가 생성하지만 데이터 주체는 펌프 제어)
- **suffix**: `_h`
- **컬럼**:
  - `predc_id` BIGINT — `DOM_SEQ_BIGINT` (SEQUENCE)
  - `pwtf_id` VARCHAR(50) — **논리 참조** (DB FK 없음 — 안건 8)
  - `predc_base_dtm` TIMESTAMP (파티션 키)
  - `predc_dtm` TIMESTAMP (기준 + 1시간)
  - `pump_cmbn_cd` VARCHAR(50) — **논리 참조** → `pump_cmbn_m` (DB FK 없음 — 안건 8)
  - `predc_elpwr_amt` NUMERIC(15,4) — `DOM_QTY_15_4` (예측 전력, kW)
  - `predc_flwrt` NUMERIC(15,4)
  - `predc_prsr` NUMERIC(15,4)
  - `ai_drvn_mod` VARCHAR(20)
  - BaseEntity 메타
- **복합 PK**: `(predc_id, predc_base_dtm)`
- **파티셔닝**: 월 RANGE(`predc_base_dtm`). 보존 3년 (`db-partitioning-and-retention.md §2` 해당)
- **인덱스**: B-Tree `(pwtf_id, predc_base_dtm DESC)`. BRIN 불필요 (시간당 수 건)
- **JPA 매핑**: `@ManyToOne` 없이 `@Column` 단독 보관. 마스터 존재 검증은 배치 1회 (`saveAll` 진입 시점, 같은 pwtf_id 묶음) — 안건 8

### 8. `ai_drvn_mod_p` — AI 운전모드 설정 (신규, 정수조 단위 현재 설정)

- **패키지**: `com.mo.swtp.ai.domain`
- **suffix**: `_p` (운전 규칙·설정값)
- **컬럼**:
  - `pwtf_id` VARCHAR(50) PK — `DOM_ID_50` (정수조 1:1, Persistable<String>)
  - `ai_drvn_mod` VARCHAR(20) — `DOM_CODE_20` (사용자 의도: AI/AI_RECOMD/AI_ANLS)
  - `ai_mode_cd` VARCHAR(20) — `DOM_CODE_20` (시스템 상태: 0/1/2)
  - `expire_dtm` TIMESTAMP NULL — 수동 제어 만료 시각 (NULL = 만료 제약 없음)
  - `last_rcv_dtm` TIMESTAMP NULL — 마지막 SCADA 수신 시각 (스케줄러가 5분 초과 판정 기준)
  - BaseEntity 메타
- **변경 주체 분리**: `ai_drvn_mod` 는 사용자 API 만, `ai_mode_cd` 는 스케줄러/장애 대응만 갱신. Service 계층에서 변경 메서드 분리.

### 9. `ai_drvn_mod_h` — 운전모드 전환 이력 (신규, 파티션 테이블)

- **패키지**: `com.mo.swtp.ai.domain`
- **suffix**: `_h`
- **컬럼**:
  - `ai_drvn_mod_h_id` BIGINT — `DOM_SEQ_BIGINT`
  - `pwtf_id` VARCHAR(50) — **논리 참조** (DB FK 없음 — 안건 8)
  - `prev_ai_drvn_mod` VARCHAR(20)
  - `new_ai_drvn_mod` VARCHAR(20)
  - `prev_ai_mode_cd` VARCHAR(20)
  - `new_ai_mode_cd` VARCHAR(20)
  - `transition_reason` VARCHAR(100) — 'USER_SELECT'/'SCADA_TIMEOUT'/'MANUAL_EXPIRE' 등
  - `rgstr_dtm` TIMESTAMP (파티션 키)
  - `rgstr_id` VARCHAR(50)
- **복합 PK**: `(ai_drvn_mod_h_id, rgstr_dtm)`
- **파티셔닝**: 월 RANGE(`rgstr_dtm`). 보존 **5년** → 파티션 DROP (`db-partitioning-and-retention.md §2` 신규 행 추가 필요)
- **인덱스**: B-Tree `(pwtf_id, rgstr_dtm DESC)`
- **JPA 매핑**: `@ManyToOne` 없이 `@Column` 단독 보관. 단건 트랜잭션이라 캐싱 의미 적음 — 호출 시점 1회 `existsById` 검증 (안건 8)

### 10. `pump_interlock_p` — 인터록 규칙 (신규, **최소 stub**)

- **패키지**: `com.mo.swtp.pump.domain`
- **suffix**: `_p`
- **컬럼** (최소 설계만):
  - `pump_interlock_id` VARCHAR(50) PK
  - `pump_id` VARCHAR(50) FK
  - `sensor_tag` VARCHAR(100)
  - `min_val` NUMERIC(15,4) NULL
  - `max_val` NUMERIC(15,4) NULL
  - `use_yn` VARCHAR(1)
  - BaseEntity 메타
- **범위**: 빈 테이블 허용. `InterlockValidator` 는 "규칙 미등록 시 통과, 있으면 평가" 로직. 실제 규칙 데이터 투입은 별도 작업.

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 |
|----------|--------|
| 요구사항 문서의 `PMP_*` 접두어 | `pump_*` 로 전면 재명명 (안건 1 결론) |
| 요구사항 문서의 `_MST`/`_HSTRY`/`_STNG` suffix | `_m`/`_h`/`_p` 로 일괄 변경 (`naming.md §DB 테이블/컬럼 네이밍` 단일 규칙 고수) |
| 요구사항 문서의 TIMESTAMP `_DT` 표기 | 모든 TIMESTAMP 컬럼 `_dtm` 강제 (`standard-words.md` 엄격 구분) |
| 요구사항 문서 `REG_ID` | `rgstr_id` 로 통일 (기존 `rgstr` 표준 단어 동의어 차단) |
| 요구사항 문서 `PMP_OPRTNG_CNTOM` | `pump_oprtng_cnt` 로 수정 (`cntom` 폐기) |
| 요구사항 문서 `PRFWT_TNKF`, `DSTRWT_TNK` 긴 복합어 | `pwtf`, `dwt` 로 4자 내 재결정 (패키지명 가독성) |
| 요구사항 문서 `CTRL_DIV`/`CTRL_RSLT` 한글 코드값 | `MANUAL`/`AUTO`, `SUCCESS`/`WAITING`/`FAIL` 영문 enum 강제 (DBA 권고, Flyway 인코딩 안정성) |
| 요구사항 문서 `PMP_CMBN_CD` 단일 문자열 저장 | `pump_cmbn_m` + `pump_cmbn_d` 1:N 정규화 (콤마 구분 배제) |
| AI 운전모드 이중 체계 혼재 (`AI_DRVN_MOD` vs `AI_MODE`) | 계층 분리: `ai_drvn_mod` = 사용자 의도 · `ai_mode_cd` = 시스템 상태. 정수조 단위 `ai_drvn_mod_p` 에 병존 |
| `ot-integration.md §2` 인터록 선행조건 누락 | `pump_interlock_p` stub + `InterlockValidator`(규칙 없으면 통과) 최소 구현 |
| `ot-integration.md §5` SCADA 강제 전환 누락 | `last_rcv_dtm` 스케줄러 측정 + `ai_mode_cd` 갱신. heartbeat 직접 구현 불필요 |
| `ot-integration.md §2` 아웃바운드 어댑터 미구현 | 본 작업에서 `com.mo.swtp.scada.outbound` + `ScadaOutboundPort` 인터페이스 + `@Profile` 분기 구현 |
| `db-partitioning-and-retention.md §2` 제어 로그 `DELETE + VACUUM` 표기 | 파티션 DROP 으로 변경 권고 (룰 업데이트) |
| `db-partitioning-and-retention.md §2` `ai_drvn_mod_h` 미기재 | 5년 보존, 파티션 DROP 행 신규 추가 |
| `db-partitioning-and-retention.md §1` 시계열 파티션 마스터 FK 금지 원칙 (커밋 53d8dc8, 2026-04-24) | 3개 파티션(`pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_h`) FK 4건 전면 제거. 애플리케이션 레벨 검증 대체 (`existsById` + `@Cacheable` 또는 배치 1회 검증). JPA 는 `@ManyToOne` 없이 컬럼 only — 안건 8 |
| `dict/standard-data-domains.md` `DOM_TAG_NM_50` 신규 등록 (커밋 53d8dc8, 2026-04-24) | `pump_m.tag_nm` VARCHAR(100) `DOM_NAME_100` NULL 허용 → VARCHAR(50) NOT NULL `DOM_TAG_NM_50` 교정. `rawdata_m.tag_nm` 과 공통 도메인 — 안건 9 |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

- **엔티티 10건 신규**: `pump_m` · `pwtf_m` · `dwt_m` · `pump_cmbn_m` · `pump_cmbn_d` · `pump_ctrl_h`(파티션) · `pump_predc_h`(파티션) · `ai_drvn_mod_p` · `ai_drvn_mod_h`(파티션) · `pump_interlock_p`(stub)
- **외부 할당 PK 엔티티 6건**: `pump_m`, `pwtf_m`, `dwt_m`, `pump_cmbn_m`, `ai_drvn_mod_p`, `pump_interlock_p` → `Persistable<String>` 구현 필수
- **시계열 파티션 엔티티 3건**: `pump_ctrl_h`, `pump_predc_h`, `ai_drvn_mod_h` → 복합 PK + 월 RANGE + 선행 생성 스케줄러

### DB 설계 변경 초안

- PostgreSQL 파티션 테이블 생성 (3개) + 6개월 선행 파티션 + 월 1일 자동 생성 배치
- 인덱스 생성: B-Tree 3건 + BRIN 1건 (`pump_ctrl_h.ctrl_dtm`)
- `pump_cmbn` 1:N 정규화 (콤마 구분 문자열 배제)
- Flyway `V{n}__{지자체}_seed.sql` 도입 여부는 PLAN 단계 확정 대상
- **시계열 파티션 3개 테이블 FK 미생성** — DDL `FOREIGN KEY` 절 작성 금지. 마스터 간 / `_d` / `_p` FK 만 생성 (안건 8)
- **`pump_m.tag_nm` 컬럼 정의**: `VARCHAR(50) NOT NULL` (`DOM_TAG_NM_50`) — `rawdata_m.tag_nm` 과 공통 도메인 (안건 9)

### 적용할 패턴

- **계층** (3-Service 오케스트레이터):
  - `PumpControlController` (3개 엔드포인트)
  - `PumpControlService` (오케스트레이터)
  - `AiPredictionService` (HTTP RestClient + CircuitBreaker + Retry)
  - `ScadaControlService` (인터페이스 `ScadaOutboundPort` + `@Profile` 분기 구현)
  - `InterlockValidator` (규칙 미등록 시 통과)
  - `ModeTransitionScheduler` (Spring Batch, SCADA 5분 초과 감지 + 만료 복귀)
- **패키지** (3개):
  - `com.mo.swtp.pump` — 마스터·이력·조합·인터록 엔티티 + Controller/Service
  - `com.mo.swtp.ai` — AI 운전모드 설정·이력 + AiPredictionService
  - `com.mo.swtp.scada.outbound` — ScadaOutboundPort 인터페이스 + Modbus/OPC-UA 어댑터
- **Python AI 연동**: HTTP REST (`RestClient`). `@CircuitBreaker(name="aiPrediction")` + `@Retry`. baseUrl 은 `resources-env/{지자체}/application.yml` 주입
- **엔티티 패턴**: 외부 할당 PK → `Persistable<String>`. 시계열 auto-increment → `GenerationType.SEQUENCE` + allocationSize. 여부 필드는 `YnType` enum + `@Enumerated(EnumType.STRING)`
- **파티션 엔티티 마스터 참조** (안건 8): `@ManyToOne` 매핑 **금지**, `@Column` 단독 보관. 조회 join 은 Querydsl `.eq()` 명시 표현. 마스터 존재 검증은 서비스 계층 — `pump_ctrl_h` 는 `@Cacheable("pumpMasterExists")` + 5분 TTL, `pump_predc_h` 는 `saveAll` 배치 1회 검증, `ai_drvn_mod_h` 는 단건 호출 시 `existsById`
- **예외·에러 코드**: `PumpErrorCode implements ErrorCode`, `AiErrorCode`, `ScadaErrorCode` 도메인별 분리. `httpStatus(int)` 필드만 허용 (`exception-patterns.md`)
- **멀티테넌트**: AI 서버 URL · PLC 엔드포인트 · SCADA 타임아웃 값은 `resources-env/{지자체}/application.yml`. 마스터 시드 (정수조·배수지·펌프) 도입 방식(Flyway 콜백 vs data.sql) 은 PLAN 확정

### 본 작업 범위 경계

- **포함**: 엔티티 10건 · 3개 API · 3-Service · HTTP REST Python 연동 · OT 아웃바운드 어댑터 최초 구현 · 인터록 stub · SCADA 장애 강제 전환 스케줄러 · `ai_drvn_mod_h` 전환 이력 · 파티션 선행 생성 배치
- **분리 (별도 작업 `ot_integration_inbound`)**: 센서 품질 관리 파이프라인 · 알람 4단계 체계(`alarm_h`) · 실제 인터록 규칙 데이터 투입
- **분리 (별도 작업 `dwt_pressure_history`)**: 배수지 요구 압력 분기별 이력 테이블(`dwt_prsr_setn_h`)

## 룰 갱신 지시서

PLAN approved 의 전제조건. 본 작업 착수 전 모든 체크박스를 완료해야 한다.

- [x] `.claude/rules/domain-abbreviations.md` — `pump` 마스터 도메인 섹션으로 승격 (도입 예정 → 마스터, `com.mo.swtp.pump` 패키지 생성 전제)
- [x] `.claude/rules/domain-abbreviations.md` — `ctrl` 마스터 도메인 섹션으로 승격 (도입 예정 → 마스터, `pump_ctrl_h` 테이블 도입 전제)
- [x] `.claude/rules/domain-abbreviations.md` — `pwtf`(정수조, purified water tank facility) 도입 예정 섹션에 신규 등록
- [x] `.claude/rules/domain-abbreviations.md` — `dwt`(배수지, distribution water tank) 도입 예정 섹션에 신규 등록
- [x] `.claude/rules/domain-abbreviations.md` — `ai`(AI 운전모드·예측 서비스) 도입 예정 섹션에 신규 등록, 비고란에 `opt` 와의 경계 명시 (`opt`=최적화 결과 저장 테이블 prefix 전용, `ai`=운전모드·예측 서비스 상위 계층)
- [x] `.claude/rules/domain-abbreviations.md` — `pmp` 폐기 이력 (사유: `pump` 동의어 통합), `reg` 폐기 이력(사유: `rgstr` 동의어), `drvn_anls`/`predc`/`scada` 비즈니스 도메인 약어 등록 거부 사유 기록
- [x] `.claude/rules/dict/standard-words.md` — `rated`, `head`, `flwrt`, `ppln`, `prsr`, `wtlv`, `elpwr`, `elceg`, `cmbn`, `predc`, `rslt`, `anls`, `recomd`, `div`, `stng`, `mod`, `base`, `tnk`, `prfwt`, `dstrwt`, `oprtng`, `drvn`, `updt`, `hstry`, `min`, `req`, `rcv` 신규 등록 (27건 — 안건 2 의 24건 + 안건 10 의 `min`/`req`/`rcv` 3건)
- [x] `.claude/rules/dict/standard-words.md` — 폐기 이력에 `cntom`(의미 불명 — `cnt` 로 대체), `tnkf`(복합어 — `pwtf` 로 흡수) 기록
- [x] `.claude/rules/dict/standard-data-domains.md` — `DOM_SEQ_BIGINT`(BIGINT, Long, NOT NULL) 신규 등록 (DBA 승인 완료 — 시계열 파티션 PK 용도)
- [x] `.claude/rules/dict/standard-terms.md` — 신규 용어 등록: `pump_id`, `pump_nm`, `pwtf_id`, `pwtf_nm`, `dwt_id`, `dwt_nm`, `rated_head`, `rated_flwrt`, `min_req_prsr`, `pump_ctrl_id`, `ctrl_dtm`, `ctrl_div`, `ctrl_rslt`, `updt_dtm`, `ai_drvn_mod`, `ai_mode_cd`, `predc_id`, `predc_base_dtm`, `predc_dtm`, `pump_cmbn_cd`, `predc_elpwr_amt`, `predc_flwrt`, `predc_prsr`, `expire_dtm`, `last_rcv_dtm`, `pump_oprtng_cnt`
- [x] `.claude/rules/dict/standard-terms.md` — 동의어·금지 패턴 표에 신규 금지 패턴 추가: `ctrl_dt`(→`ctrl_dtm`), `updt_dt`(→`updt_dtm`), `predc_base_dt`(→`predc_base_dtm`), `predc_dt`(→`predc_dtm`), `stng_dt`(→`stng_dtm`), `reg_id`(→`rgstr_id`), `pmp_*`(→`pump_*`), `pmp_oprtng_cntom`(→`pump_oprtng_cnt`)
- [x] `.claude/rules/ot-integration.md` — §2 인터록 선행조건 의무 체크 절에 "`pump_interlock_p` 규칙 미등록 시 통과(빈 테이블 허용)" 주석 추가
- [x] `.claude/rules/ot-integration.md` — §2 아웃바운드 어댑터 구현 상태 "신규 도입"으로 갱신 (pumpcontrol 작업에서 `ScadaOutboundPort` 인터페이스 + `@Profile` 분기 도입)
- [x] `.claude/rules/ot-integration.md` — §5 장애 시 동작 기준 절에 `AI_DRVN_MOD` 불변 정책(사용자 의도 유지) · `AI_MODE` 강제 전환 정책 구분 명시
- [x] `.claude/rules/ot-integration.md` — §5 아웃바운드 실패 항목에 `ai_drvn_mod_h` 전환 이력 기록 의무 추가
- [x] `.claude/rules/db-partitioning-and-retention.md` — §2 보존 정책 표에 `ai_drvn_mod_h`(운전모드 전환 이력) 행 추가 — 5년 보존, 파티션 DROP
- [x] `.claude/rules/db-partitioning-and-retention.md` — §2 제어 로그(2년) 삭제 방식 `DELETE + VACUUM` → `파티션 DROP` 으로 변경 (`pump_ctrl_h` 월 RANGE 파티셔닝 적용에 따른 수정)

## 산출물

- [계획안](../../../plan/20260422/pumpcontrol/PLAN1.md)
