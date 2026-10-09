---
status: approved
created: 2026-05-13
updated: 2026-05-13
---
# 송수펌프제어분석 — 3번섹션 시설 실시간 상태 표출 API 계획

## 목적

활성 시설(1번 섹션에서 `hasPump=true` 로 조회된 시설) 의 유량계·펌프 실시간 상태를 단건 조회 endpoint 로 제공한다.

- 유량계(`Flwmtr` 자식 instrument) 에 매핑된 FRI/PRI 태그의 최신 계측값 (유량·관압)
- 펌프(`Pump` 자식 instrument) 에 매핑된 OPS 태그의 최신값 (On/Off)
- 펌프의 `oprtngType` (AUTO_CAPABLE / SEMI_AUTO_CAPABLE) 정적 제원 동반 노출 — "AI 모드와 무관" `@Schema` 명시

## 배경

- ANALYZE1 (status: approved, 2026-05-13) 의 11개 안건 + 회의 결론 그대로 이행
- 방안 D 채택 — `rawdata_1m_h` DISTINCT ON 즉석 조회 (`tag_m` 스키마 변경 0건, 수집부 코드 변경 0건). SCADA 수집 어댑터 백지화 상태 (pump+AI 백지화 사이클 1, 2026-05-12) 가 A/B/C 안의 수집부 재설계를 차단함이 핵심 근거
- 메타 결정 plan (`~\.claude\plans\backend-image-png-2-cuddly-nova.md`) — 6 섹션 응답 구조 **섹션별 분리** 확정 (1분 폴링 주기 + SRP)

## 범위

### 포함

**3번 섹션 신규 자산**:
- `FacilityController` 에 `GET /api/facility/{facilityId}/state` 추가 (1번 섹션 RequestMapping `/api/facility` 정합 — ANALYZE1 의 `/api/facilities` 복수형은 가정 섹션 결정)
- `FacilityStateService` (`api/facility/service/`) 신규 분리
- `RawDataCustomRepository.findLatestByTagSrlNos(List<String>)` 신규 메서드 + `RawDataCustomRepositoryImpl` 구현 (DISTINCT ON 네이티브 쿼리)
- `RawDataLatestDto` (`api/raw/dto/`) Service 내부 전송용 신규
- 응답 DTO 3건 — `FacilityStateDto`·`FlwmtrStateDto`·`PumpStateDto` (모두 `api/facility/dto/`, 1번 섹션 선례 정합)

**2번 섹션 자산 일괄 폐기** (사용자 결정 2026-05-13 "DwtStatus 부분도 전부 폐기 다음 섹션 진행할 때 다시설계"):
- `backend/api/src/main/java/com/mo/swtp/facility/service/DwtStatusService.java`
- `backend/api/src/main/java/com/mo/swtp/facility/web/DwtStatusController.java`
- `backend/api/src/main/java/com/mo/swtp/facility/dto/DwtStatusDto.java`
- `backend/api/src/main/java/com/mo/swtp/facility/dto/section/DwtRequirePressureDto.java`
- `backend/api/src/main/java/com/mo/swtp/facility/dto/section/MainFactorDto.java`
- `backend/api/src/test/java/com/mo/swtp/facility/service/DwtStatusServiceTest.java`
- `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 의 기존 `findLatestByTagSrlNos(List<String>, LocalDateTime)` 메서드 + Impl 구현 (Querydsl 서브쿼리)

> 2번 섹션 (배수지 상태 표출) 자체의 재설계는 본 사이클 범위 외 — 추후 별도 사이클 (예: `송수펌프제어분석-2번섹션_재설계`) 에서 신규 ANALYZE/PLAN 진행

### 제외
- DB 스키마 변경 (방안 D)
- SCADA 수집부 코드 변경
- 캐시 도입 (N=500 SLA 미달 대응)
- OPS 결측 처리 정책 (Hold Last Value 미적용 영향, 미해결 → 추후 사이클)
- 5분 초과 결측 표시 정책 (frontend 자체 분기로 위임)
- **2번 섹션 (배수지 상태 표출) 재설계** — 본 사이클은 폐기만 수행, 재설계는 별도 사이클

## 구현 방향

### 조회 흐름 — 4-step 체이닝 (전체 개요)

```
[입력] facilityId (PathVariable)
   │
   ▼  Step 1: FacilityRepository.findById + use_yn 검증
