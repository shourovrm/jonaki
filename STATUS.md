# Status — 2026-10-02 (M0 mostly done)

Phase:        M0 done except step 0.1 and S-2 (need the phone); S-5 deferred to before M10.
Goal:         Lightweight Android agent app with threads, global and thread memory, skills, MCP, subagents,
              web search, YouTube summaries, HTML artifacts and phone tools.
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M1 step 1.
Decisions:    D-001 to D-015 and D-017 to D-022 accepted; D-016 superseded; D-023 (Room 2.7.2 with
              bundled SQLite) proposed.
Blockers:     D-023 ruling before M1 step 2 (core/storage). Phone over USB for 0.1, S-2 and device checks.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 12 JVM tests; release APK 617 KB.
Spikes:       S-1 FTS5 trigram works (SQLite 3.46.0). S-3 Gemini YouTube works on gemini-3.8-flash
              (2.5-flash closed to new users; 19-min video = 105,878 tokens). S-4 Tavily and Ollama work.
Environment:  /opt/android-sdk (platform 35), OpenJDK 21, system Gradle 9.7.1, AGP 8.7.3 stack.
              API keys in secrets.properties (gitignored); recorded responses in testdata/.
Next action:  M1 step 1: core/provider-api and providers/openai-compatible with SSE streaming.
Key files:    AGENTS.md, DECISIONS.md, docs/plans/2026-10-02-v1-plan.md, testdata/README.md.
