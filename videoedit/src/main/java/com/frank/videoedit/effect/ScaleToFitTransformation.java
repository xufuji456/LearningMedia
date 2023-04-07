
package com.frank.videoedit.effect;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Matrix;
import android.util.Pair;

import com.frank.videoedit.listener.MatrixTransform;
import com.frank.videoedit.util.GlUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public final class ScaleToFitTransformation implements MatrixTransform {

  public static final class Builder {

    private float scaleX;
    private float scaleY;
    private float rotationDegrees;

    public Builder() {
      scaleX = 1;
      scaleY = 1;
      rotationDegrees = 0;
    }

    @CanIgnoreReturnValue
    public Builder setScale(float scaleX, float scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setRotationDegrees(float rotationDegrees) {
      this.rotationDegrees = rotationDegrees;
      return this;
    }

    public ScaleToFitTransformation build() {
      return new ScaleToFitTransformation(scaleX, scaleY, rotationDegrees);
    }
  }

  private final Matrix transformationMatrix;
  private @MonotonicNonNull Matrix adjustedTransformationMatrix;

  private ScaleToFitTransformation(float scaleX, float scaleY, float rotationDegrees) {
    transformationMatrix = new Matrix();
    transformationMatrix.postScale(scaleX, scaleY);
    transformationMatrix.postRotate(rotationDegrees);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {

    adjustedTransformationMatrix = new Matrix(transformationMatrix);

    if (transformationMatrix.isIdentity()) {
      return Pair.create(inputWidth, inputHeight);
    }

    float inputAspectRatio = (float) inputWidth / inputHeight;

    adjustedTransformationMatrix.preScale(inputAspectRatio, 1f);
    adjustedTransformationMatrix.postScale(1f / inputAspectRatio, 1f);

    float[][] transformOnNdcPoints = {{-1, -1, 0, 1}, {-1, 1, 0, 1}, {1, -1, 0, 1}, {1, 1, 0, 1}};
    float minX = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float minY = Float.MAX_VALUE;
    float maxY = Float.MIN_VALUE;
    for (float[] transformOnNdcPoint : transformOnNdcPoints) {
      adjustedTransformationMatrix.mapPoints(transformOnNdcPoint);
      minX = min(minX, transformOnNdcPoint[0]);
      maxX = max(maxX, transformOnNdcPoint[0]);
      minY = min(minY, transformOnNdcPoint[1]);
      maxY = max(maxY, transformOnNdcPoint[1]);
    }

    float scaleX = (maxX - minX) / GlUtil.LENGTH_NDC;
    float scaleY = (maxY - minY) / GlUtil.LENGTH_NDC;
    adjustedTransformationMatrix.postScale(1f / scaleX, 1f / scaleY);
    return Pair.create(Math.round(inputWidth * scaleX), Math.round(inputHeight * scaleY));
  }

  @Override
  public Matrix getMatrix(long presentationTimeUs) {
    return adjustedTransformationMatrix;
  }
}
