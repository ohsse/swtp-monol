---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# tag_frequency_추가 — 도메인 분석

## 작업 배경

- **요청 요약**: 사용자 지적 "태그종류코드에 주파수가 빠져있는데?" → `TagMeasurementType` enum 에 `FQI` (Frequency Indicator, 주파수, 단위 Hz) 측정 유형 코드값 추가.
- **사용자 결정 메모**: "주파수 FQI (Frequency Indicator) 로 추가하기로 했잖아."
- **트리거 컨텍스트**:
  - 2026-05-20 `pump_drive_type` ANALYZE1 에서 `drive_type_cd = INVERTER_DRIVE` (가변속 인버터 펌프) 신규 도입. 인버터 펌프는 주파수(Hz) 제어로 회전수를 조절하므로 주파수 측정값 부재는 일관성 공백.
  - 현 `TagMeasurementType` 7종 (`FRI`·`PRI`·`LEI`·`PWI`·`RMS`·`OPS`·`VOI`) — 주파수 누락.
- **외부 산출물**: 없음 (사용자 채팅 결정 단일 소스).

## 회의록 (5인 회의 토픽 주도)

### 안건 1: `FQI` enum 코드값 신설 + `freq` 표준 단어 등록 여부

- 호출 에이전트: `wtp-glossary-manager`, `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `FQI` enum 약어 자체는 사전 미등록 (기존 7종 선례 동일 — `ot-integration.md §3` 가 측정 유형 코드 SSOT). `freq` 표준 단어는 **신규 등록 필요** — 향후 `freq_val`·`rated_freq`·`predc_freq` 등 컬럼 조합 재료 가능성. swtp 4~5자 약어 컨벤션 (`prsr`·`flwrt`·`elpwr` 선례) 정합. `Hz` 단위는 사전 등록 불필요 (기존 `m³/h`·`kgf/cm²` 등 enum unit 필드에만 존재 선례 동일). `FRI` vs `FQI` 첫 글자 동일하나 2번째 글자 `R` vs `Q` 로 명확 구분 — `PRI` vs `PWI` 공존 선례 동형, 충돌 없음.
  - **wtp-backend-engineer**: enum 값 추가는 `@Getter` + `@RequiredArgsConstructor` 불변 패턴 정합, SOLID·정량 기준 (50줄/3단/3단) 영향 0건. **블로커**: `TagMeasurementTypeTest.enum_값은_총_7종이다` 의 `hasSize(7)` assertion 이 `hasSize(8)` 로 갱신되지 않으면 빌드 RED 차단.
- **결론**: `FQI("주파수", "Hz")` 8번째 enum 값 추가. `freq` 표준 단어 신규 등록. `Hz` 단위는 enum unit 필드로만 관리. 테스트 assertion 갱신 의무 (블로커 — PLAN 단계 체크박스 명시).

### 안건 2: `FQI` 결측 대체값 정책 (`ot-integration.md §3` 표 갱신)

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: **Hold Last Value + 5분 초과 BAD 격상** 채택 (VOI 선례 동형). 인버터 주파수는 명령값 변경 → 모터 가속 시간 (수초~수십초) 누적 → 1분 미만 단절 동안 직전 Good 값과 실측값 편차 매우 작음. PWI null 저장 채택 시 AI 폐루프 피드백 추적 (지령값 vs 실측값 비교) 집계 구멍 발생. OPS 즉시 BAD 격상 채택 시 일시 잡음으로 알람 연속 발생 — 운전원 혼란. **블로커**: 5분 초과 BAD 격상 경계 미명시 시 `ot-integration.md §5` 강제 전환 트리거 판정 누락 위험.
- **결론**: `ot-integration.md §3` 결측 대체값 표에 `FQI` 행 추가 — "직전 Good 값 (Hold Last Value)" + 비고에 "5분 초과 시 BAD 격상, VOI 선례 동형. 인버터 폐루프 제어 피드백 안전성".

### 안건 3: `FQI` 이상치 기각 기준 (정격 ±30% 관행 적용 여부)

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: **±30% 관행 부적합**. 60Hz 정격 ±30% 적용 시 42~78Hz 외 기각이나, 한국 산업용 인버터 운전 범위는 통상 30~60Hz (정격 대비 50% 하한). ±30% 기준 적용 시 정상 저속 운전 구간 30~42Hz 가 이상치로 잘못 기각 → AI 모델 입력 데이터 체계적 결측 발생. `ot-integration.md §3` 의 `TagRangeConfig.physicalMin`/`physicalMax` 태그별 등록 구조 활용. **블로커**: `physicalMin=42Hz` 오설정 누락 시 수집 공백.
- **결론**: `FQI` 태그는 `physicalMin=0Hz` (정지 포함), `physicalMax=65Hz` (60Hz + 안전 여유 5Hz) 절대값 기준 등록. ±30% 관행은 압력·유량 같은 아날로그 연속 변량 한정 — 가변 주파수 제어 신호는 적용 외.

### 안건 4: `drive_type_cd = INVERTER_DRIVE` 펌프 전용 도메인 룰

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: **`RATED_DRIVE` 펌프 FQI 태그 등록 차단 의무** — 고정속 펌프는 주파수 제어 불가능, FQI 측정값 항상 60Hz 고정 또는 SCADA 미수집. 등록 허용 시 잘못된 인터록 평가 → `ot-integration.md §5 ⚠️ 절대 금지` 직결. **블로커**. `INVERTER_DRIVE` 펌프의 FQI 태그 등록 의무화 (필수)는 **권고 수준** — SCADA 인프라 미구현 상태이므로 사이클 2 인터록 설계 단계에서 의무화 여부 재결정.
- **결론**: `RATED_DRIVE` 펌프에 FQI 태그 등록 차단 — 서비스 레이어 검증 의무 (`TagService.register` 등에서 `pump.driveType` 확인 후 `RestApiException(FQI_NOT_ALLOWED_FOR_RATED_DRIVE)` 차단). `INVERTER_DRIVE` 의무화는 사이클 2 이관 (미해결 질문).

### 안건 5: 알람 4단계 + AI 운전 모드 강제 전환 영향

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: FQI 단독 BAD (다른 태그는 GOOD) 시 `ai_drvn_mod_p.last_rcv_dtm` 은 갱신되므로 강제 전환 발동 **안 됨**. 그러나 `oprtng_type_cd = AUTO_CAPABLE` 인버터 펌프에 AI 주파수 명령 송신 중 FQI 피드백 5분 초과 부재 시 폐루프 제어 피드백 단절 → "제어 불가 상태" 인터록 룰 별도 필요. **블로커**: FQI 단독 BAD 처리 경로가 `ot-integration.md §5` 에 미정의 → ANALYZE 가정 섹션 명시 의무. **권고**: FQI BAD 전용 알람 2단계(경보) 룰 신설은 사이클 2 인터록 설계로 이관.
- **결론**: FQI 단독 BAD 는 본 사이클의 강제 전환 트리거 **제외** (전체 SCADA 중단 시점에만 발동). AUTO_CAPABLE 인버터 펌프의 FQI 피드백 단절 인터록 룰 + FQI BAD 전용 알람 룰은 사이클 2 인터록 설계로 이관 (미해결).

### 안건 6: DDL 영향 + V8 patch 필요 여부

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: **DDL 변경 0건, V8 patch 불필요**. `tag_se_cd VARCHAR(20) NOT NULL` 컬럼 정의가 `FQI` 3자 수용. DB CHECK 제약 없음 (`indexing-and-migration.md §3.2` enum 단일 방어선 정책). OPS/VOI 추가 선례 (태그관리 ANALYZE1, 2026-05-08) 동일. **`docs/ddl/tag.sql` SSOT 사본 갱신 의무** (`indexing-and-migration.md §5.3` 양쪽 동시 갱신). 데이터 도메인 — **`DOM_QTY_15_4` 재사용 2차 승인** (30.0~60.0Hz 정밀도 손실 없음, 신규 `DOM_*` 불필요). 인덱스 영향 — `tag_se_cd` 카디널리티 7→8 변경은 무시할 수준 (저카디널리티 단독 인덱스 미적용 정책 `§3.4` 동일).
- **결론**: V8 patch 파일 신규 생성 없음. `docs/ddl/tag.sql` 의 enum 코드값 목록 설명 갱신만 수행. `DOM_QTY_15_4` 재사용.

### 안건 7: frontend SPEC 영향

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **`태그관리` 슬러그 `SPEC4.md` 갱신 필요** (`INDEX.md` 의 `covers_dto: [TagDto, TagUpsertDto]` 매칭). `@Schema(implementation = TagMeasurementType.class)` 가 SpringDoc allowable values 자동 추출하므로 `FQI` 자동 노출 — 변경 이력에 "DTO 필드 변경 — `TagUpsertDto.tagSeCd`·`TagDto.tagSeCd` allowable values 에 `FQI` 추가" 기재 의무. 나머지 8개 슬러그는 매칭 외.
- **결론**: `/dev:spec 태그관리` 후속 호출로 `SPEC4.md` 작성 — PLAN 의 `/dev:commit` 후 단계.

## 표준 사전 카탈로그

### 신규 표준 단어
(1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `freq` | 주파수 | 신규 | `frequency` 풀네임(9자) 약어 4자 채택 — swtp 4~5자 컨벤션 (`prsr`·`flwrt`·`elpwr` 선례) 정합. 향후 `freq_val`·`rated_freq`·`predc_freq` 등 컬럼 조합 재료. `flwrt` 와 어근·철자 충돌 없음. 기본 데이터 도메인 `DOM_QTY_15_4` (`Hz`, 측정값) |

### 신규 표준 데이터 도메인
(1차 정의: `swtp/.claude/rules/dict/standard-data-domains.md` — ROOT. **wtp-dba-reviewer 2차 승인 필수**)

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| (없음) | — | — | — | — | `DOM_QTY_15_4` 재사용 — DBA 2차 승인 완료 (안건 6). 신규 등록 불필요 |

### 신규 표준 용어
(1차 정의: `.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| (없음) | — | — | — | 본 사이클은 enum 값 1건 추가 + 결측 정책·이상치 기각 정책 갱신만 수행. 신규 DB 컬럼 0건. 향후 `freq_val`·`rated_freq` 등 도입 시 별도 사이클로 등록 |

