
package com.frank.videoedit.transform.speed;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.frank.videoedit.transform.entity.Metadata;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Holds information about the segments of slow motion playback within a track. */
public final class SlowMotionData implements Metadata.Entry {

  /** Holds information about a single segment of slow motion playback within a track. */
  public static final class Segment implements Parcelable {

    public static final Comparator<Segment> BY_START_THEN_END_THEN_DIVISOR =
        (s1, s2) ->
            ComparisonChain.start()
                .compare(s1.startTimeMs, s2.startTimeMs)
                .compare(s1.endTimeMs, s2.endTimeMs)
                .compare(s1.speedDivisor, s2.speedDivisor)
                .result();

    /** The start time, in milliseconds, of the track segment that is intended to be slow motion. */
    public final long startTimeMs;
    /** The end time, in milliseconds, of the track segment that is intended to be slow motion. */
    public final long endTimeMs;
    /**
     * The speed reduction factor.
     *
     * <p>For example, 4 would mean the segment should be played at a quarter (1/4) of the normal
     * speed.
     */
    public final int speedDivisor;

    /**
     * Creates an instance.
     *
     * @param startTimeMs See {@link #startTimeMs}. Must be less than endTimeMs.
     * @param endTimeMs See {@link #endTimeMs}.
     * @param speedDivisor See {@link #speedDivisor}.
     */
    public Segment(long startTimeMs, long endTimeMs, int speedDivisor) {
      this.startTimeMs = startTimeMs;
      this.endTimeMs = endTimeMs;
      this.speedDivisor = speedDivisor;
    }

    @Override
    public String toString() {
      return String.format(Locale.getDefault(),
          "Segment: startTimeMs=%d, endTimeMs=%d, speedDivisor=%d",
          startTimeMs, endTimeMs, speedDivisor);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Segment segment = (Segment) o;
      return startTimeMs == segment.startTimeMs
          && endTimeMs == segment.endTimeMs
          && speedDivisor == segment.speedDivisor;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(startTimeMs, endTimeMs, speedDivisor);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(startTimeMs);
      dest.writeLong(endTimeMs);
      dest.writeInt(speedDivisor);
    }

    public static final Creator<Segment> CREATOR =
        new Creator<Segment>() {

          @Override
          public Segment createFromParcel(Parcel in) {
            long startTimeMs = in.readLong();
            long endTimeMs = in.readLong();
            int speedDivisor = in.readInt();
            return new Segment(startTimeMs, endTimeMs, speedDivisor);
          }

          @Override
          public Segment[] newArray(int size) {
            return new Segment[size];
          }
        };
  }

  public final List<Segment> segments;

  /**
   * Creates an instance with a list of {@link Segment}s.
   *
   * <p>The segments must not overlap, that is that the start time of a segment can not be between
   * the start and end time of another segment.
   */
  public SlowMotionData(List<Segment> segments) {
    this.segments = segments;
    if(doSegmentsOverlap(segments)) {
      throw new IllegalArgumentException("segment duration has overlapped!");
    }
  }

  @Override
  public String toString() {
    return "SlowMotion: segments=" + segments;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SlowMotionData that = (SlowMotionData) o;
    return segments.equals(that.segments);
  }

  @Override
  public int hashCode() {
    return segments.hashCode();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeList(segments);
  }

  public static final Parcelable.Creator<SlowMotionData> CREATOR =
      new Parcelable.Creator<SlowMotionData>() {
        @Override
        public SlowMotionData createFromParcel(Parcel in) {
          List<Segment> slowMotionSegments = new ArrayList<>();
          in.readList(slowMotionSegments, Segment.class.getClassLoader());
          return new SlowMotionData(slowMotionSegments);
        }

        @Override
        public SlowMotionData[] newArray(int size) {
          return new SlowMotionData[size];
        }
      };

  private static boolean doSegmentsOverlap(List<Segment> segments) {
    if (segments.isEmpty()) {
      return false;
    }
    long previousEndTimeMs = segments.get(0).endTimeMs;
    for (int i = 1; i < segments.size(); i++) {
      if (segments.get(i).startTimeMs < previousEndTimeMs) {
        return true;
      }
      previousEndTimeMs = segments.get(i).endTimeMs;
    }

    return false;
  }
}
