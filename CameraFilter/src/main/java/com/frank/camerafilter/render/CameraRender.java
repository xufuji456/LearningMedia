package com.frank.camerafilter.render;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import com.frank.camerafilter.camera.CameraManager;
import com.frank.camerafilter.filter.BaseFilter;
import com.frank.camerafilter.filter.BeautyCameraFilter;
import com.frank.camerafilter.util.OpenGLUtil;
import com.frank.camerafilter.util.Rotation;
import com.frank.camerafilter.util.TextureRotateUtil;
import com.frank.camerafilter.view.BeautyCameraView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author xufulong
 * @date 2022/10/18 8:46 上午
 * @desc
 */
public class CameraRender implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    protected BaseFilter mFilter;

    private SurfaceTexture surfaceTexture;
    private BeautyCameraFilter cameraFilter;

    private CameraManager cameraManager;

    private int mTextureId = OpenGLUtil.NO_TEXTURE;

    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mTextureBuffer;

    private int mImageWidth, mImageHeight;
    private int mSurfaceWidth, mSurfaceHeight;

    private final float[] mMatrix = new float[16];

    private final BeautyCameraView mCameraView;

    public CameraRender(BeautyCameraView cameraView) {
        mCameraView = cameraView;

        cameraManager = new CameraManager();
        mVertexBuffer = ByteBuffer.allocateDirect(TextureRotateUtil.VERTEX.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVertexBuffer.put(TextureRotateUtil.VERTEX).position(0);
        mTextureBuffer = ByteBuffer.allocateDirect(TextureRotateUtil.TEXTURE_ROTATE_0.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTextureBuffer.put(TextureRotateUtil.TEXTURE_ROTATE_0).position(0);
    }

    // 打开摄像头，实现预览
    private void openCamera() {
        cameraManager.openCamera();
        Camera.Size size = cameraManager.getPreviewSize();
        // 90或270，需要宽高交换
        if (cameraManager.getOrientation() == 90 || cameraManager.getOrientation() == 270) {
            mImageWidth = size.height;
            mImageHeight = size.width;
        } else {
            mImageWidth = size.width;
            mImageHeight = size.height;
        }
        cameraFilter.onInputSizeChanged(mImageWidth, mImageHeight);
        adjustSize(cameraManager.getOrientation(), cameraManager.isFront(), true);
    }

    // 根据旋转角度，调整纹理坐标
    private void adjustSize(int rotation, boolean hFlip, boolean vFlip) {
        float[] vertexData = TextureRotateUtil.VERTEX;
        float[] textureData = TextureRotateUtil.getRotateTexture(Rotation.fromInt(rotation), hFlip, vFlip);
        mVertexBuffer.clear();
        mVertexBuffer.put(vertexData).position(0);
        mTextureBuffer.clear();
        mTextureBuffer.put(textureData).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        cameraFilter = new BeautyCameraFilter(mCameraView.getContext());
        cameraFilter.init();
        mTextureId = OpenGLUtil.getExternalOESTextureId();
        // 创建SurfaceTexture
        surfaceTexture = new SurfaceTexture(mTextureId);
        surfaceTexture.setOnFrameAvailableListener(this);

        openCamera();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        mSurfaceWidth  = width;
        mSurfaceHeight = height;
        // 开始预览
        cameraManager.startPreview(surfaceTexture);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // 更新纹理
        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(mMatrix);
        cameraFilter.setTransformMatrix(mMatrix);
        int id = mTextureId;
        // 原生摄像头
        if (mFilter == null) {
            cameraFilter.onDrawFrame(mTextureId, mVertexBuffer, mTextureBuffer);
        } else {
            // 有滤镜
            id = cameraFilter.onDrawToTexture(mTextureId);
            mFilter.onDrawFrame(id, mVertexBuffer, mTextureBuffer);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // 请求更新
        mCameraView.requestRender();
    }

    public void releaseCamera() {
        if (cameraManager != null) {
            cameraManager.releaseCamera();
            cameraManager = null;
        }
    }

}
