
package com.frank.videoedit.listener;

import android.content.Context;

public interface GlEffect {

  GlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr);
}
