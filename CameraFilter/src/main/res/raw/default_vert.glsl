
attribute vec4 aPosition;
attribute vec4 aInputTextureCoord;

varying vec2 vTextureCoord;

uniform mat4 mTextureTransform;

void main() {
    vTextureCoord = (mTextureTransform * aInputTextureCoord).xy;
    gl_Position = aPosition;
}
