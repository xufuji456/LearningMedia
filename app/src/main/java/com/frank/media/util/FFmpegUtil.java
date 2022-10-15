package com.frank.media.util;

import java.util.Locale;

/**
 * @author xufulong
 * @date 2022/10/15 2:33 下午
 * @desc
 */
public class FFmpegUtil {

    public static String[] photoToVideo(String inputPath, String bgPath, String outputPath) {
        String cmd = "ffmpeg -i %s -i %s -filter_complex " +
                "zoompan=z='min(zoom+0.0015,1.5)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2):d=180' %s";
        cmd = String.format(cmd, inputPath, bgPath, outputPath);
        return cmd.split(" ");
    }

    public static String[] xfadeTransition(String transition, String onePath, int width, int height, int offset,
                                           String twoPath, String outputPath) {
        String cmd = "ffmpeg -i %s -i %s -filter_complex " +
                "[0]settb=AVTB,fps=24000/1001[v0];[1]settb=AVTB,fps=24000/1001,scale=%d:%d[v1];" +
                "[v0][v1]xfade=transition=%s:offset=%d -an %s";
        cmd = String.format(Locale.getDefault(), cmd, onePath, twoPath, width, height, transition, offset, outputPath);
        return cmd.split(" ");
    }
}
