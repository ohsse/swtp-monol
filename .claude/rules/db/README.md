# DB 운영 패턴 (인덱스)

스마트정수장 PostgreSQL 데이터베이스 설계·운영 기준의 **인덱스 페이지**다.
6대 주제는 관점별로 3개 자식 문서로 분리되어 있으며, 본 README 는 진입점·매핑 표 역할만 한다.

> **이동 이력**:
> - 2026-04-24 — 단일 파일이었던 `db-patterns.md` 의 §1~§6 을 수명주기 / DDL / 런타임 3개 관점으로 분리.
> - 2026-04-26 — root 의 4개 파일(`db-patterns.md` + 자식 3) 을 `db/` 디렉토리 하위로 이동. 본 README 가 인덱스 진입점, root 의 `db-patterns.md` 는 redirect 파일로 잔존 (구 외부 참조 호환성).

---

## 자식 문서

| 문서 | 관점 | 다루는 주제 (구 §번호) |
|------|------|---------------------|
| [`partitioning-and-retention.md`](partitioning-and-retention.md) | **데이터 수명주기** | 시계열 파티셔닝 (구 §1) · 데이터 보존 기간 정책 (구 §6) |
| [`indexing-and-migration.md`](indexing-and-migration.md) | **DDL/스키마** | 인덱스 원칙 (구 §2) · 스키마 무중단 변경 원칙 (구 §3) · `DOM_YN` DDL 정책 (§3) |
| [`query-tuning.md`](query-tuning.md) | **런타임 성능** | 트랜잭션 격리 기준 (구 §4) · p6spy 슬로우 쿼리 활용 (구 §5) |

---

## 구 섹션 → 신규 위치 빠른 매핑

과거 ANALYZE/PLAN/REVIEW 문서가 `db-patterns.md §X` 형태로 참조한 경우의 매핑.

| 구 섹션 | 신규 문서 / 신규 §번호 |
|--------|-------------------|
| `db-patterns.md §1` 시계열 파티셔닝 | [`partitioning-and-retention.md §1`](partitioning-and-retention.md) |
| `db-patterns.md §2` 인덱스 원칙 | [`indexing-and-migration.md §1`](indexing-and-migration.md) |
| `db-patterns.md §3` 스키마 무중단 변경 | [`indexing-and-migration.md §2`](indexing-and-migration.md) |
| `db-patterns.md §4` 트랜잭션 격리 | [`query-tuning.md §1`](query-tuning.md) |
| `db-patterns.md §5` p6spy 슬로우 쿼리 | [`query-tuning.md §2`](query-tuning.md) |
| `db-patterns.md §6` 데이터 보존 정책 | [`partitioning-and-retention.md §2`](partitioning-and-retention.md) |

---

> **참조 정책**: 신규 작업은 본 README 에서 자식 매핑을 확인한 뒤 작업과 직접 관련된 자식 문서만 Read 한다 (`CLAUDE.md §규칙 문서 인덱스` 의 룰 참조 정책 — on-demand). 새로운 정책·예시는 자식 문서에 추가하고 본 README 는 매핑 표만 유지한다.
