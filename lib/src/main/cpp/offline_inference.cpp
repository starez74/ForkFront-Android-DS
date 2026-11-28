#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::once_flag init_flag;
jbyteArray bytes(JNIEnv * env, const std::string & text) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out) env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                                   reinterpret_cast<const jbyte *>(text.data()));
    return out;
}
jbyteArray failure(JNIEnv * env, const char * msg) {
    return bytes(env, std::string("ERROR: ") + msg);
}
struct Guard {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * sampler = nullptr;
    ~Guard() {
        if (sampler) llama_sampler_free(sampler);
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_tbd_forkfront_companion_OfflineInference_nativeInfer(
        JNIEnv * env, jclass, jstring jpath, jstring jprompt, jint requested) {
    if (!jpath || !jprompt) return failure(env, "Missing GGUF path or prompt");
    const char * pathValue = env->GetStringUTFChars(jpath, nullptr);
    if (!pathValue) return failure(env, "Could not read model path");
    std::string path(pathValue);
    env->ReleaseStringUTFChars(jpath, pathValue);
    const char * promptValue = env->GetStringUTFChars(jprompt, nullptr);
    if (!promptValue) return failure(env, "Could not read prompt");
    std::string prompt(promptValue);
    env->ReleaseStringUTFChars(jprompt, promptValue);
    if (path.empty() || path.size() > 1024 || prompt.empty() || prompt.size() > 18000)
        return failure(env, "Invalid path or prompt length");

    std::call_once(init_flag, [] { llama_backend_init(); });
    Guard guard;
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // Portable CPU-only fallback for Galaxy Tab A8.
    guard.model = llama_model_load_from_file(path.c_str(), mp);
    if (!guard.model) return failure(env, "Cannot load model: invalid file or low RAM");
    const llama_vocab * vocab = llama_model_get_vocab(guard.model);
    int count = -llama_tokenize(vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (count <= 0 || count > 1700) return failure(env, "Too many input tokens (limit 1700)");
    std::vector<llama_token> tokens(count);
    if (llama_tokenize(vocab, prompt.c_str(), prompt.size(),
                       tokens.data(), count, true, true) < 0)
        return failure(env, "Could not tokenize question");
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 512;
    cp.n_ubatch = 256;
    cp.n_threads = 3;
    cp.n_threads_batch = 3;
    guard.ctx = llama_init_from_model(guard.model, cp);
    if (!guard.ctx) return failure(env, "Insufficient memory for inference");
    for (int pos = 0; pos < count; pos += 256) {
        int size = std::min(256, count - pos);
        llama_batch batch = llama_batch_get_one(tokens.data() + pos, size);
        if (llama_decode(guard.ctx, batch) != 0)
            return failure(env, "Prompt decoding failed");
    }
    guard.sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(guard.sampler, llama_sampler_init_greedy());

    std::string result;
    int nOutput = std::clamp(static_cast<int>(requested), 1, 160);
    for (int i = 0; i < nOutput; ++i) {
        llama_token token = llama_sampler_sample(guard.sampler, guard.ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        int length = llama_token_to_piece(vocab, token, nullptr, 0, 0, true);
        if (length < 0) length = -length;
        if (length < 1 || length > 4096) return failure(env, "Invalid output token");
        std::vector<char> buffer(length + 8);
        int written = llama_token_to_piece(vocab, token, buffer.data(), buffer.size(), 0, true);
        if (written < 0) return failure(env, "Output decoding failed");
        result.append(buffer.data(), written);
        if (result.size() > 5000) break;
        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(guard.ctx, batch) != 0)
            return failure(env, "Model generation failed");
    }
    if (result.empty()) return failure(env, "Model returned no words");
    return bytes(env, result);
}
