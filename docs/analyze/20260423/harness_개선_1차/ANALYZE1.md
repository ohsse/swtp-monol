---
status: approved
created: 2026-04-23
updated: 2026-04-23
---
# 하네스 개선 1차 — 도메인 분석

> **승인 비고 (2026-04-23)**: 본 작업의 "룰 갱신 지시서" 항목은 **작업 본체** (1차 범위는 룰 파일 수정 자체) 이므로, 체크박스 완료는 `/dev:impl` 단계에서 실제 수정과 함께 수행한다. ANALYZE approved 후 곧바로 PLAN 진입.

## 작업 배경

- 요청 요약: 외부 산출물 `보고서.docx` (Claude Code 하네스 분석 보고서) 가 제시한 8개 개선점(§5.1~§5.8) 중 **1차 범위(§5.5/5.6/5.7)** 를 반영한다. 사용자 결정으로 우선 즉시 적용 가능한 3건만 시행하고 관찰 후 2/3차 범위 진행.
- 외부 산출물:
  - `docs/analyze/20260423/harness_개선_1차/보고서.docx` (원문)
  - 핵심 인용:
    - **§5.5**: "TASK 체크박스 경로 파싱 규칙은 정확하나, 작성자 실수로 축약 경로가 섞이면 훅이 침묵으로 실패할 수 있다 → '파일 경로로 보이지만 실제 존재하지 않는 경로' 를 경고로 출력하는 검증 추가"
    - **§5.6**: "test-strategy.md 는 3계층과 픽스처를 풍부히 기술하지만 커버리지 기준이 없다 → 도메인 시나리오 최소 3종 필수, 서비스 라인 커버리지 70% 등 최소치 제시"
    - **§5.7**: "TASK 분할 기준은 경험적 임계값이다 → RESULT 에 'TASK 규모(Phase 수·체크박스 수)' 필드를 추가해 임계값의 적절성을 정량적으로 관찰"

## 회의록 (5인 팀 토픽 주도)

### 안건 1: 1차 범위의 신규 메타 용어 충돌

- 호출 에이전트: `wtp-glossary-manager` (단독)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 도메인 약어 0건 (1차 범위는 메타·인프라 변경). 신규 메타 용어 4종 (`TASK 규모`, `커버리지 최소 기준`, `라인 커버리지`, `JaCoCo`) 모두 기존 사전·네이밍과 충돌 없음. `골든 패스` 는 `test-strategy.md §3` 에서 이미 사용 중인 표현으로 재사용.
  - 도메인 시나리오 클래스 3종 (`AlarmEscalationScenarioTest`, `PumpInterlockScenarioTest`, `PumpOperationModeScenarioTest`) 모두 `naming.md` 의 `{도메인}{시나리오}ScenarioTest` 패턴 정합. `Alarm`, `Pump` 도메인 약어는 `domain-abbreviations.md` "도입 예정" 섹션에 등재되어 있어 별도 승격 불필요 (실제 패키지 구현은 향후 작업).
- Round 2: 불필요
- **결론**: 도메인 약어 사전 갱신 0건. test-strategy.md 와 doc-harness.md 2개 파일만 갱신 대상.

### 안건 2: 도메인 시나리오 필수 3종 격상의 도메인 규칙 정합성

- 호출 에이전트: `wtp-domain-expert` (단독)
- Round 1 답변 요약:
  - 통과: 알람 4단계 코드값 (0/1/2/3), 인터록 의무 체크, 운전 모드 3가지가 `test-strategy.md` 와 `ot-integration.md` 에서 일치.
  - **블로커(높음) 2건**:
    1. `test-strategy.md §5-1` 의 알람 시나리오가 0→1·0→3 두 극단만 검증, **2단계 경보 / 순차 전이 / 복귀 조건** 미검증. `ot-integration.md §5` 의 "1~5분 → 1단계, 5분 초과 → 2단계 격상" 규칙은 단계 순차 전이를 전제하는데 테스트가 이를 보증하지 못함.
    2. `test-strategy.md §5-2` 인터록 시나리오에 **장애 복구 후 재검사** 케이스 누락. `ot-integration.md §5` 의 *"⚠️ 절대 금지 — 인터록 검사를 건너뛴 채 재시도"* 규정과 직결되는 항목.
  - 권고(중간) 3건: 인터록 다중 센서 (FRI/LEI), SCADA 장애 시 강제 모드 전환, 모드 전환 매트릭스 3×3 중 핵심 미포함.
  - 추가 시나리오 후보: `ScadaQualityDegradationScenarioTest`, `InterlockRecoveryScenarioTest` 2종 제안.
