---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 펌프 가동상태 boolean → 원본값(0/1) 리턴 변경 — 운전현황분석 7번 섹션

## 목적
`/api/facility/{facilityId}/operating-status/outflow-time-series` (운전현황분석 7번 섹션) 응답에서 펌프 OPS 가동상태를 `Boolean`(true/false/null) 대신 계측·예측 테이블에서 조회한 **원본 0/1 값**(Integer)으로 리턴한다.

## 배경
`FacilityOutflowTimeSeriesDto.PumpPoint` 의 `actualRunning`·`predcRunning` 이 `toBoolean` 성격의 tri-state 변환을 거쳐 `true`/`false`/`null` 로 응답된다. 운전원·frontend 가 계측데이터(`rawdata_1m_h.raw_val`)·예측데이터(`predc_1m_h.predc_val`) 의 원본 0/1 을 그대로 받기를 원한다.

## 범위
- **대상**: `FacilityOutflowTimeSeriesService` · `FacilityOutflowTimeSeriesDto`(PumpPoint) · 동 테스트 · `FacilityController`(§7 Swagger)
- **제외**: §3 `PumpStateDto.isRunning`, §7-예측 `PumpPredictionDto.predcIsRunning`, §4/§9/§10 내부 `isPumpRunning` (무수정 — 필요 시 별도 사이클)

## 구현 방향
1. `PumpPoint.actualRunning`(Boolean) → `actualOps`(Integer), `predcRunning`(Boolean) → `predcOps`(Integer). `@Schema`·정적 팩토리·Javadoc 갱신
2. Service: 중간 record `PumpParts`, 헬퍼 `actualRunningTriState`/`predcRunningTriState` 를 Integer 반환으로 변경(`actualOpsTriState`/`predcOpsTriState`). `true→1`·`false→0`·불명`null`. `effectiveVal`(corrVal우선)·GOOD 필터·슬롯 생존/생략 로직은 **현행 유지**
3. 테스트 단언 교체(`isTrue→isEqualTo(1)`·`isFalse→isEqualTo(0)`·`isNull` 유지)
4. Controller §7 `@Operation` 의 "true=가동/false=정지" → "1=가동/0=정지"

## 성공 기준 (검증 가능 형태)
- `./gradlew.bat :api:test` 에서 `FacilityOutflowTimeSeriesServiceTest` 9건 GREEN — 가동/정지 단언이 `1`/`0`, BAD·UNCERTAIN·예측 결측 단언이 `null`
- `actualRunning`·`predcRunning`·`RunningTriState` 식별자 잔존 0건 (grep 확인)
- `./gradlew.bat :api:bootRun` 시 `pumpSeries[].points[].actualOps`·`predcOps` 가 JSON 정수 `1`/`0`/`null` (선택 — 로컬 DB)

## 가정 및 미해결 질문
| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 품질 BAD/UNCERTAIN·결측은 tri-state null 유지 (raw 무조건 노출 아님) | 결정 | 사용자 결정 — PumpPoint 에 qualityCd 부재로 오인 방지 위해 null 분리 유지 (`ot-integration.md §3` 정합) |
| 값 선택은 기존 effectiveVal(corrVal우선) 유지 (raw_val 단독 강제 아님) | 결정 | 본 작업 범위 외 — 기존 동작 보존 (`coding-discipline.md §3` 정밀 수정) |

## 제외 사항
- DB·엔티티·마이그레이션 변경 없음. 신규 표준 용어 없음 (DTO 필드 camelCase 변경만)
- 형제 섹션 가동상태 표현 통일은 본 사이클 범위 외

## 예상 산출물
- 변경: `FacilityOutflowTimeSeriesDto.java` · `FacilityOutflowTimeSeriesService.java` · `FacilityOutflowTimeSeriesServiceTest.java` · `FacilityController.java`
- 후속(선택): `/dev:spec 펌프가동상태_정수리턴`
