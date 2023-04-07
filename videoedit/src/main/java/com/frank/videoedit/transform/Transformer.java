
package com.frank.videoedit.transform;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.frank.videoedit.effect.GlEffectsFrameProcessor;
import com.frank.videoedit.listener.DebugViewProvider;
import com.frank.videoedit.listener.FrameProcessor;
import com.frank.videoedit.listener.GlEffect;
import com.frank.videoedit.transform.clock.Clock;
import com.frank.videoedit.transform.entity.ListenerSet;
import com.frank.videoedit.transform.entity.MediaItem;
import com.frank.videoedit.transform.impl.Codec;
import com.frank.videoedit.transform.impl.Muxer;
import com.frank.videoedit.transform.util.MediaUtil;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * A transformer to transform media inputs.
 *
 * <p>The same Transformer instance can be used to transform multiple inputs (sequentially, not
 * concurrently).
 *
 * <p>Transformer instances must be accessed from a single application thread. For the vast majority
 * of cases this should be the application's main thread. The thread on which a Transformer instance
 * must be accessed can be explicitly specified by passing a {@link Looper} when creating the
 * transformer. If no Looper is specified, then the Looper of the thread that the {@link
 * Builder} is created on is used, or if that thread does not have a Looper, the Looper
 * of the application's main thread is used. In all cases the Looper of the thread from which the
 * transformer must be accessed can be queried using {@link #getApplicationLooper()}.
 */
public final class Transformer {

  public static final class Builder {

    private final Context context;

    private TransformationRequest transformationRequest;
    private ImmutableList<GlEffect> videoEffects;
    private boolean removeAudio;
    private boolean removeVideo;
    private ListenerSet<Listener> listeners;
    private MediaSource.Factory mediaSourceFactory;
    private Codec.DecoderFactory decoderFactory;
    private Codec.EncoderFactory encoderFactory;
    private FrameProcessor.Factory frameProcessorFactory;
    private Muxer.Factory muxerFactory;
    private Looper looper;
    private DebugViewProvider debugViewProvider;
    private Clock clock;

    private static Looper getCurrentOrMainLooper() {
      @Nullable Looper myLooper = Looper.myLooper();
      return myLooper != null ? myLooper : Looper.getMainLooper();
    }

    public Builder(Context context) {
      this.context = context.getApplicationContext();
      transformationRequest = new TransformationRequest.Builder().build();
      videoEffects = ImmutableList.of();
      decoderFactory = new DefaultDecoderFactory(this.context);
      encoderFactory = new DefaultEncoderFactory.Builder(this.context).build();
      frameProcessorFactory = new GlEffectsFrameProcessor.Factory();
      muxerFactory = new DefaultMuxer.Factory();
      looper = getCurrentOrMainLooper();
      debugViewProvider = DebugViewProvider.NONE;
      clock = Clock.DEFAULT;
      listeners = new ListenerSet<>(looper, clock, (listener, flags) -> {});
    }

    private Builder(Transformer transformer) {
      this.context = transformer.context;
      this.transformationRequest = transformer.transformationRequest;
      this.videoEffects = transformer.videoEffects;
      this.removeAudio = transformer.removeAudio;
      this.removeVideo = transformer.removeVideo;
      this.listeners = transformer.listeners;
      this.mediaSourceFactory = transformer.mediaSourceFactory;
      this.decoderFactory = transformer.decoderFactory;
      this.encoderFactory = transformer.encoderFactory;
      this.frameProcessorFactory = transformer.frameProcessorFactory;
      this.muxerFactory = transformer.muxerFactory;
      this.looper = transformer.looper;
      this.debugViewProvider = transformer.debugViewProvider;
      this.clock = transformer.clock;
    }

    public Builder setTransformationRequest(TransformationRequest transformationRequest) {
      this.transformationRequest = transformationRequest;
      return this;
    }

    public Builder setVideoEffects(List<GlEffect> effects) {
      this.videoEffects = ImmutableList.copyOf(effects);
      return this;
    }

    public Builder setRemoveAudio(boolean removeAudio) {
      this.removeAudio = removeAudio;
      return this;
    }

    public Builder setRemoveVideo(boolean removeVideo) {
      this.removeVideo = removeVideo;
      return this;
    }

    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      transformationRequest =
          transformationRequest.buildUpon().setFlattenForSlowMotion(flattenForSlowMotion).build();
      return this;
    }

    public Builder setListener(Listener listener) {
      this.listeners.clear();
      this.listeners.add(listener);
      return this;
    }

    public Builder addListener(Listener listener) {
      this.listeners.add(listener);
      return this;
    }

    public Builder removeListener(Listener listener) {
      this.listeners.remove(listener);
      return this;
    }

    public Builder removeAllListeners() {
      this.listeners.clear();
      return this;
    }

    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      this.mediaSourceFactory = mediaSourceFactory;
      return this;
    }

    public Builder setDecoderFactory(Codec.DecoderFactory decoderFactory) {
      this.decoderFactory = decoderFactory;
      return this;
    }

    public Builder setEncoderFactory(Codec.EncoderFactory encoderFactory) {
      this.encoderFactory = encoderFactory;
      return this;
    }

    public Builder setFrameProcessorFactory(FrameProcessor.Factory frameProcessorFactory) {
      this.frameProcessorFactory = frameProcessorFactory;
      return this;
    }

    public Builder setMuxerFactory(Muxer.Factory muxerFactory) {
      this.muxerFactory = muxerFactory;
      return this;
    }

    public Builder setLooper(Looper looper) {
      this.looper = looper;
      this.listeners = listeners.copy(looper, (listener, flags) -> {});
      return this;
    }

    public Builder setDebugViewProvider(DebugViewProvider debugViewProvider) {
      this.debugViewProvider = debugViewProvider;
      return this;
    }

    @VisibleForTesting
    /* package */ Builder setClock(Clock clock) {
      this.clock = clock;
      this.listeners = listeners.copy(looper, clock, (listener, flags) -> {});
      return this;
    }

    public Transformer build() {
      if (transformationRequest.audioMimeType != null) {
        checkSampleMimeType(transformationRequest.audioMimeType);
      }
      if (transformationRequest.videoMimeType != null) {
        checkSampleMimeType(transformationRequest.videoMimeType);
      }
      if (mediaSourceFactory == null) {
        DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
        mediaSourceFactory = new DefaultMediaSourceFactory(context, defaultExtractorsFactory);
      }
      return new Transformer(
          context,
          transformationRequest,
          videoEffects,
          removeAudio,
          removeVideo,
          listeners,
          mediaSourceFactory,
          decoderFactory,
          encoderFactory,
          frameProcessorFactory,
          muxerFactory,
          looper,
          debugViewProvider,
          clock);
    }

    private void checkSampleMimeType(String sampleMimeType) {

    }
  }

  public interface Listener {

    default void onTransformationCompleted(MediaItem inputMediaItem) {}

    default void onTransformationCompleted(
        MediaItem inputMediaItem, TransformationResult transformationResult) {
      onTransformationCompleted(inputMediaItem);
    }

    default void onTransformationError(MediaItem inputMediaItem, Exception exception) {
      onTransformationError(inputMediaItem, (TransformationException) exception);
    }

    default void onTransformationError(
            MediaItem inputMediaItem, TransformationException exception) {}

    default void onFallbackApplied(
        MediaItem inputMediaItem,
        TransformationRequest originalTransformationRequest,
        TransformationRequest fallbackTransformationRequest) {}
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    PROGRESS_STATE_WAITING_FOR_AVAILABILITY,
    PROGRESS_STATE_AVAILABLE,
    PROGRESS_STATE_UNAVAILABLE,
    PROGRESS_STATE_NO_TRANSFORMATION
  })
  public @interface ProgressState {}

  public static final int PROGRESS_STATE_WAITING_FOR_AVAILABILITY = 0;
  public static final int PROGRESS_STATE_AVAILABLE = 1;
  public static final int PROGRESS_STATE_UNAVAILABLE = 2;
  public static final int PROGRESS_STATE_NO_TRANSFORMATION = 4;

  @VisibleForTesting final Codec.DecoderFactory decoderFactory;
  @VisibleForTesting final Codec.EncoderFactory encoderFactory;

  private final Context context;
  private final TransformationRequest transformationRequest;
  private final ImmutableList<GlEffect> videoEffects;
  private final boolean removeAudio;
  private final boolean removeVideo;
  private final ListenerSet<Listener> listeners;
  private final MediaSource.Factory mediaSourceFactory;
  private final FrameProcessor.Factory frameProcessorFactory;
  private final Muxer.Factory muxerFactory;
  private final Looper looper;
  private final DebugViewProvider debugViewProvider;
  private final Clock clock;
  private final ExoPlayerAssetLoader exoPlayerAssetLoader;

  @Nullable private MuxerWrapper muxerWrapper;
  @Nullable private String outputPath;
  @Nullable private ParcelFileDescriptor outputParcelFileDescriptor;
  private boolean transformationInProgress;
  private boolean isCancelling;

  private Transformer(
      Context context,
      TransformationRequest transformationRequest,
      ImmutableList<GlEffect> videoEffects,
      boolean removeAudio,
      boolean removeVideo,
      ListenerSet<Listener> listeners,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      FrameProcessor.Factory frameProcessorFactory,
      Muxer.Factory muxerFactory,
      Looper looper,
      DebugViewProvider debugViewProvider,
      Clock clock) {
    this.context = context;
    this.transformationRequest = transformationRequest;
    this.videoEffects = videoEffects;
    this.removeAudio = removeAudio;
    this.removeVideo = removeVideo;
    this.listeners = listeners;
    this.mediaSourceFactory = mediaSourceFactory;
    this.decoderFactory = decoderFactory;
    this.encoderFactory = encoderFactory;
    this.frameProcessorFactory = frameProcessorFactory;
    this.muxerFactory = muxerFactory;
    this.looper = looper;
    this.debugViewProvider = debugViewProvider;
    this.clock = clock;
    exoPlayerAssetLoader =
        new ExoPlayerAssetLoader(
            context,
            transformationRequest,
            videoEffects,
            removeAudio,
            removeVideo,
            mediaSourceFactory,
            decoderFactory,
            encoderFactory,
            frameProcessorFactory,
            looper,
            debugViewProvider,
            clock);
  }

  public Builder buildUpon() {
    return new Builder(this);
  }

  public void setListener(Listener listener) {
    verifyApplicationThread();
    this.listeners.clear();
    this.listeners.add(listener);
  }

  public void addListener(Listener listener) {
    verifyApplicationThread();
    this.listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    verifyApplicationThread();
    this.listeners.remove(listener);
  }

  public void removeAllListeners() {
    verifyApplicationThread();
    this.listeners.clear();
  }

  public void startTransformation(MediaItem mediaItem, String path) {
    this.outputPath = path;
    this.outputParcelFileDescriptor = null;
    startTransformationInternal(mediaItem);
  }

  @RequiresApi(26)
  public void startTransformation(MediaItem mediaItem, ParcelFileDescriptor parcelFileDescriptor) {
    this.outputParcelFileDescriptor = parcelFileDescriptor;
    this.outputPath = null;
    startTransformationInternal(mediaItem);
  }

  private void startTransformationInternal(MediaItem mediaItem) {
    verifyApplicationThread();
    if (transformationInProgress) {
      throw new IllegalStateException("There is already a transformation in progress.");
    }
    transformationInProgress = true;
    ComponentListener componentListener = new ComponentListener(mediaItem, looper);
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputPath,
            outputParcelFileDescriptor,
            muxerFactory,
            componentListener);
    this.muxerWrapper = muxerWrapper;
    FallbackListener fallbackListener =
        new FallbackListener(
            mediaItem,
            listeners,
            clock.createHandler(looper, /* callback= */ null),
            transformationRequest);
    exoPlayerAssetLoader.start(mediaItem, muxerWrapper, componentListener, fallbackListener, componentListener);
  }

  public Looper getApplicationLooper() {
    return looper;
  }

  public @ProgressState int getProgress(ProgressHolder progressHolder) {
    verifyApplicationThread();
    return exoPlayerAssetLoader.getProgress(progressHolder);
  }

  public void cancel() {
    verifyApplicationThread();
    isCancelling = true;
    try {
      releaseResources(/* forCancellation= */ true);
    } catch (TransformationException impossible) {
      throw new IllegalStateException(impossible);
    }
    isCancelling = false;
  }

  private void releaseResources(boolean forCancellation) throws TransformationException {
    transformationInProgress = false;
    exoPlayerAssetLoader.release();
    if (muxerWrapper != null) {
      try {
        muxerWrapper.release(forCancellation);
      } catch (Muxer.MuxerException e) {
        throw TransformationException.createForMuxer(
            e, TransformationException.ERROR_CODE_MUXING_FAILED);
      }
      muxerWrapper = null;
    }
  }

  private void verifyApplicationThread() {
    if (Looper.myLooper() != looper) {
      throw new IllegalStateException("Transformer is accessed on the wrong thread.");
    }
  }

  private long getCurrentOutputFileCurrentSizeBytes() {
    long fileSize = -1;

    if (outputPath != null) {
      fileSize = new File(outputPath).length();
    } else if (outputParcelFileDescriptor != null) {
      fileSize = outputParcelFileDescriptor.getStatSize();
    }

    if (fileSize <= 0) {
      fileSize = -1;
    }

    return fileSize;
  }

  public interface AsyncErrorListener {
    void onTransformationException(TransformationException exception);
  }

  private final class ComponentListener implements ExoPlayerAssetLoader.Listener, AsyncErrorListener {

    private final MediaItem mediaItem;
    private final Handler handler;

    public ComponentListener(MediaItem mediaItem, Looper looper) {
      this.mediaItem = mediaItem;
      handler = new Handler(looper);
    }

    @Override
    public void onError(Exception e) {
      TransformationException transformationException =
          e instanceof RuntimeException
              ? TransformationException.createForPlaybackException((RuntimeException) e)
              : TransformationException.createForUnexpected(e);
      handleTransformationException(transformationException);
    }

    @Override
    public void onEnded() {
      handleTransformationEnded(/* exception= */ null);
    }

    @Override
    public void onTransformationException(TransformationException exception) {
      if (Looper.myLooper() == looper) {
        handleTransformationException(exception);
      } else {
        handler.post(() -> handleTransformationException(exception));
      }
    }

    private void handleTransformationException(TransformationException transformationException) {
      if (isCancelling) {
        listeners.queueEvent(
            -1,
            listener -> listener.onTransformationError(mediaItem, transformationException));
        listeners.flushEvents();
      } else {
        handleTransformationEnded(transformationException);
      }
    }

    private void handleTransformationEnded(@Nullable TransformationException exception) {
      MuxerWrapper muxerWrapper = Transformer.this.muxerWrapper;
      @Nullable TransformationException resourceReleaseException = null;
      try {
        releaseResources(/* forCancellation= */ false);
      } catch (TransformationException e) {
        resourceReleaseException = e;
      } catch (RuntimeException e) {
        resourceReleaseException = TransformationException.createForUnexpected(e);
      }
      if (exception == null) {
        exception = resourceReleaseException;
      }

      if (exception != null) {
        TransformationException finalException = exception;
        listeners.queueEvent(
            /* eventFlag= */ -1,
            listener -> listener.onTransformationError(mediaItem, finalException));
      } else {
        TransformationResult result =
            new TransformationResult.Builder()
                .setDurationMs(muxerWrapper.getDurationMs())
                .setAverageAudioBitrate(muxerWrapper.getTrackAverageBitrate(MediaUtil.TRACK_TYPE_AUDIO))
                .setAverageVideoBitrate(muxerWrapper.getTrackAverageBitrate(MediaUtil.TRACK_TYPE_VIDEO))
                .setVideoFrameCount(muxerWrapper.getTrackSampleCount(MediaUtil.TRACK_TYPE_VIDEO))
                .setFileSizeBytes(getCurrentOutputFileCurrentSizeBytes())
                .build();

        listeners.queueEvent(
            -1,
            listener -> listener.onTransformationCompleted(mediaItem, result));
      }
      listeners.flushEvents();
    }
  }

}
