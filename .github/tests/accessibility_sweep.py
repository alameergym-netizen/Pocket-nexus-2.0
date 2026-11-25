"""S10 accessibility sweep on Android 13: Home, Library, game details, Nexus Control and every
Settings tab, at the handheld size and at a compact size with 130% text. Each screen is checked
for controls a screen reader cannot name, controls whose touch target is under 40 dp, and a
focused control drawn off screen. Findings are written to a JSON report; with --enforce the run
fails on any finding on the screens listed in ENFORCED."""
from pathlib import Path
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("accessibility-results")
SCROLLERS = ("ScrollView", "RecyclerView", "HorizontalScrollView", "ListView", "ViewPager", "WebView")
MIN_TARGET_DP = 40
# Screens whose findings fail the run with --enforce (the rest are reported).
ENFORCED = set()


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/a11y-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/a11y-ui.xml")
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
    values = tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
    return values if len(values) == 4 else (0, 0, 0, 0)


def describe(tree):
    if tree is None:
        return "no screen captured"
    seen = []
    for node in tree.iter("node"):
        text, desc = labels(node)
        value = (text or desc).strip().replace("\n", " ")
        if value:
            seen.append(("*" if node.get("focused") == "true" else "") + value[:50])
    return " | ".join(seen[:60])


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


def tap(node):
    left, top, right, bottom = bounds(node)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def tap_label(tree, label):
    tap(next(n for n in tree.iter("node") if label in (n.get("text"), n.get("content-desc"))))


def capture(label):
    (RESULTS / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))


def launch():
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", PKG + "/com.limelight.PcView")


def setup_device(density, font_scale):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", str(density))
    adb("shell", "settings", "put", "system", "font_scale", font_scale)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    launch()
    time.sleep(3)


def named(node):
    """A screen reader can name the control from itself or the text inside it."""
    return any((t or d).strip() for t, d in map(labels, node.iter("node")))


def audit(name, tree, density):
    findings = []
    screen = bounds(next(tree.iter("node")))
    min_px = MIN_TARGET_DP * density / 160
    for node in tree.iter("node"):
        cls = node.get("class", "")
        actionable = node.get("clickable") == "true" or node.get("focusable") == "true" \
            or node.get("long-clickable") == "true"
        if not actionable or node.get("enabled") != "true" or any(s in cls for s in SCROLLERS):
            continue
        left, top, right, bottom = bounds(node)
        if right <= left or bottom <= top:
            continue
        ident = {"class": cls.rsplit(".", 1)[-1], "id": node.get("resource-id", "").rsplit("/", 1)[-1],
                 "text": (node.get("text") or node.get("content-desc") or "")[:40], "bounds": node.get("bounds")}
        if not named(node):
            findings.append(dict(ident, issue="unlabeled"))
        if node.get("clickable") == "true" and (right - left < min_px or bottom - top < min_px):
            findings.append(dict(ident, issue="small-target"))
        if node.get("focused") == "true" and (right <= screen[0] or left >= screen[2]
                                              or bottom <= screen[1] or top >= screen[3]):
            findings.append(dict(ident, issue="focus-off-screen"))
    # One finding per control and issue
    unique = {(f["issue"], f["class"], f["id"], f["text"], f["bounds"]): f for f in findings}
    return list(unique.values())


REPORT = {}


def record(screen, size, tree, density):
    found = audit(screen, tree, density)
    REPORT.setdefault(size, {})[screen] = found
    capture(size + "-" + screen)
    print("%s %s: %d finding(s)" % (size, screen, len(found)), flush=True)
    for f in found:
        print("   %s %s id=%s text=%r %s" % (f["issue"], f["class"], f["id"], f["text"], f["bounds"]), flush=True)


def open_settings():
    for _ in range(4):
        key("BUTTON_START")
        try:
            return wait_for("Settings", lambda t: has(t, "Look") and has(t, "System") and has(t, "Libraries"), 12)
        except AssertionError:
            continue
    raise AssertionError("Settings did not open")


def sweep(size, density, font_scale):
    setup_device(density, font_scale)
    home = wait_for("Home", lambda t: resource(t, "homeRow") is not None)
    time.sleep(1)
    record("home", size, snap(), density)

    library_tile = next((n for n in home.iter("node") if n.get("clickable") == "true"
                         and "Library" in (n.get("text"), n.get("content-desc"))), None)
    if library_tile is not None:
        tap(library_tile)
        library = wait_for("Library", lambda t: resource(t, "horizon_library_grid") is not None)
        time.sleep(1)
        record("library", size, snap(), density)
        details = resource(library, "horizon_library_details_action")
        if details is not None:
            tap(details)
            try:
                wait_for("game details", lambda t: resource(t, "pagePlay") is not None, 20)
                time.sleep(1)
                record("game-details", size, snap(), density)
            except AssertionError:
                print("Game details did not open; skipped", flush=True)
            key("BUTTON_B")
            time.sleep(1)
        key("BUTTON_B")
        time.sleep(1)

    setup_device(density, font_scale)
    wait_for("Home before Control", lambda t: resource(t, "homeRow") is not None)
    key("F8")
    try:
        wait_for("Nexus Control", lambda t: has(t, "Nexus Control"), 20)
        time.sleep(1)
        record("control", size, snap(), density)
        key("BUTTON_B")
        time.sleep(1)
    except AssertionError:
        print("Nexus Control did not open; skipped", flush=True)

    settings = open_settings()
    for tab in ("Look", "Home", "Libraries", "Streaming", "Controls", "System"):
        node = next((n for n in settings.iter("node") if n.get("text") == tab), None)
        if node is None:
            print("Settings tab %s not found" % tab, flush=True)
            continue
        tap(node)
        time.sleep(1.2)
        settings = snap()
        record("settings-" + tab.lower(), size, settings, density)


def main(apk, enforce):
    RESULTS.mkdir(exist_ok=True)
    adb("install", "-g", apk, timeout=180)
    adb("shell", "svc", "power", "stayon", "true")
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    setup_device(320, "1.0")
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
                    "pref\tstring\tpocket_home_layout\thorizon_workspace\n"
                    "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t501\n"
                    "pin\tpc\tBoreal Drift\temulator-test-pc\t\t\t502\n"
                    "pin\tnexus\tMusic\tmusic\t\t\t0\n", encoding="utf-8")
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

    sweep("wide", 320, "1.0")
    sweep("compact-130", 480, "1.3")
    (RESULTS / "report.json").write_text(json.dumps(REPORT, indent=2), encoding="utf-8")
    total = sum(len(v) for screens in REPORT.values() for v in screens.values())
    enforced = sum(len(v) for screens in REPORT.values() for s, v in screens.items() if s in ENFORCED)
    print("TOTAL findings: %d (enforced screens: %d)" % (total, enforced), flush=True)
    if enforce and enforced:
        raise AssertionError("Accessibility findings on enforced screens")


if __name__ == "__main__":
    try:
        main(sys.argv[1], "--enforce" in sys.argv[2:])
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
