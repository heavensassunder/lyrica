# LyricFloat

A framework-only, Java 8 Android app. It observes media sessions via notification access, looks up lyrics using LRCLIB and shows a click-through floating panel. No Gradle, AndroidX, Kotlin or application libraries are used.

## Build in Termux

Use a recent Termux from F-Droid or GitHub, not the obsolete Play Store build. Available packages and architecture support can vary with Termux version and repository. On a 32-bit ARM device the Java compiler and DEX tool can be memory-intensive; not all current Termux/OpenJDK builds necessarily support every old device.

```sh
pkg update
pkg install aapt ecj d8 apksigner openjdk-17 zip
cd LyricFloat
# Copy the Android SDK platform jar from platforms/android-34/android.jar to ~/android.jar.
# Alternatively: export ANDROID_JAR=/path/to/platforms/android-34/android.jar
chmod +x build.sh
./build.sh
```

The platform jar must be API 34 or newer for the `specialUse` manifest attribute, despite targeting API 33. Some SDK-style Termux packages may provide it; otherwise copy it from a normal Android SDK or a project containing the *platform* jar. An installed app's `base.apk` is not a substitute. The output is `LyricFloat.apk`. Run `./build.sh --open` to open the package installer, or `./build.sh --install` with Shizuku and a working `rish` setup. The latter requires the APK path to be readable by the Shizuku shell; copy it to shared storage and install from there if needed. The script creates `~/.lyricfloat-debug.keystore` once with a publicly known debug password. Keep it for upgrade compatibility; it is not suitable for release signing.

## First use

1. Install the APK and open LyricFloat.
2. Grant **Notification access** to LyricFloat in system settings; return to the app.
3. Grant **Draw over other apps**; return to the app.
4. On Android 13+, grant **Notifications** for the persistent foreground notification.
5. Start the overlay, then play music in an app exposing an active media session. Use **Test / exit test overlay** to preview it without music. Turn on **Move overlay**, drag the panel, then turn it off to save its position and restore click-through.

The offset is applied to the playback position: positive values advance the lyrics. Search results and successful lyrics are cached locally (up to 200 entries).

## Limitations

- Only apps that expose media sessions to Android's notification listener can be detected; providers may omit metadata, timestamps, duration or playback controls. If several sessions play at once, the first playing session reported by Android wins.
- LRCLIB may have no result, only unsynced text, or an incorrect match. A search result is accepted only within five seconds of known duration; without duration, the first usable result is used. Offline use requires a previous cached success. The static plain-lyrics view cannot be scrolled while click-through is active and long lyrics may exceed the screen.
- Android 12+ blocks touches through untrusted overlays above 80% window opacity; normal click-through mode caps it at 80%, even when the slider is set higher. Other overlays can also affect Android's combined obscuring-opacity rules. Edit mode is intentionally touchable.
- The operating system or device manufacturer may restrict foreground services, notification listeners, overlays, or background operation. Start the overlay manually after process death or reboot. Full-screen/security-sensitive apps may hide or block overlays.
- Test mode repeats sample lyrics every 25 seconds; press the test button again to return to detected music. Drag placement is stored in pixels and can need adjustment after rotation or a display-size change.
