package com.frank.camerafilter.filter;

import android.opengl.GLES30;

import com.frank.camerafilter.util.OpenGLUtil;
import com.frank.camerafilter.util.Rotation;
import com.frank.camerafilter.util.TextureRotateUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;

/**
 * @author xufulong
 * @date 2022/10/17 10:41 下午
 * @desc
 */
public class BaseFilter {

    private String mVertexShader;
    private String mFragmentShader;

    protected int mProgramId;
    protected int mInputWidth;
    protected int mInputHeight;
    protected int mOutputWidth;
    protected int mOutputHeight;

    protected int mUniformTexture;
    protected int mAttributePosition;
    protected int mTextureCoordinate;

    protected boolean mHasInitialized;

    protected FloatBuffer mVertexBuffer;
    protected FloatBuffer mTextureBuffer;

    private final LinkedList<Runnable> mRunnableList;

    public BaseFilter(String vertexShader, String fragmentShader) {
        mRunnableList   = new LinkedList<>();
        mVertexShader   = vertexShader;
        mFragmentShader = fragmentShader;

        // 创建顶点缓冲区
        mVertexBuffer = ByteBuffer.allocateDirect(TextureRotateUtil.VERTEX.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVertexBuffer.put(TextureRotateUtil.VERTEX).position(0);
        // 创建纹理缓冲区
        mTextureBuffer = ByteBuffer.allocateDirect(TextureRotateUtil.TEXTURE_ROTATE_0.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTextureBuffer.put(TextureRotateUtil.getRotateTexture(Rotation.NORMAL, false, true))
                .position(0);
    }

    protected void onInit() {
        mProgramId = OpenGLUtil.loadProgram(mVertexShader, mFragmentShader);
        mAttributePosition = GLES30.glGetAttribLocation(mProgramId, "aPosition");
        mUniformTexture = GLES30.glGetUniformLocation(mProgramId, "inputImageTexture");
        mTextureCoordinate = GLES30.glGetAttribLocation(mProgramId, "aInputTextureCoord");
    }

    protected void onInitialized() {}

    public void init() {
        onInit();
        mHasInitialized = true;
        onInitialized();
    }

    protected void onDestroy() {}

    public void destroy() {
        mHasInitialized = false;
        GLES30.glDeleteProgram(mProgramId);
        onDestroy();
    }

    public void onInputSizeChanged(final int width, final int height) {
        mInputWidth  = width;
        mInputHeight = height;
    }

    protected void runPendingDrawTask() {
        while (!mRunnableList.isEmpty()) {
            mRunnableList.removeFirst().run();
        }
    }

    protected void onDrawBefore() {}

    protected void onDrawAfter() {}

    public int onDrawFrame(final int textureId) {
        return onDrawFrame(textureId, mVertexBuffer, mTextureBuffer);
    }

    public int onDrawFrame(final int textureId, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        if (!mHasInitialized)
            return OpenGLUtil.NOT_INIT;

        GLES30.glUseProgram(mProgramId);
        runPendingDrawTask();
        // 设置顶点坐标、纹理坐标
        vertexBuffer.position(0);
        GLES30.glVertexAttribPointer(mAttributePosition, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glEnableVertexAttribArray(mAttributePosition);
        textureBuffer.position(0);
        GLES30.glVertexAttribPointer(mTextureCoordinate, 2, GLES30.GL_FLOAT, false, 0, textureBuffer);
        GLES30.glEnableVertexAttribArray(mTextureCoordinate);
        // 绑定纹理
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glUniform1i(mUniformTexture, 0);
        // 绘制
        onDrawBefore();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        // 解绑操作
        GLES30.glDisableVertexAttribArray(mAttributePosition);
        GLES30.glDisableVertexAttribArray(mTextureCoordinate);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        onDrawAfter();

        return OpenGLUtil.ON_DRAWN;
    }

    public boolean hasInitialized() {
        return mHasInitialized;
    }

    public int getProgramId() {
        return mProgramId;
    }

    protected void runOnDraw(final Runnable runnable) {
        synchronized (mRunnableList) {
            mRunnableList.addLast(runnable);
        }
    }

    public void setFloat(final int location, final float floatVal) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform1f(location, floatVal);
            }
        });
    }

    public void setFloatVec2(final int location, final float[] floatArray) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform2fv(location, 1, FloatBuffer.wrap(floatArray));
            }
        });
    }

    public void onOutputSizeChanged(final int width, final int height) {
        mOutputWidth  = width;
        mOutputHeight = height;
    }

}
