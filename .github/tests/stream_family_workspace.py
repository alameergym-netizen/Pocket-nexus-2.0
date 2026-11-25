"""Verify Nexus Horizon's streaming layout family on Android 13: the host, session and network
cards on Home (beside the hero), in the Library header and in Nexus Control, with honest values
(the emulator has no paired PC), controller navigation into and out of the cards, and touch.
"""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("stream-family-ui-results")
HORIZON_ACCENT = "-5715736"  # 0xFFA8C8E8, the Nexus Horizon preset
CARDS = ("horizon_deck_host", "horizon_deck_session", "horizon_deck_network")
NETWORK_VALUES = ("Wi-Fi", "Wired", "Mobile data", "Connected", "No network")


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/stream-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/stream-ui.xml")
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


def focused_card(tree):
    for name in CARDS:
        node = resource(tree, name)
        if node is not None and node.get("focused") == "true":
            return name
    return None


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


def cards_filled(tree):
    nodes = [resource(tree, name) for name in CARDS]
    return all(n is not None and n.get("content-desc") for n in nodes)


def horizon_home(tree):
    return resource(tree, "homeRow") is not None and resource(tree, "horizon_deck") is not None \
        and cards_filled(tree)


def check_honest_cards(tree, where):
    host = resource(tree, "horizon_deck_host").get("content-desc", "")
    session = resource(tree, "horizon_deck_session").get("content-desc", "")
    network = resource(tree, "horizon_deck_network").get("content-desc", "")
    # No PC is paired on the emulator: nothing may be called online or running
    assert "No PC paired" in host, (where, "host card", host)
    assert "Online" not in host and "Offline" not in host, (where, "host card claims a state", host)
    assert "No stream running" in session, (where, "session card", session)
    assert "resume" not in session.casefold(), (where, "session card offers a resume", session)
    value = network.split(": ", 1)[-1]
    assert value.startswith(NETWORK_VALUES), (where, "network card", network)
    print("%s cards: %s | %s | %s" % (where, host, session, network), flush=True)


def card_boxes(tree):
    return [bounds(resource(tree, name)) for name in CARDS]


def check_home(prefix, stacked):
    home = wait_for(prefix + " Horizon Home with its streaming cards", horizon_home)
    check_honest_cards(home, prefix + " Home")
    hero = bounds(resource(home, "pocketHero"))
    row = bounds(resource(home, "homeRow"))
    deck = bounds(resource(home, "horizon_deck"))
    screen = bounds(next(home.iter("node")))
    assert hero[2] <= deck[0], ("cards must sit right of the hero", hero, deck)
    assert row[3] <= deck[1], ("cards must sit below the games row", row, deck)
    assert deck[2] <= screen[2] and deck[3] <= screen[3], ("cards must stay on screen", deck, screen)
    boxes = card_boxes(home)
    if stacked:
        assert all(boxes[i][3] <= boxes[i + 1][1] for i in range(2)), ("stacked cards overlap", boxes)
    else:
        assert all(boxes[i][2] <= boxes[i + 1][0] for i in range(2)), ("side-by-side cards overlap", boxes)
        assert len({b[1] for b in boxes}) == 1, ("side-by-side cards must share a top", boxes)
    density = int(adb("shell", "wm", "density").decode().split()[-1]) / 160
    assert all((b[3] - b[1]) >= 40 * density - 1 for b in boxes), ("cards below a 40 dp target", boxes)
    capture(prefix + "-home")
    return home


def controller_into_cards(prefix):
    # From the games row to the hero buttons, then right past Options onto the cards
    for _ in range(3):
        key("DPAD_DOWN")
        time.sleep(0.4)
    tree = wait_for(prefix + " hero buttons focused", lambda t: focused(t, "Play") or focused(t, "Options")
                    or focused(t, "Details") or focused_card(t) is not None, timeout=15)
    for _ in range(4):
        if focused_card(tree) is not None:
            break
        key("DPAD_RIGHT")
        time.sleep(0.4)
        tree = snap()
    tree = wait_for(prefix + " Host card focused from Options", lambda t: focused_card(t) == "horizon_deck_host",
                    timeout=10)
    return tree


