---
status: approved
created: 2026-04-20
updated: 2026-04-20
---
# 스마트정수장 하네스 보강 — 도메인/DB 가이드 및 검토 게이트 신설

## 목적

현재 `backend/.claude/` 하네스가 코드 스타일 중심으로만 구성되어, 정수장 도메인 특수성과 DB 운영 관점을 흡수하지 못하고 있다. 재개발 진행 전에 rules / commands / agents 보강을 완료하여 잘못된 모델·스키마·비즈니스 규칙이 코드까지 굳어지는 것을 사전 차단한다.

## 배경

하네스 점검에서 다음 8개의 헛점을 식별했다:

| ID | 항목 | 심각도 |
|----|------|--------|
| G1 | 정수장 도메인 용어/약어 사전 부재 (legacy/docs/dictionary 미활용) | 높음 |
| G2 | DB 설계·마이그레이션·시계열 파티셔닝·인덱스 가이드 부재 | 높음 |
| G3 | REVIEW 체크리스트가 코드 스타일에 치우침 (비즈니스/성능/OT 누락) | 중간 |
| G4 | 자동 전이가 강해 도메인/DBA 외부 검증 게이트 없음 | 중간 |
| G5 | 멀티테넌트(지자체별) 도메인 모델링 가이드 부재 | 중간 |
| G6 | 레거시(KWS-GS 산출물) → 신규 매핑/추적 절차 부재 | 중간 |
| G7 | 외부 OT/IoT 연동(SCADA/Modbus/MQTT) 패턴 가이드 부재 | 낮음~중간 |
| G8 | pre-commit 훅이 슬러그 단위 unstage만 수행, DB 마이그레이션·산출물 검증 없음 | 낮음 |

**도메인 전문가/DBA 역할이 필요한 이유:** 현재 구성으로 진행하면 ① 코드 품질은 높지만 비즈니스가 틀린 시스템, ② 운영 환경에서 죽는 DB, ③ 레거시 KWS-GS 자산의 사장(死藏), ④ 지자체별 분기 지옥 위험이 크다.

## 범위

- 변경 대상: `backend/CLAUDE.md`, `backend/.claude/commands/dev/plan.md`, `backend/.claude/commands/dev/review.md`
- 신설 대상 (Phase 1): `backend/.claude/rules/domain-glossary.md`, `backend/.claude/rules/db-patterns.md`
- 신설 대상 (Phase 2): `backend/.claude/agents/wtp-domain-expert.md`, `backend/.claude/agents/wtp-dba-reviewer.md`, `backend/.claude/rules/legacy-mapping.md`
- 신설 대상 (Phase 3): `backend/.claude/rules/multi-tenant.md`, `backend/.claude/rules/ot-integration.md`, `backend/.claude/rules/test-strategy.md`

**범위 제외:** 실제 도메인 코드(엔티티/서비스/레포지토리), DB 마이그레이션 파일, 기존 TASK/RESULT/REVIEW 문서 수정.

## 구현 방향

### Phase 1 — P0: 즉시 보강 (다른 작업의 전제조건)

**1-1. `domain-glossary.md` 신설**
- `legacy/docs/dictionary/KWS-GS-SC-AN04-00(공통표준단어).xlsx`, `KWS-GS-SC-AN05-00(공통표준도메인).xlsx`, `KWS-GS-SC-DG01-00(공통표준용어).xlsx` 에서 핵심 용어를 추출하여 정리.
- 구조: `한국어 용어 | 영문명 | DB 약어 | 단위 | 설명` 형식의 표.
- 우선 그룹: 정수장 시설(취수/응집/침전/여과/소독/송수/배수지), 측정값(유량/압력/탁도/pH/잔류염소), 운전 모드(자동/수동/원격), 알람 4단계(HiHi/Hi/Lo/LoLo).
- 활용 시점: 신규 엔티티 명명 시, PLAN 단계 도메인 섹션 작성 시 반드시 참조.

**1-2. `db-patterns.md` 신설**
- 스키마 변경 무중단 원칙: NOT NULL 컬럼은 "DEFAULT 포함 추가 → 백필 → DEFAULT 제거" 3단계 적용. 인덱스 생성은 `CREATE INDEX CONCURRENTLY` 사용. 뷰/제약 변경은 DDL 트랜잭션 범위 내 수행.
- 마이그레이션 도구(Flyway/Liquibase) 선정은 별도 슬러그에서 결정한다. db-patterns.md는 도구 비종속 원칙만 기술.
- 시계열 테이블 설계 원칙: PostgreSQL `PARTITION BY RANGE (측정일시)`, 월 단위 파티션.
- 인덱스 설계 원칙: 복합 인덱스 컬럼 순서(등가 조건 먼저, 범위 조건 나중), BRIN 사용 기준(시계열 순차 데이터), GIN 사용 기준(JSONB 컬럼).
- 트랜잭션 격리: 기본 `READ COMMITTED`, 집계 보정 쿼리에는 `REPEATABLE READ`.
- p6spy 로그 활용 절차: 슬로우 쿼리 기준(100ms 이상), EXPLAIN ANALYZE 결과 RESULT 문서 첨부.
- 보존 기간 정책: 원시 센서 데이터 3년, 집계 데이터 10년, 알람 이력 5년.

**1-3. `dev/plan.md` 보강 — 도메인/DB 섹션 추가**
- 작성 전 분석 항목에 "레거시 산출물 참조 목록(KWS-GS-*)" 추가.
- PLAN 문서 템플릿에 `## 도메인 모델` 섹션(용어사전 참조 근거) + `## DB 설계 변경` 섹션(Flyway 파일명, 인덱스/파티션 계획) 추가.

