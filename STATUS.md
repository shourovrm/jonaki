# Status — 2026-10-25 (v1.5.0 built, English only, Worker live)

Phase:        D-186 to D-191 implemented. The app ships in English only (D-191): no
              values-bn, no Bangla in prompts, previews or samples; About keeps "Jonaki is
              Bangla for firefly." Jonaki Free's Worker is deployed at
              jonaki-free.shourovrm.workers.dev with its own KV namespace and secrets.
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at this file.
Decisions:    D-177 to D-191 proposed (D-186 to D-191 from 2026-10-25).
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-25,
              see D-191 Outcome). Version 1.5.0, code 22. Room v13.
Phone (A059): 1.5.0 installed over USB; the APK is also in Downloads/jonaki-1.5.0.apk.
Worker:       Live and working (v4): Free plan, 250 calls a day over all phones, 20 per phone,
              model @cf/qwen/qwen3-30b-a3b-fp8 with thinking off, GET /v1/limit. Verified with
              curl: plain answer, tool call, streaming. Not yet tried from the app on the phone.
Checked:      Settings home with six groups, setup card 1, Memory screen with To review and
              the help line, thread list review row. Not yet on a device: the error card, the
              No-model composer, the Safety check switch, the Memory log with rows, the new
              run notification with Stop, the withdrawn-skill removal.
Open:         The thread Safety check switch only turns the check off ("Off in Settings"
              when the default is off). The memory line text is built in Kotlin. Test data in
              other scripts stays in tests (D-191).
Next action:  Set CF_AI_TOKEN, then try Jonaki Free from the setup card on the phone; look at
              the run notification during a long task; tag v1.5.0 when the user says so.
