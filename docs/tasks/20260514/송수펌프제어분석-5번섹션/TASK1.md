---
status: completed
created: 2026-05-14
updated: 2026-05-18
---
# 송수펌프제어분석 5번섹션 — 최소요구관압 박스 구현 태스크

## 관련 계획
- [계획안](../../../plan/20260514/송수펌프제어분석-5번섹션/PLAN1.md)

## Phase

### Phase 1: DB 마이그레이션 DDL 신규 작성 (common 모듈)
- [x] `common/src/main/resources/db/init/V8_7__dwt_m_branch_prsr.sql` 신규 생성 — 무중단 3단계 (NULL 허용 추가 → `0.0000` 백필 → NOT NULL 전환) + `COMMENT ON COLUMN dwt_m.min_req_branch_prsr` 한국어 라벨 작성 (DOM_QTY_15_4 NOT NULL · 인터록 평가 기준값 명시) → 검증: 파일 존재 확인 + check-ddl-column-comment.sh 훅 PASS (PostToolUse 자동 차단 없음)

### Phase 2: 엔티티 변경 (common 모듈)
- [x] `common/src/main/java/com/mo/swtp/facility/domain/DistributionWaterTank.java` 수정 — 신규 필드 `minReqBranchPrsr` (BigDecimal, precision=15·scale=4, NOT NULL) 추가, `create(...)` 정적 팩토리 시그니처 +1 파라미터 + Objects.requireNonNull 추가, private 생성자 시그니처 +1 파라미터, `changeMinReqBranchPrsr(BigDecimal)` 변경 메서드 신규 추가, 클래스 Javadoc 자식 전용 컬럼 1건→2건 갱신 → 검증: ./gradlew :common:build PASS
- [x] `common/src/test/java/com/mo/swtp/facility/domain/DistributionWaterTankTest.java` 신규 생성 — create() 정상 케이스 + minReqPrsr null NPE + minReqBranchPrsr null NPE + changeMinReqPrsr 정상/null + changeMinReqBranchPrsr 정상/null 6건 케이스 → 검증: ./gradlew :common:test PASS (신규 테스트 6건 GREEN)

### Phase 3: 응답 DTO 변경 (api 모듈)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DwtStateDto.java` 수정 — 신규 필드 2건 `minReqPrsr`·`minReqBranchPrsr` (BigDecimal, @Schema 한국어 description + example) 추가, `of(...)` 정적 팩토리 파라미터 위치 facilityNm 다음 2건 삽입 (8→10), Javadoc 갱신 (4번섹션→4·5번섹션 통합 응답 명시) → 검증: ./gradlew :api:build PASS

### Phase 4: Service 매핑 변경 (api 모듈)
- [x] `api/src/main/java/com/mo/swtp/facility/service/DwtStateService.java` 수정 — `assembleDwtState(Facility dwt, ...)` 내부에서 `DistributionWaterTank dwtChild = (DistributionWaterTank) dwt;` 캐스팅 추가, `DwtStateDto.of(...)` 호출 시 facilityNm 다음에 `dwtChild.getMinReqPrsr()`·`dwtChild.getMinReqBranchPrsr()` 2건 인자 삽입, import 구문 com.mo.swtp.facility.domain.DistributionWaterTank 추가 → 검증: ./gradlew :api:build PASS

### Phase 5: Service 테스트 갱신 + 신규 매핑 검증 (api 모듈)
- [x] `api/src/test/java/com/mo/swtp/facility/service/DwtStateServiceTest.java` 수정 — 기존 픽스처에서 DistributionWaterTank.create() 호출 시 minReqBranchPrsr 인자 추가, 기존 DwtStateDto 검증 케이스에 minReqPrsr·minReqBranchPrsr 응답 매핑 assertThat 2건 추가, 신규 케이스 "5번섹션 최소요구관압 필드가 응답에 노출된다" 1건 추가 → 검증: ./gradlew :api:test PASS (기존 + 신규 케이스 모두 GREEN, 회귀 0건)

### Phase 6: 빌드 전체 검증
- [x] `./gradlew.bat clean build` 실행 → 검증: 단위 테스트 전체 GREEN. compileJava PASS. 통합 테스트 17건은 로컬 PostgreSQL 미기동 환경 의존 실패 (test-strategy.md §2) — 본 변경 무관
- [x] p6spy 로그에서 GET /api/facility/{parentFacilityId}/dwts/states 호출 시 SQL 5건 유지 확인 → 검증: 정적 보장으로 대체 — assembleDwtState 에 캐스팅 1줄만 추가, Repository 추가 호출 0건. Step 2 facilityRepository 의 Facility(JOINED 부모) 조회 시 dwt_m 자식 컬럼이 LEFT JOIN 자동 적재. 실측 p6spy 검증은 DB 의존 통합 환경 필요 (현 환경 미충족)

## 산출물
- [결과](../../../results/20260514/송수펌프제어분석-5번섹션/RESULT1.md) (Large 작업만 작성. Medium 은 생략하고 /dev:commit 으로 직행)
