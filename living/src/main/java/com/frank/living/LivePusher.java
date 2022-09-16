package com.frank.living;

import android.app.Activity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import com.frank.living.listener.LiveStateChangeListener;
import com.frank.living.listener.OnFrameDataCallback;
import com.frank.living.param.AudioParam;
import com.frank.living.param.VideoParam;
import com.frank.living.stream.AudioChannelStream;
import com.frank.living.stream.VideoStreamBase;
import com.frank.living.stream.VideoChannelStream;

public class LivePusher implements OnFrameDataCallback {

    private final static String TAG = LivePusher.class.getSimpleName();

    private final static int ERROR_VIDEO_ENCODER_OPEN   = 0x01;
    private final static int ERROR_VIDEO_ENCODER_ENCODE = 0x02;
    private final static int ERROR_AUDIO_ENCODER_OPEN   = 0x03;
    private final static int ERROR_AUDIO_ENCODER_ENCODE = 0x04;
    private final static int ERROR_RTMP_CONNECT_SERVER  = 0x05;
    private final static int ERROR_RTMP_CONNECT_STREAM  = 0x06;
    private final static int ERROR_RTMP_SEND_PACKET     = 0x07;

    static {
        try {
            System.loadLibrary("live");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "loadLibrary error=" + error);
        }
    }

    private final AudioChannelStream audioStream;
    private final VideoStreamBase videoStream;

    private final Activity activity;
    private LiveStateChangeListener liveStateChangeListener;

    public LivePusher(Activity activity,
                      VideoParam videoParam,
                      AudioParam audioParam,
                      View view) {
        this.activity = activity;
        native_init();
        audioStream = new AudioChannelStream(activity, this, audioParam);
        videoStream = new VideoChannelStream(this, view, videoParam, activity);
    }

    public void setPreviewDisplay(SurfaceHolder surfaceHolder) {
        videoStream.setPreviewDisplay(surfaceHolder);
    }

    public void switchCamera() {
        videoStream.switchCamera();
    }

    public void setPreviewDegree(int degree) {
        videoStream.onPreviewDegreeChanged(degree);
    }

    public void setMute(boolean isMute) {
        audioStream.setMute(isMute);
    }

    public void startPush(String path, LiveStateChangeListener stateChangeListener) {
        this.liveStateChangeListener = stateChangeListener;
        native_start(path);
        videoStream.startLive();
        audioStream.startLive();
    }

    public void stopPush() {
        videoStream.stopLive();
        audioStream.stopLive();
        native_stop();
    }

    public void release() {
        videoStream.release();
        audioStream.release();
        native_release();
    }

    /**
     * native层反射回调，错误消息
     *
     * @param errCode errCode
     */
    public void errorFromNative(int errCode) {
        stopPush();
        if (liveStateChangeListener != null && activity != null) {
            String msg = "";
            switch (errCode) {
                case ERROR_VIDEO_ENCODER_OPEN:
                    msg = activity.getString(R.string.error_video_encoder);
                    break;
                case ERROR_VIDEO_ENCODER_ENCODE:
                    msg = activity.getString(R.string.error_video_encode);
                    break;
                case ERROR_AUDIO_ENCODER_OPEN:
                    msg = activity.getString(R.string.error_audio_encoder);
                    break;
                case ERROR_AUDIO_ENCODER_ENCODE:
                    msg = activity.getString(R.string.error_audio_encode);
                    break;
                case ERROR_RTMP_CONNECT_SERVER:
                    msg = activity.getString(R.string.error_rtmp_connect);
                    break;
                case ERROR_RTMP_CONNECT_STREAM:
                    msg = activity.getString(R.string.error_rtmp_connect_strem);
                    break;
                case ERROR_RTMP_SEND_PACKET:
                    msg = activity.getString(R.string.error_rtmp_send_packet);
                    break;
                default:
                    break;
            }
            liveStateChangeListener.onError(msg);
        }
    }

    private int getInputSamplesFromNative() {
        return native_getInputSamples();
    }

    private void setVideoCodecInfo(int width, int height, int frameRate, int bitrate) {
        native_setVideoCodecInfo(width, height, frameRate, bitrate);
    }

    private void setAudioCodecInfo(int sampleRateInHz, int channels) {
        native_setAudioCodecInfo(sampleRateInHz, channels);
    }

    private void pushAudio(byte[] data) {
        native_pushAudio(data);
    }

    private void pushVideo(byte[] data, int cameraType) {
        native_pushVideo(data, cameraType);
    }

    @Override
    public int getInputSamples() {
        return getInputSamplesFromNative();
    }

    @Override
    public void onAudioCodecInfo(int sampleRate, int channelCount) {
        setAudioCodecInfo(sampleRate, channelCount);
    }

    @Override
    public void onAudioFrame(byte[] pcm) {
        if (pcm != null) {
            pushAudio(pcm);
        }
    }

    @Override
    public void onVideoCodecInfo(int width, int height, int frameRate, int bitrate) {
        setVideoCodecInfo(width, height, frameRate, bitrate);
    }

    @Override
    public void onVideoFrame(byte[] yuv, int cameraType) {
        if (yuv != null) {
            pushVideo(yuv, cameraType);
        }
    }

    private native void native_init();

    private native void native_start(String path);

    private native void native_setVideoCodecInfo(int width, int height, int fps, int bitrate);

    private native void native_setAudioCodecInfo(int sampleRateInHz, int channels);

    private native int native_getInputSamples();

    private native void native_pushAudio(byte[] data);

    private native void native_pushVideo(byte[] yuv, int cameraType);

    private native void native_stop();

    private native void native_release();

}
