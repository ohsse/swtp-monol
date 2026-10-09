# 표준 용어 사전 (Standard Terms) — backend SSOT

스마트정수장 백엔드 프로젝트의 **DB 컬럼명** 표준 용어 사전 진입점이다.
DB 표준화 이론의 3층 구조 중 **표준 용어 1층** 만 본 디렉토리가 정의한다.

상위 2층(표준 단어 + 표준 데이터 도메인) 과 비즈니스 도메인 약어는 모노레포 ROOT 어휘 SSOT — `swtp/.claude/rules/dict/` 가 SSOT.

---

## 구조

| 층위 | 파일 | 위치 |
|------|------|------|
| **표준 단어** | `standard-words.md` | ROOT — `swtp/.claude/rules/dict/standard-words.md` |
| **표준 데이터 도메인** | `standard-data-domains.md` | ROOT — `swtp/.claude/rules/dict/standard-data-domains.md` |
| **비즈니스 도메인 약어** | `domain-abbreviations.md` | ROOT — `swtp/.claude/rules/dict/domain-abbreviations.md` |
| **표준 용어 (DB 컬럼명)** | [`standard-terms.md`](standard-terms.md) | backend (본 디렉토리) |

> 데이터 도메인 vs 비즈니스 도메인 구분은 ROOT `swtp/.claude/rules/dict/README.md` 의 §⚠️ "도메인" 용어 충돌 방지 참조.

---

## 신규 등록 진입점

- 새 **단어·데이터 도메인·비즈니스 약어** 등록은 `/dev:analyze` 단계의 5인 회의를 거쳐 ROOT 디렉토리(`swtp/.claude/rules/dict/`) 에 반영한다
- 새 **DB 컬럼(표준 용어)** 등록은 동일 절차로 검토 후 본 디렉토리의 `standard-terms.md` 에 반영한다
- `wtp-glossary-manager` 에이전트가 충돌 분류(신규 / 기존 재사용 / 유사 충돌 / 폐기·통합) 를 주도한다
- 회의 결론은 `docs/analyze/{YYYYMMDD}/{슬러그}/ANALYZE{n}.md` 의 §표준 사전 카탈로그 3표로 기록된 후 ROOT + 본 디렉토리에 반영한다

### 표준 용어 유사 충돌 판정 기준

- **의미 중복 금지** — 예: `member_id` vs `user_id` 동시 등록 금지

(상위 층위의 충돌 판정 기준은 ROOT `swtp/.claude/rules/dict/README.md` §유사 충돌 판정 기준 참조)

---

## 다른 사전과의 관계

| 타 사전 | 위치 | 본 디렉토리와의 관계 |
|--------|------|--------------------|
| ROOT 어휘 사전 (단어·데이터 도메인·비즈니스 약어) | `swtp/.claude/rules/dict/` | 본 표준 용어가 조합 재료로 사용. ROOT 가 SSOT |
| 테이블 suffix (`_m`/`_l`/`_d`/`_h`/`_c`/`_p`) | [`../naming.md`](../naming.md) | 표준 용어 + suffix → 테이블명 |
| 센서 코드 (FRI/PRI/LEI/PWI/RMS) | [`../ot-integration.md`](../ot-integration.md) | OT 수집 전용 태그. 본 디렉토리에 중복 등록 금지 |
| 지자체 빌드 프로파일 코드 (gs·gm2·hy 등) | [`../multi-tenant.md`](../multi-tenant.md) | 빌드 속성 전용. 본 디렉토리에 중복 등록 금지 |
