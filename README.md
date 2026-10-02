<p align="center">
  <img src="docs/logo.svg" width="120" alt="MultiShot logo">
</p>

<h1 align="center">MultiShot</h1>

<p align="center">
  Screenshots with a floating button, made for Android devices with more than one screen.
</p>

<p align="center">
  <a href="https://github.com/mkaflowski/MultiShot/releases"><img src="https://img.shields.io/github/downloads/mkaflowski/MultiShot/total?style=for-the-badge&color=1E88E5&label=downloads" alt="Downloads"></a>
  <a href="https://github.com/mkaflowski/MultiShot/releases/latest"><img src="https://img.shields.io/github/v/release/mkaflowski/MultiShot?style=for-the-badge&color=1E88E5" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
</p>

---

On dual-screen devices such as the **AYN Thor**, MultiShot captures **all screens at once** and saves them as one image, stacked top to bottom. You can also capture a single screen or save each screen to its own file.

## Features

- **Floating button**: tap to take a screenshot, drag to move it, long-press to open settings. You can set its transparency, its size and which screen it appears on.
- **Multi-screen capture**:
  - all screens in one image, stacked (default)
  - all screens as separate files
  - only one selected screen
- **Quick Settings tile** to show or hide the floating button.
- **Optional feedback**: light vibration, shutter sound, and a "Saved" message.
- Hides itself while it captures, so the button never shows up in your screenshots.
- Saves to `Pictures/Screenshots` as PNG.
- No internet or storage permissions.

## Download

Get the APK from the **[latest release](https://github.com/mkaflowski/MultiShot/releases/latest)**.

## Setup

1. Install the APK.
2. Open MultiShot and tap **Ustawienia dostępności** (Accessibility settings). Enable **MultiShot – zrzuty ekranu**.
3. *(Android 13+, sideloaded APK)* If Android says the setting is restricted, go to **App info → ⋮ → Allow restricted settings** and try again.
4. *(Optional)* Tap **Dodaj kafelek do szybkich ustawień** (Add tile to Quick Settings).

> MultiShot uses an accessibility service only to take screenshots and to draw its floating button. Without root, this is the only Android API that can capture any chosen screen. The service does not read the content of your apps.

The app's interface is currently in Polish.

## Building

You need JDK 17+ and the Android SDK (API 35).

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`.
