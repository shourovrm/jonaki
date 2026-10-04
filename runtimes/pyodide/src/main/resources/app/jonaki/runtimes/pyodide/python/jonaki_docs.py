"""Ready-made helpers for writing Word, Excel and PowerPoint files in Jonaki.

Three builders share one set of themes, so a report, its workbook and its
slides look like one piece of work:

    from jonaki_docs import WordDoc, Sheets, Deck, html_to_docx

    doc = WordDoc(theme="leaf")
    doc.title("Solar payback", subtitle="5 October 2026")
    doc.heading("Costs")
    doc.paragraph("A 3 kW system costs **2.4 lakh taka** [1].")
    doc.table([["Size", "Cost"], ["3 kW", 240000]])
    doc.chart("bar", ["2024", "2025"], {"Units": [120, 180]}, title="Sales")
    doc.save("artifacts/solar.docx")

    html_to_docx("artifacts/solar.html", "artifacts/solar.docx")

Text arguments accept a small inline Markdown: **bold**, *italic*, `code`
and [text](https://link). A theme is a name ("leaf", "plain", "formal"), a
Theme, or a dict of the fields to change. `template=` takes a .docx or
.pptx whose own styles and layouts are then used instead of the theme, which
is the way to match an organisation's house style exactly; the template's
sample text and slides are left out.

The underlying libraries stay available for anything not covered here:
python-docx (`doc.document`), openpyxl (`sheets.workbook`) and python-pptx
(`deck.presentation`).
"""

import base64
import io
import json
import os
import re
from dataclasses import dataclass, replace

__version__ = "1"

__all__ = [
    "Theme", "THEMES", "theme_of", "WordDoc", "Sheets", "Deck",
    "html_to_docx", "html_to_pptx", "html_tables_to_xlsx", "chart_image",
]


# ---------------------------------------------------------------------------
# Themes
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class Theme:
    """Fonts and colours shared by the three builders. Colours are "RRGGBB"."""

    name: str
    # Fonts are names only; the reader's program substitutes one it has.
    body_font: str = "Calibri"
    heading_font: str = "Calibri"
    # Word and PowerPoint draw Bangla with the "complex script" font.
    bangla_font: str = "Noto Sans Bengali"
    mono_font: str = "Consolas"
    body_pt: float = 11
    text: str = "1B1F16"
    heading: str = "1B1F16"
    accent: str = "4E6B12"
    muted: str = "5F6852"
    table_header_fill: str = "34420F"
    table_header_text: str = "FFFFFF"
    table_band_fill: str = "F3F6EA"
    table_border: str = "C9CFBD"
    callout_fill: str = "F3F6EA"
    slide_background: str = "FFFFFF"
    slide_title: str = "1B1F16"
    # Series colours of charts, used in order.
    chart_colors: tuple = ("4E6B12", "C98A1B", "2E6A5A", "9A4A14", "285C8E", "7A3E9D")


THEMES = {
    # Jonaki's own Leaf colours, darkened for paper.
    "leaf": Theme(name="leaf"),
    # Black and grey only, for forms and anything that will be photocopied.
    "plain": Theme(
        name="plain", accent="333333", heading="000000", text="000000", muted="555555",
        table_header_fill="E6E6E6", table_header_text="000000", table_band_fill="F7F7F7",
        table_border="9A9A9A", callout_fill="F2F2F2", slide_title="000000",
        chart_colors=("333333", "777777", "AAAAAA", "555555", "999999", "CCCCCC"),
    ),
    # Navy with serif headings, for letters, proposals and academic work.
    "formal": Theme(
        name="formal", heading_font="Cambria", accent="1F3A5F", heading="1F3A5F", text="1A1A1A",
        muted="5A6470", table_header_fill="1F3A5F", table_header_text="FFFFFF",
        table_band_fill="EEF2F7", table_border="B8C2CF", callout_fill="EEF2F7", slide_title="1F3A5F",
        chart_colors=("1F3A5F", "B5651D", "3F7D6B", "8C2F39", "6B5B95", "7A7A7A"),
    ),
}


def theme_of(theme):
    """A Theme from a name, a Theme, or a dict of changes to "leaf" (or to its "base")."""
    if isinstance(theme, Theme):
        return theme
    if theme is None:
        return THEMES["leaf"]
    if isinstance(theme, str):
        if theme not in THEMES:
            raise ValueError("unknown theme %r; use one of %s" % (theme, ", ".join(sorted(THEMES))))
        return THEMES[theme]
    if isinstance(theme, dict):
        changes = dict(theme)
        base = theme_of(changes.pop("base", "leaf"))
        changes.setdefault("name", "custom")
        if "chart_colors" in changes:
            changes["chart_colors"] = tuple(changes["chart_colors"])
        return replace(base, **changes)
    raise TypeError("theme must be a name, a Theme or a dict")


# ---------------------------------------------------------------------------
# Inline Markdown
# ---------------------------------------------------------------------------

@dataclass
class _Span:
    text: str
    bold: bool = False
    italic: bool = False
    code: bool = False
    url: str = None
    underline: bool = False
    superscript: bool = False
    subscript: bool = False


_INLINE = re.compile(
    r"(\*\*(?P<bold>.+?)\*\*)"
    r"|(\*(?P<italic>[^*\s][^*]*?)\*)"
    r"|(`(?P<code>[^`]+?)`)"
    r"|(\[(?P<label>[^\]]+?)\]\((?P<url>[^)\s]+?)\))"
)


def _spans_of(text):
    """Splits "a **b** c" into spans. A list of spans or (text, style) pairs passes through."""
    if text is None:
        return []
    if isinstance(text, _Span):
        return [text]
    if isinstance(text, (list, tuple)):
        spans = []
        for part in text:
            spans.extend(_spans_of(part))
        return spans
    text = str(text)
    spans = []
    position = 0
    for match in _INLINE.finditer(text):
        if match.start() > position:
            spans.append(_Span(text[position:match.start()]))
        if match.group("bold") is not None:
            spans.append(_Span(match.group("bold"), bold=True))
        elif match.group("italic") is not None:
            spans.append(_Span(match.group("italic"), italic=True))
        elif match.group("code") is not None:
            spans.append(_Span(match.group("code"), code=True))
        else:
            spans.append(_Span(match.group("label"), url=match.group("url")))
        position = match.end()
    if position < len(text):
        spans.append(_Span(text[position:]))
    return spans


def _plain(text):
    return "".join(span.text for span in _spans_of(text))


def _looks_numeric(value):
    if isinstance(value, bool):
        return False
    if isinstance(value, (int, float)):
        return True
    return bool(re.fullmatch(r"\s*[-+]?[\d,]*\.?\d+\s*%?\s*", str(value))) and any(ch.isdigit() for ch in str(value))


def _cell_text(value):
    """What a table cell shows: numbers get thousands separators, as a reader expects in a document."""
    if value is None:
        return ""
    if isinstance(value, bool):
        return str(value)
    if isinstance(value, int) or (isinstance(value, float) and value.is_integer()):
        return "{:,}".format(int(value))
    if isinstance(value, float):
        return "{:,}".format(value)
    return value if isinstance(value, (str, list, tuple, _Span)) else str(value)


# ---------------------------------------------------------------------------
# Chart pictures (for Word, which has no chart of its own in python-docx)
# ---------------------------------------------------------------------------

def _rgb(hex_color):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[index:index + 2], 16) for index in (0, 2, 4))


def _font(size):
    from PIL import ImageFont
    try:
        return ImageFont.load_default(size)
    except TypeError:
        # An older Pillow has only the small bitmap font.
        return ImageFont.load_default()


