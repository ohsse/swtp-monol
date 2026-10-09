---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# 태그관리 — CRUD API 구현 계획

## 목적

SCADA 태그 마스터 (`tag_m`) 의 운영 가능한 CRUD 5개 엔드포인트를 신규 도입한다 (모두 ADMIN 전용). 동시에 태그 도메인의 정규화·일관성을 보강한다 — `unit_cd` 컬럼 폐기 + `TagMeasurementType` enum 단위 매핑 + OPS·VOI enum 신규 도입 + 논리 삭제 (`use_yn`) 지원.

## 배경

- ANALYZE: [`docs/analyze/20260508/태그관리/ANALYZE1.md`](../../../analyze/20260508/태그관리/ANALYZE1.md) (status: approved, 6 안건 회의 완료)
- 기 결정 자산: 마스터도메인설계 ANALYZE1 Round 3 (2026-05-03) 의 `Tag` 엔티티·`V6_4__tag_master_table.sql`·`TagMeasurementType`·`IoCode` enum·`TagRepository`(단건 조회만) 도입 완료 상태
- plan mode 단계 사용자 결정 4건:
  1. **GET 도 ADMIN 전용** — 5개 엔드포인트 모두 `roleGuard.requireAdmin(request)`
  2. **use_yn 추가 + 논리 삭제** — `rawdata_1m_h.tag_srl_no` 논리 참조 보존
  3. **단순 전체 조회 + ID 단건** — TagSearchDto / CustomRepository 불필요
  4. **`unit_cd` 폐기 + `TagMeasurementType` enum 단위 매핑 (옵션 A) + OPS/VOI 흡수** — 측정 유형별 1:1 고정값 정규화. V9_1 단일 마이그레이션에 use_yn ADD + unit_cd DROP 합본
- 송수펌프제어분석 사이클의 OPS·VOI enum 추가 미결 결정을 본 사이클로 흡수 (단일 PR 진입)

## 범위

### 포함

- `Tag` 엔티티 수정 (필드 추가·제거·메서드 추가)
- `TagMeasurementType` enum 확장 (description+unit 필드 + OPS·VOI 추가)
- 신규 마이그레이션 `V9_1__tag_m_use_yn_and_drop_unit_cd.sql`
- 신규 클래스 6건: `TagController`·`TagService`·`TagDto`·`TagUpsertDto`·`TagErrorCode` + `TagServiceTest`
- `TagRepository` 메서드 추가: `findAllByOrderByUseYnDescTagSrlNoAsc()`
- `TagMeasurementTypeTest` 신규 — enum 단위 매핑 단언
- 표준 용어 사전 갱신 — 9건 (8건 ANALYZE 작성 중 반영 완료, 1건 line 67 `unit_cd` 행 제거 + 폐기 이력 등록은 본 PLAN approved 시점 또는 직전에 반영 완료)

### 제외

- 측정값 API DTO 에 태그 메타 임베드 (HMI 일반 USER 시나리오 보강) — 별도 사이클
- `TagMeasurementType` 의 향후 단위 다양화 (예: 초소형 펌프 L/min) — 본 사이클은 7종 (FRI/PRI/LEI/PWI/RMS/OPS/VOI) 만 다룸
- `instrument_m` 자식 테이블 (Pump 등) 직접 변경 — 본 사이클 대상 외
- `Tag.changeInfo()` 시그니처에 instrument 인자 추가 — 안건 4 블로커 (계측기 교체는 deactivate + create 플로우)

## 구현 방향

### 1. 도메인 계층 (`common` 모듈)

#### Tag 엔티티 변경

