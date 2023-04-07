
package com.frank.videoedit.transform.speed;

import androidx.annotation.Nullable;

import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.impl.AudioProcessor;
import com.frank.videoedit.transform.util.MediaUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * uses the Sonic library to modify audio speed/pitch/sample rate.
 */
public final class SonicAudioProcessor implements AudioProcessor {

  public static final int SAMPLE_RATE_NO_CHANGE = -1;

  private static final float CLOSE_THRESHOLD = 0.0001f;

  private static final int MIN_BYTES_FOR_DURATION_SCALING_CALCULATION = 1024;

  private int pendingOutputSampleRate;
  private float speed;
  private float pitch;

  private AudioFormat pendingInputAudioFormat;
  private AudioFormat pendingOutputAudioFormat;
  private AudioFormat inputAudioFormat;
  private AudioFormat outputAudioFormat;

  private boolean pendingSonicRecreation;
  private Sonic sonic;
  private ByteBuffer buffer;
  private ShortBuffer shortBuffer;
  private ByteBuffer outputBuffer;
  private long inputBytes;
  private long outputBytes;
  private boolean inputEnded;

  public SonicAudioProcessor() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
  }

  public void setSpeed(float speed) {
    if (this.speed != speed) {
      this.speed = speed;
      pendingSonicRecreation = true;
    }
  }

  public void setPitch(float pitch) {
    if (this.pitch != pitch) {
      this.pitch = pitch;
      pendingSonicRecreation = true;
    }
  }

  public void setOutputSampleRateHz(int sampleRateHz) {
    pendingOutputSampleRate = sampleRateHz;
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

  public long getMediaDuration(long playDuration) {
    if (outputBytes >= MIN_BYTES_FOR_DURATION_SCALING_CALCULATION) {
      long processedInputBytes = inputBytes - sonic.getPendingInputBytes();
      return outputAudioFormat.sampleRate == inputAudioFormat.sampleRate
          ? scaleLargeTimestamp(playDuration, processedInputBytes, outputBytes)
          : scaleLargeTimestamp(
              playDuration,
              processedInputBytes * outputAudioFormat.sampleRate,
              outputBytes * inputAudioFormat.sampleRate);
    } else {
      return (long) ((double) speed * playDuration);
    }
  }

  @Override
  @CanIgnoreReturnValue
  public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
    if (inputAudioFormat.encoding != MediaUtil.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    int outputSampleRateHz =
        pendingOutputSampleRate == SAMPLE_RATE_NO_CHANGE
            ? inputAudioFormat.sampleRate
            : pendingOutputSampleRate;
    pendingInputAudioFormat = inputAudioFormat;
    pendingOutputAudioFormat =
        new AudioFormat(outputSampleRateHz, inputAudioFormat.channelCount, MediaUtil.ENCODING_PCM_16BIT);
    pendingSonicRecreation = true;
    return pendingOutputAudioFormat;
  }

  @Override
  public boolean isActive() {
    return pendingOutputAudioFormat.sampleRate != Format.NO_VALUE
        && (Math.abs(speed - 1f) >= CLOSE_THRESHOLD
            || Math.abs(pitch - 1f) >= CLOSE_THRESHOLD
            || pendingOutputAudioFormat.sampleRate != pendingInputAudioFormat.sampleRate);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    if (!inputBuffer.hasRemaining()) {
      return;
    }
    Sonic sonic = this.sonic;
    ShortBuffer shortBuffer = inputBuffer.asShortBuffer();
    int inputSize = inputBuffer.remaining();
    inputBytes += inputSize;
    sonic.queueInput(shortBuffer);
    inputBuffer.position(inputBuffer.position() + inputSize);
  }

  @Override
  public void queueEndOfStream() {
    if (sonic != null) {
      sonic.queueEndOfStream();
    }
    inputEnded = true;
  }

  @Override
  public ByteBuffer getOutput() {
    @Nullable Sonic sonic = this.sonic;
    if (sonic != null) {
      int outputSize = sonic.getOutputSize();
      if (outputSize > 0) {
        if (buffer.capacity() < outputSize) {
          buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
          shortBuffer = buffer.asShortBuffer();
        } else {
          buffer.clear();
          shortBuffer.clear();
        }
        sonic.getOutput(shortBuffer);
        outputBytes += outputSize;
        buffer.limit(outputSize);
        outputBuffer = buffer;
      }
    }
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @Override
  public boolean isEnded() {
    return inputEnded && (sonic == null || sonic.getOutputSize() == 0);
  }

  @Override
  public void flush() {
    if (isActive()) {
      inputAudioFormat = pendingInputAudioFormat;
      outputAudioFormat = pendingOutputAudioFormat;
      if (pendingSonicRecreation) {
        sonic =
            new Sonic(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                speed,
                pitch,
                outputAudioFormat.sampleRate);
      } else if (sonic != null) {
        sonic.flush();
      }
    }
    outputBuffer = EMPTY_BUFFER;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

  @Override
  public void reset() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
    pendingSonicRecreation = false;
    sonic = null;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }
}
