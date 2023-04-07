
package com.frank.videoedit.transform.clock;

import com.frank.videoedit.transform.entity.PlaybackParams;

public interface MediaClock {

  long getPositionUs();

  void setPlaybackParameters(PlaybackParams playbackParams);

  PlaybackParams getPlaybackParameters();
}
