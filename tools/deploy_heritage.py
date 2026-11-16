"""Deploy only the heritage chapter's core, quest and merged language keys."""
import argparse
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import shutil
import subprocess
import tomllib
import zipfile
from deploy_workshop import ROOT, INSTANCE, QUESTS, closed
from quest_language import read_language

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    core=ROOT/'modules/world-progression-core/build/libs/world-progression-core-0.3.0-heritage.jar'
    with zipfile.ZipFile(core) as jar:
        metadata=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'))
        mod=next(m for m in metadata['mods'] if m['modId']=='world_progression_core')
        if mod['version']!='0.3.0-heritage': raise RuntimeError('Unexpected core version')
    chapter=json.loads((QUESTS/'chapters/02_heritage_survey.snbt').read_text('utf-8-sig'))
    prefixes=['chapter.'+chapter['id']+'.']+['quest.'+q['id']+'.' for q in chapter['quests']]
    manifest=[(core,Path('mods/world-progression-core-0.1.0.jar'),None),
        (QUESTS/'chapters/02_heritage_survey.snbt',Path('config/ftbquests/quests/chapters/02_heritage_survey.snbt'),None)]
    for locale in ('zh_cn','en_us'):
        relative=Path('config/ftbquests/quests/lang')/(locale+'.snbt')
        existing=read_language(INSTANCE/relative)
        source=json.loads((QUESTS/'lang'/(locale+'.snbt')).read_text('utf-8-sig'))
        changes={key:value for key,value in source.items() if any(key.startswith(prefix) for prefix in prefixes)}
        payload=(json.dumps({**existing,**changes},ensure_ascii=False,indent=2)+'\n').encode('utf-8')
        manifest.append((QUESTS/'lang'/(locale+'.snbt'),relative,payload))
    duplicates=[p.name for p in (INSTANCE/'mods').glob('world-progression-core*.jar') if p.name!='world-progression-core-0.1.0.jar']
    if duplicates: raise RuntimeError('Other enabled core JARs: '+repr(duplicates))
    now=datetime.now(timezone(timedelta(hours=8)))
    backup=ROOT/'backups'/('heritage-'+now.strftime('%Y%m%d-%H%M%S-%f'))
    receipt={'date':now.isoformat(),'instance':str(INSTANCE),'applied':args.apply,
        'scope':'heritage chapter core, new quest and two merged language files',
        'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'game_launched':False,'game_test_run':False,'saves_modified':False,'file_hashes_computed':False,
        'new_mods_installed':False,'runtime_acceptance':'pending user playtest','files':[]}
    for source,relative,payload in manifest:
        target=(INSTANCE/relative).resolve()
        if not target.is_relative_to(INSTANCE.resolve()) or not source.is_file(): raise RuntimeError('Invalid deployment path')
        receipt['files'].append({'path':relative.as_posix(),'existed':target.exists(),
            'before_size':target.stat().st_size if target.exists() else None,
            'after_size':len(payload) if payload is not None else source.stat().st_size})
    if args.apply:
        closed();backup.mkdir(parents=True,exist_ok=False)
        for _,relative,_ in manifest:
            target=INSTANCE/relative
            if target.exists():
                saved=backup/relative;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,saved)
        closed()
        for (source,relative,payload),entry in zip(manifest,receipt['files']):
            target=INSTANCE/relative;target.parent.mkdir(parents=True,exist_ok=True)
            if payload is None: shutil.copy2(source,target)
            else: target.write_bytes(payload)
            if target.stat().st_size!=entry['after_size']: raise RuntimeError('Copy size mismatch: '+str(relative))
        receipt['backup']=str(backup)
        receipt['game_process_check']='No java/javaw process before backup and replacement'
        value=json.dumps(receipt,ensure_ascii=False,indent=2)+'\n'
        (backup/'receipt.json').write_text(value,encoding='utf-8')
        (ROOT/'evidence/heritage-deployment.json').write_text(value,encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
