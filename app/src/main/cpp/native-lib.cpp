#include <jni.h>
#include <string>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif
#include "libavcodec/avcodec.h"
#include "libavutil/avutil.h"
#include "libavutil/ffversion.h"
#ifdef __cplusplus
}
#endif

#define LOGI(FMT,...) __android_log_print(ANDROID_LOG_INFO, "NdkHelper", FMT, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_ndklearning_NdkHelper_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "welcome to the world of ndk";
    LOGI("ffmpeg version:%s", FFMPEG_VERSION);
    unsigned int version = avcodec_version();
    LOGI("avcodec version=%d", version);
    const AVCodec *codec = avcodec_find_encoder_by_name("libx264");
    if (codec) {
        LOGI("found encoder name:%s, type:%d", codec->name, codec->type);
    }
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_ndklearning_NdkHelper_setIntData(JNIEnv *env, jobject thiz, jintArray jdata) {
//    jint *data = env->GetIntArrayElements(jdata, JNI_FALSE);
    int len = env->GetArrayLength(jdata);
//    jint *data = new int[len];
//    env->GetIntArrayRegion(jdata, 0, len, data);
    jint *data = static_cast<jint *>(env->GetPrimitiveArrayCritical(jdata, JNI_FALSE));
    LOGI("jni len=%d", len);
    for (int i = 0; i < len; ++i) {
        LOGI("jni data=%d", data[i]);
    }
//    env->ReleaseIntArrayElements(jdata, data, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_ndklearning_NdkHelper_testReflect(JNIEnv *env, jobject thiz) {
    jclass clazz = env->FindClass("com/example/ndklearning/NdkHelper");
    jmethodID method = env->GetMethodID(clazz, "onJniCallback", "(Ljava/lang/String;)V");
    env->CallVoidMethod(thiz, method, env->NewStringUTF("callback from JNI..."));
}