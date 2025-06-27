package com.lym.javaapprtcsocketiodemo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.lym.Signal.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;


import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

enum  RTCLYMStats{

}
public class CallActivity extends AppCompatActivity {
    private static final int VIDEO_RESOLUTION_WIDTH = 1280;
    private static final int VIDEO_RESOLUTION_HEIGHT = 720;
    private static final int VIDEO_FPS = 30;
    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;
    //定义音视频的track id 这个没有限制 只要不重复就可以
//    public static final String VIDEO_TRACK_ID = "ARDAMSv0";//"";
//    public static final String AUDIO_TRACK_ID = "ARDAMSa0";//"";
    public static final boolean EXTRA_OPENSLES_ENABLED = true;
    public static final boolean EXTRA_ORDERED = true;
    // Peer connection statistics callback period in ms.
    private static final int STAT_CALLBACK_PERIOD = 1000;

    private static final String single_peer_id = "1111";



    // 添加工具栏变量
    private Toolbar toolbar;
    private ImageButton backButton;
    // 处理线程异常导致崩溃
    class ExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable e) { //调用此方法来进行，对异常处理，需要实现UncaughtExceptionHandler 接口
            System.out.println("Thread:" + t + " Exception message:" + e);
        }
    }
    //实现视频源的回调监听
    private static class ProxyVideoSink implements VideoSink {

        private VideoSink target;

        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (target == null) {
                Logging.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }

            target.onFrame(frame);
        }

        synchronized public void setTarget(VideoSink target) {
            this.target = target;
        }
    }

    private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
    private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
    @Nullable private PeerConnectionClient peerConnectionClient;
    @Nullable
    private APPRTCSignalClient.SignalingParameters signalingParameters;
    // 音频管理类
    @Nullable private AppRTCAudioManager audioManager;
    @Nullable
    private String mState = "init";
    //将日志输出到界面
    private TextView mLogcatView;
//    private  EglBase mRootEglBase ;
    private static final String TAG = "CallActivity";


    private final List<VideoSink> remoteSinks = new ArrayList<>();

    //继承自 surface view
    private SurfaceViewRenderer mLocalSurfaceView;
    private SurfaceViewRenderer mRemoteSurfaceView;
// Android toast
    private Toast logToast;
//    是不是运行状态
    private boolean activityRunning;
    @Nullable
    private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    private boolean connected;
    private boolean isError;
    private boolean callControlFragmentVisible = true;
    private long callStartedTimeMs;
    private boolean micEnabled = true;
    private boolean screencaptureEnabled;
    private static Intent mediaProjectionPermissionResultData;
    private static int mediaProjectionPermissionResultCode;
    // 如果是true表示可以交换本地和远端的视频显示.
    private boolean isSwappedFeeds;

//    // Controls
//    private CallFragment callFragment;
//    private HudFragment hudFragment;
//    private CpuMonitor cpuMonitor;
    private  String selfId = "";
    private  String roomId = "";
//     换存candidate信息
    private List<IceCandidate> candidateObjList;
    private boolean haveSetRemoteSdp = false;
    // 控制按钮
    private ImageButton btnSpeaker;
    private ImageButton btnMute;
    private ImageButton btnEndCall;
    private ImageButton btnSwitchCamera;

    // 状态变量
    private boolean isSpeakerOn = true;
    private boolean isMuted = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        设置当线程由于未捕获的异常而突然终止时调用的默认处理程序，并且没有为该线程定义其他处理程序。
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
        // 设置windwos 为全屏窗口
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
//                | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
//        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        setContentView(R.layout.activity_call);
        // 初始化工具栏
        // 初始化右上角返回按钮
        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed(); // 调用返回逻辑
            }
        });

        // 添加导航栏和返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false); // 隐藏默认标题
        }
        // 初始化底部控制栏按钮
        initCallControls();
        connected = false;
        signalingParameters = null;
        // 初始化换存消息的接口
        candidateObjList =new ArrayList<>();
        haveSetRemoteSdp = false;
        mLogcatView = findViewById(R.id.logShowTV);

        mLocalSurfaceView = findViewById(R.id.LocalViewRender);
        mRemoteSurfaceView = findViewById(R.id.remoteViewRender);
//        callFragment = new CallFragment();
//        hudFragment = new HudFragment();
        // 当view被点击
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                toggleCallControlFragmentVisibility();
            }
        };

        // 点击小窗口切换
        mRemoteSurfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSwappedFeeds(!isSwappedFeeds);
            }
        });

        mLocalSurfaceView.setOnClickListener(listener);
        remoteSinks.add(remoteProxyRenderer);

