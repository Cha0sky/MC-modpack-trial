"""Stage the compatible FTB Quests release and resolve other FTB components."""
from pathlib import Path
from io import BytesIO
import json
import hashlib
import urllib.request
import urllib.parse
import zipfile
import tomllib

ROOT = Path(__file__).resolve().parents[1]
QUEST_VERSION = "2101.1.35"
QUEST_FILENAME = f"ftb-quests-neoforge-{QUEST_VERSION}.jar"
QUEST_URL = f"https://maven.ftb.dev/releases/dev/ftb/mods/ftb-quests-neoforge/{QUEST_VERSION}/{QUEST_FILENAME}"


def request(url):
    req = urllib.request.Request(url, headers={"User-Agent": "StarGardenModpack/0.1 (local development)"})
    with urllib.request.urlopen(req, timeout=45) as response:
        return response.read()


def stage_quests(out):
    path = out / QUEST_FILENAME
    data = path.read_bytes() if path.is_file() else request(QUEST_URL)
    with zipfile.ZipFile(BytesIO(data)) as jar:
        meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
    if not any(mod["modId"] == "ftbquests" and mod["version"] == QUEST_VERSION for mod in meta["mods"]):
        raise ValueError(f"Unexpected FTB Quests release: {path}")
    if not path.exists():
        path.write_bytes(data)
    return {"project": "ftb-quests", "version": QUEST_VERSION, "filename": QUEST_FILENAME,
            "url": QUEST_URL, "mods": meta["mods"], "dependencies_jar": meta["dependencies"]}


def main():
    out = ROOT / "staging" / "mods"
    out.mkdir(parents=True, exist_ok=True)
    records = [stage_quests(out)]
    for slug in ["ftb-teams", "ftb-xmod-compat"]:
        query = urllib.parse.urlencode({"game_versions": '["1.21.1"]', "loaders": '["neoforge"]'})
        versions = json.loads(request("https://api.modrinth.com/v2/project/" + slug + "/version?" + query))
        releases = [v for v in versions if v["version_type"] == "release"]
        if not releases:
            raise RuntimeError("No compatible release: " + slug)
        version = sorted(releases, key=lambda v: v["date_published"], reverse=True)[0]
        f = next((f for f in version["files"] if f.get("primary")), version["files"][0])
        if Path(f["filename"]).name != f["filename"]:
            raise RuntimeError("Invalid filename")
        destination = out / f["filename"]
        data = request(f["url"])
        actual = hashlib.sha512(data).hexdigest()
        if actual != f["hashes"]["sha512"]:
            raise RuntimeError("Hash mismatch: " + slug)
        destination.write_bytes(data)
        with zipfile.ZipFile(destination) as jar:
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
        records.append({"project": slug, "version": version["version_number"], "version_id": version["id"], "published": version["date_published"], "filename": f["filename"], "url": f["url"], "sha512": actual, "dependencies_api": version["dependencies"], "mods": metadata.get("mods"), "dependencies_jar": metadata.get("dependencies")})
        print(json.dumps(records[-1], ensure_ascii=False, indent=2), flush=True)
    (ROOT / "evidence" / "ftb-releases.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
