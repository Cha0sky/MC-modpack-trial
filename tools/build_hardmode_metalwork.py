"""Author the hardmode ore line, a reusable field die, and its two construction orders."""
import json

from build_heritage_quests import ASSETS, DATA, MOD, merge, write
from build_workshop_quests import sprite

METALS = ("cobalt", "palladium")
CHARGES = [MOD + metal + "_furnace_charge" for metal in METALS]
REFINED = [MOD + "refined_" + metal + "_charge" for metal in METALS]
BLOOMS = [MOD + metal + "_bloom" for metal in METALS]
OFFCUT = MOD + "hardmetal_offcut"
UNFINISHED = MOD + "unfinished_field_die"
DIE = MOD + "field_press_die"
STAMP = "{lostworld_field_press:1b}"


def item(item_id):
    return {"item": item_id}


def product(item_id, count=1):
    return {"id": item_id, "count": count}


def write_recipe(name, recipe):
    write(DATA / f"recipe/metalwork/{name}.json", recipe)


def processing(kind, ingredients, results, **parameters):
    return {"type": "create:" + kind, "ingredients": ingredients, "results": results, **parameters}


def recipes():
    for metal, charge, refined, bloom in zip(METALS, CHARGES, REFINED, BLOOMS):
        # Both small shops produce the same feedstock: choose a washing fan or
        # re-use chapter one's cold press and a consumable binder.
        write_recipe("wash_" + metal, processing("splashing", [item(charge)], [product(refined)]))
        write_recipe("bind_" + metal, processing("compacting", [item(charge), item(MOD + "resonant_binder")],
                                                 [product(refined)]))
        write_recipe("fire_" + metal, {"type": "confluence:hellforge", "ingredients": [item(refined)],
                     "result": product(bloom), "experience": 1.0, "requires_fuel": True,
                     "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "confluence"}]})
        write_recipe("cut_" + metal, processing("cutting", [item(bloom)],
                     [product("confluence:" + metal + "_ingot", 3), product(OFFCUT)], processing_time=80,
                     **{"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "confluence"}]}))

    stock = [item(UNFINISHED)]
    held = [product(UNFINISHED)]
    write_recipe("field_press_die", {
        "type": "create:sequenced_assembly", "ingredient": item(OFFCUT), "loops": 1,
        "transitional_item": product(UNFINISHED),
        "sequence": [
            processing("deploying", stock + [item("create:andesite_alloy")], held),
            processing("cutting", stock, held, processing_time=50),
            processing("filling", stock + [{"type": "neoforge:single", "fluid": "minecraft:water", "amount": 250}], held),
            processing("pressing", stock, held),
        ], "results": [product(DIE)],
    })
    for kind, ingredients, result, count in (
        ("rail", ["create:iron_sheet", "create:iron_sheet", "minecraft:stick"], "minecraft:rail", 8),
        ("lamp", ["create:iron_sheet", "minecraft:glass_pane", "minecraft:torch", "minecraft:torch"], "minecraft:lantern", 2),
    ):
        blank = MOD + kind + "_stock"
        write_recipe(kind + "_stock", processing("compacting", [item(i) for i in ingredients], [product(blank)]))
        stamped = {**product(result, count), "components": {
            "minecraft:custom_data": STAMP,
            "minecraft:item_name": json.dumps({"translate": "item.world_progression_core.stamped_" + kind}),
        }}
        write_recipe("stamp_" + kind, processing("deploying", [item(blank), item(DIE)], [stamped], keep_held_item=True))


def stamped_item(items):
    return {"items": items, "predicates": {"minecraft:custom_data": STAMP}}


def advancements():
    stamped = stamped_item(["minecraft:rail", "minecraft:lantern"])
    # Downstream outputs prove earlier steps, including goods taken straight
    # from the end of an automated line. Plain ingots/rails cannot pass this check.
    for record, outputs in (
        ("world_turn/batch_charge", CHARGES + REFINED + BLOOMS + [OFFCUT, UNFINISHED, DIE]),
        ("world_turn/refine_charge", REFINED + BLOOMS + [OFFCUT, UNFINISHED, DIE]),
        ("world_turn/fire_bloom", BLOOMS + [OFFCUT, UNFINISHED, DIE]),
        ("world_turn/cut_batch", [OFFCUT, UNFINISHED, DIE]),
        ("field_equipment/die_ready", [DIE]),
    ):
        criteria = {name: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [predicate]}}
                    for name, predicate in (("done", {"items": outputs}), ("stamped", stamped))}
        write(DATA / f"advancement/{record}.json", {"criteria": criteria, "requirements": [list(criteria)]})
    for name, block in (("lay_rails", "minecraft:rail"), ("hang_lamp", "minecraft:lantern")):
        write(DATA / f"advancement/field_equipment/{name}.json", {"criteria": {"done": {
            "trigger": "minecraft:placed_block", "conditions": {"location": [
                {"condition": "minecraft:match_tool", "predicate": stamped_item(block)},
                {"condition": "minecraft:block_state_property", "block": block},
            ]}}}})


# The pressed charges, grooved blooms, die and blanks must read differently on a belt.
PATTERNS = {
    "charge": [
        "................","................","................","......dddd......",
        ".....dhhhsd.....","...ddhlllhdd....","..dhllddllhsd...","..dhllhhlllsd...",
        ".dhhlllldlllsd..",".dlllllhhlllsd..",".dhhhllllhhhsd..","..dsshhhhsssd...",
        "...dsssssssd....","....ddddddd.....","................","................"],
    "bloom": [
        "................","................","................","..........dddd..",
        ".......dddhhlsd.","....dddhhlllslsd","..ddhhlllsllsssd",
        ".dhhlllsllsssssd",".dlllsllssssssd.",
        ".dlsllssssssdd..",".dsssssssddd....","..dsssddd.......",
        "...ddd..........","................","................","................"],
    "offcut": [
        "................","................","................","..........ddd...",
        "........ddhld...","......ddhllsd...","....ddhllssd....","...dhllssdd.....",
        "..dhllsdd.......","..dlssd...ddd...","...ddd..ddhld...","......ddhlsd....",
        ".....dhlsdd.....",".....dsdd.......","......d.........","................"],
    "die": [
        "................","................","................",".....dddddd.....",
        "...ddhhhhhldd...","..dhhlllllllsd..",".dhllsddddsllsd.",".dllsd....dslsd.",
        ".dlllsddddsllsd.",".dhlhhllllhhlsd.",".dssllhhhhllssd.",
        "..dssddddddssd..","...ddssssssdd...",".....dddddd.....","................","................"],
    "blank": [
        "................","................","................",".......ddddd....",
        ".....ddhhhlsd...","...ddhlllsssd...","..dhlllhhsssd...",".dhllhhllsdd....",
        ".dllhllssd......",".dlllssdd.......","..dssdd.........","...dd...........",
        "................","................","................","................"],
    "rail": [
        "................","................","..........ddd...","........ddhlsd..",
        "......ddhlsdd...","....ddhlsdd.ddd.","...dhlsdd.ddhlsd","..dhlsd.ddhlsdd.",
        ".dhlsdddhlsdd...",".dlsd.dhlsd.....","..dd.dhlsd......","....dhlsd.......",
        "....dsdd........",".....dd.........","................","................"],
    "lamp": [
        "................","................","................","......dddd......",
        "....ddhllsdd....","...dhhlddslld...","...dlsd..dsld...","...dllsddslsd...",
        "...dhlhllhlsd...","....dssllssd....","....dhllhlsd....",".....dssssd.....",
        "......dddd......","................","................","................"],
}


def assets():
    colors = {"cobalt": ("23344b", "4b7498", "a4d5dd", "38506a"),
              "palladium": ("4a3030", "b56f50", "efc69c", "824739"),
              "steel": ("303940", "8799a0", "d1dad0", "56656c"),
              "brass": ("3d342f", "af875d", "e2c89a", "735e4e")}
    entries = [
        ("refined_cobalt_charge", "精炼钴炉料", "Refined Cobalt Charge", "charge", "cobalt"),
        ("refined_palladium_charge", "精炼钯金炉料", "Refined Palladium Charge", "charge", "palladium"),
        ("cobalt_bloom", "钴连铸坯", "Cobalt Bloom", "bloom", "cobalt"),
        ("palladium_bloom", "钯金连铸坯", "Palladium Bloom", "bloom", "palladium"),
        ("hardmetal_offcut", "硬质边料", "Hardmetal Offcut", "offcut", "steel"),
        ("unfinished_field_die", "未完工的夹模", "Incomplete Field Die", "blank", "brass"),
        ("field_press_die", "施工夹模", "Field Press Die", "die", "brass"),
        ("rail_stock", "轨料坯", "Rail Stock", "rail", "steel"),
        ("lamp_stock", "灯座坯", "Lantern Stock", "lamp", "brass"),
    ]
    for key, _, _, shape, metal in entries:
        write(ASSETS / f"models/item/{key}.json", {"parent": "minecraft:item/generated",
              "textures": {"layer0": MOD + "item/" + key}})
        sprite(key, PATTERNS[shape], dict(zip("dlhs", colors[metal])))
    for loc, i, rail, lamp in (("zh_cn", 1, "工坊钢轨", "工坊提灯"), ("en_us", 2, "Workshop Rail", "Workshop Lantern")):
        names = {"item.world_progression_core." + row[0]: row[i] for row in entries}
        names.update({"item.world_progression_core.stamped_rail": rail, "item.world_progression_core.stamped_lamp": lamp})
        merge(ASSETS / f"lang/{loc}.json", names)


def main():
    recipes()
    advancements()
    assets()


if __name__ == "__main__":
    main()
