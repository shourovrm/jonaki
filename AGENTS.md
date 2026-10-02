# Agent rules for Jonaki

Jonaki (জোনাকি, "firefly") is a lightweight Android app that runs an AI agent
in a mobile chat interface. The user brings API keys for OpenAI-compatible
providers (OpenRouter, DeepSeek, GLM, Xiaomi MiMo, Ollama Cloud, local Ollama)
and Gemini. The agent works in threads; each thread has its own memory and
files, and all threads share a global memory and a skill library. The agent
searches the web, summarises YouTube videos, writes HTML reports and
presentations, runs small JavaScript or Python programs, uses phone features
(calendar, reminders, notifications), connects to MCP servers and delegates
work to subagents. Coding is a secondary capability. The full history of how
the design was reached is in `docs/planning-chat-2026-10-02.md`.

## Session start, in this order
1. `STATUS.md`: the present state (20 lines at most).
2. `DECISIONS.md`: the last five entries, and every entry with status
   `proposed`.
3. The active plan in `docs/plans/` named in `STATUS.md`, including its
   progress checklist and its "Resume from" line.
Do not start work before reading these.

## Control
- The user decides; the agent proposes. Present an approach in chat and wait
  for approval before writing code that changes the design.
- These always need explicit approval: a change to an accepted decision, a new
  dependency, a new Android permission, anything that costs money, any
  deletion of user files or project history.
- Decisions go into `DECISIONS.md` with status `proposed`. Only the user
  changes a status to `accepted` or `rejected`.
- Define every technical term the first time it appears in chat or in a
  document, say what a number means for the project, and give one concrete
  example before a general rule.

## Session end, always
1. Tick the finished steps in the active plan and rewrite its "Resume from"
   line, so that the next session can start at the exact step.
2. Rewrite `STATUS.md`, 20 lines at most.
3. Add or update `DECISIONS.md` entries, including the `Outcome` field of
   older decisions when new evidence exists.
4. Commit (see Git).

## Code: one job per module
Jonaki follows the Unix rule that each program does one job and connects to
the others through a plain interface. In Jonaki the program is a Gradle
module.

- **One tool is one module.** `tools/web-search/`, `tools/read-file/` and so
  on. A tool module depends only on `core/tool-api`, never on another tool.
  Adding a tool is a new module plus one line in the tool registry
  (`app/.../ToolRegistry.kt`); removing it is the reverse.
- **The same rule applies to every swappable part:** one module per model
  provider (`providers/`), per search backend (`search/`), per code runtime
  (`runtimes/`), per screen (`feature/`).
- **Dependencies point one way:** `app` → `feature/*` → `core/*`, and
  `tools/*`, `providers/*`, `search/*`, `runtimes/*` → `core/*`. Nothing in
  `core/` depends on a tool, provider, backend, runtime or screen.
- **Text in, text out.** A tool takes JSON arguments and returns plain text
  for the model. Large output is written to a file in the thread folder and
  the tool returns the first part, the file path and a notice that names the
  next call (for example `Use offset=200 to continue`).
- **No hidden state.** Tools are stateless. State lives in the database or in
  the thread folder, where another tool can read it.
- **Fail loudly.** A failing tool returns an error text that says what failed
  and what the model can try next. It never returns an empty success.
- **Every tool declares its cost to the user:** whether it only reads or
  changes something (changes need the user's approval), and which optional
  capability it needs (for example Python).

## Code style
- Kotlin, Jetpack Compose, coroutines. Follow the Kotlin coding conventions.
- Write for a human reading the code in six months. Full names
  (`threadMemory`, never `tm`), no nested conditional expressions, no
  one-liners doing three jobs.
- One idea per function. Comment the why, never the what.
- Look for an existing helper in the repository before writing a new one.
  No abstraction until there are two real callers, except the module
  interfaces in `core/` that this file requires.
- Every module has unit tests that run on the JVM. When the logic is
  non-trivial (the agent loop, truncation, memory extraction, permission
  timeouts), write the test first. The agent loop is tested against a fake
  provider that replays scripted responses.

## Runtime rules (decided in D-005)
- Stream every model response; show each tool step live with a Stop button.
- Every tool call has a time limit; every user message has a step budget.
- The agent loop runs in a foreground service and saves every step to the
  database as it happens, so that nothing is lost if Android stops the app.
- The system prompt stays identical between requests so that the provider's
  prompt cache works; the time and other changing details go into the latest
  message.
- Long threads are summarised between messages, never during a tool run, and
  the original messages stay in the database.

## Build and release
- Release APK only; never build a debug APK:
  `gradle testReleaseUnitTest assembleRelease`. Install on the test phone
  with `adb install -r app/build/outputs/apk/release/app-release.apk`.
- System Gradle (9.7.1), no wrapper. Plugin and library versions match the
  user's other apps (BD-calendar, hujur-tracker) so the Gradle cache in
  `~/.gradle` is reused: AGP 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Compose
  BOM 2024.11.00, Room 2.6.1. Changing a version is a decision.
- SDK `/opt/android-sdk`, compileSdk and targetSdk 35, minSdk 26,
  arm64-v8a only, Java 17 target.
- Signing: `jonaki.keystore` in the repository root, gitignored, created with
  `keytool -genkeypair -keystore jonaki.keystore -alias jonaki -keyalg RSA
  -keysize 2048 -validity 10000 -storepass android -keypass android
  -dname "CN=Jonaki"` if missing.
- Distribution: GitHub release APK (D-019). Google Play requirements are out
  of scope for now.

## Verification
Claim nothing you have not run. "Done" means the module builds, its tests
pass and, for anything visible, it was checked on an emulator or device.
Report failures as failures, with the decisive error line. Report skipped
steps as skipped.

## Writing
Plain, clean, complete prose in every chat message and document. No
compressed or telegraphic style. Every claim about the app's behaviour cites
the command or test that showed it. Prefer one exact number over three
adjectives, and never present an estimate as a measurement.

## Git
- The agent commits automatically after every incremental change that
  builds and passes its tests: one logical change per commit.
- Subject line at most 80 characters, conventional prefix (`feat:`, `fix:`,
  `docs:`, `chore:`, `test:`, `refactor:`). A body only when the why is not
  obvious.
- AI attribution is allowed: end the message with the `Co-Authored-By`
  trailer.
- `DECISIONS.md`, `STATUS.md` and plan updates go in the same commit as the
  change they describe.
- The agent never pushes unless told to.
- API keys, keystores and `local.properties` are never committed;
  `.gitignore` enforces this.

## Files and places
- `STATUS.md`: present state only.
- `DECISIONS.md`: numbered decision log that grows as needed; entries are
  short so that reading the file costs few tokens.
- `secrets.properties`: local API keys for spikes and tests (gitignored).
- `docs/plans/`: implementation plans with a progress checklist each.
- `docs/planning-chat-2026-10-02.md`: the planning conversation that produced
  the design (read-only history).
- `app/`, `core/`, `feature/`, `tools/`, `providers/`, `search/`,
  `runtimes/`: Gradle modules, created as the plan reaches them.
