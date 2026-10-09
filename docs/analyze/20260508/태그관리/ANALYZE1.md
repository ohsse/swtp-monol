---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# 태그관리 — 도메인 분석

## 작업 배경

사용자가 `/dev 태그관리` 사이클로 SCADA 태그 마스터 (`tag_m`) 의 CRUD API 를 신규 도입하려 한다. **태그 엔터티 자체의 설계 (PK·필드·자식 관계)** 는 이미 마스터도메인설계 ANALYZE1 Round 3 (2026-05-03, status: approved) 에서 확정되어 코드 도입 완료 상태 (`Tag.java` + `V6_4__tag_master_table.sql` + `IoCode`·`TagMeasurementType` enum + `TagRepository`). 본 사이클은 그 결정 위에 **운영 가능한 CRUD 5개 엔드포인트 + 논리 삭제 (use_yn) + 표준 용어 사전 정합** 을 더해 완성한다.

본 ANALYZE 작성 중 plan mode 재진입 단계에서 사용자 결정 1건이 추가되어 총 4건이 확보되었다 (`~\.claude\plans\dev-eager-melody.md`):
1. **GET 도 ADMIN 전용** — 모든 CRUD 5개 엔드포인트가 `roleGuard.requireAdmin(request)` 적용
2. **use_yn 컬럼 추가 + 논리 삭제** — DELETE 호출 시 `tag.deactivate()` (rawdata_1m_h 가 `tag_srl_no` 를 논리 참조하므로 시계열 데이터 고아 위험 회피)
3. **단순 전체 조회 + ID 단건** — TagSearchDto / CustomRepository 불필요
4. **`tag_m.unit_cd` 컬럼 폐기 + `TagMeasurementType` enum 단위 매핑** — 측정 유형별 단위가 1:1 고정값임을 사용자가 발견, 레코드별 컬럼 보유는 데이터 관리·정규화 측면 부적절. 옵션 A 채택 (enum 생성자에 description+unit String 직접 매핑). OPS·VOI enum 도 본 사이클에서 함께 추가 (송수펌프제어분석 사이클 미결 결정 흡수). DDL 변경은 V9_1 신규 마이그레이션에 합본 (use_yn ADD + unit_cd DROP)

### 외부 산출물

본 사이클은 외부 산출물 (요구사항 명세서·다이어그램) 없이 사용자 채팅 기반 + 기 결정 사항 (마스터도메인설계 ANALYZE1 Round 3 + plan mode 결정) 으로 진행.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: `tag_m.use_yn` 컬럼 신설 (논리 삭제 지원)

- 호출 에이전트: `wtp-glossary-manager` (필수 — 표준 용어 갱신), `wtp-dba-reviewer` (필수 — DDL 마이그레이션 방식 + 정책 정합)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 단어/데이터 도메인/표준 용어 0건. `standard-terms.md` line 25 `use_yn` 행 사용 테이블에 `tag_m` 추가만 필요 ("비활성 운영 시나리오가 존재하는 `_m` 테이블" 정책 부합 — `rawdata_1m_h` 의 `tag_srl_no` 논리 참조로 물리 삭제 시 고아 위험 실재).
  - **wtp-dba-reviewer**: DDL 정책 4개 항목 (VARCHAR(1)·CHECK 미적용·DEFAULT 미설정·단독 인덱스 미적용) 모두 정합. **마이그레이션 옵션 B (신규 V9_1)** 권장 — V6_4 직접 갱신은 Flyway checksum 위반 위험 (스테이징·운영). 파일 경로: `common/src/main/resources/db/migration/V9_1__tag_m_use_yn.sql` (V8_x 시리즈는 송수펌프제어분석 ANALYZE1 사용 중). 3단계 무중단 절차 정식 작성 권고 (운영 데이터 0건 환경에서 백필만 생략 가능).
- **결론**: `use_yn VARCHAR(1) NOT NULL` 컬럼 신설. 신규 마이그레이션 SQL `V9_1__tag_m_use_yn.sql` 신설 (`db/migration/` 위치). 3단계 무중단 절차 (NULL 추가 → 백필 생략 가능 명시 → NOT NULL 전환). DDL CHECK·DEFAULT·단독 인덱스 모두 미적용. `Tag.create(...)` 정적 팩토리에서 `useYn = YnType.Y` 명시 할당 (SSOT). `Tag.deactivate()` 메서드 추가.

### 안건 2: `io_cd` 확정 + `tag_m` 관련 (예정) 마커 정리

