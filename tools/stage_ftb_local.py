"""Stage matching FTB component JARs from an existing local installation."""
from pathlib import Path
import zipfile
import tomllib
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\FTB OceanBlock 2\mods")


def main():
    out = ROOT / "staging" / "mods"
    out.mkdir(parents=True, exist_ok=True)
    records = []
    for filename in ["ftb-quests-neoforge-2101.1.27.jar", "ftb-teams-neoforge-2101.1.10.jar", "ftb-xmod-compat-neoforge-21.1.8.jar"]:
        source = SOURCE / filename
        data = source.read_bytes()
        with zipfile.ZipFile(source) as jar:
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
        records.append({"source": str(source), "filename": filename, "sha256": hashlib.sha256(data).hexdigest(), "mods": metadata["mods"], "dependencies": metadata.get("dependencies", {})})
        shutil.copy2(source, out / filename)
        print(json.dumps(records[-1], ensure_ascii=False, indent=2), flush=True)
    (ROOT / "evidence" / "ftb-local-staging.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
