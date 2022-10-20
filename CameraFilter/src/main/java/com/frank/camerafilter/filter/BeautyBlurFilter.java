package com.frank.camerafilter.filter;

import android.content.Context;
import android.opengl.GLES30;

import com.frank.camerafilter.R;
import com.frank.camerafilter.util.OpenGLUtil;

/**
 * @author xufulong
 * @date 2022/10/20 9:15 上午
 * @desc
 */
public class BeautyBlurFilter extends BaseFilter {

    private int blurSize;

    public BeautyBlurFilter(Context context) {
        super(OpenGLUtil.readShaderFromSource(context, R.raw.normal_vertex),
                OpenGLUtil.readShaderFromSource(context, R.raw.blur_fragment));
    }

    @Override
    protected void onInit() {
        super.onInit();
        blurSize = GLES30.glGetUniformLocation(getProgramId(), "blurSize");
    }

    @Override
    protected void onInitialized() {
        super.onInitialized();
        setFloat(blurSize, 0.3f);
    }
}
