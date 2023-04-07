
package com.frank.videoedit.transform;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaFormat;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.frank.videoedit.transform.clock.Clock;
import com.google.common.collect.ImmutableBiMap;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


public final class TransformationException extends Exception {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      open = true,
      value = {
        ERROR_CODE_UNSPECIFIED,
        ERROR_CODE_FAILED_RUNTIME_CHECK,
        ERROR_CODE_IO_UNSPECIFIED,
        ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        ERROR_CODE_IO_BAD_HTTP_STATUS,
        ERROR_CODE_IO_FILE_NOT_FOUND,
        ERROR_CODE_IO_NO_PERMISSION,
        ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        ERROR_CODE_DECODER_INIT_FAILED,
        ERROR_CODE_DECODING_FAILED,
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_ENCODER_INIT_FAILED,
        ERROR_CODE_ENCODING_FAILED,
        ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED,
        ERROR_CODE_HDR_ENCODING_UNSUPPORTED,
        ERROR_CODE_FRAME_PROCESSING_FAILED,
        ERROR_CODE_MUXING_FAILED,
      })
  public @interface ErrorCode {}

  /** Caused by an error whose cause could not be identified. */
  public static final int ERROR_CODE_UNSPECIFIED = 1000;
  /** Caused by a failed runtime check. */
  public static final int ERROR_CODE_FAILED_RUNTIME_CHECK = 1001;

  /** Caused by an Input/Output error which could not be identified. */
  public static final int ERROR_CODE_IO_UNSPECIFIED = 2000;
  /** Caused by a network connection failure. */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001;
  /** Caused by a network timeout, meaning the server is taking too long to fulfill a request. */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002;
  /** Caused by a server returning a resource with an invalid "Content-Type" HTTP header value. */
  public static final int ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;
  /** Caused by an HTTP server returning an unexpected HTTP response status code. */
  public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004;
  /** Caused by a non-existent file. */
  public static final int ERROR_CODE_IO_FILE_NOT_FOUND = 2005;
  /** Caused by lack of permission to perform an IO operation. */
  public static final int ERROR_CODE_IO_NO_PERMISSION = 2006;
  /** Caused by the player trying to access cleartext HTTP traffic */
  public static final int ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007;
  /** Caused by reading data out of the data bound. */
  public static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008;

  /** Caused by a decoder initialization failure. */
  public static final int ERROR_CODE_DECODER_INIT_FAILED = 3001;
  /** Caused by a failure while trying to decode media samples. */
  public static final int ERROR_CODE_DECODING_FAILED = 3002;
  /** Caused by trying to decode content whose format is not supported. */
  public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 3003;
  /** Caused by the decoder not supporting HDR formats. */
  public static final int ERROR_CODE_HDR_DECODING_UNSUPPORTED = 3004;

  /** Caused by an encoder initialization failure. */
  public static final int ERROR_CODE_ENCODER_INIT_FAILED = 4001;
  /** Caused by a failure while trying to encode media samples. */
  public static final int ERROR_CODE_ENCODING_FAILED = 4002;
  /** Caused by the output format for a track not being supported. */
  public static final int ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED = 4003;
  /** Caused by the encoder not supporting HDR formats. */
  public static final int ERROR_CODE_HDR_ENCODING_UNSUPPORTED = 4004;

  /** Caused by a frame processing failure. */
  public static final int ERROR_CODE_FRAME_PROCESSING_FAILED = 5001;

  /** Caused by a failure while muxing media samples. */
  public static final int ERROR_CODE_MUXING_FAILED = 6001;

  private static final ImmutableBiMap<String, @ErrorCode Integer> NAME_TO_ERROR_CODE =
      new ImmutableBiMap.Builder<String, @ErrorCode Integer>()
          .put("ERROR_CODE_FAILED_RUNTIME_CHECK", ERROR_CODE_FAILED_RUNTIME_CHECK)
          .put("ERROR_CODE_IO_UNSPECIFIED", ERROR_CODE_IO_UNSPECIFIED)
          .put("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
          .put("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT", ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
          .put("ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE", ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE)
          .put("ERROR_CODE_IO_BAD_HTTP_STATUS", ERROR_CODE_IO_BAD_HTTP_STATUS)
          .put("ERROR_CODE_IO_FILE_NOT_FOUND", ERROR_CODE_IO_FILE_NOT_FOUND)
          .put("ERROR_CODE_IO_NO_PERMISSION", ERROR_CODE_IO_NO_PERMISSION)
          .put("ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED", ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED)
          .put("ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE", ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
          .put("ERROR_CODE_DECODER_INIT_FAILED", ERROR_CODE_DECODER_INIT_FAILED)
          .put("ERROR_CODE_DECODING_FAILED", ERROR_CODE_DECODING_FAILED)
          .put("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED", ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
          .put("ERROR_CODE_HDR_DECODING_UNSUPPORTED", ERROR_CODE_HDR_DECODING_UNSUPPORTED)
          .put("ERROR_CODE_ENCODER_INIT_FAILED", ERROR_CODE_ENCODER_INIT_FAILED)
          .put("ERROR_CODE_ENCODING_FAILED", ERROR_CODE_ENCODING_FAILED)
          .put("ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED", ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED)
          .put("ERROR_CODE_HDR_ENCODING_UNSUPPORTED", ERROR_CODE_HDR_ENCODING_UNSUPPORTED)
          .put("ERROR_CODE_FRAME_PROCESSING_FAILED", ERROR_CODE_FRAME_PROCESSING_FAILED)
          .put("ERROR_CODE_MUXING_FAILED", ERROR_CODE_MUXING_FAILED)
          .buildOrThrow();


  private static @ErrorCode int getErrorCodeForName(String errorCodeName) {
    return NAME_TO_ERROR_CODE.getOrDefault(errorCodeName, ERROR_CODE_UNSPECIFIED);
  }

  public static String getErrorCodeName(@ErrorCode int errorCode) {
    return NAME_TO_ERROR_CODE.inverse().getOrDefault(errorCode, "invalid error code");
  }

  public String getErrorCodeName() {
    return getErrorCodeName(errorCode);
  }

  public static TransformationException createForCodec(
      Throwable cause,
      boolean isVideo,
      boolean isDecoder,
      MediaFormat mediaFormat,
      @Nullable String mediaCodecName,
      int errorCode) {
    String componentName = (isVideo ? "Video" : "Audio") + (isDecoder ? "Decoder" : "Encoder");
    String errorMessage =
        componentName + ", mediaFormat=" + mediaFormat + ", mediaCodecName=" + mediaCodecName;
    return new TransformationException(errorMessage, cause, errorCode);
  }

  public static TransformationException createForCodec(
      Throwable cause,
      boolean isVideo,
      boolean isDecoder,
      Format format,
      @Nullable String mediaCodecName,
      int errorCode) {
    String componentName = (isVideo ? "Video" : "Audio") + (isDecoder ? "Decoder" : "Encoder");
    String errorMessage =
        componentName + " error, format=" + format + ", mediaCodecName=" + mediaCodecName;
    return new TransformationException(errorMessage, cause, errorCode);
  }

//  public static TransformationException createForAudioProcessor(
//      Throwable cause, String componentName, AudioFormat audioFormat, int errorCode) {
//    return new TransformationException(
//        componentName + " error, audio_format = " + audioFormat, cause, errorCode);
//  }

  /* package */ static TransformationException createForFrameProcessingException(
      RuntimeException cause, int errorCode) {
    return new TransformationException("Frame processing error", cause, errorCode);
  }

  /* package */ static TransformationException createForMuxer(Throwable cause, int errorCode) {
    return new TransformationException("Muxer error", cause, errorCode);
  }

  public static TransformationException createForUnexpected(Exception cause) {
    if (cause instanceof RuntimeException) {
      return new TransformationException(
          "Unexpected runtime error", cause, ERROR_CODE_FAILED_RUNTIME_CHECK);
    }
    return new TransformationException("Unexpected error", cause, ERROR_CODE_UNSPECIFIED);
  }

  public static TransformationException createForPlaybackException(
      RuntimeException exception) {
    return new TransformationException(exception.getMessage(), exception, 99999);
  }

  public final @ErrorCode int errorCode;

  public final long timestampMs;

  private TransformationException(
      @Nullable String message, @Nullable Throwable cause, @ErrorCode int errorCode) {
    super(message, cause);
    this.errorCode = errorCode;
    this.timestampMs = Clock.DEFAULT.elapsedRealtime();
  }

//  public boolean errorInfoEquals(@Nullable TransformationException other) {
//    if (this == other) {
//      return true;
//    }
//    if (other == null || getClass() != other.getClass()) {
//      return false;
//    }
//
//    @Nullable Throwable thisCause = getCause();
//    @Nullable Throwable thatCause = other.getCause();
//    if (thisCause != null && thatCause != null) {
//      if (!thisCause.getMessage().equals(thatCause.getMessage())) {
//        return false;
//      }
//      if (!thisCause.getClass().equals(thatCause.getClass())) {
//        return false;
//      }
//    } else if (thisCause != null || thatCause != null) {
//      return false;
//    }
//    return errorCode == other.errorCode
//        && getMessage().equals(other.getMessage())
//        && timestampMs == other.timestampMs;
//  }
}
