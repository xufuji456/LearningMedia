package com.frank.living.handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.frank.living.listener.OnNetworkChangeListener;

public class ConnectionReceiver extends BroadcastReceiver {

    private final OnNetworkChangeListener networkChangeListener;

    public ConnectionReceiver(OnNetworkChangeListener networkChangeListener) {
        this.networkChangeListener = networkChangeListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo == null || !activeNetworkInfo.isAvailable()) {
                networkChangeListener.onNetworkChange();
            }

        }
    }
}
