"""Exercise the existing Pure OLED preset across the three Android workspaces."""
from pathlib import Path

import control_workspace as controls
from home_workspace import bounds, home_ready, resource

controls.RESULTS = Path("home-ui-results")
GAME = "Aster Circuit"


def labelled(tree, label):
    return next((n for n in tree.iter("node")
                 if label in (n.get("text"), n.get("content-desc"))), None)


def main():
    controls.RESULTS.mkdir(exist_ok=True)
    controls.adb("shell", "am", "force-stop", controls.PKG)
    seed_dir = "/sdcard/Android/data/" + controls.PKG + "/files/preview"
    seed = controls.RESULTS / "pure-oled-seed.txt"
    seed.write_text("pref\tbool\tpocket_animations\tfalse\n"
                    "pref\tstring\tpocket_home_layout\tgrid_tiles\n"
                    "pref\tint\tpocket_accent\t-1\n"
                    "pref\tstring\tpocket_backdrop\tblack\n"
                    "pref\tstring\tpocket_material\tglass\n"
                    "pref\tstring\tpocket_sound_style\tcrisp\n"
                    "pref\tstring\tpocket_focus_style\tring\n"
                    "pref\tbool\tpocket_gb_enabled\ttrue\n"
                    "pref\tstring\tpocket_gb_back_tap\tcontrol\n"
                    "pref\tstring\tpocket_key_back\tk:138\n"
                    "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t501\n", encoding="utf-8")
    controls.adb("push", str(seed), seed_dir + "/seed.txt")
    controls.launch()
    home = controls.wait_for("Pure OLED Home and Quick Access", lambda t:
                             home_ready(t) and controls.has(t, "Quick Access") and controls.has(t, "Library"))
    shelf = bounds(resource(home, "homeRow"))
    play = bounds(resource(home, "pocketHeroPlay"))
    assert shelf[2] <= play[0], (shelf, play)
    controls.capture("05-pure-oled-home")

    controls.tap_label(home, "Library")
    library = controls.wait_for("Pure OLED Library and game row", lambda t:
                                resource(t, "horizon_library_grid") is not None and labelled(t, GAME) is not None)
    card = bounds(labelled(library, GAME))
    assert card[2] - card[0] >= 2 * (card[3] - card[1]), card
    controls.wait_for("controller can focus the first Pure OLED Library row", lambda t: controls.focused(t, GAME))
    controls.capture("06-pure-oled-library")
    controls.key("BUTTON_B")
    controls.wait_for("B returns from Pure OLED Library", home_ready)

    control = controls.open_control()
    sleep = bounds(labelled(control, "Sleep"))
    wifi = bounds(labelled(control, "Wi-Fi"))
    bluetooth = bounds(labelled(control, "Bluetooth"))
    assert sleep[1] == wifi[1] and bluetooth[1] > sleep[1], (sleep, wifi, bluetooth)
    controls.key("DPAD_RIGHT")
    controls.wait_for("Wi-Fi can receive controller focus in Pure OLED", lambda t: controls.focused(t, "Wi-Fi"))
    controls.capture("07-pure-oled-control")
    controls.key("BUTTON_B")
    controls.wait_for("B returns from Pure OLED Control", home_ready)
    print("PASS: Pure OLED Home, Library and Control retain a distinct layout and controller navigation", flush=True)


if __name__ == "__main__":
    try:
        main()
    finally:
        controls.capture("pure-oled-last-screen")
