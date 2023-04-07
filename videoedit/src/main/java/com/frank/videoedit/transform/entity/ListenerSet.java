
package com.frank.videoedit.transform.entity;

import android.os.Looper;
import android.os.Message;

import androidx.annotation.CheckResult;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.frank.videoedit.transform.clock.Clock;
import com.frank.videoedit.transform.clock.HandlerWrapper;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayDeque;
import java.util.concurrent.CopyOnWriteArraySet;

public final class ListenerSet<T extends @NonNull Object> {

  public interface Event<T> {

    void invoke(T listener);
  }

  public interface IterationFinishedEvent<T> {

    void invoke(T listener, FlagSet eventFlags);
  }

  private static final int MSG_ITERATION_FINISHED = 0;

  private final Clock clock;
  private final HandlerWrapper handler;
  private final IterationFinishedEvent<T> iterationFinishedEvent;
  private final CopyOnWriteArraySet<ListenerHolder<T>> listeners;
  private final ArrayDeque<Runnable> flushingEvents;
  private final ArrayDeque<Runnable> queuedEvents;
  private final Object releasedLock;

  @GuardedBy("releasedLock")
  private boolean released;

  private boolean throwsWhenUsingWrongThread;

  public ListenerSet(Looper looper, Clock clock, IterationFinishedEvent<T> iterationFinishedEvent) {
    this(/* listeners= */ new CopyOnWriteArraySet<>(), looper, clock, iterationFinishedEvent);
  }

  private ListenerSet(
      CopyOnWriteArraySet<ListenerHolder<T>> listeners,
      Looper looper,
      Clock clock,
      IterationFinishedEvent<T> iterationFinishedEvent) {
    this.clock = clock;
    this.listeners = listeners;
    this.iterationFinishedEvent = iterationFinishedEvent;
    releasedLock   = new Object();
    flushingEvents = new ArrayDeque<>();
    queuedEvents   = new ArrayDeque<>();

    HandlerWrapper handler = clock.createHandler(looper, this::handleMessage);
    this.handler = handler;
    throwsWhenUsingWrongThread = true;
  }

  @CheckResult
  public ListenerSet<T> copy(Looper looper, IterationFinishedEvent<T> iterationFinishedEvent) {
    return copy(looper, clock, iterationFinishedEvent);
  }

  @CheckResult
  public ListenerSet<T> copy(
      Looper looper, Clock clock, IterationFinishedEvent<T> iterationFinishedEvent) {
    return new ListenerSet<>(listeners, looper, clock, iterationFinishedEvent);
  }

  public void add(T listener) {
    synchronized (releasedLock) {
      if (released) {
        return;
      }
      listeners.add(new ListenerHolder<>(listener));
    }
  }

  public void remove(T listener) {
    verifyCurrentThread();
    for (ListenerHolder<T> listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release(iterationFinishedEvent);
        listeners.remove(listenerHolder);
      }
    }
  }

  public void clear() {
    verifyCurrentThread();
    listeners.clear();
  }

  public int size() {
    verifyCurrentThread();
    return listeners.size();
  }

  public void queueEvent(int eventFlag, Event<T> event) {
    verifyCurrentThread();
    CopyOnWriteArraySet<ListenerHolder<T>> listenerSnapshot = new CopyOnWriteArraySet<>(listeners);
    queuedEvents.add(
        () -> {
          for (ListenerHolder<T> holder : listenerSnapshot) {
            holder.invoke(eventFlag, event);
          }
        });
  }

  public void flushEvents() {
    verifyCurrentThread();
    if (queuedEvents.isEmpty()) {
      return;
    }
    if (!handler.hasMessages(MSG_ITERATION_FINISHED)) {
      handler.sendMessageAtFrontOfQueue(handler.obtainMessage(MSG_ITERATION_FINISHED));
    }
    boolean recursiveFlushInProgress = !flushingEvents.isEmpty();
    flushingEvents.addAll(queuedEvents);
    queuedEvents.clear();
    if (recursiveFlushInProgress) {
      return;
    }
    while (!flushingEvents.isEmpty()) {
      flushingEvents.peekFirst().run();
      flushingEvents.removeFirst();
    }
  }

  public void sendEvent(int eventFlag, Event<T> event) {
    queueEvent(eventFlag, event);
    flushEvents();
  }

  public void release() {
    verifyCurrentThread();
    synchronized (releasedLock) {
      released = true;
    }
    for (ListenerHolder<T> listenerHolder : listeners) {
      listenerHolder.release(iterationFinishedEvent);
    }
    listeners.clear();
  }

  @Deprecated
  public void setThrowsWhenUsingWrongThread(boolean throwsWhenUsingWrongThread) {
    this.throwsWhenUsingWrongThread = throwsWhenUsingWrongThread;
  }

  private boolean handleMessage(Message message) {
    for (ListenerHolder<T> holder : listeners) {
      holder.iterationFinished(iterationFinishedEvent);
      if (handler.hasMessages(MSG_ITERATION_FINISHED)) {
        break;
      }
    }
    return true;
  }

  private void verifyCurrentThread() {
    if (!throwsWhenUsingWrongThread) {
      return;
    }
    if (Thread.currentThread() != handler.getLooper().getThread()) {
      throw new IllegalStateException("is not current thread!");
    }
  }

  private static final class ListenerHolder<T extends @NonNull Object> {

    public final T listener;

    private FlagSet.Builder flagsBuilder;
    private boolean needsIterationFinishedEvent;
    private boolean released;

    public ListenerHolder(T listener) {
      this.listener = listener;
      this.flagsBuilder = new FlagSet.Builder();
    }

    public void release(IterationFinishedEvent<T> event) {
      released = true;
      if (needsIterationFinishedEvent) {
        needsIterationFinishedEvent = false;
        event.invoke(listener, flagsBuilder.build());
      }
    }

    public void invoke(int eventFlag, Event<T> event) {
      if (!released) {
        if (eventFlag != -1) {
          flagsBuilder.add(eventFlag);
        }
        needsIterationFinishedEvent = true;
        event.invoke(listener);
      }
    }

    public void iterationFinished(IterationFinishedEvent<T> event) {
      if (!released && needsIterationFinishedEvent) {
        FlagSet flagsToNotify = flagsBuilder.build();
        flagsBuilder = new FlagSet.Builder();
        needsIterationFinishedEvent = false;
        event.invoke(listener, flagsToNotify);
      }
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      return listener.equals(((ListenerHolder<?>) other).listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }
}
