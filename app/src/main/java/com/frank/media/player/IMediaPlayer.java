package com.frank.media.player;

import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.frank.media.player.mediainfo.MediaInfo;
import com.frank.media.player.mediainfo.MediaTrack;
import com.frank.media.player.mediainfo.MediaType;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

public interface IMediaPlayer {

    void setSurface(Surface surface);

    void setDataSource(@NonNull String path)
            throws IOException, IllegalArgumentException, IllegalStateException;

    void setDataSource(FileDescriptor fd, long length)
            throws IOException, IllegalArgumentException, IllegalStateException;

    void prepare() throws IOException, IllegalStateException;

    void prepareAsync() throws IllegalStateException;

    void start() throws IllegalStateException;

    void pause() throws IllegalStateException;

    void stop() throws IllegalStateException;

    void resume() throws IllegalStateException;

    void setScreenOnWhilePlaying(boolean screenOn);

    int getRotate();

    int getVideoWidth();

    int getVideoHeight();

    boolean isPlaying();

    void seekTo(long msec) throws IllegalStateException;

    long getCurrentPosition();

    long getDuration();

    void setVolume(float volume);

    void setMute(boolean mute);

    void setRate(float rate);

    MediaInfo getMediaInfo(MediaType mediaType);

    List<MediaTrack> getMediaTrack(MediaType mediaType);

    Bitmap getCurrentFrame();

    void selectTrack(int trackId);

    void reset();

    void release();

    interface OnPreparedListener {
        void onPrepared(IMediaPlayer mp);
    }

    void setOnPreparedListener(OnPreparedListener listener);

    interface OnRenderFirstFrameListener {
        void onRenderFirstFrame(IMediaPlayer mp, int video, int audio);
    }

    void setOnRenderFirstFrameListener(OnRenderFirstFrameListener listener);

    interface OnCompletionListener {
        void onCompletion(IMediaPlayer mp);
    }

    void setOnCompletionListener(OnCompletionListener listener);

    interface OnErrorListener {
        boolean onError(IMediaPlayer mp, int what, int extra);
    }

    void setOnErrorListener(OnErrorListener listener);

}
