#version 100

// ES 2 fragment shader that samples from a (non-external) texture with
// uTexSampler, copying from this texture to the current output while
// applying a 4x4 RGB color matrix to change the pixel colors.

precision mediump float;
uniform sampler2D uTexSampler;
uniform mat4 uRgbMatrix;
varying vec2 vTexSamplingCoord;

void main() {
    vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);
    gl_FragColor = /*uRgbMatrix * */vec4(inputColor.rgb, 1);
    gl_FragColor.a = inputColor.a;
}
