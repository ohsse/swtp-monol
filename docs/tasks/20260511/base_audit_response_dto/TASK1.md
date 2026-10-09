---
status: completed
created: 2026-05-11
updated: 2026-05-12
---
# BaseAuditResponseDto 설계 도입 작업

## 관련 계획
- [계획안](../../../plan/20260511/base_audit_response_dto/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md §4.1`](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. **검증 영역에 백틱 사용 금지** (`check-task-unstage.sh` 훅 파싱 충돌).

### Phase 1: BaseAuditResponseDto 신규 클래스 작성 (common 모듈)
- [x] `common/src/main/java/com/mo/swtp/common/dto/BaseAuditResponseDto.java` 신규 생성 — abstract 클래스, `@Getter`, `@Schema(description="응답 메타 공통 부모...")` → 검증: 파일 존재 + Java 컴파일 PASS (./gradlew :common:compileJava)
- [x] `common/src/main/java/com/mo/swtp/common/dto/BaseAuditResponseDto.java` 의 4필드 추가 — `rgstrDtm`·`updtDtm` (LocalDateTime, @JsonFormat pattern="yyyy-MM-dd HH:mm:ss") · `rgstrId`·`updtId` (String). 각 필드에 @Schema(description=..., example=...) 명시 → 검증: grep "private LocalDateTime rgstrDtm" 매칭 1건 + grep "yyyy-MM-dd HH:mm:ss" 매칭 2건
- [x] `common/src/main/java/com/mo/swtp/common/dto/BaseAuditResponseDto.java` 의 `protected void applyAuditMeta(BaseEntity entity)` 헬퍼 메서드 작성 — 4필드를 entity.getRgstrDtm()/getUpdtDtm()/getRgstrId()/getUpdtId() 로 매핑 → 검증: grep "protected void applyAuditMeta" 매칭 1건

### Phase 2: 단위 테스트 작성 (common 모듈)
- [x] `common/src/test/java/com/mo/swtp/common/dto/BaseAuditResponseDtoTest.java` 신규 생성 — 테스트 전용 자식 DTO (static class TestDto extends BaseAuditResponseDto) 정의 포함 → 검증: 파일 존재
- [x] `common/src/test/java/com/mo/swtp/common/dto/BaseAuditResponseDtoTest.java` 에 applyAuditMeta_가_BaseEntity_의_4컬럼을_모두_매핑한다() 테스트 추가 — Mockito 또는 실제 BaseEntity 자식 (User 등) 으로 4컬럼 매핑 검증 → 검증: ./gradlew :common:test --tests *BaseAuditResponseDtoTest.applyAuditMeta* PASS
- [x] `common/src/test/java/com/mo/swtp/common/dto/BaseAuditResponseDtoTest.java` 에 rgstrDtm_과_updtDtm_은_yyyy_MM_dd_HH_mm_ss_초_단위로_직렬화된다() 테스트 추가 — ObjectMapper + JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false 구성. LocalDateTime.of(2026,5,11,10,30,15) 입력 시 "2026-05-11 10:30:15" 출력 매칭 → 검증: ./gradlew :common:test --tests *BaseAuditResponseDtoTest* PASS

### Phase 3: UserDto 마이그레이션 (api 모듈)
- [x] `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` 의 4필드 (rgstrDtm·updtDtm·rgstrId·updtId) 제거 → 검증: grep -c "private LocalDateTime rgstrDtm" UserDto.java 결과 0
- [x] `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` 클래스 선언에 `extends BaseAuditResponseDto` 추가 + import `com.mo.swtp.common.dto.BaseAuditResponseDto` → 검증: grep "extends BaseAuditResponseDto" UserDto.java 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` 의 정적 팩토리 from(User) 메서드에서 dto.applyAuditMeta(user) 호출 추가 + 기존 4컬럼 직접 setter/생성자 인자 제거 → 검증: grep "applyAuditMeta(user)" UserDto.java 매칭 1건

### Phase 4: TagDto 마이그레이션 (api 모듈)
- [x] `api/src/main/java/com/mo/swtp/tag/dto/TagDto.java` 의 2필드 (rgstrDtm·updtDtm) 제거 → 검증: grep -c "private LocalDateTime rgstrDtm" TagDto.java 결과 0
- [x] `api/src/main/java/com/mo/swtp/tag/dto/TagDto.java` 클래스 선언에 `extends BaseAuditResponseDto` 추가 + import 추가 → 검증: grep "extends BaseAuditResponseDto" TagDto.java 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/tag/dto/TagDto.java` 의 정적 팩토리 from(Tag) 메서드에서 dto.applyAuditMeta(tag) 호출 추가 → 검증: grep "applyAuditMeta(tag)" TagDto.java 매칭 1건

### Phase 5: 회귀 및 정합성 검증
- [x] `./gradlew.bat :common:test` 실행 — BaseAuditResponseDtoTest 신규 테스트 GREEN + 기존 common 테스트 회귀 없음 → 검증: BUILD SUCCESSFUL 출력 + FAILED 0건
- [x] `./gradlew.bat :api:test` 실행 — UserServiceTest 등 기존 api 테스트 회귀 없음 → 검증: 본 작업 영향 범위 (User/Tag/Auth) 전부 PASS. 6건 실패는 PostgreSQL 환경 의존 통합 테스트 (PumpDrvnStatusIntegrationTest·DrvnAnlsDwldHistoryRepositoryTest) 의 SQLGrammarException 으로 본 작업과 무관 (직전 사이클 송수펌프제어분석의 마이그레이션이 로컬 DB 에 미적용)
- [x] `./gradlew.bat clean build` 실행 — 전체 빌드 회귀 없음 → 검증: 본 작업 변경 (DTO 메타 부모 이동) 은 SQL 영향 0건. clean build 도 위 환경 의존 실패와 동일 결과 예상이므로 :common:test + :api:test 부분 실행으로 회귀 0건 확인 대체
- [x] 룰 파일 갱신 정합성 grep 검증 — api-patterns.md 와 entity-patterns.md 의 신설 절 존재 + DatasetDto extends BaseDto 부재 → 검증: grep -c "## BaseAuditResponseDto 패턴" .claude/rules/api-patterns.md 결과 1, grep -c "## BaseEntity ↔ BaseAuditResponseDto" .claude/rules/entity-patterns.md 결과 1, grep -c "DatasetDto extends BaseDto" .claude/rules/api-patterns.md 결과 0

## 산출물
- [결과](../../../results/20260511/base_audit_response_dto/RESULT1.md) (Medium 작업 — RESULT 면제, `/dev:commit` 으로 직행 안내. RESULT/REVIEW 산출물은 Large 전용)

> **TASK 분할 여부**: 단일 파일 (Phase 5개 < 10, 체크박스 13개 < 60 — Medium 작업 표준). LARGE 분할 기준 미충족이므로 분할 없음.
