---
status: approved
created: 2026-04-23
updated: 2026-04-23
---
# 하네스에 분석(analyze) 단계 도입 — 도메인 사전 점진적 확립

## 목적

`/dev` 워크플로우에 **분석(analyze) 단계** 를 정식 도입한다.
Medium/Large 작업의 PLAN 단계 직전에서 신규 등장 용어·엔티티·DB 컬럼이 기존 도메인 사전·패턴과
**겹치는지 / 상충되는지 / 신규인지** 를 판정하고, 결정 사항을 룰 파일에 점진적으로 반영해
"모든 엔티티·용어·DTO·DB 설계가 단일한 룰 안에서 정합성을 갖는 상태" 를 누적해 간다.

## 배경

### 왜 지금 도입하는가
- 핵심 도메인 패키지(`pump`, `scada`, `alarm` ...)가 아직 거의 비어있는 상태(`auth`, `common`, `user` 만 존재).
  지금이 사전·패턴 확립의 **골든 타임** — 코드가 쌓인 뒤 사전을 도입하면 retrofit 비용이 폭증한다.
- `docs/analyze/20260422/pumpcontrol/` 디렉토리가 이미 비공식적으로 운용 중. 정식화만 안 된 상태.
- 현재 도메인 용어가 3 파일에 분산: `naming.md`(DB suffix), `ot-integration.md`(센서 코드 FRI/PRI/LEI/PWI/RMS),
  `multi-tenant.md`(지자체 코드). **도메인명 약어**(`pump`, `raw`, `ctrl` 등)는 **어디에도 정의되지 않음**.

### 결정적 제약 — 같은 함정 반복 회피
2026-04-23 오전 커밋 `0247cb8` 에서 `.claude/rules/domain-glossary.md` 와 `legacy-mapping.md` 가 제거됐다.
"정적 단일 사전" 접근은 이미 한 번 실패했음 (코드와 분리된 정적 파일은 drift). 이번 설계의 1번 제약.

### 사용자 결정 사항 (확정)
| 항목 | 결정 |
|------|------|
| 사전 보관 형태 | **하이브리드** — 도메인명 약어만 신규 단일 파일, 나머지는 기존 룰 파일 갱신 |
| 적용 범위 | **Medium/Large 만** — Small 은 면제, 단 신규 용어 후보 감지 시 자동 격상 제안 |
| 갱신 강제력 | **PLAN 전제조건 + REVIEW 점검** — pre-commit 훅까지는 도입하지 않음 |
| Fix Cycle 처리 | **조건부 재진입** — 기본 스킵, 직전 REVIEW 블로커가 도메인 정합성 항목이면 ANALYZE{N+1} 자동 작성 |

### 사용자 결정 사항 — 2차 (5인 팀 회의 메커니즘)
| 항목 | 결정 |
|------|------|
| 팀 구현 방식 | **서브에이전트 5개** — 4개 도메인 에이전트(Agent 도구 호출) + 메인 Claude(오케스트레이터). 독립 컨텍스트로 진짜 의견 충돌 가능 |
| 회의 진행 방식 | **토픽 주도** — 오케스트레이터가 안건 제시 → 해당 판단 전문가만 답변 → 이견 시 2라운드. 라운드 로빈/비동기 게시판 대비 컨텍스트·토큰 절약 |
| 외부 산출물 처리 | **하이브리드** — `.png` 는 Read 도구로 자동 시각 로드, `.docx` 는 사용자가 핵심 내용을 텍스트로 paste 또는 사전 .md 변환. 워크플로우에 사용자 개입 지점 명시 |

## 범위

### 포함
- 신규 슬래시 커맨드 `/dev:analyze {슬러그}` 정의
- `dev.md` 진입점, `dev/plan.md`, `dev/review.md` 의 워크플로우 변경
- `docs/analyze/` 산출물 디렉토리 구조 정식화 + ANALYZE{N}.md 템플릿 정의
- `.claude/rules/domain-abbreviations.md` 신규 작성 (도메인명 약어 단일 진실 소스, 1차 시드)
- `doc-harness.md` 와 `CLAUDE.md` 의 인덱스/안내 갱신

