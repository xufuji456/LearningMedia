//
// Created by xu fulong on 2022/9/4.
//

#include "ff_audio_player.h"
#include "log_helper.h"

#define AUDIO_TAG "AudioPlayer"
#define BUFFER_SIZE (48000 * 10)

int FFAudioPlayer::open(const char *path) {
    if (!path)
        return -1;

    int ret;
    const AVCodec *codec;
    frame = av_frame_alloc();
    packet = av_packet_alloc();
    out_buffer = new uint8_t [BUFFER_SIZE];

    // 打开输入流
    ret = avformat_open_input(&formatContext, path, nullptr, nullptr);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "avformat_open_input error=%s", av_err2str(ret));
        return ret;
    }
    // 查找stream信息
    avformat_find_stream_info(formatContext, nullptr);
    // 找到音频index
    for (int i=0; i<formatContext->nb_streams; i++) {
        if (AVMEDIA_TYPE_AUDIO == formatContext->streams[i]->codecpar->codec_type) {
            audio_index = i;
            break;
        }
    }
    if (audio_index == -1) {
        return -1;
    }
    // 找到codec
    codec = avcodec_find_decoder(formatContext->streams[audio_index]->codecpar->codec_id);
    codecContext = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(codecContext, formatContext->streams[audio_index]->codecpar);
    // 打开解码器
    ret = avcodec_open2(codecContext, codec, nullptr);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "avcodec_open2 error=%s", av_err2str(ret));
        return ret;
    }
    // 输入输出参数：采样率、声道布局、音频格式
    int in_sample_rate = codecContext->sample_rate;
    auto in_sample_fmt = codecContext->sample_fmt;
    int in_ch_layout   = codecContext->ch_layout.u.mask; // codecContext->channel_layout
    out_sample_rate    = in_sample_rate; // 输出采样率等于输入采样率
    out_sample_fmt     = AV_SAMPLE_FMT_S16; // 16位
    out_ch_layout      = AV_CH_LAYOUT_STEREO; // 双声道
    out_channel        = codecContext->ch_layout.nb_channels; // codecContext->channels
    // 初始化音频格式转换上下文swrContext
    swrContext = swr_alloc();
    swr_alloc_set_opts(swrContext, out_ch_layout, out_sample_fmt, out_sample_rate,
                       in_ch_layout, in_sample_fmt, in_sample_rate, 0, nullptr);
    swr_init(swrContext);
    return 0;
}

int FFAudioPlayer::getChannel() const {
    return out_channel;
}

int FFAudioPlayer::getSampleRate() const {
    return out_sample_rate;
}

int FFAudioPlayer::decodeAudio() {
    int ret;
    // 读取音频数据(解封装)
    ret = av_read_frame(formatContext, packet);
    if (ret < 0) {
        return ret;
    }
    // 判断是否为音频帧
    if (packet->stream_index != audio_index) {
        return 0;
    }
    // 解码音频帧
    ret = avcodec_send_packet(codecContext, packet);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "avcodec_send_packet=%s", av_err2str(ret));
    }
    ret = avcodec_receive_frame(codecContext, frame);
    if (ret < 0) {
        if (ret == AVERROR(EAGAIN)) {
            return 0;
        } else {
            return ret;
        }
    }
    // 音频格式转换
    swr_convert(swrContext, &out_buffer, BUFFER_SIZE,
            (const uint8_t **)(frame->data), frame->nb_samples);
    // 获取转换后缓冲区大小
    int buffer_size = av_samples_get_buffer_size(nullptr, out_channel,
                                                 frame->nb_samples, out_sample_fmt, 1);
    LOGI(AUDIO_TAG, "buffer_size=%d", buffer_size);
    av_frame_unref(frame);
    av_packet_unref(packet);
    return buffer_size;
}


uint8_t *FFAudioPlayer::getDecodeFrame() const {
    return out_buffer;
}

void FFAudioPlayer::close() {
    if (formatContext) {
        avformat_close_input(&formatContext);
    }
    if (codecContext) {
        avcodec_free_context(&codecContext);
    }
    if (packet) {
        av_packet_free(&packet);
    }
    if (frame) {
        av_frame_free(&frame);
    }
    if (swrContext) {
        swr_close(swrContext);
    }
    delete[] out_buffer;
}