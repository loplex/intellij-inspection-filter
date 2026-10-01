#!/usr/bin/env bash
# Runs the UI tests, ./gradlew integrationTest, on a display of their own - see docs/development.md.
#
#   scripts/ui-tests.sh [GRADLE_ARGUMENTS...]     such as --tests '*FilterPopupUiTest*'
#
# UI_TESTS_DISPLAY picks the display the tests' IDE opens on:
#   xvfb      a virtual one, which shows nothing (the default)
#   xephyr    a window on the desktop, to watch the tests in
#   desktop   the desktop itself, $DISPLAY
# The display started for xvfb and xephyr is the first one free; UI_TESTS_SCREEN picks its size (1920x1080 by
# default). Its logs go to build/ui-tests.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
work="$root/build/ui-tests"
kind=${UI_TESTS_DISPLAY:-xvfb}
screen=${UI_TESTS_SCREEN:-1920x1080}

die() { echo "$*" >&2; exit 1; }

pids=()
cleanup() {
    for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT

start_server() {
    mkdir -p "$work"
    # The server picks the first display free, and writes its number there once it takes connections.
    local number="$work/display-number"
    rm -f "$number"
    case "$kind" in
        xvfb)
            command -v Xvfb >/dev/null || die "Xvfb is missing"
            Xvfb -displayfd 3 -screen 0 "${screen}x24" -nolisten tcp 3>"$number" </dev/null >"$work/xvfb.log" 2>&1 &
            ;;
        xephyr)
            command -v Xephyr >/dev/null || die "Xephyr is missing"
            [ -n "${DISPLAY:-}" ] || die "Xephyr opens its window on \$DISPLAY, which is not set"
            Xephyr -displayfd 3 -screen "$screen" -resizeable -title "UI tests" 3>"$number" \
                </dev/null >"$work/xephyr.log" 2>&1 &
            ;;
    esac
    local server=$!
    pids+=("$server")
    local waited=0
    until grep -qx '[0-9]\+' "$number" 2>/dev/null; do
        kill -0 "$server" 2>/dev/null || die "$kind did not start; see $work/$kind.log"
        sleep 0.2; waited=$((waited + 1))
        [ "$waited" -lt 50 ] || die "$kind did not start in 10s; see $work/$kind.log"
    done
    display=":$(cat "$number")"
    echo "UI tests on display $display"
    # Without a window manager, nothing places or focuses the windows.
    if command -v openbox >/dev/null; then
        DISPLAY="$display" openbox </dev/null >"$work/openbox.log" 2>&1 &
        pids+=($!)
    fi
}

case "$kind" in
    xvfb | xephyr) start_server ;;
    desktop) display=${DISPLAY:?the desktop is \$DISPLAY, which is not set} ;;
    *) die "UI_TESTS_DISPLAY is xvfb, xephyr or desktop, not $kind" ;;
esac

# Without WAYLAND_DISPLAY, so that an IDE able to run on Wayland opens on the display chosen all the same.
cd "$root"
env -u WAYLAND_DISPLAY DISPLAY="$display" ./gradlew integrationTest "$@"
