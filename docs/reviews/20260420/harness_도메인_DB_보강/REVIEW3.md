---
status: draft
created: 2026-04-20
updated: 2026-04-20
---
# Fix Cycle 3 코드 리뷰

## 관련 결과
- [결과](../../../results/20260420/harness_도메인_DB_보강/RESULT3.md)
- [이전 리뷰](REVIEW2.md)

## 리뷰 범위

| 파일 | 유형 |
|------|------|
| `.claude/rules/legacy-mapping.md` | 수정 |
| `CLAUDE.md` | 수정 |

Java 코드 변경 없음. 문서 내부 참조 일관성 중심으로 검토.

## 발견 사항

| 심각도 | 항목 | 위치 | 내용 |
|--------|------|------|------|
| **높음** | 외부 문서 섹션 번호 오기재 | `.claude/rules/legacy-mapping.md` 참조 표 line 19-20 | `§4 도메인 용어·약어의 원천` — `domain-glossary.md §4`는 실제 "운전 모드" 섹션이므로 불일치. `§4 파티션·인덱스·보존 기간 정책 참조 원천` — `db-patterns.md §4`는 실제 "트랜잭션 격리 기준"이므로 불일치. 이번 Fix Cycle의 "§4 잔존 확인" 수작업 검증 과정에서 외부 문서 지칭 `§4`가 누락됨 |
| 낮음 | REVIEW2 권고(R1·R2·P1) 미처리 | — | PLAN3에서 명시적 제외. Fix Cycle 4에서 위 블로커와 함께 처리 권장 |

## 개선 제안

- **블로커 B1**: `legacy-mapping.md` 참조 표 line 19-20의 `§4` 제거 또는 정확한 섹션 범위로 수정

```markdown
# 수정 전
| [`rules/domain-glossary.md`](domain-glossary.md) | §4 도메인 용어·약어의 원천 |
| [`rules/db-patterns.md`](db-patterns.md)         | §4 파티션·인덱스·보존 기간 정책 참조 원천 |

# 수정 후 (안 A — 섹션 번호 제거)
| [`rules/domain-glossary.md`](domain-glossary.md) | 도메인 용어·약어의 원천 (전 섹션) |
| [`rules/db-patterns.md`](db-patterns.md)         | 파티션(§1)·인덱스(§2)·보존 기간(§6) 정책 참조 원천 |
```

## 결론

- **블로커(높음): 1건** — Fix Cycle 4 필요
- REVIEW2 블로커(B1·B2)는 완전 해소 확인
- **권고(중간): 0건**
- **참고(낮음): 1건**
