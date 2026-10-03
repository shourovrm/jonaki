// Jonaki's JNI layer over llama.cpp (D-133), modelled on llama-server's chat
// path: common_chat_templates_apply turns OpenAI-style messages and tools into
// a prompt and a lazy tool-call grammar, the sampler follows that grammar, and
// common_chat_parse turns the streamed output back into text, reasoning and
// tool calls. Everything else (ids, events, cancellation of the flow) is
// Kotlin's job; this file stays thin because it cannot run in JVM tests.
//
// Strings cross JNI as UTF-8 byte arrays: JNI's own string functions use
// "modified UTF-8", which breaks emoji and other characters outside the BMP.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <deque>
#include <memory>
#include <set>
#include <stdexcept>
#include <string>
#include <vector>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "json.h"
#include "sampling.h"

namespace {

constexpr const char * LOG_TAG = "JonakiLlama";

// Two checkpoints per request (see process_prompt) plus one from the request before.
constexpr size_t MAX_CHECKPOINTS = 3;

// The agent loop has one request at a time and one model loaded, so one flag
// serves; it is not tied to a handle so that a cancel racing an unload is harmless.
std::atomic<bool> cancel_requested{false};

struct LoadedModel {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    const llama_vocab * vocab = nullptr;
    common_chat_templates_ptr templates;
    common_context_seq_rm_type seq_rm_type = COMMON_CONTEXT_SEQ_RM_TYPE_NO;
    int32_t sliding_window = 0;
    // Sampling defaults the GGUF file recommends, over llama.cpp's own.
    common_params_sampling sampling_defaults;
    // The tokens whose state is in the context's memory, in order.
    std::vector<llama_token> cached_tokens;
    // Saved states of the memory parts that cannot be rolled back (the
    // recurrent layers of hybrid models such as Qwen3.5, or a sliding window).
    std::deque<common_prompt_checkpoint> checkpoints;

    ~LoadedModel() {
        templates.reset();
        if (context != nullptr) {
            llama_free(context);
        }
        if (model != nullptr) {
            llama_model_free(model);
        }
    }
};

void log_to_logcat(ggml_log_level level, const char * text, void * /* user_data */) {
    int priority = ANDROID_LOG_DEBUG;
    if (level == GGML_LOG_LEVEL_ERROR) {
        priority = ANDROID_LOG_ERROR;
    } else if (level == GGML_LOG_LEVEL_WARN) {
        priority = ANDROID_LOG_WARN;
    } else if (level == GGML_LOG_LEVEL_INFO) {
        priority = ANDROID_LOG_INFO;
    }
    __android_log_write(priority, LOG_TAG, text);
}

bool abort_when_cancelled(void * /* data */) {
    return cancel_requested.load();
}

std::string string_from_bytes(JNIEnv * env, jbyteArray bytes) {
    const jsize length = env->GetArrayLength(bytes);
    std::string text(static_cast<size_t>(length), '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(text.data()));
    return text;
}

jbyteArray bytes_from_string(JNIEnv * env, const std::string & text) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return bytes;
}

void throw_java(JNIEnv * env, const std::string & message) {
    jclass exception_class = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception_class, message.c_str());
}

void read_float_metadata(const llama_model * model, llama_model_meta_key key, float & target) {
    char buffer[64] = {0};
    if (llama_model_meta_val_str(model, llama_model_meta_key_str(key), buffer, sizeof(buffer)) <= 0) {
        return;
    }
    char * end = nullptr;
    const float value = std::strtof(buffer, &end);
    if (end != buffer) {
        target = value;
    }
}

void read_int_metadata(const llama_model * model, llama_model_meta_key key, int32_t & target) {
    char buffer[64] = {0};
    if (llama_model_meta_val_str(model, llama_model_meta_key_str(key), buffer, sizeof(buffer)) <= 0) {
        return;
    }
    char * end = nullptr;
    const long value = std::strtol(buffer, &end, 10);
    if (end != buffer) {
        target = static_cast<int32_t>(value);
    }
}

