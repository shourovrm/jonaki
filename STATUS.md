# Status — 2026-10-03 (v0.7.0: M0 to M6 done, plus files and chat additions)

Phase:        M0 to M6 built; 0.6.0 and 0.7.0 add reasoning, working line, copy, edit, thinking level,
              images, camera, PDF and Office reading.
Goal:         Lightweight Android agent app with threads, memory, skills, MCP, subagents, web search,
              YouTube summaries, HTML artifacts and phone tools.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M7 step 1.
Decisions:    D-001 to D-015, D-017 to D-032, D-048 accepted; D-016 superseded. Proposed: D-033 to D-047,
              D-049 to D-057.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 559 JVM tests; APK ~5.9 MB. Room v6.
Device check: 0.7.0 on A059: picker attach of PDF, DOCX, PNG; read_document and view_image answered
              correctly. Pending: camera, share sheet from other apps, skills, viewer, D-029 checks.
Known gaps:   Attachment chips are lost if the process dies. Share-screen thread names wrap to two
              lines. Skill editor drops unsaved edits. Chat does not mark where a summary begins.
              Every past image is re-sent on each request. sqlite-bundled-jvm (tests) awaits approval.
Later list:   Projects, custom prompt, answer styles, personas, incognito chat, more services.
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
Next action:  User reviews proposed decisions; phone check of images and PDF; then M7 subagents.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, docs/plans/2026-10-02-v1-plan.md, docs/mockups/.
