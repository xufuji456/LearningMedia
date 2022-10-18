
#extension GL_OES_EGL_image_external : require

precision mediump float;

varying vec2 vTextureCoord;

uniform samplerExternalOES inputImageTexture;

void main() {
    vec3 centerColor = texture2D(inputImageTexture, vTextureCoord).rgb;
    gl_FragColor = vec4(centerColor.rgb, 1.0);
}
