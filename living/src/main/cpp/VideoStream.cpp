//
// Created by xu fulong on 2022/9/22.
//

#include "VideoStream.h"

VideoStream::VideoStream():m_frameLen(0),
                           m_pic_in(nullptr),
                           m_videoEncoder(nullptr),
                           videoCallback(nullptr) {

}

void VideoStream::setVideoCallback(VideoCallback callback) {
    videoCallback = callback;
}

int VideoStream::setVideoEncInfo(int width, int height, int frameRate, int bitrate) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_frameLen = width * height;
    if (m_videoEncoder) {
        x264_encoder_close(m_videoEncoder);
        m_videoEncoder = nullptr;
    }
    if (m_pic_in) {
        x264_picture_clean(m_pic_in);
        delete m_pic_in;
        m_pic_in = nullptr;
    }
    // 设置x264默认参数
    x264_param_t param;
    int ret = x264_param_default_preset(&param, "ultrafast", "zerolatency");
    if (ret < 0) {
        return ret;
    }
    param.i_width  = width;
    param.i_height = height;
    param.i_csp    = X264_CSP_I420;
    param.i_bframe = 0;

    param.rc.i_rc_method       = X264_RC_CRF;
    param.rc.i_bitrate         = bitrate / 1024;
    param.rc.i_vbv_max_bitrate = bitrate * 1.2 / 1024;
    param.rc.i_vbv_buffer_size = bitrate / 1024;

    param.i_fps_num      = frameRate;
    param.i_fps_den      = 1;
    param.i_timebase_num = param.i_fps_den;
    param.i_timebase_den = param.i_fps_num;

    param.i_threads        = 1;
    param.b_vfr_input      = 0;
    param.i_keyint_max     = frameRate * 2;
    param.b_repeat_headers = 1; // 每个关键帧是否带sps和pps

    ret = x264_param_apply_profile(&param, "baseline");
    if (ret < 0) {
        return ret;
    }
    m_videoEncoder = x264_encoder_open(&param);
    m_pic_in = new x264_picture_t();
    ret = x264_picture_alloc(m_pic_in, X264_CSP_I420, width, height);
    return ret;
}

void VideoStream::sendSpsPps(uint8_t *sps, uint8_t *pps, int spsLen, int ppsLen) {
    int bodySize = 16 + spsLen + ppsLen;
    RTMPPacket *packet = new RTMPPacket();
    RTMPPacket_Alloc(packet, bodySize);
    int i = 0;
    packet->m_body[i++] = 0x17;
    packet->m_body[i++] = 0x00;
    // timestamp
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    // version
    packet->m_body[i++] = 0x01;
    // profile
    packet->m_body[i++] = sps[1];
    packet->m_body[i++] = sps[2];
    packet->m_body[i++] = sps[3];
    packet->m_body[i++] = 0xFF;
    // sps
    packet->m_body[i++] = 0xE1;
    packet->m_body[i++] = (spsLen >> 8) & 0xFF;
    packet->m_body[i++] = spsLen & 0xFF;
    memcpy(&packet->m_body[i], sps, spsLen);
    i += spsLen;
    // pps
    packet->m_body[i++] = 0x01;
    packet->m_body[i++] = (ppsLen >> 8) & 0xFF;
    packet->m_body[i++] = ppsLen & 0xFF;
    memcpy(&packet->m_body[i], pps, ppsLen);

    packet->m_nChannel        = 0x10;
    packet->m_nBodySize       = bodySize;
    packet->m_headerType      = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_packetType      = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nTimeStamp      = 0;
    packet->m_hasAbsTimestamp = 0;

    videoCallback(packet);
}

void VideoStream::sendFrame(int type, uint8_t *payload, int payloadLen) {
    // 减去start code 3或4个字节
    if (payload[2] == 0x00) {
        payloadLen -= 4;
        payload += 4;
    } else {
        payloadLen -= 3;
        payload += 3;
    }
    int i = 0;
    int bodySize = 9 + payloadLen;
    RTMPPacket *packet = new RTMPPacket();
    RTMPPacket_Alloc(packet, bodySize);

    if (type == NAL_SLICE_IDR) {
        packet->m_body[i++] = 0x17; // 1:key frame 7:AVC
    } else {
        packet->m_body[i++] = 0x27; // 2:none-key frame 7:AVC
    }
    packet->m_body[i++] = 0x01;
    // timestamp
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    // packet len
    packet->m_body[i++] = (payloadLen >> 24) & 0xFF;
    packet->m_body[i++] = (payloadLen >> 16) & 0xFF;
    packet->m_body[i++] = (payloadLen >> 8) & 0xFF;
    packet->m_body[i++] = (payloadLen) & 0xFF;

    memcpy(&packet->m_body[i], payload, payloadLen);

    packet->m_nChannel        = 0x10;
    packet->m_nBodySize       = bodySize;
    packet->m_headerType      = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_packetType      = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nTimeStamp      = 0;

    videoCallback(packet);
}

void VideoStream::encodeVideo(int8_t *data) {
    std::lock_guard<std::mutex> lock(m_mutex);
    assert(m_pic_in);
    int offset = 0;
    memcpy(m_pic_in->img.plane[0], data, m_frameLen);
    offset += m_frameLen;
    memcpy(m_pic_in->img.plane[1], data + offset, m_frameLen / 4);
    offset += m_frameLen / 4;
    memcpy(m_pic_in->img.plane[2], data + offset, m_frameLen / 4);

    int pi_nal;
    x264_nal_t *pp_nal;
    x264_picture_t pic_out;
    x264_encoder_encode(m_videoEncoder, &pp_nal, &pi_nal, m_pic_in, &pic_out);
    int spsLen = 0;
    int ppsLen = 0;
    uint8_t sps[100];
    uint8_t pps[100];

    for (int i = 0; i < pi_nal; ++i) {
        x264_nal_t nal = pp_nal[i];
        if (nal.i_type == NAL_SPS) {
            spsLen = nal.i_payload - 4;
            memcpy(sps, nal.p_payload + 4, spsLen);
        } else if (nal.i_type == NAL_PPS) {
            ppsLen = nal.i_payload - 4;
            memcpy(pps, nal.p_payload + 4, ppsLen);
            // 发送sps和pps数据
            sendSpsPps(sps, pps, spsLen, ppsLen);
        } else {
            // 发送视频数据包
            sendFrame(nal.i_type, nal.p_payload, nal.i_payload);
        }
    }
}

VideoStream::~VideoStream() {
    if (m_videoEncoder) {
        x264_encoder_close(m_videoEncoder);
        m_videoEncoder = nullptr;
    }
    if (m_pic_in) {
        x264_picture_clean(m_pic_in);
        delete m_pic_in;
        m_pic_in = nullptr;
    }
}