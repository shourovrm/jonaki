# Status — 2026-10-03 (v1.0.0 plus local models, projects and subagent settings)

Phase:        v1.0.0 released; on main since: About (D-130), web_fetch rendering (D-131), request
              times (D-132), local models (D-133), project memory and files (D-135), local tool list
              (D-136), subagent limit per message (D-137), Subagents page and custom subagents (D-138).
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line lists the open checks.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129, D-135 to D-137 accepted;
              D-016 superseded. Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085,
              D-086, D-090 to D-127, D-130 to D-134, D-138.
Build:        `gradle testReleaseUnitTest assembleRelease` passes; Room v10; APK about 13.2 MB
              (6.3 MB libjonaki_llama.so). Fresh clones need `git submodule update --init --depth 1`.
Phone (A059): installed 02e2f97. Checked: About; Local models; project memory and files; the
              subagent card at 3; Subagents page, limits and a custom subagent run; local 0.8B chat.
Not checked:  web_fetch rendering; local tool calls with a 2B model; download resume.
Strings:      D-135 to D-138 strings reviewed by the main session only (user: no nested review).
Spend:        about $0.06 of the $1 for checks.
Next action:  User reviews D-138; tool calls with a 2B local model; web_fetch rendering check.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
