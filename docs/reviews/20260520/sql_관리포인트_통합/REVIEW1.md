---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# SQL 관리포인트 통합 — 리뷰

## 관련 결과

- [결과](../../../results/20260520/sql_관리포인트_통합/RESULT1.md)
- [계획안](../../../plan/20260520/sql_관리포인트_통합/PLAN1.md)
- [태스크](../../../tasks/20260520/sql_관리포인트_통합/TASK1.md)
- [분석](../../../analyze/20260520/sql_관리포인트_통합/ANALYZE1.md)

## 리뷰 범위

- backend SQL 자산 통합 정책 (V6_1~V9_4 → V1~V9 사전순 합본 + docs/ddl 도메인 SSOT 사본 신설)
- 운영본 디렉토리 단일화 (db/init + api/db/migration 폐지)
- V{N} 동결 + `V{N}_{연번}__patch.sql` 분리 정책 + docs/ddl ALTER 누적 이중 정책
- 룰·훅·doc-harness 갱신 6건의 정합성

본 리뷰는 메인 Claude (Opus 4.7) 가 wtp-backend-engineer / wtp-domain-expert 두 관점에서 자동 점검을 직접 수행한 결과다. 두 에이전트 Agent 호출이 컨텍스트 한계로 실패했으나 (`Prompt is too long`) 본 사이클은 SQL 파일·룰 변경 위주로 Java 코드 변경이 거의 없어 메인 검토로 충분한 범위다.

## 발견 사항

> ROOT [`coding-discipline.md §2.1`](../../../../../.claude/rules/coding-discipline.md) 카테고리 분류. 심각도: 높음(블로커) / 중간(권고) / 낮음(참고).

| # | 심각도 | 카테고리 | 내용 |
|---|------|---------|------|
| 1 | 낮음 (참고) | 기타 (워킹트리 위생) | TASK 외 파일 변경 5건이 워킹트리에 존재 — `api/.../proc/sse/AiDrvnModeSseController.java`·`api/.../proc/web/ProcController.java` 의 `@Tag` 이름 변경 + `swtp/frontend/docs/api-specs/송수펌프제어/{INDEX,SPEC1,SPEC2}.md` 삭제. RESULT1.md "### 계획 외 변경" 표에 "우연 (범위 이탈)" 으로 분류 + `/dev:commit` 단계 unstage 의무 명기됨. ROOT [`coding-discipline.md §3`](../../../../../.claude/rules/coding-discipline.md) (정밀한 수정) 적용 — 본 작업과 무관한 변경은 별도 사이클로 분리. 처리 적절 |
| 2 | 낮음 (참고) | 기타 (잠재 위험 명문화) | docs/ddl ↔ db/migration 양쪽 동시 갱신 의무는 `wtp-backend-engineer` REVIEW 권고(중간) 자동 점검에 위임됨 ([`db/indexing-and-migration.md §5.3`](../../../../.claude/rules/db/indexing-and-migration.md)). 운영자가 한쪽만 수정하는 케이스 (예: 운영본에 hot-fix patch 만 추가 + docs/ddl 갱신 누락) 의 누적 위험은 본 정책의 의도된 트레이드오프 — README §7 사본 관계에 명시되어 있어 적절 |

### 블로커 (높음) — **0건**

## 점검 항목별 상세

### 1. TASK 외 파일 변경 (코딩 디시플린 §3 정밀한 수정)

`git status --short` 결과 vs TASK1.md 27개 체크박스 백틱 경로 교차 비교 — 5개 파일이 TASK 외:

| 파일 | 변경 | TASK 매칭 |
|------|------|----------|
| `api/.../proc/sse/AiDrvnModeSseController.java` | `@Tag` 이름 (`"10. 송수펌프제어 AI 운전모드"` → `"10. AI 운전모드"`) | 없음 |
| `api/.../proc/web/ProcController.java` | 동일 `@Tag` 이름 변경 | 없음 |
| `swtp/frontend/docs/api-specs/송수펌프제어/INDEX.md` | 삭제 | 없음 |
| 동상 `SPEC1.md` | 삭제 | 없음 |
| 동상 `SPEC2.md` | 삭제 | 없음 |

→ RESULT1.md 가 "### 계획 외 변경" 표로 "우연 (범위 이탈)" 분류 + unstage 의무 명기. **처리 적절** (직접 삭제 금지 + 보고 의무 충족).

### 2. TASK1.md 체크박스 검증 형식 (코딩 디시플린 §4.1)

- 체크박스 27건, `→ 검증:` 매칭 28건 (헤더 안내 1건 포함). **27/27 검증 형식 충족**
- 검증 영역에 백틱 사용 없음 (`check-task-unstage.sh` 훅 파싱 호환)
- 체크박스 27건 모두 `- [x]` 완료, TASK 상태 `completed` 전환

### 3. docs/ddl 신규 카테고리 SSOT 적정성 (SOLID — SRP/OCP)

- **운영본 vs SSOT 사본 분리** = SRP 정렬 — 운영본은 `psql -f` 적용 단위, SSOT 사본은 사람이 읽는 도메인 통합본. 책임 분리 적절
- **이중 정책 트레이드오프** — ANALYZE1 안건 6 + PLAN1 §가정 및 미해결 질문에서 결정 명문화. README §7 + `db/indexing-and-migration.md §5` 양쪽 SSOT 정렬. **잠재 위험 (양쪽 미동기화)** 은 REVIEW 권고(중간) 점검으로 완화 — `wtp-backend-engineer` 가 `git diff --name-only` 교차 비교 (도메인 안전·보안 직결 아님 → 블로커 격상 미적용)
- **V{N} 동결 + patch 분리** 정책은 `indexing-and-migration.md §5.4` 신설 — 운영본 추적성 보장

