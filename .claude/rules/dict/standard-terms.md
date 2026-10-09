# 표준 용어 사전 (Standard Terms)

표준 단어 + 표준 데이터 도메인 조합으로 확정된 **DB 컬럼명** 사전이다.
DB 표준화 이론의 "표준 용어(Standard Term)" 에 해당한다.

> **SSOT 분리**: 단어·데이터 도메인·비즈니스 도메인 약어는 ROOT 어휘 사전 (`swtp/.claude/rules/dict/`) 이 SSOT — 본 파일은 그 조합 결과인 DB 컬럼명만 SSOT 다.

---

## 사용 규칙

- **모든 컬럼을 등록하지 않는다** — 신규 설계·충돌·재사용 판단이 필요한 용어만 선별 등록한다
- 물리명은 스네이크케이스 (`user_id`, `rgstr_dtm`)
- 하나의 용어는 **단 하나의 의미** 만 가진다 (예: `userId` 와 `memberId` 동시 사용 금지)
- 신규 등록은 `/dev:analyze` 에서 `wtp-glossary-manager` 검토를 거친다
- 조합 근거는 ROOT 어휘 사전 — `swtp/.claude/rules/dict/standard-words.md` · `swtp/.claude/rules/dict/domain-abbreviations.md` · `swtp/.claude/rules/dict/standard-data-domains.md` 를 참조해 기록한다

---

## 용어 표

