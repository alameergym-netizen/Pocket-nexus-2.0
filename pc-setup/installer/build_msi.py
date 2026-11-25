"""Create a per-user x64 development MSI from the verified Nexus Host staging directory."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile
import uuid
import xml.etree.ElementTree as ET


WIX_NAMESPACE = "http://schemas.microsoft.com/wix/2006/wi"
UPGRADE_CODE = uuid.UUID("b65c60ec-d4f5-4ebe-ad5b-f4fc9930d92f")
REGISTRY = r"Software\Pocket Nexus\Host MSI Development"


def element(parent, tag, **attributes):
    return ET.SubElement(parent, f"{{{WIX_NAMESPACE}}}{tag}", attributes)


def identifier(prefix, path):
    return prefix + hashlib.sha256(path.encode("utf-8")).hexdigest()[:24]


def guid(value):
    return "{" + str(value).upper() + "}"


def valid_version(value):
    parts = value.split(".")
    if len(parts) != 3 or any(not p.isascii() or not p.isdecimal() or len(p) > 5 for p in parts):
        raise argparse.ArgumentTypeError("MSI version must contain three decimal components")
    numbers = tuple(map(int, parts))
    if numbers[0] > 255 or numbers[1] > 255 or numbers[2] > 65535:
        raise argparse.ArgumentTypeError("MSI version exceeds Windows Installer limits")
    return ".".join(map(str, numbers))


def build_source(payload, version, source):
    ET.register_namespace("", WIX_NAMESPACE)
    root = ET.Element(f"{{{WIX_NAMESPACE}}}Wix")
    product = element(root, "Product", Id=guid(uuid.uuid5(UPGRADE_CODE, version)),
                      UpgradeCode=guid(UPGRADE_CODE), Name="Pocket Nexus Host Development",
                      Language="1033", Codepage="1252", Manufacturer="Pocket Nexus", Version=version)
    element(product, "Package", Id="*", InstallerVersion="500", Compressed="yes", InstallScope="perUser",
            Platform="x64", Description="Pocket Nexus Host development companion")
    element(product, "MediaTemplate", EmbedCab="yes")
    element(product, "MajorUpgrade", Schedule="afterInstallInitialize",
            DowngradeErrorMessage="A newer Pocket Nexus Host development MSI is installed.")
    element(product, "Property", Id="MSIRESTARTMANAGERCONTROL", Value="Disable")

    target = element(product, "Directory", Id="TARGETDIR", Name="SourceDir")
    local = element(target, "Directory", Id="LocalAppDataFolder")
    vendor = element(local, "Directory", Id="NexusLocalFolder", Name="PocketNexus")
    install = element(vendor, "Directory", Id="INSTALLFOLDER", Name="NexusHost-msi-dev")
    programs = element(target, "Directory", Id="ProgramMenuFolder")
    shortcuts = element(programs, "Directory", Id="NexusPrograms", Name="Pocket Nexus Host Development")
    element(target, "Directory", Id="System64Folder")
    feature = element(product, "Feature", Id="NexusHost", Title="Pocket Nexus Host", Level="1")

    def component(directory, item):
        component_id = identifier("cmp_", item)
        value = element(directory, "Component", Id=component_id, Win64="yes",
                        Guid=guid(uuid.uuid5(UPGRADE_CODE, item)))
        element(value, "RegistryValue", Root="HKCU", Key=REGISTRY, Name=component_id,
                Type="integer", Value="1", KeyPath="yes")
        element(feature, "ComponentRef", Id=component_id)
        return value

    def remove_empty_directory(directory):
        identity = directory.get("Id")
        entry = component(directory, "directory/" + identity)
        element(entry, "RemoveFolder", Id=identifier("rm_", identity), On="uninstall")

    def add_files(directory, folder):
        for path in sorted(folder.iterdir()):
            relative = path.relative_to(payload).as_posix()
            if path.is_symlink():
                raise ValueError(f"Staged payload contains a symbolic link: {relative}")
            if path.is_dir():
                child = element(directory, "Directory", Id=identifier("dir_", relative), Name=path.name)
                remove_empty_directory(child)
                add_files(child, path)
            elif path.is_file():
                entry = component(directory, relative)
                element(entry, "File", Id=identifier("fil_", relative), Name=path.name, Source=str(path), KeyPath="no")
            else:
                raise ValueError(f"Unsupported staged item: {relative}")

    remove_empty_directory(vendor)
    remove_empty_directory(install)
    add_files(install, payload)
    start_link = component(shortcuts, "start-menu-shortcut")
    script = identifier("fil_", "PocketNexus-PC.ps1")
    element(start_link, "Shortcut", Id="NexusDevStart", Name="Pocket Nexus Host Development",
            Target=r"[System64Folder]WindowsPowerShell\v1.0\powershell.exe",
            Arguments=f'-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "[#{script}]" -Background',
            WorkingDirectory="INSTALLFOLDER", Description="Start the Pocket Nexus Host development companion")
    element(start_link, "RemoveFolder", Id="RemoveNexusPrograms", On="uninstall")
    ET.indent(root)
    ET.ElementTree(root).write(source, encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload", type=Path, required=True)
    parser.add_argument("--version", type=valid_version, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--wix-bin", type=Path, required=True)
    args = parser.parse_args()
    payload = args.payload.resolve(strict=True)
    for path in ("PocketNexus-PC.ps1", "NexusSecureHost.cs", "SHA256-MANIFEST.json"):
        if not (payload / path).is_file():
            parser.error(f"Missing staging file: {path}")
    output = args.output.resolve()
    if output.exists():
        parser.error(f"Output already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="nexus-wix-") as temporary:
        source = Path(temporary) / "NexusHost.wxs"
        obj = Path(temporary) / "NexusHost.wixobj"
        build_source(payload, args.version, source)
        subprocess.run([args.wix_bin / "candle.exe", "-nologo", "-arch", "x64", "-out", obj, source], check=True)
        subprocess.run([args.wix_bin / "light.exe", "-nologo", "-out", output, obj], check=True)


if __name__ == "__main__":
    main()
