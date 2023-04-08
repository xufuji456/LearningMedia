
package com.frank.videoedit.transform.render;

import android.content.Context;

import com.frank.videoedit.listener.DebugViewProvider;
import com.frank.videoedit.listener.FrameProcessor;
import com.frank.videoedit.listener.GlEffect;
//import com.frank.videoedit.transform.entity.DecoderInputBuffer;
import com.frank.videoedit.transform.FallbackListener;
import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.MuxerWrapper;
import com.frank.videoedit.transform.PassthroughSamplePipeline;
import com.frank.videoedit.transform.TransformationException;
import com.frank.videoedit.transform.TransformationRequest;
import com.frank.videoedit.transform.Transformer;
import com.frank.videoedit.transform.TransformerMediaClock;
import com.frank.videoedit.transform.VideoTranscodeSamplePipeline;
import com.frank.videoedit.transform.impl.Codec;
import com.frank.videoedit.transform.util.MediaUtil;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.common.collect.ImmutableList;

public final class TransformerVideoRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TransformerVideoRenderer";

  private final Context context;
  private final boolean clippingStartsAtKeyFrame;
  private final ImmutableList<GlEffect> effects;
  private final FrameProcessor.Factory frameProcessorFactory;
  private final Codec.EncoderFactory encoderFactory;
  private final Codec.DecoderFactory decoderFactory;
  private final DebugViewProvider debugViewProvider;
  private final DecoderInputBuffer decoderInputBuffer;
  private static final int RESULT_FORMAT_READ = -5;

//  private final static int FLAG_REQUIRE_FORMAT = 1 << 1;

  public TransformerVideoRenderer(
      Context context,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      boolean clippingStartsAtKeyFrame,
      ImmutableList<GlEffect> effects,
      FrameProcessor.Factory frameProcessorFactory,
      Codec.EncoderFactory encoderFactory,
      Codec.DecoderFactory decoderFactory,
      Transformer.AsyncErrorListener asyncErrorListener,
      FallbackListener fallbackListener,
      DebugViewProvider debugViewProvider) {
    super(
        MediaUtil.TRACK_TYPE_VIDEO,
        muxerWrapper,
        mediaClock,
        transformationRequest,
        asyncErrorListener,
        fallbackListener);
    this.context = context;
    this.clippingStartsAtKeyFrame = clippingStartsAtKeyFrame;
    this.effects = effects;
    this.frameProcessorFactory = frameProcessorFactory;
    this.encoderFactory = encoderFactory;
    this.decoderFactory = decoderFactory;
    this.debugViewProvider = debugViewProvider;
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
    Format inputFormat = Format.createVideoSampleFormat(formatHolder.format.id,
            formatHolder.format.sampleMimeType,
            formatHolder.format.codecs,
            formatHolder.format.bitrate,
            formatHolder.format.maxInputSize,
            formatHolder.format.width,
            formatHolder.format.height,
            formatHolder.format.frameRate,
            formatHolder.format.initializationData, null);//formatHolder.format;
    if (shouldTranscode(inputFormat)) {
      samplePipeline =
          new VideoTranscodeSamplePipeline(
              context,
              inputFormat,
              streamOffsetUs,
              streamStartPositionUs,
              transformationRequest,
              effects,
              frameProcessorFactory,
              decoderFactory,
              encoderFactory,
              muxerWrapper,
              fallbackListener,
              asyncErrorListener,
              debugViewProvider);
    } else {
      samplePipeline =
          new PassthroughSamplePipeline(
              inputFormat,
              streamOffsetUs,
              streamStartPositionUs,
              transformationRequest,
              muxerWrapper,
              fallbackListener);
    }
    return true;
  }

  private boolean shouldTranscode(Format inputFormat) {
    if ((streamStartPositionUs - streamOffsetUs) != 0 && !clippingStartsAtKeyFrame) {
      return true;
    }
    if (encoderFactory.videoNeedsEncoding()) {
      return true;
    }
    if (transformationRequest.enableRequestSdrToneMapping) {
      return true;
    }
    if (transformationRequest.forceInterpretHdrVideoAsSdr) {
      return true;
    }
    if (transformationRequest.videoMimeType != null
        && !transformationRequest.videoMimeType.equals(inputFormat.sampleMimeType)) {
      return true;
    }
    if (transformationRequest.videoMimeType == null
        && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
      return true;
    }
    if (inputFormat.pixelWidthHeightRatio != 1f) {
      return true;
    }
    if (transformationRequest.rotationDegrees != 0f) {
      return true;
    }
    if (transformationRequest.scaleX != 1f) {
      return true;
    }
    if (transformationRequest.scaleY != 1f) {
      return true;
    }
    // The decoder rotates encoded frames for display by inputFormat.rotationDegrees.
    int decodedHeight =
        (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
    if (transformationRequest.outputHeight != -1
        && transformationRequest.outputHeight != decodedHeight) {
      return true;
    }
    if (!effects.isEmpty()) {
      return true;
    }
    return false;
  }

}
