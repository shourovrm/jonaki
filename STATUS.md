# Status — 2026-10-03 (v1.0.0)

Phase:        M0 to M9 and M11 built; M10 MCP and presets built, Jev on an unmerged branch. Also: Later
              list, gap fixes, Lantern revamp (D-123), Permissions and About (D-124, D-127, D-128),
              Bangla (D-125), subagent rows (D-126), Settings sub-pages and reviewed wording (D-128).
Active plan:  docs/plans/2026-10-02-v1-plan.md, v1 released; resume with the Jev merge (user) and M10's Jev check.
Decisions:    D-001 to D-015, D-017 to D-032, D-048 accepted; D-016 superseded. Proposed: D-033 to D-047,
              D-049 to D-070, D-080, D-081, D-085, D-086, D-090 to D-128.
Build:        `gradle testReleaseUnitTest assembleRelease` passes, 1,100 JVM tests; APK 6.75 MB; Room v8.
Budgets:      A059, Android 16: cold start 300 to 338 ms (am start -W, 3 runs; target < 1 s); APK 6.75 MB
              (< 15 MB); idle 88 MB PSS (< 150 MB); prompt + 18 tools 4,875 tokens, context-sheet estimate
              (target 3,500: missed); first streamed token not measured (no request-log timestamps).
Device check: 1.0.0 on A059: Settings sub-pages and search; earlier today the revamp, tables, run_code,
              MCP (DeepWiki), exact reminder (fired on time), styles, incognito, Bangla, subagent rows.
Blocked:      Jev branch merge refused by the permission check; waits for the user.
Spend:        $0.048 of the $1 for M9 to M11 checks.
Next action:  User reviews the Bangla, the proposed decisions and the Jev merge; trim tool schemas.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
