package com.example.Signal;

import android.util.Log;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketManager {
    private static  final String TAG = "SocketManager";
    // 单例属性
    private  static SocketManager singleInstace;
    private Socket mSocket;
    private  String mRoomName;
    //实现单例
    public  static SocketManager getInstance(){
        synchronized (SocketManager.class){
            if (singleInstace == null){
                singleInstace = new SocketManager();
            }
        }
        return singleInstace;
    }
    public  void  joinRoom(String serverAdd , String roomName) throws URISyntaxException {
        Log.i(TAG,"join() serverAddr = "+serverAdd +" roomName = "+roomName);
        try {
            mSocket =  IO.socket(serverAdd);
            mSocket.connect();
        }catch (URISyntaxException e){
            e.printStackTrace();
            return;
        }
        mRoomName = roomName;
        if (mSocket == null){
            Log.e(TAG,"初始化socket 失败");

        }
        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });
        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userID = (String) args[1];
                Log.e(TAG,"Received joined msg = " + args.toString());

            }
        });


mSocket.emit("join",roomName);
    }

}
