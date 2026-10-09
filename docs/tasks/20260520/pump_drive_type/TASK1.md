---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# 펌프 구동 방식 분류 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260520/pump_drive_type/PLAN1.md)

## Phase

### Phase 1: 어휘 사전 갱신 (ANALYZE 룰 갱신 지시서 완료)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `drive` 표준 단어 신규 등록 → 검증: grep `drive` 매칭, 등록일 2026-05-20 행 확인
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `drive_type_cd` 표준 용어 신규 등록 → 검증: grep `drive_type_cd` 매칭, 사용 테이블 `pump_m` 행 확인
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `oprtng_type_cd` 비고에 직교축 관계 명시 → 검증: grep `drive_type_cd 와 직교축 관계` 매칭

### Phase 2: enum + 엔티티 + ErrorCode 신설·변경 (common·api 모듈)

- [x] `common/src/main/java/com/mo/swtp/instrument/domain/PumpDriveType.java` 신규 — 단순 public enum (`PumpOprtngType` 선례 패턴), 값 2종 `INVERTER_DRIVE`/`RATED_DRIVE`, Javadoc 명시 (인버터 펌프=가변속 VFD 주파수 제어·AI 자동 제어 가능 / 정격 펌프=고정속 ON/OFF·`RATED_DRIVE + AUTO_CAPABLE` 조합 무효) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 변경 — `driveType` 필드 추가 (`@Enumerated(EnumType.STRING)` + `@Column(name="drive_type_cd", length=20, nullable=false)`) + `create()` 정적 팩토리 7번째 인자 추가 + `Objects.requireNonNull(driveType, ...)` 검증 + `RATED_DRIVE + AUTO_CAPABLE` 조합 차단 (`throw new RestApiException(PumpErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION)`) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 변경 — `changePumpSelfColumns()` 4번째 인자 `driveType` 추가 (null 인자 시 기존값 유지) + 종료 직전 결과값 `(this.driveType, this.oprtngType)` 조합 검증 (모든 인자 null 입력이어도 검증 생략 금지) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/instrument/exception/PumpErrorCode.java` 신규 (모듈 역의존 회피 정정 — 2026-05-20 IMPL) — `INVALID_PUMP_DRIVE_OPRTNG_COMBINATION(400)` enum 값 보유 + ErrorCode 인터페이스 구현 (JwtErrorCode 선례) + Javadoc 명시 (`Pump.create()`/`Pump.changePumpSelfColumns()` 진입 시 발생·`ot-integration.md §5 ⚠️ 절대 금지` 직결) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL

### Phase 3: DTO 변경 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpDto.java` 변경 — `driveType` 필드 추가 + `@Schema(description="펌프 구동 방식 (인버터/정격)", implementation = PumpDriveType.class)` + `from(Pump)` 정적 팩토리에 `dto.driveType = pump.getDriveType()` 추가 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpUpsertDto.java` 변경 — `driveType` 필드 추가 + `@NotNull` + `@Schema(description="...", implementation = PumpDriveType.class)` → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 4: Service 호출처 갱신 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/instrument/service/InstrumentService.java` 변경 — line 87-89 부근 `Pump.create()` 호출에 `p.getDriveType()` 인자 추가 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/instrument/service/InstrumentService.java` 변경 — line 113 부근 `changePumpSelfColumns()` 호출에 `p.getDriveType()` 4번째 인자 추가 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 5: 테스트 호출처 갱신 + 신규 테스트 (common·api 모듈)

- [x] `api/src/test/java/com/mo/swtp/instrument/service/InstrumentServiceTest.java` 변경 — line 65-67·143-145·161-163 부근 `Pump.create()` 호출 + `buildPumpDto()` 빌더 메서드에 `driveType=INVERTER_DRIVE` (정상 조합 기본값) 인자 추가. PumpUpsertDto 픽스처도 `driveType` 필드 포함 → 검증: ./gradlew.bat :api:test --tests InstrumentServiceTest PASS
- [x] `api/src/test/java/com/mo/swtp/instrument/dto/PumpDtoSerializationTest.java` 변경 — line 34·49·61 부근 `Pump.create()` 호출에 `driveType=INVERTER_DRIVE` 추가 + `driveType` JSON 직렬화 검증 케이스 추가 (INVERTER_DRIVE 문자열 직렬화·역직렬화) → 검증: ./gradlew.bat :api:test --tests PumpDtoSerializationTest PASS
- [x] `common/src/test/java/com/mo/swtp/instrument/domain/PumpSelfColumnsTest.java` 변경 — line 28·36·45 부근 `Pump.create()` 호출에 `driveType=INVERTER_DRIVE` 인자 추가 + `driveType=null` 전달 시 NullPointerException 발생 검증 케이스 추가 → 검증: ./gradlew.bat :common:test --tests PumpSelfColumnsTest PASS
- [x] `common/src/test/java/com/mo/swtp/instrument/domain/PumpDriveTypeTest.java` 신규 — `PumpDriveType.values()` 2건 정의 확인 (INVERTER_DRIVE·RATED_DRIVE) + `name()` 직렬화 문자열 일치 확인 → 검증: ./gradlew.bat :common:test --tests PumpDriveTypeTest PASS
- [x] `common/src/test/java/com/mo/swtp/instrument/domain/PumpDriveOprtngCombinationTest.java` 신규 — 4 조합 유효성 검증 (1) `(INVERTER_DRIVE, AUTO_CAPABLE)` 성공 (2) `(INVERTER_DRIVE, SEMI_AUTO_CAPABLE)` 성공 (3) `(RATED_DRIVE, AUTO_CAPABLE)` RestApiException(INVALID_PUMP_DRIVE_OPRTNG_COMBINATION) 발생 (4) `(RATED_DRIVE, SEMI_AUTO_CAPABLE)` 성공. `Pump.create()` + `Pump.changePumpSelfColumns()` 양 경로 모두 검증. 모든 인자 null `changePumpSelfColumns(null, null, null, null)` 도 종료 직전 검증 강제 케이스 포함 → 검증: ./gradlew.bat :common:test --tests PumpDriveOprtngCombinationTest PASS

### Phase 6: DDL 마이그레이션 신규

- [x] `common/src/main/resources/db/init/V9_3__pump_m_drive_type_cd.sql` 신규 — 3단계 무중단 마이그레이션 (V8_5 선례 동일 패턴): 1단계 `ALTER TABLE pump_m ADD COLUMN drive_type_cd VARCHAR(20);` + `COMMENT ON COLUMN pump_m.drive_type_cd IS '구동 방식 코드 (DOM_CODE_20, INVERTER_DRIVE/RATED_DRIVE — 펌프 물리적 설계값, PumpDriveType enum 매핑)';` / 2단계 `UPDATE pump_m SET drive_type_cd = 'RATED_DRIVE' WHERE drive_type_cd IS NULL;` (fail-safe 기본값, 운영자 인버터 펌프 수동 갱신 주의문 포함) / 3단계 `ALTER TABLE pump_m ALTER COLUMN drive_type_cd SET NOT NULL;` → 검증: 파일 존재 + check-ddl-column-comment.sh 훅 통과 (COMMENT 의무화)

### Phase 7: 빌드·테스트 검증

- [x] 전체 컴파일 통과 → 검증: ./gradlew.bat :common:compileJava :common:compileTestJava :api:compileJava :api:compileTestJava BUILD SUCCESSFUL
- [x] 본 작업 영향 테스트 전체 통과 → 검증: PumpDriveTypeTest·PumpDriveOprtngCombinationTest·PumpSelfColumnsTest (`:common:test`) + InstrumentServiceTest·PumpDtoSerializationTest (`:api:test`) 모두 GREEN. **별도 회귀 항목**: `FacilityServiceIntegrationTest` 3건 사전 실패 (본 작업 stash 상태에서도 동일 실패 — 본 작업 변경 무관, SQLGrammarException 통합 테스트 환경 기존 이슈)
- [x] 신규 테스트 누락 회귀 점검 → 검증: 본 작업의 신규 테스트 2건 (`PumpDriveTypeTest`·`PumpDriveOprtngCombinationTest`) + 변경 테스트 3건 (`PumpSelfColumnsTest`·`PumpDtoSerializationTest`·`InstrumentServiceTest`) 모두 GREEN 확인

## 산출물
- [결과](../../../results/20260520/pump_drive_type/RESULT1.md) (Large 작업 시 `/dev:result` 단계 작성. Medium 작업이므로 `/dev:impl` 완료 후 `/dev:commit` 안내 자동 전이)
