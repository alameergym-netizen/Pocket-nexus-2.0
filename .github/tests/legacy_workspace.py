"""Nexus Legacy on a clean Android 13 emulator: its own Home (Quick Access, a square-card
grid and a dialog-box detail panel), Library and Nexus Control, with controller focus, at the
handheld size and at a compact size with 130% text."""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("legacy-ui-results")
LEGACY_ACCENT = "-2045558"  # 0xFFE0C98A


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/legacy-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/legacy-ui.xml")
    (RESULTS / "last-screen.xml").write_bytes(xml)
    return ET.fromstring(xml)


def labels(node):
    return node.get("text", ""), node.get("content-desc", "")


def has(tree, label):
    want = label.casefold()
    return any(want in (t.casefold(), d.casefold()) for t, d in map(labels, tree.iter("node")))


def resource(tree, name):
    return next((n for n in tree.iter("node") if n.get("resource-id", "").endswith(":id/" + name)), None)


def bounds(node):
    assert node is not None, "expected view missing"
    return tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))


def focused(tree, label):
    return any(n.get("focused") == "true" and has(n, label) for n in tree.iter("node"))


def describe(tree):
    if tree is None:
        return "no screen captured"
    seen = []
    for node in tree.iter("node"):
        text, desc = labels(node)
        rid = node.get("resource-id", "").rsplit("/", 1)[-1]
        value = (text or desc or ("#" + rid if rid else "")).strip().replace("\n", " ")
        if value:
            seen.append(("*" if node.get("focused") == "true" else "") + value[:40])
    return " | ".join(seen[:70])


def wait_for(label, predicate, timeout=60):
    deadline = time.monotonic() + timeout
    tree = None
    while time.monotonic() < deadline:
        try:
            tree = snap()
            if predicate(tree):
                return tree
        except (ET.ParseError, subprocess.SubprocessError):
            pass
        time.sleep(0.5)
    print("Screen: " + describe(tree), flush=True)
    raise AssertionError("Did not reach " + label)


def key(code):
    adb("shell", "input", "dpad", "keyevent", "KEYCODE_" + code)


