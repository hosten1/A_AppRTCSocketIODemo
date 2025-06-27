/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.lym.javaapprtcsocketiodemo;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.PeerConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback;

/**
 * Peer connection client implementation.
 *
 * <p>All public methods are routed to local looper thread.
 * All PeerConnectionEvents callbacks are invoked from the same looper thread.
 * This class is a singleton.
 */
/*
 * 改造后的 PeerConnectionClient 类
 * 支持多个 PeerConnection 实例
 * 分离 offer 和 answer SDP 回调
 */
public class PeerConnectionClient {

  public static final String VIDEO_TRACK_ID = "ARDAMSv0";
  public static final String AUDIO_TRACK_ID = "ARDAMSa0";
  public static final String VIDEO_TRACK_TYPE = "video";
  private static final String TAG = "PCRTCClient";
  public static final String VIDEO_CODEC_VP8 = "VP8";
  public static final String VIDEO_CODEC_VP9 = "VP9";
  public static final String VIDEO_CODEC_H264 = "H264";
  public static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
  public static final String VIDEO_CODEC_H264_HIGH = "H264 High";
  public static final String AUDIO_CODEC_OPUS = "opus";
  public static final String AUDIO_CODEC_ISAC = "ISAC";
  private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
  private static final String VIDEO_FLEXFEC_FIELDTRIAL =
          "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
  private static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
  private static final String DISABLE_WEBRTC_AGC_FIELDTRIAL =
          "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";
  private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
  private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
  private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
  private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
  private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
  private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
  private static final int HD_VIDEO_WIDTH = 1280;
  private static final int HD_VIDEO_HEIGHT = 720;
  private static final int BPS_IN_KBPS = 1000;
  private static final String RTCEVENTLOG_OUTPUT_DIR_NAME = "rtc_event_log";

  // Executor thread is started once in private ctor and is used for all
  // peer connection API calls to ensure new peer connection factory is
  // created on the same thread as previously destroyed factory.
  private static final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final Timer statsTimer = new Timer();
  private final EglBase rootEglBase;
  private final Context appContext;
  private final PeerConnectionParameters peerConnectionParameters;
  private final PeerConnectionEvents events;
  private final PeerConnectionFactory.Options factoryOptions;

  @Nullable
  private PeerConnectionFactory factory;
  private boolean preferIsac;
  private int videoWidth;
  private int videoHeight;
  private int videoFps;
  private MediaConstraints audioConstraints;
  private MediaConstraints sdpMediaConstraints;
  private boolean renderVideo = true;
  private boolean enableAudio = true;
  public VideoTrack localVideoTrack;


  // 封装单个 PeerConnection 的状态
  private static class PeerConnectionHolder {
    public PeerConnection peerConnection;
    public SessionDescription localSdp;
    public List<IceCandidate> queuedRemoteCandidates;
    public boolean isInitiator;
    public RtpSender localVideoSender;
    public VideoTrack remoteVideoTrack;
    public DataChannel dataChannel;
    public VideoCapturer videoCapturer;
    public VideoSource videoSource;
    public SurfaceTextureHelper surfaceTextureHelper;
    public boolean videoCapturerStopped = true;
    public final String connectionId;
    public boolean isError;
    public AudioSource audioSource;
    public AudioTrack localAudioTrack;
    public RtcEventLog rtcEventLog;

    public PeerConnectionHolder(String connectionId) {
      this.connectionId = connectionId;
    }
  }

  // 修改后的回调接口
  public interface PeerConnectionEvents {
    void onLocalOffer(String connectionId, SessionDescription sdp);

    void onLocalAnswer(String connectionId, SessionDescription sdp);

    void onIceCandidate(String connectionId, IceCandidate candidate);

    void onIceCandidatesRemoved(String connectionId, IceCandidate[] candidates);

    void onIceConnected(String connectionId);

    void onIceDisconnected(String connectionId);

    void onConnected(String connectionId);

    void onDisconnected(String connectionId);

    void onPeerConnectionClosed(String connectionId);

    void onPeerConnectionStatsReady(String connectionId, StatsReport[] reports);

    void onPeerConnectionError(String connectionId, String description);
  }

  private final Map<String, PeerConnectionHolder> peerConnections = new ConcurrentHashMap<>();
  private final boolean dataChannelEnabled;

  /**
   * Peer connection parameters.
   */
  public static class DataChannelParameters {
    public final boolean ordered;
    public final int maxRetransmitTimeMs;
    public final int maxRetransmits;
    public final String protocol;
    public final boolean negotiated;
    public final int id;

    public DataChannelParameters(boolean ordered, int maxRetransmitTimeMs, int maxRetransmits,
                                 String protocol, boolean negotiated, int id) {
      this.ordered = ordered;
      this.maxRetransmitTimeMs = maxRetransmitTimeMs;
      this.maxRetransmits = maxRetransmits;
      this.protocol = protocol;
      this.negotiated = negotiated;
      this.id = id;
    }
  }

