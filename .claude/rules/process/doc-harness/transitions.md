# 상태 전이·자동 실행 표

> **참조**: 본 표는 `dev.md` §7 과 `dev:*.md` 각 스킬의 마지막 전이 안내가 참조하는 **SSOT** 다. Fix Cycle 분기 시 `dev:plan.md` 등이 따르는 알고리즘은 [`README.md` §수정 사이클](README.md#수정-사이클-fix-cycle) 참조. 5종 문서 템플릿은 [`templates.md`](templates.md) 참조.

---

각 `/dev:*` 단계의 승인 요건과 자동 전이 규칙을 단일 정의한다.

| 현재 단계 | 승인 요건 | 다음 단계 | 자동 실행 | Small | Medium | Large |
|----------|----------|----------|---------|-------|--------|-------|
| `/dev` | — (요청 구체화 + 규모 분류) | 규모별 첫 단계 | O | → `/dev:impl` | → `/dev:analyze` | → `/dev:analyze` |
| `/dev:analyze` | `status: approved` + "## 룰 갱신 지시서" 모든 체크박스 `- [x]` 완료 + 사용자 승인 | `/dev:plan` | O (승인 후 즉시) | 면제 | ✅ | ✅ |
| `/dev:plan` | `status: approved` + 사용자 승인 | `/dev:task` | O (승인 후 즉시) | 면제 | ✅ | ✅ |
| `/dev:task` | `status: approved` + 사용자 확인 | `/dev:impl` | O (확인 후 즉시) | 면제 | ✅ | ✅ |
| `/dev:impl` | 테스트 통과 + 모든 분할 TASK `status: completed` | 규모별 분기 | 부분 | → `/dev:commit` 안내 | → `/dev:commit` 안내 | → `/dev:result` 자동 |
| `/dev:result` | — | `/dev:review` | O (즉시) | 면제 | 면제 | ✅ |
| `/dev:review` | 블로커 0건 시 `status: approved` | `/dev:commit` 안내 (블로커 시 Fix Cycle 진입) | X (사용자 안내만) | 면제 | 면제 | ✅ |
| `/dev:commit` | 사용자 명시적 승인 (`커밋` / `commit`) | `/dev:spec` 안내 (선택) | X (**절대** 자동 실행 금지) | ✅ | ✅ | ✅ |
| `/dev:spec` | — | 종료 | X (사용자 명시 호출만) | 선택 | 선택 | 선택 |

**Fix Cycle 분기**: REVIEW 블로커 발견 시 [`README.md` §수정 사이클](README.md#수정-사이클-fix-cycle) 의 "Fix Cycle 감지 알고리즘 (의사 코드)" 박스를 따른다 (ANALYZE 재진입 여부 결정).

**핵심 원칙**:
- 승인 요건이 있는 단계는 **사용자 응답 후에만** 다음 단계로 자동 전이한다 (응답 전 자동 실행 금지)
- `/dev:commit` 은 **항상** 사용자 명시적 승인이 필요하며 자동 전이 대상이 아니다
- 분할 TASK 인 경우 `/dev:impl` 은 한 번에 하나의 split 만 처리하며, 다음 split 은 사용자가 다시 호출해야 한다
- `/dev:spec` 은 frontend 측 `swtp/frontend/docs/api-specs/{슬러그}/SPEC{N}.md` 산출물 라이프사이클을 가지며, backend `/dev` 워크플로우의 cycle 번호와 **독립적**으로 증가한다 (entity·DTO 스펙 변경 시마다 +1)
