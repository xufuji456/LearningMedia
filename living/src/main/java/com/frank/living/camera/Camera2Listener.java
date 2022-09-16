package com.frank.living.camera;


import android.util.Size;

public interface Camera2Listener {

    void onCameraOpened(Size previewSize, int displayOrientation);

    void onPreviewFrame(byte[] yuvData);

    void onCameraClosed();

    void onCameraError(Exception e);

}