def check_home_controller(prefix, stacked):
    controller_into_cards(prefix)
    step = "DPAD_DOWN" if stacked else "DPAD_RIGHT"
    back = "DPAD_UP" if stacked else "DPAD_LEFT"
    key(step)
    wait_for(prefix + " Session card next", lambda t: focused_card(t) == "horizon_deck_session", timeout=10)
    key(step)
    wait_for(prefix + " Network card last", lambda t: focused_card(t) == "horizon_deck_network", timeout=10)
    key(step)
    time.sleep(0.6)
    assert focused_card(snap()) == "horizon_deck_network", "focus must stay on the last card"
    capture(prefix + "-home-network-focused")
    key(back)
    key(back)
    wait_for(prefix + " back on the Host card", lambda t: focused_card(t) == "horizon_deck_host", timeout=10)
    key("DPAD_LEFT")
    wait_for(prefix + " LEFT leaves the cards for Options", lambda t: focused(t, "Options"), timeout=10)
    key("DPAD_RIGHT")
    wait_for(prefix + " RIGHT returns to the Host card", lambda t: focused_card(t) == "horizon_deck_host", timeout=10)
    key("DPAD_UP")
    wait_for(prefix + " UP leaves the cards for the games",
             lambda t: focused_card(t) is None and any(n.get("focused") == "true" for n in
                                                       resource(t, "homeRow").iter("node")), timeout=10)
    print("PASS: %s controller reaches the cards from Options and leaves them up and left" % prefix, flush=True)


def check_network_touch(prefix):
    home = wait_for(prefix + " Home before the network tap", horizon_home)
    tap(resource(home, "horizon_deck_network"))

    def outside_app(tree):
        # Android's panel (Settings, or System UI's internet dialog on Android 12+) is on top
        packages = {n.get("package") for n in tree.iter("node") if n.get("package")}
        return bool(packages) and PKG not in packages
    try:
        panel = wait_for(prefix + " Android's network panel", outside_app, timeout=15)
        opened = True
        print("Network panel: " + ", ".join(sorted({n.get("package") for n in panel.iter("node")
                                                    if n.get("package")})), flush=True)
    except AssertionError:
        opened = False
    assert opened, "a tap on the Network card must open Android's network panel"
    capture(prefix + "-network-panel")
    key("BACK")
    wait_for(prefix + " Home after the network panel", horizon_home, timeout=20)
    print("PASS: %s a single tap on the Network card opens Android's network panel" % prefix, flush=True)


def open_library(prefix):
    home = wait_for(prefix + " Home Library tile", horizon_home)
    row = resource(home, "homeRow")
    tiles = [n for n in row.iter("node") if n.get("clickable") == "true" and has(n, "Library")]
    assert tiles, "the pinned Library tile is missing from the games row"

    def area(n):
        b = bounds(n)
        return (b[2] - b[0]) * (b[3] - b[1])
    tap(min(tiles, key=area))
    return wait_for(prefix + " Horizon Library", lambda t: resource(t, "horizon_library_grid") is not None
                    and resource(t, "horizon_library_detail") is not None)


