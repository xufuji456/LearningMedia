
package com.frank.videoedit.listener;

import android.content.Context;
import android.view.Surface;

import com.frank.videoedit.entity.ColorInfo;
import com.frank.videoedit.entity.FrameInfo;
import com.frank.videoedit.entity.SurfaceInfo;

import java.util.List;

public interface FrameProcessor {

  interface Factory {

    FrameProcessor create(
        Context context,
        Listener listener,
        List<GlEffect> effects,
        DebugViewProvider debugViewProvider,
        ColorInfo colorInfo,
        boolean releaseFramesAutomatically);
  }

  interface Listener {

    void onOutputSizeChanged(int width, int height);

    void onOutputFrameAvailable(long presentationTimeUs);

    void onFrameProcessingError(RuntimeException exception);

    void onFrameProcessingEnded();
  }

  long RELEASE_OUTPUT_FRAME_IMMEDIATELY = -1;

  long DROP_OUTPUT_FRAME = -2;

  Surface getInputSurface();

  void setInputFrameInfo(FrameInfo inputFrameInfo);

  void registerInputFrame();

  int getPendingInputFrameCount();

  void setOutputSurfaceInfo(SurfaceInfo outputSurfaceInfo);

  void releaseOutputFrame(long releaseTimeNs);

  void signalEndOfInput();

  void release();
}