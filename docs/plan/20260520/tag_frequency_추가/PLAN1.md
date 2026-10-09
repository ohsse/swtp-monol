---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# tag_frequency_추가 — 계획

## 목적

`TagMeasurementType` enum 에 신규 측정 유형 `FQI` (Frequency Indicator, 주파수, 단위 Hz) 8번째 값을 추가하여 인버터 펌프 (`PumpDriveType.INVERTER_DRIVE`) 주파수(Hz) 측정값 부재 일관성 공백을 해소한다. 동시에 `PumpDriveType.RATED_DRIVE` 펌프 또는 비-Pump 계측기에 FQI 태그 등록 시도를 서비스 레이어에서 차단하여 도메인 안전성을 보장한다 ([`ot-integration.md §5 ⚠️ 절대 금지`](../../../../.claude/rules/ot-integration.md) 직결).

## 배경

- 2026-05-20 `pump_drive_type` ANALYZE1 에서 `drive_type_cd = INVERTER_DRIVE` 도입. 인버터 펌프는 VFD 주파수 제어로 회전수 조절 — 주파수 측정값 부재는 폐루프 피드백 부재 위협.
- 현 `TagMeasurementType` 7종 (FRI·PRI·LEI·PWI·RMS·OPS·VOI) — 주파수 누락.
- [`ANALYZE1`](../../../analyze/20260520/tag_frequency_추가/ANALYZE1.md) 5인 회의 결론: enum 값 1건 + ErrorCode 1건 + 서비스 검증 추가. DDL 변경 0건 (`DOM_QTY_15_4` 재사용, DBA 2차 승인 완료).
- 룰 갱신 2건 완료 (ANALYZE1 룰 갱신 지시서 §[x]):
  - `swtp/.claude/rules/dict/standard-words.md` — `freq` 표준 단어 등록
  - `.claude/rules/ot-integration.md §3` — 결측 대체값 표에 FQI 행 추가 (Hold Last Value + 5분 초과 BAD 격상)

## 범위

### 포함

- `TagMeasurementType.FQI("주파수", "Hz")` enum 값 추가
- `TagErrorCode.FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP` 신규 (httpStatus 400)
- `TagService.registerTag` / `modifyTag` 에 FQI 태그 등록 차단 검증 추가
- `TagMeasurementTypeTest` 갱신 (`hasSize(7)` → `hasSize(8)` + FQI assertion 2건)
- `TagServiceTest` 신규 시나리오 2건 (RATED_DRIVE 펌프 차단 / 비-Pump 계측기 차단)
- `docs/ddl/tag.sql` SSOT 사본의 enum 코드값 목록 설명에 FQI 보강 (DDL 변경 없음, 주석/설명만)

### 제외

- V8 patch 마이그레이션 SQL 신규 생성 (DDL 변경 0건)
- 신규 표준 데이터 도메인 등록 (`DOM_QTY_15_4` 재사용)
- 신규 표준 용어 (DB 컬럼) 등록 (DB 컬럼 0건)
- `INVERTER_DRIVE` 펌프 FQI 태그 등록 의무화 — 사이클 2 인터록 설계로 이관
- AUTO_CAPABLE 인버터 펌프 FQI 단독 BAD 인터록 룰 — 사이클 2 이관
- FQI BAD 전용 알람 2단계(경보) 룰 신설 — 사이클 2 이관
- `TagRangeConfig` 실제 등록 데이터 투입 (SCADA 인프라 미구현 — 정책 명시만)
- frontend SPEC4 작성 — `/dev:commit` 후 `/dev:spec 태그관리` 별도 호출

## 도메인 모델

신규 엔티티·테이블·필드는 없으며 enum 값과 ErrorCode 만 추가된다 (도메인 모델 검토 게이트 스킵 — 신규 엔티티 0건 + DB 설계 변경 0건).

| 변경 대상 | 위치 | 변경 내용 |
|---------|------|---------|
| `TagMeasurementType` enum | `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` | `FQI("주파수", "Hz")` 8번째 값 추가. `@Getter` + `@RequiredArgsConstructor` 불변 패턴 유지 |
| `TagErrorCode` enum | `api/src/main/java/com/mo/swtp/tag/exception/TagErrorCode.java` | `FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP(400)` 추가. `httpStatus(int)` 단일 필드 규약 ([`exception-patterns.md §2`](../../../../.claude/rules/exception-patterns.md)) 준수 |
| `TagService` 검증 로직 | `api/src/main/java/com/mo/swtp/tag/service/TagService.java` | `registerTag`·`modifyTag` 진입 직후 `tagSeCd == FQI && (instrument !instanceof Pump \|\| pump.driveType == RATED_DRIVE)` 시 `FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP` throw |

## DB 설계 변경

**없음**.

