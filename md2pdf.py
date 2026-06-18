"""
将 Markdown 文件转换为 PDF（支持中文），使用 reportlab。
用法：python md2pdf.py input.md output.pdf
"""
import re
import sys
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    HRFlowable, Preformatted
)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.enums import TA_LEFT, TA_CENTER

# ── 注册中文字体（使用 Windows 内置字体）──────────────────────────────────────
import os

FONT_PATHS = [
    "C:/Windows/Fonts/msyh.ttc",    # 微软雅黑
    "C:/Windows/Fonts/simsun.ttc",  # 宋体
    "C:/Windows/Fonts/simhei.ttf",  # 黑体
]
FONT_BOLD_PATHS = [
    "C:/Windows/Fonts/msyhbd.ttc",  # 微软雅黑 Bold
    "C:/Windows/Fonts/simhei.ttf",
]

def register_fonts():
    registered = False
    for path in FONT_PATHS:
        if os.path.exists(path):
            try:
                pdfmetrics.registerFont(TTFont("CN", path, subfontIndex=0))
                registered = True
                break
            except Exception:
                pass
    if not registered:
        raise RuntimeError("找不到可用的中文字体，请确认 Windows 字体目录存在 msyh.ttc / simsun.ttc / simhei.ttf")

    registered_bold = False
    for path in FONT_BOLD_PATHS:
        if os.path.exists(path):
            try:
                pdfmetrics.registerFont(TTFont("CN-Bold", path, subfontIndex=0))
                registered_bold = True
                break
            except Exception:
                pass
    if not registered_bold:
        pdfmetrics.registerFont(TTFont("CN-Bold", FONT_PATHS[0], subfontIndex=0))

register_fonts()

# ── 样式定义 ──────────────────────────────────────────────────────────────────
def make_styles():
    base = ParagraphStyle
    styles = {
        "h1": base("h1", fontName="CN-Bold", fontSize=20, leading=28,
                   spaceAfter=10, spaceBefore=16, textColor=colors.HexColor("#1a1a1a")),
        "h2": base("h2", fontName="CN-Bold", fontSize=15, leading=22,
                   spaceAfter=7, spaceBefore=14, textColor=colors.HexColor("#1a1a1a"),
                   borderPadding=(0,0,3,0)),
        "h3": base("h3", fontName="CN-Bold", fontSize=13, leading=19,
                   spaceAfter=5, spaceBefore=10, textColor=colors.HexColor("#333333")),
        "h4": base("h4", fontName="CN-Bold", fontSize=11, leading=17,
                   spaceAfter=4, spaceBefore=8, textColor=colors.HexColor("#444444")),
        "normal": base("normal", fontName="CN", fontSize=10.5, leading=18,
                       spaceAfter=4, spaceBefore=2, textColor=colors.HexColor("#222222")),
        "bullet": base("bullet", fontName="CN", fontSize=10.5, leading=18,
                       spaceAfter=3, spaceBefore=1, leftIndent=14,
                       textColor=colors.HexColor("#222222")),
        "bullet2": base("bullet2", fontName="CN", fontSize=10.5, leading=18,
                        spaceAfter=3, spaceBefore=1, leftIndent=28,
                        textColor=colors.HexColor("#222222")),
        "code": base("code", fontName="Courier", fontSize=8.5, leading=13,
                     backColor=colors.HexColor("#f5f5f5"), leftIndent=8, rightIndent=8,
                     spaceBefore=4, spaceAfter=4,
                     borderColor=colors.HexColor("#dddddd"), borderWidth=0.5,
                     borderPadding=6, textColor=colors.HexColor("#333333")),
        "blockquote": base("blockquote", fontName="CN", fontSize=10, leading=17,
                           leftIndent=16, rightIndent=8, spaceAfter=4, spaceBefore=4,
                           textColor=colors.HexColor("#666666"),
                           borderColor=colors.HexColor("#cccccc"), borderWidth=2,
                           borderPadding=(0,0,0,8)),
    }
    return styles


# ── 内联格式处理（bold / code / 转义 < > &）─────────────────────────────────
def escape_xml(text):
    text = text.replace("&", "&amp;")
    text = text.replace("<", "&lt;")
    text = text.replace(">", "&gt;")
    return text

def inline_format(text):
    """将 Markdown 内联语法转为 ReportLab XML 标签"""
    # 先提取 `code` 片段，用占位符保护，避免被后续正则破坏
    code_chunks = {}
    def save_code(m):
        key = f"\x00CODE{len(code_chunks)}\x00"
        code_chunks[key] = f'<font name="Courier" size="9">{escape_xml(m.group(1))}</font>'
        return key
    text = re.sub(r'`(.+?)`', save_code, text)

    # 转义剩余文本的 XML 特殊字符
    text = escape_xml(text)

    # **bold**
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    text = re.sub(r'__(.+?)__',     r'<b>\1</b>', text)
    # *italic*（不含已处理的 **）
    text = re.sub(r'\*([^*]+?)\*', r'<i>\1</i>', text)
    # _italic_（只匹配非下划线字符，避免误匹配变量名）
    text = re.sub(r'(?<!\w)_([^_]+?)_(?!\w)', r'<i>\1</i>', text)

    # 还原 code 占位符
    for key, val in code_chunks.items():
        text = text.replace(escape_xml(key), val)

    return text


