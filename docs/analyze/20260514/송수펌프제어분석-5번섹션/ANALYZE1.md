---
status: approved
created: 2026-05-14
updated: 2026-05-14
---
# 송수펌프제어분석 5번섹션 — 도메인 분석

## 작업 배경

송수펌프제어분석 화면 5번 박스 (최소요구관압) 는 4번섹션의 배수지(DWT) 목록 중 사용자가 선택한 배수지에 대해 다음 3개 필드를 표출한다.

| 표시 라벨 | 데이터 출처 |
|---------|----------|
| 최소요구관압 기준배수지 | 선택된 배수지명 — 이미 4번섹션 `DwtStateDto.facilityNm` 에 포함됨 |
| 최소요구관압(분기점) | 배수지로 분기되는 관로 분기점 지점의 **별도 물리값** — 신규 컬럼 필요 |
| 최소요구관압 | 배수지 본체의 최소요구압력 — 이미 `dwt_m.min_req_prsr` NOT NULL 컬럼 존재 |

**외부 산출물**: `swtp/backend/image/송수펌프제어분석.png` (5번 박스 시각 명세).

**사용자 결정 (Phase 1 사전 확인)**:
1. **1안 채택** — 4번섹션 응답 DTO (`DwtStateDto`) 확장. 별도 엔드포인트 미도입. JOINED 자식 상속으로 추가 쿼리 0건, 화면 응답 응집성 유지.
2. **"분기점" = 별도 물리값** — `dwt_m` 자식 신규 컬럼 + 신규 표준 단어 `branch` 등록 필요.

**선행 4번섹션 자산 (커밋 `6fa2e32`, 2026-05-14)**:
- 엔드포인트: `GET /api/facility/{parentFacilityId}/dwts/states`
- 응답 DTO: `DwtGroupStateDto` (컨테이너) → `dwts: List<DwtStateDto>` (배수지별 운전 상태)
- `DwtStateDto` 기존 필드: `facilityId`·`facilityNm`·`inFlwmtr`·`outFlwmtr`·`multipleInFlwmtrDetected`·`multipleOutFlwmtrDetected`·`valves[]`·`lvmtrs[]`
- Javadoc 명시: "실시간 통지성 응답 분류"

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 신규 표준 단어 `branch` 등록 검토

- 호출 에이전트: `wtp-glossary-manager` (필수)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: ROOT 표준 단어 사전 (`swtp/.claude/rules/dict/standard-words.md`) 45개 단어 + ROOT 비즈니스 도메인 약어 사전 (`swtp/.claude/rules/dict/domain-abbreviations.md`) 모두 미등록 확인. `point`(비즈니스 도메인 약어 도입 예정 — 관로 계측 분기점) 와 층위 다름 (표준 단어 vs 비즈니스 도메인 약어) — `swtp/.claude/rules/dict/README.md` §⚠️ "도메인" 용어 충돌 방지 적용 대상 아님, 충돌 없음. 약어 컨벤션 검토 결과 `format`(6자) · `quality`(7자) 풀네임 채택 선례 동일 — 풀네임 `branch` 채택 권장.
- Round 2: 불필요 (블로커 없음).
- **결론**: 신규 표준 단어 `branch` (풀네임, 한글 논리명 "분기", 기본 데이터 도메인 "조합") 신규 등록.

### 안건 2: 신규 표준 용어 `min_req_branch_prsr` 등록 검토

- 호출 에이전트: `wtp-glossary-manager` (필수) + `wtp-dba-reviewer` (2차 승인 — 안건 3 와 통합)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: backend 표준 용어 사전 (`swtp/backend/.claude/rules/dict/standard-terms.md`) 미등록. 어순은 기존 `min_req_prsr` 일관성 유지 — `min`(최소) + `req`(요구) + `branch`(분기점) + `prsr`(압력) 수식어 어순. `min_req_prsr` (배수지 본체) 와 측정 지점이 다른 독립 컬럼 — 의미 중복 없음. 데이터 도메인 `DOM_QTY_15_4` 기존 재사용.
  - **wtp-dba-reviewer** (안건 3 와 통합 답변): `DOM_QTY_15_4` 재사용 2차 승인 (NUMERIC(15,4) + BigDecimal + 압력값 표현 적합, 기존 `min_req_prsr` 와 동일 물리량 kgf/cm²).
