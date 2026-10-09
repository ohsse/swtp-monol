---
status: approved
created: 2026-04-24
updated: 2026-04-24
---
# TAG 도메인 개념 정립 — 도메인 분석

## 작업 배경

- **사용자 정의**: TAG = SCADA 계측값을 식별하는 단위. FRI/PRI/LEI/PWI/RMS = 태그의 계측데이터 유형 코드. 예: `706-FRI-xxx-xxx` = 유량(FRI) 측정값을 표현하는 태그명
- **본 작업 범위** (사용자 승인): TAG 비즈니스 도메인 정립 + 표준 사전 3층 등록 + 룰 정합. 신규 엔티티·API 구현은 본 작업 범위 외
- **외부 산출물**: 없음 (사용자 채팅 입력만)
- **참조 룰**: `.claude/rules/ot-integration.md` (FRI/PRI/LEI/PWI/RMS 정의 산재), `.claude/rules/db-partitioning-and-retention.md §1` (`rawdata_m.tag_nm`/`tag_val` 사용처), `.claude/rules/dict/README.md` (3층 사전 SSOT 룰)
- **현 사전 상태**: `domain-abbreviations.md` 에 `tag` 미등록. `standard-words.md` 에 `tag`/`se`/`desc`/`alias` 미등록(`seq` 만). `standard-data-domains.md` 에 `DOM_QTY_15_4`(`tag_val`) 만 등록. `standard-terms.md` 에 `tag_val` 만 등록. SWTP 코드에 TAG 관련 엔티티 0건

## 회의록 (5인 팀 토픽 주도)