def _nice_ceiling(value):
    """The next "round" number at or above value: 1, 2, 2.5, 5 or 10 times a power of ten."""
    if value <= 0:
        return 1.0
    power = 10 ** len(str(int(value))) / 10 if value >= 1 else 10 ** -len(str(int(1 / value)))
    for step in (1, 2, 2.5, 5, 10):
        if step * power >= value:
            return step * power
    return 10 * power


def _number_label(value):
    if abs(value) >= 1000 or float(value).is_integer():
        return "{:,.0f}".format(value)
    return "{:,.1f}".format(value)


def chart_image(kind, categories, series, title=None, theme="leaf", width=1600, height=900):
    """A bar or line chart as PNG bytes. `series` maps a name to its values.

    The labels use Pillow's built-in font, which has Latin letters only; give
    Bangla charts English labels, or use a native chart in Excel or PowerPoint.
    """
    from PIL import Image, ImageDraw

    if kind not in ("bar", "line"):
        raise ValueError("chart kind must be 'bar' or 'line' for a picture; pie charts exist in Sheets and Deck")
    theme = theme_of(theme)
    categories = [str(category) for category in categories]
    names = list(series)
    values = [[float(value) for value in series[name]] for name in names]
    for name, row in zip(names, values):
        if len(row) != len(categories):
            raise ValueError("series %r has %d values for %d categories" % (name, len(row), len(categories)))

    image = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(image)
    label_font = _font(int(height * 0.036))
    title_font = _font(int(height * 0.052))
    text_color = _rgb(theme.text)
    muted = _rgb(theme.muted)
    grid = _rgb(theme.table_border)

    top = int(height * 0.08)
    if title:
        draw.text((int(width * 0.06), int(height * 0.035)), str(title), font=title_font, fill=text_color)
        top = int(height * 0.16)
    show_legend = len(names) > 1
    bottom = height - int(height * (0.22 if show_legend else 0.13))
    left = int(width * 0.10)
    right = width - int(width * 0.04)

    highest = _nice_ceiling(max([max(row) for row in values] + [0.0]))
    lowest = min([min(row) for row in values] + [0.0])
    lowest = -_nice_ceiling(-lowest) if lowest < 0 else 0.0
    span = (highest - lowest) or 1.0

    def y_of(value):
        return bottom - (value - lowest) / span * (bottom - top)

    for step in range(5):
        value = lowest + span * step / 4
        y = y_of(value)
        draw.line([(left, y), (right, y)], fill=grid, width=2)
        label = _number_label(value)
        label_width = draw.textlength(label, font=label_font)
        draw.text((left - label_width - 14, y - label_font.size * 0.6), label, font=label_font, fill=muted)

    slot = (right - left) / max(len(categories), 1)
    for index, category in enumerate(categories):
        label_width = draw.textlength(category, font=label_font)
        draw.text((left + slot * (index + 0.5) - label_width / 2, bottom + 14), category, font=label_font, fill=muted)

    colors = [_rgb(color) for color in theme.chart_colors]
    if kind == "bar":
        group_width = slot * 0.7
        bar_width = group_width / len(names)
        for series_index, row in enumerate(values):
            color = colors[series_index % len(colors)]
            for index, value in enumerate(row):
                x = left + slot * index + (slot - group_width) / 2 + bar_width * series_index
                draw.rectangle([x + 2, min(y_of(value), y_of(0)), x + bar_width - 2, max(y_of(value), y_of(0))], fill=color)
    else:
        for series_index, row in enumerate(values):
            color = colors[series_index % len(colors)]
            points = [(left + slot * (index + 0.5), y_of(value)) for index, value in enumerate(row)]
            draw.line(points, fill=color, width=6, joint="curve")
            for x, y in points:
                draw.ellipse([x - 9, y - 9, x + 9, y + 9], fill=color)
    draw.line([(left, y_of(0)), (right, y_of(0))], fill=muted, width=3)

    if show_legend:
        x = left
        y = height - int(height * 0.075)
        box = int(label_font.size * 0.8)
        for series_index, name in enumerate(names):
            draw.rectangle([x, y, x + box, y + box], fill=colors[series_index % len(colors)])
            draw.text((x + box + 10, y - box * 0.2), str(name), font=label_font, fill=text_color)
            x += box + 10 + draw.textlength(str(name), font=label_font) + 40

    output = io.BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


# ---------------------------------------------------------------------------
# Word
# ---------------------------------------------------------------------------

_PAGE_SIZES_CM = {"a4": (21.0, 29.7), "letter": (21.59, 27.94)}


