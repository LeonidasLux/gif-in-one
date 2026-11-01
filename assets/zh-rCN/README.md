> #### [English](/README.md) | 简体中文

# GIF in One

GIF in One 是一款简洁好用的 Android 端视频与 GIF 处理工具，基于开源项目
[CuteGIF](https://github.com/tasy5kg/CuteGIF) 二次开发。

## 功能

- 视频转 GIF，简单易用
- 直接编辑 GIF：截取、裁剪、调速、添加文字、抠图、导出选项等
- 视频编辑：裁剪画幅、截取时长、调速、添加文字、去背景
- 自定义 GIF 分辨率、清晰度、色彩品质、帧率等
- 导出前预览效果
- 支持 GIF 拆分与 GIF 转视频
- 动态照片转 GIF
- 视频或 GIF 转小米兼容的动态照片
- 从 GitHub Release 检查更新，支持后台下载、SHA-256 校验与系统安装

## 构建

```bash
./gradlew :app:assembleRelease
```

release 构建需要固定签名，必须设置 `ANDROID_KEYSTORE_PATH`、
`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS` 和
`ANDROID_KEY_PASSWORD`。后续所有更新必须使用同一签名。
首次启用发布前应生成并离线备份 keystore：

```powershell
keytool -genkeypair -v -keystore gif-in-one-release.jks -alias gif-in-one -keyalg RSA -keysize 4096 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("gif-in-one-release.jks")) |
  Set-Content "gif-in-one-keystore-base64.txt" -NoNewline
```

Base64 内容和密码只应保存到 GitHub Actions Secrets，禁止提交 keystore
及其 Base64 文件。

## 自动发布

普通提交和推送不会触发版本发布。需要发布时，在 `main` 分支手动运行
`.github/workflows/release-android.yml`。工作流会：

1. 校验 Conventional Commits 并生成中文更新日志；
2. 更新 `yy.MMdd.HHmm` 格式的 `versionName` 和递增的 `versionCode`；
3. 构建并签名 `arm64-v8a` 与 `x86_64` 两个 release APK；
4. 创建 GitHub Release，上传两个 APK、SHA-256 文件和 `update.json`。

首次运行前需要在仓库 Actions Secrets 中配置：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

应用启动时会检查最新 GitHub Release。更新包由 WorkManager 后台下载，
完成后应用首页会显示“立即更新”入口；下载过程不发送系统通知，最终安装仍需
用户在 Android 系统安装器中确认。

此前本地打包的 APK 可能使用了不同签名。首次切换到 CI release 签名时，Android
会拒绝覆盖安装，需要先备份应用数据并卸载旧版本。

## 开源协议

本项目采用 [GPL-3.0 协议](/COPYING)，基于开源项目
[CuteGIF](https://github.com/tasy5kg/CuteGIF) 二次开发，感谢原项目及其作者。