- DDL 변경 0건 — `tag_se_cd VARCHAR(20) NOT NULL` 컬럼 정의가 `FQI` 3자 수용. DB CHECK 제약 없음 (`indexing-and-migration.md §3.2` enum 단일 방어선 정책). OPS/VOI 추가 선례 (태그관리 ANALYZE1, 2026-05-08) 동일.
- V8 patch 파일 신규 생성 없음.
- `docs/ddl/tag.sql` SSOT 사본은 enum 코드값 목록 설명 부분에 `FQI` 행 보강 (주석/설명만, DDL 본문 변경 0).
- DBA 2차 승인 완료 ([`ANALYZE1`](../../../analyze/20260520/tag_frequency_추가/ANALYZE1.md) 안건 6): `DOM_QTY_15_4` 재사용 (30.0~60.0Hz 정밀도 손실 없음, 신규 `DOM_*` 불필요). 인덱스 영향 무시할 수준 (카디널리티 7→8).

> DB 설계 변경 게이트 스킵 — `wtp-dba-reviewer` 추가 검토 불필요 (ANALYZE 안건 6 검토 결과 적용).

## 구현 방향

### Phase 1: enum 값 추가 (common 모듈)

- `TagMeasurementType.FQI("주파수", "Hz")` 추가 — Javadoc 도입 이력 절에 "tag_frequency_추가 ANALYZE1, 2026-05-20" 명기
- 결측 대체값 정책 (Hold Last Value + 5분 초과 BAD 격상, `physicalMin=0Hz`/`physicalMax=65Hz` 절대값 기준) 은 `ot-integration.md §3` 가 SSOT 이므로 enum Javadoc 에는 "결측 대체값 정책은 ot-integration.md §3 참조" 만 명기 (정책 본문 복제 금지)

### Phase 2: ErrorCode 추가 (api 모듈)

- `TagErrorCode.FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP(400)` — Javadoc 으로 "FQI 측정 유형은 `PumpDriveType.INVERTER_DRIVE` 펌프 계측기에만 허용. RATED_DRIVE 펌프 + 비-Pump 계측기 차단" 명시

### Phase 3: TagService 검증 로직 추가 (api 모듈)

- `private static void validateFqiTagAllowance(Tag` `Instrument instrument, TagMeasurementType tagSeCd)` 보조 메서드 도입 — `registerTag` 와 `modifyTag` 양쪽에서 호출 (DRY)
- 분기: `tagSeCd != FQI` → 통과. `instrument instanceof Pump pump && pump.getDriveType() == INVERTER_DRIVE` → 통과. 그 외 → `FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP` throw
- Pattern matching (Java 21 `instanceof` + pattern variable) 사용 — `coding-discipline.md §2` 단순성 우선 정합
- `modifyTag` 의 경우 기존 instrument 와 새 tagSeCd 조합 검증 (instrument 교체는 본 메서드 시그니처상 불가 — 태그관리 ANALYZE1 안건 4 결정 유지)

### Phase 4: 테스트 갱신

- `TagMeasurementTypeTest` — `hasSize(7)` → `hasSize(8)` 갱신 + `assertThat(FQI.getDescription()).isEqualTo("주파수")` + `assertThat(FQI.getUnit()).isEqualTo("Hz")` assertion 추가
- `TagServiceTest` 시나리오 추가:
  - `RATED_DRIVE_펌프에_FQI_태그_등록_시_예외가_발생한다` → `FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP`
  - `비_Pump_계측기에_FQI_태그_등록_시_예외가_발생한다` (예: Valve 계측기) → 동일 ErrorCode
  - `INVERTER_DRIVE_펌프에_FQI_태그_등록은_허용된다` (positive 경로)
  - `FQI_외_측정유형은_RATED_DRIVE_펌프에도_허용된다` (회귀 방지)

### Phase 5: docs/ddl/tag.sql SSOT 사본 갱신

