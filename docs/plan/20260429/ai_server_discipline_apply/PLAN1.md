---
status: approved
created: 2026-04-29
updated: 2026-04-29
---
# ai-server LLM 코딩 디시플린 적용 — 계획

## 목적

ROOT `swtp/.claude/rules/coding-discipline.md` 의 ai-server 적용을 완료하여 모노레포 LLM 코딩 행동 규율 SSOT 가 backend·ai-server 양쪽에 일관 적용되도록 한다. 직전 작업(`llm_coding_discipline`) REVIEW1 §개선 제안 1·3 를 후속 처리한다.

## 배경

- [ANALYZE1](../../../analyze/20260429/ai_server_discipline_apply/ANALYZE1.md) 4인 팀 회의 검증 결과:
  - 안건 1 (B): ai-server `wtp-domain-expert` 카피에 항목 6번 의미적 변형 추가 — 추론 schema 가정 + 책임 분리 점검
  - 안건 2 (A): ai-server 자체 룰 파일 `swtp/ai-server/.claude/rules/coding-discipline.md` 신규 작성 — Python 60줄 상향 조정 + §2.5 면책 영역 2건 (추론 단일 흐름·이상 탐지 파이프라인)
  - 안건 3: ROOT `coding-discipline.md §5` 표 ai-server 행 갱신 (위임 텍스트)
  - 안건 4 (A): ai-server CLAUDE.md §외부 참조 룰 + §내부 룰 양쪽 1줄씩 추가
- 표준 사전 4층 신규 등록 0건 (룰/문서 변경)
- 블로커 0건, 권고 1건 (글로사리 vs 엔지니어 표면 충돌은 오케스트레이터 종합으로 해소), 참고 2건
- 사전 plan: `~\.claude\plans\generic-forging-acorn.md` (REVIEW1 4건 개선 제안 분류 — 본 작업이 Group A)

## 범위

### 포함

| 분류 | 파일 | 변경 유형 |
|------|------|---------|
| ai-server 신규 룰 | `swtp/ai-server/.claude/rules/coding-discipline.md` | 신규 작성 (ROOT 인용 + Python 60줄 + 면책 영역 2건 + 적용 시기) |
| ai-server 에이전트 | `swtp/ai-server/.claude/agents/wtp-domain-expert.md` | §검토 항목 끝에 6번 추가 |
| ai-server 인덱스 | `swtp/ai-server/CLAUDE.md` | §외부 참조 룰 1줄 + §내부 룰 1줄 추가 |
| ROOT 룰 | `swtp/.claude/rules/coding-discipline.md` | §5 표 ai-server 행 갱신 + §폐기·갱신 이력 1행 추가 |

### 제외

- **ai-server `coding-discipline.md` 의 실제 코드 위반 점검** — Phase 3 (신규 추론 함수 작성 시점) 적용. Phase 2 (501 스텁) 시점은 적용 대상 코드 없음
- **`_legacy/` 코드 소급 적용** — 적용 범위 외 (이식 대상이 아닌 레거시)
- **ai-server 자체 자동 차단 훅 신설** — 별도 ANALYZE (운영 사례 누적 후)
- **ai-server `wtp-ai-engineer` 검토 항목 변경** — 본 작업 범위 외 (도메인 검토는 `wtp-domain-expert` 책임)
- **ai-server 자체 `/dev:analyze` 워크플로우 변경** — 본 작업은 ROOT/ai-server 룰 인용 체계만 변경

## 구현 방향

### Phase 구성 (예상 4개, 단일 TASK1 으로 운영)

| Phase | 작업 | 산출 파일 |
|------|------|---------|
| 1 | ai-server 신규 룰 파일 작성 (가장 긴 작업) | `swtp/ai-server/.claude/rules/coding-discipline.md` |
| 2 | ai-server 에이전트 검토 항목 6번 추가 | `swtp/ai-server/.claude/agents/wtp-domain-expert.md` |
| 3 | ai-server CLAUDE.md 인용 추가 (외부 참조 + 내부 룰 양쪽) | `swtp/ai-server/CLAUDE.md` |
| 4 | ROOT `coding-discipline.md` §5 표 + §폐기·갱신 이력 갱신 + backend `./gradlew.bat build` 무영향 확인 | `swtp/.claude/rules/coding-discipline.md` + 빌드 검증 |

