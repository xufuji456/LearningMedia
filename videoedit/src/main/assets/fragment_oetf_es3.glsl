#version 300 es

// ES 3 fragment shader that:
// 1. samples optical linear BT.2020 RGB from a (non-external) texture with
//    uTexSampler, and applies a 4x4 RGB color matrix to change the pixel
//    colors,
// 2. applies the HLG or PQ OETF to yield electrical (HLG or PQ) BT.2020 RGB,
//    and
// 3. copies this converted texture color to the current output.

precision mediump float;
uniform sampler2D uTexSampler;
in vec2 vTexSamplingCoord;
out vec4 outColor;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_ST2084 and COLOR_TRANSFER_HLG are allowed.
uniform int uOetfColorTransfer;
uniform mat3 uColorTransform;
uniform mat4 uRgbMatrix;

// TODO(b/227624622): Consider using mediump to save precision, if it won't lead
//  to noticeable quantization.

// HLG OETF for one channel.
highp float hlgOetfSingleChannel(highp float linearChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=529-543;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;

  return linearChannel <= 1.0 / 12.0 ? sqrt(3.0 * linearChannel) :
      a * log(12.0 * linearChannel - b) + c;
}

// BT.2100 / BT.2020 HLG OETF.
highp vec3 hlgOetf(highp vec3 linearColor) {
  return vec3(
      hlgOetfSingleChannel(linearColor.r),
      hlgOetfSingleChannel(linearColor.g),
      hlgOetfSingleChannel(linearColor.b)
  );
}

// BT.2100 / BT.2020, PQ / ST2084 OETF.
highp vec3 pqOetf(highp vec3 linearColor) {
  // Specification:
  // https://registry.khronos.org/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_PQ
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=514-527;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float m1 = (2610.0 / 16384.0);
  const highp float m2 = (2523.0 / 4096.0) * 128.0;
  const highp float c1 = (3424.0 / 4096.0);
  const highp float c2 = (2413.0 / 4096.0) * 32.0;
  const highp float c3 = (2392.0 / 4096.0) * 32.0;

  highp vec3 temp = pow(linearColor, vec3(m1));
  temp = (c1 + c2 * temp) / (1.0 + c3 * temp);
  return pow(temp, vec3(m2));
}

// Applies the appropriate OETF to convert linear optical signals to nonlinear
// electrical signals. Input and output are both normalzied to [0, 1].
highp vec3 getElectricalColor(highp vec3 linearColor) {
  // LINT.IfChange(color_transfer)
  const int COLOR_TRANSFER_ST2084 = 6;
  return (uOetfColorTransfer == COLOR_TRANSFER_ST2084) ?
      pqOetf(linearColor) : hlgOetf(linearColor);
}

void main() {
  vec4 inputColor = texture(uTexSampler, vTexSamplingCoord);
  vec4 transformedColors = /*uRgbMatrix * */vec4(inputColor.rgb, 1);
  outColor = vec4(getElectricalColor(transformedColors.rgb), inputColor.a);
}
