
#include "MediaPlayer.h"

MediaPlayer::MediaPlayer() {
    avformat_network_init();
    m_playerParam  = new PlayerParam();
    m_duration     = -1;
    m_lastPause    = -1;
    m_audioDecoder = nullptr;
    m_videoDecoder = nullptr;

    m_eof            = 0;
    m_exitPlay       = true;
    m_readThread     = nullptr;
    m_audioRender    = nullptr;
    m_audioResampler = nullptr;
    m_avSync = new AVSync(m_playerParam);
}

MediaPlayer::~MediaPlayer() {
    avformat_network_deinit();
}

void MediaPlayer::setDataSource(const char *url) {
    Mutex::AutoLock lock(m_playerMutex);
    m_playerParam->url = av_strdup(url);
}

void MediaPlayer::setVideoRender(VideoRender *render) {
    Mutex::AutoLock lock(m_playerMutex);
    m_avSync->setVideoRender(render);
}

int MediaPlayer::prepare() {
    Mutex::AutoLock lock(m_playerMutex);
    if (m_playerParam->url == nullptr)
        return -1;
    m_playerParam->m_abortReq = 0;
    m_readThread = new Thread(this);
    m_readThread->start();
    return 0;
}

int MediaPlayer::prepareAsync() {
    Mutex::AutoLock lock(m_playerMutex);
    if (m_playerParam->url == nullptr)
        return -1;
    m_playerParam->m_messageQueue->sendMessage(MSG_REQUEST_PREPARE);
    return 0;
}

void MediaPlayer::start() {
    Mutex::AutoLock lock(m_playerMutex);
    m_playerParam->m_abortReq = 0;
    m_playerParam->m_pauseReq = 0;
    m_exitPlay = false;
    m_playerCond.signal();
}

void MediaPlayer::pause() {
    Mutex::AutoLock lock(m_playerMutex);
    m_playerParam->m_pauseReq = 1;
    m_avSync->updateClock(true);
    m_playerCond.signal();
}

void MediaPlayer::resume() {
    Mutex::AutoLock lock(m_playerMutex);
    m_playerParam->m_pauseReq = 0;
    m_avSync->updateClock(false);
    m_playerCond.signal();
}

void MediaPlayer::seekTo(long timeMs) {
    if (m_duration <= 0)
        return;
    m_playerMutex.lock();
    while (m_playerParam->m_seekRequest) {
        m_playerCond.wait(m_playerMutex);
    }
    m_playerMutex.unlock();

    int64_t seek_time = av_rescale(timeMs, AV_TIME_BASE, 1000);
    m_playerParam->m_seekPos = seek_time;
    m_playerParam->m_seekFlag &= ~AVSEEK_FLAG_BYTE;
    m_playerParam->m_seekRequest = 1;
    m_playerCond.signal();
}

void MediaPlayer::setVolume(float volume) {
    if (m_audioRender) {
        m_audioRender->setVolume(volume);
    }
}

void MediaPlayer::setMute(int mute) {
    m_playerMutex.lock();
    m_playerParam->m_mute = mute;
    m_playerCond.signal();
    m_playerMutex.unlock();
}

void MediaPlayer::setRate(float rate) {
    m_playerMutex.lock();
    m_playerParam->m_playbackRate = rate;
    m_playerCond.signal();
    m_playerMutex.unlock();
}

int MediaPlayer::getRotate() {
    Mutex::AutoLock lock(m_playerMutex);
    if (m_videoDecoder) {
        return m_videoDecoder->getRotate();
    }
    return 0;
}

int MediaPlayer::getVideoWidth() {
    Mutex::AutoLock lock(m_playerMutex);
    if (m_videoDecoder) {
        return m_videoDecoder->getCodecContext()->width;
    }
    return 0;
}

int MediaPlayer::getVideoHeight() {
    Mutex::AutoLock lock(m_playerMutex);
    if (m_videoDecoder) {
        return m_videoDecoder->getCodecContext()->height;
    }
    return 0;
}

