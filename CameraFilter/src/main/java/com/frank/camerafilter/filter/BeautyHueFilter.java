package com.frank.camerafilter.filter;

import android.content.Context;
import android.opengl.GLES30;

import com.frank.camerafilter.R;
import com.frank.camerafilter.util.OpenGLUtil;

/**
 * @author xufulong
 * @date 2022/10/20 9:12 上午
 * @desc
 */
public class BeautyHueFilter extends BaseFilter {

    private int hueAdjust;

    public BeautyHueFilter(Context context) {
        super(OpenGLUtil.readShaderFromSource(context, R.raw.normal_vertex),
                OpenGLUtil.readShaderFromSource(context, R.raw.hue_fragment));
    }

    @Override
    protected void onInit() {
        super.onInit();
        hueAdjust = GLES30.glGetUniformLocation(getProgramId(), "hueAdjust");
    }

    @Override
    protected void onInitialized() {
        super.onInitialized();
        setFloat(hueAdjust, 3.0f);
    }
}
