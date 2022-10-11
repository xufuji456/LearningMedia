//
// Created by xu fulong on 2022/9/23.
//

#include <jni.h>
#include <string>
#include "VideoStream.h"
#include "AudioStream.h"
#include "PacketQueue.h"
#include "LogHelper.h"

#define RTMP_PUSH_FUNC(RETURN_TYPE, FUNC_NAME, ...) \
    extern "C" \
    JNIEXPORT RETURN_TYPE JNICALL Java_com_frank_living_LivePusher_ ## FUNC_NAME \
    (JNIEnv *env, jobject obj, ##__VA_ARGS__)       \

VideoStream *videoStream = nullptr;
AudioStream *audioStream = nullptr;
PacketQueue<RTMPPacket*> packetQueue;

uint32_t startTime;
std::atomic<bool> isPushing;

void callback(RTMPPacket *packet) {
    if (packet) {
        packet->m_nTimeStamp = RTMP_GetTime() - startTime;
        packetQueue.push(packet);
    }
}

void releasePackets(RTMPPacket *&packet) {
    if (packet) {
        RTMPPacket_Free(packet);
        delete packet;
        packet = nullptr;
    }
}

void *sendRtmpPacket(void *args) {
    char *url = static_cast<char *>(args);
    RTMP *rtmp;
    do {
        rtmp = RTMP_Alloc();
        RTMP_Init(rtmp);
        int ret = RTMP_SetupURL(rtmp, url);
        if (ret < 0) {
            LOGE("RTMP_SetupURL err=%d", ret);
            break;
        }
        rtmp->Link.timeout = 8;
        RTMP_EnableWrite(rtmp);
        ret = RTMP_Connect(rtmp, nullptr);
        if (ret < 0) {
            LOGE("RTMP_Connect err=%d", ret);
            break;
        }
        ret = RTMP_ConnectStream(rtmp, 0);
        if (ret < 0) {
            LOGE("RTMP_ConnectStream err=%d", ret);
            break;
        }
        RTMPPacket  *packet = nullptr;
        isPushing = true;
        packetQueue.setRunning(true);
        startTime = RTMP_GetTime();
        callback(audioStream->getAudioTag());

        while (isPushing) {
            // 从队列取出RTMP包
            packetQueue.pop(packet);
            if (!packet)
                continue;
            if (!isPushing)
                break;
            packet->m_nInfoField2 = rtmp->m_stream_id;
            ret = RTMP_SendPacket(rtmp, packet, 1);
            if (ret < 0) {
                releasePackets(packet);
                LOGE("RTMP_SendPacket err=%d", ret);
                break;
            }
            releasePackets(packet);
        }
    } while (0);

    isPushing = false;
    packetQueue.setRunning(false);
    packetQueue.clear();
    if (rtmp) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
    }
    delete url;
    return nullptr;
}

RTMP_PUSH_FUNC(void, native_1init) {
    videoStream = new VideoStream();
    audioStream = new AudioStream();
    videoStream->setVideoCallback(callback);
    audioStream->setAudioCallback(callback);
    packetQueue.setReleaseCallback(releasePackets);
}

RTMP_PUSH_FUNC(void, native_1start, jstring jpath) {
    if (isPushing) {
        return;
    }
    const char* path = env->GetStringUTFChars(jpath, JNI_FALSE);
    LOGI("native start, path=%s", path);
    char *url = new char[strlen(path) + 1];
    strcpy(url, path);

    // 启动rtmp推流线程
    std::thread pushThread(sendRtmpPacket, url);
    pushThread.detach();

    env->ReleaseStringUTFChars(jpath, path);
}

RTMP_PUSH_FUNC(void, native_1setVideoCodecInfo, jint width, jint height, jint frameRate, jint bitrate) {
    if (!videoStream)
        return;
    videoStream->setVideoEncInfo(width, height, frameRate, bitrate);
}

RTMP_PUSH_FUNC(void, native_1setAudioCodecInfo, jint sampleRate, jint channels) {
    if (!audioStream)
        return;
    audioStream->setAudioEncInfo(sampleRate, channels);
}

RTMP_PUSH_FUNC(jint, native_1getInputSamples) {
    if (!audioStream)
        return 0;
    return audioStream->getInputSamples();
}

RTMP_PUSH_FUNC(void, native_1pushVideo, jbyteArray yuv) {
    if (!videoStream || !isPushing)
        return;
    jbyte *yuv_plane = env->GetByteArrayElements(yuv, JNI_FALSE);
    videoStream->encodeVideo(yuv_plane);
    env->ReleaseByteArrayElements(yuv, yuv_plane, 0);
}

RTMP_PUSH_FUNC(void, native_1pushAudio, jbyteArray pcm) {
    if (!audioStream || !isPushing)
        return;
    jbyte *pcm_data = env->GetByteArrayElements(pcm, JNI_FALSE);
    audioStream->encodeAudio(pcm_data);
    env->ReleaseByteArrayElements(pcm, pcm_data, 0);
}

RTMP_PUSH_FUNC(void, native_1stop) {
    LOGI("native stop pushing.");
    isPushing = false;
    packetQueue.setRunning(false);
}

RTMP_PUSH_FUNC(void, native_1release) {
    delete videoStream;
    videoStream = nullptr;
    delete audioStream;
    audioStream = nullptr;
}