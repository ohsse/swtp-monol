---
status: approved
created: 2026-06-04
updated: 2026-06-04
---
# 전력피크분석-1번섹션 — 전력피크 목표값 수정·저장 + SSE 실시간 전파 도메인 분석

## 작업 배경

전력피크 분석 화면(`backend/image/전력피크분석.png`)의 **1번 섹션**은 운전원이 *목표 피크 전력값(kW)* 하나를 입력하고 저장하는 설정 박스다(이미지: "피크치 설정" 입력값 + "저장" 버튼, "목표 피크 전력 900" 표시).

- 이 목표값은 정수장(테넌트) 전체가 공유하는 **시스템 전역 단일 값**이다.
- 한 운전원이 값을 변경하면 같은 화면을 구독하는 **모든 사용자**가 즉시 변경을 인지해야 한다 → SSE 단방향 push.
- 현재 이 값을 저장할 테이블이 존재하지 않는다 → 신규 설정 테이블 1개 신설.
- 직전 사이클(송수펌프제어분석-2번섹션, proc 도메인 AI 운전모드)에서 확립한 `DomainEventEntity` + 전용 `EventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `SseService` 패턴을 재현한다.

### 사용자 확정 사항 (요건 잠금)
| 항목 | 결정 |
|------|------|
| 적용 범위 | 시스템 전역 단일 값 |
| 변경 이력 | 마스터만 갱신 + 감사메타 (이력 `_h` 테이블 없음) |
| 도메인 배치 | 기존 `opt` 도메인(`com.mo.swtp.opt`) 확장 |

> 외부 산출물: `backend/image/전력피크분석.png` (1번 섹션 = 단일 입력 + 저장).

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 전력피크 목표값 신규 어휘 등록 (4층 사전)
- 호출 에이전트: `wtp-glossary-manager` (Round 1 + Round 2 reconcile)
- **Round 1 결론**:
  - 신규 표준 단어 `peak`(피크) — `standard-words.md` 미등록, 어근·철자 충돌 없음, 풀네임 4자(`rate`·`end` 선례)
  - 신규 표준 단어 `target`(목표) — `tgt`(3자, `tag`와 철자 혼동)·`goal`(비즈니스 목표 어감) 대비 `target`(6자) 채택. `format`·`quality`·`segment` 풀네임 선례 정합. `stng`(설정 동사형)·`min`/`req`·`base` 와 의미 경계 분리
  - `elpwr`(전력 순시, kW) + `DOM_QTY_15_4` 기존 재사용 (목표 피크값 단위·타입 일치, `elceg` kWh 와 혼용 아님)
  - 표준 용어 `target_peak_elpwr` — 수식어-선행 어순(`min_req_prsr` 선례): `target`+`peak`+`elpwr`. 역순 `peak_target_elpwr`은 의미 모호. `predc_elpwr_amt`(AI 예측 산출물)와 의미 분리
- **Round 2 결론 (DBA의 `DOM_CODE_20` PK 확정 반영)**:
  - PK 컬럼 물리명 **`peak_cd`** — 고정 코드값 PK는 `cd`(코드, `DOM_CODE_20`) suffix 정합(`ai_drvn_mod_cd`·`drive_type_cd`·`tag_se_cd` 선례). `_id`는 UUID/외부할당 식별자 전용이라 혼동 회피. 신규 단어 `config` 등록 회피
  - 테이블 물리명 **`opt_peak_target_p`** — `opt`(비즈니스 도메인) + `peak` + `target` + `_p`. `target` 포함으로 향후 피크 관련 타 테이블과 혼동 방지. `_p`(명세/설정값) 유지

### 안건 2: 단일 전역 행 설정 테이블 설계 + 데이터 도메인 2차 승인
- 호출 에이전트: `wtp-dba-reviewer`
- **Round 1 결론**:
  - PK 데이터 도메인: **`DOM_CODE_20` 재사용** 확정 (고정 코드값 `'PEAK_TARGET'`, `DOM_ID_50`는 의미 과부하). 신규 데이터 도메인 불요
  - 단일 행 강제: **(A) 고정 코드값 PK + `CHECK (peak_cd = 'PEAK_TARGET')`** 채택 — `proc_id` 외부할당 PK + `Persistable<String>` 선례 정합, DB 레벨 2행 이상 INSERT 차단
  - `target_peak_elpwr` **NOT NULL** 필수 — 미설정/임계값 성격 값의 NULL 미입력 구별 방지(`min_req_prsr` NOT NULL 선례). `DOM_QTY_15_4` 정밀도(정수부 11자리)는 kW 수백~수천 범위에 적정
  - 인덱스: 단일 행·PK 조회만 → **추가 인덱스 불요** 확정
  - **시드 행 DDL 포함 권고** — NOT NULL 컬럼이므로 `INSERT` 시드 1행 포함, 최초 GET null 경로 회피
  - 동시성: 최초 행 부재 시 `SELECT FOR UPDATE`는 직렬화 불가 → UPSERT 또는 **시드로 부재 제거** 권고
  - `COMMENT ON COLUMN` 의무 + BaseEntity 4컬럼 표준 라벨
- **오케스트레이터 동시성 종합**: 시드 행으로 부재를 제거하면 런타임 저장은 **항상 UPDATE**가 되어 ① `SELECT FOR UPDATE`가 동시 변경을 정상 직렬화하고 ② JPA `entity.change()` → `registerEvent()` 도메인 이벤트가 정상 발화한다(네이티브 `INSERT ... ON CONFLICT` UPSERT는 JPA 생명주기를 우회해 도메인 이벤트가 누락되므로 배제). DBA 권고(시드)와 backend 이벤트 패턴이 양립.

### 안건 3: opt 도메인 첫 Controller/Service/ErrorCode + SSE 패턴 정합
- 호출 에이전트: `wtp-backend-engineer`
- **Round 1 결론**:
  - opt 도메인 첫 `web`/`service`/`repository`/`exception`/`event`/`sse` 도입 — `CommonController` 상속·`@Transactional(readOnly=true)` 기본 정합. `OptErrorCode implements ErrorCode`(httpStatus만, message 금지)
  - 엔티티 `common` 배치 + `DomainEventEntity` 상속(감사메타+이벤트 겸용) + `create()/change()` 내부 `registerEvent()` 정합. **블로커(높음)**: 전용 `PeakTargetEventPublisher`·`SseService`·`Listener`는 반드시 `api` 모듈 배치(common→api 빈 의존 불가, `NoSuchBeanDefinitionException` 회피) → PLAN 가정 명시
  - SSE 보관소: 전역 단일 채널이므로 `ConcurrentHashMap` 대신 **`CopyOnWriteArrayList<SseEmitter>` 단일 리스트가 §2 단순성에 부합** → 구독자 수 수십 이하 가정 PLAN 명기
  - `@EnableScheduling`: proc `SseConfig`로 앱 전역 이미 활성 → **opt 전용 config 신설 금지**(의미 없는 중복)
  - DTO: 응답 `PeakTargetDto extends BaseAuditResponseDto`, 요청 `PeakTargetUpsertDto`(`BigDecimal`+`@NotNull`/`@Positive`). enum/참조형 없으니 `@Schema(implementation)` 불요
  - `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=false)` 명시 권장
  - SSE는 `coding-discipline.md §2.5` 면책 미해당(도메인 안전 패턴 아님), Service 저장 메서드 50줄 초과 위험 낮음

### 안건 4: 도메인 룰 4영역 점검
- 호출 에이전트: `wtp-domain-expert`
- **Round 1 결론**: 알람 4단계·인터록·AI 운전모드·이력 기록 의무 **4영역 모두 비해당** (각 구체 사유는 아래 "## 도메인 룰 4영역 점검" 표). SSE payload 비민감화는 proc 3필드 record 선례 대비 정합(목표값+시각 수준, 개인식별정보 미포함). 목표값이 추후 피크 제어 임계값으로 편입되면 4영역 재점검 필요 → 가정 섹션 명기 권고.

---

## 표준 사전 카탈로그

### 신규 표준 단어
| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `peak` | 피크 | 신규 | `standard-words.md` 미등록. 어근·철자 충돌 없음. 풀네임 4자(`rate`·`end` 선례). `opt` 비즈니스 도메인과 층위 다름 |
| `target` | 목표 | 신규 | `standard-words.md` 미등록. `tgt`(`tag` 혼동)·`goal`(어감) 대비 `target` 채택(`format`·`quality`·`segment` 풀네임 선례). `stng`·`min`/`req`·`base` 와 의미 경계 분리 |

### 신규 표준 데이터 도메인
없음 — `DOM_CODE_20`(`peak_cd`) · `DOM_QTY_15_4`(`target_peak_elpwr`) 모두 기존 재사용 (DBA 2차 승인).

### 신규 표준 용어
| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `peak_cd` | `peak`(신규) + `cd`(기존) | `DOM_CODE_20` | 신규 | `opt_peak_target_p` PK. 고정 코드값(`'PEAK_TARGET'`) NOT NULL + `CHECK` 제약. `Persistable<String>` 필수 |
| `target_peak_elpwr` | `target`(신규) + `peak`(신규) + `elpwr`(기존) | `DOM_QTY_15_4` | 신규 | 목표 피크 전력값 (kW), NOT NULL. 수식어-선행 어순(`min_req_prsr` 선례) |

분류값: **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

---

## 신규 엔티티/DB 컬럼

- **엔티티**: `PeakTarget` — `com.mo.swtp.opt.domain` (`common` 모듈). `DomainEventEntity` 상속(BaseEntity 감사메타 4 + 도메인 이벤트 겸용), `Persistable<String>` 구현(외부할당 코드 PK). `change(BigDecimal targetPeakElpwr, ...)` 에서 `registerEvent(PeakTargetChangedEvent)`.
- **테이블**: `opt_peak_target_p` (`_p` 명세/설정값, 단일 전역 행, 시계열 아님 → 파티션·BRIN 불요)
  | 컬럼 | 데이터 도메인 | NULL | 비고 |
  |------|-------------|------|------|
  | `peak_cd` | `DOM_CODE_20` | NOT NULL | PK, 고정 코드값 `'PEAK_TARGET'`, `CHECK (peak_cd = 'PEAK_TARGET')` |
  | `target_peak_elpwr` | `DOM_QTY_15_4` | NOT NULL | 목표 피크 전력값 (kW) |
  | `rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id` | `DOM_DTM`·`DOM_ID_50` | — | BaseEntity 4 표준 라벨 |
- **DDL**: 운영본 `common/src/main/resources/db/migration/V5_1__opt_patch.sql`(V5 동결→patch 분리) + SSOT 사본 `backend/docs/ddl/opt.sql`(양쪽 동시 갱신). 시드 1행 `INSERT` 포함(기본값은 PLAN 결정). 전 컬럼 `COMMENT ON COLUMN` 의무.
- **인덱스**: 추가 없음 (단일 행, PK 조회).

---

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소 |
|----------|------|------|
| PK 데이터 도메인 — glossary `DOM_ID_50` vs DBA `DOM_CODE_20` | DBA 2차 승인 권한 우선 | **`DOM_CODE_20`** 확정. PK 컬럼 `peak_cd`(`_cd` suffix) 로 정합 |
| 테이블 suffix — 사용자 "마스터(`_m`)" vs 네이밍 룰 `_p`(설정값) | `naming.md` 경계 해석 우선 | 단일 행 설정값 → **`_p`** 채택(`ai_drvn_mod_p` 선례). ※ 사용자 확인 대상 |
| 동시성 — `SELECT FOR UPDATE`(proc 선례) vs 최초 INSERT 부재 | 시드 행으로 부재 제거 | **시드 + 항상 UPDATE + FOR UPDATE** — DBA 권고와 도메인 이벤트 패턴 양립 |
| `target_peak_elpwr` vs `predc_elpwr_amt` | 의미 분리 | 목표값(운전원 입력) vs AI 예측 산출물 — 중복 없음 |

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델**: `PeakTarget`(common, DomainEventEntity, Persistable<String>) + `PeakTargetChangedEvent`(record, common) + `PeakTargetEventPublisher`(api, `AbstractDomainEventPublisher<PeakTarget>`) + `PeakTargetChangedEventListener`(api, AFTER_COMMIT) + `PeakTargetSseService`(api, 단일 리스트) + `PeakTargetSseController`(api) + `PeakTargetService`/`PeakTargetRepository`/`PeakTargetDto`/`PeakTargetUpsertDto`/`OptErrorCode`(api).
- **DB 설계**: `opt_peak_target_p` 신설(`V5_1__opt_patch.sql` + `docs/ddl/opt.sql`), 시드 1행, CHECK 제약, COMMENT 전 컬럼.
- **저장 흐름**: `SELECT FOR UPDATE` 싱글톤 → `entity.change()` → `publisher.changeAndPublish()` → commit → AFTER_COMMIT `sseService.broadcast()`.
- **API**: `GET /api/opt/peak-target`(조회) · `PUT /api/opt/peak-target`(수정·저장) · `GET /api/opt/peak-target/subscribe`(SSE, `text/event-stream`).
- **인증**: 구독 고정 경로를 `auth.jwt.exclude-paths` 에 와일드카드 없이 등록.
- **적용 패턴**: proc SSE 패턴 복제(clean-break, 재사용/일반화 금지) + BaseAuditResponseDto + DomainEventEntity 전용 Publisher.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 엔티티는 `common`, Publisher·SseService·Listener·Controller·Service 는 `api` 모듈 배치 | 가정 | backend-engineer 블로커 해소 — proc 레이아웃 정합 |
| SSE 구독자 수 수십 이하 (`CopyOnWriteArrayList` 쓰기 복사 비용 무시 가능) | 가정 | 정수장 운영자 단말 수 개. 멀티 인스턴스 시 Redis pub/sub 별도 사이클 |
| 단일 backend 인스턴스 (지자체별 단일 배포, `multi-tenant.md`) | 가정 | SSE 인메모리 보관소 전제 |
| 시드 행 기본 목표값(`target_peak_elpwr` 초기값) | 미해결 → PLAN 결정 | NOT NULL이므로 구체값 필요(0 vs 운영 기본값). "미설정" 의미 표현 방식 PLAN 확정 |
| 테이블 suffix `_p`(설정값) 채택 — 사용자 "마스터" 표현과 차이 | 미해결 → 사용자 승인 시 확인 | `naming.md` `_p` 정합. 사용자가 `_m` 선호 시 조정 |
| SSE 인증 우회 정책 proc 선례 동일 적용 | 가정 | payload 비민감화 전제 |
| 목표값을 본 사이클에서 알람 임계값·피크 제어로 사용하지 않음 | 가정 | 추후 피크 제어/초과 알람 편입 시 도메인 4영역 점검 재진입, 본 ANALYZE 비해당 판정 무효화 |
| 변경 이력 `_h` 테이블 미보유 (현재 값만 유지) | 가정 | 감사 추적 요구 발생 시 이력 테이블 재검토 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 목표값 저장 시 `PeakTargetChangedEvent` 1건 발행 | `PeakTargetService` 단위 테스트 — `publisher.changeAndPublish` 호출 verify (Mockito) |
| 잘못된 값(0·음수·null) 저장 거부 | `PeakTargetService`/DTO 검증 테스트 — `RestApiException(OptErrorCode.INVALID_PEAK_TARGET_VALUE)` 또는 Bean Validation 400 |
| 다중 구독자에게 broadcast 전파 | `PeakTargetSseService` 단위 테스트 — emitter 2건 등록 후 broadcast 시 2건 모두 send 호출 verify + 자기정리 |
| 저장→조회 왕복 일관성 | 통합 테스트(`@SpringBootTest(NONE)`) — PUT 저장 후 GET 시 변경값 반환 |
| SSE 컨트롤러 인증 우회 + 스트림 타입 | 통합 테스트 — 토큰 없이 200 + `text/event-stream` Content-Type |
| AFTER_COMMIT 발화 | 리스너 테스트 — commit 후에만 broadcast 호출 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 목표 피크값의 설정·저장·SSE 전파만 수행. 알람 임계값·전이·복귀 조건 로직 무접촉. `alarm_h` 기록 없음. 추후 피크 초과 알람 편입 가능성은 가정 섹션 명기 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | PLC 제어 명령 발행 경로 없음(설정 저장+전파만). `⚠️ 절대 금지`(인터록 건너뛰기)와 접점 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`/`ai_mode_cd` 읽기·쓰기 없음. SCADA 5분 강제 전환 트리거와 무접촉 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 변경 이력 테이블(`_h`) 미보유(본 사이클 전제). `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 와 무접촉 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `peak`(피크) 신규 등록
- [x] `swtp/.claude/rules/dict/standard-words.md` — `target`(목표) 신규 등록
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `peak_cd` 신규 등록 (`opt_peak_target_p` PK, `DOM_CODE_20`, NOT NULL, 고정 코드값 `'PEAK_TARGET'` + `CHECK` 제약)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `target_peak_elpwr` 신규 등록 (`opt_peak_target_p` 컬럼, `DOM_QTY_15_4`, NOT NULL)

> `cd`(코드, `DOM_CODE_20`)·`elpwr`(전력 순시, `DOM_QTY_15_4`) 기존 등록 — 신규 등록 불요. 표준 데이터 도메인 신규 등록 없음. 비즈니스 도메인 약어 신규 없음(`opt` 재사용).

## 산출물
- [계획안](../../../plan/20260604/전력피크분석-1번섹션/PLAN1.md)
