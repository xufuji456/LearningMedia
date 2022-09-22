//
// Created by xu fulong on 2022/9/4.
//

#ifndef LEARNINGMEDIA_LOGHELPER_H
#define LEARNINGMEDIA_LOGHELPER_H

#include "android/log.h"

#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, "RtmpLive", FORMAT, ##__VA_ARGS__)
#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, "RtmpLive", FORMAT, ##__VA_ARGS__)

/*************** callback to Java ***************/
//error code for opening video encoder
const int ERROR_VIDEO_ENCODER_OPEN = 0x01;
//error code for video encoding
const int ERROR_VIDEO_ENCODE = 0x02;
//error code for opening audio encoder
const int ERROR_AUDIO_ENCODER_OPEN = 0x03;
//error code for audio encoding
const int ERROR_AUDIO_ENCODE = 0x04;
//error code for RTMP connecting
const int ERROR_RTMP_CONNECT = 0x05;
//error code for connecting stream
const int ERROR_RTMP_CONNECT_STREAM = 0x06;
//error code for sending packet
const int ERROR_RTMP_SEND_PACKET = 0x07;

#endif //LEARNINGMEDIA_LOGHELPER_H
