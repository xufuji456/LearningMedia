package com.frank.camerafilter.recorder;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.frank.camerafilter.filter.BeautyCameraFilter;
import com.frank.camerafilter.recorder.gles.EglCore;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;

/**
 * @author xufulong
 * @date 2022/10/18 11:12 下午
 * @desc
 */
public class CameraVideoRecorder implements Runnable {

    private final static int MSG_START_RECORD    = 0;
    private final static int MSG_STOP_RECORD     = 1;
    private final static int MSG_FRAME_AVAILABLE = 2;
    private final static int MSG_SET_TEXTURE_ID  = 3;
    private final static int MSG_UPDATE_CONTEXT  = 4;
    private final static int MSG_EXIT_RECORD     = 5;

    private int mTextureId;
    private EglCore mEglCore;
    private BeautyCameraFilter cameraFilter;
    private WindowEglSurface mWindowSurface;
    private VideoRecorderCore mVideoRecorder;

    private boolean mReady;
    private boolean mRunning;
    private Context mContext;
    private float[] mTransformMatrix; // 变换矩阵
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTextureBuffer;

    private final Object mReadyLock = new Object();

    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mVideoWidth;
    private int mVideoHeight;

    private RecorderHandler mHandler;

    public CameraVideoRecorder(Context context) {
        mContext = context;
    }

    public static class RecorderConfig {
        int mWidth;
        int mHeight;
        int mBitrate;
        File mOutputFile;
        EGLContext mEGLContext;

        public RecorderConfig(int width, int height, int bitrate, File outputFile, EGLContext eglContext) {
            this.mWidth = width;
            this.mHeight = height;
            this.mBitrate = bitrate;
            this.mOutputFile = outputFile;
            this.mEGLContext = eglContext;
        }
    }

    // 用于处理UI线程与GL线程的消息交互
    private static class RecorderHandler extends Handler {

        private final WeakReference<CameraVideoRecorder> mWeakRecorder;

        public RecorderHandler(CameraVideoRecorder recorder) {
            mWeakRecorder = new WeakReference<>(recorder);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            CameraVideoRecorder recorder = mWeakRecorder.get();
            if (recorder == null)
                return;

            switch (msg.what) {
                case MSG_START_RECORD:
                    recorder.handleStartRecord((RecorderConfig) msg.obj);
                    break;
                case MSG_STOP_RECORD:
                    recorder.handleStopRecord();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long)msg.arg1) << 32) | (((long)msg.arg2) & 0xffffffffL);
                    recorder.handleFrameAvailable((float[]) msg.obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    recorder.handleSetTextureId(msg.arg1);
                    break;
                case MSG_UPDATE_CONTEXT:
                    recorder.handleUpdateSharedContext((EGLContext) msg.obj);
                    break;
                case MSG_EXIT_RECORD:
                    Looper.myLooper().quit();
                    break;
                default:
                    break;
            }
        }
    }

    private void handleStartRecord(RecorderConfig config) {
        prepareRecorder(
                config.mWidth,
                config.mHeight,
                config.mBitrate,
                config.mOutputFile,
                config.mEGLContext);
    }

    private void handleStopRecord() {
        mVideoRecorder.drainEncoder(true);
        releaseRecorder();
    }

    private void handleFrameAvailable(float[] transform, long timestamp) {
        mVideoRecorder.drainEncoder(false);
        cameraFilter.setTransformMatrix(transform);
        cameraFilter.onDrawFrame(mTextureId, mVertexBuffer, mTextureBuffer);
        mWindowSurface.setPresentationTime(timestamp);
        mWindowSurface.swapBuffers();
    }

    private void handleSetTextureId(int textureId) {
        mTextureId = textureId;
    }

    private void handleUpdateSharedContext(EGLContext eglContext) {
        mWindowSurface.releaseEglSurface();
        cameraFilter.destroy();
        mEglCore.release();

        mEglCore = new EglCore(eglContext, EglCore.FLAG_RECORDABLE);
        mWindowSurface.recreate(mEglCore);
        mWindowSurface.makeCurrent();

        cameraFilter = new BeautyCameraFilter(mContext);
        cameraFilter.init();
    }

    private void prepareRecorder(int width, int height, int bitrate, File outputFile, EGLContext eglContext) {
        try {
            mVideoRecorder = new VideoRecorderCore(width, height, bitrate, outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mVideoWidth = width;
        mVideoHeight = height;
        mEglCore = new EglCore(eglContext, EglCore.FLAG_RECORDABLE);
        mWindowSurface = new WindowEglSurface(mEglCore, mVideoRecorder.getInputSurface(), true);
        mWindowSurface.makeCurrent();

        cameraFilter = new BeautyCameraFilter(mContext);
        cameraFilter.init();
    }

    private void releaseRecorder() {
        mVideoRecorder.release();
        if (mWindowSurface != null) {
            mWindowSurface.release();
            mWindowSurface  = null;
        }
        if (cameraFilter != null) {
            cameraFilter.destroy();
            cameraFilter = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    // 开始录像
    public void startRecording(RecorderConfig config) {
        synchronized (mReadyLock) {
            if (mRunning) {
                return;
            }
            mRunning = true;
            new Thread(this, "CameraRecorder").start();
            while (!mReady) {
                try {
                    mReadyLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORD, config));
    }

    // 结束录像
    public void stopRecording() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORD));
        mHandler.sendMessage(mHandler.obtainMessage(MSG_EXIT_RECORD));
    }

    // 是否在录像中
    public boolean isRecording() {
        return mRunning;
    }

    // 更新上下文
    public void updateSharedContext(EGLContext eglContext) {
        mHandler.obtainMessage(MSG_UPDATE_CONTEXT, eglContext).sendToTarget();
    }

    public void frameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (mReadyLock) {
            if (!mReady)
                return;
            if (mTransformMatrix == null) {
                mTransformMatrix = new float[16];
            }
            surfaceTexture.getTransformMatrix(mTransformMatrix);
            long timestamp = surfaceTexture.getTimestamp();
            mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                    (int)(timestamp >> 32), (int)timestamp, mTransformMatrix));
        }
    }

    public void setTextureId(int textureId) {
        synchronized (mReadyLock) {
            if (!mReady)
                return;
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, textureId, 0, null));
    }

    @Override
    public void run() {
        Looper.prepare();
        synchronized (mReadyLock) {
            mHandler = new RecorderHandler(this);
            mReady = true;
            mReadyLock.notify();
        }
        Looper.loop();
        synchronized (mReadyLock) {
            mReady = false;
            mRunning = false;
            mHandler = null;
        }
    }

    public void setPreviewSize(int width, int height) {
        mPreviewWidth = width;
        mPreviewHeight = height;
    }

    public void setTextureBuffer(FloatBuffer textureBuffer) {
        this.mTextureBuffer = textureBuffer;
    }

    public void setCubeBuffer(FloatBuffer vertexBuffer) {
        this.mVertexBuffer = vertexBuffer;
    }

}
