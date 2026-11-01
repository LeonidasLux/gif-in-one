# AGENTS.md

本文件用于指导 AI 代理（及开发者）在本仓库中工作。

## 项目概况

- **GIF in One**：Android 视频/GIF 处理工具（基于开源项目 CuteGIF 二次开发，GPL-3.0），Kotlin + ViewBinding（无 Compose）。
- 应用 ID `com.leonidaslux.gifinone`，入口 `MainActivity`。
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
   & $adb -s emulator-5554 shell am start -n com.leonidaslux.gifinone/.MainActivity
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

### 3. 提交、推送与版本发布

- 用户说“提交”“提交推送”等提交意图时，默认直接提交并推送到当前主分支。
- 当前项目默认主分支为 `main`；除非用户明确指定其他分支或 remote，否则直接推送
  `origin/main`，无需再次确认。
- 普通提交和推送不得触发 Android 版本发布工作流。
- 只有用户明确说“提交并升级版本”或“打包 APK”时，才在提交推送后触发
  `.github/workflows/release-android.yml`；未指定版本号时由工作流使用当前上海时间生成
  下一个 `yy.MMdd.HHmm` 版本（在现有时间版本体系下等价于默认 `patch` 升级）。
- 推送失败时先安全整合远端更新后重试，禁止使用强推覆盖远端历史。
- 新提交必须使用 Conventional Commits：`类型(范围): 说明`，例如
  `feat(update): 增加应用内后台更新`。
- 允许的类型为 `feat`、`fix`、`perf`、`refactor`、`style`、`docs`、`test`、`build`、
  `ci`、`chore`、`revert`；破坏性变更在类型后加 `!`，并在正文写明
  `BREAKING CHANGE:`。
- 提交标题应准确概括用户可见变化，说明使用简体中文，不超过 72 个字符；不要使用
  “更新代码”等无法形成发布日志的描述。
- 仓库启用 `.githooks/commit-msg` 校验。首次检出后可执行：

  ```powershell
  git config core.hooksPath .githooks
  ```

- GitHub Release 必须包含 `gif-in-one-arm64-v8a.apk`、`gif-in-one-x86_64.apk`、
  对应 SHA-256 文件和 `update.json`，应用更新功能依赖这些固定资产名。
- Android 应用更新下载不得发送系统通知；下载完成后必须在应用首页显示明确提示，
  并提供可直接触发系统安装器的“立即更新”按钮。

### 4. “提交并升级版本” / “打包 APK”流程

用户明确要求发布版本时，按以下顺序执行，不再询问是否创建分支或 PR：

1. 确认当前在 `main`，完成代码审查、单元测试和必要的设备测试。
2. 提交所有未提交改动并推送：

   ```powershell
   git push -u origin HEAD:main
   ```

3. 手动触发 GitHub Release 工作流；未指定版本号时留空，由工作流使用当前时间：

   ```powershell
   gh workflow run release-android.yml --ref main
   ```

   若用户给出 `yy.MMdd.HHmm` 版本号：

   ```powershell
   gh workflow run release-android.yml --ref main -f version_name=26.1101.1834
   ```

4. 等待工作流完成并检查结果：

   ```powershell
   gh run list --workflow release-android.yml --limit 1
   gh run watch
   ```

5. 工作流负责更新 [app/build.gradle](app/build.gradle) 中的 `versionName` 和
   `versionCode`、生成中文发布说明、构建两个 ABI 的签名 APK、提交版本变更、创建
   `v版本号` 标签并发布 GitHub Release。不要同时在本地重复修改同一版本号。

本地 release 构建必须设置 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、
`ANDROID_KEY_ALIAS` 和 `ANDROID_KEY_PASSWORD`。GitHub Actions 使用以下仓库 Secrets：
`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、
`ANDROID_KEY_PASSWORD`。
