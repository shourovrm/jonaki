# Status — 2026-10-03 (v1.0.0 plus local models and project memory)

Phase:        v1.0.0 released; on main since: About (D-130), web_fetch rendering (D-131), request
              times (D-132), local models with llama.cpp (D-133), project memory and files (D-135),
              local tool list (D-136), subagent limit of 2 per message (D-137).
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line lists the open checks.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129, D-135 to D-137 accepted;
              D-016 superseded. Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085,
              D-086, D-090 to D-127, D-130 to D-134.
Build:        `gradle testReleaseUnitTest assembleRelease` passes; Room v10; APK about 13.2 MB
              (6.3 MB libjonaki_llama.so). Fresh clones need `git submodule update --init --depth 1`.
Phone (A059): installed 3663275. Checked: About, Local models page (Tools section, Clears line),
              local 0.8B chat with prompt reuse, App delay 15 to 53 ms.
Not checked:  project Memory and Files screens (no project on the phone); delegate card above 2
              subagents; web_fetch rendering; local tool calls with 2B; download resume.
Strings:      D-135 to D-137 strings reviewed by the main session only (user: no subagents).
Spend:        $0.048 of the $1 for M9 to M11 checks.
Next action:  User checks a project's Memory and Files and a 3-subagent request on the phone.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
