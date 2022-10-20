package com.frank.camerafilter.factory;

import android.content.Context;

import com.frank.camerafilter.filter.BaseFilter;
import com.frank.camerafilter.filter.BeautyBlurFilter;
import com.frank.camerafilter.filter.BeautyHueFilter;

/**
 * @author xufulong
 * @date 2022/10/20 9:17 上午
 * @desc
 */
public class BeautyFilterFactory {

    public static BaseFilter getFilter(BeautyFilterType type, Context context) {
        switch (type) {
            case HUE:
                return new BeautyHueFilter(context);
            case BLUR:
                return new BeautyBlurFilter(context);
            case NONE:
            default:
                return null;
        }
    }
}
