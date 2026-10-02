# Status — 2026-10-02 (v0.5.0: M0 to M6 done)

Phase:        M0 to M6 built. v0.3.0 (M4) smoke-tested on the phone; v0.4.0 and v0.5.0 not yet on a device.
Goal:         Lightweight Android agent app with threads, memory, skills, MCP, subagents, web search,
              YouTube summaries, HTML artifacts and phone tools.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M7 step 1.
Decisions:    D-001 to D-015, D-017 to D-031 accepted; D-016 superseded. Proposed: D-032 to D-048.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 502 JVM tests; release APK ~4.0 MB.
New in 0.5.0: share sheet, attach button and one linked folder bring files into inbox/; share_file
              saves to Downloads/Jonaki, save as, share, linked folder (with approval); artifact
              tool and offline viewer with versions, print to PDF and bundled Chart.js 4.5.1.
Device check: pending for 0.4.0 and 0.5.0 (skills, files, viewer, D-029 long-text checks).
Known gaps:   Attachment chips are lost if the process dies. Share-screen thread names wrap to two
              lines. Skill editor drops unsaved edits. Chat does not mark where a summary begins.
              Test-only dependency sqlite-bundled-jvm 2.5.2 awaits the user's approval.
Later list:   Projects, custom prompt, answer styles, personas, incognito chat, more services.
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
Next action:  User reviews D-032 to D-048; device check of 0.5.0; then M7 subagents.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, docs/plans/2026-10-02-v1-plan.md, docs/mockups/.