| 물리명 | 한글 논리명 | 조합 | 데이터 도메인 | 사용 테이블 | 비고 |
|--------|-----------|------|-------------|-----------|------|
| `user_id` | 사용자 ID | `user`(비즈니스 도메인) + `id` | `DOM_ID_50` | `user_m` | 외부 할당 PK — `Persistable<String>` 구현 필수 |
| `use_yn` | 사용 여부 | `use` + `yn` | `DOM_YN` | `user_m`, `pump_m`, `pump_interlock_p`, `tag_m`, `proc_m`(예정) | 비활성 운영 시나리오가 존재하는 `_m` 테이블에 선별 보유 — 전체 `_m` 의무 아님. 신규 마스터 도입 시 ANALYZE 에서 판단. `tag_m` 추가 — 논리 삭제 목적 (rawdata_1m_h 가 `tag_srl_no` 를 논리 참조하므로 물리 삭제 금지, 태그관리 ANALYZE1, 2026-05-08). `proc_m`(예정) 추가 — 공정/제어대상 카테고리 비활성화 시나리오 대응 (송수펌프제어분석-2번섹션 ANALYZE1, 2026-05-20). DDL 정책: [db/indexing-and-migration.md §3 `DOM_YN` DDL 정책](../db/indexing-and-migration.md) (use_yn_consistency ANALYZE1, 2026-04-25 갱신) |
| `rgstr_dtm` | 등록 일시 | `rgstr` + `dtm` | `DOM_DTM` | 공통 메타 | `BaseEntity` 공통 메타 필드 |
| `tag_val` | 태그 측정값 | `tag` + `val` | `DOM_QTY_15_4` | `rawdata_m` | SCADA 수집값 |
| `tag_nm` | 태그 식별명 | `tag`(비즈니스 도메인) + `nm` | `DOM_TAG_NM_50` | `rawdata_m`, `tag_m` | SCADA 계측값 식별 단위 — `706-FRI-xxx-xxx` 등. `tag_m` 코드 도입 완료 (태그관리 ANALYZE1, 2026-05-08 — 예정 마커 제거). `pump_m.tag_nm` (송수펌프제어분석 PLAN1, 2026-05-08 도입) 은 시설물응답DTO명세 ANALYZE1 (2026-05-12) 에서 운영 사용 전 폐기 — 양방향 중복 (`tag_m.instrument_id` FK SSOT 위반). 본 사이클 이전부터 사용 테이블 목록에 `pump_m` 미등록 상태였음 (송수펌프제어분석 PLAN1 룰 갱신 누락) — 본 사이클 코드 폐기로 자연 해소 |
| `tag_se_cd` | 태그 유형 코드 | `tag`(비즈니스 도메인) + `se` + `cd` | `DOM_CODE_20` (재사용) | `tag_m` | `TagMeasurementType` enum 매핑 (FRI·PRI·LEI·PWI·RMS·OPS·VOI·FQI·PWQ·CMD·FRQ 11종) — `@Enumerated(EnumType.STRING)`. `tag_m` 코드 도입 완료 (태그관리 ANALYZE1, 2026-05-08 — 예정 마커 제거). FQI 행 추가 (주파수, Hz — 인버터 펌프 운전 주파수 측정 유형) — 주파수측정유형등록 ANALYZE1 안건 1 (2026-05-20). PWQ 행 추가 (적산전력량, kWh — 송수펌프 전력량 시계열 산정용 1분 수집 누적 미터값. PWI(순시전력, kW)와 단위·물리 성격 분리) — 송수펌프가동이력_3번섹션 ANALYZE1 안건 1 (2026-06-02). CMD 행 추가 (운전제어, 단위 없음 — `io_cd='OUTPUT'` 펌프 제어 태그 식별용. 가동상태 OPS(INPUT)와 별개 제어 태그. enum 의미 "측정유형→신호유형(Signal Type)" 재해석·javadoc 갱신) — 제어이력 재도입 ANALYZE1 안건 5 (2026-06-04). FRQ 행 추가 (유량적산, m³ — 배수지 유입·유출 유량계의 누적 적산 유량. FRI 설명 '유량'→'유량순시' 변경, FRI(순시 m³/h)와 단위·물리 성격 분리·PWI/PWQ 쌍 동형). **⚠️ 사용자 직접 결정 도입 — `/dev:analyze` 미경유** (FQI/PWQ/CMD 선례와 차이, 계측기 태그 시딩 2026-06-10) |
| `tag_desc` | 태그 설명 | `tag`(비즈니스 도메인) + `desc` | `DOM_TEXT` (재사용) | `tag_m` | 자유형 설명. `@Size(max=200)` 애플리케이션 검증. `tag_m` 코드 도입 완료 (태그관리 ANALYZE1, 2026-05-08 — 예정 마커 제거) |
| `pump_id` | 펌프 ID | `pump`(비즈니스 도메인) + `id` | `DOM_ID_50` | `pump_m`, `pump_cmbn_d`, `pump_interlock_p`, `pump_ctrl_h`(논리 참조) | 외부 할당 PK — `Persistable<String>` 필수 (pumpcontrol ANALYZE1) |
| `pump_nm` | 펌프명 | `pump` + `nm` | `DOM_NAME_100` | `pump_m` | 펌프 명칭 |
| `pwtf_id` | 정수조 ID | `pwtf`(비즈니스 도메인) + `id` | `DOM_ID_50` | `pwtf_m`, `pump_m`, `pump_cmbn_m`, `dwt_m`(예정), `ai_drvn_mod_p`, `pump_predc_h`(논리 참조), `ai_drvn_mod_h`(논리 참조) | 외부 할당 PK — `Persistable<String>` 필수 |
| `pwtf_nm` | 정수조명 | `pwtf` + `nm` | `DOM_NAME_100` | `pwtf_m` | 정수조 명칭 |
| `dwt_id` | 배수지 ID | `dwt`(비즈니스 도메인) + `id` | `DOM_ID_50` | `dwt_m` | 외부 할당 PK — `Persistable<String>` 필수 |
| `dwt_nm` | 배수지명 | `dwt` + `nm` | `DOM_NAME_100` | `dwt_m` | 배수지 명칭 |
| `rated_head` | 정격 양정 | `rated` + `head` | `DOM_QTY_15_4` | `pump_m` | 펌프 정격 양정 (m). NOT NULL 정책 — 제조사 명판값 항상 존재, AI 예측 모델 정규화 인자 (pumpcontrol_null_alignment ANALYZE1, 2026-04-25) |
| `rated_flwrt` | 정격 유량 | `rated` + `flwrt` | `DOM_QTY_15_4` | `pump_m` | 펌프 정격 유량 (m³/h). NOT NULL 정책 — 동일 사유 (pumpcontrol_null_alignment ANALYZE1, 2026-04-25) |
| `min_req_prsr` | 최소 요구 압력 | `min` + `req` + `prsr` | `DOM_QTY_15_4` | `dwt_m` | 배수지 최소 요구 압력 (kgf/cm²). 분기별 이력은 별도 작업. NOT NULL 정책 — DOM_QTY_15_4 기본 NULL 정책에서 더 엄격하게 적용 (인터록 평가 NULL/미입력 구별 불가 방지, pumpcontrol_null_alignment ANALYZE1, 2026-04-25) |
| `min_req_branch_prsr` | 분기점 최소 요구 압력 | `min` + `req` + `branch`(신규) + `prsr` | `DOM_QTY_15_4` (재사용) | `dwt_m` | 배수지로 분기되는 관로 분기점 지점의 최소 요구 압력 (kgf/cm²). `min_req_prsr`(배수지 본체)와 측정 지점 다른 독립 컬럼 — 의미 중복 없음. 어순은 기존 `min_req_prsr` 수식어 패턴 일관성 유지. NOT NULL 정책 — `min_req_prsr` 선례 동일 (인터록 평가 NULL/미입력 구별 불가 방지, 사이클 2 인터록 임계값 편입 가능성 대비). 본 사이클은 표출 전용 (송수펌프제어분석-5번섹션 ANALYZE1, 2026-05-14) |
| `pump_ctrl_id` | 펌프 제어 이력 ID | `pump` + `ctrl`(테이블 역사 흔적, 약어 폐기 2026-05-20) + `id` | `DOM_SEQ_BIGINT` | `pump_ctrl_h` | 시계열 BIGINT PK — `GenerationType.SEQUENCE` allocationSize=100. **폐기 후 재도입** (백지화 2026-05-12 → 제어이력 재도입 ANALYZE1, 2026-06-04). `com.mo.swtp.instrument` `PumpCtrlHistory` 엔티티, 복합 PK `(pump_ctrl_id, ctrl_dtm)`. 물리명 유지(최대 활용) |
| `ctrl_dtm` | 제어 일시 | `ctrl`(테이블 역사 흔적) + `dtm` | `DOM_DTM` | `pump_ctrl_h` | 파티션 키 (월 RANGE) — 제어요청시간 표출. **폐기 후 재도입** (제어이력 재도입 ANALYZE1, 2026-06-04) |
| `ctrl_div` | 제어 구분 | `ctrl`(역사 흔적) + `div` | `DOM_CODE_20` | `pump_ctrl_h` | **폐기 후 재도입 (코드값 재정의)** — 구 'MANUAL'/'AUTO' → 신 'START'/'STOP' (`ControlCommand` enum, 가동/중지). 0행이라 실데이터 영향 없음 (제어이력 재도입 ANALYZE1, 2026-06-04) |
| `ctrl_rslt` | 제어 결과 | `ctrl`(역사 흔적) + `rslt` | `DOM_CODE_20` | `pump_ctrl_h` | **폐기 후 재도입 (코드값 재정의)** — 구 'SUCCESS'/'WAITING'/'FAIL' → 신 'COMPLETED'/'CANCELLED' (`ControlResult` enum, 제어완료/제어취소) (제어이력 재도입 ANALYZE1, 2026-06-04) |
| `updt_dtm` | 수정 일시 | `updt` + `dtm` | `DOM_DTM` | 공통 메타 | `BaseEntity` 자동 주입 — `rgstr_dtm` 의 짝 |
| `ai_drvn_mod` | AI 운전 모드 | `ai`(비즈니스 도메인) + `drvn` + `mod` | `DOM_CODE_20` | `pump_ctrl_h` | 사용자 의도 — 'AI' / 'AI_RECOMD' / 'AI_ANLS', **NULL 허용=수동 제어** (DBA 2차 승인, DDL COMMENT 명기 의무). **폐기 후 재도입** (제어이력 재도입 ANALYZE1, 2026-06-04) — `pump_ctrl_h` 전용, `AiDrvnModeCode` enum 재사용. proc 도메인 `ai_drvn_mod_cd`(`ai_drvn_mod_p`/`_h`, 별개 테이블·`_cd` suffix)와 물리명 혼동 주의 |
| `ai_mode_cd` | AI 시스템 상태 코드 | `ai` + `mod` + `cd` | `DOM_CODE_20` | `ai_drvn_mod_p`, `ai_drvn_mod_h` | 시스템 상태 — '0'(수동) / '1'(AI자동) / '2'(반자동). 사용자 의도(`ai_drvn_mod`) 와 분리 |
| `predc_id` | 예측 결과 ID | `predc` + `id` | `DOM_SEQ_BIGINT` | `predc_1m_h` | 폐기 후 재등록 — 구 `pump_predc_h` PK 의미 소멸(2026-05-12 백지화). 신규 의미: `predc_1m_h` 시계열 BIGINT PK (`GenerationType.SEQUENCE` allocationSize=100) (송수펌프제어분석-7번섹션 ANALYZE1 안건 2, 2026-05-18) |
| `predc_base_dtm` | 예측 기준 일시 | `predc` + `base` + `dtm` | `DOM_DTM` | `pump_predc_h` | 파티션 키 |
| `predc_dtm` | 예측 대상 일시 | `predc` + `dtm` | `DOM_DTM` | `predc_1m_h` | 폐기 후 재등록 — 구 의미(기준+1시간 오프셋 예측 대상 일시) 소멸. 신규 의미: `predc_1m_h` 단일 예측 대상 시각, 월 RANGE 파티션 키 (송수펌프제어분석-7번섹션 ANALYZE1 안건 2, 2026-05-18) |
| `predc_val` | 예측 측정값 | `predc` + `val`(신규) | `DOM_QTY_15_4` | `predc_1m_h` | 신규 — AI 예측 측정값. `raw_val`·`corr_val` 패턴 동형. NULL 허용. 의미 중복 없음 (송수펌프제어분석-7번섹션 ANALYZE1 안건 2, 2026-05-18) |
| `pump_cmbn_cd` | 펌프 조합 코드 | `pump` + `cmbn` + `cd` | `DOM_CODE_20` | `pump_cmbn_m` (PK), `pump_cmbn_d` (FK), `pump_predc_h` (논리 참조) | 펌프 조합 식별 — 콤마 구분 문자열 배제, 1:N 정규화 결과 |
| `predc_elpwr_amt` | 예측 전력 | `predc` + `elpwr` + `amt` | `DOM_QTY_15_4` | `pump_predc_h` | 예측 전력 (kW, 순시) |
| `predc_flwrt` | 예측 유량 | `predc` + `flwrt` | `DOM_QTY_15_4` | `pump_predc_h` | 예측 유량 (m³/h) |
| `predc_prsr` | 예측 압력 | `predc` + `prsr` | `DOM_QTY_15_4` | `pump_predc_h` | 예측 압력 (kgf/cm²) |
| `expire_dtm` | 만료 일시 | expire(일반어) + `dtm` | `DOM_DTM` | `ai_drvn_mod_p` | 수동 제어 만료 시각 (NULL = 만료 제약 없음) |
| `last_rcv_dtm` | 마지막 수신 일시 | last(일반어) + `rcv` + `dtm` | `DOM_DTM` | `ai_drvn_mod_p` | 마지막 SCADA 수신 시각 — 5분 초과 시 강제 모드 전환 판정 기준 |
| `pump_oprtng_cnt` | 운전중 펌프 대수 | `pump` + `oprtng` + `cnt` | `INTEGER` | `pump_predc_h` 등 | 동시 운전 펌프 대수. `PMP_OPRTNG_CNTOM` 폐기 후 대체 |
| `facility_id` | 시설 ID | `facility`(비즈니스 도메인) + `id` | `DOM_ID_36` | `facility_m`(예정) + 자식 9종 | UUID 자동 생성 PK (`@GeneratedValue(GenerationType.UUID)`) — `Persistable` 미구현. 마스터도메인설계 ANALYZE1 Round 3 사용자 결정 (2026-05-03) |
| `facility_nm` | 시설명 | `facility` + `nm` | `DOM_NAME_100` | `facility_m`(예정) | **UNIQUE 인덱스 (시스템 전체)** — 사용자 결정 핵심 (마스터도메인설계 ANALYZE1 Round 3) |
| `facility_type_cd` | 시설 유형 코드 | `facility` + `type` + `cd` | `DOM_CODE_20` | `facility_m`(예정) | DiscriminatorColumn 12종 (PWTF·DWT·RSV·POINT·PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR) — JPA `@Inheritance(JOINED)` 매핑 키, `FacilityType` enum SSOT. 시설 그룹은 `FacilityGroup` enum 파생 속성 (STORAGE=PWTF·DWT·RSV / OPERATION=PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR / NETWORK=POINT) — DB 컬럼 미신설, DTO `facilityGroupCd` 계산 노출. WTBLD(송수동)·CHMB(약품동)·ACFB(활성탄여과지)·POZB(전오존동)·FLTB(여과지동)·DEWB(탈수기동)·SOLAR(태양광) 신규 7종은 enum 코드값으로만 관리 (시설_도메인_확장 ANALYZE1, 2026-06-08) |
| `parent_facility_id` | 상위 시설 ID | `parent` + `facility` + `id` | `DOM_ID_36` | `facility_m`(예정) | self-FK NULL 허용. 재귀 깊이 제한 미정 (가정 섹션) — 인터록 평가 쿼리는 self-FK 비순회 |
| `disp_ord` | 표시 순서 | `disp` + `ord` | `INTEGER` | `facility_m`·`instrument_m`(예정)·`menu_m`(예정) | 화면 표시 순서. 마스터도메인설계 ANALYZE1 Round 3 사용자 결정 (2026-05-03) — `sort` 신규 등록 vs `ord` 단독 vs `disp_ord` 중 채택. 권한메뉴 ANALYZE1 (2026-05-06) `menu_m` 추가 |
| `main_yn` | 주요 시설 여부 | `main` + `yn` | `DOM_YN` | `facility_m`(예정) | 시설 주요시설여부. `YnType` enum + `@Enumerated(EnumType.STRING)`. 마스터도메인설계 ANALYZE1 Round 3 사용자 결정 |
| `instrument_id` | 계측기 ID | `instrument`(비즈니스 도메인) + `id` | `DOM_ID_36` | `instrument_m`(예정) + 자식 6종 | UUID 자동 생성 PK. 마스터도메인설계 ANALYZE1 Round 3 사용자 결정 |
| `instrument_nm` | 계측기명 | `instrument` + `nm` | `DOM_NAME_100` | `instrument_m`(예정) | **UNIQUE 인덱스** — 범위 (시스템 전체 vs `(facility_id, instrument_nm)` 복합) PLAN 단계 결정 (가정 섹션) |
| `equip_type_cd` | 장비 유형 코드 | `equip` + `type` + `cd` | `DOM_CODE_20` | `instrument_m`(예정) | DiscriminatorColumn (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR) |
| `tag_srl_no` | 태그 시리얼번호 | `tag`(비즈니스 도메인) + `srl` + `no` | `DOM_TAG_SRL_NO_50` | `tag_m`, `rawdata_1m_h`(예정, 논리 참조) | **자연키 PK** (외부 할당, `Persistable<String>` 필수) — 706-FRI-xxx-xxx 형식. 마스터도메인설계 ANALYZE1 Round 3 사용자 결정 (`tag_id` 폐기 후 대체). `tag_m` 코드 도입 완료 (태그관리 ANALYZE1, 2026-05-08 — 예정 마커 제거) |
| `io_cd` | 입출력 코드 | `io` + `cd` | `DOM_CODE_20` | `tag_m` | 태그 입출력 구분 코드 — `IoCode` enum (INPUT/OUTPUT/BIDIR) 채택. `io_yn` (단순 boolean) 대안 폐기 (마스터도메인설계 ANALYZE1 Round 3 + 태그관리 ANALYZE1, 2026-05-08 확정) |
| `raw_val` | 원본 측정값 | `raw`(단어) + `val` | `DOM_QTY_15_4` | `rawdata_1m_h`(예정) | SCADA 원본 측정값. 마스터도메인설계 ANALYZE1 Round 3 — `tag_val` 분리 |
| `corr_val` | 보정 측정값 | `corr` + `val` | `DOM_QTY_15_4` | `rawdata_1m_h`(예정) | 보정/수정값 NULL 허용 — Hold Last Value 적용 결과 또는 운영자 보정. 마스터도메인설계 ANALYZE1 Round 3 추가 |
| `rawdata_id` | 로우데이터 ID | `rawdata` 풀네임 + `id` (또는 `raw` + `id`) | `DOM_SEQ_BIGINT` | `rawdata_1m_h`(예정) | 시계열 BIGINT PK — `GenerationType.SEQUENCE` + `allocationSize=100` |
| `quality_cd` | 품질 코드 | `quality` + `cd` | `DOM_CODE_20` | `rawdata_1m_h`(예정) | SCADA QUALITY 코드 (GOOD/BAD/UNCERTAIN) — `ot-integration.md §3` |
| `menu_id` | 메뉴 ID | `menu`(비즈니스 도메인) + `id` | `DOM_ID_36` | `menu_m`(예정) | UUID 자동 생성 PK — `Persistable` 미구현. `facility_id`·`instrument_id` 선례 동일 (권한메뉴 ANALYZE1, 2026-05-06) |
| `menu_nm` | 메뉴명 | `menu` + `nm` | `DOM_NAME_100` | `menu_m`(예정) | **시스템 전체 UNIQUE 인덱스** — `facility_nm`·`instrument_nm` 선례 동일 패턴 (권한메뉴 ANALYZE1, 2026-05-06) |
| `menu_url` | 메뉴 URL | `menu` + `url` | (`VARCHAR(255)`, DOM 미지정) | `menu_m`(예정) | NULL 허용 (부모 그룹 메뉴 URL 없음). `DOM_URL_200` REJECT 결과로 도메인 미지정 (권한메뉴 ANALYZE1 안건 2, 2026-05-06) |
| `menu_desc` | 메뉴 설명 | `menu` + `desc` | `DOM_TEXT` | `menu_m`(예정) | NULL 허용. `tag_desc` 선례 동일 패턴 (권한메뉴 ANALYZE1, 2026-05-06) |
| `parent_menu_id` | 상위 메뉴 ID | `parent` + `menu` + `id` | `DOM_ID_36` | `menu_m`(예정) | self-FK NULL 허용. 깊이 N=3 애플리케이션 검증. `parent_facility_id` 선례 동일 패턴 (권한메뉴 ANALYZE1, 2026-05-06) |
| `user_role` | 사용자 권한 | `user`(비즈니스 도메인) + `role` | `DOM_CODE_20` | `user_m`, `menu_role_r`(예정) | `UserRole` enum 매핑 (ADMIN/USER) — `@Enumerated(EnumType.STRING)`. `menu_role_r` 매핑 테이블 컬럼 재사용 (권한메뉴 ANALYZE1+2, 2026-05-06) |
| `dwld_id` | 다운로드 ID | `dwld`(신규) + `id` | `DOM_SEQ_BIGINT` | `drvn_anls_dwld_h`(예정) | 시계열 BIGINT PK — `GenerationType.SEQUENCE` + `allocationSize`. `pump_ctrl_id` 패턴 재사용 (송수펌프제어_운전현황분석 ANALYZE1, 2026-05-07) |
| `dwld_file_nm` | 다운로드 파일명 | `dwld`(신규) + `file`(일반어) + `nm` | `DOM_NAME_100` | `drvn_anls_dwld_h`(예정) | 다운로드 파일명. `file` 은 표준 단어 미등록 (1건 사용 시점 등록 부담 회피) (송수펌프제어_운전현황분석 ANALYZE1, 2026-05-07) |
| `dwld_format_cd` | 다운로드 파일 형식 코드 | `dwld`(신규) + `format`(신규) + `cd` | `DOM_CODE_20` | `drvn_anls_dwld_h`(예정) | 파일 형식 (CSV/XLSX) — `FileFormatType` enum 매핑 후보. `DOM_FILE_FORMAT_10` 거부 후 `DOM_CODE_20` 재사용 (Q4.2 결정, 송수펌프제어_운전현황분석 ANALYZE1, 2026-05-07) |
| `data_div_cd` | 자료 구분 코드 | `data`(일반어) + `div`(기존) + `cd` | `DOM_CODE_20` | `drvn_anls_dwld_h`(예정) | 자료 구분 (PRDC/ANLS — 예측조회/분석이력) — `DataDivType` enum 매핑 후보 (송수펌프제어_운전현황분석 ANALYZE1, 2026-05-07) |
| `dwld_dtm` | 다운로드 일시 | `dwld`(신규) + `dtm` | `DOM_DTM` | `drvn_anls_dwld_h`(예정) | 다운로드 일시. BaseEntity `rgstr_dtm` 흡수 후보 — PLAN 단계 결정 (송수펌프제어_운전현황분석 ANALYZE1, 2026-05-07) |
| `oprtng_type_cd` | 조작유형 코드 | `oprtng` + `type`(신규) + `cd` | `DOM_CODE_20` | `pump_m`(예정) | 펌프 조작유형 (AUTO_CAPABLE / SEMI_AUTO_CAPABLE 상호 배타). `PumpOprtngType` enum 매핑 + `@Enumerated(EnumType.STRING)`. 펌프의 물리적 설계값 (자동 조작 가능 펌프 ⊕ 반자동 조작 가능 펌프). `ai_mode_cd` 시스템 상태 (`AI_AUTO`/`SEMI_AUTO`) 와 의미 분리 — `_CAPABLE` 접미사로 능력값 명시 (펌프조작유형 ANALYZE1 안건 2·3, 2026-05-12). `drive_type_cd` 와 직교축 관계 — `oprtng_type_cd`=제어 방식(조작 유형), `drive_type_cd`=구동 방식(설비 물리 사양). `RATED_DRIVE + AUTO_CAPABLE` 조합 무효 (정격 펌프 가변속 불가) — 애플리케이션 레벨 차단 의무 (pump_drive_type ANALYZE1 안건 5, 2026-05-20) |
| `acq_dtm` | 수집 일시 | `acq`(신규) + `dtm` | `DOM_DTM` | `rawdata_1m_h` (파티션 키, 기 사용) | SCADA 데이터 수집·취득 시점. `rawdata_1m_h` 파티션 키로 이미 사용 중이었으나 표준 용어 사전 미등록 상태 — 본 사이클에서 응답 DTO `acqDtm` 변수명 사용 전제로 등록 공백 해소. `rcv`(수신) 와 의미 분리 (송수펌프제어분석-3번섹션 ANALYZE1 안건 11, 2026-05-13) |
| `drive_type_cd` | 구동 방식 코드 | `drive`(신규) + `type` + `cd` | `DOM_CODE_20` (재사용) | `pump_m` | 펌프 구동 방식 (INVERTER_DRIVE / RATED_DRIVE 상호 배타). `PumpDriveType` enum 매핑 + `@Enumerated(EnumType.STRING)`. 펌프의 물리적 설계값 (가변속 인버터 펌프 ⊕ 고정속 정격 펌프). `oprtng_type_cd` 와 직교축 — `drive_type_cd`=구동 방식, `oprtng_type_cd`=제어 방식. 4 조합 중 `RATED_DRIVE + AUTO_CAPABLE` 만 무효 (정격 펌프 가변속 불가 → AI 자동 제어 명령 송신 불능, `ot-integration.md §5 ⚠️ 절대 금지` 직결). `type` 채택 근거 — `oprtng_type_cd` 선례 동일 패턴 (상호 배타적 분류 → `type`, `se`(세부) 는 동일 레코드 세분화 `tag_se_cd` 맥락) (pump_drive_type ANALYZE1 안건 2, 2026-05-20) |
| `proc_id` | 공정/제어대상 ID | `proc`(비즈니스 도메인) + `id` | `DOM_ID_50` | `proc_m`(예정), `ai_drvn_mod_p`(예정 PK+FK), `ai_drvn_mod_h`(예정 논리 참조) | **외부 할당 PK** — `Persistable<String>` 구현 필수. 대문자+언더스코어 코드값 형식 (예: `PUMP_CONTROL`·`WTR_TREAT`), Service 정규식 `^[A-Z][A-Z0-9_]*$` 검증. `user_id`·`tag_srl_no` 선례 동일 패턴. **ANALYZE1 결정 (UUID 자동 생성·DOM_ID_36·`facility_id`·`instrument_id`·`menu_id` 선례 인용) 폐기** — 사업장 간 공통 코드값 정렬 목적 (송수펌프제어분석-2번섹션 ANALYZE2 안건 1, 2026-05-20) |
| `proc_nm` | 공정/제어대상명 | `proc` + `nm` | `DOM_NAME_100` | `proc_m`(예정) | **시스템 전체 UNIQUE 인덱스** — `facility_nm`·`menu_nm` 선례 동일 패턴. 업무 화면 카테고리 이름 (예: "송수펌프제어", "정수공정") (송수펌프제어분석-2번섹션 ANALYZE1 안건 1, 2026-05-20) |
| `ai_drvn_mod_cd` | AI 운전모드 코드 | `ai`(비즈니스 도메인) + `drvn` + `mod` + `cd` | `DOM_CODE_20` (재사용) | `ai_drvn_mod_p`(예정), `ai_drvn_mod_h`(예정) | AI 운전모드 (`AI` / `AI_RECOMD` / `AI_ANLS` 3종) — `AiDrvnMode` enum 매핑 + `@Enumerated(EnumType.STRING)`. 폐기된 `ai_drvn_mod`(2026-05-12) 와 의미 동일하나 `_cd` suffix 추가로 코드값 명시 — `tag_se_cd`·`oprtng_type_cd`·`drive_type_cd` 선례 동일 패턴. 동의어 충돌 회피 (송수펌프제어분석-2번섹션 ANALYZE1 안건 5, 2026-05-20) |
| `start_dtm` | 시작 일시 | `start`(신규) + `dtm` | `DOM_DTM` | `ai_drvn_mod_p`(예정), `ai_drvn_mod_h`(예정) | 업무 기간 시작 시점. NOT NULL 정책 — DOM_DTM 기본 NULL 정책에서 더 엄격하게 적용 (활성 시점 항상 존재) (송수펌프제어분석-2번섹션 ANALYZE1 안건 6, 2026-05-20) |
| `end_dtm` | 종료 일시 | `end`(신규) + `dtm` | `DOM_DTM` | `ai_drvn_mod_h`(예정) | 업무 기간 종료 시점. NULL 허용 (현재 활성 행 표현). 부분 UNIQUE 인덱스 `(proc_id) WHERE end_dtm IS NULL` 적용으로 공정/제어대상별 활성 행 1건 강제 (이력 동시성 안전망). SQL 예약어 `END` 주의 (`standard-words.md` `end` 행 비고 참조) (송수펌프제어분석-2번섹션 ANALYZE1 안건 6, 2026-05-20) |
| `ai_drvn_mod_id` | AI 운전모드 이력 ID | `ai` + `drvn` + `mod` + `id` | `DOM_SEQ_BIGINT` | `ai_drvn_mod_h`(예정) | 이력 테이블 PK — `GenerationType.SEQUENCE` allocationSize=100. 시계열 BIGINT PK 정책 (`standard-data-domains.md` `DOM_SEQ_BIGINT`). 폐기된 `pump_ctrl_id`·`predc_id` 선례 동일 패턴 (송수펌프제어분석-2번섹션 ANALYZE1 안건 3, 2026-05-20) |
| `peak_cd` | 피크 코드 | `peak`(신규) + `cd` | `DOM_CODE_20` (재사용) | `opt_peak_target_p` | **외부 할당 고정 코드값 PK** — `Persistable<String>` 구현 필수. 단일 전역 행 강제: 고정 코드값 `'PEAK_TARGET'` + `CHECK (peak_cd = 'PEAK_TARGET')` 제약. `_cd` suffix 채택 — 고정 코드값 PK 는 `ai_drvn_mod_cd`·`drive_type_cd`·`tag_se_cd` 선례 정합, `_id`(UUID/외부할당 식별자) 와 혼동 회피. `proc_id`(외부할당 PK) 선례 동일 패턴 (전력피크분석-1번섹션 ANALYZE1 안건 1·2, 2026-06-04) |
| `target_peak_elpwr` | 목표 피크 전력값 | `target`(신규) + `peak`(신규) + `elpwr`(재사용) | `DOM_QTY_15_4` (재사용) | `opt_peak_target_p` | 운전원이 설정하는 목표 피크 전력값 (kW). NOT NULL 정책 — 미설정/임계값 성격 값의 NULL 미입력 구별 방지 (`min_req_prsr` NOT NULL 선례). 수식어-선행 어순 (`min_req_prsr` 선례) — `target`+`peak`+`elpwr`. `predc_elpwr_amt`(AI 예측 산출물) 와 의미 분리 — 목표값(운전원 입력) vs 예측값 (전력피크분석-1번섹션 ANALYZE1 안건 1·2, 2026-06-04) |

