# Status — 2026-10-03 (v0.8.0: M0 to M8 done, plus chat additions)

Phase:        M0 to M8 built. 0.8.0 adds subagents (M7), approval modes, run_code with JavaScript and
              Python (M8), tool picker, Python settings, parallel read-only calls, context sheet,
              + button with photo gallery, code viewer, Reddit skill, README.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M9 step 1.
Decisions:    D-001 to D-015, D-017 to D-032, D-048 accepted; D-016 superseded. Proposed: D-033 to D-047,
              D-049 to D-070, D-080, D-081, D-085, D-086, D-090 to D-095.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, ~790 JVM tests; APK ~6.2 MB. Room v7.
Device check: 0.8.0 quick check on A059: upgrade from 0.7.0 kept all threads, tool picker, Python
              install (13.5 MB, <20 s), run_code approval card, pandas install card, Try again.
              Open: subagents, approval modes, parallel calls, context sheet, gallery and permission,
              code viewer, Reddit skill, D-029 checks. Markdown tables render as raw pipes.
Known gaps:   Attachment chips are lost if the process dies. Share-screen thread names wrap to two
              lines. Skill editor drops unsaved edits. Chat does not mark where a summary begins.
              Every past image is re-sent on each request. sqlite-bundled-jvm (tests) awaits approval.
Later list:   Projects, custom prompt, answer styles, personas, incognito chat, more services.
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
Next action:  Quick device checks of the open items; user reviews proposed decisions; then M9.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