- Round 2: 불필요.
- **결론**: 신규 표준 용어 `min_req_branch_prsr` (어순: `min_req_branch_prsr`, `DOM_QTY_15_4` 재사용, 사용 테이블 `dwt_m`) 등록.

### 안건 3: NULL 정책 + 무중단 마이그레이션 전략

- 호출 에이전트: `wtp-dba-reviewer` (필수)
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: NOT NULL 채택 — 선례 `min_req_prsr` 의 pumpcontrol_null_alignment ANALYZE1 (2026-04-25) 결정 사유 ("인터록 평가 NULL/미입력 구별 불가 방지") 동일 적용. `DOM_QTY_15_4` 기본 정책 (NULL 허용) 보다 더 엄격한 NOT NULL 적용은 `swtp/.claude/rules/dict/standard-data-domains.md` "더 엄격하게 설정 가능" 조항으로 허용. 무중단 3단계 의무 (`swtp/backend/.claude/rules/db/indexing-and-migration.md §2`) — 운영 행 수 미상이라 안전 의무. DDL 파일 가용 번호 `V8_7` (확인 결과 V8_6 까지 사용, V8_7 가용). 인덱스 미적용 (단순 조회 SELECT, 조회 조건 범위 검색 부재).
- Round 2: 불필요.
- **결론**: NOT NULL 채택. 무중단 3단계 마이그레이션 (NULL 허용 추가 → 백필 → NOT NULL 전환). DDL 파일 `V8_7__dwt_m_branch_prsr.sql`. 단독 인덱스 미적용.

### 안건 4: 응답 DTO 확장 — `DwtStateDto` 2필드 추가

- 호출 에이전트: `wtp-backend-engineer` (필수)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `DwtStateDto` 직접 확장 (별도 서브 DTO 분리 불필요 — `coding-discipline.md §2` "일회성 코드를 위해 추상화 계층을 만들지 않는다"). BaseAuditResponseDto 옵트인 미적용 — `DwtStateDto` Javadoc 에 "실시간 통지성 응답 분류" 명시, `api-patterns.md §BaseAuditResponseDto 패턴 §적용 범위` 의 "실시간 통지 응답 DTO" 분류 유지. 3단 상속 패턴 미적용 — `DwtStateDto` 는 다형성 자식 DTO 가 아님, JsonTypeInfo·JsonSubTypes·oneOf 모두 미충족. `BigDecimal` 은 Swagger 자동 인식 — `@Schema(description, example)` 만 명시 (`api-patterns.md §DTO @Schema(implementation) 명시 패턴 §적용 제외 대상`). 핵심 코드 변경: `DwtStateDto.of(...)` 파라미터 8→10 확장, `assembleDwtState(...)` 에서 매핑 2줄 추가.
- Round 2: 불필요.
- **결론**: `DwtStateDto` 에 `minReqPrsr`·`minReqBranchPrsr` 2필드 직접 추가. BaseAuditResponseDto 옵트인 미적용. 정적 팩토리 시그니처 8→10 확장.

### 안건 5: 도메인 룰 4영역 점검

- 호출 에이전트: `wtp-domain-expert` (필수)
- Round 1 답변 요약:
  - **wtp-domain-expert**: 본 사이클 범위 (응답 DTO 확장 + `dwt_m` 컬럼 추가) 는 도메인 4영역 모두 비해당. 알람 4단계 — 임계값 평가 변경 없음. 인터록 선행조건 — OT 아웃바운드 구조 (`ot-integration.md §2`) 백지화 중, 인터록 평가 코드 미존재. 단 사이클 2 (pump+AI 재설계) 에서 `min_req_branch_prsr` 가 인터록 임계값으로 편입될 가능성 높음 (선례 `min_req_prsr` NOT NULL 사유 정합). AI 운전 모드 — `ai_drvn_mod_p`·`ai_mode_cd` 모두 보류, 무접촉. 이력 기록 의무 — `BaseEntity` 4컬럼 (`facility_m` 부모 상속) 만으로 충족, 별도 `*_h` 불필요 (분기별 이력 요구사항 발생 시 별도 작업).
