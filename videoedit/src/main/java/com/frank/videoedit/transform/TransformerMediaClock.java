
package com.frank.videoedit.transform;

import static java.lang.Math.min;

import android.util.SparseLongArray;

//import com.frank.videoedit.transform.clock.MediaClock;
import com.frank.videoedit.transform.util.MediaUtil;

import java.util.NoSuchElementException;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.util.MediaClock;

public final class TransformerMediaClock implements MediaClock {

  private final SparseLongArray trackTypeToTimeUs;
  private long minTrackTimeUs;
  private static final long TIME_UNSET = Long.MIN_VALUE + 1;

  public TransformerMediaClock() {
    trackTypeToTimeUs = new SparseLongArray();
  }

  /**
   * Updates the time for a given track type. The clock time is computed based on the different
   * track times.
   */
  public void updateTimeForTrackType(@MediaUtil.TrackType int trackType, long timeUs) {
    long previousTimeUs = trackTypeToTimeUs.get(trackType, TIME_UNSET);
    if (previousTimeUs != TIME_UNSET && timeUs <= previousTimeUs) {
      // Make sure that the track times are increasing and therefore that the clock time is
      // increasing. This is necessary for progress updates.
      return;
    }
    trackTypeToTimeUs.put(trackType, timeUs);
    if (previousTimeUs == TIME_UNSET || previousTimeUs == minTrackTimeUs) {
      minTrackTimeUs = minValue(trackTypeToTimeUs);
    }
  }

  @Override
  public long getPositionUs() {
    // Use minimum position among tracks as position to ensure that the buffered duration is
    // positive. This is also useful for controlling samples interleaving.
    return minTrackTimeUs;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParams) {}

  @Override
  public PlaybackParameters getPlaybackParameters() {
    // Playback parameters are unknown. Set default value.
    return PlaybackParameters.DEFAULT;
  }

  private static long minValue(SparseLongArray sparseLongArray) {
    if (sparseLongArray.size() == 0) {
      throw new NoSuchElementException();
    }
    long min = Long.MAX_VALUE;
    for (int i = 0; i < sparseLongArray.size(); i++) {
      min = min(min, sparseLongArray.valueAt(i));
    }
    return min;
  }

}
