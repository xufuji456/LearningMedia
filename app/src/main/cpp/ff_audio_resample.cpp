//
// Created by xu fulong on 2022/9/11.
//

#include "ff_audio_resample.h"

#define RESAMPLE_TAG "AudioResample"

FFAudioResample::FFAudioResample() {
    resample = new AudioResample();
}

FFAudioResample::~FFAudioResample() {
    delete resample;
}

// 初始化AVFrame，包括音频格式、采样数、声道布局、采样率
static int initOutputFrame(AudioResample **pResample) {
    AudioResample *ar = *pResample;

    AVFrame *frame        = av_frame_alloc();
    frame->format         = ar->outCodecCtx->sample_fmt;
    frame->nb_samples     = ar->outCodecCtx->frame_size;
    frame->sample_rate    = ar->outCodecCtx->sample_rate;
    frame->channel_layout = ar->outCodecCtx->channel_layout;

    int ret = av_frame_get_buffer(frame, 0);
    ar->outFrame = frame;
    *pResample   = ar;
    return ret;
}

// 初始化SwrContext上下文
static int initResample(AudioResample **pResample) {
    AudioResample *ar = *pResample;
    SwrContext *context = swr_alloc_set_opts(nullptr,
                                             av_get_default_channel_layout(ar->outCodecCtx->channels),
                                             ar->outCodecCtx->sample_fmt,
                                             ar->outCodecCtx->sample_rate,
                                             av_get_default_channel_layout(ar->inCodecCtx->channels),
                                             ar->inCodecCtx->sample_fmt,
                                             ar->inCodecCtx->sample_rate,
                                             0, nullptr);
    int ret = swr_init(context);
    ar->resampleCtx = context;
    *pResample = ar;
    return ret;
}

// 分配重采样的内存
static int initConvertedSamples(AudioResample **pResample, uint8_t ***converted_input_samples, int frame_size) {
    int ret;
    AudioResample *ar = *pResample;
    *converted_input_samples = (uint8_t **) calloc(ar->outCodecCtx->channels, sizeof(**converted_input_samples));

    if ((ret = av_samples_alloc(*converted_input_samples, nullptr,
                                ar->outCodecCtx->channels,
                                frame_size,
                                ar->outCodecCtx->sample_fmt, 0)) < 0) {
        LOGE(RESAMPLE_TAG, "av_samples_alloc error:%s", av_err2str(ret));
        av_freep(&(*converted_input_samples)[0]);
        free(*converted_input_samples);
        return ret;
    }
    return 0;
}

int FFAudioResample::openInput(const char *inputPath) {
    AVStream *audioStream = nullptr;
    int ret = avformat_open_input(&resample->inFormatCtx, inputPath, nullptr, nullptr);
    if (ret < 0) {
        LOGE(RESAMPLE_TAG, "avformat_open_input err=%s", av_err2str(ret));
        return ret;
    }
    avformat_find_stream_info(resample->inFormatCtx, nullptr);
    for (int i = 0; i < resample->inFormatCtx->nb_streams; ++i) {
        if (AVMEDIA_TYPE_AUDIO == resample->inFormatCtx->streams[i]->codecpar->codec_type) {
            audioStream = resample->inFormatCtx->streams[i];
        }
    }
    if (!audioStream)
        return -1;
    const AVCodec *codec = avcodec_find_decoder(audioStream->codecpar->codec_id);
    if (!codec) {
        LOGE(RESAMPLE_TAG, "can't find decoder:%s", avcodec_get_name(audioStream->codecpar->codec_id));
        return -1;
    }
    resample->inCodecCtx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(resample->inCodecCtx, audioStream->codecpar);
    // 打开解码器
    ret = avcodec_open2(resample->inCodecCtx, codec, nullptr);
    if (ret < 0) {
        LOGE(RESAMPLE_TAG, "avcodec_open2 err=%s", av_err2str(ret));
    }
    resample->inFrame = av_frame_alloc();
    return ret;
}