---

## 동의어·금지 패턴

동일 의미의 서로 다른 물리명이 등장하면 본 사전에 기록하고 폐기 대상을 명시한다.

| 권장 물리명 | 금지 물리명 | 사유 |
|-----------|-----------|------|
| `user_id` | `member_id`, `u_id`, `userId` (DB 컬럼) | `user` 가 비즈니스 도메인 약어 (`swtp/.claude/rules/dict/domain-abbreviations.md`) 이므로 일관 사용 |
| `rgstr_dtm` | `create_dtm`, `created_at`, `reg_dtm` | `rgstr` 가 표준 단어 (`swtp/.claude/rules/dict/standard-words.md`) |
| `tag_nm` | `tagname`, `tag_name`, `tagNm` (DB 컬럼) | 스네이크케이스 + `nm` 표준 단어 일관 사용 |
| `tag_se_cd` | `tag_type`, `tag_se`, `tagType` | `tag_se_cd` 는 측정 항목 내부 분류이므로 `se` 사용. Discriminator 성격은 `type` 사용 — `equip_type_cd`·`facility_type_cd` 선례 참조 (펌프조작유형 ANALYZE1 안건 1, 2026-05-12 — `type` 표준 단어 정식 등록 후 의미 경계 명시) |
| `tag_desc` | `tag_description`, `tagDesc` | `desc` 표준 단어 + 스네이크케이스 일관 사용 |
| `ctrl_dtm` | `ctrl_dt` | TIMESTAMP 는 `_dtm` (`dt` 는 DATE 전용) — pumpcontrol ANALYZE1 안건 1 |
| `updt_dtm` | `updt_dt` | TIMESTAMP 는 `_dtm` |
| `predc_base_dtm` | `predc_base_dt` | TIMESTAMP 는 `_dtm` |
| `predc_dtm` | `predc_dt` | TIMESTAMP 는 `_dtm` |
| `stng_dtm` | `stng_dt` | TIMESTAMP 는 `_dtm` (설정 일시 향후 도입 시 적용) |
| `rgstr_id` | `reg_id` | `rgstr` 가 표준 단어 — `reg` 동의어 폐기 (pumpcontrol ANALYZE1 안건 2) |
| `pump_*` | `pmp_*` | `pump` 가 비즈니스 도메인 약어 — `pmp` 동의어 폐기 (pumpcontrol ANALYZE1 안건 1) |
| `pump_oprtng_cnt` | `pmp_oprtng_cntom` | `pmp` 폐기 + `cntom` 표준 단어 폐기 — `cnt` 단독 사용 (pumpcontrol ANALYZE1 안건 2) |
| `facility_id` | `pwtf_id` (시계열 이력 테이블 컬럼) | 마스터도메인설계 ANALYZE1 Round 2 — `pump_predc_h.pwtf_id`·`ai_drvn_mod_p.pwtf_id`·`ai_drvn_mod_h.pwtf_id` 등 시계열 컬럼은 facility 자식 PK 정렬에 따라 `facility_id` 사용. 송수펌프제어분석 사이클 V8_4 마이그레이션 SQL 로 정렬 (송수펌프제어분석 ANALYZE1, 2026-05-08) |

