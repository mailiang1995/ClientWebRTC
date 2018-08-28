package com.mailiang.mywebrtc;

import android.util.Log;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Created by Administrator on 2018/5/11 0011.
 */

public class SignallingClient {
    private String TAG = MainActivity.class.getSimpleName();
    private static SignallingClient instance;
    private String roomName = null;
    private Socket socket;
    boolean isChannelReady = false;
    boolean isInitiator = false;
    boolean isStarted = false;
    private SignalingInterface callback;
    private String localId;
    private String remoteId;

    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) {
        }
    }};

    public static SignallingClient getInstance() {
        if (instance == null) {
            instance = new SignallingClient();
        }
        if (instance.roomName == null) {
            //set the room name here
            instance.roomName = "1";
        }
        return instance;
    }


    public void init(SignalingInterface signalingInterface,String logtag) {
        this.callback = signalingInterface;
        this.TAG = logtag;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            //set the socket.io url here
            socket = IO.socket("http://120.79.10.196:3000");
            socket.connect();
            Log.v(TAG, "init() called");

            if (!roomName.isEmpty()) {
                emitInitStatement(roomName);
            }


            //room created event.
            socket.on("created", args -> {
                Log.v(TAG, "created call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String){
                    isInitiator = true;
                    localId = (String) args[0];
                    callback.onCreatedRoom(localId);
                }

            });

            //room is full event
            socket.on("full", args -> Log.d(TAG, "full call() called with: args = [" + Arrays.toString(args) + "]"));

            //peer joined event
            socket.on("join", args -> {
                Log.v(TAG, "join call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String){
                    String id = (String)args[0];
                    if (!localId.equals(id)){
                        remoteId = id;
                        isChannelReady = true;
                        callback.onNewPeerJoined(remoteId);
                    }

                }
            });

            //when you joined a chat room successfully
            socket.on("joined", args -> {
                Log.v(TAG, "joined call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String){
                    isChannelReady = true;
                    localId = (String)args[0];
                    callback.onJoinedRoom(localId);
                }
            });

            //log event
            socket.on("log", args -> Log.v(TAG, "log call() called with: args = [" + Arrays.toString(args) + "]"));

            //bye event
            socket.on("bye", args -> callback.onRemoteHangUp((String) args[0]));

            //messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                Log.v(TAG, "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.v(TAG, "String received :: " + args[0]);
                    String data = (String) args[0];
                    if (data.equalsIgnoreCase("got user media")) {
                        callback.onTryToStart();
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        callback.onRemoteHangUp(data);
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {

                        JSONObject data = (JSONObject) args[0];
                        Log.v(TAG, "Json Received :: " + data.toString());
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(data);
                        } else if (type.equalsIgnoreCase("answer") && isStarted) {
                            callback.onAnswerReceived(data);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            callback.onIceCandidateReceived(data);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(String message) {
        Log.v(TAG, "emitInitStatement() called with: event = [" + "create or join" + "], message = [" + message + "]");
        socket.emit("create or join", message);
    }

    public void emitMessage(String message) {

        try {
            Log.v(TAG, "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("room",roomName);
            obj.put("message",message);
            socket.emit("message", obj);
            Log.v(TAG, obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void emitMessage(SessionDescription message) {
        try {
            Log.v(TAG, "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("room",roomName);
            obj.put("sdp", message.description);
            Log.v("emitMessage", obj.toString());
            socket.emit("message", obj);
            Log.v(TAG, obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("room",roomName);
            object.put("candidate", iceCandidate.sdp);
            socket.emit("message", object);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void close() {
        socket.emit("bye", roomName);
        socket.disconnect();
        socket.close();
    }


    interface SignalingInterface {
        void onRemoteHangUp(String msg);

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);

        void onTryToStart();

        void onCreatedRoom(String localID);

        void onJoinedRoom(String localID);

        void onNewPeerJoined(String remoteID);
    }
}
