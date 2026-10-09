---
status: completed
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 — TASK1-3: 분리 + 링크 갱신 (Tier 2)

## 관련 분할 TASK
- [TASK1-1 SSOT 강화 + 참조 정리](TASK1-1.md)
- [TASK1-2 신규 파일 도입](TASK1-2.md)
- [TASK1-3 분리 + 링크 갱신](TASK1-3.md)
- [TASK1-4 검증](TASK1-4.md)

## 관련 계획
- [계획안](../../../plan/20260424/rules_dedup_정리/PLAN1.md)
- [도메인 분석](../../../analyze/20260424/rules_dedup_정리/ANALYZE1.md)

## Phase

### Phase 4: 분리 + 링크 갱신 (영향 범위 가장 큼)

#### T2-F — db-patterns.md 3분리 + 모든 외부 참조 갱신 (13건)

**신규 파일 생성 (4건)**
- [x] `.claude/rules/db-partitioning-and-retention.md` 신규 작성 — `db-patterns.md` §1 시계열 파티셔닝 + §6 데이터 보존 정책 이동 (수명주기 통합 관점)
- [x] `.claude/rules/db-indexing-and-migration.md` 신규 작성 — `db-patterns.md` §2 인덱스 원칙 + §3 스키마 무중단 변경 이동 (DDL/스키마 관점)
- [x] `.claude/rules/db-query-tuning.md` 신규 작성 — `db-patterns.md` §4 트랜잭션 격리 + §5 슬로우 쿼리 분석 이동 (런타임 성능 관점)
- [x] `.claude/rules/db-patterns.md` 를 인덱스 파일로 전환 (옵션 1) — 3개 자식 파일 링크 + 1줄 요약만 보유

**외부 참조 갱신 (5건)**
- [x] `.claude/rules/test-strategy.md` 의 `db-patterns.md §1`·§2·§5 참조 5곳을 새 파일·앵커로 갱신 (실측 4곳 + 파생 `dict/standard-data-domains.md` · `test-strategy-e2e-roadmap.md` 추가 갱신)
- [x] `.claude/rules/ot-integration.md` 의 `db-patterns.md §1`·§6 참조 2곳을 새 파일·앵커로 갱신 (실측 3곳 — 참조표 1곳 포함)
- [x] `.claude/rules/multi-tenant.md` 참조 표의 `db-patterns.md` 행을 새 파일로 갱신 (인덱스 파일 + "분리 인덱스" 표기로 자식 3개 안내)
- [x] `CLAUDE.md` §규칙 문서 인덱스 표의 `db-patterns.md` 행을 3행으로 분리 (T1-F 의 인덱스 보강과 통합 적용 — 중복 적용 방지) — db 4행 모두 검수 + db-patterns.md 행 설명을 "분리 인덱스" 로 갱신
- [x] `.claude/agents/wtp-dba-reviewer.md` §1 의 `db-patterns.md` 참조를 새 파일로 갱신

**에이전트 정의 검수 (3건 — 변경 없음 예상)**
- [x] `.claude/agents/wtp-glossary-manager.md` 검토 항목 §5 의 `naming.md` 참조 검수 (db-patterns 미참조 — 변경 없음)
- [x] `.claude/agents/wtp-backend-engineer.md` 검토 항목의 `entity-patterns.md` · `api-patterns.md` · `exception-patterns.md` 참조 검수 (db-patterns 미참조 — 변경 없음)
- [x] `.claude/agents/wtp-domain-expert.md` 참조 검수 (룰 파일 직접 참조 0건 — 변경 없음)

#### T2-H — ot-integration.md 헤더 태그 보강 (분리 보류, 1건)
- [x] `.claude/rules/ot-integration.md` §1·§2 헤더에 `[기술 계층]` 태그, §3 헤더에 `[데이터 처리 정책]` 태그, §4 헤더에 `[기술 계층 — 회복성]` 태그, §5 헤더에 `[비즈니스 계층 — 장애 시 운전 모드]` 태그 추가 + 문서 상단 "참조 문서 관계" 표 아래 1문장 추가 ("본 문서는 기술 계층(채널·회복성)과 비즈니스 계층(품질·장애 동작)을 함께 다룬다. OT 도입 ANALYZE 단계에서 분리 검토.")

## 산출물
- [결과](../../../results/20260424/rules_dedup_정리/RESULT1.md)
