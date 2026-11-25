"""Exercise the Media permission recovery and the Connect entry point on Android 13."""
from pathlib import Path
import subprocess
import sys

import control_workspace as controls
from home_workspace import bounds, resource

controls.RESULTS = Path("media-connect-ui-results")
AUDIO = "android.permission.READ_MEDIA_AUDIO"
ACCESS = "Music access settings"


def node_with_label(tree, label):
    return next((n for n in tree.iter("node") if label in (n.get("text"), n.get("content-desc"))), None)


def tap(node):
    left, top, right, bottom = bounds(node)
    assert left < right and top < bottom
    controls.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def main(apk):
    controls.RESULTS.mkdir(exist_ok=True)
    controls.adb("install", "-g", apk, timeout=180)
    controls.adb("shell", "svc", "power", "stayon", "true")
    controls.adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    controls.setup_device(320)
    controls.adb("shell", "am", "force-stop", controls.PKG)
    controls.adb("shell", "pm", "revoke", controls.PKG, AUDIO)
    controls.adb("shell", "pm", "set-permission-flags", controls.PKG, AUDIO, "user-set", "user-fixed")

    seed_dir = "/sdcard/Android/data/" + controls.PKG + "/files/preview"
    controls.adb("shell", "mkdir", "-p", seed_dir)
    seed = controls.RESULTS / "seed.txt"
    seed.write_text(
        "pref\tbool\tpocket_onboarding_pending\tfalse\n"
        "pref\tbool\tpocket_first_run_done_v1\ttrue\n"
        "pref\tbool\tpocket_animations\tfalse\n"
        "pref\tint\tpocket_music_tab2\t1\n"
        "pref\tstring\tpocket_home_layout\thorizon_workspace\n",
        encoding="utf-8",
    )
    controls.adb("push", str(seed), seed_dir + "/seed.txt")
    controls.launch()
    home = controls.wait_for("Music in Home quick access", lambda t: controls.has(t, "Music"))
    tap(node_with_label(home, "Music"))
    controls.wait_for("Music permission recovery accepts controller focus",
                      lambda t: controls.focused(t, ACCESS))
    controls.capture("01-music-permission-denied")
    controls.key("BUTTON_A")
    controls.wait_for("Android app details for Music access", lambda t:
                      any(n.get("package") == "com.android.settings" for n in t.iter("node")))
    controls.adb("shell", "pm", "clear-permission-flags", controls.PKG, AUDIO, "user-set", "user-fixed")
    controls.adb("shell", "pm", "grant", controls.PKG, AUDIO)
    controls.adb("shell", "input", "keyevent", "KEYCODE_BACK")
    controls.wait_for("Music refreshes after a grant", lambda t: not controls.has(t, ACCESS)
                      and any("No music here yet" in n.get("text", "") for n in t.iter("node")))
    controls.capture("02-music-library")
    controls.key("BUTTON_B")
    controls.wait_for("B returns from Media to Home", lambda t: resource(t, "homeRow") is not None)
    controls.key("BUTTON_R1")
    connect = controls.wait_for("Connect shows the no-host state and setup controls",
                                lambda t: controls.has(t, "Connect to PC")
                                and resource(t, "horizonConnectAdd") is not None
                                and resource(t, "horizonConnectHelp") is not None
                                and resource(t, "horizonConnectRemote") is not None)
    controls.capture("03-connect-empty")
    tap(resource(connect, "horizonConnectRemote"))
    controls.wait_for("Remote Play lists the two official routes", lambda t:
                      controls.has(t, "Xbox Remote Play") and controls.has(t, "PS Remote Play"))
    controls.capture("04-connect-remote-play")
    controls.key("BUTTON_B")
    connect = controls.wait_for("B closes Remote Play and leaves Connect in place", lambda t:
                                not controls.has(t, "Xbox Remote Play")
                                and resource(t, "horizonConnectAdd") is not None)
    tap(resource(connect, "horizonConnectAdd"))
    controls.wait_for("manual host entry", lambda t: resource(t, "hostTextView") is not None)
    controls.capture("05-connect-manual-entry")
    print("PASS: Media permission recovery and Connect controller-to-touch navigation", flush=True)


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    finally:
        controls.RESULTS.mkdir(exist_ok=True)
        for name, args in (
                ("final.png", ("exec-out", "screencap", "-p")),
                ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (controls.RESULTS / name).write_bytes(controls.adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
