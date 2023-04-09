
package com.frank.videoedit.listener;

import android.content.Context;

import com.google.android.exoplayer2.util.Effect;

public interface GlEffect extends Effect {

  GlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr);
}
