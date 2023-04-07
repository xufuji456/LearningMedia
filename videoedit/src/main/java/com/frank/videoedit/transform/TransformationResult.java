
package com.frank.videoedit.transform;

import androidx.annotation.Nullable;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class TransformationResult {

  public static final class Builder {
    private long durationMs;
    private long fileSizeBytes;
    private int averageAudioBitrate;
    private int averageVideoBitrate;
    private int videoFrameCount;

    public Builder() {
      durationMs = Long.MIN_VALUE + 1;
      fileSizeBytes = -1;
      averageAudioBitrate = Integer.MIN_VALUE + 1;
      averageVideoBitrate = Integer.MIN_VALUE + 1;
    }

    @CanIgnoreReturnValue
    public Builder setDurationMs(long durationMs) {
      checkArgument(durationMs > 0 || durationMs == Long.MIN_VALUE + 1);
      this.durationMs = durationMs;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setFileSizeBytes(long fileSizeBytes) {
      checkArgument(fileSizeBytes > 0 || fileSizeBytes == -1);
      this.fileSizeBytes = fileSizeBytes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAverageAudioBitrate(int averageAudioBitrate) {
      checkArgument(averageAudioBitrate > 0 || averageAudioBitrate == Integer.MIN_VALUE + 1);
      this.averageAudioBitrate = averageAudioBitrate;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAverageVideoBitrate(int averageVideoBitrate) {
      checkArgument(averageVideoBitrate > 0 || averageVideoBitrate == Integer.MIN_VALUE + 1);
      this.averageVideoBitrate = averageVideoBitrate;
      return this;
    }

    /**
     * Sets the number of video frames.
     *
     * <p>Input must be positive or {@code 0}.
     */
    @CanIgnoreReturnValue
    public Builder setVideoFrameCount(int videoFrameCount) {
      checkArgument(videoFrameCount >= 0);
      this.videoFrameCount = videoFrameCount;
      return this;
    }

    public TransformationResult build() {
      return new TransformationResult(
          durationMs, fileSizeBytes, averageAudioBitrate, averageVideoBitrate, videoFrameCount);
    }
  }

  public final long durationMs;
  public final long fileSizeBytes;
  public final int averageAudioBitrate;
  public final int averageVideoBitrate;
  public final int videoFrameCount;

  private TransformationResult(
      long durationMs,
      long fileSizeBytes,
      int averageAudioBitrate,
      int averageVideoBitrate,
      int videoFrameCount) {
    this.durationMs = durationMs;
    this.fileSizeBytes = fileSizeBytes;
    this.averageAudioBitrate = averageAudioBitrate;
    this.averageVideoBitrate = averageVideoBitrate;
    this.videoFrameCount = videoFrameCount;
  }

  public Builder buildUpon() {
    return new Builder()
        .setDurationMs(durationMs)
        .setFileSizeBytes(fileSizeBytes)
        .setAverageAudioBitrate(averageAudioBitrate)
        .setAverageVideoBitrate(averageVideoBitrate)
        .setVideoFrameCount(videoFrameCount);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TransformationResult)) {
      return false;
    }
    TransformationResult result = (TransformationResult) o;
    return durationMs == result.durationMs
        && fileSizeBytes == result.fileSizeBytes
        && averageAudioBitrate == result.averageAudioBitrate
        && averageVideoBitrate == result.averageVideoBitrate
        && videoFrameCount == result.videoFrameCount;
  }

  @Override
  public int hashCode() {
    int result = (int) durationMs;
    result = 31 * result + (int) fileSizeBytes;
    result = 31 * result + averageAudioBitrate;
    result = 31 * result + averageVideoBitrate;
    result = 31 * result + videoFrameCount;
    return result;
  }

  private static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

}
