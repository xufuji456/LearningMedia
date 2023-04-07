
package com.frank.videoedit.transform.impl;

import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
//import com.frank.videoedit.transform.entity.DecoderInputBuffer;
import com.frank.videoedit.transform.TransformationException;

public interface SamplePipeline {

  DecoderInputBuffer dequeueInputBuffer() throws TransformationException;

  void queueInputBuffer() throws TransformationException;

  boolean processData() throws TransformationException;

  boolean isEnded();

  void release();
}