### 제외 (별도 작업으로 분리)
- 기존 룰 파일 본문의 대규모 리팩터링 (예: `ot-integration.md` 의 센서 코드 표를 `domain-abbreviations.md` 로 이동)
- pre-commit 훅 추가 (사용자 결정대로 이번엔 미도입)
- 도메인 약어의 코드 강제력 (lint, ArchUnit 등)
- `wtp-domain-expert` 에이전트 정의 변경 (다음 사이클로 분리)

## 구현 방향

### 1. 신규/변경 파일 목록

| 파일 | 액션 | 목적 |
|------|------|------|
| `.claude/commands/dev/analyze.md` | **신규** | `/dev:analyze {슬러그}` 커맨드 정의 — 5인 팀 회의 오케스트레이션 포함 |
| `.claude/commands/dev.md` | 수정 | Medium/Large 흐름 안내 + 자동 전이 표에 `analyze` 추가 |
| `.claude/commands/dev/plan.md` | 수정 | 전제조건에 "ANALYZE 룰 갱신 지시서 모두 체크 완료" 추가 |
| `.claude/commands/dev/review.md` | 수정 | 자동 점검 항목에 "ANALYZE 의 룰 갱신 지시서 ↔ 실제 룰 파일 변경 대조" 추가 |
| `.claude/rules/doc-harness.md` | 수정 | `docs/analyze/` 디렉토리 트리, ANALYZE{N}.md 템플릿(회의록 섹션 포함), Fix Cycle 분기 규칙 추가 |
| `.claude/rules/domain-abbreviations.md` | **신규** | 도메인명 약어 단일 진실 소스 (1차 시드) |
| `.claude/agents/wtp-backend-engineer.md` | **신규** | Spring Boot/JPA/Querydsl 패턴·SOLID·계층 책임 검토 페르소나 |
| `.claude/agents/wtp-glossary-manager.md` | **신규** | 신규 용어/약어 ↔ 기존 사전 대조, 충돌 판정, 룰 갱신 지시서 작성 페르소나 |
| `CLAUDE.md` | 수정 | 규칙 문서 인덱스에 한 줄 추가, 작업 흐름 문장에 analyze 단계 언급 |

> 기존 `wtp-domain-expert.md`, `wtp-dba-reviewer.md` 는 본 작업에서 정의 변경 없이 그대로 호출만 한다. (PLAN §제외 사항 참조)

### 2. 워크플로우 변경 (자동 전이 표)

```
Small  : /dev → /dev:impl → /dev:commit                                                      (변경 없음)
Medium : /dev → /dev:analyze → /dev:plan → /dev:task → /dev:impl → /dev:commit
Large  : /dev → /dev:analyze → /dev:plan → /dev:task → /dev:impl → /dev:result → /dev:review → /dev:commit
```

자동 전이 표에 추가될 행:
| 현재 단계 | 승인 필요 | 완료 후 자동 전이 |
|----------|----------|----------------|
| `/dev:analyze` | 사용자 승인 (status: approved) | 승인 후 → `/dev:plan` 자동 실행 |

`dev.md` 6단계(자동 전이 첫 단계)도 Medium/Large 의 경우 `/dev:plan` 대신 `/dev:analyze` 로 변경.

### 3. `/dev:analyze {슬러그}` 동작 규약 — 5인 팀 회의 오케스트레이션

#### 3.1 팀 구성

| 역할 | 구현 | 책임 |
|------|------|------|
| **오케스트레이터** | 메인 Claude (커맨드 본문) | 회의 진행, 안건 제시, 결론 종합, ANALYZE.md 작성 |
| **DBA** | `wtp-dba-reviewer` (기존 에이전트) | DB 스키마·인덱스·파티션·무중단 마이그레이션 검토 |
| **Backend 개발자** | `wtp-backend-engineer` (신규 에이전트) | Spring Boot/JPA/Querydsl 패턴, 계층 책임, SOLID 검토 |
| **정수장 도메인 전문가** | `wtp-domain-expert` (기존 에이전트) | 운전 모드/알람 4단계/인터록 등 도메인 비즈니스 규칙 검토 |
| **용어/도메인 관리자** | `wtp-glossary-manager` (신규 에이전트) | 신규 용어 ↔ 기존 사전 대조, 충돌 판정, 룰 갱신 지시서 작성 |

#### 3.2 동작 절차

