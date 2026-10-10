# Status — 2026-10-11 (v1.4.8 released)

Phase:        v1.4.8 released on GitHub: 1.4.7 plus web_fetch reading PDF links (D-185).
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at the same log.
Decisions:    Every entry up to D-176 is accepted (user, 2026-10-10: the shipped ones), except
              D-016, superseded. Proposed: D-177 to D-185.
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-11).
              Version 1.4.8, code 21, tag v1.4.8, pushed. Room v12.
Phone (A059): 1.4.7 installed with adb (2026-10-10); 1.4.8 is not installed. The app was not
              opened afterwards, so nothing of 1.4.7 was looked at.
In 1.4.8:     web_fetch downloads a PDF link into the private cache `web-fetch-pdf` and
              returns its pages as text; nothing is saved in the thread folder (D-185).
Not checked:  D-185 on the phone with a real PDF link. The setup cards on any screen
              (Settings > About > Show setup cards), and at 360 dp with font scale 1.3.
              From 1.4.5 and 1.4.6: Serper live, a real update download and install, D-180
              cards, D-181 to D-183 pages.
Open:         GLM 5.3 Flash saw a picture as blank (try Gemini); remove stealth/space-bunny-alpha.
              The mockup's "No model yet" composer is not built. A scanned PDF link gives
              empty pages (D-185).
Next action:  Install 1.4.8 and try a PDF link; then look at the setup cards on the phone.
