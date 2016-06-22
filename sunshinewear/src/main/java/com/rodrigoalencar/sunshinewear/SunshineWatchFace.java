package com.rodrigoalencar.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    public static final String TAG = "SunshineWatchFace";
    public static final String PATH_WITH_FEATURE = "/watch_face_sunshine/sunshine";
    public static final String FORECAST_UPDATE = "FORECAST_UPDATE";
    private static final int WEATHER_NONE = -1;

    public static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    public static final Typeface NORMAL_THIN = Typeface.create("sans-serif-condensed-light", Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    public static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    public static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;

        Paint mTextPaint;
        Paint mTextPaintDate;
        Paint mTextPaintMax;
        Paint mTextPaintMin;

        boolean mAmbient;

        float mXOffset;
        float mYOffsetTime;
        float mYOffsetDate;
        float mYOffsetDivider;
        float mYOffsetTemp;
        float mMargin;
        float mLineSize;

        private String mTempMIN = null;
        private String mTempMAX = null;
        private int mWeatherId = WEATHER_NONE;
        private long mWeatherUpdate;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        final BroadcastReceiver mForecastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTempMIN = intent.getStringExtra("low");
                mTempMAX = intent.getStringExtra("high");
                mWeatherId = intent.getIntExtra("weatherId", WEATHER_NONE);
                mWeatherUpdate = System.currentTimeMillis();

                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            mMargin = resources.getDimension(R.dimen.temp_margin);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);
            mTextPaintDate = createTextPaint(resources.getColor(R.color.digital_text_date), NORMAL_TYPEFACE);
            mTextPaintMax = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);
            mTextPaintMin = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_THIN);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                queryForecast();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {

                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);

            IntentFilter filterForecast = new IntentFilter(FORECAST_UPDATE);
            SunshineWatchFace.this.registerReceiver(mForecastReceiver, filterForecast);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            SunshineWatchFace.this.unregisterReceiver(mForecastReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float textSizeDate = resources.getDimension(isRound
                    ? R.dimen.digital_text_date_size_round : R.dimen.digital_text_date_size);

            float textSizeMax = resources.getDimension(isRound
                    ? R.dimen.digital_text_max_size_round : R.dimen.digital_text_max_size);

            mYOffsetTime = resources.getDimension(isRound ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            mYOffsetDate = resources.getDimension(isRound ? R.dimen.digital_y_offset_date_round : R.dimen.digital_y_offset_date);
            mYOffsetDivider = resources.getDimension(isRound ? R.dimen.digital_y_offset_divider_round : R.dimen.digital_y_offset_divider);
            mYOffsetTemp = resources.getDimension(isRound ? R.dimen.digital_y_offset_temp_round : R.dimen.digital_y_offset_temp);

            mLineSize = resources.getDimension(isRound ? R.dimen.digital_line_divider_round : R.dimen.digital_line_divider);

            mTextPaint.setTextSize(textSize);
            mTextPaintDate.setTextSize(textSizeDate);
            mTextPaintMax.setTextSize(textSizeMax);
            mTextPaintMin.setTextSize(textSizeMax);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                String date = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault()).format(new Date()).toUpperCase();
                canvas.drawText(date, bounds.centerX() - (mTextPaintDate.measureText(date))/2, mYOffsetDate, mTextPaintDate);

                if(mWeatherId != WEATHER_NONE) {
                    canvas.drawLine(bounds.centerX() - mLineSize, mYOffsetDivider, bounds.centerX() + mLineSize, mYOffsetDivider, mTextPaintDate);

                    float maxR = bounds.centerX() + (mTextPaintMax.measureText(mTempMAX)) / 2;
                    float maxL = bounds.centerX() - (mTextPaintMax.measureText(mTempMAX)) / 2;

                    canvas.drawText(mTempMAX, maxL, mYOffsetTemp, mTextPaintMax);
                    canvas.drawText(mTempMIN, maxR + mMargin, mYOffsetTemp, mTextPaintMin);

                    float mid = mYOffsetTemp - mTextPaintMax.getTextSize() / 2;

                    Bitmap icon = BitmapFactory.decodeResource(SunshineWatchFace.this.getResources(), Utility.getIconResourceForWeatherCondition(mWeatherId));
                    canvas.drawBitmap(icon, maxL - mMargin - icon.getWidth(), mid - icon.getHeight() / 2, new Paint());
                }
            }

            mTime.setToNow();
            String text = String.format("%d:%02d", mTime.hour, mTime.minute);
            canvas.drawText(text, bounds.centerX() - (mTextPaint.measureText(text))/2, mYOffsetTime, mTextPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private void queryForecast() {
        Intent intent = new Intent(this, QueryForecastService.class);
        startService(intent);
    }
}
