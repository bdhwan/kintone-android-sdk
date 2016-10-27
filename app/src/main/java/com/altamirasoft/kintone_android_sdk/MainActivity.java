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
import com.cybozu.kintone.database.FileDto;
import com.cybozu.kintone.database.Record;
import com.cybozu.kintone.database.ResultSet;

import java.io.File;
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

            Log.d("log", "clickLogin, "+hostString+", "+usernameString+", "+passwordString);


            Task.callInBackground(new Callable<Object>() {
                @Override
                public Object call() throws Exception {

                    Connection db = new Connection(hostString, usernameString, passwordString);

                    AppDto aApp = db.getApp(79);
                    String query = "order by $id desc limit 2 offset 0";
                    ResultSet result =  db.select(aApp.getAppId(),query);
                    List<Record> records = result.getRecords();
                    Log.d("log", "records = " +records.size());
                    for(int j=0;j<records.size();j++){
                        Record aRecord = records.get(j);

                        Log.d("log",aRecord.getId()+", aRecord = "+ aRecord.getString("FileVersion"));

                        Log.d("log","fields ="+aRecord.getFieldNames().toString());

                        List<FileDto> files =   aRecord.getFiles("VXM");
                        Log.d("log","files ="+files.size());

                        String fileKey = files.get(0).getFileKey();
                        Log.d("log","fileKey ="+fileKey);
                        String fileUrl = files.get(0).getUrl();
                        Log.d("log","fileUrl ="+fileUrl);

                        File file=  db.downloadFile(fileKey);
                        Log.d("log","file ="+file.getAbsolutePath());
                    }



                    List<AppDto> appList = db.getApps(null);
                    Log.d("log", "appList = " + appList.size());

                    for(int i =0;i<appList.size();i++){


                        if(!aApp.getName().equals("vxm-applicationa")){

//
//                            sb.append("limit=");
//                            sb.append(limit);
//                            sb.append("&offset=");
//                            sb.append(offset);
//

//                            StringBuilder sb = new StringBuilder();
//                            sb.append("&limit=");
//                            sb.append(1);


//                            String query = new String(sb);


//                            String query = "FileVersion=\"07ad8920-941b-11e6-8d8b-63a74c4bc2a0\" limit 1 offset 0";
//                            String query = "order by $id desc limit 2 offset 0";
//                            ResultSet result =  db.select(aApp.getAppId(),query);
//                            List<Record> records = result.getRecords();
//                            Log.d("log", "records = " +records.size());
//                            for(int j=0;j<records.size();j++){
//                                Record aRecord = records.get(j);
//
//                                Log.d("log",aRecord.getId()+", aRecord = "+ aRecord.getString("FileVersion"));
//
//                                Log.d("log","fields ="+aRecord.getFieldNames().toString());
//
//                                List<FileDto> files =   aRecord.getFiles("VXM");
//                                Log.d("log","files ="+files.size());
//
//                                String fileKey = files.get(0).getFileKey();
//                                Log.d("log","fileKey ="+fileKey);
//                                String fileUrl = files.get(0).getUrl();
//                                Log.d("log","fileUrl ="+fileUrl);
//
//                                File file=  db.downloadFile(fileKey);
//                                Log.d("log","file ="+file.getAbsolutePath());
//                            }
                        }
                    }
                    return null;
                }
            });

        } catch (Exception e) {
            Log.d("log", "ex = " + e.toString());
            return;
        }
    }


}
