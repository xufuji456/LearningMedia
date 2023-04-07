
package com.frank.videoedit.effect;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Pair;

import com.frank.videoedit.util.GlProgram;
import com.frank.videoedit.util.GlUtil;

import java.io.IOException;

public final class ContrastProcessor extends TextureProcessorBase {
  private static final String VERTEX_SHADER_PATH   = "vertex_transform_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "fragment_contrast_es2.glsl";

  private final GlProgram glProgram;

  public ContrastProcessor(Context context, Contrast contrastEffect, boolean useHdr) {
    super(useHdr);
    // Use 1.0001f to avoid division by zero issues.
    float contrastFactor = (1 + contrastEffect.contrast) / (1.0001f - contrastEffect.contrast);

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new RuntimeException(e);
    }

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
    glProgram.setFloatUniform("uContrastFactor", contrastFactor);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return Pair.create(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) {
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0);
      glProgram.bindAttributesAndUniforms();

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
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

}
