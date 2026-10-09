---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 거버넌스 진단 슬래시 명령 (`/governance`) 신설 — 계획안

## 목적

사용자 주기 호출 슬래시 명령 `/governance` 를 신설하여 ROOT/backend 분산 SSOT 모델의 (1) 정량 진단 + (2) 정합 진단을 자동화한다. **자동 수정 없음** — 발견사항 보고만, 실제 수정은 사용자가 별도 `/dev` 사이클로 결정한다.

## 배경

ROOT/backend 거버넌스 강화 plan (`~/.claude/plans/quiet-hugging-bachman.md`) §Cycle 2 산출물 작성 사이클. Cycle 1 (`analyze_section_enforcement`, 커밋 `9208f4a`) 종료 후 진행. ANALYZE1.md (status: approved) 의 정량 4항목 + 정합 12항목 + 즉시 발견사항 4건 (낮음 3 + 중간 1) 을 본 PLAN 으로 IMPL 변환.

본 환경은 `feature-dev` 플러그인 미설치 (메모리 `feedback_no_feature_dev_plugin.md` 등록). `/governance` 의 R9 진단 항목이 본 환경 정책 위배 인용을 자동 식별한다.

## 범위

### 포함
- `backend/.claude/commands/governance.md` 절차서 신규 작성 (정량 4 + 정합 12 + 거짓 양성 구분 정책 + 산출물 위치)
- `backend/CLAUDE.md` `§규칙 문서 인덱스` 갱신 (`/governance` 안내 행 추가)
- `backend/.claude/rules/process/doc-harness/README.md` §디렉토리 구조 갱신 (5종 외 6번째 카테고리 추가)
- `docs/governance/{YYYYMMDD}/REPORT.md` 출력 디렉토리 (Phase 6 dogfood 시 자동 생성)

### 제외 (별도 사이클로 위임)
1. `process/README.md` "구 root 3개 파일" 표기 불일치 수정 — 본 사이클 REPORT 출력 후 사용자 결정
2. `dev/plan.md:94`·`dev/review.md:20` `feature-dev:code-reviewer` 인용 갱신 — 본 사이클 R9 진단으로 자동 식별, 갱신은 별도 사이클
3. `DOM_AMT_15_2` 활용처 도입 — R12 진단으로 식별, 도입 결정은 별도 사이클
4. 룰 파일 분리 (300줄+ 후보 3건 — `templates.md`·`test-strategy.md`·`entity-patterns.md`) — Q3 진단 후 사용자 결정
5. ROOT 룰 변경 (`coding-discipline.md` 본문 변경 0건)
6. ROOT 어휘 사전 갱신 (신규 어휘 0건)
7. Java 소스·DB·테스트 변경 (룰/명령 자산만)

## 도메인 모델

**변경 없음** — 본 사이클은 룰/명령 자산만 변경. 신규 엔티티·DTO·컬럼 0건.

## DB 설계 변경

**변경 없음** — 스키마 변경 0건.

## 구현 방향

### Phase 1: `governance.md` 절차서 - 헤더 + 정량 진단 (Q1~Q4)
- 슬래시 명령 진입점 절차 + 사용자 주기 호출 안내 + 자동 실행 금지 명문화
- Q1 CLAUDE.md 줄 수 측정 (ROOT + backend) — `wc -l` + 매핑 표 전용 섹션 vs 직접 정책 기술 섹션 분리
- Q2 룰 파일 줄 수 분포 **5구간** (`<30/30~50/50~200/200~500/500+`)
- Q3 300줄+ 식별 + "단일 관심사 vs 복합 관심사" 구분 컬럼
- Q4 redirect stub 2건 (`db-patterns.md`·`doc-harness.md`) 외부 참조 grep — `.claude/rules/` 스코프 vs `docs/` 스코프 분리

