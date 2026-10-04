---
name: report
description: Write a researched report as one self-contained HTML file in artifacts/, with sources, tables and simple charts. Use when the user asks for a report, a comparison, a brief or a write-up to keep.
---

# Report

A report is one HTML file in `artifacts/`, for example `artifacts/laptop-comparison.html`. Jonaki shows it in a viewer with network access off, so the file must contain everything: no links to scripts, styles, fonts or images on the web.

## Steps

1. Settle the question. If the request leaves the scope open (which market, which year, which budget), ask one short question, or state the assumption in the report's first paragraph.
2. Gather facts. Use `web_search`, then `web_fetch` on the two to five best pages. Read files the user shared in `inbox/` with `read_file`. Keep notes in `work/<topic>-notes.md` when the research takes more than three steps.
3. Write the report with `write_file`, call `artifact` with its path so the user can open it, then give the three main findings in the chat.

## Structure

- Title, a one-line subtitle with the date, and a summary of three to five sentences that answers the question.
- Sections with plain noun-phrase headings. Begin each paragraph with the fact the reader can use, then the number, the example or the reason behind it.
- A table whenever three or more items are compared on the same attributes.
- A "Sources" list at the end: title, site and URL of each page used. Cite as [1], [2] in the text.
- Write in the language the user writes in. For Bangla, set `lang="bn"` and use the font stack `"Noto Sans Bengali", sans-serif`.

## Writing

- Begin each paragraph with a concrete fact the reader can use or check, then back it with a number, an example or the reason behind it.
- Define each term where it first appears.
- Write only sentences that carry content: nothing that announces what comes next, no opening line for effect, no list of three built for rhythm, no sweeping claim about the whole of anything.
- Prefer plain verbs (is, has, does) and one exact number over several adjectives.
- Headings are plain noun phrases that name the section's subject, never questions or promises.

## HTML rules

- One file, `<!doctype html>`, `<meta charset="utf-8">`, `<meta name="viewport" content="width=device-width, initial-scale=1">`.
- All CSS in one `<style>` block. Body text 16 px, line height 1.55, maximum line length about 70 characters, page padding 16 px, so the report reads well on a phone.
- Support light and dark: define colours as CSS variables and switch them with `@media (prefers-color-scheme: dark)`.
- Tables scroll sideways inside a wrapper (`overflow-x: auto`) instead of breaking the page width.
- Charts: load the bundled Chart.js 4 with `<script src="lib/chart.js"></script>` (it works only inside Jonaki's viewer) and draw into a `<canvas>` inside a box of fixed height, with `responsive: true, maintainAspectRatio: false`. Label both axes and give each dataset a label. For one or two simple bars, inline SVG is also fine.
- Add `@media print` rules: white background, black text, no shadows, `break-inside: avoid` on tables and figures, so a PDF export looks clean.
- When the user asks for a PDF, call `export_pdf` with the report's path (page `a4`, the default). It writes the PDF beside the HTML file; `share_file` then saves or shares it.
- When the user asks for a Word or Excel file, read the `office-documents` skill. Give every chart's `<canvas>` a `data-chart` attribute with its type, labels and series as JSON, so the chart can be carried into the Office file.

## Checks before you finish

- Every number has a source or is marked as an estimate.
- No external URL appears in `src=` or `href=` of a stylesheet or script.
- The summary answers the user's actual question.
