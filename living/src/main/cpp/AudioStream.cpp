//
// Created by xu fulong on 2022/9/22.
//

#include <cstring>
#include "AudioStream.h"

AudioStream::AudioStream() {

}

int AudioStream::setAudioEncInfo(int sampleRate, int channels) {
    m_channels = channels;
    // 打开aac编码器
    m_audioEncoder = faacEncOpen(sampleRate, channels, &m_inputSample, &m_outputBytes);
    if (!m_audioEncoder)
        return -1;
    m_buffer = new u_char[m_outputBytes];
    // 设置编码器参数
    faacEncConfigurationPtr configPtr = faacEncGetCurrentConfiguration(m_audioEncoder);
    configPtr->inputFormat   = FAAC_INPUT_16BIT;
    configPtr->outputFormat  = 0;
    configPtr->mpegVersion   = MPEG4;
    configPtr->aacObjectType = LOW;
    return faacEncSetConfiguration(m_audioEncoder, configPtr);
}

void AudioStream::setAudioCallback(AudioCallback callback) {
    audioCallback = callback;
}

int AudioStream::getInputSamples() const {
    return m_inputSample;
}

RTMPPacket *AudioStream::getAudioTag() {
    u_char *buffer;
    u_long len;
    faacEncGetDecoderSpecificInfo(m_audioEncoder, &buffer, &len);
    int bodySize = 2 + len;
    auto *packet = new RTMPPacket();
    RTMPPacket_Alloc(packet, bodySize);
    if (m_channels == 1) { // 单声道
        packet->m_body[0] = 0xAE;
    } else { // 双声道
        packet->m_body[0] = 0xAF;
    }
    packet->m_body[1] = 0x00;
    memcpy(&packet->m_body[2], buffer, len);

    packet->m_nChannel        = 0x11;
    packet->m_nBodySize       = bodySize;
    packet->m_headerType      = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_packetType      = RTMP_PACKET_TYPE_AUDIO;
    packet->m_hasAbsTimestamp = 0;

    return packet;
}

void AudioStream::encodeAudio(int8_t *data) {
    int len = faacEncEncode(m_audioEncoder, reinterpret_cast<int32_t *>(data),
                            m_inputSample, m_buffer, m_outputBytes);
    if (len <= 0)
        return;

    int bodySize = 2 + len;
    auto *packet = new RTMPPacket();
    RTMPPacket_Alloc(packet, bodySize);
    if (m_channels == 1) { // 单声道
        packet->m_body[0] = 0xAE;
    } else { // 双声道
        packet->m_body[0] = 0xAF;
    }
    packet->m_body[1] = 0x01;
    memcpy(&packet->m_body[2], m_buffer, len);

    packet->m_nChannel        = 0x11;
    packet->m_nBodySize       = bodySize;
    packet->m_headerType      = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_packetType      = RTMP_PACKET_TYPE_AUDIO;
    packet->m_hasAbsTimestamp = 0;

    audioCallback(packet);
}

AudioStream::~AudioStream() {
    delete m_buffer;
    if (m_audioEncoder) {
        faacEncClose(m_audioEncoder);
        m_audioEncoder = nullptr;
    }
}

