"""Launch development instance at title screen with an offline QA profile."""
from pathlib import Path
import json
import hashlib
import uuid
import subprocess
import re
import argparse

ROOT = Path(__file__).resolve().parents[1]
INSTANCE = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版")
MC = INSTANCE.parents[1]
JAVA = Path(r"C:\Users\俞皓天\AppData\Roaming\.minecraft\runtime\java-runtime-delta\bin\java.exe")


def allowed(rules):
    result = not rules
    for rule in rules:
        osrule = rule.get("os", {})
        match = osrule.get("name", "windows") == "windows"
        match &= osrule.get("arch", "amd64") in ["amd64", "x86_64"]
        match &= all({"has_custom_resolution": True}.get(k, False) == value for k, value in rule.get("features", {}).items())
        if match:
            result = rule["action"] == "allow"
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    doc = json.loads((INSTANCE / (INSTANCE.name + ".json")).read_text("utf-8-sig"))
    cp = []
    for library in doc["libraries"]:
        if not allowed(library.get("rules", [])):
            continue
        artifact = library.get("downloads", {}).get("artifact", {})
        relative = artifact.get("path")
        if not relative:
            group, name, version, *classifier = library["name"].split(":")
            filename = name + "-" + version + ("-" + classifier[0] if classifier else "") + ".jar"
            relative = "/".join([group.replace(".", "/"), name, version, filename])
        path = MC / "libraries" / relative
        if not path.is_file():
            raise RuntimeError("Missing library: " + str(path))
        if str(path) not in cp:
            cp.append(str(path))
    cp.append(str(INSTANCE / (INSTANCE.name + ".jar")))
    name = "StarGardenQA"
    raw = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    raw[6], raw[8] = (raw[6] & 15) | 48, (raw[8] & 63) | 128
    values = {"natives_directory": str(INSTANCE / "mod test-natives"), "launcher_name": "CodexLocalQA", "launcher_version": "1.0", "classpath": ";".join(cp), "classpath_separator": ";", "library_directory": str(MC / "libraries"), "version_name": INSTANCE.name, "auth_player_name": name, "auth_uuid": uuid.UUID(bytes=bytes(raw)).hex, "game_directory": str(INSTANCE), "assets_root": str(MC / "assets"), "assets_index_name": doc["assetIndex"]["id"], "auth_access_token": "0", "clientid": "0", "auth_xuid": "0", "user_type": "legacy", "version_type": doc.get("type", "release"), "resolution_width": "1100", "resolution_height": "700"}
    def expand(value):
        for key, replacement in values.items():
            value = value.replace("${" + key + "}", replacement)
        if "${" in value:
            raise RuntimeError("Unexpanded placeholder")
        return value
    def arguments(entries):
        output = []
        for entry in entries:
            if isinstance(entry, str):
                output.append(expand(entry))
            elif allowed(entry.get("rules", [])):
                value = entry["value"]
                output.extend(expand(x) for x in (value if isinstance(value, list) else [value]))
        return output
    command = [str(JAVA), "-Xmx6G", "-Dfile.encoding=UTF-8"] + arguments(doc["arguments"]["jvm"]) + [doc["mainClass"]] + arguments(doc["arguments"]["game"])
    if len(subprocess.list2cmdline(command)) > 32000:
        raise RuntimeError("Command too long")
    if args.dry_run:
        print(json.dumps({"libraries_present": len(cp), "instance": str(INSTANCE), "qa_name": name, "launches": False}, ensure_ascii=False))
        return
    log_path = ROOT / "evidence" / "development-client-console.log"
    with log_path.open("wb") as log:
        process = subprocess.Popen(command, cwd=INSTANCE, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    (ROOT / "evidence" / "development-client-launch.json").write_text(json.dumps({"pid": process.pid, "instance": str(INSTANCE), "qa_profile": name, "log": str(log_path), "opens_existing_save": False}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"pid": process.pid, "log": str(log_path)}))


if __name__ == "__main__":
    main()
