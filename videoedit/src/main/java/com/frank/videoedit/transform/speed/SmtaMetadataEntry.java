
package com.frank.videoedit.transform.speed;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.frank.videoedit.transform.entity.Metadata;
import com.google.common.primitives.Floats;

/**
 * Stores metadata from the Samsung smta box.
 *
 */
public final class SmtaMetadataEntry implements Metadata.Entry {

  public final float captureFrameRate;
  /** The number of layers in the SVC extended frames. */
  public final int svcTemporalLayerCount;

  /** Creates an instance. */
  public SmtaMetadataEntry(float captureFrameRate, int svcTemporalLayerCount) {
    this.captureFrameRate = captureFrameRate;
    this.svcTemporalLayerCount = svcTemporalLayerCount;
  }

  private SmtaMetadataEntry(Parcel in) {
    captureFrameRate = in.readFloat();
    svcTemporalLayerCount = in.readInt();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    SmtaMetadataEntry other = (SmtaMetadataEntry) obj;
    return captureFrameRate == other.captureFrameRate
        && svcTemporalLayerCount == other.svcTemporalLayerCount;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Floats.hashCode(captureFrameRate);
    result = 31 * result + svcTemporalLayerCount;
    return result;
  }

  @Override
  public String toString() {
    return "smta: captureFrameRate="
        + captureFrameRate
        + ", svcTemporalLayerCount="
        + svcTemporalLayerCount;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(captureFrameRate);
    dest.writeInt(svcTemporalLayerCount);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<SmtaMetadataEntry> CREATOR =
      new Parcelable.Creator<SmtaMetadataEntry>() {

        @Override
        public SmtaMetadataEntry createFromParcel(Parcel in) {
          return new SmtaMetadataEntry(in);
        }

        @Override
        public SmtaMetadataEntry[] newArray(int size) {
          return new SmtaMetadataEntry[size];
        }
      };
}
