#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_hybridassistant_llm_LlamaBridge_nativeHealthCheck(
        JNIEnv *env,
        jobject /* this */) {
#ifdef HYBRID_LLAMA_ENABLED
    std::string status = "llama.cpp JNI bridge: enabled";
#else
    std::string status = "llama.cpp JNI bridge: stub (add llama.cpp source)";
#endif
    return env->NewStringUTF(status.c_str());
}
