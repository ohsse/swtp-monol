---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# 거버넌스 진단 슬래시 명령 (`/governance`) 신설 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260506/governance_health_check/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: 첫 백틱 쌍에 파일 경로, `→ 검증:` 다음에 확인 방법 (검증 영역 백틱 사용 금지 — `check-task-unstage.sh` 훅 파싱 충돌 방지).

### Phase 1: governance.md 헤더 + 정량 진단 (Q1~Q4)

- [x] `backend/.claude/commands/governance.md` 신규 작성 — 헤더 (목적·사용자 주기 호출·자동 실행 금지·자동 수정 없음 정책) + 절차 진입점 + 슬래시 명령 호출 패턴 → 검증: ls backend/.claude/commands/governance.md 존재 확인 + grep "자동 수정 없음\|보고만" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` Q1 정량 진단 절차 추가 — CLAUDE.md 줄 수 (ROOT + backend) wc -l + 매핑 표 vs 직접 정책 기술 분리 측정 (표 형식 80% 임계값, Phase 6 dogfood 에서 조정 가능 명시) → 검증: grep "Q1" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` Q2 룰 파일 줄 수 분포 5구간 (less than 30 / 30 to 50 / 50 to 200 / 200 to 500 / 500 plus) 추가 → 검증: grep "Q2" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` Q3 300줄 plus 단일 파일 식별 + 단일 관심사 vs 복합 관심사 구분 컬럼 추가 (현 시점 후보 3건: templates.md 376 / test-strategy.md 343 / entity-patterns.md 318 명기) → 검증: grep "Q3" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` Q4 redirect stub 외부 참조 grep — db-patterns.md 와 doc-harness.md 2건 확정 + .claude/rules/ 스코프 vs docs/ 스코프 이중 분리 → 검증: grep "Q4" backend/.claude/commands/governance.md 매칭 1건 이상

### Phase 2: governance.md 정합 진단 (R1~R12)

- [x] `backend/.claude/commands/governance.md` R1 4영역 SSOT 정책 본문 고유 문구 grep 추가 — SCADA_TIMEOUT, OUTBOUND_FAIL, MANUAL_EXPIRE, SYSTEM_INIT, hold last value, last_rcv_dtm 6 키워드 → 검증: grep "R1" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R2 Cycle 1 산출물 정합 — templates.md ANALYZE 3섹션 vs dev/plan.md §5b 1:1 grep 추가 → 검증: grep "R2" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R3 REVIEW1 #1 자동 식별 — templates.md 의 ../../ot-integration 깨진 경로 grep 명령 명기 → 검증: grep "R3" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R4 REVIEW1 #3 자동 식별 — impl.md vs commit.md 의 규모 판단 또는 Large 키워드 비교 명령 명기 → 검증: grep "R4" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R5 §2.5 면책 인용 근거 vs 실사용 매칭 grep 추가 (코드 주석 §2.5 면책 검색) → 검증: grep "R5" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R6 coding-discipline.md 4원칙 본문 복제 grep — Think Before Coding, Simplicity First, Surgical Changes, Goal-Driven Execution 4 키워드 외부 등장 검출 → 검증: grep "R6" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R7 ROOT 3층 vs backend 1층 SSOT 분리 grep — DOM_* 정의 행 검출 + 폐기 어휘 코드 잔존 5건 (pmp_, reg_id, tag_id, tag_val, cntom) → 검증: grep "R7" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R8 dict/README.md 2개 분리 인덱스 정합 grep — VARCHAR, BIGINT, NUMERIC, LocalDateTime 정책 본문 키워드 미존재 검증 → 검증: grep "R8" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R9 feature-dev:code-reviewer 인용 grep — backend/.claude/commands/ 스코프 매칭 시 중간 심각도 보고 (메모리 정책 적용) → 검증: grep "R9" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R10 신규 어휘 0건 검증 — ANALYZE 사전 카탈로그 3표 "없음" 정합 점검 절차 → 검증: grep "R10" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R11 DB 룰 정책 본문 복제 grep — PARTITION BY, BRIN, CONCURRENTLY, executionThreshold, EXPLAIN ANALYZE 5 키워드 자식 3 외 등장 + 정책 지침 vs 예시 코드 구분 → 검증: grep "R11" backend/.claude/commands/governance.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` R12 redirect stub 이중 표 (현행 룰 참조 vs docs 이력 참조) + DOM_* 미사용 도메인 식별 (코드 순회 grep) → 검증: grep "R12" backend/.claude/commands/governance.md 매칭 1건 이상

