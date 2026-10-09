---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 2번섹션 — 운영시설 목록 조회 API

## 목적

`backend/image/설비별사용량.png` 대시보드 **2번섹션**(좌측 "시설 현황" 패널)의 백엔드 API 를 구현한다. 시설 중 **운영시설(`FacilityGroup.OPERATION` 8종: PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR)** 목록을 조회하는 **읽기 전용 기능**이다.

기존 `GET /api/facility`(`FacilitySearchDto`)는 단일 `facilityTypeCd` 필터만 보유하고 **그룹 단위 필터가 없어** 운영시설 묶음 조회가 불가하다. 본 작업은 그룹 필터(`facilityGroupCd`)를 추가해 이 공백을 메운다.

## 배경

- 도메인 분석 완료: [ANALYZE1](../../../analyze/20260609/설비별사용량-2번섹션/ANALYZE1.md) (`status: approved`) — 5인 회의 블로커 0건, 신규 어휘/DB 0건, 도메인 4영역 전부 비해당, 룰 갱신 지시서 0건.
- 사용자 사전 확정(AskUserQuestion, 2026-06-09):
  1. **범위** — 본 섹션은 운영시설 목록 조회만. 기존 `시설별사용량` 대시보드(2번섹션=전력 사용량, 구현 완료 `GET /api/facility/energy-usage`)와의 관계는 **이후 섹션 재논의**(현 섹션 미고려).
  2. **구현 방식** — 기존 엔드포인트 필터 확장(`hasPump` 선례 동형). 전용 엔드포인트 신설 안 함.
  3. **응답 DTO** — 기존 다형성 `FacilityDto` 재사용.
- 기존 선례: `hasPump` 필터 도입(송수펌프_시설목록, 2026-05-13) — `FacilitySearchDto` 신규 필드 + `findFacilities()` BooleanBuilder 분기 추가 패턴. 본 작업은 이 패턴을 동형으로 따른다.

## 범위

| 모듈 | 변경 대상 | 변경 유형 |
|------|---------|---------|
| `common` | `FacilityType.java` | `typesOf(FacilityGroup)` 정적 헬퍼 추가 (group→소속 type 목록 파생 SSOT) |
| `common` | `FacilityTypeTest.java` (신규) | `typesOf` 순수 함수 단위 테스트 |
| `api` | `FacilitySearchDto.java` | `facilityGroupCd` 필터 필드 1개 추가 |
| `api` | `FacilityCustomRepositoryImpl.java` | `findFacilities()` 그룹 필터 분기 1개 추가 |
| `api` | `FacilityController.java` | `findAllFacilities` `@Operation(description)` 보강 (시그니처/엔드포인트 불변) |

- `FacilityService` 변경 없음 — 필터는 `FacilitySearchDto` 경유 자동 반영.

## 구현 방향

### 1. `common` — `FacilityType.typesOf(FacilityGroup)` 정적 헬퍼

`common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java` 에 group→소속 type 목록을 파생하는 정적 헬퍼를 추가한다. 그룹 8종을 코드에 하드코딩하지 않고 `group` 필드 기준으로 단일 도출(SSOT)한다.

```java
/** 그룹 소속 시설 유형 목록 — group→types 파생 SSOT (하드코딩 방지). */
public static List<FacilityType> typesOf(FacilityGroup group) {
    return Arrays.stream(values()).filter(t -> t.group == group).toList();
}
```

- `import java.util.Arrays;`·`import java.util.List;` 추가.
- enum 본문(생성자 필드 `description`·`group` 선언) 하단에 메서드 배치.
- `common/CLAUDE.md` 허용 범위(도메인 enum) 정합 — api 호출 가능.

### 2. `api` — `FacilitySearchDto.facilityGroupCd` 필터 필드

`api/src/main/java/com/mo/swtp/facility/dto/FacilitySearchDto.java` 에 NULL 허용 필터 필드 1개를 추가한다(`facilityTypeCd`·`useYn`·`hasPump` 와 동일 정책 — NULL 시 전체).

```java
@Schema(description = "시설 그룹 코드 필터 (NULL 시 전체). OPERATION=운영시설 8종 등 그룹 단위 IN 조회.",
        implementation = FacilityGroup.class)
private FacilityGroup facilityGroupCd;
```

- `import com.mo.swtp.facility.domain.enumtype.FacilityGroup;` 추가.
- `@Schema(implementation = FacilityGroup.class)` 명시 — `api-patterns.md §DTO @Schema(implementation) 명시 패턴`(사용자 정의 enum 필수) 준수.

### 3. `api` — `FacilityCustomRepositoryImpl.findFacilities()` 그룹 필터 분기

`api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` 의 `findFacilities()` `if (searchDto != null)` 블록 내부에 기존 분기와 동형으로 그룹 조건을 추가한다.

```java
if (searchDto.getFacilityGroupCd() != null) {
    where.and(f.facilityType.in(FacilityType.typesOf(searchDto.getFacilityGroupCd())));
}
```

- `FacilityType` 는 이미 import 됨(파일 6행). 추가 import 불필요.
- `facilityTypeCd`(eq) 와 `facilityGroupCd`(in) 동시 지정 시 BooleanBuilder AND 교집합 — 별도 충돌 처리 없음(단순성 우선, `coding-discipline.md §2`).
- 정렬 `f.useYn.desc(), f.dispOrd.asc(), f.facilityNm.asc()` 유지(변경 없음).
- **`facility_type_cd` 필터 강제 룰 준수** — 그룹 필터를 `facility_type_cd IN (OPERATION 8종)` 으로 전개하는 것은 STORAGE/NETWORK 혼입 차단이므로 룰 위반이 아닌 **준수**(`entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 §도메인 룰`).

