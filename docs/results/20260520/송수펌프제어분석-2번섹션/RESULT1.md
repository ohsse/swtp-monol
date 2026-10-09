---
status: draft
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석-2번섹션 — AI 운전모드 표출·이력 관리 + SSE 실시간 전파 (RESULT1)

## 관련 작업
- [계획안](../../../plan/20260520/송수펌프제어분석-2번섹션/PLAN2.md) (status: approved)
- [태스크](../../../tasks/20260520/송수펌프제어분석-2번섹션/TASK1.md) (status: completed)
- 직전 ANALYZE: [ANALYZE2](../../../analyze/20260520/송수펌프제어분석-2번섹션/ANALYZE2.md) (status: approved)

## 작업 요약

PLAN2 범위 (비즈니스 도메인 `proc` 신규 + 엔티티 3종 + Repository 3 + DTO 5 + Service 2 + Controller 2 + ErrorCode + SSE 인프라 4 + DDL `V9_4__proc_ai_drvn_mod_도입.sql` + JWT 인증 우회 + 테스트 6) 일괄 구현. `com.mo.swtp.proc` 패키지 신설 (api·common 양측). 변경 트랜잭션 6단계 (FOR UPDATE → end_dtm UPDATE → UPSERT _p → INSERT _h → publishEvent → commit) 흐름 + SSE AFTER_COMMIT 통지 + heartbeat 15초 + Emitter 30분 타임아웃.

## TASK 규모
| 항목 | 값 |
|------|----|
| Phase 수 | 12 |
| 체크박스 수 | 70 |
| 분할 여부 | N |
| 분할 근거 | — (단일 도메인 `com.mo.swtp.proc` 응집, LARGE 분할 임계 근접하나 컨텍스트 부담 낮음 — TASK1.md §TASK 분할 정책) |

## 변경 사항

### 의도된 변경

- DDL 마이그레이션 신규 1건: `common/src/main/resources/db/migration/V6__proc.sql` (sql_관리포인트_통합 정책 정합 — 도메인 단일 V 번호) + `docs/ddl/proc.sql` 사본
- enum 신규 1건: `common/src/main/java/com/mo/swtp/proc/domain/enumtype/AiDrvnModeCode.java` (`AI`·`AI_RECOMD`·`AI_ANLS` 3종)
- ErrorCode 신규 1건: `api/src/main/java/com/mo/swtp/proc/exception/ProcErrorCode.java` (`PROC_NOT_FOUND` 404 · `INVALID_AI_DRVN_MOD` 400 · `AI_MODE_CONCURRENT_UPDATE` 409 · `INVALID_PROC_ID_FORMAT` 400)
- 엔티티 신규 3건: `Process`·`AiDrvnMode`·`AiDrvnModeHistory` (모두 `common.proc.domain`)
- Repository 신규 3건: `ProcessRepository`·`AiDrvnModeRepository`·`AiDrvnModeHistoryRepository` (api 측, FOR UPDATE 잠금 쿼리 포함)
- DTO 신규 5건: `ProcDto`·`ProcUpsertDto`·`AiDrvnModeDto`·`AiDrvnModeUpsertDto`·`AiDrvnModeHistoryDto`
- Service 신규 2건: `ProcessService`·`AiDrvnModeService` (6단계 트랜잭션 + 동시성 충돌 매핑)
- Event/Listener 신규 2건: `AiDrvnModeChangedEvent` (record) · `AiDrvnModeChangedEventListener` (`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=false)`)
- SSE 인프라 신규 3건: `AiDrvnModeSseService` (`ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>` 보관소 + heartbeat 15초 + 30분 타임아웃) · `AiDrvnModeSseController` (`GET /api/proc/{procId}/ai-mode/subscribe`) · `SseConfig` (`@EnableScheduling`)
- Controller 신규 1건: `ProcController` (4 REST 엔드포인트, `@Tag "10. 송수펌프제어 AI 운전모드"`)
- JWT 인증 우회 설정: `application-common.yml` `auth.jwt.exclude-paths` 에 `/api/proc/*/ai-mode/subscribe` 추가 (AntPathMatcher `*` 와일드카드)
- 테스트 신규 6건: `ProcessTest` (common) · `AiDrvnModeServiceIntegrationTest` · `AiDrvnModeServiceConcurrencyTest` · `AiDrvnModeChangedEventListenerTest` · `AiDrvnModeSseServiceTest` · `AiDrvnModeSseControllerIntegrationTest`

### 계획 외 변경

- TASK1 Phase 12 작성 시 Javadoc `*/` 충돌 1건 정정 (체크박스 81 비고) — 컴파일 차단 해소 목적, 범위 이탈 아님
- TASK1 Phase 9 `application-common.yml` exclude-paths 검증 시 resources-env/*/application.yml 재정의 0건 확인 (체크박스 67) — 검증성 보강, 범위 이탈 아님

## 테스트 결과

| 항목 | 결과 |
|------|------|
| `./gradlew :common:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :api:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :common:test` (`ProcessTest` 포함) | BUILD SUCCESSFUL |
| `./gradlew :api:test --tests AiDrvnModeSseServiceTest` | BUILD SUCCESSFUL (단위 4 PASS) |
| `./gradlew :api:test` 전체 | **사용자 작업 잔존** — `V6__proc.sql` 을 PostgreSQL 에 수동 적용 후 재실행 필요 (`ddl-auto: none` 정책). 적용 명령: `psql -U smartwtp -d smartwtp -f common/src/main/resources/db/migration/V6__proc.sql` |
| `./gradlew clean build` | **사용자 작업 잔존** — V6 DB 적용 후 실행 |
| `check-ddl-column-comment.sh` 훅 | 차단 없음 (V6 SQL 저장 시) |
| `check-errorcode-contract.sh` 훅 | 차단 없음 (`ProcErrorCode.java` 저장 시) |

## 비고

- 본 사이클은 ANALYZE2 결정 (사용자 의도 단일축 + 시스템 상태/강제 전환 제외) 의 범위 내 완료. 강제 전환 정책 (SCADA 5분 초과 자동 전환·OUTBOUND_FAIL 사유 등 `ot-integration.md §5` 보류 영역) 은 별도 사이클 보류 (PLAN2 §제외 범위 정합)
- 본 RESULT1 는 사실 보고. 패턴 정합성 점검 결과는 REVIEW1 단계에서 다룬다
