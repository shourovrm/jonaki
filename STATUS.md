# Status — 2026-10-25 (v1.5.0 built, not released)

Phase:        D-186 to D-190 implemented from the three v2 mockups (docs/mockups/*-v2.html):
              first-run deck with free options, Settings regrouped, memory and skill
              proposals visible, Jonaki Free service, YouTube retry with Flash Lite.
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at this file.
Decisions:    D-177 to D-185 still proposed; D-186 to D-190 proposed (2026-10-25).
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` (see the Outcome
              lines of D-186 to D-190 for the run on 2026-10-25). Version 1.5.0, code 22. Room v13.
Phone (A059): on this PC over USB; 1.4.7 installed before this session.
Worker:       server/jonaki-free is written and the KV namespace jonaki-free-limits exists
              (id in wrangler.toml), but the Worker is NOT deployed: the tool deploy was
              blocked as a production deploy. Deploy: `cd server/jonaki-free && npx wrangler
              login && npx wrangler secret put ACCOUNT_ID && npx wrangler secret put
              CF_AI_TOKEN && npx wrangler deploy` (CF_AI_TOKEN: an API token with Workers AI
              Read, made at dash.cloudflare.com/profile/api-tokens). Until then "Jonaki Free"
              answers with a connection error.
Not checked:  Every new screen on the phone (first-run deck v2, Settings v2, memory lines,
              Memory log, review row, error card, Safety check switch) at 360 dp and 1.3.
Open:         The memory line's text ("Remembered 2 facts") is built in Kotlin, not in
              strings, so Bangla phones see it in English. The thread Safety check switch
              only turns the check off; with the Settings default off it cannot turn it on.
              D-187 strings changed after the Opus review: "Review web facts", "USD / 1M".
Next action:  Install 1.5.0, walk through the setup cards (Settings > About > Show setup
              cards) and the Settings pages on the phone; then deploy the Worker.
