package com.media.rtc.engine;

import android.content.Context;
import android.media.AudioManager;
import android.view.View;

import com.media.rtc.Const;
import com.media.rtc.engine.listener.EngineCallback;
import com.media.rtc.engine.listener.EngineListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.List;

/**
 * 提供AudioSource、VideoSource
 * 视频预览、停止预览
 * 切换摄像头
 */
public class AppRTCEngine implements EngineListener, PeerConnectionClient.PeerEventListener {

    public boolean mOnlyAudio;
    private final Context mContext;
    private EngineCallback mCallback;
    private boolean isSwitching = false;
    private final AudioManager mAudioManager;

    private EglBase mEglBase;
    private VideoSource mVideoSource;
    private AudioSource mAudioSource;
    private MediaStream mLocalStream;
    private VideoCapturer mVideoCapture;
    private SurfaceViewRenderer mViewRenderer;
    private SurfaceTextureHelper mSurfaceTextureHelper;
    private PeerConnectionFactory mPeerConnectionFactory;

    private static final int VIDEO_FRAME_RATE = 20;
    private static final int VIDEO_RESOLUTION_WIDTH  = 640;
    private static final int VIDEO_RESOLUTION_HEIGHT = 480;

    private static final String MEDIA_TRACK_ID = "ARDAMS";
    private static final String VIDEO_TRACK_ID = MEDIA_TRACK_ID + "v0";
    private static final String AUDIO_TRACK_ID = MEDIA_TRACK_ID + "a0";

    private static final String CONSTRAINT_AEC = "googEchoCancellation"; // 回声消除
    private static final String CONSTRAINT_AGC = "googAutoGainControl";  // 自动增益
    private static final String CONSTRAINT_ANS = "googNoiseSuppression"; // 噪声抑制

    private PeerConnectionClient mPeerConnectionClient;
    private final List<PeerConnection.IceServer> iceServerList = new ArrayList<>();

    public AppRTCEngine(boolean audioOnly, Context context) {
        mOnlyAudio = audioOnly;
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        initIceServer();
    }

