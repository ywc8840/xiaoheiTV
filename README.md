# 小黑TV (xiaoheiTV)

Android TV 上的 IPTV 直播播放器。

## 功能
- 解析 M3U 频道列表，按分组展示频道
- 内置 EPG（节目单）数据拉取与展示
- 设置页：切换频道源、调整播放器偏好
- 适配遥控器的 TV 布局与焦点导航

## 目录结构
- `app/src/main/java/com/xiaohei/tv/` — 应用源码（Kotlin）
- `app/src/main/assets/` — 内置频道源（`channels_main.m3u` / `channels_backup.m3u`）与台标
- `sources/` — 额外的频道源列表（guovin / iptvorg 等）

## 构建
- 使用 Android Studio / Gradle
- 最低 SDK 见 `app/build.gradle.kts`
- 构建命令：`./gradlew assembleRelease`

## 说明
- 内置频道源来自第三方公开列表，仅供学习测试。
- 许可证：未指定。
- 台标资源来自各电视台，版权归原所有者所有。