  /**
   * Peer connection parameters.
   */
  public static class PeerConnectionParameters {
    public final boolean videoCallEnabled;
    public final boolean loopback;
    public final boolean tracing;
    public final int videoWidth;
    public final int videoHeight;
    public final int videoFps;
    public final int videoMaxBitrate;
    public final String videoCodec;
    public final boolean videoCodecHwAcceleration;
    public final boolean videoFlexfecEnabled;
    public final int audioStartBitrate;
    public final String audioCodec;
    public final boolean noAudioProcessing;
    public final boolean aecDump;
    public final boolean saveInputAudioToFile;
    public final boolean useOpenSLES;
    public final boolean disableBuiltInAEC;
    public final boolean disableBuiltInAGC;
    public final boolean disableBuiltInNS;
    public final boolean disableWebRtcAGCAndHPF;
    public final boolean enableRtcEventLog;
    private final DataChannelParameters dataChannelParameters;

    public PeerConnectionParameters(boolean videoCallEnabled, boolean loopback, boolean tracing,
                                    int videoWidth, int videoHeight, int videoFps, int videoMaxBitrate, String videoCodec,
                                    boolean videoCodecHwAcceleration, boolean videoFlexfecEnabled, int audioStartBitrate,
                                    String audioCodec, boolean noAudioProcessing, boolean aecDump, boolean saveInputAudioToFile,
                                    boolean useOpenSLES, boolean disableBuiltInAEC, boolean disableBuiltInAGC,
                                    boolean disableBuiltInNS, boolean disableWebRtcAGCAndHPF, boolean enableRtcEventLog,
                                    DataChannelParameters dataChannelParameters) {
      this.videoCallEnabled = videoCallEnabled;
      this.loopback = loopback;
      this.tracing = tracing;
      this.videoWidth = videoWidth;
      this.videoHeight = videoHeight;
      this.videoFps = videoFps;
      this.videoMaxBitrate = videoMaxBitrate;
      this.videoCodec = videoCodec;
      this.videoFlexfecEnabled = videoFlexfecEnabled;
      this.videoCodecHwAcceleration = videoCodecHwAcceleration;
      this.audioStartBitrate = audioStartBitrate;
      this.audioCodec = audioCodec;
      this.noAudioProcessing = noAudioProcessing;
      this.aecDump = aecDump;
      this.saveInputAudioToFile = saveInputAudioToFile;
      this.useOpenSLES = useOpenSLES;
      this.disableBuiltInAEC = disableBuiltInAEC;
      this.disableBuiltInAGC = disableBuiltInAGC;
      this.disableBuiltInNS = disableBuiltInNS;
      this.disableWebRtcAGCAndHPF = disableWebRtcAGCAndHPF;
      this.enableRtcEventLog = enableRtcEventLog;
      this.dataChannelParameters = dataChannelParameters;
    }
  }

  /**
   * Create a PeerConnectionClient with the specified parameters. PeerConnectionClient takes
   * ownership of |eglBase|.
   */
  public PeerConnectionClient(Context appContext, EglBase eglBase,
                              PeerConnectionParameters peerConnectionParameters, PeerConnectionEvents events,
                              PeerConnectionFactory.Options factoryOptions) {
    this.rootEglBase = eglBase;
    this.appContext = appContext;
    this.events = events;
    this.peerConnectionParameters = peerConnectionParameters;
    this.dataChannelEnabled = peerConnectionParameters.dataChannelParameters != null;
    this.factoryOptions = factoryOptions;

    Log.d(TAG, "Preferred video codec: " + getSdpVideoCodecName(peerConnectionParameters));

    final String fieldTrials = getFieldTrials(peerConnectionParameters);
    executor.execute(() -> {
      Log.d(TAG, "Initialize WebRTC. Field trials: " + fieldTrials);
      PeerConnectionFactory.initialize(
              PeerConnectionFactory.InitializationOptions.builder(appContext)
                      .setFieldTrials(fieldTrials)
                      .setEnableInternalTracer(true)
                      .createInitializationOptions());
      // 创建 PeerConnectionFactory
      createPeerConnectionFactoryInternal(factoryOptions);
    });
  }

  public void createPeerConnection(String connectionId, final VideoSink localRender, final VideoSink remoteSink,
                                   final VideoCapturer videoCapturer, final APPRTCSignalClient.SignalingParameters signalingParameters) {
    if (peerConnectionParameters.videoCallEnabled && videoCapturer == null) {
      Log.w(TAG, "Video call enabled but no video capturer provided.");
    }
    createPeerConnection(connectionId,
            localRender, Collections.singletonList(remoteSink), videoCapturer, signalingParameters);
  }