long MediaPlayer::getCurrentPosition() {
    Mutex::AutoLock lock(m_playerMutex);
    if (m_playerParam->m_seekRequest) {
        return m_playerParam->m_seekPos;
    } else {
        int64_t pos;
        double clock = m_avSync->getMasterClock();
        if (isnan(clock)) {
            pos = m_playerParam->m_seekPos;
        } else {
            pos = (int64_t)(clock * 1000);
        }
        return pos < m_duration ? pos : m_duration;
    }
}

long MediaPlayer::getDuration() {
    Mutex::AutoLock lock(m_playerMutex);
    return m_duration;
}

int MediaPlayer::isPlaying() {
    Mutex::AutoLock lock(m_playerMutex);
    return !m_playerParam->m_pauseReq && !m_playerParam->m_abortReq;
}

static int avformat_interrupt_cb(void *ctx) {
    auto *playerState = (PlayerParam *) ctx;
    if (playerState->m_abortReq) {
        return AVERROR_EOF;
    }
    return 0;
}

FFMessageQueue *MediaPlayer::getMessageQueue() {
    Mutex::AutoLock lock(m_playerMutex);
    return m_playerParam->m_messageQueue;
}

AVStream *MediaPlayer::getAVStream(int mediaType) const {
    if (mediaType == AVMEDIA_TYPE_AUDIO) {
        return m_playerParam->m_audioStream;
    } else if (mediaType == AVMEDIA_TYPE_VIDEO) {
        return m_playerParam->m_videoStream;
    } else if (mediaType == AVMEDIA_TYPE_SUBTITLE) {
        return m_playerParam->m_subtitleStream;
    } else {
        return nullptr;
    }
}

AVFormatContext *MediaPlayer::getMetadata() const {
    return m_playerParam->m_formatCtx;
}

void MediaPlayer::stop() {
    m_playerMutex.lock();
    m_playerParam->m_abortReq = 1;
    m_playerCond.signal();
    m_playerMutex.unlock();
    m_playerMutex.lock();
    while (!m_exitPlay) {
        m_playerCond.wait(m_playerMutex);
    }
    m_playerMutex.unlock();
    if (m_readThread != nullptr) {
        m_readThread->join();
        delete m_readThread;
        m_readThread = nullptr;
    }
}

int MediaPlayer::reset() {
    stop();
    if (m_avSync) {
        m_avSync->reset();
        delete m_avSync;
        m_avSync = nullptr;
    }
    if (m_audioDecoder != nullptr) {
        m_audioDecoder->stop();
        delete m_audioDecoder;
        m_audioDecoder = nullptr;
    }
    if (m_videoDecoder != nullptr) {
        m_videoDecoder->stop();
        delete m_videoDecoder;
        m_videoDecoder = nullptr;
    }
    if (m_audioRender != nullptr) {
        m_audioRender->stop();
        delete m_audioRender;
        m_audioRender = nullptr;
    }
    if (m_audioResampler) {
        delete m_audioResampler;
        m_audioResampler = nullptr;
    }
    if (m_playerParam) {
        if (m_playerParam->m_formatCtx != nullptr) {
            avformat_close_input(&m_playerParam->m_formatCtx);
            m_playerParam->m_formatCtx = nullptr;
        }
        delete m_playerParam;
        m_playerParam = nullptr;
    }
    return 0;
}

void MediaPlayer::startAudioRender(PlayerParam *playerParam) {
    if (playerParam->m_audioCodecCtx) {
        int ret = openAudioRender(playerParam->m_audioCodecCtx->ch_layout,
                                  playerParam->m_audioCodecCtx->sample_rate);
        if (ret < 0) {
            closeDecoder(m_playerParam->m_audioIndex);
        } else {
            m_audioRender->start();
        }
    }
}

