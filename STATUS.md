# Status — 2026-10-10 (v1.4.6 released)

Phase:        v1.4.6 released on GitHub: 1.4.5 (D-177 to D-181) plus D-182. Older issues: the log.
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at the same log.
Decisions:    Every entry up to D-176 is accepted (user, 2026-10-10: the shipped ones), except
              D-016, superseded. Proposed: D-177 to D-183.
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-10).
              Version 1.4.6, code 19, tag v1.4.6, pushed. Room v12.
Phone (A059): 1.4.6 installed with adb (dumpsys: versionName=1.4.6, code 19). The user asked
              for no testing yet, so the app was not opened and nothing was looked at.
Since 1.4.4:  Serper search (D-177); update button in Settings > About (D-178); withdrawn model
              falls back to the thread's model (D-179); web_fetch asks before a composed address
              after outside content (D-180); Subagents page (D-181); Vector image generation
              section in Settings > Models (D-182); empty-JPEG safeguard.
Not checked:  Everything above on the phone. Serper against the live service (no key); a real
              update check, download and install; D-180 cards in a research thread; new pages
              at 360 dp and font scale 1.3; strings of D-178 not reviewed. D-174 leftovers.
Open:         GLM 5.3 Flash saw a picture as blank (try Gemini); remove stealth/space-bunny-alpha.
Unreleased:   D-183, service cards in the vector and video sections; built, not installed, not
              seen on a phone. No version bump yet (user's word). First-run design in progress.
