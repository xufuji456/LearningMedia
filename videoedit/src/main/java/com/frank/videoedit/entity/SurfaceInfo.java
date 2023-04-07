
package com.frank.videoedit.entity;

import android.view.Surface;

public final class SurfaceInfo {

  public final Surface surface;

  public final int width;

  public final int height;

  public final int orientationDegrees;

  public SurfaceInfo(Surface surface, int width, int height) {
    this(surface, width, height, 0);
  }

  public SurfaceInfo(Surface surface, int width, int height, int orientationDegrees) {
    this.width              = width;
    this.height             = height;
    this.surface            = surface;
    this.orientationDegrees = orientationDegrees;
  }

}
