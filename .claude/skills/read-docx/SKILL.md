---
name: read-docx
description: "Word 문서(.docx) 에서 본문·표·헤딩을 Markdown 텍스트로 추출한다. `/dev:analyze` 단계에서 요구사항 명세서(*.docx) 가 `docs/analyze/{날짜}/{슬러그}/` 에 포함되어 있을 때, 또는 사용자가 `.docx` 파일을 읽어/분석/변환해 달라고 요청할 때 반드시 사용한다. Python 3 stdlib 만 사용하므로 pandoc·LibreOffice 가 없는 환경에서도 동작한다. Word 문서를 **새로 만들거나 편집** 할 때는 이 스킬이 아니라 `document-skills:docx` 를 사용한다."
---

# read-docx

스마트정수장 `/dev:analyze` 워크플로우에서 `.docx` 요구사항 명세서를 자동으로 읽기 위한 경량 전용 스킬. `document-skills:docx` 플러그인과 달리 **읽기(추출) 전용** 이며 Python 3 stdlib 외 의존성이 없다.

---

## 1. 사용 시점

- `/dev:analyze {슬러그}` 단계에서 `docs/analyze/{YYYYMMDD}/{슬러그}/` 디렉토리에 `.docx` 파일이 수집되었을 때 — `dev/analyze.md §2` 에 따라 **자동 호출**
- 사용자가 "이 docx 파일 내용 요약해 줘" · "요구사항 명세서 읽어 줘" 등 **읽기/분석/변환** 을 요청할 때
- 임의의 docx 파일에서 본문·표·헤딩 구조를 Markdown 으로 변환해야 할 때

**사용하지 않아야 하는 경우**:

- `.docx` 를 **새로 만들거나 편집** 할 때 → `document-skills:docx` 사용
- `.pdf` · `.xlsx` · `.pptx` → 각각 기존 플러그인 스킬 참조
- `.doc` (레거시 바이너리) → 본 스킬 미지원. 변환이 필요하면 `document-skills:docx` 의 `soffice.py` 참조

---

## 2. 실행 방법

Bash tool 로 다음을 호출한다:

```bash
python .claude/skills/read-docx/scripts/extract_docx.py "{docx 파일 경로}"
```

- 파일 경로에 공백·한글이 있으면 **반드시 따옴표** 로 감싼다
- 출력: stdout 으로 Markdown 형식 텍스트 (UTF-8)
- 종료 코드: `0` (정상) / `2` (파일 없음·zip 아님·document.xml 없음)

긴 문서는 `| head -N` 으로 앞부분만 확인하거나, 임시 파일로 저장 후 `Read` 도구로 구간별 조회한다:

```bash
python .claude/skills/read-docx/scripts/extract_docx.py "docs/analyze/20260422/pumpcontrol/01.요구사항 명세서.docx" > /tmp/spec.md
```

---

## 3. 출력 예시

요구사항 명세서 상단:

```markdown
[2.1 송수펌프 제어 분석]

기능 요구사항 명세서

v1.0   2026-04-16   최초 작성

1. 범위

정수지 선택, AI 운전모드(AI / AI추천 / AI분석) 설정, ...
```

표 포함 예시 (보고서 형식):

```markdown
# 2. CLAUDE.md 구성의 특징

## 2.1 전체 구조

CLAUDE.md 는 "프로젝트 선언문 + 규칙 인덱스" 역할만 담당하며, ...

| 규칙 문서 | 1차 정의 영역 | 참조 시점 |
|---|---|---|
| naming.md | 네이밍 컨벤션 | 클래스·DB 컬럼 명명 전 |
```

---

## 4. 추출 규칙

| Word 요소 | Markdown 변환 |
|----------|--------------|
| `<w:p>` (일반 단락) | 빈 줄로 구분된 단락 |
| `<w:p>` + `<w:pStyle w:val="Heading1..6">` | `# {텍스트}` ~ `###### {텍스트}` |
| `<w:tbl>` 표 | pipe 표. **첫 행을 헤더로** 가정하여 `| --- |` 구분선 삽입. 셀 내부 여러 단락은 `<br>` join |
| `<w:drawing>` / `<w:pict>` (이미지) | `[이미지]` 플레이스홀더 (바이너리는 추출 안 함) |
| `<w:tab>` · `<w:br>` | `\t` · `\n` |

---

## 5. 제한 사항

- **이미지 바이너리 미추출** — `[이미지]` 플레이스홀더만 표기한다. 이미지 분리 저장이 필요하면 사용자에게 PNG 로 직접 export 요청
- **tracked changes (변경 추적) 비지원** — 수정 전/후 텍스트 구분 없이 최종본만 출력. 변경 이력이 필요하면 `document-skills:docx` 의 `accept_changes.py` 사용
- **머릿말·바닥글·각주 미포함** — `word/document.xml` 의 `<w:body>` 만 읽는다 (`header1.xml` · `footer1.xml` · `footnotes.xml` 은 무시)
- **복잡한 중첩 표** — 셀 병합(`<w:gridSpan>`·`<w:vMerge>`) 은 무시하고 단순 격자로 평탄화된다. 병합 셀이 많은 표는 시각적으로 어긋날 수 있음
- **.doc (레거시 97-2003) 미지원** — zipfile 로 열면 `BadZipFile` 예외. 필요 시 `document-skills:docx` 의 LibreOffice 변환 루트 사용

---

## 6. 스크립트 위치

```
.claude/skills/read-docx/
├── SKILL.md                  ← 본 문서
└── scripts/
    └── extract_docx.py       ← Python 3 stdlib 전용 추출 스크립트
```

스크립트는 `python` 명령으로 직접 실행 가능하며, 프로젝트 외부(다른 워킹 트리) 에서는 경로를 절대 경로로 지정한다.
