package com.frank.camerafilter.recorder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author xufulong
 * @date 2022/10/18 10:52 下午
 * @desc
 */
public class VideoRecorderCore {

    private final static String TAG = VideoRecorderCore.class.getSimpleName();

    private final static int FRAME_RATE = 25;
    private final static int IFRAME_INTERVAL = FRAME_RATE;
    private final static int TIMEOUT_US = 20 * 1000;
    private final static String MIME_TYPE = "video/avc";

    private int mTrackIndex;
    private boolean mMuxerStarted;
    private final Surface mSurface;
    // 视频封装
    private MediaMuxer mMediaMuxer;
    // 视频编码
    private MediaCodec mVideoEncoder;
    private final MediaCodec.BufferInfo mBufferInfo;

    public VideoRecorderCore(int width, int height, int bitrate, File outputFile) throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();
        // 创建MediaFormat
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        // 创建编码器
        mVideoEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mVideoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();
        // 创建封装器
        mMediaMuxer   = new MediaMuxer(outputFile.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mTrackIndex   = -1;
        mMuxerStarted = false;
    }

    public Surface getInputSurface() {
        return mSurface;
    }

    // 视频编码
    public void drainEncoder(boolean endOfStream) {
        if (endOfStream) {
            mVideoEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] outputBuffers = mVideoEncoder.getOutputBuffers();
        while (true) {
            int status = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break;
                }
            } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mVideoEncoder.getOutputBuffers();
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mVideoEncoder.getOutputFormat();
                mTrackIndex = mMediaMuxer.addTrack(newFormat);
                // 启动muxer
                mMediaMuxer.start();
                mMuxerStarted = true;
            } else if (status < 0) {
                Log.e(TAG, "encoder error:" + status);
            } else {
                // 编码成功，开始封装媒体包
                ByteBuffer data = outputBuffers[status];
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size > 0) {
                    data.position(mBufferInfo.offset);
                    data.limit(mBufferInfo.offset + mBufferInfo.size);
                    mMediaMuxer.writeSampleData(mTrackIndex, data, mBufferInfo);
                }
                mVideoEncoder.releaseOutputBuffer(status, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    public void release() {
        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mMediaMuxer != null) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
    }

}
