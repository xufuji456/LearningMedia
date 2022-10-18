package com.frank.camerafilter.filter;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;

import com.frank.camerafilter.R;
import com.frank.camerafilter.util.OpenGLUtil;

import java.nio.FloatBuffer;

/**
 * @author xufulong
 * @date 2022/10/18 8:27 上午
 * @desc
 */
public class BeautyCameraFilter extends BaseFilter {

    private int mWidth;
    private int mHeight;
    private int[] mFrameBuffer = null;
    private int[] mFrameBufferTexture = null;

    private int mTransformLocation;
    private float[] mTransformMatrix;

    public BeautyCameraFilter(Context context) {
        super(OpenGLUtil.readShaderFromSource(context, R.raw.default_vert),
                OpenGLUtil.readShaderFromSource(context, R.raw.default_fragment));
    }

    protected void onInit() {
        super.onInit();
        mTransformLocation = GLES30.glGetUniformLocation(getProgramId(), "mTextureTransform");
    }

    public void setTransformMatrix(float[] matrix) {
        mTransformMatrix = matrix;
    }

    @Override
    public int onDrawFrame(int textureId) {
        return onDrawFrame(textureId, mVertexBuffer, mTextureBuffer);
    }

    @Override
    public int onDrawFrame(int textureId, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        if (!hasInitialized())
            return OpenGLUtil.NOT_INIT;

        GLES30.glUseProgram(getProgramId());
        runPendingDrawTask();

        vertexBuffer.position(0);
        GLES30.glVertexAttribPointer(mAttributePosition, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glEnableVertexAttribArray(mAttributePosition);
        textureBuffer.position(0);
        GLES30.glVertexAttribPointer(mTextureCoordinate, 2, GLES30.GL_FLOAT, false, 0, textureBuffer);
        GLES30.glEnableVertexAttribArray(mTextureCoordinate);
        GLES30.glUniformMatrix4fv(mTransformLocation, 1, false, mTransformMatrix, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        // surfaceTexture对应的GL_TEXTURE_EXTERNAL_OES
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES30.glUniform1i(mUniformTexture, 0);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        GLES30.glDisableVertexAttribArray(mAttributePosition);
        GLES30.glDisableVertexAttribArray(mTextureCoordinate);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        return OpenGLUtil.ON_DRAWN;
    }

    // 使用FBO绑定纹理
    public int onDrawToTexture(int textureId) {
        if (!hasInitialized())
            return OpenGLUtil.NOT_INIT;
        if (mFrameBuffer == null)
            return OpenGLUtil.NO_TEXTURE;

        GLES30.glUseProgram(getProgramId());
        runPendingDrawTask();
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFrameBuffer[0]);

        mVertexBuffer.position(0);
        GLES30.glVertexAttribPointer(mAttributePosition, 2, GLES30.GL_FLOAT, false, 0, mVertexBuffer);
        GLES30.glEnableVertexAttribArray(mAttributePosition);
        mTextureBuffer.position(0);
        GLES30.glVertexAttribPointer(mTextureCoordinate, 2, GLES30.GL_FLOAT, false, 0, mTextureBuffer);
        GLES30.glEnableVertexAttribArray(mTextureCoordinate);
        GLES30.glUniformMatrix4fv(mTransformLocation, 1, false, mTransformMatrix, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES30.glUniform1i(mUniformTexture, 0);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        GLES30.glDisableVertexAttribArray(mAttributePosition);
        GLES30.glDisableVertexAttribArray(mTextureCoordinate);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mOutputWidth, mOutputHeight);

        return mFrameBufferTexture[0];
    }

    @Override
    public void onInputSizeChanged(int width, int height) {
        super.onInputSizeChanged(width, height);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyFrameBuffer();
    }

    public void destroyFrameBuffer() {
        if (mFrameBufferTexture != null) {
            GLES30.glDeleteTextures(1, mFrameBufferTexture, 0);
            mFrameBufferTexture = null;
        }
        if (mFrameBuffer != null) {
            GLES30.glDeleteFramebuffers(1, mFrameBuffer, 0);
            mFrameBuffer = null;
        }
    }
}
