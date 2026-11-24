"""Verify opt-in Home workspace against a disposable Android 13 emulator."""
from pathlib import Path
import re
import subprocess
import sys
import time

import control_workspace as c

c.RESULTS = Path("home-ui-results")

def resource(tree, name):
    return next((n for n in tree.iter("node") if n.get("resource-id", "").endswith(":id/" + name)), None)

def bounds(node):
    assert node is not None, "expected view missing"
    values = tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    assert len(values) == 4
    return values

def focus_label(tree, name):
    return any(n.get("focused") == "true" and n.get("content-desc") == name
               for n in tree.iter("node"))

def home_ready(tree):
    return resource(tree, "homeRow") is not None and c.has(tree, "Add to Home")

def main(apk):
    c.RESULTS.mkdir(exist_ok=True)
    c.adb("install", "-g", apk, timeout=180)
    c.adb("shell", "svc", "power", "stayon", "true")
    c.adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    c.setup_device(320)
    c.adb("shell", "am", "force-stop", c.PKG)
    seed_dir = "/sdcard/Android/data/" + c.PKG + "/files/preview"
    c.adb("shell", "mkdir", "-p", seed_dir)
    seed = c.RESULTS / "seed.txt"
    seed.write_text("pref\tbool\tpocket_onboarding_pending\tfalse\n"
                    "pref\tbool\tpocket_first_run_done_v1\ttrue\n"
                    "pref\tstring\tpocket_home_layout\thorizon_workspace\n"
                    "pin\tnexus\tMusic\tmusic\t\t\t0\n"
                    "pin\tnexus\tFiles\tfiles\t\t\t0\n", encoding="utf-8")
    c.adb("push", str(seed), seed_dir + "/seed.txt")
    c.launch()
    tree = c.wait_for("Home with pinned tools and Add tile", home_ready)
    assert c.has(tree, "Music") and c.has(tree, "Files"), "seeded pins not visible"
    grid = bounds(resource(tree, "homeRow"))
    # These regions must be distinct; the grid cannot run into the bottom tool strip.
    play = bounds(resource(tree, "pocketHeroPlay"))
    assert grid[2] <= play[0], (grid, play)
    assert c.has(tree, "Quick Access"), "persistent tool strip missing"
    c.capture("01-workspace")
    try:
        c.wait_for("initial Music tile", lambda t: focus_label(t, "Music"))
    except AssertionError:
        tree = c.snap()
        print("Focused nodes:", [(n.get("resource-id"), n.get("text"), n.get("content-desc"))
                                 for n in tree.iter("node") if n.get("focused") == "true"], flush=True)
        raise
    c.key("DPAD_RIGHT")
    c.wait_for("Files tile", lambda t: focus_label(t, "Files"))
    c.key("DPAD_DOWN")
    c.wait_for("dock Library focus", lambda t: focus_label(t, "Library"))
    c.capture("02-dock-focus")
    c.key("DPAD_UP")
    c.wait_for("return to Files", lambda t: focus_label(t, "Files"))
    c.key("DPAD_RIGHT")
    c.wait_for("Add tile", lambda t: focus_label(t, "Add to Home"))
    c.key("DPAD_RIGHT")
    c.wait_for("detail action focus", lambda t: resource(t, "pocketHeroPlay") is not None
               and resource(t, "pocketHeroPlay").get("focused") == "true")
    c.capture("03-detail-focus")
    print("PASS: independent regions, grid-to-dock and detail focus", flush=True)
    c.key("BUTTON_SELECT")
    # Layout chip is transient (1.5s); UIAutomator waits for idle and can miss it.
    # Verify the actual selected layout and persisted preference instead of a toast-like chip.
    def legacy_geometry(tree):
        grid_node = resource(tree, "homeRow")
        detail_node = resource(tree, "pocketHeroPlay")
        if grid_node is None or detail_node is None or not home_ready(tree):
            return False
        shelf, detail = bounds(grid_node), bounds(detail_node)
        return shelf[1] >= detail[3] and c.has(tree, "Music") and c.has(tree, "Files")

    tree = c.wait_for("legacy Tiles geometry and retained pins", legacy_geometry)
    prefs = c.adb("shell", "run-as", c.PKG, "cat",
                  "shared_prefs/" + c.PKG + "_preferences.xml").decode("utf-8")
    assert re.search(r'<string name="pocket_home_layout">tiles</string>', prefs), "Tiles preference not persisted"
    assert c.has(tree, "Music") and c.has(tree, "Files")
    c.adb("shell", "am", "force-stop", c.PKG)
    c.launch()
    c.wait_for("legacy Tiles survives process restart", legacy_geometry)
    c.capture("04-legacy-tiles")
    print("PASS: legacy layout remains available and retains seeded pins", flush=True)

if __name__ == "__main__":
    try:
        main(sys.argv[1])
    finally:
        c.RESULTS.mkdir(exist_ok=True)
        for file, args in (("final.png", ("exec-out", "screencap", "-p")),
                           ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (c.RESULTS / file).write_bytes(c.adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
