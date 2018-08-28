package com.mailiang.mywebrtc;

import android.util.Log;

import org.webrtc.CameraVideoCapturer;

/**
 * Created by Administrator on 2018/5/11 0011.
 */

public class CustomCameraEventsHandle implements CameraVideoCapturer.CameraEventsHandler {

    private String TAG = MainActivity.class.getSimpleName();

    public CustomCameraEventsHandle(String logtag){
        this.TAG = logtag;
    }

    @Override
    public void onCameraError(String s) {
        Log.v(TAG,"onCameraError() called with: s = [" + s + "]");
    }

    @Override
    public void onCameraDisconnected() {
        Log.v(TAG,"onCameraDisconnected() called");
    }

    @Override
    public void onCameraFreezed(String s) {
        Log.v(TAG, "onCameraFreezed() called with: s = [" + s + "]");
    }

    @Override
    public void onCameraOpening(String s) {
        Log.v(TAG, "onCameraOpening() called with: i = [" + s + "]");
    }

    @Override
    public void onFirstFrameAvailable() {
        Log.v(TAG, "onFirstFrameAvailable() called");
    }

    @Override
    public void onCameraClosed() {
        Log.v(TAG, "onCameraClosed() called");
    }
}
