#!/bin/bash
# Launches a Modelio .app like a double-click (LaunchServices `open`, no shell PATH/JAVA_HOME) and checks that it works.
# macOS only. Used by CI (smoke test and signing self-test).
#
#   smoke-open.sh <path/to/Modelio.app>
#   env: SHOT_NAME  screenshot file stem written to $RUNNER_TEMP (default: screenshot-open)
#
# Passes when the app reaches the workspace stage, the process has loaded the libjvm of the BUNDLED JRE (the macOS
# launcher runs the JVM in its own process, so there is no separate java process) and the log has no application error.
APP="${1:?usage: smoke-open.sh <app>}"
SHOT="${SHOT_NAME:-screenshot-open}"
TMPDIR_="${RUNNER_TEMP:-/tmp}"

pkill -f "Contents/MacOS/modelio" 2>/dev/null; sleep 3
rm -rf "$HOME/.modelio"
open "$APP"
OK=0; LOGF=""
for i in $(seq 1 24); do
    sleep 5
    LOGF="$(ls "$HOME"/.modelio/5.4/modelio-*.log 2>/dev/null | head -1 || true)"
    if [ -n "$LOGF" ] && grep -q "Changing workspace to" "$LOGF"; then OK=1; echo "reached the workspace stage after about $((i*5))s"; break; fi
done
echo "log file: $LOGF"
echo "---- Modelio processes:"; ps -axo pid,etime,args | grep -E "[C]ontents/MacOS/modelio|[j]re/Contents/Home/bin/java" | cut -c1-200
sleep 15
screencapture -x "$TMPDIR_/$SHOT.png" 2>&1 || echo "screencapture failed"
[ -n "$LOGF" ] && { echo "---- log tail:"; tail -15 "$LOGF" | cut -c1-180; }
if [ "$OK" != 1 ]; then echo "::error::launched with open: the app did not reach the workspace stage"; exit 1; fi

# the macOS launcher loads the JVM inside its own process (no separate java process): look at the loaded libjvm
PID="$(pgrep -f "Contents/MacOS/modelio" | head -1)"
JVMLIB="$(lsof -p "$PID" 2>/dev/null | grep "libjvm.dylib" | grep -o '/.*libjvm.dylib' | head -1)"   # path contains spaces
echo "libjvm loaded by the launcher process $PID: $JVMLIB"
case "$JVMLIB" in
    *"Modelio 5.4.1.app/Contents/Eclipse/jre/Contents/Home/"*) ;;
    *) echo "::error::launched with open: not running on the bundled JRE (libjvm: '$JVMLIB')"; exit 1 ;;
esac
if grep -qE "Application error|could not be found in the registry|UnsatisfiedLinkError" "$LOGF"; then
    echo "::error::launched with open: application error in the log"; grep -nE -A4 "Application error|UnsatisfiedLinkError" "$LOGF" | head -20; exit 1
fi
echo "OK: started with open, on the bundled JRE, no application error"
pkill -f "Contents/MacOS/modelio" 2>/dev/null || true