1. **전제조건 검증**
   - `$ARGUMENTS` 비면 중단
   - Fix Cycle 감지 (PLAN1 §결정 4 적용): 직전 REVIEW 블로커에 도메인 정합성 키워드(`용어`, `약어`,
     `중복 정의`, `네이밍 충돌`, `엔티티 통합`) 포함 시 → ANALYZE{N+1} 작성, 아니면 즉시 PLAN 전이
   - 기존 ANALYZE 가 `approved` 면 "이미 승인됨, `/dev:plan` 진행" 안내

2. **외부 산출물 수집**
   - `docs/analyze/{YYYYMMDD}/{슬러그}/` 디렉토리 스캔
   - `.png`/`.jpg` → Read 도구로 자동 시각 로드 (Claude 가 직접 해석)
   - `.docx`/`.xlsx`/`.pdf` → **사용자 개입 지점**: "이 파일의 핵심 내용을 paste 또는 .md 로 변환해
     같은 디렉토리에 저장해 주세요" 안내 후 대기. 사전 변환된 .md 가 있으면 자동 진행
   - 그 외 (.md, .txt) → Read 자동 로드

3. **사전 분석 — 메인 Claude 단독 수행**
   - 사용자 요청문 + 외부 산출물에서 명사구·약어 후보 추출
   - 기존 사전 1차 대조: `.claude/rules/domain-abbreviations.md`, `naming.md`, `ot-integration.md`,
     `multi-tenant.md`, `common/src/main/java/com/mo/swtp/` 패키지 트리, 기존 엔티티 클래스명 grep
   - 안건 후보 목록 생성 (예: "용어 X 가 기존 약어 Y 와 충돌 가능", "신규 테이블 Z 의 파티션 전략 필요")

4. **회의 진행 — 토픽 주도 방식**

   각 안건마다 다음 라운드 실행:

   ```
   [Round 1] 오케스트레이터 → 안건 제시 → 판단 권한자(1~2명) Agent 호출 → 답변 수집
   [Round 2] (이견·블로커 발견 시만) → 관련 전문가 추가 호출 → 종합
   ```

   **에이전트별 호출 트리거 (안건 키워드 매핑):**

   | 안건 키워드 | 호출 에이전트 |
   |------------|--------------|
   | 신규 용어/약어/네이밍 충돌 | `wtp-glossary-manager` (필수) + `wtp-domain-expert` (선택) |
   | DB 테이블/컬럼/인덱스/파티션 | `wtp-dba-reviewer` (필수) |
   | 엔티티 패턴/Service 구조/계층 책임 | `wtp-backend-engineer` (필수) |
   | 알람 4단계/인터록/운전 모드/SCADA | `wtp-domain-expert` (필수) |
   | 외부 산출물(요구사항·다이어그램) 정합성 | 4개 에이전트 모두 1라운드 호출 |

   - 모든 에이전트는 컨텍스트 절약을 위해 **단답형(200~400단어)** 으로 답변하도록 프롬프트 강제
   - 에이전트 간 직접 대화 없음 — 항상 오케스트레이터가 중계

5. **결론 종합 + ANALYZE{N}.md 작성**
   - 회의록 섹션에 안건별 라운드/답변 요약
   - 신규 용어 카탈로그 표 채우기 (분류: 신규/기존재사용/유사충돌/폐기·통합)
   - 룰 갱신 지시서 작성 (체크박스 형태, 룰 파일 전체 경로 명시)
   - PLAN 으로 전달할 결정 사항 정리

6. **사용자 승인** → `status: approved` 로 상태 변경 후 `/dev:plan` 자동 실행

### 4. `/dev:plan` 전제조건 추가

```
ANALYZE 검증 (Medium/Large):
- docs/analyze/{YYYYMMDD}/{슬러그}/ANALYZE{N}.md 존재 확인
- status: approved 여야 함
- "## 룰 갱신 지시서" 섹션의 모든 체크박스가 - [x] 로 완료
- 미완료 시 "ANALYZE 의 룰 갱신을 먼저 완료하세요" 안내 후 중단
```

### 5. ANALYZE{N}.md 템플릿 (doc-harness.md 에 추가)

