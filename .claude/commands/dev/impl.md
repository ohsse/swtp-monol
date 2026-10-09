# 4단계: 구현 및 검증

목적 슬러그: $ARGUMENTS

---

## 전제조건 검증
1. `$ARGUMENTS`가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:impl jwt_인증_추가`" 안내 후 중단

2. **문서 존재 여부 검증** — `docs/` 하위에서 슬러그 `$ARGUMENTS`와 일치하는 디렉토리를 탐색한다:

   **PLAN 문서 검증:**
   - `docs/plan/` 하위에서 슬러그 일치 디렉토리 탐색 (날짜 불문)
   - PLAN 문서가 존재하면 → Medium 이상 작업으로 판단
   - PLAN 문서가 없으면:
     - TASK 문서도 없으면 → Small 작업으로 간주하고 자유롭게 구현
     - TASK 문서만 있으면 → **"PLAN 문서가 없습니다. `/dev:plan $ARGUMENTS`를 먼저 실행하세요." 경고 후 중단**

   **TASK 문서 검증 (PLAN이 존재하는 경우):**
   - `docs/tasks/` 하위에서 슬러그 일치 디렉토리 탐색
   - **분할 TASK 감지**: `TASK{N}-1.md`, `TASK{N}-2.md`, ... 형태의 파일이 존재하면 분할 TASK로 판단. `- [ ]` 미완료 항목이 있는 가장 낮은 `split` 번호의 파일을 현재 처리 대상으로 선택한다. 한 번의 `/dev:impl` 호출은 **하나의 분할 파일만** 처리한다.
   - **단일 TASK**: `TASK{N}.md` 하나를 처리.
   - TASK 문서가 있으면: `status: approved` 여부 확인. 미승인이면 경고 표시 후 계속 진행할지 확인
   - TASK 문서가 없으면: **"TASK 문서가 없습니다. `/dev:task $ARGUMENTS`를 먼저 실행하세요." 경고 후 중단**

3. 기준 날짜 결정:
   - TASK/PLAN 문서가 있으면 해당 날짜 사용
   - 없으면 오늘 날짜 사용

## 구현

TASK 파일이 있는 경우 미완료 항목(`- [ ]`)을 순서대로 구현한다. **분할 TASK인 경우 현재 선택된 `TASK{N}-{split}` 문서의 미완료 항목만 처리하며, 다른 split 문서는 건드리지 않는다.**

**반드시 준수해야 할 코딩 규칙 (CLAUDE.md 기반):**

### 패키지 구조
- feature-based 도메인 중심 패키지 사용
- `com.mo.swtp.{도메인명}` (api/scheduler)
- `com.mo.swtp.common.{기능}` (공통)

### 코드 스타일
- Java 21, UTF-8, 들여쓰기 4칸
- Lombok 적극 활용: `@Getter`, `@RequiredArgsConstructor`, `@Builder` 등
- `@Setter` 사용 금지 (엔티티)
- 의존성 주입: `@RequiredArgsConstructor` 생성자 주입
- 신규/수정 클래스·메서드에 Javadoc 주석 작성

### 계층별 규칙
- **엔티티**: `.claude/rules/entity-patterns.md` 패턴 준수
- **서비스/리포지토리**: `.claude/rules/api-patterns.md` 패턴 준수
- **네이밍**: `.claude/rules/naming.md` 컨벤션 준수
- **API**: Swagger `@Tag`, `@Operation`, `@ApiResponses` 어노테이션 작성
- **테스트**: `.claude/rules/test-strategy.md` — 단위 테스트(§1)·통합 테스트(§2) 실사용 패턴과 도메인 시나리오(§3·§5.2) 기준 준수. 통합·Testcontainers 격상은 §부록 A 로드맵을 따름

### 예외/응답 처리
- 비즈니스 예외: `RestApiException` 사용
- 에러 코드: `ErrorCode` 인터페이스를 구현하는 enum으로 관리
- 응답: `CommonResponseDto` 형태 사용

### 보안
- JWT secret, DB 계정 등 민감 정보 절대 하드코딩 금지
- 환경별 설정은 `application-{profile}.yml` 또는 환경변수로 주입

## TASK 체크박스 자동 업데이트

각 Task 구현이 완료될 때마다 TASK 문서의 해당 체크박스를 업데이트한다:
- `- [ ] Task 내용` → `- [x] Task 내용`
- `updated` 날짜도 갱신

## 테스트 실행

구현 완료 후 영향받은 모듈의 테스트를 실행한다:
```bash
# 변경된 모듈에 따라 선택적 실행
./gradlew.bat :common:test
./gradlew.bat :api:test
./gradlew.bat :scheduler:test
# 또는 전체
./gradlew.bat test
```

테스트 결과를 사용자에게 보고한다.

## 완료 후 안내 및 자동 전이

테스트 통과 시 절차는 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:impl` 행을 따른다.

요약:
- 현재 TASK 파일 `status: completed` 전환
- **분할 TASK 인 경우**: 남은 split 파일(미완료 항목이 있는 TASK{N}-{split+1} 이후) 이 있으면 "다음 split: TASK{N}-{split+1}. `/dev:impl $ARGUMENTS` 를 다시 실행하세요." 안내 후 대기. 모든 split 이 `completed` 일 때만 다음 단계로 진행
- 규모별 다음 단계: **Small/Medium** → 사용자에게 `/dev:commit` 안내, **Large** → `/dev:result $ARGUMENTS` **자동 실행**

> 규모 판단: PLAN/TASK 둘 다 없으면 Small, 있고 RESULT 가 예상 산출물에 포함되면 Large.
