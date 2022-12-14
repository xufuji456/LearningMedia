package com.frank.media.player.manager;

import android.graphics.Bitmap;
import android.view.Surface;

import com.frank.media.player.factory.PlayerFactory;
import com.frank.media.player.IMediaPlayer;
import com.frank.media.player.listener.PlayerManagerCallback;
import com.frank.media.player.mediainfo.MediaInfo;
import com.frank.media.player.mediainfo.MediaTrack;
import com.frank.media.player.mediainfo.MediaType;

import java.io.IOException;
import java.util.List;

/**
 * @author xufulong
 * @date 2022/10/26 5:59 下午
 * @desc
 */
public class XuPlayerManager implements IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnRenderFirstFrameListener,
        IMediaPlayer.OnCompletionListener {

    private Surface mSurface;
    private IMediaPlayer mPlayer;
    private final PlayerManagerCallback mCallback;
    private @PlayerFactory.PlayerType int mPlayerType;

    public XuPlayerManager(String path, @PlayerFactory.PlayerType int playerType, PlayerManagerCallback callback) {
        mCallback = callback;
        mPlayerType = playerType;
        initPlayer(path);
    }

    private void initPlayer(String path) {
        mPlayer = PlayerFactory.createPlayer(mPlayerType);
        if (mPlayer == null)
            throw new NullPointerException("no player created...");
        try {
            mPlayer.setDataSource(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSurface(Surface surface) {
        mSurface = surface;
        mPlayer.setSurface(surface);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnRenderFirstFrameListener(this);
        mPlayer.setOnErrorListener(this);
        mPlayer.setOnCompletionListener(this);
    }

    public void prepare() throws IOException {
        mPlayer.prepare();
    }

    public void prepareAsync() {
        mPlayer.prepareAsync();
    }

    public void start() {
        mPlayer.start();
    }

    public void pause() {
        mPlayer.pause();
    }

    public void stop() {
        mPlayer.stop();
    }

    public void resume() {
        mPlayer.resume();
    }

    public void setScreenOnWhilePlaying(boolean screenOn) {
        mPlayer.setScreenOnWhilePlaying(screenOn);
    }

    public int getRotate() {
        return mPlayer.getRotate();
    }

    public int getVideoWidth() {
        return mPlayer.getVideoWidth();
    }

    public int getVideoHeight() {
        return mPlayer.getVideoHeight();
    }

    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    public void seekTo(long msec) throws IllegalStateException {
        mPlayer.seekTo(msec);
    }

    public long getCurrentPosition() {
        if (mPlayer == null)
            return 0;
       return mPlayer.getCurrentPosition();
    }

    public long getDuration() {
       return mPlayer.getDuration();
    }

    public void setVolume(float volume) {
        mPlayer.setVolume(volume);
    }

    public void setMute(boolean mute) {
        mPlayer.setMute(mute);
    }

    public void setRate(float rate) {
        mPlayer.setRate(rate);
    }

    public MediaInfo getMediaInfo(MediaType mediaType) {
        return mPlayer.getMediaInfo(mediaType);
    }

    public List<MediaTrack> getMediaTrack(MediaType mediaType) {
        return mPlayer.getMediaTrack(mediaType);
    }

    public Bitmap getCurrentFrame() {
       return mPlayer.getCurrentFrame();
    }

    public void selectTrack(int trackId) {
        mPlayer.selectTrack(trackId);
    }

    public void reset() {
        mPlayer.reset();
    }

    public void release() {
        mPlayer.release();
    }

    public void switchNext(String path) {
        if (mPlayer != null) {
            mPlayer.pause();
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        initPlayer(path);
        setSurface(mSurface);
        prepareAsync();
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        mCallback.onPrepared();
    }

    @Override
    public void onRenderFirstFrame(IMediaPlayer mp, int video, int audio) {
        mCallback.onRenderFirstFrame(video, audio);
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        mCallback.onCompletion();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        return mCallback.onError(what, extra);
    }
}
