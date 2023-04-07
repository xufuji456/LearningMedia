
package com.frank.videoedit.transform.entity;

import android.os.Build;
import android.util.SparseBooleanArray;

import androidx.annotation.Nullable;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class FlagSet {

  public static final class Builder {

    private final SparseBooleanArray flags;

    private boolean buildCalled;

    public Builder() {
      flags = new SparseBooleanArray();
    }

    @CanIgnoreReturnValue
    public Builder add(int flag) {
      flags.append(flag, /* value= */ true);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addIf(int flag, boolean condition) {
      if (condition) {
        return add(flag);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAll(int... flags) {
      for (int flag : flags) {
        add(flag);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAll(FlagSet flags) {
      for (int i = 0; i < flags.size(); i++) {
        add(flags.get(i));
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder remove(int flag) {
      flags.delete(flag);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder removeIf(int flag, boolean condition) {
      if (condition) {
        return remove(flag);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder removeAll(int... flags) {
      for (int flag : flags) {
        remove(flag);
      }
      return this;
    }

    public FlagSet build() {
      buildCalled = true;
      return new FlagSet(flags);
    }
  }

  private final SparseBooleanArray flags;

  private FlagSet(SparseBooleanArray flags) {
    this.flags = flags;
  }

  public boolean contains(int flag) {
    return flags.get(flag);
  }

  public boolean containsAny(int... flags) {
    for (int flag : flags) {
      if (contains(flag)) {
        return true;
      }
    }
    return false;
  }

  public int size() {
    return flags.size();
  }

  public int get(int index) {
    return flags.keyAt(index);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FlagSet)) {
      return false;
    }
    FlagSet that = (FlagSet) o;
    if (Build.VERSION.SDK_INT < 24) {
      if (size() != that.size()) {
        return false;
      }
      for (int i = 0; i < size(); i++) {
        if (get(i) != that.get(i)) {
          return false;
        }
      }
      return true;
    } else {
      return flags.equals(that.flags);
    }
  }

  @Override
  public int hashCode() {
    if (Build.VERSION.SDK_INT < 24) {
      int hashCode = size();
      for (int i = 0; i < size(); i++) {
        hashCode = 31 * hashCode + get(i);
      }
      return hashCode;
    } else {
      return flags.hashCode();
    }
  }
}
