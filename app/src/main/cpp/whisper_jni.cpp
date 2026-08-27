#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "KatibWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static whisper_context* g_ctx = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_katib_muhadarat_WhisperHelper_initModel(JNIEnv* env, jobject /*thiz*/, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (g_ctx) { whisper_free(g_ctx); g_ctx = nullptr; }
    struct whisper_context_params cparams = whisper_context_default_params();
    g_ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!g_ctx) { LOGE("Failed to load model: %s", path); return JNI_FALSE; }
    LOGI("Model loaded: %s", path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_katib_muhadarat_WhisperHelper_freeModel(JNIEnv*, jobject) {
    if (g_ctx) { whisper_free(g_ctx); g_ctx = nullptr; }
}

JNIEXPORT jstring JNICALL
Java_com_katib_muhadarat_WhisperHelper_transcribe(JNIEnv* env, jobject, jfloatArray pcmData) {
    if (!g_ctx) return env->NewStringUTF("النموذج غير محمّل");

    jsize len = env->GetArrayLength(pcmData);
    jfloat* pcm = env->GetFloatArrayElements(pcmData, nullptr);
    std::vector<float> buf(pcm, pcm + len);
    env->ReleaseFloatArrayElements(pcmData, pcm, JNI_ABORT);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.language = "auto";          // عربي + إنجليزي تلقائي
    wparams.translate = false;
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;

    int ret = whisper_full(g_ctx, wparams, buf.data(), (int)buf.size());
    if (ret != 0) {
        LOGE("whisper_full failed: %d", ret);
        return env->NewStringUTF("فشل التفريغ");
    }
    int n = whisper_full_n_segments(g_ctx);
    std::string out;
    for (int i = 0; i < n; ++i) {
        const char* txt = whisper_full_get_segment_text(g_ctx, i);
        if (i) out += " ";
        out += txt;
    }
    if (out.empty()) out = "(لم يتم التعرف على كلام)";
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_katib_muhadarat_WhisperHelper_getSystemInfo(JNIEnv* env, jobject) {
    std::string s = whisper_print_system_info();
    return env->NewStringUTF(s.c_str());
}

} // extern "C"
