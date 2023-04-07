
package com.frank.videoedit.transform.entity;

import android.os.Bundle;

import androidx.annotation.CheckResult;
import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;

import com.frank.videoedit.listener.Bundleable;

public final class PlaybackParams implements Bundleable {

  public static final PlaybackParams DEFAULT = new PlaybackParams(/* speed= */ 1f);

  public final float speed;

  public final float pitch;

  public PlaybackParams(float speed) {
    this(speed, /* pitch= */ 1f);
  }

  public PlaybackParams(
      @FloatRange(from = 0, fromInclusive = false) float speed,
      @FloatRange(from = 0, fromInclusive = false) float pitch) {

    this.speed = speed;
    this.pitch = pitch;
  }

  @CheckResult
  public PlaybackParams withSpeed(@FloatRange(from = 0, fromInclusive = false) float speed) {
    return new PlaybackParams(speed, pitch);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    PlaybackParams other = (PlaybackParams) obj;
    return this.speed == other.speed && this.pitch == other.pitch;
  }


  private static final String FIELD_SPEED = Integer.toString(0, Character.MAX_RADIX);
  private static final String FIELD_PITCH = Integer.toString(1, Character.MAX_RADIX);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putFloat(FIELD_SPEED, speed);
    bundle.putFloat(FIELD_PITCH, pitch);
    return bundle;
  }

  /** Object that can restore {@link PlaybackParams} from a {@link Bundle}. */
  public static final Creator<PlaybackParams> CREATOR =
      bundle -> {
        float speed = bundle.getFloat(FIELD_SPEED, /* defaultValue= */ 1f);
        float pitch = bundle.getFloat(FIELD_PITCH, /* defaultValue= */ 1f);
        return new PlaybackParams(speed, pitch);
      };
}
