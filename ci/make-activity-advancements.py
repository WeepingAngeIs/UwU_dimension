"""Additive, stable activity IDs. Never rewrite the existing root, recipes or lore."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
base=ROOT/'src/main/resources/data/uwudimension/advancement'
langpath=ROOT/'src/main/resources/assets/uwudimension/lang/en_us.json'
lang=json.loads(langpath.read_text(encoding='utf-8'))
catalog=[]
def native(trigger,**conditions):return {'trigger':'minecraft:'+trigger,'conditions':conditions}
def inventory(*items):return native('inventory_changed',items=[{'items':['uwudimension:'+i for i in items]}])
def eaten(*items):return native('consume_item',item={'items':['uwudimension:'+i for i in items]})
def placed(block):return native('placed_block',location=[{'condition':'minecraft:block_state_property','block':'uwudimension:'+block}])
def recipe(id):return native('recipe_crafted',recipe_id='uwudimension:'+id)
def trade(faction):return native('villager_trade',villager={'type':'uwudimension:'+faction+'_keeper'})
def visit(biome):return native('location',player={'location':{'dimension':'uwudimension:sakura_reach','biomes':biome}})
def add(id,title,description,icon,parent,criteria,frame='task',all=True):
    if isinstance(criteria,str):criteria={'done':{'trigger':'minecraft:impossible'}}
    elif 'trigger' in criteria:criteria={'done':criteria}
    data={'parent':'uwudimension:'+('a_rather_pretty_place' if parent is None else 'activities/'+parent),
      'display':{'icon':{'id':'uwudimension:'+icon},'title':{'translate':'advancements.uwudimension.activities.'+id+'.title'},
      'description':{'translate':'advancements.uwudimension.activities.'+id+'.description'},'frame':frame,'show_toast':True,'announce_to_chat':False,'hidden':False},
      'criteria':criteria,'requirements':[[k] for k in criteria] if all else [list(criteria)]}
    path=base/'activities'/Path(id+'.json');path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    lang['advancements.uwudimension.activities.'+id+'.title']=title
    lang['advancements.uwudimension.activities.'+id+'.description']=description
    catalog.append({'id':'uwudimension:activities/'+id,'title':title,'description':description,'parent':data['parent'],
        'criteria':criteria,'requirements':data['requirements'],'icon':data['display']['icon']['id'],'frame':frame})

exploration=[
 ('canopy_club','Canopy Appreciation Society','Visit a Cherry Grove in Sakura Reach.','giant_sakura_sapling','minecraft:cherry_grove',None),
 ('ribbon_bluffs','A Cliffhanger With Good Lighting','Visit the Ribbon Bluffs in Sakura Reach.','roseglass_prism','uwudimension:ribbon_bluffs','journey/canopy_club'),
 ('velvet_highlands','Pastel Is a State of Altitude','Visit the Velvet Highlands in Sakura Reach.','vesper_opal','uwudimension:velvet_highlands','journey/ribbon_bluffs'),
 ('lantern_fens','Swamp, But Make It Cozy','Visit the Lantern Fens in Sakura Reach.','heart_neon_lantern','uwudimension:lantern_fens','journey/canopy_club'),
 ('blush_reef','Underwater, Overdressed','Visit the Blush Reef in Sakura Reach.','blush_shellstone','uwudimension:blush_reef','journey/lantern_fens'),
 ('moonwake_trench','Abyss-solutely Adorable','Visit the Moonwake Trench in Sakura Reach.','moonwake_shellstone','uwudimension:moonwake_trench','journey/blush_reef'),
 ('starlace_frostlands','Cold Hands, Warm Vibes','Visit the Starlace Frostlands in Sakura Reach.','frostpetal','uwudimension:starlace_frostlands','journey/canopy_club'),
 ('dusk_frostlands','The Snow Has Entered Its Goth Era','Visit the Dusk Frostlands in Sakura Reach.','rough_vesper_opal','uwudimension:dusk_frostlands','journey/starlace_frostlands')]
for slug,title,desc,icon,biome,parent in exploration:add('journey/'+slug,title,desc,icon,parent,visit(biome))
add('journey/scenic_route','Taking the Scenic Route Very Seriously','Visit the Blush Reef, Ribbon Bluffs and both frostlands in Sakura Reach.',
    'heartwater_locket','journey/canopy_club',{s:visit(b) for s,b in [('reef','uwudimension:blush_reef'),('bluffs','uwudimension:ribbon_bluffs'),('petal_snow','uwudimension:starlace_frostlands'),('dusk_snow','uwudimension:dusk_frostlands')]},'goal')

seeds=['heartberry_seeds','sakura_rice_seeds','starfruit_seeds','frostpetal_seeds','sweetbean_seeds','glowginger_seeds','vanilla_bean_seeds']
crops=['heartberry_crop','sakura_rice_crop','starfruit_crop','frostpetal_crop','sweetbean_crop','glowginger_crop','vanilla_bean_crop']
add('garden/seeds','Gardening Starter Pack','Obtain any native pantry seed.','sakura_rice_seeds',None,inventory(*seeds))
add('garden/plant','A Plot Twist','Successfully plant a native pantry crop.','heartberry_seeds','garden/seeds',{c:placed(c) for c in crops},all=False)
add('garden/harvest','An Honest Day\'s UwU','Harvest a fully mature native pantry crop by breaking it.','sakura_rice','garden/plant','event')
add('garden/fruit','Pick Me, Literally','Pick a visible fruit cluster from native fruiting leaves.','moonberry','garden/harvest','event')
add('garden/sapling','The Fine Print Said Giant','Obtain a giant sakura sapling.','giant_sakura_sapling','garden/seeds',inventory('giant_sakura_sapling'))
add('garden/giant','I Thought You Said Houseplant','Use bone meal to successfully grow Anya\'s authored giant sakura. Natural trees and unattended growth do not count.','giant_sakura_sapling','garden/sapling','event','goal')
add('garden/chakram','Crop Circles, but Helpful','Harvest a mature crop with a returning physical chakram.','sakura_rice','garden/harvest','event','goal')

add('pantry/snack','Locally Sourced Munchies','Eat a native crop ingredient.','heartberry',None,eaten('heartberry','sakura_rice','starfruit','frostpetal','sweetbean_pods','glowginger','vanilla_bean'))
add('pantry/seeds','The Seed Packet Was a Snack','Eat any native pantry seed.','sweetbean_seeds','pantry/snack',eaten(*seeds))
add('pantry/burger','Stack Overflow, but Lunch','Eat a Heartberry Burger.','heartberry_burger','pantry/snack',eaten('heartberry_burger'))
add('pantry/hotpot','Soup-er-vised Chaos','Eat a Cozy Udon Hotpot.','cozy_udon_hotpot','pantry/snack',eaten('cozy_udon_hotpot'))
add('pantry/dessert','Professionally Distracted by Dessert','Eat sakura mochi ice cream, a maple sundae or a heart bar.','sakura_mochi_ice_cream','pantry/snack',eaten('sakura_mochi_ice_cream','maple_ice_cream_sundae','nanaimo_heart_bar'))
add('pantry/picnic','A Very Important Little Picnic','Eat a Cherry Picnic Bento.','cherry_picnic_bento','pantry/snack',eaten('cherry_picnic_bento'))
add('pantry/three_course','Balanced Diet, Unbalanced Vibes','Enjoy a Heartberry Burger, Cozy Udon Hotpot and sakura mochi ice cream.','cherry_picnic_bento','pantry/burger',
    {'burger':eaten('heartberry_burger'),'hotpot':eaten('cozy_udon_hotpot'),'dessert':eaten('sakura_mochi_ice_cream')},'goal')

for faction,gem,title in [('petalweave','roseglass_prism','My Emotional Support Sparkle'),('nightbriar','vesper_opal','A Tiny Gothic Shopping Trip')]:
    stem='wardrobe/'+faction
    add(stem+'_gem',title,'Obtain '+('a Roseglass Prism.' if faction=='petalweave' else 'a Vesper Opal.'),gem,None,inventory(gem))
    add(stem+'_crafted','Sewn With Main-Character Energy' if faction=='petalweave' else 'Dress Code: Moonlight',
        'Personally craft all four '+('Petalweave' if faction=='petalweave' else 'Nightbriar')+' armor pieces.',faction+'_chestplate',stem+'_gem',
        {part:recipe(faction+'_'+part) for part in ['helmet','chestplate','leggings','boots']},'goal')
    add(stem+'_worn','Soft, but Armored' if faction=='petalweave' else 'Gothic, With Benefits',
        'Wear a complete matching '+('Petalweave' if faction=='petalweave' else 'Nightbriar')+' armor set.',faction+'_helmet',stem+'_gem',
        native('location',player={'equipment':{slot:{'items':['uwudimension:'+faction+'_'+part]} for slot,part in [('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')]}}),'goal')

add('community/petal_trade','Retail Therapy: Petal Edition','Complete a trade with a Petal Keeper.','roseglass_prism',None,trade('petal'))
add('community/dusk_trade','Retail Therapy: After Dark','Complete a trade with a Dusk Keeper.','vesper_opal','community/petal_trade',trade('dusk'))
add('community/both_trades','Diplomacy With Receipts','Complete at least one trade with each Keeper faction.','keeper_figurine','community/petal_trade',{'petal':trade('petal'),'dusk':trade('dusk')},'goal')
add('community/contribute','The Group Project Enjoyer','Contribute supplies to a native village commons project at its bell.','heart_neon_lantern','community/petal_trade','event')
add('community/keepsake','Certified Community Bean','Collect an earned commons project keepsake as a contributor.','keeper_figurine','community/contribute','event','goal')
add('community/court','Court Adjourned, Sparkles Pending','Earn your own Mirror Court victory seal as a contributing participant.','vesper_opal','community/both_trades','event','challenge')

add('guardians/feed','Paid in Snacks','Successfully feed an eligible adult Owl Guardian a native crop.','heartberry',None,'event')
add('guardians/family','Tiny Guardian, Huge Responsibilities','Help feed a pair of Owl Guardians that successfully welcomes a child.','uwu_guardian_spawn_egg','guardians/feed','event','goal')
add('guardians/together','It Takes Two Snack Dealers','Welcome an Owl Guardian child with a different player feeding the other parent. Both helpers earn this.','heartberry','guardians/family','event','goal')

add('utility/lantern_crafted','Heartware Engineer','Craft a Heart Neon Lantern using either supported recipe.','heart_neon_lantern',None,
    {'original':recipe('heart_neon_lantern'),'silver':recipe('heart_neon_lantern_from_silver')},all=False)
add('utility/lantern_placed','Home Is Where the Heart Glows','Place a Heart Neon Lantern.','heart_neon_lantern','utility/lantern_crafted',placed('heart_neon_lantern'))
add('utility/dusk_lantern','Mood Lighting Is a Personality','Place a Dusk Heart Lantern.','dusk_heart_lantern','utility/lantern_placed',placed('dusk_heart_lantern'))
add('utility/hammer','A Minor Mining Incident','Use the Bell Hammer\'s area mining to successfully break an extra block. Protected or failed breaks do not count.','bell_hammer','utility/lantern_placed','event')
add('utility/return','It Came Back With the Milk','Receive your returning physical relic back into your hand or inventory.','lantern_spear','utility/lantern_placed','event')

add('cupid/bow','A Bow With Excellent Intentions','Obtain a Cupid Bow.','cupid_bow',None,inventory('cupid_bow'))
add('cupid/charm','Friend Request Sent','Successfully charm an eligible creature or player with Cupid magic.','cupid_bow','cupid/bow','event')
add('cupid/player','Multiplayer Friendship Protocol','Successfully charm an eligible survival player with Cupid magic. PvP rules still apply.','cupid_bow','cupid/charm','event')
add('cupid/wish','Two Hearts, One Tiny Friend','Complete a Cupid wish that actually creates one viable baby creature.','heartberry','cupid/charm','event','goal')
add('cupid/player_wish','Plus One, Plus Moo','Complete a successful wish with a charmed, opted-in player and a viable creature.','sakura_mochi_ice_cream','cupid/wish','event','goal')
add('cupid/witness','Witness Protection, but Cute','Opt in and participate as the charmed player in a successful creature wish.','heartberry','cupid/player','event')
add('cupid/command','Besties With a Battle Plan','Have a charmed ally actually damage the foe you designated. A targeting message alone does not count.','cupid_bow','cupid/charm','event','goal')

langpath.write_text(json.dumps(lang,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
catalogpath=ROOT/'docs/ACTIVITY-ADVANCEMENTS.json';catalogpath.write_text(json.dumps(catalog,indent=2)+'\n')
lines=['UwU Dimension activity achievements','',str(len(catalog))+' additive achievements. Existing a_rather_pretty_place and every recipe advancement are preserved.','',
    'No XP, items, functions, recipe rewards, global chat announcements or repeat rewards are added. IDs and criterion names are stable; existing earned progress is never reset.','',
    'Children can be earned before their parent; the existing Sakura Reach root controls tab visibility. Native criteria retain ordinary Minecraft creative-mode semantics. Server hook awards exclude dead/spectator actors.','',
    'Lore-book acquisition, delivery, text and art are outside this change. No new image/audio/model assets.','']
for e in catalog:lines.extend([e['title'],e['id'],e['description'],'Trigger: '+', '.join(sorted({c['trigger'] for c in e['criteria'].values()})),''])
(ROOT/'docs/ACTIVITY-ADVANCEMENTS.txt').write_text('\n'.join(lines),encoding='utf-8')
print('Wrote',len(catalog),'additive activity advancements')
