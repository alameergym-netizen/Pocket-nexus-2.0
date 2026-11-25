"""Verify Titanium geometry and Android-observed telemetry on the emulator."""
from pathlib import Path

import control_workspace as controls
from home_workspace import bounds, focus_label, home_ready, resource

controls.RESULTS = Path("home-ui-results")
GAME = "Aster Circuit"


def main():
    controls.adb("shell", "am", "force-stop", controls.PKG)
    seed = controls.RESULTS / "titanium-seed.txt"
    seed.write_text("pref\tstring\tpocket_home_layout\tlist\n"
                    "pref\tint\tpocket_accent\t-4668210\n"
                    "pref\tstring\tpocket_backdrop\tblack\n"
                    "pref\tstring\tpocket_material\tfrost\n"
                    "pref\tstring\tpocket_sound_style\ttactile\n"
                    "pref\tstring\tpocket_focus_style\tring\n", encoding="utf-8")
    seed_dir = "/sdcard/Android/data/" + controls.PKG + "/files/preview"
    controls.adb("push", str(seed), seed_dir + "/seed.txt")
    controls.adb("shell", "dumpsys", "battery", "set", "level", "67")
    controls.launch()
    home = controls.wait_for("Titanium Home with the battery reported by Android", lambda tree:
                             home_ready(tree) and controls.has(tree, "Battery 67%")
                             and resource(tree, "horizon_titanium_telemetry") is not None)
    telemetry = resource(home, "horizon_titanium_telemetry")
    assert bounds(telemetry)[3] <= bounds(resource(home, "homeRow"))[1]
    assert any(n.get("text", "").startswith("Thermal ") for n in telemetry.iter("node"))
    assert any(n.get("text", "").startswith("Display ") for n in telemetry.iter("node"))
    controls.adb("shell", "dumpsys", "battery", "set", "level", "42")
    controls.wait_for("visible Home updates the observed battery level", lambda tree: controls.has(tree, "Battery 42%"))
    controls.capture("08-titanium-home")

    controls.tap_label(home, "Library")
    library = controls.wait_for("Titanium Library index and observed launch source", lambda tree:
                                resource(tree, "horizon_library_grid") is not None
                                and controls.has(tree, "1 launch source") and focus_label(tree, GAME))
    grid = bounds(resource(library, "horizon_library_grid"))
    detail = bounds(resource(library, "horizon_library_detail"))
    assert grid[2] < detail[0], (grid, detail)
    controls.capture("09-titanium-library")
    controls.key("BUTTON_B")
    controls.wait_for("B returns from Titanium Library", home_ready)

    control = controls.open_control()
    control = controls.wait_for("Control shows current thermal pressure and battery", lambda tree:
                                controls.control(tree) and controls.has(tree, "42%")
                                and controls.has(tree, "Thermal pressure"))
    telemetry = resource(control, "horizon_titanium_telemetry")
    sleep = next(n for n in control.iter("node") if n.get("content-desc") == "Sleep" or n.get("text") == "Sleep")
    assert bounds(telemetry)[3] <= bounds(sleep)[1]
    controls.key("DPAD_RIGHT")
    controls.wait_for("Wi-Fi retains controller focus in Titanium", lambda tree: controls.focused(tree, "Wi-Fi"))
    controls.capture("10-titanium-control")
    controls.key("BUTTON_B")
    controls.wait_for("B returns from Titanium Control", home_ready)
    print("PASS: original Titanium geometry, live Android battery updates, real Library source count and controller focus",
          flush=True)


if __name__ == "__main__":
    try:
        main()
    finally:
        controls.adb("shell", "dumpsys", "battery", "reset")
        controls.capture("titanium-last-screen")
