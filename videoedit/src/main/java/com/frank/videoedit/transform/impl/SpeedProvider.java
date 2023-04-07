
package com.frank.videoedit.transform.impl;

public interface SpeedProvider {

  float getSpeed(long timeUs);

  long getNextSpeedChangeTimeUs(long timeUs);
}
