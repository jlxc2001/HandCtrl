package com.jlxc.wifitouchdemo;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * UDP high-frequency coordinate receiver.
 *
 * Protocol:
 *   SET 123.4 567.8
 *
 * The effect is equivalent to HTTP:
 *   POST /api/set x=123.4&y=567.8
 *
 * UDP intentionally only handles cursor coordinates. Click/back/home/recents stay on HTTP.
 */
public class TouchUdpServer {
    private static final String TAG = "TouchUdpServer";
    private static final int MAX_PACKET_SIZE = 256;

    private final CursorOverlay cursor;
    private final int port;
    private volatile boolean running;
    private Thread thread;
    private DatagramSocket socket;

    public TouchUdpServer(CursorOverlay cursor, int port) {
        this.cursor = cursor;
        this.port = port;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "WiFiTouchUdpServer");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (thread != null) thread.interrupt(); } catch (Exception ignored) {}
    }

    public boolean isAlive() {
        return running && thread != null && thread.isAlive();
    }

    private void loop() {
        try {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handlePacket(packet);
            }
        } catch (SocketException e) {
            if (running) Log.e(TAG, "udp socket failed", e);
        } catch (Exception e) {
            if (running) Log.e(TAG, "udp loop failed", e);
        } finally {
            running = false;
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handlePacket(DatagramPacket packet) {
        try {
            String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
            if (text.length() == 0) return;

            String[] parts = text.split("\\s+");
            if (parts.length != 3) return;
            if (!"SET".equals(parts[0].toUpperCase(Locale.US))) return;

            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            cursor.setPosition(x, y);
        } catch (Exception ignored) {
            // Ignore malformed high-frequency packets. Do not spam logcat during movement.
        }
    }
}
