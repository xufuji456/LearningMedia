
package com.frank.videoedit.effect;

import android.util.Pair;

import androidx.annotation.CallSuper;

import com.frank.videoedit.entity.TextureInfo;
import com.frank.videoedit.listener.GlTextureProcessor;
import com.frank.videoedit.util.GlUtil;

public abstract class TextureProcessorBase implements GlTextureProcessor {

  private final boolean useHdr;

  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private int inputWidth;
  private int inputHeight;
  private TextureInfo outputTexture;
  private boolean outputTextureInUse;

  public TextureProcessorBase(boolean useHdr) {
    this.useHdr    = useHdr;
    inputListener  = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener  = (frameProcessingException) -> {};
  }

  public abstract Pair<Integer, Integer> configure(int inputWidth, int inputHeight);

  public abstract void drawFrame(int inputTexId, long presentationTimeUs);

  @Override
  public final void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (!outputTextureInUse) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public final void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public final void setErrorListener(ErrorListener errorListener) {
    this.errorListener = errorListener;
  }

  @Override
  public final void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {

    try {
      if (outputTexture == null
          || inputTexture.width != inputWidth
          || inputTexture.height != inputHeight) {
        configureOutputTexture(inputTexture.width, inputTexture.height);
      }
      outputTextureInUse = true;
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      GlUtil.clearOutputFrame();
      drawFrame(inputTexture.texId, presentationTimeUs);
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (GlUtil.GlException | RuntimeException e) {
      errorListener.onFrameProcessingError(new RuntimeException(e));
    }
  }

  private void configureOutputTexture(int inputWidth, int inputHeight) throws GlUtil.GlException {
    this.inputWidth = inputWidth;
    this.inputHeight = inputHeight;
    Pair<Integer, Integer> outputSize = configure(inputWidth, inputHeight);
    if (outputTexture == null
        || outputSize.first != outputTexture.width
        || outputSize.second != outputTexture.height) {
      if (outputTexture != null) {
        GlUtil.deleteTexture(outputTexture.texId);
      }
      int outputTexId = GlUtil.createTexture(outputSize.first, outputSize.second, useHdr);
      int outputFboId = GlUtil.createFboForTexture(outputTexId);
      outputTexture = new TextureInfo(outputTexId, outputFboId, outputSize.first, outputSize.second);
    }
  }

  @Override
  public final void releaseOutputFrame(TextureInfo outputTexture) {
    outputTextureInUse = false;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public final void signalEndOfCurrentInputStream() {
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  @CallSuper
  public void release() {
    if (outputTexture != null) {
      try {
        GlUtil.deleteTexture(outputTexture.texId);
      } catch (GlUtil.GlException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
