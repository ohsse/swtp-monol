---
status: approved
created: 2026-04-23
updated: 2026-04-23
---
# 하네스 개선 1차 — 계획

## 목적

`docs/analyze/20260423/harness_개선_1차/ANALYZE1.md` 승인 결과를 토대로 외부 보고서 `보고서.docx` 의 개선점 **§5.5 (TASK 경로 파싱 훅 강화), §5.6 (테스트 커버리지 최소 기준), §5.7 (RESULT TASK 규모 필드)** 를 반영한다.

## 배경

- 외부 보고서(`docs/analyze/20260423/harness_개선_1차/보고서.docx`) 가 하네스(CLAUDE.md · 규칙 · 커맨드 · 에이전트 · 훅) 의 8개 개선점을 제시.
- 사용자 결정으로 즉시 적용 가능한 1차 범위(§5.5/5.6/5.7) 만 우선 시행, 관찰 후 2/3차 범위 진행.
- **self-reform 케이스 예외**: 이번 작업의 "룰 갱신 지시서" 체크박스는 `/dev:impl` 단계에서 실제 수정과 함께 채워진다. `/dev:plan` §5b 게이트는 본 작업의 본체가 룰 갱신 자체인 특수성을 반영해 예외 처리한다 (ANALYZE1.md 승인 비고 참조).

### ANALYZE1 핵심 결정 요약
- 안건 1 (용어): 도메인 약어 신규 0건, 메타 용어 충돌 없음
- 안건 2 (도메인): §7 신설 시 각 시나리오 클래스의 **최소 필수 케이스 목록** 함께 정의 (알람 순차 전이/복귀, 인터록 재검사 포함)
- 안건 3 (패턴): 라인 커버리지 **50% 시작 → 70% 로드맵**, CI 강제 X / TASK 규모 체크는 `wtp-backend-engineer.md` 대신 **`.claude/commands/dev/review.md`** 에 추가
- 안건 4 (DB): 영향 없음, 승인

## 범위

### 포함 (1차)
| 개선점 | 대상 파일 | 변경 요지 |
|-------|----------|---------|
| §5.5 | `backend/.claude/hooks/check-task-unstage.sh` | 경로 존재성 검사 + stderr 경고 (차단 X) |
| §5.6 | `backend/.claude/rules/test-strategy.md` | §7 커버리지 최소 기준 섹션 신설 |
| §5.7 (템플릿) | `backend/.claude/rules/doc-harness.md` | RESULT{n}.md 템플릿에 `## TASK 규모` 섹션 삽입 |
| §5.7 (체크리스트) | `backend/.claude/commands/dev/review.md` | 리뷰 체크리스트에 "TASK 규모 적정성" 1줄 추가 |

### 제외 (2차/3차 범위로 이월)
- §5.1 교차 주제 인덱스 (2차)
- §5.2 ANALYZE 재진입 수동 플래그 (2차)
- §5.3 약어 승격 체크리스트 (2차)
- §5.4 docs/by-slug 링크 (3차/보류)
- §5.8 CLAUDE.md 섹션 추가 분리 (3차/보류)

## 구현 방향

### 1. `check-task-unstage.sh` — 경로 존재성 경고 (§5.5)

