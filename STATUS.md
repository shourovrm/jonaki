# Status — 2026-10-03 (main after v0.8.0: M9, M10 MCP, Later list, revamp, Bangla)

Phase:        M0 to M9 built; M10 MCP and presets built, Jev on an unmerged branch; M11 step 1 done.
              Also merged: Later list (instructions, styles, personas, projects, incognito), known-gap
              fixes, Lantern revamp (D-123), Permissions and About (D-124), subagent rows (D-126).
Active plan:  docs/plans/2026-10-02-v1-plan.md, resume from M11 step 2.
Decisions:    D-001 to D-015, D-017 to D-032, D-048 accepted; D-016 superseded. Proposed: D-033 to D-047,
              D-049 to D-070, D-080, D-081, D-085, D-086, D-090 to D-126.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 1,056 JVM tests; APK ~6.7 MB; Room v8.
Device check: 0.8.0 on A059 passed subagents, Auto approvals, parallel calls, context sheet, gallery,
              code viewer, Reddit skill. Nothing after 0.8.0 is checked on the phone yet (phone was
              disconnected at 06:55): revamp, M9, MCP, styles, projects, incognito, Bangla, tables.
Blocked:      Jev branch merge refused by the permission check; waits for the user.
Spend:        $0.048 of the $1 for M9-M11 checks (Jev spike and M10 comparison).
Environment:  /opt/android-sdk, OpenJDK 21, Gradle 9.7.1, AGP 8.7.3, Room 2.7.2.
Next action:  Reconnect the phone; device check; performance budgets; releases v0.8.0 then v1.0.0.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