## 신규 엔티티/DB 컬럼

- 신규 엔티티: 없음
- 신규 DB 컬럼: 없음
- enum 값 추가: `TagMeasurementType.FQI("주파수", "Hz")` 1건 (`com.mo.swtp.tag.domain.enumtype` 패키지, `common` 모듈)

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 (해소책) |
|---------|--------------|
| `FRI`(유량) vs `FQI`(주파수) 첫 글자 동일 | 2번째 글자 `R` vs `Q` 명확 구분. `PRI` vs `PWI` 공존 선례 동형. 충돌 없음으로 판정 |
| `flwrt`(유량) vs `freq`(주파수) 표준 단어 어근 충돌 | `flwrt` (flow rate) 와 `freq` (frequency) 어근·철자 완전 분리. 충돌 없음 |
| `Hz` 단위 표기 사전 등록 여부 | 기존 `m³/h`·`kgf/cm²`·`%` 단위가 모두 표준 단어 사전 미등록 — enum unit 필드로만 관리하는 선례 동일 적용. 사전 등록 불필요 |
| `±30%` 이상치 기각 관행 vs 인버터 주파수 운전 범위 | 가변 주파수 제어 신호 (정격 대비 50% 하한) 에 ±30% 관행 부적합 → `TagRangeConfig.physicalMin=0Hz`/`physicalMax=65Hz` 절대값 기준 채택 |
| `RATED_DRIVE` 펌프 FQI 태그 등록 가능성 | 서비스 레이어 검증 차단 의무. 미차단 시 `ot-integration.md §5 ⚠️ 절대 금지` 직결 |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- `TagMeasurementType` enum 에 `FQI("주파수", "Hz")` 8번째 값 추가
- `Tag` 엔티티·`TagDto`·`TagUpsertDto` 타입 선언 변경 0건 (enum 값 자동 노출)
- `RATED_DRIVE` 펌프 FQI 태그 등록 차단 — `TagService.register` (또는 `TagValidator`) 에서 `instrument.driveType = RATED_DRIVE` 인 경우 `FQI` 거부 검증 추가. 신규 `ErrorCode`: `FQI_NOT_ALLOWED_FOR_RATED_DRIVE` (httpStatus 400)