// 初始化 intent传递的参数 和 WebRTC提供的 EglBase
        final Intent intent = getIntent();
        final EglBase eglBase = EglBase.create();


        mLocalSurfaceView.init(eglBase.getEglBaseContext(), null);
        mLocalSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
//        设置镜像翻转
        mLocalSurfaceView.setMirror(true);
        mLocalSurfaceView.setEnableHardwareScaler(false );
//        mLocalSurfaceView.;

        mRemoteSurfaceView.init(eglBase.getEglBaseContext(), null);
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        mRemoteSurfaceView.setMirror(true);
        mRemoteSurfaceView.setEnableHardwareScaler(true /* enabled */);
        // 当view叠加的时候 来确定view的顺序
        mRemoteSurfaceView.setZOrderMediaOverlay(true);
        // Start with local feed in fullscreen and swap it to the pip when the call is connected.
//        当同时连接后需要切换把本地从全屏显示切换到小屏显示
        setSwappedFeeds(true /* isSwappedFeeds */);
        PeerConnectionClient.DataChannelParameters dataChannelParameters = null;
        if (false) {
            dataChannelParameters = new PeerConnectionClient.DataChannelParameters(EXTRA_ORDERED,
                    -1,
                    -1,
                    "",
                    false,
                    1);
        }
        peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(true, false,
                        false, VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT,VIDEO_FPS,
                        8000, PeerConnectionClient.VIDEO_CODEC_VP8,
                        true,
                       false,
                        60, PeerConnectionClient.AUDIO_CODEC_OPUS,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false, dataChannelParameters);

        // 设置socket 消息监听
        SocketManager.getInstance().setListener(mOnSignalEventListener);
        String serverAddr = intent.getStringExtra("ServerAddr");
        serverAddr = "https://"+serverAddr;
        String roomName =   intent.getStringExtra("RoomName");
        this.roomId = roomName;
        try {
            // 加入房间
            SocketManager.getInstance().joinRoom(serverAddr, roomName);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        // Create peer connection client.
        peerConnectionClient = new PeerConnectionClient(
                getApplicationContext(), eglBase, peerConnectionParameters, mEvent,options);

//        如果是本地回环需要把networkIgnoreMask标记成0
//        if (loopback) {
//            options.networkIgnoreMask = 0;
//        }

        startCall();
    }
    // 处理左上角返回按钮
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // 断开连接并关闭 Activity
        disconnect();
        super.onBackPressed();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

    }
    // Activity interfaces
    @Override
    public void onStop() {
        super.onStop();
        activityRunning = false;
        // Don't stop the video when using screencapture to allow user to show other apps to the remote
        // end.
        if (peerConnectionClient != null && !screencaptureEnabled) {
            peerConnectionClient.stopVideoSource();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        activityRunning = true;
        // Video is not paused for screencapture. See onPause.
        if (peerConnectionClient != null && !screencaptureEnabled) {
            peerConnectionClient.startVideoSource();
        }
    }

    @Override
    protected void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        disconnect();
        if (logToast != null) {
            logToast.cancel();
        }
        activityRunning = false;
        super.onDestroy();
    }
