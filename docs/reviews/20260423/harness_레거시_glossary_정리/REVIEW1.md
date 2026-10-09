---
status: approved
created: 2026-04-23
updated: 2026-04-23
---

# 하네스 레거시·glossary 정리 리뷰

## 관련 결과
- [결과](../../../results/20260423/harness_레거시_glossary_정리/RESULT1.md)

## 리뷰 범위

- `.claude/` 하위 rules·agents·commands 수정/삭제 분
- 루트 `CLAUDE.md` 및 `common/CLAUDE.md` 변경
- `docs/` 하위 이전 산출물(20260421/20260422) 참조 텍스트 정리
- 신규 PLAN1·TASK1·RESULT1 산출물 구조 및 템플릿 준수 여부
- 검증 6건 결과의 타당성

리뷰 체크리스트(`.claude/commands/dev/review.md`) 중 본 작업에 해당되는 항목만 적용한다. Java 코드 변경이 없으므로 Lombok·Swagger·CommonResponseDto·ErrorCode 등의 기술 체크리스트는 해당 없음.

## 발견 사항

### 높음 (블로커)

없음.

### 중간

**[M1] `.claude/rules/ot-integration.md` 참조 테이블 행 수 축소**

`C:\dev\workspace\swtp\backend\.claude\rules\ot-integration.md` 참조 문서 관계 테이블에서 3행을 제거한 결과 본문 테이블 행이 `db-patterns.md` 와 `legacy/ems/src/main/java/.../kafka/` 2개만 남았다. 구조상 유지는 가능하나, 향후 규칙 추가 없이 이 상태가 지속되면 표 구획의 의미가 약해진다. 후속 작업에서 SCADA·HMI 연동 관련 규칙 문서(예: 센서 관리, 제어 로그)가 추가될 때 이 테이블에 포함시키도록 한다.

### 낮음

**[L1] `pump_interlock_p` 테이블 명세 출처 유보**

`C:\dev\workspace\swtp\backend\.claude\rules\ot-integration.md` line 100 에서 `pump_interlock_p` 테이블의 출처였던 `legacy-mapping.md §3` 이 사라지고 "향후 pump 도메인 구현 시 테이블 명세 확정" 유보 문구로 대체됐다. 현재는 안전한 상태지만, pump 도메인 PLAN 작성 시 이 유보 문구를 "실제 DDL 파일 경로"로 교체하는 작업이 함께 필요하다는 점을 인지해둔다.

**[L2] `docs/plan/20260420/harness_도메인_DB_보강/` 산출물 내 참조 잔존**

사용자 결정에 따라 `docs/plan|tasks|results|reviews/20260420/harness_도메인_DB_보강/` 는 이력 보존 목적으로 손대지 않았다. 이 디렉토리 문서에는 여전히 `legacy-mapping`·`domain-glossary` 문자열이 남아 있다. 이 상태는 의도된 것으로 검증 2차 grep 필터에서도 제외했다. 혹시 후속 작업에서 "docs/ 전체에서 legacy-mapping 참조 0건"을 전제로 하는 검증이 생기면, 이 이력 보존 예외를 명시해야 한다.

**[L3] 신규 PLAN1·TASK1·RESULT1·REVIEW1 문서 내 변경 기록 용도의 문자열 언급**

본 작업의 PLAN1·TASK1·RESULT1·REVIEW1 에는 변경 대상 경로로서 `legacy-mapping.md`·`domain-glossary.md` 문자열이 등장한다. 이는 변경 사항을 기록하기 위한 메타데이터이므로 정상이다. 검증 2차 grep 필터는 `docs/*/20260423/` 경로도 제외하도록 구성하여 노이즈를 차단했다.

**[L4] `wtp-domain-expert` 에이전트 호출 지점 재확인 필요**

`.claude/commands/dev/plan.md` 의 "도메인·DB 검토 게이트" 섹션은 여전히 `wtp-domain-expert` 호출 조건(신규 엔티티·필드 기재 시)을 유지하고 있다. 축소된 에이전트가 알람 4단계·인터록·운전 모드 3개 항목만 검토하므로, 엔티티·필드 명명 검토 기능은 사라졌음을 향후 PLAN 작성자가 인지하도록 해야 한다. 현재 문서는 검토 항목을 명시하지 않고 "에이전트 역할 수행"이라고만 위임하므로 기능 감소를 자동 인지하기 어렵다. 향후 `/dev:plan` 실행 흐름에서 사용자가 혼란을 겪으면 그 시점에 보강한다.

## 개선 제안

- [L4] 다음 `/dev:plan` 실행에서 혼란이 발견되면 `.claude/commands/dev/plan.md` 의 검토 게이트 섹션에 "현재 wtp-domain-expert 는 알람 4단계·인터록·운전 모드 3개 항목만 검토한다" 를 명시적으로 추가한다.
- [L1] pump 도메인 PLAN 작성 시점에 `ot-integration.md` line 100 유보 문구를 실제 DDL 파일 경로로 교체하는 체크 항목을 함께 포함한다.
- 용어 사전 재정제가 별도 PLAN 으로 진행될 때, 본 작업의 산출물(PLAN1·RESULT1·REVIEW1)을 "이전 상태" 참조 문서로 링크한다.

## 결론

- 블로커(높음): 0건
- 권고(중간): 1건
- 참고(낮음): 4건

블로커가 없어 `status: approved` 로 전환 가능하며, `/dev:commit harness_레거시_glossary_정리` 로 커밋 단계로 진행할 수 있다. 중간 1건·낮음 4건은 모두 향후 관련 작업 시점에 자연스럽게 해소되는 성격이므로 별도 Fix Cycle 은 불필요하다.
