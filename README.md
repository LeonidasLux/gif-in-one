> #### English | [简体中文](/assets/zh-rCN/README.md)

# GIF in One

GIF in One is a simple and easy-to-use video & GIF processing tool for Android,
based on the open-source project [CuteGIF](https://github.com/tasy5kg/CuteGIF).

## Features

- Video to GIF, simple and easy-to-use
- Edit a GIF directly: trim, crop, speed, add text, color key, export options & more
- Video editing: crop, trim, speed up, add text & remove background
- Custom GIF resolution, clarity, color quality, frame rate & more
- Preview the effect before exporting
- GIF split and GIF to video supported
- Motion Photo to GIF
- Video or GIF to Xiaomi-compatible Motion Photo
- In-app GitHub Release updates with background APK download and SHA-256 verification

## Build

```bash
./gradlew :app:assembleRelease
```

Release builds require a stable signing key through
`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
and `ANDROID_KEY_PASSWORD`. The same key must be used for every update.
Create and back up the key before enabling releases:

```powershell
keytool -genkeypair -v -keystore gif-in-one-release.jks -alias gif-in-one -keyalg RSA -keysize 4096 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("gif-in-one-release.jks")) |
  Set-Content "gif-in-one-keystore-base64.txt" -NoNewline
```

Store the Base64 text and passwords only in GitHub Actions Secrets. Never commit
the keystore or Base64 file.

## Release workflow

Normal commits and pushes do not create releases. Manually run
`.github/workflows/release-android.yml` from `main`. The workflow:

1. validates Conventional Commits and generates Chinese release notes;
2. sets an increasing `yy.MMdd.HHmm` version and matching `versionCode`;
3. builds signed `arm64-v8a` and `x86_64` release APKs;
4. publishes both APKs, SHA-256 files and `update.json` in a GitHub Release.

Configure these repository secrets before the first run:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The app checks the latest GitHub Release at startup. Downloads run in
WorkManager without system notifications, are verified before installation,
and expose an install button on the home screen when complete. Android still
requires the user to approve the system installer.

The former locally signed APK may use a different key from the CI release key.
When moving between those signatures for the first time, Android requires
backing up app data and uninstalling the old APK before installing the new one.

## License

This project is licensed under the [GPL-3.0 license](/COPYING).
It is a derivative work of the open-source project [CuteGIF](https://github.com/tasy5kg/CuteGIF),
with thanks to the original project and its author.
