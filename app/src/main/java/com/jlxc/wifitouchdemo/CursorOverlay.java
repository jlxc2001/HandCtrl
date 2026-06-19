package com.jlxc.wifitouchdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class CursorOverlay {
    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private CursorView cursorView;
    private WindowManager.LayoutParams params;
    private int screenW;
    private int screenH;
    private int size;
    private volatile float cursorX;
    private volatile float cursorY;
    private volatile boolean shown;

    public CursorOverlay(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        updateMetrics();
        cursorX = screenW / 2f;
        cursorY = screenH / 2f;
    }

    public void show() {
        mainHandler.post(() -> {
            if (shown) return;
            if (!Settings.canDrawOverlays(context)) return;
            updateMetrics();
            size = dp(48);
            cursorView = new CursorView(context);
            params = new WindowManager.LayoutParams(
                    size,
                    size,
                    android.os.Build.VERSION.SDK_INT >= 26
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.START | Gravity.TOP;
            params.x = clamp(Math.round(cursorX), 0, Math.max(0, screenW - 1));
            params.y = clamp(Math.round(cursorY), 0, Math.max(0, screenH - 1));
            try {
                windowManager.addView(cursorView, params);
                shown = true;
            } catch (Exception ignored) {
                shown = false;
            }
        });
    }

    public void hide() {
        mainHandler.post(() -> {
            if (!shown || cursorView == null) return;
            try { windowManager.removeView(cursorView); } catch (Exception ignored) {}
            shown = false;
            cursorView = null;
        });
    }

    public boolean isShown() {
        return shown;
    }

    public void moveBy(float dx, float dy) {
        updateMetrics();
        cursorX = clampFloat(cursorX + dx, 0, Math.max(1, screenW - 1));
        cursorY = clampFloat(cursorY + dy, 0, Math.max(1, screenH - 1));
        applyPosition(false);
    }

    public void setPosition(float x, float y) {
        updateMetrics();
        cursorX = clampFloat(x, 0, Math.max(1, screenW - 1));
        cursorY = clampFloat(y, 0, Math.max(1, screenH - 1));
        applyPosition(false);
    }

    public void pulse() {
        mainHandler.post(() -> {
            if (cursorView != null) {
                cursorView.pulse();
            }
        });
    }

    public float getX() { return cursorX; }
    public float getY() { return cursorY; }
    public int getScreenW() { updateMetrics(); return screenW; }
    public int getScreenH() { updateMetrics(); return screenH; }

    private void applyPosition(boolean forceShow) {
        mainHandler.post(() -> {
            if (forceShow && !shown) show();
            if (!shown || params == null || cursorView == null) return;
            params.x = clamp(Math.round(cursorX), 0, Math.max(0, screenW - 1));
            params.y = clamp(Math.round(cursorY), 0, Math.max(0, screenH - 1));
            try { windowManager.updateViewLayout(cursorView, params); } catch (Exception ignored) {}
        });
    }

    private void updateMetrics() {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(dm);
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
        } catch (Exception e) {
            screenW = context.getResources().getDisplayMetrics().widthPixels;
            screenH = context.getResources().getDisplayMetrics().heightPixels;
        }
    }

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long pulseUntil = 0;

        CursorView(Context context) {
            super(context);
            fill.setColor(Color.WHITE);
            fill.setStyle(Paint.Style.FILL);
            stroke.setColor(Color.BLACK);
            stroke.setStrokeWidth(dp(2));
            stroke.setStyle(Paint.Style.STROKE);
            circle.setColor(Color.argb(120, 33, 150, 243));
            circle.setStyle(Paint.Style.STROKE);
            circle.setStrokeWidth(dp(3));
        }

        void pulse() {
            pulseUntil = System.currentTimeMillis() + 180;
            invalidate();
            postDelayed(this::invalidate, 200);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float s = Math.min(getWidth(), getHeight());
            Path p = new Path();
            p.moveTo(0, 0);
            p.lineTo(s * 0.72f, s * 0.42f);
            p.lineTo(s * 0.43f, s * 0.50f);
            p.lineTo(s * 0.58f, s * 0.88f);
            p.lineTo(s * 0.42f, s * 0.95f);
            p.lineTo(s * 0.27f, s * 0.58f);
            p.lineTo(s * 0.02f, s * 0.78f);
            p.close();
            canvas.drawPath(p, fill);
            canvas.drawPath(p, stroke);
            if (System.currentTimeMillis() < pulseUntil) {
                canvas.drawCircle(s * 0.08f, s * 0.08f, s * 0.32f, circle);
            }
        }
    }
}