- 호출 에이전트: `wtp-glossary-manager` (필수 — 표준 용어 갱신)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `Tag.java` (line 66~67) + `V6_4` DDL 모두 `io_cd` (DOM_CODE_20) + `IoCode` enum (INPUT/OUTPUT/BIDIR) 채택 도입 완료 확인. `standard-terms.md` line 68 `io_yn 또는 io_cd` 미결 행은 코드 도입 완료 상태에서 사전만 미반영. `tag_nm`·`tag_se_cd`·`tag_desc`·`tag_srl_no`·`unit_cd` 5건의 `(예정)` 마커도 모두 코드 도입 완료. `io_yn` 분류는 **등록 거부** (코드 미도입 상태에서 대안 폐기).
- **결론**: 표기 정리 5건 (예정 마커 제거) + line 68 `io_cd` 단일 확정 행 교체 + `io_yn` 등록 거부 이력 추가. 신규 단어/데이터 도메인 0건.

### 안건 3: GET 도 ADMIN 전용 결정의 도메인 정합성

- 호출 에이전트: `wtp-domain-expert` (필수 — 운영 시나리오 영향)
- Round 1 답변 요약:
  - **wtp-domain-expert**: 도메인 4영역 (알람·인터록·운전 모드·이력 기록) 와 충돌 없음. 단 **일부 우려** — `UserController` 단건 조회 (`/api/users/{userId}`) 는 ADMIN 전용 아님 (선례 불일치). HMI 대시보드의 일반 USER 가 측정값 차트 라벨·알람 발생 태그명 등으로 태그 메타 의존하는 시나리오 미확인. PLAN 단계 보강 권고: Option A (측정값 응답 DTO 에 태그 메타 임베드) 또는 Option B (`GET /api/tags/{tagSrlNo}` 단건만 인증 사용자 허용).
- **결론**: 본 사이클은 사용자 결정대로 **GET 도 ADMIN 전용** 5개 엔드포인트 모두 `roleGuard.requireAdmin(request)` 적용. 보강 권고는 **가정 섹션** 에 명시하여 PLAN 단계에서 측정값 API DTO 임베드 방식 도입 가능성 결정 (본 사이클 외 — 측정값 API 변경은 별도 사이클).

### 안건 4: `instrument_id` FK 검증 의무화 + Service 패턴

- 호출 에이전트: `wtp-backend-engineer` (필수 — 계층 책임·SOLID·정량 기준)
- Round 1 답변 요약:
  - **wtp-backend-engineer**:
    - **블로커 (높음)**: `Tag.changeInfo()` 에 `instrument` 인자 추가 **금지** — 도메인 의미상 태그는 특정 계측기에 묶인 SCADA 식별자, 계측기 교체는 새 `tag_srl_no` 발급 또는 `deactivate + create` 플로우가 정합. `ot-integration.md §5` 이력 기록 의무와도 정합.
    - **권고 (중간) 1**: Service 레벨 FK 명시 검증 권장 — `instrumentRepository.existsById(dto.getInstrumentId())` 후 `TagErrorCode.INVALID_INSTRUMENT_ID(400)` 변환. DB FK 위반 시 `DataIntegrityViolationException` → 500 노출은 `exception-patterns.md §1 응답 계약` 위반.
    - **권고 (중간) 2**: DTO 위치는 `UserUpsertDto` 실제 위치 확인 후 결정. `common/CLAUDE.md` 는 "도메인 command DTO 는 common 허용" 명문화이나 실 선례 우선.
    - **참고 (낮음)**: `findAllTags()` 의 `use_yn` 정책 — User 선례 (`findAllByOrderByUseYnDescUserIdAsc` — 활성 우선 정렬, 비활성 포함) 재사용 권장.
- 오케스트레이터 보강 검증: `UserUpsertDto`·`MenuUpsertDto` 실 위치 모두 **api 모듈** (`api/src/main/java/com/mo/swtp/{도메인}/dto/`) 확인 — DTO 위치는 api 모듈로 확정.
- **결론**:
  - `Tag.changeInfo()` 시그니처는 `(tagDesc, unitCd, ioCd)` 3 인자 유지 (instrument 인자 추가 금지)
  - Service `registerTag()` / `modifyTag()` 모두 instrument_id 변경 시점에 `instrumentRepository.existsById` 명시 검증 후 `INVALID_INSTRUMENT_ID(400)` 변환
  - `TagUpsertDto` 위치: `api/src/main/java/com/mo/swtp/tag/dto/TagUpsertDto.java` (User·Menu 선례 정합)
  - `findAllTags()` 정렬: `findAllByOrderByUseYnDescTagSrlNoAsc()` (User 선례 재사용)

### 안건 5: DDL 마이그레이션 파일 위치·번호 (안건 1 답변에 통합)

