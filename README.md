# GC-Tune

[![GitHub Release](https://img.shields.io/github/v/release/Citrinae-Lime/GC-Tune?style=flat-square&color=2ea44f)](https://github.com/Citrinae-Lime/GC-Tune/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2011%2B-brightgreen.svg?style=flat-square)](https://android.com)
[![OriginOS](https://img.shields.io/badge/OriginOS-Supported-informational.svg?style=flat-square)](https://www.vivo.com/origin-os)

An advanced system tuning utility and privilege channel implementation for OriginOS and vivo devices. GC-Tune leverages the Game Cube (Ultra Game Mode) execution environment to execute commands with elevated system privileges (`uid 1000` / `system`), install custom `.itz` themes, and apply custom icon pack coloring directly to the OriginOS launcher without root.

[Download Latest Release](https://github.com/Citrinae-Lime/GC-Tune/releases/latest) &bull; [Quick Setup](#quick-setup) &bull; [Features](#features) &bull; [Building](#building)

---

## Overview

GC-Tune is built around an IPC channel established through the privileged Game Cube subsystem. This grants `system` (uid 1000) execution capabilities to tune device settings, patch system themes, and tint icons with Material You and custom palettes.

### Key Capabilities

- **Privileged System Shell (`uid 1000`)**  
  Run administrative shell commands directly under system credentials. Uses an idle-safe wake mechanism driven by `dumpsys SurfaceFlinger` and `logcat` signals without background polling or battery drain.

- **OriginOS Theme Patcher (`.itz`)**  
  Imports custom and third-party `.itz` theme archives, cleans up proprietary store metadata, and normalizes catalog identifiers (`id`, `id3`, `uid`) so custom themes can be previewed and applied via iTheme without license paywalls.

- **System & Adaptive Icon Recoloring**  
  Applies uniform icon tinting and styling directly into the OriginOS launcher:
  - Material 3 and custom HEX color tinting
  - Adjustable desaturation, brightness, and tint strength
  - Custom background tiles and squircle corner radius adjustment
  - Compatibility with standard third-party icon packs

- **Zero Root Requirement**  
  Requires no root access or bootloader unlocking. Operates purely through system framework integration and basic standard permissions (`WRITE_SECURE_SETTINGS` via ADB).

---

## Quick Setup

### 1. Installation

Download the signed APK from the [Releases](https://github.com/Citrinae-Lime/GC-Tune/releases/latest) page and install it on your device:

```bash
adb install -r gctune-signed.apk
```

### 2. Grant Permissions

Grant `WRITE_SECURE_SETTINGS` via ADB once after installation:

```bash
adb shell pm grant com.poc.gctune android.permission.WRITE_SECURE_SETTINGS
```

### 3. Add to Game Cube

1. Open **Settings** &rarr; **Ultra Game Mode** (Game Cube).
2. Go to **Supported Games** (Add Game).
3. Under **Other Apps**, select **GC Tune** and add it to the game list.
4. Launch GC Tune. The onboarding screen will confirm the active channel connection.

---

## Architecture & IPC Channel

```
 +-------------------------------------------------------+
 |                       GC Tune                         |
 |  com.poc.gctune (Activities / Foreground Service)      |
 +-------------------------------------------------------+
                             |
                   SurfaceControl / IPC
                             v
 +-------------------------------------------------------+
 |                 Game Cube Subsystem                   |
 |              SurfaceFlinger / uid 1000                |
 +-------------------------------------------------------+
                             |
         +-------------------+-------------------+
         |                   |                   |
         v                   v                   v
   System Shell        Theme Patcher        Icon Engine
 (uid 1000 execution)   (.itz rewrite)    (Launcher tint)
```

The app initiates a lightweight foreground service (`ChannelService`) holding the layer and ACK receiver while commands run. The watcher daemon runs strictly event-driven via `logcat`, ensuring zero CPU usage when idle.

---

## Building from Source

### Option 1: Standard Gradle Build (Recommended)

This compiles the active `app/` application module, including complete multi-language resources, Material 3 components, and optimizations.

#### Prerequisites

- **JDK 17 or higher** (JDK 21 or 25 recommended)
- **Android SDK** (Platform 36 / Build-Tools 35+)

#### 1. Configure Signing Credentials

Signing configuration is in [`app/signing.gradle`](app/signing.gradle) so you can specify your own keystore credentials without modifying core build scripts:

```groovy
// In app/signing.gradle
android {
    signingConfigs {
        release {
            storeFile file('/path/to/your/release.keystore')
            storePassword 'your_keystore_password'
            keyAlias 'your_key_alias'
            keyPassword 'your_key_password'
        }
    }
}
```

*(For debug builds, Gradle automatically uses the default debug keystore).*

#### 2. Compile via Gradle Wrapper

```powershell
# Build Release APK
.\gradlew assembleRelease

# Or build Debug APK
.\gradlew assembleDebug
```

Compiled APKs will be located at `app/build/outputs/apk/`.

---

### Option 2: Legacy Standalone Script (`build.ps1`)

The repository retains the original upstream standalone PowerShell build script (`build.ps1`), which compiles the root-level flat `.java` files directly using raw SDK tools (`javac`, `d8`, `aapt`, `zipalign`, `apksigner`) without Gradle.

> [!NOTE]
> `build.ps1` compiles the legacy flat files without the XML resource system or internationalization. Before running it, ensure you edit `build.ps1` to update the hardcoded local paths (`$src`, `$bt`, `$androidJar`, `$jdk`, and signing keystore) to match your local machine.

```powershell
.\build.ps1
```

---

## Disclaimer

This project is intended for research, device customization, and educational purposes. Ensure you comply with your device manufacturer policies before modifying system parameters.