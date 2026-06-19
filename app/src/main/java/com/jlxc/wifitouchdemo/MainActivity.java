package com.jlxc.wifitouchdemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView statusView;
    private TextView urlView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("WiFi Touch Demo");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("把安卓车机变成局域网可控设备：手机浏览器或手机端发送器连接车机 IP 后，可以移动屏幕悬浮光标并点击。\n\n需要权限：悬浮窗 + 无障碍服务。\n\n新增：UDP 坐标接口，端口与 HTTP 相同，格式：SET 123.4 567.8");
        desc.setTextSize(16);
        desc.setTextColor(Color.DKGRAY);
        desc.setPadding(0, dp(12), 0, dp(12));
        root.addView(desc);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setTextColor(Color.BLACK);
        statusView.setPadding(0, dp(6), 0, dp(10));
        root.addView(statusView);

        urlView = new TextView(this);
        urlView.setTextSize(18);
        urlView.setTextColor(Color.rgb(0, 80, 180));
        urlView.setTextIsSelectable(true);
        urlView.setPadding(0, dp(6), 0, dp(18));
        root.addView(urlView);

        root.addView(makeButton("1. 打开悬浮窗权限", v -> openOverlaySettings()));
        root.addView(makeButton("2. 打开无障碍权限", v -> openAccessibilitySettings()));
        root.addView(makeButton("3. 启动 WiFi 触控服务", v -> startTouchService()));
        root.addView(makeButton("4. 停止 WiFi 触控服务", v -> stopTouchService()));
        root.addView(makeButton("刷新状态 / IP", v -> refreshStatus()));

        TextView tips = new TextView(this);
        tips.setText("使用方法：\n" +
                "1）在车机上安装并打开这个 App。\n" +
                "2）打开悬浮窗权限。\n" +
                "3）在无障碍设置里启用“WiFi Touch Demo 输入服务”。\n" +
                "4）启动服务。\n" +
                "5）手机和车机连接同一个 WiFi/热点，在手机浏览器打开上面的地址。\n\n" +
                "手势：\n" +
                "单指滑动：移动光标\n" +
                "单击：点击光标位置\n" +
                "长按：长按光标位置\n" +
                "双指上下滑：滚动\n" +
                "按钮：返回 / 主页 / 最近任务");
        tips.setTextSize(15);
        tips.setTextColor(Color.DKGRAY);
        tips.setPadding(0, dp(16), 0, 0);
        root.addView(tips);

        setContentView(scrollView);
        refreshStatus();
    }

    private Button makeButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshStatus() {
        String ip = NetUtil.getLocalIpAddress(this);
        boolean overlay = Settings.canDrawOverlays(this);
        boolean accessibility = isAccessibilityEnabled(this, TouchAccessibilityService.class.getName());
        boolean running = TouchServerService.isRunning();

        statusView.setText("悬浮窗权限：" + (overlay ? "已开启" : "未开启") + "\n" +
                "无障碍服务：" + (accessibility ? "已开启" : "未开启") + "\n" +
                "触控服务：" + (running ? "运行中" : "未运行") + "\n" +
                "车机 IP：" + ip + "\n" +
                "端口：47220（HTTP/UDP 同端口）");

        if (TextUtils.isEmpty(ip) || "0.0.0.0".equals(ip)) {
            urlView.setText("未获取到局域网 IP，请确认 WiFi/热点已连接");
        } else {
            urlView.setText("手机浏览器打开：\nhttp://" + ip + ":47220");
        }
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "找到并开启：WiFi Touch Demo 输入服务", Toast.LENGTH_LONG).show();
    }

    private void startTouchService() {
        Intent intent = new Intent(this, TouchServerService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "已请求启动服务", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void stopTouchService() {
        stopService(new Intent(this, TouchServerService.class));
        Toast.makeText(this, "已请求停止服务", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private static boolean isAccessibilityEnabled(Context context, String serviceClassName) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        String expected = context.getPackageName() + "/" + serviceClassName;
        String expectedShort = context.getPackageName() + "/." + serviceClassName.substring(serviceClassName.lastIndexOf('.') + 1);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            String item = splitter.next();
            if (item.equalsIgnoreCase(expected) || item.equalsIgnoreCase(expectedShort)) return true;
        }
        return false;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