// The length of the longest prefix of text that ends on a whole UTF-8
// character, so that a token ending inside a character is not shown half.
size_t complete_utf8_length(const std::string & text) {
    const size_t length = text.size();
    for (size_t back = 1; back <= 4 && back <= length; back++) {
        const auto byte = static_cast<unsigned char>(text[length - back]);
        if ((byte & 0xC0) == 0x80) {
            continue;  // a continuation byte; look further back for the lead byte
        }
        size_t expected = 1;
        if ((byte & 0xE0) == 0xC0) {
            expected = 2;
        } else if ((byte & 0xF0) == 0xE0) {
            expected = 3;
        } else if ((byte & 0xF8) == 0xF0) {
            expected = 4;
        }
        return back >= expected ? length : length - back;
    }
    return length;
}

// The length of text that cannot be the start of a stop string, so that a
// stop string arriving over several tokens is never shown.
size_t length_before_possible_stop(const std::string & text, const std::vector<std::string> & stops) {
    size_t keep = text.size();
    for (const auto & stop : stops) {
        if (stop.empty()) {
            continue;
        }
        const size_t longest = std::min(stop.size() - 1, text.size());
        for (size_t overlap = longest; overlap > 0; overlap--) {
            if (text.compare(text.size() - overlap, overlap, stop, 0, overlap) == 0) {
                keep = std::min(keep, text.size() - overlap);
                break;
            }
        }
    }
    return keep;
}

// Reuses the longest prefix of the cached tokens that the new prompt shares,
// as llama-server does. Returns how many prompt tokens need no processing.
size_t reuse_cached_prefix(LoadedModel & loaded, const std::vector<llama_token> & prompt) {
    llama_memory_t memory = llama_get_memory(loaded.context);
    size_t reused = 0;
    while (reused < loaded.cached_tokens.size() && reused < prompt.size() &&
           loaded.cached_tokens[reused] == prompt[reused]) {
        reused++;
    }
    // The last prompt token is always processed, so that there are logits to sample from.
    if (reused == prompt.size() && reused > 0) {
        reused--;
    }

    if (reused > 0) {
        const llama_pos memory_start = llama_memory_seq_pos_min(memory, 0);
        const llama_pos threshold = std::max<llama_pos>(0, static_cast<llama_pos>(reused) - loaded.sliding_window);
        // Recurrent and sliding-window memory only holds the state at its end,
        // so it can only go back to a saved checkpoint.
        if (memory_start >= threshold) {
            const common_prompt_checkpoint * usable = nullptr;
            for (auto it = loaded.checkpoints.rbegin(); it != loaded.checkpoints.rend(); ++it) {
                if (it->pos_max > static_cast<llama_pos>(reused)) {
                    continue;
                }
                if (it->pos_min < threshold || it->pos_min == 0) {
                    usable = &*it;
                    break;
                }
            }
            if (usable == nullptr) {
                reused = 0;
            } else {
                usable->load_tgt(loaded.context, 0, LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY);
                const llama_pos restored_end = std::max(usable->pos_min + 1, usable->pos_max);
                reused = std::min({reused, static_cast<size_t>(restored_end), static_cast<size_t>(usable->n_tokens)});
                __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "restored checkpoint at %lld tokens",
                                    static_cast<long long>(usable->n_tokens));
            }
        }
    }

    for (auto it = loaded.checkpoints.begin(); it != loaded.checkpoints.end();) {
        if (it->pos_max > static_cast<llama_pos>(reused)) {
            it = loaded.checkpoints.erase(it);
        } else {
            ++it;
        }
    }

    if (!llama_memory_seq_rm(memory, 0, static_cast<llama_pos>(reused), -1)) {
        llama_memory_clear(memory, true);
        loaded.checkpoints.clear();
        reused = 0;
    }
    loaded.cached_tokens.resize(reused);
    return reused;
}

