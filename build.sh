#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/35.0.0"
ANDROID="$SDK/platforms/android-35/android.jar"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p build/classes build/dex lib
if [ ! -f lib/xposed-api-82.jar ]; then
 curl -fL https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar -o lib/xposed-api-82.jar
fi
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk -I "$ANDROID" --manifest AndroidManifest.xml --java build/generated -A assets build/resources.zip
javac -encoding UTF-8 --release 8 -classpath "$ANDROID:lib/xposed-api-82.jar" -d build/classes src/cn/macmanual/*.java build/generated/cn/macmanual/R.java
jar cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$ANDROID" --classpath lib/xposed-api-82.jar --min-api 26 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
# Local test-distribution certificate. Not a production signing identity.
if [ ! -f build/local-test.jks ]; then
 keytool -genkeypair -keystore build/local-test.jks -storepass android -keypass android -alias macmanual -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=MAC Manual Local Build"
fi
"$BT/apksigner" sign --ks build/local-test.jks --ks-pass pass:android --ks-key-alias macmanual --out build/MAC-Manual.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/MAC-Manual.apk
OUTPUT_DIR="${OUTPUT_DIR:-$PWD/dist}"
mkdir -p "$OUTPUT_DIR"
cp build/MAC-Manual.apk "$OUTPUT_DIR/MAC-Manual.apk"
