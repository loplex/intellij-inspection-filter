#!/usr/bin/env bash
# Runs the sandbox IDE of `./gradlew runIde` on a virtual X display, to check the plugin by hand there - see
# docs/development.md.
#
#   scripts/sandbox-on-xvfb.sh start PROJECT_DIR   starts the display and the IDE with PROJECT_DIR open
#   scripts/sandbox-on-xvfb.sh shot NAME [GEOMETRY] saves a screenshot, cropped to GEOMETRY (such as 800x600+0+0)
#   scripts/sandbox-on-xvfb.sh stop                closes the IDE as File | Exit does, then the display
#
# XVFB_DISPLAY picks the display number (99 by default). Logs, screenshots and process ids go to build/manual-test.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
work="$root/build/manual-test"
display=":${XVFB_DISPLAY:-99}"
export DISPLAY="$display"

die() { echo "$*" >&2; exit 1; }

# The id of the IDE's main window: the one whose title names the open project.
main_window() { wmctrl -l 2>/dev/null | awk -v name="$1" 'index($0, name) { print $1; exit }'; }

start() {
    local project
    project=$(cd "${1:?a project directory to open}" && pwd)
    for tool in Xvfb xdpyinfo xdotool wmctrl import convert; do
        command -v "$tool" >/dev/null || die "$tool is missing"
    done
    # The sandbox has one configuration directory, which a second IDE would fight over.
    if pgrep -f '[c]onfig_runIde' >/dev/null; then
        die "a sandbox IDE is already running; close it first"
    fi
    if xdpyinfo >/dev/null 2>&1; then
        die "display $display is taken; pick another with XVFB_DISPLAY"
    fi
    mkdir -p "$work/shots"

    Xvfb "$display" -screen 0 1920x1080x24 -nolisten tcp </dev/null >"$work/xvfb.log" 2>&1 &
    echo $! >"$work/xvfb.pid"
    sleep 1
    xdpyinfo >/dev/null 2>&1 || die "Xvfb did not start on $display; see $work/xvfb.log"
    # Without a window manager, nothing places or focuses the windows.
    if command -v openbox >/dev/null; then
        openbox </dev/null >"$work/openbox.log" 2>&1 &
        echo $! >"$work/openbox.pid"
    fi

    # Without WAYLAND_DISPLAY, so that an IDE able to run on Wayland opens on the virtual display all the same.
    # All of its output to the log, or a caller reading this script's output would wait for the IDE to exit.
    (cd "$root" && env -u WAYLAND_DISPLAY ./gradlew runIde --args="$project") </dev/null >"$work/runIde.log" 2>&1 &
    echo $! >"$work/runIde.pid"
    basename "$project" >"$work/project-name"

    local name window="" waited=0
    name=$(basename "$project")
    while [ -z "$window" ] && [ "$waited" -lt 300 ]; do
        sleep 2; waited=$((waited + 2))
        kill -0 "$(cat "$work/runIde.pid")" 2>/dev/null || die "runIde ended; see $work/runIde.log"
        window=$(main_window "$name")
    done
    if [ -n "$window" ]; then
        wmctrl -i -r "$window" -b add,maximized_vert,maximized_horz
        echo "IDE is up on $display after ${waited}s."
    else
        echo "No window named after $name after ${waited}s; a dialog may be waiting - take a shot."
    fi
    wmctrl -l
    echo "Run: export DISPLAY=$display"
}

shot() {
    local file="$work/shots/${1:?a name for the screenshot}.png"
    mkdir -p "$work/shots"
    import -window root "$file"
    if [ -n "${2:-}" ]; then
        convert "$file" -crop "$2" +repage "$file"
    fi
    echo "$file"
}

stop() {
    local name window waited=0
    if [ -f "$work/project-name" ]; then
        name=$(cat "$work/project-name")
        window=$(main_window "$name")
        if [ -n "$window" ]; then
            wmctrl -i -c "$window"
            sleep 2
            # Asked only when the IDE is set to confirm before exiting.
            if wmctrl -a 'Confirm Exit' 2>/dev/null; then
                sleep 0.5
                xdotool key Return
            fi
        fi
    fi
    if [ -f "$work/runIde.pid" ]; then
        while kill -0 "$(cat "$work/runIde.pid")" 2>/dev/null && [ "$waited" -lt 60 ]; do
            sleep 1; waited=$((waited + 1))
        done
        kill -0 "$(cat "$work/runIde.pid")" 2>/dev/null && die "the IDE has not exited after ${waited}s; close it and run stop again"
    fi
    for process in openbox xvfb; do
        if [ -f "$work/$process.pid" ]; then
            kill "$(cat "$work/$process.pid")" 2>/dev/null || true
            rm -f "$work/$process.pid"
        fi
    done
    rm -f "$work/runIde.pid" "$work/project-name"
    echo "Stopped."
}

case "${1:-}" in
    start) shift; start "$@" ;;
    shot) shift; shot "$@" ;;
    stop) stop ;;
    *) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
