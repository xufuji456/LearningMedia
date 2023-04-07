
package com.frank.videoedit.transform.impl;

import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.util.MediaUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public interface AudioProcessor {

  final class AudioFormat {

    public static final AudioFormat NOT_SET =
        new AudioFormat(
            /* sampleRate= */ Format.NO_VALUE,
            /* channelCount= */ Format.NO_VALUE,
            /* encoding= */ Format.NO_VALUE);


    public final int sampleRate;

    public final int channelCount;

    public final @MediaUtil.PcmEncoding int encoding;

    public final int bytesPerFrame;

    public AudioFormat(int sampleRate, int channelCount, @MediaUtil.PcmEncoding int encoding) {
      this.sampleRate = sampleRate;
      this.channelCount = channelCount;
      this.encoding = encoding;
      bytesPerFrame =
              MediaUtil.isEncodingLinearPcm(encoding)
              ? MediaUtil.getPcmFrameSize(encoding, channelCount)
              : Format.NO_VALUE;
    }

  }

  final class UnhandledAudioFormatException extends Exception {

    public UnhandledAudioFormatException(AudioFormat inputAudioFormat) {
      super("Unhandled format: " + inputAudioFormat);
    }
  }

  ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  @CanIgnoreReturnValue
  AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException;

  boolean isActive();

  void queueInput(ByteBuffer inputBuffer);

  void queueEndOfStream();

  ByteBuffer getOutput();

  boolean isEnded();

  void flush();

  void reset();
}
