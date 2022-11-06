package com.media.rtc.engine;

import android.content.Context;
import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建AudioSource、VideoSource
 * 创建AudioStream、VideoStream
 * 创建sdp的offer和answer
 */
public class PeerConnectionClient implements PeerConnection.Observer, SdpObserver {
    private final static String TAG = PeerConnectionClient.class.getSimpleName();

    private final String mUserId;
    private final PeerConnection mPeerConnection;
    private final PeerConnectionFactory mConnectionFactory;
    private final List<PeerConnection.IceServer> mIceServerList;
    private final PeerEventListener mPeerEventListener;

    private boolean isOffer;
    public MediaStream mMediaStream;
    private List<IceCandidate> mIceCandidateList;
    private SessionDescription mSessionDescription;
    public SurfaceViewRenderer mSurfaceViewRenderer;

    public interface PeerEventListener {

        void onSendIceCandidate(String userId, IceCandidate candidate);

        void onSendOffer(String userId, SessionDescription description);

        void onSendAnswer(String userId, SessionDescription description);

        void onRemoteStream(String userId, MediaStream stream);

        void onRemoveStream(String userId, MediaStream stream);

        void onDisconnected(String userId);
    }

    public PeerConnectionClient(PeerConnectionFactory factory, List<PeerConnection.IceServer> serverList,
                                String userId, PeerEventListener listener) {
        mUserId            = userId;
        mIceServerList     = serverList;
        mPeerEventListener = listener;
        mConnectionFactory = factory;
        mIceCandidateList  = new ArrayList<>();
        mPeerConnection    = createPeerConnection();
    }

    private PeerConnection createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(mIceServerList);
        return mConnectionFactory.createPeerConnection(rtcConfiguration, this);
    }

    private MediaConstraints getMediaConstraint() {
        MediaConstraints constraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        constraints.mandatory.addAll(keyValuePairs);
        return constraints;
    }

    public void setOffer(boolean isOffer) {
        this.isOffer = isOffer;
    }

    public void createOffer() {
        mPeerConnection.createOffer(this, getMediaConstraint());
    }

    public void createAnswer() {
        mPeerConnection.createAnswer(this, getMediaConstraint());
    }

    public void setLocalDescription(SessionDescription sdp) {
        mPeerConnection.setLocalDescription(this, sdp);
    }

    public void setRemoteDescription(SessionDescription sdp) {
        mPeerConnection.setRemoteDescription(this, sdp);
    }

    // 添加本地流
    public void addLocalStream(MediaStream stream) {
        mPeerConnection.addStream(stream);
    }

    // 添加candidate
    public void addRemoteIceCandidate(IceCandidate candidate) {
        if (mIceCandidateList != null) {
            mIceCandidateList.add(candidate);
        } else {
            mPeerConnection.addIceCandidate(candidate);
        }
    }

    // 创建render
    public void createRender(EglBase eglBase, Context context, boolean overlay) {
        mSurfaceViewRenderer = new SurfaceViewRenderer(context);
        mSurfaceViewRenderer.init(eglBase.getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {

            }

            @Override
            public void onFrameResolutionChanged(int i, int i1, int i2) {

            }
        });
        mSurfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        mSurfaceViewRenderer.setMirror(true); // 是否镜像
        mSurfaceViewRenderer.setZOrderMediaOverlay(overlay);
        if (mMediaStream != null && mMediaStream.videoTracks.size() > 0) {
            // 设置预览帧回调
            mMediaStream.videoTracks.get(0).addSink(new VideoSink() {
                @Override
                public void onFrame(VideoFrame videoFrame) {
                    mSurfaceViewRenderer.onFrame(videoFrame);
                }
            });
        }
    }

    public void close() {
        if (mSurfaceViewRenderer != null) {
            mSurfaceViewRenderer.release();
            mSurfaceViewRenderer = null;
        }
        if (mPeerConnection != null) {
            mPeerConnection.close();
        }
    }


    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED
                || iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
            mPeerEventListener.onDisconnected(mUserId);
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        mPeerEventListener.onSendIceCandidate(mUserId, iceCandidate);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        mediaStream.audioTracks.get(0).setEnabled(true);
        mMediaStream = mediaStream;
        mPeerEventListener.onRemoteStream(mUserId, mediaStream);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        mPeerEventListener.onRemoveStream(mUserId, mediaStream);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onRenegotiationNeeded() {

    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        String description = sessionDescription.description;
        SessionDescription sdp = new SessionDescription(sessionDescription.type, description);
        mSessionDescription = sdp;
        setLocalDescription(sdp);
    }

    @Override
    public void onSetSuccess() {
        // 发起方
        if (isOffer) {
            if (mPeerConnection.getRemoteDescription() == null) {
                if (!isOffer) {
                    mPeerEventListener.onSendAnswer(mUserId, mSessionDescription);
                } else {
                    mPeerEventListener.onSendOffer(mUserId, mSessionDescription);
                }
            } else {
                addCandidates();
            }
        } else {
            if (mPeerConnection.getLocalDescription() != null) {
                if (!isOffer) {
                    mPeerEventListener.onSendAnswer(mUserId, mSessionDescription);
                } else {
                    mPeerEventListener.onSendOffer(mUserId, mSessionDescription);
                }
                addCandidates();
            }
        }
    }

    private void addCandidates() {
        if (mIceCandidateList != null) {
            for (IceCandidate candidate:mIceCandidateList) {
                mPeerConnection.addIceCandidate(candidate);
            }
            mIceCandidateList = null ;
        }
    }

    @Override
    public void onCreateFailure(String s) {

    }

    @Override
    public void onSetFailure(String s) {

    }


}
