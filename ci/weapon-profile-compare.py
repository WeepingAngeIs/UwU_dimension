"""Compare real Better Combat registry output with the verified beta.11 baseline.

This is intentionally strict: approved attack strings and three specific two-handed
poses may change, but all other values, explicit presets, null profiles and
conditional attacks remain unchanged. Output contains full before/after profiles.
"""
import copy
import gzip
import hashlib
import json
import sys
from pathlib import Path
from zipfile import ZipFile

if len(sys.argv)!=5:
    raise SystemExit('usage: weapon-profile-compare.py baseline.json.gz observed.json bettercombat.jar simplyswords.jar')
base_path,current_path,better_path,simply_path=map(Path,sys.argv[1:])
expected_baseline='1eaef2310b9967d1cd6c7d9ba2fb7550e6fd418ae4b5a837ab605de5fc82511b'
expected_jars={
    better_path:'0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a',
    simply_path:'5e75bd66ea489079d60c9c1ed98762550fbc0bee88cd7af399f17bbeb56608c3',
}
digest=lambda data:hashlib.sha256(data).hexdigest()
base_raw=gzip.decompress(base_path.read_bytes())
if digest(base_raw)!=expected_baseline:raise AssertionError('Baseline does not match verified beta.11 observation')
base=json.loads(base_raw);current=json.loads(current_path.read_text(encoding='utf-8'))
for mod,version in base['versions'].items():
    if mod!='uwudimension' and current['versions'].get(mod)!=version:
        raise AssertionError(f'Baseline dependency changed: {mod} {version} -> {current["versions"].get(mod)}')

resources=set()
for jar,expected in expected_jars.items():
    if digest(jar.read_bytes())!=expected:raise AssertionError(f'Pinned installed reference JAR differs: {jar}')
    with ZipFile(jar) as z:
        for path in z.namelist():
            p=path.split('/')
            if len(p)==4 and p[0]=='assets' and p[2]=='player_animations' and p[3].endswith('.json'):
                resources.add(p[1]+':'+p[3][:-5])
if not resources:raise AssertionError('No Better Combat / Simply Swords animation resources found')

def index(rows):
    table={r['id']:r for r in rows}
    if len(table)!=len(rows):raise AssertionError('Duplicate item IDs in observation')
    return table
before=index(base['items']);after=index(current['items'])
if before.keys()!=after.keys():
    raise AssertionError(f'Item IDs changed: removed={sorted(before.keys()-after.keys())}, added={sorted(after.keys()-before.keys())}')
for key in ('uwu_item_count','reference_item_count','glitch_form_count'):
    if base[key]!=current[key]:raise AssertionError(f'Observation coverage changed: {key}')

def base_shape(item_id):
    return item_id.split('#',1)[-1].split(':',1)[-1]
roster=json.loads((Path(__file__).resolve().parent.parent/'src/main/resources/assets/uwudimension/counter_roster.json').read_text(encoding='utf-8'))
shapes={item['id']:item['shape'] for item in roster}
eligible_shapes={'axe','hammer','scythe','spear','chakram','whip','wand'}
eligible_relics={'blossom_greatsword','bellheart_axe','lockkeeper_axe','vesper_scythe','lantern_spear',
                 'axolotl_whip','frost_whip','rift_whip','satin_whip','ribbonhare_scepter','blue_hour_wand'}
relic_shapes={'blossom_greatsword':'axe','bellheart_axe':'axe','lockkeeper_axe':'axe',
              'vesper_scythe':'scythe','lantern_spear':'spear'}
approved_poses={'axe':'bettercombat:pose_two_handed_heavy',
                'spear':'bettercombat:pose_two_handed_polearm',
                'scythe':'bettercombat:pose_two_handed_scythe'}
def animations(row):
    profile=row.get('base_profile')
    return [attack.get('animation') if attack is not None else None for attack in (profile.get('attacks') or [])] if profile else None