```java
@Entity
@Table(name = "tag_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Tag extends BaseEntity implements Persistable<String> {

    @Id
    @Column(name = "tag_srl_no", length = 50, nullable = false)
    private String tagSrlNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_se_cd", length = 20, nullable = false)
    private TagMeasurementType tagSeCd;

    @Column(name = "tag_desc", columnDefinition = "TEXT")
    private String tagDesc;

    // unitCd 필드 제거 — TagMeasurementType.getUnit() 매핑으로 대체

    @Enumerated(EnumType.STRING)
    @Column(name = "io_cd", length = 20, nullable = false)
    private IoCode ioCd;

    @Enumerated(EnumType.STRING)                       // 신규
    @Column(name = "use_yn", length = 1, nullable = false)
    private YnType useYn;

    public static Tag create(String tagSrlNo, Instrument instrument,
                             TagMeasurementType tagSeCd, String tagDesc, IoCode ioCd) {
        // unitCd 인자 제거 — enum 매핑
        return new Tag(tagSrlNo, instrument, tagSeCd, tagDesc, ioCd, YnType.Y);
    }

    public void changeInfo(TagMeasurementType tagSeCd, String tagDesc, IoCode ioCd) {
        // instrument 인자 미포함 (안건 4 블로커), unitCd 인자 제거 (안건 6)
        if (tagSeCd != null) this.tagSeCd = tagSeCd;
        if (tagDesc != null) this.tagDesc = tagDesc;
        if (ioCd != null) this.ioCd = ioCd;
    }

    public void deactivate() {
        this.useYn = YnType.N;
    }

    @Override public String getId() { return tagSrlNo; }
}
```

#### TagMeasurementType enum 확장

```java
@Getter
@RequiredArgsConstructor
public enum TagMeasurementType {
    FRI("유량",     "m³/h"),
    PRI("압력",     "kgf/cm²"),
    LEI("수위",     "m"),
    PWI("전력",     "kW"),
    RMS("진동",     ""),
    OPS("가동상태", ""),     // 신규 — boolean DI 신호
    VOI("개도율",   "%");    // 신규

    private final String description;
    private final String unit;
}
```

### 2. 영속 계층 (`api` 모듈)

#### TagRepository 메서드 추가

```java
public interface TagRepository extends JpaRepository<Tag, String> {
    Optional<Tag> findByInstrumentInstrumentId(String instrumentId);  // 기존
    List<Tag> findAllByOrderByUseYnDescTagSrlNoAsc();                 // 신규 — User 선례 정합
}
```

`existsById(String)` 은 JpaRepository 기본 메서드 활용.

### 3. 서비스 계층 (`api` 모듈)

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    private final InstrumentRepository instrumentRepository;

    @Transactional
    public void registerTag(TagUpsertDto dto) {
        if (tagRepository.existsById(dto.getTagSrlNo())) {
            throw new RestApiException(TagErrorCode.DUPLICATE_TAG_SRL_NO);
        }
        Instrument instrument = instrumentRepository.findById(dto.getInstrumentId())
                .orElseThrow(() -> new RestApiException(TagErrorCode.INVALID_INSTRUMENT_ID));
        Tag tag = Tag.create(dto.getTagSrlNo(), instrument,
                dto.getTagSeCd(), dto.getTagDesc(), dto.getIoCd());
        tagRepository.save(tag);
    }

    public List<TagDto> findAllTags() {
        return tagRepository.findAllByOrderByUseYnDescTagSrlNoAsc().stream()
                .map(TagDto::from).toList();
    }

    public TagDto findTag(String tagSrlNo) {
        // 활성·비활성 모두 반환 — ADMIN 이 비활성 태그 메타 검토·재활성화 결정 필요 (도메인 검토 권고 1 반영)
        // ANALYZE1 §적용할 패턴 의 'findActiveTag — use_yn=Y 만' 표현은 권고 1 사유로 'findTag — 전체 반환' 으로 정정됨
        Tag tag = tagRepository.findById(tagSrlNo)
                .orElseThrow(() -> new RestApiException(TagErrorCode.TAG_NOT_FOUND));
        return TagDto.from(tag);
    }

    @Transactional
    public void modifyTag(String tagSrlNo, TagUpsertDto dto) {
        Tag tag = tagRepository.findById(tagSrlNo)
                .orElseThrow(() -> new RestApiException(TagErrorCode.TAG_NOT_FOUND));
        // instrument_id 변경은 무시 (안건 4 — 계측기 교체는 deactivate+create 플로우)
        tag.changeInfo(dto.getTagSeCd(), dto.getTagDesc(), dto.getIoCd());
    }

    @Transactional
    public void deactivateTag(String tagSrlNo) {
        Tag tag = tagRepository.findById(tagSrlNo)
                .orElseThrow(() -> new RestApiException(TagErrorCode.TAG_NOT_FOUND));
        tag.deactivate();
    }
}
```

### 4. 웹 계층 (`api` 모듈)

```java
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Tag(name = "10. 태그 관리")
public class TagController extends CommonController {

