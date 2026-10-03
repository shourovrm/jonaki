package app.jonaki.core.localmodels

/**
 * The model families the bundled llama.cpp can run as a chat model (D-133).
 * Source: LLM_ARCH_NAMES in src/llama-arch.cpp at llama.cpp release b11366
 * (raw.githubusercontent.com/ggml-org/llama.cpp/b11366/src/llama-arch.cpp,
 * read on 2026-10-03), minus the names that are not text generators:
 * encoders and embedding models (bert and its variants, t5encoder,
 * gemma-embedding, llama-embed, clip), speech (wavtokenizer-dec, qwen3tts,
 * pockettts), draft heads for speculative decoding (eagle3, dflash,
 * gemma4-assistant), diffusion models (dream, llada, llada-moe, rnd1), OCR
 * models (deepseek2-ocr, paddleocr), the encoder-decoder t5, and
 * chameleon and cogvlm, which need images to be useful. Moving llama.cpp
 * to a newer release means reading the file again.
 */
object SupportedArchitectures {
    val names: Set<String> = setOf(
        "llama", "llama4", "deci", "falcon", "grok", "gpt2", "gptj", "gptneox", "mpt", "baichuan",
        "starcoder", "refact", "bloom", "stablelm",
        "qwen", "qwen2", "qwen2moe", "qwen2vl", "qwen3", "qwen3moe", "qwen3next", "qwen3vl", "qwen3vlmoe",
        "qwen35", "qwen35moe", "qwen4exp",
        "phi2", "phi3", "phimoe", "plamo", "plamo2", "plamo3", "codeshell", "orion", "internlm2",
        "minicpm", "minicpm3", "gemma", "gemma2", "gemma3", "gemma3n", "gemma4", "starcoder2",
        "mamba", "mamba2", "maple", "jamba", "falcon-h1", "xverse", "command-r", "cohere2", "cohere2moe",
        "dbrx", "olmo", "olmo2", "olmoe", "muse-glimmer", "openelm", "arctic",
        "deepseek", "deepseek2", "deepseek32", "deepseek4", "chatglm", "glm4", "glm4moe", "glm-dsa",
        "bitnet", "jais", "jais2", "nemotron", "nemotron_h", "nemotron_h_moe", "exaone", "exaone4", "exaone-moe",
        "rwkv6", "rwkv6qwen2", "rwkv7", "arwkv7",
        "granite", "granitemoe", "granitehybrid", "graniteswitch", "granite_swa",
        "plm", "bailingmoe", "bailingmoe2", "bailingmoe3", "dots1", "dots3note", "arcee", "afmoe", "laguna",
        "ernie4_5", "ernie4_5-moe", "hunyuan-moe", "hunyuan-dense", "hunyuan_vl", "hy_v3", "hy_v4",
        "smollm3", "gpt-oss", "lfm2", "lfm2moe", "smallthinker", "seed_oss", "grovemoe", "apertus",
        "minimax-01", "hrm_text", "minimax-m2", "minimax-m3", "pangu-embedded", "mistral3", "mistral4",
        "mimo2", "step35", "spark2_5", "maincoder", "kimi-linear", "kimi-k3", "glm5-next", "talkie",
        "mellum", "nanbeige",
    )

    /** Null (no metadata) counts as unsupported: Jonaki cannot tell whether the file would load. */
    fun isSupported(architecture: String?): Boolean = architecture != null && architecture in names
}
