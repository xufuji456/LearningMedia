package com.frank.living.handler;

import android.content.Context;
import android.view.OrientationEventListener;


public class OrientationHandler {

    private final int OFFSET_ANGLE = 5;

    private int lastOrientationDegree = 0;
    private OnOrientationListener onOrientationListener;
    private OrientationEventListener orientationEventListener;

    public interface OnOrientationListener {
        void onOrientation(int orientation);
    }

    public OrientationHandler(Context context) {
        initOrientation(context);
    }

    public void setOnOrientationListener(OnOrientationListener onOrientationListener) {
        this.onOrientationListener = onOrientationListener;
    }

    private void initOrientation(Context context) {
        orientationEventListener = new OrientationEventListener(context.getApplicationContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN)
                    return;

                if (orientation >= 0 - OFFSET_ANGLE && orientation <= OFFSET_ANGLE) {
                    if (lastOrientationDegree != 0) {
                        lastOrientationDegree = 0;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                } else if (orientation >= 90 - OFFSET_ANGLE && orientation <= 90 + OFFSET_ANGLE) {
                    if (lastOrientationDegree != 90) {
                        lastOrientationDegree = 90;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                } else if (orientation >= 180 - OFFSET_ANGLE && orientation <= 180 + OFFSET_ANGLE) {
                    if (lastOrientationDegree != 180) {
                        lastOrientationDegree = 180;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                } else if (orientation >= 270 - OFFSET_ANGLE && orientation <= 270 + OFFSET_ANGLE) {
                    if (lastOrientationDegree !=270) {
                        lastOrientationDegree = 270;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                }
            }
        };
    }

    public void enable() {
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    public void disable() {
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.disable();
        }
    }

}
