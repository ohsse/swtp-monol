---
status: approved
created: 2026-05-13
updated: 2026-05-13
---
# 시설물·계측기 자식별 응답 DTO 분리 + Pump.tagNm 폐기 + 고아 자산 백지화 — 코드 리뷰

## 관련 결과
- [결과](../../../results/20260512/시설물응답DTO명세/RESULT1.md)
- [분석](../../../analyze/20260512/시설물응답DTO명세/ANALYZE1.md)
- [계획안](../../../plan/20260512/시설물응답DTO명세/PLAN1.md)
- [태스크](../../../tasks/20260512/시설물응답DTO명세/TASK1.md)

## 리뷰 범위

4 에이전트 병렬 호출로 종합 검토 수행 ([feature-dev 플러그인 미사용](~\.claude\projects\C--dev-workspace-swtp\memory\feedback_no_feature_dev_plugin.md) 메모리 적용):
- `wtp-backend-engineer` — §7 정량 기준 (3단 상속) / §8 TASK 외 파일 변경 / §9 체크박스 검증 누락 / §10 데드 코드 직접 삭제 / §11 경로 표기 정합성
- `wtp-domain-expert` — §4 가정 섹션 도메인 가정 / §5 경로 표기 / §6 4영역 섹션 자체 점검 + 도메인 안전 추가 (`Pump.tagNm` 폐기와 인터록 흐름·`InstrumentDto.from()` default 분기·`FacilityDto.from()` switch exhaustiveness)
- `wtp-dba-reviewer` — V8_6 DDL 형식·V 번호 채번·COMMENT 의무화 훅 통과·운영 영향·롤백 절차
- `wtp-glossary-manager` — 4 층위 사전 정합성 + 룰 갱신 지시서 5건 ↔ 실제 룰 본문 정합

**ANALYZE-룰 정합성 점검 수행**: ANALYZE1 §룰 갱신 지시서 5건 모두 `[x]` 완료 + 실제 git diff 에서 3 룰 파일 변경 확인 (`api-patterns.md` 2건·`entity-patterns.md` 2건·`standard-terms.md` 1건 = 5건 모두 반영). **누락 0건**.

## 발견 사항

