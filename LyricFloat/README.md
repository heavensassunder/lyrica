# LyricFloat

A framework-only, Java 8 Android app. It observes media sessions via notification access, looks up lyrics using LRCLIB and shows a click-through floating panel. It also has seven editable colours, themes and a Canvas-drawn guitar mode with chord sheets. No Gradle, AndroidX, Kotlin or application libraries are used.

## Build in Termux

If you built FloatingPet on this phone, the script can reuse its existing SDK at `~/android-sdk/platforms/android-34/android.jar`. Like FloatingPet, it looks for `d8` in `~/android-sdk/build-tools` and uses a 512 MB Java heap by default. It finds `aapt2`, `zipalign`, and `apksigner` on PATH or in the SDK build-tools; if `aapt2` is unavailable it can use `aapt` instead. Missing tools are reported with their Termux package names.

From a fresh checkout:

```sh
cd ~
git clone --branch arena/0bb2cbe3-lyrica https://github.com/heavensassunder/lyrica.git lyrica
cd ~/lyrica/LyricFloat
bash build.sh --open
```

For a later build, just run the last two commands. `--open` opens the package installer; omit it to build only. If a tool is missing, install the package named in the error, for example `pkg install aapt d8 apksigner openjdk-17 zip`. If neither `javac` nor `ecj` is present, install `openjdk-17` or `ecj`. Available packages and architecture support can vary with the Termux version and repository, especially on older 32-bit ARM devices.

The platform jar must be API 34 or newer for the `specialUse` manifest attribute, despite targeting API 33. The script checks the FloatingPet SDK path first and then `~/android.jar`. To use another platform jar, run `ANDROID_JAR=/path/to/android.jar bash build.sh --open`. An installed app's `base.apk` is not a substitute. Unlike FloatingPet's packaging step, LyricFloat links resources against the API 34 platform jar rather than `/system/framework/framework-res.apk`, so builds also work on devices whose own framework predates API 34.

The output is `LyricFloat.apk`. You can alternatively use `./build.sh --install` with Shizuku and a working `rish` setup. The APK path must be readable by the Shizuku shell; copy it to shared storage and install from there if needed. The script creates `~/.lyricfloat-debug.keystore` once with a publicly known debug password. Keep it for upgrade compatibility; it is not suitable for release signing.

## First use

1. Install the APK and open LyricFloat.
2. Grant **Notification access** to LyricFloat in system settings; return to the app.
3. Grant **Draw over other apps**; return to the app.
4. On Android 13+, grant **Notifications** for the persistent foreground notification.
5. Start the overlay, then play music in an app exposing an active media session. Use **Test / exit test overlay** to preview it without music. Turn on **Move overlay**, drag the panel, then turn it off to save its position and restore click-through.

The offset is applied to the playback position: positive values advance the lyrics. Search results and successful lyrics are cached locally (up to 200 entries).

## If it says "Waiting for music"

Open LyricFloat and read the **Media:** line under **Now playing**. It reports whether the listener is connected, how many sessions are visible and the current track. **Refresh media session** asks the listener to re-query immediately; the listener also reconciles every three seconds and requests a system rebind after a disconnect.

- **Notification access: Not granted** or **Listener disconnected**: open **Notification access**, turn LyricFloat off and back on, then return to the app. Installing an APK or an OEM battery manager can silently drop this permission.
- **No active media sessions** while audio plays: the player is not exposing a media session (or it was force-stopped/swiped away). Reopen the player, start playback from inside it, and tap Refresh.
- **Playing: …** but the overlay still waits: stop and restart the overlay so it reloads the current state.
- Exclude LyricFloat from aggressive battery optimisation and "app sleep" on OEM ROMs; notification listeners and overlays are often killed otherwise.

## Themes

