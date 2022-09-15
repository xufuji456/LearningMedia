package com.frank.media.live.stream;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;

import com.frank.media.live.listener.OnFrameDataCallback;
import com.frank.media.live.param.AudioParam;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioChannelStream {

    private boolean isMute;
    private boolean isLiving;
    private final int inputSamples;
    private final ExecutorService executor;
    private AudioRecord audioRecord = null;
    private final OnFrameDataCallback mCallback;

    public AudioChannelStream(Context context, OnFrameDataCallback callback, AudioParam audioParam) {
        mCallback = callback;
        executor = Executors.newSingleThreadExecutor();
        int channelConfig;
        if (audioParam.getChannels() == 2) {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        } else {
            channelConfig = AudioFormat.CHANNEL_IN_MONO;
        }

        mCallback.onAudioCodecInfo(audioParam.getSampleRate(), audioParam.getChannels());
        inputSamples = mCallback.getInputSamples() * 2;

        int minBufferSize = AudioRecord.getMinBufferSize(audioParam.getSampleRate(),
                channelConfig, audioParam.getAudioFormat()) * 2;
        int bufferSizeInBytes = Math.max(minBufferSize, inputSamples);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, audioParam.getSampleRate(),
                channelConfig, audioParam.getAudioFormat(), bufferSizeInBytes);
    }


    public void startLive() {
        isLiving = true;
        executor.submit(new AudioTask());
    }

    public void stopLive() {
        isLiving = false;
    }


    public void release() {
        audioRecord.release();
    }


    class AudioTask implements Runnable {

        @Override
        public void run() {
            audioRecord.startRecording();
            byte[] bytes = new byte[inputSamples];
            while (isLiving) {
                if (!isMute) {
                    int len = audioRecord.read(bytes, 0, bytes.length);
                    if (len > 0) {
                        mCallback.onAudioFrame(bytes);
                    }
                }
            }
            audioRecord.stop();
        }
    }

    public void setMute(boolean isMute) {
        this.isMute = isMute;
    }

}
