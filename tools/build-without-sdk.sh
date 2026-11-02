#!/usr/bin/env bash
# Builds a signed APK without the Android SDK or Gradle (used where Google's Maven is unreachable).
# The normal way is ./gradlew assembleRelease; this script is only a fallback.
# Needs: JDK 17+, aapt, zipalign, apksigner (Debian/Ubuntu packages), r8lib.jar (D8) and an android.jar
# (e.g. the Robolectric android-all 14 jar from Maven Central, which includes resources.arsc).
#   SDK=/path/with/android-34.jar+r8lib.jar KEYSTORE=/path/release.keystore KS_PASS=... tools/build-without-sdk.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
SDK="${SDK:?set SDK to the folder with android-34.jar and r8lib.jar}"
OUT="${OUT:-$ROOT/build/nosdk}"
KEYSTORE="${KEYSTORE:?set KEYSTORE to your signing keystore}"
KS_PASS="${KS_PASS:?set KS_PASS}"
KS_ALIAS="${KS_ALIAS:-ledsync}"
VCODE=$(grep -oP "versionCode \K\d+" "$APP/build.gradle")
VNAME=$(grep -oP "versionName '\K[^']+" "$APP/build.gradle")
PKG=$(grep -oP "applicationId '\K[^']+" "$APP/build.gradle")
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

# AGP takes the package from build.gradle; aapt 1 needs it in the manifest
sed "s|<manifest |<manifest package=\"$PKG\" android:versionCode=\"$VCODE\" android:versionName=\"$VNAME\" |" \
    "$APP/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
aapt package -f -M "$OUT/AndroidManifest.xml" -S "$APP/src/main/res" -I "$SDK/android-34.jar" \
     --min-sdk-version 29 --target-sdk-version 34 -J "$OUT/gen" -F "$OUT/app.unaligned.apk"
javac -nowarn --release 17 -cp "$SDK/android-34.jar" -d "$OUT/classes" \
      $(find "$APP/src/main/java" "$OUT/gen" -name '*.java')
java -cp "$SDK/r8lib.jar" com.android.tools.r8.D8 --release --min-api 29 --lib "$SDK/android-34.jar" \
     --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -j "$OUT/app.unaligned.apk" classes.dex)
zipalign -f 4 "$OUT/app.unaligned.apk" "$OUT/app.aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
               --out "$OUT/LedSync-$VNAME.apk" "$OUT/app.aligned.apk"
apksigner verify "$OUT/LedSync-$VNAME.apk"
echo "OK: $OUT/LedSync-$VNAME.apk"