- 호출 에이전트: `wtp-dba-reviewer` (안건 1 통합 호출)
- **결론**: 안건 1 회의록 참조. `common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql`. V8_x 시리즈는 송수펌프제어분석 ANALYZE1 사용 중. `db/init/` 가 아닌 `db/migration/` (COMMENT 의무화 훅 적용 글롭 매칭). **안건 6 결정 흡수** — use_yn ADD 와 unit_cd DROP 을 단일 마이그레이션 파일에 합본.

### 안건 6: `tag_m.unit_cd` 컬럼 폐기 + `TagMeasurementType` enum 단위 매핑 (plan mode 재진입 추가 안건)

- 호출 에이전트: 4 에이전트 모두 (plan mode 재진입 단계의 사용자 결정 — 본 안건은 사후 회의록 정합화)
- **사용자 발견**: `tag_m.unit_cd` 컬럼은 측정 유형별 1:1 고정값 (`ot-integration.md §3` 결측 대체값 표 — FRI=m³/h, PRI=kgf/cm², LEI=m, PWI=kW, RMS=단위 없음, OPS=boolean, VOI=%). 레코드마다 unit_cd 보유는 데이터 관리·정규화 측면 부적절.
- 정합화 답변 요약 (사용자 결정 사후 검증):
  - **wtp-glossary-manager**: `unit_cd` 표준 용어는 사용 테이블에서 `tag_m` 제거 → 사용 테이블 0건. **즉시 폐기 처리** (코드 도입 전 변경 — 안전). `unit` 표준 단어는 보존 (다른 컬럼 조합 재료 가능). 폐기 이력 항목 추가 의무.
  - **wtp-dba-reviewer**: V9_1 단일 마이그레이션에 `ALTER TABLE tag_m DROP COLUMN unit_cd` 합본 가능. 운영 데이터 0건 가정으로 백필 부재. Flyway checksum 안전. 인덱스 영향 없음 (unit_cd 단독 인덱스 미존재). 마이그레이션 파일명 변경 권고: `V9_1__tag_m_use_yn_and_drop_unit_cd.sql`.
  - **wtp-backend-engineer**: enum 매핑 옵션 A (생성자에 description+unit String 직접) 단순·읽기 쉬움 권장. 옵션 B (단위 별도 enum 추출) 는 코드량 증가 + 향후 SI 표기·환산 계수 도입 가능성이 명확할 때만 고려. 빈 문자열 (`""`) 로 "단위 없음" 처리 (Optional 회피). `Tag.changeInfo()` 시그니처에서 `unitCd` 인자 제거 (안건 4 정합 — 계측기 변경 금지 정책과 동일 원칙으로 단위는 enum 결정).
  - **wtp-domain-expert**: 측정 유형 ↔ 단위 1:1 가정의 도메인 정합성 검증 — `ot-integration.md §3` 결측 대체값 표가 측정 유형별 단위를 명시 (FRI=m³/h 등). 향후 단위 변경 사례는 새 enum 값 추가로 대응 가능 (예: 초소형 펌프의 L/min 단위 도입 시 `FRI_L_MIN("유량(L/min)", "L/min")` 별도 enum). OPS·VOI 본 사이클 흡수는 송수펌프제어분석 사이클의 enum 추가 작업과 충돌 없음 (단일 PR 로 깔끔히 진입).
- **결론**:
  - `tag_m.unit_cd` 컬럼 **즉시 폐기** (코드 도입 전 변경 안전)
  - `TagMeasurementType` enum 의 생성자에 `description`·`unit` String 두 필드 추가 (옵션 A)
  - OPS("가동상태", "")·VOI("개도율", "%") 신규 enum 값 본 사이클에서 함께 추가
  - V9_1 마이그레이션에 use_yn ADD + unit_cd DROP 합본
  - `Tag.changeInfo()` 시그니처에서 `unitCd` 인자 제거 — 새 시그니처: `changeInfo(instrument, tagSeCd, tagDesc, ioCd)` (instrument 인자도 안건 4 결정으로 제거 후보 검토 필요 — 본 안건은 unitCd 만 다룸)
  - 표준 용어 사전 갱신: line 67 `unit_cd` 의 사용 테이블 컬럼에서 `tag_m` 제거 + 비고 갱신 + 폐기 이력 섹션에 `unit_cd` 즉시 폐기 행 추가

---

## 표준 사전 카탈로그

> 본 ANALYZE 는 **신규 표준 단어/표준 데이터 도메인/표준 용어 0건** — 모든 변경이 (1) 사용 테이블 목록 갱신, (2) `(예정)` 마커 제거, (3) `io_cd` 단일 확정 행 교체, (4) `unit_cd` 사용 테이블 제거 (폐기 처리), (5) 폐기 이력 추가 의 5가지 표기 정리로 해결.

