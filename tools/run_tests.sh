#!/usr/bin/env bash
# Tests sur PC des parties sans interface (lecture .glb, ouvrants, OBD2, CAN, demo).
# Necessite : build.sh deja lance (classes compilees) + org.json (telecharge si absent).
set -euo pipefail
cd "$(dirname "$0")/.."
A34="${ANDROID_JARS:-/opt/android-local/platforms}/android-34/android.jar"
LIB=build/testlib
mkdir -p "$LIB" build/test
if ! unzip -tq "$LIB/json.jar" >/dev/null 2>&1; then
  for i in 1 2 3 4; do
    curl -fsS -o "$LIB/json.jar" https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar \
      && unzip -tq "$LIB/json.jar" >/dev/null 2>&1 && break
    sleep $((i * 3))
  done
fi
# org.json reel AVANT android.jar (dont les classes ne sont que des bouchons)
CP="$LIB/json.jar:build/classes:$A34"
javac -nowarn -encoding UTF-8 -cp "$CP" -d build/test app/src/test/java/com/rayan/w204dash/UnitTests.java
java -cp "build/test:$CP" com.rayan.w204dash.UnitTests "$@"
