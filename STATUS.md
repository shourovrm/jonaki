# Status — 2026-10-03 (v0.8.0: M0 to M8 done, plus chat additions)

Phase:        M0 to M8 built. 0.8.0 adds subagents (M7), approval modes, run_code with JavaScript and
              Python (M8), tool picker, Python settings, parallel read-only calls, context sheet,
              + button with photo gallery, code viewer, Reddit skill, README.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M9 step 1.
Decisions:    D-001 to D-015, D-017 to D-032, D-048 accepted; D-016 superseded. Proposed: D-033 to D-047,
              D-049 to D-070, D-080, D-081, D-085, D-086, D-090 to D-095, D-124.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 1,022 JVM tests; APK 6.5 MB. Settings has Permissions + About (D-124).
Device check: 0.8.0 on A059 (model Space Bunny Alpha, $0): upgrade kept threads, tool picker, Python
              install, run_code card, pandas card. Passed: 3 parallel researcher subagents with live
              cards; Auto approval (write_file no card, share_file asks); 3 parallel web_search; context
              sheet rows sum to the total; Photos tabs and "Add 2" chips; code viewer tabs; Reddit skill
              (2 threads, then HTTP 429). Bugs: run_code JS output cut at the first ", " (fib.join(', ')
              printed "0"); Markdown tables render as raw pipes. Not checked: D-029 width checks.
Known gaps:   Chips lost on process death; share-screen names wrap; skill editor drops edits; no
              summary marker; past images re-sent (all fixed on unmerged branches). Later list: plan.
              Every past image is re-sent on each request. sqlite-bundled-jvm (tests) awaits approval.
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
Next action:  Quick device checks of the open items; user reviews proposed decisions; then M9.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
