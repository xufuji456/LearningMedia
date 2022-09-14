//
// Created by xu fulong on 2022/9/11.
//

#ifndef LEARNINGMEDIA_FF_AUDIO_RESAMPLE_H
#define LEARNINGMEDIA_FF_AUDIO_RESAMPLE_H

#include "log_helper.h"

#ifdef __cplusplus
extern "C" {
#endif
#include "libavformat/avformat.h"
#include "libavformat/avio.h"
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"

#include "libavutil/audio_fifo.h"
#ifdef __cplusplus
}
#endif

struct AudioResample {
    int64_t pts = 0;

    AVPacket inPacket;
    AVPacket outPacket;
    AVFrame  *inFrame;
    AVFrame  *outFrame;

    SwrContext *resampleCtx;
    AVAudioFifo *fifo;

    AVFormatContext *inFormatCtx;
    AVFormatContext *outFormatCtx;
    AVCodecContext  *inCodecCtx;
    AVCodecContext  *outCodecCtx;
};

class FFAudioResample {
private:
    AudioResample *resample;

    int openInput(const char *inputPath);

    int openOutput(const char *outputPath, int sampleRate);

    int decodeAudioFrame(AVFrame *frame, int *data_present, int *finished);

    int decodeAndConvert(int *finished);

    int encodeAudioFrame(AVFrame *frame, int *data_present);

    int encodeAndWrite();

public:
    FFAudioResample();

    ~FFAudioResample();

    int resampling(const char *inputPath, const char *outputPath, int sampleRate);

};

#endif //LEARNINGMEDIA_FF_AUDIO_RESAMPLE_H
