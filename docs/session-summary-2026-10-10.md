# Session log, 2026-10-10

This log records one working session on Jonaki, from v1.4.1 to the test build
v1.4.2. The next session starts from the section "Open issues" below. Each
decision named here has its full entry in `DECISIONS.md`.

## State at the end of the session

- `main` is at the commit that adds this file, about 98 commits ahead of
  `origin/main`. Nothing is pushed. No release was made: v1.4.2 has no tag and
  no GitHub release, and the README still links v1.4.1.
- The test phone (A059) runs v1.4.2, version code 15, built from the last
  commit of the afternoon and installed with `adb install -r`, so its
  threads, keys and settings were kept.
- `gradle testReleaseUnitTest test assembleRelease --offline` exits with 0 on
  that commit.
- Decisions D-157 to D-174 are all `proposed`. Only the user changes a status.
- Spend on paid test requests: about $0.05 in the morning and $0.25 in the
  afternoon, each against a $0.50 allowance from the user.

## Open issues

### Found in the phone pass of the afternoon, not fixed

The afternoon session tested the test build on the A059 for $0.25 of paid
requests (credit used rose from $12.349 to $12.598). Fixed that day: the
Providers sheet shows each provider's cache price; a vector picture's
preview is drawn (it was a blank box); the "Image quality" row is aligned
with the cards. These remain:

1. **A failed send in media mode gives no reason.** The step shows "Failed"
   and the model's key, and nothing else, because no chat model is there to
   explain it. Seen with Recraft V4 Styles Vector, which answers HTTP 400
   "input_references: must have between 1 and 10 items". A likely fix:
   `DirectToolRun` hands back the last tool output, and `runMediaMode` saves
   a red error row when it is an error.
2. **A model that needs a reference picture can be chosen for media mode**,
   where no reference can be sent. The model list declares it
   (`input_references` with `min` 1), so such a model can be left out of the
   chips or marked.
3. **Vector models are found only by searching the picker.** The picker
   does tag the six Recraft vector models "SVG", and the Vector chip appears
   once one is added; nothing on a screen says so. D-174 proposes the
   answer.
4. **Model keys shown in place of names**: the step line, the approval card
   and the prompt sheet show "openrouter:recraft/recraft-v4.1-flash".
   "Running generate_video…" and "Allow generate_image?" show tool ids.
5. **The approval card for a picture or video names no price.**
6. **The thread's model sheet**: fully opened it draws under the status
   bar; "Default" and "Medium" in the Thinking control are cut to "Defa…"
   and "Medi…" at 360 dp; raster and vector models share one "Image model"
   list with one selected radio each.
7. **The steps summary of a turn** ("2 steps 37 s $0.0043") counts the chat
   model's tokens only, not the picture the turn paid for (about $0.04).
8. **The chat does not always open at its newest message** after the app is
   restarted; seen once in three restarts.

### Still not seen on a screen

- D-164: the copy button on a red error; a failed subagent's reason line.
- D-163: Delete in multi-select, and multi-select in Memory.
- D-161: a run with the screen off (the wake lock).
- D-158: a fresh install with no service. Checking it means wiping the
  app's data, which needs the user's word.

### Still not tried against the real service

- Any picture through Gemini directly, and Gemini as the chat model after
  a media-mode send.
- High image quality, and a video that outlives one tool call.
- Providers with fallbacks switched off, and the HTTP 404 assumed for "no
  chosen provider available".

### Waiting for the user

- D-174: the settings row under the media chips, drawn in
  `docs/mockups/media-controls.html` (option A recommended, option B beside
  it). Nothing is built.

### Owed work

- **String review.** Every English and Bangla interface string of D-157 to
  D-173 was written by a Sonnet subagent. `AGENTS.md` requires an adversarial
  review before strings are committed; it has not been done. Do it on the
  phone, one screen at a time.
- **Attached pictures in media mode.** The chips are disabled while a file is
  attached. With D-172 a photo attached in Picture mode could go along as a
  reference.
- **Two network changes the user has not ruled on:** give up on a model call
  that sends nothing for 30 to 45 seconds (today 120), and end a subagent
  after two or three network failures in a row. See D-164 for the reasoning.
- **Other image services.** The user wants image services beyond OpenRouter
  and Gemini and has not yet named them.
- **A known gap in D-167:** an old picture step whose call named no model
  shows the thread's present image model on its line.
