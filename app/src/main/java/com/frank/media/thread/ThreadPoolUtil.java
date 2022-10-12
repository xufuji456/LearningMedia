package com.frank.media.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xufulong
 * @date 2022/10/12 10:36 上午
 * @desc
 */
public class ThreadPoolUtil {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void runThread(Runnable runnable) {
        executor.submit(runnable);
    }

}
