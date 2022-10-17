package com.frank.media;

import static com.frank.media.handler.FFmpegHandler.MSG_BEGIN;
import static com.frank.media.handler.FFmpegHandler.MSG_END;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.frank.media.handler.FFmpegHandler;
import com.frank.media.util.FFmpegUtil;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

    private SimpleAudioRecord simpleAudioRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, 54321);
        }

        Button btnRecord = findViewById(R.id.btn_record);
        btnRecord.setOnClickListener(this);

        Button btnPlay = findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(this);

        Button btnResample = findViewById(R.id.btn_resample);
        btnResample.setOnClickListener(this);

        Button btnEqualizer = findViewById(R.id.btn_equalizer);
        btnEqualizer.setOnClickListener(this);

        Button btnFFmpegPush = findViewById(R.id.btn_ffmpeg_push);
        btnFFmpegPush.setOnClickListener(this);

        Button btnVideoEdit = findViewById(R.id.btn_video_edit);
        btnVideoEdit.setOnClickListener(this);

        Button btnCamera = findViewById(R.id.btn_camera);
        btnCamera.setOnClickListener(this);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_record:
                if (simpleAudioRecord == null) {
                    simpleAudioRecord = new SimpleAudioRecord();
                    // 注意Android10以上，分区存储需要另外的处理
                    String path = Environment.getExternalStorageDirectory().getPath() + "/hello.pcm";
                    simpleAudioRecord.startRecord(path, MainActivity.this);
                    Log.e("Main", "start record...");
                } else {
                    simpleAudioRecord.stopRecord();
                    Log.e("Main", "stop record...");
                }
                break;
            case R.id.btn_play:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MediaJniHelper mediaJniHelper = new MediaJniHelper();
                        String path = Environment.getExternalStorageDirectory().getPath() + "/tiger.mp3";
                        mediaJniHelper.init();
                        mediaJniHelper.playAudio(path);
                    }
                }).start();
                break;
            case R.id.btn_resample:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MediaJniHelper mediaJniHelper = new MediaJniHelper();
                        String path = Environment.getExternalStorageDirectory().getPath() + "/hello.m4a";
                        String output = Environment.getExternalStorageDirectory().getPath() + "/16000.m4a";
                        mediaJniHelper.audioResample(path, output, 16000);
                    }
                }).start();
                break;
            case R.id.btn_equalizer:
                Intent equalizerIntent = new Intent(MainActivity.this, EqualizerActivity.class);
                startActivity(equalizerIntent);
                break;
            case R.id.btn_ffmpeg_push:
                String inputPath = "sdcard/beyond.mp4";
                String outputPath = "rtmp://192.168.31.129/live/stream";
                MediaJniHelper mediaJniHelper = new MediaJniHelper();
                mediaJniHelper.pushStream(inputPath, outputPath);
                break;
            case R.id.btn_video_edit:
                String one = "sdcard/one.mp4";
                String two = "sdcard/two.mp4";
                String output = "sdcard/xfade_left.mp4";
                String transition = "slideleft";
                FFmpegHandler ffmpegHandler = new FFmpegHandler(mHandler);
                String[] cmdLine = FFmpegUtil.xfadeTransition(transition, one, 640, 360, 4, two, output);
                ffmpegHandler.execFFmpegCommand(cmdLine);
                break;
            case R.id.btn_camera:
                Intent cameraIntent = new Intent(MainActivity.this, CameraFilterActivity.class);
                startActivity(cameraIntent);
            default:
                break;
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_BEGIN) {
                Log.i("MainActivity", "FFmpeg begin...");
            } else if (msg.what == MSG_END) {
                int result = (int) msg.obj;
                Log.i("MainActivity", "FFmpeg end result=" + result);
            }
        }
    };

}