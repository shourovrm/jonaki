---
name: office-documents
description: Write or change Word (.docx), Excel (.xlsx) and PowerPoint (.pptx) files with Python, in a consistent theme or in the user's own template. Use when the user asks for a Word document, a spreadsheet, a PowerPoint deck, or an editable copy of a report or slides.
---

# Office documents

Office files are written by a Python program run with `run_code`. The program imports `jonaki_docs`, a helper that ships with Jonaki, and saves into `artifacts/`. Python and its documents add-on must be installed; when they are missing, `run_code` says so and the user gets an install card.

Use this skill when the reader needs a file they can edit in Word, Excel or PowerPoint. When they only need to read or print, the `report` or `slides` skill with `export_pdf` looks better and costs less work.

## Steps

1. Settle the content first, as for a report: facts, numbers, sources.
2. Write one Python program that builds the file with the builders below and ends with `print()` of the saved path.
3. Run it with `run_code` (language python). Name every existing file the program reads (a template, a picture, an HTML report) in the call's input files.
4. Call `share_file` when the user wants the file saved or sent. Say in one sentence what the file holds.

## The three builders

Text arguments accept `**bold**`, `*italic*`, `` `code` `` and `[text](https://link)`. A list inside a list is a sub-bullet.

```python
from jonaki_docs import WordDoc, Sheets, Deck

doc = WordDoc(theme="leaf")                      # page="a4" or "letter", landscape=True
doc.title("Rooftop solar in Dhaka", subtitle="5 October 2026")
doc.contents()                                   # table of contents, filled when opened in Word
doc.heading("Costs")                             # level=1 to 4
doc.paragraph("A 3 kW system costs **2.4 lakh taka** [1].")
doc.bullets(["Panels are 55%", ["Mono panels cost more"], "The inverter is 20%"])
doc.numbered(["Measure the roof", "Get three quotes"])
doc.callout("Larger systems pay back sooner.", title="Key finding")
doc.table([["Size", "Cost (taka)"], ["3 kW", 240000], ["5 kW", 380000]], caption="Table 1: sizes")
doc.chart("bar", ["Jan", "Apr"], {"3 kW": [310, 420]}, title="Output (kWh)", caption="Figure 1")
doc.image("inbox/roof.jpg", width_cm=12, caption="The roof")
doc.quote("..."); doc.code("..."); doc.page_break()
doc.footer("Draft", page_numbers=True)
doc.save("artifacts/solar.docx")

sheets = Sheets(theme="leaf")
budget = sheets.sheet("Budget", [["Month", "Spend"], ["Jan", 120000], ["Feb", 95000]],
                      number_formats={"Spend": "#,##0"}, totals={"Spend": "sum"})
sheets.chart(budget, "bar", title="Spend by month")   # "bar", "line" or "pie"; a native Excel chart
sheets.save("artifacts/budget.xlsx")

deck = Deck(theme="leaf")
deck.title_slide("Rooftop solar pays back in 6 years", subtitle="October 2026", notes="...")
deck.bullet_slide("Panels are over half the cost", ["Panels: **55%**", "Inverter: 20%"])
deck.table_slide("Larger systems pay back sooner", [["Size", "Payback"], ["3 kW", 6.1]])
deck.chart_slide("Output peaks in April", "line", ["Jan", "Apr"], {"3 kW": [310, 420]})
deck.text_slide("Payback at 10 kW", "4.9 years")       # one large number or statement
deck.two_column_slide("Buy or lease", ["Lower cost"], ["No upfront cost"], left_heading="Buy", right_heading="Lease")
deck.image_slide("The site", "inbox/roof.jpg", caption="...")
deck.save("artifacts/solar.pptx")
```

Numbers in a table are right-aligned and get thousands separators; pass numbers as numbers, not as text. In Excel, write formulas as text starting with `=`.

## Look: themes and templates

- `theme="leaf"` (green, Jonaki's own), `"plain"` (black and grey, for forms and photocopies) or `"formal"` (navy with serif headings, for letters, proposals and academic work). Use one theme for every file of the same piece of work.
- Change single values with a dict: `theme={"base": "formal", "accent": "8C2F39", "body_font": "Georgia"}`. Colours are `RRGGBB`.
- For an organisation's house style, ask the user for a `.docx` or `.pptx` of theirs and pass `template="inbox/company.docx"`. The template's own styles, fonts, header, footer and slide layouts are then used instead of the theme, and its sample text and slides are left out. This is the only way to match a house style exactly; do not try to imitate one with theme values.
- Bangla text works in all three formats. The reader's program picks its own Bangla font when the named one is missing.

## From an HTML report or deck

```python
from jonaki_docs import html_to_docx, html_to_pptx, html_tables_to_xlsx
html_to_docx("artifacts/solar.html", "artifacts/solar.docx", theme="formal")
html_to_pptx("artifacts/solar-slides.html", "artifacts/solar.pptx")   # one <section class="slide"> per slide
html_tables_to_xlsx("artifacts/solar.html", "artifacts/solar-tables.xlsx")
```

- Headings, paragraphs, lists, tables, quotes, code, links and pictures are carried over. CSS is not: Word and PowerPoint have no CSS, so the theme or template gives the look.
- A Chart.js chart keeps its data in script, which the converter cannot read. Give the `<canvas>` a `data-chart` attribute and the chart is carried over, as a picture in Word and as a native chart in PowerPoint:
  `<canvas id="output" data-chart='{"type":"bar","labels":["Jan","Apr"],"series":{"3 kW":[310,420]}}'></canvas>`
- Check the result: say which parts were left out (a chart without `data-chart`, an SVG drawing, a web image).

## Changing a file the user gave

Open it with the library itself, change it and save under a new name in `artifacts/`; never overwrite the user's file.

```python
from docx import Document            # python-docx
document = Document("inbox/contract.docx")
for paragraph in document.paragraphs:
    for run in paragraph.runs:       # change runs, not paragraph.text, so the formatting stays
        run.text = run.text.replace("2025", "2026")
document.save("artifacts/contract-2026.docx")
```

`openpyxl.load_workbook(path)` does the same for Excel and `pptx.Presentation(path)` for PowerPoint. The builders expose the same objects as `doc.document`, `sheets.workbook` and `deck.presentation`, for anything the helper does not cover (merged cells, conditional formatting, slide masters).

## Limits to state when they apply

- A chart in a Word file is a picture with Latin labels only; give a Bangla chart English labels, or put it in Excel or PowerPoint, where charts are native and editable.
- Old formats (.doc, .xls, .ppt) cannot be read or written; ask for .docx, .xlsx or .pptx.
- A table of contents is filled in when the file is opened in Word and the field is updated.
- Programs run offline, so pictures must be files in the thread, not web addresses.
