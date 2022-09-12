//
// Created by xu fulong on 2022/9/4.
//

#include "ff_audio_player.h"
#include <jni.h>
#include <unistd.h>

#define SLEEP_TIME (16000)

extern "C"
JNIEXPORT long JNICALL
Java_com_frank_media_MediaJniHelper_native_1init(JNIEnv* env, jobject thiz) {
    FFAudioPlayer *audioPlayer = new FFAudioPlayer();
    return (long) audioPlayer;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_frank_media_MediaJniHelper_play_1audio(JNIEnv* env, jobject thiz, long context, jstring path) {
    if (path == nullptr)
        return;
    const char* native_path = env->GetStringUTFChars(path, JNI_FALSE);
    int result = 0;
    FFAudioPlayer *audioPlayer = (FFAudioPlayer *) context;
    // 打开输入流
    audioPlayer->open(native_path);
    // 初始化AudioTrack
    jclass audio_class = env->GetObjectClass(thiz);
    jmethodID audio_track_method = env->GetMethodID(audio_class,
                                                    "createAudioTrack", "(II)Landroid/media/AudioTrack;");
    jobject audio_track = env->CallObjectMethod(thiz, audio_track_method, audioPlayer->getSampleRate(), audioPlayer->getChannel());
    // 调用play函数
    jclass audio_track_class = env->GetObjectClass(audio_track);
    jmethodID play_method = env->GetMethodID(audio_track_class, "play", "()V");
    env->CallVoidMethod(audio_track, play_method);
    // 获取write方法id
    jmethodID write_method = env->GetMethodID(audio_track_class, "write", "([BII)I");

    // 解码音频帧
    while (result >= 0) {
        result = audioPlayer->decodeAudio();
        if (result == 0) {
            continue;
        } else if (result < 0) {
            break;
        }
        int size = result;
        // 调用AudioTrack播放(可优化：数组复用)
        jbyteArray audio_array = env->NewByteArray(size);
        jbyte *data_address = env->GetByteArrayElements(audio_array, JNI_FALSE);
        memcpy(data_address, audioPlayer->getDecodeFrame(), size);
        env->ReleaseByteArrayElements(audio_array, data_address, 0);
        env->CallIntMethod(audio_track, write_method, audio_array, 0, size);
        env->DeleteLocalRef(audio_array);

        // 延时等待
        usleep(SLEEP_TIME);
    }

    env->ReleaseStringUTFChars(path, native_path);
    jmethodID release_method = env->GetMethodID(audio_class, "releaseAudioTrack", "()V");
    env->CallVoidMethod(thiz, release_method);
    audioPlayer->close();
    delete audioPlayer;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_frank_media_MediaJniHelper_filter_1again(JNIEnv* env, jobject thiz, long context, jstring filter) {
    if (context == 0)
        return;
    FFAudioPlayer *audioPlayer = (FFAudioPlayer *) context;
    if (!audioPlayer)
        return;
    const char *filter_desc = env->GetStringUTFChars(filter, JNI_FALSE);
    audioPlayer->setFilterAgain(filter_desc);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_frank_media_MediaJniHelper_native_1release(JNIEnv* env, jobject thiz, long context) {
    if (context == 0)
        return;
    FFAudioPlayer *audioPlayer = (FFAudioPlayer *) context;
    if (!audioPlayer)
        return;
    audioPlayer->setExitPlaying(true);
}