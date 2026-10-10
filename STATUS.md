# Status — 2026-10-10 (v1.4.1, unreleased work on main)

Phase:        After v1.4.1, not released: D-157 to D-173 (names, fresh install, providers, images,
              wake lock, start screen and drafts, multi-select, errors and :batch, language rule).
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line lists the open checks.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129, D-135 to D-137 accepted;
              D-016 superseded. Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085,
              D-086, D-090 to D-127, D-130 to D-134, D-138 to D-173.
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-10). Room v12.
Phone (A059): 2026-10-10: D-157, D-159, D-160 checked; D-162 start and draft; D-163 selection
              mode; D-165 Bangla kept after an English follow-up. Each Outcome line has details.
Not checked:  D-158 fresh install; D-161 with the screen off; D-163 Delete and Memory; D-164 on
              screen; D-165 with a link; image Save and Share; and the older list in the plan.
Unchecked:    D-166 image service cards, picker prices, and any Gemini picture (API details
              not confirmed); D-167 the image model section of the thread's model sheet.
Strings:      D-157 to D-165 written by subagents, not yet reviewed. Spend: about $0.04 of $0.50.
Next action:  The phone checks of D-161 to D-167 when the phone is free;
              the string review.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
