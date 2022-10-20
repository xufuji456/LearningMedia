varying highp vec2 vTextureCoord;

uniform sampler2D inputImageTexture;

uniform highp vec2 blurCenter;
uniform highp float blurSize;

void main()
{
    highp vec2 textureCoordinate = vTextureCoord.xy;
    highp vec2 samplingOffset = 1.0/100.0 * (blurCenter - textureCoordinate) * blurSize;

    lowp vec4 fragmentColor = texture2D(inputImageTexture, textureCoordinate) * 0.18;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate + samplingOffset) * 0.15;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate + (2.0 * samplingOffset)) *  0.12;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate + (3.0 * samplingOffset)) * 0.09;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate + (4.0 * samplingOffset)) * 0.05;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate - samplingOffset) * 0.15;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate - (2.0 * samplingOffset)) *  0.12;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate - (3.0 * samplingOffset)) * 0.09;
    fragmentColor += texture2D(inputImageTexture, textureCoordinate - (4.0 * samplingOffset)) * 0.05;

    gl_FragColor = fragmentColor;
}
