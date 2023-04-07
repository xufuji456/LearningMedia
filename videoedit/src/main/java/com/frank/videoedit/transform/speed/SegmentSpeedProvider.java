
package com.frank.videoedit.transform.speed;

import static com.frank.videoedit.transform.speed.SlowMotionData.Segment.BY_START_THEN_END_THEN_DIVISOR;

import androidx.annotation.Nullable;

import com.frank.videoedit.transform.Format;
import com.frank.videoedit.transform.entity.Metadata;
import com.frank.videoedit.transform.impl.SpeedProvider;
import com.frank.videoedit.transform.speed.SlowMotionData.Segment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SegmentSpeedProvider implements SpeedProvider {

  /**
   * Input frame rate of Samsung Slow motion videos is always 30. See
   * go/exoplayer-sef-slomo-video-flattening.
   */
  private static final int INPUT_FRAME_RATE = 30;

  private final ImmutableSortedMap<Long, Float> speedsByStartTimeUs;
  private final float baseSpeedMultiplier;

  public SegmentSpeedProvider(Format format) {
    float captureFrameRate = getCaptureFrameRate(format);
    this.baseSpeedMultiplier =
        captureFrameRate == -Float.MAX_VALUE ? 1 : captureFrameRate / INPUT_FRAME_RATE;
    this.speedsByStartTimeUs = buildSpeedByStartTimeUsMap(format, baseSpeedMultiplier);
  }

  @Override
  public float getSpeed(long timeUs) {
    @Nullable Map.Entry<Long, Float> entry = speedsByStartTimeUs.floorEntry(timeUs);
    return entry != null ? entry.getValue() : baseSpeedMultiplier;
  }

  @Override
  public long getNextSpeedChangeTimeUs(long timeUs) {
    @Nullable Long nextTimeUs = speedsByStartTimeUs.higherKey(timeUs);
    return nextTimeUs != null ? nextTimeUs : Long.MIN_VALUE + 1;
  }

  private static ImmutableSortedMap<Long, Float> buildSpeedByStartTimeUsMap(
      Format format, float baseSpeed) {
    List<Segment> segments = extractSlowMotionSegments(format);

    if (segments.isEmpty()) {
      return ImmutableSortedMap.of();
    }

    TreeMap<Long, Float> speedsByStartTimeUs = new TreeMap<>();

    // Start time maps to the segment speed.
    for (int i = 0; i < segments.size(); i++) {
      Segment currentSegment = segments.get(i);
      speedsByStartTimeUs.put(
          msToUs(currentSegment.startTimeMs), baseSpeed / currentSegment.speedDivisor);
    }

    // If the map has an entry at endTime, this is the next segments start time. If no such entry
    // exists, map the endTime to base speed because the times after the end time are not in a
    // segment.
    for (int i = 0; i < segments.size(); i++) {
      Segment currentSegment = segments.get(i);
      if (!speedsByStartTimeUs.containsKey(msToUs(currentSegment.endTimeMs))) {
        speedsByStartTimeUs.put(msToUs(currentSegment.endTimeMs), baseSpeed);
      }
    }

    return ImmutableSortedMap.copyOf(speedsByStartTimeUs);
  }

  private static float getCaptureFrameRate(Format format) {
    @Nullable Metadata metadata = format.metadata;
    if (metadata == null) {
      return -Float.MAX_VALUE;
    }
    for (int i = 0; i < metadata.length(); i++) {
      Metadata.Entry entry = metadata.get(i);
      if (entry instanceof SmtaMetadataEntry) {
        return ((SmtaMetadataEntry) entry).captureFrameRate;
      }
    }

    return -Float.MAX_VALUE;
  }

  private static ImmutableList<Segment> extractSlowMotionSegments(Format format) {
    List<Segment> segments = new ArrayList<>();
    @Nullable Metadata metadata = format.metadata;
    if (metadata != null) {
      for (int i = 0; i < metadata.length(); i++) {
        Metadata.Entry entry = metadata.get(i);
        if (entry instanceof SlowMotionData) {
          segments.addAll(((SlowMotionData) entry).segments);
        }
      }
    }
    return ImmutableList.sortedCopyOf(BY_START_THEN_END_THEN_DIVISOR, segments);
  }

  private static long msToUs(long timeMs) {
    return (timeMs == Long.MIN_VALUE + 1 || timeMs == Long.MIN_VALUE) ? timeMs : (timeMs * 1000);
  }

}
