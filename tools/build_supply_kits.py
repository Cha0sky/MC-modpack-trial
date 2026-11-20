"""Write the two supply crate recipes, parcel sprites and item tooltips."""

from build_heritage_quests import ASSETS, DATA, MOD, merge, write
from build_workshop_quests import sprite


def make_crate(name, ingredients, label, tooltip, emblem, palette):
    write(DATA / f"recipe/{name}.json", {
        "type": "create:compacting",
        "ingredients": [{"item": item} for item, count in ingredients for _ in range(count)],
        "results": [{"id": MOD + name, "count": 1}],
    })
    write(ASSETS / f"models/item/{name}.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": MOD + "item/" + name}})
    key = "item.world_progression_core." + name
    for i, locale in enumerate(("zh_cn", "en_us")):
        merge(ASSETS / f"lang/{locale}.json", {key: label[i], key + ".contents": tooltip[i]})

    # Each paper parcel carries a five-pixel emblem on its front label.
    rows = [
        "................", "................", "....ddddddddd...",
        "...dltttllttld..", "..dltttlltttld..", ".dddddddddddddd.",
    ]
    for mark in emblem:
        rows.append(".dslt" + mark + "tltsd.")
    rows += [".dsltbbbbbtltsd.", ".dsslllssslssld.", "..ddddddddddddd.",
             "................", "................"]
    sprite(name, rows, palette)


def main():
    make_crate("trail_supply_crate", [
        (MOD + "sealed_ration", 2), ("minecraft:coal", 2), ("minecraft:stick", 2), ("minecraft:paper", 1)],
        ("野外补给包", "Trail Supply Crate"),
        ("右键拆包：2 份热封行军餐、8 支火把。", "Open to unpack 2 sealed field rations and 8 torches."),
        ["bbeeb", "beeeb", "bbeeb", "bbebb", "bbebb"],
        {"d": "413529", "s": "775c42", "l": "f3cd8f", "t": "c39964", "b": "536044", "e": "ffd275"})
    make_crate("rescue_supply_crate", [
        ("minecraft:golden_apple", 1), ("minecraft:bamboo", 6), ("minecraft:string", 1), ("minecraft:paper", 1)],
        ("救援补给包", "Rescue Supply Crate"),
        ("右键拆包：1 个金苹果、6 个脚手架。", "Open to unpack 1 golden apple and 6 scaffolding."),
        ["bbebb", "bbebb", "eeeee", "bbebb", "bbebb"],
        {"d": "363f3e", "s": "776247", "l": "ead7a3", "t": "b29c73", "b": "306969", "e": "f7edcd"})


if __name__ == "__main__":
    main()
