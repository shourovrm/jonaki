# Status — 2026-10-03 (v1.0.0 plus local models)

Phase:        v1.0.0 released; since then on main: About page (D-130), web_fetch renders JS pages
              (D-131), request times (D-132), local models with llama.cpp (D-133).
Active plan:  docs/plans/2026-10-02-v1-plan.md; resume line lists the open local-model steps.
Decisions:    D-001 to D-015, D-017 to D-033, D-048, D-128, D-129 accepted; D-016 superseded.
              Proposed: D-034 to D-047, D-049 to D-070, D-080, D-081, D-085, D-086, D-090 to D-127,
              D-130 to D-134.
Build:        `gradle testReleaseUnitTest assembleRelease` passes; Room v9; APK 13.2 MB (6.3 MB is
              libjonaki_llama.so, stored uncompressed). Fresh clones need
              `git submodule update --init --depth 1`.
Phone (A059): About page checked; Local models page, download (0.8B, about 2.5 MB/s) and chat
              checked; prompt reuse 2,229 of 2,285 tokens; App delay 15 to 53 ms (target < 100).
              The D-132 save crash was found there and fixed (92a0319).
Not checked:  web_fetch rendering on the phone; tool calls by a local model (0.8B did not call
              phone; 2B untested); download resume after a dropped connection.
Spend:        $0.048 of the $1 for M9 to M11 checks.
Next action:  Local tool list choice and Tools section; 2B tool-call test; project memory.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/plans/2026-10-02-v1-plan.md.
