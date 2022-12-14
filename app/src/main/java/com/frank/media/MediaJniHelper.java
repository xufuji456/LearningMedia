package com.frank.media;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * @author xufulong
 * @date 2022/8/14 6:31 下午
 * @desc
 */
public class MediaJniHelper {

    static {
        System.loadLibrary("like_media");
    }

    private long audioContext;

    public native String stringFromJNI();

    public native void setIntData(int[] data);

    public native void testReflect();

    public native void audioResample(String inputPath, String outputPath, int sampleRate);

    void onJniCallback(String msg) {
        if (msg != null) {
            Log.i("MediaJniHelper", msg);
        }
    }

    private AudioTrack audioTrack;

    private native long native_init();

    private native void play_audio(long context, String path);

    private native void filter_again(long context, String filterDesc);

    private native void native_release(long context);

    public void init() {
        audioContext = native_init();
    }

    public void playAudio(String path) {
        play_audio(audioContext, path);
    }

    public void filterAgain(String filterDesc) {
        filter_again(audioContext, filterDesc);
    }

    public void release() {
        if (audioContext == 0)
            return;

        native_release(audioContext);
        audioContext = 0;
    }

    // 用于native反射创建
    public AudioTrack createAudioTrack(int sampleRate, int channel) {
        int channelConfig = channel == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                audioFormat, bufferSize, AudioTrack.MODE_STREAM);
        return audioTrack;
    }

    public void releaseAudioTrack() {
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
    }

    private native int push(String inputPath, String outputPath);

    public int pushStream(String inputPath, String outputPath) {
        return push(inputPath, outputPath);
    }

}
