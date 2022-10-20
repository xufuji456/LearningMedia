attribute vec4 aPosition;
attribute vec4 aInputTextureCoord;
varying vec2 vTextureCoord;

void main() {
    gl_Position = aPosition;
    vTextureCoord = aInputTextureCoord.xy;
}
