"""
docx 파일에서 본문·표·헤딩을 Markdown 텍스트로 추출한다.

Python 3 stdlib 만 사용 (zipfile / xml.etree.ElementTree / re / sys).
pandoc·LibreOffice 없이 Windows·Linux 어디서든 동작.

사용법:
    python extract_docx.py "{docx 파일 경로}"

종료 코드:
    0 — 정상 추출
    2 — 파일 없음·zip 아님·document.xml 없음 등 입력 오류
"""

import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

# WordprocessingML 네임스페이스
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
# DrawingML 관계 네임스페이스 (이미지 rId 추출용)
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

NS_W = f"{{{W}}}"
HEADING_STYLE_RE = re.compile(r"^Heading([1-6])$")


def _text(elem) -> str:
    """ 단락·셀에서 <w:t> 텍스트 조각을 순서대로 모아 합친다. <w:tab>·<w:br> 는 공백/개행으로 치환. """
    parts = []
    for node in elem.iter():
        tag = node.tag.split("}", 1)[-1]
        if tag == "t" and node.text:
            parts.append(node.text)
        elif tag == "tab":
            parts.append("\t")
        elif tag == "br":
            parts.append("\n")
    return "".join(parts).strip()


def _heading_level(p) -> int:
    """ <w:pStyle w:val="HeadingN"> 가 있으면 N, 없으면 0 반환. """
    p_pr = p.find(f"{NS_W}pPr")
    if p_pr is None:
        return 0
    style = p_pr.find(f"{NS_W}pStyle")
    if style is None:
        return 0
    val = style.get(f"{NS_W}val", "")
    m = HEADING_STYLE_RE.match(val)
    return int(m.group(1)) if m else 0


def _has_image(p) -> bool:
    """ 단락 내 <w:drawing> 존재 여부. """
    return p.find(f".//{NS_W}drawing") is not None or p.find(f".//{NS_W}pict") is not None


def render_paragraph(p) -> str:
    text = _text(p)
    has_img = _has_image(p)
    if not text and not has_img:
        return ""
    lvl = _heading_level(p)
    if lvl > 0 and text:
        return f"{'#' * lvl} {text}"
    if has_img and not text:
        return "[이미지]"
    if has_img:
        return f"{text}\n\n[이미지]"
    return text


def render_table(tbl) -> str:
    rows = []
    for tr in tbl.findall(f"{NS_W}tr"):
        cells = []
        for tc in tr.findall(f"{NS_W}tc"):
            # 셀 내부 단락을 <br> 로 join
            cell_paras = [_text(p) for p in tc.findall(f"{NS_W}p")]
            cell_text = "<br>".join(s for s in cell_paras if s) or " "
            # pipe 이스케이프 (Markdown 표 깨짐 방지)
            cells.append(cell_text.replace("|", "\\|"))
        if cells:
            rows.append(cells)

    if not rows:
        return ""

    # 모든 행의 최대 컬럼 수에 맞춰 패딩
    width = max(len(r) for r in rows)
    rows = [r + [" "] * (width - len(r)) for r in rows]

    lines = [f"| {' | '.join(rows[0])} |", f"|{'|'.join(['---'] * width)}|"]
    for r in rows[1:]:
        lines.append(f"| {' | '.join(r)} |")
    return "\n".join(lines)


def extract(path: str) -> int:
    if not os.path.exists(path):
        print(f"[read-docx] 파일을 찾을 수 없습니다: {path}", file=sys.stderr)
        return 2

    try:
        with zipfile.ZipFile(path) as z:
            try:
                xml_bytes = z.read("word/document.xml")
            except KeyError:
                print(
                    f"[read-docx] word/document.xml 이 없습니다. 유효한 .docx 파일이 아닐 수 있습니다: {path}",
                    file=sys.stderr,
                )
                return 2
    except zipfile.BadZipFile:
        print(f"[read-docx] 유효한 .docx(zip) 파일이 아닙니다: {path}", file=sys.stderr)
        return 2

    root = ET.fromstring(xml_bytes)
    body = root.find(f"{NS_W}body")
    if body is None:
        print("[read-docx] <w:body> 요소를 찾을 수 없습니다.", file=sys.stderr)
        return 2

    blocks = []
    for child in body:
        tag = child.tag.split("}", 1)[-1]
        if tag == "p":
            rendered = render_paragraph(child)
            if rendered:
                blocks.append(rendered)
        elif tag == "tbl":
            rendered = render_table(child)
            if rendered:
                blocks.append(rendered)
        # sectPr 등은 무시

    print("\n\n".join(blocks))
    return 0


def main() -> int:
    # Windows 콘솔에서 한글 깨짐 방지
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except AttributeError:
        pass

    if len(sys.argv) != 2:
        print('사용법: python extract_docx.py "{docx 파일 경로}"', file=sys.stderr)
        return 2

    return extract(sys.argv[1])


if __name__ == "__main__":
    sys.exit(main())