### 안건 1: TAG 비즈니스 도메인 등록 위상
- **호출 에이전트**: `wtp-glossary-manager`, `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: `domain-abbreviations.md` "도입 예정" 섹션에 `tag` 신규 등록. 풀네임은 `tag` 단순형 (기존 `pump`/`raw`/`ctrl` 패턴 일관). `measurement-tag` 합성어는 약어 일관성 해침. README.md SSOT 룰에 따라 `standard-words.md` 에는 중복 등록 금지
  - **wtp-domain-expert**: TAG 는 알람·진단·AI 최적화·펌프 운전 모두의 공통 참조점이므로 `com.mo.swtp.tag` 단독 패키지 분리 가치 충분. `scada`/`raw` 하위 흡수 시 역방향 의존 위험. ⚠️ 향후 `alarm`/`diag`/`opt` 가 enum 직접 참조 시 `common` 모듈 이관 검토 필요 (PLAN 위임)
- **Round 2**: 불필요 (양 에이전트 동일 결론)
- **결론**: `domain-abbreviations.md` "도입 예정" 섹션에 `tag` 약어 신규 등록. 풀네임 `tag`, 설명 "SCADA 계측값 식별 단위", 비고 "도입 예정 (예: `tag_m`)". 모듈 위치(`api` 단독 vs `common` 이관) 는 `tag_m` 도입 시 별도 ANALYZE 결정

### 안건 2: 표준 단어 사전 처리 정책
- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - `tag` 자체는 비즈니스 도메인 약어 SSOT 룰(`dict/README.md`) 에 따라 `standard-words.md` 중복 등록 **금지**
  - 신규 조합 재료 단어 판정:
    - `se` (세부/종류, subdivision) — 미등록·충돌 없음. 기본 도메인 `DOM_CODE_20` — **신규 등록**
    - `desc` (설명, description) — 미등록. 기본 도메인 `DOM_TEXT` — **신규 등록**
    - `alias` (별명, alias) — `nm` 과 유사 충돌 가능성. ⚠️ 본 작업 범위(`tag_alias` 가 핵심 아님) 와 무관하게 별도 판정 필요
- **Round 2**: 본 작업 범위가 "사전 등록 + 룰 정합" 으로 한정되었고 `tag_alias` 가 본 작업 핵심이 아니므로 (레거시 EMS 의 `if_tag.tag_alias` 정도만 참고), `alias` 단어 + `tag_alias` 용어 등록을 **본 작업 외로 미룬다**
- **결론**: `tag` 미등록 (도메인 약어 SSOT 유지). `se`/`desc` 신규 단어 등록. `alias` 는 본 작업 외 — 향후 별도 ANALYZE 에서 `nm` 유사 충돌 판정

### 안건 3: 표준 데이터 도메인 신규 등록 (DBA 2차 승인)
- **호출 에이전트**: `wtp-glossary-manager` + `wtp-dba-reviewer` (2차 승인)
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer 판정**:
    - `DOM_TAG_NM_50` (VARCHAR(50), String, NOT NULL): ✅ **승인** — `DOM_ID_50` 과 타입 동일하나 용도(식별자 PK vs 시계열 비PK 계측 식별자) + 인덱스 전략 + NULL 의미 분리. 50자는 `706-FRI-RAWWATER-INLET-01` (25자) + 확장 마진 충분
    - `DOM_TAG_TYPE_20` (VARCHAR(20)): ⚠️ **부분승인** — `DOM_CODE_20` 과 SQL 타입·길이·NULL 정책 동일. 신규 등록보다 `DOM_CODE_20` **재사용** 권장 + 사용처에서 `TagMeasurementType` 전용 enum 매핑 (`standard-data-domains.md` 의 "VARCHAR(n) enum" 룰)
    - `DOM_TAG_DESC_200` (VARCHAR(200)): ❌ **거부** — `DOM_NAME_100`/`DOM_TEXT` 와 중간 길이 중복. `DOM_TEXT` 재사용 + 애플리케이션 `@Size(max=200)` 검증으로 대체. 향후 `DOM_MEMO_300` 등 누적 충돌 예방
  - **wtp-glossary-manager**: DBA 부분승인·거부 결과 수용. `DOM_CODE_20`·`DOM_TEXT` 재사용 채택 시 사전 일관성 유지
  - **wtp-dba-reviewer 추가 룰화 권고** (사용자 검토 결과 — 권고 1 본 작업 포함, 권고 2 별도 작업):
    1. ✅ 시계열 파티션 테이블(`_h` suffix) 은 마스터 테이블 FK 추가 **금지** (대용량 INSERT 잠금 회피) — **본 작업 포함**
    2. ⏸ `rawdata_m` 의 `(tag_nm, acq_dtm DESC)` 복합 인덱스 순서 룰화. `tag_se_cd` 단독 인덱스 카디널리티(5종) 부족 → `tag_m` 마스터 경유 IN 절 변환 패턴 권장 — **별도 작업으로 미룸**
- **Round 2**: 불필요 (DBA·glossary-manager 통합 결론)
- **결론**:
  - `DOM_TAG_NM_50` 신규 등록 (DBA 승인)
  - `DOM_TAG_TYPE_20` 미등록 → `DOM_CODE_20` 재사용 + 사용처 enum 매핑
  - `DOM_TAG_DESC_200` 미등록 → `DOM_TEXT` 재사용 + 애플리케이션 검증
  - DBA 추가 권고 1 본 작업 포함 (룰 갱신 지시서 추가), 권고 2 별도 작업으로 미룸

### 안건 4: 표준 용어 신규 등록
- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**: 안건 2·3 결정 반영하여 4개 용어 등록 (`tag_alias` 는 안건 2 미결로 보류). 동의어 금지 패턴 정리
- **결론**:
  - `tag_nm` 신규 등록 (조합 `tag` + `nm` / `DOM_TAG_NM_50`)
  - `tag_se_cd` 신규 등록 (조합 `tag` + `se` + `cd` / `DOM_CODE_20` 재사용 + `TagMeasurementType` enum 매핑)
  - `tag_desc` 신규 등록 (조합 `tag` + `desc` / `DOM_TEXT` 재사용 + `@Size(max=200)`)
  - `tag_alias` **본 작업 외** (안건 2 결정 반영)
  - 동의어·금지 패턴 추가:
    - 권장 `tag_nm` ↔ 금지 `tagname`, `tag_name`, `tagNm`
    - 권장 `tag_se_cd` ↔ 금지 `tag_type`, `tag_se`, `tagType`
    - 권장 `tag_desc` ↔ 금지 `tag_description`, `tagDesc`

### 안건 5: 센서 측정 유형 코드(FRI/PRI/LEI/PWI/RMS) 위상
- **호출 에이전트**: `wtp-domain-expert`, `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: 옵션 A(tag enum) 추천. 옵션 B(`measurement` 신규 도메인) 는 `tag` 와 유사 의미 약어 동시 등록 금지 룰 위반 위험. 옵션 C 는 `tag_se_cd` 컬럼 SSOT 모호. ot-integration.md(기술 계층) 와 standard-terms.md(표준 용어) 역할 분리 가능
  - **wtp-domain-expert**: 옵션 A 추천. `TagMeasurementType.getDefaultPolicy()` 메서드로 `ot-integration.md §3` 결측 대체값 정책(FRI/PRI/LEI = Hold Last Value, PWI/RMS = null 저장) 캡슐화. ⚠️ `alarm`·`diag`·`opt` 가 enum 직접 참조 시 `tag` 도메인 `common` 이관 검토 (PLAN 위임)
