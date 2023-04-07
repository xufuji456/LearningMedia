
package com.frank.videoedit.transform;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.ParcelFileDescriptor;
import android.util.SparseIntArray;
import android.util.SparseLongArray;

import androidx.annotation.Nullable;

import com.frank.videoedit.transform.impl.Muxer;

import com.frank.videoedit.transform.util.MediaUtil;
import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public final class MuxerWrapper {

  private static final long MAX_TRACK_WRITE_AHEAD_US = msToUs(500);

  @Nullable private final String outputPath;
  @Nullable private final ParcelFileDescriptor outputParcelFileDescriptor;
  private final Muxer.Factory muxerFactory;
  private final Transformer.AsyncErrorListener asyncErrorListener;
  private final SparseIntArray trackTypeToIndex;
  private final SparseIntArray trackTypeToSampleCount;
  private final SparseLongArray trackTypeToTimeUs;
  private final SparseLongArray trackTypeToBytesWritten;
  private final ScheduledExecutorService abortScheduledExecutorService;

  private int trackCount;
  private int trackFormatCount;
  private boolean isReady;
  private @MediaUtil.TrackType int previousTrackType;
  private long minTrackTimeUs;
  private @MonotonicNonNull ScheduledFuture<?> abortScheduledFuture;
  private boolean isAborted;
  private @MonotonicNonNull Muxer muxer;

  public MuxerWrapper(
      @Nullable String outputPath,
      @Nullable ParcelFileDescriptor outputParcelFileDescriptor,
      Muxer.Factory muxerFactory,
      Transformer.AsyncErrorListener asyncErrorListener) {
    if (outputPath == null && outputParcelFileDescriptor == null) {
      throw new NullPointerException("Both output path and ParcelFileDescriptor are null");
    }

    this.outputPath = outputPath;
    this.outputParcelFileDescriptor = outputParcelFileDescriptor;
    this.muxerFactory = muxerFactory;
    this.asyncErrorListener = asyncErrorListener;

    trackTypeToIndex = new SparseIntArray();
    trackTypeToSampleCount = new SparseIntArray();
    trackTypeToTimeUs = new SparseLongArray();
    trackTypeToBytesWritten = new SparseLongArray();
    previousTrackType = MediaUtil.TRACK_TYPE_NONE;
    abortScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  }

  public void registerTrack() {
    trackCount++;
  }

  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    @MediaUtil.TrackType int trackType = MediaUtil.getTrackType(mimeType);
    return getSupportedSampleMimeTypes(trackType).contains(mimeType);
  }

  public ImmutableList<String> getSupportedSampleMimeTypes(@MediaUtil.TrackType int trackType) {
    return muxerFactory.getSupportedSampleMimeTypes(trackType);
  }

  public void addTrackFormat(Format format) throws Muxer.MuxerException {
    @Nullable String sampleMimeType = format.sampleMimeType;
    @MediaUtil.TrackType int trackType = MediaUtil.getTrackType(sampleMimeType);

    ensureMuxerInitialized();

    int trackIndex = muxer.addTrack(format);
    trackTypeToIndex.put(trackType, trackIndex);
    trackTypeToSampleCount.put(trackType, 0);
    trackTypeToTimeUs.put(trackType, 0L);
    trackTypeToBytesWritten.put(trackType, 0L);
    trackFormatCount++;
    if (trackFormatCount == trackCount) {
      isReady = true;
      resetAbortTimer();
    }
  }

  public boolean writeSample(
      @MediaUtil.TrackType int trackType, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws Muxer.MuxerException {
    int trackIndex = trackTypeToIndex.get(trackType, -1);

    if (!canWriteSampleOfType(trackType)) {
      return false;
    }

    trackTypeToSampleCount.put(trackType, trackTypeToSampleCount.get(trackType) + 1);
    trackTypeToBytesWritten.put(
        trackType, trackTypeToBytesWritten.get(trackType) + data.remaining());
    if (trackTypeToTimeUs.get(trackType) < presentationTimeUs) {
      trackTypeToTimeUs.put(trackType, presentationTimeUs);
    }

    resetAbortTimer();
    muxer.writeSampleData(trackIndex, data, isKeyFrame, presentationTimeUs);
    previousTrackType = trackType;
    return true;
  }

  public void endTrack(@MediaUtil.TrackType int trackType) {
    trackTypeToIndex.delete(trackType);
    if (trackTypeToIndex.size() == 0) {
      abortScheduledExecutorService.shutdownNow();
    }
  }

  public void release(boolean forCancellation) throws Muxer.MuxerException {
    isReady = false;
    abortScheduledExecutorService.shutdownNow();
    if (muxer != null) {
      muxer.release(forCancellation);
    }
  }

  public int getTrackCount() {
    return trackCount;
  }

  public int getTrackAverageBitrate(@MediaUtil.TrackType int trackType) {
    long trackDurationUs = trackTypeToTimeUs.get(trackType, /* valueIfKeyNotFound= */ -1);
    long trackBytes = trackTypeToBytesWritten.get(trackType, /* valueIfKeyNotFound= */ -1);
    if (trackDurationUs <= 0 || trackBytes <= 0) {
      return -1;
    }

    return (int) scaleLargeTimestamp(
            /* timestamp= */ trackBytes,
            /* multiplier= */ 8 * 1000000,
            /* divisor= */ trackDurationUs);
  }

  /** Returns the number of samples written to the track of the provided {@code trackType}. */
  public int getTrackSampleCount(@MediaUtil.TrackType int trackType) {
    return trackTypeToSampleCount.get(trackType, /* valueIfKeyNotFound= */ 0);
  }

  /** Returns the duration of the longest track in milliseconds. */
  public long getDurationMs() {
    return usToMs(maxValue(trackTypeToTimeUs));
  }

  private boolean canWriteSampleOfType(int trackType) {
    long trackTimeUs = trackTypeToTimeUs.get(trackType, Long.MIN_VALUE + 1);

    if (!isReady) {
      return false;
    }
    if (trackTypeToIndex.size() == 1) {
      return true;
    }
    if (trackType != previousTrackType) {
      minTrackTimeUs = minValue(trackTypeToTimeUs);
    }
    return trackTimeUs - minTrackTimeUs <= MAX_TRACK_WRITE_AHEAD_US;
  }

  @RequiresNonNull("muxer")
  private void resetAbortTimer() {
    long maxDelayBetweenSamplesMs = muxer.getMaxDelayBetweenSamplesMs();
    if (maxDelayBetweenSamplesMs == Long.MIN_VALUE + 1) {
      return;
    }
    if (abortScheduledFuture != null) {
      abortScheduledFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
    abortScheduledFuture =
        abortScheduledExecutorService.schedule(
            () -> {
              if (isAborted) {
                return;
              }
              isAborted = true;
              asyncErrorListener.onTransformationException(
                  TransformationException.createForMuxer(
                      new IllegalStateException(
                          "No output sample written in the last "
                              + maxDelayBetweenSamplesMs
                              + " milliseconds. Aborting transformation."),
                      TransformationException.ERROR_CODE_MUXING_FAILED));
            },
            maxDelayBetweenSamplesMs,
            MILLISECONDS);
  }

  @EnsuresNonNull("muxer")
  private void ensureMuxerInitialized() throws Muxer.MuxerException {
    if (muxer == null) {
      if (outputPath != null) {
        muxer = muxerFactory.create(outputPath);
      } else {
        muxer = muxerFactory.create(outputParcelFileDescriptor);
      }
    }
  }

  private static long minValue(SparseLongArray sparseLongArray) {
    if (sparseLongArray.size() == 0) {
      throw new NoSuchElementException();
    }
    long min = Long.MAX_VALUE;
    for (int i = 0; i < sparseLongArray.size(); i++) {
      min = min(min, sparseLongArray.valueAt(i));
    }
    return min;
  }

  private static long maxValue(SparseLongArray sparseLongArray) {
    if (sparseLongArray.size() == 0) {
      throw new NoSuchElementException();
    }
    long max = Long.MIN_VALUE;
    for (int i = 0; i < sparseLongArray.size(); i++) {
      max = max(max, sparseLongArray.valueAt(i));
    }
    return max;
  }

  private static long scaleLargeTimestamp(long timestamp, long multiplier, long divisor) {
    if (divisor >= multiplier && (divisor % multiplier) == 0) {
      long divisionFactor = divisor / multiplier;
      return timestamp / divisionFactor;
    } else if (divisor < multiplier && (multiplier % divisor) == 0) {
      long multiplicationFactor = multiplier / divisor;
      return timestamp * multiplicationFactor;
    } else {
      double multiplicationFactor = (double) multiplier / divisor;
      return (long) (timestamp * multiplicationFactor);
    }
  }

  private static long usToMs(long timeUs) {
    return (timeUs == Long.MIN_VALUE + 1 || timeUs == Long.MIN_VALUE) ? timeUs : (timeUs / 1000);
  }

  public static long msToUs(long timeMs) {
    return (timeMs == Long.MIN_VALUE + 1 || timeMs == Long.MIN_VALUE) ? timeMs : (timeMs * 1000);
  }

}
