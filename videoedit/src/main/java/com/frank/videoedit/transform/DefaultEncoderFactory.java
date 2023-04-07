
package com.frank.videoedit.transform;

import static java.lang.Math.abs;
import static java.lang.Math.floor;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.util.Size;

import androidx.annotation.Nullable;

import com.frank.videoedit.entity.ColorInfo;
import com.frank.videoedit.transform.impl.Codec;
import com.frank.videoedit.transform.impl.EncoderSelector;
import com.frank.videoedit.transform.util.EncoderUtil;

import com.frank.videoedit.transform.util.MediaUtil;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DefaultEncoderFactory implements Codec.EncoderFactory {

  private static final int DEFAULT_FRAME_RATE = 30;

  private static final int PRIORITY_BEST_EFFORT = 1;

  private static final String TAG = "DefaultEncoderFactory";

  public static final class Builder {

    private final Context context;

    @Nullable private EncoderSelector encoderSelector;
    @Nullable private VideoEncoderSettings requestedVideoEncoderSettings;
    private boolean enableFallback;

    public Builder(Context context) {
      this.context = context;
      this.enableFallback = true;
    }

    @CanIgnoreReturnValue
    public Builder setVideoEncoderSelector(EncoderSelector encoderSelector) {
      this.encoderSelector = encoderSelector;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setRequestedVideoEncoderSettings(
        VideoEncoderSettings requestedVideoEncoderSettings) {
      this.requestedVideoEncoderSettings = requestedVideoEncoderSettings;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setEnableFallback(boolean enableFallback) {
      this.enableFallback = enableFallback;
      return this;
    }

    public DefaultEncoderFactory build() {
      if (encoderSelector == null) {
        encoderSelector = EncoderSelector.DEFAULT;
      }
      if (requestedVideoEncoderSettings == null) {
        requestedVideoEncoderSettings = VideoEncoderSettings.DEFAULT;
      }
      return new DefaultEncoderFactory(
          context, encoderSelector, requestedVideoEncoderSettings, enableFallback);
    }
  }

  private final Context context;
  private final EncoderSelector videoEncoderSelector;
  private final VideoEncoderSettings requestedVideoEncoderSettings;
  private final boolean enableFallback;

  @Deprecated
  @SuppressWarnings("deprecation")
  public DefaultEncoderFactory(Context context) {
    this(context, EncoderSelector.DEFAULT, /* enableFallback= */ true);
  }

  @Deprecated
  @SuppressWarnings("deprecation")
  public DefaultEncoderFactory(
      Context context, EncoderSelector videoEncoderSelector, boolean enableFallback) {
    this(context, videoEncoderSelector, VideoEncoderSettings.DEFAULT, enableFallback);
  }

  @Deprecated
  public DefaultEncoderFactory(
      Context context,
      EncoderSelector videoEncoderSelector,
      VideoEncoderSettings requestedVideoEncoderSettings,
      boolean enableFallback) {
    this.context = context;
    this.videoEncoderSelector = videoEncoderSelector;
    this.requestedVideoEncoderSettings = requestedVideoEncoderSettings;
    this.enableFallback = enableFallback;
  }

  @Override
  public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
      throws TransformationException {
    if (!allowedMimeTypes.contains(format.sampleMimeType)) {
      if (enableFallback) {
        format = format.buildUpon().setSampleMimeType(allowedMimeTypes.get(0)).build();
      } else {
        throw createTransformationException(format);
      }
    }
    MediaFormat mediaFormat =
        MediaFormat.createAudioFormat(format.sampleMimeType, format.sampleRate, format.channelCount);
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate);

    @Nullable
    String mediaCodecName = EncoderUtil.findCodecForFormat(mediaFormat, false);
    if (mediaCodecName == null) {
      throw createTransformationException(format);
    }
    return new DefaultCodec(context,
        format, mediaFormat, mediaCodecName, false, null);
  }

  @Override
  public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
      throws TransformationException {
    if (format.frameRate == Format.NO_VALUE) {
      format = format.buildUpon().setFrameRate(DEFAULT_FRAME_RATE).build();
    }

    @Nullable
    VideoEncoderQueryResult encoderAndClosestFormatSupport =
        findEncoderWithClosestSupportedFormat(
            format,
            requestedVideoEncoderSettings,
            videoEncoderSelector,
            allowedMimeTypes,
            enableFallback);

    if (encoderAndClosestFormatSupport == null) {
      throw createTransformationException(format);
    }

    MediaCodecInfo encoderInfo = encoderAndClosestFormatSupport.encoder;
    Format encoderSupportedFormat = encoderAndClosestFormatSupport.supportedFormat;
    VideoEncoderSettings supportedVideoEncoderSettings =
        encoderAndClosestFormatSupport.supportedEncoderSettings;

    String mimeType = encoderSupportedFormat.sampleMimeType;
    MediaFormat mediaFormat =
        MediaFormat.createVideoFormat(
            mimeType, encoderSupportedFormat.width, encoderSupportedFormat.height);
    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, Math.round(encoderSupportedFormat.frameRate));

    if (supportedVideoEncoderSettings.enableHighQualityTargeting) {
      int bitrate =
          new MappedEncoderBitrateProvider()
              .getBitrate(
                  encoderInfo.getName(),
                  encoderSupportedFormat.width,
                  encoderSupportedFormat.height,
                  encoderSupportedFormat.frameRate);
      encoderSupportedFormat =
          encoderSupportedFormat.buildUpon().setAverageBitrate(bitrate).build();
    } else if (encoderSupportedFormat.bitrate == Format.NO_VALUE) {
      int bitrate =
          getSuggestedBitrate(
              encoderSupportedFormat.width,
              encoderSupportedFormat.height,
              encoderSupportedFormat.frameRate);
      encoderSupportedFormat =
          encoderSupportedFormat.buildUpon().setAverageBitrate(bitrate).build();
    }

    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, encoderSupportedFormat.averageBitrate);
    mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, supportedVideoEncoderSettings.bitrateMode);

    if (supportedVideoEncoderSettings.profile != Format.NO_VALUE
        && supportedVideoEncoderSettings.level != Format.NO_VALUE
        && Build.VERSION.SDK_INT >= 23) {
      // Set profile and level at the same time to maximize compatibility, or the encoder will pick
      // the values.
      mediaFormat.setInteger(MediaFormat.KEY_PROFILE, supportedVideoEncoderSettings.profile);
      mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedVideoEncoderSettings.level);
    }

    if (mimeType != null && mimeType.equals(MediaUtil.VIDEO_H264)) {
      adjustMediaFormatForH264EncoderSettings(format.colorInfo, encoderInfo, mediaFormat);
    }

    MediaUtil.maybeSetColorInfo(mediaFormat, encoderSupportedFormat.colorInfo);
    if (Build.VERSION.SDK_INT >= 31 && ColorInfo.isTransferHdr(format.colorInfo)) {
      if (EncoderUtil.getSupportedColorFormats(encoderInfo, mimeType)
          .contains(MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010)) {
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010);
      } else {
        throw createTransformationException(format);
      }
    } else {
      mediaFormat.setInteger(
          MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    }

    if (Build.VERSION.SDK_INT >= 25) {
      mediaFormat.setFloat(
          MediaFormat.KEY_I_FRAME_INTERVAL, supportedVideoEncoderSettings.iFrameIntervalSeconds);
    } else {
      float iFrameIntervalSeconds = supportedVideoEncoderSettings.iFrameIntervalSeconds;
      mediaFormat.setInteger(
          MediaFormat.KEY_I_FRAME_INTERVAL,
          (iFrameIntervalSeconds > 0f && iFrameIntervalSeconds <= 1f)
              ? 1
              : (int) floor(iFrameIntervalSeconds));
    }

    if (Build.VERSION.SDK_INT >= 23) {
      if (supportedVideoEncoderSettings.operatingRate == Format.NO_VALUE
          && supportedVideoEncoderSettings.priority == Format.NO_VALUE) {
        adjustMediaFormatForEncoderPerformanceSettings(mediaFormat);
      } else {
        if (supportedVideoEncoderSettings.operatingRate != Format.NO_VALUE) {
          mediaFormat.setInteger(
              MediaFormat.KEY_OPERATING_RATE, supportedVideoEncoderSettings.operatingRate);
        }
        if (supportedVideoEncoderSettings.priority != Format.NO_VALUE) {
          mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, supportedVideoEncoderSettings.priority);
        }
      }
    }

    return new DefaultCodec(context, encoderSupportedFormat, mediaFormat, encoderInfo.getName(),
            false, null);
  }

  @Override
  public boolean videoNeedsEncoding() {
    return !requestedVideoEncoderSettings.equals(VideoEncoderSettings.DEFAULT);
  }

  @Nullable
  private static VideoEncoderQueryResult findEncoderWithClosestSupportedFormat(
      Format requestedFormat,
      VideoEncoderSettings videoEncoderSettings,
      EncoderSelector encoderSelector,
      List<String> allowedMimeTypes,
      boolean enableFallback) {
    String requestedMimeType = requestedFormat.sampleMimeType;
    @Nullable
    String mimeType = findFallbackMimeType(encoderSelector, requestedMimeType, allowedMimeTypes);
    if (mimeType == null || (!enableFallback && !requestedMimeType.equals(mimeType))) {
      return null;
    }

    ImmutableList<MediaCodecInfo> filteredEncoderInfos =
        encoderSelector.selectEncoderInfos(mimeType);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    if (!enableFallback) {
      return new VideoEncoderQueryResult(
          filteredEncoderInfos.get(0), requestedFormat, videoEncoderSettings);
    }

    filteredEncoderInfos =
        filterEncodersByResolution(
            filteredEncoderInfos, mimeType, requestedFormat.width, requestedFormat.height);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    Size finalResolution =
            EncoderUtil.getSupportedResolution(
                filteredEncoderInfos.get(0),
                mimeType,
                requestedFormat.width,
                requestedFormat.height);
    if (finalResolution == null)
      return null;

    int requestedBitrate =
        videoEncoderSettings.bitrate != Format.NO_VALUE
            ? videoEncoderSettings.bitrate
            : getSuggestedBitrate(
                finalResolution.getWidth(), finalResolution.getHeight(), requestedFormat.frameRate);

    filteredEncoderInfos =
        filterEncodersByBitrate(filteredEncoderInfos, mimeType, requestedBitrate);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    filteredEncoderInfos =
        filterEncodersByBitrateMode(
            filteredEncoderInfos, mimeType, videoEncoderSettings.bitrateMode);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    MediaCodecInfo pickedEncoderInfo = filteredEncoderInfos.get(0);
    int closestSupportedBitrate =
        EncoderUtil.getSupportedBitrateRange(pickedEncoderInfo, mimeType).clamp(requestedBitrate);

    VideoEncoderSettings.Builder supportedEncodingSettingBuilder = videoEncoderSettings.buildUpon();
    Format.Builder encoderSupportedFormatBuilder =
        requestedFormat
            .buildUpon()
            .setSampleMimeType(mimeType)
            .setWidth(finalResolution.getWidth())
            .setHeight(finalResolution.getHeight());

    if (!videoEncoderSettings.enableHighQualityTargeting) {
      supportedEncodingSettingBuilder.setBitrate(closestSupportedBitrate);
      encoderSupportedFormatBuilder.setAverageBitrate(closestSupportedBitrate);
    }

    if (videoEncoderSettings.profile == Format.NO_VALUE
        || videoEncoderSettings.level == Format.NO_VALUE
        || videoEncoderSettings.level
            > EncoderUtil.findHighestSupportedEncodingLevel(
                pickedEncoderInfo, mimeType, videoEncoderSettings.profile)) {
      supportedEncodingSettingBuilder.setEncodingProfileLevel(
          Format.NO_VALUE, Format.NO_VALUE);
    }

    return new VideoEncoderQueryResult(
        pickedEncoderInfo,
        encoderSupportedFormatBuilder.build(),
        supportedEncodingSettingBuilder.build());
  }

  /** Returns a list of encoders that support the requested resolution most closely. */
  private static ImmutableList<MediaCodecInfo> filterEncodersByResolution(
      List<MediaCodecInfo> encoders, String mimeType, int requestedWidth, int requestedHeight) {
    return filterEncoders(
        encoders,
        /* cost= */ (encoderInfo) -> {
          @Nullable
          Size closestSupportedResolution =
              EncoderUtil.getSupportedResolution(
                  encoderInfo, mimeType, requestedWidth, requestedHeight);
          if (closestSupportedResolution == null) {
            // Drops encoder.
            return Integer.MAX_VALUE;
          }
          return abs(
              requestedWidth * requestedHeight
                  - closestSupportedResolution.getWidth() * closestSupportedResolution.getHeight());
        },
        /* filterName= */ "resolution");
  }

  /** Returns a list of encoders that support the requested bitrate most closely. */
  private static ImmutableList<MediaCodecInfo> filterEncodersByBitrate(
      List<MediaCodecInfo> encoders, String mimeType, int requestedBitrate) {
    return filterEncoders(
        encoders,
        /* cost= */ (encoderInfo) -> {
          int achievableBitrate =
              EncoderUtil.getSupportedBitrateRange(encoderInfo, mimeType).clamp(requestedBitrate);
          return abs(achievableBitrate - requestedBitrate);
        },
        /* filterName= */ "bitrate");
  }

  /** Returns a list of encoders that support the requested bitrate mode. */
  private static ImmutableList<MediaCodecInfo> filterEncodersByBitrateMode(
      List<MediaCodecInfo> encoders, String mimeType, int requestedBitrateMode) {
    return filterEncoders(
        encoders,
        /* cost= */ (encoderInfo) ->
            EncoderUtil.isBitrateModeSupported(encoderInfo, mimeType, requestedBitrateMode)
                ? 0
                : Integer.MAX_VALUE, // Drops encoder.
        /* filterName= */ "bitrate mode");
  }

  private static final class VideoEncoderQueryResult {
    public final MediaCodecInfo encoder;
    public final Format supportedFormat;
    public final VideoEncoderSettings supportedEncoderSettings;

    public VideoEncoderQueryResult(
        MediaCodecInfo encoder,
        Format supportedFormat,
        VideoEncoderSettings supportedEncoderSettings) {
      this.encoder = encoder;
      this.supportedFormat = supportedFormat;
      this.supportedEncoderSettings = supportedEncoderSettings;
    }
  }

  private static void adjustMediaFormatForEncoderPerformanceSettings(MediaFormat mediaFormat) {
    if (Build.VERSION.SDK_INT < 25) {
      return;
    }

    mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, PRIORITY_BEST_EFFORT);

    if (Build.VERSION.SDK_INT == 26) {
      mediaFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, DEFAULT_FRAME_RATE);
    } else {
      mediaFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Integer.MAX_VALUE);
    }
  }
  private static void adjustMediaFormatForH264EncoderSettings(
      @Nullable ColorInfo colorInfo, MediaCodecInfo encoderInfo, MediaFormat mediaFormat) {
    String mimeType = MediaUtil.VIDEO_H264;
    if (Build.VERSION.SDK_INT >= 29) {
      int expectedEncodingProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
      if (colorInfo != null) {
        int colorTransfer = colorInfo.colorTransfer;
        ImmutableList<Integer> codecProfiles =
            EncoderUtil.getCodecProfilesForHdrFormat(mimeType, colorTransfer);
        if (!codecProfiles.isEmpty()) {
          expectedEncodingProfile = codecProfiles.get(0);
        }
      }
      int supportedEncodingLevel =
          EncoderUtil.findHighestSupportedEncodingLevel(
              encoderInfo, mimeType, expectedEncodingProfile);
      if (supportedEncodingLevel != EncoderUtil.LEVEL_UNSET) {
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, expectedEncodingProfile);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedEncodingLevel);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 1);
      }
    } else if (Build.VERSION.SDK_INT >= 26) {
      int expectedEncodingProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
      int supportedEncodingLevel =
          EncoderUtil.findHighestSupportedEncodingLevel(
              encoderInfo, mimeType, expectedEncodingProfile);
      if (supportedEncodingLevel != EncoderUtil.LEVEL_UNSET) {
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, expectedEncodingProfile);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedEncodingLevel);
        mediaFormat.setInteger(MediaFormat.KEY_LATENCY, 1);
      }
    } else if (Build.VERSION.SDK_INT >= 24) {
      int expectedEncodingProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
      int supportedLevel =
          EncoderUtil.findHighestSupportedEncodingLevel(
              encoderInfo, mimeType, expectedEncodingProfile);
      mediaFormat.setInteger(MediaFormat.KEY_PROFILE, expectedEncodingProfile);
      mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedLevel);
    }
  }

  private interface EncoderFallbackCost {
    int getParameterSupportGap(MediaCodecInfo encoderInfo);
  }

  private static ImmutableList<MediaCodecInfo> filterEncoders(
      List<MediaCodecInfo> encoders, EncoderFallbackCost cost, String filterName) {
    List<MediaCodecInfo> filteredEncoders = new ArrayList<>(encoders.size());

    int minGap = Integer.MAX_VALUE;
    for (int i = 0; i < encoders.size(); i++) {
      MediaCodecInfo encoderInfo = encoders.get(i);
      int gap = cost.getParameterSupportGap(encoderInfo);
      if (gap == Integer.MAX_VALUE) {
        continue;
      }

      if (gap < minGap) {
        minGap = gap;
        filteredEncoders.clear();
        filteredEncoders.add(encoderInfo);
      } else if (gap == minGap) {
        filteredEncoders.add(encoderInfo);
      }
    }

    List<MediaCodecInfo> removedEncoders = new ArrayList<>(encoders);
    removedEncoders.removeAll(filteredEncoders);
    StringBuilder stringBuilder =
        new StringBuilder("Encoders removed for ").append(filterName).append(":\n");
    for (int i = 0; i < removedEncoders.size(); i++) {
      MediaCodecInfo encoderInfo = removedEncoders.get(i);
      stringBuilder.append(String.format(Locale.getDefault(), "  %s\n", encoderInfo.getName()));
    }
    Log.i(TAG, stringBuilder.toString());

    return ImmutableList.copyOf(filteredEncoders);
  }

  @Nullable
  private static String findFallbackMimeType(
      EncoderSelector encoderSelector, String requestedMimeType, List<String> allowedMimeTypes) {
    if (mimeTypeIsSupported(encoderSelector, requestedMimeType, allowedMimeTypes)) {
      return requestedMimeType;
    } else if (mimeTypeIsSupported(encoderSelector, MediaUtil.VIDEO_H265, allowedMimeTypes)) {
      return MediaUtil.VIDEO_H265;
    } else if (mimeTypeIsSupported(encoderSelector, MediaUtil.VIDEO_H264, allowedMimeTypes)) {
      return MediaUtil.VIDEO_H264;
    } else {
      for (int i = 0; i < allowedMimeTypes.size(); i++) {
        String allowedMimeType = allowedMimeTypes.get(i);
        if (mimeTypeIsSupported(encoderSelector, allowedMimeType, allowedMimeTypes)) {
          return allowedMimeType;
        }
      }
    }
    return null;
  }

  private static boolean mimeTypeIsSupported(
      EncoderSelector encoderSelector, String mimeType, List<String> allowedMimeTypes) {
    return !encoderSelector.selectEncoderInfos(mimeType).isEmpty()
        && allowedMimeTypes.contains(mimeType);
  }

  /**
   * Computes the video bit rate using the Kush Gauge.
   *
   * <p>{@code kushGaugeBitrate = height * width * frameRate * 0.07 * motionFactor}.
   *
   * <p>Motion factors:
   *
   * <ul>
   *   <li>Low motion video - 1
   *   <li>Medium motion video - 2
   *   <li>High motion video - 4
   * </ul>
   */
  private static int getSuggestedBitrate(int width, int height, float frameRate) {
    // 1080p60 -> 16.6Mbps, 720p30 -> 3.7Mbps.
    return (int) (width * height * frameRate * 0.07 * 2);
  }

  private static TransformationException createTransformationException(Format format) {
    return TransformationException.createForCodec(
        new IllegalArgumentException("The requested encoding format is not supported."),
        MediaUtil.isVideo(format.sampleMimeType), false,
        format, null,
        TransformationException.ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED);
  }
}
