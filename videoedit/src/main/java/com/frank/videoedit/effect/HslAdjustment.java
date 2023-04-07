
package com.frank.videoedit.effect;

import android.content.Context;

import com.frank.videoedit.listener.GlEffect;


/** Adjusts the HSL (Hue, Saturation, and Brightness) of a frame. */
public class HslAdjustment implements GlEffect {

  public static final class Builder {
    private float hueAdjustment;
    private float saturationAdjustment;
    private float lightnessAdjustment;

    public Builder() {}

    public Builder adjustHue(float hueAdjustmentDegrees) {
      hueAdjustment = hueAdjustmentDegrees % 360;
      return this;
    }

    public Builder adjustSaturation(float saturationAdjustment) {
      if (saturationAdjustment < -100 || saturationAdjustment > 100) {
        throw new IllegalArgumentException(
                "saturation is from -100 to 100, but provided " + saturationAdjustment);
      }
      this.saturationAdjustment = saturationAdjustment;
      return this;
    }

    public Builder adjustLightness(float lightnessAdjustment) {
      if (lightnessAdjustment < -100 || lightnessAdjustment > 100) {
        throw new IllegalArgumentException(
                "Brightness is from -100 to 100, but provided " + lightnessAdjustment);
      }
      this.lightnessAdjustment = lightnessAdjustment;
      return this;
    }

    public HslAdjustment build() {
      return new HslAdjustment(hueAdjustment, saturationAdjustment, lightnessAdjustment);
    }
  }

  public final float hueAdjustmentDegrees;
  public final float saturationAdjustment;
  public final float lightnessAdjustment;

  private HslAdjustment(
      float hueAdjustmentDegrees, float saturationAdjustment, float lightnessAdjustment) {
    this.hueAdjustmentDegrees = hueAdjustmentDegrees;
    this.saturationAdjustment = saturationAdjustment;
    this.lightnessAdjustment  = lightnessAdjustment;
  }

  @Override
  public TextureProcessorBase toGlTextureProcessor(Context context, boolean useHdr) {
    return new HslProcessor(context, this, useHdr);
  }

}
