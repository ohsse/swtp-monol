---
status: approved
created: 2026-04-20
updated: 2026-04-20
---
# Fix Cycle 4 — 외부 문서 섹션 번호 오기재 수정 및 REVIEW2 권고 처리

## 목적

REVIEW3 블로커 1건 해소 + REVIEW2 권고 3건(R1·R2·P1)을 이번 사이클에 병합 처리한다.

## 배경

- [이전 리뷰](../../../reviews/20260420/harness_도메인_DB_보강/REVIEW3.md) 블로커 해소

REVIEW3 블로커 요약:

| 코드 | 위치 | 내용 |
|------|------|------|
| B1 | `legacy-mapping.md` 참조 표 line 19-20 | `§4 도메인 용어·약어의 원천` — `domain-glossary.md §4`는 실제 "운전 모드". `§4 파티션·인덱스·보존 기간` — `db-patterns.md §4`는 실제 "트랜잭션 격리 기준". 외부 문서 섹션 번호 잘못 기재 |

REVIEW2 권고 병합 (낮음 우선순위이나 동일 파일 수정 범위이므로 함께 처리):

| 코드 | 내용 |
|------|------|
| R1 | `§3` 헤더에 seed 행 수 표기 추가 |
| R2 | 서문에 섹션 번호 off-by-one 비고 추가 |
| P1 | 참조 표 display name에서 `rules/` 접두어 제거 (표기 통일) |

## 범위

| 파일 | 변경 유형 | 변경 내용 |
|------|----------|----------|
| `.claude/rules/legacy-mapping.md` | 수정 | B1(§4 제거), P1(display name 정리), R1(§3 헤더 행 수), R2(서문 비고) |

Java 코드 변경 없음.

## 구현 방향

### B1 + P1: 참조 표 line 19-20 수정

| 항목 | 수정 전 | 수정 후 |
|------|--------|--------|
| domain-glossary 행 display | `rules/domain-glossary.md` | `domain-glossary.md` |
| domain-glossary 행 설명 | `§4 도메인 용어·약어의 원천` | `도메인 용어·약어의 원천 (전 섹션)` |
| db-patterns 행 display | `rules/db-patterns.md` | `db-patterns.md` |
| db-patterns 행 설명 | `§4 파티션·인덱스·보존 기간 정책 참조 원천` | `파티션(§1)·인덱스(§2)·보존 기간(§6) 정책 참조 원천` |

### R1: §3 헤더 seed 행 수 표기

```markdown
## 3. 도메인별 매핑표 seed

핵심 도메인 seed (초기 13행). 전수 매핑은 각 도메인 구현 PR 에서 본 표에 행을 추가하여 확장한다.
```

### R2: 서문 섹션 번호 비고 추가

서문 마지막에 다음 비고를 추가한다:

```markdown
> **참고**: 이 문서의 섹션 번호(§1~§7)는 PLAN2 계획안 섹션 번호보다 1씩 앞당겨진다.
> (PLAN2 §1 목적이 이 문서의 서문으로 통합됨)
```

## 도메인 모델

변경 없음.

## DB 설계 변경

변경 없음.

## 테스트 전략

Java 코드 변경 없음 → `./gradlew.bat build` 통과 확인.

수작업 확인:
1. `legacy-mapping.md` 내 `§4` 잔존 여부 — 외부 문서 지칭 포함 완전 제거 확인
2. §3 헤더 행 수 표기와 실제 테이블 행 수(13행) 일치 확인

## 제외 사항

- 기타 에이전트·문서 파일 변경 없음
- Java 코드 변경 없음

## 예상 산출물

- [태스크](../../../tasks/20260420/harness_도메인_DB_보강/TASK4.md)
