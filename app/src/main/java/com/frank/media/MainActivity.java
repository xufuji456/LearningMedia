package com.frank.media;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private final String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO};

    private SimpleAudioRecord simpleAudioRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, 54321);
        }

        Button btnRecord = findViewById(R.id.btn_record);
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

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
            }
        });

        Button btnPlay = findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MediaJniHelper mediaJniHelper = new MediaJniHelper();
                        String path = Environment.getExternalStorageDirectory().getPath() + "/tiger.mp3";
                        mediaJniHelper.playAudio(path);
                    }
                }).start();
            }
        });

        Button btnResample = findViewById(R.id.btn_resample);
        btnResample.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MediaJniHelper mediaJniHelper = new MediaJniHelper();
                        String path = Environment.getExternalStorageDirectory().getPath() + "/hello.m4a";
                        String output = Environment.getExternalStorageDirectory().getPath() + "/16000.m4a";
                        mediaJniHelper.audioResample(path, output, 16000);
                    }
                }).start();
            }
        });
    }

}