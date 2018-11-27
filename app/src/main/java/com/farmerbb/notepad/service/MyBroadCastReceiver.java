package com.farmerbb.notepad.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class MyBroadCastReceiver extends BroadcastReceiver {

    public interface ReceiverListener {
            public void receivedIntent(Intent intent);
    }

    private ReceiverListener mListener = null;

    public void setmListener(ReceiverListener listener) {
        mListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        if (mListener != null){
            mListener.receivedIntent(intent);
        }
    }
}