def check_library(prefix, expect_cards):
    library = open_library(prefix)
    if not expect_cards:
        time.sleep(1.5)
        library = snap()
        assert resource(library, "horizon_deck") is None, "short screens keep the Library's room for games"
        capture(prefix + "-library")
        print("PASS: %s Library leaves the cards out on a short screen" % prefix, flush=True)
    else:
        library = wait_for(prefix + " Library streaming cards", cards_filled)
        check_honest_cards(library, prefix + " Library")
        deck = bounds(resource(library, "horizon_deck"))
        grid = bounds(resource(library, "horizon_library_grid"))
        assert deck[3] <= grid[1], ("Library cards must sit above the games", deck, grid)
        boxes = card_boxes(library)
        assert all(boxes[i][2] <= boxes[i + 1][0] for i in range(2)), ("Library cards overlap", boxes)
        capture(prefix + "-library")
        key("DPAD_RIGHT")
        time.sleep(0.5)
        tree = snap()
        for _ in range(5):
            if focused_card(tree) is not None:
                break
            key("DPAD_UP")
            time.sleep(0.4)
            tree = snap()
        assert focused_card(tree) is not None, "UP from the games reaches the Library cards"
        key("DPAD_DOWN")
        wait_for(prefix + " DOWN returns to the games", lambda t: focused_card(t) is None and any(
            n.get("focused") == "true" for n in resource(t, "horizon_library_grid").iter("node")), timeout=10)
        print("PASS: %s Library cards above the games, reached and left with the controller" % prefix, flush=True)
    key("BUTTON_B")
    wait_for(prefix + " back to Home", horizon_home)


def check_control(prefix):
    key("F8")
    control = wait_for(prefix + " Horizon Nexus Control", lambda t: has(t, "Nexus Control") and cards_filled(t))
    check_honest_cards(control, prefix + " Control")
    wait_for(prefix + " first tile focused", lambda t: focused(t, "Sleep"))
    deck = bounds(resource(control, "horizon_deck"))
    # The smallest clickable view labelled Sleep is the tile (the panel and the scrim are clickable too)
    sleep_tile = min((n for n in control.iter("node") if has(n, "Sleep") and n.get("clickable") == "true"),
                     key=lambda n: (bounds(n)[2] - bounds(n)[0]) * (bounds(n)[3] - bounds(n)[1]))
    assert deck[3] <= bounds(sleep_tile)[1], ("Control cards must sit above the tiles", deck, bounds(sleep_tile))
    capture(prefix + "-control")
    key("DPAD_UP")
    wait_for(prefix + " UP from the tiles reaches the cards", lambda t: focused_card(t) is not None, timeout=10)
    key("DPAD_RIGHT")
    time.sleep(0.5)
    assert focused_card(snap()) is not None, "RIGHT moves along the Control cards"
    key("DPAD_DOWN")
    wait_for(prefix + " DOWN returns to the first tile", lambda t: focused(t, "Sleep"), timeout=10)
    key("BUTTON_B")
    wait_for(prefix + " Control closes", lambda t: not has(t, "Nexus Control"))
    print("PASS: %s Nexus Control cards above the tiles with controller in and out" % prefix, flush=True)


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
                    "pref\tstring\tpocket_home_layout\tportal\n"
                    "pref\tint\tpocket_accent\t" + HORIZON_ACCENT + "\n"
                    "pref\tstring\tpocket_backdrop\tart\n"
                    "pref\tstring\tpocket_material\tfrost\n"
                    "pref\tstring\tpocket_sound_style\tcrystal\n"
                    "pref\tstring\tpocket_focus_style\tring_zoom\n"
                    "pin\tnexus\tLibrary\tlibrary\t\t\t0\n"
                    "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t501\n"
                    "pin\tpc\tBoreal Drift\temulator-test-pc\t\t\t502\n", encoding="utf-8")
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

    setup_device(320)
    check_home("01-wide", stacked=False)
    check_home_controller("01-wide", stacked=False)
    check_network_touch("01-wide")
    check_library("01-wide", expect_cards=True)
    check_control("01-wide")
    print("PASS: Horizon streaming cards on Home, Library and Control at the handheld size", flush=True)

    setup_device(480, "1.3")
    check_home("02-compact-130", stacked=True)
    check_home_controller("02-compact-130", stacked=True)
    check_library("02-compact-130", expect_cards=False)
    check_control("02-compact-130")
    print("PASS: Horizon streaming cards at a compact size with 130% text", flush=True)


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