| 심각도 | 항목 | 위치 | 내용 |
|--------|------|------|------|
| 중간 | `entity-patterns.md §FK 보유 측 SSOT` 인용 오류 (정정 완료) | `backend/.claude/rules/entity-patterns.md` L.437 | 본 사이클 룰 갱신 작업 중 작성한 "위 두 예외 외 모든 역방향 중복 보유는 `coding-discipline.md §2.5 ⚠️ 절대 금지` 와 동격" 문구가 인용 오류. `coding-discipline.md §2.5` 는 "정량 기준 면책 조항" 이지 "⚠️ 절대 금지" 가 아님 — `⚠️ 절대 금지` 표현은 `ot-integration.md §5` 의 것. 정정: "`ot-integration.md §5 ⚠️ 절대 금지` 의 이중 소스 불일치 위험과 동격으로 처리" 로 변경. **REVIEW 진행 중 즉시 정정 완료** (`coding-discipline.md §3` 본인 수정으로 인한 즉시 정리 규정 적용 — 본 사이클이 신설한 룰 본문의 인용 오류이므로 본 사이클에서 정리하는 것이 정합) |
| 낮음 | `InstrumentDto.from()` default 분기 — 후속 사이클 인터록 평가 위험 | `backend/api/src/main/java/com/mo/swtp/instrument/dto/InstrumentDto.java` | 현 시점 `InstrumentController` 부재 + Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 5종 skeleton 자식 미신설 상태에서 default 분기 `IllegalStateException` 작동. 향후 5종 자식 DTO 신설 사이클에서 switch 분기 미확장 + `InterlockValidator` 가 부모 다형성 조회 시 런타임 폭발 위험. 후속 사이클 ANALYZE 가정 섹션에 "5종 분기 미확장 시 인터록 평가 런타임 차단 위험" 명기 권고 (`entity-patterns.md §JPA JOINED §도메인 룰 — equip_type_cd 필터 강제` + `ot-integration.md §5` 영향) |
| 낮음 | `FacilityDto.from()` switch exhaustiveness 미보장 | `backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` | sealed class 미사용으로 컴파일러 exhaustiveness 미보장. 새 `Facility` 자식 종류 (예: `ElectricRoom`) 추가 시 switch 분기 미확장 + 운전 모드 평가 (시설 단위 `ai_drvn_mod_p`) 에서 런타임 차단 가능성. 향후 자식 종류 추가 ANALYZE 가정 섹션에 명기 권고 |
| 낮음 | 데드 코드 직접 삭제 — 룰 면제 조항 부재 | Phase 4 백지화 4건 | `coding-discipline.md §3.1` 본문은 "기존부터 존재하던 데드 코드는 직접 삭제 금지, RESULT 발견 사항 보고만" 이나 본 사이클은 "사용자 명시 결정 + pump+AI 백지화 사이클 1 직후 연속 처리" 두 조건으로 자체 해석 면제. 향후 재발 시 5인 회의 안건으로 §3.1 에 "사용자 명시 결정 + 직전 사이클 즉시 부수 효과" 면제 조항 명시 검토 권고 |
| 낮음 | reflection helper 중복 3건 (4건 누적 시 공통 추출) | `FacilityDtoSerializationTest.setFacilityType` · `PumpDtoSerializationTest.setEquipType` · `FacilityServiceTest.setFacilityIdAndType` | 동일 패턴 reflection 헬퍼 3건 — 현 시점 중복 추출 기준 (4건) 미달. 향후 4건 누적 시 `common/src/test/java` 공통 helper 추출 권고. 본 사이클 즉시 추출 불요 (`coding-discipline.md §2` "요청되지 않은 추상화 계층 금지") |
| 낮음 | V8_6 DDL 주석 내 룰 경로 표기 | `backend/common/src/main/resources/db/init/V8_6__pump_m_drop_tag_nm.sql` L.5·27 | 주석에 `.claude/rules/entity-patterns.md` 모듈 루트 기준 상대 경로 표기. 운영 무영향. `coding-discipline.md §1` 5번째 항목 (절대 경로 검증) 정합 차원에서 향후 DDL 작성 시 `backend/.claude/rules/...` 형식 권고 |
| 낮음 | `api-patterns.md §상속 상한` 적용 사례 "..." 생략 표기 | `backend/.claude/rules/api-patterns.md` L.219 | "`BaseAuditResponseDto → InstrumentDto(abstract) → PumpDto/...`" 의 "..." 가 미정 자식 5종 (Valve 등) 을 암시. 본 사이클 결정 (PumpDto 만, 5종 후속 이연) 정합하나 명세 정확성을 위해 "PumpDto (Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 는 후속 사이클)" 명시 권고 |
| 낮음 | RESULT1 §계획 외 변경 — 다른 사이클 미커밋 파일 분류 | `RESULT1.md §계획 외 변경` | git status 에서 `image/*.png`·`docs/analyze/20260508/송수펌프_가동이력`·`docs/plan/20260512/계측기관리CRUD` 등 다른 사이클 미커밋 파일이 표시되나 RESULT 가 본 사이클 무관 + `/dev:commit` 스테이징 대상 외로 명시. `coding-discipline.md §3` 준수 |

## 개선 제안

본 사이클 즉시 적용:
- (정정 완료) `entity-patterns.md §FK 보유 측 SSOT` 인용 오류 — `coding-discipline.md §2.5 ⚠️ 절대 금지` → `ot-integration.md §5 ⚠️ 절대 금지`

후속 사이클 또는 별도 ANALYZE 안건:
- Instrument 자식 5종 응답 DTO 신설 시점에 `InstrumentDto.from()` switch 분기 확장 — ANALYZE 가정 섹션에 default 분기 위험 명기
- `Facility` 자식 종류 추가 사이클에서 동일 점검 — `FacilityDto.from()` switch 확장
- `coding-discipline.md §3.1` 데드 코드 정책에 "사용자 명시 결정 + 직전 사이클 즉시 부수 효과" 면제 조항 명시 검토 — ROOT 룰 갱신이므로 backend `/dev:analyze` 5인 회의 + 사용자 승인 (`coding-discipline.md §5.1`)
- reflection helper 4건 누적 시 `common/src/test/java/com/mo/swtp/common/test` 공통 헬퍼 추출
- `api-patterns.md §상속 상한` 적용 사례 명시화 — Valve 등 5종 후속 사이클 이연 사실 보강

## 결론

**블로커 (높음): 0건** — Fix Cycle 진입 불요.
**권고 (중간): 1건** (정정 완료) — REVIEW 진행 중 즉시 정리.
**참고 (낮음): 7건** — 후속 사이클 또는 4건 누적 시 공통 추출 등 예방적 지적.

본 사이클 산출물은 ANALYZE1 5인 회의 결론 + PLAN1 결정 + TASK1 27 체크박스 모두 정합 적용. clean build BUILD SUCCESSFUL + api:test 113 PASS + grep 최종 검증 모두 통과. 룰 갱신 5건 ANALYZE 결론과 본문 정합 확인.

`/dev:commit 시설물응답DTO명세` 진행 가능.