---

## 폐기 이력

### 즉시 폐기 (코드 미도입 — 마스터도메인설계 ANALYZE1 Round 3, 2026-05-03)

| 폐기 컬럼 | 폐기일 | 사유 | 대체 |
|---------|--------|------|------|
| `tag_id` | 2026-05-03 | 마스터도메인설계 ANALYZE1 Round 3 사용자 결정 — 태그 PK 를 자연키 (`tag_srl_no`) 로 변경. `tag_id` 별도 식별자 불필요 (코드 미도입 상태에서 변경) | `tag_srl_no` (DOM_TAG_SRL_NO_50, 자연키 PK) |
| `tag_val` | 2026-05-03 | 마스터도메인설계 ANALYZE1 Round 3 사용자 결정 — SCADA 원본값 + 보정값 분리 필요. 단일 컬럼 → 2 컬럼 (`raw_val` + `corr_val`) | `raw_val` (원본) + `corr_val` (보정, NULL 허용) |
| `io_yn` | 2026-05-08 | 마스터도메인설계 ANALYZE1 Round 3 미결 대안 — `io_cd` (`IoCode` enum, INPUT/OUTPUT/BIDIR) 최종 채택으로 boolean 단순화 대안 폐기. `tag_m` 코드 미도입 상태에서 결정 (태그관리 ANALYZE1, 2026-05-08) | `io_cd` (DOM_CODE_20) |
| `unit_cd` | 2026-05-08 | 측정 유형별 1:1 고정값 정규화 — `TagMeasurementType` enum 의 `unit` 필드로 흡수 (FRI=m³/h·PRI=kgf/cm²·LEI=m·PWI=kW·RMS=빈문자열·OPS=빈문자열·VOI=%). 사용 테이블 0건 (코드 도입 전 변경). 옵션 A 채택 — enum 생성자에 description+unit String 직접 (옵션 B 단위 별도 enum 추출은 향후 SI 표기·환산 계수 도입 시 재검토). `unit` 표준 단어는 보존 (다른 컬럼 조합 재료 가능). 본 사이클의 V9_1 마이그레이션에서 `ALTER TABLE tag_m DROP COLUMN unit_cd` (태그관리 ANALYZE1 안건 6, 2026-05-08) | `TagMeasurementType.{값}.getUnit()` enum 메서드 매핑 |

