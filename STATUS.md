# Status — 2026-10-05 (v1.4.0)

Phase:        v1.4.0: Word, Excel and PowerPoint files through a documents add-on for Python and
              the bundled jonaki_docs helper (D-153). v1.3.0: memory rework, search_chats,
              Guardrails and the Jev options, skill proposals, MiniMax balance (D-147 to D-152).
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line lists the open checks.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129, D-135 to D-137 accepted;
              D-016 superseded. Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085,
              D-086, D-090 to D-127, D-130 to D-134, D-138 to D-155.
Build:        `gradle testReleaseUnitTest assembleRelease` passes; Room v12. Fresh clones need
              `git submodule update --init --depth 1`.
Phone (A059): 2026-10-05: search run, report with chart, edit_file card, About text, skill from
              a GitHub link and a skill proposal work. Report PDF broke table headers mid-word.
Not checked:  the documents add-on's install and a run of jonaki_docs in the WebView worker; files
              opened in Microsoft Office (LibreOffice only); every v1.3.0 screen, the upgrade
              from 1.2.0 (message index), the MiniMax balance with a real key; from v1.2.0:
              thumbnails, the Jev guard on, "Allow all in this thread", Settings rules.
Strings:      D-147 to D-153 reviewed by the main session only. Spend: about $0.07 of the $1.
Next action:  Phone checks: the upgrade from 1.2.0, the documents add-on, the Jev guard on.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
