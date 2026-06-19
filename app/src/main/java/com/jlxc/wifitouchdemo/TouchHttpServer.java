package com.jlxc.wifitouchdemo;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TouchHttpServer {
    private static final String TAG = "TouchHttpServer";
    private final Context context;
    private final CursorOverlay cursor;
    private final int port;
    private volatile boolean running;
    private Thread thread;
    private ServerSocket serverSocket;

    public TouchHttpServer(Context context, CursorOverlay cursor, int port) {
        this.context = context.getApplicationContext();
        this.cursor = cursor;
        this.port = port;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "WiFiTouchHttpServer");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (thread != null) thread.interrupt(); } catch (Exception ignored) {}
    }

    public boolean isAlive() {
        return running && thread != null && thread.isAlive();
    }

    private void loop() {
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handle(socket), "WiFiTouchClient").start();
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "server loop failed", e);
        } finally {
            running = false;
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(2500);
            InputStream input = s.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.length() == 0) return;
            String[] first = requestLine.split(" ");
            String method = first.length > 0 ? first[0] : "GET";
            String fullPath = first.length > 1 ? first[1] : "/";
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()); } catch (Exception ignored) {}
                }
            }
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = reader.read(buf, read, contentLength - read);
                    if (n <= 0) break;
                    read += n;
                }
                body = new String(buf, 0, read);
            }

            String path = fullPath;
            String query = "";
            int q = fullPath.indexOf('?');
            if (q >= 0) {
                path = fullPath.substring(0, q);
                query = fullPath.substring(q + 1);
            }

            if ("GET".equalsIgnoreCase(method) && ("/".equals(path) || "/index.html".equals(path))) {
                respond(s, 200, "text/html; charset=utf-8", html());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/status".equals(path)) {
                respondJson(s, statusJson());
                return;
            }
            if (path.startsWith("/api/")) {
                Map<String, String> params = parseParams(query + (body.length() > 0 ? "&" + body : ""));
                String result = handleApi(path, params);
                respondJson(s, result);
                return;
            }
            respond(s, 404, "text/plain; charset=utf-8", "Not found");
        } catch (Exception e) {
            Log.e(TAG, "handle client failed", e);
        }
    }

    private String handleApi(String path, Map<String, String> p) {
        boolean ok = true;
        String msg = "ok";
        try {
            switch (path) {
                case "/api/move": {
                    float dx = f(p, "dx", 0);
                    float dy = f(p, "dy", 0);
                    float speed = f(p, "speed", 1.4f);
                    cursor.moveBy(dx * speed, dy * speed);
                    break;
                }
                case "/api/set": {
                    float x = f(p, "x", cursor.getX());
                    float y = f(p, "y", cursor.getY());
                    cursor.setPosition(x, y);
                    break;
                }
                case "/api/tap": {
                    cursor.pulse();
                    ok = TouchAccessibilityService.tap(cursor.getX(), cursor.getY());
                    if (!ok) msg = "accessibility service not ready";
                    break;
                }
                case "/api/doubletap": {
                    cursor.pulse();
                    ok = TouchAccessibilityService.doubleTap(cursor.getX(), cursor.getY());
                    if (!ok) msg = "accessibility service not ready";
                    break;
                }
                case "/api/longpress": {
                    cursor.pulse();
                    ok = TouchAccessibilityService.longPress(cursor.getX(), cursor.getY());
                    if (!ok) msg = "accessibility service not ready";
                    break;
                }
                case "/api/swipe": {
                    float sx = f(p, "sx", cursor.getX());
                    float sy = f(p, "sy", cursor.getY());
                    float ex = f(p, "ex", cursor.getX());
                    float ey = f(p, "ey", cursor.getY());
                    long dur = (long) f(p, "duration", 220);
                    ok = TouchAccessibilityService.swipe(sx, sy, ex, ey, dur);
                    if (!ok) msg = "accessibility service not ready";
                    break;
                }
                case "/api/scroll": {
                    float dy = f(p, "dy", 0);
                    float x = cursor.getX();
                    float y = cursor.getY();
                    float distance = Math.max(120, Math.min(520, Math.abs(dy) * 2.2f));
                    // Android swipe direction: finger moves up to scroll down, moves down to scroll up.
                    float sy = y;
                    float ey = y - Math.signum(dy) * distance;
                    ok = TouchAccessibilityService.swipe(x, sy, x, ey, 260);
                    if (!ok) msg = "accessibility service not ready";
                    break;
                }
                case "/api/back": ok = TouchAccessibilityService.back(); break;
                case "/api/home": ok = TouchAccessibilityService.home(); break;
                case "/api/recents": ok = TouchAccessibilityService.recents(); break;
                default:
                    ok = false;
                    msg = "unknown api";
            }
        } catch (Exception e) {
            ok = false;
            msg = e.toString();
        }
        return "{\"ok\":" + ok + ",\"msg\":" + json(msg) + ",\"x\":" + Math.round(cursor.getX()) + ",\"y\":" + Math.round(cursor.getY()) + ",\"a11y\":" + TouchAccessibilityService.isReady() + "}";
    }

    private String statusJson() {
        return "{\"ok\":true," +
                "\"ip\":" + json(NetUtil.getLocalIpAddress(context)) + "," +
                "\"port\":" + port + "," +
                "\"x\":" + Math.round(cursor.getX()) + "," +
                "\"y\":" + Math.round(cursor.getY()) + "," +
                "\"screenW\":" + cursor.getScreenW() + "," +
                "\"screenH\":" + cursor.getScreenH() + "," +
                "\"cursorShown\":" + cursor.isShown() + "," +
                "\"a11y\":" + TouchAccessibilityService.isReady() + "}";
    }

    private static Map<String, String> parseParams(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        String[] parts = raw.split("&");
        for (String part : parts) {
            if (part.length() == 0) continue;
            int eq = part.indexOf('=');
            try {
                String k = eq >= 0 ? part.substring(0, eq) : part;
                String v = eq >= 0 ? part.substring(eq + 1) : "";
                map.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
            } catch (Exception ignored) {}
        }
        return map;
    }

    private static float f(Map<String, String> map, String key, float def) {
        try {
            String v = map.get(key);
            return v == null ? def : Float.parseFloat(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static String json(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private void respondJson(Socket s, String body) throws Exception {
        respond(s, 200, "application/json; charset=utf-8", body);
    }

    private void respond(Socket s, int code, String type, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        OutputStream out = s.getOutputStream();
        String status = code == 200 ? "OK" : "ERR";
        String header = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + type + "\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private String html() {
        return "<!doctype html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<title>WiFi Touch Demo</title>" +
                "<style>html,body{margin:0;height:100%;background:#111;color:#eee;font-family:system-ui,-apple-system,Segoe UI,Arial,sans-serif;overscroll-behavior:none;touch-action:none}#wrap{height:100%;display:flex;flex-direction:column}.top{padding:10px 12px;font-size:14px;background:#1b1b1b}.pad{flex:1;margin:10px;border:1px solid #444;border-radius:18px;background:linear-gradient(135deg,#222,#151515);display:flex;align-items:center;justify-content:center;color:#777;font-size:24px;user-select:none}.btns{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;padding:0 10px 10px}button{height:48px;border-radius:12px;border:0;background:#333;color:#fff;font-size:16px}.primary{background:#1976d2}.small{font-size:12px;color:#aaa;margin-top:4px}</style>" +
                "</head><body><div id='wrap'><div class='top'><b>WiFi Touch Demo</b><div id='st' class='small'>connecting...</div></div><div id='pad' class='pad'>触控板</div><div class='btns'><button onclick='api(\"/api/back\")'>返回</button><button onclick='api(\"/api/home\")'>主页</button><button onclick='api(\"/api/recents\")'>最近</button><button onclick='api(\"/api/tap\")' class='primary'>点击</button><button onclick='api(\"/api/doubletap\")'>双击</button><button onclick='api(\"/api/longpress\")'>长按</button></div></div>" +
                "<script>const pad=document.getElementById('pad'),st=document.getElementById('st');let last=null,lastTap=0,twoStart=null,speed=1.4;function qs(o){return Object.keys(o).map(k=>encodeURIComponent(k)+'='+encodeURIComponent(o[k])).join('&')}function api(p,o={}){fetch(p,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:qs(o)}).then(r=>r.json()).then(j=>{st.textContent='x='+j.x+' y='+j.y+' a11y='+j.a11y+' '+(j.ok?'':'ERR '+j.msg)}).catch(e=>st.textContent='网络错误 '+e)}function dist(a,b){let dx=a.clientX-b.clientX,dy=a.clientY-b.clientY;return Math.sqrt(dx*dx+dy*dy)}pad.addEventListener('touchstart',e=>{e.preventDefault();if(e.touches.length===1){last={x:e.touches[0].clientX,y:e.touches[0].clientY,t:Date.now()}}else if(e.touches.length===2){twoStart={y:(e.touches[0].clientY+e.touches[1].clientY)/2,d:dist(e.touches[0],e.touches[1])}} ,{passive:false});pad.addEventListener('touchmove',e=>{e.preventDefault();if(e.touches.length===1&&last){let t=e.touches[0],dx=t.clientX-last.x,dy=t.clientY-last.y;last={x:t.clientX,y:t.clientY,t:Date.now()};api('/api/move',{dx,dy,speed})}else if(e.touches.length===2&&twoStart){let cy=(e.touches[0].clientY+e.touches[1].clientY)/2;let dy=cy-twoStart.y;if(Math.abs(dy)>16){api('/api/scroll',{dy});twoStart.y=cy}}},{passive:false});pad.addEventListener('touchend',e=>{e.preventDefault();if(e.touches.length===0&&last){let now=Date.now();if(now-last.t<220){api(now-lastTap<320?'/api/doubletap':'/api/tap');lastTap=now}last=null;twoStart=null}},{passive:false});pad.addEventListener('mousedown',e=>{last={x:e.clientX,y:e.clientY,t:Date.now()}});window.addEventListener('mousemove',e=>{if(!last)return;let dx=e.clientX-last.x,dy=e.clientY-last.y;last={x:e.clientX,y:e.clientY,t:Date.now()};api('/api/move',{dx,dy,speed})});window.addEventListener('mouseup',e=>{if(last&&Date.now()-last.t<220)api('/api/tap');last=null});setInterval(()=>fetch('/status').then(r=>r.json()).then(j=>{st.textContent='屏幕 '+j.screenW+'x'+j.screenH+' | 光标 '+j.x+','+j.y+' | 无障碍 '+j.a11y+' | 悬浮窗 '+j.cursorShown}).catch(e=>st.textContent='连接失败') ,1500);</script></body></html>";
    }
}
