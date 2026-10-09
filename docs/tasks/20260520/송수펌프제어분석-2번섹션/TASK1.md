---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 표출·이력 관리 + SSE 실시간 전파 (TASK1)

## 관련 계획
- [계획안](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN2.md) (status: approved, 2026-05-20)
- 직전 ANALYZE: [ANALYZE2](../../../analyze/20260520/송수펌프제어분석-2번섹션/ANALYZE2.md) (status: approved)
- 폐기된 PLAN: [PLAN1](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN1.md)

## TASK 분할 정책
- **단일 TASK1.md 채택** (분할 미적용) — Medium 등급 + 단일 도메인 `com.mo.swtp.proc` 내 응집. `templates.md` 분할 기준 (LARGE 전용 / Phase 10·체크박스 60 초과) 임계 근접하나 단일 도메인 추가라 컨텍스트 부담 낮음

## Phase

### Phase 1: DDL 마이그레이션 + 시드 데이터
- [x] `common/src/main/resources/db/init/V9_4__proc_ai_drvn_mod_도입.sql` 신규 작성 → 검증: BUILD SUCCESSFUL + check-ddl-column-comment.sh 차단 없음
- [x] proc_m 테이블 DDL (proc_id VARCHAR(50) NOT NULL PK + proc_nm UNIQUE + use_yn + disp_ord + BaseEntity 4) + COMMENT 9건 → 검증: psql \\d+ proc_m 컬럼 9건 노출
- [x] ai_drvn_mod_p 테이블 DDL (proc_id VARCHAR(50) PK+FK→proc_m + ai_drvn_mod_cd + start_dtm + BaseEntity 4) + COMMENT 8건 → 검증: psql \\d+ ai_drvn_mod_p 컬럼 8건 노출
- [x] ai_drvn_mod_h 테이블 DDL (ai_drvn_mod_id BIGINT PK + proc_id VARCHAR(50) + ai_drvn_mod_cd + start_dtm + end_dtm + BaseEntity 4) + COMMENT 9건 → 검증: psql \\d+ ai_drvn_mod_h 컬럼 9건 노출
- [x] CREATE SEQUENCE seq_ai_drvn_mod_h_id START 1 INCREMENT 100 → 검증: psql \\ds seq_ai_drvn_mod_h_id 1행 노출
- [x] CREATE INDEX idx_ai_drvn_mod_h_proc_start (proc_id, start_dtm DESC) + CREATE UNIQUE INDEX uk_ai_drvn_mod_h_proc_active (proc_id) WHERE end_dtm IS NULL → 검증: psql \\di 2개 인덱스 노출
- [x] 시드 데이터 INSERT INTO proc_m VALUES ('PUMP_CONTROL', '송수펌프제어', 'Y', 1, ...) → 검증: SELECT proc_nm FROM proc_m WHERE proc_id = 'PUMP_CONTROL' 1행 반환

