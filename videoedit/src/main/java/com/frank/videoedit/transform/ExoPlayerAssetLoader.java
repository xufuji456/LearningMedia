
package com.frank.videoedit.transform;

import static com.frank.videoedit.transform.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.frank.videoedit.transform.Transformer.PROGRESS_STATE_NO_TRANSFORMATION;
import static com.frank.videoedit.transform.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static com.frank.videoedit.transform.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
import static java.lang.Math.min;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.frank.videoedit.listener.DebugViewProvider;
import com.frank.videoedit.listener.FrameProcessor;
import com.frank.videoedit.listener.GlEffect;
import com.frank.videoedit.transform.clock.Clock;
import com.frank.videoedit.transform.entity.MediaItem;
import com.frank.videoedit.transform.impl.Codec;
//import com.frank.videoedit.transform.render.Renderer;
import com.frank.videoedit.transform.render.TransformerAudioRenderer;
import com.frank.videoedit.transform.render.TransformerVideoRenderer;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.common.collect.ImmutableList;

final class ExoPlayerAssetLoader {

  public interface Listener {

    void onEnded();

    void onError(Exception e);
  }

  private final Context context;
  private final TransformationRequest transformationRequest;
  private final ImmutableList<GlEffect> videoEffects;
  private final boolean removeAudio;
  private final boolean removeVideo;
  private final MediaSource.Factory mediaSourceFactory;
  private final Codec.DecoderFactory decoderFactory;
  private final Codec.EncoderFactory encoderFactory;
  private final FrameProcessor.Factory frameProcessorFactory;
  private final Looper looper;
  private final DebugViewProvider debugViewProvider;
  private final Clock clock;

  private MuxerWrapper muxerWrapper;
  @Nullable private ExoPlayer player;
  private @Transformer.ProgressState int progressState;

  public ExoPlayerAssetLoader(
      Context context,
      TransformationRequest transformationRequest,
      ImmutableList<GlEffect> videoEffects,
      boolean removeAudio,
      boolean removeVideo,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      FrameProcessor.Factory frameProcessorFactory,
      Looper looper,
      DebugViewProvider debugViewProvider,
      Clock clock) {
    this.context = context;
    this.transformationRequest = transformationRequest;
    this.videoEffects = videoEffects;
    this.removeAudio = removeAudio;
    this.removeVideo = removeVideo;
    this.mediaSourceFactory = mediaSourceFactory;
    this.decoderFactory = decoderFactory;
    this.encoderFactory = encoderFactory;
    this.frameProcessorFactory = frameProcessorFactory;
    this.looper = looper;
    this.debugViewProvider = debugViewProvider;
    this.clock = clock;
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
  }

  public void start(
      MediaItem mediaItem,
      MuxerWrapper muxerWrapper,
      Listener listener,
      FallbackListener fallbackListener,
      Transformer.AsyncErrorListener asyncErrorListener) {
    this.muxerWrapper = muxerWrapper;

    DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
    trackSelector.setParameters(
        new DefaultTrackSelector.Parameters.Builder(context)
            .setForceHighestSupportedBitrate(true)
            .build());
    DefaultLoadControl loadControl =
        new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10)
            .build();
    ExoPlayer.Builder playerBuilder =
        new ExoPlayer.Builder(
                context,
                new RenderersFactoryImpl(
                    context,
                    muxerWrapper,
                    removeAudio,
                    removeVideo,
                    transformationRequest,
                    false,
                    videoEffects,
                    frameProcessorFactory,
                    encoderFactory,
                    decoderFactory,
                    fallbackListener,
                    asyncErrorListener,
                    debugViewProvider))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setLooper(looper);
    if (clock != Clock.DEFAULT) {
      // TODO: clock not different
//      playerBuilder.setClock(clock);
    }

    player = playerBuilder.build();
    // TODO: copy mediaItem (Uri)
    com.google.android.exoplayer2.MediaItem exoMediaItem =
            com.google.android.exoplayer2.MediaItem.fromUri(mediaItem.uri);
    player.setMediaItem(exoMediaItem);
    player.addListener(new PlayerListener(listener));
    player.prepare();

