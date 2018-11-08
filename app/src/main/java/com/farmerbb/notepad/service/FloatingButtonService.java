package com.farmerbb.notepad.service;

import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;

import com.farmerbb.notepad.R;

public class FloatingButtonService extends Service {
    public static boolean isStarted = false;
    public static String FLOAT_BUTTON_INTENT = "com.farmerbb.notepad.service.floatbuttonintent";
    public static enum GESTURES {
        SWIPE_UP, SWIPE_LEFT, SWIPE_RIGHT, DOUBLE_TAP
    }

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    IBinder mBinder = new LocalBinder();

    private Button button;
    private int btn_x = 264;
    private int btn_y = 1576;

    @Override
    public void onCreate() {
        super.onCreate();
        isStarted = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        //overlay the international button
        layoutParams.width = 120;
        layoutParams.height = 120;
        layoutParams.x = btn_x;
        layoutParams.y = btn_y;
    }

    public class LocalBinder extends Binder {
        public FloatingButtonService getServerInstance() {
            return FloatingButtonService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showFloatingWindow();
        return super.onStartCommand(intent, flags, startId);
    }

    private void showFloatingWindow() {
        if (Settings.canDrawOverlays(this)) {
            button = new Button(getApplicationContext());
            button.setBackgroundResource(R.drawable.round_button);
            windowManager.addView(button, layoutParams);
            button.setOnTouchListener(new FloatingOnTouchListener());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isStarted = false;
        if (button != null) {
            if (button.isShown()) {
                windowManager.removeViewImmediate(button);
            }
        }
    }

    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;
        private int start_x;
        private int start_y;
        private long time;
        boolean firstTouched = false;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    start_x = x;
                    start_y = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;
                    layoutParams.x = layoutParams.x + movedX;
                    layoutParams.y = layoutParams.y + movedY;
                    windowManager.updateViewLayout(view, layoutParams);
                    break;
                case MotionEvent.ACTION_UP:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    Intent intent = new Intent(FloatingButtonService.FLOAT_BUTTON_INTENT);

                    ValueAnimator buttonAnimator = new ValueAnimator();
                    buttonAnimator.setValues(PropertyValuesHolder.ofInt("x", layoutParams.x, btn_x), // set the limits of property "x"
                            PropertyValuesHolder.ofInt("y", layoutParams.y, btn_y));
                    buttonAnimator.addUpdateListener( (ValueAnimator animation) -> {
                        layoutParams.x = (int)animation.getAnimatedValue("x");
                        layoutParams.y = (int)animation.getAnimatedValue("y");
                        windowManager.updateViewLayout(view, layoutParams);
                    });
                    buttonAnimator.setInterpolator(new LinearInterpolator());
                    buttonAnimator.setDuration(90);
                    buttonAnimator.start();

                    boolean motion_processed = false;
                    if (start_x - x > 80 && start_y - y < 100) {
                        //left swipe
                        motion_processed = true;
                        intent.putExtra("gesture", GESTURES.SWIPE_LEFT);
                        Log.e("[Log]", "onTouch: swipe left");
                    } else if (x - start_x > 80 && start_y - y < 100){
                        // right swipt
                        motion_processed = true;
                        intent.putExtra("gesture", GESTURES.SWIPE_RIGHT);
                        Log.e("[Log]", "onTouch: swipe right");
                    } else if (start_y - y > 100) {
                        // swipe up
                        motion_processed = true;
                        intent.putExtra("gesture", GESTURES.SWIPE_UP);
                        Log.e("[Log]", "onTouch: swipe up");
                    }

                    if (motion_processed) {
                        firstTouched = false;
                    } else if (firstTouched){
                        firstTouched = false;
                        if (System.currentTimeMillis() - time <= 300){
                            //double tap to undo
                            intent.putExtra("gesture", GESTURES.DOUBLE_TAP);
                            Log.e("[Log]", "double tapped");
                        }
                    } else {
                        firstTouched = true;
                        time = System.currentTimeMillis();
                    }
                    sendBroadcast(intent);
                    break;
                default:
                    break;
            }
            return false;
        }
    }
}