def capture(label):
    (RESULTS / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    print("Captured " + label, flush=True)


def tap(node):
    left, top, right, bottom = bounds(node)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def launch():
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", PKG + "/com.limelight.PcView")


def setup_device(density, font_scale="1.0"):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", str(density))
    adb("shell", "settings", "put", "system", "font_scale", font_scale)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    launch()
    time.sleep(3)


def legacy_home(tree):
    return resource(tree, "homeRow") is not None and resource(tree, "horizon_legacy_kicker") is not None


def check_size(prefix, density, font_scale):
    setup_device(density, font_scale)
    home = wait_for(prefix + " Legacy Home", legacy_home)
    grid, hero = bounds(resource(home, "homeRow")), bounds(resource(home, "pocketHero"))
    assert grid[2] <= hero[0], ("grid must sit left of the dialog panel", grid, hero)
    assert has(home, "Library"), "Quick Access is missing"
    capture(prefix + "-home")

    library_tile = next(n for n in home.iter("node") if n.get("clickable") == "true"
                        and "Library" in (n.get("text"), n.get("content-desc")))
    tap(library_tile)
    library = wait_for(prefix + " Legacy Library", lambda t: resource(t, "horizon_library_grid") is not None
                       and resource(t, "horizon_legacy_kicker") is not None and has(t, "Game index"))
    detail, grid = bounds(resource(library, "horizon_library_detail")), bounds(resource(library, "horizon_library_grid"))
    # Side by side on wide screens, stacked on compact ones: never overlapping
    apart = detail[2] <= grid[0] or grid[2] <= detail[0] or detail[3] <= grid[1] or grid[3] <= detail[1]
    assert apart, ("Library detail and grid overlap", detail, grid)
    capture(prefix + "-library")
    key("BUTTON_B")
    wait_for(prefix + " back to Home", legacy_home)

    key("F8")
    control = wait_for(prefix + " Legacy Nexus Control", lambda t: has(t, "Nexus Control")
                       and resource(t, "horizon_legacy_panel") is not None)
    wait_for(prefix + " first tile focused", lambda t: focused(t, "Sleep"))
    key("DPAD_RIGHT")
    wait_for(prefix + " controller focus moves in Legacy Control", lambda t: focused(t, "Wi-Fi"))
    capture(prefix + "-control")
    key("BUTTON_B")
    wait_for(prefix + " Control closes", lambda t: not has(t, "Nexus Control"))


def churn_package():
    """A system app that can be switched off and on to send package-changed broadcasts."""
    listed = {line.strip()[len("package:"):] for line in
              adb("shell", "pm", "list", "packages").decode("utf-8", "replace").splitlines()
              if line.strip().startswith("package:")}
    for candidate in ("com.android.camera2", "com.android.deskclock", "com.android.calendar",
                      "com.android.contacts", "com.android.gallery3d"):
        if candidate in listed:
            return candidate
    return None


def dock_taps_survive_package_churn():
    """Quick Access must open on the first tap while other apps change (the reported flake:
    every package broadcast used to rebuild every dock button)."""
    pkg = churn_package()
    if pkg is None:
        print("No system app available to toggle; package churn step skipped", flush=True)
        return
    loop = subprocess.Popen(["adb", "shell", "for i in $(seq 1 80); do pm disable-user --user 0 %s >/dev/null 2>&1; "
                             "pm enable %s >/dev/null 2>&1; done" % (pkg, pkg)],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1)
    try:
        for attempt in range(1, 6):
            home = wait_for("Home before dock tap %d" % attempt, legacy_home)
            tile = next(n for n in home.iter("node") if n.get("clickable") == "true"
                        and "Library" in (n.get("text"), n.get("content-desc")))
            tap(tile)
            wait_for("a single tap opens Library during package churn (%d)" % attempt,
                     lambda t: resource(t, "horizon_library_grid") is not None, timeout=20)
            key("BUTTON_B")
        wait_for("Home after the churn", legacy_home)
    finally:
        loop.terminate()
        try:
            loop.wait(timeout=10)
        except subprocess.TimeoutExpired:
            loop.kill()
        adb("shell", "pm", "enable", pkg)
    print("PASS: five single dock taps open Library while %s is switched off and on" % pkg, flush=True)


def main(apk):
    RESULTS.mkdir(exist_ok=True)
    adb("install", "-g", apk, timeout=180)
    adb("shell", "svc", "power", "stayon", "true")
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    setup_device(320)
    adb("shell", "am", "force-stop", PKG)
    seed_dir = "/sdcard/Android/data/" + PKG + "/files/preview"
    adb("shell", "mkdir", "-p", seed_dir)
    seed = RESULTS / "seed.txt"
    seed.write_text("pref\tbool\tpocket_onboarding_pending\tfalse\n"
                    "pref\tbool\tpocket_first_run_done_v1\ttrue\n"
                    "pref\tbool\tpocket_animations\tfalse\n"
                    "pref\tbool\tpocket_gb_enabled\ttrue\n"
                    "pref\tstring\tpocket_gb_back_tap\tcontrol\n"
                    "pref\tstring\tpocket_key_back\tk:138\n"
                    "pref\tstring\tpocket_home_layout\tgrid_covers\n"
                    "pref\tint\tpocket_accent\t" + LEGACY_ACCENT + "\n"
                    "pref\tstring\tpocket_backdrop\tblack\n"
                    "pref\tstring\tpocket_material\tglass\n"
                    "pref\tstring\tpocket_sound_style\tretro\n"
                    "pref\tstring\tpocket_focus_style\tring\n"
                    "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t501\n"
                    "pin\tpc\tBoreal Drift\temulator-test-pc\t\t\t502\n"
                    "pin\tpc\tCinder Quest\temulator-test-pc\t\t\t503\n", encoding="utf-8")
    adb("push", str(seed), seed_dir + "/seed.txt")
    launch()
    deadline = time.monotonic() + 45
    while True:
        try:
            adb("shell", "test", "-f", seed_dir + "/seed.done")
            break
        except subprocess.SubprocessError:
            if time.monotonic() > deadline:
                raise AssertionError("Seed was not applied")
            time.sleep(0.5)
    check_size("01-wide", 320, "1.0")
    print("PASS: Legacy Home, Library and Control at the handheld size", flush=True)
    dock_taps_survive_package_churn()
    check_size("02-compact-130", 480, "1.3")
    print("PASS: Legacy Home, Library and Control at a compact size with 130% text", flush=True)


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    finally:
        RESULTS.mkdir(exist_ok=True)
        for file, args in (("final.png", ("exec-out", "screencap", "-p")),
                           ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (RESULTS / file).write_bytes(adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
        try:
            adb("shell", "settings", "put", "system", "font_scale", "1.0", timeout=8)
        except subprocess.SubprocessError:
            pass
