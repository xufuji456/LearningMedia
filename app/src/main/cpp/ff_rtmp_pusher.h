//
// Created by xu fulong on 2022/9/18.
//

#ifndef LEARNINGMEDIA_FF_RTMP_PUSHER_H
#define LEARNINGMEDIA_FF_RTMP_PUSHER_H

#include "log_helper.h"

#ifdef __cplusplus
extern "C" {
#endif
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libavutil/avutil.h"
#include <libavutil/time.h>
#ifdef __cplusplus
}
#endif

class FFRtmpPusher {
private:
    AVFormatContext *inFormatCtx;
    AVFormatContext *outFormatCtx;

    int video_index = -1;
    int audio_index = -1;

    AVPacket packet;

public:
    int open (const char* inputPath, const char* outputPath);

    int push();

    void close();
};

#endif //LEARNINGMEDIA_FF_RTMP_PUSHER_H