    private final TagService tagService;
    private final RoleGuard roleGuard;

    @PostMapping
    @Operation(summary = "태그 등록")
    public ResponseEntity<CommonResponseDto<Void>> registerTag(
            HttpServletRequest request, @RequestBody @Valid TagUpsertDto dto) {
        roleGuard.requireAdmin(request);
        tagService.registerTag(dto);
        return getResponseEntity();
    }
    // GET (전체) / GET (단건) / PUT / DELETE 동일 패턴 — 모두 첫 줄 roleGuard.requireAdmin(request)
}
```

### 5. DTO

`TagDto` 에 `unit` 필드 추가 — `TagMeasurementType.getUnit()` 매핑 결과 노출:

```java
@Data
@NoArgsConstructor
@Schema(description = "태그 응답 DTO")
public class TagDto {
    private String tagSrlNo;
    private String instrumentId;
    @Schema(implementation = TagMeasurementType.class) private TagMeasurementType tagSeCd;
    private String description;     // tagSeCd.getDescription() 매핑
    private String unit;             // tagSeCd.getUnit() 매핑 — 본 사이클 신규
    private String tagDesc;
    @Schema(implementation = IoCode.class) private IoCode ioCd;
    @Schema(implementation = YnType.class) private YnType useYn;
    private LocalDateTime rgstrDtm;

    public static TagDto from(Tag tag) {
        TagDto dto = new TagDto();
        dto.tagSrlNo = tag.getTagSrlNo();
        dto.instrumentId = tag.getInstrument().getInstrumentId();
        dto.tagSeCd = tag.getTagSeCd();
        dto.description = tag.getTagSeCd().getDescription();
        dto.unit = tag.getTagSeCd().getUnit();
        dto.tagDesc = tag.getTagDesc();
        dto.ioCd = tag.getIoCd();
        dto.useYn = tag.getUseYn();
        dto.rgstrDtm = tag.getRgstrDtm();
        return dto;
    }
}
```

`TagUpsertDto` 는 `tagSrlNo`·`instrumentId`·`tagSeCd`·`tagDesc`·`ioCd` 필드 보유 (`useYn` 제외 — 등록 시 `Y` 강제, 삭제는 별도 endpoint).

## 도메인 모델

### 신규/변경 엔티티

| 엔티티/테이블 | 역할 | 주요 필드 (변경 사항) |
|------------|------|------------------|
| `Tag` (`tag_m`) | SCADA 태그 마스터 | `useYn` 추가 (`@Enumerated(EnumType.STRING)`), `unitCd` 제거, `Tag.create(...)` 시그니처 변경, `Tag.changeInfo(...)` 시그니처 변경 (unitCd 인자 제거), `deactivate()` 메서드 추가 |
| `TagMeasurementType` (enum) | 측정 유형 + 단위 매핑 | `description`·`unit` String 두 필드 추가. 기존 5개 (FRI/PRI/LEI/PWI/RMS) 에 매핑 + OPS("가동상태", "")·VOI("개도율", "%") 신규 |

### 도메인 룰

- 측정 유형 ↔ 단위 1:1 매핑 (`ot-integration.md §3` 결측 대체값 표 정합) — 향후 다양화 시 새 enum 값 추가
- `Tag.changeInfo()` 의 instrument 인자 추가 금지 (계측기 교체는 deactivate + create 플로우)
- 논리 삭제 — `rawdata_1m_h.tag_srl_no` 논리 참조 보존
- 도메인 4영역 (알람·인터록·운전 모드·이력 기록) 모두 비해당 — Tag CRUD 는 마스터 메타 관리 전용 (ANALYZE1 §도메인 룰 4영역 점검 참조)
- **OPS·VOI enum 추가의 알람 4단계 영향 무관 명시** (도메인 검토 권고 2 반영) — `ot-integration.md §3` 의 OPS 결측 대체값 정책 ("UNCERTAIN 알람 즉시 발생 후 BAD 격상" 특수 정책) 은 SCADA 수신 파이프라인의 결측 처리 로직에 적용되며, 본 사이클의 enum 추가 (TagMeasurementType 의 description+unit 필드 매핑) 는 알람 평가 로직과 무접촉. enum 추가만으로 알람 4단계 평가 룰 변경 없음 — 알람 평가 룰은 `pump_interlock_p`·`alarm_h` 등 별도 도메인 코드에서 관리됨

## DB 설계 변경

### V9_1 마이그레이션 (신규 합본)

**파일**: `common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql`

```sql
-- (1) use_yn 컬럼 ADD — 3단계 무중단 절차
-- 1단계: NULL 허용 추가 (즉시 완료, 락 없음)
ALTER TABLE tag_m ADD COLUMN use_yn VARCHAR(1);