```markdown
---
status: draft
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# {제목} — 도메인 분석

## 작업 배경
- 요청 요약
- 외부 산출물: `요구사항.docx`, `도메인모델링.png`, `클래스다이어그램.png` 등

## 회의록 (5인 팀 토픽 주도)

### 안건 1: {제목}
- 호출 에이전트: `wtp-glossary-manager`, `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: ...
  - **wtp-domain-expert**: ...
- Round 2 (이견 시): ...
- **결론**: ...

### 안건 2: {제목}
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약: ...
- **결론**: ...

(안건 N 까지 반복)

## 신규 용어 카탈로그
| 용어 | 후보 위치 | 분류 | 결정 |
|------|----------|------|------|
| (예) `pump_combination` | DB 테이블/엔티티 | 신규 | `pump_cmb_p` 로 등록 |
| (예) 흡입 압력 | 도메인 용어 | 기존 재사용 | `ot-integration.md` 의 PRI 사용 |

분류값: **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

## 신규 엔티티/DB 컬럼
- 엔티티: 도메인 패키지, 외부 PK 여부, suffix(`_m`/`_d`/...), 인덱스 후보
- 컬럼: 타입, NULL 정책, 약어 적합성

## 기존 사전·패턴과의 충돌
- 충돌 항목 + 권장 해소책 (회의 결론과 일치해야 함)

## PLAN 으로 전달할 결정 사항
- 도메인 모델 초안: ...
- DB 설계 변경 초안: ...
- 적용할 패턴: ...

## 룰 갱신 지시서 (PLAN approved 의 전제조건)
- [ ] `.claude/rules/domain-abbreviations.md` — `pump_cmb` 항목 추가
- [ ] `.claude/rules/naming.md` — DB suffix 표 변경 (있다면)
- [ ] `.claude/rules/entity-patterns.md` — 신규 패턴 추가 (있다면)

## 산출물
- [계획안](../../../plan/YYYYMMDD/{슬러그}/PLAN1.md)
```

### 6. `/dev:review` 자동 점검 추가

```
ANALYZE-룰 정합성 점검:
- ANALYZE.md "## 룰 갱신 지시서" 에 명시된 룰 파일들이 작업 브랜치에서 실제로 수정됐는지 git diff 로 대조
- 명시됐으나 미수정인 파일이 있으면 REVIEW 의 발견 사항(중간 우선순위) 으로 자동 추가
```

### 7. `domain-abbreviations.md` 1차 시드

신규 카테고리(도메인 모듈명 약어, 이벤트 타입 약어 등)만 본 파일에 등록한다.
DB suffix·센서 코드·지자체 코드는 기존 1차 정의 파일을 그대로 참조 (중복 정의 금지).

```markdown
# 도메인명 약어 사전

## 사용 규칙
- 모든 신규 도메인 약어는 이 파일에 먼저 등록한 뒤 코드/DB 에 사용한다
- 동의어/유사 의미 약어 동시 등록 금지 (충돌 시 ANALYZE 에서 결정)

## 약어 표

### 마스터 도메인 (현재 코드 존재)
| 약어 | 풀네임 | 설명 | 1차 등록일 |
|------|--------|------|-----------|
| user | user | 사용자 | 2026-04-23 |
| auth | authentication | 인증 | 2026-04-23 |

### 도입 예정 (아직 코드 없음 — 도입 시 ANALYZE 로 확정)
| 약어 | 풀네임 | 설명 | 비고 |
|------|--------|------|------|
| pump | pump | 펌프 | 도입 예정 |
| raw  | raw data | SCADA 원시 수집 데이터 | 도입 예정 (예: `rawdata_1m_h`) |
| ctrl | control | 제어 명령/로그 | 도입 예정 |
| alarm | alarm | 알람 | 도입 예정 |
| diag | diagnosis | 진단 | 도입 예정 |
| opt  | optimization | AI 최적화 결과 | 도입 예정 (예: `opt_result_h`) |

## 다른 약어 사전과의 관계
- DB suffix(`_m`, `_l`, `_d`, `_h`, `_c`, `_p`): `naming.md` 가 1차 정의
- 센서 코드(FRI, PRI, LEI, PWI, RMS): `ot-integration.md` 가 1차 정의
- 지자체 코드(gs, gm2 ...): `multi-tenant.md` 가 1차 정의

