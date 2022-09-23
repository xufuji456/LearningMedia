//
// Created by xu fulong on 2022/9/22.
//

#ifndef LEARNINGMEDIA_AUDIOSTREAM_H
#define LEARNINGMEDIA_AUDIOSTREAM_H

#include "rtmp/rtmp.h"
#include "faac/faac.h"
#include "sys/types.h"

class AudioStream {

    typedef void (*AudioCallback)(RTMPPacket *pkt);

private:
    int m_channels;
    u_long m_inputSample;
    u_long m_outputBytes;
    u_char *m_buffer;

    faacEncHandle m_audioEncoder;
    AudioCallback audioCallback;

public:
    AudioStream();

    ~AudioStream();

    int setAudioEncInfo(int sampleRate, int channels);

    int getInputSamples() const;

    RTMPPacket *getAudioTag();

    void encodeAudio(int8_t *data);

    void setAudioCallback(AudioCallback callback);

};


#endif //LEARNINGMEDIA_AUDIOSTREAM_H
