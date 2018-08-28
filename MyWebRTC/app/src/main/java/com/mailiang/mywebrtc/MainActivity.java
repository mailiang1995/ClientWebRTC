package com.mailiang.mywebrtc;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SignallingClient.SignalingInterface {

    private static final String VIDEO_TRACK_ID = "VideoDaojia";
    private static final String AUDIO_TRACK_ID = "AudioDaojia";
    private String TAG = MainActivity.class.getSimpleName();
    PeerConnectionFactory peerConnectionFactory;
    CustomCameraEventsHandle cameraEventsHandle;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints = new MediaConstraints();
    MediaStream localMS;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;

    SurfaceViewRenderer localVideoView;
    SurfaceViewRenderer remoteVideoView;
    VideoRenderer localRenderer;
    VideoRenderer remoteRenderer;

    Button hangup;
    Peer remotePeer;
    EglBase rootEglBase;

    boolean gotUserMedia;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    private String LocalId;
    private String RemoteId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initVideos();
        getIceServers();
        SignallingClient.getInstance().init(this,TAG);

        start();
    }

    private void initViews() {
        hangup = findViewById(R.id.end_call);
        localVideoView = findViewById(R.id.local_gl_surface_view);
        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
        hangup.setOnClickListener(this);
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.setZOrderMediaOverlay(true);

        cameraEventsHandle = new CustomCameraEventsHandle(TAG);
    }

    private VideoCapturer createVideoCapture(){
        VideoCapturer videoCapturer;
        videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
        return videoCapturer;
    }

    private void getIceServers() {
        //get Ice servers using xirsys
        peerIceServers.add(new PeerConnection.IceServer("stun: stun.stunprotocol.org:3478"));
        peerIceServers.add(new PeerConnection.IceServer("stun: stun.ekiga.net"));
        peerIceServers.add(new PeerConnection.IceServer("stun:stunserver.org"));
        peerIceServers.add(new PeerConnection.IceServer("stun:stun.ideasip.com"));


        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }


    public void start() {
        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory =PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .createPeerConnectionFactory();


        //Now create a VideoCapturer instance.
        VideoCapturer videoCapturerAndroid;
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));


        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }
        localVideoView.setVisibility(View.VISIBLE);
        //create a videoRenderer based on SurfaceViewRenderer instance
        localRenderer = new VideoRenderer(localVideoView);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addRenderer(localRenderer);

        localVideoView.setMirror(true);
        remoteVideoView.setMirror(true);

        localMS = peerConnectionFactory.createLocalMediaStream("MS");
        localMS.addTrack(localAudioTrack);
        localMS.addTrack(localVideoTrack);

        gotUserMedia = true;
        if (SignallingClient.getInstance().isInitiator) {
            onTryToStart();
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    @Override
    public void onTryToStart() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!SignallingClient.getInstance().isStarted && localVideoTrack != null && SignallingClient.getInstance().isChannelReady) {
                    createPeerConnection();
                    SignallingClient.getInstance().isStarted = true;
                    if (SignallingClient.getInstance().isInitiator) {

                    }
                }
            }
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

    }

    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        remotePeer.pc.addStream(stream);
    }




    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    @Override
    public void onCreatedRoom(String localID) {
        LocalId = localID;
        //showToast("You created the room " + gotUserMedia);
        //if (gotUserMedia) {
        //    SignallingClient.getInstance().emitMessage("got user media");
        //}

    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    @Override
    public void onJoinedRoom(String LocalID) {
        LocalId = LocalID;
        //showToast("You joined the room " + gotUserMedia);
        //if (gotUserMedia) {
        //    SignallingClient.getInstance().emitMessage("got user media");
        //}
    }

    @Override
    public void onNewPeerJoined(String RemoteID) {
        RemoteId = RemoteID;
        showToast("Remote Peer Joined");
        remotePeer = new Peer(RemoteID);
        //addStreamToLocalPeer();
        remotePeer.creareOffer();

    }

    @Override
    public void onRemoteHangUp(String msg) {
        showToast("Remote Peer hungup");
        runOnUiThread(this::hangup);
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received Offer");
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted) {
                onTryToStart();
            }

            try {
                remotePeer.pc.setRemoteDescription(remotePeer, new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type")), data.getString("sdp")));
                doAnswer();
                updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer() {
        remotePeer.pc.createAnswer(remotePeer, sdpConstraints);
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */

    @Override
    public void onAnswerReceived(JSONObject data) {
        showToast("Received Answer");
        try {
            remotePeer.pc.setRemoteDescription(remotePeer, new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type")), data.getString("sdp")));
            updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remote IceCandidate received
     */
    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            remotePeer.pc.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void updateVideoViews(final boolean remoteVisible) {
        /*runOnUiThread(() -> {
            ViewGroup.LayoutParams params = localVideoView.getLayoutParams();
            if (remoteVisible) {
                params.height = dpToPx(100);
                params.width = dpToPx(100);
            } else {
                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            localVideoView.setLayoutParams(params);
        });*/

    }


    /**
     * Closing up - normal hangup and app destroye
     */

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.end_call: {
                hangup();
                break;
            }
        }
    }

    private void hangup() {
        try {
            remotePeer.release();
            SignallingClient.getInstance().close();
            updateVideoViews(false);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        SignallingClient.getInstance().close();
        videoSource.dispose();
        super.onDestroy();
    }

    /**
     * Util Methods
     */
    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }
    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.v(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.v(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, cameraEventsHandle);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.v(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.v(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, cameraEventsHandle);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }





    class Peer implements PeerConnection.Observer ,SdpObserver,DataChannel.Observer{
        public PeerConnection pc;
        public String id;
        public DataChannel dc;

        private String TAG = MainActivity.class.getSimpleName();
        Peer(String id){
            this.pc = peerConnectionFactory.createPeerConnection(peerIceServers,this);
            this.id = id;
            this.pc.addStream(localMS);
            /*
            DataChannel.Init 可配参数说明：
            ordered：是否保证顺序传输；
            maxRetransmitTimeMs：重传允许的最长时间；
            maxRetransmits：重传允许的最大次数；
             */
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            this.dc = this.pc.createDataChannel("dataChannel", init);
        }

        public void sendDataChannelMessage(String message) {
            byte[] msg = message.getBytes();
            DataChannel.Buffer buffer = new DataChannel.Buffer(
                    ByteBuffer.wrap(msg),
                    false);
            this.dc.send(buffer);
        }

        public void release() {
            pc.close();
            dc.close();
            pc = null;
            dc = null;
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.v(TAG, "onSignalingChange() called with: signalingState = [" + signalingState + "]");
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.v(TAG, "onIceConnectionChange() called with: iceConnectionState = [" + iceConnectionState + "]");
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.v(TAG, "onIceConnectionReceivingChange() called with: b = [" + b + "]");
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.v(TAG, "onIceGatheringChange() called with: iceGatheringState = [" + iceGatheringState + "]");
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.v(TAG, "onIceCandidate() called with: iceCandidate = [" + iceCandidate + "]");
            SignallingClient.getInstance().emitIceCandidate(iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.v(TAG, "onIceCandidatesRemoved() called with: iceCandidates = [" + iceCandidates + "]");
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.v(TAG, "onAddStream() called with: mediaStream = [" + mediaStream + "]");
            showToast("Received Remote stream");
            final VideoTrack videoTrack = mediaStream.videoTracks.get(0);
            remoteRenderer = new VideoRenderer(remoteVideoView);
            videoTrack.addRenderer(remoteRenderer);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.v(TAG, "onRemoveStream() called with: mediaStream = [" + mediaStream + "]");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.v(TAG, "onDataChannel() called with: dataChannel = [" + dataChannel + "]");
            dataChannel.registerObserver(this);
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded() called");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.v(TAG, "onAddTrack() called with: rtpReceiver = [" + rtpReceiver + "], mediaStreams = [" + mediaStreams + "]");
        }

        @Override
        public void onBufferedAmountChange(long l) {
            Log.v(TAG, "onBufferedAmountChange() called with: [" + l + "]");
        }

        @Override
        public void onStateChange() {
            Log.v(TAG, "onStateChange() called");
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            Log.v(TAG, "onMessage() called with: [" + buffer + "]");
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.v(TAG, "onCreateSuccess() called with: [" + sessionDescription + "]");
            Log.v(TAG, "SignallingClient emit ");
            this.pc.setLocalDescription(Peer.this,sessionDescription);
            SignallingClient.getInstance().emitMessage(sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            Log.v(TAG, "onSetSuccess() called");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.v(TAG, "onCreateFailure() called with: [" + s + "]");
        }

        @Override
        public void onSetFailure(String s) {
            Log.v(TAG, "onSetFailure() called with: [" + s + "]");
        }

        public void creareOffer() {
            this.pc.createOffer(this, sdpConstraints);
        }
    }
}