    private MediaConstraints createAudioConstraint() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair(CONSTRAINT_AEC, "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair(CONSTRAINT_AGC, "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair(CONSTRAINT_ANS, "true"));
        return constraints;
    }

    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        String[] deviceNames = enumerator.getDeviceNames();
        for (String device:deviceNames) {
            // 是否前置摄像头
            if (enumerator.isFrontFacing(device)) {
                return enumerator.createCapturer(device, null);
            }
        }
        for (String device:deviceNames) {
            // 后置摄像头
            if (!enumerator.isFrontFacing(device)) {
                return enumerator.createCapturer(device, null);
            }
        }
        return null;
    }

    private VideoCapturer createVideoCapture() {
        VideoCapturer videoCapturer;
        if (Camera2Enumerator.isSupported(mContext)) {
            videoCapturer = createCameraCapture(new Camera2Enumerator(mContext));
        } else {
            videoCapturer = createCameraCapture(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private void createLocalStream() {
        mLocalStream = mPeerConnectionFactory.createLocalMediaStream(MEDIA_TRACK_ID);
        // audio
        mAudioSource = mPeerConnectionFactory.createAudioSource(createAudioConstraint());
        AudioTrack audioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, mAudioSource);
        mLocalStream.addTrack(audioTrack);
        // video
        mVideoCapture = createVideoCapture();
        mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mEglBase.getEglBaseContext());
        mVideoSource = mPeerConnectionFactory.createVideoSource(mVideoCapture.isScreencast());
        mVideoCapture.initialize(mSurfaceTextureHelper, mContext, mVideoSource.getCapturerObserver());
        // 设置预览参数
        mVideoCapture.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FRAME_RATE);
        VideoTrack videoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, mVideoSource);
        mLocalStream.addTrack(videoTrack);
    }

    private void initIceServer() {
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(Const.ICE_SERVER).createIceServer();
        iceServerList.add(iceServer);
        PeerConnection.IceServer stunServer = PeerConnection.IceServer.builder(Const.STUN_SERVER).createIceServer();
        iceServerList.add(stunServer);
        PeerConnection.IceServer turnServer = PeerConnection.IceServer.builder(Const.TURN_SERVER)
                .setUsername(Const.USER_NAME).setPassword(Const.PASSWORD).createIceServer();
        iceServerList.add(turnServer);
        PeerConnection.IceServer turnServer1 = PeerConnection.IceServer.builder(Const.TURN_SERVER1)
                .setUsername(Const.USER_NAME).setPassword(Const.PASSWORD).createIceServer();
        iceServerList.add(turnServer1);
    }

    // 创建PeerConnectionFactory
    private PeerConnectionFactory createConnectionFactory() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(mContext).createInitializationOptions());
        // 视频编码器、解码器
        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                mEglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(mEglBase.getEglBaseContext());
        // 音频模块
        AudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        return PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    @Override
    public void init(EngineCallback callback) {
        mCallback = callback;
        mEglBase = EglBase.create();
        mPeerConnectionFactory = createConnectionFactory();
        createLocalStream();
    }

    // 对方加入房间
    @Override
    public void joinRoom(String targetId) {
        mPeerConnectionClient = new PeerConnectionClient(mPeerConnectionFactory, iceServerList, targetId, this);
        mPeerConnectionClient.setOffer(false);
        mPeerConnectionClient.addLocalStream(mLocalStream);
        mCallback.onJoinRoom();
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    // 发起方进入房间
    @Override
    public void userIn(String userId) {
        mPeerConnectionClient = new PeerConnectionClient(mPeerConnectionFactory, iceServerList, userId, this);
        mPeerConnectionClient.setOffer(true);
        mPeerConnectionClient.addLocalStream(mLocalStream);
        mPeerConnectionClient.createOffer();
    }

    @Override
    public void userReject(String userId, int type) {
        mCallback.onReject(type);
    }

    @Override
    public void disconnected(String userId) {
        mCallback.disconnected(userId);
    }

    @Override
    public void receiveOffer(String userId, String description) {
        // 接收offer，回复answer
        SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, description);
        mPeerConnectionClient.setOffer(false);
        mPeerConnectionClient.setRemoteDescription(sdp);
        mPeerConnectionClient.createAnswer();
    }

    @Override
    public void receiveAnswer(String userId, String sdp) {
        SessionDescription description = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        mPeerConnectionClient.setRemoteDescription(description);
    }

    @Override
    public void receiveIceCandidate(String userId, String id, int label, String candidate) {
        IceCandidate iceCandidate = new IceCandidate(id, label, candidate);
        mPeerConnectionClient.addRemoteIceCandidate(iceCandidate);
    }

    @Override
    public void leaveRoom(String userId) {
        mPeerConnectionClient.close();
        mCallback.onExitRoom();
    }

    // 设置本地预览
    @Override
    public View setupLocalPreview(boolean isOverlay) {
        if (mEglBase == null) {
            return null;
        }
        mViewRenderer = new SurfaceViewRenderer(mContext);
        mViewRenderer.init(mEglBase.getEglBaseContext(), null);
        mViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mViewRenderer.setMirror(true);
        mViewRenderer.setZOrderMediaOverlay(isOverlay);
        if (mLocalStream.videoTracks.size() > 0) {
            mLocalStream.videoTracks.get(0).addSink(new VideoSink() {
                @Override
                public void onFrame(VideoFrame videoFrame) {
                    mViewRenderer.onFrame(videoFrame);
                }
            });
        }
        return mViewRenderer;
    }

    @Override
    public void stopPreview() {
        if (mAudioSource != null) {
            mAudioSource.dispose();
            mAudioSource = null;
        }
        if (mVideoCapture != null) {
            try {
                mVideoCapture.stopCapture();
                mVideoCapture.dispose();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mVideoCapture = null;
        }
        if (mSurfaceTextureHelper != null) {
            mSurfaceTextureHelper.dispose();
            mSurfaceTextureHelper = null;
        }
        if (mVideoSource != null) {
            mVideoSource.dispose();
            mVideoSource = null;
        }
        if (mLocalStream != null) {
            mLocalStream.dispose();
            mLocalStream = null;
        }
        if (mViewRenderer != null) {
            mViewRenderer.release();
            mViewRenderer = null;
        }
    }

    @Override
    public View setupRemoteVideo(String userId, boolean isO) {
        if (mPeerConnectionClient.mSurfaceViewRenderer == null) {
            mPeerConnectionClient.createRender(mEglBase, mContext, isO);
        }
        return mPeerConnectionClient.mSurfaceViewRenderer;
    }

    // 切换摄像头
    @Override
    public void switchCamera() {
        if (isSwitching || mVideoCapture == null)
            return;
        isSwitching = true;
        if (mVideoCapture instanceof CameraVideoCapturer) {
            CameraVideoCapturer capture = (CameraVideoCapturer) mVideoCapture;
            capture.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFrontCamera) {
                    // 前置摄像头，设置镜像
                    mViewRenderer.setMirror(isFrontCamera);
                    isSwitching = true;
                }

                @Override
                public void onCameraSwitchError(String s) {

                }
            });
        }
    }

    @Override
    public void toggleSpeaker(boolean enable) {

    }

    @Override
    public void release() {
        stopPreview();
        if (mAudioManager != null) {
            mAudioManager.setMode(AudioManager.MODE_NORMAL);
        }
        if (mPeerConnectionClient != null) {
            mPeerConnectionClient.close();
            mPeerConnectionClient = null;
        }
        if (mPeerConnectionFactory != null) {
            mPeerConnectionFactory.dispose();
            mPeerConnectionFactory = null;
        }
        if (mEglBase != null) {
            mEglBase.release();
            mEglBase = null;
        }
    }

    @Override
    public void onSendIceCandidate(String userId, IceCandidate candidate) {
        mCallback.onSendIceCandidate(userId, candidate);
    }

    @Override
    public void onSendOffer(String userId, SessionDescription description) {
        mCallback.onSendOffer(userId, description);
    }

    @Override
    public void onSendAnswer(String userId, SessionDescription description) {
        mCallback.onSendAnswer(userId, description);
    }

    @Override
    public void onRemoteStream(String userId, MediaStream stream) {
        mCallback.onRemoteStream(userId);
    }

    @Override
    public void onRemoveStream(String userId, MediaStream stream) {
        leaveRoom(userId);
    }

    @Override
    public void onDisconnected(String userId) {
        mCallback.onDisconnected(userId);
    }
}
