---
status: completed
created: 2026-06-04
updated: 2026-06-04
---
# 전력피크분석-1번섹션 — 전력피크 목표값 수정·저장 + SSE 실시간 전파

## 관련 계획
- [계획안](../../../plan/20260604/전력피크분석-1번섹션/PLAN1.md)
- [선행 분석](../../../analyze/20260604/전력피크분석-1번섹션/ANALYZE1.md)

## Phase

> 계층 의존 순서(DDL → common 엔티티/이벤트 → api 인프라 → api 응용 → 설정 → 테스트 → 빌드)로 분해. 체크박스 형식: `- [ ] {파일경로} 작업 → 검증: {확인 방법}` (검증 영역 백틱 금지 — 훅 파싱 충돌).
>
> **확정 사항** (PLAN1 부록 권고 반영): ① BaseEntity 4컬럼 DDL **NOT NULL** 명시, ② 저장 메서드 `@Transactional`(READ_COMMITTED) + `findByPeakCdForUpdate`(`PESSIMISTIC_WRITE`) 락 보유 최소화, ③ `PeakTargetDto.targetPeakElpwr` `@Schema` 에 "0 = 미설정" 명기, ④ `@Tag(name = "13. 전력피크 목표값")` (기존 11·12 충돌 회피 확정).

### Phase 1: DB DDL (시드 1행 + 전 컬럼 COMMENT)
- [x] `common/src/main/resources/db/migration/V5_1__opt_patch.sql` 신규 작성 — `opt_peak_target_p` CREATE TABLE (`peak_cd` VARCHAR(20) PK + `CHECK (peak_cd = 'PEAK_TARGET')`, `target_peak_elpwr` NUMERIC(15,4) NOT NULL, BaseEntity 4컬럼 모두 NOT NULL) + 전 컬럼 COMMENT ON COLUMN(BaseEntity 4 표준 라벨) + 시드 INSERT('PEAK_TARGET', 0, now(), now(), 'system', 'system') → 검증: check-ddl-column-comment.sh 차단 없이 저장 완료 (전 컬럼 COMMENT 존재)
- [x] `docs/ddl/opt.sql` 하단에 동일 CREATE TABLE + CHECK + COMMENT + 시드 INSERT 누적 (V5_1 운영본과 1:1 동일 내용) → 검증: V5_1__opt_patch.sql 과 테이블·컬럼·CHECK·시드 정의 일치 (양쪽 동시 갱신 의무 indexing-and-migration.md 5.3)

### Phase 2: common 모듈 — 엔티티 + 이벤트 record
- [x] `common/src/main/java/com/mo/swtp/opt/event/PeakTargetChangedEvent.java` 신규 — record(targetPeakElpwr BigDecimal, updtDtm LocalDateTime), Javadoc(payload 비민감화 2필드 한정 근거) → 검증: ./gradlew.bat :common:compileJava 성공
- [x] `common/src/main/java/com/mo/swtp/opt/domain/PeakTarget.java` 신규 — @Entity @Table(name=opt_peak_target_p) extends DomainEventEntity implements Persistable<String>, 상수 PEAK_TARGET_CD='PEAK_TARGET', @Id peakCd + targetPeakElpwr, getId() override, change(BigDecimal) 변경 메서드 내부 registerEvent(PeakTargetChangedEvent), create 정적 팩토리 미생성 → 검증: ./gradlew.bat :common:compileJava 성공 + QPeakTarget 생성