[Facility 1건]   ──(존재 안 함 / 비활성 → FACILITY_NOT_FOUND)
   │
   ▼  Step 2: InstrumentCustomRepository.findByFacilityIdAndEquipType
[Instrument N건 — Pump M + Flwmtr K]   ──(빈 결과 허용)
   │
   ▼  Step 3: TagRepository.findByInstrumentInstrumentIdInAndUseYn + tagSeCd 필터
[Tag P건 — FRI/PRI/OPS]   ──(태그 미연결 instrument 는 결측 처리)
   │
   ▼  Step 4: RawDataCustomRepository.findLatestByTagSrlNos (DISTINCT ON)
[RawData 최신값 Q건]   ──(tag 별 최근 1시간 내 행 1개)
   │
   ▼  Step 5~6: instrument 단위 그룹핑 + DTO 빌드
[FacilityStateDto]
```

### 왜 JOIN 한 쿼리로 묶지 않는가

`rawdata_1m_h` 시계열 테이블은 `tag_m`·`instrument_m`·`facility_m` **어느 마스터와도 FK 제약을 두지 않는다** — `db/partitioning-and-retention.md §1 운영 원칙` 정합 ("시계열 파티션 테이블(`_h` suffix) 은 마스터 테이블 FK 추가 금지. 1분 주기 대용량 INSERT 마다 마스터 행 존재 확인 잠금이 발생하여 수집 성능에 직격 영향. 참조 무결성은 애플리케이션 레벨 검증으로 대체.").

따라서:

- `instrument_m` ↔ `tag_m` 는 ManyToOne FK 보유로 JOIN 가능하지만, 한 쿼리 묶음의 이득이 작음. Step 2/3 분리가 SRP·기존 메서드 재사용·N+1 회피 (Step 3 는 단일 IN 쿼리 1회) 면에서 우월 — 본 사이클 분리 유지
- `rawdata_1m_h` ↔ `tag_m` 는 **FK 부재** — JOIN 으로 묶으려면 native SQL 에서 `JOIN tag_m USING (tag_srl_no)` 형태로 작성 필요. DISTINCT ON 의 `ORDER BY tag_srl_no, acq_dtm DESC` 단순성과 `idx_rawdata_1m_h_tag_time` 인덱스 정합성을 깨므로 별도 Step 4 로 분리 유지 (ANALYZE1 안건 7 결론)

### Repository — 신규 native DISTINCT ON + 2번 섹션 자산 폐기

**폐기 대상** (송수펌프제어분석 PLAN1 2026-05-08 자산, 본 PLAN1 사이클 일괄 폐기 — 사용자 결정 2026-05-13 "DwtStatus 부분도 전부 폐기 다음 섹션 진행할 때 다시설계"):

- `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 의 기존 메서드 `findLatestByTagSrlNos(List<String> tagSrlNos, LocalDateTime acqDtmFrom)` (Querydsl 서브쿼리 구현, `Map<String, RawData>` 반환)
- `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 의 동 메서드 구현

**신규 메서드**:

- 위치: `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java`
- 메서드 시그니처:
  ```java
  List<RawDataLatestDto> findLatestByTagSrlNos(List<String> tagSrlNos);
  ```
- 구현: `EntityManager.createNativeQuery(SQL, Tuple.class)` + `Object[]` 매핑. `tagSrlNos` 는 `String[]` 변환 후 `setParameter(1, ...)` 으로 `tag_srl_no = ANY(?)` 바인딩
- 본문 골자:
  ```sql
  SELECT DISTINCT ON (tag_srl_no)
         tag_srl_no, raw_val, corr_val, acq_dtm, quality_cd
  FROM rawdata_1m_h
  WHERE tag_srl_no = ANY(?)
    AND acq_dtm >= NOW() - INTERVAL '1 hour'
  ORDER BY tag_srl_no, acq_dtm DESC;
  ```
- `@Transactional(readOnly = true)` 적용
- ROOT `coding-discipline.md §2.5` 면책 인용 주석 의무 — `query-tuning.md §2` 인용 근거 명시 (단일 메서드 50줄 자연 초과 시)
- 반환 dto `RawDataLatestDto` 는 `api/raw/dto/` 위치 (record 또는 `@Getter` Java 클래스, 검토 게이트 미해당 — 응답 DTO 가 아닌 Service 내부 전송용)

### Service — `FacilityStateService`

- 위치: `backend/api/src/main/java/com/mo/swtp/facility/service/FacilityStateService.java`
- 클래스 레벨 `@Transactional(readOnly = true)` + `@RequiredArgsConstructor`
- 단일 public 메서드: `FacilityStateDto findFacilityState(String facilityId)`
- 책임 분담 — 각 단계별 사용 Repository 메서드 + 기존 자산 재사용 / 신규 분류:

| 단계 | 책임 | Repository 메서드 | 분류 |
|------|-----|----------------|------|
| 1 | 시설 존재·활성 검증 | `FacilityRepository.findById(facilityId)` + `use_yn` 검사 (없으면 `RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND)`) | 재사용 |
| 2 | 시설 산하 활성 계측기 조회 (PUMP+FLWMTR) | `InstrumentCustomRepository.findByFacilityIdAndEquipType(facilityId, [PUMP, FLWMTR])` — `use_yn = Y` 필터·`disp_ord ASC` 정렬은 메서드 내부 책임 | **재사용** (송수펌프제어분석 PLAN1 2026-05-08 도입) |
| 3 | 계측기 산하 활성 태그 조회 (FRI/PRI/OPS) | `TagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y)` + Service 레벨에서 `tagSeCd IN [FRI, PRI, OPS]` Stream 필터 | **재사용** (동일 사이클 도입) |
| 4 | 태그 시리얼번호 → 최신 계측값 단일 native 쿼리 | `RawDataCustomRepository.findLatestByTagSrlNos(tagSrlNos)` — native DISTINCT ON 단일 SQL. 반환 `List<RawDataLatestDto>` | **신규** (본 PLAN1, 2026-05-08 Querydsl 구현은 §Repository 섹션의 폐기 대상) |
| 5 | instrument 단위 그룹핑 + FRI/PRI/OPS 매핑 (유량계: FRI=`flwrtRawVal` + PRI=`prsrRawVal`, 펌프: OPS=`isRunning`) | Service 내부 로직 (Repository 호출 없음) | — |
| 6 | DTO 빌드 + `FacilityStateDto` 반환 | Service 내부 + DTO 정적 팩토리 | — |

- N+1 회피: 2/3 단계는 IN 절 일괄 조회, 4 단계는 단일 native query — 전체 흐름에서 SQL 4회 (Facility 1 + Instrument 1 + Tag 1 + RawData 1)

### Controller — `FacilityController` 신규 endpoint

- 위치: `backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java`
- 추가 endpoint:
  ```java
  @GetMapping("/{facilityId}/state")
  public ResponseEntity<CommonResponseDto<FacilityStateDto>> findFacilityState(
          @PathVariable String facilityId);
  ```
- `@Operation`·`@ApiResponses` 명시 (200/400/401/403/404/500)
- 응답 `@Schema(implementation = FacilityStateDto.class)` — 자식 DTO 는 `oneOf` 다형성 미사용 (단일 DTO 가 자식 목록 List 보유 — 1번 섹션의 facility 자식 oneOf 와 구조 다름)

### DTO — 3건

| 클래스 | 위치 | 필드 |
|--------|-----|------|
| `FacilityStateDto` | `api/facility/dto/` | `facilityId`·`facilityNm`·`List<FlwmtrStateDto> flwmtrs`·`List<PumpStateDto> pumps` |
| `FlwmtrStateDto` | `api/facility/dto/` | `instrumentId`·`instrumentNm`·`flwrtRawVal`·`flwrtCorrVal`·`flwrtAcqDtm`·`flwrtQualityCd`·`prsrRawVal`·`prsrCorrVal`·`prsrAcqDtm`·`prsrQualityCd` |
| `PumpStateDto` | `api/facility/dto/` | `instrumentId`·`instrumentNm`·`oprtngType`·`isRunning`·`acqDtm`·`qualityCd` |

- 모두 `BaseAuditResponseDto` 미적용 (실시간 통지 분류 — `api-patterns.md §BaseAuditResponseDto 적용 범위`)
- `@Getter` + `private` 기본 생성자 + 정적 팩토리 `from(...)` 패턴
- `oprtngType`: `@Schema(description = "펌프 물리 조작 가능 유형 (AI 운전 모드와 무관)", implementation = PumpOprtngType.class)`
- `qualityCd`/`flwrtQualityCd`/`prsrQualityCd`: `@Schema(description = "SCADA 품질 코드", implementation = QualityCode.class)` enum name 직렬화
- `isRunning`: `Boolean` (OPS 태그 `raw_val == 1.0` → true, `raw_val == 0.0` → false, NULL → null)

## 도메인 모델

### 신규 엔티티
**0건** (방안 D)

### 신규 응답 DTO 3건

| 엔티티/테이블 | 역할 | 주요 필드 |
|------------|-----|----------|
| `FacilityStateDto` | 시설 단위 실시간 상태 컨테이너 | `facilityId`, `facilityNm`, `flwmtrs[]`, `pumps[]` |
| `FlwmtrStateDto` | 유량계 1대의 FRI·PRI 최신값 결합 | `instrumentId`, `instrumentNm`, FRI 4컬럼, PRI 4컬럼 |
| `PumpStateDto` | 펌프 1대의 OPS 최신값 + 정적 조작유형 | `instrumentId`, `instrumentNm`, `oprtngType`, `isRunning`, `acqDtm`, `qualityCd` |

### 조회 흐름 참여 기존 엔티티 4건 (변경 없음, 재사용)

본 사이클은 다음 4 엔티티를 **변경 없이 재사용** 한다. 신규 컬럼·신규 메서드 추가 없음 (Repository 메서드는 §구현 방향 §Service 책임 분담 표 참조).

| 엔티티 | 패키지 | 본 사이클 역할 | 주요 필드 사용처 |
|--------|------|------------|----------------|
| `Facility` (`facility_m`) | `com.mo.swtp.facility.domain` | Step 1 — 시설 존재·활성 검증 | `facilityId` PathVariable 매칭 + `facilityNm` 응답 노출 + `useYn` 검증 |
| `Instrument` (`instrument_m`, JPA JOINED 부모) | `com.mo.swtp.instrument.domain` | Step 2 — 시설 산하 활성 계측기 조회 (`equipType IN [PUMP, FLWMTR]`) | `instrumentId`·`instrumentNm` 응답 노출. 자식 `Pump` 의 `oprtngType` 정적 노출 |
| `Tag` (`tag_m`) | `com.mo.swtp.tag.domain` | Step 3 — 계측기 산하 활성 태그 조회 (`tagSeCd IN [FRI, PRI, OPS]`) | `tagSrlNo` Step 4 입력값. `tagSeCd` 분류로 instrument 별 FRI/PRI/OPS 매핑. `instrument` FK 로 instrument 그룹핑 |
| `RawData` (`rawdata_1m_h`, 시계열) | `com.mo.swtp.raw.domain` | Step 4 — `tagSrlNo` 최신 계측값 DISTINCT ON | `rawVal`·`corrVal`·`acqDtm`·`qualityCd` 응답 노출 |

> **도메인 검토 게이트 미해당**: 신규 엔티티·테이블·DB 컬럼 0건 — `wtp-domain-expert` 추가 호출 불요 (ANALYZE1 안건 9·10 에서 이미 검토 완료). DTO 만 신규 — 도메인 룰 영향은 `quality_cd` 노출 정책 (ANALYZE1 안건 10 결론 그대로) 단일 항목으로 기 결정.

## DB 설계 변경

**0건** — `tag_m` / `rawdata_1m_h` / `instrument_m` / `facility_m` 스키마 변경 없음. 기존 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 인덱스 그대로 활용.

> **DBA 검토 게이트 미해당**: DB 스키마 변경 0건 — `wtp-dba-reviewer` 추가 호출 불요 (ANALYZE1 안건 5·6·7·8 에서 인덱스·파티션 프루닝·구현 방법·SLA 모두 검토 완료).

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE1 의 후보 5건을 검증 명령 확정 형태로 변환.

| # | 기준 | 검증 명령 |
|---|------|---------|
| 1 | DISTINCT ON 쿼리가 인덱스 활용 + 파티션 프루닝 정상 작동 | 로컬 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS) SELECT DISTINCT ON (tag_srl_no) tag_srl_no, raw_val, corr_val, acq_dtm, quality_cd FROM rawdata_1m_h WHERE tag_srl_no = ANY(ARRAY[...]) AND acq_dtm >= NOW() - INTERVAL '1 hour' ORDER BY tag_srl_no, acq_dtm DESC;` 실행 결과에 (1) `Index Scan Backward using idx_rawdata_1m_h_tag_time` 출력, (2) `Append` 하위 파티션 1~2개, (3) `Sort` 노드 부재 모두 확인 |
| 2 | N=100 활성 태그 fixture 기준 응답 시간 200ms 이내 | `./gradlew.bat :api:test --tests FacilityStateServiceTest.findFacilityState_100_tags_within_200ms` 통합 테스트 RawData 100건 INSERT 후 `FacilityStateService.findFacilityState(facilityId)` 호출 응답 시간 측정 PASS |
| 3 | `FacilityStateService` 가 `quality_cd` 가 BAD/UNCERTAIN 인 태그의 최신값도 그대로 반환 | `./gradlew.bat :api:test --tests FacilityStateServiceTest.findFacilityState_returns_BAD_quality_as_is` 단위 테스트 — `RawDataCustomRepository.findLatestByTagSrlNos` mock 으로 `QualityCode.BAD` 행 주입 시 응답 DTO `qualityCd = BAD` 노출 GREEN |
| 4 | 응답 DTO 에 `oprtngType` 노출 + `@Schema(implementation = PumpOprtngType.class)` 명시 | `./gradlew.bat :api:bootRun -Pprofile=local` 기동 후 Swagger UI `http://localhost:8080/swagger-ui.html` 에서 `PumpStateDto` 스키마 `oprtngType` 의 `enum: [AUTO_CAPABLE, SEMI_AUTO_CAPABLE]` 노출 확인 |
| 5 | 신규 엔드포인트 응답이 `CommonResponseDto<FacilityStateDto>` + `code: "SUCCESS"` | `curl -H "Authorization: Bearer {token}" "http://localhost:8080/api/facility/{facilityId}/state"` 응답 body 의 `code == "SUCCESS"` + `data.facilityId == {요청 facilityId}` 검증 |

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE1 의 가정·미해결 6건을 PLAN 결정으로 변환. 추가로 PLAN 단계에서 부상한 endpoint URL 컨벤션 불일치 1건 명시.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| Endpoint URL — 1번 섹션 RequestMapping `/api/facility` (단수) vs ANALYZE1 의 `/api/facilities` (복수) | 미해결 → 결정 | **`/api/facility/{facilityId}/state` 단수형 채택** — 1번 섹션 `FacilityController` 의 `@RequestMapping("/api/facility")` 와 같은 컨트롤러 + 같은 도메인 endpoint 정렬 (단일 컨트롤러에서 단수·복수 혼용 금지). ANALYZE1 의 `facilities` 표기는 영문 일반 RESTful 관례 인용일 뿐 본 프로젝트 SSOT 와 충돌 — PLAN 에서 정렬 |
| OPS (`quality_cd = BAD`) 결측 시 표출 방식 | 미해결 | **본 사이클 미결 유지** — raw 결과 그대로 노출 (frontend 가 `qualityCd` 보고 표시 분기). 추후 사이클에서 정책 결정 |
| `acq_dtm` 5분 초과 결측 표시 정책 | 미해결 | **본 사이클 미결 유지** — `acq_dtm` 을 응답 DTO 에 포함하여 frontend 가 자체 정책 적용. 백엔드 "—" 치환 안 함 |
| 활성 태그 N=500 시 SLA 200ms 미달 가능성 | 미해결 | **본 사이클 N=100 fixture 측정 한정** — 운영 측정 후 캐시 도입 여부는 별도 사이클 결정. PLAN 성공 기준 2번 (N=100 200ms) 만 PASS 의무 |
| `FlwmtrStateDto` 가 유량태그·압력태그를 단일 DTO 에 포함할지, 별도 필드로 분리할지 | 가정 → 결정 | **단일 DTO + FRI/PRI 분리 필드 채택** — `flwrtRawVal`·`flwrtCorrVal`·`flwrtAcqDtm`·`flwrtQualityCd`·`prsrRawVal`·`prsrCorrVal`·`prsrAcqDtm`·`prsrQualityCd` (8 필드). FRI 와 PRI 가 측정 유형 (`tag_se_cd`) 이 달라 의미 분리 필수. instrument 단위 응답 단순화 |
| `FacilityStateDto`·`FlwmtrStateDto`·`PumpStateDto` 패키지 위치 | 가정 → 결정 | **`com.mo.swtp.facility.dto` 단일 패키지 채택** — 1번 섹션 (`FacilityDto`·`FacilitySearchDto`) 선례 정합. `facility/state/dto` 분리는 추상화 계층 과잉 (`coding-discipline.md §2` 위반 위험) |
| 활성 시설의 식별 방법 — `facility_id` 단건 vs 사용자 선택 시설 목록 | 가정 → 결정 | **`facility_id` 단건 PathVariable 채택** — frontend 는 1번 섹션 응답에서 시설 목록을 받은 후 각 시설마다 본 endpoint 를 N 회 호출 (1분 폴링 주기, N+1 부담 미미). batch 엔드포인트는 별도 사이클 (N=100+ 확장 시) |
| 기존 `RawDataCustomRepository.findLatestByTagSrlNos(List<String>, LocalDateTime)` (2026-05-08 송수펌프제어분석 PLAN1) 와 본 PLAN1 의 native DISTINCT ON 신규 결정 충돌 | 미해결 → 결정 | **2번 섹션 자산 일괄 폐기 + 본 PLAN1 원안 (native DISTINCT ON) 신규 작성** — 사용자 결정 2026-05-13 ("DwtStatus 부분도 전부 폐기 다음 섹션 진행할 때 다시설계"). 호출처 `DwtStatusService.java:99` 도 폐기 대상. 섹션별 사이클 폐기·재설계 정책 정렬 (메모리 `feedback-section-cycle-discard-policy`) |

