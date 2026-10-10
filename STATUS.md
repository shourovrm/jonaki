# Status — 2026-10-10 (v1.4.4 released)

Phase:        v1.4.4 released on GitHub with D-157 to D-176. The session's log, with every open
              issue, is docs/session-summary-2026-10-10.md; start the next session there.
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at the same log.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129, D-135 to D-137 accepted;
              D-016 superseded. Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085,
              D-086, D-090 to D-127, D-130 to D-134, D-138 to D-177.
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-10).
              Version 1.4.4, code 17, tag v1.4.4, pushed. Room v12.
Phone (A059): runs 1.4.4. Phone pass done for D-166 to D-173. D-174 (media button, details
              line, settings sheet) seen for Video; D-176 (model under the title) seen.
Fixed today:  cache price in the Providers sheet; blank vector preview; Image quality padding;
              Claude models through OpenRouter now use the prompt cache (D-175, seen working).
Not seen:     D-174 picture and vector sheets, a reference picture in Picture mode, the error
              row of a failed media send; D-158, D-161, D-163 delete, D-164 copy button.
Strings:      D-174 reviewed; D-157 to D-173 not reviewed. Spend: $0.28 of $0.50.
Next action:  D-177 (Serper, unreleased): a live search and a phone check; then D-174, the log.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md,
              docs/session-summary-2026-10-10.md.
