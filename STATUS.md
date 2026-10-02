# Status — 2026-10-02 (M0 to M3 done, v0.1.0)

Phase:        M0 to M3 built, tested on the phone (A059, Android 16) and released as v0.1.0.
Goal:         Lightweight Android agent app with threads, memory, skills, MCP, subagents, web search,
              YouTube summaries, HTML artifacts and phone tools.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M4 step 1.
Decisions:    D-001 to D-015, D-017 to D-026 accepted; D-016 superseded. None proposed.
Waiting on:   User's choice for cost display and in-thread model picker (mockups requested).
Build:        `gradle testReleaseUnitTest assembleRelease` passes; 221 JVM tests; release APK ~3.5 MB.
Device check: search with fallback, web_fetch, write_file approval, Stop, killed app keeps partial
              text, YouTube summary via Gemini (retry after 503), light and dark themes.
Known gaps:   No thread delete or rename in the UI. No "interrupted" mark on a killed answer.
              DAO tests need a device. Default models for non-OpenRouter presets unchecked (M10).
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2 (D-023).
              API keys in secrets.properties (gitignored). Remote: github.com/shourovrm/jonaki.
Next action:  Cost and model-picker mockups, then M4 memory and compaction.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, docs/plans/2026-10-02-v1-plan.md, docs/mockups/.
