package com.frank.media.live.param;


public class AudioParam {
    private int channelConfig;
    private int sampleRate;
    private int audioFormat;
    private int channels;

    public AudioParam(int sampleRate, int channelConfig, int audioFormat, int channels) {
        this.channels      = channels;
        this.sampleRate    = sampleRate;
        this.audioFormat   = audioFormat;
        this.channelConfig = channelConfig;
    }

    public int getChannelConfig() {
        return channelConfig;
    }

    public void setChannelConfig(int channelConfig) {
        this.channelConfig = channelConfig;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getAudioFormat() {
        return audioFormat;
    }

    public void setAudioFormat(int audioFormat) {
        this.audioFormat = audioFormat;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }
}
