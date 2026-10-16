# AGENTS.md

本文件用于指导 AI 代理（及开发者）在本仓库中工作。

## 项目概况

- **CuteGIF**：Android 视频转 GIF 工具，Kotlin + ViewBinding（无 Compose）。
- 应用 ID `me.tasy5kg.cutegif`，入口 `MainActivity`。
- Gradle Wrapper 8.4 + AGP 8.3.1，minSdk 29 / targetSdk 34 / compileSdk 34。
- `com.arthenica:ffmpeg-kit-full-gpl` 已从 Maven Central 下架，`settings.gradle` 中的阿里云镜像
  （`https://maven.aliyun.com/repository/public`）用于解析该依赖，不要移除。

## 本机环境

- Android SDK：`%LOCALAPPDATA%\Android\Sdk`（即 `C:\Users\Administrator\AppData\Local\Android\Sdk`）。
  构建前设置 `$env:ANDROID_HOME` 指向该目录。
- JDK 17：已通过 `JAVA_HOME` 配置，无需手动设置。
- 模拟器 AVD：`Medium_Phone`（x86_64），运行时 adb 序列号为 `emulator-5554`。

## 规则

### 1. 修改代码后必须重新构建、重装并重启应用

改完 `app/src` 下的任何代码或资源后，不要只交付改动，必须在模拟器上按以下顺序验证：

1. **确保模拟器已运行**（若 `adb devices` 中没有 `emulator-5554` 则启动，并等待开机完成）：

   ```powershell
   Start-Process "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd", "Medium_Phone", "-no-boot-anim"
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" wait-for-device
   ```

2. **重新构建 debug APK**：

   ```powershell
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   .\gradlew.bat :app:assembleDebug --console=plain
   ```

3. **重装到模拟器**（`-r` 保留应用数据）：

   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   $apk = (Get-ChildItem app\build\outputs\apk\debug\*x86_64-debug.apk | Select-Object -First 1).FullName
   & $adb -s emulator-5554 install -r $apk
   ```

4. **重新启动应用**：

   ```powershell
   & $adb -s emulator-5554 shell am start -n me.tasy5kg.cutegif/.MainActivity
   ```

补充说明：

- 模拟器使用 x86_64 包；真机（arm64）则安装 `*arm64-v8a-debug.apk`。
- APK 文件名包含版本号，会随版本变化，始终按通配方式取文件。
- 除非确有必要，不要 `adb uninstall`，避免清除应用数据。
- 首次构建需要联网下载依赖；Gradle 发行版若从官方源超时，可改用腾讯镜像
  （`https://mirrors.cloud.tencent.com/gradle/`）放入 wrapper 缓存。

### 2. 修改代码后运行测试

- 单元测试（JVM，覆盖导出命令构造、裁剪参数等纯逻辑）：

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  ```

- 端到端导出管线测试（需模拟器运行；会安装测试包并在结束后卸载应用）：

  ```powershell
  .\gradlew.bat :app:connectedDebugAndroidTest
  ```