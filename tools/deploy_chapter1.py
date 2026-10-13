"""Deploy ONLY approved chapter files; back up all targets and preserve instance-only language keys."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil

ROOT=Path(__file__).resolve().parents[1]
INSTANCE=Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--apply",action="store_true")
    args=parser.parse_args()
    jar=ROOT/"modules/world-progression-core/build/libs/world-progression-core-0.1.0.jar"
    quests=ROOT/"staging/config/ftbquests/quests"
    chapter=quests/"chapters/00_lostworld_chapter1.snbt"
    manifest=[(jar,Path("mods")/jar.name,None),
              (chapter,Path("config/ftbquests/quests/chapters")/chapter.name,None)]
    language=json.loads((quests/"lang/zh_cn.snbt").read_text("utf-8-sig"))
    chapterdoc=json.loads(chapter.read_text("utf-8-sig"))
    prefixes=["chapter."+chapterdoc["id"]+"."]+["quest."+q["id"]+"." for q in chapterdoc["quests"]]
    keys={k:v for k,v in language.items() if any(k.startswith(p) for p in prefixes)}
    for locale in ("zh_cn","en_us"):
        relative=Path(f"config/ftbquests/quests/lang/{locale}.snbt")
        target=INSTANCE/relative
        existing=json.loads(target.read_text("utf-8-sig")) if target.exists() else {}
        # Fail if a customized deployed version would be overwritten silently.
        for k,v in keys.items():
            if k in existing and existing[k]!=v:
                raise RuntimeError("Instance has a customized chapter language key: "+k)
        merged={**existing,**keys}
        manifest.append((quests/f"lang/{locale}.snbt",relative,
                         (json.dumps(merged,ensure_ascii=False,indent=2)+"\n").encode("utf-8")))
    if not jar.is_file(): raise RuntimeError("Build jar first")
    conflicts=[p.name for p in (INSTANCE/"mods").glob("world-progression-core*.jar") if p.name!=jar.name]
    if conflicts: raise RuntimeError("Other chapter-core jar already installed: "+", ".join(conflicts))
    receipt={"instance":str(INSTANCE),"approved_scope":"first chapter prototype", "applied":args.apply,"files":[]}
    backup=ROOT/"backups"/("chapter1-"+datetime.now().strftime("%Y%m%d-%H%M%S"))
    # Preflight everything before the first external write.
    for src,rel,payload in manifest:
        dest=(INSTANCE/rel).resolve()
        if not dest.is_relative_to(INSTANCE.resolve()): raise RuntimeError("Invalid target")
        if not src.is_file(): raise RuntimeError("Missing source: "+str(src))
        receipt["files"].append({"path":str(rel),"before_sha256":sha(dest),
            "after_sha256":hashlib.sha256(payload).hexdigest() if payload is not None else sha(src),"existed":dest.exists()})
    if args.apply:
        backup.mkdir(parents=True,exist_ok=False)
        for (src,rel,payload),entry in zip(manifest,receipt["files"]):
            dest=INSTANCE/rel
            if sha(dest)!=entry["before_sha256"]: raise RuntimeError("Target changed during preflight: "+str(rel))
            if dest.exists():
                saved=backup/rel; saved.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(dest,saved)
                if sha(saved)!=entry["before_sha256"]: raise RuntimeError("Backup checksum failed")
            dest.parent.mkdir(parents=True,exist_ok=True)
            if payload is None: shutil.copy2(src,dest)
            else: dest.write_bytes(payload)
            if sha(dest)!=entry["after_sha256"]: raise RuntimeError("Deployment checksum failed")
        receipt["backup"]=str(backup)
        (backup/"receipt.json").write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
        (ROOT/"evidence/chapter1-deployment.json").write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(receipt,ensure_ascii=False,indent=2))


if __name__=="__main__": main()
