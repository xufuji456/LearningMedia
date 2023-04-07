#version 100

// ES 2 fragment shader that overlays the bitmap from uTexSampler1 over a video
// frame from uTexSampler0.

precision mediump float;
// Texture containing an input video frame.
uniform sampler2D uTexSampler0;
// Texture containing the overlap bitmap.
uniform sampler2D uTexSampler1;
// Horizontal scaling factor for the overlap bitmap.
uniform float uScaleX;
// Vertical scaling factory for the overlap bitmap.
uniform float uScaleY;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 videoColor = texture2D(uTexSampler0, vTexSamplingCoord);
  vec4 overlayColor = texture2D(uTexSampler1,
                                vec2(vTexSamplingCoord.x * uScaleX,
                                     vTexSamplingCoord.y * uScaleY));
  // Blend the video decoder output and the overlay bitmap.
  gl_FragColor = videoColor * (1.0 - overlayColor.a)
      + overlayColor * overlayColor.a;
}
