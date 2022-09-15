package com.frank.media.live.listener;

public interface OnFrameDataCallback {

    int getInputSamples();

    void onAudioFrame(byte[] pcm);

    void onAudioCodecInfo(int sampleRate, int channelCount);

    void onVideoFrame(byte[] yuv, int cameraType);

    void onVideoCodecInfo(int width, int height, int frameRate, int bitrate);
}
