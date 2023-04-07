
package com.frank.videoedit.transform;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
//import com.frank.videoedit.transform.entity.DecoderInputBuffer;
import com.frank.videoedit.transform.impl.Muxer;
import com.frank.videoedit.transform.impl.SamplePipeline;
import com.frank.videoedit.transform.util.MediaUtil;

abstract class BaseSamplePipeline implements SamplePipeline {

  private final long streamOffsetUs;
  private final long streamStartPositionUs;
  private final MuxerWrapper muxerWrapper;
  private final @MediaUtil.TrackType int trackType;

  @Nullable private DecoderInputBuffer inputBuffer;
  private boolean muxerWrapperTrackAdded;
  private boolean isEnded;

  public BaseSamplePipeline(
      Format inputFormat,
      long streamOffsetUs,
      long streamStartPositionUs,
      boolean flattenForSlowMotion,
      MuxerWrapper muxerWrapper) {
    this.streamOffsetUs = streamOffsetUs;
    this.streamStartPositionUs = streamStartPositionUs;
    this.muxerWrapper = muxerWrapper;
    trackType = MediaUtil.getTrackType(inputFormat.sampleMimeType);
  }

  @Nullable
  @Override
  public DecoderInputBuffer dequeueInputBuffer() throws TransformationException {
    inputBuffer = dequeueInputBufferInternal();
    return inputBuffer;
  }

  @Override
  public void queueInputBuffer() throws TransformationException {
//    if (!shouldDropInputBuffer()) {
      queueInputBufferInternal();
//    }
  }

  @Override
  public boolean processData() throws TransformationException {
    return feedMuxer() || processDataUpToMuxer();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Nullable
  protected abstract DecoderInputBuffer dequeueInputBufferInternal() throws TransformationException;

  protected abstract void queueInputBufferInternal() throws TransformationException;

  protected abstract boolean processDataUpToMuxer() throws TransformationException;

  @Nullable
  protected abstract Format getMuxerInputFormat() throws TransformationException;

  @Nullable
  protected abstract DecoderInputBuffer getMuxerInputBuffer() throws TransformationException;

  protected abstract void releaseMuxerInputBuffer() throws TransformationException;

  protected abstract boolean isMuxerInputEnded();

  private boolean feedMuxer() throws TransformationException {
    if (!muxerWrapperTrackAdded) {
      @Nullable Format inputFormat = getMuxerInputFormat();
      if (inputFormat == null) {
        return false;
      }
      try {
        muxerWrapper.addTrackFormat(inputFormat);
      } catch (Muxer.MuxerException e) {
        throw TransformationException.createForMuxer(
            e, TransformationException.ERROR_CODE_MUXING_FAILED);
      }
      muxerWrapperTrackAdded = true;
    }

    if (isMuxerInputEnded()) {
      muxerWrapper.endTrack(trackType);
      isEnded = true;
      return false;
    }

    @Nullable DecoderInputBuffer muxerInputBuffer = getMuxerInputBuffer();
    if (muxerInputBuffer == null) {
      return false;
    }

    long samplePresentationTimeUs = muxerInputBuffer.timeUs - streamStartPositionUs;

    try {
      if (!muxerWrapper.writeSample(
          trackType,
          muxerInputBuffer.data,
          muxerInputBuffer.isKeyFrame(),
          samplePresentationTimeUs)) {
        return false;
      }
    } catch (Muxer.MuxerException e) {
      throw TransformationException.createForMuxer(
          e, TransformationException.ERROR_CODE_MUXING_FAILED);
    }

    releaseMuxerInputBuffer();
    return true;
  }
}
