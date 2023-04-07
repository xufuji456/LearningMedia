
package com.frank.videoedit.transform.clock;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

public interface Clock {

  Clock DEFAULT = new SystemClock();

  long currentTimeMillis();

  long elapsedRealtime();

  long uptimeMillis();

  HandlerWrapper createHandler(Looper looper, @Nullable Handler.Callback callback);

  void onThreadBlocked();
}
