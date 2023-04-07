
package com.frank.videoedit.transform.clock;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;

import androidx.annotation.Nullable;

public class SystemClock implements Clock {

  protected SystemClock() {}

  @Override
  public long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  @Override
  public long elapsedRealtime() {
    return android.os.SystemClock.elapsedRealtime();
  }

  @Override
  public long uptimeMillis() {
    return android.os.SystemClock.uptimeMillis();
  }

  @Override
  public HandlerWrapper createHandler(Looper looper, @Nullable Callback callback) {
    return new SystemHandlerWrapper(new Handler(looper, callback));
  }

  @Override
  public void onThreadBlocked() {

  }
}
