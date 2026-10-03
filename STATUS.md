# Status — 2026-10-03 (v1.0.0 plus three merged changes)

Phase:        M0 to M11 built and released as v1.0.0; Jev left out (D-129). After the release:
              About as a short page (D-130), web_fetch renders JavaScript pages (D-131), request
              times in the usage sheet (D-132); all merged on main, none checked on the phone yet.
Active plan:  docs/plans/2026-10-02-v1-plan.md; resume with the phone checks of D-130 to D-132.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129 accepted; D-016 superseded.
              Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085, D-086, D-090 to D-127,
              D-130 to D-132.
Build:        `gradle testReleaseUnitTest assembleRelease` passes; Room v9; APK about 6.8 MB.
Budgets:      A059 (Nothing Phone 3a), Android 16: cold start 300 to 338 ms; idle 88 MB PSS;
              prompt + 18 tools 4,875 tokens (target 3,500, kept by user ruling); first token
              now recorded as "Service wait" and "App delay" (D-132), not yet measured.
Blocked:      adb install refused by the permission check; the user installs or allows it.
Research:     docs/research/local-models-2026-10-03.md (llama.cpp provider recommended); awaits
              the user's decision.
Spend:        $0.048 of the $1 for M9 to M11 checks.
Next action:  Phone checks: About page, a JavaScript page through web_fetch, request times.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
