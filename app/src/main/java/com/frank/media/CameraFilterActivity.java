package com.frank.media;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.frank.camerafilter.factory.BeautyFilterType;
import com.frank.camerafilter.view.BeautyCameraView;

public class CameraFilterActivity extends AppCompatActivity implements View.OnClickListener {

    private BeautyCameraView cameraView;

    private int index;

    private final BeautyFilterType[] filterType = {
            BeautyFilterType.NONE,
            BeautyFilterType.BLUR,
            BeautyFilterType.HUE
    };

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
        ImageView btnCameraFilter = findViewById(R.id.btn_camera_filter);
        btnCameraFilter.setOnClickListener(this);
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
        } else if (v.getId() == R.id.btn_camera_filter) {
            index++;
            if (index >= filterType.length)
                index = 0;
            cameraView.setFilter(filterType[index]);
            String name = filterTypeToName(filterType[index]);
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
        }
    }

    private String filterTypeToName(BeautyFilterType type) {
        switch (type) {
            case BLUR: return "模糊";
            case HUE: return "暖色";
            case NONE:
            default:
                return "正常";
        }
    }

}