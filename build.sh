#!/bin/sh
# Builds SuperTux.jar + SuperTux.jad (MIDP 2.0, CLDC 1.1) into dist/
set -e
cd "$(dirname "$0")"
J=/tmp/claude-0/j2me/jdk8u422-b05
API=/home/claude/hex007/freej2me/build/classes     # javax.microedition signatures (compile only)
PG=/tmp/claude-0/j2me/proguard-7.4.2/lib/proguard.jar
rm -rf build dist; mkdir -p build/cls dist
$J/bin/javac -nowarn -source 1.3 -target 1.3 -bootclasspath $J/jre/lib/rt.jar -cp $API -d build/cls src/*.java 2>&1 | grep -v JAVA_TOOL_OPTIONS | tee build/javac.log || true
if grep -q "error" build/javac.log; then exit 1; fi
ls build/cls/SuperTux.class >/dev/null
cat > build/MANIFEST.MF <<M
MIDlet-1: SuperTux,/icon.png,SuperTux
MIDlet-Name: SuperTux
MIDlet-Vendor: SuperTux Devel Team, Jecka22LR
MIDlet-Version: 1.1.0
MIDlet-Description: SuperTux 0.1.3 J2ME - Beta Release 1.1
MIDlet-Icon: /icon.png
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
M
cp -r res build/res
$J/bin/jar cfm build/raw.jar build/MANIFEST.MF -C build/cls . -C build/res . 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
cat > build/pg.pro <<P
-injars $PWD/build/raw.jar
-outjars $PWD/dist/SuperTux.jar
-libraryjars $J/jre/lib/rt.jar
-libraryjars $API
-microedition
-target 1.3
-dontobfuscate
-dontoptimize
-keep public class SuperTux
-dontnote
-dontwarn
P
$J/bin/java -jar $PG @build/pg.pro 2>&1 | grep -v JAVA_TOOL_OPTIONS | grep -i "error\|warn" || true
SIZE=$(stat -c %s dist/SuperTux.jar)
{ cat build/MANIFEST.MF; echo "MIDlet-Jar-URL: SuperTux.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > dist/SuperTux.jad
# lite build for old phones (1 MB JAR limit): MIDI music only
mkdir -p build/lite && cd build/lite && unzip -q -o ../../dist/SuperTux.jar && rm -f m/*.mp3 && \
  $J/bin/jar cfm ../../dist/SuperTux-lite.jar META-INF/MANIFEST.MF $(ls | grep -v META-INF) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
cd ../..
SIZE=$(stat -c %s dist/SuperTux-lite.jar)
{ cat build/MANIFEST.MF; echo "MIDlet-Jar-URL: SuperTux-lite.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > dist/SuperTux-lite.jad
ls -la dist