**1-4. `CLAUDE.md` 보강**
- 신설된 `domain-glossary.md`, `db-patterns.md` 참조 링크 및 사용 시점 명시.

### Phase 2 — P1: 검토 게이트 및 리뷰 확장

**2-1. `backend/.claude/agents/wtp-domain-expert.md` 신설**
- 스마트정수장 도메인 정합성 검토 전문 서브에이전트.
- PLAN 단계: 엔티티 모델이 도메인 용어사전과 일치하는지, 알람 4단계/인터록/운전 모드가 표현되었는지, KWS-GS 알고리즘 자산을 참조했는지 검토.
- REVIEW 단계: 비즈니스 규칙 구현 누락, 단위 오류, 안전 직결 로직 여부 검토.

**2-2. `backend/.claude/agents/wtp-dba-reviewer.md` 신설**
- DB 스키마/쿼리 성능 전문 서브에이전트.
- PLAN 단계: 스키마 초안의 파티셔닝/인덱스/FK 정책 검토.
- IMPL 직전: 마이그레이션 SQL의 무중단 적용 가능성(NOT NULL 컬럼 추가 방식, 인덱스 CONCURRENTLY 사용) 검토.
- REVIEW 단계: p6spy 로그 분석, N+1 패턴, 인덱스 미사용 여부 검토.

**2-3. `dev/plan.md` 에 검토 게이트 추가**
- PLAN 초안 작성 후, 사용자 승인 요청 전에 "도메인/DB 검토 필요 여부" 체크 단계 삽입.
- 필요 시 `wtp-domain-expert`, `wtp-dba-reviewer` 서브에이전트를 각각 spawn하여 PLAN 문서 부록에 검토 결과 첨부.

**2-4. `dev/review.md` 체크리스트 확장**
- 기존 10개 항목 유지하고 아래를 추가:
  - `[ ]` 도메인 규칙 정합성 (알람 4단계, 인터록, 운전 모드 전이 누락 여부) — wtp-domain-expert spawn
  - `[ ]` 쿼리 성능 (N+1 쿼리, 인덱스 사용 여부, 페이징 누락) — wtp-dba-reviewer spawn
  - `[ ]` 레거시 동등성 (KWS-GS 요구사항 누락 여부)
  - `[ ]` OT 연동 안전성 (재시도 정책, 회복성, 센서 품질 처리 여부)

**2-5. `legacy-mapping.md` 신설**
- legacy/ems, legacy/pms 모듈과 신규 모델 간 매핑 절차 정의.
- 내용: 레거시 테이블 → 신규 엔티티 매핑 작성 의무, KWS-GS-*-AN04/AN05(요구사항정의서/추적표) 갱신 절차, 동등성 검증(레거시 대비 기능 누락 체크) 테스트 시나리오.

### Phase 3 — P2: 확장 가이드

**3-1. `multi-tenant.md` 신설**
- 지자체별 차이 흡수 위치 결정 기준 (엔티티 공통화 vs. 프로파일 분기 vs. 별도 모듈 기준).
- `resources-env/{지자체명}` 빌드 구조와 연계하여 어디까지 공통이고 어디서부터 지자체별인지 결정 흐름.

**3-2. `ot-integration.md` 신설**
- OT 연동 어댑터 패턴: 인바운드(SCADA → Spring) / 아웃바운드(Spring → PLC) 어댑터 구조.
- 재시도 정책(Resilience4j), 서킷브레이커, 센서 품질 관리(누락 대체값, 이상치 기각 기준).

**3-3. `test-strategy.md` 신설**
- 단위/통합/E2E 분리 기준.
- 시계열 픽스처: TestContainers + PostgreSQL + 날짜 시뮬레이션 전략.
- 도메인 시나리오 테스트: 알람 4단계 전이, 인터록 체인, 운전 모드 전환.

**3-4. 통합 검증**
- 신설된 모든 rules가 `dev/impl.md`, `dev/review.md`에서 참조되는지 점검.
- 누락 참조 발견 시 해당 commands 파일 추가 보강.

## 테스트 전략

본 작업은 Java 코드 변경이 없으므로 `./gradlew.bat test`는 회귀 확인 목적으로만 사용한다.

**검증 항목:**
1. `domain-glossary.md` / `db-patterns.md` / `legacy-mapping.md` / `multi-tenant.md` / `ot-integration.md` / `test-strategy.md` 가 `CLAUDE.md` 및 `dev/{plan,impl,review}.md` 에서 모두 참조되는지 수동 교차 확인.
2. `wtp-domain-expert` / `wtp-dba-reviewer` 에이전트가 `~/.claude/agents/` 에 존재하고 Agent 도구로 호출 가능한지 확인.
3. `dev/review.md` 확장 체크리스트 항목이 서브에이전트 spawn을 정확히 지시하는지 확인.
4. `check-task-unstage.sh` 훅이 본 슬러그 TASK 파일 경로를 정상적으로 파싱하는지 확인.
5. `./gradlew.bat build` 통과.

## 제외 사항

- 실제 도메인 코드 작성 (엔티티, 서비스, 레포지토리) — 본 작업 완료 후 별도 슬러그로 진행.
- 마이그레이션 도구(Flyway/Liquibase) 선정 및 도입 — db-patterns.md는 도구 비종속 일반 원칙만 제공하며, 실제 도구 도입은 별도 슬러그에서 결정.
- 마이그레이션 파일 신규 작성 — 실제 스키마는 도메인별 슬러그에서 작성.
- legacy/docs/dictionary xlsx 전체 파싱 자동화 — domain-glossary.md는 수작업 정제본으로 작성.

## 예상 산출물

- [태스크](../../../tasks/20260420/harness_도메인_DB_보강/TASK1.md)