### Phase 2: `governance.md` 절차서 - 정합 진단 (R1~R12)
- R1 4영역 SSOT 정책 본문 고유 문구 grep (`SCADA_TIMEOUT`·`OUTBOUND_FAIL`·`MANUAL_EXPIRE`·`SYSTEM_INIT`·`hold last value`·`last_rcv_dtm`)
- R2 Cycle 1 산출물 정합 — `templates.md` ANALYZE 3섹션 ↔ `dev/plan.md` §5b 1:1 grep
- R3 REVIEW1 #1 자동 식별 — `grep -n "\.\./\.\./ot-integration" templates.md`
- R4 REVIEW1 #3 자동 식별 — `grep -n "규모 판단\|Large" impl.md commit.md` 비교
- R5 §2.5 면책 인용 근거 ↔ 실사용 매칭 — `grep -rn "§2\.5 면책" backend/`
- R6 `coding-discipline.md` 4원칙 본문 복제 grep (`Think Before Coding`·`Simplicity First`·`Surgical Changes`·`Goal-Driven Execution` 외부 등장)
- R7 ROOT 3층 ↔ backend 1층 SSOT 분리 — DOM_* 정의 행 검출 + 폐기 어휘 코드 잔존 grep (`pmp_`·`reg_id`·`tag_id`·`tag_val`·`cntom`)
- R8 `dict/README.md` 2개 분리 인덱스 정합 (`VARCHAR/BIGINT/NUMERIC/LocalDateTime` 정책 본문 키워드 미존재)
- R9 `feature-dev:code-reviewer` 인용 grep — `grep -rn "feature-dev:code-reviewer" backend/.claude/commands/` 매칭 시 중간 심각도
- R10 신규 어휘 0건 검증 — ANALYZE 의 사전 카탈로그 3표 "없음" 표기 정합
- R11 DB 룰 정책 본문 복제 grep (`PARTITION BY/BRIN/CONCURRENTLY/executionThreshold/EXPLAIN ANALYZE`) — 자식 3 외 등장 + 정책 지침 vs 예시 코드 구분
- R12 redirect stub 이중 표 (현행 룰 참조 vs docs 이력 참조) + DOM_* 미사용 도메인 식별

### Phase 3: 거짓 양성 구분 정책 명문화
- "레이블 + 파일 링크만 = 인용" / "임계값·값·시퀀스 본문이 복사 = 복제"
- "정책 지침 문맥 = 복제 후보" / "예시 코드·테스트 도입 트리거 언급 = 인용"
- REPORT 발견사항을 **"자동 확정 / 사람 검토 필요"** 두 카테고리로 분리 출력

### Phase 4: 보고서 산출물 위치 + doc-harness 갱신
- `docs/governance/{YYYYMMDD}/REPORT.md` 디렉토리 신설 (Phase 6 dogfood 시 첫 생성)
- `doc-harness/README.md` §디렉토리 구조 갱신 — 5종 산출물 외 6번째 카테고리 추가
- 사이클 외 진단 산출물 라이프사이클 명시 (재호출 시 누적, status 흐름 단순화 — `draft → completed` 또는 status 미사용)

### Phase 5: backend `CLAUDE.md` 인덱스 갱신
- `§규칙 문서 인덱스` 표 또는 별도 섹션에 `/governance` 슬래시 명령 안내 행 추가
- 호출 빈도·점검 항목 요약·산출물 위치 명시

### Phase 6: dogfood 검증
- `/governance` 첫 호출 → 본 ANALYZE1 의 즉시 발견사항 4건 정확 재현 검증
- `time` 명령으로 grep 실행 시간 측정 → 60초 이내 확인
- 거짓 양성 구분 정책의 실제 적용 사례 1건 이상 확인 (예: `entity-patterns.md` `CREATE INDEX CONCURRENTLY` = 인용으로 분류)

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 적용. "성능 개선" 같은 모호 목표 금지. 각 기준에 검증 명령·테스트·조회 명시.

