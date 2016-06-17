package com.example.android.sunshine.app.sync;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by rodrigo.alencar on 6/17/16.
 */
public class WearUpdateService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "WearUpdateService";

    public static final String COMMAND = "command";
    private static final String PATH_WITH_FEATURE = "/watch_face_sunshine/Sunshine";
    public static final byte CONNECT = 1;
    public static final byte SEND = 2;

    private static GoogleApiClient mGoogleApiClient;

    public WearUpdateService() {
        super("WearUpdateAdapter");
    }

    private void send() {
        Log.d(TAG, "send");

        String locationQuery = Utility.getPreferredLocation(this);
        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
        Cursor cursor = getContentResolver().query(weatherUri, SunshineSyncAdapter.NOTIFY_WEATHER_PROJECTION, null, null, null);

        int weatherId;
        String high;
        String low;
        if (cursor.moveToFirst()) {
            weatherId = cursor.getInt(SunshineSyncAdapter.INDEX_WEATHER_ID);
            double h = cursor.getDouble(SunshineSyncAdapter.INDEX_MAX_TEMP);
            double l = cursor.getDouble(SunshineSyncAdapter.INDEX_MIN_TEMP);

            int iconId = Utility.getIconResourceForWeatherCondition(weatherId);

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

        cursor.close();

        disconnect();

        Log.d(TAG, "send: all messages has been sent (or none)");
    }

    private void connect() {
        Log.d(TAG, "connect");

        if(mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();

        } else if(mGoogleApiClient.isConnected()) {
            Intent intent = new Intent(this, WearUpdateService.class);
            intent.putExtra(COMMAND, SEND);
            startService(intent);

        } else if(!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    private void disconnect() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        byte command = intent.getByteExtra(COMMAND, CONNECT);
        switch (command) {
            case CONNECT:
                connect();
                break;

            case SEND:
                send();
                break;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        super.onDestroy();
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected: " + connectionHint);

        Intent intent = new Intent(this, WearUpdateService.class);
        intent.putExtra(COMMAND, SEND);
        startService(intent);
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended: " + cause);
    }

    @Override // GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed: " + result);
    }
}