- Round 2: 블로커 해소 방향 결정 필요 → 메인 클로드(오케스트레이터) 종합 판단:
  - 1차 작업의 본질은 "기존 시나리오의 **격상**" 이지 "시나리오 자체의 정의" 가 아님.
  - 그러나 "필수 3종" 으로 못박을 때 도메인 규칙 미커버가 영구화될 위험 인정.
  - **타협안**: §7 신설 시 *시나리오 클래스 3종을 필수로 명시하되, 각 클래스의 **최소 필수 케이스 목록** 을 함께 정의* 한다. 시나리오 코드 구현은 도메인 패키지 도입 시점의 별도 작업이지만, 케이스 목록은 §7 에서 사전 확립.
  - `ScadaQualityDegradationScenarioTest`, `InterlockRecoveryScenarioTest` 는 §7 에 *"권장 추가 시나리오"* 로 표기 (필수에는 미포함, 향후 별도 ANALYZE 에서 격상 결정).
- **결론**: 블로커 2건은 §7 신설 시 *"각 시나리오의 최소 필수 케이스 목록"* 작성으로 해소. 추가 시나리오 2종은 권장만 표기.

### 안건 3: 5.7 RESULT TASK 규모 필드 + 리뷰 체크리스트 배치

- 호출 에이전트: `wtp-backend-engineer` (단독)
- Round 1 답변 요약:
  - 통과: 5.5/5.6/5.7 모두 Java 코드·계층 책임·feature-based 패키지에 직접 영향 없음.
  - **권고(중간) 2건**:
    1. **5.6 라인 커버리지 70% 과도**: 현재 api 모듈 main 31 / test 9 (≈29%), common 21 / test 6 (≈28%). 70% 즉시 적용 시 신규 단계에서 블로커 수준. *50% 시작 → 70% 로드맵* 권장. JaCoCo 수치를 CI 강제(`violationRules`) 로 두면 즉시 빌드 실패하므로 *권고 기준* 표현 권장.
    2. **5.7 TASK 규모 체크 역할**: "TASK 규모 적정성" 은 *코드 패턴 검토* 가 아닌 *작업 계획 품질 검토* — 본인(`wtp-backend-engineer`) 역할 범위 벗어남. **오케스트레이터 또는 별도 메타 리뷰어** 가 적합. 본인 에이전트에 1줄 추가는 SRP 위반.
  - 참고(낮음) 2건:
    - RESULT 신규 섹션 위치는 `## 비고` 직전이 아닌 **`## 작업 요약` 직후** 가 적합 (수치는 요약 직후 와야 이후 변경 사항을 규모 맥락으로 읽음).
    - 5.5 훅 경로 검사 위치는 라인 49~50 사이 (`matches` 추가 *전*) — 매칭 후 검사하면 `git reset HEAD --` 가 존재하지 않는 경로에 대해 `error: pathspec did not match` 출력.
- Round 2: 불필요. 권고 2건 모두 수용.
- **결론**:
  - 5.6: 라인 커버리지 *권고 기준 50% 시작 → 70% 로드맵*, JaCoCo 는 *선택 사항*, CI 강제 X.
  - 5.7: TASK 규모 체크는 `wtp-backend-engineer.md` 에 추가하지 않고 **`.claude/commands/dev/review.md`** (오케스트레이터 단계) 의 체크리스트 1줄로 추가. RESULT 템플릿 자체에는 `## 작업 요약` 직후에 `## TASK 규모` 섹션 배치.
  - 5.5: 경로 존재성 검사 삽입 위치는 `check-task-unstage.sh` 라인 49~50 사이.

