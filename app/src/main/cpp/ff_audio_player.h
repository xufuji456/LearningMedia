//
// Created by xu fulong on 2022/9/4.
//

#ifndef LEARNINGMEDIA_FF_AUDIO_PLAYER_H
#define LEARNINGMEDIA_FF_AUDIO_PLAYER_H

#ifdef __cplusplus
extern "C" {
#endif
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
#include "libavfilter/buffersrc.h"
#include "libavfilter/buffersink.h"
#include "libavfilter/avfilter.h"
#ifdef __cplusplus
}
#endif

class FFAudioPlayer {
private:

    AVFormatContext *formatContext;
    AVCodecContext *codecContext;
    int audio_index = -1;
    SwrContext *swrContext;
    int out_sample_rate;
    int out_ch_layout;
    int out_channel;
    enum AVSampleFormat out_sample_fmt;
    AVPacket *packet;
    AVFrame *frame;
    AVFrame *filterFrame;
    uint8_t *out_buffer;

    AVFilterGraph *filterGraph;
    AVFilterContext *srcContext;
    AVFilterContext *sinkContext;

    // 滤波描述
    const char *filterDesc;
    bool filterAgain = false;
    bool exitPlaying = false;

public:
    int open(const char* path);

    int getSampleRate() const;

    int getChannel() const;

    int decodeAudio();

    void setFilterAgain(const char *filter);

    void setExitPlaying(bool exit);

    uint8_t *getDecodeFrame() const;

    void close();
};
#endif //LEARNINGMEDIA_FF_AUDIO_PLAYER_H
