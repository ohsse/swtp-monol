# TASK / ANALYZE 체크박스 파일 경로 기록 규칙

> **참조**: 본 규칙은 [`README.md`](README.md) §디렉토리 구조 · §문서 상태 흐름 을 전제로 한다. 5종 문서 템플릿 본문은 [`templates.md`](templates.md) 참조.

---

pre-commit 훅이 TASK 문서를 파싱하여 파일을 자동 unstage하며, ANALYZE 의 "## 룰 갱신 지시서" 체크박스도
`/dev:plan` 의 전제조건 검증과 `/dev:review` 의 자동 점검에서 동일 방식으로 파싱되므로, **정확한 경로 기록이 필수**다.

| 규칙 | 설명 |
|------|------|
| **전체 상대 경로 필수** | 모듈 루트 기준 전체 경로를 백틱으로 감싸 기록한다 |
| **축약 경로 금지** | `common/.../p6spy/...` 형태 금지 |
| **글롭 패턴 금지** | `api/src/main/resources-env/local/*` 형태 금지 |
| **명령어 별도 표기** | `./gradlew.bat ...` 등 실행 명령어는 백틱 경로와 구분되도록 코드블록 또는 일반 텍스트로 기록 |
| **룰 파일 경로** | ANALYZE 의 "## 룰 갱신 지시서" 체크박스도 동일 규칙 — `.claude/rules/{파일명}.md` 또는 `CLAUDE.md` 등 모듈 루트 기준 전체 상대경로를 백틱으로 감싼다 |

```markdown
# 올바른 예
- [ ] `common/src/main/java/com/mo/swtp/common/p6spy/CustomP6SpySqlFormatter.java` 생성
- [ ] `api/src/main/resources/application.yml` 수정

# 잘못된 예 (훅 파싱 오류 유발)
- [ ] `common/.../p6spy/CustomP6SpySqlFormatter.java` 생성   ← 축약 금지
- [ ] `api/src/main/resources-env/local/*` 설정 추가         ← 글롭 금지
- [ ] `./gradlew.bat clean build` 성공 확인                  ← 명령어 금지
```

명령어 검증 항목은 Phase 내에서 다음과 같이 별도 표기한다:

```markdown
### Phase 5: 빌드 검증
- [ ] `./gradlew.bat clean build` 실행 성공 확인
```

> `./` 로 시작하는 항목은 훅이 자동으로 파일 경로가 아닌 명령어로 인식하여 스킵한다.

---

## 검증 기준 표기 형식

ROOT [`coding-discipline.md` §4.1](../../../../.claude/rules/coding-discipline.md) 적용. TASK 체크박스에 검증 기준을 명시하는 형식과 훅 파싱과의 호환성 규칙.

### 형식

```markdown
- [ ] `{파일경로}` 작업 설명 → 검증: {확인 명령 / 테스트 / 조회}
```

- 첫 백틱 쌍에 파일 경로
- `→ 검증:` 다음에 확인 방법 (예: `grep "X" Y.md 매칭`, `./gradlew.bat test PASS`, `BUILD SUCCESSFUL 출력 확인`)

### ⚠️ 검증 영역 백틱 사용 금지

**검증 영역에는 백틱(`` ` ``) 을 사용하지 않는다.** 사용 시 `check-task-unstage.sh` 훅의 `sed -n 's/^- \[.\] \`\([^\`]*\)\`.*/\1/p'` 패턴이 두 번째 백틱을 첫 백틱의 닫는 쌍으로 오인하여, 파일 경로가 잘못 파싱되거나 누락된다.

```markdown
# 올바른 예
- [ ] `common/src/.../Foo.java` 생성 → 검증: ./gradlew :common:test PASS

# 잘못된 예 (훅 파싱 오작동)
- [ ] `common/src/.../Foo.java` 생성 → 검증: `./gradlew :common:test` PASS
```

검증 명령어가 길어지더라도 백틱 없이 일반 텍스트로 기록한다. 코드블록 표기는 Phase 본문 외부에서 사용한다.

---

## 훅 동작과의 연계

본 규칙은 `.claude/hooks/check-task-unstage.sh` 가 `sed -n 's/^- \[.\] \`\([^\`]*\)\`.*/\1/p'` 형식으로 백틱 안의 첫 경로를 추출하는 동작과 1:1 대응한다. 훅 트리거·차단 동작 상세는 [`../hooks-guide.md`](../hooks-guide.md) §3 (`check-task-unstage.sh`) 참조.
