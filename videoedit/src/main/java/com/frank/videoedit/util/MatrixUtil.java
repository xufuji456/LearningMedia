
package com.frank.videoedit.util;

import android.opengl.Matrix;
import android.util.Pair;

import com.frank.videoedit.listener.GlMatrixTransform;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;

public final class MatrixUtil {

  private static final float[][] NDC_CUBE =
      new float[][] {
        new float[] {1, 0, 0, 1},
        new float[] {-1, 0, 0, 1},
        new float[] {0, 1, 0, 1},
        new float[] {0, -1, 0, 1},
        new float[] {0, 0, 1, 1},
        new float[] {0, 0, -1, 1}
      };

  public static float[] getGlMatrixArray(android.graphics.Matrix matrix) {
    float[] matrix3x3Array = new float[9];
    matrix.getValues(matrix3x3Array);
    float[] matrix4x4Array = getMatrix4x4Array(matrix3x3Array);

    float[] transposedMatrix4x4Array = new float[16];
    Matrix.transposeM(
        transposedMatrix4x4Array, 0, matrix4x4Array, 0);

    return transposedMatrix4x4Array;
  }

  /**
   * Returns a 4x4 matrix array containing the 3x3 matrix array's contents.
   *
   * <p>The 3x3 matrix array is expected to be in 2 dimensions, and the 4x4 matrix array is expected
   * to be in 3 dimensions. The output will have the third row/column's values be an identity
   * matrix's values, so that vertex transformations using this matrix will not affect the z axis.
   * <br>
   * Input format: [a, b, c, d, e, f, g, h, i] <br>
   * Output format: [a, b, 0, c, d, e, 0, f, 0, 0, 1, 0, g, h, 0, i]
   */
  private static float[] getMatrix4x4Array(float[] matrix3x3Array) {
    float[] matrix4x4Array = new float[16];
    matrix4x4Array[10] = 1;
    for (int inputRow = 0; inputRow < 3; inputRow++) {
      for (int inputColumn = 0; inputColumn < 3; inputColumn++) {
        int outputRow = (inputRow == 2) ? 3 : inputRow;
        int outputColumn = (inputColumn == 2) ? 3 : inputColumn;
        matrix4x4Array[outputRow * 4 + outputColumn] = matrix3x3Array[inputRow * 3 + inputColumn];
      }
    }
    return matrix4x4Array;
  }


  public static ImmutableList<float[]> clipConvexPolygonToNdcRange(
      ImmutableList<float[]> polygonVertices) {
    ImmutableList.Builder<float[]> outputVertices =
        new ImmutableList.Builder<float[]>().addAll(polygonVertices);
    for (float[] clippingPlane : NDC_CUBE) {
      ImmutableList<float[]> inputVertices = outputVertices.build();
      outputVertices = new ImmutableList.Builder<>();

      for (int i = 0; i < inputVertices.size(); i++) {
        float[] currentVertex = inputVertices.get(i);
        float[] previousVertex =
            inputVertices.get((inputVertices.size() + i - 1) % inputVertices.size());
        if (isInsideClippingHalfSpace(currentVertex, clippingPlane)) {
          if (!isInsideClippingHalfSpace(previousVertex, clippingPlane)) {
            float[] intersectionPoint =
                computeIntersectionPoint(
                    clippingPlane, clippingPlane, previousVertex, currentVertex);
            if (!Arrays.equals(currentVertex, intersectionPoint)) {
              outputVertices.add(intersectionPoint);
            }
          }
          outputVertices.add(currentVertex);
        } else if (isInsideClippingHalfSpace(previousVertex, clippingPlane)) {
          float[] intersection =
              computeIntersectionPoint(clippingPlane, clippingPlane, previousVertex, currentVertex);
          if (!Arrays.equals(previousVertex, intersection)) {
            outputVertices.add(intersection);
          }
        }
      }
    }

    return outputVertices.build();
  }


  private static boolean isInsideClippingHalfSpace(float[] point, float[] clippingPlane) {

    return clippingPlane[0] * point[0] + clippingPlane[1] * point[1] + clippingPlane[2] * point[2]
        <= clippingPlane[3];
  }

  private static float[] computeIntersectionPoint(
      float[] planePoint, float[] planeParameters, float[] linePoint1, float[] linePoint2) {

    float lineEquationParameter =
        ((planePoint[0] - linePoint1[0]) * planeParameters[0]
                + (planePoint[1] - linePoint1[1]) * planeParameters[1]
                + (planePoint[2] - linePoint1[2]) * planeParameters[2])
            / ((linePoint2[0] - linePoint1[0]) * planeParameters[0]
                + (linePoint2[1] - linePoint1[1]) * planeParameters[1]
                + (linePoint2[2] - linePoint1[2]) * planeParameters[2]);
    float x = linePoint1[0] + (linePoint2[0] - linePoint1[0]) * lineEquationParameter;
    float y = linePoint1[1] + (linePoint2[1] - linePoint1[1]) * lineEquationParameter;
    float z = linePoint1[2] + (linePoint2[2] - linePoint1[2]) * lineEquationParameter;
    return new float[] {x, y, z, 1};
  }

  public static ImmutableList<float[]> transformPoints(
      float[] transformationMatrix, ImmutableList<float[]> points) {
    ImmutableList.Builder<float[]> transformedPoints = new ImmutableList.Builder<>();
    for (int i = 0; i < points.size(); i++) {
      float[] transformedPoint = new float[4];
      Matrix.multiplyMV(
          transformedPoint,
          0,
          transformationMatrix,
          0,
          points.get(i),
          0);

      transformedPoint[0] /= transformedPoint[3];
      transformedPoint[1] /= transformedPoint[3];
      transformedPoint[2] /= transformedPoint[3];
      transformedPoint[3] = 1;
      transformedPoints.add(transformedPoint);
    }
    return transformedPoints.build();
  }

  public static Pair<Integer, Integer> configureAndGetOutputSize(
      int inputWidth,
      int inputHeight,
      ImmutableList<GlMatrixTransform> matrixTransformations) {

    Pair<Integer, Integer> outputSize = Pair.create(inputWidth, inputHeight);
    for (int i = 0; i < matrixTransformations.size(); i++) {
      outputSize = matrixTransformations.get(i).configure(outputSize.first, outputSize.second);
    }

    return outputSize;
  }

  private MatrixUtil() {}
}
