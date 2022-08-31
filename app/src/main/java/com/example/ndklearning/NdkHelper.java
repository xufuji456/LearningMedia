package com.example.ndklearning;

import android.util.Log;

/**
 * @author xufulong
 * @date 2022/8/14 6:31 下午
 * @desc
 */
public class NdkHelper {

    static {
        System.loadLibrary("ndklearning");
    }

    public native String stringFromJNI();

    public native void setIntData(int[] data);

    public native void testReflect();

    void onJniCallback(String msg) {
        if (msg != null) {
            Log.i("NdkHelper", msg);
        }
    }

}