### 폐기 예정 (코드 도입 상태 — PLAN approved 후 폐기 이력 등록)

| 폐기 예정 컬럼 | 결정일 | 사유 | 대체 |
|------------|--------|------|------|
| `pwtf_id`·`pwtf_nm` | 2026-05-02 | 마스터도메인설계 ANALYZE1 Round 2 — 정수조 단일 마스터 → facility 자식 (PWTF) 흡수, 자식 PK 는 부모 `facility_id` 동일 (JPA JOINED 표준) | `facility_id`(자식 PK 동일)·`facility_nm` |
| `dwt_id`·`dwt_nm` | 2026-05-02 | 마스터도메인설계 ANALYZE1 Round 2 — 배수지 단일 마스터 → facility 자식 (DWT) 흡수 | `facility_id`(자식 PK 동일)·`facility_nm` |
| `pump_id`·`pump_nm` | 2026-05-02 | 마스터도메인설계 ANALYZE1 Round 2 — 펌프 단일 마스터 → instrument 자식 (PUMP) 흡수 | `instrument_id`(자식 PK 동일)·`instrument_nm` |
| `pump_cmbn_*`·`pump_interlock_*`·`pump_ctrl_*`·`pump_predc_*` 의 `pump_id` FK | 2026-05-02 | 마스터도메인설계 ANALYZE1 Round 2 — `pump_id` PK 폐기에 따라 `instrument_id` FK 로 변경 | `instrument_id` FK (자식 테이블 PK 참조) |