### 신규 표준 단어

없음. **`unit` 단어 보존** (다른 컬럼 조합 재료 가능 — 안건 6 wtp-glossary-manager 결정).

### 신규 표준 데이터 도메인

없음. `DOM_YN`·`DOM_CODE_20` 모두 기존 재사용 (DBA 2차 승인 불필요 — 타입·길이 변경 없음).

### 신규 표준 용어

없음. **표기 정리 7건** (기존 재사용 + 폐기 분류 혼합):

| 물리명 | 갱신 내용 | 분류 | 결정 근거 |
|--------|---------|------|----------|
| `use_yn` (line 25) | 사용 테이블에 `tag_m` 추가 | 기존 재사용 | 안건 1 결정 — `tag_m` 이 "비활성 운영 시나리오 `_m`" 정책 부합 |
| `tag_nm` (line 28) | `tag_m(예정)` → `tag_m` (예정 마커 제거) | 기존 재사용 | 안건 2 결정 — Tag.java + V6_4 도입 완료 |
| `tag_se_cd` (line 29) | `tag_m(예정)` → `tag_m` | 기존 재사용 | 동일 |
| `tag_desc` (line 30) | `tag_m(예정)` → `tag_m` | 기존 재사용 | 동일 |
| `tag_srl_no` (line 66) | `tag_m(예정)` → `tag_m` (rawdata_1m_h 예정 유지) | 기존 재사용 | 동일 |
| **`unit_cd` (line 67)** | **사용 테이블에서 `tag_m` 제거 + 비고 갱신 (`TagMeasurementType` enum 매핑으로 대체)** | **폐기·통합** | **안건 6 결정 — 측정 유형별 1:1 고정값, enum 단위 매핑으로 정규화** |
| `io_cd` (line 68 교체) | `io_yn 또는 io_cd` 미결 행 → `io_cd` 단일 확정 | 기존 재사용 | 안건 2 결정 — IoCode enum 채택 코드 도입 완료 |

### 폐기 이력 추가

| 물리명 / 후보 | 분류 | 사유 |
|-------------|------|------|
| `io_yn` | **등록 거부** | 코드 미도입 상태 대안 폐기, 마스터도메인설계 ANALYZE1 Round 3 미결 → 본 사이클 `io_cd` 확정 (안건 2) |
| `unit_cd` | **즉시 폐기** | 사용 테이블 0건 (코드 도입 전 변경 안전) — `TagMeasurementType` enum 단위 매핑 채택, 측정 유형별 1:1 고정값 정규화 (안건 6) |

분류값: 신규 / 기존 재사용 / 유사 충돌 / 폐기·통합

---

## 신규 엔티티/DB 컬럼

### 신규 컬럼

| 컬럼 | 테이블 | 타입 | 데이터 도메인 | NULL | 비고 |
|------|------|------|-------------|------|------|
| `use_yn` | `tag_m` | VARCHAR(1) | DOM_YN | NOT NULL | YnType enum, 정적 팩토리 `Tag.create(...)` 에서 `YnType.Y` 명시 할당. DDL CHECK·DEFAULT·단독 인덱스 미적용 |

### 폐기 컬럼

| 컬럼 | 테이블 | 사유 |
|------|------|------|
| `unit_cd` | `tag_m` | 안건 6 결정 — 측정 유형별 1:1 고정값, `TagMeasurementType` enum 단위 매핑으로 대체. V9_1 마이그레이션에서 DROP COLUMN |

### 신규 엔티티

없음 — `Tag` 엔티티는 도입 완료.

**Tag 엔티티 수정** (3건):
- `useYn` 필드 추가 (안건 1)
- **`unitCd` 필드 제거** (안건 6) — `TagMeasurementType` enum 매핑으로 대체
- `deactivate()` 메서드 추가 (안건 1)
- `Tag.create(...)` 정적 팩토리: `useYn = YnType.Y` 명시 할당 + `unitCd` 인자 제거 (안건 1+6)
- `Tag.changeInfo(...)` 시그니처: `(instrument, tagSeCd, tagDesc, ioCd)` — `unitCd` 인자 제거 (안건 6). instrument 인자는 **유지**(안건 4 블로커는 instrument 추가 금지였고, 본 시점에 changeInfo 가 이미 instrument 받지 않는 시그니처라면 그대로 — 본 사이클은 unitCd 만 제거)