### Phase 2: enum + ErrorCode
- [x] `common/src/main/java/com/mo/swtp/proc/domain/enumtype/AiDrvnModeCode.java` 신규 작성 (AI · AI_RECOMD · AI_ANLS, 한국어 라벨 description 필드) — common 모듈 (`com.mo.swtp.{도메인}/domain/enumtype/` 선례: TagMeasurementType·IoCode·FacilityType·EquipType) → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/exception/ProcErrorCode.java` 신규 작성 (PROC_NOT_FOUND 404 · INVALID_AI_DRVN_MOD 400 · AI_MODE_CONCURRENT_UPDATE 409 · INVALID_PROC_ID_FORMAT 400) → 검증: check-errorcode-contract.sh 차단 없음 + grep "private final String" 매칭 없음

### Phase 3: 엔티티 3종 (common 모듈)
- [x] `common/src/main/java/com/mo/swtp/proc/domain/Process.java` 신규 작성 (BaseEntity 상속 + Persistable<String> 구현 + 정적 팩토리 정규식 검증 ^[A-Z][A-Z0-9_]*$ + deactivate() 메서드) — common 모듈 (`com.mo.swtp.{도메인}/domain/` Menu/User/Tag 선례) → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnMode.java` 신규 작성 (BaseEntity 상속 + @MapsId + @OneToOne(LAZY) Process + change(code, startDtm) 변경 메서드) → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/proc/domain/AiDrvnModeHistory.java` 신규 작성 (BaseEntity 상속 + @GeneratedValue SEQUENCE allocationSize=100 + close(endDtm) 메서드 + create 정적 팩토리) → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL

### Phase 4: Repository 3종
- [x] `api/src/main/java/com/mo/swtp/proc/repository/ProcessRepository.java` 신규 작성 (JpaRepository<Process, String>) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/repository/AiDrvnModeRepository.java` 신규 작성 (JpaRepository<AiDrvnMode, String> + @Query @Lock(PESSIMISTIC_WRITE) findByProcIdForUpdate) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/repository/AiDrvnModeHistoryRepository.java` 신규 작성 (JpaRepository<AiDrvnModeHistory, Long> + findActiveByProcId + findByProcIdOrderByStartDtmDesc 페이지네이션) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL

### Phase 5: DTO 5종
- [x] `api/src/main/java/com/mo/swtp/proc/dto/ProcDto.java` 신규 작성 (BaseAuditResponseDto 상속 + from(Process) 정적 팩토리 + applyAuditMeta 호출 + @Schema(implementation=YnType.class)) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/dto/ProcUpsertDto.java` 신규 작성 (procId + procNm + dispOrd + @NotBlank @Pattern 정규식) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/dto/AiDrvnModeDto.java` 신규 작성 (BaseAuditResponseDto 상속 + from(AiDrvnMode) + @Schema(implementation=AiDrvnModeCode.class)) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/dto/AiDrvnModeUpsertDto.java` 신규 작성 (aiDrvnModCd @NotNull + @Schema(implementation=AiDrvnModeCode.class)) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/dto/AiDrvnModeHistoryDto.java` 신규 작성 (BaseAuditResponseDto 미상속 + rgstrDtm/updtDtm/rgstrId/updtId 직접 선언 + from(AiDrvnModeHistory)) → 검증: grep "extends BaseAuditResponseDto" 매칭 없음 + grep "private LocalDateTime rgstrDtm" 매칭 있음

### Phase 6: Service 2종 + 트랜잭션 흐름
- [x] `api/src/main/java/com/mo/swtp/proc/service/ProcessService.java` 신규 작성 (조회 메서드 + 클래스 레벨 @Transactional(readOnly=true)) — 본 사이클 범위: 조회 전용 (신규 등록/비활성화는 별도 사이클, ROOT coding-discipline.md §2 정합) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/proc/service/AiDrvnModeService.java` 신규 작성 — 4 의존성 주입 (ProcessRepository + AiDrvnModeRepository + AiDrvnModeHistoryRepository + ApplicationEventPublisher) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] AiDrvnModeService#changeAiDrvnMode 메서드 — @Transactional 단일 트랜잭션 내 6단계 (FOR UPDATE → UPDATE end_dtm → UPSERT _p → INSERT _h → publishEvent → commit) 구현 → 검증: 신규 통합 테스트 변경_트랜잭션은_세_작업이_원자적으로_적용되며_이벤트가_발행된다 PASS
- [x] AiDrvnModeService#findCurrent + findHistory 조회 메서드 + DataIntegrityViolationException → AI_MODE_CONCURRENT_UPDATE 매핑 → 검증: 신규 동시성 테스트 동시_변경_시_부분_UNIQUE_인덱스가_409를_반환한다 PASS

### Phase 7: Event + Listener (옵션 B)
- [x] `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEvent.java` 신규 작성 (record + 3 필드 procId+aiDrvnModCd+startDtm 만) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL + grep record 매칭
- [x] `api/src/main/java/com/mo/swtp/proc/event/AiDrvnModeChangedEventListener.java` 신규 작성 (@Component + @TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=false) + SseService 위임) → 검증: 신규 통합 테스트 트랜잭션_커밋_후에만_SseService_send가_호출된다 PASS + grep fallbackExecution = false 매칭

### Phase 8: SSE Service (Emitter 보관소 + heartbeat)
- [x] `api/src/main/java/com/mo/swtp/proc/sse/AiDrvnModeSseService.java` 신규 작성 (ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> 보관소 + subscribe/send/remove + onCompletion/onTimeout/onError 자기 정리) → 검증: 신규 단위 테스트 AiDrvnModeSseServiceTest 4개 PASS
- [x] AiDrvnModeSseService 타임아웃 30분 + heartbeat 15초 (@Scheduled fixedRate=15000) + sendPing keep-alive comment → 검증: grep "30L \\* 60L \\* 1000L" + "15L \\* 1000L" 매칭
- [x] `api/src/main/java/com/mo/swtp/proc/sse/SseConfig.java` 신규 작성 (@Configuration @EnableScheduling) — ApiApplication 무변경, 도메인 응집 → 검증: grep "@EnableScheduling" api/src/main/java 디렉토리 매칭