### Phase 3: 거짓 양성 구분 정책 명문화

- [x] `backend/.claude/commands/governance.md` "거짓 양성 구분" 섹션 추가 — "레이블 + 파일 링크 = 인용 / 임계값·값·시퀀스 본문 = 복제" + "정책 지침 문맥 = 복제 후보 / 예시 코드·테스트 도입 트리거 언급 = 인용" 두 정책 명기 → 검증: grep -E "레이블\|정책 지침\|예시 코드" backend/.claude/commands/governance.md 매칭 3건 이상
- [x] `backend/.claude/commands/governance.md` REPORT 발견사항 출력 형식 명문화 — "자동 확정 / 사람 검토 필요" 두 카테고리 분리 + 사람 검토 행에 ⚠️ 마커 명기 → 검증: grep "자동 확정\|사람 검토 필요\|⚠️" backend/.claude/commands/governance.md 매칭 2건 이상

### Phase 4: 보고서 산출물 위치 + doc-harness 갱신

- [x] `backend/docs/governance/.gitkeep` 신설 — Phase 6 dogfood 시 첫 REPORT 자동 생성 전 디렉토리 확보 → 검증: test -d backend/docs/governance 결과 exit 0
- [x] `backend/.claude/rules/process/doc-harness/README.md` §디렉토리 구조에 docs/governance/{YYYYMMDD}/REPORT.md 카테고리 추가 — 5종 산출물 외 6번째 카테고리 명시 + status frontmatter 미사용 라이프사이클 명시 (재호출 시 누적, 단순 마크다운 보고서) → 검증: grep "docs/governance" backend/.claude/rules/process/doc-harness/README.md 매칭 1건 이상
- [x] `backend/.claude/commands/governance.md` 보고서 산출물 위치 명기 (docs/governance/{YYYYMMDD}/REPORT.md) + 재호출 시 누적 정책 + status frontmatter 미사용 명시 → 검증: grep "docs/governance" backend/.claude/commands/governance.md 매칭 1건 이상

### Phase 5: backend CLAUDE.md 인덱스 갱신

- [x] `backend/CLAUDE.md` §규칙 문서 인덱스 표 또는 별도 섹션에 /governance 슬래시 명령 안내 행 추가 — 호출 빈도 (사용자 주기) + 점검 항목 (정량 4 + 정합 12) + 산출물 위치 (docs/governance/) 명시 → 검증: grep "/governance" backend/CLAUDE.md 매칭 1건 이상

### Phase 6: dogfood 검증

- [x] `backend/docs/governance/20260506/REPORT.md` 신설 — governance.md 절차 첫 호출 결과 작성 (정량 4 + 정합 12 + 발견사항 표 + 거짓 양성 구분 사례) → 검증: test -f backend/docs/governance/20260506/REPORT.md 결과 exit 0
- [x] dogfood REPORT.md 의 발견사항 표에 ANALYZE1 즉시 발견사항 4건 정확 재현 — process/README.md "3개 파일" 불일치 / feature-dev:code-reviewer 인용 / DOM_AMT_15_2 미사용 / db-patterns.md docs 이력 N건 → 검증: grep -E "process/README.md\|feature-dev:code-reviewer\|DOM_AMT_15_2\|db-patterns.md" backend/docs/governance/20260506/REPORT.md 매칭 4건 이상
- [x] dogfood 응답 시간 60초 이내 측정 — Phase 6 grep 명령 16~20건 실행 시간을 time 명령으로 측정 후 결과 REPORT 부속에 기록 → 검증: REPORT.md 부속에 time real 값 60초 이내 기록 1건 이상
- [x] dogfood 거짓 양성 구분 사례 1건 이상 정확 분류 — entity-patterns.md 의 CREATE INDEX CONCURRENTLY 가 "예시 코드 = 인용" 으로 분류되는지 REPORT 에 기록 → 검증: grep -E "entity-patterns.md.*인용\|CREATE INDEX CONCURRENTLY.*인용" backend/docs/governance/20260506/REPORT.md 매칭 1건 이상

### Phase 7: 빌드 검증

- [x] `./gradlew.bat build` 실행 성공 확인 — 룰/명령 자산만 변경하므로 빌드 영향 없음 사후 검증

## 산출물
- [결과](../../../results/20260506/governance_health_check/RESULT1.md)