**TagMeasurementType enum 수정** (안건 6):
- 생성자에 `description`·`unit` String 두 필드 추가 (`@Getter @RequiredArgsConstructor` 패턴)
- 기존 5개 값 (FRI/PRI/LEI/PWI/RMS) 에 description+unit 매핑
- **신규 OPS·VOI 추가** — OPS("가동상태", ""), VOI("개도율", "%")
- 빈 문자열 (`""`) = "단위 없음" (RMS·OPS)

### 신규 마이그레이션 SQL 파일

| 파일 | 내용 |
|------|------|
| `common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql` | (1) `use_yn` 컬럼 ADD (3단계 무중단 절차 — NULL 허용 추가 → 백필 생략 명시 → NOT NULL 전환). (2) `unit_cd` 컬럼 DROP. (3) `COMMENT ON COLUMN tag_m.use_yn` 의무. V8_x 시리즈는 송수펌프제어분석 ANALYZE1 사용 중 |

### 신규 Controller·Service·Repository·DTO·ErrorCode

| 클래스 | 위치 | 책임 |
|------|------|------|
| `TagController` | `api/src/main/java/com/mo/swtp/tag/web/` | `08+` Tag 번호 (PLAN 결정), 5개 엔드포인트, `@Operation` + `@ApiResponses(401·403·404·409·500)` + `roleGuard.requireAdmin(request)` |
| `TagService` | `api/src/main/java/com/mo/swtp/tag/service/` | `@Transactional(readOnly=true)` + 쓰기 메소드 override. `existsById` 중복 검증 + `instrumentRepository.existsById` FK 선검증 |
| `TagDto` | `api/src/main/java/com/mo/swtp/tag/dto/` | 응답 DTO. `from(Tag)` 정적 팩토리. enum 필드는 `@Schema(implementation = X.class)` 명시 |
| `TagUpsertDto` | `api/src/main/java/com/mo/swtp/tag/dto/` | 요청 DTO. `@NotBlank`/`@NotNull` 검증. **api 모듈 위치** (User·Menu 선례 정합) |
| `TagErrorCode` | `api/src/main/java/com/mo/swtp/tag/exception/` | enum implements `ErrorCode`. `httpStatus(int)` 만. 항목: `TAG_NOT_FOUND(404)`, `DUPLICATE_TAG_SRL_NO(409)`, `INVALID_INSTRUMENT_ID(400)` |
| `TagRepository` (수정) | `api/src/main/java/com/mo/swtp/tag/repository/` (기존) | 메소드 추가: `findAllByOrderByUseYnDescTagSrlNoAsc()` (User 선례 정합), `existsById(String)` 활용 |

---

## 기존 사전·패턴과의 충돌

| 항목 | 위치 | 해소책 |
|------|------|--------|
| `standard-terms.md` line 68 `io_yn 또는 io_cd` 미결 행 | `swtp/backend/.claude/rules/dict/standard-terms.md` | 안건 2 — `io_cd` 단일 확정 행으로 교체 |
| `tag_*` 관련 5개 행의 `(예정)` 마커 (코드 도입 완료 상태) | 동일 파일 line 28~30, 66~67 | 안건 2 — 예정 마커 제거 (rawdata_1m_h 참조는 예정 유지) |
| `Tag.changeInfo()` instrument 인자 추가 검토 | `common/src/main/java/com/mo/swtp/tag/domain/Tag.java` | 안건 4 블로커 — 추가 **금지**. 계측기 교체는 `deactivate + create` 플로우 강제 |
| GET 5개 엔드포인트 ADMIN 전용 결정의 운영 시나리오 미확인 | (PLAN 단계) | 안건 3 권고 — 가정 섹션에 측정값 API 태그 메타 임베드 가능성 명시 |
| V6_4 직접 갱신 vs V9_1 신규 마이그레이션 | `common/src/main/resources/db/{init,migration}/` | 안건 1 — V9_1 신규 (Flyway checksum 위반 회피, V8_x 시리즈 충돌 회피) |
| **`tag_m.unit_cd` 컬럼 보유 vs enum 단위 매핑** | `Tag.java`·`V6_4__tag_master_table.sql`·`standard-terms.md` line 67 | **안건 6 — 컬럼 폐기 + `TagMeasurementType` enum 단위 매핑 (옵션 A)**. V9_1 합본 마이그레이션에서 DROP. 표준 용어 사전 line 67 `unit_cd` 사용 테이블에서 `tag_m` 제거 + 폐기 이력 행 추가 |
| **OPS·VOI enum 추가 시점** — 송수펌프제어분석 ANALYZE1 (2026-05-08) 미결 | `TagMeasurementType.java` | **안건 6 — 본 사이클 흡수 (단일 PR 로 깔끔히 진입)**. 송수펌프제어분석 사이클은 enum 추가 작업 본 사이클 결과 의존 |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

