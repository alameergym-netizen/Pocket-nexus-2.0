"""Verify Nexus Signature's cinematic layout on Android 13: letterbox bars framing the art on
Home (the games on the bottom bar like a credits roll, an accent rule over the title), a
widescreen still in the Library and a black slate for Nexus Control, at two sizes.
"""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("signature-ui-results")
SIGNATURE_ACCENT = "-855310"  # 0xFFF2F2F2, the Nexus Signature preset
SEED_DIR = "/sdcard/Android/data/" + PKG + "/files/preview"


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/signature-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/signature-ui.xml")
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
            seen.append(("*" if node.get("focused") == "true" else "") + value[:60])
    return " | ".join(seen[:80])


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


def dp():
    return int(adb("shell", "wm", "density").decode().split()[-1]) / 160


def seed(lines):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "mkdir", "-p", SEED_DIR)
    adb("shell", "rm", "-f", SEED_DIR + "/seed.done")
    path = RESULTS / "seed.txt"
    path.write_text("".join(line + "\n" for line in lines), encoding="utf-8")
    adb("push", str(path), SEED_DIR + "/seed.txt")
    launch()
    deadline = time.monotonic() + 45
    while True:
        try:
            adb("shell", "test", "-f", SEED_DIR + "/seed.done")
            return
        except subprocess.SubprocessError:
            if time.monotonic() > deadline:
                raise AssertionError("Seed was not applied")
            time.sleep(0.5)


def signature_home(tree):
    return resource(tree, "homeRow") is not None and resource(tree, "pocketHero") is not None \
        and resource(tree, "horizon_signature_rule") is not None


def check_home(prefix, framed):
    home = wait_for(prefix + " Signature Home", signature_home)
    screen = bounds(next(home.iter("node")))
    row = bounds(resource(home, "homeRow"))
    hero = bounds(resource(home, "pocketHero"))
    rule = bounds(resource(home, "horizon_signature_rule"))
    unit = dp()
    assert hero[0] <= rule[0] and rule[2] <= hero[2] and rule[1] >= hero[1], ("the rule marks the hero", rule, hero)
    top, bottom = resource(home, "horizon_signature_bar_top"), resource(home, "horizon_signature_bar_bottom")
    if framed:
        assert top is not None and bottom is not None, "a tall screen is framed by both bars"
        top, bottom = bounds(top), bounds(bottom)
        assert top[1] <= screen[1] + 1 and top[2] - top[0] >= screen[2] - screen[0] - 1, ("top bar", top, screen)
        assert bottom[3] >= screen[3] - 1 and bottom[1] <= row[1], ("the games sit on the bottom bar", bottom, row)
        assert hero[3] <= bottom[1] + 18 * unit + 1, ("the hero stands in the picture", hero, bottom)
        assert bottom[1] - top[3] >= 100 * unit, ("a band of art between the bars", top, bottom)
    else:
        assert top is None and bottom is None, "a short screen keeps its picture: no bars"
    capture(prefix + "-home")
    print("PASS: %s Signature Home%s with the accent rule" % (prefix, " framed by letterbox bars" if framed else ""),
          flush=True)


def check_library(prefix, still):
    home = wait_for(prefix + " Home Library", lambda t: signature_home(t) and has(t, "Library"))
    tile = next(n for n in home.iter("node") if n.get("clickable") == "true"
                and "Library" in (n.get("text"), n.get("content-desc")))
    tap(tile)
    library = wait_for(prefix + " Signature Library", lambda t: resource(t, "horizon_library_grid") is not None
                       and resource(t, "horizon_signature_bar_top") is not None)
    screen = bounds(next(library.iter("node")))
    top, bottom = bounds(resource(library, "horizon_signature_bar_top")), \
        bounds(resource(library, "horizon_signature_bar_bottom"))
    assert top[1] <= screen[1] + 1 and bottom[3] >= screen[3] - 1, ("Library letterbox bars", top, bottom)
    picture = resource(library, "horizon_signature_still")
    if still:
        assert picture is not None, "the wide Library shows the game as a widescreen still"
        b = bounds(picture)
        aspect = (b[2] - b[0]) / max(1, b[3] - b[1])
        assert 2.2 <= aspect <= 2.6, ("the still is widescreen", b, aspect)
    capture(prefix + "-library")
    key("BUTTON_B")
    wait_for(prefix + " back to Home", signature_home)
    print("PASS: %s Signature Library framed%s" % (prefix, ", with a widescreen still" if still else ""), flush=True)


def check_control(prefix):
    key("F8")
    wait_for(prefix + " Signature Nexus Control", lambda t: has(t, "Nexus Control")
             and resource(t, "horizon_signature_panel") is not None
             and resource(t, "horizon_signature_bar_top") is not None)
    wait_for(prefix + " first tile focused", lambda t: focused(t, "Sleep"))
    key("DPAD_RIGHT")
    wait_for(prefix + " controller moves on the slate", lambda t: focused(t, "Wi-Fi"))
    capture(prefix + "-control")
    key("BUTTON_B")
    wait_for(prefix + " Control closes", lambda t: not has(t, "Nexus Control"))
    print("PASS: %s Signature Nexus Control slate with controller focus" % prefix, flush=True)


def main(apk):
    RESULTS.mkdir(exist_ok=True)
    adb("install", "-g", apk, timeout=180)
    adb("shell", "svc", "power", "stayon", "true")
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    setup_device(320)
    seed(["pref\tbool\tpocket_onboarding_pending\tfalse",
          "pref\tbool\tpocket_first_run_done_v1\ttrue",
          "pref\tbool\tpocket_animations\tfalse",
          "pref\tbool\tpocket_gb_enabled\ttrue",
          "pref\tstring\tpocket_gb_back_tap\tcontrol",
          "pref\tstring\tpocket_key_back\tk:138",
          "pref\tstring\tpocket_home_layout\ttiles",
          "pref\tint\tpocket_accent\t" + SIGNATURE_ACCENT,
          "pref\tstring\tpocket_backdrop\tart",
          "pref\tstring\tpocket_material\tglass",
          "pref\tstring\tpocket_sound_style\tcrisp",
          "pref\tstring\tpocket_focus_style\tring_zoom",
          "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t501",
          "pin\tpc\tBoreal Drift\temulator-test-pc\t\t\t502",
          "pin\tpc\tCinder Quest\temulator-test-pc\t\t\t503"])

    setup_device(320)
    check_home("01-wide", framed=True)
    check_library("01-wide", still=True)
    check_control("01-wide")

    setup_device(480, "1.3")
    check_home("02-compact-130", framed=False)
    check_library("02-compact-130", still=False)
    check_control("02-compact-130")
    print("PASS: Signature's cinematic frame on Home, Library and Control at two sizes", flush=True)


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
