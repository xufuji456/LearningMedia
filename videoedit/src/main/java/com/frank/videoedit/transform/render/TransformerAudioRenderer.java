
package com.frank.videoedit.transform.render;

import com.frank.videoedit.transform.AudioTranscodeSamplePipeline;
//import com.frank.videoedit.transform.entity.DecoderInputBuffer;
import com.frank.videoedit.transform.FallbackListener;
import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.MuxerWrapper;
import com.frank.videoedit.transform.PassthroughSamplePipeline;
import com.frank.videoedit.transform.TransformationException;
import com.frank.videoedit.transform.TransformationRequest;
import com.frank.videoedit.transform.Transformer;
import com.frank.videoedit.transform.TransformerMediaClock;
import com.frank.videoedit.transform.impl.Codec;
import com.frank.videoedit.transform.util.MediaUtil;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

public final class TransformerAudioRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TAudioRenderer";

  private final Codec.EncoderFactory encoderFactory;
  private final Codec.DecoderFactory decoderFactory;
  private final DecoderInputBuffer decoderInputBuffer;
  private static final int RESULT_FORMAT_READ = -5;

//  private final static int FLAG_REQUIRE_FORMAT = 1 << 1;

  public TransformerAudioRenderer(
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      Codec.DecoderFactory decoderFactory,
      Transformer.AsyncErrorListener asyncErrorListener,
      FallbackListener fallbackListener) {
    super(
        MediaUtil.TRACK_TYPE_AUDIO,
        muxerWrapper,
        mediaClock,
        transformationRequest,
        asyncErrorListener,
        fallbackListener);
    this.encoderFactory = encoderFactory;
    this.decoderFactory = decoderFactory;
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected boolean ensureConfigured() throws TransformationException {
    if (samplePipeline != null) {
      return true;
    }
    FormatHolder formatHolder = getFormatHolder();
    @SampleStream.ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ SampleStream.FLAG_REQUIRE_FORMAT);
    if (result != RESULT_FORMAT_READ) {
      return false;
    }
//    Format inputFormat = formatHolder.format;
    Format inputFormat = Format.createAudioSampleFormat(formatHolder.format.id,
            formatHolder.format.sampleMimeType,
            formatHolder.format.codecs,
            formatHolder.format.bitrate,
            formatHolder.format.maxInputSize,
            formatHolder.format.channelCount,
            formatHolder.format.sampleRate,
            formatHolder.format.initializationData, null);
    if (shouldPassthrough(inputFormat)) {
      samplePipeline =
          new PassthroughSamplePipeline(
              inputFormat,
              streamOffsetUs,
              streamStartPositionUs,
              transformationRequest,
              muxerWrapper,
              fallbackListener);
    } else {
      samplePipeline =
          new AudioTranscodeSamplePipeline(
              inputFormat,
              streamOffsetUs,
              streamStartPositionUs,
              transformationRequest,
              decoderFactory,
              encoderFactory,
              muxerWrapper,
              fallbackListener);
    }
    return true;
  }

  private boolean shouldPassthrough(Format inputFormat) {
    if (encoderFactory.audioNeedsEncoding()) {
      return false;
    }
    if (transformationRequest.audioMimeType != null
        && !transformationRequest.audioMimeType.equals(inputFormat.sampleMimeType)) {
      return false;
    }
    if (transformationRequest.audioMimeType == null
        && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
      return false;
    }
    return true;
  }

}
