# DB 쿼리 튜닝·트랜잭션 격리

스마트정수장 PostgreSQL 의 **런타임 성능 관점** 운영 기준.
트랜잭션 격리 수준 선택 기준과 p6spy 기반 슬로우 쿼리 분석 절차, N+1 방지 원칙을 다룬다.

> 본 문서는 과거 `db-patterns.md` 의 §4 트랜잭션 격리 + §5 p6spy 슬로우 쿼리 활용을 분리한 결과다 (2026-04-24). 2026-04-26 디렉토리화로 `db/` 하위로 이동.
> 데이터 수명주기 관점은 [`partitioning-and-retention.md`](partitioning-and-retention.md), DDL/스키마 관점은 [`indexing-and-migration.md`](indexing-and-migration.md) 참조.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`README.md`](README.md) | 본 문서의 인덱스 페이지 — 진입점 (구 root `db-patterns.md` 자리) |
| [`partitioning-and-retention.md`](partitioning-and-retention.md) | 시계열 파티션 — 대용량 조회 페이지네이션 전제 |
| [`indexing-and-migration.md`](indexing-and-migration.md) | 인덱스 설계 — 슬로우 쿼리 개선 1차 도구 |

---

## 1. 트랜잭션 격리 기준

### 기본 정책
- Spring의 기본 격리 수준인 **READ_COMMITTED** 사용 (PostgreSQL 기본값과 일치).
- 집계·통계 쿼리처럼 팬텀 읽기가 문제 될 경우에만 **REPEATABLE_READ** 로컬 설정.

```java
// 일반 서비스: ReadOnly + READ_COMMITTED (기본)
@Transactional(readOnly = true)
public List<RawDataDto> findRawData(RawDataSearchDto dto) { ... }

// 집계 서비스: REPEATABLE_READ 필요 시
@Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
public EnergyStatDto calcEnergyStat(LocalDate from, LocalDate to) { ... }
```

### 주의 사항
- 시계열 대용량 조회는 **페이지네이션** 또는 **커서 기반** 분할 조회를 사용한다.
- SERIALIZABLE은 성능 저하가 크므로 배치 정산 등 명확한 필요성이 없으면 사용하지 않는다.
- 장시간 트랜잭션은 `VACUUM` 지연 및 테이블 팽창을 유발하므로 최소 범위로 유지한다.

---

## 2. p6spy 슬로우 쿼리 활용

### 슬로우 쿼리 임계값
- `p6spy` 설정(`common/src/main/resources-env/*/spy.properties`)에서 `executionThreshold` 값(기본 500ms) 이상 걸리는 쿼리는 로그에 기록된다.

### 분석 절차
1. 로컬·스테이징 환경에서 슬로우 쿼리 로그 확인
2. `EXPLAIN (ANALYZE, BUFFERS)` 실행하여 실행 계획 확인
3. Seq Scan → 인덱스 추가 검토 / Nested Loop 과다 → 조인 조건 재검토
4. 수정 후 재실행하여 비교

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM rawdata_m
WHERE pump_id = 'P-001' AND acq_dtm BETWEEN '2026-01-01' AND '2026-02-01';
```

### N+1 방지 원칙
- 연관 엔티티 조회는 `JOIN FETCH` 또는 `@EntityGraph` 사용
- 루프 내 개별 조회 금지: 배치 조회(`IN` 절) 또는 `@BatchSize`로 대체