// 打开解码器
int MediaPlayer::openDecoder(int streamIndex) {
    int ret;
    AVCodecContext *codecContext;
    if (streamIndex < 0 || streamIndex >= m_playerParam->m_formatCtx->nb_streams)
        return -1;

    codecContext = avcodec_alloc_context3(nullptr);
    do {
        ret = avcodec_parameters_to_context(codecContext,
                                            m_playerParam->m_formatCtx->streams[streamIndex]->codecpar);
        if (ret < 0) {
            break;
        }
        // 查找解码器
        codecContext->pkt_timebase = m_playerParam->m_formatCtx->streams[streamIndex]->time_base;
        const AVCodec *codec = avcodec_find_decoder(codecContext->codec_id);
        if (!codec) {
            ret = AVERROR(EINVAL);
            break;
        }
        // 打开对应的解码器
        if ((ret = avcodec_open2(codecContext, codec, nullptr)) < 0) {
            break;
        }

        m_playerParam->m_formatCtx->streams[streamIndex]->discard = AVDISCARD_DEFAULT;
        switch (codecContext->codec_type) {
            case AVMEDIA_TYPE_AUDIO: {
                m_playerParam->m_audioIndex = streamIndex;
                m_playerParam->m_audioCodecCtx = codecContext;
                m_playerParam->m_audioStream = m_playerParam->m_formatCtx->streams[streamIndex];
                m_audioDecoder = new AudioDecoder(m_playerParam);
                m_audioDecoder->start();
                startAudioRender(m_playerParam);
                m_avSync->setAudioDecoder(m_audioDecoder);
                break;
            }
            case AVMEDIA_TYPE_VIDEO: {
                m_playerParam->m_videoIndex = streamIndex;
                m_playerParam->m_videoStream = m_playerParam->m_formatCtx->streams[streamIndex];
                m_playerParam->m_videoCodecCtx = codecContext;
                m_videoDecoder = new VideoDecoder(m_playerParam);
            }
            case AVMEDIA_TYPE_SUBTITLE:
                av_log(nullptr, AV_LOG_WARNING, "not implementation...");
                break;
            default:
                break;
        }

    } while (false);

    if (ret < 0) {
        avcodec_free_context(&codecContext);
    }
    return ret;
}

// 关闭解码器
void MediaPlayer::closeDecoder(int streamIndex) {
    if (streamIndex < 0 || streamIndex >= m_playerParam->m_formatCtx->nb_streams)
        return;
    AVStream *stream = m_playerParam->m_formatCtx->streams[streamIndex];
    switch (stream->codecpar->codec_type) {
        case AVMEDIA_TYPE_AUDIO: {
            if (m_audioDecoder)
                m_audioDecoder->flush();
            if (m_audioRender) {
                m_audioRender->stop();
                delete m_audioRender;
                m_audioRender = nullptr;
            }
            if (m_audioResampler) {
                delete m_audioResampler;
                m_audioResampler = nullptr;
            }
            if (m_audioDecoder) {
                m_audioDecoder->stop();
                delete m_audioDecoder;
                m_audioDecoder = nullptr;
            }
            m_playerParam->m_audioIndex = -1;
            m_playerParam->m_audioStream = nullptr;
            break;
        }
        case AVMEDIA_TYPE_VIDEO: {
            if (m_videoDecoder) {
                m_videoDecoder->stop();
                delete m_videoDecoder;
                m_videoDecoder = nullptr;
            }
            m_playerParam->m_videoIndex = -1;
            m_playerParam->m_videoStream = nullptr;
            break;
        }
        case AVMEDIA_TYPE_SUBTITLE:
            break;
        default:
            break;
    }
}

void audioPCMCallback(void *opaque, uint8_t *stream, int len) {
    auto *mediaPlayer = (MediaPlayer *) opaque;
    mediaPlayer->pcmCallback(stream, len);
}

