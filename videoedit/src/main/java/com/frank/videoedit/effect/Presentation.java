
package com.frank.videoedit.effect;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.graphics.Matrix;
import android.util.Pair;

import androidx.annotation.IntDef;

import com.frank.videoedit.listener.MatrixTransform;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;


public final class Presentation implements MatrixTransform {


  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({LAYOUT_SCALE_TO_FIT, LAYOUT_SCALE_TO_FIT_WITH_CROP, LAYOUT_STRETCH_TO_FIT})
  public @interface Layout {}

  public static final int LAYOUT_SCALE_TO_FIT = 0;

  public static final int LAYOUT_SCALE_TO_FIT_WITH_CROP = 1;

  public static final int LAYOUT_STRETCH_TO_FIT = 2;

  private static final float ASPECT_RATIO_UNSET = -1f;

  private static void checkLayout(@Layout int layout) {
    if(layout != LAYOUT_SCALE_TO_FIT
            && layout != LAYOUT_SCALE_TO_FIT_WITH_CROP
            && layout != LAYOUT_STRETCH_TO_FIT) {
      throw new IllegalArgumentException("invalid layout " + layout);
    }
  }

  public static Presentation createForAspectRatio(float aspectRatio, @Layout int layout) {
    checkLayout(layout);
    return new Presentation(-1, -1, aspectRatio, layout);
  }

  public static Presentation createForHeight(int height) {
    return new Presentation(-1, height, ASPECT_RATIO_UNSET, LAYOUT_SCALE_TO_FIT);
  }

  public static Presentation createForWidthAndHeight(int width, int height, @Layout int layout) {
    checkLayout(layout);
    return new Presentation(width, height, ASPECT_RATIO_UNSET, layout);
  }

  private final int requestedWidthPixels;
  private final int requestedHeightPixels;
  private float requestedAspectRatio;
  private final @Layout int layout;

  private float outputWidth;
  private float outputHeight;
  private Matrix transformationMatrix;

  private Presentation(int width, int height, float aspectRatio, @Layout int layout) {
    this.layout                = layout;
    this.requestedWidthPixels  = width;
    this.requestedHeightPixels = height;
    this.requestedAspectRatio  = aspectRatio;

    outputWidth  = -1;
    outputHeight = -1;
    transformationMatrix = new Matrix();
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    transformationMatrix = new Matrix();
    outputWidth = inputWidth;
    outputHeight = inputHeight;

    if ((requestedWidthPixels != -1) && (requestedHeightPixels != -1)) {
      requestedAspectRatio = (float) requestedWidthPixels / requestedHeightPixels;
    }

    if (requestedAspectRatio != -1) {
      applyAspectRatio();
    }

    if (requestedHeightPixels != -1) {
      if (requestedWidthPixels != -1) {
        outputWidth = requestedWidthPixels;
      } else {
        outputWidth = requestedHeightPixels * outputWidth / outputHeight;
      }
      outputHeight = requestedHeightPixels;
    }
    return Pair.create(Math.round(outputWidth), Math.round(outputHeight));
  }

  @Override
  public Matrix getMatrix(long presentationTimeUs) {
    return transformationMatrix;
  }

  private void applyAspectRatio() {
    float inputAspectRatio = outputWidth / outputHeight;
    if (layout == LAYOUT_SCALE_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = outputHeight * requestedAspectRatio;
      } else {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = outputWidth / requestedAspectRatio;
      }
    } else if (layout == LAYOUT_SCALE_TO_FIT_WITH_CROP) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = outputWidth / requestedAspectRatio;
      } else {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = outputHeight * requestedAspectRatio;
      }
    } else if (layout == LAYOUT_STRETCH_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        outputWidth = outputHeight * requestedAspectRatio;
      } else {
        outputHeight = outputWidth / requestedAspectRatio;
      }
    }
  }
}