  public void createPeerConnection(String connectionId,
                                   VideoSink localRender,
                                   List<VideoSink> remoteSinks,
                                   VideoCapturer videoCapturer,
                                   APPRTCSignalClient.SignalingParameters signalingParameters) {
    executor.execute(() -> {
      if (peerConnectionParameters == null) {
        Log.e(TAG, "Creating peer connection without initializing factory.");
        return;
      }

      if (peerConnections.containsKey(connectionId)) {
        Log.w(TAG, "Connection already exists: " + connectionId);
        return;
      }

      PeerConnectionHolder holder = new PeerConnectionHolder(connectionId);
      holder.videoCapturer = videoCapturer;

      try {
        createMediaConstraintsInternal();
        createPeerConnectionInternal(holder, signalingParameters);
        maybeCreateAndStartRtcEventLog(holder);
        peerConnections.put(connectionId, holder);

        // 设置渲染
        if (localRender != null && localVideoTrack != null) {
          localVideoTrack.addSink(localRender);
        }

        if (remoteSinks != null && holder.remoteVideoTrack != null) {
          for (VideoSink remoteSink : remoteSinks) {
            holder.remoteVideoTrack.addSink(remoteSink);
          }
        }

      } catch (Exception e) {
        reportError(connectionId, "Failed to create peer connection: " + e.getMessage());
      }
    });
  }

  public void close() {
    executor.execute(() -> {
      for (String connectionId : new ArrayList<>(peerConnections.keySet())) {
        closeConnection(connectionId);
      }
    });
  }

  private boolean isVideoCallEnabled() {
    return peerConnectionParameters.videoCallEnabled;
  }

  private void createPeerConnectionFactoryInternal(PeerConnectionFactory.Options options) {
    if (factory != null) {
      return;
    }

    if (peerConnectionParameters.tracing) {
      PeerConnectionFactory.startInternalTracingCapture(
              Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                      + "webrtc-trace.txt");
    }

    // Check if ISAC is used by default.
    preferIsac = peerConnectionParameters.audioCodec != null
            && peerConnectionParameters.audioCodec.equals(AUDIO_CODEC_ISAC);

    final AudioDeviceModule adm = createJavaAudioDevice();

    // Create peer connection factory.
    if (options != null) {
      Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
    }
    final boolean enableH264HighProfile =
            VIDEO_CODEC_H264_HIGH.equals(peerConnectionParameters.videoCodec);
    final VideoEncoderFactory encoderFactory;
    final VideoDecoderFactory decoderFactory;

    if (peerConnectionParameters.videoCodecHwAcceleration) {
      encoderFactory = new DefaultVideoEncoderFactory(
              rootEglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */, enableH264HighProfile);
      decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
    } else {
      encoderFactory = new SoftwareVideoEncoderFactory();
      decoderFactory = new SoftwareVideoDecoderFactory();
    }

    factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory();
    Log.d(TAG, "Peer connection factory created.");
    adm.release();
  }

