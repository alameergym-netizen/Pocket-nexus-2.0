"""Use the Files menu of the installed development APK to verify save copy recovery."""
from pathlib import Path
import hashlib
import subprocess

import control_workspace as controls
from home_workspace import bounds

controls.RESULTS = Path("files-save-copy-results")
SAVE = "Horizon-slot.sav"
BACKUP = "Backup - " + SAVE + ".nexussave"
RECOVERED = "Horizon-slot (2).sav"
DOWNLOADS = "/sdcard/Download/"
PREVIEW = "Preview save restore"
RESTORE = "Restore verified copy here"


def hold_row(tree, name):
    row = next(n for n in tree.iter("node") if n.get("text") == name)
    left, top, right, bottom = bounds(row)
    assert left < right and top < bottom
    x, y = str((left + right) // 2), str((top + bottom) // 2)
    controls.adb("shell", "input", "swipe", x, y, x, y, "850")


def open_restore_preview(tree, digest):
    hold_row(tree, BACKUP)
    menu = controls.wait_for("verified restore preview action", lambda t: controls.has(t, PREVIEW))
    controls.tap_label(menu, PREVIEW)
    return controls.wait_for("restore preview verifies checksum and explains the conflict", lambda t:
                             controls.has(t, RESTORE)
                             and any(digest in n.get("text", "") for n in t.iter("node"))
                             and any("Restore keeps both files" in n.get("text", "") for n in t.iter("node")))


def main():
    controls.RESULTS.mkdir(exist_ok=True)
    controls.adb("shell", "appops", "set", "--uid", controls.PKG, "MANAGE_EXTERNAL_STORAGE", "allow")
    local_save = controls.RESULTS / SAVE
    initial_save = bytes([0, 25, 42, 128, 255])
    local_save.write_bytes(initial_save)
    controls.adb("push", str(local_save), DOWNLOADS + SAVE)
    controls.adb("shell", "am", "force-stop", controls.PKG)
    controls.launch()
    home = controls.wait_for("Files in Home quick access", lambda t: controls.has(t, "Files"))
    controls.tap_label(home, "Files")
    files = controls.wait_for("Files storage choices", lambda t: controls.has(t, "Downloads"))
    controls.tap_label(files, "Downloads")
    files = controls.wait_for("save file in Downloads", lambda t: controls.has(t, SAVE))
    hold_row(files, SAVE)
    menu = controls.wait_for("save backup action", lambda t: controls.has(t, "Create verified backup"))
    controls.capture("01-verified-backup-action")
    controls.tap_label(menu, "Create verified backup")
    files = controls.wait_for("published backup in Files", lambda t: controls.has(t, BACKUP))

    current_save = bytes([5, 6, 7, 8])
    local_save.write_bytes(current_save)
    controls.adb("push", str(local_save), DOWNLOADS + SAVE)
    archive = controls.RESULTS / "archive.nexussave"
    controls.adb("pull", DOWNLOADS + BACKUP, str(archive))
    digest = hashlib.sha256(archive.read_bytes()[:-32]).hexdigest()
    open_restore_preview(files, digest)
    controls.capture("02-verified-restore-preview")
    controls.key("BUTTON_B")
    files = controls.wait_for("B closes preview without publishing a save", lambda t:
                              controls.has(t, BACKUP) and not controls.has(t, PREVIEW))
    assert not controls.has(files, RECOVERED)
    controls.tap_label(open_restore_preview(files, digest), RESTORE)
    controls.wait_for("published recovered file", lambda t: controls.has(t, RECOVERED))
    # adb pull passes the exact filenames without quoting paths for the Android shell.
    original = controls.RESULTS / "current.sav"
    recovered = controls.RESULTS / "restored.sav"
    controls.adb("pull", DOWNLOADS + SAVE, str(original))
    controls.adb("pull", DOWNLOADS + RECOVERED, str(recovered))
    assert original.read_bytes() == current_save, "Restore changed the current save"
    assert recovered.read_bytes() == initial_save, "Recovered bytes differ from the backup input"
    controls.capture("03-restored-with-original")
    print("PASS: Files verified backup and recovery preserve the current save and restore the original bytes")


if __name__ == "__main__":
    try:
        main()
    finally:
        for name, args in (("final.png", ("exec-out", "screencap", "-p")),
                           ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (controls.RESULTS / name).write_bytes(controls.adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