void save_checkpoint(LoadedModel & loaded) {
    llama_memory_t memory = llama_get_memory(loaded.context);
    while (loaded.checkpoints.size() >= MAX_CHECKPOINTS) {
        loaded.checkpoints.pop_front();
    }
    common_prompt_checkpoint & checkpoint = loaded.checkpoints.emplace_back();
    checkpoint.update_pos(static_cast<int64_t>(loaded.cached_tokens.size()),
                          llama_memory_seq_pos_min(memory, 0), llama_memory_seq_pos_max(memory, 0));
    checkpoint.update_tgt(loaded.context, 0, LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY);
}

enum class PromptOutcome { DONE, CANCELLED, FAILED };

// Decodes prompt[from, end) in batches. Where the memory cannot be rolled
// back, it saves checkpoints just before the generation prompt and four
// tokens before the end (llama-server's offsets), because the next agent step
// resends this prompt with the assistant turn rendered differently from what
// the model generated, so the two diverge only there.
PromptOutcome process_prompt(LoadedModel & loaded, const std::vector<llama_token> & prompt, size_t from,
                             size_t generation_prompt_tokens) {
    const bool needs_checkpoints = loaded.seq_rm_type == COMMON_CONTEXT_SEQ_RM_TYPE_FULL ||
                                   loaded.seq_rm_type == COMMON_CONTEXT_SEQ_RM_TYPE_RS ||
                                   loaded.sliding_window > 0;
    std::set<size_t> checkpoint_ends;
    if (needs_checkpoints) {
        const size_t candidates[] = {generation_prompt_tokens, 4};
        for (size_t distance : candidates) {
            if (distance > 0 && prompt.size() > distance && prompt.size() - distance > from) {
                checkpoint_ends.insert(prompt.size() - distance);
            }
        }
    }
    const size_t batch_size = llama_n_batch(loaded.context);
    size_t position = from;
    while (position < prompt.size()) {
        size_t end = std::min(position + batch_size, prompt.size());
        auto next_checkpoint = checkpoint_ends.upper_bound(position);
        if (next_checkpoint != checkpoint_ends.end() && *next_checkpoint < end) {
            end = *next_checkpoint;
        }
        llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(prompt.data() + position),
                                                static_cast<int32_t>(end - position));
        const int32_t result = llama_decode(loaded.context, batch);
        if (result != 0) {
            // Decoded batches stay in memory after an abort; keep the token list in step with them.
            const llama_pos last = llama_memory_seq_pos_max(llama_get_memory(loaded.context), 0);
            loaded.cached_tokens.assign(prompt.begin(), prompt.begin() + std::max<llama_pos>(0, last + 1));
            return result == 2 ? PromptOutcome::CANCELLED : PromptOutcome::FAILED;
        }
        loaded.cached_tokens.insert(loaded.cached_tokens.end(), prompt.begin() + position, prompt.begin() + end);
        position = end;
        if (checkpoint_ends.count(end) > 0) {
            save_checkpoint(loaded);
        }
    }
    return PromptOutcome::DONE;
}

common_params_sampling sampling_for(const LoadedModel & loaded, const common_chat_params & chat) {
    common_params_sampling sampling = loaded.sampling_defaults;
    for (const auto & text : chat.preserved_tokens) {
        const auto ids = common_tokenize(loaded.vocab, text, false, true);
        if (ids.size() == 1) {
            sampling.preserved_tokens.insert(ids[0]);
        }
    }
    for (const auto & trigger : chat.grammar_triggers) {
        if (trigger.type != COMMON_GRAMMAR_TRIGGER_TYPE_WORD) {
            sampling.grammar_triggers.push_back(trigger);
            continue;
        }
        // A trigger word that is one preserved token is matched by token, as llama-server does.
        const auto ids = common_tokenize(loaded.vocab, trigger.value, false, true);
        if (ids.size() == 1 && sampling.preserved_tokens.count(ids[0]) > 0) {
            common_grammar_trigger token_trigger;
            token_trigger.type = COMMON_GRAMMAR_TRIGGER_TYPE_TOKEN;
            token_trigger.value = trigger.value;
            token_trigger.token = ids[0];
            sampling.grammar_triggers.push_back(token_trigger);
        } else {
            sampling.grammar_triggers.push_back(trigger);
        }
    }
    const bool lazy_without_triggers = chat.grammar_lazy && sampling.grammar_triggers.empty();
    if (!chat.grammar.empty() && !lazy_without_triggers) {
        sampling.grammar = common_grammar(COMMON_GRAMMAR_TYPE_TOOL_CALLS, chat.grammar);
        sampling.grammar_lazy = chat.grammar_lazy;
    }
    sampling.generation_prompt = chat.generation_prompt;
    return sampling;
}

