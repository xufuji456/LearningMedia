
package com.frank.videoedit.effect;

import android.graphics.SurfaceTexture;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.frank.videoedit.entity.FrameInfo;
import com.frank.videoedit.entity.TextureInfo;
import com.frank.videoedit.listener.ExternalTextureProcessor;
import com.frank.videoedit.listener.GlTextureProcessor;
import com.frank.videoedit.util.GlUtil;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ExternalTextureManager implements GlTextureProcessor.InputListener {

  private final FrameProcessTaskExecutor frameProcessingTaskExecutor;
  private final ExternalTextureProcessor externalTextureProcessor;
  private final int externalTexId;
  private final SurfaceTexture surfaceTexture;
  private final float[] textureTransformMatrix;
  private final Queue<FrameInfo> pendingFrames;

  // Incremented on any thread when a frame becomes available on the surfaceTexture, decremented on
  // the GL thread only.
  private final AtomicInteger availableFrameCount;
  // Incremented on any thread, decremented on the GL thread only.
  private final AtomicInteger externalTextureProcessorInputCapacity;

  // Set to true on any thread. Read on the GL thread only.
  private volatile boolean inputStreamEnded;
  // The frame that is sent downstream and is not done processing yet.
  // Set to null on any thread. Read and set to non-null on the GL thread only.
  @Nullable private volatile FrameInfo currentFrame;

  private long previousStreamOffsetUs;

  public ExternalTextureManager(
      ExternalTextureProcessor externalTextureProcessor,
      FrameProcessTaskExecutor frameProcessingTaskExecutor) {
    this.externalTextureProcessor = externalTextureProcessor;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    try {
      externalTexId = GlUtil.createExternalTexture();
    } catch (GlUtil.GlException e) {
      throw new RuntimeException(e);
    }
    surfaceTexture = new SurfaceTexture(externalTexId);
    textureTransformMatrix = new float[16];
    pendingFrames = new ConcurrentLinkedQueue<>();
    availableFrameCount = new AtomicInteger();
    externalTextureProcessorInputCapacity = new AtomicInteger();
    previousStreamOffsetUs = -1;
  }

  public SurfaceTexture getSurfaceTexture() {
    surfaceTexture.setOnFrameAvailableListener(
        unused -> {
          availableFrameCount.getAndIncrement();
          frameProcessingTaskExecutor.submit(this::maybeQueueFrameToExternalTextureProcessor);
        });
    return surfaceTexture;
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    externalTextureProcessorInputCapacity.getAndIncrement();
    frameProcessingTaskExecutor.submit(this::maybeQueueFrameToExternalTextureProcessor);
  }

  @Override
  public void onInputFrameProcessed(TextureInfo inputTexture) {
    currentFrame = null;
    frameProcessingTaskExecutor.submit(this::maybeQueueFrameToExternalTextureProcessor);
  }

  public void registerInputFrame(FrameInfo frame) {
    pendingFrames.add(frame);
  }

  public int getPendingFrameCount() {
    return pendingFrames.size();
  }

  @WorkerThread
  public void signalEndOfInput() {
    inputStreamEnded = true;
    if (pendingFrames.isEmpty() && currentFrame == null) {
      externalTextureProcessor.signalEndOfCurrentInputStream();
    }
  }

  public void release() {
    surfaceTexture.release();
  }

  @WorkerThread
  private void maybeQueueFrameToExternalTextureProcessor() {
    if (externalTextureProcessorInputCapacity.get() == 0
        || availableFrameCount.get() == 0
        || currentFrame != null) {
      return;
    }

    availableFrameCount.getAndDecrement();
    surfaceTexture.updateTexImage();
    this.currentFrame = pendingFrames.remove();

    FrameInfo currentFrame = this.currentFrame;
    externalTextureProcessorInputCapacity.getAndDecrement();
    surfaceTexture.getTransformMatrix(textureTransformMatrix);
    externalTextureProcessor.setTextureTransformMatrix(textureTransformMatrix);
    long frameTimeNs = surfaceTexture.getTimestamp();
    long streamOffsetUs = currentFrame.streamOffsetUs;
    if (streamOffsetUs != previousStreamOffsetUs) {
      if (previousStreamOffsetUs != Long.MIN_VALUE + 1) {
        externalTextureProcessor.signalEndOfCurrentInputStream();
      }
      previousStreamOffsetUs = streamOffsetUs;
    }
    // Correct for the stream offset so processors see original media presentation timestamps.
    long presentationTimeUs = (frameTimeNs / 1000) - streamOffsetUs;
    externalTextureProcessor.queueInputFrame(
        new TextureInfo(
            externalTexId, -1, currentFrame.width, currentFrame.height),
        presentationTimeUs);

    if (inputStreamEnded && pendingFrames.isEmpty()) {
      externalTextureProcessor.signalEndOfCurrentInputStream();
    }
  }
}
