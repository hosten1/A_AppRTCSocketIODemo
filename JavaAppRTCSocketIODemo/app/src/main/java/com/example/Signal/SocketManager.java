package com.example.Signal;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.WebSocket;

class SSLSocketClient {

public static  X509TrustManager myX509TrustManager = new X509TrustManager() {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
};
public  static SSLSocketFactory getSSLSocketFactory(){
    try {

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, getTrustManager(), new SecureRandom());
             return sslContext.getSocketFactory();

        } catch (Exception e) {

                throw new RuntimeException(e);

         }
}
    //获取TrustManager

private static TrustManager[] getTrustManager() {

    TrustManager[] trustAllCerts = new TrustManager[]{

     myX509TrustManager

 };

return trustAllCerts;

}

 //获取HostnameVerifier
 public static HostnameVerifier getHostnameVerifier() {

        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        return hostnameVerifier;

}


}

public class SocketManager {
    private static  final String TAG = "SocketManager";
    // 单例属性
    private  static SocketManager singleInstace;
    private Socket mSocket;
    private  String mRoomName;
    private  OnSocketEventListener mListener;

    public interface OnSocketEventListener {
        void onConnected();
        void onConnecting();
        void onDisconnected();
        void onUserJoined(String roomName, String userID);
        void onUserLeaved(String roomName, String userID);
        void onRemoteUserJoined(String roomName);
        void onRemoteUserLeaved(String roomName, String userID);
        void onRoomFull(String roomName, String userID);
        void onMessage(JSONObject message);
    }
    //实现单例
    public  static SocketManager getInstance(){
        synchronized (SocketManager.class){
            if (singleInstace == null){
                singleInstace = new SocketManager();
            }
        }
        return singleInstace;
    }
    // 设置消息监听对象
    public void setListener(final OnSocketEventListener listener) {
        mListener = listener;
    }
    public  void  joinRoom(String serverAdd , String roomName) throws URISyntaxException {
        Log.i(TAG,"join() serverAddr = "+serverAdd +" roomName = "+roomName);
        try {

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .hostnameVerifier(SSLSocketClient.getHostnameVerifier())
                    .sslSocketFactory(SSLSocketClient.getSSLSocketFactory(),SSLSocketClient.myX509TrustManager)
                    .build();

//default settings for all sockets

            IO.setDefaultOkHttpWebSocketFactory(okHttpClient);

            IO.setDefaultOkHttpCallFactory(okHttpClient);
            IO.Options opt = new  IO.Options();
            opt.secure = true;
            opt.callFactory=okHttpClient;
            opt.webSocketFactory=okHttpClient;

            mSocket =  IO.socket(serverAdd,opt);
            mSocket.connect();
        }catch (URISyntaxException e){
            e.printStackTrace();
            return;
        }
        mRoomName = roomName;
        if (mSocket == null){
            Log.e(TAG,"初始化socket 失败");

        }
        mSocket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                Log.e(TAG, "onConnectError: " + args);
            }
        });

        mSocket.on(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                Log.e(TAG, "onError: " + args);
            }
        });

        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String sessionId = mSocket.id();
                Log.i(TAG, "onConnected");
                if (mListener != null) {
                    mListener.onConnected();
                }
                mSocket.emit("join",roomName);
            }
        });

        mSocket.on(Socket.EVENT_CONNECTING, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "onConnecting");
                if (mListener != null) {
                    mListener.onConnecting();
                }
            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "onDisconnected");
                if (mListener != null) {
                    mListener.onDisconnected();
                }
            }
        });
        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {


                    JSONObject msg = (JSONObject) args[0];
                    String roomName = msg.getString("room");
                    String userId = msg.getString("id");
                    Log.e(TAG, "Received joined msg = " + args.toString());
                    if (mListener != null) {
                        mListener.onUserJoined(roomName, userId);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mSocket.on("leaved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {


                    JSONObject msg = (JSONObject) args[0];
                    String roomName = msg.getString("room");
                    String userId = msg.getString("id");
                    Log.e(TAG, "Received joined msg = " + args.toString());
                    if (mListener != null) {
                        mListener.onUserLeaved(roomName, userId);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "onUserLeaved, room:" + roomName + "uid:" + userId);
            }
        });

        mSocket.on("otherjoin", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                try {


                    JSONObject msg = (JSONObject) args[0];
                    String roomName = msg.getString("room");
                    String userId = msg.getString("id");
                    Log.e(TAG, "Received joined msg = " + args.toString());
                    if (mListener != null) {
                        mListener.onRemoteUserJoined(roomName);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "onRemoteUserJoined, room:" + roomName + "uid:" + userId);
            }
        });

        mSocket.on("bye", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userId = (String) args[1];
                if (mListener != null) {
                    mListener.onRemoteUserLeaved(roomName, userId);
                }
                Log.i(TAG, "onRemoteUserLeaved, room:" + roomName + "uid:" + userId);

            }
        });

        mSocket.on("full", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                //释放资源
                mSocket.disconnect();
                mSocket.close();
                mSocket = null;

                String roomName = (String) args[0];
                String userId = (String) args[1];

                if (mListener != null) {
                    mListener.onRoomFull(roomName, userId);
                }

                Log.i(TAG, "onRoomFull, room:" + roomName + "uid:" + userId);

            }
        });

        mSocket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject msgT = (JSONObject) args[0];
                    String id = msgT.getString("id");
                    Log.e(TAG, "Received joined msg = " + args.toString());
                    JSONObject msg = msgT.getJSONObject("sdp");

                    if (mListener != null) {
                        mListener.onMessage(msg);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                Log.i(TAG, "onMessage, room:" + roomName + "data:" + msg);

            }
        });


    }
    public  void  sendMsg(JSONObject message){
        Log.i(TAG, "broadcast: " + message);
        if (mSocket == null) {
            return;
        }
        mSocket.emit("message", mRoomName, message);
    }

    public  void  close(){
        Log.i(TAG, "leaveRoom: " + mRoomName);
        if (mSocket != null)
            mSocket.emit("leave", mRoomName);
            mSocket.close();
            mSocket = null;
    }

}