common_json tool_calls_json(const common_chat_msg & message) {
    common_json calls = common_json::array();
    for (const auto & call : message.tool_calls) {
        common_json item = common_json::object();
        item["name"] = call.name;
        item["arguments"] = call.arguments;
        item["id"] = call.id;
        calls.push_back(item);
    }
    return calls;
}

std::string error_result(const std::string & message) {
    common_json result = common_json::object();
    result["finish"] = "error";
    result["error"] = message;
    return result.dump_safe();
}

struct SnapshotSender {
    JNIEnv * env;
    jobject listener;
    jmethodID on_snapshot;
    std::string sent_content;
    std::string sent_reasoning;

    // Returns false when the listener threw, which counts as a cancel.
    bool send(const common_chat_msg & message) {
        if (message.content == sent_content && message.reasoning_content == sent_reasoning) {
            return true;
        }
        sent_content = message.content;
        sent_reasoning = message.reasoning_content;
        jbyteArray content = bytes_from_string(env, sent_content);
        jbyteArray reasoning = bytes_from_string(env, sent_reasoning);
        env->CallVoidMethod(listener, on_snapshot, content, reasoning);
        env->DeleteLocalRef(content);
        env->DeleteLocalRef(reasoning);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        return true;
    }
};

std::string generate(JNIEnv * env, LoadedModel & loaded, const std::string & request_text, jobject listener) {
    const auto started = std::chrono::steady_clock::now();
    const common_json request = common_json::parse(request_text);

    common_chat_templates_inputs inputs;
    inputs.messages = common_chat_msgs_parse_oaicompat(request.at("messages"));
    inputs.tools = common_chat_tools_parse_oaicompat(request.value("tools", common_json::array()));
    inputs.tool_choice = request.value("tool_choice", std::string("auto")) == "none" ? COMMON_CHAT_TOOL_CHOICE_NONE
                                                                                     : COMMON_CHAT_TOOL_CHOICE_AUTO;
    inputs.parallel_tool_calls = common_chat_templates_get_caps(loaded.templates.get())["supports_parallel_tool_calls"];
    inputs.reasoning_format = COMMON_REASONING_FORMAT_DEEPSEEK;
    inputs.enable_thinking = request.value("enable_thinking", true);
    inputs.add_generation_prompt = true;
    const common_chat_params chat = common_chat_templates_apply(loaded.templates.get(), inputs);

    const std::vector<llama_token> prompt = common_tokenize(loaded.vocab, chat.prompt, true, true);
    const size_t context_size = llama_n_ctx(loaded.context);
    if (prompt.size() + 1 >= context_size) {
        return error_result("The conversation is " + std::to_string(prompt.size()) + " tokens; the local model holds " +
                            std::to_string(context_size) + ".");
    }
    const size_t generation_prompt_tokens = common_tokenize(loaded.vocab, chat.generation_prompt, false, true).size();

    const size_t reused = reuse_cached_prefix(loaded, prompt);
    const PromptOutcome prompt_outcome = process_prompt(loaded, prompt, reused, generation_prompt_tokens);
    const auto prompt_done = std::chrono::steady_clock::now();
    if (prompt_outcome == PromptOutcome::FAILED) {
        return error_result("llama.cpp could not process the prompt.");
    }

    common_params_sampling sampling = sampling_for(loaded, chat);
    common_sampler_ptr sampler(common_sampler_init(loaded.model, sampling));
    if (!sampler) {
        return error_result("llama.cpp could not build the sampler for this model's tool-call grammar.");
    }

    common_chat_parser_params parser_params(chat);
    parser_params.reasoning_format = COMMON_REASONING_FORMAT_DEEPSEEK;
    parser_params.parse_tool_calls = !inputs.tools.empty() && inputs.tool_choice != COMMON_CHAT_TOOL_CHOICE_NONE;
    if (!chat.parser.empty()) {
        parser_params.parser.load(chat.parser);
    }

    const int64_t requested_limit = request.value("max_tokens", static_cast<int64_t>(0));
    const size_t output_limit = requested_limit > 0 ? static_cast<size_t>(requested_limit) : context_size;

    SnapshotSender sender{env, listener, env->GetMethodID(env->GetObjectClass(listener), "onSnapshot", "([B[B)V")};
    std::string generated;
    std::string finish = prompt_outcome == PromptOutcome::CANCELLED ? "cancelled" : "";
    size_t generated_tokens = 0;
    while (finish.empty()) {
        if (cancel_requested.load()) {
            finish = "cancelled";
            break;
        }
        llama_token token = common_sampler_sample(sampler.get(), loaded.context, -1);
        common_sampler_accept(sampler.get(), token, true);
        if (llama_vocab_is_eog(loaded.vocab, token)) {
            finish = "stop";
            break;
        }
        const bool show_special = sampling.preserved_tokens.count(token) > 0;
        const size_t search_from = generated.size();
        generated += common_token_to_piece(loaded.context, token, show_special);
        generated_tokens++;

        for (const auto & stop : chat.additional_stops) {
            if (stop.empty()) {
                continue;
            }
            const size_t start = search_from >= stop.size() ? search_from - stop.size() + 1 : 0;
            const size_t found = generated.find(stop, start);
            if (found != std::string::npos) {
                generated.resize(found);
                finish = "stop";
            }
        }
        if (!finish.empty()) {
            break;
        }

        llama_batch batch = llama_batch_get_one(&token, 1);
        const int32_t result = llama_decode(loaded.context, batch);
        if (result == 2) {
            finish = "cancelled";
            break;
        }
        if (result != 0) {
            return error_result("llama.cpp failed while generating (code " + std::to_string(result) + ").");
        }
        loaded.cached_tokens.push_back(token);

        if (generated_tokens >= output_limit || loaded.cached_tokens.size() + 1 >= context_size) {
            finish = "length";
            break;
        }

        size_t shown = complete_utf8_length(generated);
        shown = std::min(shown, length_before_possible_stop(generated.substr(0, shown), chat.additional_stops));
        try {
            const common_chat_msg partial = common_chat_parse(generated.substr(0, shown), true, parser_params);
            if (!sender.send(partial)) {
                finish = "cancelled";
            }
        } catch (const std::exception &) {
            // A partial parse can fail mid-structure; the next token usually completes it.
        }
    }

    common_chat_msg final_message;
    const bool complete = finish == "stop";
    try {
        final_message = common_chat_parse(generated, !complete, parser_params);
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "final parse failed: %s", error.what());
        final_message.content = generated;
    }

    const auto finished = std::chrono::steady_clock::now();
    const auto prompt_ms = std::chrono::duration_cast<std::chrono::milliseconds>(prompt_done - started).count();
    const auto generation_ms = std::chrono::duration_cast<std::chrono::milliseconds>(finished - prompt_done).count();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "prompt %zu tokens (%zu reused) in %lld ms; %zu tokens generated in %lld ms; finish %s",
                        prompt.size(), reused, static_cast<long long>(prompt_ms), generated_tokens,
                        static_cast<long long>(generation_ms), finish.c_str());

    common_json result = common_json::object();
    result["finish"] = finish;
    result["content"] = final_message.content;
    result["reasoning"] = final_message.reasoning_content;
    result["tool_calls"] = tool_calls_json(final_message);
    result["prompt_tokens"] = static_cast<int64_t>(prompt.size());
    result["cached_tokens"] = static_cast<int64_t>(reused);
    result["completion_tokens"] = static_cast<int64_t>(generated_tokens);
    result["prompt_ms"] = static_cast<int64_t>(prompt_ms);
    result["generation_ms"] = static_cast<int64_t>(generation_ms);
    return result.dump_safe();
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_jonaki_providers_localllama_NativeLlamaEngine_nativeLoad(JNIEnv * env, jobject /* this */, jbyteArray path_bytes,
                                                                  jint context_tokens, jint threads) {
    static bool backend_ready = false;
    if (!backend_ready) {
        llama_log_set(log_to_logcat, nullptr);
        llama_backend_init();
        backend_ready = true;
    }
    const std::string path = string_from_bytes(env, path_bytes);
    auto loaded = std::make_unique<LoadedModel>();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    loaded->model = llama_model_load_from_file(path.c_str(), model_params);
    if (loaded->model == nullptr) {
        throw_java(env, "llama.cpp could not load " + path);
        return 0;
    }
    loaded->vocab = llama_model_get_vocab(loaded->model);

    llama_context_params context_params = llama_context_default_params();
    const int32_t trained_context = llama_model_n_ctx_train(loaded->model);
    context_params.n_ctx = static_cast<uint32_t>(
        trained_context > 0 ? std::min<int32_t>(context_tokens, trained_context) : context_tokens);
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_seq_max = 1;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.abort_callback = abort_when_cancelled;
    context_params.abort_callback_data = nullptr;
    loaded->context = llama_init_from_model(loaded->model, context_params);
    if (loaded->context == nullptr) {
        throw_java(env, "llama.cpp could not make a context of " + std::to_string(context_params.n_ctx) + " tokens");
        return 0;
    }

    try {
        loaded->templates = common_chat_templates_init(loaded->model, "");
    } catch (const std::exception & error) {
        throw_java(env, std::string("The model's chat template could not be read: ") + error.what());
        return 0;
    }
    loaded->seq_rm_type = common_context_can_seq_rm(loaded->context);
    loaded->sliding_window = llama_model_n_swa(loaded->model);

    common_params_sampling & defaults = loaded->sampling_defaults;
    read_float_metadata(loaded->model, LLAMA_MODEL_META_KEY_SAMPLING_TEMP, defaults.temp);
    read_int_metadata(loaded->model, LLAMA_MODEL_META_KEY_SAMPLING_TOP_K, defaults.top_k);
    read_float_metadata(loaded->model, LLAMA_MODEL_META_KEY_SAMPLING_TOP_P, defaults.top_p);
    read_float_metadata(loaded->model, LLAMA_MODEL_META_KEY_SAMPLING_MIN_P, defaults.min_p);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "loaded %s: context %u, memory rollback type %d, sliding window %d",
                        path.c_str(), context_params.n_ctx, static_cast<int>(loaded->seq_rm_type), loaded->sliding_window);
    return reinterpret_cast<jlong>(loaded.release());
}