def without_base_animations(row):
    result=copy.deepcopy(row);profile=result.get('base_profile')
    if profile:
        profile.pop('pose',None)
        for attack in profile.get('attacks') or []:
            if attack is not None:attack.pop('animation',None)
    return result
def used_ids(row):
    for key in ('base_profile','explicit_profile'):
        profile=row.get(key)
        if not profile:continue
        for field in ('pose','offHandPose'):
            value=profile.get(field)
            if value:yield value
        for attack in profile.get('attacks') or []:
            if attack and attack.get('animation'):yield attack['animation']

rows=[];changed=[];used=set()
for item_id,old in before.items():
    new=after[item_id];used.update(used_ids(old));used.update(used_ids(new))
    if without_base_animations(old)!=without_base_animations(new):
        raise AssertionError(f'Non-animation, null-profile, item modifiers, explicit preset or reference data changed: {item_id}')
    old_profile=old.get('base_profile');new_profile=new.get('base_profile')
    old_animations=animations(old);new_animations=animations(new)
    changes=[]
    if old_animations is not None:
        for i,(a,b) in enumerate(zip(old_animations,new_animations)):
            attack=old_profile['attacks'][i]
            if attack and attack.get('conditions') and a!=b:
                raise AssertionError(f'Conditional attack animation changed: {item_id} index {i}')
            if a!=b:changes.append({'attack_index':i,'before':a,'after':b})
    shape=base_shape(item_id)
    category=old_profile.get('category') if old_profile else None
    protected=(not item_id.startswith('uwudimension:') or category in ('dagger','trident')
               or shapes.get(shape) in ('dagger','bow') or shape in ('rose_dagger','tidelace_trident','cupid_bow','ripple_bow','glitch_bow'))
    allowed=(item_id.startswith('uwudimension:') and (shape in eligible_relics or shapes.get(shape) in eligible_shapes))
    before_pose=old_profile.get('pose') if old_profile else None
    after_pose=new_profile.get('pose') if new_profile else None
    pose_changed=before_pose!=after_pose
    if (changes or pose_changed) and (protected or not allowed or old.get('base_preset') is not None):
        raise AssertionError(f'Unexpected, protected or explicitly preset visual changed: {item_id}')
    weapon_shape=shapes.get(shape,relic_shapes.get(shape))
    if pose_changed and (not old_profile or old_profile.get('two_handed') is not True
                         or not old_profile.get('isTwoHanded')
                         or weapon_shape not in approved_poses or after_pose!=approved_poses[weapon_shape]):
        raise AssertionError(f'Unapproved pose change: {item_id}: {before_pose} -> {after_pose}')
    if changes or pose_changed:changed.append(item_id)
    rows.append({'id':item_id,'protected':protected,'baseline_profile':old_profile,'current_profile':new_profile,
                 'baseline_pose':before_pose,'current_pose':after_pose,'pose_changed':pose_changed,'animation_changes':changes})
missing=sorted(used-resources)
if missing:raise AssertionError(f'Animation IDs absent from pinned installed-reference JARs: {missing}')
if 'uwudimension:blossom_greatsword' not in changed:
    raise AssertionError('Expected Blossom Great Axe animation correction was not observed')
report={'baseline_sha256':expected_baseline,'baseline_versions':base['versions'],'current_versions':current['versions'],
        'reference_jar_sha256':{p.name:h for p,h in expected_jars.items()},'total_items':len(rows),
        'changed_item_count':len(changed),'changed_items':changed,'resolved_animation_ids':sorted(used),
        'reference_animation_resource_count':len(resources),'items':rows}
Path('weapon-profile-comparison.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(f'UWU WEAPON PROFILE COMPARISON PASS items={len(rows)} changed={len(changed)} resources={len(used)}; approved two-handed poses only; nonvisual fields, offhand pose, nulls, explicit presets, conditional attacks, dagger/trident/bows and references preserved')
