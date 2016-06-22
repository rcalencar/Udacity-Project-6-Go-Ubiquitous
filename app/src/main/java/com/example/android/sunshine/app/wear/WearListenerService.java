package com.example.android.sunshine.app.wear;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WearListenerService extends WearableListenerService {
    private static final String TAG = "WearListener";
    private static final String PATH_WITH_FEATURE = "/app_sunshine/forecast";

    @Override // WearableListenerService
    public void onMessageReceived(MessageEvent messageEvent) {

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onMessageReceived: " + messageEvent);
        }

        if (!messageEvent.getPath().equals(PATH_WITH_FEATURE)) {
            return;
        }

        byte[] rawData = messageEvent.getData();

        DataMap messageReceived = DataMap.fromByteArray(rawData);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Received wear message: " + messageReceived);
        }

        Intent intent = new Intent(this, WearUpdateService.class);
        startService(intent);

    }
}
