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


def show_details():
    def detail_open(tree):
        if resource(tree, GRID) is not None:
            return False
        activities = controls.adb("shell", "dumpsys", "activity", "activities")
        return any(
            b"mResumedActivity" in line and b"com.limelight.pocket.GamePageActivity" in line
            for line in activities.splitlines()
        )

    controls.wait_for("game detail activity", detail_open)


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
    controls.key("DPAD_RIGHT")
    controls.wait_for("second game controller focus", lambda t: game_has_focus(t, SECOND))
    controls.key("BUTTON_X")
    show_details()
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
    show_details()
    controls.capture("05-touch-game-details")
    print("PASS: compact stacked layout and touch options and game details", flush=True)


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