int MediaPlayer::openAudioRender(AVChannelLayout layout, int wanted_sample_rate) {
    int wanted_nb_channels = layout.nb_channels;
    uint64_t wanted_channel_layout = layout.u.mask;
    AudioRenderSpec wanted_spec, spec;
    const int next_nb_channels[] = {0, 0, 1, 6, 2, 6, 4, 6};
    const int next_sample_rates[] = {44100, 48000};
    int next_sample_rate_idx = FF_ARRAY_ELEMS(next_sample_rates) - 1;
    if (!wanted_channel_layout) {
        av_channel_layout_default(&layout, wanted_nb_channels);
        wanted_channel_layout = layout.u.mask;
    }
    wanted_spec.channels = wanted_nb_channels;
    wanted_spec.freq = wanted_sample_rate;
    if (wanted_spec.freq <= 0 || wanted_spec.channels <= 0) {
        av_log(nullptr, AV_LOG_ERROR, "Invalid sample rate or channel count!\n");
        return -1;
    }
    while (next_sample_rate_idx && next_sample_rates[next_sample_rate_idx] >= wanted_spec.freq) {
        next_sample_rate_idx--;
    }

    wanted_spec.format   = AV_SAMPLE_FMT_S16;
    wanted_spec.samples  = FFMAX(AUDIO_MIN_BUFFER_SIZE,2 << av_log2(wanted_spec.freq / AUDIO_MAX_CALLBACKS_PER_SEC));
    wanted_spec.callback = audioPCMCallback;
    wanted_spec.opaque   = this;

    // Audio Render
    m_audioRender = new OpenSLAudioRender();
    while (m_audioRender->open(&wanted_spec, &spec) < 0) {
        av_log(nullptr, AV_LOG_ERROR, "open audio render error: channel=%d, sampleRate=%d\n",
               wanted_spec.channels, wanted_spec.freq);
        wanted_spec.channels = next_nb_channels[FFMIN(7, wanted_spec.channels)];
        if (!wanted_spec.channels) {
            wanted_spec.freq = next_sample_rates[next_sample_rate_idx--];
            wanted_spec.channels = wanted_nb_channels;
            if (!wanted_spec.freq) {
                av_log(nullptr, AV_LOG_ERROR, "audio open failed\n");
                return -1;
            }
        }
    }

    if (spec.format != AV_SAMPLE_FMT_S16) {
        av_log(nullptr, AV_LOG_ERROR, "audio format %d is not supported!\n", spec.format);
        return -1;
    }

    if (spec.channels != wanted_spec.channels) {
        av_channel_layout_default(&layout, spec.channels);
        wanted_channel_layout = layout.u.mask;
        if (!wanted_channel_layout) {
            av_log(nullptr, AV_LOG_ERROR, "don't support channel:%d\n", spec.channels);
            return -1;
        }
    }

    m_audioResampler = new AudioResampler(m_playerParam, m_audioDecoder, m_avSync);
    m_audioResampler->setResampleParams(&spec, wanted_channel_layout);

    return 0;
}

void MediaPlayer::pcmCallback(uint8_t *stream, int len) {
    if (!m_audioResampler) {
        memset(stream, 0, len);
        return;
    }
    // 音频重采样
    m_audioResampler->pcmQueueCallback(stream, len);
}

static AVSyncType get_master_sync_type(int av_sync_type, AVStream *video_st, AVStream *audio_st) {
    if (av_sync_type == AV_SYNC_VIDEO) {
        if (video_st)
            return AV_SYNC_VIDEO;
        else
            return AV_SYNC_AUDIO;
    } else if (av_sync_type == AV_SYNC_AUDIO) {
        if (audio_st)
            return AV_SYNC_AUDIO;
        else
            return AV_SYNC_EXTERNAL;
    } else {
        return AV_SYNC_EXTERNAL;
    }
}

void MediaPlayer::run() {
    readPackets();
}