- **Round 2**: 불필요 (양 에이전트 옵션 A 동의)
- **결론**: 옵션 A 채택. `TagMeasurementType` Java enum (FRI/PRI/LEI/PWI/RMS) 을 향후 `com.mo.swtp.tag` (또는 `common` 이관) 에 정의. `tag_se_cd` 컬럼은 `DOM_CODE_20` 재사용 + `@Enumerated(EnumType.STRING)`. ot-integration.md §3 결측 대체값 정책은 기술 계층 SSOT 로 유지하되, **측정 유형 코드 정의 위치는 표준 용어 사전(`tag_se_cd`) + Java enum** 으로 이전됨을 명시 (역할 분리)

### 안건 6: 태그명 명명 규칙(`706-FRI-xxx-xxx`) 룰화
- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약**:
  - 패턴 추정 (`{시설코드}-{측정유형}-{설비ID}-{채널/순번}`) 도메인 합리적이나 `706` 의 정확한 의미(지자체 코드 vs 공정 구역 코드) 불명확. 멀티테넌트(`multi-tenant.md`) 와의 충돌 여부 결정 필요
  - 옵션 X (ot-integration.md 통합) — OT 연동 문서 범위 초과
  - 옵션 Y (신규 룰 파일 `tag-naming.md`) — 패턴 확정 후 타당
  - **옵션 Z 추천** — 본 작업 외, 사용자 확인 후 별도 작업
- **Round 2**: 불필요
- **사용자 응답** (ANALYZE 검토 단계): `706` 의 의미 **미확정** — 사용자가 현 시점 답변 보류. 향후 별도 ANALYZE 에서 재확인
- **결론**: 옵션 Z 채택 — **본 작업 범위 외**. `706` 의미 사용자 미확정 상태로 보존. 별도 ANALYZE 진입 시 사용자에게 실제 태그명 예시 2~3건 + `706` 의미 재확인 후 룰화

## 표준 사전 카탈로그

### 신규 표준 단어
(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `.claude/rules/dict/standard-words.md`)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `se` | 세부/종류 | 신규 | `standard-words.md` 미등록, `domain-abbreviations.md` 충돌 없음. 기본 도메인 `DOM_CODE_20`. `tag_se_cd` 등 코드값 조합 재료 |
| `desc` | 설명 | 신규 | `standard-words.md` 미등록. 기본 도메인 `DOM_TEXT`. `tag_desc` 등 자유형 텍스트 조합 재료 |
| `alias` | 별명 | **본 작업 외** | `nm` 과 유사 의미 단어 동시 등록 금지 룰 잠재 위반 — 별도 ANALYZE 에서 판정 |

### 신규 표준 데이터 도메인
(값 형식 — SQL 타입·길이·Java 타입. 1차 정의: `.claude/rules/dict/standard-data-domains.md`. **`wtp-dba-reviewer` 2차 승인 필수**)

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| `DOM_TAG_NM_50` | VARCHAR(50) | String | NOT NULL | 신규 | `rawdata_m.tag_nm` 기존 사용 + 향후 `tag_m.tag_nm` PK. DBA 승인 — `DOM_ID_50` 과 용도(시계열 계측 식별자) 분리 |
| ~~`DOM_TAG_TYPE_20`~~ | (VARCHAR(20)) | (전용 enum) | (NOT NULL) | **폐기·통합** | DBA 부분승인 — `DOM_CODE_20` 재사용 권장 (타입·길이·NULL 동일 중복 금지) |
| ~~`DOM_TAG_DESC_200`~~ | (VARCHAR(200)) | (String) | (NULL 허용) | **폐기·통합** | DBA 거부 — `DOM_TEXT` 재사용 + `@Size(max=200)` 애플리케이션 검증 |