Open **Theme / colors** in the app. Tap any of the seven swatch buttons to open the live picker: drag the hue bar, touch the saturation/value square, set alpha, or type `#RRGGBB` / `#AARRGGBB`. The sample line and the running overlay update immediately. **Cancel** restores the previous colour. The seven presets replace all colours; **Save current as theme** stores colours, font and outline as JSON in SharedPreferences, and **Saved themes** applies or deletes them. The font selector and outline slider also apply live.

## Guitar mode

Turn on **Guitar mode**, then start the overlay. It uses 95% of the screen width and 50-90% of its height (default 75%). **Move overlay** still drags it and restores click-through when turned off. The fixed top bar shows the current and next chord with a progress bar, and `NOW` diagrams can be enabled. Lyrics scroll so the current line sits about 30% down; earlier lines fade. The first unconfigured launch of a song shows a no-chords notice while the synced lyrics still scroll.

- **Import chord sheet** accepts a chord line above each lyric line, ChordPro `[Am]inline` chords, and `[Verse]` / `Verse 1:` labels. Paste from the clipboard or share text to LyricFloat. **Preview alignment** reports matches and interpolates unmatched lines before saving. The result is stored as `files/chords/<sha256>.json`, using the same title cleaning and artist/track hash convention as the lyrics cache.
- **Record chords** shows the sheet's chord buttons plus **Add chord**. While the song plays, each tap stores the chord at the current position minus the reaction delay (default 150 ms). Recorded changes can be nudged by 50 ms or deleted.
- **Edit chord sheet** opens each line; place the cursor and add a chord, or edit a chord's name, character offset and exact recorded time.
- **Export / share chord sheet** sends a plain-text chord-over-lyrics sheet through any text app; shared text can be imported back.

Transpose (-11 to +11) and capo (0-12) only change what is displayed; the stored chords stay in concert pitch. **Prefer flats**, **Simplify chords**, diagram visibility/size and the separate chord timing offset are in **Guitar settings**. **Test / exit test guitar mode** previews a built-in five-line sample without music.

## Limitations

- Only apps that expose media sessions to Android's notification listener can be detected; providers may omit metadata, timestamps, duration or playback controls. If several sessions play at once, the first playing session reported by Android wins.
- LRCLIB may have no result, only unsynced text, or an incorrect match. A search result is accepted only within five seconds of known duration; without duration, the first usable result is used. Offline use requires a previous cached success. The static plain-lyrics view cannot be scrolled while click-through is active and long lyrics may exceed the screen.
- Android 12+ blocks touches through untrusted overlays above 80% window opacity; normal click-through mode caps it at 80%, even when the slider is set higher. Other overlays can also affect Android's combined obscuring-opacity rules. Edit mode is intentionally touchable.
- The operating system or device manufacturer may restrict foreground services, notification listeners, overlays, or background operation. Start the overlay manually after process death or reboot. Full-screen/security-sensitive apps may hide or block overlays.
- Test mode repeats sample lyrics every 25 seconds; press the test button again to return to detected music. Drag placement is stored in pixels and can need adjustment after rotation or a display-size change.
- Guitar chord recognition accepts common roots, `m`, `maj`, `min`, `dim`, `aug`, `sus2`, `sus4`, `addN`, numeric extensions and slash basses. Unusual symbols (`M7`, `6/9`, parentheses, `N.C.`) are ignored or imported as text. Diagrams come from movable E/A-form shapes; unknown or slash chords show only the name. Capo display assumes standard tuning.
- Alignment is a best-effort fuzzy match in forward order. Covers, repeated lines, ad-libs and sheets whose lyrics differ from LRCLIB can need the manual editor or recorded times. A sheet with no matched lines gets an estimated four-second line cadence and is marked "Timing estimated". Unsynchronised lyrics scroll in guitar layout at the same estimated cadence.
- The click-through overlay cannot receive a tap, so the no-chords notice is a prompt to open LyricFloat rather than an overlay button. Sharing text only attaches to the song detected when the import screen opens.