### DB 설계 변경 초안
- DDL 변경 없음 (V8 patch 신규 생성 0건)
- `docs/ddl/tag.sql` SSOT 사본 — enum 코드값 목록 설명 부분에 `FQI` 행 보강 (주석/설명 갱신만)

### 적용할 패턴
- enum `@Getter` + `@RequiredArgsConstructor` 불변 패턴 유지 (`entity-patterns.md`)
- `TagMeasurementTypeTest` 갱신 — `hasSize(7)` → `hasSize(8)`, `FQI` description/unit assertion 2건 추가
- frontend SPEC — `/dev:spec 태그관리` 후속 호출로 `SPEC4.md` 작성 (변경 이력 §DTO 필드 변경에 `tagSeCd` allowable values `FQI` 추가 기재)

## 가정 및 미해결 질문

> 본 ANALYZE 의 가정·미해결 질문. `wtp-domain-expert` 가 도메인 4영역 충돌 점검 결과 블로커 4건 → 결정 변환 + 미해결 이관 분리.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `FQI` 결측 대체값 = Hold Last Value + 5분 초과 BAD 격상 (VOI 선례 동형) | 결정 | 안건 2 결론. `ot-integration.md §3` 표 갱신 |
| `FQI` 태그 `physicalMin=0Hz`, `physicalMax=65Hz` 절대값 기준 | 결정 | 안건 3 결론. `TagRangeConfig` 등록 정책 — 본 사이클은 정책 명시만, 실제 등록 데이터 투입은 SCADA 인프라 도입 시 |
| `RATED_DRIVE` 펌프 FQI 태그 등록 차단 — 서비스 레이어 검증 의무 | 결정 | 안건 4 결론. `FQI_NOT_ALLOWED_FOR_RATED_DRIVE` ErrorCode 신설 |
| `INVERTER_DRIVE` 펌프 FQI 태그 등록 의무화 (필수화) | 미해결 | 사이클 2 인터록 설계로 이관 — SCADA 인프라 미구현 상태이므로 현 시점 권고 수준 |
| AUTO_CAPABLE 인버터 펌프 FQI 단독 BAD (5분 초과) 시 인터록 룰 | 미해결 | `last_rcv_dtm` 전체 SCADA 중단 트리거와 별도 — "제어 불가 상태" 인터록 룰. 사이클 2 인터록 설계로 이관 |
| FQI BAD 전용 알람 2단계(경보) 룰 신설 | 미해결 | 사이클 2 인터록·알람 룰 설계로 이관. 본 사이클은 결측 대체값 정책만 명시 |
| 본 사이클의 강제 전환 트리거 범위 | 결정 | FQI 단독 BAD 는 본 사이클 강제 전환 트리거 **제외**. 전체 SCADA 수신 중단 시점에만 발동 (기존 `§5` 정책 변경 없음) |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `TagMeasurementType.FQI` enum 값 추가 + description/unit 매핑 정합 | `./gradlew.bat :common:test` PASS — `TagMeasurementTypeTest.측정_유형별_단위가_고정값으로_매핑된다` 에 FQI 행 추가 + `hasSize(8)` GREEN |
| `RATED_DRIVE` 펌프 FQI 태그 등록 차단 | 신규 단위 테스트 GREEN — `TagServiceTest.RATED_DRIVE_펌프에_FQI_태그_등록_시_예외가_발생한다` (`FQI_NOT_ALLOWED_FOR_RATED_DRIVE` ErrorCode 발생) |
| `ot-integration.md §3` 결측 대체값 표에 FQI 행 추가 (Hold Last Value + 5분 초과 BAD 격상) | grep "FQI" `.claude/rules/ot-integration.md` 매칭 + "Hold Last Value" 동행 매칭 |
| `freq` 표준 단어 ROOT 등록 | grep "`freq`" `swtp/.claude/rules/dict/standard-words.md` 매칭 + 한글 논리명 "주파수" 동행 매칭 |
| `docs/ddl/tag.sql` SSOT 사본에 FQI enum 코드값 설명 보강 | grep "FQI" `docs/ddl/tag.sql` 매칭 |
| frontend SPEC4 작성 — `tagSeCd` allowable values 에 `FQI` 추가 (`/dev:spec 태그관리` 후속 호출) | `/dev:commit` 후 별도 단계 — 본 사이클 PLAN 범위 외 |

