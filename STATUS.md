# Status — 2026-10-10 (v1.4.5 released)

Phase:        v1.4.5 released on GitHub with D-177 to D-181. Older open issues: the session log.
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at the same log.
Decisions:    Every entry up to D-176 is accepted (user, 2026-10-10: the shipped ones), except
              D-016, superseded. Proposed: D-177 to D-182.
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-10).
              Version 1.4.5, code 18, tag v1.4.5, pushed. Room v12.
Phone (A059): runs 1.4.4; not connected when 1.4.5 was built, so nothing of 1.4.5 was seen.
In 1.4.5:     Serper search (D-177); update button in Settings > About (D-178); withdrawn model
              falls back to the thread's model (D-179); web_fetch asks before a composed address
              after outside content (D-180); Subagents page (D-181); empty-JPEG safeguard.
Not checked:  Serper against the live service (no key); a real update check, download and
              install; D-180 cards in a real research thread; the Subagents page at 360 dp and
              font scale 1.3; strings of D-178 not reviewed. D-174 leftovers as before.
Open:         Why GLM 5.3 Flash saw an attached picture as blank (no cause proven; test the same
              picture with Gemini). Remove stealth/space-bunny-alpha from the OpenRouter list.
Unreleased:   D-182, a Vector image generation section in Settings > Models; not seen on a phone.
Next action:  Install the build, add the Serper key, run one search; then the checks above.
Key files:    AGENTS.md, DECISIONS.md, PRODUCT.md, README.md, docs/session-summary-2026-10-10.md.
