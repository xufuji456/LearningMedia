
package com.frank.videoedit.listener;

import android.view.SurfaceView;

import androidx.annotation.Nullable;

public interface DebugViewProvider {

  DebugViewProvider NONE = (int width, int height) -> null;

  @Nullable
  SurfaceView getDebugPreviewSurfaceView(int width, int height);
}
