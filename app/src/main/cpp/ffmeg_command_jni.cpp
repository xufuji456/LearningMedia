//
// Created by xu fulong on 2022/10/15.
//

#include <jni.h>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif
#include "ffmpeg/ffmpeg.h"
#ifdef __cplusplus
}
#endif

#define TAG "FFmpegCommandJNI"

#define ALOGI(FORMAT,...) __android_log_vprint(ANDROID_LOG_INFO, TAG, FORMAT, ##__VA_ARGS__)
#define ALOGE(FORMAT,...) __android_log_vprint(ANDROID_LOG_ERROR, TAG, FORMAT, ##__VA_ARGS__)

void log_callback(void *ptr, int level, const char *format, va_list args) {
    switch (level) {
        case AV_LOG_INFO:
            ALOGI(format, args);
            break;
        case AV_LOG_ERROR:
            ALOGE(format, args);
            break;
        default:
            break;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_frank_media_FFmpegCommand_runFFmpeg(JNIEnv *env, jclass clazz, jobjectArray command_line) {

    av_log_set_level(AV_LOG_INFO);
    av_log_set_callback(log_callback);

    int argc = env->GetArrayLength(command_line);
    char **argv = static_cast<char **>(malloc(argc * sizeof(char *)));
    for (int i = 0; i < argc; i++) {
        auto jstr = (jstring)(env->GetObjectArrayElement(command_line, i));
        const char *native_str = env->GetStringUTFChars(jstr, JNI_FALSE);
        argv[i] = static_cast<char *>(malloc(10 * 1024));
        strcpy(argv[i], native_str);
        env->ReleaseStringUTFChars(jstr, native_str);
    }
    int result = run_ffmpeg(argc, argv);
    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);
    return result;
}
