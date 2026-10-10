# Session log, 2026-10-10

This log records one working session on Jonaki, from v1.4.1 to the test build
v1.4.2. The next session starts from the section "Open issues" below. Each
decision named here has its full entry in `DECISIONS.md`.

## State at the end of the session

- `main` is at the commit that adds this file, about 98 commits ahead of
  `origin/main`. Nothing is pushed. No release was made: v1.4.2 has no tag and
  no GitHub release, and the README still links v1.4.1.
- The test phone (A059) runs v1.4.2, version code 15, installed with
  `adb install -r`, so its threads, keys and settings were kept.
- `gradle testReleaseUnitTest test assembleRelease --offline` exits with 0 on
  that commit.
- Decisions D-157 to D-173 are all `proposed`. Only the user changes a status.
- Spend on paid test requests: about $0.05 of the $0.50 the user allowed
  (two pictures of $0.014 each, a few short chat messages, one refused
  request that cost nothing).

## Open issues

### Reported by the user on v1.4.2, not yet diagnosed

1. **No Vector chip and no place to set a vector model.** The user looked for
   them on the phone and found neither. What the code does: there is no
   separate vector setting. A vector model is an image model whose OpenRouter
   entry lists `output_format` as exactly `["svg"]` (six Recraft models). It is
   added through Settings > Models > Image generation > OpenRouter > Add
   model, and the Vector chip appears only after one is added. Nothing on any
   screen says this. To check first in the next session: whether the image
   model picker lists the Recraft vector models at all and marks them "SVG",
   and whether adding one makes the chip appear. A likely fix whatever the
   cause: make vector models findable, for example a "Vector" filter or
   section in the picker and a hint where the chip would be.

### Built but never seen on a screen

None of these has been looked at on a device. Each is covered by unit tests
only.

- D-164: the copy button on a red error; a failed subagent's reason line;
  `:batch` models hidden from the picker.
- D-166: image service cards; prices in the image picker; the Gemini image
  service.
- D-167: the "Image model" section in the "Model for this thread" sheet.
- D-168: privacy icons and legend in the Providers sheet; the chosen
  provider's price on a model's row (added in D-173).
- D-169: the video tool, its Settings section and the video card.
- D-170: the vector tool, the "SVG" tag and the SVG preview in a WebView.
- D-171 and D-173: the Picture, Vector and Video chips and the details line
  with the price.
- D-172: the "Image quality" row; reference pictures; the prompt sheet opened
  from a picture or video step; the whole prompt on the approval card.
- D-161: a run with the screen off (the wake lock).
- D-163: Delete in multi-select, and multi-select in Memory.
- D-158: a fresh install with no service. Checking it means wiping the app's
  data, which needs the user's word.

### Never tried against the real service

- A video request to OpenRouter (`POST /api/v1/videos`, polling, download).
  The parsing follows the documentation only.
- A vector request. The form in which the SVG returns is unknown; base64,
  plain text and a data URI are accepted.
- A picture with a reference picture. The `data:` URL form is assumed.
- Any picture through Gemini directly. Google's pages document a newer
  "Interactions" API first; the field for the aspect ratio and the list of
  model ids are unconfirmed.
- A chat request after a media-mode send with Gemini as the chat model. The
  history then ends in a tool result.
- Providers with fallbacks switched off, and the HTTP 404 assumed for "no
  chosen provider available".
- One request of each kind would cost about $0.25 in total.

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
  Bioluminescence Explained" and "Firefly Explanation in Bangla"; the image
  model FLUX.2 Klein 4B, which ranks 126 of 168 on quality and was added only
  because it is cheap; a language fact [41] waiting for approval on the
  Memory screen.

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
