# Status — 2026-10-02 (v0.4.0: M0 to M5 done)

Phase:        M0 to M5 built. v0.3.0 (M4) smoke-tested on the phone; v0.4.0 (M5) not yet on a device.
Goal:         Lightweight Android agent app with threads, memory, skills, MCP, subagents, web search,
              YouTube summaries, HTML artifacts and phone tools.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M6 step 1.
Decisions:    D-001 to D-015, D-017 to D-031 accepted; D-016 superseded. Proposed: D-032 to D-041.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 446 JVM tests; release APK ~3.8 MB.
New in 0.4.0: skill library with import from file, link or GitHub folder; Skills screens; per-thread
              skill switches; skills listed in the prompt; built-ins report, slides, youtube-summary,
              bangla-formal-letter. Room version 4.
Device check: pending for memory extraction, compaction, all skills screens, D-029 long-text checks.
Known gaps:   Skill editor and reset drop unsaved edits without asking. GitHub branch names with "/"
              fail. find_files cannot list /skills/. Chat does not mark where a summary begins.
              Test-only dependency sqlite-bundled-jvm 2.5.2 awaits the user's approval.
Later list:   Projects, custom prompt, answer styles, personas, incognito chat, more services.
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
Next action:  User reviews D-032 to D-041; device check of 0.4.0; then M6 files and artifacts.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, docs/plans/2026-10-02-v1-plan.md, docs/mockups/.
