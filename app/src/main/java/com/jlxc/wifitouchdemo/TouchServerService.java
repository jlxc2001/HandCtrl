package com.jlxc.wifitouchdemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class TouchServerService extends Service {
    public static final int PORT = 47220;
    private static volatile boolean running = false;

    private CursorOverlay cursorOverlay;
    private TouchHttpServer server;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForegroundCompat();
        cursorOverlay = new CursorOverlay(this);
        cursorOverlay.show();
        server = new TouchHttpServer(this, cursorOverlay, PORT);
        server.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        if (cursorOverlay != null) cursorOverlay.show();
        if (server != null && !server.isAlive()) server.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (server != null) server.stop();
        if (cursorOverlay != null) cursorOverlay.hide();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundCompat() {
        String channelId = "wifi_touch_demo";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "WiFi Touch Demo",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("WiFi Touch Demo 运行中")
                .setContentText("局域网触控服务端口：" + PORT)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1001, notification);
    }
}