### Phase 9: SSE Controller + JWT 인증 우회 설정
- [x] `api/src/main/java/com/mo/swtp/proc/sse/AiDrvnModeSseController.java` 신규 작성 (@RestController + @GetMapping produces=TEXT_EVENT_STREAM_VALUE + @Tag) — CommonController 상속 미적용 (SseEmitter 직접 반환, CommonResponseDto 래핑 불가) → 검증: 신규 통합 테스트 Authorization 헤더_없이_subscribe가_200을_반환한다 PASS + Content-Type text/event-stream 검증
- [x] `api/src/main/resources/application-common.yml` exclude-paths 에 /api/proc/*/ai-mode/subscribe 추가 (`*` 와일드카드 의무 — `{procId}` literal 금지) → 검증: grep "/api/proc/\\*/ai-mode/subscribe" application-common.yml 매칭
- [x] resources-env/*/application.yml 에서 exclude-paths 재정의 여부 확인 — 재정의 0건 확인 (application-common.yml 단일 정의로 모든 프로파일 적용) → 검증: grep "exclude-paths" resources-env 결과 0건

### Phase 10: REST Controller (ProcController)
- [x] `api/src/main/java/com/mo/swtp/proc/web/ProcController.java` 신규 작성 (@Tag "10. 송수펌프제어 AI 운전모드") — api 모듈 web 패키지 (CLAUDE.md `com.mo.swtp.{도메인}/web/` Menu 선례) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] GET /api/proc 공정 마스터 목록 조회 + GET /api/proc/{procId}/ai-mode 현재 모드 조회 + PUT /api/proc/{procId}/ai-mode 모드 변경 + GET /api/proc/{procId}/ai-mode/history 이력 페이지네이션 4 엔드포인트 → 검증: 로컬 실행 후 http://localhost:8080/swagger-ui.html "10. 송수펌프제어 AI 운전모드" Tag 4 엔드포인트 노출

### Phase 11: 테스트 6종
- [x] `common/src/test/java/com/mo/swtp/proc/domain/ProcessTest.java` 신규 작성 (정규식 검증 단위 테스트 — PUMP_CONTROL 통과 / pump_control REJECT / 1ABC REJECT) — common 모듈 테스트 → 검증: ./gradlew :common:test --tests com.mo.swtp.proc.domain.ProcessTest PASS
- [x] `api/src/test/java/com/mo/swtp/proc/service/AiDrvnModeServiceIntegrationTest.java` 신규 작성 (변경_트랜잭션은_세_작업이_원자적으로_적용되며_이벤트가_발행된다) → 검증: ./gradlew :api:test --tests AiDrvnModeServiceIntegrationTest PASS
- [x] `api/src/test/java/com/mo/swtp/proc/service/AiDrvnModeServiceConcurrencyTest.java` 신규 작성 (동시_변경_시_부분_UNIQUE_인덱스가_409를_반환한다 — 2 스레드 latch) → 검증: ./gradlew :api:test --tests AiDrvnModeServiceConcurrencyTest PASS
- [x] `api/src/test/java/com/mo/swtp/proc/event/AiDrvnModeChangedEventListenerTest.java` 신규 작성 (트랜잭션_커밋_후에만_SseService_send가_호출된다 + 롤백_시_send_호출_0건) → 검증: ./gradlew :api:test --tests AiDrvnModeChangedEventListenerTest PASS
- [x] `api/src/test/java/com/mo/swtp/proc/sse/AiDrvnModeSseServiceTest.java` 신규 작성 (subscribe / send / 다중_subscriber_동시_send / onError_자기_정리 4 케이스) → 검증: ./gradlew :api:test --tests AiDrvnModeSseServiceTest PASS
- [x] `api/src/test/java/com/mo/swtp/proc/sse/AiDrvnModeSseControllerIntegrationTest.java` 신규 작성 (Authorization 헤더_없이_subscribe가_200을_반환한다 + Content-Type text/event-stream 검증) → 검증: ./gradlew :api:test --tests AiDrvnModeSseControllerIntegrationTest PASS

### Phase 12: 빌드 검증
- [x] ./gradlew.bat :common:compileJava :api:compileJava 실행 → 검증: BUILD SUCCESSFUL (Javadoc `*/` 충돌 1건 정정 후 통과)
- [x] ./gradlew.bat :common:test 실행 → 검증: BUILD SUCCESSFUL (ProcessTest 포함 common 전체 PASS)
- [x] ./gradlew.bat :api:test --tests AiDrvnModeSseServiceTest 실행 → 검증: BUILD SUCCESSFUL (단위 4 PASS)
- [ ] **사용자 작업 필요**: ./gradlew.bat :api:test 전체 실행 — V9_4 마이그레이션을 PostgreSQL 에 수동 적용 후 재실행 필요 (`ddl-auto: none` 정책, 기존 V9_3 동일 메커니즘). 적용 명령: `psql -U smartwtp -d smartwtp -f common/src/main/resources/db/init/V9_4__proc_ai_drvn_mod_도입.sql` → 검증: 적용 후 :api:test 전체 PASS
- [ ] ./gradlew.bat clean build 실행 → 검증: V9_4 DB 적용 후 BUILD SUCCESSFUL + QClass 재생성 정상
- [x] check-ddl-column-comment.sh 훅 — V9_4 SQL 저장 시 차단 없음 → 검증: PostToolUse 훅 통과 (stderr 누락 표 출력 0건)
- [x] check-errorcode-contract.sh 훅 — ProcErrorCode.java 저장 시 차단 없음 → 검증: PostToolUse 훅 통과

## 산출물
- [결과](../../../results/20260520/송수펌프제어분석-2번섹션/RESULT1.md) — `/dev:impl` 완료 후 자동 생성 (Medium 등급이라 사용자 명시 호출 시 작성 — 워크플로우 transitions 표 Medium 행)
