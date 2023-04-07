
package com.frank.videoedit.transform.util;

import static java.lang.Math.max;
import static java.lang.Math.round;

import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Pair;
import android.util.Range;
import android.util.Size;

import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.frank.videoedit.entity.ColorInfo;
import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.impl.EncoderSelector;

import com.google.common.base.Ascii;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;

/** Utility methods for {@link MediaCodec} encoders. */
public final class EncoderUtil {

  /** A value to indicate the encoding level is not set. */
  public static final int LEVEL_UNSET = Format.NO_VALUE;

  private static final Supplier<ImmutableListMultimap<String, MediaCodecInfo>>
      MIME_TYPE_TO_ENCODERS = Suppliers.memoize(EncoderUtil::populateEncoderInfos);

  /**
   * Returns a list of {@linkplain MediaCodecInfo encoders} that support the given {@code mimeType},
   * or an empty list if there is none.
   */
  public static ImmutableList<MediaCodecInfo> getSupportedEncoders(String mimeType) {
    return MIME_TYPE_TO_ENCODERS.get().get(Ascii.toLowerCase(mimeType));
  }

  public static ImmutableSet<String> getSupportedVideoMimeTypes() {
    return MIME_TYPE_TO_ENCODERS.get().keySet();
  }

  public static ImmutableList<String> getSupportedEncoderNamesForHdrEditing(
      String mimeType, @Nullable ColorInfo colorInfo) {
    if (Build.VERSION.SDK_INT < 31 || colorInfo == null) {
      return ImmutableList.of();
    }

    @ColorInfo.ColorTransfer int colorTransfer = colorInfo.colorTransfer;
    ImmutableList<Integer> profiles = getCodecProfilesForHdrFormat(mimeType, colorTransfer);
    ImmutableList.Builder<String> resultBuilder = ImmutableList.builder();
    ImmutableList<MediaCodecInfo> mediaCodecInfos =
        EncoderSelector.DEFAULT.selectEncoderInfos(mimeType);
    for (int i = 0; i < mediaCodecInfos.size(); i++) {
      MediaCodecInfo mediaCodecInfo = mediaCodecInfos.get(i);
      if (mediaCodecInfo.isAlias()
          || !isFeatureSupported(
              mediaCodecInfo, mimeType, MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing)) {
        continue;
      }
      for (MediaCodecInfo.CodecProfileLevel codecProfileLevel :
          mediaCodecInfo.getCapabilitiesForType(mimeType).profileLevels) {
        if (profiles.contains(codecProfileLevel.profile)) {
          resultBuilder.add(mediaCodecInfo.getName());
        }
      }
    }
    return resultBuilder.build();
  }

