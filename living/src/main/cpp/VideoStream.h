//
// Created by xu fulong on 2022/9/22.
//

#ifndef LEARNINGMEDIA_VIDEOSTREAM_H
#define LEARNINGMEDIA_VIDEOSTREAM_H

#include <mutex>
#include "inttypes.h"
#include "x264/x264.h"
#include "rtmp/rtmp.h"

class VideoStream {
    typedef void (*VideoCallback)(RTMPPacket *pkt);

private:
    std::mutex m_mutex;

    int m_frameLen;
    x264_t *m_videoEncoder;
    x264_picture_t *m_pic_in;

    VideoCallback videoCallback;

    void sendSpsPps(uint8_t *sps, uint8_t *pps, int spsLen, int ppsLen);

    void sendFrame(int type, uint8_t *payload, int payloadLen);

public:

    VideoStream();

    ~VideoStream();

    int setVideoEncInfo(int width, int height, int frameRate, int bitrate);

    void encodeVideo(int8_t *data);

    void setVideoCallback(VideoCallback callback);

};


#endif //LEARNINGMEDIA_VIDEOSTREAM_H
