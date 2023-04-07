#version 100

// ES 2 fragment shader that samples from a (non-external) texture with uTexSampler,
// copying from this texture to the current output while applying a vignette effect
// by linearly darkening the pixels between uInnerRadius and uOuterRadius.

precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uCenter;
uniform float uInnerRadius;
uniform float uOuterRadius;
varying vec2 vTexSamplingCoord;
void main() {
  vec3 src = texture2D(uTexSampler, vTexSamplingCoord).xyz;
  float dist = distance(vTexSamplingCoord, uCenter);
  float scale = clamp(1.0 - (dist - uInnerRadius) / (uOuterRadius - uInnerRadius), 0.0, 1.0);
  gl_FragColor = vec4(src.r * scale, src.g * scale, src.b * scale, 1.0);
}
