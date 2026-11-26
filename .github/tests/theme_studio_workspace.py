"""Theme Studio on a clean Android 13 emulator: focus previews without changing settings, A then
Apply applies, B closes without applying, the previous look comes back, custom slots save and
clear, copied looks paste back, and the compact layout with larger text keeps every action."""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("theme-studio-results")
PREFS = "shared_prefs/" + PKG + "_preferences.xml"
SIGNATURE_ACCENT = 'name="pocket_accent" value="-855310"'  # 0xFFF2F2F2
WHITE_ACCENT = 'name="pocket_accent" value="-1"'


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/studio-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/studio-ui.xml")
    (RESULTS / "last-screen.xml").write_bytes(xml)
    return ET.fromstring(xml)


def labels(node):
    return node.get("text", ""), node.get("content-desc", "")


def has(tree, label):
    want = label.casefold()
    return any(want in (text.casefold(), desc.casefold()) for text, desc in map(labels, tree.iter("node")))


def contains(tree, fragment):
    want = fragment.casefold()
    return any(want in text.casefold() or want in desc.casefold() for text, desc in map(labels, tree.iter("node")))


def focused(tree, label):
    return any(n.get("focused") == "true" and has(n, label) for n in tree.iter("node"))


def describe(tree):
    """What was on screen: visible labels and the focused one, for the CI log."""
    if tree is None:
        return "no screen captured"
    seen = []
    for node in tree.iter("node"):
        text, desc = labels(node)
        value = (text or desc).strip().replace("\n", " ")
        if value:
            seen.append(("*" if node.get("focused") == "true" else "") + value[:60])
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


def prefs():
    return adb("shell", "run-as", PKG, "cat", PREFS).decode("utf-8", "replace")


