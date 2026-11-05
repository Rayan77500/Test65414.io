#!/usr/bin/env bash
# Compilation de l'APK sans Android Studio ni Gradle :
#   aapt2 (ressources) -> javac (Java 8) -> verification API 16 -> dx (dex)
#   -> zipalign -> apksigner (cle de debug du depot) -> livrable/W204Dash.apk
#
# Prerequis (Ubuntu/Debian) : sudo apt install openjdk-17-jdk aapt zipalign apksigner dalvik-exchange
# + android.jar API 34 et API 16 (variable ANDROID_JARS, par defaut /opt/android-local/platforms)
set -euo pipefail
cd "$(dirname "$0")"

JARS="${ANDROID_JARS:-/opt/android-local/platforms}"
A34="$JARS/android-34/android.jar"
A16="$JARS/android-16/android.jar"
BT="${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}"
AAPT2="$BT/aapt2"
OUT=build
APP=app/src/main
# version / SDK lus dans app/build.gradle (source unique, partagee avec Android Studio)
VERSION_NAME=$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle)
VERSION_CODE=$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' app/build.gradle)
MIN_SDK=$(sed -n 's/.*minSdk \([0-9]*\).*/\1/p' app/build.gradle)
TARGET_SDK=$(sed -n 's/.*targetSdk \([0-9]*\).*/\1/p' app/build.gradle)

for f in "$A34" "$A16" "$AAPT2"; do
  [ -f "$f" ] || { echo "Manquant : $f"; exit 1; }
done

# (build/testlib et build/preview, utilises par les tests, sont conserves)
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/check" "$OUT/final" "$OUT/dex" "$OUT"/*.apk "$OUT"/*.zip "$OUT"/*.txt "$OUT"/*.xml
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/check" "$OUT/dex"

PKG=com.rayan.w204dash
echo "1/6 Ressources (aapt2)"
# le manifeste source n'a pas d'attribut package (convention Android Studio) : on l'ajoute
sed "s#<manifest #<manifest package=\"$PKG\" #" "$APP/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res.zip"
"$AAPT2" link -I "$A34" --manifest "$OUT/AndroidManifest.xml" -A "$APP/assets" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --java "$OUT/gen" \
  -o "$OUT/base.apk" "$OUT/res.zip"

echo "2/6 Compilation Java"
find "$APP/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$A34" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "3/6 Verification de compatibilite Android 4.1 (API 16)"
# tout le code hors du paquet 'compat' doit compiler avec l'API 16 seule
grep -v '/compat/' "$OUT/sources.txt" > "$OUT/sources16.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$A16" \
  -cp "$OUT/classes" -implicit:none -d "$OUT/check" @"$OUT/sources16.txt"

# l'APK utilise le code compile contre l'API 16 (+ le paquet compat compile contre l'API 34)
mkdir -p "$OUT/final/com/rayan/w204dash"
cp -r "$OUT/check/." "$OUT/final/"
cp -r "$OUT/classes/com/rayan/w204dash/compat" "$OUT/final/com/rayan/w204dash/"
# aucun appel Java 8+ absent d'Android 4.1 (lambdas, Objects, try-with-resources...)
if find "$OUT/final" -name '*.class' -print0 | xargs -0 javap -c -p 2>/dev/null \
   | grep -E 'invokedynamic|java/util/Objects|addSuppressed|java/util/function|java/nio/charset/StandardCharsets' ; then
  echo "Bytecode incompatible avec Android 4.1"; exit 1
fi

echo "4/6 Conversion en dex"
dalvik-exchange --dex --min-sdk-version=16 --output="$OUT/dex/classes.dex" "$OUT/final" 2>&1 \
  | grep -v JAVA_TOOL_OPTIONS || true
[ -f "$OUT/dex/classes.dex" ] || { echo "dx a echoue"; exit 1; }

echo "5/6 Assemblage + alignement"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q -X ../unaligned.apk classes.dex)
zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "6/6 Signature (cle de debug)"
KS=tools/debug.keystore
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
    -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 2>&1 | grep -v JAVA_TOOL || true
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey \
  --min-sdk-version 16 --out "$OUT/W204Dash.apk" "$OUT/aligned.apk" 2>&1 | grep -v JAVA_TOOL || true
apksigner verify "$OUT/W204Dash.apk" 2>&1 | grep -v JAVA_TOOL || true

mkdir -p livrable
cp "$OUT/W204Dash.apk" livrable/W204Dash.apk
echo "OK : livrable/W204Dash.apk (version $VERSION_NAME, $(du -h livrable/W204Dash.apk | cut -f1))"