-- 2단계: 백필 — 운영 데이터 0건 가정 (스테이징·운영 일관성을 위해 절차 명시)
UPDATE tag_m SET use_yn = 'Y' WHERE use_yn IS NULL;

-- 3단계: NOT NULL 전환
ALTER TABLE tag_m ALTER COLUMN use_yn SET NOT NULL;

-- (2) unit_cd 컬럼 DROP — 안건 6 결정 (TagMeasurementType enum 단위 매핑으로 대체)
ALTER TABLE tag_m DROP COLUMN unit_cd;

-- (3) COMMENT
COMMENT ON COLUMN tag_m.use_yn IS '사용 여부 (DOM_YN, YnType enum — Y·N, 논리 삭제 지원, rawdata_1m_h.tag_srl_no 논리 참조 보존)';
```

### DDL 정책 정합

| 정책 | 적용 |
|------|------|
| `DOM_YN` SQL 타입 | `VARCHAR(1)` (CHAR(1) 금지) ✓ |
| `DOM_YN` CHECK 제약 | 미적용 — `@Enumerated(EnumType.STRING)` + `YnType` enum 단일 방어선 ✓ |
| `DOM_YN` DEFAULT | 미설정 — Java 정적 팩토리 `Tag.create(...)` 가 SSOT (`YnType.Y` 명시) ✓ |
| `DOM_YN` 단독 인덱스 | 미적용 — 카디널리티 2, 옵티마이저 Seq Scan 선호 ✓ |
| Flyway checksum | V6_4 직접 갱신 회피, V9_1 신규 마이그레이션 ✓ |
| COMMENT 의무화 훅 | 신규 컬럼 `use_yn` COMMENT 동시 추가, `db/migration/` 글롭 매칭. **본 마이그레이션은 `ALTER TABLE` 만 포함 — 훅 (`check-ddl-column-comment.sh`) 의 `CREATE TABLE` 인라인 형식 인식 대상 외, 차단 없음 예상** ✓ |
| 무중단 마이그레이션 | 3단계 절차 정식 작성 (운영 데이터 0건 가정으로 백필 라인은 보존되나 영향 없음) ✓ |
| **DROP COLUMN 시 COMMENT 자동 정리** | PostgreSQL 의 `DROP COLUMN unit_cd` 는 해당 컬럼의 `COMMENT ON COLUMN` 을 자동 제거 — 별도 `COMMENT ON COLUMN tag_m.unit_cd IS NULL` 문장 불필요 (DBA 검토 참고 2 반영) ✓ |
| **DROP COLUMN 락 영향** | `ALTER TABLE ... DROP COLUMN` 은 `ACCESS EXCLUSIVE` 락 획득하나 DDL 처리 시간 (밀리초) 매우 짧음. 본 사이클은 운영 데이터 0건 + 코드 도입 전 환경 전제 — 운영 환경 적용 시 별도 유지보수 창 권고. TASK 체크박스에 운영 적용 주의 문구 명시 (DBA 검토 참고 1 반영) ✓ |

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 |
|------|--------|
| 빌드 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL |
| common 모듈 빌드 | `./gradlew.bat :common:build` PASS — Tag·TagMeasurementType QClass 재생성 |
| api 모듈 단위 테스트 | `./gradlew.bat :api:test` PASS — `TagServiceTest` 6건 + `TagMeasurementTypeTest` 2건 GREEN |
| enum 단위 매핑 동작 | `TagMeasurementTypeTest`: `FRI.getUnit()="m³/h"`, `PRI.getUnit()="kgf/cm²"`, `LEI.getUnit()="m"`, `PWI.getUnit()="kW"`, `RMS.getUnit()=""`, `OPS.getUnit()=""`, `VOI.getUnit()="%"` 단언 + `values().length == 7` 단언 |
| 정상 등록 흐름 | `TagServiceTest.정상_등록`: `tagRepository.save(any(Tag.class))` 호출 verify, `tag.getUseYn() == YnType.Y` 단언 |
| 중복 등록 차단 | `TagServiceTest.중복_등록_차단`: 동일 `tag_srl_no` 등록 시 `RestApiException(DUPLICATE_TAG_SRL_NO)` |
| FK 검증 | `TagServiceTest.instrument_미존재_차단`: `instrumentRepository.findById` empty 시 `RestApiException(INVALID_INSTRUMENT_ID)` 발생, `tagRepository.save` 미호출 verify |
| 미존재 단건 조회 | `TagServiceTest.미존재_조회_시_404`: `findById` empty 시 `RestApiException(TAG_NOT_FOUND)` |
| 수정 동작 | `TagServiceTest.수정_정상`: `tag.changeInfo` 가 호출되어 필드 갱신 단언 |
| 논리 삭제 동작 | `TagServiceTest.논리_삭제`: `tag.deactivate()` 호출 후 `tag.getUseYn() == YnType.N` 단언 |
| ADMIN 권한 강제 | 수동 검증 — USER role JWT 로 POST/GET/PUT/DELETE 호출 시 403 FORBIDDEN |
| Swagger 노출 | `:api:bootRun` 후 `/swagger-ui.html` 에 `10. 태그 관리` Tag + 5 엔드포인트 + `TagDto.unit` 필드 + enum (TagMeasurementType/IoCode/YnType) implementation 명시 |
| ErrorCode 훅 통과 | `TagErrorCode.java` 저장 시 `check-errorcode-contract.sh` 차단 없음 (String 필드 0건) |
| DDL COMMENT 훅 통과 | V9_1 SQL 저장 시 `check-ddl-column-comment.sh` 차단 없음 (use_yn 컬럼 COMMENT 동시 추가) |
| DDL 적용 결과 | psql `\d+ tag_m` 결과: unit_cd 컬럼 부재 + `use_yn varchar(1) NOT NULL` + COMMENT 표시 |
| 표준 용어 사전 정합 | `grep "tag_m" backend/.claude/rules/dict/standard-terms.md` 에서 `unit_cd` 행 부재 (폐기 이력으로 이전), `use_yn` 행에 `tag_m` 매칭 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| Swagger Tag 번호 `10. 태그 관리` 채택 | **결정** | 송수펌프제어분석 사이클의 `08·09` 사용 중 + `01~05` 기존 사용. 다음 비어있는 번호 10 채택 (UI 영향 없음, 트리비얼) |
| `findAllTags()` 정렬: `findAllByOrderByUseYnDescTagSrlNoAsc()` | **결정** | User 선례 정합 — Y 우선 정렬 + 비활성 포함 (use_yn='N' 인 태그도 응답에 포함하여 ADMIN 이 비활성 태그도 식별 가능) |
| `TagController` 의 `findTag(tagSrlNo)` 단건 조회 — `use_yn='N'` 도 반환 vs 활성만 반환 | **결정** | 활성·비활성 모두 반환 (ADMIN 이 비활성 태그 메타 검토·재활성화 결정 필요). `findActiveTag` 명명 회피, `findTag` 사용 |
| `TagUpsertDto.useYn` 필드 보유 여부 | **결정** | 미보유 — 등록 시 `Y` 강제 (Tag.create 정적 팩토리 SSOT). 비활성화는 DELETE endpoint, 재활성화는 별도 endpoint 검토 (본 사이클 외) |
| 비활성 태그 재활성화 endpoint | **미해결** | 본 사이클 범위 외 — DELETE 후 재활성화 필요 시 별도 사이클로 추가. 현 시점은 ADMIN 이 SQL 직접 갱신 가능 (마스터 데이터 운영 가능) |
| HMI 일반 USER 의 태그 메타 의존 시나리오 | **미해결** | 본 사이클 범위 외 — 측정값 API DTO 임베드 (Option A) vs 단건 분리 (Option B) 별도 사이클 보강 |
| 운영 데이터 0건 환경에서 V9_1 백필 단계 라인 보존 | **결정** | 스테이징·운영 일관성 — 동일 SQL 적용 시 데이터 존재 가능성 대비 (백필 라인이 무영향이라도 보존) |
| 향후 측정 유형 다양화 (예: 초소형 펌프 L/min) | 가정 | 본 사이클은 7종만 다룸. 새 enum 값 추가 시 `FRI_L_MIN("유량(L/min)", "L/min")` 형태로 별도 enum (FRI 와 단위만 다른 별도 측정 유형으로 취급) |

## 제외 사항

- 측정값 API (`/api/rawdata/...`) DTO 변경 — 본 사이클은 마스터 CRUD 만
- HMI USER role 태그 메타 조회 권한 — ANALYZE 안건 3 권고로 별도 사이클
- `pump_interlock_p` 등 인터록 룰의 `tag_srl_no` 참조 — 본 사이클 비영향 (Tag 마스터 변경이 인터록 룰 평가에 영향 없음, ANALYZE 도메인 4영역 점검 참조)
- TagMeasurementType OPS·VOI 의 알람 4단계 평가 룰 — `ot-integration.md §3·§5` 본 사이클 비변경 (결측 대체값 표는 송수펌프제어분석 사이클에서 이미 갱신됨)

## 예상 산출물

- [태스크 (작성 예정)](../../../tasks/20260508/태그관리/TASK1.md)

---

## 부록: 도메인/DB 검토 결과

`/dev:plan` 단계의 도메인·DB 검토 게이트 실행 결과 (2026-05-08).

### wtp-domain-expert 검토 — 블로커 0건 / 권고 2건 / 참고 1건

**통과 항목**:
- `Tag.changeInfo()` instrument 인자 추가 금지 — ANALYZE1 안건 4 블로커 정합 유지
- `use_yn` 논리 삭제 ↔ `rawdata_1m_h.tag_srl_no` 논리 참조 보존 명확 연결
- `unitCd` 인자 제거의 도메인 의미 자연스러움 (단위는 측정 유형으로 결정)
- 도메인 4영역 비해당 4건 모두 구체 사유 명기 — 형식적 충족 패턴 해당 없음
- OPS("가동상태", "")·VOI("개도율", "%") 가 `ot-integration.md §3` 결측 대체값 표 정합

**권고 1 (반영 완료)** — 단건 조회 비활성 반환 정책 명확화: §구현 방향 `findTag()` 코드 주석에 "활성·비활성 모두 반환" 명시 + ANALYZE1 의 `findActiveTag` 표현 정정 사유 기록

**권고 2 (반영 완료)** — OPS BAD 격상 특수 정책과 비해당 판정 연결 명기: §도메인 모델 §도메인 룰 마지막 항목에 "enum 추가 ↔ 알람 평가 로직 무접촉" 1줄 추가

**참고 1**: Swagger Tag 번호 10 채택 — 트리비얼, 추적 기록만

### wtp-dba-reviewer 검토 — 블로커 0건 / 권고 0건 / 참고 2건

**통과 항목**:
- `DOM_YN` DDL 정책 4항목 (VARCHAR(1)·CHECK 미적용·DEFAULT 미설정·단독 인덱스 미적용) 모두 정합
- 3단계 무중단 마이그레이션 절차 (`indexing-and-migration.md §2`) 준수
- `ADD COLUMN ... NOT NULL DEFAULT val` 한 줄 패턴 회피 (절대 금지 항목 회피)
- `rawdata_1m_h.tag_srl_no` 논리 참조 시계열 → 마스터 FK 금지 정책 정합
- Flyway 버전 격리 V6_4 회피 + V8_x 시리즈 충돌 회피 + V9_1 신규
- `check-ddl-column-comment.sh` 훅 통과 예상 (`ALTER TABLE` 만 포함, `CREATE TABLE` 인식 대상 외)
- 인덱스 영향 없음 (`idx_tag_m_instrument_id` 보존, `unit_cd` 단독·복합 인덱스 미존재)

**참고 1 (반영 완료)** — DROP COLUMN `ACCESS EXCLUSIVE` 락 명시: §DDL 정책 정합 표에 "락 획득 짧음 + 운영 적용 시 유지보수 창 권고" 행 추가

**참고 2 (반영 완료)** — DROP COLUMN 시 COMMENT 자동 정리: §DDL 정책 정합 표에 "PostgreSQL 자동 제거" 행 추가

### 종합 결론

블로커 0건 — 본 PLAN 승인 차단 사유 없음. 권고 2건·참고 2건 모두 PLAN 본문 정정으로 해소 완료. TASK 단계 진입 가능.
