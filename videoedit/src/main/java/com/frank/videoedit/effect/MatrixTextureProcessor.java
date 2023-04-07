
package com.frank.videoedit.effect;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Pair;

import com.frank.videoedit.listener.ExternalTextureProcessor;
import com.frank.videoedit.listener.GlMatrixTransform;
import com.frank.videoedit.transform.Format;
import com.frank.videoedit.util.GlProgram;
import com.frank.videoedit.util.MatrixUtil;
import com.frank.videoedit.util.GlUtil;
import com.frank.videoedit.entity.ColorInfo;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class MatrixTextureProcessor extends TextureProcessorBase
    implements ExternalTextureProcessor {

  private static final String VERTEX_TRANSFORM_PATH     = "vertex_transform_es2.glsl";
  private static final String VERTEX_TRANSFORM_ES3_PATH = "vertex_transform_es3.glsl";
  private static final String FRAGMENT_OETF_ES3_PATH    = "fragment_oetf_es3.glsl";
  private static final String FRAGMENT_TRANSFORM_PATH   = "fragment_transform_es2.glsl";
  private static final String FRAGMENT_TRANSFORM_SDR_OETF_ES2_PATH = "fragment_transform_sdr_oetf_es2.glsl";
  private static final String FRAGMENT_TRANSFORM_SDR_EXTERNAL_PATH = "fragment_transform_sdr_external_es2.glsl";
  private static final String FRAGMENT_TRANSFORM_EXTERNAL_YUV_ES3_PATH = "fragment_transform_external_yuv_es3.glsl";

  private static final ImmutableList<float[]> NDC_SQUARE =
      ImmutableList.of(
          new float[] {-1, -1, 0, 1},
          new float[] {-1, 1, 0, 1},
          new float[] {1, 1, 0, 1},
          new float[] {1, -1, 0, 1});

  // YUV to RGB color transform coefficients can be calculated from the BT.2020 specification, by
  // inverting the RGB to YUV equations, and scaling for limited range.
  // https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.2020-2-201510-I!!PDF-E.pdf
  private static final float[] BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX = {
    1.0000f, 1.0000f, 1.0000f,
    0.0000f, -0.1646f, 1.8814f,
    1.4746f, -0.5714f, 0.0000f
  };
  private static final float[] BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX = {
    1.1689f, 1.1689f, 1.1689f,
    0.0000f, -0.1881f, 2.1502f,
    1.6853f, -0.6530f, 0.0000f,
  };

  private final ImmutableList<GlMatrixTransform> matrixTransformations;

  private final boolean useHdr;

  private final float[] tempResultMatrix;

  private final float[][] transformationMatrixCache;

  private final float[] compositeTransformationMatrixArray;

  private ImmutableList<float[]> visiblePolygon;

  private final GlProgram glProgram;

  public static MatrixTextureProcessor create(
      Context context,
      List<GlMatrixTransform> matrixTransformations,
      boolean useHdr) {
    GlProgram glProgram =
        createGlProgram(context, VERTEX_TRANSFORM_PATH, FRAGMENT_TRANSFORM_PATH);

    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        useHdr);
  }

  public static MatrixTextureProcessor createWithExternalSamplerApplyingEotf(
      Context context,
      List<GlMatrixTransform> matrixTransformations,
      ColorInfo electricalColorInfo) {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_TRANSFORM_ES3_PATH : VERTEX_TRANSFORM_PATH;
    String fragmentShaderFilePath =
        useHdr
            ? FRAGMENT_TRANSFORM_EXTERNAL_YUV_ES3_PATH
            : FRAGMENT_TRANSFORM_SDR_EXTERNAL_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    if (useHdr) {
      // In HDR editing mode the decoder output is sampled in YUV.
      if (!GlUtil.isYuvTargetExtensionSupported()) {
        throw new RuntimeException(
            "The EXT_YUV_target extension is required for HDR editing input.");
      }
      glProgram.setFloatsUniform(
          "uYuvToRgbColorTransform",
          electricalColorInfo.colorRange == ColorInfo.COLOR_RANGE_FULL
              ? BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX
              : BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX);

      @ColorInfo.ColorTransfer int colorTransfer = electricalColorInfo.colorTransfer;
      if (colorTransfer != ColorInfo.COLOR_TRANSFER_HLG
              && colorTransfer != ColorInfo.COLOR_TRANSFER_ST2084) {
        throw new IllegalArgumentException("invalid color transfer:" + colorTransfer);
      }
      glProgram.setIntUniform("uEotfColorTransfer", colorTransfer);
    } else {
      glProgram.setIntUniform("uApplyOetf", 0);
    }

    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        useHdr);
  }

  public static MatrixTextureProcessor createApplyingOetf(
      Context context,
      List<GlMatrixTransform> matrixTransformations,
      ColorInfo electricalColorInfo) {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_TRANSFORM_ES3_PATH : VERTEX_TRANSFORM_PATH;
    String fragmentShaderFilePath =
        useHdr ? FRAGMENT_OETF_ES3_PATH : FRAGMENT_TRANSFORM_SDR_OETF_ES2_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    if (useHdr) {
      @ColorInfo.ColorTransfer int colorTransfer = electricalColorInfo.colorTransfer;
      if (colorTransfer != ColorInfo.COLOR_TRANSFER_HLG
              && colorTransfer != ColorInfo.COLOR_TRANSFER_ST2084) {
        throw new IllegalArgumentException("invalid color transfer:" + colorTransfer);
      }
      glProgram.setIntUniform("uOetfColorTransfer", colorTransfer);
    }

    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        useHdr);
  }

  public static MatrixTextureProcessor createWithExternalSamplerApplyingEotfThenOetf(
      Context context,
      List<GlMatrixTransform> matrixTransformations,
      ColorInfo electricalColorInfo) {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_TRANSFORM_ES3_PATH : VERTEX_TRANSFORM_PATH;
    String fragmentShaderFilePath =
        useHdr
            ? FRAGMENT_TRANSFORM_EXTERNAL_YUV_ES3_PATH
            : FRAGMENT_TRANSFORM_SDR_EXTERNAL_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    if (useHdr) {
      // In HDR editing mode the decoder output is sampled in YUV.
      if (!GlUtil.isYuvTargetExtensionSupported()) {
        throw new RuntimeException(
            "The EXT_YUV_target extension is required for HDR editing input.");
      }
      glProgram.setFloatsUniform(
          "uYuvToRgbColorTransform",
          electricalColorInfo.colorRange == ColorInfo.COLOR_RANGE_FULL
              ? BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX
              : BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX);

      // No transfer functions needed, because the EOTF and OETF cancel out.
      glProgram.setIntUniform("uEotfColorTransfer", Format.NO_VALUE);
    } else {
      glProgram.setIntUniform("uApplyOetf", 1);
    }

    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        useHdr);
  }

  private MatrixTextureProcessor(
      GlProgram glProgram,
      ImmutableList<GlMatrixTransform> matrixTransformations,
      boolean useHdr) {
    super(useHdr);
    this.glProgram = glProgram;
    this.matrixTransformations = matrixTransformations;
    this.useHdr = useHdr;

    transformationMatrixCache = new float[matrixTransformations.size()][16];
    compositeTransformationMatrixArray = GlUtil.create4x4IdentityMatrix();
    tempResultMatrix = new float[16];
    visiblePolygon = NDC_SQUARE;
  }

  private static GlProgram createGlProgram(
      Context context, String vertexShaderFilePath, String fragmentShaderFilePath) {

    GlProgram glProgram;
    try {
      glProgram = new GlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);
    } catch (IOException | GlUtil.GlException e) {
      throw new RuntimeException(e);
    }

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
    return glProgram;
  }

  @Override
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    glProgram.setFloatsUniform("uTexTransformationMatrix", textureTransformMatrix);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return MatrixUtil.configureAndGetOutputSize(inputWidth, inputHeight, matrixTransformations);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) {
    updateCompositeTransformationMatrixAndVisiblePolygon(presentationTimeUs);
    if (visiblePolygon.size() < 3) {
      return; // Need at least three visible vertices for a triangle.
    }

    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.setFloatsUniform("uTransformationMatrix", compositeTransformationMatrixArray);
      glProgram.setBufferAttribute(
          "aFramePosition",
          GlUtil.createVertexBuffer(visiblePolygon),
          GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
      glProgram.bindAttributesAndUniforms();
      GLES20.glDrawArrays(
          GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* count= */ visiblePolygon.size());
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void release() {
    super.release();
    try {
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Updates {@link #compositeTransformationMatrixArray} and {@link #visiblePolygon} based on the
   * given frame timestamp.
   */
  private void updateCompositeTransformationMatrixAndVisiblePolygon(long presentationTimeUs) {
    float[][] matricesAtPresentationTime = new float[matrixTransformations.size()][16];
    for (int i = 0; i < matrixTransformations.size(); i++) {
      matricesAtPresentationTime[i] =
          matrixTransformations.get(i).getGlMatrixArray(presentationTimeUs);
    }

    if (!updateMatrixCache(transformationMatrixCache, matricesAtPresentationTime)) {
      return;
    }

    GlUtil.setToIdentity(compositeTransformationMatrixArray);
    visiblePolygon = NDC_SQUARE;
    for (float[] transformationMatrix : transformationMatrixCache) {
      Matrix.multiplyMM(tempResultMatrix, 0, transformationMatrix, 0,
          compositeTransformationMatrixArray, 0);
      System.arraycopy(
          tempResultMatrix, 0,
          compositeTransformationMatrixArray, 0, tempResultMatrix.length);
      visiblePolygon =
          MatrixUtil.clipConvexPolygonToNdcRange(
              MatrixUtil.transformPoints(transformationMatrix, visiblePolygon));
      if (visiblePolygon.size() < 3) {
        return;
      }
    }

    Matrix.invertM(tempResultMatrix, 0, compositeTransformationMatrixArray, 0);
    visiblePolygon = MatrixUtil.transformPoints(tempResultMatrix, visiblePolygon);
  }

  /**
   * Updates the {@code cachedMatrices} with the {@code newMatrices}. Returns whether a matrix has
   * changed inside the cache.
   *
   * @param cachedMatrices The existing cached matrices. Gets updated if it is out of date.
   * @param newMatrices The new matrices to compare the cached matrices against.
   */
  private static boolean updateMatrixCache(float[][] cachedMatrices, float[][] newMatrices) {
    boolean matrixChanged = false;
    for (int i = 0; i < cachedMatrices.length; i++) {
      float[] cachedMatrix = cachedMatrices[i];
      float[] newMatrix = newMatrices[i];
      if (!Arrays.equals(cachedMatrix, newMatrix)) {
        System.arraycopy(
            newMatrix, 0, cachedMatrix, 0, newMatrix.length);
        matrixChanged = true;
      }
    }
    return matrixChanged;
  }

}
