"""Verify touch and controller Library navigation on Android 13."""
from pathlib import Path
import subprocess
import sys

import control_workspace as controls
from home_workspace import bounds, resource

controls.RESULTS = Path("library-ui-results")
DETAIL = "horizon_library_detail"
GRID = "horizon_library_grid"
DETAILS = "horizon_library_details_action"
OPTIONS = "horizon_library_options_action"
FIRST = "Aster Circuit"
SECOND = "Boreal Drift"
COLLECTION = "Weekend"


def node_with_label(tree, label):
    return next(
        (node for node in tree.iter("node") if node.get("clickable") == "true"
         and label in (node.get("text"), node.get("content-desc"))),
        None,
    )


def tap(node):
    left, top, right, bottom = bounds(node)
    assert right > left and bottom > top
    controls.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def selected(tree, label):
    node = node_with_label(tree, label)
    return node is not None and node.get("selected") == "true"


def game_has_focus(tree, label):
    return any(
        node.get("focused") == "true" and node.get("content-desc") == label
        for node in tree.iter("node")
    )


def library_with_games(tree):
    return resource(tree, GRID) is not None and node_with_label(tree, FIRST) is not None


def open_library():
    home = controls.wait_for("Home Library control", lambda t: node_with_label(t, "Library") is not None)
    tap(node_with_label(home, "Library"))
    return controls.wait_for("Library with fixtures", library_with_games)


def show_details(title):
    controls.wait_for(
        "details for " + title,
        lambda t: resource(t, "pagePlay") is not None and controls.has(t, title),
    )


def open_collections():
    library = controls.wait_for("game options available for collection", lambda t: resource(t, OPTIONS) is not None)
    tap(resource(library, OPTIONS))
    options = controls.wait_for("collection submenu", lambda t: controls.has(t, "Collections"))
    controls.tap_label(options, "Collections")
    return controls.wait_for("collection actions", lambda t: controls.has(t, "New collection"))


def add_collection(name):
    controls.tap_label(open_collections(), "New collection")
    controls.wait_for("collection name field", lambda t: any(n.get("class") == "android.widget.EditText"
                                                             for n in t.iter("node")))
    controls.adb("shell", "input", "text", name)
    controls.adb("shell", "input", "keyevent", "KEYCODE_ENTER")


def weekend_membership(tree):
    return selected(tree, COLLECTION) and library_with_games(tree) and node_with_label(tree, SECOND) is None


def check_collections():
    add_collection(COLLECTION)
    library = controls.wait_for("new collection persisted to the filter row",
                                lambda t: node_with_label(t, COLLECTION) is not None)
    tap(node_with_label(library, COLLECTION))
    controls.wait_for("only the first game is in Weekend", weekend_membership)
    add_collection(COLLECTION)
    controls.wait_for("repeated add keeps the current game in Weekend", weekend_membership)
    add_collection("AList")
    controls.wait_for("alphabetically earlier collection does not change the selected shelf",
                      lambda t: weekend_membership(t) and node_with_label(t, "AList") is not None)

    controls.adb("shell", "am", "force-stop", controls.PKG)
    controls.launch()
    library = open_library()
    tap(node_with_label(library, COLLECTION))
    controls.wait_for("collection membership survives a process restart", weekend_membership)
    controls.capture("08-collection-membership")

    open_collections()
    # AList sorts first and Weekend second. In the submenu, D-pad moves then A toggles.
    controls.key("DPAD_DOWN")
    controls.key("BUTTON_A")
    controls.key("BUTTON_B")
    controls.key("BUTTON_B")
    controls.wait_for("removal refreshes the active collection on menu dismissal",
                      lambda t: selected(t, COLLECTION) and resource(t, OPTIONS) is None
                      and node_with_label(t, FIRST) is None)
    controls.capture("09-empty-collection")
    print("PASS: duplicate collection adds, active selection, restart, and membership removal", flush=True)


