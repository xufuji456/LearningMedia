
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

}

void MediaPlayer::setVideoRender(VideoRender *render) {

}

int MediaPlayer::prepare() {

    return 0;
}

int MediaPlayer::prepareAsync() {

    return 0;
}

void MediaPlayer::start() {

}

void MediaPlayer::pause() {

}

void MediaPlayer::resume() {

}

void MediaPlayer::seekTo(long timeMs) {

}

void MediaPlayer::setVolume(float volume) {

}

void MediaPlayer::setMute(int mute) {

}

void MediaPlayer::setRate(float rate) {

}

int MediaPlayer::getRotate() {

    return 0;
}

int MediaPlayer::getVideoWidth() {

    return 0;
}

int MediaPlayer::getVideoHeight() {

    return 0;
}

long MediaPlayer::getCurrentPosition() {

}

long MediaPlayer::getDuration() {

}

int MediaPlayer::isPlaying() {

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

void startAudioDecoder(PlayerParam *playerParam, AudioDecoder *audioDecoder) {

}

void MediaPlayer::startAudioRender(PlayerParam *playerParam) {

}

int MediaPlayer::openDecoder(int streamIndex) {

}

void MediaPlayer::closeDecoder(int streamIndex) {

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

}

void MediaPlayer::run() {
    readPackets();
}

int MediaPlayer::readPackets() {

}
