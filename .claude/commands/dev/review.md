# 6단계: 코드 리뷰 (REVIEW 문서 작성)

목적 슬러그: $ARGUMENTS

---

## 전제조건 검증
1. `$ARGUMENTS`가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:review jwt_인증_추가`" 안내 후 중단
2. RESULT 문서 탐색 (`docs/results/` 하위에서 슬러그 일치):
   - 같은 날짜 디렉토리 내에서 **가장 큰 번호**의 RESULT 문서를 찾는다.
   - RESULT 문서가 없으면 → "결과 문서가 없습니다. `/dev:result $ARGUMENTS`를 먼저 실행하세요." 경고 후 중단
3. 기준 날짜: RESULT 문서의 날짜를 재사용
4. 현재 RESULT 번호가 N이면 REVIEW{N}을 생성한다 (RESULT1→REVIEW1, RESULT2→REVIEW2)
5. `docs/reviews/{날짜}/$ARGUMENTS/` 탐색:
   - 기존 REVIEW 문서가 있으면 내용을 확인하여 블로커 해소 여부 재검토
   - Fix cycle 진행 중이면 "관련 결과"에 이전 REVIEW 링크도 추가

## 코드 리뷰 수행

리뷰는 **메인 Claude 가 변경 영역에 따라 wtp-\* 서브에이전트를 직접 호출** 하는 방식으로 수행한다. `feature-dev` 플러그인은 본 프로젝트에 미설치되어 있으며, 각 에이전트의 검토 항목 SSOT 는 agent 정의서에 명시되어 있다.

**호출 매핑** (`git diff --name-only HEAD` 결과 + PLAN/RESULT 본문 기준):

| 변경 영역 | 필수 호출 에이전트 | 책임 항목 SSOT |
|---------|------------------|--------------|
| Java 소스 (`api/`·`common/`·`scheduler/` 의 `*.java`) | `wtp-backend-engineer` | [`.claude/agents/wtp-backend-engineer.md`](../../agents/wtp-backend-engineer.md) §검토 항목 7·8·9·10·11 |
| 도메인 룰 직결 (알람·인터록·운전 모드·이력 기록) — ANALYZE "## 도메인 룰 4영역 점검" 에 "해당" 표기가 있거나 `ot-integration.md` 인용 코드 변경 | `wtp-domain-expert` | [`.claude/agents/wtp-domain-expert.md`](../../agents/wtp-domain-expert.md) §검토 항목 4·5·6 |
| DB 마이그레이션 SQL (`db/migration/*.sql`) · `docs/ddl/*.sql` 도메인 SSOT 사본 · JPA Repository 쿼리 메서드·QueryDSL 빌더·`@EntityGraph`/`@Query` 신규·수정 | `wtp-dba-reviewer` | [`.claude/agents/wtp-dba-reviewer.md`](../../agents/wtp-dba-reviewer.md) §REVIEW 자동 점검 책임 |
| ANALYZE 사전 카탈로그(3표) 또는 룰 갱신 지시서 체크박스에 명시된 룰 파일이 git diff 에 빠진 경우 | `wtp-glossary-manager` | [`.claude/agents/wtp-glossary-manager.md`](../../agents/wtp-glossary-manager.md) §검토 항목 8 |

**호출 프롬프트 공통**:
- 변경된 파일 목록 (`git diff --name-only HEAD`)
- PLAN 문서 경로 + 내용 (Medium/Large)
- RESULT 문서 경로 + 내용 (Large)
- 검토 대상 섹션·범위 명시 + 단답형 200~400단어 강제 (agent 정의서 표준)

각 에이전트는 본인의 §검토 시작 전 필수 파일 읽기 목록을 자체적으로 Read 한 후 판정한다 — 메인 Claude 가 룰 파일 경로를 prompt 에 일일이 나열할 필요 없다.

**리뷰 체크리스트:**
- [ ] Lombok 사용 (`@Getter`, `@RequiredArgsConstructor`, `@Setter` 금지)
- [ ] 생성자 주입 사용 (`@Autowired` 필드 주입 금지)
- [ ] Javadoc 주석 (신규/수정 클래스, 메서드)
- [ ] Swagger 어노테이션 (`@Tag`, `@Operation`, `@ApiResponses`)
- [ ] `CommonResponseDto` 응답 형태
- [ ] `RestApiException` + `ErrorCode` enum 사용
- [ ] `ErrorCode` 구현 enum 필드 구성 점검 — `httpStatus`만 허용, `String` 타입 필드(`message` 등) 금지 (`.claude/rules/exception-patterns.md` 참조)
- [ ] 민감 정보 하드코딩 여부
- [ ] 네이밍 컨벤션 준수
- [ ] 패키지 구조 (feature-based 도메인 중심)
- [ ] 엔티티 패턴 (`@Setter` 금지, 정적 팩토리, 변경 메서드)
- [ ] **도메인 규칙 정합성**: 알람 4단계(0:정상·1:주의·2:경보·3:TRIP) 처리 누락 여부, 인터록 조건 구현 여부, 운전 모드 전환 로직 누락 여부
- [ ] **쿼리 성능**: N+1 패턴 없음, 대용량 테이블 페이지네이션 적용, 시계열 쿼리 파티션 키 포함 (`db/README.md` 진입 — 자식 룰 적용)
- [ ] **OT 연동 안전성**: SCADA 수신 데이터에 센서 품질 검사(QUALITY 필드) 적용 여부, 외부 시스템 호출에 재시도·회복성 로직 포함 여부
- [ ] **프로세스 검증**: TASK 규모(Phase 수/체크박스 수)가 분할 기준(Phase 10 / 체크박스 60) 대비 적절했는가 — RESULT{N}.md 의 `## TASK 규모` 표 참조

## ANALYZE-룰 정합성 점검 (자동)

ANALYZE 문서가 존재하는 작업(Medium/Large)에 대해 자동으로 룰 갱신 누락을 점검한다.

1. `docs/analyze/` 하위에서 슬러그 일치 가장 최신 ANALYZE 문서를 탐색
2. ANALYZE 문서가 없으면 본 점검은 건너뛴다 (해당 작업이 Small 이거나 ANALYZE 도입 이전 작업)
3. ANALYZE 문서의 "## 룰 갱신 지시서" 섹션에서 모든 체크박스 항목의 룰 파일 경로 추출
   - 형식 예시: `- [x] \`swtp/.claude/rules/dict/domain-abbreviations.md\` — pump 항목 추가`
   - 백틱 안의 경로를 추출 (`.claude/rules/...` / `CLAUDE.md` 등)
4. `git diff --name-only HEAD~..HEAD` 또는 작업 브랜치의 변경 파일 목록과 대조
5. 룰 갱신 지시서에 명시되었으나 실제 변경에 포함되지 않은 룰 파일이 있으면:
   - REVIEW 문서의 "## 발견 사항" 표에 **중간 우선순위**로 자동 추가
   - 형식 예시:
     ```
     | 중간 | ANALYZE 룰 갱신 누락 | `swtp/.claude/rules/dict/domain-abbreviations.md` | ANALYZE1 의 룰 갱신 지시서에 명시되었으나 실제 변경 없음. 누락 시 도메인 사전 drift 발생 |
     ```
6. 본 점검 결과는 REVIEW 의 "## 리뷰 범위" 섹션에 "ANALYZE-룰 정합성 점검 수행" 한 줄로 명시

## REVIEW 문서 작성

발견 사항을 심각도별로 분류하여 문서를 작성한다:

| 심각도 | 기준 |
|--------|------|
| **높음 (블로커)** | 보안 취약점, 규칙 위반, 명백한 버그 |
| **중간** | 코드 품질 문제, 가독성 저하 |
| **낮음** | 개선 제안, 스타일 의견 |

**REVIEW 문서 템플릿**: [`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) 의 `REVIEW{n}.md` 블록(SSOT)을 그대로 사용한다. 치환 규칙:

- `작업목적` → `$ARGUMENTS`
- `YYYYMMDD` → RESULT 문서의 날짜
- `RESULT1.md` → `RESULT{N}.md` (현재 사이클 번호)
- Fix cycle 진행 중(N ≥ 2)이면 [`process/doc-harness/README.md` §상호 참조 규칙](../../rules/process/doc-harness/README.md#상호-참조-규칙) 의 `REVIEW{n}.md 헤더` 양식대로 직전 REVIEW 링크를 `## 관련 결과` 에 추가

## 완료 후 안내

리뷰 완료 후 절차는 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:review` 행을 따른다.

요약:
- **블로커(높음) 발견 시**: `status: draft` 유지 → "블로커 N건 발견. 수정 사이클을 시작하려면 `/dev $ARGUMENTS` 를 실행하세요." 안내 (Fix Cycle 감지 알고리즘은 doc-harness §수정 사이클 박스 참조)
- **블로커 없음**: `status: approved` 전환 → 사용자에게 `/dev:commit $ARGUMENTS` 안내 (자동 실행 금지)
