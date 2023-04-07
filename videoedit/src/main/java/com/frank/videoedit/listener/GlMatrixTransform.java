
package com.frank.videoedit.listener;

import android.content.Context;
import android.util.Pair;

import com.frank.videoedit.effect.MatrixTextureProcessor;
import com.frank.videoedit.effect.TextureProcessorBase;
import com.google.common.collect.ImmutableList;

public interface GlMatrixTransform extends GlEffect {

  default Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return Pair.create(inputWidth, inputHeight);
  }

  float[] getGlMatrixArray(long presentationTimeUs);

  @Override
  default TextureProcessorBase toGlTextureProcessor(Context context, boolean useHdr) {
    return MatrixTextureProcessor.create(
        context,
        ImmutableList.of(this),
        useHdr);
  }
}
