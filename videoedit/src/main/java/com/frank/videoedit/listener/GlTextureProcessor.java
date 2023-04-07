
package com.frank.videoedit.listener;

import com.frank.videoedit.entity.TextureInfo;

public interface GlTextureProcessor {

  interface InputListener {

    default void onReadyToAcceptInputFrame() {}

    default void onInputFrameProcessed(TextureInfo inputTexture) {}
  }

  interface OutputListener {

    default void onOutputFrameAvailable(TextureInfo outputTexture, long presentationTimeUs) {}

    default void onCurrentOutputStreamEnded() {}
  }

  interface ErrorListener {
    void onFrameProcessingError(RuntimeException e);
  }

  void setInputListener(InputListener inputListener);

  void setOutputListener(OutputListener outputListener);

  void setErrorListener(ErrorListener errorListener);


  void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs);

  void releaseOutputFrame(TextureInfo outputTexture);

  void signalEndOfCurrentInputStream();

  void release();
}
