
package com.frank.videoedit.transform.impl;

public interface EncoderBitrateProvider {

  int getBitrate(String encoderName, int width, int height, float frameRate);
}
