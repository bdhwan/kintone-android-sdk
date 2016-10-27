package com.altamirasoft.kintone.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.cybozu.kintone.database.Connection;
import com.cybozu.kintone.database.Record;
import com.cybozu.kintone.database.ResultSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;

/**
 * Created by bdhwan on 2016. 10. 25..
 */

public abstract class KintoneRecordQueryAdapter extends BaseAdapter {

    ArrayList<Record> objects = new ArrayList<Record>();
    Connection connection;
    long appId;
    String query;
    Context context;


    int limit = 25;

    boolean canLoadMore = true;

    boolean isLoading = false;

    String orderBy = "order by $id desc";


    public abstract View getItemView(Record object, View v, ViewGroup parent);





    public KintoneRecordQueryAdapter(Context context, Connection connection, long appId){
        this.context = context;
        this.appId = appId;
        this.connection = connection;
    }

    public KintoneRecordQueryAdapter(Context context, Connection connection, long appId, String query){
        this.context = context;
        this.appId = appId;
        this.connection = connection;
        this.query = query;
    }

    public KintoneRecordQueryAdapter(Context context, Connection connection, long appId, String query, String orderBy){
        this.context = context;
        this.appId = appId;
        this.connection = connection;
        this.query = query;
        this.orderBy = orderBy;
    }


    public void reloadObjects(){
        objects.clear();
        notifyDataSetChanged();
        loadObjects();
    }


    public void loadObjects(){
        if(isLoading)return;
        isLoading = true;
        Task.callInBackground(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                String tempQuery = null;
                if(TextUtils.isEmpty(query)){
                    tempQuery = orderBy+" limit "+limit+" offset "+objects.size();
                }
                else{
                    tempQuery = query+" "+ orderBy+" limit "+limit+" offset "+objects.size();
                }

                ResultSet result =  connection.select(appId,tempQuery.trim());
                List<Record> tempList = result.getRecords();
                for(int j=0;j<tempList.size();j++) {
                    Record aRecord = tempList.get(j);
//                    Log.d("log","user ="+aRecord.getString("user"));
                    Log.d("log","filedsName ="+aRecord.getFieldNames().toString());

                }
                objects.addAll(tempList);
                canLoadMore = limit == tempList.size();
                return null;
            }
        }).continueWith(new Continuation<Object, Object>() {
            @Override
            public Object then(Task<Object> task) throws Exception {
                notifyDataSetChanged();
                isLoading = false;
                return null;
            }
        },Task.UI_THREAD_EXECUTOR);
    }


    @Override
    public Record getItem(int index) {
        return this.objects.get(index);
    }

    @Override
    public final View getView(int position, View convertView, ViewGroup parent) {
        return this.getItemView(this.getItem(position), convertView, parent);
    }


    @Override
    public int getCount() {
        return objects.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }



}
