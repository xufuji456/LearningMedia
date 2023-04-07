
package com.frank.videoedit.listener;

import android.graphics.Matrix;

import com.frank.videoedit.util.MatrixUtil;

public interface MatrixTransform extends GlMatrixTransform {

  Matrix getMatrix(long presentationTimeUs);

  @Override
  default float[] getGlMatrixArray(long presentationTimeUs) {
    return MatrixUtil.getGlMatrixArray(getMatrix(presentationTimeUs));
  }
}
