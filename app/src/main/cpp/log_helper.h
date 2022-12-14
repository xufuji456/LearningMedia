//
// Created by xu fulong on 2022/9/4.
//

#ifndef LEARNINGMEDIA_LOGHELPER_H
#define LEARNINGMEDIA_LOGHELPER_H

#include "android/log.h"

#define LOGI(TAG, FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, TAG, FORMAT, ##__VA_ARGS__)
#define LOGE(TAG, FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, TAG, FORMAT, ##__VA_ARGS__)

#define FFPLAY_TAG "FFmpegPlayer"
#define ALOGD(FORMAT, ...) __android_log_print(ANDROID_LOG_DEBUG, FFPLAY_TAG, FORMAT, ##__VA_ARGS__)
#define ALOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, FFPLAY_TAG, FORMAT, ##__VA_ARGS__)
#define ALOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, FFPLAY_TAG, FORMAT, ##__VA_ARGS__)

#endif //LEARNINGMEDIA_LOGHELPER_H
