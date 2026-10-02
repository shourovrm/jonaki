# Status — 2026-10-02 (v0.2.0: M0 to M3b done)

Phase:        M0 to M3 and M3b built, tested on the phone (A059, Android 16), released as v0.2.0.
Goal:         Lightweight Android agent app with threads, memory, skills, MCP, subagents, web search,
              YouTube summaries, HTML artifacts and phone tools.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M4 step 1.
Decisions:    D-001 to D-015, D-017 to D-031 accepted; D-016 superseded. None proposed.
Build:        `gradle testReleaseUnitTest assembleRelease` passes; release APK ~3.7 MB.
Device check: cost strip, usage sheet, model switch, service cards, model search, balances, thread
              rename and full names, upgrade from 0.1.0 (Room migration 1→2).
Known gaps:   Routing fallback not yet seen live. youtube_summarize cost not recorded. No
              "interrupted" mark on a killed answer. DAO tests need a device.
Later list:   Projects, custom system prompt, answer styles, personas (plan, "Later").
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
              API keys in secrets.properties (gitignored). Remote: github.com/shourovrm/jonaki.
Next action:  M4 memory and compaction.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, docs/plans/2026-10-02-v1-plan.md, docs/mockups/.
