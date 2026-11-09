#include <jni.h>
#include <string>
#include <android/log.h>
static jclass gEpdcClass = nullptr; static jmethodID gPostMethod1 = nullptr; static jmethodID gPostMethod2 = nullptr; static jmethodID gForceMethod = nullptr;
void clearException(JNIEnv *env) { if (env->ExceptionCheck()) { env->ExceptionClear(); } }
void initEpdc(JNIEnv *env) {
    if (gEpdcClass) return;
    jclass local = env->FindClass("android/eink/EPDCDevice");
    if (!local || env->ExceptionCheck()) { clearException(env); return; }
    gEpdcClass = (jclass) env->NewGlobalRef(local); env->DeleteLocalRef(local);
    gPostMethod1 = env->GetStaticMethodID(gEpdcClass, "nativePostCommand", "(Ljava/lang/String;)I");
    if (!gPostMethod1) { clearException(env); gPostMethod2 = env->GetStaticMethodID(gEpdcClass, "nativePostCommand", "(Ljava/lang/String;[Ljava/lang/String;)I"); }
    gForceMethod = env->GetStaticMethodID(gEpdcClass, "setForceNextPostMode", "(I)V");
}
extern "C" JNIEXPORT jboolean JNICALL Java_moe_shizuku_manager_ripple_EpdcHelper_nativeSendPageH(JNIEnv *env, jobject, jint effect) {
    initEpdc(env);
    if (!gForceMethod || (!gPostMethod1 && !gPostMethod2)) {
        clearException(env);
        return JNI_FALSE;
    }
    char cmd[64];
    snprintf(cmd, sizeof(cmd), "next-effect-type %d", effect);
    jstring jcmd = env->NewStringUTF(cmd);
    if (!jcmd || env->ExceptionCheck()) {
        clearException(env);
        return JNI_FALSE;
    }
    const jint posted = gPostMethod1
        ? env->CallStaticIntMethod(gEpdcClass, gPostMethod1, jcmd)
        : env->CallStaticIntMethod(gEpdcClass, gPostMethod2, jcmd, nullptr);
    if (env->ExceptionCheck()) {
        clearException(env);
        env->DeleteLocalRef(jcmd);
        return JNI_FALSE;
    }
    env->CallStaticVoidMethod(gEpdcClass, gForceMethod, 0x01000063);
    const bool forceOk = !env->ExceptionCheck();
    if (!forceOk) clearException(env);
    env->DeleteLocalRef(jcmd);
    // API accepted != visible waveform; negative post result is a vendor failure.
    return forceOk && posted >= 0 ? JNI_TRUE : JNI_FALSE;
}
