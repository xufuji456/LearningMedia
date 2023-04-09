
package com.frank.videoedit.effect;

import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.view.Surface;

import androidx.annotation.WorkerThread;

import com.frank.videoedit.entity.ColorInfo;
import com.frank.videoedit.entity.FrameInfo;
import com.frank.videoedit.entity.SurfaceInfo;
import com.frank.videoedit.listener.DebugViewProvider;
import com.frank.videoedit.listener.ExternalTextureProcessor;
import com.frank.videoedit.listener.FrameProcessor;
import com.frank.videoedit.listener.GlEffect;
import com.frank.videoedit.listener.GlMatrixTransform;
import com.frank.videoedit.listener.GlTextureProcessor;

import com.frank.videoedit.util.GlUtil;
import com.google.android.exoplayer2.util.Effect;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class GlEffectsFrameProcessor implements FrameProcessor {

  public static class Factory implements FrameProcessor.Factory {

    @Override
    public GlEffectsFrameProcessor create(
        Context context,
        FrameProcessor.Listener listener,
        List<Effect> effects,
        DebugViewProvider debugViewProvider,
        ColorInfo colorInfo,
        boolean releaseFramesAutomatically) {

      ExecutorService singleThreadExecutorService = Executors.newSingleThreadExecutor(
              runnable -> new Thread(runnable, THREAD_NAME));

      Future<GlEffectsFrameProcessor> glFrameProcessorFuture =
          singleThreadExecutorService.submit(
              () ->
                  createOpenGlObjectsAndFrameProcessor(
                      context,
                      listener,
                      effects,
                      debugViewProvider,
                      colorInfo,
                      releaseFramesAutomatically,
                      singleThreadExecutorService));

      try {
        return glFrameProcessorFuture.get();
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }

  @WorkerThread
  private static GlEffectsFrameProcessor createOpenGlObjectsAndFrameProcessor(
      Context context,
      FrameProcessor.Listener listener,
      List<Effect> effects,
      DebugViewProvider debugViewProvider,
      ColorInfo colorInfo,
      boolean releaseFramesAutomatically,
      ExecutorService singleThreadExecutorService)
      throws GlUtil.GlException {

    boolean useHdr = ColorInfo.isTransferHdr(colorInfo);
    EGLDisplay eglDisplay = GlUtil.createEglDisplay();
    int[] configAttributes =
        useHdr ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102 : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888;
    EGLContext eglContext = GlUtil.createEglContext(eglDisplay, configAttributes);
    GlUtil.createFocusedPlaceholderEglSurface(eglContext, eglDisplay, configAttributes);

    ImmutableList<GlTextureProcessor> textureProcessors =
        getGlTextureProcessorsForGlEffects(
            context,
            effects,
            eglDisplay,
            eglContext,
            listener,
            debugViewProvider,
            colorInfo,
            releaseFramesAutomatically);
    FrameProcessTaskExecutor frameProcessTaskExecutor =
        new FrameProcessTaskExecutor(singleThreadExecutorService, listener);
    chainTextureProcessorsWithListeners(textureProcessors, frameProcessTaskExecutor, listener);

    return new GlEffectsFrameProcessor(
        eglDisplay,
        eglContext,
        frameProcessTaskExecutor,
        textureProcessors,
        releaseFramesAutomatically);
  }

  private static ImmutableList<GlTextureProcessor> getGlTextureProcessorsForGlEffects(
      Context context,
      List<Effect> effects,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      FrameProcessor.Listener listener,
      DebugViewProvider debugViewProvider,
      ColorInfo colorInfo,
      boolean releaseFramesAutomatically) {
    ImmutableList.Builder<GlTextureProcessor> textureProcessorListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<GlMatrixTransform> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();

    boolean sampleFromExternalTexture = true;
    for (int i = 0; i < effects.size(); i++) {
      GlEffect glEffect = (GlEffect) effects.get(i);
      if (glEffect instanceof GlMatrixTransform) {
        matrixTransformationListBuilder.add((GlMatrixTransform) glEffect);
        continue;
      }

      ImmutableList<GlMatrixTransform> matrixTransformations =
          matrixTransformationListBuilder.build();

      if (!matrixTransformations.isEmpty() || sampleFromExternalTexture) {
        MatrixTextureProcessor matrixTextureProcessor;
        if (sampleFromExternalTexture) {
          matrixTextureProcessor =
              MatrixTextureProcessor.createWithExternalSamplerApplyingEotf(
                  context, matrixTransformations, colorInfo);
        } else {
          matrixTextureProcessor =
              MatrixTextureProcessor.create(
                  context, matrixTransformations, ColorInfo.isTransferHdr(colorInfo));
        }
        textureProcessorListBuilder.add(matrixTextureProcessor);
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
        sampleFromExternalTexture = false;
      }
      textureProcessorListBuilder.add(
          glEffect.toGlTextureProcessor(context, ColorInfo.isTransferHdr(colorInfo)));
    }

    textureProcessorListBuilder.add(
        new TextureProcessorWrapper(
            context,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder.build(),
            listener,
            debugViewProvider,
            sampleFromExternalTexture,
            colorInfo,
            releaseFramesAutomatically));
    return textureProcessorListBuilder.build();
  }

  /**
   * Chains the given {@link GlTextureProcessor} instances using {@link
   * TextureProcessorListener} instances.
   */
  private static void chainTextureProcessorsWithListeners(
      ImmutableList<GlTextureProcessor> textureProcessors,
      FrameProcessTaskExecutor frameProcessTaskExecutor,
      FrameProcessor.Listener frameProcessorListener) {
    for (int i = 0; i < textureProcessors.size() - 1; i++) {
      GlTextureProcessor producingGlTextureProcessor = textureProcessors.get(i);
      GlTextureProcessor consumingGlTextureProcessor = textureProcessors.get(i + 1);
      TextureProcessorListener textureProcessorListener =
          new TextureProcessorListener(
              producingGlTextureProcessor,
              consumingGlTextureProcessor,
              frameProcessTaskExecutor);
      producingGlTextureProcessor.setOutputListener(textureProcessorListener);
      producingGlTextureProcessor.setErrorListener(frameProcessorListener::onFrameProcessingError);
      consumingGlTextureProcessor.setInputListener(textureProcessorListener);
    }
  }

  private static final String THREAD_NAME = "Effect:GlThread";
  private static final long RELEASE_WAIT_TIME_MS = 100;

  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final FrameProcessTaskExecutor frameProcessingTaskExecutor;
  private final ExternalTextureManager inputExternalTextureManager;
  private final Surface inputSurface;
  private final boolean releaseFramesAutomatically;
  private final TextureProcessorWrapper finalTextureProcessorWrapper;
  private final ImmutableList<GlTextureProcessor> allTextureProcessors;

  private FrameInfo nextInputFrameInfo;

  private long previousStreamOffsetUs;
  private static final long TIME_UNSET = Long.MIN_VALUE + 1;

  private GlEffectsFrameProcessor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      FrameProcessTaskExecutor frameProcessingTaskExecutor,
      ImmutableList<GlTextureProcessor> textureProcessors,
      boolean releaseFramesAutomatically) {

    this.eglDisplay                  = eglDisplay;
    this.eglContext                  = eglContext;
    this.releaseFramesAutomatically  = releaseFramesAutomatically;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;

    ExternalTextureProcessor inputExternalTextureProcessor =
        (ExternalTextureProcessor) textureProcessors.get(0);
    inputExternalTextureManager =
        new ExternalTextureManager(inputExternalTextureProcessor, frameProcessingTaskExecutor);
    inputExternalTextureProcessor.setInputListener(inputExternalTextureManager);
    inputSurface = new Surface(inputExternalTextureManager.getSurfaceTexture());
    finalTextureProcessorWrapper = (TextureProcessorWrapper) getLast(textureProcessors);
    allTextureProcessors = textureProcessors;
    previousStreamOffsetUs = TIME_UNSET;
  }

  @Override
  public Surface getInputSurface() {
    return inputSurface;
  }

  @Override
  public void setInputFrameInfo(FrameInfo inputFrameInfo) {
    nextInputFrameInfo = adjustForPixelWidthHeightRatio(inputFrameInfo);

    if (nextInputFrameInfo.streamOffsetUs != previousStreamOffsetUs) {
      finalTextureProcessorWrapper.appendStream(nextInputFrameInfo.streamOffsetUs);
      previousStreamOffsetUs = nextInputFrameInfo.streamOffsetUs;
    }
  }

  @Override
  public void registerInputFrame() {
    inputExternalTextureManager.registerInputFrame(nextInputFrameInfo);
  }

  @Override
  public int getPendingInputFrameCount() {
    return inputExternalTextureManager.getPendingFrameCount();
  }

  @Override
  public void setOutputSurfaceInfo(SurfaceInfo outputSurfaceInfo) {
    finalTextureProcessorWrapper.setOutputSurfaceInfo(outputSurfaceInfo);
  }

  @Override
  public void releaseOutputFrame(long releaseTimeNs) {
    frameProcessingTaskExecutor.submitWithHighPriority(
        () -> finalTextureProcessorWrapper.releaseOutputFrame(releaseTimeNs));
  }

  @Override
  public void signalEndOfInput() {
    frameProcessingTaskExecutor.submit(inputExternalTextureManager::signalEndOfInput);
  }

  @Override
  public void release() {
    try {
      frameProcessingTaskExecutor.release(
          this::releaseTextureProcessorsAndDestroyGlContext,
          RELEASE_WAIT_TIME_MS);
    } catch (InterruptedException unexpected) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(unexpected);
    }
    inputExternalTextureManager.release();
    inputSurface.release();
  }

  private FrameInfo adjustForPixelWidthHeightRatio(FrameInfo frameInfo) {
    if (frameInfo.pixelWidthHeightRatio > 1f) {
      return new FrameInfo(
          (int) (frameInfo.width * frameInfo.pixelWidthHeightRatio),
          frameInfo.height,
          1,
          frameInfo.streamOffsetUs);
    } else if (frameInfo.pixelWidthHeightRatio < 1f) {
      return new FrameInfo(
          frameInfo.width,
          (int) (frameInfo.height / frameInfo.pixelWidthHeightRatio),
          1,
          frameInfo.streamOffsetUs);
    } else {
      return frameInfo;
    }
  }

  @WorkerThread
  private void releaseTextureProcessorsAndDestroyGlContext()
      throws GlUtil.GlException {
    for (int i = 0; i < allTextureProcessors.size(); i++) {
      allTextureProcessors.get(i).release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

}
