
package com.frank.videoedit.entity;

public final class TextureInfo {

  public static final TextureInfo UNSET =
      new TextureInfo(-1, -1, -1, -1);

  public final int texId;

  public final int fboId;

  public final int width;

  public final int height;

  public TextureInfo(int texId, int fboId, int width, int height) {
    this.texId  = texId;
    this.fboId  = fboId;
    this.width  = width;
    this.height = height;
  }
}
