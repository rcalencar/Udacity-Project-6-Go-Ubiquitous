package com.rodrigoalencar.sunshinewear;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

public class SunshineWatchFaceConfigListenerService extends WearableListenerService {
    private static final String TAG = "DigitalListenerService";

    @Override // WearableListenerService
    public void onMessageReceived(MessageEvent messageEvent) {

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onMessageReceived: " + messageEvent);
        }

        if (!messageEvent.getPath().equals(SunshineWatchFace.PATH_WITH_FEATURE)) {
            return;
        }

        byte[] rawData = messageEvent.getData();

        DataMap configKeysToOverwrite = DataMap.fromByteArray(rawData);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Received watch face config message: " + configKeysToOverwrite);
        }

        Intent intent = new Intent(SunshineWatchFace.FORECAST_UPDATE);
        intent.putExtra("low", configKeysToOverwrite.getString("low"));
        intent.putExtra("high", configKeysToOverwrite.getString("high"));
        intent.putExtra("weatherId", configKeysToOverwrite.getInt("weatherId"));
        sendBroadcast(intent);
    }
}
