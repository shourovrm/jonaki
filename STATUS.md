# Status — 2026-10-04 (v1.2.0)

Phase:        v1.2.0 with: subagent budgets per type and cap of 5 (D-139), queued messages
              (D-140), reminders until Done (D-141), fewer approval cards with a thread allowance
              and Settings rules (D-142), prompt-injection guardrails (D-143), Jev guard off by
              default (D-144), export_pdf (D-145), date line without the zone, thumbnails (D-146).
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line lists the open checks.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129, D-135 to D-137 accepted;
              D-016 superseded. Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085,
              D-086, D-090 to D-127, D-130 to D-134, D-138 to D-146.
Build:        `gradle testReleaseUnitTest assembleRelease` passes; Room v11. Fresh clones need
              `git submodule update --init --depth 1`.
Phone (A059): Checked: queued messages; export_pdf (9-page A4 report); reminder buttons, Later
              sheet and Done; a reminder in Auto without a card; the shield chip and its sheet.
Not checked:  thumbnails; the Jev guard switched on; Stop with a queued message; "Allow all in
              this thread", Settings rules and the card after outside content; a subagent's
              Blockers; Settings > Subagents and the Battery row; D-131 and 2B local tool calls.
Strings:      D-139 to D-146 reviewed by the main session only. Spend: about $0.06 of the $1.
Next action:  The phone checks listed above; a second Jev test with real pages before default on.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
