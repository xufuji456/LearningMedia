
package com.frank.videoedit.transform.speed;

import androidx.annotation.CallSuper;

import com.frank.videoedit.transform.impl.AudioProcessor;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class BaseAudioProcessor implements AudioProcessor {

  protected AudioFormat inputAudioFormat;
  protected AudioFormat outputAudioFormat;

  private AudioFormat pendingInputAudioFormat;
  private AudioFormat pendingOutputAudioFormat;
  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private boolean inputEnded;

  public BaseAudioProcessor() {
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
  }

  @Override
  @CanIgnoreReturnValue
  public final AudioFormat configure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    pendingInputAudioFormat = inputAudioFormat;
    pendingOutputAudioFormat = onConfigure(inputAudioFormat);
    return isActive() ? pendingOutputAudioFormat : AudioFormat.NOT_SET;
  }

  @Override
  public boolean isActive() {
    return pendingOutputAudioFormat != AudioFormat.NOT_SET;
  }

  @Override
  public final void queueEndOfStream() {
    inputEnded = true;
    onQueueEndOfStream();
  }

  @CallSuper
  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @CallSuper
  @Override
  public boolean isEnded() {
    return inputEnded && outputBuffer == EMPTY_BUFFER;
  }

  @Override
  public final void flush() {
    outputBuffer = EMPTY_BUFFER;
    inputEnded = false;
    inputAudioFormat = pendingInputAudioFormat;
    outputAudioFormat = pendingOutputAudioFormat;
    onFlush();
  }

  @Override
  public final void reset() {
    flush();
    buffer = EMPTY_BUFFER;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    onReset();
  }

  protected final ByteBuffer replaceOutputBuffer(int size) {
    if (buffer.capacity() < size) {
      buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }
    outputBuffer = buffer;
    return buffer;
  }

  protected final boolean hasPendingOutput() {
    return outputBuffer.hasRemaining();
  }

  @CanIgnoreReturnValue
  protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    return AudioFormat.NOT_SET;
  }

  protected void onQueueEndOfStream() {
    // Do nothing.
  }

  protected void onFlush() {
    // Do nothing.
  }

  protected void onReset() {
    // Do nothing.
  }
}
