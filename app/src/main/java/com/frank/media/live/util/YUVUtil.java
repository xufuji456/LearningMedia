package com.frank.media.live.util;

public class YUVUtil {

    public static void YUV420pRotate90(byte[] dst, byte[] src, int width, int height) {
        int n = 0;
        int wh = width * height;
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        // y
        for (int j = 0; j < width; j++) {
            for (int i = height - 1; i >= 0; i--) {
                dst[n++] = src[width * i + j];
            }
        }
        // u
        for (int i = 0; i < halfWidth; i++) {
            for (int j = 1; j <= halfHeight; j++) {
                dst[n++] = src[wh + ((halfHeight - j) * halfWidth + i)];
            }
        }
        // v
        for (int i = 0; i < halfWidth; i++) {
            for (int j = 1; j <= halfHeight; j++) {
                dst[n++] = src[wh + wh / 4 + ((halfHeight - j) * halfWidth + i)];
            }
        }
    }

    public static void YUV420pRotate180(byte[] dst, byte[] src, int width, int height) {
        int n = 0;
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        // y
        for (int j = height - 1; j >= 0; j--) {
            for (int i = width; i > 0; i--) {
                dst[n++] = src[width * j + i - 1];
            }
        }
        // u
        int offset = width * height;
        for (int j = halfHeight - 1; j >= 0; j--) {
            for (int i = halfWidth; i > 0; i--) {
                dst[n++] = src[offset + halfWidth * j + i - 1];
            }
        }
        // v
        offset += halfWidth * halfHeight;
        for (int j = halfHeight - 1; j >= 0; j--) {
            for (int i = halfWidth; i > 0; i--) {
                dst[n++] = src[offset + halfWidth * j + i - 1];
            }
        }
    }

}
