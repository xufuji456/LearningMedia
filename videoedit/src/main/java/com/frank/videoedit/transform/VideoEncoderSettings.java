
package com.frank.videoedit.transform;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class VideoEncoderSettings {

  /** The default I-frame interval in seconds. */
  public static final float DEFAULT_I_FRAME_INTERVAL_SECONDS = 1.0f;

  /** A default {@link VideoEncoderSettings}. */
  public static final VideoEncoderSettings DEFAULT = new Builder().build();

  /**
   * The allowed values for {@code bitrateMode}.
   *
   * <ul>
   *   <li>Variable bitrate: {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_VBR}.
   *   <li>Constant bitrate: {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_CBR}.
   * </ul>
   */
  @SuppressLint("InlinedApi")
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    BITRATE_MODE_VBR,
    BITRATE_MODE_CBR,
  })
  public @interface BitrateMode {}

  public static final class Builder {
    private int bitrate;
    private @BitrateMode int bitrateMode;
    private int profile;
    private int level;
    private float iFrameIntervalSeconds;
    private int operatingRate;
    private int priority;
    private boolean enableHighQualityTargeting;

    public Builder() {
      this.bitrate = Format.NO_VALUE;
      this.bitrateMode = BITRATE_MODE_VBR;
      this.profile = Format.NO_VALUE;
      this.level = Format.NO_VALUE;
      this.iFrameIntervalSeconds = DEFAULT_I_FRAME_INTERVAL_SECONDS;
      this.operatingRate = Format.NO_VALUE;
      this.priority = Format.NO_VALUE;
    }

    private Builder(VideoEncoderSettings videoEncoderSettings) {
      this.bitrate = videoEncoderSettings.bitrate;
      this.bitrateMode = videoEncoderSettings.bitrateMode;
      this.profile = videoEncoderSettings.profile;
      this.level = videoEncoderSettings.level;
      this.iFrameIntervalSeconds = videoEncoderSettings.iFrameIntervalSeconds;
      this.operatingRate = videoEncoderSettings.operatingRate;
      this.priority = videoEncoderSettings.priority;
      this.enableHighQualityTargeting = videoEncoderSettings.enableHighQualityTargeting;
    }

    @CanIgnoreReturnValue
    public Builder setBitrate(int bitrate) {
      this.bitrate = bitrate;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setBitrateMode(@BitrateMode int bitrateMode) {
      if(bitrateMode != BITRATE_MODE_VBR && bitrateMode != BITRATE_MODE_CBR) {
        throw new IllegalArgumentException("not VBR or CBR, don't support!");
      }
      this.bitrateMode = bitrateMode;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setEncodingProfileLevel(int encodingProfile, int encodingLevel) {
      this.profile = encodingProfile;
      this.level = encodingLevel;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setiFrameIntervalSeconds(float iFrameIntervalSeconds) {
      this.iFrameIntervalSeconds = iFrameIntervalSeconds;
      return this;
    }

    @CanIgnoreReturnValue
    @VisibleForTesting
    public Builder setEncoderPerformanceParameters(int operatingRate, int priority) {
      this.operatingRate = operatingRate;
      this.priority = priority;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setEnableHighQualityTargeting(boolean enableHighQualityTargeting) {
      this.enableHighQualityTargeting = enableHighQualityTargeting;
      return this;
    }

    /** Builds the instance. */
    public VideoEncoderSettings build() {
      return new VideoEncoderSettings(
          bitrate,
          bitrateMode,
          profile,
          level,
          iFrameIntervalSeconds,
          operatingRate,
          priority,
          enableHighQualityTargeting);
    }
  }

  /** The encoding bitrate. */
  public final int bitrate;
  /** One of {@linkplain BitrateMode}. */
  public final @BitrateMode int bitrateMode;
  /** The encoding profile. */
  public final int profile;
  /** The encoding level. */
  public final int level;
  /** The encoding I-Frame interval in seconds. */
  public final float iFrameIntervalSeconds;
  /** The encoder {@link MediaFormat#KEY_OPERATING_RATE operating rate}. */
  public final int operatingRate;
  /** The encoder {@link MediaFormat#KEY_PRIORITY priority}. */
  public final int priority;
  /** Whether the encoder should automatically set the bitrate to target a high quality encoding. */
  public final boolean enableHighQualityTargeting;

  private VideoEncoderSettings(
      int bitrate,
      int bitrateMode,
      int profile,
      int level,
      float iFrameIntervalSeconds,
      int operatingRate,
      int priority,
      boolean enableHighQualityTargeting) {
    this.bitrate = bitrate;
    this.bitrateMode = bitrateMode;
    this.profile = profile;
    this.level = level;
    this.iFrameIntervalSeconds = iFrameIntervalSeconds;
    this.operatingRate = operatingRate;
    this.priority = priority;
    this.enableHighQualityTargeting = enableHighQualityTargeting;
  }

  /**
   * Returns a {@link Builder} initialized with the values of this instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VideoEncoderSettings)) {
      return false;
    }
    VideoEncoderSettings that = (VideoEncoderSettings) o;
    return bitrate == that.bitrate
        && bitrateMode == that.bitrateMode
        && profile == that.profile
        && level == that.level
        && iFrameIntervalSeconds == that.iFrameIntervalSeconds
        && operatingRate == that.operatingRate
        && priority == that.priority
        && enableHighQualityTargeting == that.enableHighQualityTargeting;
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + bitrate;
    result = 31 * result + bitrateMode;
    result = 31 * result + profile;
    result = 31 * result + level;
    result = 31 * result + Float.floatToIntBits(iFrameIntervalSeconds);
    result = 31 * result + operatingRate;
    result = 31 * result + priority;
    result = 31 * result + (enableHighQualityTargeting ? 1 : 0);
    return result;
  }
}