## 제외 사항

- DB 스키마 변경 (방안 D 채택의 정의)
- SCADA 수집부 코드 변경 (백지화 상태 유지)
- 캐시 도입 (Redis·Caffeine 등 — N=500 SLA 미달 시 별도 사이클)
- 결측 표시 정책 (OPS BAD 격상·5분 초과 — frontend 자체 분기)
- batch 엔드포인트 (`POST /api/facility/state` body 시설 ID 목록 — 별도 사이클)
- 6번 섹션 펌프 가동대수·관압 (frontend 가 본 응답 `pumps[].isRunning` 카운트 자체 산출 가능 — 6번 ANALYZE 사이클에서 별도 endpoint 도입 여부 결정)
- `@Inheritance(JOINED)` 마스터 다형성 자식 DTO 3단 상속 (본 응답은 facility/instrument 부모 다형성 응답이 아니므로 미해당)

## 테스트 전략

### 단위 테스트 (`./gradlew.bat :api:test`)

| 테스트 클래스 | 검증 항목 |
|-------------|---------|
| `FacilityStateServiceTest` | (1) 시설 미존재 → `FACILITY_NOT_FOUND`, (2) 비활성 시설 → `FACILITY_NOT_FOUND` (use_yn 검증), (3) BAD QUALITY 태그도 그대로 반환, (4) 활성 instrument 없으면 빈 List 반환, (5) FRI/PRI 분리 매핑, (6) OPS 1.0/0.0 → `isRunning` true/false |
| `RawDataCustomRepositoryImplTest` | DISTINCT ON 메서드는 통합 테스트로 작성 (`@SpringBootTest(NONE) + @Transactional`) — 단위 테스트 범위 외 |

