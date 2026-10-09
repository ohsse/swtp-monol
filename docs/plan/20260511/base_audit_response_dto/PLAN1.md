---
status: approved
created: 2026-05-11
updated: 2026-05-11
---
# BaseAuditResponseDto 설계 도입 계획

## 목적

`BaseEntity` 를 상속하는 마스터(`_m`) 엔티티의 응답 DTO 에 공통 메타 4컬럼(`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) 노출을 표준화하는 `BaseAuditResponseDto` 추상 부모 클래스를 도입하여 다음을 달성한다.

1. 마스터 응답 DTO 의 메타 노출 비일관성 해소 (`UserDto` 4개·`TagDto` 2개·다수 0개 → 옵트인 표준화)
2. `@JsonFormat("yyyy-MM-dd HH:mm:ss")` 초 단위 직렬화 SSOT 적용
3. `BaseEntity` → 응답 DTO 메타 매핑 표준 헬퍼 (`protected applyAuditMeta(BaseEntity)`) 도입
4. 응답 DTO 상속 상한 2단 룰 명문화로 향후 3단 진입 자율 차단

## 배경

- ANALYZE1.md (`docs/analyze/20260511/base_audit_response_dto/ANALYZE1.md`) 5인 회의 결과 7건 안건 모두 결론 도출
- SWTP backend 에 `BaseDto` 클래스 부재 (reference 잔재 — `api-patterns.md:70` 의 `extends BaseDto` 는 코드 미실현)
- 응답 DTO 의 메타 노출 비일관: `UserDto`(4개) · `TagDto`(2개) · `AiModeDto`/`PumpStateDto`/`MenuTreeDto`(0개)
- `@JsonFormat` 직렬화 정책 산발 — reference 는 `"HH:mm"` 분 단위, SWTP 는 미적용
- DTO 상속 0건 + `@SuperBuilder` 사용 0건 — 신규 패턴 도입 시 기존 코드와 충돌 없음
- ANALYZE 룰 갱신 지시서 3건 모두 완료 (`api-patterns.md` 2건 + `entity-patterns.md` 1건) — PLAN 진입 전제조건 충족

## 범위

### 포함

1. **신규 클래스 1건**
   - `common/src/main/java/com/mo/swtp/common/dto/BaseAuditResponseDto.java` (abstract)

2. **응답 DTO 마이그레이션 2건**
   - `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` — 4컬럼 직접 보유 → 부모 상속으로 흡수
   - `api/src/main/java/com/mo/swtp/tag/dto/TagDto.java` — 2컬럼 → 부모 상속으로 4컬럼 노출 + `@JsonFormat` 표준 적용

3. **단위 테스트 신규 1건**
   - `common/src/test/java/com/mo/swtp/common/dto/BaseAuditResponseDtoTest.java`

4. **회귀 테스트 검증** (코드 변경 없음, 테스트 PASS 확인)
   - `api/src/test/java/com/mo/swtp/user/service/UserServiceTest.java` (기존 통합 테스트)

### 제외

1. **`AiModeDto`·`PumpStateDto`·`MenuTreeDto`** — ANALYZE 안건 8 결론으로 옵트인 미적용 (도메인 룰 명세·통지·요약 응답)
2. **시계열(`_h`) 응답 DTO** — 시계열 행 자체 메타 무의미
3. **`ai_drvn_mod_p` 마스터 응답 DTO** — 코드 미존재. 향후 도입 시 별도 사이클에서 옵트인 결정
4. **DB 스키마 변경 일체** — BaseEntity 컬럼·인덱스·파티션 무접촉
5. **`rgstr_id`·`updt_id` 표준 용어 사전 정식 등재** — ANALYZE 데드코드 보고 (별도 사이클)
6. **검색 조건 부모 `BaseSearchDto` 도입** — 검색 조건 DTO 3건 이상 누적 시 별도 ANALYZE 진입

## 구현 방향

### 1. `BaseAuditResponseDto` 추상 클래스 (신규)

```java
package com.mo.swtp.common.dto;

@Getter
@Schema(description = "응답 메타 공통 부모 — BaseEntity 의 4컬럼을 응답 DTO 에 일관 노출")
public abstract class BaseAuditResponseDto {

    @Schema(description = "등록 일시", example = "2026-05-11 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rgstrDtm;

    @Schema(description = "수정 일시", example = "2026-05-11 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updtDtm;

    @Schema(description = "등록자 ID", example = "system")
    private String rgstrId;

    @Schema(description = "수정자 ID", example = "system")
    private String updtId;

    /**
     * BaseEntity 의 메타 4컬럼을 응답 DTO 에 적용한다.
     * 자식 DTO 의 정적 팩토리 from(Entity) 에서 호출한다.
     */
    protected void applyAuditMeta(BaseEntity entity) {
        this.rgstrDtm = entity.getRgstrDtm();
        this.updtDtm = entity.getUpdtDtm();
        this.rgstrId = entity.getRgstrId();
        this.updtId = entity.getUpdtId();
    }
}
```

- 패키지: `com.mo.swtp.common.dto` (common 모듈, [`common/CLAUDE.md`](../../../../common/CLAUDE.md) 의 "공통 DTO" 허용 범위)
- abstract — 직접 인스턴스화 금지
- `@SuperBuilder` 미사용 — ANALYZE 안건 7 결론
- `BaseEntity` import 는 `com.mo.swtp.common.domain.BaseEntity` (동일 common 모듈)

### 2. `UserDto` 마이그레이션

기존 `UserDto` 는 4컬럼을 직접 보유. 부모 상속으로 흡수:

```java
@Getter
@Schema(description = "사용자 DTO")
public class UserDto extends BaseAuditResponseDto {

    @Schema(description = "사용자 ID")
    private String userId;
    @Schema(description = "사용자 이름")
    private String userNm;
    @Schema(description = "권한 역할", implementation = UserRole.class)
    private UserRole userRole;
    @Schema(description = "사용 여부", implementation = YnType.class)
    private YnType useYn;
    // rgstrDtm·updtDtm·rgstrId·updtId 필드는 제거 (부모로 흡수)

    private UserDto() {}

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.userId = user.getUserId();
        dto.userNm = user.getUserNm();
        dto.userRole = user.getUserRole();
        dto.useYn = user.getUseYn();
        dto.applyAuditMeta(user);
        return dto;
    }
}
```

> 기존 필드 명·노출 4컬럼 동일 — frontend 호환성 영향 없음. **차이는 `@JsonFormat` 적용으로 인한 직렬화 포맷 표준화만**.

### 3. `TagDto` 마이그레이션

기존 `TagDto` 는 2컬럼(`rgstrDtm`·`updtDtm`) 만 노출 → 4컬럼으로 확장:

```java
@Getter
@Schema(description = "태그 DTO")
public class TagDto extends BaseAuditResponseDto {

    @Schema(description = "태그 시리얼번호")
    private String tagSrlNo;
    @Schema(description = "태그 명칭")
    private String tagNm;
    @Schema(description = "태그 유형 코드", implementation = TagMeasurementType.class)
    private TagMeasurementType tagSeCd;
    @Schema(description = "입출력 코드", implementation = IoCode.class)
    private IoCode ioCd;
    @Schema(description = "사용 여부", implementation = YnType.class)
    private YnType useYn;
    @Schema(description = "태그 설명")
    private String tagDesc;
    // rgstrDtm·updtDtm 필드는 제거 (부모로 흡수, rgstrId·updtId 신규 노출)

    private TagDto() {}

    public static TagDto from(Tag tag) {
        TagDto dto = new TagDto();
        dto.tagSrlNo = tag.getTagSrlNo();
        dto.tagNm = tag.getTagNm();
        dto.tagSeCd = tag.getTagSeCd();
        dto.ioCd = tag.getIoCd();
        dto.useYn = tag.getUseYn();
        dto.tagDesc = tag.getTagDesc();
        dto.applyAuditMeta(tag);
        return dto;
    }
}
```

> **변경점**: `rgstrId`·`updtId` 2개 신규 노출. frontend SPEC{N+1} 갱신 대상 (`태그관리` 슬러그).

### 4. 단위 테스트 (신규)

`BaseAuditResponseDtoTest` — `applyAuditMeta` 헬퍼의 4컬럼 매핑 및 `@JsonFormat` 직렬화 정밀도를 검증.

```java
class BaseAuditResponseDtoTest {

    /** 테스트 전용 자식 DTO */
    static class TestDto extends BaseAuditResponseDto {
        public TestDto(BaseEntity entity) {
            applyAuditMeta(entity);
        }
    }

    @Test
    void applyAuditMeta_가_BaseEntity_의_4컬럼을_모두_매핑한다() { /* ... */ }

    @Test
    void rgstrDtm_과_updtDtm_은_yyyy_MM_dd_HH_mm_ss_초_단위로_직렬화된다() { /* ObjectMapper 기반 직렬화 검증 */ }
}
```

### 5. 룰 갱신 — 이미 완료

ANALYZE 단계에서 3건 룰 갱신을 모두 수행 완료. PLAN 단계에서는 코드와 룰의 정합성만 검증.

- `swtp/backend/.claude/rules/api-patterns.md` — `## BaseAuditResponseDto 패턴` 절 신설 + `DatasetDto extends BaseDto` 예시 정정 (완료)
- `swtp/backend/.claude/rules/entity-patterns.md` — `## BaseEntity ↔ BaseAuditResponseDto 매핑 패턴` 절 신설 (완료)

### 6. PLAN 외부 자동 단계 — `/dev:spec` 시 SPEC 갱신

본 작업 commit 후 `/dev:spec` 호출 시 2개 슬러그의 SPEC{N+1} 자동 갱신:
- `swtp/frontend/docs/api-specs/태그관리/SPEC{N+1}.md` — `TagDto` 의 `rgstrId`·`updtId` 추가, `rgstrDtm`·`updtDtm` 직렬화 포맷 표준화
- `swtp/frontend/docs/api-specs/사용자관리개선/SPEC{N+1}.md` — `UserDto` 의 직렬화 포맷 표준화 (필드 변동 없음)

본 작업의 슬러그(`base_audit_response_dto`) 전용 SPEC 디렉토리 신설은 하지 않음.

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md §4.2`](../../../../../.claude/rules/coding-discipline.md) 적용 — "성능 개선" 같은 모호 목표 금지, 각 기준에 검증 명령·테스트·조회 명시.

| 기준 | 검증 명령 / 테스트 / 조회 |
|-----|----------------------|
| 1. `BaseAuditResponseDto.applyAuditMeta(BaseEntity)` 가 4컬럼 모두 매핑 | `./gradlew.bat :common:test --tests "*BaseAuditResponseDtoTest"` PASS — `applyAuditMeta_가_BaseEntity_의_4컬럼을_모두_매핑한다()` GREEN |
| 2. `@JsonFormat("yyyy-MM-dd HH:mm:ss")` 직렬화 정밀도 | `BaseAuditResponseDtoTest` 의 `rgstrDtm_과_updtDtm_은_yyyy_MM_dd_HH_mm_ss_초_단위로_직렬화된다()` GREEN — `LocalDateTime.of(2026,5,11,10,30,15)` 입력 시 `"2026-05-11 10:30:15"` 출력 매칭 |
| 3. `UserDto` 마이그레이션 후 기존 회귀 없음 | `./gradlew.bat :api:test --tests "*UserServiceTest"` PASS |
| 4. `TagDto` 가 4컬럼 노출 (`rgstrId`·`updtId` 신규 추가) | `TagDto` 직렬화 검증 단위 테스트 GREEN — Tag entity 입력 시 응답 JSON 의 키 집합에 `rgstrId`·`updtId` 포함 |
| 5. 룰 파일 갱신 정합성 (코드 ↔ 룰) | `grep -c "## BaseAuditResponseDto 패턴" .claude/rules/api-patterns.md` → 1 출력. `grep -c "## BaseEntity ↔ BaseAuditResponseDto" .claude/rules/entity-patterns.md` → 1 출력. `grep -c "DatasetDto extends BaseDto" .claude/rules/api-patterns.md` → 0 출력 |
| 6. 전체 빌드 회귀 없음 | `./gradlew.bat clean build` 실행 → `BUILD SUCCESSFUL` 출력 |
| 7. DTO 상속 깊이 2단 준수 | `UserDto`·`TagDto` 의 `extends` 체인은 `BaseAuditResponseDto → Object` 1단 (총 2단). ROOT `coding-discipline.md §2.1` DTO 상속 깊이 3단 임계 미달 |

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md §1`](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 의 가정·미해결 질문을 PLAN 단계 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `BaseAuditResponseDto` 위치는 `common.dto` 패키지 | 결정 | 확정 — `common.dto` 패키지. [`common/CLAUDE.md`](../../../../common/CLAUDE.md) 의 공통 DTO 허용 범위 적용 |
| `@SuperBuilder` 미사용 — `protected applyAuditMeta(BaseEntity)` 헬퍼 메서드 채택 | 결정 | 확정 — ANALYZE 안건 7 결론. 자식 `from()` 정적 팩토리 내부에서 호출 |
| `@JsonFormat` 패턴 `"yyyy-MM-dd HH:mm:ss"` 초 단위 | 결정 | 확정 — ANALYZE 안건 3 결론 |
| 응답 DTO 상속 상한 2단 룰화 | 결정 | 확정 — `api-patterns.md §BaseAuditResponseDto 패턴` 의 §상속 상한 — 2단 절에 명문화 완료 |
| 본 사이클 적용 대상 `UserDto`·`TagDto` 일괄 마이그레이션 | 결정 | 확정 — `AiModeDto`·`PumpStateDto`·`MenuTreeDto` 제외, 시계열(`_h`)·이력 immutable DTO 제외 |
| `UserDto` 의 기존 `@Schema(description=..., example=...)` 어노테이션 그대로 유지 가능 | 가정 | 확정 — 부모 클래스의 `@Schema` 가 자식의 메타 필드 4컬럼에 자동 적용. 자식 `UserDto`/`TagDto` 가 별도로 4컬럼 메타에 `@Schema` 를 작성하지 않음 (Swagger 명세는 부모에서 단일 노출) |
| `BaseAuditResponseDtoTest` 가 `ObjectMapper` 로 JSON 직렬화 검증 시 어떤 `ObjectMapper` 구성을 사용하는가? | 미해결 → 결정 | Spring Boot 기본 `JacksonAutoConfiguration` 의 `ObjectMapper` 와 동등하게 — `JavaTimeModule` 등록 + `WRITE_DATES_AS_TIMESTAMPS=false`. 단순화를 위해 `new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` 직접 구성 (Spring Context 불필요한 단위 테스트) |
| `ai_drvn_mod_p` 마스터 응답 DTO 가 향후 도입될 때 옵트인 적용 여부 | 미해결 | 본 작업 범위 외. `ai_drvn_mod_p` 마스터 응답 DTO 신규 도입 사이클에서 별도 결정 — 운영자 책임 추적 vs `ai_drvn_mod`/`ai_mode_cd` 두 축 동시 노출 frontend 오독 위험의 트레이드오프 |
| 검색 조건 부모 `BaseSearchDto` 도입 시점 | 미해결 | 본 작업과 독립. 검색 조건 DTO 가 3건 이상 누적 시 별도 ANALYZE 트리거 |

## 제외 사항

다음 항목은 본 PLAN 범위 외이며, 별도 작업 사이클에서 다룬다.

1. **`AiModeDto`·`PumpStateDto`·`MenuTreeDto` 등의 옵트인 마이그레이션** — ANALYZE 결론으로 제외 분류 확정
2. **시계열(`_h`) 응답 DTO 의 메타 노출** — 시계열 행 자체 메타 무의미
3. **immutable 이력(`_h`) DTO 가 메타 필요 시 `rgstrDtm`·`rgstrId` 2컬럼 직접 선언 패턴 적용** — 본 작업에 해당 사례 없음. `entity-patterns.md` 룰만 명문화
4. **`@SuperBuilder` 도입 재검토** — ANALYZE 안건 7 보류 결정
5. **검색 조건 부모 `BaseSearchDto` 도입** — 별도 ANALYZE 사이클
6. **`rgstr_id`·`updt_id` 표준 용어 사전 정식 등재** — ANALYZE 데드코드 보고, 별도 사이클
7. **`/dev:spec` 자동 호출** — `/dev:commit` 후 사용자가 명시 호출

## 예상 산출물

- [태스크](../../../tasks/20260511/base_audit_response_dto/TASK1.md) (작성 예정)
