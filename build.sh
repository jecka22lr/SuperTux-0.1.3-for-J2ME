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
MIDlet-Version: 1.2.0
MIDlet-Description: SuperTux 0.1.3 J2ME - Beta Release 1.2
MIDlet-Icon: /icon.png
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
M
cp -r res build/res
rm -f build/res/t-stock.png build/res/t-stock.bin build/res/te-stock.bin   # only for the jars without the mod
[ "$SFXTEST" = 1 ] || rm -rf build/res/a16 build/res/a8              # SFX format test sets: SFXTEST=1 sh build.sh
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
# the stock-only tile atlas (smaller, less RAM) for the jars without the mod, run inside their folder
stock_tiles() {
  [ -f ../../res/t-stock.png ] || return 0
  cp ../../res/t-stock.png t.png && cp ../../res/t-stock.bin t.bin && cp ../../res/te-stock.bin te.bin
}
# lite build for old phones (1 MB JAR limit): MIDI music only
mkdir -p build/lite && cd build/lite && unzip -q -o ../../dist/SuperTux.jar && rm -f m/*.mp3 && \
  { [ ! -f ../../res-atnw-only.txt ] || xargs rm -f < ../../res-atnw-only.txt; } && \
  mv x/g-lite.txt x/g.txt && rm -f x/g-atnw.txt && rm -rf a16 a8 && stock_tiles && \
  $J/bin/jar cfm ../../dist/SuperTux-lite.jar META-INF/MANIFEST.MF $(ls | grep -v META-INF) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
cd ../..
SIZE=$(stat -c %s dist/SuperTux-lite.jar)
{ cat build/MANIFEST.MF; echo "MIDlet-Jar-URL: SuperTux-lite.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > dist/SuperTux-lite.jad
# the full jar without the ATTNW mod (MP3 music, everything GPL): the one to publish, since ATTNW's
# music and many of its backgrounds come from other games
if [ -f res-atnw-only.txt ]; then
  mkdir -p build/gpl && cd build/gpl && unzip -q -o ../../dist/SuperTux.jar && \
    xargs rm -f < ../../res-atnw-only.txt && mv x/g-lite.txt x/g.txt && rm -f x/g-atnw.txt && stock_tiles && \
    $J/bin/jar cfm ../../dist/SuperTux-noattnw.jar META-INF/MANIFEST.MF $(ls | grep -v META-INF) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
  cd ../..
  SIZE=$(stat -c %s dist/SuperTux-noattnw.jar)
  { cat build/MANIFEST.MF; echo "MIDlet-Jar-URL: SuperTux-noattnw.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > dist/SuperTux-noattnw.jad
fi
# Adventure To The New World (ATTNW) edition (when convert.py was given the mod): its three adventures and
# islands, like the mod's own data swap; its own MIDlet name so it installs next to SuperTux
if [ -f res-atnw-only.txt ]; then
  mkdir -p build/atnw && cd build/atnw && unzip -q -o ../../dist/SuperTux.jar && \
    rm -f l/a[0-9]*.bin l/t[0-9]*.bin l/o[0-9]*.bin w/0.bin x/g-lite.txt && mv x/g-atnw.txt x/g.txt && \
    sed 's/^MIDlet-1: SuperTux,/MIDlet-1: SuperTux ATTNW,/; s/^MIDlet-Name: SuperTux$/MIDlet-Name: SuperTux ATTNW/; s/^MIDlet-Description: .*/MIDlet-Description: Adventure To The New World (ATTNW) by Miksy91, on SuperTux 0.1.3 J2ME/' \
      ../MANIFEST.MF > ../atnw.mf && \
    $J/bin/jar cfm ../../dist/SuperTux-ATTNW.jar ../atnw.mf $(ls | grep -v META-INF) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
  cd ../..
  SIZE=$(stat -c %s dist/SuperTux-ATTNW.jar)
  { cat build/atnw.mf; echo "MIDlet-Jar-URL: SuperTux-ATTNW.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > dist/SuperTux-ATTNW.jad
fi
ls -la dist