### 폐기 (pump+AI 백지화 사이클 1, 2026-05-12)

본 사이클의 코드·DDL 백지화로 `com.mo.swtp.pump.*` · `com.mo.swtp.ai.*` · `com.mo.swtp.scada.*` 패키지 + 관련 테이블 (`pump_ctrl_h`·`pump_predc_h`·`pump_cmbn_m`·`pump_cmbn_d`·`pump_interlock_p`·`ai_drvn_mod_p`·`ai_drvn_mod_h`·`drvn_anls_dwld_h`) 이 일괄 삭제됨에 따라 등록된 컬럼들의 사용처가 사라졌다. 재설계 (사이클 2 `/dev:analyze`) 에서 신규 도입될 컬럼은 본 표와 무관하게 표준 어휘 사전 + 본 표준 용어 사전 절차로 재등록한다.

| 폐기 컬럼 그룹 | 폐기일 | 사유 | 대체 |
|-------------|--------|------|------|
| 제어 이력: `pump_ctrl_id`·`ctrl_dtm`·`ctrl_div`·`ctrl_rslt` | 2026-05-12 | `pump_ctrl_h` 테이블 백지화. 제어 로그 구조는 사이클 2 에서 재설계 (현 시점 OT 아웃바운드·인터록 구조 자체가 보류 — [`ot-integration.md §2·§5`](../ot-integration.md) 보류 마커 참조) | **재도입** (제어이력 재도입 ANALYZE1, 2026-06-04) — `pump_ctrl_h` 를 `com.mo.swtp.instrument` 조회 전용으로 재도입 (물리명 유지·코드값 재정의 `ctrl_div` START/STOP, `ctrl_rslt` COMPLETED/CANCELLED). 본 표 4행 갱신 반영 |
| AI 운전 모드: `ai_drvn_mod`·`ai_mode_cd`·`expire_dtm`·`last_rcv_dtm` | 2026-05-12 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 테이블 백지화. 사용자 의도/시스템 상태 이중 체계 + 강제 전환 정책 보류 ([`ot-integration.md §5`](../ot-integration.md)) | `ai_drvn_mod` 만 **재도입** (제어이력 재도입 ANALYZE1, 2026-06-04 — `pump_ctrl_h` NULL 허용 수동 제어, `AiDrvnModeCode` enum 재사용). `ai_mode_cd`·`expire_dtm`·`last_rcv_dtm` 는 폐기 유지 (사이클 2 결정) |
| AI 예측 결과: `predc_id`·`predc_base_dtm`·`predc_dtm`·`predc_elpwr_amt`·`predc_flwrt`·`predc_prsr` | 2026-05-12 | `pump_predc_h` 테이블 백지화. AI 추론 서버 호출 클라이언트 (`AiServerClient`) 와 함께 폐기 ([`ot-integration.md §6`](../ot-integration.md) 보류) | 사이클 2 결정 |
| 펌프 조합: `pump_cmbn_cd`·`pump_oprtng_cnt` | 2026-05-12 | `pump_cmbn_m`·`pump_cmbn_d` 테이블 + 운영 카운트 컬럼 백지화 | 사이클 2 결정 |
| 운전현황 분석 다운로드: `dwld_id`·`dwld_file_nm`·`dwld_format_cd`·`data_div_cd`·`dwld_dtm` | 2026-05-12 | `drvn_anls_dwld_h` 테이블 백지화. 송수펌프제어_운전현황분석 ANALYZE1 (2026-05-07) 산출 어휘 자체는 표준 단어 사전에 보존 — 향후 다운로드 이력 도메인 재도입 시 재사용 가능 | 사이클 2 결정 (재도입 시 본 어휘 재사용 가능) |
