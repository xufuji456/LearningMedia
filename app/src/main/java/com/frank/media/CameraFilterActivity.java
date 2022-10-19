package com.frank.media;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.frank.camerafilter.view.BeautyCameraView;

public class CameraFilterActivity extends AppCompatActivity implements View.OnClickListener {

    private BeautyCameraView cameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_filter);

        initView();
    }

    private void initView() {
        cameraView = findViewById(R.id.surface_camera_filter);
        ImageView btnVideoRecorder = findViewById(R.id.btn_video_recorder);
        btnVideoRecorder.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_video_recorder) {
            boolean isRecording = cameraView.isRecording();
            cameraView.setRecording(!isRecording);
            if (!isRecording) {
                Toast.makeText(this, "start recording...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "stop recording...", Toast.LENGTH_SHORT).show();
            }
        }
    }

}