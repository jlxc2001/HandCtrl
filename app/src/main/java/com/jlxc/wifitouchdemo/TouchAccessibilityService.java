package com.jlxc.wifitouchdemo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class TouchAccessibilityService extends AccessibilityService {
    private static final String TAG = "TouchA11y";
    private static volatile TouchAccessibilityService instance;

    public static boolean isReady() {
        return instance != null;
    }

    public static boolean tap(float x, float y) {
        TouchAccessibilityService s = instance;
        if (s == null) return false;
        return s.dispatchTap(x, y, 60);
    }

    public static boolean doubleTap(float x, float y) {
        TouchAccessibilityService s = instance;
        if (s == null) return false;
        boolean first = s.dispatchTap(x, y, 45);
        try { Thread.sleep(80); } catch (InterruptedException ignored) {}
        boolean second = s.dispatchTap(x, y, 45);
        return first && second;
    }

    public static boolean longPress(float x, float y) {
        TouchAccessibilityService s = instance;
        if (s == null) return false;
        return s.dispatchTap(x, y, 650);
    }

    public static boolean swipe(float sx, float sy, float ex, float ey, long durationMs) {
        TouchAccessibilityService s = instance;
        if (s == null) return false;
        return s.dispatchSwipe(sx, sy, ex, ey, Math.max(80, durationMs));
    }

    public static boolean back() {
        TouchAccessibilityService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean home() {
        TouchAccessibilityService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean recents() {
        TouchAccessibilityService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op. This service only injects gestures for the local WiFi touch demo.
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    private boolean dispatchTap(float x, float y, long durationMs) {
        if (Build.VERSION.SDK_INT < 24) return false;
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, Math.max(40, durationMs));
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Log.e(TAG, "dispatchTap failed", e);
            return false;
        }
    }

    private boolean dispatchSwipe(float sx, float sy, float ex, float ey, long durationMs) {
        if (Build.VERSION.SDK_INT < 24) return false;
        try {
            Path path = new Path();
            path.moveTo(sx, sy);
            path.lineTo(ex, ey);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, durationMs);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Log.e(TAG, "dispatchSwipe failed", e);
            return false;
        }
    }
}
