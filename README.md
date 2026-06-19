# WiFi Touch Demo

这是一个用于验证“安卓车机通过 WiFi 局域网接收手机触控”的最小 Demo。

## 功能

- 车机端显示一个悬浮虚拟光标。
- 手机浏览器打开车机 IP 后，手机屏幕可以作为触控板。
- 单指滑动：移动光标。
- 单击：点击光标所在位置。
- 双击：双击。
- 长按：长按。
- 双指上下滑：滚动。
- 网页按钮：返回、主页、最近任务。

## 权限

需要在车机上手动开启：

1. 悬浮窗权限
2. 无障碍服务：`WiFi Touch Demo 输入服务`

## 使用方法

1. 安装 APK 到车机。
2. 打开 App。
3. 点击“打开悬浮窗权限”，允许显示在其他应用上层。
4. 点击“打开无障碍权限”，启用 `WiFi Touch Demo 输入服务`。
5. 回到 App，点击“启动 WiFi 触控服务”。
6. 手机和车机连接同一个 WiFi/热点。
7. 在手机浏览器打开 App 显示的地址，例如：

```text
http://192.168.100.124:47220
```

## GitHub Actions 打包

把整个项目上传到 GitHub 后，进入 Actions，运行 `Build Debug APK`。
打包完成后在 Artifacts 里下载 `WiFiTouchDemo-debug-apk`。

## ADB 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 调试接口

状态：

```text
http://车机IP:47220/status
```

移动：

```text
POST http://车机IP:47220/api/move
body: dx=10&dy=5&speed=1.4
```

点击：

```text
POST http://车机IP:47220/api/tap
```