### 통합 테스트 (`./gradlew.bat :api:test`)

| 테스트 클래스 | 검증 항목 |
|-------------|---------|
| `RawDataCustomRepositoryImplIntegrationTest` (또는 `RawDataCustomRepositoryImplTest` 통합 분류) | `@SpringBootTest(webEnvironment = NONE)` + `@ActiveProfiles("test")` + `@Transactional`. RawData 100건 INSERT 후 `findLatestByTagSrlNos(tagSrlNos)` 호출 → 각 `tag_srl_no` 마다 가장 최근 행 1개씩 반환 검증 + 응답 시간 200ms 이내 |

### Swagger 수동 검증

- `./gradlew.bat :api:bootRun -Pprofile=local` 기동
- `http://localhost:8080/swagger-ui.html` 의 `06. 시설물 관리` 태그에 신규 endpoint 노출 확인
- `PumpStateDto.oprtngType` enum 노출 + `QualityCode` enum name 직렬화 확인

## 도메인 룰 4영역 점검 (PLAN 시점 재확인)

ANALYZE1 §도메인 룰 4영역 점검 결론 그대로 — 알람 4단계 "해당" (quality_cd 노출 운전원 오인 방지) + 인터록/AI 모드/이력 기록 "비해당" (표출 전용). PLAN 단계 추가 변경 없음.

## 예상 산출물

- [태스크](../../../tasks/20260513/송수펌프제어분석-3번섹션/TASK1.md) (생성 예정)
