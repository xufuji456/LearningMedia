
package com.frank.videoedit.transform.impl;

import android.os.ParcelFileDescriptor;

import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.util.MediaUtil;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;

public interface Muxer {

  final class MuxerException extends Exception {

    public MuxerException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  interface Factory {

    Muxer create(String path) throws MuxerException;

    Muxer create(ParcelFileDescriptor parcelFileDescriptor) throws MuxerException;

    ImmutableList<String> getSupportedSampleMimeTypes(@MediaUtil.TrackType int trackType);
  }

  int addTrack(Format format) throws MuxerException;

  void writeSampleData(int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws MuxerException;

  void release(boolean forCancellation) throws MuxerException;

  long getMaxDelayBetweenSamplesMs();
}
