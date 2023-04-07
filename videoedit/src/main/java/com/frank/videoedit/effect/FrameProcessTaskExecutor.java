
package com.frank.videoedit.effect;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.frank.videoedit.listener.FrameProcessTask;
import com.frank.videoedit.listener.FrameProcessor;
import com.frank.videoedit.util.GlUtil;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FrameProcessTaskExecutor {

  private final ExecutorService singleThreadExecutorService;
  private final FrameProcessor.Listener listener;
  private final ConcurrentLinkedQueue<Future<?>> futures;
  private final ConcurrentLinkedQueue<FrameProcessTask> highPriorityTasks;
  private final AtomicBoolean shouldCancelTasks;

  public FrameProcessTaskExecutor(
      ExecutorService singleThreadExecutorService, FrameProcessor.Listener listener) {
    this.singleThreadExecutorService = singleThreadExecutorService;
    this.listener = listener;

    futures = new ConcurrentLinkedQueue<>();
    highPriorityTasks = new ConcurrentLinkedQueue<>();
    shouldCancelTasks = new AtomicBoolean();
  }

  public void submit(FrameProcessTask task) {
    if (shouldCancelTasks.get()) {
      return;
    }
    try {
      futures.add(wrapTaskAndSubmitToExecutorService(task));
    } catch (RejectedExecutionException e) {
      handleException(e);
    }
  }

  public void submitWithHighPriority(FrameProcessTask task) {
    if (shouldCancelTasks.get()) {
      return;
    }
    highPriorityTasks.add(task);
    submit(() -> {});
  }

  public void release(FrameProcessTask releaseTask, long releaseWaitTimeMs)
      throws InterruptedException {
    shouldCancelTasks.getAndSet(true);
    cancelNonStartedTasks();
    Future<?> releaseFuture = wrapTaskAndSubmitToExecutorService(releaseTask);
    singleThreadExecutorService.shutdown();
    try {
      if (!singleThreadExecutorService.awaitTermination(releaseWaitTimeMs, MILLISECONDS)) {
        listener.onFrameProcessingError(new RuntimeException("Release timed out"));
      }
      releaseFuture.get();
    } catch (ExecutionException e) {
      listener.onFrameProcessingError(new RuntimeException(e));
    }
  }

  private Future<?> wrapTaskAndSubmitToExecutorService(FrameProcessTask defaultPriorityTask) {
    return singleThreadExecutorService.submit(
        () -> {
          try {
            while (!highPriorityTasks.isEmpty()) {
              highPriorityTasks.remove().run();
            }
            defaultPriorityTask.run();
            removeFinishedFutures();
          } catch (GlUtil.GlException e) {
            handleException(e);
          }
        });
  }

  private void cancelNonStartedTasks() {
    while (!futures.isEmpty()) {
      futures.remove().cancel(false);
    }
  }

  private void handleException(Exception exception) {
    if (shouldCancelTasks.getAndSet(true)) {

      return;
    }
    listener.onFrameProcessingError(new RuntimeException(exception));
    cancelNonStartedTasks();
  }

  private void removeFinishedFutures() {
    while (!futures.isEmpty()) {
      if (!futures.element().isDone()) {
        return;
      }
      try {
        futures.remove().get();
      } catch (ExecutionException impossible) {
        handleException(new IllegalStateException("Unexpected error", impossible));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        handleException(e);
      }
    }
  }

}
