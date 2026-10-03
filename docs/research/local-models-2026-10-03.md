# On-device models for Jonaki (research, 2026-10-03)

Status: research only; nothing is built. The user asked how Jonaki could
download and run a model that fits the phone, as PocketPal and Google AI Edge
Gallery do.

## Recommended runtime

llama.cpp (MIT) behind Jonaki's own small JNI layer, in a new module
`providers:local-llama` that implements the existing `ChatProvider`
interface in-process, so the agent loop does not change. llama.cpp's
`common/chat.h` turns messages plus tools into a prompt and a lazy grammar
(`common_chat_templates_apply`) and parses streamed output back into text,
reasoning and tool calls (`common_chat_parse`), which is the path
`llama-server` uses for tool calling. It runs any GGUF model, including
Qwen3.5 and Gemma 4, and its OpenCL backend lists the Adreno GPU of the
Snapdragon 7s Gen 3. The `examples/llama.android` sample is a starting point
for the CMake set-up only: it has no tool support and formats prompts with
`use_jinja = false`.

Runner-up: LiteRT-LM 0.17.1 (Apache-2.0, Google's successor to MediaPipe
LLM Inference; powers AI Edge Gallery), with a Kotlin API, CPU/GPU/NPU and
built-in tool calling, but only `.litertlm` models. Ruled out: MediaPipe LLM
Inference (deprecated), MLC LLM (models compiled per device), ONNX Runtime
GenAI (Java API in preview, large AAR), ExecuTorch (models exported ahead of
time), ML Kit Gemini Nano (beta, no tool calling, no Nothing phones).

## APK size

Measured from release binaries: llama.cpp b11366 stripped arm64 libraries
are 14.9 MB uncompressed, 5.8 MB compressed; LiteRT-LM's JNI library is
21.8 MB, 9.5 MB compressed. With `useLegacyPackaging = true` the release APK
would grow from 6.75 MB to about 12 to 13 MB with llama.cpp (estimate).

## Device-fit rule

Required memory = (model file + KV cache + compute buffer) × 1.1, where the
KV cache is attention layers × context × KV heads × head size × 2 × bytes
per value, and the compute buffer is (vocabulary + embedding width) × 512 ×
4 bytes (PocketPal's rule). Budget = min(`availMem`, 0.6 × total RAM); free
storage must exceed the file plus 1 GB. Example: Qwen3.5-4B Q4_0 (2,583 MB)
at 8,192 tokens needs (2,583 + 268 + 514) × 1.1 ≈ 3.7 GB; an 8 GB phone's
fallback budget is 4.8 GB, so it fits on paper; Qwen3.5-2B needs about
2.0 GB.

## Test phone

A059 is the Nothing Phone (3a): Snapdragon 7s Gen 3, 7.6 GB RAM visible to
Android, 2.7 GB `MemAvailable` with the usual apps open (`/proc/meminfo`,
2026-10-03). `llama-bench` from llama.cpp b11366 (CPU, armv8.6 variant
chosen by the loader, 2 repetitions) on Qwen3.5-0.8B Q4_0 (473 MiB):

| Threads | Prompt 512 tokens | Prompt 1,500 tokens | Generation |
|---|---|---|---|
| 4 | 197 tokens/s | 175 tokens/s | 22.2 tokens/s |
| 6 | 154 tokens/s | 122 tokens/s | 19.2 tokens/s |

Four threads beat six, because the extra threads land on the slow A520
cores. With the 1,450-token local tool set the 0.8B model reads the first
prompt in about 8 s; the full 4,875-token prompt would take about 28 s.
Larger models were not measured (user ruling: test with one small model);
a 2B model has about 2.5 times the weights, so roughly 70 and 9 tokens/s
is an extrapolation, not a measurement. With 2.7 GB available, the fit
rule allows Qwen3.5-2B (about 2.0 GB) but not Qwen3.5-4B (about 3.7 GB).

## Models (GGUF, ungated, Apache-2.0)

| Model | Q4_0 size | Phone RAM | Note |
|---|---|---|---|
| Qwen3.5-0.8B | 0.51 GB | 6 GB or less | weak tool calls (BFCL-V4 25.3) |
| Qwen3.5-2B | 1.21 GB | 6 to 8 GB | default; BFCL-V4 43.6 |
| Qwen3.5-4B | 2.58 GB | 8 GB with free memory, 12 GB | best tool caller here; BFCL-V4 50.3 |
| Gemma 4 E2B-it | 3.04 GB | 8 GB or more | 140+ languages, native tool use |
| Gemma 4 E4B-it | 4.84 GB | 12 GB or more | optional |

Bangla quality is not measured for any of them.

## Download

`https://huggingface.co/<repo>/resolve/<commit>/<file>` redirects to a
signed CDN address that supports byte ranges; the `x-linked-etag` header is
the file's SHA-256. A WorkManager worker in the foreground (`dataSync`,
which Jonaki already declares) writes a `.part` file, resumes with
`Range`, hashes while writing and renames only on a matching hash. Files go
to app-specific storage, excluded from backup. Android 15 limits `dataSync`
services to 6 hours a day, shared with the agent-loop service.

## Modules

1. `providers:local-llama`: llama.cpp pinned, CMake, arm64 only; JNI calls
   load, applyTemplate, generate and cancel; KV-cache prefix reuse between
   agent steps.
2. `core:model-catalog`: a local-model list (repo, commit, file, size,
   SHA-256, RAM tier) and the fit function, tested on the JVM.
3. A Settings sub-page: RAM, free storage, the recommended model, download
   with progress, delete, use for a thread.
4. The download worker.
5. A smaller tool set for local models, because CPU prefill of a
   4,875-token prompt could take tens of seconds.

## Risks

Long prefill on the CPU; weaker prefix reuse on Qwen3.5's hybrid layers;
wrong tool choices by small models; the low-memory killer while a model is
loaded; a new NDK build with a hand-updated llama.cpp pin; the APK roughly
doubling (a separate "Jonaki Local" APK flavour is the alternative).

Sources: llama.cpp `common/chat.h`, `docs/function-calling.md`,
`docs/backend/OPENCL.md`; developers.google.com/edge/litert-lm/android;
github.com/a-ghorbani/pocketpal-ai (`memoryEstimator.ts`);
github.com/google-ai-edge/gallery (model allowlist, `DownloadWorker.kt`);
huggingface.co/unsloth (GGUF sizes); huggingface.co/Qwen/Qwen3.5-4B
(config); developer.android.com foreground-service timeout page.