  AudioDeviceModule createJavaAudioDevice() {
    // Enable/disable OpenSL ES playback.
    if (!peerConnectionParameters.useOpenSLES) {
      Log.w(TAG, "External OpenSLES ADM not implemented yet.");
      // TODO(magjed): Add support for external OpenSLES ADM.
    }

    // Set audio record error callbacks.
    AudioRecordErrorCallback audioRecordErrorCallback = new AudioRecordErrorCallback() {
      @Override
      public void onWebRtcAudioRecordInitError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
        reportError("", errorMessage);
      }

      @Override
      public void onWebRtcAudioRecordStartError(
              JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
        reportError("", errorMessage);
      }

      @Override
      public void onWebRtcAudioRecordError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
        reportError("", errorMessage);
      }
    };

    AudioTrackErrorCallback audioTrackErrorCallback = new AudioTrackErrorCallback() {
      @Override
      public void onWebRtcAudioTrackInitError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
        reportError("", errorMessage);
      }

      @Override
      public void onWebRtcAudioTrackStartError(
              JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
        reportError("", errorMessage);
      }

      @Override
      public void onWebRtcAudioTrackError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
        reportError("", errorMessage);
      }
    };

    return JavaAudioDeviceModule.builder(appContext)
            .setSamplesReadyCallback(null)
            .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
            .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .createAudioDeviceModule();
  }

  private void createMediaConstraintsInternal() {
    // Create video constraints if video call is enabled.
    if (isVideoCallEnabled()) {
      videoWidth = peerConnectionParameters.videoWidth;
      videoHeight = peerConnectionParameters.videoHeight;
      videoFps = peerConnectionParameters.videoFps;

      // If video resolution is not specified, default to HD.
      if (videoWidth == 0 || videoHeight == 0) {
        videoWidth = HD_VIDEO_WIDTH;
        videoHeight = HD_VIDEO_HEIGHT;
      }

      // If fps is not specified, default to 30.
      if (videoFps == 0) {
        videoFps = 30;
      }
      Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);
    }

    // Create audio constraints.
    audioConstraints = new MediaConstraints();
    // added for audio performance measurements
    if (peerConnectionParameters.noAudioProcessing) {
      Log.d(TAG, "Disabling audio processing");
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
              new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
    }
    // Create SDP constraints.
    sdpMediaConstraints = new MediaConstraints();
    sdpMediaConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
            "OfferToReceiveVideo", Boolean.toString(isVideoCallEnabled())));
  }

  private void createPeerConnectionInternal(PeerConnectionHolder holder,
                                            APPRTCSignalClient.SignalingParameters signalingParameters) {
    if (factory == null) {
      Log.e(TAG, "Peerconnection factory is not created");
      return;
    }
    Log.d(TAG, "Create peer connection for: " + holder.connectionId);

    holder.queuedRemoteCandidates = new ArrayList<>();

    PeerConnection.RTCConfiguration rtcConfig =
            new PeerConnection.RTCConfiguration(signalingParameters.iceServers);
    // TCP candidates are only useful when connecting to a server that supports
    // ICE-TCP.
    rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
    rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
    rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
    rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
    // Use ECDSA encryption.
    rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
    // Enable DTLS for normal calls and disable for loopback calls.
    rtcConfig.enableDtlsSrtp = !peerConnectionParameters.loopback;
    rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

    holder.peerConnection = factory.createPeerConnection(rtcConfig, new PCObserver(holder.connectionId));

    if (dataChannelEnabled) {
      DataChannel.Init init = new DataChannel.Init();
      init.ordered = peerConnectionParameters.dataChannelParameters.ordered;
      init.negotiated = peerConnectionParameters.dataChannelParameters.negotiated;
      init.maxRetransmits = peerConnectionParameters.dataChannelParameters.maxRetransmits;
      init.maxRetransmitTimeMs = peerConnectionParameters.dataChannelParameters.maxRetransmitTimeMs;
      init.id = peerConnectionParameters.dataChannelParameters.id;
      init.protocol = peerConnectionParameters.dataChannelParameters.protocol;
      holder.dataChannel = holder.peerConnection.createDataChannel("ApprtcDemo data", init);
    }
    holder.isInitiator = false;
    // Set INFO libjingle logging.
    // NOTE: this _must_ happen while |factory| is alive!
    Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

    List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
    if (isVideoCallEnabled() && holder.videoCapturer != null) {
      VideoTrack videoTrack = createVideoTrack(holder, holder.videoCapturer);
      localVideoTrack = videoTrack;
      holder.peerConnection.addTrack(videoTrack, mediaStreamLabels);
      holder.remoteVideoTrack = getRemoteVideoTrack(holder.peerConnection);
      if (holder.remoteVideoTrack != null) {
        holder.remoteVideoTrack.setEnabled(renderVideo);
      }
    }

    AudioTrack audioTrack = createAudioTrack();
    holder.localAudioTrack = audioTrack;
    holder.peerConnection.addTrack(audioTrack, mediaStreamLabels);

    if (isVideoCallEnabled()) {
      findVideoSender(holder);
    }

    if (peerConnectionParameters.aecDump) {
      try {
        ParcelFileDescriptor aecDumpFileDescriptor =
                ParcelFileDescriptor.open(new File(Environment.getExternalStorageDirectory().getPath()
                                + File.separator + "Download/audio.aecdump"),
                        ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE);
        factory.startAecDump(aecDumpFileDescriptor.detachFd(), -1);
      } catch (IOException e) {
        Log.e(TAG, "Can not open aecdump file", e);
      }
    }
    Log.d(TAG, "Peer connection created.");
  }

  private File createRtcEventLogOutputFile() {
    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_hhmm_ss", Locale.getDefault());
    Date date = new Date();
    final String outputFileName = "event_log_" + dateFormat.format(date) + ".log";
    return new File(
            appContext.getDir(RTCEVENTLOG_OUTPUT_DIR_NAME, Context.MODE_PRIVATE), outputFileName);
  }

  private void maybeCreateAndStartRtcEventLog(PeerConnectionHolder holder) {
    if (appContext == null || holder.peerConnection == null) {
      return;
    }
    if (!peerConnectionParameters.enableRtcEventLog) {
      Log.d(TAG, "RtcEventLog is disabled.");
      return;
    }
    holder.rtcEventLog = new RtcEventLog(holder.peerConnection);
    holder.rtcEventLog.start(createRtcEventLogOutputFile());
  }

  public void closeConnection(String connectionId) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.remove(connectionId);
      if (holder == null) return;

      closeInternal(holder);
      events.onPeerConnectionClosed(connectionId);
    });
  }

  private void closeInternal(PeerConnectionHolder holder) {
    Log.d(TAG, "Closing peer connection: " + holder.connectionId);

    if (holder.dataChannel != null) {
      holder.dataChannel.dispose();
      holder.dataChannel = null;
    }

    if (holder.peerConnection != null) {
      holder.peerConnection.dispose();
      holder.peerConnection = null;
    }

    if (holder.videoCapturer != null && !holder.videoCapturerStopped) {
      try {
        holder.videoCapturer.stopCapture();
      } catch (InterruptedException ignored) {
      }
      holder.videoCapturerStopped = true;
      holder.videoCapturer.dispose();
      holder.videoCapturer = null;
    }

    if (holder.videoSource != null) {
      holder.videoSource.dispose();
      holder.videoSource = null;
    }

    if (holder.surfaceTextureHelper != null) {
      holder.surfaceTextureHelper.dispose();
      holder.surfaceTextureHelper = null;
    }

    if (holder.audioSource != null) {
      holder.audioSource.dispose();
      holder.audioSource = null;
    }

    if (holder.localAudioTrack != null) {
      holder.localAudioTrack.dispose();
      holder.localAudioTrack = null;
    }

    if (localVideoTrack != null) {
      localVideoTrack.dispose();
      localVideoTrack = null;
    }

    if (holder.rtcEventLog != null) {
      holder.rtcEventLog.stop();
      holder.rtcEventLog = null;
    }

    Log.d(TAG, "Peer connection closed: " + holder.connectionId);
  }

  public boolean isHDVideo() {
    return isVideoCallEnabled() && videoWidth * videoHeight >= 1280 * 720;
  }

  @SuppressWarnings("deprecation") // TODO(sakal): getStats is deprecated.
  private void getStats(String connectionId) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.isError || holder.peerConnection == null) return;

      boolean success = holder.peerConnection.getStats(new StatsObserver() {
        @Override
        public void onComplete(final StatsReport[] reports) {
          events.onPeerConnectionStatsReady(connectionId, reports);
        }
      }, null);
      if (!success) {
        Log.e(TAG, "getStats() returns false for connection: " + connectionId);
      }
    });
  }

  public void enableStatsEvents(boolean enable, int periodMs) {
    if (enable) {
      try {
        statsTimer.schedule(new TimerTask() {
          @Override
          public void run() {
            for (String connectionId : peerConnections.keySet()) {
              getStats(connectionId);
            }
          }
        }, 0, periodMs);
      } catch (Exception e) {
        Log.e(TAG, "Can not schedule statistics timer", e);
      }
    } else {
      statsTimer.cancel();
    }
  }

  public void setAudioEnabled(String connectionId, final boolean enable) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;

      enableAudio = enable;
      if (holder.localAudioTrack != null) {
        holder.localAudioTrack.setEnabled(enable);
      }
    });
  }

  public void setVideoEnabled(String connectionId, final boolean enable) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;

      renderVideo = enable;
      if (localVideoTrack != null) {
        localVideoTrack.setEnabled(renderVideo);
      }
      if (holder.remoteVideoTrack != null) {
        holder.remoteVideoTrack.setEnabled(renderVideo);
      }
    });
  }

  public void createOffer(String connectionId) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.isError || holder.peerConnection == null) return;

      Log.d(TAG, "PC Create OFFER for: " + connectionId);
      holder.isInitiator = true;
      holder.peerConnection.createOffer(
              new SDPObserver(connectionId, SessionDescription.Type.OFFER),
              sdpMediaConstraints
      );
    });
  }

  public void createAnswer(String connectionId) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.isError || holder.peerConnection == null) return;

      Log.d(TAG, "PC create ANSWER for: " + connectionId);
      holder.isInitiator = false;
      holder.peerConnection.createAnswer(
              new SDPObserver(connectionId, SessionDescription.Type.ANSWER),
              sdpMediaConstraints
      );
    });
  }

  public void setRemoteDescription(String connectionId, final SessionDescription sdp) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.isError || holder.peerConnection == null) return;

      String sdpDescription = sdp.description;
      if (preferIsac) {
        sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
      }
      if (isVideoCallEnabled()) {
        sdpDescription =
                preferCodec(sdpDescription, getSdpVideoCodecName(peerConnectionParameters), false);
      }
      if (peerConnectionParameters.audioStartBitrate > 0) {
        sdpDescription = setStartBitrate(
                AUDIO_CODEC_OPUS, false, sdpDescription, peerConnectionParameters.audioStartBitrate);
      }
      Log.d(TAG, "Set remote SDP for: " + connectionId);
      SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
      holder.peerConnection.setRemoteDescription(
              new SDPObserver(connectionId, null), sdpRemote);
    });
  }

  public void addRemoteIceCandidate(String connectionId, final IceCandidate candidate) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.isError || holder.peerConnection == null) return;

      if (holder.queuedRemoteCandidates != null) {
        holder.queuedRemoteCandidates.add(candidate);
      } else {
        holder.peerConnection.addIceCandidate(candidate);
      }
    });
  }

  public void removeRemoteIceCandidates(String connectionId, final IceCandidate[] candidates) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.isError || holder.peerConnection == null) return;

      drainCandidates(holder);
      holder.peerConnection.removeIceCandidates(candidates);
    });
  }

  public void stopVideoSource(String connectionId) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;

      if (holder.videoCapturer != null && !holder.videoCapturerStopped) {
        Log.d(TAG, "Stop video source for: " + connectionId);
        try {
          holder.videoCapturer.stopCapture();
        } catch (InterruptedException e) {
        }
        holder.videoCapturerStopped = true;
      }
    });
  }

  public void startVideoSource(String connectionId) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;

      if (holder.videoCapturer != null && holder.videoCapturerStopped) {
        Log.d(TAG, "Restart video source for: " + connectionId);
        holder.videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
        holder.videoCapturerStopped = false;
      }
    });
  }

  public void setVideoMaxBitrate(String connectionId, @Nullable final Integer maxBitrateKbps) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.isError || holder.peerConnection == null) {
        return;
      }
      Log.d(TAG, "Requested max video bitrate for " + connectionId + ": " + maxBitrateKbps);
      if (holder.localVideoSender == null) {
        Log.w(TAG, "Sender is not ready for: " + connectionId);
        return;
      }

      RtpParameters parameters = holder.localVideoSender.getParameters();
      if (parameters.encodings.size() == 0) {
        Log.w(TAG, "RtpParameters are not ready for: " + connectionId);
        return;
      }

      for (RtpParameters.Encoding encoding : parameters.encodings) {
        // Null value means no limit.
        encoding.maxBitrateBps = maxBitrateKbps == null ? null : maxBitrateKbps * BPS_IN_KBPS;
      }
      if (!holder.localVideoSender.setParameters(parameters)) {
        Log.e(TAG, "RtpSender.setParameters failed for: " + connectionId);
      }
      Log.d(TAG, "Configured max video bitrate to " + maxBitrateKbps + " for: " + connectionId);
    });
  }

  private void reportError(String connectionId, final String errorMessage) {
    Log.e(TAG, "[" + connectionId + "] Peerconnection error: " + errorMessage);
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder != null && !holder.isError) {
        events.onPeerConnectionError(connectionId, errorMessage);
        holder.isError = true;
      }
    });
  }

  @Nullable
  private AudioTrack createAudioTrack() {
    if (factory == null) return null;

    AudioSource audioSource = factory.createAudioSource(audioConstraints);
    AudioTrack audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
    audioTrack.setEnabled(enableAudio);
    return audioTrack;
  }

  @Nullable
  private VideoTrack createVideoTrack(PeerConnectionHolder holder, VideoCapturer capturer) {
    if (factory == null) return null;

    holder.surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread-" + holder.connectionId,
                    rootEglBase.getEglBaseContext());
    holder.videoSource = factory.createVideoSource(capturer.isScreencast());
    capturer.initialize(holder.surfaceTextureHelper, appContext,
            holder.videoSource.getCapturerObserver());
    capturer.startCapture(videoWidth, videoHeight, videoFps);
    holder.videoCapturerStopped = false;

    return factory.createVideoTrack(VIDEO_TRACK_ID, holder.videoSource);
  }

  private void findVideoSender(PeerConnectionHolder holder) {
    if (holder.peerConnection == null) return;

    for (RtpSender sender : holder.peerConnection.getSenders()) {
      if (sender.track() != null && sender.track().kind().equals(VIDEO_TRACK_TYPE)) {
        holder.localVideoSender = sender;
        Log.d(TAG, "Found video sender for: " + holder.connectionId);
        break;
      }
    }
  }

  private VideoTrack getRemoteVideoTrack(PeerConnection peerConnection) {
    for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
      MediaStreamTrack track = transceiver.getReceiver().track();
      if (track instanceof VideoTrack) {
        return (VideoTrack) track;
      }
    }
    return null;
  }

  private static String getSdpVideoCodecName(PeerConnectionParameters parameters) {
    switch (parameters.videoCodec) {
      case VIDEO_CODEC_VP8:
        return VIDEO_CODEC_VP8;
      case VIDEO_CODEC_VP9:
        return VIDEO_CODEC_VP9;
      case VIDEO_CODEC_H264_HIGH:
      case VIDEO_CODEC_H264_BASELINE:
        return VIDEO_CODEC_H264;
      default:
        return VIDEO_CODEC_VP8;
    }
  }

  private static String getFieldTrials(PeerConnectionParameters peerConnectionParameters) {
    String fieldTrials = "";
    if (peerConnectionParameters.videoFlexfecEnabled) {
      fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
      Log.d(TAG, "Enable FlexFEC field trial.");
    }
    fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
    if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
      fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;
      Log.d(TAG, "Disable WebRTC AGC field trial.");
    }
    return fieldTrials;
  }

  @SuppressWarnings("StringSplitter")
  private static String setStartBitrate(
          String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
    String[] lines = sdpDescription.split("\r\n");
    int rtpmapLineIndex = -1;
    boolean sdpFormatUpdated = false;
    String codecRtpMap = null;
    // Search for codec rtpmap in format
    // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
    String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
    Pattern codecPattern = Pattern.compile(regex);
    for (int i = 0; i < lines.length; i++) {
      Matcher codecMatcher = codecPattern.matcher(lines[i]);
      if (codecMatcher.matches()) {
        codecRtpMap = codecMatcher.group(1);
        rtpmapLineIndex = i;
        break;
      }
    }
    if (codecRtpMap == null) {
      Log.w(TAG, "No rtpmap for " + codec + " codec");
      return sdpDescription;
    }
    Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);

    // Check if a=fmtp string already exist in remote SDP for this codec and
    // update it with new bitrate parameter.
    regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
    codecPattern = Pattern.compile(regex);
    for (int i = 0; i < lines.length; i++) {
      Matcher codecMatcher = codecPattern.matcher(lines[i]);
      if (codecMatcher.matches()) {
        Log.d(TAG, "Found " + codec + " " + lines[i]);
        if (isVideoCodec) {
          lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
        } else {
          lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
        }
        Log.d(TAG, "Update remote SDP line: " + lines[i]);
        sdpFormatUpdated = true;
        break;
      }
    }

    StringBuilder newSdpDescription = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      newSdpDescription.append(lines[i]).append("\r\n");
      // Append new a=fmtp line if no such line exist for a codec.
      if (!sdpFormatUpdated && i == rtpmapLineIndex) {
        String bitrateSet;
        if (isVideoCodec) {
          bitrateSet =
                  "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
        } else {
          bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "="
                  + (bitrateKbps * 1000);
        }
        Log.d(TAG, "Add remote SDP line: " + bitrateSet);
        newSdpDescription.append(bitrateSet).append("\r\n");
      }
    }
    return newSdpDescription.toString();
  }

  /** Returns the line number containing "m=audio|video", or -1 if no such line exists. */
  private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
    final String mediaDescription = isAudio ? "m=audio " : "m=video ";
    for (int i = 0; i < sdpLines.length; ++i) {
      if (sdpLines[i].startsWith(mediaDescription)) {
        return i;
      }
    }
    return -1;
  }

  private static String joinString(
          Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
    Iterator<? extends CharSequence> iter = s.iterator();
    if (!iter.hasNext()) {
      return "";
    }
    StringBuilder buffer = new StringBuilder(iter.next());
    while (iter.hasNext()) {
      buffer.append(delimiter).append(iter.next());
    }
    if (delimiterAtEnd) {
      buffer.append(delimiter);
    }
    return buffer.toString();
  }

  private static @Nullable
  String movePayloadTypesToFront(
          List<String> preferredPayloadTypes, String mLine) {
    // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
    final List<String> origLineParts = Arrays.asList(mLine.split(" "));
    if (origLineParts.size() <= 3) {
      Log.e(TAG, "Wrong SDP media description format: " + mLine);
      return null;
    }
    final List<String> header = origLineParts.subList(0, 3);
    final List<String> unpreferredPayloadTypes =
            new ArrayList<>(origLineParts.subList(3, origLineParts.size()));
    unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
    // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
    // types.
    final List<String> newLineParts = new ArrayList<>();
    newLineParts.addAll(header);
    newLineParts.addAll(preferredPayloadTypes);
    newLineParts.addAll(unpreferredPayloadTypes);
    return joinString(newLineParts, " ", false /* delimiterAtEnd */);
  }

  private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
    final String[] lines = sdpDescription.split("\r\n");
    final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
    if (mLineIndex == -1) {
      Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
      return sdpDescription;
    }
    // A list with all the payload types with name |codec|. The payload types are integers in the
    // range 96-127, but they are stored as strings here.
    final List<String> codecPayloadTypes = new ArrayList<>();
    // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
    final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
    for (String line : lines) {
      Matcher codecMatcher = codecPattern.matcher(line);
      if (codecMatcher.matches()) {
        codecPayloadTypes.add(codecMatcher.group(1));
      }
    }
    if (codecPayloadTypes.isEmpty()) {
      Log.w(TAG, "No payload types with name " + codec);
      return sdpDescription;
    }

    final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
    if (newMLine == null) {
      return sdpDescription;
    }
    Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
    lines[mLineIndex] = newMLine;
    return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
  }

  // 辅助方法
  private void drainCandidates(PeerConnectionHolder holder) {
    if (holder.queuedRemoteCandidates != null && holder.peerConnection != null) {
      Log.d(TAG, "Draining queued ICE candidates for: " + holder.connectionId);
      for (IceCandidate candidate : holder.queuedRemoteCandidates) {
        holder.peerConnection.addIceCandidate(candidate);
      }
      holder.queuedRemoteCandidates = null;
    }
  }

  public void switchCamera(String connectionId) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;

      if (holder.videoCapturer instanceof CameraVideoCapturer) {
        if (!isVideoCallEnabled() || holder.isError) {
          Log.e(TAG,
                  "Failed to switch camera. Video: " + isVideoCallEnabled() + ". Error : " + holder.isError);
          return;
        }
        Log.d(TAG, "Switch camera for: " + connectionId);
        CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) holder.videoCapturer;
        cameraVideoCapturer.switchCamera(null);
      } else {
        Log.d(TAG, "Will not switch camera, video capturer is not a camera for: " + connectionId);
      }
    });
  }

  public void changeCaptureFormat(String connectionId, final int width, final int height, final int framerate) {
    executor.execute(() -> {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;

      if (!isVideoCallEnabled() || holder.isError || holder.videoCapturer == null) {
        Log.e(TAG,
                "Failed to change capture format. Video: " + isVideoCallEnabled()
                        + ". Error : " + holder.isError);
        return;
      }
      Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate + " for: " + connectionId);
      holder.videoSource.adaptOutputFormat(width, height, framerate);
    });
  }

  // Implementation detail: observe ICE & stream changes and react accordingly.
  private class PCObserver implements PeerConnection.Observer {
    private final String connectionId;

    public PCObserver(String connectionId) {
      this.connectionId = connectionId;
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
      events.onIceCandidate(connectionId, candidate);
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
      events.onIceCandidatesRemoved(connectionId, candidates);
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {

    }

    @Override
    public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
      executor.execute(() -> {
        if (newState == IceConnectionState.CONNECTED) {
          events.onIceConnected(connectionId);
        } else if (newState == IceConnectionState.DISCONNECTED) {
          events.onIceDisconnected(connectionId);
        } else if (newState == IceConnectionState.FAILED) {
          reportError(connectionId, "ICE connection failed.");
        }
      });
    }

    @Override
    public void onConnectionChange(final PeerConnection.PeerConnectionState newState) {
      executor.execute(() -> {
        if (newState == PeerConnectionState.CONNECTED) {
          events.onConnected(connectionId);
        } else if (newState == PeerConnectionState.DISCONNECTED) {
          events.onDisconnected(connectionId);
        } else if (newState == PeerConnectionState.FAILED) {
          reportError(connectionId, "DTLS connection failed.");
        }
      });
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
      Log.d(TAG, "IceGatheringState for " + connectionId + ": " + newState);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
      Log.d(TAG, "IceConnectionReceiving for " + connectionId + " changed to " + receiving);
    }

    @Override
    public void onAddStream(final MediaStream stream) {
    }

    @Override
    public void onRemoveStream(final MediaStream stream) {
    }

    @Override
    public void onDataChannel(final DataChannel dc) {
      Log.d(TAG, "New Data channel " + dc.label() + " for: " + connectionId);

      if (!dataChannelEnabled)
        return;

      dc.registerObserver(new DataChannel.Observer() {
        @Override
        public void onBufferedAmountChange(long previousAmount) {
          Log.d(TAG, "Data channel buffered amount changed: " + dc.label() + ": " + dc.state());
        }

        @Override
        public void onStateChange() {
          Log.d(TAG, "Data channel state changed: " + dc.label() + ": " + dc.state());
        }

        @Override
        public void onMessage(final DataChannel.Buffer buffer) {
          if (buffer.binary) {
            Log.d(TAG, "Received binary msg over " + dc);
            return;
          }
          ByteBuffer data = buffer.data;
          final byte[] bytes = new byte[data.capacity()];
          data.get(bytes);
          String strData = new String(bytes, Charset.forName("UTF-8"));
          Log.d(TAG, "Got msg: " + strData + " over " + dc);
        }
      });
    }

    @Override
    public void onRenegotiationNeeded() {
      // No need to do anything; AppRTC follows a pre-agreed-upon
      // signaling/negotiation protocol.
    }

    @Override
    public void onAddTrack(final RtpReceiver receiver, final MediaStream[] mediaStreams) {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;
      if (holder.remoteVideoTrack == null){
        Log.d(TAG, "Add remote video track for: " + connectionId);
        // 判断是不是视频的track
        if (receiver.track() instanceof VideoTrack) { 
          holder.remoteVideoTrack = (VideoTrack) receiver.track();
        }

      }
    }
  }

  // Implementation detail: handle offer creation/signaling and answer setting,
  // as well as adding remote ICE candidates once the answer SDP is set.
  // 自定义 SDPObserver
  private class SDPObserver implements SdpObserver {
    private final String connectionId;
    private final SessionDescription.Type expectedType;

    public SDPObserver(String connectionId, SessionDescription.Type expectedType) {
      this.connectionId = connectionId;
      this.expectedType = expectedType;
    }

    @Override
    public void onCreateSuccess(final SessionDescription origSdp) {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null || holder.localSdp != null) {
        reportError(connectionId, "Multiple SDP create.");
        return;
      }

      String sdpDescription = origSdp.description;
      if (preferIsac) {
        sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
      }
      if (isVideoCallEnabled()) {
        sdpDescription =
                preferCodec(sdpDescription, getSdpVideoCodecName(peerConnectionParameters), false);
      }
      if (peerConnectionParameters.audioStartBitrate > 0) {
        sdpDescription = setStartBitrate(
                AUDIO_CODEC_OPUS, false, sdpDescription, peerConnectionParameters.audioStartBitrate);
      }

      final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
      holder.localSdp = sdp;

      // 分离回调
      if (sdp.type == SessionDescription.Type.OFFER) {
        events.onLocalOffer(connectionId, sdp);
      } else if (sdp.type == SessionDescription.Type.ANSWER) {
        events.onLocalAnswer(connectionId, sdp);
      }

      executor.execute(() -> {
        if (holder.peerConnection != null && !holder.isError) {
          Log.d(TAG, "Set local SDP for: " + connectionId);
          holder.peerConnection.setLocalDescription(this, sdp);
        }
      });
    }

    @Override
    public void onSetSuccess() {
      PeerConnectionHolder holder = peerConnections.get(connectionId);
      if (holder == null) return;

      if (holder.isInitiator) {
        if (holder.peerConnection.getRemoteDescription() == null) {
          Log.d(TAG, "Local SDP set successfully for: " + connectionId);
        } else {
          Log.d(TAG, "Remote SDP set successfully for: " + connectionId);
          drainCandidates(holder);
        }
      } else {
        if (holder.peerConnection.getLocalDescription() != null) {
          Log.d(TAG, "Local SDP set successfully for: " + connectionId);
          drainCandidates(holder);
        } else {
          Log.d(TAG, "Remote SDP set successfully for: " + connectionId);
        }
      }
    }

    @Override
    public void onCreateFailure(final String error) {
      reportError(connectionId, "createSDP error: " + error);
    }

    @Override
    public void onSetFailure(final String error) {
      reportError(connectionId, "setSDP error: " + error);
    }
  }
}