## 도메인 룰 4영역 점검

> `wtp-domain-expert` Round 1 답변에 따라 4영역 충돌 여부 표시. 인용 근거: `.claude/rules/ot-integration.md`.

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 해당 | `FQI` BAD 시 알람 1단계(UNCERTAIN, 이상치 기각) + 알람 2단계(경보, 결측 5분 초과) 트리거 — 본 사이클은 결측 정책만 명시, 알람 2단계 전용 룰 신설은 사이클 2로 이관 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 해당 | `RATED_DRIVE` 펌프 FQI 태그 등록 시 잘못된 인터록 평가 위험 → 서비스 레이어 차단 의무. AUTO_CAPABLE 인버터 펌프 FQI 피드백 단절 인터록 룰은 사이클 2 이관 |
| AI 운전 모드 (`ot-integration.md §5`) | 해당 | FQI 단독 BAD 는 본 사이클 강제 전환 트리거 **제외** (전체 SCADA 중단만 발동, 기존 정책 변경 없음). AUTO_CAPABLE 인버터 폐루프 피드백 단절 시나리오는 사이클 2 이관 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 본 사이클은 enum 값 + 결측 정책 + 이상치 기준만 변경. `ai_drvn_mod_h` 모드 전환 이력 신규 사유·`pump_ctrl_h` 제어 로그 영향 없음 (현 시점 두 테이블 모두 백지화 상태 — `domain-abbreviations.md` pump+AI 백지화 사이클 1, 2026-05-12) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `freq` (주파수, frequency) 신규 등록. 기본 데이터 도메인 `DOM_QTY_15_4`, 비고 "swtp 4자 약어 컨벤션 (`prsr`·`flwrt`·`elpwr` 선례) 정합. 인버터 펌프 주파수 측정값 컬럼 조합 재료 (예: `freq_val`·`rated_freq`·`predc_freq`)" + 2026-05-20 등록일
- [x] `.claude/rules/ot-integration.md` — §3 결측 대체값 기준 표에 FQI 행 추가. "주파수 (FQI)" / "직전 Good 값 (Hold Last Value)" / "인버터 가속 시간 (수초~수십초) 누적 안정화, 1분 미만 단절 영향 작음. 5분 초과 시 BAD 격상 (VOI 선례 동형). AI 폐루프 제어 피드백 안전성. tag_frequency_추가 ANALYZE1, 2026-05-20" + 안건 3 이상치 기각 절대값 기준 (`physicalMin=0Hz`/`physicalMax=65Hz`) 비고 통합 기재

## 산출물

- [계획안](../../../plan/20260520/tag_frequency_추가/PLAN1.md) (예정)
