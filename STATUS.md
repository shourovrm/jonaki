# Status — 2026-10-10 (v1.4.7 released)

Phase:        v1.4.7 released on GitHub: 1.4.6 plus D-183 and D-184. Older issues: the log.
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at the same log.
Decisions:    Every entry up to D-176 is accepted (user, 2026-10-10: the shipped ones), except
              D-016, superseded. Proposed: D-177 to D-184.
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-10).
              Version 1.4.7, code 20, tag v1.4.7, pushed. Room v12.
Phone (A059): 1.4.7 installed with adb (dumpsys: versionName=1.4.7, code 20). The app was not
              opened afterwards (the launcher was in front), so nothing of 1.4.7 was looked at.
In 1.4.7:     First-run setup cards, "Left to set up" on the thread list, "Show setup cards" in
              About (D-184); service cards in the vector and video sections (D-183); new About
              and README wording.
Not checked:  The setup cards on any screen (open them with Settings > About > Show setup
              cards), and at 360 dp with font scale 1.3. Everything listed for 1.4.5 and 1.4.6:
              Serper live, a real update download and install, D-180 cards, D-181 to D-183 pages.
Open:         GLM 5.3 Flash saw a picture as blank (try Gemini); remove stealth/space-bunny-alpha.
              A thread without a model still shows "No model. Add one in Settings." only after
              a send; the mockup's "No model yet" composer is not built.
Next action:  Look at the setup cards on the phone and fix what the first look shows.
