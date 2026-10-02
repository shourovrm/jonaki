# Status — 2026-10-02 (v0.3.0: M0 to M4 done)

Phase:        M0 to M4 built; M4 released as v0.3.0 with only a smoke test on the phone (A059).
Goal:         Lightweight Android agent app with threads, memory, skills, MCP, subagents, web search,
              YouTube summaries, HTML artifacts and phone tools.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M5 step 1.
Decisions:    D-001 to D-015, D-017 to D-031 accepted; D-016 superseded. Proposed: D-032 to D-036.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 391 JVM tests; release APK ~3.7 MB.
New in 0.3.0: memory tool, background extraction, memory in the prompt, memory screen, compaction,
              closed service cards show "$12.87 left · $0.08 this month" (all USD).
Device check: balance line seen on the phone. Memory, extraction and compaction not yet checked
              on a device (D-029 long-text check of the memory screen also pending).
Known gaps:   Chat does not mark where a summary begins. Extraction skips messages beyond 40
              unread. Test-only dependency sqlite-bundled-jvm 2.5.2 awaits the user's approval.
Later list:   Projects, custom prompt, answer styles, personas, incognito chat, more services.
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
              API keys in secrets.properties (gitignored). Remote: github.com/shourovrm/jonaki.
Next action:  User reviews D-032 to D-036; then M5 skills.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, docs/plans/2026-10-02-v1-plan.md, docs/mockups/.
