package com.frank.media.handler;

import android.os.Handler;
import android.util.Log;

import com.frank.media.FFmpegCommand;
import com.frank.media.listener.OnHandlerListener;

/**
 * @author xufulong
 * @date 2022/10/15 2:27 下午
 * @desc
 */
public class FFmpegHandler {

    public static final int MSG_BEGIN = 0x1000;
    public static final int MSG_END   = 0x2000;

    private final Handler mHandler;

    public FFmpegHandler(Handler handler) {
        mHandler = handler;
    }

    public void execFFmpegCommand(String[] commandLine) {
        if (commandLine == null || commandLine.length == 0)
            return;
        mHandler.removeCallbacks(null);
        FFmpegCommand.execute(commandLine, new OnHandlerListener() {
            @Override
            public void onBegin() {
                mHandler.sendEmptyMessage(MSG_BEGIN);
            }

            @Override
            public void onEnd(int result) {
                mHandler.obtainMessage(MSG_END, result).sendToTarget();
            }
        });
    }

}
