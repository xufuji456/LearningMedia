
package com.frank.videoedit.transform.impl;

import android.media.MediaCodecInfo;

import com.frank.videoedit.transform.util.EncoderUtil;

import com.google.common.collect.ImmutableList;

public interface EncoderSelector {

  EncoderSelector DEFAULT = EncoderUtil::getSupportedEncoders;

  ImmutableList<MediaCodecInfo> selectEncoderInfos(String mimeType);
}
