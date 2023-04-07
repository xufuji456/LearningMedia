
package com.frank.videoedit.transform;

import com.frank.videoedit.transform.clock.HandlerWrapper;
import com.frank.videoedit.transform.entity.ListenerSet;
import com.frank.videoedit.transform.entity.MediaItem;

public final class FallbackListener {

  private final MediaItem mediaItem;
  private final TransformationRequest originalTransformationRequest;
  private final ListenerSet<Transformer.Listener> transformerListeners;
  private final HandlerWrapper transformerListenerHandler;

  private TransformationRequest fallbackTransformationRequest;
  private int trackCount;

  public FallbackListener(
      MediaItem mediaItem,
      ListenerSet<Transformer.Listener> transformerListeners,
      HandlerWrapper transformerListenerHandler,
      TransformationRequest originalTransformationRequest) {
    this.mediaItem                     = mediaItem;
    this.transformerListeners          = transformerListeners;
    this.transformerListenerHandler    = transformerListenerHandler;
    this.originalTransformationRequest = originalTransformationRequest;
    this.fallbackTransformationRequest = originalTransformationRequest;
  }

  public void registerTrack() {
    trackCount++;
  }

  public void onTransformationRequestFinalized(TransformationRequest transformationRequest) {

    TransformationRequest.Builder fallbackRequestBuilder =
        fallbackTransformationRequest.buildUpon();
    if (transformationRequest.audioMimeType !=null
            && !transformationRequest.audioMimeType.equals(originalTransformationRequest.audioMimeType)) {
      fallbackRequestBuilder.setAudioMimeType(transformationRequest.audioMimeType);
    }
    if (transformationRequest.videoMimeType != null
            && !transformationRequest.videoMimeType.equals(originalTransformationRequest.videoMimeType)) {
      fallbackRequestBuilder.setVideoMimeType(transformationRequest.videoMimeType);
    }
    if (transformationRequest.outputHeight != originalTransformationRequest.outputHeight) {
      fallbackRequestBuilder.setResolution(transformationRequest.outputHeight);
    }
    if (transformationRequest.enableHdrEditing != originalTransformationRequest.enableHdrEditing) {
      fallbackRequestBuilder.experimental_setEnableHdrEditing(
          transformationRequest.enableHdrEditing);
    }
    if (transformationRequest.enableRequestSdrToneMapping
        != originalTransformationRequest.enableRequestSdrToneMapping) {
      fallbackRequestBuilder.setEnableRequestSdrToneMapping(
          transformationRequest.enableRequestSdrToneMapping);
    }
    TransformationRequest newFallbackTransformationRequest = fallbackRequestBuilder.build();
    fallbackTransformationRequest = newFallbackTransformationRequest;

    if (trackCount == 0 && !originalTransformationRequest.equals(fallbackTransformationRequest)) {
      transformerListenerHandler.post(
          () ->
              transformerListeners.sendEvent(
                  -1,
                  listener ->
                      listener.onFallbackApplied(
                          mediaItem,
                          originalTransformationRequest,
                          newFallbackTransformationRequest)));
    }
  }
}
