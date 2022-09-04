package com.frank.media;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author xufulong
 * @date 2022/9/4 11:35 上午
 * @desc
 */
public class SimpleAudioRecord {

    private AudioRecord audioRecord;
    private int bufferSize;
    private RecordThread recordThread;

    private void initRecord(Context context) {
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
    }

    public void startRecord(String path, Context context) {
        if (audioRecord == null) {
            initRecord(context);
        }
        if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            return;
        }
        // 开始录音
        audioRecord.startRecording();
        // 启动录音线程
        recordThread = new RecordThread(path, bufferSize, audioRecord);
        recordThread.setRecording(true);
        recordThread.start();
    }

    public void stopRecord() {
        // 退出录音线程
        if (recordThread != null && !recordThread.isInterrupted()) {
            recordThread.setRecording(false);
            recordThread.interrupt();
            recordThread = null;
        }
        // 停止录音
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private static class RecordThread extends Thread {

        private AudioRecord mAudioRecord;
        private byte[] buffer;
        private boolean isRecording;
        private FileOutputStream outputStream;

        public RecordThread(String path, int size, AudioRecord audioRecord) {
            buffer = new byte[size];
            mAudioRecord = audioRecord;
            try {
                outputStream = new FileOutputStream(path);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public void setRecording(boolean val) {
            isRecording = val;
        }

        @Override
        public void run() {
            super.run();

            while (isRecording) {
                // 读取录音数据
                int size = mAudioRecord.read(buffer, 0, buffer.length);
                if (size > 0) {
                    try {
                        outputStream.write(buffer, 0, size);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
