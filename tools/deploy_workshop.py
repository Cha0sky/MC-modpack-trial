"""Deploy the approved workshop chapter, preserving unrelated quest language keys."""
import argparse
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import shutil
import subprocess
import tomllib
import zipfile
from quest_language import read_language

ROOT=Path(__file__).resolve().parents[1]
INSTANCE=Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版")
QUESTS=ROOT/'staging/config/ftbquests/quests'

def closed():
    result=subprocess.run([r'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe',
        '-NoProfile','-NonInteractive','-Command',
        '$p=@(Get-Process -Name java,javaw -ErrorAction SilentlyContinue); if ($p.Count -gt 0) { exit 9 }; exit 0'])
    if result.returncode: raise RuntimeError('Close the Java game before deployment; no files replaced')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    chapter=json.loads((QUESTS/'chapters/01_workshop_rekindled.snbt').read_text('utf-8-sig'))
    prologue=json.loads((QUESTS/'chapters/00_lostworld_chapter1.snbt').read_text('utf-8-sig'))
    prefixes=['chapter.'+chapter['id']+'.']+['quest.'+q['id']+'.' for q in chapter['quests']]
    title='chapter.'+prologue['id']+'.title'
    # Add dependencies first, then the new core and its chapter; never scan or rewrite saves.
    manifest=[
        (ROOT/'staging/mods/Explorify v1.6.6.mod.jar',Path('mods/Explorify v1.6.6.mod.jar'),None),
        (ROOT/'staging/mods/exposure-neoforge-1.21.1-1.9.21.jar',Path('mods/exposure-neoforge-1.21.1-1.9.21.jar'),None),
        (ROOT/'modules/world-progression-core/build/libs/world-progression-core-0.2.0-workshop.jar',Path('mods/world-progression-core-0.1.0.jar'),None),
        (QUESTS/'chapters/01_workshop_rekindled.snbt',Path('config/ftbquests/quests/chapters/01_workshop_rekindled.snbt'),None),
    ]
    expected={'explorify':'1.6.6','exposure':'1.9.21','world_progression_core':'0.2.0-workshop'}
    for source,relative,_ in manifest[:3]:
        with zipfile.ZipFile(source) as jar:
            metadata=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'))
            mod=next(m for m in metadata['mods'] if m['modId'] in expected)
            if mod['version']!=expected[mod['modId']]: raise RuntimeError('Unexpected artifact version: '+source.name)
        matches=list((INSTANCE/'mods').glob({'explorify':'*xplorify*.jar','exposure':'*exposure*.jar',
            'world_progression_core':'world-progression-core*.jar'}[mod['modId']]))
        if any(p.name!=relative.name for p in matches): raise RuntimeError('Another enabled JAR already exists for '+mod['modId'])
    for locale in ('zh_cn','en_us'):
        relative=Path('config/ftbquests/quests/lang')/(locale+'.snbt')
        target=INSTANCE/relative
        existing=read_language(target)
        source=json.loads((QUESTS/'lang'/(locale+'.snbt')).read_text('utf-8-sig'))
        changes={key:value for key,value in source.items() if key==title or any(key.startswith(prefix) for prefix in prefixes)}
        if existing.get(title) not in (None,'第一章 · 从小屋出发','序章 · 从小屋出发'):
            raise RuntimeError('Custom prologue title needs review: '+locale)
        merged={**existing,**changes}
        manifest.append((QUESTS/'lang'/(locale+'.snbt'),relative,
                         (json.dumps(merged,ensure_ascii=False,indent=2)+'\n').encode('utf-8')))
    now=datetime.now(timezone(timedelta(hours=8)))
    backup=ROOT/'backups'/('workshop-'+now.strftime('%Y%m%d-%H%M%S-%f'))
    receipt={'date':now.isoformat(),'instance':str(INSTANCE),'applied':args.apply,
             'scope':'workshop chapter core, Explorify, Exposure, new quest file and two merged languages',
             'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
             'game_launched':False,'game_test_run':False,'saves_modified':False,'file_hashes_computed':False,
             'runtime_acceptance':'pending user playtest','files':[]}
    for source,relative,payload in manifest:
        dest=(INSTANCE/relative).resolve()
        if not dest.is_relative_to(INSTANCE.resolve()) or not source.is_file(): raise RuntimeError('Invalid deployment path')
        receipt['files'].append({'path':relative.as_posix(),'existed':dest.exists(),
            'before_size':dest.stat().st_size if dest.exists() else None,
            'after_size':len(payload) if payload is not None else source.stat().st_size})
    if args.apply:
        closed();backup.mkdir(parents=True,exist_ok=False)
        for _,relative,_ in manifest:
            dest=INSTANCE/relative
            if dest.exists():
                saved=backup/relative;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest,saved)
        closed()
        for (source,relative,payload),record in zip(manifest,receipt['files']):
            dest=INSTANCE/relative;dest.parent.mkdir(parents=True,exist_ok=True)
            if payload is None: shutil.copy2(source,dest)
            else: dest.write_bytes(payload)
            if dest.stat().st_size!=record['after_size']: raise RuntimeError('Copy size mismatch: '+str(relative))
        receipt['backup']=str(backup)
        receipt['game_process_check']='No java/javaw process before backup and replacement'
        value=json.dumps(receipt,ensure_ascii=False,indent=2)+'\n'
        (backup/'receipt.json').write_text(value,encoding='utf-8')
        (ROOT/'evidence/workshop-deployment.json').write_text(value,encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
