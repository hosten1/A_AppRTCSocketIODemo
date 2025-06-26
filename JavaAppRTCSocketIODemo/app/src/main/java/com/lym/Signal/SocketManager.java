package com.lym.Signal;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import okhttp3.OkHttpClient;

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
    private static volatile SocketManager instance;
    private Socket mSocket;
    private  String mRoomName;
    private  OnSocketEventListener mListener;
    // 存储所有监听器，用于清理
    private final ConcurrentHashMap<String, Emitter.Listener> listeners = new ConcurrentHashMap<>();

    public interface OnSocketEventListener {
        void onConnected();
        void onConnecting();
        void onDisconnected();
        void onUserJoined(String roomName, String userID);
        void onUserLeaved(String roomName, String userID);
        void onRemoteUserJoined(String roomName, String userID);
        void onRemoteUserLeaved(String roomName, String userID);
        void onRoomFull(String roomName, String userID);
        void onMessage(JSONObject message);
    }
    //实现单例
    // 双重检查锁定单例
    public static SocketManager getInstance() {
        if (instance == null) {
            synchronized (SocketManager.class) {
                if (instance == null) {
                    instance = new SocketManager();
                }
            }
        }
        return instance;
    }

    public void setListener(OnSocketEventListener listener) {
        mListener = listener;
    }
    public  void  joinRoom(String serverAdd , String roomName) throws URISyntaxException {
        Log.i(TAG,"join() serverAddr = "+serverAdd +" roomName = "+roomName);
        try {

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .hostnameVerifier(SSLSocketClient.getHostnameVerifier())
                    .sslSocketFactory(SSLSocketClient.getSSLSocketFactory(),SSLSocketClient.myX509TrustManager)
                    .build();

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

//        mSocket.on(Socket.EVENT_ERROR, new Emitter.Listener() {
//            @Override
//            public void call(Object... args) {
//
//                Log.e(TAG, "onError: " + args);
//            }
//        });

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

//        mSocket.on(Socket.EVENT_CONNECTING, new Emitter.Listener() {
//            @Override
//            public void call(Object... args) {
//                Log.i(TAG, "onConnecting");
//                if (mListener != null) {
//                    mListener.onConnecting();
//                }
//            }
//        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "onDisconnected");
                if (mListener != null) {
                    mListener.onDisconnected();
                }
            }
        });
      Emitter emit = mSocket.on("joined", new Emitter.Listener() {
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
                    Ack ack;
                    Object[] _args;
                    int lastIndex = args.length - 1;
                    // 2
                    if (args.length > 0 && args[lastIndex] instanceof Ack) {
                        _args = new Object[lastIndex];
                        for (int i = 0; i < lastIndex; i++) {
                            _args[i] = args[i];
                        }
                        // 3
                        ack = (Ack) args[lastIndex];
                    } else {
                        _args = args;
                        ack = null;
                    }
                    // 4
                    if(ack != null){
                        ack.call("1234");
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
                    Log.i(TAG, "onUserLeaved, room:" + roomName + "uid:" + userId);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        mSocket.on("otherJoined", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                try {


                    JSONObject msg = (JSONObject) args[0];
                    String roomName = msg.getString("room");
                    String userId = msg.getString("id");
                    Log.e(TAG, "Received joined msg = " + args.toString());
                    if (mListener != null) {
                        mListener.onRemoteUserJoined(roomName,userId);
                    }
                    Log.i(TAG, "onRemoteUserJoined, room:" + roomName + "uid:" + userId);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
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
//                try {
                    JSONObject msgT = (JSONObject) args[0];
//                    String id = msgT.getString("id");
//                    String roomName =  msgT.getString("room");
//                    Log.i(TAG, "onMessage, room:" + roomName);
                    if (mListener != null) {
                        mListener.onMessage(msgT);
                    }


//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }


            }
        });


    }// 移除所有监听器
    // 添加监听器并存储引用
    private void addListener(String event, Emitter.Listener listener) {
        if (mSocket != null) {
            mSocket.on(event, listener);
            listeners.put(event, listener);
        }
    }
    private void removeAllListeners() {
        if (mSocket != null) {
            for (Map.Entry<String, Emitter.Listener> entry : listeners.entrySet()) {
                mSocket.off(entry.getKey(), entry.getValue());
            }
            listeners.clear();
        }
    }
    public  void  sendMsg(JSONObject message){
        Log.i(TAG, "broadcast: " + message);
        if (mSocket == null) {
            return;
        }
        mSocket.emit("message", mRoomName, message, new Ack() {
            @Override
            public void call(Object... args) {
                Log.i(TAG,"recv ack msg："+args[0].toString());
            }
        });
    }
    public  void  sendMsg(JSONObject message, Ack ack){
        Log.i(TAG, "broadcast: " + message);
        if (mSocket == null) {
            return;
        }
        mSocket.emit("message", mRoomName,message,ack);
    }

    public  void  close(){
        Log.i(TAG, "leaveRoom: " + mRoomName);
        if (mSocket != null) {
            try {
                // 1. 发送离开通知
                if (mSocket.connected()) {
                    mSocket.emit("leave", mRoomName);
                }

                // 2. 移除监听器
                removeAllListeners();

                // 3. 断开连接
                mSocket.disconnect();
                mSocket.close();
            } finally {
                mSocket = null;
            }
        }
        mRoomName = null;
    }

}
