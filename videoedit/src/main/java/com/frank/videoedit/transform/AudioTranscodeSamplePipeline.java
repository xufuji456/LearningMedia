
package com.frank.videoedit.transform;

import static java.lang.Math.min;

import androidx.annotation.Nullable;

import com.frank.videoedit.transform.entity.Buffer;
//import com.frank.videoedit.transform.entity.DecoderInputBuffer;
import com.frank.videoedit.transform.impl.AudioProcessor;
import com.frank.videoedit.transform.impl.AudioProcessor.AudioFormat;
import com.frank.videoedit.transform.impl.Codec;
import com.frank.videoedit.transform.speed.SegmentSpeedProvider;
import com.frank.videoedit.transform.speed.SpeedChangingAudioProcessor;
import com.frank.videoedit.transform.util.MediaUtil;

import org.checkerframework.dataflow.qual.Pure;

import java.nio.ByteBuffer;

import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

public final class AudioTranscodeSamplePipeline extends BaseSamplePipeline {

  private static final int DEFAULT_ENCODER_BITRATE = 128 * 1024;
  private static final long MICROS_PER_SECOND = 1000000L;

  private final Codec decoder;
  private final DecoderInputBuffer decoderInputBuffer;

  private final SpeedChangingAudioProcessor speedChangingAudioProcessor;

  private final Codec encoder;
  private final AudioFormat encoderInputAudioFormat;
  private final DecoderInputBuffer encoderInputBuffer;
  private final DecoderInputBuffer encoderOutputBuffer;

  private ByteBuffer processorOutputBuffer;

  private long nextEncoderInputBufferTimeUs;
  private long encoderBufferDurationRemainder;

  public AudioTranscodeSamplePipeline(
      Format inputFormat,
      long streamOffsetUs,
      long streamStartPositionUs,
      TransformationRequest transformationRequest,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper,
      FallbackListener fallbackListener)
      throws TransformationException {
    super(
        inputFormat,
        streamOffsetUs,
        streamStartPositionUs,
        transformationRequest.flattenForSlowMotion,
        muxerWrapper);

    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);

    decoder = decoderFactory.createForAudioDecoding(inputFormat);

    AudioFormat encoderInputAudioFormat =
        new AudioFormat(
            inputFormat.sampleRate,
            inputFormat.channelCount,
            MediaUtil.ENCODING_PCM_16BIT);
    if (transformationRequest.flattenForSlowMotion) {
      speedChangingAudioProcessor =
          new SpeedChangingAudioProcessor(new SegmentSpeedProvider(inputFormat));
      try {
        encoderInputAudioFormat = speedChangingAudioProcessor.configure(encoderInputAudioFormat);
      } catch (AudioProcessor.UnhandledAudioFormatException impossible) {
        throw new IllegalStateException(impossible);
      }
      speedChangingAudioProcessor.flush();
    } else {
      speedChangingAudioProcessor = null;
    }
    processorOutputBuffer = AudioProcessor.EMPTY_BUFFER;

    this.encoderInputAudioFormat = encoderInputAudioFormat;
    Format requestedOutputFormat =
        new Format.Builder()
            .setSampleMimeType(
                transformationRequest.audioMimeType == null
                    ? inputFormat.sampleMimeType
                    : transformationRequest.audioMimeType)
            .setSampleRate(encoderInputAudioFormat.sampleRate)
            .setChannelCount(encoderInputAudioFormat.channelCount)
            .setAverageBitrate(DEFAULT_ENCODER_BITRATE)
            .build();
    encoder =
        encoderFactory.createForAudioEncoding(
            requestedOutputFormat, muxerWrapper.getSupportedSampleMimeTypes(MediaUtil.TRACK_TYPE_AUDIO));

    fallbackListener.onTransformationRequestFinalized(
        createFallbackTransformationRequest(
            transformationRequest, requestedOutputFormat, encoder.getConfigurationFormat()));