- Round 2: 불필요.
- **결론**: 본 사이클 도메인 4영역 모두 비해당. 사이클 2 인터록 평가 임계값 사용 가능성 + 변경 이력 `*_h` 필요성 2건은 가정 섹션 명시.

### 안건 6: 1안 채택 정합성 재확인

- 호출 에이전트: `wtp-backend-engineer` (필수)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 1안 적정 — 화면 박스 4번·5번이 배수지 선택 단일 액션에 연동, 5번만 독립 새로고침 시나리오 없음. JOINED 자식 (`DistributionWaterTank`) 상속으로 부모 `Facility` 조회 시 자동 함께 로드 (추가 쿼리 0건). 별도 엔드포인트 분리 시 동일 엔티티 두 번 SELECT — N+1 유발. Service 통합 유지 — `DwtStateService` "한 부모 시설의 자식 DWT 군 상태 조회" 단일 책임 범위 내 확장. `assembleDwtState(...)` 약 30줄 + `findDwtStates(...)` 약 25줄 — 2필드 추가 시 50줄 임계 (`coding-discipline.md §2.1`) 도달 안 함. SRP 무문제.
- Round 2: 불필요.
- **결론**: 1안 채택 적정. `DwtStateService` 통합 유지. 신규 Service·DTO 클래스 생성 없음.

---

## 표준 사전 카탈로그

### 신규 표준 단어

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `branch` | 분기 | 신규 | `swtp/.claude/rules/dict/standard-words.md` 미등록. `domain-abbreviations.md` 의 `point`(비즈니스 도메인 약어) 와 층위 다름 — 충돌 없음. `format`(6자)·`quality`(7자) 풀네임 채택 선례 정합 — 풀네임 `branch` 채택. 기본 데이터 도메인: (조합) |

### 신규 표준 데이터 도메인

없음 (`DOM_QTY_15_4` 기존 재사용 — DBA 2차 승인 완료).

### 신규 표준 용어

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `min_req_branch_prsr` | `min` + `req` + `branch`(신규) + `prsr` | `DOM_QTY_15_4` (재사용) | 신규 | `swtp/backend/.claude/rules/dict/standard-terms.md` 미등록. `min_req_prsr`(배수지 본체)와 측정 지점 다른 독립 컬럼 — 의미 중복 없음. 어순은 기존 `min_req_prsr` 수식어 패턴 일관성 유지. NOT NULL 정책 (선례 동일) |

---

## 신규 엔티티/DB 컬럼

### 신규 컬럼 — `dwt_m.min_req_branch_prsr`

| 항목 | 결정 |
|------|------|
| 비즈니스 도메인 패키지 | `com.mo.swtp.facility` (자식 엔티티 `DistributionWaterTank`) |
| 테이블 | `dwt_m` (Facility JOINED 자식 — `@DiscriminatorValue("DWT")`) |
| 컬럼명 | `min_req_branch_prsr` |
| SQL 타입 | `NUMERIC(15, 4)` |
| Java 타입 | `BigDecimal` |
| NULL 정책 | NOT NULL (선례 `min_req_prsr` 동일) |
| 데이터 도메인 | `DOM_QTY_15_4` (재사용) |
| 인덱스 | 미적용 (단순 조회 SELECT, 조회 조건 범위 검색 부재) |
| DDL 파일 | `V8_7__dwt_m_branch_prsr.sql` |
| 마이그레이션 | 무중단 3단계 (NULL 허용 추가 → 백필 → NOT NULL 전환) |
| COMMENT | `'분기점 최소 요구 압력 (kgf/cm², DOM_QTY_15_4 NOT NULL — 인터록 평가 기준값 향후 활용 가능성, min_req_prsr 선례 동일 NOT NULL 정책)'` |

### 응답 DTO 확장 — `DwtStateDto`

