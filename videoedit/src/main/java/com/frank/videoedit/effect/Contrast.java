
package com.frank.videoedit.effect;

import android.content.Context;
import android.util.Log;

import com.frank.videoedit.listener.GlEffect;

public class Contrast implements GlEffect {

  /** Adjusts the contrast of video frames in the interval [-1, 1]. */
  public final float contrast;

  public Contrast(float contrast) {
    if (contrast < -1 || contrast > 1) {
      Log.e("ContrastEffect", "Contrast needs to be in the interval [-1, 1].");
    }
    this.contrast = contrast;
  }

  @Override
  public TextureProcessorBase toGlTextureProcessor(Context context, boolean useHdr) {
    return new ContrastProcessor(context, this, useHdr);
  }

}