JNIEXPORT void JNICALL
Java_app_jonaki_providers_localllama_NativeLlamaEngine_nativeUnload(JNIEnv * /* env */, jobject /* this */, jlong handle) {
    delete reinterpret_cast<LoadedModel *>(handle);
}

JNIEXPORT jbyteArray JNICALL
Java_app_jonaki_providers_localllama_NativeLlamaEngine_nativeGenerate(JNIEnv * env, jobject /* this */, jlong handle,
                                                                      jbyteArray request_bytes, jobject listener) {
    auto * loaded = reinterpret_cast<LoadedModel *>(handle);
    std::string result;
    try {
        result = generate(env, *loaded, string_from_bytes(env, request_bytes), listener);
    } catch (const std::exception & error) {
        result = error_result(error.what());
    }
    return bytes_from_string(env, result);
}

JNIEXPORT void JNICALL
Java_app_jonaki_providers_localllama_NativeLlamaEngine_nativeCancel(JNIEnv * /* env */, jobject /* this */) {
    cancel_requested.store(true);
}

JNIEXPORT void JNICALL
Java_app_jonaki_providers_localllama_NativeLlamaEngine_nativeClearCancel(JNIEnv * /* env */, jobject /* this */) {
    cancel_requested.store(false);
}

}  // extern "C"
