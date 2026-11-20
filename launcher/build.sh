#!/bin/sh
# Builds and signs launcher.apk (package tv.local.ytredirect, kept so installs update in place) using the local Android SDK (no Gradle).
set -e
cd "$(dirname "$0")"
if [ "$(uname)" = "Darwin" ]; then
  SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; X=""
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; JBR="$JAVA_HOME/bin"
else
  SDK="$LOCALAPPDATA/Android/Sdk"; X=".bat"
  JBR="/c/Program Files/Android/Android Studio/jbr/bin"; export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
fi
BT="$SDK/build-tools/36.1.0"; JAR="$SDK/platforms/android-36.1/android.jar"
rm -rf build && mkdir -p build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml build/res.zip
"$JBR/javac" --release 17 -cp "$JAR" -d build/classes $(find src -name '*.java') 2>&1 | grep -v "warning" || true
"$BT/d8$X" --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && "$JBR/jar" uf ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
[ -f debug.keystore ] || "$JBR/keytool" -genkeypair -keystore debug.keystore -storepass android -keypass android -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=yt-redirect" >/dev/null 2>&1
"$BT/apksigner$X" sign --ks debug.keystore --ks-pass pass:android --out launcher.apk build/aligned.apk
echo BUILT launcher.apk
