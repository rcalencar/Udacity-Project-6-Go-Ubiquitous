package com.example.android.sunshine.app.wear;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

/**
 * Created by rodrigo.alencar on 6/17/16.
 */
public class WearUpdateService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "WearUpdateService";

    private static final String PATH_WITH_FEATURE = "/watch_face_sunshine/sunshine";
    private static GoogleApiClient mGoogleApiClient;

    public WearUpdateService() {
        super("WearUpdateAdapter");
    }

    private void send() {
        Log.d(TAG, "send");

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
        }
        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "Failed to connect to GoogleApiClient.");
                return;
            }
        }

        String locationQuery = Utility.getPreferredLocation(this);
        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
        Cursor cursor = getContentResolver().query(weatherUri, SunshineSyncAdapter.NOTIFY_WEATHER_PROJECTION, null, null, null);

        int weatherId;
        String high;
        String low;
        if (cursor != null && cursor.moveToFirst()) {
            weatherId = cursor.getInt(SunshineSyncAdapter.INDEX_WEATHER_ID);
            double h = cursor.getDouble(SunshineSyncAdapter.INDEX_MAX_TEMP);
            double l = cursor.getDouble(SunshineSyncAdapter.INDEX_MIN_TEMP);

            high = Utility.formatTemperature(this, h);
            low = Utility.formatTemperature(this, l);

            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

            DataMap config = new DataMap();
            config.putString("low", low);
            config.putString("high", high);
            config.putInt("weatherId", weatherId);
            byte[] rawData = config.toByteArray();

            for(Node node : nodes.getNodes()) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), PATH_WITH_FEATURE, rawData);

                Log.d(TAG, "Sent watch face config message: " + node.getId() + " " + config.toString());
            }
        }

        if(cursor != null) {
            cursor.close();
        }

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        Log.d(TAG, "send: all messages has been sent (or none)");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        send();
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnected: " + connectionHint);
        }
    }

    @Override  // GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }
    }

    @Override  // GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionFailed: " + result);
        }
    }
}