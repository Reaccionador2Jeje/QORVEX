#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_dev_atdev_qorvex_runtime_NativeQemuBridge_nativeRuntimeStatus(JNIEnv* env, jobject /* thiz */) {
    // JNI boundary is ready. QEMU itself is intentionally not reported as available
    // until an audited upstream QEMU build is linked into this target.
    const std::string status =
        "JNI bridge loaded; QEMU engine is not bundled in this build.";
    return env->NewStringUTF(status.c_str());
}
