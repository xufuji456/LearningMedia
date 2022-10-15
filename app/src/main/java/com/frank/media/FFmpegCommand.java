package com.frank.media;

import com.frank.media.listener.OnHandlerListener;
import com.frank.media.thread.ThreadPoolUtil;

/**
 * @author xufulong
 * @date 2022/10/15 2:13 下午
 * @desc
 */
public class FFmpegCommand {

    static {
        System.loadLibrary("like_media");
    }

    private static native int runFFmpeg(String[] commandLine);

    public static void execute(String[] commandLine, OnHandlerListener handlerListener) {
        ThreadPoolUtil.runThread(new Runnable() {
            @Override
            public void run() {
                if (handlerListener != null) {
                    handlerListener.onBegin();
                }
                int result = runFFmpeg(commandLine);
                if (handlerListener != null) {
                    handlerListener.onEnd(result);
                }
            }
        });
    }
}
