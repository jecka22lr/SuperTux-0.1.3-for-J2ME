#!/bin/sh
# Builds the unobfuscated classes against the desktop stand-in and runs a scripted test.
# usage: ./test.sh <outdir> <set> <level> "<script>"
set -e
cd "$(dirname "$0")"
J=/tmp/claude-0/j2me/jdk8u422-b05
rm -rf build/test; mkdir -p build/test
$J/bin/javac -nowarn -encoding UTF-8 -d build/test harness/javax/microedition/midlet/*.java harness/javax/microedition/lcdui/*.java harness/javax/microedition/lcdui/game/*.java harness/javax/microedition/media/*.java harness/javax/microedition/rms/*.java src/*.java harness/Run.java 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
cp -r res/* build/test/
$J/bin/java -Djava.awt.headless=true -cp build/test Run "$@" 2>&1 | grep -v JAVA_TOOL_OPTIONS