체크박스 분할 기준 (Phase 10 / 체크박스 60) 모두 미달 (Phase 4 · 체크박스 약 7~8개) → **단일 TASK1 로 진행 예정**.

### 핵심 설계 결정

1. **ai-server 자체 룰 파일 신설 + ROOT §5 위임 패턴** (안건 2·3 종합): ROOT 룰 본문은 불변 유지, Python 고유 차이 (60줄 상향·면책 영역 2건) 는 ai-server 자체 룰에 집중. ROOT §5 표는 "ai-server 자체 룰로 위임" 한 줄로 갱신. 단일 출처 원칙(ai-server CLAUDE.md §외부 참조 룰) 과의 충돌은 인용 텍스트에 "위임" 명시로 흡수

2. **§2.5 면책 영역 ai-server 정의** (안건 2): backend 의 면책 (정수장 안전·DB 쿼리) 은 ai-server 범위 외이므로 ai-server 자체 면책 영역으로 대체:
   - **모델 추론 단일 흐름**: 전처리(NaN·shape·scaler) → `predict()` → 역변환 → 결과 검증 (RELU·음수 클리핑). 분해 시 역변환 전 단위 검증 누락 위험
   - **이상 탐지 특징 추출 파이프라인**: FFT → SVM 점수 → rule 가중치 합산 → 알람 임계값 비교. 분해 시 중간 상태 전파 오류
   - 인용 근거 명기 의무 (주석 `# §2.5 면책 (추론 단일 흐름)`) 는 backend 와 동일

3. **ai-server `wtp-domain-expert` 카피의 책임 분리** (안건 1): backend 카피의 ANALYZE/PLAN 가정 점검 = 문서 프로세스 수준. ai-server 카피의 항목 6번은 그것을 의미적 변형하여 "추론 schema 설계 가정 + 책임 분리 원칙(`ot-integration.md §6.6`) 점검" 으로 작성. 단순 이식 X. 기존 5개 항목과 카테고리 일관성 유지 (모두 추론 schema·전처리·후처리 단위)

4. **Phase 3 적용 시기 명시** (안건 2): Phase 2 (501 스텁) 적용 X, `_legacy/` 적용 범위 외, **Phase 3 신규 추론 함수 작성 시점부터 적용**. ai-server 룰 §3 절에 명시

5. **dogfooding** — 본 PLAN 자체에 ROOT `coding-discipline.md` 의 가정 섹션·검증 형식·계획 외 변경 패턴 적용 (아래 §성공 기준 §4 참조)

## 도메인 모델

신규 엔티티·DTO 없음. 본 작업은 룰/에이전트/CLAUDE.md 변경이므로 도메인 모델 변경 0건. (ANALYZE1 §신규 엔티티/DB 컬럼 = 없음 확정)

## DB 설계 변경

**없음** — ai-server 룰 파일 + 에이전트 + CLAUDE.md 전용 작업. DDL·인덱스·파티션·시계열 보존 정책 변경 0건.

