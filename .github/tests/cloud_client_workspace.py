"""Exercise Nexus cloud client selection with no provider credentials."""
from pathlib import Path
import sys

import control_workspace as controls
from home_workspace import bounds, home_ready, resource

controls.RESULTS = Path("cloud-client-ui-results")
WRAPPER = "Better xCloud for Android"
PACKAGE = "com.redphx.betterxc"


def open_client_choices():
    controls.adb("shell", "am", "force-stop", controls.PKG)
    controls.launch()
    home = controls.wait_for("Home with the Cloud tab", lambda tree: home_ready(tree) and controls.has(tree, "Cloud"))
    controls.tap_label(home, "Cloud")
    controls.wait_for("Xbox Cloud Gaming service", lambda tree: controls.has(tree, "Xbox Cloud Gaming"))
    controls.key("BUTTON_Y")
    options = controls.wait_for("Xbox Cloud Gaming options", lambda tree: controls.has(tree, "Play with"))
    controls.tap_label(options, "Play with")


def main(wrapper_apk):
    controls.RESULTS.mkdir(exist_ok=True)
    open_client_choices()
    controls.wait_for("built-in fallback and optional external client setup", lambda tree:
                      controls.has(tree, "Better xCloud Android setup") and not controls.has(tree, WRAPPER))
    controls.capture("01-cloud-wrapper-unavailable")
    if not wrapper_apk:
        return

    controls.adb("install", "-g", wrapper_apk, timeout=120)
    open_client_choices()
    available = controls.wait_for("verified installed client and its exact reported version", lambda tree:
                                  controls.has(tree, WRAPPER) and controls.has(tree, "0.24.3"))
    controls.capture("02-cloud-wrapper-installed")
    controls.tap_label(available, WRAPPER)
    cloud = controls.wait_for("client preference applied", lambda tree:
                              controls.has(tree, WRAPPER) and resource(tree, "pocketHeroPlay") is not None)
    x1, y1, x2, y2 = bounds(resource(cloud, "pocketHeroPlay"))
    controls.adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    controls.wait_for("Android opened the installed Better xCloud app", lambda tree:
                      any(n.get("package") == PACKAGE for n in tree.iter("node")))
    controls.capture("03-cloud-external-handoff")
    print("PASS: installed upstream client opens through the Nexus preference without sharing credentials", flush=True)


if __name__ == "__main__":
    try:
        main(sys.argv[1] if len(sys.argv) > 1 else None)
    finally:
        controls.capture("final")
