
package com.frank.videoedit.transform;

import androidx.annotation.Nullable;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class TransformationRequest {

  public static final class Builder {

    private boolean flattenForSlowMotion;
    private float scaleX;
    private float scaleY;
    private float rotationDegrees;
    private int outputHeight;
    private String audioMimeType;
    private String videoMimeType;
    private boolean enableRequestSdrToneMapping;
    private boolean forceInterpretHdrVideoAsSdr;
    private boolean enableHdrEditing;

    public Builder() {
      scaleX = 1;
      scaleY = 1;
      outputHeight = -1;
    }

    private Builder(TransformationRequest transformationRequest) {
      this.scaleX = transformationRequest.scaleX;
      this.scaleY = transformationRequest.scaleY;
      this.outputHeight     = transformationRequest.outputHeight;
      this.audioMimeType    = transformationRequest.audioMimeType;
      this.videoMimeType    = transformationRequest.videoMimeType;
      this.rotationDegrees  = transformationRequest.rotationDegrees;
      this.enableHdrEditing = transformationRequest.enableHdrEditing;
      this.flattenForSlowMotion        = transformationRequest.flattenForSlowMotion;
      this.enableRequestSdrToneMapping = transformationRequest.enableRequestSdrToneMapping;
      this.forceInterpretHdrVideoAsSdr = transformationRequest.forceInterpretHdrVideoAsSdr;
    }

    @CanIgnoreReturnValue
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      this.flattenForSlowMotion = flattenForSlowMotion;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setScale(float scaleX, float scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setRotationDegrees(float rotationDegrees) {
      this.rotationDegrees = rotationDegrees;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setResolution(int outputHeight) {
      this.outputHeight = outputHeight;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setVideoMimeType(@Nullable String videoMimeType) {
      this.videoMimeType = videoMimeType;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAudioMimeType(@Nullable String audioMimeType) {
      this.audioMimeType = audioMimeType;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setEnableRequestSdrToneMapping(boolean enableRequestSdrToneMapping) {
      this.enableRequestSdrToneMapping = enableRequestSdrToneMapping;
      if (enableRequestSdrToneMapping) {
        forceInterpretHdrVideoAsSdr = false;
        enableHdrEditing = false;
      }
      return this;
    }

    /**
     * Sets whether to interpret HDR video as SDR, resulting in washed out video.
     *
     * @param forceInterpretHdrVideoAsSdr Whether to interpret HDR contents as SDR.
     * @return This builder.
     */
    // TODO(http://b/258246130): Use IntDef to select between tone mapping, HDR editing, and this.
    @CanIgnoreReturnValue
    public Builder experimental_setForceInterpretHdrVideoAsSdr(
        boolean forceInterpretHdrVideoAsSdr) {
      this.forceInterpretHdrVideoAsSdr = forceInterpretHdrVideoAsSdr;
      if (forceInterpretHdrVideoAsSdr) {
        enableRequestSdrToneMapping = false;
        enableHdrEditing = false;
      }
      return this;
    }

    /**
     * Sets whether to allow processing high dynamic range (HDR) input video streams as HDR.
     *
     * <p>The default value is {@code false}, with {@link #setEnableRequestSdrToneMapping} being
     * applied.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release. The HDR
     * editing feature is under development and is intended for developing/testing HDR support.
     *
     * <p>Setting this as {@code true} will set {@linkplain #experimental_setEnableHdrEditing} and
     * {@linkplain #forceInterpretHdrVideoAsSdr} to {@code false}.
     *
     * <p>With this flag enabled, HDR streams will correctly edit in HDR, convert via tone-mapping
     * to SDR, or throw an error, based on the device's HDR support. SDR streams will be interpreted
     * the same way regardless of this flag's state.
     *
     * @param enableHdrEditing Whether to attempt to process any input video stream as a high
     *     dynamic range (HDR) signal.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder experimental_setEnableHdrEditing(boolean enableHdrEditing) {
      this.enableHdrEditing = enableHdrEditing;
      if (enableHdrEditing) {
        enableRequestSdrToneMapping = false;
        forceInterpretHdrVideoAsSdr = false;
      }
      return this;
    }

    /** Builds a {@link TransformationRequest} instance. */
    public TransformationRequest build() {
      return new TransformationRequest(
          flattenForSlowMotion,
          scaleX,
          scaleY,
          rotationDegrees,
          outputHeight,
          audioMimeType,
          videoMimeType,
          enableRequestSdrToneMapping,
          forceInterpretHdrVideoAsSdr,
          enableHdrEditing);
    }
  }

  public final boolean flattenForSlowMotion;

  public final float scaleX;

  public final float scaleY;

  public final float rotationDegrees;

  public final int outputHeight;

  public final String audioMimeType;

  public final String videoMimeType;
  /** Whether to request tone-mapping to standard dynamic range (SDR). */
  public final boolean enableRequestSdrToneMapping;

  /** Whether to force interpreting HDR video as SDR. */
  public final boolean forceInterpretHdrVideoAsSdr;

  public final boolean enableHdrEditing;

  private static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  private TransformationRequest(
      boolean flattenForSlowMotion,
      float scaleX,
      float scaleY,
      float rotationDegrees,
      int outputHeight,
      @Nullable String audioMimeType,
      @Nullable String videoMimeType,
      boolean enableRequestSdrToneMapping,
      boolean forceInterpretHdrVideoAsSdr,
      boolean enableHdrEditing) {
    checkArgument(!forceInterpretHdrVideoAsSdr || !enableRequestSdrToneMapping);
    checkArgument(!enableHdrEditing || !forceInterpretHdrVideoAsSdr);
    checkArgument(!enableHdrEditing || !enableRequestSdrToneMapping);

    this.flattenForSlowMotion = flattenForSlowMotion;
    this.scaleX = scaleX;
    this.scaleY = scaleY;
    this.rotationDegrees = rotationDegrees;
    this.outputHeight = outputHeight;
    this.audioMimeType = audioMimeType;
    this.videoMimeType = videoMimeType;
    this.enableRequestSdrToneMapping = enableRequestSdrToneMapping;
    this.forceInterpretHdrVideoAsSdr = forceInterpretHdrVideoAsSdr;
    this.enableHdrEditing = enableHdrEditing;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TransformationRequest)) {
      return false;
    }
    TransformationRequest that = (TransformationRequest) o;
    return flattenForSlowMotion == that.flattenForSlowMotion
        && scaleX == that.scaleX
        && scaleY == that.scaleY
        && rotationDegrees == that.rotationDegrees
        && outputHeight == that.outputHeight
        && audioMimeType != null && audioMimeType.equals(that.audioMimeType)
        && videoMimeType != null && videoMimeType.equals(that.videoMimeType)
        && enableRequestSdrToneMapping == that.enableRequestSdrToneMapping
        && forceInterpretHdrVideoAsSdr == that.forceInterpretHdrVideoAsSdr
        && enableHdrEditing == that.enableHdrEditing;
  }

  @Override
  public int hashCode() {
    int result = (flattenForSlowMotion ? 1 : 0);
    result = 31 * result + Float.floatToIntBits(scaleX);
    result = 31 * result + Float.floatToIntBits(scaleY);
    result = 31 * result + Float.floatToIntBits(rotationDegrees);
    result = 31 * result + outputHeight;
    result = 31 * result + (audioMimeType != null ? audioMimeType.hashCode() : 0);
    result = 31 * result + (videoMimeType != null ? videoMimeType.hashCode() : 0);
    result = 31 * result + (enableRequestSdrToneMapping ? 1 : 0);
    result = 31 * result + (forceInterpretHdrVideoAsSdr ? 1 : 0);
    result = 31 * result + (enableHdrEditing ? 1 : 0);
    return result;
  }

  public Builder buildUpon() {
    return new Builder(this);
  }
}