int MediaPlayer::readPackets() {
    int ret;
    AVFormatContext *ic;
    m_playerMutex.lock();
    do {
        ic = avformat_alloc_context();
        m_playerParam->m_formatCtx = ic;
        ic->interrupt_callback.callback = avformat_interrupt_cb;
        ic->interrupt_callback.opaque = m_playerParam;
        // 打开输入流
        ret = avformat_open_input(&ic, m_playerParam->url, nullptr, nullptr);
        if (ret < 0) {
            av_log(nullptr, AV_LOG_ERROR, "open input error:%s", strerror(ret));
            break;
        }
        // 查找码流信息
        avformat_find_stream_info(ic, nullptr);
        // 获取时长
        if (ic->duration > 0) {
            m_duration = av_rescale(ic->duration, 1000, AV_TIME_BASE);
        }
        // 获取音视频流
        int audioIndex = -1;
        int videoIndex = -1;
        for (int i = 0; i < ic->nb_streams; ++i) {
            if (AVMEDIA_TYPE_AUDIO == ic->streams[i]->codecpar->codec_type) {
                if (audioIndex == -1)
                    audioIndex = i;
            } else if (AVMEDIA_TYPE_VIDEO == ic->streams[i]->codecpar->codec_type) {
                videoIndex = i;
            }
        }
        if (audioIndex == -1 && videoIndex == -1) {
            av_log(nullptr, AV_LOG_ERROR, "not audio or video stream...");
            ret = -1;
            break;
        }
        // 打开音频解码器
        if (audioIndex >= 0) {
            openDecoder(audioIndex);
        }
        // 打开视频解码器
        if (videoIndex >= 0) {
            openDecoder(videoIndex);
        }
        if (!m_videoDecoder && !m_audioDecoder) {
            ret = -1;
            break;
        }
        ret = 0;
    } while (false);
    m_playerMutex.unlock();
    // 打开输入流或者打开解码器失败
    if (ret < 0) {
        m_exitPlay = true;
        m_playerCond.signal();
        const char* msg = "open input or open decoder error!";
        m_playerParam->m_messageQueue->sendMessage(MSG_ON_ERROR, 0, 0,
                                                   (void*)msg, (int)strlen(msg));
    }
    m_playerParam->m_messageQueue->sendMessage(MSG_ON_PREPARED);
    // 获取参考时钟
    m_playerParam->m_syncType = get_master_sync_type(m_playerParam->m_syncType,
                                                     m_playerParam->m_videoStream,
                                                     m_playerParam->m_audioStream);
    // 启动视频解码器
    if (m_videoDecoder) {
        m_videoDecoder->start();
        if (m_playerParam->m_syncType == AV_SYNC_VIDEO) {
            m_videoDecoder->setMasterClock(m_avSync->getVideoClock());
        } else if (m_playerParam->m_syncType == AV_SYNC_AUDIO) {
            m_videoDecoder->setMasterClock(m_avSync->getAudioClock());
        } else {
            m_videoDecoder->setMasterClock(m_avSync->getExternalClock());
        }
    }
    // 开始avsync
    m_avSync->start(m_videoDecoder, m_audioDecoder);
    m_playerParam->m_messageQueue->sendMessage(MSG_ON_START);

    ret = 0;
    m_eof = 0;
    int64_t  pktTime;
    bool playInRange;
    bool waitSeek = false;
    int64_t streamStartTime;
    AVPacket pkt1, *pkt = &pkt1;
    // 开始解封装(复用)
    for (;;) {
        if (m_playerParam->m_abortReq)
            break;
        // 暂停处理
        if (m_playerParam->m_pauseReq != m_lastPause) {
            m_lastPause = m_playerParam->m_pauseReq;
            if (m_playerParam->m_pauseReq) {
                av_read_pause(ic);
            } else {
                av_read_play(ic);
            }
        }
        // seek处理
        if (m_playerParam->m_seekRequest) {
            int64_t seek_target = m_playerParam->m_seekPos;
            int64_t seek_min = INT64_MIN;
            int64_t seek_max = INT64_MAX;
            m_playerParam->m_playMutex.lock();
            ret = avformat_seek_file(ic, -1, seek_min, seek_target, seek_max, m_playerParam->m_seekFlag);
            m_playerParam->m_playMutex.unlock();
            if (ret < 0) {
                av_log(nullptr, AV_LOG_ERROR, "seek file error:%s", strerror(ret));
            } else {
                // 清空解码缓冲区
                if (m_videoDecoder)
                    m_videoDecoder->flush();
                if (m_audioDecoder)
                    m_audioDecoder->flush();
                // 更新时钟
                if (m_playerParam->m_seekFlag & AVSEEK_FLAG_BYTE) {
                    m_avSync->updateExternalClock(NAN);
                } else {
                    m_avSync->updateExternalClock((double)seek_target / AV_TIME_BASE);
                }
                m_avSync->refreshVideoTimer();
            }
            m_playerParam->m_seekRequest = 0;
            m_playerCond.signal();
            m_eof = 0;
            if (m_playerParam->m_messageQueue) {
                int seekTime = (int) av_rescale(seek_target, 1000, AV_TIME_BASE);
                m_playerParam->m_messageQueue->sendMessage(MSG_SEEK_COMPLETE, seekTime, ret);
            }
        }
        // 判断packet队列是否缓冲满
        if ((!m_audioDecoder || m_audioDecoder->hasEnoughPackets(m_playerParam->m_audioStream))
            && (!m_videoDecoder || m_videoDecoder->hasEnoughPackets(m_playerParam->m_videoStream))) {
            continue;
        }
        if (!waitSeek) {
            // 读取媒体数据包
            ret = av_read_frame(ic, pkt);
        } else {
            ret = -1;
        }
        if (ret < 0) {
            if ((ret == AVERROR_EOF || avio_feof(ic->pb)) && !m_eof) {
                m_eof = 1;
            }
            if (ic->pb && ic->pb->error) {
                ret = -1;
                break;
            }
            // 判定为结束播放
            if (!m_playerParam->m_pauseReq && (!m_audioDecoder || m_audioDecoder->getPacketSize() == 0)
                && (!m_videoDecoder || m_videoDecoder->getPacketSize() == 0)) {
                ret = AVERROR_EOF;
                break;
            }
            av_usleep(10 * 1000);
            continue;
        } else {
            m_eof = 0;
        }

        streamStartTime = ic->streams[pkt->stream_index]->start_time;
        int64_t startTime = streamStartTime != AV_NOPTS_VALUE ? streamStartTime : 0;
        pktTime = pkt->pts == AV_NOPTS_VALUE ? pkt->dts : pkt->pts;
        // 是否处于可播放范围
        playInRange = m_playerParam->m_duration == AV_NOPTS_VALUE || (double)(pktTime - startTime) * av_q2d(ic->streams[pkt->stream_index]->time_base)
                        - (double)(m_playerParam->m_startTime != AV_NOPTS_VALUE ? m_playerParam->m_startTime : 0)/1000000
                        <= ((double)m_playerParam->m_duration / 1000000);
        // packet入队列
        if (playInRange && m_audioDecoder && pkt->stream_index == m_playerParam->m_audioIndex) {
            m_audioDecoder->pushPacket(pkt);
        } else if (playInRange && m_videoDecoder && pkt->stream_index == m_playerParam->m_videoIndex) {
            m_videoDecoder->pushPacket(pkt);
        } else {
            av_packet_unref(pkt);
        }
        if (!playInRange) {
            waitSeek = true;
        }
    }

    // 关闭解码器
    if (m_playerParam->m_audioIndex >= 0) {
        closeDecoder(m_playerParam->m_audioIndex);
    }
    if (m_playerParam->m_videoIndex >= 0) {
        closeDecoder(m_playerParam->m_videoIndex);
    }
    m_exitPlay = true;
    m_playerCond.signal();
    // 发送播放完成信息
    if (ret >= 0) {
        m_playerParam->m_messageQueue->sendMessage(MSG_ON_COMPLETE);
    } else {
        const char *msg = "read frame error...";
        m_playerParam->m_messageQueue->sendMessage(MSG_ON_ERROR, 0, 0,
                                                   (void*)msg, (int) strlen(msg));
    }
    // 停止消息队列
    m_playerParam->m_messageQueue->stop();

    return ret;
}