1. **`Tag` 엔티티 변경** (`common/src/main/java/com/mo/swtp/tag/domain/Tag.java`):
   - 필드 추가: `@Enumerated(EnumType.STRING) @Column(name="use_yn", nullable=false, length=1) private YnType useYn`
   - **필드 제거**: `private String unitCd` (안건 6) — `TagMeasurementType` enum 매핑으로 대체
   - `Tag.create(...)` 정적 팩토리에서 `useYn = YnType.Y` 명시 할당 + `unitCd` 인자 제거
   - `Tag.deactivate()` 메서드 추가 (`this.useYn = YnType.N`)
   - `Tag.changeInfo()` 시그니처에서 `unitCd` 인자 제거 (instrument 인자 추가 금지 — 안건 4 블로커 유지)
2. **`TagMeasurementType` enum 변경** (`common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java`):
   - `description`·`unit` String 두 필드 추가 (`@Getter @RequiredArgsConstructor` 패턴)
   - 기존 5개 값에 description+unit 매핑 (FRI="m³/h"·PRI="kgf/cm²"·LEI="m"·PWI="kW"·RMS="")
   - **신규 OPS·VOI 추가** — OPS("가동상태", "")·VOI("개도율", "%")
3. 다른 엔티티 변경 없음

### DB 설계 변경 초안

- 신규 마이그레이션 SQL 1건 (합본): `common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql`
  - **(1) `use_yn` 컬럼 ADD** — 3단계: `ALTER TABLE tag_m ADD COLUMN use_yn VARCHAR(1)` (NULL 허용) → 백필 생략 (운영 데이터 0건 환경 명시) → `ALTER COLUMN SET NOT NULL`
  - **(2) `unit_cd` 컬럼 DROP** — `ALTER TABLE tag_m DROP COLUMN unit_cd` (안건 6, 운영 데이터 0건 가정)
  - `COMMENT ON COLUMN tag_m.use_yn IS '사용 여부 (DOM_YN, YnType enum — Y·N, 논리 삭제 지원)'`
  - DDL CHECK·DEFAULT·단독 인덱스 미적용
- V6_4 직접 갱신 금지 (Flyway checksum 위반 회피)

### 적용할 패턴

- **CRUD API 5개 엔드포인트** (모두 ADMIN 전용 — `roleGuard.requireAdmin(request)`):
  - POST `/api/tags` — 등록 (`registerTag`)
  - GET `/api/tags` — 전체 목록 (`findAllTags` — use_yn 내림차순 + tag_srl_no 오름차순)
  - GET `/api/tags/{tagSrlNo}` — 단건 조회 (`findActiveTag` — use_yn='Y' 만)
  - PUT `/api/tags/{tagSrlNo}` — 수정 (`modifyTag` — instrument_id 변경 금지)
  - DELETE `/api/tags/{tagSrlNo}` — 논리 삭제 (`deactivateTag` — use_yn='N')