int FFAudioResample::openOutput(const char *outputPath, int sampleRate) {
    AVIOContext *avioContext = nullptr;
    int ret = avio_open(&avioContext, outputPath, AVIO_FLAG_WRITE);
    if (ret < 0) {
        LOGE(RESAMPLE_TAG, "avio_open err=%s", av_err2str(ret));
        return ret;
    }

    resample->outFormatCtx          = avformat_alloc_context();
    resample->outFormatCtx->pb      = avioContext;
    resample->outFormatCtx->url     = av_strdup(outputPath);
    resample->outFormatCtx->oformat = av_guess_format(nullptr, outputPath, nullptr);

    const AVCodec *codec = avcodec_find_encoder(resample->inCodecCtx->codec_id);
    if (!codec) {
        LOGE(RESAMPLE_TAG, "avcodec_find_encoder err=%s", resample->inCodecCtx->codec->name);
        return -1;
    }
    AVStream *stream = avformat_new_stream(resample->outFormatCtx, nullptr);
    resample->outCodecCtx = avcodec_alloc_context3(codec);
    // 设置codecContext相关参数
    resample->outCodecCtx->channels       = resample->inCodecCtx->channels;
    resample->outCodecCtx->channel_layout = resample->inCodecCtx->channel_layout;
    resample->outCodecCtx->sample_rate    = sampleRate;
    resample->outCodecCtx->sample_fmt     = codec->sample_fmts[0];
    // 允许使用试验性编码器
    resample->outCodecCtx->strict_std_compliance = FF_COMPLIANCE_EXPERIMENTAL;
    // 设置timebase时间基
    stream->time_base.den = sampleRate;
    stream->time_base.num = 1;
    // 加上global flag
    if (resample->outFormatCtx->oformat->flags & AVFMT_GLOBALHEADER) {
        resample->outCodecCtx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
    // 打开编码器
    ret = avcodec_open2(resample->outCodecCtx, codec, nullptr);
    if (ret < 0) {
        LOGE(RESAMPLE_TAG, "avcodec_open2 err=%s", av_err2str(ret));
        return ret;
    }
    avcodec_parameters_from_context(stream->codecpar, resample->outCodecCtx);
    return ret;
}

int FFAudioResample::decodeAudioFrame(AVFrame *frame, int *data_present, int *finished) {
    // 读取音频帧
    int ret = av_read_frame(resample->inFormatCtx, &resample->inPacket);
    if (ret == AVERROR_EOF) {
        *finished = 1;
    }
    // 判断是否为音频流
    if (AVMEDIA_TYPE_AUDIO != resample->inFormatCtx->streams[resample->inPacket.stream_index]->codecpar->codec_type) {
        ret = 0;
        goto end;
    }
    // 发送到解码器
    ret = avcodec_send_packet(resample->inCodecCtx, &resample->inPacket);
    if (ret < 0) {
        LOGE(RESAMPLE_TAG, "avcodec_send_packet error=%s", av_err2str(ret));
    }
    ret = avcodec_receive_frame(resample->inCodecCtx, frame);
    if (ret == AVERROR(EAGAIN)) {
        ret = 0;
        goto end;
    } else if (ret == AVERROR_EOF) {
        *finished = 1;
        ret = 0;
        goto end;
    } else if (ret < 0) {
        LOGE(RESAMPLE_TAG, "decode frame error=%s", av_err2str(ret));
        goto end;
    } else {
        *data_present = 1;
        goto end;
    }
end:
    av_packet_unref(&resample->inPacket);
    return ret;
}

// 解码，重采样，写到FIFO队列
int FFAudioResample::decodeAndConvert(int *finished) {
    uint8_t **dst_samples = nullptr;
    int ret = 0;
    int data_present = 0;

    if (decodeAudioFrame(resample->inFrame, &data_present, finished) < 0)
        goto end;
    if (*finished) {
        ret = 0;
        goto end;
    }
    // 解码成功，进行重采样
    if (data_present) {
        int dst_nb_samples = av_rescale_rnd(resample->inFrame->nb_samples, resample->outCodecCtx->sample_rate,
                                           resample->inCodecCtx->sample_rate, AV_ROUND_UP);
        if (initConvertedSamples(&resample, &dst_samples, dst_nb_samples))
            goto end;
        ret = swr_convert(resample->resampleCtx, dst_samples, dst_nb_samples,
                (const uint8_t **)(resample->inFrame->extended_data), resample->inFrame->nb_samples);
        if (ret < 0) {
            LOGE(RESAMPLE_TAG, "resample error=%s", av_err2str(ret));
            goto end;
        }
        av_audio_fifo_write(resample->fifo, (void **)dst_samples, dst_nb_samples);
    }
    ret = 0;
end:
    if (dst_samples) {
        av_freep(&dst_samples[0]);
        free(dst_samples);
    }
    return ret;
}

int FFAudioResample::encodeAudioFrame(AVFrame *frame, int *data_present) {
    int ret = 0;
    // pts累加
    if (frame) {
        frame->pts = resample->pts;
        resample->pts += frame->nb_samples;
    }
    // 发送音频帧到编码器
    ret = avcodec_send_frame(resample->outCodecCtx, frame);
    if (ret == AVERROR_EOF) {
        ret = 0;
        goto end;
    } else if (ret < 0) {
        LOGE(RESAMPLE_TAG, "encode frame err=%s", av_err2str(ret));
        goto end;
    }
    ret = avcodec_receive_packet(resample->outCodecCtx, &resample->outPacket);
    if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
        ret = 0;
        goto end;
    } else if (ret < 0) {
        goto end;
    } else {
        *data_present = 1;
    }
   // 封装：音频帧写到文件
    if (*data_present && (ret = av_write_frame(resample->outFormatCtx, &resample->outPacket)) < 0) {
        LOGE(RESAMPLE_TAG, "av_write_frame err=%s", av_err2str(ret));
    }
end:
    av_packet_unref(&resample->outPacket);
    return ret;
}

