<div align="center">
  <img src="docs/icon.png" width="88" height="88" alt="Offset Brightness icon" />
  <h1>Offset Brightness</h1>
  <p>A custom auto-brightness controller for Android.</p>
</div>

## What it does

A transparent, tunable alternative to Android's built-in auto-brightness. It reads the ambient light sensor in real time, maps lux to screen brightness on a smooth logarithmic curve, and lets you layer a manual **−100% to +100% offset** on top — so the screen can stay consistently brighter or dimmer than the automatic baseline. Changes ease in frame-by-frame (no flicker), and an optional foreground service keeps it adjusting in the background. Includes live lux/nits readouts and two themes (Slate and Midnight). Everything runs on-device — no accounts, no network.

## Build & run

Requires Android Studio, a device on **Android 8.0 (API 26)+** with an ambient light sensor.

1. Open in Android Studio and let Gradle sync.
2. Generate the git-ignored debug keystore the debug build expects:
   ```bash
   keytool -genkeypair -v -keystore debug.keystore \
     -storepass android -keypass android -alias androiddebugkey \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Android Debug,O=Android,C=US"
   ```
3. Run it, then grant **Modify system settings** when prompted and flip **Service Active** on.

## Other Android brands?

Mostly yes — it only uses standard APIs (`SensorManager` + `Settings.System.SCREEN_BRIGHTNESS`), so it runs across Samsung, Pixel, Xiaomi, OnePlus, Oppo/Vivo, Motorola, etc. on Android 8.0+. Caveats: it needs an ambient light sensor; aggressive OEM battery managers (Xiaomi, Huawei, Samsung, Oppo/Vivo) may kill the background service unless you exempt the app from battery optimization; and a few devices don't use the standard 0–255 brightness range, so absolute brightness/nits may be approximate (the relative offset still works).
