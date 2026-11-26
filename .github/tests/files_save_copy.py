"""Use the Files menu of the installed development APK to verify save copy recovery."""
from pathlib import Path
import hashlib
import subprocess
import shlex

import control_workspace as controls
from home_workspace import bounds

controls.RESULTS = Path("files-save-copy-results")
SAVE = "Horizon-slot.sav"
BACKUP = "Backup - " + SAVE + ".nexussave"
RECOVERED = "Horizon-slot (2).sav"
DOWNLOADS = "/sdcard/Download/"
PREVIEW = "Preview save restore"
RESTORE = "Restore verified copy here"
FOLDER = "Horizon-Save-Folder"
FOLDER_BACKUP = "Backup - " + FOLDER + ".nexusfolder"
FOLDER_RESTORED = FOLDER + " (2)"
FOLDER_DATA = {"save.dat": bytes([1, 7, 9]), "states/slot.sav": bytes([4, 12, 128, 255])}


def prepare_folder():
    controls.adb("shell", "mkdir", "-p", DOWNLOADS + FOLDER + "/states", DOWNLOADS + FOLDER + "/empty")
    for relative, data in FOLDER_DATA.items():
        local = controls.RESULTS / ("folder-" + relative.replace("/", "-"))
        local.write_bytes(data)
        controls.adb("push", str(local), DOWNLOADS + FOLDER + "/" + relative)


def open_folder_preview(tree, digest):
    hold_row(tree, FOLDER_BACKUP)
    menu = controls.wait_for("folder restore preview action", lambda t: controls.has(t, "Preview folder restore"))
    controls.tap_label(menu, "Preview folder restore")
    return controls.wait_for("verified folder manifest and keep-both choice", lambda t:
                             controls.has(t, "Restore separate folder here")
                             and controls.has(t, "Files: 2 · Folders: 2")
                             and any(digest in n.get("text", "") for n in t.iter("node")))


def folder_snapshot():
    files = controls.wait_for("save folder in the existing Downloads view", lambda t: controls.has(t, FOLDER))
    hold_row(files, FOLDER)
    menu = controls.wait_for("multi-file backup action", lambda t: controls.has(t, "Back up save folder"))
    controls.tap_label(menu, "Back up save folder")
    files = controls.wait_for("published verified folder archive", lambda t: controls.has(t, FOLDER_BACKUP))
    archive = controls.RESULTS / "folder-archive.nexusfolder"
    controls.adb("pull", DOWNLOADS + FOLDER_BACKUP, str(archive))
    digest = hashlib.sha256(archive.read_bytes()[:-32]).hexdigest()
    current = controls.RESULTS / "live-folder-save.dat"
    current.write_bytes(bytes([55, 66]))
    controls.adb("push", str(current), DOWNLOADS + FOLDER + "/save.dat")
    open_folder_preview(files, digest)
    controls.capture("04-folder-restore-preview")
    controls.key("BUTTON_B")
    files = controls.wait_for("B dismisses folder preview", lambda t:
                              controls.has(t, FOLDER_BACKUP) and not controls.has(t, "Preview folder restore"))
    assert not controls.has(files, FOLDER_RESTORED), "Cancelling a preview restored a folder"
    controls.tap_label(open_folder_preview(files, digest), "Restore separate folder here")
    controls.wait_for("recovered separate folder", lambda t: controls.has(t, FOLDER_RESTORED))
    for relative, data in FOLDER_DATA.items():
        local = controls.RESULTS / ("recovered-folder-" + relative.replace("/", "-"))
        controls.adb("pull", DOWNLOADS + FOLDER_RESTORED + "/" + relative, str(local))
        assert local.read_bytes() == data, "Folder recovery changed the archived bytes: " + relative
    live = controls.RESULTS / "unchanged-live-folder.dat"
    controls.adb("pull", DOWNLOADS + FOLDER + "/save.dat", str(live))
    assert live.read_bytes() == bytes([55, 66]), "Folder recovery overwrote a current save"
    controls.adb("shell", "test", "-d", shlex.quote(DOWNLOADS + FOLDER_RESTORED + "/empty"))
    controls.capture("05-folder-recovered-with-original")
    print("PASS: multi-file folder backup, verified preview, B cancellation and separate-folder recovery preserve current saves")


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
    prepare_folder()
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
    folder_snapshot()


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
