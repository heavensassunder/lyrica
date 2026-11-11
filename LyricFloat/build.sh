#!/data/data/com.termux/files/usr/bin/bash
# LyricFloat: one-time setup in a current Termux installation:
#   pkg update && pkg install aapt ecj d8 apksigner openjdk-17 zip
# Copy a *platform* android.jar from Android SDK platform API 34 or newer to
# ~/android.jar (or set ANDROID_JAR). An installed app's base.apk is NOT an
# android.jar. Some Termux/third-party Android SDK packages may supply platform
# jars; otherwise copy platforms/android-34/android.jar from an SDK installation
# or from an existing project that contains the actual platform android.jar.
# API 34+ is needed to compile the special-use foreground-service declarations.
# Then run: chmod +x build.sh && ./build.sh [--install|--open]
set -euo pipefail
cd "$(dirname "$0")"
missing=0
need() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing $1. Install with: pkg install $2" >&2
        missing=1
    fi
}
need aapt2 aapt
need d8 d8
need apksigner apksigner
need zipalign aapt
need zip zip
need keytool openjdk-17
if ! command -v javac >/dev/null 2>&1 && ! command -v ecj >/dev/null 2>&1; then
    echo 'Missing Java compiler. Install with: pkg install ecj (or pkg install openjdk-17)' >&2
    missing=1
fi
if [ "$missing" -ne 0 ]; then exit 1; fi
ANDROID_JAR="${ANDROID_JAR:-$HOME/android.jar}"
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Missing platform android.jar: $ANDROID_JAR" >&2
    echo 'Copy an Android SDK platforms/android-34/android.jar (or newer) to ~/android.jar, or set ANDROID_JAR.' >&2
    echo 'Some Termux Android SDK-style packages may provide a platform jar. A base.apk is not a platform jar.' >&2
    exit 1
fi
case "${1:-}" in ''|--install|--open) ;; *) echo "Usage: $0 [--install|--open]" >&2; exit 2;; esac
trap 'echo "Build failed at line $LINENO. Check the tool and platform jar versions above." >&2' ERR
rm -rf build
mkdir -p build/compiled build/gen build/classes build/dex
printf '%s\n' '[1/6] Compiling resources'
aapt2 compile --dir res -o build/compiled/resources.zip
printf '%s\n' '[2/6] Linking resources'
aapt2 link -o build/unsigned.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 26 --target-sdk-version 33 --java build/gen build/compiled/resources.zip
printf '%s\n' '[3/6] Compiling Java'
find src build/gen -name '*.java' | sort > build/sources.txt
if command -v javac >/dev/null 2>&1; then
    javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d build/classes @build/sources.txt
else
    ecj -1.8 -bootclasspath "$ANDROID_JAR" -d build/classes @build/sources.txt
fi
printf '%s\n' '[4/6] Converting to DEX'
# A jar avoids command-line length limits and is accepted by d8.
(cd build/classes && zip -q -r ../classes.jar .)
d8 --min-api 26 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
(cd build/dex && zip -q ../unsigned.apk classes.dex)
printf '%s\n' '[5/6] Aligning APK'
zipalign -f 4 build/unsigned.apk build/aligned.apk
printf '%s\n' '[6/6] Signing APK'
KEYSTORE="${LYRICFLOAT_KEYSTORE:-$HOME/.lyricfloat-debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias lyricfloat -keyalg RSA -keysize 2048 -validity 10000 \
        -dname 'CN=LyricFloat Debug,O=Local Development,C=US'
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias lyricfloat --ks-pass pass:android \
    --key-pass pass:android --out LyricFloat.apk build/aligned.apk
apksigner verify LyricFloat.apk
printf '%s\n' 'Built LyricFloat.apk'
if [ "${1:-}" = --open ]; then
    if command -v termux-open >/dev/null 2>&1; then termux-open LyricFloat.apk
    else echo 'Install with: pkg install termux-tools' >&2; exit 1; fi
elif [ "${1:-}" = --install ]; then
    if command -v rish >/dev/null 2>&1; then rish -c "pm install -r '$(pwd)/LyricFloat.apk'"
    else echo 'rish not found. Set up Shizuku/rish, or run ./build.sh --open to use the package installer.' >&2; exit 1; fi
fi