- **Leftovers on the phone from testing:** the threads "Firefly
  Bioluminescence Explained", "Firefly Explanation in Bangla" and "Dusk
  Firefly Paper Cut" (a picture, a failed vector, a video, an edited picture
  and a vector, $0.249); the image models FLUX.2 Klein 4B and Recraft V4.1
  Vector, added for tests; the file a-simple-firefly-icon-two-colours.svg in
  Download/Jonaki; a language fact [41] waiting for approval on the Memory
  screen.

## Work done, in order

| Decision | What it adds |
|---|---|
| D-157 | Short generated thread names; one line per thread in the list; "Web search" removed from the thread's ⋮ menu |
| D-158 | A fresh install has no service and no model |
| D-159 | Chosen providers for an OpenRouter model (the Providers sheet) |
| D-160 | `generate_image` through OpenRouter |
| D-161 | A wake lock while a run is going (new permission `WAKE_LOCK`, approved by the user) |
| D-162 | A fresh start reopens the thread left under 30 minutes ago, else a new thread; saved drafts |
| D-163 | Selecting several threads or memory facts to delete |
| D-164 | Copyable errors; a failed subagent's reason; `:batch` models hidden |
| D-165 | The answer-language rule in the system prompt |
| D-166 | Image services as cards (OpenRouter, Gemini); prices in the image picker |
| D-167 | A thread's own image model |
| D-168 | Privacy icons in the Providers sheet; the message for a limit at the service (HTTP 429) |
| D-169 | `generate_video` |
| D-170 | `generate_vector_image` |
| D-171 | Picture mode: the user's words go straight to the image model |
| D-172 | Image quality; reference pictures; guidance for instructional pictures; the full prompt of a step; a day cache for provider lists |
| D-173 | Media mode for pictures, vectors and videos; the chosen provider's price on a model's row |

## Checked on the phone during the session

These were seen working on A059, on the builds of the morning:

- D-157: one-line thread list; the ⋮ menu without "Web search"; a new thread
  renamed "Firefly Bioluminescence Explained".
- D-159: the Providers sheet with prices; DeepInfra ticked; a message
  answered on that model.
- D-160: an image model added; one picture made in 7.8 seconds after "Allow
  once"; the thread's cost rose to $0.015.
- D-162: a fresh start opened a new thread, then reopened a left thread with
  its typed draft; the list showed "Draft: a draft to keep".
- D-163: two threads selected, tinted with a check mark, "2 selected".
- D-165: asked for Bangla once, then a follow-up in English; the second
  answer stayed Bangla (Claude Haiku 5.5).

## Findings worth keeping

- **The 22-minute run.** A run can last about 22 minutes by design when the
  network half-dies: a 120-second read timeout, one retry per turn, a
  15-minute limit for a Researcher. That run does not prove the phone slept.
- **Nano Banana 2.1 and HTTP 429.** OpenRouter serves it through one
  provider, Google AI Studio, whose own quota was used up. The user's key and
  credit were fine. Recorded in
  `testdata/openrouter/image-error-429-upstream-quota.json`.
- **Why a ChatGPT infographic looked better than Jonaki's.** Same image model
  family. Jonaki's chat model wrote a prompt asking for a "flat minimal"
  sheet; the tool gave no guidance for instructional pictures and sent no
  quality. D-171 to D-173 answer this.
- **Image model value** (Artificial Analysis arena, undated): Nano Banana 2.1
  is near the top at about $0.034 a picture; GPT Image 2.5 Sunburst is first
  at about $0.21 at top quality; Recraft V4.1 Flash is the cheapest usable at
  $0.007.
- **Video model value:** MiniMax Hailuo 3 Max is within 26 points of the top
  at $0.08 a second; Grok Imagine Video 1.5 Lite is the cheapest decent at
  $0.03 a second at 720p.
- **Provider data policies** are in no documented OpenRouter list. They come
  from `api/frontend/v1/all-providers`, the website's own list, which may
  change without notice.

## How the session was run

- Up to three Sonnet subagents at a time, each in its own git worktree, none
  allowed to spawn others. The main session reviewed, merged, built and did
  the phone checks.
- Merges went through a short-lived integration branch and a fast-forward of
  `main`. The subagents' worktrees and branches were left in place.
- **A mistake to avoid.** During one phone check the user had another app
  (Google Keep) in front, and the script pressed Back once and sent one line
  of text before this was noticed. The phone scripts in the scratchpad now
  refuse to press or type unless `app.jonaki` is the focused window. Before
  any `adb shell input`, check the focused window, and treat the phone as the
  user's unless they say it is free.