def main(apk):
    controls.RESULTS.mkdir(exist_ok=True)
    controls.adb("install", "-g", apk, timeout=180)
    controls.adb("shell", "svc", "power", "stayon", "true")
    controls.adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    controls.setup_device(320)
    controls.adb("shell", "am", "force-stop", controls.PKG)
    seed_dir = "/sdcard/Android/data/" + controls.PKG + "/files/preview"
    controls.adb("shell", "mkdir", "-p", seed_dir)
    seed = controls.RESULTS / "seed.txt"
    seed.write_text(
        "pref\tbool\tpocket_onboarding_pending\tfalse\n"
        "pref\tbool\tpocket_first_run_done_v1\ttrue\n"
        "pref\tbool\tpocket_animations\tfalse\n"
        "pref\tstring\tpocket_home_layout\thorizon_workspace\n"
        "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t501\n"
        "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t509\n"
        "pin\tpc\tBoreal Drift\temulator-test-pc\t\t\t502\n",
        encoding="utf-8",
    )
    controls.adb("push", str(seed), seed_dir + "/seed.txt")
    controls.launch()
    library = open_library()
    detail, grid = bounds(resource(library, DETAIL)), bounds(resource(library, GRID))
    assert detail[2] <= grid[0], (detail, grid)
    controls.wait_for("first game focus after touch entry", lambda t: game_has_focus(t, FIRST))
    controls.capture("01-wide-library")
    controls.key("BUTTON_A")
    controls.wait_for("duplicate PC app IDs require an explicit choice", lambda t:
                      controls.has(t, "PC · app 501") and controls.has(t, "PC · app 509"))
    controls.capture("06-pc-app-choice")
    controls.key("BUTTON_B")
    controls.wait_for("focused game after closing the choice", lambda t: game_has_focus(t, FIRST))
    controls.key("DPAD_RIGHT")
    controls.wait_for("second game controller focus", lambda t: game_has_focus(t, SECOND))
    controls.key("BUTTON_X")
    show_details(SECOND)
    controls.capture("02-controller-game-details")
    controls.key("BUTTON_B")
    controls.wait_for("B returns to Library", library_with_games)
    controls.key("BUTTON_R1")
    controls.wait_for("shoulder selects empty Playing filter",
                      lambda t: selected(t, "Playing") and resource(t, DETAILS) is None)
    playing = controls.wait_for("All filter available", lambda t: node_with_label(t, "All") is not None)
    tap(node_with_label(playing, "All"))
    controls.wait_for("touch restores All filter", lambda t: selected(t, "All") and library_with_games(t))
    print("PASS: wide split, controller focus, game detail and filter with touch return", flush=True)
    check_collections()

    controls.setup_device(480)
    compact = open_library()
    detail, grid = bounds(resource(compact, DETAIL)), bounds(resource(compact, GRID))
    assert detail[3] <= grid[1], (detail, grid)
    controls.capture("03-compact-library")
    tap(resource(compact, OPTIONS))
    controls.wait_for("touch Game Options", lambda t: node_with_label(t, "Collections") is not None
                      or controls.has(t, "Collections"))
    controls.capture("04-touch-options")
    controls.key("BUTTON_B")
    compact = controls.wait_for("B closes options", lambda t: library_with_games(t)
                                and not controls.has(t, "Collections"))
    tap(resource(compact, DETAILS))
    show_details(FIRST)
    controls.capture("05-touch-game-details")
    details = controls.wait_for("Play from game details", lambda t: resource(t, "pagePlay") is not None)
    tap(resource(details, "pagePlay"))
    controls.wait_for("Play from details returns to the same game's source chooser", lambda t:
                      controls.has(t, "PC · app 501") and controls.has(t, "PC · app 509"))
    controls.capture("07-details-play-choice")
    print("PASS: compact layout, touch options and safe Play from game details", flush=True)


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    except AssertionError:
        tree = controls.snap()
        print("Focused nodes:", [(node.get("resource-id"), node.get("text"), node.get("content-desc"))
                                for node in tree.iter("node") if node.get("focused") == "true"], flush=True)
        raise
    finally:
        controls.RESULTS.mkdir(exist_ok=True)
        for name, args in (
                ("final.png", ("exec-out", "screencap", "-p")),
                ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (controls.RESULTS / name).write_bytes(controls.adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