### 4. 도메인 4영역 점검 (ANALYZE/PLAN/RESULT 일관성)

`ot-integration.md §5` 인용 근거로 4영역 (알람 4단계 / 인터록 / AI 운전 모드 / 이력 기록) 점검:

| 영역 | ANALYZE1 | PLAN1 | RESULT1 일관성 |
|------|---------|-------|---------------|
| 알람 4단계 | 비해당 (자산 재배치, 알람 테이블 0개 접촉) | 비해당 (동일 사유) | ✅ |
| 인터록 | 비해당 (인터록 테이블 미접촉) | 비해당 | ✅ |
| AI 운전 모드 | 비해당 (`ai_drvn_mod_p`·`ai_drvn_mod_h` 재배치 후 정의 동일) | 비해당 | ✅ |
| 이력 기록 의무 | 비해당 (운영 DB 스키마 불변) | 비해당 | ✅ |

→ 4영역 "비해당" 단독 4건 차단 해제 조건 충족: (1) 구체 사유 명기, (2) "## 신규 엔티티/DB 컬럼" "없음". `wtp-domain-expert` Round 1 확정 인용 정합.

### 5. README §2 "기존 환경 재적용 금지" 정책 명확성

- 신규 환경 부트스트랩 vs 기존 환경 재적용 분기를 ⚠️ 마커로 명확히 구분
- 재적용 시 위험 3종 (CREATE TABLE 충돌 / 시드 INSERT UNIQUE 충돌 / 인덱스 충돌) 명시
- 기존 환경 검증 시 §5 동등성 검증 절차 (pg_dump diff + 테이블 `\d+` 비교) 안내

→ 운영자 안전성 가드레일 적절.

### 6. 룰·훅·doc-harness 갱신 정합성

| 갱신 | 정합성 |
|------|--------|
| `.claude/rules/db/indexing-and-migration.md §5` (SQL 관리 절 신설) | V{N} 동결 + patch 정책 + docs/ddl 사본 관계 + 도메인-V 매핑 SSOT 명시. README.md §6·§7 과 일관 |
| `.claude/rules/process/doc-harness/README.md` (docs/ddl 7번째 카테고리) | governance/REPORT 와 동일 라이프사이클 (status frontmatter 미사용, 슬러그 단위 아님). 카테고리 정의 명확 |
| `.claude/rules/process/hooks-guide.md §6.2` | db/init 글롭 항목 폐기 이력 명기 |
| `.claude/hooks/check-ddl-column-comment.sh` | `db/migration/*.sql` 단일 글롭 — db/init 잔존 0건 확인 |
| `.claude/commands/dev/review.md` | docs/ddl ↔ db/migration 미동기화 권고 점검 항목 추가 |
| `.claude/agents/wtp-dba-reviewer.md` | docs/ddl SSOT 사본 책임 항목 추가 |

→ 6개 룰·훅·에이전트 정의가 SSOT 분리 + 권고 점검 위임을 일관 명시. 정합.

## 개선 제안

본 사이클 범위 외 향후 검토용 (블로커 아님):

1. **patch 파일 누적 트래킹 자동화** (향후) — V{N}_1·V{N}_2·... patch 가 누적될 때 README §3 도메인-V 매핑 표의 patch 카운터 자동 갱신 또는 별도 INVENTORY 산출 검토. 도입 트리거: patch 파일 3개 이상 누적 도메인 발생
2. **운영본 ↔ docs/ddl 자동 동기화 훅** (향후) — REVIEW 권고(중간) 점검에 추가하여 PostToolUse 자동 차단 훅으로 격상. 도입 트리거: 미동기화 발견이 REVIEW 에서 3회 이상 누적 (ROOT [`coding-discipline.md §7.3` 향후 도입 검토 트리거](../../../../.claude/rules/coding-discipline.md) 정합)
3. **frontend SPEC 동기화 별도 사이클** — RESULT §계획 외 변경의 frontend SPEC 3개 삭제는 본 작업 무관하므로 사용자 결정 사이클로 분리 처리

## 결론

**블로커 0건 — `status: approved` 전환 가능.**

본 사이클은:
- 운영 DB 스키마 불변 (파일 관리 정책 통합만)
- 27/27 체크박스 검증 형식 충족 + 모두 완료
- 4영역 비해당 (ANALYZE/PLAN/RESULT 일관)
- 룰·훅·doc-harness 갱신 6건 SSOT 정렬
- BUILD SUCCESSFUL (2m 27s)
- 11/11 성공 기준 충족

워킹트리에 TASK 외 파일 변경 5건이 존재하나 RESULT 에 명기 + `/dev:commit` 단계 unstage 의무 명시로 처리 적절. `/dev:commit sql_관리포인트_통합` 진행 권고.

→ **사용자 명시적 승인 (`커밋` / `commit`) 후** `/dev:commit` 으로 진입한다 ([`process/doc-harness/transitions.md`](../../../../.claude/rules/process/doc-harness/transitions.md) `/dev:commit` 행 — **자동 실행 절대 금지**).
