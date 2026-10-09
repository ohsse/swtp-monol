---
status: completed
created: 2026-04-20
updated: 2026-04-20
---
# 스마트정수장 하네스 보강 — 도메인/DB 가이드 및 검토 게이트 Task

## 관련 계획
- [계획안](../../../plan/20260420/harness_도메인_DB_보강/PLAN1.md)

## Phase

### Phase 1: 핵심 rules 신설 + CLAUDE.md 보강 (P0)

- [x] `CLAUDE.md` 수정 — 코드 작성 규칙 섹션에 `domain-glossary.md`, `db-patterns.md` 참조 링크 및 사용 시점(엔티티 명명·PLAN 작성 시 반드시 참조) 명시
- [x] `.claude/rules/domain-glossary.md` 신설 — legacy/docs/dictionary 3종 xlsx 핵심 용어(정수장 시설·측정값·운전 모드·알람 4단계) 추출 및 `한국어 용어 | 영문명 | DB 약어 | 단위 | 설명` 표 형식 정리
- [x] `.claude/rules/db-patterns.md` 신설 — 시계열 파티셔닝(월 단위 RANGE), 인덱스 원칙(복합·BRIN·GIN), 스키마 무중단 변경 원칙(NOT NULL 3단계·CONCURRENTLY), 트랜잭션 격리 기준, p6spy 슬로우 쿼리 활용 절차, 데이터 보존 기간 정책 기술
- [x] `.claude/commands/dev/plan.md` 수정 — 분석 항목에 "레거시 산출물 참조 목록(KWS-GS-*)" 추가, PLAN 문서 템플릿에 `## 도메인 모델` 섹션 및 `## DB 설계 변경` 섹션 추가

### Phase 2: 검토 게이트 및 에이전트 신설 (P1)

- [x] `.claude/agents/wtp-domain-expert.md` 신설 — 도메인 정합성(엔티티 명명·알람 4단계·인터록·운전 모드·KWS-GS 참조) 검토 전문 서브에이전트 명세 작성
- [x] `.claude/agents/wtp-dba-reviewer.md` 신설 — DB 스키마·쿼리 성능(파티션·인덱스·FK·마이그레이션 무중단·N+1·p6spy 분석) 검토 전문 서브에이전트 명세 작성
- [x] `.claude/commands/dev/plan.md` 수정 — PLAN 초안 완료 후 "도메인/DB 검토 필요 여부" 체크 단계 삽입, wtp-domain-expert·wtp-dba-reviewer spawn 지시 및 검토 결과 PLAN 문서 부록 첨부 안내 추가
- [x] `.claude/commands/dev/review.md` 수정 — 체크리스트에 ① 도메인 규칙 정합성(알람 4단계·인터록·운전 모드 누락) ② 쿼리 성능(N+1·인덱스·페이징) ③ 레거시 동등성(KWS-GS 요구사항 누락) ④ OT 연동 안전성(재시도·회복성·센서 품질) 4개 항목 추가
- [x] `.claude/rules/legacy-mapping.md` 신설 — legacy/ems·pms → 신규 엔티티 매핑 작성 의무, KWS-GS-*-AN04/AN05 요구사항 추적표 갱신 절차, 동등성 검증 테스트 시나리오 기술 (TASK2에서 완료)

### Phase 3: 확장 가이드 신설 (P2)

- [x] `.claude/rules/multi-tenant.md` 신설 — 지자체별 공통화·프로파일 분기·별도 모듈 결정 기준, resources-env/{지자체명} 빌드 구조 연계 방침
- [x] `.claude/rules/ot-integration.md` 신설 — 인바운드(SCADA→Spring)/아웃바운드(Spring→PLC) 어댑터 구조, Resilience4j 재시도·서킷브레이커, 센서 품질 관리(누락 대체값·이상치 기각 기준) 가이드
- [x] `.claude/rules/test-strategy.md` 신설 — 단위/통합/E2E 분리 기준, TestContainers + PostgreSQL 시계열 픽스처 전략, 알람 4단계 전이·인터록·운전 모드 전환 도메인 시나리오 테스트 가이드

### Phase 4: 통합 검증 및 마무리

- [x] `CLAUDE.md`, `.claude/commands/dev/impl.md`, `.claude/commands/dev/review.md`에서 Phase 1~3에서 신설한 모든 rules 참조 누락 여부 교차 확인 및 보완 수정
- [x] `.claude/agents/wtp-domain-expert.md`, `.claude/agents/wtp-dba-reviewer.md` 메타데이터(model·description·tools 등) 형식이 Agent 도구 호출 규격과 일치하는지 검증
- [x] `.claude/commands/dev/review.md` 확장 체크리스트의 서브에이전트 spawn 지시 문구 정확성 확인
- [x] `./gradlew.bat build` 실행 성공 확인

## 산출물
- [결과](../../../results/20260420/harness_도메인_DB_보강/RESULT1.md)
