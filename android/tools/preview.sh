#!/bin/bash
# Запускає застосунок в емуляторі, натискає підчергу й записує відео та скріншоти в preview/.
set -x
OUT=preview
mkdir -p "$OUT"
APK=android/app/build/outputs/apk/debug/app-debug.apk
QUEUE="${QUEUE:-6.1}"

adb wait-for-device
adb shell setprop persist.sys.timezone Europe/Kiev
adb shell cmd alarm set-timezone Europe/Kiev || true
adb shell svc power stayon true
adb shell input keyevent 82
adb install -r "$APK"

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null
  python3 - "$1" <<'EOF'
import re, sys
x = open('/tmp/ui.xml', encoding='utf-8').read()
m = re.search(r'text="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(sys.argv[1]), x)
if m:
    a, b, c, d = map(int, m.groups())
    print((a + c) // 2, (b + d) // 2)
EOF
}

adb shell screenrecord --time-limit 45 --bit-rate 6000000 /sdcard/demo.mp4 &
sleep 2

adb shell am start -n com.kiparisss.svitlo/.MainActivity
sleep 6
adb exec-out screencap -p > "$OUT/1-start.png"

XY=$(tap_text "$QUEUE")
echo "tap $QUEUE at: $XY"
if [ -n "$XY" ]; then adb shell input tap $XY; fi
sleep 14
adb exec-out screencap -p > "$OUT/2-schedule.png"

adb shell input swipe 540 1900 540 700 600
sleep 4
adb exec-out screencap -p > "$OUT/3-widget.png"

adb shell input swipe 540 700 540 1900 600
sleep 6

adb shell pkill -INT screenrecord || true
sleep 4
adb pull /sdcard/demo.mp4 "$OUT/demo.mp4"
adb logcat -d -s AndroidRuntime:E System.err:W > "$OUT/log.txt" || true
ls -la "$OUT"