- **Service 검증 의무** — Service 레벨 `instrumentRepository.existsById(dto.getInstrumentId())` 선검증 후 `TagErrorCode.INVALID_INSTRUMENT_ID(400)` 변환 (DB FK 500 노출 회피)
- **DTO 위치** — `api/src/main/java/com/mo/swtp/tag/dto/` (User·Menu 선례 정합)
- **DTO 응답 필드 매핑** — `TagDto.from(Tag tag)` 정적 팩토리에서 `unit = tag.getTagMeasurementType().getUnit()` 으로 enum 매핑 결과 노출 (`unitCd` 필드 부재)
- **ErrorCode 항목**: `TAG_NOT_FOUND(404)`, `DUPLICATE_TAG_SRL_NO(409)`, `INVALID_INSTRUMENT_ID(400)` (`String message` 필드 금지)
- **Swagger Tag 번호** — `08. 태그 관리` 또는 후속 번호 (PLAN 단계 결정 — 송수펌프제어분석 사이클 08·09 사용 중이므로 10 또는 그 후)
- **테스트** — `TagServiceTest` Mockito 단위 테스트 6건 + `TagMeasurementTypeTest` 단위 테스트 2건 (enum 단위 매핑 단언 — `FRI.getUnit()="m³/h"`, `OPS.getUnit()=""`, `VOI.getUnit()="%"`)

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 본 사이클은 GET 5개 엔드포인트 모두 ADMIN 전용으로 진행 — HMI 대시보드 등 일반 USER 가 태그 메타 의존 시나리오는 측정값 API 태그 메타 임베드 (Option A) 또는 단건 분리 (Option B) 로 별도 사이클에서 보강 | **결정** | 안건 3 — wtp-domain-expert 권고. PLAN 단계에서 보강 옵션 명시 필요 시 추가 결정 |
| 운영 데이터 0건 환경에서 V9_1 마이그레이션은 백필 생략 — 동일 SQL 이 스테이징·운영에 적용될 때 3단계 절차 정식 작성으로 환경 일관성 확보 | **결정** | 안건 1 — wtp-dba-reviewer 권고 |
| `Tag.changeInfo()` 시그니처에 instrument 인자 추가 금지 — 계측기 교체는 `deactivate + create` 플로우 강제 | **결정** | 안건 4 블로커 |
| `tag_m.unit_cd` 컬럼 폐기 + `TagMeasurementType` enum 단위 매핑 (옵션 A) — 측정 유형별 1:1 고정값 정규화 | **결정** | 안건 6 — plan mode 재진입 사용자 결정. enum 생성자에 description+unit String 직접 |
| OPS·VOI enum 본 사이클 흡수 — 송수펌프제어분석 사이클 미결 결정을 단일 PR 로 처리 | **결정** | 안건 6 — 사용자 결정. 송수펌프제어분석 사이클은 본 사이클 결과 의존 |
| `findAllTags()` 정렬: `findAllByOrderByUseYnDescTagSrlNoAsc()` — User 선례 정합 (활성 우선, 비활성 포함) | 가정 | PLAN 단계에서 use_yn='Y' 만 반환 옵션 검토 가능 |
| `TagController` Swagger Tag 번호는 `10. 태그 관리` 가정 — 송수펌프제어분석 ANALYZE1 의 `08·09` 사용 중이므로 10 채택 가능성 높음 | 가정 | PLAN 단계에서 사이클 진행 순서 확인 후 확정 |
| 향후 측정 유형별 단위가 다양화될 경우 (예: 초소형 펌프 L/min) 새 enum 값 추가로 대응 — `FRI_L_MIN("유량(L/min)", "L/min")` 같은 별도 enum 신설 | 가정 | 본 사이클은 7종 (FRI/PRI/LEI/PWI/RMS/OPS/VOI) 만 다룸. 향후 확장 시 enum 1곳만 수정 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `tag_m.use_yn` 컬럼 신설 + V9_1 마이그레이션 적용 | `psql \d+ tag_m` 출력에 `use_yn varchar(1) NOT NULL` 행 + COMMENT 표시 |
| **`tag_m.unit_cd` 컬럼 폐기** | `psql \d+ tag_m` 출력에 `unit_cd` 행 부재 |
| **`TagMeasurementType` enum 단위 매핑 동작** | `TagMeasurementTypeTest` 단위 테스트 — `FRI.getUnit()="m³/h"`·`PRI.getUnit()="kgf/cm²"`·`LEI.getUnit()="m"`·`PWI.getUnit()="kW"`·`RMS.getUnit()=""`·`OPS.getUnit()=""`·`VOI.getUnit()="%"` 단언 GREEN |
| **OPS·VOI enum 신규 추가** | `TagMeasurementType.values().length == 7` 단언 + OPS·VOI enum 값 존재 확인 |
| `Tag.useYn` 필드 + `deactivate()` 메서드 + `unitCd` 필드 제거 빌드 통과 | ./gradlew.bat :common:build BUILD SUCCESSFUL — Tag·TagMeasurementType 변경 후 QClass 재생성 정상 |
| `TagService` Mockito 단위 테스트 6건 GREEN | ./gradlew.bat :api:test PASS — TagServiceTest 6건 (등록 정상·DUPLICATE 차단·INVALID_INSTRUMENT FK 위반·TAG_NOT_FOUND 미존재·modify 정상·deactivate use_yn=N) |
| 5개 엔드포인트 모두 ADMIN 전용 강제 — USER role JWT 로 호출 시 403 FORBIDDEN | 통합 테스트 또는 수동 검증 — `RoleGuard.requireAdmin` 분기 |
| Swagger 노출 — 5개 엔드포인트 + DTO 스키마 + enum (TagMeasurementType/IoCode/YnType) implementation 명시 + **`TagDto.unit` 필드 enum 매핑 노출** | `:api:bootRun` 후 `/swagger-ui.html` 에 `0X. 태그 관리` Tag + 5 엔드포인트 + enum 타입 표시 + TagDto 응답 스키마에 `unit` 필드 (enum 매핑 결과) |
| `TagErrorCode` `String` 필드 0건 — `check-errorcode-contract.sh` 훅 통과 | `Write` 시 PostToolUse 훅 차단 없음 |
| V9_1 마이그레이션 SQL 의 `use_yn` 컬럼 COMMENT ON COLUMN 추가 — `check-ddl-column-comment.sh` 훅 통과 | `Write` 시 PostToolUse 훅 차단 없음 |
| **표준 용어 사전 갱신 9건 적용** — line 25 use_yn 사용 테이블 + line 28~30 + line 66 예정 마커 제거 + line 67 unit_cd 사용 테이블 제거 + line 68 io_cd 확정 + 폐기 이력 io_yn 등록 거부 + 폐기 이력 unit_cd 즉시 폐기 | `grep "tag_m" .claude/rules/dict/standard-terms.md` 에서 `(예정)` 마커 0건 (rawdata_1m_h 제외), `io_yn 또는` 매칭 0건, `unit_cd` 행에 `tag_m` 미매칭 (폐기 이력으로 이전) |
| `instrumentRepository.existsById` FK 선검증 — instrument_id 미존재 등록 시 INVALID_INSTRUMENT_ID(400) | TagServiceTest 1건 GREEN — DB FK 500 노출 0건 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | Tag CRUD 는 마스터 관리 전용. 알람 임계값·전이 조건·복귀 조건 변경 없음. `tag_m` 행이 `tagRangeConfigRepo.findByTagNm` 등 알람 평가 입력으로 사용되더라도 본 사이클은 메타 CRUD 만 다룸 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | Tag 등록·수정·삭제 경로는 PLC 제어 명령 발행 경로와 분리. `pump_interlock_p` 룰 변경 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | Tag CRUD 는 `ai_drvn_mod`·`ai_mode_cd`·`ai_drvn_mod_p` 와 무접촉. 강제 전환 절차 영향 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 행 추가 경로 없음. Tag 자체는 마스터 데이터로 BaseEntity Auditing 으로 충분 (별도 이력 테이블 미신설) |

