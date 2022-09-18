//
// Created by xu fulong on 2022/9/18.
//

#include "ff_rtmp_pusher.h"
#include <jni.h>

extern "C"
JNIEXPORT jint JNICALL
Java_com_frank_media_MediaJniHelper_push(JNIEnv *env, jobject thiz, jstring inputPath,
                                         jstring outputPath) {
    int ret;
    if (!inputPath || !outputPath)
        return -1;
    const char* input_path = env->GetStringUTFChars(inputPath, JNI_FALSE);
    const char* output_path = env->GetStringUTFChars(outputPath, JNI_FALSE);
    auto* pusher = new FFRtmpPusher();
    ret = pusher->open(input_path, output_path);
    if (ret < 0) {
        LOGE("FFmpegPush", "open error=%d", ret);
        return ret;
    }

    ret = pusher->push();

    pusher->close();
    env->ReleaseStringUTFChars(inputPath, input_path);
    env->ReleaseStringUTFChars(outputPath, output_path);

    return ret;
}