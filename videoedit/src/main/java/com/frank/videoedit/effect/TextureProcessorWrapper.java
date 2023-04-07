
package com.frank.videoedit.effect;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.frank.videoedit.entity.TextureInfo;
import com.frank.videoedit.listener.ExternalTextureProcessor;
import com.frank.videoedit.listener.FrameProcessTask;
import com.frank.videoedit.listener.GlMatrixTransform;
import com.frank.videoedit.listener.GlTextureProcessor;
import com.frank.videoedit.util.MatrixUtil;
import com.frank.videoedit.listener.DebugViewProvider;
import com.frank.videoedit.listener.FrameProcessor;
import com.frank.videoedit.util.GlUtil;
import com.frank.videoedit.entity.SurfaceInfo;
import com.frank.videoedit.entity.ColorInfo;
import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Wrapper around a {@link GlTextureProcessor} that writes to the provided output surface and
 * optional debug surface view.
 *
 */
public final class TextureProcessorWrapper implements ExternalTextureProcessor {

  private static final String TAG = "FinalProcessorWrapper";

  private final Context context;
  private final ImmutableList<GlMatrixTransform> matrixTransformations;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final DebugViewProvider debugViewProvider;
  private final FrameProcessor.Listener frameProcessorListener;
  private final boolean sampleFromExternalTexture;
  private final ColorInfo colorInfo;
  private final boolean releaseFramesAutomatically;
  private final float[] textureTransformMatrix;
  private final Queue<Long> streamOffsetUsQueue;
  private final Queue<Pair<TextureInfo, Long>> availableFrames;

  private int inputWidth;
  private int inputHeight;
  @Nullable private MatrixTextureProcessor matrixTextureProcessor;
  @Nullable private SurfaceViewWrapper debugSurfaceViewWrapper;
  private InputListener inputListener;
  private @MonotonicNonNull Pair<Integer, Integer> outputSizeBeforeSurfaceTransformation;
  @Nullable private SurfaceView debugSurfaceView;

  private volatile boolean outputSizeOrRotationChanged;

  @GuardedBy("this")
  @Nullable
  private SurfaceInfo outputSurfaceInfo;

  @GuardedBy("this")
  @Nullable
  private EGLSurface outputEglSurface;

  public TextureProcessorWrapper(
      Context context,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      ImmutableList<GlMatrixTransform> matrixTransformations,
      FrameProcessor.Listener frameProcessorListener,
      DebugViewProvider debugViewProvider,
      boolean sampleFromExternalTexture,
      ColorInfo colorInfo,
      boolean releaseFramesAutomatically) {
    this.context                    = context;
    this.colorInfo                  = colorInfo;
    this.eglDisplay                 = eglDisplay;
    this.eglContext                 = eglContext;
    this.debugViewProvider          = debugViewProvider;
    this.matrixTransformations      = matrixTransformations;
    this.frameProcessorListener     = frameProcessorListener;
    this.sampleFromExternalTexture  = sampleFromExternalTexture;
    this.releaseFramesAutomatically = releaseFramesAutomatically;

    textureTransformMatrix = GlUtil.create4x4IdentityMatrix();
    streamOffsetUsQueue = new ConcurrentLinkedQueue<>();
    inputListener = new InputListener() {};
    availableFrames = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setErrorListener(ErrorListener errorListener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
    long streamOffsetUs = 0;
    if (!streamOffsetUsQueue.isEmpty()) {
      streamOffsetUs = streamOffsetUsQueue.peek();
    }
    long offsetPresentationTimeUs = presentationTimeUs + streamOffsetUs;
    frameProcessorListener.onOutputFrameAvailable(offsetPresentationTimeUs);
    if (releaseFramesAutomatically) {
      renderFrameToSurfaces(
          inputTexture, presentationTimeUs, /* releaseTimeNs= */ offsetPresentationTimeUs * 1000);
    } else {
      availableFrames.add(Pair.create(inputTexture, presentationTimeUs));
    }
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void releaseOutputFrame(TextureInfo outputTexture) {
    throw new UnsupportedOperationException();
  }

  @WorkerThread
  public void releaseOutputFrame(long releaseTimeNs) {
    Pair<TextureInfo, Long> oldestAvailableFrame = availableFrames.remove();
    renderFrameToSurfaces(oldestAvailableFrame.first, oldestAvailableFrame.second, releaseTimeNs);
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    streamOffsetUsQueue.remove();
    if (streamOffsetUsQueue.isEmpty()) {
      frameProcessorListener.onFrameProcessingEnded();
    }
  }

  @Override
  @WorkerThread
  public void release() {
    if (matrixTextureProcessor != null) {
      matrixTextureProcessor.release();
    }
  }

  @Override
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    System.arraycopy(
        textureTransformMatrix, 0,
        this.textureTransformMatrix, 0, textureTransformMatrix.length);

    if (matrixTextureProcessor != null) {
      matrixTextureProcessor.setTextureTransformMatrix(textureTransformMatrix);
    }
  }

  public void appendStream(long streamOffsetUs) {
    streamOffsetUsQueue.add(streamOffsetUs);
  }

  public synchronized void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    if (this.outputSurfaceInfo != null && !this.outputSurfaceInfo.equals(outputSurfaceInfo)) {
      if (outputSurfaceInfo != null
          && !this.outputSurfaceInfo.surface.equals(outputSurfaceInfo.surface)) {
        this.outputEglSurface = null;
      }
      outputSizeOrRotationChanged =
              outputSurfaceInfo == null
              || this.outputSurfaceInfo.width != outputSurfaceInfo.width
              || this.outputSurfaceInfo.height != outputSurfaceInfo.height
              || this.outputSurfaceInfo.orientationDegrees != outputSurfaceInfo.orientationDegrees;
      this.outputSurfaceInfo = outputSurfaceInfo;
    }
  }

