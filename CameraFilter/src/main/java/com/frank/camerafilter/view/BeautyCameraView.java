package com.frank.camerafilter.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import com.frank.camerafilter.render.CameraRender;

/**
 * @author xufulong
 * @date 2022/10/18 8:49 上午
 * @desc
 */
public class BeautyCameraView extends GLSurfaceView {

    private CameraRender cameraRender;

    public BeautyCameraView(Context context) {
        super(context);
    }

    public BeautyCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);

        cameraRender = new CameraRender(this);
        setEGLContextClientVersion(3);
        setRenderer(cameraRender);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        if (cameraRender != null) {
            cameraRender.releaseCamera();
        }
    }
}
