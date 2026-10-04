---
name: slides
description: Build a slide presentation as one self-contained HTML file in artifacts/, swipeable on a phone and printable to PDF one slide per page. Use when the user asks for slides, a presentation, a deck or a talk outline to show.
---

# Slides

A deck is one HTML file in `artifacts/`, for example `artifacts/solar-energy-slides.html`. Jonaki's viewer has network access off, so the file holds all its CSS and JavaScript and loads nothing from the web.

## Steps

1. Ask for, or assume and state, the audience, the length (default 8 to 12 slides) and the language.
2. Gather content as for a report: `web_search`, `web_fetch`, files in `inbox/`. Draft the outline in `work/<topic>-outline.md` first when the deck has more than 8 slides, and show the outline to the user before writing the HTML if they asked to review it.
3. Write the deck with `write_file`, call `artifact` with its path so the user can open it, and tell the user the slide count.

## Content rules

- Slide 1: title, subtitle, presenter or date. Last slide: the key message or next steps, not "Thank you" alone.
- One idea per slide. A headline that states the point ("Rooftop solar pays back in 6 years"), not a topic label ("Costs").
- At most 5 bullets of at most 12 words each. Put detail in speaker notes, not on the slide.
- Numbers as large type or one chart per slide, drawn with the bundled Chart.js 4 (`<script src="lib/chart.js"></script>`, a `<canvas>` in a fixed-height box).
- Sources on the last slide or in small type at the foot of the slide that uses them.

## Writing

- Bullets and speaker notes carry facts: a number, an example or a reason, never a line for effect.
- No bullet that announces what comes next, no list of three built for rhythm, no sweeping claim.
- Define each term on the slide where it first appears.

## HTML structure

- Each slide is `<section class="slide">` with a 16:9 box (`aspect-ratio: 16 / 9; width: min(100vw, 177.78vh)`), centred, text sized in `vmin` so it scales from phone to projector.
- Speaker notes go in `<aside class="notes">`, hidden on screen.
- Navigation in a small inline script: arrow keys, tap on the left or right third of the screen, and horizontal swipe (touchstart / touchend, 40 px threshold). Show "3 / 10" in a corner.
- Light theme by default with one accent colour; respect `prefers-color-scheme: dark` unless the user asks for a fixed look.
- Print: `@page { size: 16in 9in; margin: 0 }` and `@media print` showing every slide, one per page (`break-after: page`), with navigation hidden. This is what PDF export uses.
- When the user asks for a PDF, call `export_pdf` with the deck's path and `page` set to `slides` (16:9, one slide per page). `share_file` then saves or shares the file.
- Bangla text: `lang="bn"` and the font stack `"Noto Sans Bengali", sans-serif`.

## Checks before you finish

- Every slide fits its box at 360 px width without scrolling.
- The script runs with no network and no external file other than lib/chart.js.
- Headlines read as a story when listed alone.