class WordDoc:
    """Builds a .docx. Every method returns the paragraph or table it added."""

    def __init__(self, theme="leaf", template=None, page="a4", landscape=False, margins_cm=2.2, keep_template_content=False):
        from docx import Document
        from docx.oxml.ns import qn
        from docx.shared import Cm

        self.theme = theme_of(theme)
        self.uses_template = template is not None
        self.document = Document(template) if template else Document()
        if self.uses_template and not keep_template_content:
            # A template is wanted for its styles, header and footer; its sample text would
            # otherwise stay above the new content. The last element holds the page setup.
            body = self.document.element.body
            for element in list(body):
                if element.tag != qn("w:sectPr"):
                    body.remove(element)
        if not self.uses_template:
            if page not in _PAGE_SIZES_CM:
                raise ValueError("page must be 'a4' or 'letter'")
            width, height = _PAGE_SIZES_CM[page]
            if landscape:
                width, height = height, width
            section = self.document.sections[0]
            section.page_width, section.page_height = Cm(width), Cm(height)
            section.left_margin = section.right_margin = Cm(margins_cm)
            section.top_margin = section.bottom_margin = Cm(margins_cm)
            self._style_document()

    # -- styles ------------------------------------------------------------

    def _set_fonts(self, run_properties_owner, latin):
        """Sets the Latin font and the Bangla (complex script) font on a run or a style."""
        from docx.oxml.ns import qn

        element = run_properties_owner.element if hasattr(run_properties_owner, "element") else run_properties_owner._element
        properties = element.get_or_add_rPr()
        fonts = properties.find(qn("w:rFonts"))
        if fonts is None:
            fonts = properties.makeelement(qn("w:rFonts"), {})
            properties.insert(0, fonts)
        fonts.set(qn("w:ascii"), latin)
        fonts.set(qn("w:hAnsi"), latin)
        fonts.set(qn("w:cs"), self.theme.bangla_font)
        fonts.set(qn("w:eastAsia"), latin)

    def _style_document(self):
        from docx.shared import Pt, RGBColor

        theme = self.theme
        styles = self.document.styles
        normal = styles["Normal"]
        normal.font.size = Pt(theme.body_pt)
        normal.font.color.rgb = RGBColor.from_string(theme.text)
        normal.paragraph_format.space_after = Pt(theme.body_pt * 0.7)
        normal.paragraph_format.line_spacing = 1.25
        self._set_fonts(normal, theme.body_font)

        heading_sizes = {"Title": 26, "Heading 1": 18, "Heading 2": 14, "Heading 3": 12, "Heading 4": 11}
        for style_name, size in heading_sizes.items():
            style = styles[style_name]
            style.font.size = Pt(size)
            style.font.bold = style_name != "Title"
            style.font.italic = False
            style.font.color.rgb = RGBColor.from_string(theme.heading if style_name != "Heading 2" else theme.accent)
            self._set_fonts(style, theme.heading_font)
            style.paragraph_format.space_before = Pt(0 if style_name == "Title" else size * 1.1)
            style.paragraph_format.space_after = Pt(size * 0.4)
            style.paragraph_format.keep_with_next = True
            # The default template draws a blue rule under the title.
            paragraph_properties = style.element.pPr
            if paragraph_properties is not None:
                for border in paragraph_properties.findall("{http://schemas.openxmlformats.org/wordprocessingml/2006/main}pBdr"):
                    paragraph_properties.remove(border)
        subtitle = styles["Subtitle"]
        subtitle.font.size = Pt(12)
        subtitle.font.italic = False
        subtitle.font.color.rgb = RGBColor.from_string(theme.muted)
        self._set_fonts(subtitle, theme.body_font)
        caption = styles["Caption"]
        caption.font.size = Pt(9)
        caption.font.bold = False
        caption.font.color.rgb = RGBColor.from_string(theme.muted)
        self._set_fonts(caption, theme.body_font)

    # -- text --------------------------------------------------------------

    def _add_hyperlink(self, paragraph, span):
        from docx.opc.constants import RELATIONSHIP_TYPE
        from docx.oxml.ns import qn

        relation_id = paragraph.part.relate_to(span.url, RELATIONSHIP_TYPE.HYPERLINK, is_external=True)
        hyperlink = paragraph._p.makeelement(qn("w:hyperlink"), {qn("r:id"): relation_id})
        run = paragraph.add_run(span.text)
        self._format_run(run, span, is_link=True)
        hyperlink.append(run._r)
        paragraph._p.append(hyperlink)

    def _format_run(self, run, span, is_link=False):
        from docx.shared import RGBColor

        run.bold = True if span.bold else None
        run.italic = True if span.italic else None
        if span.underline or is_link:
            run.underline = True
        if span.superscript:
            run.font.superscript = True
        if span.subscript:
            run.font.subscript = True
        if span.code:
            self._set_fonts(run, self.theme.mono_font)
        if is_link:
            run.font.color.rgb = RGBColor.from_string(self.theme.accent)

    def _fill(self, paragraph, text):
        for span in _spans_of(text):
            if not span.text:
                continue
            if span.url:
                self._add_hyperlink(paragraph, span)
            else:
                self._format_run(paragraph.add_run(span.text), span)
        return paragraph

    def title(self, text, subtitle=None):
        paragraph = self._fill(self.document.add_paragraph(style="Title"), text)
        if subtitle:
            self._fill(self.document.add_paragraph(style="Subtitle"), subtitle)
        return paragraph

    def heading(self, text, level=1):
        level = max(1, min(int(level), 4))
        return self._fill(self.document.add_paragraph(style="Heading %d" % level), text)

    def paragraph(self, text, align=None, style=None):
        paragraph = self._fill(self.document.add_paragraph(style=style), text)
        if align:
            from docx.enum.text import WD_ALIGN_PARAGRAPH
            paragraph.alignment = {
                "left": WD_ALIGN_PARAGRAPH.LEFT, "center": WD_ALIGN_PARAGRAPH.CENTER,
                "right": WD_ALIGN_PARAGRAPH.RIGHT, "justify": WD_ALIGN_PARAGRAPH.JUSTIFY,
            }[align]
        return paragraph

    def _list(self, items, style_base, level):
        from docx.shared import Pt

        added = []
        for item in items:
            if isinstance(item, (list, tuple)) and not all(isinstance(part, (_Span, str)) and not isinstance(part, list) for part in item):
                added.extend(self._list(item, style_base, level + 1))
                continue
            if isinstance(item, list):
                added.extend(self._list(item, style_base, level + 1))
                continue
            style = style_base if level == 1 else "%s %d" % (style_base, min(level, 3))
            paragraph = self._fill(self.document.add_paragraph(style=style), item)
            paragraph.paragraph_format.space_after = Pt(2)
            added.append(paragraph)
        return added

    def bullets(self, items):
        """A bulleted list. A list inside the list is one level deeper."""
        return self._list(items, "List Bullet", 1)

    def numbered(self, items):
        return self._list(items, "List Number", 1)

    def quote(self, text):
        from docx.shared import Cm, RGBColor

        paragraph = self._fill(self.document.add_paragraph(), text)
        paragraph.paragraph_format.left_indent = Cm(1)
        for run in paragraph.runs:
            run.italic = True
            run.font.color.rgb = RGBColor.from_string(self.theme.muted)
        return paragraph

    def code(self, text):
        """A block of code or other fixed-width text, on a tinted background."""
        from docx.shared import Pt

        table = self.document.add_table(rows=1, cols=1)
        cell = table.cell(0, 0)
        self._shade(cell, self.theme.callout_fill)
        lines = str(text).rstrip("\n").split("\n")
        paragraph = cell.paragraphs[0]
        for index, line in enumerate(lines):
            run = paragraph.add_run(line)
            self._set_fonts(run, self.theme.mono_font)
            run.font.size = Pt(max(self.theme.body_pt - 2, 8))
            if index < len(lines) - 1:
                run.add_break()
        paragraph.paragraph_format.space_after = Pt(0)
        self.document.add_paragraph()
        return table

    def callout(self, text, title=None):
        """A tinted box with an accent bar on its left, for a key finding or a warning."""
        from docx.shared import Pt

        # A table has no space of its own above it, so the box would touch the text before it.
        self.document.add_paragraph().paragraph_format.space_after = Pt(0)
        table = self.document.add_table(rows=1, cols=1)
        cell = table.cell(0, 0)
        # Word reads a cell's borders before its shading; the other order is not valid.
        self._borders(cell, left=(self.theme.accent, 24))
        self._shade(cell, self.theme.callout_fill)
        paragraph = cell.paragraphs[0]
        if title:
            self._fill(paragraph, _Span(_plain(title), bold=True))
            paragraph.paragraph_format.space_after = Pt(2)
            paragraph = cell.add_paragraph()
        self._fill(paragraph, text)
        paragraph.paragraph_format.space_after = Pt(0)
        self.document.add_paragraph()
        return table

    def page_break(self):
        self.document.add_page_break()

    def contents(self, levels=3):
        """A table of contents. Word fills it in when the file is opened and the field updated."""
        paragraph = self.document.add_paragraph()
        self._field(paragraph, 'TOC \\o "1-%d" \\h \\z \\u' % levels, placeholder="Update this field to build the contents.")
        return paragraph

    def footer(self, text=None, page_numbers=True):
        """Text at the foot of every page, with "Page 3" on the right when page_numbers is true."""
        from docx.enum.text import WD_ALIGN_PARAGRAPH
        from docx.shared import Pt, RGBColor

        paragraph = self.document.sections[0].footer.paragraphs[0]
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        if text:
            paragraph.add_run(_plain(text) + ("   ·   " if page_numbers else ""))
        if page_numbers:
            self._field(paragraph, "PAGE", placeholder="1")
        for run in paragraph.runs:
            run.font.size = Pt(9)
            run.font.color.rgb = RGBColor.from_string(self.theme.muted)
        return paragraph

    def _field(self, paragraph, instruction, placeholder=""):
        from docx.oxml.ns import qn

        def mark(kind):
            run = paragraph.add_run()
            element = run._r.makeelement(qn("w:fldChar"), {qn("w:fldCharType"): kind})
            run._r.append(element)

        mark("begin")
        instruction_run = paragraph.add_run()
        instruction_element = instruction_run._r.makeelement(qn("w:instrText"), {})
        instruction_element.text = instruction
        instruction_element.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        instruction_run._r.append(instruction_element)
        mark("separate")
        paragraph.add_run(placeholder)
        mark("end")

    # -- tables ------------------------------------------------------------

    def _shade(self, cell, fill):
        from docx.oxml.ns import qn

        properties = cell._tc.get_or_add_tcPr()
        shading = properties.makeelement(qn("w:shd"), {qn("w:val"): "clear", qn("w:color"): "auto", qn("w:fill"): fill})
        properties.append(shading)

    def _borders(self, cell, **edges):
        """edges: left/right/top/bottom=(color, eighths of a point)."""
        from docx.oxml.ns import qn

        properties = cell._tc.get_or_add_tcPr()
        borders = properties.makeelement(qn("w:tcBorders"), {})
        for edge, (color, size) in edges.items():
            borders.append(borders.makeelement(qn("w:" + edge), {
                qn("w:val"): "single", qn("w:sz"): str(size), qn("w:space"): "0", qn("w:color"): color,
            }))
        properties.append(borders)

    def table(self, rows, header=True, widths_cm=None, caption=None, band=True):
        """A table from a list of rows. Numbers are right-aligned; the header row repeats on each page."""
        from docx.enum.table import WD_TABLE_ALIGNMENT
        from docx.enum.text import WD_ALIGN_PARAGRAPH
        from docx.oxml.ns import qn
        from docx.shared import Cm, Pt, RGBColor

        rows = [list(row) for row in rows]
        if not rows:
            raise ValueError("a table needs at least one row")
        column_count = max(len(row) for row in rows)
        table = self.document.add_table(rows=len(rows), cols=column_count)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        theme = self.theme
        if not self.uses_template:
            table.style = "Table Grid"
        for row_index, row in enumerate(rows):
            is_header = header and row_index == 0
            for column_index in range(column_count):
                value = row[column_index] if column_index < len(row) else ""
                cell = table.cell(row_index, column_index)
                paragraph = cell.paragraphs[0]
                paragraph.paragraph_format.space_after = Pt(0)
                self._fill(paragraph, _cell_text(value))
                if _looks_numeric(value) and not is_header:
                    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
                if self.uses_template:
                    continue
                edge = (theme.table_border, 4)
                self._borders(cell, top=edge, bottom=edge, left=edge, right=edge)
                if is_header:
                    self._shade(cell, theme.table_header_fill)
                    for run in paragraph.runs:
                        run.bold = True
                        run.font.color.rgb = RGBColor.from_string(theme.table_header_text)
                elif band and row_index % 2 == 0:
                    self._shade(cell, theme.table_band_fill)
        if header:
            row_properties = table.rows[0]._tr.get_or_add_trPr()
            row_properties.append(row_properties.makeelement(qn("w:tblHeader"), {}))
        if widths_cm:
            for column_index, width in enumerate(widths_cm):
                for row in table.rows:
                    row.cells[column_index].width = Cm(width)
        if caption:
            self._fill(self.document.add_paragraph(style="Caption"), caption).alignment = WD_ALIGN_PARAGRAPH.CENTER
        else:
            self.document.add_paragraph().paragraph_format.space_after = Pt(0)
        return table

    # -- pictures ----------------------------------------------------------

    def _usable_width_cm(self):
        section = self.document.sections[0]
        return (section.page_width - section.left_margin - section.right_margin) / 360000

    def image(self, source, width_cm=None, caption=None):
        """A picture from a file path or from bytes, centred, never wider than the text."""
        from docx.enum.text import WD_ALIGN_PARAGRAPH
        from docx.shared import Cm

        stream = io.BytesIO(source) if isinstance(source, (bytes, bytearray)) else source
        width = min(width_cm or self._usable_width_cm(), self._usable_width_cm())
        self.document.add_picture(stream, width=Cm(width))
        picture_paragraph = self.document.paragraphs[-1]
        picture_paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        if caption:
            self._fill(self.document.add_paragraph(style="Caption"), caption).alignment = WD_ALIGN_PARAGRAPH.CENTER
        return picture_paragraph

    def chart(self, kind, categories, series, title=None, caption=None, width_cm=None):
        """A bar or line chart, drawn as a picture (see chart_image)."""
        return self.image(chart_image(kind, categories, series, title=title, theme=self.theme), width_cm=width_cm, caption=caption)

    def save(self, path):
        _make_parent(path)
        self.document.save(path)
        return path


