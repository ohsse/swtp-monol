---
status: completed
created: 2026-04-20
updated: 2026-04-20
---
# 레거시 EMS/PMS → 신규 분류 체계 매핑 설계 결과

## 관련 작업
- [계획안](../../../plan/20260420/harness_도메인_DB_보강/PLAN2.md)
- [태스크](../../../tasks/20260420/harness_도메인_DB_보강/TASK2.md)

## 작업 요약

PLAN2에서 정의한 4개 파일을 작성/수정하여 레거시 EMS/PMS → 신규 6축 분류 체계 매핑 규칙 문서를 완성했다.
핵심 산출물인 `.claude/rules/legacy-mapping.md`(8개 섹션)를 신설하고, 이를 참조하는 하네스 문서 3개를 수정했다.

## 변경 사항

| 파일 | 유형 | 변경 내용 |
|------|------|----------|
| `.claude/rules/legacy-mapping.md` | **신설** | 8개 섹션 전체: 분류 6축 해석 기준, 레거시 suffix 판정 지침, 도메인별 매핑표 seed 13행, 안티패턴 A·B·C 해소 가이드, KWS-GS-* 추적 절차, 동등성 검증 체크리스트, 매핑표 갱신 룰 |
| `CLAUDE.md` | **수정** | "도메인 모델링 시 필수 참조" 표에 `legacy-mapping.md` 항목 추가 |
| `.claude/rules/naming.md` | **수정** | suffix 표 하단에 d/l·p/c 경계 해석 주석 3줄 보완 |
| `.claude/agents/wtp-domain-expert.md` | **수정** | §5 KWS-GS-* 추적 항목에 "§3 매핑표 미수록 테이블 → 블로커 지적" 항목 추가 |
| `.claude/agents/wtp-dba-reviewer.md` | **수정** | §1 파티셔닝 전략 헤더에 `legacy-mapping.md §3` 신규 테이블명 참조 추가 |

### legacy-mapping.md 주요 내용

- **§1 분류 6축 해석 기준**: m/l/d/h/c/p 각각의 본질·변경 빈도·데이터 수명·정수장 대표 예 + 충돌 판정 규칙 3가지
- **§2 레거시 suffix 판정 지침**: `_INF / _RST / _LOG / _ALR / _TAG / _VAL / _GRP / _HOUR~_MONTH / 접미 없음 / _OLD` 9종 1차 권고 + 조건부 변경 기준
- **§3 매핑표 seed 13행**: 펌프 실체·조합·계수·인터록, 알람, 태그, SCADA 수집·집계, AI 진단, 진동, 요금제, 절감, 관망(EPA) 도메인 커버
- **§4 안티패턴 해소 가이드**: A(`_INF` 위장 마스터), B(알람 3중 의미 혼재), C(초광역 와이드 테이블) — 레거시 대표 사례 및 신규 해소 안 포함
- **§5~§7 운영 절차**: KWS-GS-* 추적 의무, 동등성 체크리스트 7항목, 매핑표 갱신 룰 3가지

## 테스트 결과

Java 코드 변경 없음 — 전체 빌드로 회귀 확인.

```
./gradlew.bat build
BUILD SUCCESSFUL in 24s
18 actionable tasks: 18 up-to-date
```

| 검증 항목 | 결과 |
|----------|------|
| 레거시 상대 경로 존재 확인 (`../../legacy/ems/docs/ems_schema.md` 등 3개) | ✅ 모두 존재 |
| 도메인 용어 glossary 상충 여부 | ✅ 상충 없음 (알람 4단계 0~3, 측정값 약어 등 일치) |
| `dev/review.md:45` legacy-mapping.md 참조 일치 | ✅ 기존 참조 경로와 신설 파일명 일치 |
| wtp-domain-expert.md / wtp-dba-reviewer.md 참조 추가 | ✅ 두 에이전트 모두 반영 |
| `./gradlew.bat build` 통과 | ✅ BUILD SUCCESSFUL |

## 비고

- **PLAN2 제외 사항 준수**: 실제 엔티티·JPA·마이그레이션 코드는 이 PR에 포함하지 않았다. §3 매핑표는 seed만 기록하며 각 도메인 구현 PR에서 확장된다.
- **후속 작업**: 이후 도메인 엔티티 PR(예: `pump_도메인_구현`)은 legacy-mapping.md §3 행 존재 여부를 wtp-domain-expert가 블로커로 체크한다.
- **wtp-dba-reviewer.md 추가 수정**: PLAN2 범위에는 명시되지 않았으나, PLAN2 테스트 전략 4번 항목(`wtp-dba-reviewer.md가 본 문서 경로를 참조하는지 확인`)에 근거해 §1 파티셔닝 헤더에 참조를 추가했다.