# ── Markdown 解析 → Flowable 列表 ────────────────────────────────────────────
def md_to_flowables(md_text, styles):
    flowables = []
    lines = md_text.splitlines()
    i = 0
    in_code = False
    code_lines = []

    while i < len(lines):
        line = lines[i]

        # 代码块 ```
        if line.strip().startswith("```"):
            if not in_code:
                in_code = True
                code_lines = []
            else:
                in_code = False
                code_content = "\n".join(code_lines)
                # 代码块用 Preformatted（Courier），支持等宽
                pre = Preformatted(code_content, styles["code"])
                flowables.append(pre)
                flowables.append(Spacer(1, 4))
            i += 1
            continue

        if in_code:
            code_lines.append(line)
            i += 1
            continue

        # 空行
        if line.strip() == "":
            flowables.append(Spacer(1, 4))
            i += 1
            continue

        # 分隔线 ---
        if re.match(r'^-{3,}$', line.strip()) or re.match(r'^\*{3,}$', line.strip()):
            flowables.append(HRFlowable(width="100%", thickness=0.5,
                                        color=colors.HexColor("#cccccc"), spaceAfter=6, spaceBefore=6))
            i += 1
            continue

        # 标题
        m = re.match(r'^(#{1,4})\s+(.*)', line)
        if m:
            level = len(m.group(1))
            text = inline_format(m.group(2))
            skey = f"h{level}" if level <= 4 else "h4"
            flowables.append(Paragraph(text, styles[skey]))
            if level <= 2:
                flowables.append(HRFlowable(width="100%", thickness=0.3,
                                            color=colors.HexColor("#e0e0e0"),
                                            spaceAfter=4, spaceBefore=0))
            i += 1
            continue

        # 表格（收集连续的 | 行）
        if line.strip().startswith("|"):
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i])
                i += 1
            # 过滤分隔行（|---|---|）
            data_lines = [l for l in table_lines
                          if not re.match(r'^\s*\|[\s\-\|:]+\|\s*$', l)]
            if data_lines:
                table_data = []
                for tl in data_lines:
                    cells = [c.strip() for c in tl.strip().strip("|").split("|")]
                    cells = [Paragraph(inline_format(c),
                                       ParagraphStyle("tc", fontName="CN", fontSize=9.5,
                                                      leading=14)) for c in cells]
                    table_data.append(cells)
                col_count = max(len(r) for r in table_data)
                col_width = (A4[0] - 40*mm) / col_count
                t = Table(table_data, colWidths=[col_width]*col_count, repeatRows=1)
                t.setStyle(TableStyle([
                    ("BACKGROUND",  (0,0), (-1,0),  colors.HexColor("#4472C4")),
                    ("TEXTCOLOR",   (0,0), (-1,0),  colors.white),
                    ("FONTNAME",    (0,0), (-1,0),  "CN-Bold"),
                    ("FONTSIZE",    (0,0), (-1,0),  9.5),
                    ("ROWBACKGROUNDS", (0,1), (-1,-1),
                     [colors.HexColor("#f7f9fc"), colors.white]),
                    ("GRID",        (0,0), (-1,-1),  0.4, colors.HexColor("#c0c0c0")),
                    ("VALIGN",      (0,0), (-1,-1),  "TOP"),
                    ("TOPPADDING",  (0,0), (-1,-1),  5),
                    ("BOTTOMPADDING",(0,0),(-1,-1),  5),
                    ("LEFTPADDING", (0,0), (-1,-1),  6),
                    ("RIGHTPADDING",(0,0), (-1,-1),  6),
                ]))
                flowables.append(t)
                flowables.append(Spacer(1, 6))
            continue

        # 引用 >
        if line.strip().startswith(">"):
            text = inline_format(line.strip().lstrip(">").strip())
            flowables.append(Paragraph(text, styles["blockquote"]))
            i += 1
            continue

        # 二级列表  （两空格或四空格 + - / *）
        m = re.match(r'^(?:  {2,}|\t)[-*+]\s+(.*)', line)
        if m:
            text = "· " + inline_format(m.group(1))
            flowables.append(Paragraph(text, styles["bullet2"]))
            i += 1
            continue

        # 一级列表 - / * / +
        m = re.match(r'^[-*+]\s+(.*)', line)
        if m:
            text = "• " + inline_format(m.group(1))
            flowables.append(Paragraph(text, styles["bullet"]))
            i += 1
            continue

        # 有序列表
        m = re.match(r'^\d+\.\s+(.*)', line)
        if m:
            text = inline_format(m.group(1))
            flowables.append(Paragraph(text, styles["bullet"]))
            i += 1
            continue

        # 普通段落
        text = inline_format(line.strip())
        if text:
            flowables.append(Paragraph(text, styles["normal"]))
        i += 1

    return flowables


# ── 主程序 ────────────────────────────────────────────────────────────────────
def convert(md_path, pdf_path):
    with open(md_path, encoding="utf-8") as f:
        md_text = f.read()

    styles = make_styles()

    doc = SimpleDocTemplate(
        pdf_path,
        pagesize=A4,
        leftMargin=20*mm, rightMargin=20*mm,
        topMargin=18*mm, bottomMargin=18*mm,
        title=os.path.basename(md_path),
    )

    flowables = md_to_flowables(md_text, styles)
    doc.build(flowables)
    print(f"生成完成：{pdf_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("用法：python md2pdf.py input.md output.pdf")
        sys.exit(1)
    convert(sys.argv[1], sys.argv[2])