`check-ddl-column-comment.sh` 훅 트리거 대상 (`db/init/`·`db/migration/`) 변경 없음 → 자동 차단 미발동. RESULT 에서 동일 문구로 명시.

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md §4.2`](../../../../../.claude/rules/coding-discipline.md) 적용. 각 기준에 검증 명령·테스트·조회 명시.

1. **ai-server 자체 룰 파일 정합성** — 검증: `swtp/ai-server/.claude/rules/coding-discipline.md` 존재 + grep "60줄" 매칭 1건 이상 + grep "추론 단일 흐름" 매칭 1건 이상 + grep "이상 탐지" 매칭 1건 이상 + ROOT 룰 인용 (`swtp/.claude/rules/coding-discipline.md`) 1건 이상

2. **ai-server `wtp-domain-expert` 항목 6번 추가** — 검증:
   - grep "ANALYZE/PLAN 가정 섹션" `swtp/ai-server/.claude/agents/wtp-domain-expert.md` 매칭
   - grep "책임 분리 원칙" `swtp/ai-server/.claude/agents/wtp-domain-expert.md` 매칭
   - 기존 5개 검토 항목 (1.알람·2.인터록·3.운전 모드·4.물리적 타당성·5.결측 대체값) 텍스트 그대로 보존 확인 (변경 없음)

3. **ai-server CLAUDE.md 인용 추가** — 검증:
   - grep "coding-discipline" `swtp/ai-server/CLAUDE.md` 매칭 2건 이상 (외부 참조 1건 + 내부 룰 1건)
   - 외부 참조 텍스트에 "Python 60줄 조정" 또는 "ai-server 자체 룰" 매칭 1건 이상

4. **ROOT `coding-discipline.md` §5 표 갱신** — 검증:
   - grep "ai-server 자체 룰" `swtp/.claude/rules/coding-discipline.md` 매칭 1건 이상
   - §폐기·갱신 이력 표에 2026-04-29 행 2개 (직전 작업 1행 + 본 작업 1행) 확인
   - "(현재 본 룰 미적용 — 별도 ANALYZE 후 도입 검토)" 텍스트 grep 결과 0건 (제거 확인)

5. **빌드 무영향** — 검증: `./gradlew.bat build` PASS, 기존 16개 backend 테스트 모두 통과. ai-server 영역은 Java 빌드 대상 외이므로 영향 받지 않음을 확인

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md §1`](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE1 의 가정 4건을 PLAN 단계 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| ai-server `wtp-domain-expert` 항목 6번 의미적 변형 텍스트의 효과 — Phase 3 첫 시연까지 미검증 | 가정 | 본 작업은 룰 텍스트 작성까지만 책임. 효과 검증은 Phase 3 첫 추론 함수 이식 작업의 ai-server `/dev:analyze` 첫 사이클 책임 |
| Python 60줄 상향 조정 적정성 — _legacy `Predict()` 80줄 사례 기반, 신규 추론 함수가 동일 길이 패턴인지 미검증 | 가정 | 본 작업은 60줄 임계로 적용. 운영 1~2 사이클 후 재조정 필요 시 ai-server 자체 ANALYZE 안건으로 분리 |
| §2.5 면책 영역 후보 2건 (추론 단일 흐름·이상 탐지 파이프라인) 의 실제 적용 사례 — Phase 2 시점 시연 불가 | 가정 | Phase 3 이식 시점에 첫 인용 사례 발생. 본 작업은 정의만 명문화 |
| backend ANALYZE 가 ai-server 룰 변경을 결정하는 거버넌스 정합성 — ROOT §5.1 의무 vs ai-server 자체 결정 | 결정 | 본 작업 한정으로 backend ANALYZE (ROOT 결정의 종속). 향후 ai-server 자체 룰 변경은 ai-server `/dev:analyze` 가 1차 책임. 본 결정을 ai-server `coding-discipline.md` 머리말에 "본 룰 신설은 backend ANALYZE (`docs/analyze/20260429/ai_server_discipline_apply/ANALYZE1.md`) 결정" 으로 명시하여 추적성 확보 |
| TASK 분할 여부 | 결정 | Phase 4·체크박스 약 7~8개 예상 → **단일 TASK1 로 진행** (분할 임계 Phase 10 / 체크박스 60 모두 미달) |

## 제외 사항

- **ai-server 코드 변경 0건** — Phase 2 (501 스텁) 단계라 적용 대상 코드 없음. Phase 3 이식 시점부터 룰 적용
- **자동 차단 훅 신설** — 별도 ANALYZE (운영 사례 누적 후)
- **`_legacy/` 소급 적용** — 적용 범위 외
- **`wtp-ai-engineer` / `wtp-ml-reviewer` 변경** — 본 작업 범위 외
- **backend 룰 추가 변경** — 직전 작업(`llm_coding_discipline`) 으로 완료. 본 작업은 ai-server 영역만 변경

## 예상 산출물

- [태스크](../../../tasks/20260429/ai_server_discipline_apply/TASK1.md)

---

## 부록: 도메인/DB 검토 결과

신규 엔티티·DB 변경 0건 → 검토 게이트 생략 (PLAN 단계 §도메인·DB 검토 게이트 의 "도메인 모델과 DB 변경이 모두 없는 경우 생략" 규칙). ANALYZE1 의 결론 "DB 영향 없음 + 면책 범위 ai-server 식 재정의" 를 본 PLAN §구현 방향 2 에 흡수.