def _make_parent(path):
    folder = os.path.dirname(str(path))
    if folder:
        os.makedirs(folder, exist_ok=True)


# ---------------------------------------------------------------------------
# Excel
# ---------------------------------------------------------------------------

class Sheets:
    """Builds an .xlsx with styled header rows, sensible column widths and native charts."""

    def __init__(self, theme="leaf"):
        from openpyxl import Workbook

        self.theme = theme_of(theme)
        self.workbook = Workbook()
        self._has_first_sheet = False

    def sheet(self, name, rows, header=True, number_formats=None, totals=None, freeze=True, filter=True, widths=None):
        """Adds a sheet from a list of rows and returns it.

        number_formats maps a column (1-based number or header text) to an Excel
        format such as "#,##0.00" or "0%". totals maps a column to "sum",
        "average", "min", "max" or "count" and adds a formula row at the end.
        """
        from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
        from openpyxl.utils import get_column_letter

        rows = [list(row) for row in rows]
        if not rows:
            raise ValueError("a sheet needs at least one row")
        title = re.sub(r"[\[\]:*?/\\]", " ", str(name))[:31] or "Sheet"
        if self._has_first_sheet:
            worksheet = self.workbook.create_sheet(title)
        else:
            worksheet = self.workbook.active
            worksheet.title = title
            self._has_first_sheet = True

        theme = self.theme
        for row in rows:
            worksheet.append([value if not isinstance(value, (list, tuple, _Span)) else _plain(value) for value in row])
        column_count = max(len(row) for row in rows)
        first_data_row = 2 if header else 1
        last_data_row = len(rows)

        def column_number(key):
            if isinstance(key, int):
                return key
            if header and key in rows[0]:
                return rows[0].index(key) + 1
            raise ValueError("no column %r in the header row" % (key,))

        side = Side(style="thin", color=theme.table_border)
        border = Border(left=side, right=side, top=side, bottom=side)
        body_font = Font(name=theme.body_font, size=theme.body_pt, color=theme.text)
        for row_cells in worksheet.iter_rows(min_row=1, max_row=last_data_row, max_col=column_count):
            for cell in row_cells:
                cell.border = border
                cell.font = body_font
                cell.alignment = Alignment(vertical="top", wrap_text=isinstance(cell.value, str) and len(cell.value) > 60)
        if header:
            for cell in worksheet[1][:column_count]:
                cell.font = Font(name=theme.body_font, size=theme.body_pt, bold=True, color=theme.table_header_text)
                cell.fill = PatternFill("solid", fgColor=theme.table_header_fill)
                cell.alignment = Alignment(vertical="center", wrap_text=True)

        for key, number_format in (number_formats or {}).items():
            letter = get_column_letter(column_number(key))
            for row_index in range(first_data_row, last_data_row + 1):
                worksheet["%s%d" % (letter, row_index)].number_format = number_format

        if totals:
            total_row = last_data_row + 1
            functions = {"sum": "SUM", "average": "AVERAGE", "min": "MIN", "max": "MAX", "count": "COUNT"}
            worksheet.cell(row=total_row, column=1, value="Total").font = Font(name=theme.body_font, bold=True)
            for key, function in totals.items():
                number = column_number(key)
                letter = get_column_letter(number)
                cell = worksheet.cell(
                    row=total_row, column=number,
                    value="=%s(%s%d:%s%d)" % (functions[function.lower()], letter, first_data_row, letter, last_data_row),
                )
                cell.font = Font(name=theme.body_font, bold=True)
                cell.number_format = worksheet["%s%d" % (letter, last_data_row)].number_format
            for cell in worksheet[total_row][:column_count]:
                cell.border = Border(top=Side(style="medium", color=theme.accent))

        for column_index in range(1, column_count + 1):
            letter = get_column_letter(column_index)
            if widths and column_index - 1 < len(widths) and widths[column_index - 1]:
                worksheet.column_dimensions[letter].width = widths[column_index - 1]
                continue
            longest = 0
            for row in rows:
                if column_index - 1 < len(row) and row[column_index - 1] is not None:
                    longest = max(longest, max(len(line) for line in _plain(_cell_text(row[column_index - 1])).split("\n")))
            worksheet.column_dimensions[letter].width = min(max(longest + 3, 8), 60)

        if header and freeze:
            worksheet.freeze_panes = "A2"
        if header and filter and last_data_row > 1:
            worksheet.auto_filter.ref = "A1:%s%d" % (get_column_letter(column_count), last_data_row)
        return worksheet

    def chart(self, worksheet, kind, title=None, categories_column=1, value_columns=None, anchor=None, first_row=1, last_row=None):
        """A native Excel chart over a sheet's columns. kind is "bar", "line" or "pie".

        The header row names the series. value_columns defaults to every column
        after the categories; the chart is placed to the right of the data.
        """
        from openpyxl.chart import BarChart, LineChart, PieChart, Reference
        from openpyxl.utils import get_column_letter

        if isinstance(worksheet, str):
            worksheet = self.workbook[worksheet]
        kinds = {"bar": BarChart, "line": LineChart, "pie": PieChart}
        if kind not in kinds:
            raise ValueError("chart kind must be 'bar', 'line' or 'pie'")
        if last_row is None:
            last_row = worksheet.max_row
            # A totals row is not a category.
            if str(worksheet.cell(row=last_row, column=1).value) == "Total":
                last_row -= 1
        value_columns = value_columns or list(range(categories_column + 1, worksheet.max_column + 1))
        chart = kinds[kind]()
        chart.title = title
        chart.height, chart.width = 8.5, 16
        for column in value_columns:
            chart.add_data(Reference(worksheet, min_col=column, min_row=first_row, max_row=last_row), titles_from_data=True)
        chart.set_categories(Reference(worksheet, min_col=categories_column, min_row=first_row + 1, max_row=last_row))
        if kind != "pie":
            for index, series in enumerate(chart.series):
                color = self.theme.chart_colors[index % len(self.theme.chart_colors)]
                series.graphicalProperties.line.solidFill = color
                if kind == "bar":
                    series.graphicalProperties.solidFill = color
        worksheet.add_chart(chart, anchor or "%s2" % get_column_letter(worksheet.max_column + 2))
        return chart

    def save(self, path):
        _make_parent(path)
        self.workbook.save(path)
        return path