//    // CallFragment.OnCallEvents interface implementation.
//    @Override
//    public void onCallHangUp() {
//        disconnect();
//    }
//
//    @Override
//    public void onCameraSwitch() {
//        if (peerConnectionClient != null) {
//            peerConnectionClient.switchCamera();
//        }
//    }
//
//    @Override
//    public void onVideoScalingSwitch(ScalingType scalingType) {
//        fullscreenRenderer.setScalingType(scalingType);
//    }
//
//    @Override
//    public void onCaptureFormatChange(int width, int height, int framerate) {
//        if (peerConnectionClient != null) {
//            peerConnectionClient.changeCaptureFormat(width, height, framerate);
//        }
//    }
//
//    @Override
//    public boolean onToggleMic() {
//        if (peerConnectionClient != null) {
//            micEnabled = !micEnabled;
//            peerConnectionClient.setAudioEnabled(micEnabled);
//        }
//        return micEnabled;
//    }
private void initCallControls() {
    btnSpeaker = findViewById(R.id.btn_speaker);
    btnMute = findViewById(R.id.btn_mute);
    btnEndCall = findViewById(R.id.btn_end_call);
    btnSwitchCamera = findViewById(R.id.btn_switch_camera);

    // 设置按钮点击事件
    btnSpeaker.setOnClickListener(v -> toggleSpeaker());
    btnMute.setOnClickListener(v -> toggleMute());
    btnEndCall.setOnClickListener(v -> endCall());
    btnSwitchCamera.setOnClickListener(v -> switchCamera());
}

    // 切换扬声器/听筒模式
    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        if (audioManager != null) {
            if (!isSpeakerOn){
                audioManager.selectAudioDevice(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
            }else {
                audioManager.selectAudioDevice(AppRTCAudioManager.AudioDevice.EARPIECE);
            }
        }

        // 更新图标
        btnSpeaker.setImageResource(isSpeakerOn ?
                R.drawable.ic_speaker : R.drawable.ic_speaker_off);

        logAndToast(isSpeakerOn ? "扬声器已启用" : "听筒模式已启用");
    }

    // 切换静音状态
    private void toggleMute() {
        isMuted = !isMuted;
        if (peerConnectionClient != null) {
            peerConnectionClient.setAudioEnabled(!isMuted);
        }

        // 更新图标
        btnMute.setImageResource(isMuted ?
                R.drawable.ic_mic_off : R.drawable.ic_mic);

        logAndToast(isMuted ? "麦克风已静音" : "麦克风已启用");
    }

    // 结束通话
    private void endCall() {
        new AlertDialog.Builder(this)
                .setTitle("结束通话")
                .setMessage("确定要结束通话吗？")
                .setPositiveButton("结束", (dialog, which) -> disconnect())
                .setNegativeButton("取消", null)
                .show();
    }

    // 切换摄像头
    private void switchCamera() {
        if (peerConnectionClient != null) {
            peerConnectionClient.switchCamera();
            logAndToast("正在切换摄像头...");
        }
    }
    // 添加返回按钮处理
    @TargetApi(21)
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }
    private void startCall() {
        callStartedTimeMs = System.currentTimeMillis();

        // 创建一个音频管理，用于处理音频路由，音频模式，及设备的枚举
        audioManager = AppRTCAudioManager.create(getApplicationContext());

//存储当前的音频设置信息到 MODE_IN_COMMUNICATION
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(new AppRTCAudioManager.AudioManagerEvents() {
            // 这个方法会去遍历每一个可用的音频设备.
            @Override
            public void onAudioDeviceChanged(
                    AppRTCAudioManager.AudioDevice audioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
    }
    // 这个方法应改在UI线程调用
    private void callConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.i(TAG, "Call connected: delay=" + delta + "ms");
        if (peerConnectionClient == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state");
            return;
        }
        // Enable statistics callback.
        peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
        setSwappedFeeds(false /* isSwappedFeeds */);
    }

//     这个方法在音频设备发生改变的时候会调用
    // e.g 例如从听筒切换到外放

    private void onAudioManagerDevicesChanged(
            final AppRTCAudioManager.AudioDevice device, final Set<AppRTCAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }
    // Disconnect from remote resources, dispose of local resources, and exit.
    private void disconnect() {
        activityRunning = false;
        remoteProxyRenderer.setTarget(null);
        localProxyVideoSink.setTarget(null);
        if (candidateObjList.size() > 0){
            candidateObjList.clear();
            candidateObjList = null;
        }
        if (mRemoteSurfaceView != null) {
            mRemoteSurfaceView.release();
            mRemoteSurfaceView = null;
        }
//        if (videoFileRenderer != null) {
//            videoFileRenderer.release();
//            videoFileRenderer = null;
//        }
        if (mLocalSurfaceView != null) {
            mLocalSurfaceView.release();
            mLocalSurfaceView = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
        if (connected && !isError) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
//        调用系统的方法结束 activity
        finish();
        SocketManager.getInstance().close();
    }
    private @Nullable
    VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        String videoFileAsCamera = null;
        if (videoFileAsCamera != null) {
            try {
                videoCapturer = new FileVideoCapturer(videoFileAsCamera);
            } catch (IOException e) {
                reportError("Failed to open video file for emulated camera");
                return null;
            }
        } else if (screencaptureEnabled) {
            return createScreenCapturer();
        } else if (useCamera2()) {
            if (!captureToTexture()) {
                reportError(getString(R.string.camera2_texture_only_error));
                return null;
            }

            Logging.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            Logging.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
        }
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }
    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
    }

    private boolean captureToTexture() {
        return true;
    }

    private @Nullable VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @TargetApi(21)
    private @Nullable VideoCapturer createScreenCapturer() {
        if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            reportError("User didn't give permission to capture the screen.");
            return null;
        }
        return new ScreenCapturerAndroid(
                mediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                reportError("User revoked permission to capture the screen.");
            }
        });
    }
    // 交换大小窗口
    private void setSwappedFeeds(boolean isSwappedFeeds) {
        Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
        this.isSwappedFeeds = isSwappedFeeds;
        localProxyVideoSink.setTarget(isSwappedFeeds ? mLocalSurfaceView : mRemoteSurfaceView);
        remoteProxyRenderer.setTarget(isSwappedFeeds ? mRemoteSurfaceView : mLocalSurfaceView);
        mLocalSurfaceView.setMirror(isSwappedFeeds);
        mRemoteSurfaceView.setMirror(!isSwappedFeeds);
    }

    private void sendIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", 2);
            message.put("roomId",  roomId);
            message.put("id", selfId);
            JSONObject data = new JSONObject();
            data.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            data.put("sdpMid", iceCandidate.sdpMid);
            data.put("candidate", iceCandidate.sdp);
            message.put("candidate",data);
            SocketManager.getInstance().sendMsg(message);
            Log.i(TAG,  "iceCandidate" + " lym sendIceCandidate: " + message.toString());

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void sendSDP(boolean isOffer ,String sdp) {
        String sdpType = isOffer ? "offer":"answer";

        try {
            JSONObject message = new JSONObject();
            message.put("type",  isOffer?0:1);
            message.put("roomId",  roomId);
            message.put("id", selfId);
            JSONObject data = new JSONObject();
            data.put("sdp",sdp);
            data.put("type",sdpType);
            message.put("sdp", data);
            SocketManager.getInstance().sendMsg(message);
            Log.i(TAG,  sdpType + " lym sendSDP: " + message.toString());

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void printfToLogCatUtils(String msg) {
        Log.i(TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String output = mLogcatView.getText() + "\n" + msg;
                mLogcatView.setText(output);
            }
        });
    }
    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }
    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }
    private LinkedList<PeerConnection.IceServer> getIceSerVerList(){
        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
        PeerConnection.IceServer ice_server =
                PeerConnection.IceServer.builder("stun:8.137.17.218:3478")
                        .setPassword("lym123456")
                        .setUsername("lym")
                        .createIceServer();
        iceServers.add(ice_server);
        return iceServers;
    }
    private void disconnectWithErrorMessage(final String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle(getText(R.string.channel_error_title))
                .setMessage(errorMessage)
                .setCancelable(false)
                .setNeutralButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                disconnect();
                            }
                        })
                .create()
                .show();
    }

    public void onConnectedToRoom(APPRTCSignalClient.SignalingParameters params) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        });
    }

    public void onRemoteDescription(SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
                peerConnectionClient.setRemoteDescription(sdp);
                if (!signalingParameters.initiator) {
                    logAndToast("Creating ANSWER...");
                    // Create answer. Answer SDP will be sent to offering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    peerConnectionClient.createAnswer();
                }
                haveSetRemoteSdp = true;
                addIceCandidateClear();
            }
        });
    }
    private PeerConnectionClient.PeerConnectionEvents mEvent = new PeerConnectionClient.PeerConnectionEvents() {

        @Override
        public void onLocalDescription(SessionDescription sdp) {
            final long delta = System.currentTimeMillis() - callStartedTimeMs;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
                    if (signalingParameters.initiator) {
                        sendSDP(true,sdp.description);
                    } else {
                        sendSDP(false,sdp.description);
                    }
                    if (peerConnectionParameters.videoMaxBitrate > 0) {
                        Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
                        peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
                    }
                }
            });
        }

        @Override
        public void onIceCandidate(IceCandidate candidate) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sendIceCandidate(candidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                if (appRtcClient != null) {
//                    appRtcClient.sendLocalIceCandidateRemovals(candidates);
//                }
                }
            });
        }

        @Override
        public void onIceConnected() {
            final long delta = System.currentTimeMillis() - callStartedTimeMs;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logAndToast("ICE connected, delay=" + delta + "ms");
                }
            });
        }

        @Override
        public void onIceDisconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logAndToast("ICE disconnected");
                }
            });
        }

        @Override
        public void onConnected() {
            final long delta = System.currentTimeMillis() - callStartedTimeMs;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logAndToast("DTLS connected, delay=" + delta + "ms");
                    connected = true;
                    // 连接成功后 可以开始获取stats
                    callConnected();
                }
            });
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logAndToast("DTLS disconnected");
                    connected = false;
                    disconnect();
                }
            });
        }

        @Override
        public void onPeerConnectionClosed() {

        }

        @Override
        public void onPeerConnectionStatsReady(StatsReport[] reports) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isError && connected) {
//                    hudFragment.updateEncoderStatistics(reports);
                    }
                }
            });
        }

        @Override
        public void onPeerConnectionError(String description) {
            reportError(description);
        }
    };
    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // socketio的消息回调，所有的消息都要确保在主线程执行.
    private void onConnectedToRoomInternal(final APPRTCSignalClient.SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        signalingParameters = params;
        logAndToast("Creating peer connection, delay=" + delta + "ms");
        VideoCapturer videoCapturer = null;
        if (peerConnectionParameters.videoCallEnabled) {
            videoCapturer = createVideoCapturer();
        }
        peerConnectionClient.createPeerConnection(
                localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters);

        if (signalingParameters.initiator) {
            logAndToast("Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createOffer();
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient.setRemoteDescription(params.offerSdp);
                logAndToast("Creating ANSWER...");
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient.createAnswer();
                haveSetRemoteSdp = true;
                addIceCandidateClear();
            }

        }
    }
    private void addIceCandidateClear(){
        if (candidateObjList != null) {
            // Add remote ICE candidates from room.
            for (IceCandidate iceCandidate : candidateObjList) {
                peerConnectionClient.addRemoteIceCandidate(iceCandidate);
            }
            candidateObjList.clear();
        }
    }

    public void onRemoteIceCandidate(IceCandidate candidate) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 如果还没有调用setRemote 那么久先换存起来
                if (haveSetRemoteSdp){
                    if (peerConnectionClient == null) {
                        Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
                        return;
                    }
                    peerConnectionClient.addRemoteIceCandidate(candidate);
                }else{
                    candidateObjList.add(candidate);
                }

            }
        });
    }

    public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.removeRemoteIceCandidates(candidates);
            }
        });
    }

    public void onChannelClose() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("Remote end hung up; dropping PeerConnection");
                disconnect();
            }
        });
    }

    public void onChannelError(String description) {
        reportError(description);
    }
    // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.

    // 设置socket消息监听
    private  SocketManager.OnSocketEventListener mOnSignalEventListener = new SocketManager.OnSocketEventListener() {
        @Override
        public void onConnected() {
            printfToLogCatUtils("socket  connected ");
        }

        @Override
        public void onConnecting() {

        }

        @Override
        public void onDisconnected() {
            printfToLogCatUtils("socket  onDisconnected ");
        }

        @Override
        public void onUserJoined(String roomName, String userID) {
            if (!selfId.equals(userID)){
                printfToLogCatUtils("Receive self id  is " + userID);
                selfId = userID;
            }

        }

        @Override
        public void onUserLeaved(String roomName, String userID) {
            onChannelClose();

            printfToLogCatUtils("Receive Remote Hangup Event ...");
        }

        @Override
        public void onRemoteUserJoined(String roomName, String userID) {
            if (selfId.equals(userID)){
                return;
            }
            //调用call， 进行媒体协商
            printfToLogCatUtils("Receive other user joined : " + userID);
            signalingParameters = new APPRTCSignalClient.SignalingParameters(getIceSerVerList(),
                    true,
                    "1234",
                    "",
                    "",
                    null,null);
            onConnectedToRoom(signalingParameters);
        }

        @Override
        public void onRemoteUserLeaved(String roomName, String userID) {
            onChannelClose();
        }

        @Override
        public void onRoomFull(String roomName, String userID) {

        }

        @Override
        public void onMessage(JSONObject message) {
//            if (selfId.equals(userID)){
//                return;
//            }
            Log.i(TAG, "onMessage: " + message);
            try {
                int type = message.getInt(("type"));
                if (type == 0) {
                    JSONObject data =  message.getJSONObject("sdp");
                    String description = data.getString("sdp");
                    signalingParameters = new APPRTCSignalClient.SignalingParameters(getIceSerVerList(),
                            false,
                            "1234",
                            "",
                            "",
                            new SessionDescription(
                                    SessionDescription.Type.OFFER,
                                    description),null);
                    onConnectedToRoom(signalingParameters);
                }else if(type == 1) {
                    JSONObject data =  message.getJSONObject("sdp");
                    String description = data.getString("sdp");
                    onRemoteDescription(new SessionDescription(
                            SessionDescription.Type.ANSWER,
                            description));

                }else if(type == 2) {
                    JSONObject data =  message.getJSONObject("candidate");
//                    printfToLogCatUtils("receive msg :"+data.toString());
                    IceCandidate remoteIceCandidate =
                            new IceCandidate(data.getString("sdpMid"),
                                    data.getInt("sdpMLineIndex"),
                                    data.getString("candidate"));
                    onRemoteIceCandidate(remoteIceCandidate);


                }else{
                    Log.w(TAG, "the type is invalid: " + type);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };
}
