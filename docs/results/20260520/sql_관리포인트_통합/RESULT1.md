---
status: draft
created: 2026-05-20
updated: 2026-05-20
---
# SQL 관리포인트 통합 — 결과

## 관련 작업

- [계획안](../../../plan/20260520/sql_관리포인트_통합/PLAN1.md)
- [태스크](../../../tasks/20260520/sql_관리포인트_통합/TASK1.md)
- [분석](../../../analyze/20260520/sql_관리포인트_통합/ANALYZE1.md)

## 작업 요약

backend SQL 자산의 관리 포인트를 **이중 정책** 으로 통합했다.

- **운영본**: `common/src/main/resources/db/migration/V1__{도메인}.sql ~ V9__{도메인}.sql` (9개 도메인 사전순 합본, V{N} 동결 + `V{N}_{연번}__patch.sql` 분리 정책)
- **도메인 SSOT 사본**: `backend/docs/ddl/{도메인}.sql` 9개 (사람이 읽는 도메인 단위 통합본, ALTER 결과 인라인 누적)
- **폐기**: `common/src/main/resources/db/init/` 전체 (16개) + `api/src/main/resources/db/migration/` 전체 (3개) + 구 `V9_1`·`V9_2`·`V9_3` (3개) — 총 22개 파일

V6_1~V9_4 의 ALTER 흡수 결과만 인라인 정렬하여 V1~V9 사전순 (auth → facility → instrument → menu → opt → proc → raw → tag → user) 으로 재구성했다. 운영 DB 스키마 자체는 **불변** 이며, 기존 환경 재적용은 금지된다 (README §2).

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 5 |
| 체크박스 수 | 27 |
| 분할 여부 | N |
| 분할 근거 | — (Phase 10 / 체크박스 60 임계 미달) |

## 변경 사항

### 의도된 변경

#### 신규 작성 (19개 파일)