# ---------------------------------------------------------------------------
# PowerPoint
# ---------------------------------------------------------------------------

class Deck:
    """Builds a .pptx. Without a template every slide is drawn in the theme on a 16:9 page;
    with one, the template's own layouts and fonts are used."""

    def __init__(self, theme="leaf", template=None, keep_template_slides=False):
        from pptx import Presentation
        from pptx.util import Inches

        self.theme = theme_of(theme)
        self.uses_template = template is not None
        self.presentation = Presentation(template) if template else Presentation()
        if self.uses_template and not keep_template_slides:
            # A template is wanted for its layouts; its own slides would come before the new ones.
            slide_ids = self.presentation.slides._sldIdLst
            for slide_id in list(slide_ids):
                self.presentation.part.drop_rel(slide_id.rId)
                slide_ids.remove(slide_id)
        if not self.uses_template:
            self.presentation.slide_width = Inches(13.333)
            self.presentation.slide_height = Inches(7.5)
        self.width = self.presentation.slide_width
        self.height = self.presentation.slide_height
        self.margin = int(self.width * 0.06)

    # -- drawing helpers ---------------------------------------------------

    def _blank(self):
        layouts = self.presentation.slide_layouts
        # Layout 6 of the built-in template is "Blank".
        slide = self.presentation.slides.add_slide(layouts[6] if len(layouts) > 6 else layouts[-1])
        from pptx.dml.color import RGBColor
        slide.background.fill.solid()
        slide.background.fill.fore_color.rgb = RGBColor.from_string(self.theme.slide_background)
        return slide

    def _style_run(self, run, span, size, color, bold=False, font=None):
        from pptx.dml.color import RGBColor
        from pptx.oxml.ns import qn
        from pptx.util import Pt

        run.font.size = Pt(size)
        run.font.bold = bool(bold or span.bold)
        run.font.italic = bool(span.italic)
        run.font.name = self.theme.mono_font if span.code else (font or self.theme.body_font)
        run.font.color.rgb = RGBColor.from_string(self.theme.accent if span.url else color)
        # Bangla is drawn with the complex-script font. These two follow the Latin font and
        # come before a link in the run's properties, so the link is added after them.
        properties = run._r.get_or_add_rPr()
        for tag in ("a:ea", "a:cs"):
            element = properties.find(qn(tag))
            if element is None:
                element = properties.makeelement(qn(tag), {})
                properties.append(element)
            element.set("typeface", self.theme.bangla_font if tag == "a:cs" else (font or self.theme.body_font))
        if span.url:
            run.hyperlink.address = span.url

    def _text(self, slide, left, top, width, height, text, size, color=None, bold=False, font=None, align=None, anchor="top"):
        from pptx.enum.text import MSO_ANCHOR, PP_ALIGN

        box = slide.shapes.add_textbox(left, top, width, height)
        frame = box.text_frame
        frame.word_wrap = True
        frame.vertical_anchor = {"top": MSO_ANCHOR.TOP, "middle": MSO_ANCHOR.MIDDLE, "bottom": MSO_ANCHOR.BOTTOM}[anchor]
        paragraph = frame.paragraphs[0]
        if align:
            paragraph.alignment = {"left": PP_ALIGN.LEFT, "center": PP_ALIGN.CENTER, "right": PP_ALIGN.RIGHT}[align]
        for span in _spans_of(text):
            self._style_run(paragraph.add_run(), span, size, color or self.theme.text, bold, font)
            paragraph.runs[-1].text = span.text
        return box

    def _title(self, slide, text):
        """The headline at the top of a content slide, with a short accent bar above it."""
        from pptx.dml.color import RGBColor
        from pptx.enum.shapes import MSO_SHAPE

        bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, self.margin, int(self.height * 0.085), int(self.width * 0.05), int(self.height * 0.012))
        bar.fill.solid()
        bar.fill.fore_color.rgb = RGBColor.from_string(self.theme.accent)
        bar.line.fill.background()
        return self._text(
            slide, self.margin, int(self.height * 0.11), self.width - 2 * self.margin, int(self.height * 0.17),
            text, 34, color=self.theme.slide_title, bold=True, font=self.theme.heading_font, anchor="middle",
        )

    def _body_box(self):
        top = int(self.height * 0.31)
        return self.margin, top, self.width - 2 * self.margin, self.height - top - int(self.height * 0.09)

    def _notes(self, slide, notes):
        if notes:
            slide.notes_slide.notes_text_frame.text = _plain(notes)

    def _flatten(self, items, level=0):
        flat = []
        for item in items:
            if isinstance(item, list):
                flat.extend(self._flatten(item, level + 1))
            else:
                flat.append((level, item))
        return flat

    def _bullets(self, slide, box, items, size=None):
        from pptx.oxml.ns import qn
        from pptx.util import Emu, Pt

        left, top, width, height = box
        flat = self._flatten(items)
        if size is None:
            # Fewer, larger lines read better; more lines must still fit the box.
            size = 28 if len(flat) <= 4 else 24 if len(flat) <= 6 else 20 if len(flat) <= 9 else 16
        shape = slide.shapes.add_textbox(left, top, width, height)
        frame = shape.text_frame
        frame.word_wrap = True
        for index, (level, item) in enumerate(flat):
            paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
            paragraph.space_after = Pt(size * 0.45)
            properties = paragraph._p.get_or_add_pPr()
            indent = Emu(int(Pt(size * 1.1)))
            properties.set("marL", str(indent * (level + 1)))
            properties.set("indent", str(-indent))
            color = properties.makeelement(qn("a:buClr"), {})
            color.append(color.makeelement(qn("a:srgbClr"), {"val": self.theme.accent}))
            properties.append(color)
            properties.append(properties.makeelement(qn("a:buFont"), {"typeface": "Arial"}))
            properties.append(properties.makeelement(qn("a:buChar"), {"char": "•" if level == 0 else "–"}))
            for span in _spans_of(item):
                run = paragraph.add_run()
                run.text = span.text
                self._style_run(run, span, size - 3 * level, self.theme.text)
        return shape

    # -- slides ------------------------------------------------------------

    def title_slide(self, title, subtitle=None, notes=None):
        from pptx.dml.color import RGBColor
        from pptx.enum.shapes import MSO_SHAPE

        if self.uses_template:
            slide = self.presentation.slides.add_slide(self.presentation.slide_layouts[0])
            slide.shapes.title.text = _plain(title)
            if subtitle and len(slide.placeholders) > 1:
                slide.placeholders[1].text = _plain(subtitle)
            self._notes(slide, notes)
            return slide
        slide = self._blank()
        band = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, int(self.width * 0.035), self.height)
        band.fill.solid()
        band.fill.fore_color.rgb = RGBColor.from_string(self.theme.accent)
        band.line.fill.background()
        left = int(self.width * 0.10)
        self._text(slide, left, int(self.height * 0.28), self.width - left - self.margin, int(self.height * 0.30),
                   title, 44, color=self.theme.slide_title, bold=True, font=self.theme.heading_font, anchor="bottom")
        if subtitle:
            self._text(slide, left, int(self.height * 0.60), self.width - left - self.margin, int(self.height * 0.15),
                       subtitle, 20, color=self.theme.muted)
        self._notes(slide, notes)
        return slide

    def section_slide(self, title, notes=None):
        """A divider between parts of the deck."""
        slide = self._content_slide(title, notes, title_only=True)
        return slide

    def _content_slide(self, title, notes, title_only=False):
        if self.uses_template:
            layouts = self.presentation.slide_layouts
            layout = layouts[5] if title_only and len(layouts) > 5 else layouts[1] if not title_only and len(layouts) > 1 else layouts[0]
            slide = self.presentation.slides.add_slide(layout)
            if slide.shapes.title is not None:
                slide.shapes.title.text = _plain(title)
        else:
            slide = self._blank()
            self._title(slide, title)
        self._notes(slide, notes)
        return slide

    def bullet_slide(self, title, bullets, notes=None):
        """A headline and up to about eight bullets. A list inside the list is a sub-bullet."""
        if self.uses_template:
            slide = self._content_slide(title, notes)
            body = slide.placeholders[1].text_frame if len(slide.placeholders) > 1 else None
            if body is not None:
                for index, (level, item) in enumerate(self._flatten(bullets)):
                    paragraph = body.paragraphs[0] if index == 0 else body.add_paragraph()
                    paragraph.text = _plain(item)
                    paragraph.level = min(level, 4)
            return slide
        slide = self._content_slide(title, notes)
        self._bullets(slide, self._body_box(), bullets)
        return slide

    def text_slide(self, title, text, notes=None):
        """A headline and a paragraph, or one large number or statement."""
        slide = self._content_slide(title, notes, title_only=True)
        left, top, width, height = self._body_box()
        length = len(_plain(text))
        self._text(slide, left, top, width, height, text, 54 if length <= 24 else 28 if length <= 160 else 20)
        return slide

    def table_slide(self, title, rows, header=True, notes=None):
        from pptx.dml.color import RGBColor
        from pptx.enum.text import PP_ALIGN
        from pptx.util import Pt

        rows = [list(row) for row in rows]
        slide = self._content_slide(title, notes, title_only=True)
        left, top, width, height = self._body_box()
        column_count = max(len(row) for row in rows)
        size = 20 if len(rows) <= 6 else 16 if len(rows) <= 9 else 13 if len(rows) <= 12 else 11
        row_height = min(int(Pt(size * 2.4)), height // len(rows))
        graphic = slide.shapes.add_table(len(rows), column_count, left, top, width, row_height * len(rows))
        table = graphic.table
        for table_row in table.rows:
            table_row.height = row_height
        theme = self.theme
        for row_index, row in enumerate(rows):
            is_header = header and row_index == 0
            for column_index in range(column_count):
                value = row[column_index] if column_index < len(row) else ""
                cell = table.cell(row_index, column_index)
                cell.fill.solid()
                fill = theme.table_header_fill if is_header else theme.table_band_fill if row_index % 2 == 0 else theme.slide_background
                cell.fill.fore_color.rgb = RGBColor.from_string(fill)
                paragraph = cell.text_frame.paragraphs[0]
                if _looks_numeric(value) and not is_header:
                    paragraph.alignment = PP_ALIGN.RIGHT
                for span in _spans_of(_cell_text(value)):
                    run = paragraph.add_run()
                    run.text = span.text
                    self._style_run(run, span, size, theme.table_header_text if is_header else theme.text, bold=is_header)
        return slide

    def chart_slide(self, title, kind, categories, series, notes=None, number_format=None):
        """A native PowerPoint chart: "bar", "line" or "pie". `series` maps a name to its values."""
        from pptx.chart.data import CategoryChartData
        from pptx.dml.color import RGBColor
        from pptx.enum.chart import XL_CHART_TYPE, XL_LEGEND_POSITION
        from pptx.util import Pt

        kinds = {"bar": XL_CHART_TYPE.COLUMN_CLUSTERED, "line": XL_CHART_TYPE.LINE_MARKERS, "pie": XL_CHART_TYPE.PIE}
        if kind not in kinds:
            raise ValueError("chart kind must be 'bar', 'line' or 'pie'")
        slide = self._content_slide(title, notes, title_only=True)
        data = CategoryChartData()
        data.categories = [str(category) for category in categories]
        for name, values in series.items():
            data.add_series(str(name), [float(value) for value in values])
        left, top, width, height = self._body_box()
        chart = slide.shapes.add_chart(kinds[kind], left, top, width, height, data).chart
        chart.font.size = Pt(14)
        chart.font.name = self.theme.body_font
        chart.has_legend = kind == "pie" or len(series) > 1
        if chart.has_legend:
            chart.legend.position = XL_LEGEND_POSITION.BOTTOM
            chart.legend.include_in_layout = False
        colors = self.theme.chart_colors
        plot = chart.plots[0]
        if kind == "pie":
            plot.has_data_labels = True
            if number_format:
                plot.data_labels.number_format = number_format
                plot.data_labels.number_format_is_linked = False
            for index, point in enumerate(plot.series[0].points):
                point.format.fill.solid()
                point.format.fill.fore_color.rgb = RGBColor.from_string(colors[index % len(colors)])
        else:
            for index, chart_series in enumerate(plot.series):
                color = RGBColor.from_string(colors[index % len(colors)])
                if kind == "bar":
                    chart_series.format.fill.solid()
                    chart_series.format.fill.fore_color.rgb = color
                else:
                    chart_series.format.line.color.rgb = color
                    chart_series.format.line.width = Pt(3)
            if number_format:
                chart.value_axis.tick_labels.number_format = number_format
                chart.value_axis.tick_labels.number_format_is_linked = False
        return slide

    def image_slide(self, title, source, caption=None, notes=None):
        """A picture from a path or bytes, fitted into the slide without changing its shape."""
        from PIL import Image

        slide = self._content_slide(title, notes, title_only=True)
        left, top, width, height = self._body_box()
        if caption:
            height -= int(self.height * 0.07)
        data = source if isinstance(source, (bytes, bytearray)) else open(source, "rb").read()
        with Image.open(io.BytesIO(data)) as picture:
            picture_width, picture_height = picture.size
        scale = min(width / picture_width, height / picture_height)
        shown_width, shown_height = int(picture_width * scale), int(picture_height * scale)
        slide.shapes.add_picture(io.BytesIO(data), left + (width - shown_width) // 2, top, shown_width, shown_height)
        if caption:
            self._text(slide, left, top + shown_height + int(self.height * 0.01), width, int(self.height * 0.06),
                       caption, 14, color=self.theme.muted, align="center")
        return slide

    def two_column_slide(self, title, left_bullets, right_bullets, left_heading=None, right_heading=None, notes=None):
        """Two lists side by side, for a comparison."""
        slide = self._content_slide(title, notes, title_only=True)
        left, top, width, height = self._body_box()
        gap = int(self.width * 0.04)
        column_width = (width - gap) // 2
        for index, (heading, bullets) in enumerate(((left_heading, left_bullets), (right_heading, right_bullets))):
            column_left = left + index * (column_width + gap)
            column_top = top
            if heading:
                self._text(slide, column_left, top, column_width, int(self.height * 0.08), heading, 20, color=self.theme.accent, bold=True)
                column_top += int(self.height * 0.09)
            self._bullets(slide, (column_left, column_top, column_width, height - (column_top - top)), bullets, size=20)
        return slide

    def save(self, path):
        _make_parent(path)
        self.presentation.save(path)
        return path


# ---------------------------------------------------------------------------
# From HTML
# ---------------------------------------------------------------------------

_SKIPPED_TAGS = {"script", "style", "nav", "noscript", "template", "button", "head", "svg"}
_BLOCK_TAGS = {
    "h1", "h2", "h3", "h4", "h5", "h6", "p", "ul", "ol", "table", "blockquote", "pre", "img", "figure",
    "canvas", "hr", "div", "section", "article", "main", "header", "footer", "aside", "body", "html", "details", "summary",
}


def _soup(html):
    from bs4 import BeautifulSoup

    text = html
    base_folder = "."
    if isinstance(html, str) and "<" not in html and os.path.exists(html):
        base_folder = os.path.dirname(html) or "."
        with open(html, encoding="utf-8") as handle:
            text = handle.read()
    return BeautifulSoup(text, "html.parser"), base_folder


def _inline_spans(node, bold=False, italic=False, code=False, url=None, underline=False, superscript=False, subscript=False):
    """The text of an inline run of HTML as spans, keeping bold, italic, code and links."""
    from bs4 import NavigableString, Comment

    spans = []
    for child in node.children:
        if isinstance(child, Comment):
            continue
        if isinstance(child, NavigableString):
            text = re.sub(r"\s+", " ", str(child))
            if text:
                spans.append(_Span(text, bold=bold, italic=italic, code=code, url=url, underline=underline,
                                   superscript=superscript, subscript=subscript))
            continue
        name = child.name.lower()
        if name in _SKIPPED_TAGS or name in ("ul", "ol", "table"):
            continue
        if name == "br":
            spans.append(_Span("\n", bold=bold, italic=italic))
            continue
        spans.extend(_inline_spans(
            child,
            bold=bold or name in ("b", "strong", "th"),
            italic=italic or name in ("i", "em", "cite"),
            code=code or name in ("code", "kbd", "samp"),
            url=child.get("href") if name == "a" and str(child.get("href", "")).startswith(("http://", "https://", "mailto:")) else url,
            underline=underline or name == "u",
            superscript=superscript or name == "sup",
            subscript=subscript or name == "sub",
        ))
    return spans


def _trimmed(spans):
    """Drops empty spans and the spaces at both ends of a paragraph."""
    spans = [span for span in spans if span.text]
    if spans:
        spans[0].text = spans[0].text.lstrip()
        spans[-1].text = spans[-1].text.rstrip()
    return [span for span in spans if span.text]


def _list_items(list_node):
    """A <ul> or <ol> as a nested Python list of span lists."""
    items = []
    for item in list_node.find_all("li", recursive=False):
        spans = _trimmed(_inline_spans(item))
        if spans:
            items.append(tuple(spans))
        for nested in item.find_all(["ul", "ol"], recursive=False):
            items.append(_list_items(nested))
    return items


def _table_rows(table_node):
    """The rows of a <table> and whether its first row is a header."""
    rows = []
    has_header = False
    for row_index, row in enumerate(table_node.find_all("tr")):
        cells = row.find_all(["th", "td"], recursive=False)
        if row_index == 0 and cells and all(cell.name == "th" for cell in cells):
            has_header = True
        values = []
        for cell in cells:
            spans = _trimmed(_inline_spans(cell))
            text = "".join(span.text for span in spans)
            is_plain = all(not (span.bold or span.italic or span.code or span.url) for span in spans) or cell.name == "th"
            values.append(text if is_plain else tuple(spans))
        if values:
            rows.append(values)
    return rows, has_header


def _image_bytes(source, base_folder):
    """The bytes of an <img src>: a data: URI or a file beside the HTML. None for anything else."""
    if not source:
        return None
    if source.startswith("data:"):
        header, _, payload = source.partition(",")
        if ";base64" not in header or "svg" in header:
            return None
        return base64.b64decode(payload)
    if source.startswith(("http://", "https://")):
        return None
    path = os.path.join(base_folder, source)
    if os.path.exists(path) and not path.lower().endswith(".svg"):
        with open(path, "rb") as handle:
            return handle.read()
    return None


def _chart_spec(node, charts):
    """A chart's data from data-chart='{"type":..,"labels":..,"series":{..}}' or from `charts[id]`."""
    spec = None
    if node.get("data-chart"):
        try:
            spec = json.loads(node["data-chart"])
        except ValueError:
            spec = None
    if spec is None and charts and node.get("id") in charts:
        spec = charts[node.get("id")]
    if not spec:
        return None
    series = spec.get("series") or {}
    if isinstance(series, list):
        series = {entry.get("name", "Series %d" % (index + 1)): entry.get("values", []) for index, entry in enumerate(series)}
    return {"kind": spec.get("type", "bar"), "categories": spec.get("labels", []), "series": series, "title": spec.get("title")}


def _has_block_child(node):
    return any(getattr(child, "name", None) and child.name.lower() in _BLOCK_TAGS for child in node.children)


def _blocks(node, base_folder, charts):
    """Walks the HTML and yields (kind, value) blocks in reading order."""
    from bs4 import NavigableString

    for child in node.children:
        if isinstance(child, NavigableString):
            text = re.sub(r"\s+", " ", str(child)).strip()
            if text and type(child) is NavigableString:
                yield "paragraph", [_Span(text)]
            continue
        name = child.name.lower()
        if name in _SKIPPED_TAGS or child.get("hidden") is not None:
            continue
        if re.fullmatch(r"h[1-6]", name):
            spans = _trimmed(_inline_spans(child))
            if spans:
                yield "heading", (int(name[1]), spans)
        elif name in ("ul", "ol"):
            items = _list_items(child)
            if items:
                yield "bullets" if name == "ul" else "numbered", items
        elif name == "table":
            rows, has_header = _table_rows(child)
            if rows:
                caption = child.find("caption")
                yield "table", (rows, has_header, caption.get_text(" ", strip=True) if caption else None)
        elif name == "blockquote":
            spans = _trimmed(_inline_spans(child))
            if spans:
                yield "quote", spans
        elif name == "pre":
            yield "code", child.get_text()
        elif name == "hr":
            yield "rule", None
        elif name == "img":
            data = _image_bytes(child.get("src"), base_folder)
            if data:
                yield "image", (data, child.get("alt") or None)
        elif name == "canvas":
            spec = _chart_spec(child, charts)
            if spec:
                yield "chart", spec
        elif name == "figure":
            caption = child.find("figcaption")
            caption_text = caption.get_text(" ", strip=True) if caption else None
            for kind, value in _blocks(child, base_folder, charts):
                if kind == "image":
                    yield "image", (value[0], caption_text or value[1])
                elif kind == "chart":
                    yield "chart", dict(value, caption=caption_text)
                elif kind != "paragraph":
                    yield kind, value
        elif name == "figcaption":
            continue
        elif _has_block_child(child):
            if child.get("data-chart"):
                spec = _chart_spec(child, charts)
                if spec:
                    yield "chart", spec
                    continue
            yield from _blocks(child, base_folder, charts)
        else:
            spans = _trimmed(_inline_spans(child))
            if spans:
                yield "paragraph", spans


def html_to_docx(html, path, theme="leaf", template=None, charts=None, page="a4", footer=True):
    """Turns a report written as HTML into a Word file in the theme.

    `html` is a file path or the HTML text. Headings, paragraphs, lists,
    tables, quotes, code, links and pictures are carried over; CSS is not,
    because Word has no CSS: the theme (or the template) gives the look.
    A Chart.js <canvas> has its data in script, which cannot be read, so give
    the canvas data-chart='{"type":"bar","labels":[..],"series":{"Name":[..]}}'
    (or pass charts={canvas_id: {...}}) and it is drawn as a picture.
    """
    soup, base_folder = _soup(html)
    document = WordDoc(theme=theme, template=template, page=page)
    root = soup.body or soup
    seen_title = False
    for kind, value in _blocks(root, base_folder, charts):
        if kind == "heading":
            level, spans = value
            if level == 1 and not seen_title:
                document.title(spans)
                seen_title = True
            else:
                document.heading(spans, level=max(level - 1, 1) if seen_title else level)
        elif kind == "paragraph":
            document.paragraph(value)
        elif kind == "bullets":
            document.bullets(value)
        elif kind == "numbered":
            document.numbered(value)
        elif kind == "table":
            rows, has_header, caption = value
            document.table(rows, header=has_header, caption=caption)
        elif kind == "quote":
            document.quote(value)
        elif kind == "code":
            document.code(value)
        elif kind == "image":
            document.image(value[0], caption=value[1])
        elif kind == "chart":
            document.chart(value["kind"] if value["kind"] in ("bar", "line") else "bar", value["categories"], value["series"],
                           title=value.get("title"), caption=value.get("caption"))
    if footer and template is None:
        document.footer(page_numbers=True)
    return document.save(path)


def html_to_pptx(html, path, theme="leaf", template=None, charts=None):
    """Turns a slide deck written as HTML (<section class="slide"> per slide) into a .pptx.

    Each slide's first heading is its headline; then a chart (see html_to_docx
    for data-chart), a table, a picture or its bullets fill the slide, in that
    order of preference. <aside class="notes"> becomes the speaker notes.
    """
    soup, base_folder = _soup(html)
    deck = Deck(theme=theme, template=template)
    sections = soup.select("section.slide") or soup.find_all("section")
    if not sections:
        raise ValueError("no <section class=\"slide\"> found; each slide must be its own <section>")
    for index, section in enumerate(sections):
        notes_node = section.find("aside")
        notes = notes_node.get_text(" ", strip=True) if notes_node else None
        if notes_node:
            notes_node.extract()
        blocks = list(_blocks(section, base_folder, charts))
        headings = [value for kind, value in blocks if kind == "heading"]
        title = headings[0][1] if headings else [_Span("Slide %d" % (index + 1))]
        rest = [(kind, value) for kind, value in blocks if not (kind == "heading" and value is headings[0])] if headings else blocks
        chart = next((value for kind, value in rest if kind == "chart"), None)
        table = next((value for kind, value in rest if kind == "table"), None)
        image = next((value for kind, value in rest if kind == "image"), None)
        bullets = []
        for kind, value in rest:
            if kind in ("bullets", "numbered"):
                bullets.extend(value)
            elif kind in ("paragraph", "quote"):
                bullets.append(tuple(value))
            elif kind == "heading":
                bullets.append(tuple(value[1]))
        if index == 0 and not (chart or table or image):
            subtitle = [span for item in bullets for span in (list(item) + [_Span("  ")])] if bullets else None
            deck.title_slide(title, subtitle=subtitle, notes=notes)
        elif chart:
            deck.chart_slide(title, chart["kind"] if chart["kind"] in ("bar", "line", "pie") else "bar", chart["categories"], chart["series"], notes=notes)
        elif table:
            deck.table_slide(title, table[0], header=table[1], notes=notes)
        elif image:
            deck.image_slide(title, image[0], caption=image[1], notes=notes)
        elif len(bullets) == 1 and not isinstance(bullets[0], list):
            deck.text_slide(title, bullets[0], notes=notes)
        elif bullets:
            deck.bullet_slide(title, bullets, notes=notes)
        else:
            deck.section_slide(title, notes=notes)
    return deck.save(path)


def html_tables_to_xlsx(html, path, theme="leaf"):
    """Writes every <table> of an HTML file to its own sheet, named by its caption or the heading before it."""
    soup, _ = _soup(html)
    sheets = Sheets(theme=theme)
    used_names = set()
    tables = soup.find_all("table")
    if not tables:
        raise ValueError("the HTML has no <table>")
    for index, table in enumerate(tables):
        rows, has_header = _table_rows(table)
        if not rows:
            continue
        caption = table.find("caption")
        heading = table.find_previous(re.compile(r"^h[1-6]$"))
        name = (caption.get_text(" ", strip=True) if caption else heading.get_text(" ", strip=True) if heading else "") or "Table %d" % (index + 1)
        name = re.sub(r"[\[\]:*?/\\]", " ", name)[:31]
        while name in used_names:
            name = (name[:27] + " (%d)" % (index + 1))[:31]
        used_names.add(name)
        converted = [[_to_number(_plain(value)) for value in row] for row in rows]
        sheets.sheet(name, converted, header=has_header)
    return sheets.save(path)


def _to_number(text):
    """"1,200" and "12.5" become numbers so Excel can add them up; anything else stays text."""
    stripped = text.strip()
    if re.fullmatch(r"[-+]?\d{1,3}(,\d{3})+(\.\d+)?", stripped) or re.fullmatch(r"[-+]?\d+(\.\d+)?", stripped):
        number = float(stripped.replace(",", ""))
        return int(number) if number.is_integer() and "." not in stripped else number
    return text
