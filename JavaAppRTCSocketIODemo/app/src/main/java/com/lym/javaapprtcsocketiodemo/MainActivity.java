package com.lym.javaapprtcsocketiodemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        System.out.println("输入你的姓名：");
        final EditText serverAddrET = findViewById(R.id.ServerNameET);
        final EditText roomNamerET = findViewById(R.id.RoomNameET);
        final Button joinBtn = findViewById(R.id.JoinBtn);
        joinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              String serverAdd = serverAddrET.getText().toString() + ":" +"443";
              String roomName = roomNamerET.getText().toString();
              Log.i("INFO","输入的值是：" + serverAdd + "  "+roomName);
            if (!serverAdd.isEmpty() && !roomName.isEmpty()){
                Intent callIntent = new Intent(MainActivity.this,CallActivity.class);
                callIntent.putExtra("ServerAddr",serverAdd);
                callIntent.putExtra("RoomName",roomName);
                //启动自定义的active
                startActivity(callIntent);
            }
            }
        });
        // 动态权限申请
        String [] params = {
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_NETWORK_STATE,
        };
        if (!EasyPermissions.hasPermissions(this,params)){
            EasyPermissions.requestPermissions(this,"我们需要你同意使用麦克风和摄像头权限",0,params);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 做一些操作
        Log.i("INFO","权限结果是：" + requestCode);
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults);
    }
}
