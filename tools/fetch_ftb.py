"""Resolve MC 1.21.1 NeoForge FTB releases from the public Modrinth API."""
from pathlib import Path
import json
import hashlib
import urllib.request
import urllib.parse
import zipfile
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def request(url):
    req = urllib.request.Request(url, headers={"User-Agent": "StarGardenModpack/0.1 (local development)"})
    with urllib.request.urlopen(req, timeout=45) as response:
        return response.read()


def main():
    out = ROOT / "staging" / "mods"
    out.mkdir(parents=True, exist_ok=True)
    records = []
    for slug in ["ftb-teams", "ftb-quests", "ftb-xmod-compat"]:
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
