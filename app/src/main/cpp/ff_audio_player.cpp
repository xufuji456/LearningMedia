//
// Created by xu fulong on 2022/9/4.
//

#include "ff_audio_player.h"
#include "log_helper.h"

#define AUDIO_TAG "AudioPlayer"
#define BUFFER_SIZE (48000 * 10)

#define FILTER_DESC "superequalizer=6b=4:8b=5:10b=5"

int initFilter(const char *filter, AVCodecContext *codecCtx, AVFilterGraph **graph,
               AVFilterContext **src, AVFilterContext **sink) {
    int ret = 0;
    char args[512];
    AVFilterContext *buffersrc_ctx;
    AVFilterContext *buffersink_ctx;
    AVFilterInOut *inputs      = avfilter_inout_alloc();
    AVFilterInOut *outputs     = avfilter_inout_alloc();
    const AVFilter *buffersrc  = avfilter_get_by_name("abuffer");
    const AVFilter *buffersink = avfilter_get_by_name("abuffersink");

    AVFilterGraph *filter_graph = avfilter_graph_alloc();
    if (!inputs || !outputs || !filter_graph) {
        goto end;
    }
    codecCtx->channel_layout = av_get_default_channel_layout(codecCtx->channels);
    // 输入参数到数组
    snprintf(args, sizeof(args), "time_base=%d/%d:sample_rate=%d:sample_fmt=%s:channel_layout=0x%" PRIx64 "",
             codecCtx->time_base.num, codecCtx->time_base.den, codecCtx->sample_rate,
            av_get_sample_fmt_name(codecCtx->sample_fmt), codecCtx->channel_layout);
    // 创建滤波器
    ret = avfilter_graph_create_filter(&buffersrc_ctx, buffersrc, "in", args, nullptr, filter_graph);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "create buffersrc err=%s", av_err2str(ret));
        goto end;
    }
    ret = avfilter_graph_create_filter(&buffersink_ctx, buffersink, "out", nullptr, nullptr, filter_graph);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "create buffersink err=%s", av_err2str(ret));
        goto end;
    }

    outputs->name       = av_strdup("in");
    outputs->next       = nullptr;
    outputs->pad_idx    = 0;
    outputs->filter_ctx = buffersrc_ctx;
    inputs->name        = av_strdup("out");
    inputs->next        = nullptr;
    inputs->pad_idx     = 0;
    inputs->filter_ctx  = buffersink_ctx;

    // 解析滤波器语句，添加到graph
    ret = avfilter_graph_parse_ptr(filter_graph, filter, &inputs, &outputs, nullptr);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "avfilter_graph_parse_ptr err=%s", av_err2str(ret));
        goto end;
    }
    ret = avfilter_graph_config(filter_graph, nullptr);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "avfilter_graph_config err=%s", av_err2str(ret));
        goto end;
    }

    *graph = filter_graph;
    *src   = buffersrc_ctx;
    *sink  = buffersink_ctx;

end:
    avfilter_inout_free(&inputs);
    avfilter_inout_free(&outputs);
    return ret;
}

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

    filterFrame = av_frame_alloc();
    initFilter(FILTER_DESC, codecContext, &filterGraph, &srcContext, &sinkContext);

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
    if (exitPlaying)
        return -1;
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
    // 判断是否要更新filter_desc
    if (filterAgain) {
        filterAgain = false;
        if (filterGraph) {
            avfilter_graph_free(&filterGraph);
        }
        initFilter(filterDesc, codecContext, &filterGraph, &srcContext, &sinkContext);
    }

    // 输入到滤波器
    ret = av_buffersrc_add_frame(srcContext, frame);
    if (ret < 0) {
        LOGE(AUDIO_TAG, "av_buffersrc_add_frame err=%s", av_err2str(ret));
    }
    // 从滤波器取出
    ret = av_buffersink_get_frame(sinkContext, filterFrame);
    if (ret == AVERROR(EAGAIN)) {
        return 0;
    } else if (ret < 0) {
        LOGE(AUDIO_TAG, "av_buffersink_get_frame err=%s", av_err2str(ret));
        return ret;
    }

    // 音频格式转换
    swr_convert(swrContext, &out_buffer, BUFFER_SIZE,
            (const uint8_t **)(filterFrame->data), filterFrame->nb_samples);
    // 获取转换后缓冲区大小
    int buffer_size = av_samples_get_buffer_size(nullptr, out_channel,
                                                 filterFrame->nb_samples, out_sample_fmt, 1);
    LOGI(AUDIO_TAG, "buffer_size=%d", buffer_size);
    av_frame_unref(frame);
    av_frame_unref(filterFrame);
    av_packet_unref(packet);
    return buffer_size;
}


uint8_t *FFAudioPlayer::getDecodeFrame() const {
    return out_buffer;
}

void FFAudioPlayer::setFilterAgain(const char *filter) {
    filterDesc = filter;
    filterAgain = true;
}

void FFAudioPlayer::setExitPlaying(bool exit) {
    exitPlaying = exit;
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
    if (filterFrame) {
        av_frame_free(&filterFrame);
    }
    if (filterGraph) {
        avfilter_graph_free(&filterGraph);
    }
    if (swrContext) {
        swr_close(swrContext);
    }
    delete[] out_buffer;
}