| 신규 필드 | 타입 | 출처 |
|---------|------|------|
| `minReqPrsr` | `BigDecimal` | `DistributionWaterTank.getMinReqPrsr()` (기존 엔티티 필드) |
| `minReqBranchPrsr` | `BigDecimal` | `DistributionWaterTank.getMinReqBranchPrsr()` (신규 엔티티 필드) |

`DwtStateDto.of(...)` 정적 팩토리 시그니처 8→10 파라미터 확장. `assembleDwtState(...)` 에서 매핑 2줄 추가.

---

## 기존 사전·패턴과의 충돌

없음 — 6개 안건 모두 Round 1 단답형으로 결론 도달, Round 2 호출 불필요. 충돌 0건.

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- `DistributionWaterTank` 엔티티에 신규 필드 `minReqBranchPrsr` (BigDecimal, precision=15·scale=4, NOT NULL) 추가
- 정적 팩토리 `create(...)`·`update(...)` 시그니처 1파라미터 확장 + `Objects.requireNonNull(minReqBranchPrsr)` 검증 추가

### DB 설계 변경 초안
- DDL 파일: `common/src/main/resources/db/init/V8_7__dwt_m_branch_prsr.sql`
- 단계 1 (NULL 허용 추가): `ALTER TABLE dwt_m ADD COLUMN min_req_branch_prsr NUMERIC(15, 4);`
- 단계 2 (백필): `UPDATE dwt_m SET min_req_branch_prsr = 0.0000 WHERE min_req_branch_prsr IS NULL;` (운영자 보정 안내 PLAN 명시)
- 단계 3 (NOT NULL): `ALTER TABLE dwt_m ALTER COLUMN min_req_branch_prsr SET NOT NULL;`
- COMMENT: `COMMENT ON COLUMN dwt_m.min_req_branch_prsr IS '분기점 최소 요구 압력 (kgf/cm², DOM_QTY_15_4 NOT NULL — 인터록 평가 기준값 향후 활용 가능성, min_req_prsr 선례 동일 NOT NULL 정책)';`

### 응답 DTO 변경 초안
- `api/src/main/java/com/mo/swtp/facility/dto/DwtStateDto.java` — 2필드 추가 + `of(...)` 시그니처 확장 + `@Schema(description, example)` 명시
- `api/src/main/java/com/mo/swtp/facility/service/DwtStateService.java` — `assembleDwtState(...)` 에 `dwt.getMinReqPrsr()`·`dwt.getMinReqBranchPrsr()` 매핑 2줄 추가

### 적용할 패턴
- 엔티티: `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴` (기존 `DistributionWaterTank` 자식 엔티티 확장)
- DTO: `api-patterns.md §DTO @Schema(implementation) 명시 패턴 §적용 제외 대상` (`BigDecimal` 자동 인식)
- DDL: `db/indexing-and-migration.md §2` 무중단 3단계 + `§4` COMMENT 의무

### Service 분리/통합
- `DwtStateService` 통합 유지 (Service·DTO 신규 생성 없음).

