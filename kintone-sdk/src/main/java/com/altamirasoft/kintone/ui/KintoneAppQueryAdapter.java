package com.altamirasoft.kintone.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.cybozu.kintone.database.AppDto;
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

public abstract class KintoneAppQueryAdapter extends BaseAdapter {

    ArrayList<AppDto> objects = new ArrayList<AppDto>();
    Connection connection;
    long appId;
    String query;
    Context context;


    int limit = 25;

    boolean canLoadMore = true;

    boolean isLoading = false;

    String orderBy = "order by $id desc";


    public abstract View getItemView(AppDto object, View v, ViewGroup parent);





    public KintoneAppQueryAdapter(Context context, Connection connection, long appId){
        this.context = context;
        this.appId = appId;
        this.connection = connection;
    }

    public KintoneAppQueryAdapter(Context context, Connection connection, long appId, String query){
        this.context = context;
        this.appId = appId;
        this.connection = connection;
        this.query = query;
    }

    public KintoneAppQueryAdapter(Context context, Connection connection, long appId, String query, String orderBy){
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
                List<AppDto> tempList =  connection.getApps(null,limit,objects.size());
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
    public AppDto getItem(int index) {
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
