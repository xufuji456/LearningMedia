
package com.frank.videoedit.transform.clock;

import android.os.Looper;

import androidx.annotation.Nullable;

public interface HandlerWrapper {

  interface Message {

    void sendToTarget();

    HandlerWrapper getTarget();
  }

  Looper getLooper();

  boolean hasMessages(int what);

  Message obtainMessage(int what);

  Message obtainMessage(int what, @Nullable Object obj);

  Message obtainMessage(int what, int arg1, int arg2);

  Message obtainMessage(int what, int arg1, int arg2, @Nullable Object obj);

  boolean sendMessageAtFrontOfQueue(Message message);

  boolean sendEmptyMessage(int what);

  boolean sendEmptyMessageDelayed(int what, int delayMs);

  boolean sendEmptyMessageAtTime(int what, long uptimeMs);

  void removeMessages(int what);

  void removeCallbacksAndMessages(@Nullable Object token);

  boolean post(Runnable runnable);

  boolean postDelayed(Runnable runnable, long delayMs);

  boolean postAtFrontOfQueue(Runnable runnable);
}
