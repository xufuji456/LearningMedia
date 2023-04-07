
package com.frank.videoedit.transform.render;

import com.frank.videoedit.transform.FallbackListener;
//import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.MuxerWrapper;
import com.frank.videoedit.transform.TransformationException;
import com.frank.videoedit.transform.TransformationRequest;
import com.frank.videoedit.transform.Transformer;
import com.frank.videoedit.transform.TransformerMediaClock;
//import com.frank.videoedit.transform.entity.DecoderInputBuffer;
import com.frank.videoedit.transform.impl.SamplePipeline;

import com.frank.videoedit.transform.util.MediaUtil;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.errorprone.annotations.ForOverride;

import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

public abstract class TransformerBaseRenderer extends BaseRenderer {

  protected final MuxerWrapper muxerWrapper;
  protected final TransformerMediaClock mediaClock;
  protected final TransformationRequest transformationRequest;
  protected final Transformer.AsyncErrorListener asyncErrorListener;
  protected final FallbackListener fallbackListener;

  private boolean isTransformationRunning;
  protected long streamOffsetUs;
  protected long streamStartPositionUs;
  protected @MonotonicNonNull SamplePipeline samplePipeline;

  public TransformerBaseRenderer(
      int trackType,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      Transformer.AsyncErrorListener asyncErrorListener,
      FallbackListener fallbackListener) {
    super(trackType);
    this.muxerWrapper = muxerWrapper;
    this.mediaClock = mediaClock;
    this.transformationRequest = transformationRequest;
    this.asyncErrorListener = asyncErrorListener;
    this.fallbackListener = fallbackListener;
  }

  @Override
  public final @Capabilities int supportsFormat(Format format) {
    return RendererCapabilities.create(
        MediaUtil.getTrackType(format.sampleMimeType) == getTrackType()
            ? MediaUtil.FORMAT_HANDLED
            : MediaUtil.FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  public final MediaClock getMediaClock() {
    return mediaClock;
  }

  @Override
  public final boolean isReady() {
    return isSourceReady();
  }

  @Override
  public final boolean isEnded() {
    return samplePipeline != null && samplePipeline.isEnded();
  }

  @Override
  public final void render(long positionUs, long elapsedRealtimeUs) {
    try {
      if (!isTransformationRunning || isEnded() || !ensureConfigured()) {
        return;
      }

      while (samplePipeline.processData() || feedPipelineFromInput()) {}
    } catch (TransformationException e) {
      isTransformationRunning = false;
      asyncErrorListener.onTransformationException(e);
    }
  }

  @Override
  protected final void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.streamOffsetUs = offsetUs;
    this.streamStartPositionUs = startPositionUs;
  }

  @Override
  protected final void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    muxerWrapper.registerTrack();
    fallbackListener.registerTrack();
    mediaClock.updateTimeForTrackType(getTrackType(), 0L);
  }

  @Override
  protected final void onStarted() {
    isTransformationRunning = true;
  }

  @Override
  protected final void onStopped() {
    isTransformationRunning = false;
  }

  @Override
  protected final void onReset() {
    if (samplePipeline != null) {
      samplePipeline.release();
    }
  }

  @ForOverride
  @EnsuresNonNullIf(expression = "samplePipeline", result = true)
  protected abstract boolean ensureConfigured() throws TransformationException;

  @RequiresNonNull("samplePipeline")
  private boolean feedPipelineFromInput() throws TransformationException {
    DecoderInputBuffer samplePipelineInputBuffer = samplePipeline.dequeueInputBuffer();
    if (samplePipelineInputBuffer == null) {
      return false;
    }

    @SampleStream.ReadDataResult
    int result = readSource(getFormatHolder(), samplePipelineInputBuffer, 0);
    switch (result) {
      case MediaUtil.RESULT_BUFFER_READ:
        samplePipelineInputBuffer.flip();
        if (samplePipelineInputBuffer.isEndOfStream()) {
          samplePipeline.queueInputBuffer();
          return false;
        }
        mediaClock.updateTimeForTrackType(getTrackType(), samplePipelineInputBuffer.timeUs);
        samplePipeline.queueInputBuffer();
        return true;
      case MediaUtil.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case MediaUtil.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }

}