### 신규 표준 용어
(단어 + 데이터 도메인 → DB 컬럼명. 1차 정의: `.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `tag_nm` | `tag`(비즈니스 도메인) + `nm` | `DOM_TAG_NM_50` | 신규 | `rawdata_m.tag_nm` 기존 사용 정합. 향후 `tag_m.tag_nm` PK 후보 |
| `tag_se_cd` | `tag` + `se` + `cd` | `DOM_CODE_20` (재사용) | 신규 | FRI/PRI/LEI/PWI/RMS 5종 enum 보관. `TagMeasurementType` Java enum 매핑 |
| `tag_desc` | `tag` + `desc` | `DOM_TEXT` (재사용) | 신규 | `tag_m.tag_desc` 후보. `@Size(max=200)` 애플리케이션 검증 |
| `tag_alias` | (`tag` + `alias`) | (`DOM_NAME_100`) | **본 작업 외** | `alias` 단어 미결정으로 보류 |

## 신규 엔티티/DB 컬럼

본 작업은 **사전 등록 + 룰 정합** 만 다루며, 신규 엔티티/DB 컬럼 자체는 추가하지 않는다 (사용자 §질문2 답변 확정).

향후 도입 예정 (각각 별도 ANALYZE 필요):
- `com.mo.swtp.tag` 패키지 (또는 `common` 이관) — `Tag` 마스터 엔티티 + `TagMeasurementType` Java enum
- `tag_m` 테이블 (suffix `_m` 마스터) — `tag_nm` PK + `tag_se_cd` + `tag_desc` + `tag_alias`(별도 결정 후)

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 해소책 |
|----------|----------------|
| `DOM_TAG_TYPE_20` ↔ `DOM_CODE_20` (타입·길이·NULL 동일 중복) | `DOM_CODE_20` 재사용 + 사용처 `TagMeasurementType` enum 매핑 (`standard-data-domains.md` "VARCHAR(n) enum" 룰 적용) |
| `DOM_TAG_DESC_200` ↔ `DOM_TEXT` (중간 길이 중복) | `DOM_TEXT` 재사용 + 애플리케이션 `@Size(max=200)` 검증. 향후 `DOM_MEMO_*` 누적 충돌 예방 |
| `alias` 단어 ↔ `nm` (유사 의미 단어 동시 등록 금지 잠재 위반) | 본 작업 외 — 별도 ANALYZE 에서 판정. `tag_alias` 용어도 본 작업 외 |
| `ot-integration.md` 의 FRI/PRI/LEI/PWI/RMS 정의 ↔ 표준 용어 사전 (옵션 A 채택) | 두 문서 역할 분리 명시 — ot-integration.md 는 기술 계층(품질 관리·결측 대체값 정책 SSOT), standard-terms.md + Java enum 은 표준 용어(컬럼·코드값 정의 SSOT). 상호 참조 링크 추가 |
| 시계열 파티션(`_h` suffix) 에 마스터 FK 추가 시 대용량 INSERT 잠금 위험 | DBA 권고 1 — `db-partitioning-and-retention.md §1 운영 원칙` 에 "시계열 파티션 테이블은 마스터 테이블 FK 추가 금지" 절 추가 (본 작업 포함) |

## 사용자 응답 결과 (ANALYZE 검토 단계)

| 확인 항목 | 사용자 응답 | 반영 위치 |
|---------|-----------|---------|
| 태그명 패턴 `706` 의미 | 모름 — "사용자 미확정" 으로 기록 | 안건 6 결론 + "PLAN 으로 전달" §본 작업 외 |
| DBA 권고 1 — 시계열 파티션 FK 금지 룰화 | **포함** | 룰 갱신 지시서 신규 항목 추가 |
| DBA 권고 2 — `tag_nm` 복합 인덱스 + `tag_se_cd` 가이드 | 별도 작업으로 미룸 | "PLAN 으로 전달" §본 작업 외 추가 |

## PLAN 으로 전달할 결정 사항

### 사전 등록·룰 정합 (본 작업 범위 — 확정)
- `tag` 비즈니스 도메인 약어 도입 예정 등록
- `se`, `desc` 표준 단어 신규 등록
- `DOM_TAG_NM_50` 표준 데이터 도메인 신규 등록 (DBA 승인)
- `tag_nm`, `tag_se_cd`, `tag_desc` 표준 용어 신규 등록 + 동의어 금지 패턴 추가
- `ot-integration.md` 와 표준 용어 사전 역할 분리 명시 (FRI/PRI/LEI/PWI/RMS 의 정의 위치 SSOT 변경 — 결측 대체값 정책은 ot-integration.md 유지)
- `db-partitioning-and-retention.md §1 운영 원칙` 에 시계열 파티션 마스터 FK 금지 절 추가 (DBA 권고 1, 사용자 승인)

### 본 작업 외로 미룰 항목 (별도 작업)
1. `tag_alias` 용어 + `alias` 단어 등록 — 별도 ANALYZE 에서 `nm` 유사 충돌 판정 후 결정
2. 태그명 명명 규칙(`706-FRI-xxx-xxx`) 패턴 룰화 — `706` 의미 등 사용자 미확정 → 추후 사용자 정보 확보 후 별도 ANALYZE
3. `tag_m` 마스터 엔티티 + `TagMeasurementType` Java enum 구현 — 별도 작업 (Medium 또는 Large)
4. `tag` 도메인 모듈 위치 (`api` 단독 vs `common` 이관) — `tag_m` 도입 시 ANALYZE 결정
5. **DBA 권고 2** — `rawdata_m` 의 `(tag_nm, acq_dtm DESC)` 복합 인덱스 순서 + `tag_se_cd` 카디널리티 가이드 룰화 — `tag_m` 마스터 도입 작업의 DB 설계 단계로 미룸 (사용자 결정)

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `.claude/rules/domain-abbreviations.md` — `tag` 비즈니스 도메인 약어 "도입 예정" 섹션 신규 등록 (풀네임 `tag`, 설명 "SCADA 계측값 식별 단위", 비고 "도입 예정 (예: `tag_m`)")
- [x] `.claude/rules/dict/standard-words.md` — `se` 단어 신규 등록 (한글 "세부/종류", 풀네임 "subdivision", 기본 데이터 도메인 `DOM_CODE_20`, 등록일 2026-04-24)
- [x] `.claude/rules/dict/standard-words.md` — `desc` 단어 신규 등록 (한글 "설명", 풀네임 "description", 기본 데이터 도메인 `DOM_TEXT`, 등록일 2026-04-24)
- [x] `.claude/rules/dict/standard-data-domains.md` — `DOM_TAG_NM_50` 신규 등록 (VARCHAR(50), String, NOT NULL, 비고 "rawdata_m.tag_nm 기존 사용 + 향후 tag_m.tag_nm PK")
- [x] `.claude/rules/dict/standard-terms.md` — `tag_nm` 용어 신규 등록 (조합 `tag`(비즈니스 도메인) + `nm`, 데이터 도메인 `DOM_TAG_NM_50`, 사용 테이블 `rawdata_m`/`tag_m`(예정))
- [x] `.claude/rules/dict/standard-terms.md` — `tag_se_cd` 용어 신규 등록 (조합 `tag` + `se` + `cd`, 데이터 도메인 `DOM_CODE_20` 재사용, 비고 "TagMeasurementType enum 매핑 / FRI·PRI·LEI·PWI·RMS")
- [x] `.claude/rules/dict/standard-terms.md` — `tag_desc` 용어 신규 등록 (조합 `tag` + `desc`, 데이터 도메인 `DOM_TEXT` 재사용, 비고 "@Size(max=200) 애플리케이션 검증")
- [x] `.claude/rules/dict/standard-terms.md` — 동의어·금지 패턴 표에 3건 추가 (`tag_nm` ↔ tagname/tag_name/tagNm / `tag_se_cd` ↔ tag_type/tag_se/tagType / `tag_desc` ↔ tag_description/tagDesc)
- [x] `.claude/rules/ot-integration.md` — §3 센서 품질 관리 섹션에 측정 유형 코드(FRI/PRI/LEI/PWI/RMS) 의 enum 정의 위치 SSOT 가 표준 용어 사전(`tag_se_cd`) 및 향후 `TagMeasurementType` Java enum 임을 명시. 결측 대체값 정책(Hold Last Value vs null 저장) 은 본 문서가 SSOT 유지
- [x] `.claude/rules/db-partitioning-and-retention.md` — §1 운영 원칙 절에 "시계열 파티션 테이블(`_h` suffix) 은 마스터 테이블 FK 추가 금지. 참조 무결성은 애플리케이션 레벨 검증으로 대체" 원칙 신규 추가 (DBA 권고 1, 사용자 승인 — 대용량 INSERT 잠금 회피)

## 산출물

- [계획안](../../../plan/20260424/tag_개념_정의/PLAN1.md) (다음 단계 자동 전이 시 생성)
