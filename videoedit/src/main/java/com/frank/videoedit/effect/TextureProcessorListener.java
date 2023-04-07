
package com.frank.videoedit.effect;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.frank.videoedit.entity.TextureInfo;
import com.frank.videoedit.listener.GlTextureProcessor;

import java.util.ArrayDeque;
import java.util.Queue;

public final class TextureProcessorListener
    implements GlTextureProcessor.InputListener, GlTextureProcessor.OutputListener {

  private static final long END_OF_STREAM = Long.MIN_VALUE;

  private final GlTextureProcessor producingGlTextureProcessor;
  private final GlTextureProcessor consumingGlTextureProcessor;
  private final FrameProcessTaskExecutor frameProcessingTaskExecutor;

  private final Queue<Pair<TextureInfo, Long>> availableFrames;

  private int consumingGlTextureProcessorInputCapacity;

  public TextureProcessorListener(GlTextureProcessor producingGlTextureProcessor,
      GlTextureProcessor consumingGlTextureProcessor,
      FrameProcessTaskExecutor frameProcessingTaskExecutor) {
    this.producingGlTextureProcessor = producingGlTextureProcessor;
    this.consumingGlTextureProcessor = consumingGlTextureProcessor;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    availableFrames = new ArrayDeque<>();
  }

  @Override
  public synchronized void onReadyToAcceptInputFrame() {
    @Nullable Pair<TextureInfo, Long> pendingFrame = availableFrames.poll();
    if (pendingFrame == null) {
      consumingGlTextureProcessorInputCapacity++;
      return;
    }

    long presentationTimeUs = pendingFrame.second;
    if (presentationTimeUs == END_OF_STREAM) {
      frameProcessingTaskExecutor.submit(
          consumingGlTextureProcessor::signalEndOfCurrentInputStream);
    } else {
      frameProcessingTaskExecutor.submit(
          () ->
              consumingGlTextureProcessor.queueInputFrame(pendingFrame.first, presentationTimeUs));
    }
  }

  @Override
  public void onInputFrameProcessed(TextureInfo inputTexture) {
    frameProcessingTaskExecutor.submit(
        () -> producingGlTextureProcessor.releaseOutputFrame(inputTexture));
  }

  @Override
  public synchronized void onOutputFrameAvailable(TextureInfo outputTexture, long presentationTimeUs) {
    if (consumingGlTextureProcessorInputCapacity > 0) {
      frameProcessingTaskExecutor.submit(
          () ->
              consumingGlTextureProcessor.queueInputFrame(outputTexture, presentationTimeUs));
      consumingGlTextureProcessorInputCapacity--;
    } else {
      availableFrames.add(new Pair<>(outputTexture, presentationTimeUs));
    }
  }

  @Override
  public synchronized void onCurrentOutputStreamEnded() {
    if (!availableFrames.isEmpty()) {
      availableFrames.add(new Pair<>(TextureInfo.UNSET, END_OF_STREAM));
    } else {
      frameProcessingTaskExecutor.submit(
          consumingGlTextureProcessor::signalEndOfCurrentInputStream);
    }
  }
}