### 안건 4: DB·시계열 파티션 영향

- 호출 에이전트: `wtp-dba-reviewer` (단독)
- Round 1 답변 요약:
  - **DB 영향 0건**. 1차 범위 3건 모두 DDL/마이그레이션/파티션/인덱스/쿼리에 영향 없음.
  - 참고(낮음) 1건: 시나리오 테스트가 향후 통합 테스트로 격상될 경우 시계열 파티션 픽스처 사전 준비 필요 (파티션 선행 생성 의무, TestContainers 격상, `acq_dtm` 픽스처 날짜 고정화).
  - 라인 커버리지는 N+1·EXPLAIN·파티션 프루닝을 보증하지 않음 → §7 에 *DBA 관점 별도 검증(EXPLAIN, p6spy)* 병기 권고.
- Round 2: 불필요.
- **결론**: DBA 관점 1차 범위 승인. §7 에 "라인 커버리지는 DB 관점 보증 아님 — EXPLAIN/p6spy 별도 검증 필요" 한 줄 병기. 통합 테스트 격상 시 파티션 픽스처 주의 사항 §7 말미에 함께 명시.

## 신규 용어 카탈로그

| 용어 | 후보 위치 | 분류 | 결정 |
|------|----------|------|------|
| `TASK 규모` | `doc-harness.md` RESULT 템플릿 신규 섹션명 | 신규 | 신설 (Phase 수 / 체크박스 수 / 분할 여부 / 분할 근거) |
| `커버리지 최소 기준` | `test-strategy.md` 신규 §7 섹션명 | 신규 | 신설 |
| `라인 커버리지` | `test-strategy.md` §7 본문 | 신규 | JaCoCo 측정 기준 — 권고 기준으로 표현 |
| `골든 패스` | `test-strategy.md` §7 + 기존 §3 | 기존 재사용 | §3 표현 유지 (재정의 X) |
| `JaCoCo` | `test-strategy.md` §7 도구명 | 신규 | 외부 도구 고유명 — 약어 사전 등록 X, 선택 사항 명시 |
| `AlarmEscalationScenarioTest` | `test-strategy.md` §7 + 기존 §5-1 | 기존 재사용 | 패턴 정합 ✓ |
| `PumpInterlockScenarioTest` | `test-strategy.md` §7 신규 명시 | 신규 | 패턴 정합 ✓ — §5-2 시나리오의 클래스명 명시 |
| `PumpOperationModeScenarioTest` | `test-strategy.md` §7 신규 명시 | 신규 | 패턴 정합 ✓ — §5-3 시나리오의 클래스명 명시 |

분류값: **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

## 신규 엔티티/DB 컬럼

- 신규 엔티티: 없음 (메타 변경)
- 신규 DB 컬럼: 없음
- 기존 DB 영향: 없음 (DBA 검토 통과)

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 / 해소책 |
|----------|------------------|
| 5.6 도메인 시나리오 "필수 3종" 격상이 도메인 규칙(알람 순차 전이·인터록 재검사) 미커버 위험 | §7 신설 시 *각 시나리오 클래스의 최소 필수 케이스 목록* 함께 정의. 추가 시나리오 2종(`ScadaQualityDegradation`, `InterlockRecovery`) 은 권장으로만 표기 |
| 5.6 라인 커버리지 70% 가 현재 코드 규모(test/main 약 28%) 대비 과도 | 권고 기준 *50% 시작 → 70% 로드맵*, JaCoCo CI 강제 X, 선택 사항 명시 |
| 5.7 TASK 규모 체크가 `wtp-backend-engineer` 역할(코드 패턴) 범위 벗어남 | `wtp-backend-engineer.md` 변경 X. **`.claude/commands/dev/review.md`** 체크리스트에 1줄 추가 |
| 5.7 RESULT 신규 섹션 위치 | `## 비고` 직전이 아닌 **`## 작업 요약` 직후** |
| 5.5 경로 존재성 검사 위치 | `check-task-unstage.sh` 라인 49~50 사이 (`matches` 추가 *전*) — `git reset` 오류 방지 |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- 신규 도메인 모델 없음 (하네스 메타 변경)