**현재 구조 (라인 45~54):**
```bash
# 체크박스 파일 경로 추출 (명령어 제외)
while IFS= read -r file_path; do
    [[ "$file_path" == ./* ]] && continue
    [[ "$file_path" == *gradlew* ]] && continue
    full="backend/$file_path"
    for s in "${staged_files[@]}"; do
        [ "$s" = "$full" ] && { matches+=("$full|$task_slug"); break; }
    done
done < <(grep -oP '^- \[.\] `\K[^`]+' "$task_file" 2>/dev/null || true)
```

**변경 (라인 49 직후 삽입 — `matches` 추가 *전*):**
- `full="backend/$file_path"` 다음에 경로 존재성 검사 블록 추가
- `[ ! -f "$REPO_ROOT/$full" ] && [ ! -d "$REPO_ROOT/$full" ]` 이면 stderr 에 `[경로 경고] TASK '{slug}' 에 존재하지 않는 경로: {file_path}` 출력 후 `continue` — `matches` 에 추가하지 않음 (디렉토리도 허용하므로 `-d` 동시 체크).
- 차단 없음 (exit 0 유지), 훅 전체 흐름은 그대로.
- `backend-engineer` 권고 반영: `matches` 추가 *전*에 검사해야 `git reset HEAD --` 가 존재하지 않는 경로에 대해 오류를 출력하지 않는다.

**엣지 케이스 처리:**
- 백틱 안에 명령어(`./gradlew.bat ...`) 가 오면 이미 `./` prefix 필터로 제외됨 — 추가 처리 불필요.
- 경고는 **파일 단위** 로만 출력, 동일 경로 중복 경고는 허용 (TASK 여러 개에 같은 허위 경로가 있을 수 있음).

### 2. `test-strategy.md` §7 커버리지 최소 기준 신설 (§5.6)

**삽입 위치**: 문서 말미 (§6 테스트 디렉토리·네이밍 뒤).

**§7 구조:**

```markdown
## 7. 커버리지 최소 기준

테스트 "충분함" 의 객관적 기준. 정량 수치는 권고, 도메인 시나리오 필수 3종은 의무.

### 7.1 계층별 권고 기준

| 계층 | 최소 시나리오 수 | 라인 커버리지 (권고) | Mock 허용 |
|------|---------------|------------------|---------|
| 단위 (Service/Entity) | 도메인당 3개 이상 | 50% → 70% (로드맵) | 허용 |
| 통합 (Repository 슬라이스) | 주요 쿼리당 1개 | — | 제한적 |
| E2E (골든 패스) | 도메인당 1개 | — | 금지 |

- 수치는 **권고** 이며 CI 강제(violationRules) 로 두지 않는다. JaCoCo 도입 자체는 선택 사항.
- 초기 50% 에서 단계적으로 70% 로 상향한다 (현재 api/common 모듈 test/main 비율 ≈29%).

### 7.2 도메인 시나리오 필수 3종

아래 3개 클래스는 도메인 규칙(알람 4단계·인터록·운전 모드) 과 직결되므로 **반드시 작성** 한다. 각 클래스의 **최소 필수 케이스** 까지 의무.

#### 7.2.1 `AlarmEscalationScenarioTest` (§5-1 격상)
- [ ] 정상(0) → 주의(1) 전이 (주의 임계값 초과)
- [ ] 주의(1) → 경보(2) 순차 전이 (경보 임계값 초과)
- [ ] 경보(2) → 위험/TRIP(3) 전이 (위험 임계값 초과)
- [ ] 위험(3) → 정상(0) 복귀 (복귀 조건 만족)
- [ ] 이상치 기각 → `alarm_h` UNCERTAIN 알람 기록 (`ot-integration.md §3`)

#### 7.2.2 `PumpInterlockScenarioTest` (§5-2 격상)
- [ ] 기본 — 흡입압력(PRI) 미충족 시 기동 명령 차단
- [ ] 다중 센서 — 유량(FRI)·수위(LEI) 각각의 인터록 위반 케이스
- [ ] 모든 선행조건 만족 시 기동 명령 허용
- [ ] **장애 복구 후 재검사 통과** (CircuitBreaker CLOSED 복구 후 인터록 재검사 실행)
- [ ] **장애 복구 후 재검사 실패** (인터록 위반 상태 유지 시 차단 유지) — `ot-integration.md §5` *⚠️ 절대 금지* 규정 직결

#### 7.2.3 `PumpOperationModeScenarioTest` (§5-3 격상)
- [ ] AI 자동 모드에서 수동 주파수 입력 거부
- [ ] 수동 모드 전환 시 진행 중 AI 제어 명령 취소
- [ ] 반자동 모드에서 펌프 조합 선택만 허용
- [ ] SCADA 5분 초과 중단 시 AI 자동 → 반자동 **강제 전환** (`ot-integration.md §5`)

### 7.3 권장 추가 시나리오 (필수 아님)

향후 별도 ANALYZE 를 거쳐 필수로 승격 검토:
- `ScadaQualityDegradationScenarioTest` — SCADA 품질 저하 연계 알람·모드 전환 (시간 기반 격상 통합)
- `InterlockRecoveryScenarioTest` — 아웃바운드 장애 복구 후 인터록 재검사 흐름 (위 7.2.2 를 별도 클래스로 분리 시)

### 7.4 라인 커버리지로 보증되지 않는 검증

라인 커버리지는 JVM 바이트코드 실행 여부만 집계한다. 다음 품질 지표는 커버리지로 보증되지 않으며 별도 수단으로 관리한다.

| 관심 영역 | 검증 수단 |
|----------|---------|
| 쿼리 실행 계획 · 파티션 프루닝 | `EXPLAIN (ANALYZE, BUFFERS)` (`db-patterns.md §5`) |
| 슬로우 쿼리 | p6spy 로그 + executionThreshold |
| N+1 발생 | `JOIN FETCH` · `@EntityGraph` · 배치 사이즈 검토 |

### 7.5 통합 테스트 격상 시 파티션 픽스처 주의 사항

§7.2 의 3종 시나리오가 Mock 기반에서 실제 DB 통합으로 격상될 경우:
- `alarm_h`, `ctrl_log_h` 등 시계열 테이블 INSERT 전 해당 월 파티션을 **반드시 선행 생성** (`db-patterns.md §1`, `test-strategy.md §4`)
- PostgreSQL 전용 DDL (파티션·BRIN) 필요 시 **TestContainers 도입** (`test-strategy.md §2`)
- `acq_dtm`/`rgstr_dtm` 픽스처 날짜는 **상수로 고정**, `LocalDateTime.now()` 사용 금지

### 7.6 JaCoCo 도입 예시 (선택 사항)

JaCoCo 는 커버리지 측정 *도구* 로만 사용하며 CI 실패 조건으로 두지 않는다.

```groovy
// build.gradle (모듈별, 선택)
plugins { id 'jacoco' }

jacoco { toolVersion = '0.8.12' }

test { finalizedBy jacocoTestReport }

jacocoTestReport {
    dependsOn test
    reports { xml.required = true; html.required = true }
}
```
```

### 3. `doc-harness.md` RESULT 템플릿 수정 (§5.7)

**현재 RESULT{n}.md 템플릿 (파일 §2 "문서 템플릿" 의 RESULT 섹션):**
```markdown
## 관련 작업
## 작업 요약
## 변경 사항
## 테스트 결과
## 비고
```

**변경 후:**
```markdown
## 관련 작업
## 작업 요약
## TASK 규모
<!-- Phase 수 / 체크박스 수 / 분할 여부 / 분할 근거를 한 표로 기록. 분할 기준(Phase 10 / 체크박스 60) 대비 적정성 관찰용. -->
| 항목 | 값 |
|------|----|
| Phase 수 | N |
| 체크박스 수 | N |
| 분할 여부 | Y/N |
| 분할 근거 | (Y인 경우 사유 한 줄) |
## 변경 사항
## 테스트 결과
## 비고
```

- 삽입 위치: `## 작업 요약` *직후* (backend-engineer 참고 수용). 수치가 요약 직후 와야 이후 변경 사항을 규모 맥락으로 읽을 수 있음.

### 4. `.claude/commands/dev/review.md` 리뷰 체크리스트에 1줄 추가 (§5.7)

- 기존 review 커맨드의 체크리스트 섹션에 다음 항목 1줄 추가:
  - `- [ ] TASK 규모(Phase 수/체크박스 수)가 분할 기준(Phase 10 / 체크박스 60) 대비 적절했는가`
- 추가 위치는 review.md 의 리뷰 체크리스트 섹션 중 "문서 하네스" 관련 블록 근처. (TASK 단위 관찰이므로 "프로세스 검증" 계열)

## 도메인 모델

- 신규 도메인 모델 없음 (하네스 메타 변경)

## DB 설계 변경

- 변경 없음

## 테스트 전략

1. **훅 수동 테스트** (§5.5): 
   - 임시 TASK 문서 생성 (예: `docs/tasks/20260423/harness_개선_1차/TEMP_HOOK_TEST.md`) 에 존재하지 않는 경로 체크박스 포함.
   - `git commit -m "temp hook test"` 시도 → stderr 에 `[경로 경고] ... 존재하지 않는 경로` 출력 확인.
   - 임시 TASK 롤백. **※ TEMP 파일은 절대 커밋하지 않음**.

2. **문서 링크 무결성** (§5.6/5.7): 
   - 수정 후 `grep -rn '\.md)' .claude/rules/ .claude/commands/` 로 링크 추출, 깨진 링크 없는지 확인.

3. **RESULT 템플릿 회귀 테스트** (§5.7): 
   - 본 작업의 RESULT1.md 를 새 템플릿으로 작성하여 `## TASK 규모` 섹션 자체가 첫 번째 사용 사례가 됨 (end-to-end 검증).

4. **빌드 영향 없음 확인** (전체): 
   - `./gradlew.bat build` 수행 — 본 변경은 Java 소스에 영향 없어야 하므로 빌드 결과 변동 없음.

## 제외 사항

- §5.1/5.2/5.3 은 2차 범위로 이월 (별도 슬러그 또는 `harness_개선_2차` 로 진행 예정)
- §5.4/5.8 은 3차/보류
- 실제 `AlarmEscalationScenarioTest` 등 시나리오 테스트 **코드** 구현은 별도 도메인 작업 (pump·alarm 패키지 도입 시). 본 작업은 **테스트 전략 문서상의 필수 케이스 목록 확립** 에 한정.
- JaCoCo Gradle 플러그인 **실 설치** 는 1차 범위 외. §7.6 은 가이드 예시 문서화만.

## 예상 산출물

- [태스크](../../../tasks/20260423/harness_개선_1차/TASK1.md)