- `tag_se_cd` 컬럼 COMMENT 또는 인접 주석에 enum 코드값 목록을 노출하는 부분이 있으면 `FQI` 추가
- 주석/설명만 변경 — DDL 본문 변경 0
- `indexing-and-migration.md §5.3` 양쪽 동시 갱신 의무 (운영본 `V8__tag.sql` 변경 없음, SSOT 사본만 보강)

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령·테스트·조회 |
|------|------------------|
| `TagMeasurementType.FQI` enum 값 추가 + description/unit 매핑 | `./gradlew.bat :common:test --tests TagMeasurementTypeTest` PASS — `hasSize(8)` + FQI description "주파수" + unit "Hz" assertion GREEN |
| `TagErrorCode.FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP` 신규 등록 | grep "FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP" `api/src/main/java/com/mo/swtp/tag/exception/TagErrorCode.java` 매칭 + httpStatus 400 |
| `TagService.registerTag` 가 RATED_DRIVE 펌프 FQI 등록 차단 | `./gradlew.bat :api:test --tests TagServiceTest.RATED_DRIVE_펌프에_FQI_태그_등록_시_예외가_발생한다` PASS — `FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP` 발생 검증 |
| `TagService.registerTag` 가 비-Pump 계측기 FQI 등록 차단 | `./gradlew.bat :api:test --tests TagServiceTest.비_Pump_계측기에_FQI_태그_등록_시_예외가_발생한다` PASS |
| `TagService.registerTag` 가 INVERTER_DRIVE 펌프 FQI 등록 허용 | `./gradlew.bat :api:test --tests TagServiceTest.INVERTER_DRIVE_펌프에_FQI_태그_등록은_허용된다` PASS |
| `TagService.modifyTag` 도 동일 검증 적용 | `./gradlew.bat :api:test --tests TagServiceTest.modify*` 통과 — `modifyTag` 의 FQI 차단 회귀 테스트 추가 |
| 기존 7종 측정 유형 회귀 없음 | `./gradlew.bat :api:test --tests TagServiceTest` 전체 PASS (기존 시나리오 GREEN 유지) |
| 빌드 전체 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL — common·api·scheduler 3 모듈 모두 |
| ROOT 표준 단어 사전 `freq` 등록 | grep "`freq`" `swtp/.claude/rules/dict/standard-words.md` 매칭 + 한글 논리명 "주파수" 동행 |
| `ot-integration.md §3` 결측 대체값 표 FQI 행 추가 | grep "주파수 (FQI)" `.claude/rules/ot-integration.md` 매칭 + "Hold Last Value" 동행 |
| `docs/ddl/tag.sql` SSOT 사본 FQI 보강 | grep "FQI" `docs/ddl/tag.sql` 매칭 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| FQI 측정 유형은 `Pump` 자식 계측기 + `INVERTER_DRIVE` 만 허용 — RATED_DRIVE 펌프 + 비-Pump 계측기 모두 단일 ErrorCode `FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP` 로 차단 | 결정 | ANALYZE 안건 4 결론 적용. 단순성 원칙 — 두 차단 사유를 분리 ErrorCode 로 나누지 않음 (`coding-discipline.md §2`) |
| `validateFqiTagAllowance` 보조 메서드는 `TagService` private static 으로 두고 별도 `TagValidator` 컴포넌트로 분리하지 않음 | 결정 | ANALYZE 단계 미결 — 검증 로직 단일 메서드 + 단일 분기 (instanceof + driveType) 만 존재. `coding-discipline.md §2.1` 정량 기준 영향 0건. 검증 로직 누적 시점에 별도 컴포넌트 추출 검토 (현 시점 과설계) |
| `TagRangeConfig.physicalMin=0Hz`/`physicalMax=65Hz` 절대값 기준은 ANALYZE 결정으로 `ot-integration.md §3` 에 명시 완료 — 본 PLAN 의 구현 범위 외 | 결정 | SCADA 인바운드 어댑터·`TagRangeConfig` 등록 데이터 투입은 OT 인프라 도입 사이클에서 처리. 본 사이클은 enum 값 + 검증 로직만 |
| INVERTER_DRIVE 펌프 FQI 태그 등록 의무화 (필수화) | 미해결 | 사이클 2 인터록 설계로 이관. SCADA 인프라 미구현 상태이므로 본 사이클 권고 수준 |
| AUTO_CAPABLE 인버터 펌프 FQI 단독 BAD (5분 초과) 시 "제어 불가 상태" 인터록 룰 | 미해결 | 사이클 2 인터록 설계로 이관. `last_rcv_dtm` 전체 SCADA 중단 트리거와 별도 룰 필요 |
| FQI BAD 전용 알람 2단계(경보) 룰 | 미해결 | 사이클 2 인터록·알람 룰 설계로 이관 |
| frontend SPEC4 갱신 (`태그관리` 슬러그) | 미해결 → 결정 | `/dev:commit` 후 별도 단계 — `/dev:spec 태그관리` 호출로 자동 추출. 본 PLAN 범위 외 |

## 제외 사항

- DDL 마이그레이션 SQL (V8 patch) — DDL 변경 0건
- 신규 데이터 도메인 등록 — `DOM_QTY_15_4` 재사용
- 신규 DB 컬럼 — 본 사이클은 enum 값 + 검증 로직만
- SCADA 인바운드·OT 어댑터 — 현 시점 미구현 영역 (`ot-integration.md §1·§2` 보류)
- AI 추론 서버 연동 — `ot-integration.md §6` 보류 (pump+AI 백지화 사이클 1, 2026-05-12)
- `TagValidator` 별도 컴포넌트 추출 — 검증 로직 누적 시점 재검토 (`coding-discipline.md §2`)
- frontend SPEC4 갱신 — `/dev:commit` 후 별도 `/dev:spec` 단계

## 예상 산출물

- [태스크](../../../tasks/20260520/tag_frequency_추가/TASK1.md) (예정)
