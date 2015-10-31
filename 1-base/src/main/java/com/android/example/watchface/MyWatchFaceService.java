/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        //Watch Battery Percent
        private boolean mRegisteredBatteryPercentageReceiver = false;

        private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context arg0, Intent intent)
            {
                batteryPercentage = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) + "%");
            }
        };


        private boolean mRegisteredTimeZoneReceiver = false;

        private static final float HAND_END_CAP_RADIUS=4.f;
        private static final float STROKE_WIDTH = 4.f;
        private static final float SHADOW_RADIUS=6.f;

        private Time mTime;

        private Paint mBackgroundPaint;
        private Paint mHandPaint;
        //Define the ticks
        private Paint mTickPaint;

        private Paint mClockPaint;
        private ClockNumbers mClockNumbers;

        private boolean mAmbient;

        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;

        private float mHourHandLength;
        private float mMinuteHandLength;
        private float mSecondHandLength;
        //Watch Battery
        String batteryPercentage = "";
        Paint BatteryPercentagePaint;
        //Watch Battery icon

        private Paint mStepPaint;



        /**
         * Whether the display supports fewer bits for each color in ambient mode.
         * When true, we disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        /**
         * Whether the display supports burn in protection in ambient mode.
         * When true, remove the background in ambient mode.
         */
        private boolean mBurnInProtection;

        private int mWidth;
        private int mHeight;
        private int mWatchHandColor;
        private int mWatchHandShadowColor;
        private float mCenterX;
        private float mCenterY;
        private float mScale=1;
        private Rect mCardBounds = new Rect();




        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);


            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            final int backgroundResId = R.drawable.custom_background;

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), backgroundResId);
            // Add the Ticks

            //Paint the hands
            mHandPaint = new Paint();
            mHandPaint.setColor(Color.WHITE);
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.BLACK);
            mHandPaint.setStyle(Paint.Style.STROKE);

            mTickPaint = new Paint();
            mTickPaint.setColor(Color.WHITE);
            mTickPaint.setAntiAlias(true);
            mHandPaint.setColor(Color.WHITE);
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mClockPaint = new Paint();
            mClockPaint.setColor(Color.WHITE);
            mClockPaint.setAntiAlias(true);
            mClockPaint.setTextSize(12);
            mClockPaint.setTextAlign(Paint.Align.CENTER);
            mClockPaint.setTypeface(Typeface.SANS_SERIF);
            mClockNumbers = new ClockNumbers(mClockPaint, new Point(160, 160), 153);
            //Watch Battery
            BatteryPercentagePaint = new Paint();
            BatteryPercentagePaint.setColor(Color.WHITE);
            BatteryPercentagePaint.setStrokeWidth(6.f);
            BatteryPercentagePaint.setAntiAlias(true);
            BatteryPercentagePaint.setTextSize(15);

           // mStepPaint = createTextPaint(INTERACTIVE_DIGITS_COLOR);


            /*
            The bellow code allows for changing of hand color based on background color...*/
            Palette.generateAsync(mBackgroundBitmap, new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    /*
                     * Sometimes, palette is unable to generate a color palette
                     * so we need to check that we have one.
                     */
                    /**if (palette != null) {
                        mWatchHandColor = palette.getVibrantColor(Color.WHITE);
                        mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                        setWatchHandColor();
                            }*/
                        }
                    });

            mTime = new Time();
        }
     // implement custom color based on watchhand color
        private void setWatchHandColor(){
            if (mAmbient){
                mHandPaint.setColor(Color.WHITE);
                mHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.BLACK);
            } else {
                mHandPaint.setColor(mWatchHandColor);
                mHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
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
                if (mLowBitAmbient || mBurnInProtection) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                    BatteryPercentagePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2.f;
            mCenterY = mHeight / 2.f;
            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */
            mHourHandLength = 0.5f * width / 2.f;
            mMinuteHandLength = 0.7f * width / 2.f;
            mSecondHandLength =0.9f * width / 2.f;

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);
            if (!mBurnInProtection || !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

           // int width = bounds.width();
           // int height = bounds.height();

            // Draw the background.
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }

            /*
             * These calculations reflect the rotation in degrees per unit of
             * time, e.g. 360 / 60 = 6 and 360 / 12 = 30
             */
            final float secondsRotation = mTime.second * 6f;
            final float minutesRotation = mTime.minute * 6f;
            // account for the offset of the hour hand due to minutes of the hour.
            final float hourHandOffset = mTime.minute / 2f;
            final float hoursRotation = (mTime.hour * 30) + hourHandOffset;

            // Draw Numbers
            mClockNumbers.draw(canvas);

            //Draw Ticks


            //Tick Draw from codingfury
           /** float innerTickRadius = mCenterX;
             for (int tickIndex = 0; tickIndex < 12; tickIndex++)
             {
             float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
             float innerX = (float) Math.sin(tickRot) * innerTickRadius;
             float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
             float outerX = (float) Math.sin(tickRot) * mCenterX;
             float outerY = (float) -Math.cos(tickRot) * mCenterX;
             canvas.drawLine(mCenterX + innerX, mCenterY + innerY, mCenterX + outerX, mCenterY + outerY, mTickPaint);
             }*/

            // Draw the tick marks complicated like round
             int w = bounds.width(), h = bounds.height();
             float cx = w / 2.0f, cy = h / 2.0f;

             double sinVal = 0, cosVal = 0, angle = 0;
             float length1 = 0, length2 = 0;
             float x1 = 0, y1 = 0, x2 = 0, y2 = 0;
             length1 = cx - 40;
             length2 = cx - 15;
             for (int i = 0; i < 60; i++) {
             angle = (i * Math.PI * 2 / 60);
             sinVal = Math.sin(angle);
             cosVal = Math.cos(angle);
             float len = (i % 5 == 0) ? length1 :
             (length1 + 15);
             x1 = (float) (sinVal * len);
             y1 = (float) (-cosVal * len);
             x2 = (float) (sinVal * length2);
             y2 = (float) (-cosVal * length2);
             canvas.drawLine(cx + x1, cy + y1, cx + x2,
             cy + y2, mTickPaint);
             }

            //Square Ticks?
            /**for (int i = 0; i < 24; ++i) {
                if (i % 3 != 0) {
                    canvas.drawLine(-40, mCenterY, width = 100, mCenterY, mTickPaint);
                }

                //canvas.rotate(30f, mCenterX, mCenterY);
            }*/
            //canvas.restore();
            /**if (mRound) {
                canvas.drawCircle(mCenterX, mCenterY, mCenterX-32, mBlackFIllPaint)
            }else
            {
                canvas.drawRect();
            }*/


            //WatchBattery
            canvas.drawText(batteryPercentage,mCenterX-100f, mCenterY+5f, BatteryPercentagePaint);

            // save the canvas state before we begin to rotate it
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            drawHand(canvas, mHourHandLength);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            drawHand(canvas, mMinuteHandLength);

            /*
             * Make sure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise we only update the watch face once a minute.
             */


            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX, mCenterY - HAND_END_CAP_RADIUS, mCenterX,
                        mCenterY - mSecondHandLength, mHandPaint);
            }
            canvas.drawCircle(mCenterX, mCenterY, HAND_END_CAP_RADIUS, mHandPaint);
            // restore the canvas' original orientation.
            canvas.restore();

            if (mAmbient) {
                canvas.drawRect(mCardBounds, mBackgroundPaint);
            }

        }

        private void drawHand(Canvas canvas, float handLength) {
            canvas.drawRoundRect(mCenterX - HAND_END_CAP_RADIUS, mCenterY - handLength,
                    mCenterX + HAND_END_CAP_RADIUS, mCenterY + HAND_END_CAP_RADIUS,
                    HAND_END_CAP_RADIUS, HAND_END_CAP_RADIUS, mHandPaint);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }
        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mCardBounds.set(rect);
        }

        private void registerReceiver() {
            if (!mRegisteredTimeZoneReceiver)
            {
                mRegisteredTimeZoneReceiver = true;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
            }
            if (!mRegisteredBatteryPercentageReceiver)
            {
                mRegisteredBatteryPercentageReceiver = true;
                IntentFilter filterBattery = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                MyWatchFaceService.this.registerReceiver(mBatInfoReceiver, filterBattery);
            }
        }

        private void unregisterReceiver() {
            if (mRegisteredTimeZoneReceiver)
            {
                mRegisteredTimeZoneReceiver = false;
                MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
            }
            if (mRegisteredBatteryPercentageReceiver)
            {
                mRegisteredBatteryPercentageReceiver = false;
                MyWatchFaceService.this.unregisterReceiver(mBatInfoReceiver);
            }
        }


        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