### Phase 3: api 모듈 — 이벤트/SSE 인프라 (proc 패턴 clean-break 복제)
- [x] `api/src/main/java/com/mo/swtp/opt/event/PeakTargetEventPublisher.java` 신규 — @Component extends AbstractDomainEventPublisher<PeakTarget>, changeAndPublish(entity) → super.publishAndClear(entity) → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/sse/PeakTargetSseService.java` 신규 — 전역 단일 CopyOnWriteArrayList<SseEmitter> 보관소, subscribe()/broadcast(event)/onCompletion·onTimeout·onError 자기정리, 15초 heartbeat @Scheduled, 30분 timeout, count() 테스트용, 이벤트명 peak-target-changed → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/event/PeakTargetChangedEventListener.java` 신규 — @Component @TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=false) handle(event) → sseService.broadcast(event) → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 4: api 모듈 — 응용 계층 (Service/Repository/DTO/ErrorCode/Controller)
- [x] `api/src/main/java/com/mo/swtp/opt/exception/OptErrorCode.java` 신규 — @Getter @RequiredArgsConstructor enum implements ErrorCode, PEAK_TARGET_NOT_INITIALIZED(500), private final int httpStatus (message 필드 금지) → 검증: check-errorcode-contract.sh 차단 없이 저장 + ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PeakTargetRepository.java` 신규 — JpaRepository<PeakTarget,String> + @Lock(PESSIMISTIC_WRITE) @Query findByPeakCdForUpdate(peakCd) → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/dto/PeakTargetUpsertDto.java` 신규 — @Data @NoArgsConstructor, targetPeakElpwr(BigDecimal, @NotNull @Positive), @Schema → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/dto/PeakTargetDto.java` 신규 — @Getter extends BaseAuditResponseDto, targetPeakElpwr(@Schema description "0 = 미설정 — 운전원 최초 저장 전"), private 생성자 + from(PeakTarget) applyAuditMeta 호출, peak_cd 미노출 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/service/PeakTargetService.java` 신규 — @Service @Transactional(readOnly=true), getPeakTarget() 조회, changePeakTarget(dto) @Transactional: findByPeakCdForUpdate(PEAK_TARGET_CD) orElseThrow(PEAK_TARGET_NOT_INITIALIZED) → entity.change() → peakTargetEventPublisher.changeAndPublish() → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/web/PeakTargetController.java` 신규 — extends CommonController, @Tag(name="13. 전력피크 목표값") @RequestMapping(/api/opt/peak-target), GET 조회 + PUT(@Valid @RequestBody PeakTargetUpsertDto) 저장, @Operation/@ApiResponses, CommonResponseDto<PeakTargetDto> → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/sse/PeakTargetSseController.java` 신규 — @Tag(name="13. 전력피크 목표값") @RequestMapping(/api/opt), @GetMapping(value=/peak-target/subscribe, produces=TEXT_EVENT_STREAM_VALUE) subscribe() → sseService.subscribe() → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 5: 설정 — SSE 구독 인증 우회
- [x] `api/src/main/resources/application-common.yml` 의 auth.jwt.exclude-paths 에 /api/opt/peak-target/subscribe 1줄 추가 (고정 경로 — 와일드카드 없음) → 검증: 파일 내 /api/opt/peak-target/subscribe 항목 존재 + 기존 proc 항목 보존

### Phase 6: 테스트 (단위 + 통합)
- [x] `api/src/test/java/com/mo/swtp/opt/service/PeakTargetServiceTest.java` 신규 — @ExtendWith(MockitoExtension.class), 저장 시 changeAndPublish 1회 verify / 시드 부재 시 PEAK_TARGET_NOT_INITIALIZED 예외 → 검증: ./gradlew.bat :api:test --tests *PeakTargetServiceTest PASS
- [x] `api/src/test/java/com/mo/swtp/opt/sse/PeakTargetSseServiceTest.java` 신규 — subscribe count=1, 다중 등록 count=3, 빈 보관소 broadcast silent, 다중 구독 후 broadcast 무예외 (count 유지). onCompletion·onTimeout·onError 자기정리는 MVC 런타임 콜백 발화 동작이라 컨트롤러 통합 테스트 + 수동 2탭 검증으로 위임 (코딩 디시플린 §1 정직성) → 검증: ./gradlew.bat :api:test --tests *PeakTargetSseServiceTest PASS
- [x] `api/src/test/java/com/mo/swtp/opt/event/PeakTargetChangedEventListenerTest.java` 신규 — AFTER_COMMIT 발화 시 sseService.broadcast 호출 verify → 검증: ./gradlew.bat :api:test --tests *PeakTargetChangedEventListenerTest PASS
- [x] `api/src/test/java/com/mo/swtp/opt/service/PeakTargetServiceIntegrationTest.java` 신규 — @SpringBootTest(NONE) @ActiveProfiles(test) @Transactional, PUT 저장 후 GET 변경값 반환(시드 기준) → 검증: ./gradlew.bat :api:test --tests *PeakTargetServiceIntegrationTest PASS (로컬 PostgreSQL smartwtp 필요)
- [x] `api/src/test/java/com/mo/swtp/opt/sse/PeakTargetSseControllerIntegrationTest.java` 신규 — 토큰 없이 subscribe 200 + Content-Type text/event-stream 검증 → 검증: ./gradlew.bat :api:test --tests *PeakTargetSseControllerIntegrationTest PASS

### Phase 7: 빌드 검증
- [x] `common` → `api` 순차 빌드 (QClass 재생성 포함) → 검증: ./gradlew.bat :common:build 후 ./gradlew.bat :api:build 모두 BUILD SUCCESSFUL

## 산출물
- [결과](../../../results/20260604/전력피크분석-1번섹션/RESULT1.md) (Medium — RESULT/REVIEW 면제, 작성 생략)