    nextEncoderInputBufferTimeUs = streamOffsetUs;
  }

  @Override
  public void release() {
    if (speedChangingAudioProcessor != null) {
      speedChangingAudioProcessor.reset();
    }
    decoder.release();
    encoder.release();
  }

  @Override
  @Nullable
  protected DecoderInputBuffer dequeueInputBufferInternal() throws TransformationException {
    return decoder.maybeDequeueInputBuffer(decoderInputBuffer) ? decoderInputBuffer : null;
  }

  @Override
  protected void queueInputBufferInternal() throws TransformationException {
    decoder.queueInputBuffer(decoderInputBuffer);
  }

  @Override
  protected boolean processDataUpToMuxer() throws TransformationException {
    if (speedChangingAudioProcessor != null) {
      return feedEncoderFromProcessor() || feedProcessorFromDecoder();
    } else {
      return feedEncoderFromDecoder();
    }
  }

  @Override
  @Nullable
  protected Format getMuxerInputFormat() throws TransformationException {
    return encoder.getOutputFormat();
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() throws TransformationException {
    encoderOutputBuffer.data = encoder.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    encoderOutputBuffer.timeUs = encoder.getOutputBufferInfo().presentationTimeUs;
    encoderOutputBuffer.setFlags(Buffer.BUFFER_FLAG_KEY_FRAME);
    return encoderOutputBuffer;
  }

  @Override
  protected void releaseMuxerInputBuffer() throws TransformationException {
    encoder.releaseOutputBuffer(/* render= */ false);
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return encoder.isEnded();
  }

  private boolean feedEncoderFromDecoder() throws TransformationException {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (decoder.isEnded()) {
      queueEndOfStreamToEncoder();
      return false;
    }

    @Nullable ByteBuffer decoderOutputBuffer = decoder.getOutputBuffer();
    if (decoderOutputBuffer == null) {
      return false;
    }

    feedEncoder(decoderOutputBuffer);
    if (!decoderOutputBuffer.hasRemaining()) {
      decoder.releaseOutputBuffer(/* render= */ false);
    }
    return true;
  }

  private boolean feedEncoderFromProcessor() throws TransformationException {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (!processorOutputBuffer.hasRemaining()) {
      processorOutputBuffer = speedChangingAudioProcessor.getOutput();
      if (!processorOutputBuffer.hasRemaining()) {
        if (decoder.isEnded() && speedChangingAudioProcessor.isEnded()) {
          queueEndOfStreamToEncoder();
        }
        return false;
      }
    }

    feedEncoder(processorOutputBuffer);
    return true;
  }

  private boolean feedProcessorFromDecoder() throws TransformationException {
    if (processorOutputBuffer.hasRemaining()
        || speedChangingAudioProcessor.getOutput().hasRemaining()) {
      return false;
    }

    if (decoder.isEnded()) {
      speedChangingAudioProcessor.queueEndOfStream();
      return false;
    }

    @Nullable ByteBuffer decoderOutputBuffer = decoder.getOutputBuffer();
    if (decoderOutputBuffer == null) {
      return false;
    }

    speedChangingAudioProcessor.queueInput(decoderOutputBuffer);
    if (!decoderOutputBuffer.hasRemaining()) {
      decoder.releaseOutputBuffer(/* render= */ false);
    }
    return true;
  }

  private void feedEncoder(ByteBuffer inputBuffer) throws TransformationException {
    ByteBuffer encoderInputBufferData = encoderInputBuffer.data;
    int bufferLimit = inputBuffer.limit();
    inputBuffer.limit(min(bufferLimit, inputBuffer.position() + encoderInputBufferData.capacity()));
    encoderInputBufferData.put(inputBuffer);
    encoderInputBuffer.timeUs = nextEncoderInputBufferTimeUs;
    computeNextEncoderInputBufferTimeUs(
        encoderInputBufferData.position(),
        encoderInputAudioFormat.bytesPerFrame,
        encoderInputAudioFormat.sampleRate);
    encoderInputBuffer.setFlags(0);
    encoderInputBuffer.flip();
    inputBuffer.limit(bufferLimit);
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  private void queueEndOfStreamToEncoder() throws TransformationException {
    encoderInputBuffer.timeUs = nextEncoderInputBufferTimeUs;
    encoderInputBuffer.addFlag(Buffer.BUFFER_FLAG_END_OF_STREAM);
    encoderInputBuffer.flip();
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  private void computeNextEncoderInputBufferTimeUs(
      long bytesWritten, int bytesPerFrame, int sampleRate) {

    long numerator = bytesWritten * MICROS_PER_SECOND + encoderBufferDurationRemainder;
    long denominator = (long) bytesPerFrame * sampleRate;
    long bufferDurationUs = numerator / denominator;
    encoderBufferDurationRemainder = numerator - bufferDurationUs * denominator;
    if (encoderBufferDurationRemainder > 0) {
      bufferDurationUs += 1;
      encoderBufferDurationRemainder -= denominator;
    }
    nextEncoderInputBufferTimeUs += bufferDurationUs;
  }

  @Pure
  private static TransformationRequest createFallbackTransformationRequest(
      TransformationRequest transformationRequest, Format requestedFormat, Format actualFormat) {
    if (requestedFormat.sampleMimeType != null &&
            requestedFormat.sampleMimeType.equals(actualFormat.sampleMimeType)) {
      return transformationRequest;
    }
    return transformationRequest.buildUpon().setAudioMimeType(actualFormat.sampleMimeType).build();
  }
}
