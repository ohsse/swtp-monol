---
status: approved
created: 2026-05-11
updated: 2026-05-11
---
# BaseAuditResponseDto 설계 — 도메인 분석

## 작업 배경

### 요청 요약
`BaseEntity` 를 상속하는 엔티티의 응답 DTO 명세에 대해 공통 메타 4컬럼(`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) 노출을 표준화하는 `BaseAuditResponseDto` 추상 부모 클래스를 설계·도입한다.

### 문제 현황 (사전 분석 결과)
- `BaseEntity` (`common/src/main/java/com/mo/swtp/common/domain/BaseEntity.java:33-87`): 4컬럼 + `@Transient newEntity` 플래그로 표준화 완료
- SWTP backend 에 `BaseDto` 클래스 **부재** — `api-patterns.md:70` 의 `extends BaseDto` 는 reference 프로젝트 잔재
- 응답 DTO 메타 노출 **비일관**: `UserDto`(4개 모두) · `TagDto`(2개) · `AiModeDto`/`PumpStateDto`/`MenuTreeDto`(0개)
- `@JsonFormat` 직렬화 정책 미적용 (reference `BaseDto` 는 `"yyyy-MM-dd HH:mm"` 적용 중)
- SWTP backend 의 DTO 상속 사례 **0건** (`extends *Dto` grep 결과)
- `@SuperBuilder` 사용 사례 **0건**

### 외부 산출물
- 없음 (코드 패턴 표준화 작업으로 요구사항·다이어그램 부재)
- plan 모드 plan 파일: `~\.claude\plans\basedto-baseentity-precious-clarke.md`

### 사용자 사전 결정 (AskUserQuestion 답변)
- **적용 범위**: 선택적 옵트인 — 마스터(`_m`) 응답 DTO 한정
- **클래스명**: `BaseAuditResponseDto` (검색 조건 부모 BaseDto 와 분리)
- **직렬화 정책**: `@JsonFormat` 표준화를 본 작업에 포함

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: DTO 상속 깊이 3단 제약 검증 (ROOT `coding-discipline.md §2.1`)
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `BaseAuditResponseDto` 1단 도입은 3단 초과 위험 없음. 현재 SWTP 상속 0건 기준 `BaseAuditResponseDto → UserDto` 체인 2단으로 임계 미달. PLAN 에서 상속 깊이 상한(2단) 을 룰로 명시해 3단 접근을 사전 차단하도록 권고
- **결론**: 1단 도입 안전. PLAN 단계에서 응답 DTO 상속 상한 2단을 `api-patterns.md` 에 추가 명문화

### 안건 2: api-patterns.md L70 BaseDto 역할 정정
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 옵션 (c) 채택 — `api-patterns.md` 본문의 `DatasetDto extends BaseDto` 코드 예시를 삭제하고 단순 독립 클래스로 교체. 검색 조건 부모 BaseDto 동시 도입은 보류 (`coding-discipline.md §2` "요청되지 않은 추상화 계층 금지"). 검색 조건 부모 필요성이 실제 누적되면 별도 ANALYZE 에서 결정
- **결론**: 옵션 (c) 채택. 검색 조건 부모 BaseDto 도입 보류, `api-patterns.md` 의 DatasetDto 예시를 독립 클래스로 정정

### 안건 3: `@JsonFormat` 표준 패턴 결정
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `"yyyy-MM-dd HH:mm:ss"` 초 단위 채택. `BaseEntity` 의 `LocalDateTime` (`DOM_DTM`) 은 초 단위 정밀도 보유. `ot-integration.md §5` 의 `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h.ctrl_dtm` 등 OT 운영 이력 추적은 초 단위 식별이 감사에 필요. 분 단위 잘라내기는 정밀도 손실
- **결론**: `@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 표준 채택

### 안건 4: 마이그레이션 전략 — DTO 컨벤션 + frontend SPEC 영향
- 호출 에이전트: `wtp-glossary-manager` (Round 1 단독으로 충분)
- Round 1 답변 요약:
  - **wtp-glossary-manager**:
    1. camelCase 컨벤션(`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) 은 기존 `UserDto` 와 동일 — `api-patterns.md` DTO 패턴 정합
    2. `TagDto` 2개 → 4개 확장은 `/dev:spec` 자동 추출이 "DTO 필드 변경 — 추가" 행으로 감지 가능 (Swagger `@Schema` 정적 비교)
    3. SPEC 갱신 대상: `base_audit_response_dto` 신규 슬러그 불필요. `TagDto` 변경은 `태그관리` 슬러그 SPEC{N+1}, `UserDto` 변경은 `사용자관리개선` 슬러그 SPEC{N+1} 로 갱신
- **결론**: camelCase 컨벤션 유지. SPEC 갱신은 기존 슬러그(`태그관리`·`사용자관리개선`) SPEC{N+1} 각 1건 (신규 슬러그 없음)

### 안건 5: 도메인 룰 4영역 비해당 확인
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4영역 모두 비해당. `BaseAuditResponseDto` 는 응답 직렬화 계층에만 영향, 임계값·전이 조건·선행조건 검사·모드 전환 로직·이력 INSERT 경로 모두 무접촉. AI 운전 모드는 조건부 비해당 — `AiDrvnModResponseDto` 류가 향후 옵트인되어 `ai_drvn_mod`(사용자 의도)·`ai_mode_cd`(시스템 상태) 두 축 동시 노출 시 frontend 오독 위험 있으나 도메인 룰 위반은 아님. `coding-discipline.md §2.5` 면책 영역 적용 불필요
- **결론**: 4영역 모두 비해당. 본 작업은 면책 영역 외

### 안건 6: 표준 사전 4층 신규 등록 0건 확인
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**:
    1. 표준 단어 — `rgstr`·`updt`·`dtm`·`id` 모두 기등재. `audit` 은 DB 컬럼 조합 재료 미사용 → **등록 거부** (Java 식별자 한정)
    2. 표준 데이터 도메인 — `DOM_DTM`·`DOM_ID_50` 재사용, 신규 0건
    3. 비즈니스 도메인 약어 — 공통 인프라 작업으로 비즈니스 영역 신설 없음
    4. 표준 용어 — `rgstr_dtm`·`updt_dtm` 정식 등재. **데드코드 보고**: `rgstr_id`·`updt_id` 가 동의어 패턴 절에만 있고 용어 표 본행 누락 (본 작업 범위 외)
    Java 식별자 (`BaseAuditResponseDto`·`applyAuditMeta`) 는 어휘 사전 등록 대상 외 (`naming.md` Java 클래스 네이밍 범주)
- **결론**: 4층 사전 신규 등록 0건. ROOT 어휘 사전·backend 표준 용어 사전 갱신 불필요

### 안건 7: `@SuperBuilder` + 상속 빌더 체인 안전성
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **`@SuperBuilder` 신규 도입 보류**. SWTP 에 사용 0건이고 Lombok `@SuperBuilder` 는 제네릭 빌더 체인 컴파일 오류·Jackson 역직렬화 충돌 등 알려진 엣지 케이스 존재. `coding-discipline.md §2` "일회성 코드를 위해 추상화 계층을 만들지 않는다" 적용. `BaseAuditResponseDto` 에 `protected` setter 또는 `protected` 생성자를 두고 자식 DTO `from()` 정적 팩토리에서 호출하는 방식이 단순성·검증 가능성·가독성 모두 우위. 응답 DTO 는 출력 전용이라 Jackson 역직렬화 불필요
- **결론**: `@SuperBuilder` 도입 보류. `protected` setter (or 생성자) + 자식 `from()` 정적 팩토리 패턴 채택. **plan 모드 plan 파일의 §1 골격 (SuperBuilder 사용 예시) 폐기**

### 안건 8: 선택적 옵트인 적용 대상 — 백엔드 + 도메인 공동
- 호출 에이전트: `wtp-backend-engineer` + `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 제외 분류 적정. 단 **`ai_drvn_mod_p` 마스터 응답 DTO (예: `AiDrvnModResponseDto`) 는 옵트인 대상으로 별도 분류 검토 필요** — 누가 언제 모드 설정했는지 운영자 책임 추적이 실무상 필요
  - **wtp-domain-expert**: 제외 3건 모두 적정. `_h` 이력 DTO 가 별도 존재 시 시계열 응답으로 옵트인 제외. 이력 DTO 에 메타 필요 시 `rgstrDtm`·`rgstrId` 2개만 직접 선언 (`db/indexing-and-migration.md §4.3` immutable 이력 예외 적용)
- **결론**: 제외 분류 3건(AiModeDto·PumpStateDto·MenuTreeDto) 적정. `ai_drvn_mod_p` 류 마스터 응답 DTO 가 코드에 미존재하므로 본 사이클 적용 대상 외 — **향후 도입 시 별도 결정**으로 미룬다. 이력(`_h`) 응답 DTO 가 메타 필요 시 `BaseAuditResponseDto` 상속 대신 `rgstrDtm`·`rgstrId` 2개만 직접 선언하는 immutable 예외 패턴 적용

---

## 표준 사전 카탈로그

### 신규 표준 단어
없음. 4층 모두 기존 재사용.

### 신규 표준 데이터 도메인
없음. `DOM_DTM`·`DOM_ID_50` 재사용.

### 신규 표준 용어
없음. `rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id` 모두 BaseEntity 기존 운용 컬럼.

> **데드코드 보고** (`coding-discipline.md §3.1` 기존 데드코드 보고): `rgstr_id`·`updt_id` 가 `swtp/backend/.claude/rules/dict/standard-terms.md` 의 "## 동의어·금지 패턴" 절에만 등장하고 본 용어 표에 정식 등재 누락. 본 작업 범위 외 — 별도 사이클에서 정렬 검토 권고

---

## 신규 엔티티/DB 컬럼

없음. 본 작업은 **응답 DTO 추상화 작업**으로 신규 엔티티·DB 컬럼·인덱스·파티션 변경이 없다.

- 비즈니스 도메인 패키지: 변경 없음 (`common.dto` 패키지 활용)
- DB 테이블: 변경 없음
- DB 컬럼: 변경 없음
- 인덱스: 변경 없음

신규 도입 Java 식별자 (어휘 사전 등록 대상 외):
- 클래스: `BaseAuditResponseDto` (위치: `common/src/main/java/com/mo/swtp/common/dto/BaseAuditResponseDto.java`)
- 메서드: `BaseAuditResponseDto.applyAuditMeta(BaseEntity)` protected helper (또는 protected setter 4종 — `setRgstrDtm`·`setUpdtDtm`·`setRgstrId`·`setUpdtId`. 자식 `from()` 에서 호출)

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 해소책 |
|---------|---------------|
| `api-patterns.md:70` 의 `DatasetDto extends BaseDto` 는 SWTP 에 부재한 BaseDto 클래스 참조 (reference 잔재) | **옵션 (c)**: 본문 코드 예시를 단순 독립 클래스로 정정. `DatasetDto extends BaseDto` 표기 삭제 (안건 2 결론) |
| 응답 DTO 메타 노출 비일관 (`UserDto` 4개 / `TagDto` 2개 / 다수 0개) | `BaseAuditResponseDto` 도입으로 마스터 `_m` 응답 DTO 옵트인 표준화 (안건 8 결론) |
| `@SuperBuilder` 도입 의도 vs SWTP 미사용 + Lombok 엣지 케이스 | `protected` setter / 생성자 + 자식 `from()` 정적 팩토리 패턴 채택 (안건 7 결론) |
| `@JsonFormat` 패턴 산발 | `"yyyy-MM-dd HH:mm:ss"` 초 단위 SSOT 채택 (안건 3 결론) |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- 신규 추상 클래스 `BaseAuditResponseDto` 를 `common.dto` 패키지에 도입
- 4 필드: `rgstrDtm`·`updtDtm`·`rgstrId`·`updtId` (BaseEntity 와 동일)
- `@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 적용 (안건 3)
- `@Schema(description=..., example=...)` Swagger 표준 메타 (`api-patterns.md` 정합)
- 부모는 추상(`abstract`) — 직접 인스턴스화 금지
- 자식 매핑은 `protected` setter 4종 또는 `protected` 생성자 — `@SuperBuilder` 미사용 (안건 7)

### DB 설계 변경
없음 (본 작업은 응답 DTO 계층만 변경).

### 적용할 패턴
- 자식 DTO 가 `BaseAuditResponseDto` 를 상속하고, 정적 팩토리 `from(Entity)` 내부에서 부모의 `protected` setter (또는 protected 생성자) 로 메타 4컬럼을 할당
- 본 사이클 적용 대상: `UserDto` (마이그레이션 — 4개 직접 보유 → 부모 상속으로 흡수), `TagDto` (2개 → 4개로 확장 + 부모 상속)
- 본 사이클 제외 대상: `AiModeDto`·`PumpStateDto`·`MenuTreeDto` (도메인 룰·통지·요약 응답)
- 향후 마스터 DTO (FacilityDto·InstrumentDto·MenuDto 등 신규 도입 시) 표준 적용

### 응답 DTO 상속 상한 룰화
- `api-patterns.md` 에 응답 DTO 상속 상한 **2단** 명문화 (`BaseAuditResponseDto → 도메인 DTO`). 3단 이상 진입 시 ANALYZE 필수 (`coding-discipline.md §2.1` DTO 상속 깊이 3단 초과 자동 지적 룰의 응답 DTO 특화 정렬)

### frontend SPEC 영향 (PLAN 의 `/dev:spec` 단계 검토 항목)
- 본 작업의 슬러그 `base_audit_response_dto` 전용 SPEC 디렉토리 신설 불필요
- SPEC 갱신은 기존 슬러그의 SPEC{N+1} 로:
  - `swtp/frontend/docs/api-specs/태그관리/SPEC{N+1}.md` — `TagDto` 의 `rgstrId`·`updtId` 추가
  - `swtp/frontend/docs/api-specs/사용자관리개선/SPEC{N+1}.md` — `UserDto` 의 필드 직렬화 포맷 변경 (`@JsonFormat` 신규 적용)

---

## 성공 기준 후보 (PLAN 변환 대상)

> 본 섹션의 후보 기준은 PLAN 단계에서 ROOT [`coding-discipline.md §4.2`](../../../../../.claude/rules/coding-discipline.md) 의 "성공 기준 (검증 가능 형태)" 로 변환된다.

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `BaseAuditResponseDto.applyAuditMeta(BaseEntity)` 가 4컬럼 모두 매핑 | `BaseAuditResponseDtoTest` 단위 테스트 GREEN — `applyAuditMeta` 호출 전후 `rgstrDtm`·`updtDtm`·`rgstrId`·`updtId` 4컬럼 매칭 검증 4건 |
| `UserDto` 마이그레이션 후 기존 응답 호환성 유지 | `./gradlew.bat :api:test --tests "*UserServiceTest"` PASS (회귀 없음) |
| `TagDto` 가 `rgstrId`·`updtId` 신규 노출 | `TagDto` 응답 JSON 의 4컬럼 직렬화 검증 단위 테스트 1건 GREEN |
| `@JsonFormat` 직렬화 표준 적용 | 단위 테스트에서 `LocalDateTime.of(2026,5,11,10,30,15)` 입력 시 `"2026-05-11 10:30:15"` 출력 매칭 |
| 룰 파일 3건 갱신 정합성 | `grep "## BaseAuditResponseDto 패턴" .claude/rules/api-patterns.md` 매칭 1건 + `grep "## BaseEntity ↔ BaseAuditResponseDto" .claude/rules/entity-patterns.md` 매칭 1건 + `grep "DatasetDto extends BaseDto" .claude/rules/api-patterns.md` 매칭 0건 |
| 전체 빌드 회귀 없음 | `./gradlew.bat clean build` BUILD SUCCESSFUL 출력 확인 |
| frontend SPEC 갱신 대상 슬러그 식별 | `/dev:spec` 단계에서 `태그관리`·`사용자관리개선` 슬러그 SPEC{N+1} 갱신 (본 작업 슬러그 전용 SPEC 신설 0건) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `BaseAuditResponseDto` 위치는 `common.dto` 패키지 (BaseEntity 의 `common.domain` 과 페어) | 결정 | ANALYZE 결론 |
| `@SuperBuilder` 미사용 — `protected` setter 또는 `protected` 생성자 + 자식 `from()` 정적 팩토리 | 결정 | 안건 7 결론 (plan 모드 plan 파일의 §1 골격 폐기) |
| `@JsonFormat` 패턴은 `"yyyy-MM-dd HH:mm:ss"` (초 단위) | 결정 | 안건 3 결론 |
| 응답 DTO 상속 상한 2단을 `api-patterns.md` 에 명문화 | 결정 | 안건 1 결론 |
| 본 사이클 적용 대상은 `UserDto`·`TagDto` 일괄 마이그레이션 | 결정 | 안건 8 결론 — `ai_drvn_mod_p` 류 응답 DTO 는 향후 도입 시 별도 결정 |
| 이력(`_h`) 응답 DTO 가 메타 필요 시 immutable 예외 패턴 (`rgstrDtm`·`rgstrId` 2개만 직접 선언) 적용 | 결정 | 안건 8 결론, `db/indexing-and-migration.md §4.3` 참조 |
| `rgstr_id`·`updt_id` 표준 용어 표 정식 등재는 본 작업 범위 외 — 별도 사이클 처리 | 결정 | 안건 6 데드코드 보고 (`coding-discipline.md §3.1`) |
| `protected` setter 4종 vs `protected` 생성자 (4개 인자) vs `protected` 헬퍼 메서드 — 어느 쪽이 더 단순한가? | 결정 | **`protected void applyAuditMeta(BaseEntity entity)` 헬퍼 메서드 채택** — setter 4종 분산 호출 대비 자식 `from()` 가독성 우위, 생성자 인자 4개 대비 변경 시 영향 범위 작음. 룰 갱신 지시서 §1·§3 에 반영 완료 |
| 검색 조건 부모 `BaseSearchDto` 필요성이 누적되면 별도 ANALYZE 진입 | 미해결 | 본 작업과 독립. 검색 조건 DTO 가 3건 이상 누적 시 트리거 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 임계값·전이 조건·복귀 조건 로직 무접촉. 응답 DTO 메타 노출만 변경 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 선행조건 검사·기동 차단·복구 후 재검사 경로 무접촉 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 (조건부) | `ai_drvn_mod_p` 엔티티·서비스 무접촉. `AiModeDto` 가 옵트인 제외 분류로 두 축(`ai_drvn_mod`/`ai_mode_cd`) 동시 노출 위험 부재. 향후 `ai_drvn_mod_p` 마스터 응답 DTO 도입 시 별도 결정 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h` INSERT 경로·`transition_reason` 5종 기록 로직 무접촉. 이력 응답 DTO 가 본 사이클에 미포함 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/backend/.claude/rules/api-patterns.md` — `## BaseAuditResponseDto 패턴` 절 신설 (응답 메타 표준 클래스 정의 + `@JsonFormat` 표준 + 옵트인 정책 + 상속 상한 2단)
- [x] `swtp/backend/.claude/rules/api-patterns.md` — 기존 `## DTO 패턴` 절의 `DatasetDto extends BaseDto` 예시를 단순 독립 클래스로 정정 (안건 2 결론)
- [x] `swtp/backend/.claude/rules/entity-patterns.md` — `## BaseEntity ↔ BaseAuditResponseDto 매핑 패턴` 절 신설 (`protected void applyAuditMeta(BaseEntity)` 헬퍼 메서드 + 자식 `from()` 정적 팩토리)

> 4층 사전 갱신 없음:
> - (해당 없음) `swtp/.claude/rules/dict/standard-words.md` — 신규 단어 0건 (`audit` 등록 거부)
> - (해당 없음) `swtp/.claude/rules/dict/standard-data-domains.md` — 신규 도메인 0건
> - (해당 없음) `swtp/.claude/rules/dict/domain-abbreviations.md` — 신규 약어 0건
> - (해당 없음) `swtp/backend/.claude/rules/dict/standard-terms.md` — 신규 용어 0건 (기존 4컬럼 재사용)

---

## 산출물

- [계획안](../../../plan/20260511/base_audit_response_dto/PLAN1.md) (작성 예정)
