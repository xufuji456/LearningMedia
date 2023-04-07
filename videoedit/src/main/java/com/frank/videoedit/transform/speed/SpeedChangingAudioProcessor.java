
package com.frank.videoedit.transform.speed;

import static java.lang.Math.min;

import com.frank.videoedit.transform.impl.SpeedProvider;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.nio.ByteBuffer;

public final class SpeedChangingAudioProcessor extends BaseAudioProcessor {

  private final SpeedProvider speedProvider;

  private final SonicAudioProcessor sonicAudioProcessor;

  private float currentSpeed;
  private long bytesRead;
  private boolean endOfStreamQueuedToSonic;

  private static final long MICROS_PER_SECOND = 1000000L;

  public SpeedChangingAudioProcessor(SpeedProvider speedProvider) {
    this.speedProvider = speedProvider;
    sonicAudioProcessor = new SonicAudioProcessor();
    currentSpeed = 1f;
  }

  @Override
  @CanIgnoreReturnValue
  public AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    return sonicAudioProcessor.configure(inputAudioFormat);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    long timeUs = scaleLargeTimestamp(
            /* timestamp= */ bytesRead,
            /* multiplier= */ MICROS_PER_SECOND,
            /* divisor= */ (long) inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame);
    float newSpeed = speedProvider.getSpeed(timeUs);
    if (newSpeed != currentSpeed) {
      currentSpeed = newSpeed;
      if (isUsingSonic()) {
        sonicAudioProcessor.setSpeed(newSpeed);
        sonicAudioProcessor.setPitch(newSpeed);
      }
      flush();
    }

    int inputBufferLimit = inputBuffer.limit();
    long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(timeUs);
    int bytesToNextSpeedChange;
    if (nextSpeedChangeTimeUs != Long.MIN_VALUE + 1) {
      bytesToNextSpeedChange =
          (int) scaleLargeTimestamp(
                  /* timestamp= */ nextSpeedChangeTimeUs - timeUs,
                  /* multiplier= */ (long) inputAudioFormat.sampleRate
                      * inputAudioFormat.bytesPerFrame,
                  /* divisor= */ MICROS_PER_SECOND);
      int bytesToNextFrame =
          inputAudioFormat.bytesPerFrame - bytesToNextSpeedChange % inputAudioFormat.bytesPerFrame;
      if (bytesToNextFrame != inputAudioFormat.bytesPerFrame) {
        bytesToNextSpeedChange += bytesToNextFrame;
      }
      // Update the input buffer limit to make sure that all samples processed have the same speed.
      inputBuffer.limit(min(inputBufferLimit, inputBuffer.position() + bytesToNextSpeedChange));
    } else {
      bytesToNextSpeedChange = -1;
    }

    long startPosition = inputBuffer.position();
    if (isUsingSonic()) {
      sonicAudioProcessor.queueInput(inputBuffer);
      if (bytesToNextSpeedChange != -1
          && (inputBuffer.position() - startPosition) == bytesToNextSpeedChange) {
        sonicAudioProcessor.queueEndOfStream();
        endOfStreamQueuedToSonic = true;
      }
    } else {
      ByteBuffer buffer = replaceOutputBuffer(/* size= */ inputBuffer.remaining());
      buffer.put(inputBuffer);
      buffer.flip();
    }
    bytesRead += inputBuffer.position() - startPosition;
    inputBuffer.limit(inputBufferLimit);
  }

  @Override
  protected void onQueueEndOfStream() {
    if (!endOfStreamQueuedToSonic) {
      sonicAudioProcessor.queueEndOfStream();
      endOfStreamQueuedToSonic = true;
    }
  }

  @Override
  public ByteBuffer getOutput() {
    return isUsingSonic() ? sonicAudioProcessor.getOutput() : super.getOutput();
  }

  @Override
  public boolean isEnded() {
    return super.isEnded() && sonicAudioProcessor.isEnded();
  }

  @Override
  protected void onFlush() {
    sonicAudioProcessor.flush();
    endOfStreamQueuedToSonic = false;
  }

  @Override
  protected void onReset() {
    currentSpeed = 1f;
    bytesRead = 0;
    sonicAudioProcessor.reset();
    endOfStreamQueuedToSonic = false;
  }

  private boolean isUsingSonic() {
    return currentSpeed != 1f;
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

}