  @SuppressWarnings("InlinedApi")
  public static ImmutableList<Integer> getCodecProfilesForHdrFormat(
      String mimeType, @ColorInfo.ColorTransfer int colorTransfer) {

    switch (mimeType) {
      case MediaUtil.VIDEO_VP9:
        if (colorTransfer == ColorInfo.COLOR_TRANSFER_HLG || colorTransfer == ColorInfo.COLOR_TRANSFER_ST2084) {
          // Profiles support both HLG and PQ.
          return ImmutableList.of(
              MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR,
              MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR);
        }
        break;
      case MediaUtil.VIDEO_H264:
        if (colorTransfer == ColorInfo.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10);
        }
        // CodecProfileLevel does not support PQ/HDR10 for H264.
        break;
      case MediaUtil.VIDEO_H265:
        if (colorTransfer == ColorInfo.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10);
        } else if (colorTransfer == ColorInfo.COLOR_TRANSFER_ST2084) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10);
        }
        break;
      case MediaUtil.VIDEO_AV1:
        if (colorTransfer == ColorInfo.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10);
        } else if (colorTransfer == ColorInfo.COLOR_TRANSFER_ST2084) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10);
        }
        break;
      default:
        break;
    }
    // There are no profiles defined for the HDR format, or it's invalid.
    return ImmutableList.of();
  }

  /** Returns whether the {@linkplain MediaCodecInfo encoder} supports the given resolution. */
  public static boolean isSizeSupported(
      MediaCodecInfo encoderInfo, String mimeType, int width, int height) {
    if (encoderInfo
        .getCapabilitiesForType(mimeType)
        .getVideoCapabilities()
        .isSizeSupported(width, height)) {
      return true;
    }

    // Some devices (Samsung, Huawei, and Pixel 6. See b/222095724) under-report their encoding
    // capabilities. The supported height reported for H265@3840x2160 is 2144, and
    // H264@1920x1080 is 1072. See b/229825948.
    // Cross reference with CamcorderProfile to ensure a resolution is supported.
    if (width == 1920 && height == 1080) {
      return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P);
    }
    if (width == 3840 && height == 2160) {
      return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_2160P);
    }
    return false;
  }

  public static Range<Integer> getSupportedHeights(
      MediaCodecInfo encoderInfo, String mimeType, int width) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getVideoCapabilities()
        .getSupportedHeightsFor(width);
  }

  public static Pair<Range<Integer>, Range<Integer>> getSupportedResolutionRanges(
      MediaCodecInfo encoderInfo, String mimeType) {
    MediaCodecInfo.VideoCapabilities videoCapabilities =
        encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();
    return Pair.create(
        videoCapabilities.getSupportedWidths(), videoCapabilities.getSupportedHeights());
  }

  @Nullable
  public static Size getSupportedResolution(
      MediaCodecInfo encoderInfo, String mimeType, int width, int height) {
    MediaCodecInfo.VideoCapabilities videoEncoderCapabilities =
        encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();
    int widthAlignment = videoEncoderCapabilities.getWidthAlignment();
    int heightAlignment = videoEncoderCapabilities.getHeightAlignment();

    // Fix size alignment.
    width = alignResolution(width, widthAlignment);
    height = alignResolution(height, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, width, height)) {
      return new Size(width, height);
    }

    // Try three-fourths (e.g. 1440 -> 1080).
    int newWidth = alignResolution(width * 3 / 4, widthAlignment);
    int newHeight = alignResolution(height * 3 / 4, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Try two-thirds (e.g. 4k -> 1440).
    newWidth = alignResolution(width * 2 / 3, widthAlignment);
    newHeight = alignResolution(height * 2 / 3, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, width, height)) {
      return new Size(newWidth, newHeight);
    }

    // Try half (e.g. 4k -> 1080).
    newWidth = alignResolution(width / 2, widthAlignment);
    newHeight = alignResolution(height / 2, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Try one-third (e.g. 4k -> 720).
    newWidth = alignResolution(width / 3, widthAlignment);
    newHeight = alignResolution(height / 3, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Fix frame being too wide or too tall.
    width = videoEncoderCapabilities.getSupportedWidths().clamp(width);
    int adjustedHeight = videoEncoderCapabilities.getSupportedHeightsFor(width).clamp(height);
    if (adjustedHeight != height) {
      width =
          alignResolution((int) round((double) width * adjustedHeight / height), widthAlignment);
      height = alignResolution(adjustedHeight, heightAlignment);
    }

    return isSizeSupported(encoderInfo, mimeType, width, height) ? new Size(width, height) : null;
  }

  public static ImmutableSet<Integer> findSupportedEncodingProfiles(
      MediaCodecInfo encoderInfo, String mimeType) {
    MediaCodecInfo.CodecProfileLevel[] profileLevels =
        encoderInfo.getCapabilitiesForType(mimeType).profileLevels;
    ImmutableSet.Builder<Integer> supportedProfilesBuilder = new ImmutableSet.Builder<>();
    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
      supportedProfilesBuilder.add(profileLevel.profile);
    }
    return supportedProfilesBuilder.build();
  }

  public static int findHighestSupportedEncodingLevel(
      MediaCodecInfo encoderInfo, String mimeType, int profile) {

    MediaCodecInfo.CodecProfileLevel[] profileLevels =
        encoderInfo.getCapabilitiesForType(mimeType).profileLevels;

    int maxSupportedLevel = LEVEL_UNSET;
    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
      if (profileLevel.profile == profile) {
        maxSupportedLevel = max(maxSupportedLevel, profileLevel.level);
      }
    }
    return maxSupportedLevel;
  }

  @Nullable
  public static String findCodecForFormat(MediaFormat format, boolean isDecoder) {
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);

    float frameRate = Format.NO_VALUE;
    if (Build.VERSION.SDK_INT == 21 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      try {
        frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE);
      } catch (ClassCastException e) {
        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
      }
      // Clears the frame rate field.
      format.setString(MediaFormat.KEY_FRAME_RATE, null);
    }

    String mediaCodecName =
        isDecoder
            ? mediaCodecList.findDecoderForFormat(format)
            : mediaCodecList.findEncoderForFormat(format);

    if (Build.VERSION.SDK_INT == 21) {
      MediaUtil.maybeSetInteger(format, MediaFormat.KEY_FRAME_RATE, round(frameRate));
    }
    return mediaCodecName;
  }

  public static Range<Integer> getSupportedBitrateRange(
      MediaCodecInfo encoderInfo, String mimeType) {
    return encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities().getBitrateRange();
  }

  public static boolean isBitrateModeSupported(
      MediaCodecInfo encoderInfo, String mimeType, int bitrateMode) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getEncoderCapabilities()
        .isBitrateModeSupported(bitrateMode);
  }

  public static ImmutableList<Integer> getSupportedColorFormats(
      MediaCodecInfo encoderInfo, String mimeType) {
    return ImmutableList.copyOf(
        Ints.asList(encoderInfo.getCapabilitiesForType(mimeType).colorFormats));
  }

  public static boolean isHardwareAccelerated(MediaCodecInfo encoderInfo, String mimeType) {
    if (Build.VERSION.SDK_INT >= 29) {
      return Api29.isHardwareAccelerated(encoderInfo);
    }
    return !isSoftwareOnly(encoderInfo, mimeType);
  }

  public static boolean isFeatureSupported(
      MediaCodecInfo encoderInfo, String mimeType, String featureName) {
    return encoderInfo.getCapabilitiesForType(mimeType).isFeatureSupported(featureName);
  }

  @RequiresApi(23)
  public static int getMaxSupportedInstances(MediaCodecInfo encoderInfo, String mimeType) {
    return encoderInfo.getCapabilitiesForType(mimeType).getMaxSupportedInstances();
  }

  private static boolean isSoftwareOnly(MediaCodecInfo encoderInfo, String mimeType) {
    if (Build.VERSION.SDK_INT >= 29) {
      return Api29.isSoftwareOnly(encoderInfo);
    }

    if (MediaUtil.isAudio(mimeType)) {
      return true;
    }
    String codecName = Ascii.toLowerCase(encoderInfo.getName());
    if (codecName.startsWith("arc.")) {
      return false;
    }

    // Estimate whether a codec is software-only, to emulate isSoftwareOnly on API < 29.
    return codecName.startsWith("omx.google.")
        || codecName.startsWith("omx.ffmpeg.")
        || (codecName.startsWith("omx.sec.") && codecName.contains(".sw."))
        || codecName.equals("omx.qcom.video.decoder.hevcswvdec")
        || codecName.startsWith("c2.android.")
        || codecName.startsWith("c2.google.")
        || (!codecName.startsWith("omx.") && !codecName.startsWith("c2."));
  }

  private static int alignResolution(int size, int alignment) {
    // Aligning to resolutions that are multiples of 10, like from 1081 to 1080, assuming alignment
    // is 2 in most encoders.
    boolean shouldRoundDown = false;
    if (size % 10 == 1) {
      shouldRoundDown = true;
    }
    return shouldRoundDown
        ? (int) (alignment * Math.floor((float) size / alignment))
        : alignment * Math.round((float) size / alignment);
  }

  private static ImmutableListMultimap<String, MediaCodecInfo> populateEncoderInfos() {
    ImmutableListMultimap.Builder<String, MediaCodecInfo> encoderInfosBuilder =
        new ImmutableListMultimap.Builder<>();

    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    MediaCodecInfo[] allCodecInfos = mediaCodecList.getCodecInfos();

    for (MediaCodecInfo mediaCodecInfo : allCodecInfos) {
      if (!mediaCodecInfo.isEncoder()) {
        continue;
      }
      String[] supportedMimeTypes = mediaCodecInfo.getSupportedTypes();
      for (String mimeType : supportedMimeTypes) {
        if (MediaUtil.isVideo(mimeType)) {
          encoderInfosBuilder.put(Ascii.toLowerCase(mimeType), mediaCodecInfo);
        }
      }
    }
    return encoderInfosBuilder.build();
  }

  @RequiresApi(29)
  private static final class Api29 {
    @DoNotInline
    public static boolean isHardwareAccelerated(MediaCodecInfo encoderInfo) {
      return encoderInfo.isHardwareAccelerated();
    }

    @DoNotInline
    public static boolean isSoftwareOnly(MediaCodecInfo encoderInfo) {
      return encoderInfo.isSoftwareOnly();
    }
  }

  private EncoderUtil() {}
}
