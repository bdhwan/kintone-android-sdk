package com.altamirasoft.kintone_android_sdk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.cybozu.kintone.database.AppDto;
import com.cybozu.kintone.database.Connection;

import java.util.List;
import java.util.concurrent.Callable;

import bolts.Task;

public class MainActivity extends AppCompatActivity {


    EditText username;
    EditText password;
    EditText host;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        host = (EditText) findViewById(R.id.host);
        username = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);

    }

    public void clickLogin(View v) {

        final String hostString = host.getText().toString();
        final String usernameString = username.getText().toString();
        final String passwordString = password.getText().toString();

        try {

            Task.callInBackground(new Callable<Object>() {
                @Override
                public Object call() throws Exception {

                    Connection db = new Connection(hostString, usernameString, passwordString);
                    List<AppDto> appList = db.getApps(null);
                    Log.d("log", "appList = " + appList.size());
                    return null;
                }
            });

        } catch (Exception e) {
            Log.d("log", "ex = " + e.toString());
            return;
        }
    }


}
