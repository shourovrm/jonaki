# Office documents spike

Checks `jonaki_docs.py` (the helper bundled with the Python runtime) in the same Pyodide release the app installs, 314.0.7, with the packages of the documents add-on.

## Setup

```
cd spikes/office-docs
npm install pyodide@314.0.7
mkdir wheels   # then download the five wheels pinned in PyodideRelease.kt into it
node run_helper.mjs
```

`node_modules/`, `wheels/` and `made/` are not committed. The lock packages (lxml, pillow, typing-extensions, beautifulsoup4, soupsieve) are fetched from the Pyodide CDN by the run.

## What the run does

`use_helper.py` builds a Word file in each of the three themes, a workbook with a totals row and a native chart, two decks, converts `report.html` and `slides.html` to .docx, .pptx and .xlsx, and uses a made file as a template. It then opens every file again and checks its content: 31 assertions, among them the Bangla text, a sub-bullet, the table's numbers with thousands separators, the SUM formula, the frozen header and filter, the speaker notes, two native charts in the deck, the `data-chart` canvas carried into both formats, and that a template's own text and slides are left out.

## Results (2026-10-05, Node 26.10, desktop)

- All checks passed. The program took 2.2 to 2.6 s after Pyodide and the packages had loaded (1.3 s and 0.9 s in the first test).
- The made files were rendered with LibreOffice to PDF and looked at: Bangla text, tables, callouts, native bar, line and pie charts and the chart pictures came out as intended in all three themes.
- A first feasibility run with the libraries alone wrote, reopened and edited a .docx, wrote an .xlsx with a formula and a native chart, and a .pptx with a native chart.

## Not checked

- The files were not opened in Microsoft Word, Excel or PowerPoint, only in LibreOffice.
- The run on a phone: the worker's loading of the wheels from the app's own files, the time it takes there, and memory.