> **"비해당" 4건 단독 차단 해제 조건 충족 검증**: (1) 각 행에 구체 사유 명기 — 4영역 모두 운영 시나리오 영향 명시 ("해당 없음" 한 줄 아님). (2) "## 신규 엔티티/DB 컬럼" 섹션 — `tag_m.use_yn` 컬럼 1건 + Tag 엔티티 변경 (필드 1 + 메소드 1 추가) 신규 항목 보유. 두 조건 모두 충족 — 형식적 충족 패턴 아님 (실 사유 명기 + 마스터 메타 영역 외 4영역 영향 없음).

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — line 25 `use_yn` 행 사용 테이블에 `tag_m` 추가, 비고에 "논리 삭제 목적 — rawdata_1m_h 가 tag_srl_no 를 논리 참조하므로 물리 삭제 금지 (태그관리 ANALYZE1, 2026-05-08)" 이력 추가
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — line 28 `tag_nm` 사용 테이블에서 `tag_m(예정)` → `tag_m` 예정 마커 제거
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — line 29 `tag_se_cd` 사용 테이블에서 `tag_m(예정)` → `tag_m` 예정 마커 제거
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — line 30 `tag_desc` 사용 테이블에서 `tag_m(예정)` → `tag_m` 예정 마커 제거
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — line 66 `tag_srl_no` 사용 테이블에서 `tag_m(예정)` → `tag_m` (rawdata_1m_h 예정 유지)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — line 67 `unit_cd` 사용 테이블에서 `tag_m(예정)` → `tag_m` 예정 마커 제거 (※ 안건 6 결정에 따라 본 라인은 후속 갱신으로 `tag_m` 자체 제거 — 아래 별도 체크박스 참조)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — line 68 `io_yn 또는 io_cd` 미결 행 전면 교체 → `io_cd` 단일 확정 행 (DOM_CODE_20, IoCode enum INPUT/OUTPUT/BIDIR, 태그관리 ANALYZE1 2026-05-08 확정)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — 폐기 이력 섹션에 `io_yn` 등록 거부 항목 추가 (마스터도메인설계 Round 3 미결 대안 → io_cd 채택, tag_m 코드 미도입 상태)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — **line 67 `unit_cd` 행 자체 제거** (안건 6 결정 — 사용 테이블 0건이므로 표준 용어 표 행 자체 제거. `TagMeasurementType` enum 단위 매핑으로 대체)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — **폐기 이력 섹션의 "즉시 폐기" 표에 `unit_cd` 행 추가** (사유: 측정 유형별 1:1 고정값 정규화 — `TagMeasurementType` enum 의 `unit` 필드로 흡수, 사용 테이블 0건. 대체: `TagMeasurementType.{값}.getUnit()` 메서드)

---

## 산출물

- [계획안 (작성 예정)](../../../plan/20260508/태그관리/PLAN1.md)
