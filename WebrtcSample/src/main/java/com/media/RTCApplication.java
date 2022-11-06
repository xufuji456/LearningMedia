package com.media;

import android.app.Application;

public class RTCApplication extends Application {

    private static RTCApplication mApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        mApplication = this;
    }

    public static RTCApplication getInstance() {
        return mApplication;
    }

}
