package com.ahoyrtc.testrtc;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.webrtc.AudioSource;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private VideoEncoderFactory encoderFactory;
    private VideoDecoderFactory decoderFactory;
    private EglBase rootEglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private MediaStream mediaStream;
    private VideoTrack videoTrack;
    private VideoCapturer videoCapturer;
    private SurfaceViewRenderer localRenderer;
    private VideoSink videoSink;
    private boolean hasPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootEglBase = EglBase.create();
        localRenderer = (SurfaceViewRenderer) findViewById(R.id.localRenderer);
        localRenderer.init(rootEglBase.getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
            }

            @Override
            public void onFrameResolutionChanged(int i, int i1, int i2) {

            }
        });

        String fieldTrials = "";

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                        .setFieldTrials(fieldTrials)
                        .setEnableVideoHwAcceleration(true)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions());

        encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true);
        decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        peerConnectionFactory = new PeerConnectionFactory(null, encoderFactory, decoderFactory);

    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasPermissions) {
            checkPermissions();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @TargetApi(23)
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{ Manifest.permission.CAMERA }
                    , 1);
            hasPermissions = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            hasPermissions = false;
        }
    }

    private void startCamera() {
        videoCapturer = createVideoCapturer(true, true, this);
        createLocalMedia(videoCapturer, 1280, 720, 30);
        if (videoTrack != null) {
            localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            localRenderer.setEnableHardwareScaler(true);

            videoSink = new VideoSink() {
                @Override
                public void onFrame(VideoFrame videoFrame) {
                    localRenderer.onFrame(videoFrame);
                    videoFrame.release();	// without this line it will memleak and crash after a few seconds
                }
            };
            videoTrack.addSink(videoSink);
            videoTrack.setEnabled(true);

            localRenderer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CameraVideoCapturer camera = (CameraVideoCapturer)videoCapturer;
                    camera.switchCamera(null);
                }
            });

        }
    }

    private VideoCapturer createVideoCapturer(boolean useFrontFacingDevice, boolean useCamera2, Context context) {
        VideoCapturer videoCapturer = null;
        if (useCamera2 && Camera2Enumerator.isSupported(context)){
            Camera2Enumerator camera2Enumerator = new Camera2Enumerator(context);
            final String[] deviceNames = camera2Enumerator.getDeviceNames();
            for (String deviceName : deviceNames) {
                if (!useFrontFacingDevice && camera2Enumerator.isBackFacing(deviceName)) {
                    return camera2Enumerator.createCapturer(deviceName, null);
                }
                if (useFrontFacingDevice && camera2Enumerator.isFrontFacing(deviceName)) {
                    return camera2Enumerator.createCapturer(deviceName, null);
                }
            }
        } else {
            Camera1Enumerator camera1Enumerator = new Camera1Enumerator();
            final String[] deviceNames = camera1Enumerator.getDeviceNames();
            for (String deviceName : deviceNames) {
                if (!useFrontFacingDevice && camera1Enumerator.isBackFacing(deviceName)) {
                }
                if (useFrontFacingDevice && camera1Enumerator.isFrontFacing(deviceName)) {
                    return camera1Enumerator.createCapturer(deviceName, null);
                }
            }
        }
        return null;
    }

    private void createLocalMedia(VideoCapturer videoCapturer, int width, int height, int framerate) {
        String label = UUID.randomUUID().toString();
        mediaStream = peerConnectionFactory.createLocalMediaStream(label);
        MediaConstraints audioConstraints = new MediaConstraints();
        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        mediaStream.addTrack(peerConnectionFactory.createAudioTrack(label + "a0", audioSource));


        if ((videoCapturer != null) && (width > 0) && (height > 0) && (framerate > 0)) {
            VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer);
            videoCapturer.startCapture(width, height, framerate);
            videoTrack = peerConnectionFactory.createVideoTrack(label + "v0", videoSource);
            mediaStream.addTrack(videoTrack);
        }
    }
}
