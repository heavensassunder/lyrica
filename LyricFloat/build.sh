#!/data/data/com.termux/files/usr/bin/bash
# LyricFloat: build on the phone, without Gradle.
# If FloatingPet already builds on this phone, its SDK and tools can be reused:
#   ~/android-sdk/platforms/android-34/android.jar
#   ~/android-sdk/build-tools/<version>/d8 (and optionally aapt2, zipalign, apksigner)
# Otherwise install the missing tools listed below and provide an API 34+ platform
# android.jar at that location, ~/android.jar, or in ANDROID_JAR. A base.apk is
# not a platform jar. Some Termux SDK-style packages may provide a platform jar.
# Run: chmod +x build.sh && ./build.sh [--install|--open]
set -euo pipefail
cd "$(dirname "$0")"

# Use the same SDK build-tools location as FloatingPet when tools are not on PATH.
find_tool() {
    local name="$1" found=""
    if command -v "$name" >/dev/null 2>&1; then
        command -v "$name"
        return
    fi
    if [ -d "$HOME/android-sdk/build-tools" ]; then
        found=$(find "$HOME/android-sdk/build-tools" -type f -name "$name" 2>/dev/null | sort -V | tail -n 1)
    fi
    if [ -n "$found" ] && [ -x "$found" ]; then printf '%s\n' "$found"; return; fi
    return 1
}
missing=0
need_tool() {
    local name="$1" package="$2" path
    if path=$(find_tool "$name"); then printf -v "$3" '%s' "$path"
    else echo "Missing $name. Install with: pkg install $package" >&2; missing=1; fi
}
# aapt2 is preferred, but FloatingPet's aapt can also generate R.java and package resources.
AAPT2=""; AAPT=""
AAPT2=$(find_tool aapt2) || AAPT2=""
if [ -n "$AAPT2" ] && ! "$AAPT2" version >/dev/null 2>&1; then
    echo "Warning: $AAPT2 cannot run; trying aapt instead." >&2
    AAPT2=""
fi
if [ -z "$AAPT2" ]; then
    AAPT=$(find_tool aapt) || AAPT=""
    if [ -n "$AAPT" ] && ! "$AAPT" version >/dev/null 2>&1; then
        echo "Warning: $AAPT cannot run." >&2
        AAPT=""
    fi
fi
if [ -z "$AAPT2" ] && [ -z "$AAPT" ]; then
    echo 'Missing or broken aapt/aapt2. Try: pkg update && pkg upgrade -y' >&2
    echo 'If needed, reinstall with: pkg reinstall aapt abseil-cpp libprotobuf' >&2
    missing=1
fi
need_tool d8 d8 D8
need_tool apksigner apksigner APKSIGNER
need_tool zipalign aapt ZIPALIGN
need_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing $1. Install with: pkg install $2" >&2; missing=1
    fi
}
need_command zip zip
need_command keytool openjdk-17
need_command find findutils
if ! command -v javac >/dev/null 2>&1 && ! command -v ecj >/dev/null 2>&1; then
    echo 'Missing Java compiler. Install with: pkg install ecj (or pkg install openjdk-17)' >&2
    missing=1
fi
if [ "$missing" -ne 0 ]; then exit 1; fi

if [ -n "${ANDROID_JAR:-}" ]; then
    : # Respect an explicit path, even if it is invalid, so the error is clear.
elif [ -f "$HOME/android-sdk/platforms/android-34/android.jar" ]; then
    ANDROID_JAR="$HOME/android-sdk/platforms/android-34/android.jar"
else
    ANDROID_JAR="$HOME/android.jar"
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Missing platform android.jar: $ANDROID_JAR" >&2
    echo 'Copy an Android SDK platforms/android-34/android.jar (or newer) to ~/android.jar, or set ANDROID_JAR.' >&2
    echo 'Some Termux Android SDK-style packages may provide a platform jar. A base.apk is not a platform jar.' >&2
    exit 1
fi
case "${1:-}" in ''|--install|--open) ;; *) echo "Usage: $0 [--install|--open]" >&2; exit 2;; esac
trap 'echo "Build failed at line $LINENO. Check the tool and platform jar versions above." >&2' ERR
# FloatingPet uses a 512 MB Java heap to keep D8 manageable on a phone.
export _JAVA_OPTIONS="${_JAVA_OPTIONS:--Xmx512m}"
echo "Using Android platform: $ANDROID_JAR"
echo "Using D8: $D8"
rm -rf build
mkdir -p build/compiled build/gen build/classes build/dex
if [ -n "$AAPT2" ]; then
    printf '%s\n' '[1/6] Compiling resources with aapt2'
    "$AAPT2" compile --dir res -o build/compiled/resources.zip
    printf '%s\n' '[2/6] Linking resources'
    "$AAPT2" link -o build/unsigned.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
        --min-sdk-version 26 --target-sdk-version 33 --java build/gen build/compiled/resources.zip
else
    printf '%s\n' '[1/6] Packaging resources with aapt'
    # Use the API 34 platform jar, not the device framework: the manifest uses
    # specialUse, which is not present in older devices' framework-res.apk.
    "$AAPT" package -f -m -M AndroidManifest.xml -S res -I "$ANDROID_JAR" \
        -J build/gen -F build/unsigned.apk --min-sdk-version 26 --target-sdk-version 33
    printf '%s\n' '[2/6] Resources linked'
fi
printf '%s\n' '[3/6] Compiling Java'
find src build/gen -name '*.java' | sort > build/sources.txt
if command -v javac >/dev/null 2>&1; then
    javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d build/classes @build/sources.txt
else
    ecj -1.8 -bootclasspath "$ANDROID_JAR" -d build/classes @build/sources.txt
fi
printf '%s\n' '[4/6] Converting to DEX'
# A jar includes nested classes and avoids command-line length limits.
(cd build/classes && zip -q -r ../classes.jar .)
"$D8" --min-api 26 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
(cd build/dex && zip -q ../unsigned.apk classes.dex)
printf '%s\n' '[5/6] Aligning APK'
"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk
printf '%s\n' '[6/6] Signing APK'
KEYSTORE="${LYRICFLOAT_KEYSTORE:-$HOME/.lyricfloat-debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias lyricfloat -keyalg RSA -keysize 2048 -validity 10000 \
        -dname 'CN=LyricFloat Debug,O=Local Development,C=US'
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias lyricfloat --ks-pass pass:android \
    --key-pass pass:android --out LyricFloat.apk build/aligned.apk
"$APKSIGNER" verify LyricFloat.apk
printf '%s\n' 'Built LyricFloat.apk'
if [ "${1:-}" = --open ]; then
    if command -v termux-open >/dev/null 2>&1; then termux-open LyricFloat.apk
    else echo 'Install with: pkg install termux-tools' >&2; exit 1; fi
elif [ "${1:-}" = --install ]; then
    if command -v rish >/dev/null 2>&1; then rish -c "pm install -r '$(pwd)/LyricFloat.apk'"
    else echo 'rish not found. Set up Shizuku/rish, or run ./build.sh --open to use the package installer.' >&2; exit 1; fi
fi
