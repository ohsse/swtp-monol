---
status: completed
created: 2026-05-12
updated: 2026-05-12
---
# 펌프조작유형 — TASK1

## 관련 계획

- [계획안](../../../plan/20260512/펌프조작유형/PLAN1.md)
- [분석](../../../analyze/20260512/펌프조작유형/ANALYZE1.md)

> **PLAN1.md 보정 사항** (작성 후 발견): `Pump.java` 가 이미 `com.mo.swtp.instrument.domain` 패키지로 이관 완료된 상태이므로 본 TASK 의 체크박스는 `instrument/domain/` 경로 사용 (PLAN1.md "## 구현 방향" 의 `pump/domain/` 표기는 ANALYZE1.md 안건 3 결론 "instrument 이관 시 동행" 의 사전 표현).
>
> **백지화 컨텍스트 보정** (2026-05-12 IMPL): pump+AI 도메인 백지화 사이클 1 인지로 `com.mo.swtp.pump`·`com.mo.swtp.ai` 패키지 백지화 확인 — `api/src/test/.../pump/` 디렉토리 미존재. PLAN1.md Phase 3 의 4건 중 3건 (PumpMasterCacheServiceTest·PumpAnalysisDashboardServiceTest·PumpControlIntegrationTest) 갱신 불필요. backend/CLAUDE.md "패키지 도입 현황 / 재설계 예정" 섹션 + ot-integration.md §2·§5 보류 박스에 사이클 2 재설계 명시.

## Phase

### Phase 1: PumpOprtngType enum 신설

- [x] `backend/common/src/main/java/com/mo/swtp/instrument/domain/PumpOprtngType.java` 신규 작성 (AUTO_CAPABLE / SEMI_AUTO_CAPABLE 2값 + Javadoc — 펌프의 물리적 설계값, 시스템 상태 enum 과 의미 분리 명시) → 검증: ./gradlew.bat :common:compileJava 성공

### Phase 2: Pump 엔티티 변경

- [x] `backend/common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 자식 전용 필드 oprtngType 추가 (@Enumerated EnumType.STRING + @Column name=oprtng_type_cd nullable=false length=20) → 검증: ./gradlew.bat :common:compileJava 성공
- [x] `backend/common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` Pump.create() 정적 팩토리 시그니처에 PumpOprtngType 인자 추가 + 생성자 인자 전달 + Objects.requireNonNull 검증 + Javadoc 백지화 컨텍스트 명시 → 검증: ./gradlew.bat :common:compileJava 성공 + Pump 생성자 시그니처 검증

### Phase 3: test 호출처 갱신 (1건 — 백지화 보정)

> **백지화 컨텍스트 보정** (2026-05-12 IMPL 단계): pump+AI 도메인 백지화로 `backend/api/src/test/java/com/mo/swtp/pump/` 디렉토리 미존재 — 종전 3건 (PumpMasterCacheServiceTest·PumpAnalysisDashboardServiceTest·PumpControlIntegrationTest) 갱신 대상 아님. PumpSelfColumnsTest 1건만 유효 (common 모듈 `com.mo.swtp.instrument.domain` 패키지 존재).

- [x] `backend/common/src/test/java/com/mo/swtp/instrument/domain/PumpSelfColumnsTest.java` Pump.create() 호출 3건에 PumpOprtngType.AUTO_CAPABLE 인자 추가 + oprtngType null 검증 테스트 1건 추가 + getOprtngType() 매핑 검증 → 검증: ./gradlew.bat :common:test --tests *PumpSelfColumnsTest* GREEN

### Phase 4: V8_5 마이그레이션 SQL

- [x] `backend/common/src/main/resources/db/init/V8_5__pump_m_oprtng_type.sql` 신규 작성 (3단계: ADD COLUMN VARCHAR(20) NULL + UPDATE 백필 AUTO_CAPABLE + ALTER SET NOT NULL + COMMENT ON COLUMN 의무) → 검증: 로컬 PostgreSQL 마이그레이션 순차 실행 + SELECT COUNT(*) FROM pump_m WHERE oprtng_type_cd IS NULL 결과 0 + check-ddl-column-comment.sh 훅 통과

### Phase 5: 단위 테스트 + 빌드 검증

- [x] `backend/common/src/test/java/com/mo/swtp/instrument/domain/PumpOprtngTypeTest.java` 신규 작성 (enum 값 2건 존재·name() 매핑 검증) → 검증: ./gradlew.bat :common:test --tests *PumpOprtngTypeTest* GREEN
- [x] `./gradlew.bat clean build` 실행 성공 확인 → 검증: BUILD SUCCESSFUL 출력 + 회귀 테스트 0건 실패

## 산출물

- 본 사이클은 Medium 분류 — RESULT/REVIEW 면제 (transitions.md /dev:impl 행). IMPL 완료 후 곧장 `/dev:commit` 진입.