### 4. `api` — `FacilityController.findAllFacilities` Swagger description 보강

`api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 의 `findAllFacilities` `@Operation(description=...)` 에 `facilityGroupCd` 필터와 설비별사용량 2번섹션 운영시설 목록 용도를 한 줄 보강한다. 엔드포인트(`GET /api/facility`)·메서드 시그니처·응답 타입(`ResponseEntity<CommonResponseDto<List<FacilityDto>>>`)·`@ApiResponses` 변경 없음.

- 현재 description: `"facilityTypeCd / useYn / hasPump 필터로 ..."` → `facilityGroupCd` 포함 + 그룹 IN 전개 설명 + 2번섹션 운영시설 용도 추가.

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 방법 |
|---------|---------|
| `FacilityType.typesOf(OPERATION)` = 8종(PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR) 정확 반환 | `FacilityTypeTest` 신규 단위 테스트 — size 8 + 8종 정확 포함 GREEN (`./gradlew.bat :common:test` PASS) |
| `typesOf(STORAGE)` = 3종(PWTF·DWT·RSV) / `typesOf(NETWORK)` = 1종(POINT) | `FacilityTypeTest` 신규 단위 테스트 — 그룹별 분류 정확 GREEN |
| `common` 모듈 컴파일·빌드 성공 (FacilityType 변경, QClass 영향 없음) | ./gradlew.bat :common:build 실행 후 BUILD SUCCESSFUL 출력 확인 |
| `api` 모듈 컴파일·빌드 성공 (SearchDto·RepositoryImpl·Controller 변경) | ./gradlew.bat :api:build 실행 후 BUILD SUCCESSFUL 출력 확인 |
| (선택) 그룹 필터 적용 시 OPERATION 8종만 반환 | dev DB GET /api/facility?facilityGroupCd=OPERATION 응답 facilityTypeCd 전부 OPERATION 그룹 (수동) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `facilityGroupCd` 미전달(NULL) 시 그룹 조건 미적용 = 전체 시설 반환 | 결정 | 기존 `facilityTypeCd`·`useYn` NULL=전체 시맨틱 동형. 2번섹션 화면은 `facilityGroupCd=OPERATION` 명시 호출 |
| `facilityTypeCd` 와 `facilityGroupCd` 동시 지정 시 AND 교집합 | 결정 | 단순성 우선 — 별도 배타/검증 없음. 그룹 밖 type 지정 시 빈 결과(정상 동작 허용) |
| 활성 필터(useYn=Y)는 클라이언트가 파라미터로 제어(서버 강제 안 함) | 결정 | 기존 `useYn` 필터 재사용. 좌측 nav 는 frontend 가 `useYn=Y` 전달 |
| 운영시설 목록은 평면(flat) 목록 — 트리/계층 구조 아님 | 결정 | 사용자 "목록 조회" 기준. 트리 필요 시 별도 섹션 |
| `typesOf` 신규 작성, 기존 `FacilityEnergyUsageService` 인라인 OPERATION 도출은 미수정 | 결정 | `coding-discipline.md §3` 정밀 수정 — 중복은 RESULT 발견사항 보고만, 본 사이클 리팩토링 강제 금지 |

## 제외 사항

- **DB 스키마 변경 없음** — 신규 테이블·컬럼·인덱스 0건. `docs/ddl/`·`db/migration/` 갱신 불필요.
- **신규 엔티티/표준 용어/데이터 도메인 0건** — 모든 어휘 기존 등록 재사용(ANALYZE1 카탈로그 전부 "없음").
- **전용 엔드포인트 신설 안 함** — 기존 `GET /api/facility` 필터 확장(사용자 결정).
- **`FacilityEnergyUsageService` OPERATION 인라인 도출 리팩토링 제외** — `typesOf` 와 중복이나 직접 수정 금지(§3). RESULT 발견사항 보고만.
- **`시설별사용량` 대시보드 연계 제외** — 이후 섹션 재논의(사용자 결정).
- **페이지네이션·시설명 검색 제외** — `FacilitySearchDto` 기존 정책(시설물관리기능 ANALYZE1, 2026-05-11) 유지.

## 도메인 모델

**신규 엔티티·테이블·필드 없음.** `FacilityType.typesOf(FacilityGroup)` 정적 헬퍼는 도메인 enum 내 순수 함수(group→type 목록 파생)이며 DB 영속 대상이 아니다. `FacilityGroup` enum(STORAGE/OPERATION/NETWORK)은 기존 자산(시설_도메인_확장 ANALYZE1, 2026-06-08), `facility_m` 에 컬럼 미신설.

## DB 설계 변경

**없음.** 스키마 변경 0건. `facility_m` 은 마스터 소량 테이블이라 `facility_type_cd IN (8종)` + `use_yn` 조건은 성능 영향 무시 수준이며 신규 인덱스 불필요(ANALYZE1 안건 4 — wtp-dba-reviewer 확인, DBA 2차 승인 대상 없음).

> **검토 게이트 생략 근거**: `## 도메인 모델` 신규 엔티티 0건 + `## DB 설계 변경` 없음 → wtp-domain-expert·wtp-dba-reviewer PLAN 재검토 생략. 도메인 정합성·DB 영향은 ANALYZE1 5인 회의(안건 3·4)에서 이미 검토 완료(블로커 0건).

## 예상 산출물
- [태스크](../../../tasks/20260609/설비별사용량-2번섹션/TASK1.md)
