#version 100

// ES 2 fragment shader that samples from a (non-external) texture with
// uTexSampler, copying from this texture to the current output
// while adjusting contrast based on uContrastFactor.

precision mediump float;
uniform sampler2D uTexSampler;
uniform float uContrastFactor;
varying vec2 vTexSamplingCoord;

void main() {
    vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);

    gl_FragColor = vec4(
        uContrastFactor * (inputColor.r - 0.5) + 0.5,
        uContrastFactor * (inputColor.g - 0.5) + 0.5,
        uContrastFactor * (inputColor.b - 0.5) + 0.5,
        inputColor.a);
}