// 从FIFO队列读取，编码，封装
int FFAudioResample::encodeAndWrite() {
    int data_written;
    int frame_size = FFMIN(av_audio_fifo_size(resample->fifo), resample->outCodecCtx->frame_size);
    resample->outFrame->nb_samples = frame_size;

    av_audio_fifo_read(resample->fifo, (void **)resample->outFrame->data, frame_size);
    if (encodeAudioFrame(resample->outFrame, &data_written)) {
        return AVERROR_EXIT;
    }
    return 0;
}

int FFAudioResample::resampling(const char *inputPath, const char *outputPath, int sampleRate) {
    int ret = 0;
    // 打开输入文件
    if (openInput(inputPath) < 0)
        goto end;
    // 打开输出文件
    if (openOutput(outputPath, sampleRate) < 0)
        goto end;
    if (initResample(&resample) < 0) {
        goto end;
    }
    if (initOutputFrame(&resample) < 0) {
        goto end;
    }
    resample->fifo = av_audio_fifo_alloc(resample->outCodecCtx->sample_fmt,
                                         resample->outCodecCtx->channels,
                                         1024 * 10);
    // 写文件媒体头
    ret = avformat_write_header(resample->outFormatCtx, nullptr);
    if (ret < 0) {
        LOGE(RESAMPLE_TAG, "avformat_write_header err=%s", av_err2str(ret));
        goto end;
    }

    while (true) {
        int finished = 0;
        int frame_size = resample->outCodecCtx->frame_size;
        // 解码，重采样
        while (av_audio_fifo_size(resample->fifo) < frame_size) {
            if (decodeAndConvert(&finished) < 0)
                goto end;
            if (finished)
                break;
        }
        // 编码，封装
        while (av_audio_fifo_size(resample->fifo) >= frame_size ||
                (finished && av_audio_fifo_size(resample->fifo) > 0)) {
            if (encodeAndWrite() < 0)
                goto end;
        }
        // 编码剩下缓冲区的数据
        if (finished) {
            int data_written = 0;
            do {
                data_written = 0;
                if (encodeAudioFrame(nullptr, &data_written))
                    goto end;
            } while (data_written);
            break;
        }
    }

    // 写文件尾
    av_write_trailer(resample->outFormatCtx);
    ret = 0;
end:
    if (resample->fifo)
        av_audio_fifo_free(resample->fifo);
    swr_free(&resample->resampleCtx);
    if (resample->inFormatCtx)
        avformat_close_input(&resample->inFormatCtx);
    if (resample->inCodecCtx)
        avcodec_free_context(&resample->inCodecCtx);
    if (resample->outFormatCtx)
        avformat_close_input(&resample->outFormatCtx);
    if (resample->outCodecCtx)
        avcodec_free_context(&resample->outCodecCtx);
    if (resample->inFrame)
        av_frame_free(&resample->inFrame);
    if (resample->outFrame)
        av_frame_free(&resample->outFrame);
    return ret;
}