    progressState = PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
  }

  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      Player player = this.player;
      long durationMs = player.getDuration();
      long positionMs = player.getCurrentPosition();
      progressHolder.progress = min((int) (positionMs * 100 / durationMs), 99);
    }
    return progressState;
  }

  public void release() {
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
    if (player != null) {
      player.release();
      player = null;
    }
  }

  private static final class RenderersFactoryImpl implements RenderersFactory {

    private final Context context;
    private final MuxerWrapper muxerWrapper;
    private final TransformerMediaClock mediaClock;
    private final boolean removeAudio;
    private final boolean removeVideo;
    private final TransformationRequest transformationRequest;
    private final boolean clippingStartsAtKeyFrame;
    private final ImmutableList<GlEffect> videoEffects;
    private final FrameProcessor.Factory frameProcessorFactory;
    private final Codec.EncoderFactory encoderFactory;
    private final Codec.DecoderFactory decoderFactory;
    private final FallbackListener fallbackListener;
    private final Transformer.AsyncErrorListener asyncErrorListener;
    private final DebugViewProvider debugViewProvider;

    public RenderersFactoryImpl(
        Context context,
        MuxerWrapper muxerWrapper,
        boolean removeAudio,
        boolean removeVideo,
        TransformationRequest transformationRequest,
        boolean clippingStartsAtKeyFrame,
        ImmutableList<GlEffect> videoEffects,
        FrameProcessor.Factory frameProcessorFactory,
        Codec.EncoderFactory encoderFactory,
        Codec.DecoderFactory decoderFactory,
        FallbackListener fallbackListener,
        Transformer.AsyncErrorListener asyncErrorListener,
        DebugViewProvider debugViewProvider) {
      this.context = context;
      this.muxerWrapper = muxerWrapper;
      this.removeAudio = removeAudio;
      this.removeVideo = removeVideo;
      this.transformationRequest = transformationRequest;
      this.clippingStartsAtKeyFrame = clippingStartsAtKeyFrame;
      this.videoEffects = videoEffects;
      this.frameProcessorFactory = frameProcessorFactory;
      this.encoderFactory = encoderFactory;
      this.decoderFactory = decoderFactory;
      this.fallbackListener = fallbackListener;
      this.asyncErrorListener = asyncErrorListener;
      this.debugViewProvider = debugViewProvider;
      mediaClock = new TransformerMediaClock();
    }

    @Override
    public Renderer[] createRenderers(
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      int rendererCount = removeAudio || removeVideo ? 1 : 2;
      Renderer[] renderers = new Renderer[rendererCount];
      int index = 0;
      if (!removeAudio) {
        renderers[index] =
            new TransformerAudioRenderer(
                muxerWrapper,
                mediaClock,
                transformationRequest,
                encoderFactory,
                decoderFactory,
                asyncErrorListener,
                fallbackListener);
        index++;
      }
      if (!removeVideo) {
        renderers[index] =
            new TransformerVideoRenderer(
                context,
                muxerWrapper,
                mediaClock,
                transformationRequest,
                clippingStartsAtKeyFrame,
                videoEffects,
                frameProcessorFactory,
                encoderFactory,
                decoderFactory,
                asyncErrorListener,
                fallbackListener,
                debugViewProvider);
        index++;
      }
      return renderers;
    }
  }

  private final class PlayerListener implements Player.Listener {

    private final Listener listener;

    public PlayerListener(Listener listener) {
      this.listener = listener;
    }

    @Override
    public void onPlaybackStateChanged(int state) {
      if (state == Player.STATE_ENDED) {
        listener.onEnded();
      }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
      if (progressState != PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
        return;
      }
      Timeline.Window window = new Timeline.Window();
      timeline.getWindow(/* windowIndex= */ 0, window);
      if (!window.isPlaceholder) {
        long durationUs = window.durationUs;
        progressState =
            durationUs <= 0 ? PROGRESS_STATE_UNAVAILABLE : PROGRESS_STATE_AVAILABLE;
        player.play();
      }
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
      if (muxerWrapper.getTrackCount() == 0) {
        listener.onError(new IllegalStateException("The output does not contain any tracks."));
      }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      listener.onError(error);
    }
  }

}