### frontend SPEC 전파
- `/dev:spec` 단계에서 `swtp/frontend/docs/api-specs/송수펌프제어분석-5번섹션/SPEC1.md` 생성 또는 `송수펌프제어분석-4번섹션/SPEC2.md` 누적 — 슬러그 결정은 사용자 확인 사항 (4번섹션 슬러그 누적 vs 5번섹션 별도 슬러그).

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `min_req_branch_prsr` 는 본 사이클에서 표출 전용. 사이클 2 (pump+AI 재설계, `/dev:analyze`) 에서 `pump_interlock_p` 룰에 인터록 평가 임계값으로 편입될 가능성 높음 — NOT NULL 정책 사전 채택은 본 가능성 대비 | 가정 | `wtp-domain-expert` 안건 5 결론 |
| `min_req_branch_prsr` 변경 이력 전용 `_h` 테이블 필요성 — 선례 `min_req_prsr` 의 "분기별 이력 별도 작업 범위" 유보와 동일 적용 (본 사이클 미수행) | 가정 | PLAN 단계 결정 — 본 사이클 범위 외 |
| `dwt_m` 운영 데이터 행 수 미상 — 무중단 3단계 의무 적용 (NOT NULL 즉시 적용 시 운영 행 1건이라도 있으면 락 영향 또는 오류) | 가정 | PLAN 단계 운영 환경 확인 후 백필 값 (`0.0000` 임시값) 운영자 보정 의무 명시 |
| frontend SPEC 슬러그 — 4번섹션 SPEC2 누적 vs 5번섹션 별도 SPEC1 신설 | 미해결 | `/dev:spec` 단계 사용자 결정 |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `DistributionWaterTank` 엔티티가 `minReqBranchPrsr` 필드 보유 + `create()`·`update()` 정적 팩토리에서 NOT NULL 검증 | 신규 단위 테스트 GREEN — `DistributionWaterTankTest` 에서 null 입력 시 `NullPointerException` 검증 |
| DDL 마이그레이션 3단계 후 `dwt_m.min_req_branch_prsr` 컬럼 + COMMENT 존재 | psql `\d+ dwt_m` 출력에 `min_req_branch_prsr NUMERIC(15,4) NOT NULL` + COMMENT 매칭 |
| `GET /api/facility/{parentFacilityId}/dwts/states` 응답에 `minReqPrsr`·`minReqBranchPrsr` 2필드 표출 | Swagger UI 응답 스키마 + 통합 테스트 (`DwtStateServiceTest` 신규) GREEN |
| `DwtStateDto.of(...)` 시그니처 변경에도 4번섹션 기존 매핑 회귀 없음 | `./gradlew :api:test` PASS + 기존 `DwtStateServiceTest` GREEN |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 사이클은 `dwt_m` 컬럼 추가 + 응답 DTO 표출 전용. 알람 임계값·전이 조건·복귀 조건 변경 없음. 분기점 압력이 향후 알람 임계값 평가 기준으로 활용될 가능성은 있으나 본 사이클 범위 외. |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 해당 | OT 아웃바운드 구조 (`ScadaOutboundPort`·`InterlockValidator`·`pump_interlock_p`) 백지화 중 (`§2 ⚠️ 본 절 보류`)이라 본 사이클의 인터록 평가 코드 자체는 미존재. 그러나 본 사이클의 NOT NULL 정책 채택 사유 ("`min_req_prsr` 선례 동일 — 인터록 평가 NULL/미입력 구별 불가 방지, 사이클 2 인터록 임계값 편입 가능성 대비") 가 인터록 영역의 미래 영향을 사전 고려한 설계 결정. wtp-domain-expert 안건 5 답변: "사이클 2 (pump+AI 재설계) 에서 `min_req_branch_prsr` 가 인터록 임계값으로 편입될 가능성 높음 (선례 `min_req_prsr` NOT NULL 사유 정합)". 사이클 2 의 `pump_interlock_p` 룰 데이터 신설 시 본 컬럼이 인터록 룰 임계값으로 직접 참조 예정. |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h`·`ai_mode_cd` 모두 백지화 중 (`§5 ⚠️ 본 절 일부 보류`). 본 사이클은 DWT 마스터 컬럼 추가로 사용자 의도/시스템 상태 양 축 모두 무접촉. |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `dwt_m` 은 `facility_m` 부모 JOINED 상속 — `BaseEntity` 4컬럼 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) `AuditingEntityListener` 자동 갱신으로 변경 추적 충족. 별도 `*_h` 이력 테이블 본 사이클 미수행 (선례 `min_req_prsr` 동일 유보). |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `branch` (분기, branch, 기본 데이터 도메인: 조합) 신규 등록 (송수펌프제어분석-5번섹션 ANALYZE1, 2026-05-14)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `min_req_branch_prsr` (분기점 최소 요구 압력, `min`+`req`+`branch`+`prsr`, `DOM_QTY_15_4`, `dwt_m`, NOT NULL) 신규 등록 (송수펌프제어분석-5번섹션 ANALYZE1, 2026-05-14)

---

## 산출물

- [계획안](../../../plan/20260514/송수펌프제어분석-5번섹션/PLAN1.md) (PLAN 단계 작성 예정)