def wait_prefs(label, predicate, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        current = prefs()
        if predicate(current):
            return current
        time.sleep(0.4)
    (RESULTS / "prefs.xml").write_text(prefs(), encoding="utf-8")
    raise AssertionError("Preferences did not show " + label)


def key(code):
    adb("shell", "input", "dpad", "keyevent", "KEYCODE_" + code)


def capture(label):
    (RESULTS / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    print("Captured " + label, flush=True)


def tap_label(tree, label):
    node = next(n for n in tree.iter("node") if label in (n.get("text"), n.get("content-desc")))
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
    assert left < right and top < bottom
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


def studio(tree):
    # The actions stay pinned on screen; the list may have scrolled past its first header
    return has(tree, "Apply look") and has(tree, "Close without applying")


def open_studio():
    """Settings > Look > Theme Studio, with the controller (the primary path)."""
    settings = None
    for _ in range(4):
        key("BUTTON_START")
        try:
            settings = wait_for("Look settings with Theme Studio",
                                lambda t: has(t, "Theme Studio") and not studio(t), timeout=12)
            break
        except AssertionError:
            continue
    assert settings is not None, "Settings did not open from Home"
    return enter_from_settings()


def enter_from_settings():
    """From the Look tab or its rows: focus the Theme Studio row and press A."""
    if not focused(snap(), "Theme Studio"):
        key("DPAD_RIGHT")
        move_focus_to("Theme Studio", "DPAD_UP", limit=8)
    key("BUTTON_A")
    return wait_for("Theme Studio", studio)


def back_in_settings(tree):
    # Applying a look rebuilds Settings (focus on its Look tab); otherwise the row keeps focus
    return not studio(tree) and has(tree, "Theme Studio") and (focused(tree, "Theme Studio")
                                                               or focused(tree, "Look"))


def move_focus_to(label, direction, limit=40):
    for _ in range(limit):
        tree = snap()
        if focused(tree, label):
            return tree
        key(direction)
        time.sleep(0.25)
    raise AssertionError("Could not move the focus to " + label)


def visit_scenes(prefix):
    key("BUTTON_R1")
    wait_for(prefix + " Library scene", lambda t: contains(t, "Library preview of"))
    capture(prefix + "-library")
    key("BUTTON_R1")
    wait_for(prefix + " Control scene", lambda t: contains(t, "Control preview of"))
    capture(prefix + "-control")
    key("BUTTON_R1")
    wait_for(prefix + " Home scene again", lambda t: contains(t, "Home preview of"))


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
                    "pref\tbool\tpocket_first_run_done_v1\ttrue\n", encoding="utf-8")
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

    # Wide handheld layout: the applied look is focused and shown
    setup_device(320)
    open_studio()
    wait_for("applied Nexus look focused", lambda t: focused(t, "Nexus") and has(t, "Showing the applied look"))
    capture("01-wide-applied")
    assert SIGNATURE_ACCENT not in prefs()

    # Focus previews without changing anything
    move_focus_to("Nexus Signature", "DPAD_UP")
    wait_for("focus previews Signature", lambda t: has(t, "Previewing Nexus Signature")
             and contains(t, "Home preview of Nexus Signature")
             and contains(t, "Brings its own Home, Library and Control layouts"))
    capture("02-signature-home")
    visit_scenes("03-signature")
    assert SIGNATURE_ACCENT not in prefs(), "a preview changed the saved look"
    print("PASS: focus and scene changes preview without saving", flush=True)

    # A takes the focus to Apply; A again applies and remembers the previous look
    key("BUTTON_A")
    wait_for("A moves to Apply", lambda t: focused(t, "Apply look"))
    key("BUTTON_A")
    wait_prefs("Signature applied with an undo snapshot", lambda x: SIGNATURE_ACCENT in x
               and '<string name="pocket_sound_style">crisp</string>' in x and "horizon_look_undo" in x)
    wait_for("studio shows the applied Signature", lambda t: has(t, "Showing the applied look")
             and focused(t, "Nexus Signature"))
    capture("04-signature-applied")
    print("PASS: A then Apply writes the look and keeps the previous one", flush=True)

    # Horizon and Atmosphere preview their own layout families (streaming cards, layered glass)
    for name, label in (("Nexus Horizon", "04a-horizon"), ("Nexus Atmosphere", "04b-atmosphere")):
        move_focus_to(name, "DPAD_DOWN")
        wait_for(name + " preview", lambda t, n=name: has(t, "Previewing " + n)
                 and contains(t, "Brings its own Home, Library and Control layouts"))
        capture(label + "-home")
        visit_scenes(label)
    print("PASS: Signature, Horizon and Atmosphere preview their own layouts", flush=True)

    # Pure OLED and Titanium preview their own layouts; B leaves without applying
    move_focus_to("Nexus Pure OLED", "DPAD_DOWN")
    wait_for("Pure OLED preview", lambda t: has(t, "Previewing Nexus Pure OLED")
             and contains(t, "Brings its own Home, Library and Control layouts"))
    capture("05-pure-oled-home")
    visit_scenes("06-pure-oled")
    move_focus_to("Nexus Titanium", "DPAD_DOWN")
    wait_for("Titanium preview", lambda t: has(t, "Previewing Nexus Titanium"))
    capture("07-titanium-home")
    visit_scenes("08-titanium")
    move_focus_to("Nexus Legacy", "DPAD_UP")
    wait_for("Legacy preview", lambda t: has(t, "Previewing Nexus Legacy"))
    capture("09-legacy-home")
    key("BUTTON_B")
    wait_for("B returns to Settings on the Look tab", back_in_settings)
    current = prefs()
    assert SIGNATURE_ACCENT in current, "closing the studio changed the look"
    assert "grid_tiles" not in current and '<string name="pocket_backdrop">black</string>' not in current
    print("PASS: B closes without applying the preview", flush=True)

    # The look before the last apply comes back
    enter_from_settings()
    wait_for("studio reopens on Signature", lambda t: studio(t) and focused(t, "Nexus Signature"))
    move_focus_to("Look before the last apply", "DPAD_DOWN", limit=60)
    wait_for("previous look preview", lambda t: has(t, "Previewing Look before the last apply"))
    key("BUTTON_A")
    wait_for("Apply focused for undo", lambda t: focused(t, "Apply look"))
    key("BUTTON_A")
    wait_prefs("the previous Nexus look restored", lambda x: WHITE_ACCENT in x
               and '<string name="pocket_sound_style">aurora</string>' in x)
    capture("10-undo-applied")
    print("PASS: the previous look is restored", flush=True)

    # Custom slots: save, then a two-press clear
    move_focus_to("Save preview as custom look 1", "DPAD_UP")
    key("BUTTON_A")
    wait_prefs("custom look 1 saved", lambda x: "horizon_look_slot_0" in x)
    wait_for("slot actions refresh", lambda t: has(t, "Replace custom look 1 with the preview")
             and has(t, "Clear custom look 1"))
    capture("11-slot-saved")
    move_focus_to("Clear custom look 1", "DPAD_DOWN")
    key("BUTTON_A")
    wait_for("clearing asks for a second press", lambda t: focused(t, "Press again to clear custom look 1"))
    assert "horizon_look_slot_0" in prefs(), "one press cleared the slot"
    key("BUTTON_A")
    wait_prefs("custom look 1 cleared", lambda x: "horizon_look_slot_0" not in x)
    print("PASS: custom look save and confirmed clear", flush=True)

    # Copy the previewed look as text, paste it back, then close without applying
    move_focus_to("Copy preview as text", "DPAD_DOWN", limit=60)
    key("BUTTON_A")
    move_focus_to("Paste look from clipboard", "DPAD_DOWN")
    key("BUTTON_A")
    wait_for("pasted look previews", lambda t: has(t, "Previewing Pasted look") and focused(t, "Apply look"))
    capture("12-pasted-look")
    key("BUTTON_B")
    wait_for("B closes after paste", back_in_settings)
    assert WHITE_ACCENT in prefs(), "pasting applied a look without Apply"
    print("PASS: copy and paste preview a look without applying it", flush=True)

    # T09: the Look tab's Typeface: the next option switches to the device's font, is kept, and
    # Settings rebuilds with it; two more steps come back to Inter
    nxt = "Next option for Typeface"
    for _ in range(12):
        tree = snap()
        if has(tree, nxt):
            break
        adb("shell", "input", "swipe", "540", "900", "540", "420", "250")
        time.sleep(0.6)
    tap_label(wait_for("the Typeface choice", lambda t: has(t, nxt), timeout=10), nxt)
    wait_prefs("the device font chosen", lambda p: 'name="pocket_typeface">system<' in p)
    wait_for("Settings shows the device font", lambda t: has(t, "Device font"), timeout=15)
    capture("12b-typeface-device-font")
    for expected in ("condensed", "inter"):
        for _ in range(12):
            tree = snap()
            if has(tree, nxt):
                break
            adb("shell", "input", "swipe", "540", "900", "540", "420", "250")
            time.sleep(0.6)
        tap_label(wait_for("the Typeface choice again", lambda t: has(t, nxt), timeout=10), nxt)
        wait_prefs("typeface " + expected, lambda p, e=expected: 'name="pocket_typeface">' + e + '<' in p)
    print("PASS: the Look tab switches the typeface, keeps it and returns to Inter", flush=True)

    # Compact handheld with larger text: list, preview and both actions stay reachable
    setup_device(480, "1.3")
    open_studio()
    wait_for("compact studio focus", lambda t: focused(t, "Nexus") and contains(t, "Home preview of Nexus."))
    capture("13-compact-large-text")
    key("DPAD_RIGHT")
    wait_for("actions reachable beside the list", lambda t: focused(t, "Apply look")
             or focused(t, "Close without applying"))
    capture("14-compact-actions")
    key("BUTTON_B")
    wait_for("compact studio closes", lambda t: not studio(t))
    print("PASS: compact layout with 130% text keeps the list, preview and actions", flush=True)


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
