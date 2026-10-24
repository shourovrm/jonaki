# Status — 2026-10-25 (v0.9.0 released)

Phase:        D-186 to D-191 implemented. The app ships in English only (D-191): no
              values-bn, no Bangla in prompts, previews or samples; About keeps "Jonaki is
              Bangla for firefly." Jonaki Free's Worker is deployed at
              jonaki-free.shourovrm.workers.dev with its own KV namespace and secrets.
Active plan:  docs/plans/2026-10-02-v1-plan.md; its resume line points at this file.
Decisions:    D-177 to D-191 proposed (D-186 to D-191 from 2026-10-25); D-192 accepted
              (version names 0.x from 0.9.0).
Build:        `gradle testReleaseUnitTest test assembleRelease --offline` passes (2026-10-25,
              see D-191 Outcome). Version 0.9.0, code 23, tag v0.9.0, pushed and released on GitHub. Room v13.
Phone (A059): 0.9.0 installed over USB; the APK is also in Downloads/jonaki-0.9.0.apk.
Worker:       Live and working (v4): Free plan, 250 calls a day over all phones, 20 per phone,
              model @cf/qwen/qwen3-30b-a3b-fp8 with thinking off, GET /v1/limit. Verified with
              curl: plain answer, tool call, streaming. Tried from the app on the phone: a message answered through Jonaki Free.
Checked:      Settings home with six groups, setup card 1, Memory screen with To review and
              the help line, thread list review row, Jonaki Free end to end, the Python page
              (documents add-on now reads Installed; package names compared without case). Not yet on a device: the error card, the
              No-model composer, the Safety check switch, the Memory log with rows, the new
              run notification with Stop, the withdrawn-skill removal.
Open:         The thread Safety check switch only turns the check off ("Off in Settings"
              when the default is off). The memory line text is built in Kotlin. Test data in
              other scripts stays in tests (D-191).
Next action:  Set CF_AI_TOKEN, then try Jonaki Free from the setup card on the phone; look at
              the run notification during a long task; tag v1.5.0 when the user says so.