### DB 설계 변경 초안
- 변경 없음

### 적용할 패턴
- 5.5: 셸 스크립트 가드 패턴 — 추출된 경로 → 존재성 검사 → 차단 없는 stderr 경고
- 5.6: 정량 기준 권고 + 선택 도구 패턴 — 라인 커버리지 % 는 권고, JaCoCo 도입은 선택, CI 강제 X
- 5.7: 메타 데이터 수집 + 오케스트레이터 검토 패턴 — RESULT 작성 시 자동 기입, `/dev:review` 단계에서 적정성 검토

### 변경 대상 파일 (확정)
| 개선점 | 파일 |
|-------|------|
| 5.5 | `backend/.claude/hooks/check-task-unstage.sh` |
| 5.6 | `backend/.claude/rules/test-strategy.md` |
| 5.7 RESULT 템플릿 | `backend/.claude/rules/doc-harness.md` |
| 5.7 리뷰 체크리스트 | `backend/.claude/commands/dev/review.md` (※ wtp-backend-engineer.md 가 아님) |

→ **수정 4개 파일, 신규 0개 파일**

### 1차 범위 검증 방법
1. 가짜 TASK 문서(존재하지 않는 경로 체크박스) 임시 생성 → `git commit` 시도 → stderr 경고 출력 확인 → 임시 TASK 롤백
2. `.claude/rules/test-strategy.md` §7 신설 후 깨진 링크 없는지 grep
3. `/dev:review` 체크리스트 추가 후 다음 작업의 REVIEW 단계에서 자연 검증

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> **self-reform 케이스**: 이 작업의 본체가 룰 갱신 자체이므로 체크박스는 `/dev:impl` 단계에서 실제 수정과 함께 완료된다. 각 항목 옆의 "✓ impl 완료" 표기는 구현 직후 추가됨.

- [x] `.claude/rules/test-strategy.md` — `## 7. 커버리지 최소 기준` 섹션 신설 ✓ impl 완료
  - 도메인 시나리오 필수 3종 + 각 클래스의 최소 필수 케이스 목록 (알람 순차 전이/복귀, 인터록 재검사 포함)
  - 라인 커버리지 권고 기준 (50% 시작 → 70% 로드맵, CI 강제 X)
  - JaCoCo Gradle 설정 예시 (선택 사항 명시)
  - DBA 관점 별도 검증 병기 (EXPLAIN/p6spy 는 라인 커버리지로 보증 안 됨)
  - 권장 추가 시나리오 2종 표기 (`ScadaQualityDegradationScenarioTest`, `InterlockRecoveryScenarioTest`)
  - 통합 테스트 격상 시 파티션 픽스처 주의 사항 (선행 생성 / TestContainers / 날짜 고정화)
- [x] `.claude/rules/doc-harness.md` — RESULT{n}.md 템플릿에 `## 작업 요약` 직후 `## TASK 규모` 섹션 추가 (Phase 수 / 체크박스 수 / 분할 여부 / 분할 근거) ✓ impl 완료
- [x] `.claude/commands/dev/review.md` — 리뷰 체크리스트에 "TASK 규모(Phase 수/체크박스 수)가 분할 기준(Phase 10 / 체크박스 60) 대비 적절했는가" 1줄 추가 ✓ impl 완료
- [x] `.claude/hooks/check-task-unstage.sh` — 경로 존재성 검사 + stderr 경고 출력 추가 (exit 0 유지, 차단 X). 추가 발견: `grep -oP` 가 Windows Git Bash 환경에서 locale 문제로 실패하여 기존 훅 파싱 자체가 작동하지 않던 상태 — 본 구현에서 `sed -n 's/^- \[.\] \`\([^\`]*\)\`.*/\1/p'` 로 대체 ✓ impl 완료

## 산출물

- [계획안](../../../plan/20260423/harness_개선_1차/PLAN1.md)