| 카테고리 | 파일 | 용도 |
|---------|------|------|
| 도메인 SSOT 사본 | `backend/docs/ddl/auth.sql` (10 COMMENT) | refresh_token_p |
| 도메인 SSOT 사본 | `backend/docs/ddl/facility.sql` (18 COMMENT) | facility_m + 자식 5종 |
| 도메인 SSOT 사본 | `backend/docs/ddl/instrument.sql` (20 COMMENT) | instrument_m + 자식 6종 |
| 도메인 SSOT 사본 | `backend/docs/ddl/menu.sql` (15 COMMENT) | menu_m + menu_role_r |
| 도메인 SSOT 사본 | `backend/docs/ddl/opt.sql` (6 COMMENT) | predc_1m_h |
| 도메인 SSOT 사본 | `backend/docs/ddl/proc.sql` (24 COMMENT) | proc_m + ai_drvn_mod_p + ai_drvn_mod_h |
| 도메인 SSOT 사본 | `backend/docs/ddl/raw.sql` (10 COMMENT) | rawdata_1m_h |
| 도메인 SSOT 사본 | `backend/docs/ddl/tag.sql` (10 COMMENT) | tag_m |
| 도메인 SSOT 사본 | `backend/docs/ddl/user.sql` (9 COMMENT) | user_m |
| 운영본 | `common/src/main/resources/db/migration/V1__auth.sql` ~ `V9__user.sql` (9개) | docs/ddl 와 `diff` 0 라인 차이 동일 |
| 운영자 안내 | `common/src/main/resources/db/migration/README.md` (16 ##/###) | 7섹션: 목적 / ⚠️ 기존 환경 재적용 금지 / 도메인-V 매핑 / 적용 명령 / 동등성 검증 / V{N} 동결 + patch 정책 / docs/ddl 사본 관계 |

#### 삭제 (22개 파일)

| 디렉토리 | 파일 수 | 사유 |
|---------|--------|------|
| `common/src/main/resources/db/init/` | 16 | 폐지 — `db/migration/` 단일 디렉토리로 일원화 |
| `common/src/main/resources/db/migration/V9_1`·`V9_2`·`V9_3` | 3 | V8/V9 패치가 V1~V9 합본으로 흡수됨 |
| `api/src/main/resources/db/migration/` | 3 | 폐지 — common 단일 위치로 일원화 |

#### 룰·훅 갱신 (ANALYZE1 단계에서 선행 완료, 본 RESULT 에 결과 보고)

| 파일 | 변경 |
|------|------|
| `.claude/rules/db/indexing-and-migration.md` | §5 SQL 관리 — 도메인별 단일 파일 + 이중 정책 신설 (5.1~5.4 절) |
| `.claude/rules/process/doc-harness/README.md` | docs/ddl 7번째 카테고리 추가 + 매핑 표 |
| `.claude/rules/process/hooks-guide.md` | §6.2 `db/init` 글롭 항목 폐기 이력 명기 |
| `.claude/hooks/check-ddl-column-comment.sh` | `**/db/init/*.sql` 글롭 제거 (단일 `**/db/migration/*.sql` 만 유지) |
| `.claude/commands/dev/review.md` | docs/ddl ↔ db/migration 미동기화 권고 점검 항목 추가 |
| `.claude/agents/wtp-dba-reviewer.md` | docs/ddl SSOT 사본 책임 항목 추가 |
| `.claude/settings.local.json` | (보조 설정 — 본 사이클 관련 권한 항목) |

### 계획 외 변경

본 사이클 작업과 무관한 변경이 워킹트리에 함께 존재한다. **TASK 외 파일 변경** 으로 분류되며 별도 커밋·사이클에서 처리 필요.

| 분류 | 파일 | 변경 | 처리 |
|------|------|------|------|
| 우연 (범위 이탈) | `api/src/main/java/com/mo/swtp/proc/sse/AiDrvnModeSseController.java` | `@Tag(name = "10. 송수펌프제어 AI 운전모드", …)` → `"10. AI 운전모드"` | 본 커밋에 포함하지 않음 — Tag 명 정리 별도 사이클 |
| 우연 (범위 이탈) | `api/src/main/java/com/mo/swtp/proc/web/ProcController.java` | 동일 `@Tag` 이름 변경 | 동일 |
| 우연 (범위 이탈) | `swtp/frontend/docs/api-specs/송수펌프제어/INDEX.md` · `SPEC1.md` · `SPEC2.md` | 삭제 | 본 커밋에 포함하지 않음 — frontend 명세 정리는 별도 사이클 (`/dev:spec` 호출 또는 사용자 결정) |

> 위 5개 파일은 본 사이클 TASK1.md 의 27개 체크박스 어디에도 명시되지 않았다. ROOT [`coding-discipline.md §3`](../../../../.claude/rules/coding-discipline.md) (정밀한 수정) 정합 — 본 사이클 커밋에서 unstage 후 별도 작업으로 분리한다.

## 테스트 결과

### 빌드 검증

```
./gradlew.bat clean build
BUILD SUCCESSFUL in 2m 27s
22 actionable tasks: 21 executed, 1 up-to-date
```

- 3개 모듈(common · api · scheduler) 모두 통과
- JPA `ddl-auto: none` 정책상 V1~V9 운영본이 빌드 시점에 실행되지 않음 — 빌드 성공은 자바 코드의 스키마 의존성 무손상 의미. 운영본 적용 자체의 정합성은 §README §5 동등성 검증 절차에 위임

### 동등성 검증 (운영본 ↔ docs/ddl 사본)

```
diff common/src/main/resources/db/migration/V{N}__{도메인}.sql backend/docs/ddl/{도메인}.sql
```

9 도메인 모두 **0 라인 차이** 확인 (TASK1 Phase 2 체크박스 9건 PASS).

### 룰 정합성 재확인

| 검증 | 결과 |
|------|------|
| `grep -rn "db/init" .claude/rules/ .claude/hooks/ .claude/agents/ .claude/commands/` | 잔존 0건 (이력·폐기·2026-05-20 컨텍스트 외) |
| `grep "db/migration" .claude/hooks/check-ddl-column-comment.sh` + `grep "db/init" .claude/hooks/check-ddl-column-comment.sh` | 단일 글롭 매칭 + db/init 잔존 0건 |
| `check-ddl-column-comment.sh` 훅 자체 실행 (V1~V9 9파일 대상) | 누락 0건 — 122 COMMENT ON COLUMN 모두 매칭 |

## 성공 기준 충족도 (PLAN1.md §성공 기준 11건)

| 성공 기준 | 검증 명령 | 결과 |
|---------|---------|------|
| 1. docs/ddl 9 파일 존재 + COMMENT 의무 충족 | `ls backend/docs/ddl/*.sql \| wc -l` = 9 / 122 COMMENT | ✅ |
| 2. V1~V9 운영본 9 파일 작성 | `ls common/.../db/migration/V*.sql \| wc -l` = 9 | ✅ |
| 3. 운영본 ↔ SSOT 사본 동일 | `diff` 9쌍 모두 0 라인 차이 | ✅ |
| 4. README.md 작성 (7섹션) | `grep -c "##" README.md` = 16 (## + ### 합) | ✅ |
| 5. db/init 디렉토리 부재 | `test -d common/.../db/init` = 비-zero | ✅ |
| 6. api/db/migration 디렉토리 부재 | `test -d api/.../db/migration` = 비-zero | ✅ |
| 7. 구 V9_x 부재 | `test -f V9_1·V9_2·V9_3` = 비-zero | ✅ |
| 8. BUILD SUCCESSFUL | `./gradlew.bat clean build` | ✅ |
| 9. db/init 룰 영역 잔존 0건 | grep 결과 갱신 이력 외 0건 | ✅ |
| 10. 훅 글롭 단일화 | `db/migration` 매칭 + `db/init` 0건 | ✅ |
| 11. ddl-column-comment 훅 자체 실행 (V1~V9) | 122 COMMENT 모두 매칭, 누락 0건 | ✅ |

## 비고

- **운영 DB 스키마 불변**: 본 사이클은 **파일 관리 정책 통합** 이며 운영 DB 스키마 자체는 변경 없음. 기존 환경 적용 금지 (README §2 강제).
- **사이클 외 변경 unstage 의무**: §변경 사항 §계획 외 변경 표의 5개 파일은 `/dev:commit` 단계에서 staging 영역에 포함되지 않도록 명시적 unstage 필요.
- **frontend SPEC 영향 없음**: 본 사이클은 entity·DTO·Controller 시그니처 변경이 없어 `/dev:spec` 호출 불필요. SPEC 삭제는 본 작업과 무관.
- **다음 단계**: `/dev:review sql_관리포인트_통합` 자동 전이 (Large 작업의 transitions.md SSOT 정책).
