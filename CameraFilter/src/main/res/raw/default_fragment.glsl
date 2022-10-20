
#extension GL_OES_EGL_image_external : require

precision mediump float;

varying vec2 vTextureCoord;

uniform samplerExternalOES inputImageTexture;

void main() {
    vec2 xy = vTextureCoord.xy;
    // 二分屏
//    if (xy.x <= 0.5) {
//        xy.x += 0.25;
//    } else {
//        xy.x -= 0.25;
//        // 镜像
//        xy.x = 1.0 - xy.x;
//    }
    // 四分屏
//    if (xy.x <=  0.5) {
//        xy.x = xy.x * 2.0;
//    } else {
//        xy.x = (xy.x - 0.5) * 2.0;
//    }
//    if (xy.y <=  0.5) {
//        xy.y = xy.y * 2.0;
//    } else {
//        xy.y = (xy.y - 0.5) * 2.0;
//    }

    vec3 textureColor = texture2D(inputImageTexture, xy).rgb;
    // 黑白相机
//    const vec3 weight = vec3(0.3, 0.59, 0.11);
//    float gray = dot(textureColor.rgb, weight);
    // 颜色反相
    gl_FragColor = vec4(textureColor.rgb, 1.0);
}