  private void renderFrameToSurfaces(
      TextureInfo inputTexture, long presentationTimeUs, long releaseTimeNs) {
    try {
      maybeRenderFrameToOutputSurface(inputTexture, presentationTimeUs, releaseTimeNs);
    } catch (RuntimeException | GlUtil.GlException e) {
      frameProcessorListener.onFrameProcessingError(new RuntimeException(e));
    }
    maybeRenderFrameToDebugSurface(inputTexture, presentationTimeUs);
    inputListener.onInputFrameProcessed(inputTexture);
  }

  private synchronized void maybeRenderFrameToOutputSurface(
      TextureInfo inputTexture, long presentationTimeUs, long releaseTimeNs)
      throws RuntimeException, GlUtil.GlException {
    if (releaseTimeNs == FrameProcessor.DROP_OUTPUT_FRAME
        || !ensureConfigured(inputTexture.width, inputTexture.height)) {
      return;
    }

    EGLSurface outputEglSurface = this.outputEglSurface;
    SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
    MatrixTextureProcessor matrixTextureProcessor = this.matrixTextureProcessor;

    GlUtil.focusEglSurface(
        eglDisplay,
        eglContext,
        outputEglSurface,
        outputSurfaceInfo.width,
        outputSurfaceInfo.height);
    GlUtil.clearOutputFrame();
    matrixTextureProcessor.drawFrame(inputTexture.texId, presentationTimeUs);

    EGLExt.eglPresentationTimeANDROID(
        eglDisplay,
        outputEglSurface,
        releaseTimeNs == FrameProcessor.RELEASE_OUTPUT_FRAME_IMMEDIATELY
            ? System.nanoTime()
            : releaseTimeNs);
    EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);
  }

  @EnsuresNonNullIf(
      expression = {"outputSurfaceInfo", "outputEglSurface", "matrixTextureProcessor"},
      result = true)
  private synchronized boolean ensureConfigured(int inputWidth, int inputHeight)
      throws RuntimeException, GlUtil.GlException {

    if (this.inputWidth != inputWidth
        || this.inputHeight != inputHeight
        || this.outputSizeBeforeSurfaceTransformation == null) {
      this.inputWidth = inputWidth;
      this.inputHeight = inputHeight;
      Pair<Integer, Integer> outputSizeBeforeSurfaceTransformation =
          MatrixUtil.configureAndGetOutputSize(inputWidth, inputHeight, matrixTransformations);
      if (!outputSizeBeforeSurfaceTransformation.equals(this.outputSizeBeforeSurfaceTransformation)) {
        this.outputSizeBeforeSurfaceTransformation = outputSizeBeforeSurfaceTransformation;
        frameProcessorListener.onOutputSizeChanged(
            outputSizeBeforeSurfaceTransformation.first,
            outputSizeBeforeSurfaceTransformation.second);
      }
    }

    if (outputSurfaceInfo == null) {
      if (matrixTextureProcessor != null) {
        matrixTextureProcessor.release();
        matrixTextureProcessor = null;
      }
      outputEglSurface = null;
      return false;
    }

    SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
    @Nullable EGLSurface outputEglSurface = this.outputEglSurface;
    if (outputEglSurface == null) {
      boolean colorInfoIsHdr = ColorInfo.isTransferHdr(colorInfo);

      outputEglSurface =
          GlUtil.getEglSurface(
              eglDisplay,
              outputSurfaceInfo.surface,
              colorInfoIsHdr
                  ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102
                  : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);

      @Nullable
      SurfaceView debugSurfaceView =
          debugViewProvider.getDebugPreviewSurfaceView(
              outputSurfaceInfo.width, outputSurfaceInfo.height);
      if (debugSurfaceView != null && this.debugSurfaceView != null
              && !this.debugSurfaceView.equals(debugSurfaceView)) {
        debugSurfaceViewWrapper =
            new SurfaceViewWrapper(eglDisplay, eglContext, colorInfoIsHdr, debugSurfaceView);
      }
      this.debugSurfaceView = debugSurfaceView;
    }

    if (matrixTextureProcessor != null && outputSizeOrRotationChanged) {
      matrixTextureProcessor.release();
      matrixTextureProcessor = null;
      outputSizeOrRotationChanged = false;
    }
    if (matrixTextureProcessor == null) {
      matrixTextureProcessor = createMatrixTextureProcessorForOutputSurface(outputSurfaceInfo);
    }

    this.outputSurfaceInfo = outputSurfaceInfo;
    this.outputEglSurface = outputEglSurface;
    return true;
  }

  private MatrixTextureProcessor createMatrixTextureProcessorForOutputSurface(
      SurfaceInfo outputSurfaceInfo) throws RuntimeException {
    ImmutableList.Builder<GlMatrixTransform> matrixTransformationListBuilder =
        new ImmutableList.Builder<GlMatrixTransform>().addAll(matrixTransformations);
    if (outputSurfaceInfo.orientationDegrees != 0) {
      matrixTransformationListBuilder.add(
          new ScaleToFitTransformation.Builder()
              .setRotationDegrees(outputSurfaceInfo.orientationDegrees)
              .build());
    }
    matrixTransformationListBuilder.add(
        Presentation.createForWidthAndHeight(
            outputSurfaceInfo.width, outputSurfaceInfo.height, Presentation.LAYOUT_SCALE_TO_FIT));

    MatrixTextureProcessor matrixTextureProcessor;
    ImmutableList<GlMatrixTransform> expandedMatrixTransformations =
        matrixTransformationListBuilder.build();
    if (sampleFromExternalTexture) {
      matrixTextureProcessor =
          MatrixTextureProcessor.createWithExternalSamplerApplyingEotfThenOetf(
              context, expandedMatrixTransformations, colorInfo);
    } else {
      matrixTextureProcessor =
          MatrixTextureProcessor.createApplyingOetf(
              context, expandedMatrixTransformations, colorInfo);
    }

    matrixTextureProcessor.setTextureTransformMatrix(textureTransformMatrix);

    return matrixTextureProcessor;
  }

  private void maybeRenderFrameToDebugSurface(TextureInfo inputTexture, long presentationTimeUs) {
    if (debugSurfaceViewWrapper == null || this.matrixTextureProcessor == null) {
      return;
    }

    MatrixTextureProcessor matrixTextureProcessor = this.matrixTextureProcessor;
    try {
      debugSurfaceViewWrapper.maybeRenderToSurfaceView(
          () -> {
            GlUtil.clearOutputFrame();
            matrixTextureProcessor.drawFrame(inputTexture.texId, presentationTimeUs);
          });
    } catch (RuntimeException | GlUtil.GlException e) {
      Log.e(TAG, "Error rendering to debug preview", e);
    }
  }


  private static final class SurfaceViewWrapper implements SurfaceHolder.Callback {

    private Surface surface;
    private EGLSurface eglSurface;

    private int width;
    private int height;

    private final boolean useHdr;
    private final EGLDisplay eglDisplay;
    private final EGLContext eglContext;

    public SurfaceViewWrapper(
        EGLDisplay eglDisplay, EGLContext eglContext, boolean useHdr, SurfaceView surfaceView) {
      this.useHdr     = useHdr;
      this.eglDisplay = eglDisplay;
      this.eglContext = eglContext;

      surfaceView.getHolder().addCallback(this);

      surface = surfaceView.getHolder().getSurface();
      width   = surfaceView.getWidth();
      height  = surfaceView.getHeight();
    }

    @WorkerThread
    public synchronized void maybeRenderToSurfaceView(FrameProcessTask renderingTask)
        throws GlUtil.GlException, RuntimeException {
      if (surface == null) {
        return;
      }

      if (eglSurface == null) {
        eglSurface =
            GlUtil.getEglSurface(
                eglDisplay,
                surface,
                useHdr
                    ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102
                    : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);
      }
      EGLSurface eglSurface = this.eglSurface;
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, width, height);
      renderingTask.run();
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);
      // Prevents white flashing on the debug SurfaceView when frames are rendered too fast.
      GLES20.glFinish();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public synchronized void surfaceChanged(
        SurfaceHolder holder, int format, int width, int height) {
      this.width  = width;
      this.height = height;
      Surface newSurface = holder.getSurface();
      if (surface == null || !surface.equals(newSurface)) {
        surface = newSurface;
        eglSurface = null;
      }
    }

    @Override
    public synchronized void surfaceDestroyed(SurfaceHolder holder) {
      width      = -1;
      height     = -1;
      surface    = null;
      eglSurface = null;
    }
  }

}