| # | 기준 | 검증 명령 |
|---|------|---------|
| 1 | `governance.md` 절차서에 정량 4 + 정합 12 = 16 항목 모두 포함 | `grep -cE "Q1\|Q2\|Q3\|Q4\|R1\|R2\|R3\|R4\|R5\|R6\|R7\|R8\|R9\|R10\|R11\|R12" backend/.claude/commands/governance.md` 매칭 16 이상 |
| 2 | 거짓 양성 구분 정책 명문화 (3 키워드 모두 등장) | `grep -E "레이블\|정책 지침\|예시 코드" backend/.claude/commands/governance.md` 매칭 3건 이상 |
| 3 | 보고서 산출물 위치 디렉토리 신설 + doc-harness 갱신 | `test -d backend/docs/governance` exit 0 + `grep "docs/governance" backend/.claude/rules/process/doc-harness/README.md` 매칭 1건 이상 |
| 4 | backend `CLAUDE.md` 인덱스에 `/governance` 안내 추가 | `grep "/governance" backend/CLAUDE.md` 매칭 1건 이상 |
| 5 | dogfood — `/governance` 호출 시 ANALYZE1 즉시 발견사항 4건 정확 재현 | REPORT.md 의 발견사항 표 4행 매칭 — `process/README.md` "3개 파일" 불일치 / `feature-dev:code-reviewer` 인용 / `DOM_AMT_15_2` 미사용 / `db-patterns.md` docs 이력 N건 |
| 6 | 응답 시간 60초 이내 | `time` 명령으로 Phase 6 dogfood 실행 시간 측정, 60초 이내 |
| 7 | 자동 수정 없음 정책 정합 — `governance.md` 본문에 "자동 수정 없음" 또는 "보고만" 명시 1건 이상 | `grep "자동 수정 없음\|보고만\|발견사항 보고" backend/.claude/commands/governance.md` 매칭 1건 이상 |

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 의 가정·미해결 질문을 PLAN 단계 결정으로 변환. 최소 1건 이상 기재 의무.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `/governance` 응답 시간 60초 이내 | 가정 → 결정 | grep 명령 16~20건 추정, 60초 내 충분 — Phase 6 dogfood 에서 `time` 측정 필수 |
| `docs/governance/{YYYYMMDD}/REPORT.md` 디렉토리 신설 (5종 외 6번째 카테고리) | 결정 | doc-harness/README.md §디렉토리 구조 갱신 의무 (Phase 4). status 흐름 단순화 — `draft → completed` 또는 status frontmatter 미사용 (사이클 외 진단 산출물). PLAN 결정: **status frontmatter 미사용** (REPORT 는 진단 결과 보고이지 사이클 산출물이 아니므로 doc-harness 의 5종 라이프사이클 외 — 단순 마크다운 보고서) |
| 거짓 양성 구분 자동화 한계 | 가정 → 결정 | "자동 확정 / 사람 검토 필요" 두 카테고리로 분리 출력 (Phase 3 명문화). 사람 검토 행은 발견사항 표에 ⚠️ 마커로 표시 |
| `process/README.md` 표기 불일치 수정 | 결정 | 본 사이클 범위 외. REPORT 출력 후 별도 사이클 (Q4 진단 결과로 자동 보고) |
| `feature-dev:code-reviewer` 인용 갱신 | 결정 | 본 사이클 범위 외. R9 진단으로 자동 식별, 메모리 정책 (`feedback_no_feature_dev_plugin.md`) 우선 적용 |
| `DOM_*` 미사용 도메인 전수 grep | 미해결 → 결정 | Phase 6 dogfood 에서 `DOM_*` 코드 순회 grep 실행 → 결과 REPORT 부속 표로 출력. ANALYZE1 가정 섹션의 `DOM_AMT_15_2` 외 추가 미사용 도메인 발견 가능성은 dogfood 결과로 확정 |
| 정량 진단 (Q1) CLAUDE.md "매핑 표 vs 직접 정책 기술" 분리 측정 알고리즘 | 미해결 → 결정 | 섹션 헤더 (`## §`) 단위로 본문 라인 패턴 분류 — "표 형식 (`\| ... \|`) 비율 80% 이상" → 매핑 표 전용 / 미달 → 직접 정책 기술. 임계값 80% 는 Phase 6 dogfood 에서 조정 |

분류값: 가정 / 미해결 → 결정

## 제외 사항

1. **자동 수정 없음** — `/governance` 는 발견사항 보고만, 자동 수정 0건 (plan §Cycle 2 정의 정합)
2. **ROOT 룰 변경 0건** — `coding-discipline.md` 본문 변경 0건 (plan §본 plan 의 범위 외 §2 정합)
3. **ROOT 어휘 사전 갱신 0건** — 신규 어휘 0건 (ANALYZE1 사전 카탈로그 3표 "없음")
4. **Java 소스·DB·테스트 변경 0건** — 룰/명령 자산만
5. `process/README.md` "구 root 3개 파일" 표기 불일치 수정 (별도 사이클)
6. `feature-dev:code-reviewer` 인용 갱신 (별도 사이클)
7. 룰 파일 분리 (300줄+ 후보 3건, 별도 사이클)
8. `DOM_AMT_15_2` 활용처 도입 (별도 사이클)
9. 자동 차단 훅 신설 0건 — `coding-discipline.md §7.1` 보류 결정 유지 (Cycle 1 과 동일)

## 예상 산출물
- [태스크](../../../tasks/20260506/governance_health_check/TASK1.md) (다음 단계)