> 위 3개는 이미 정의되어 있는 영역이므로 본 파일에서 중복 정의하지 않는다.
```

### 8. 자기 참조 회피 — 이번 작업 자체는 `/dev:analyze` 미적용

이번 작업은 분석 단계가 아직 존재하지 않는 시점에 분석 단계를 만드는 메타 작업이므로
`/dev:analyze` 단계를 적용하지 않고 PLAN→TASK→IMPL→RESULT→REVIEW→COMMIT 순으로만 진행한다.
다음 작업부터 `/dev:analyze` 가 정상 작동한다.

## 테스트 전략

코드 변경이 아닌 **하네스 메타 워크플로우 변경** 이므로 단위 테스트가 아닌 시나리오 드라이런으로 검증한다.

1. **샘플 슬러그로 드라이런** — 임의 슬러그(예: `pump_basic_master`)로 `/dev` 호출 시
   `/dev:analyze` 가 첫 단계로 호출되고 `docs/analyze/20260423/pump_basic_master/ANALYZE1.md` 가 생성되는지 확인
2. **PLAN 차단 검증** — ANALYZE 의 "룰 갱신 지시서" 체크박스를 일부러 미체크 상태로 둔 채 `/dev:plan` 호출 →
   "ANALYZE 의 룰 갱신을 먼저 완료하세요" 메시지로 차단되는지
3. **Fix Cycle 분기 검증**
   - 케이스 A: REVIEW 의 블로커에 "용어 충돌" 키워드가 있는 fixture → `/dev` 재진입 시 ANALYZE2 자동 작성
   - 케이스 B: 일반 코드 블로커만 있는 fixture → ANALYZE 스킵, PLAN2 직행
4. **REVIEW 자동 점검 검증** — 룰 갱신 지시서에 명시된 파일을 일부러 수정하지 않은 상태에서 `/dev:review` →
   발견 사항에 "룰 갱신 누락" 항목이 자동 추가되는지
5. **기존 디렉토리 회귀 호환** — 기존 `docs/analyze/20260422/pumpcontrol/` 에 ANALYZE1.md 만 보강해서
   `/dev:analyze` 가 정상 인식하는지
6. **5인 팀 회의 라운드트립** — 신규 용어와 신규 테이블이 동시에 등장하는 fixture 로 `/dev:analyze` 호출 →
   `wtp-glossary-manager`/`wtp-dba-reviewer`/`wtp-backend-engineer`/`wtp-domain-expert` 4개 에이전트가
   안건 키워드 매핑(§구현 방향 §3.2 4번)대로 호출되고 회의록 섹션이 채워지는지
7. **외부 산출물 사용자 개입 검증** — `.docx` 파일만 있는 fixture 로 `/dev:analyze` 호출 시
   "사용자가 .md 변환 또는 paste" 안내 메시지 출력 후 대기 상태 진입하는지

## 제외 사항

(상기 §범위·제외 와 동일 — 다음 작업으로 분리)
- 기존 룰 파일 본문의 대규모 리팩터링
- pre-commit 훅 추가
- 도메인 약어의 코드 강제력 (lint 등)
- `wtp-domain-expert` 에이전트 정의 변경

## 예상 산출물

LARGE 작업, TASK 분할 (가) 안 채택:

- **TASK1-1 커맨드/하네스/에이전트** — `dev/analyze.md` 신규 (5인 팀 오케스트레이션 본문 포함),
  `dev.md`/`dev/plan.md`/`dev/review.md` 수정, `wtp-backend-engineer.md`/`wtp-glossary-manager.md` 신규
- **TASK1-2 룰/문서** — `domain-abbreviations.md` 신규, `doc-harness.md`/`CLAUDE.md` 수정
  (ANALYZE 템플릿에 회의록 섹션 포함)
- **TASK1-3 검증** — 시나리오 7종 드라이런 (정상 흐름 / PLAN 차단 / Fix Cycle 분기 /
  REVIEW 점검 / 기존 디렉토리 호환 / 5인 회의 라운드트립 / 외부 산출물 사용자 개입)

- [태스크 1-1](../../../tasks/20260423/analyze_phase_도입/TASK1-1.md)
- [태스크 1-2](../../../tasks/20260423/analyze_phase_도입/TASK1-2.md)
- [태스크 1-3](../../../tasks/20260423/analyze_phase_도입/TASK1-3.md)
