#!/usr/bin/env python3
"""Self-check for the RPG Leveling Patchouli guide book.

Validates every rule the book has to satisfy, using the facts established from
the Patchouli 1.21.1-93-NEOFORGE jar during reconnaissance:

  1. every JSON file parses (no trailing commas, no comments, UTF-8)
  2. each entry's "category" resolves to a real category (fully qualified)
  3. every category has at least one entry
  4. zh_cn and en_us file trees are exactly identical (same categories, same
     entries, same page counts) - otherwise one language silently loses content
  5. every referenced id (icon / item / recipe) lives in minecraft, rpgleveling
     or patchouli, and is checked against a real registry when one is reachable:
       - minecraft: item ids extracted from the local Minecraft jar lang file
       - rpgleveling: item ids parsed out of RpgItems.java
       - patchouli: item models found inside the Patchouli jar
  6. every page "type" is in the authoritative Patchouli page-type set, and the
     type-specific required fields are present
  7. every referenced texture / model / recipe file really exists
  8. no raw "$" macro survives in the text (Patchouli formatting character)

Usage:
    python tools/validate_patchouli_book.py [--resources src/main/resources]
                                            [--patchouli-jar PATH]

Exit code 0 = PASS, 1 = FAIL.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import zipfile

BOOK_NS = "rpgleveling"
BOOK_ID = "guide"

# From ClientBookRegistry.addPageTypes (javap of the Patchouli jar).
PAGE_TYPES = {
    "text", "crafting", "smelting", "blasting", "smoking", "campfire_cooking",
    "smithing", "stonecutting", "image", "spotlight", "empty", "multiblock",
    "link", "relations", "entity", "quest", "template",
}
PAGE_REQUIRED = {
    "patchouli:text": ("text",),
    "patchouli:crafting": ("recipe",),
    "patchouli:smelting": ("recipe",),
    "patchouli:blasting": ("recipe",),
    "patchouli:smoking": ("recipe",),
    "patchouli:campfire_cooking": ("recipe",),
    "patchouli:smithing": ("recipe",),
    "patchouli:stonecutting": ("recipe",),
    "patchouli:spotlight": ("item",),
    "patchouli:image": ("images",),
    "patchouli:link": ("url",),
    "patchouli:quest": ("trigger",),
    "patchouli:entity": ("name",),
    "patchouli:relations": ("entries",),
}
CATEGORY_REQUIRED = ("name", "description", "icon")
ENTRY_REQUIRED = ("name", "category", "icon", "pages")
BOOK_REQUIRED = ("name", "landing_text", "use_resource_pack", "model",
                 "book_texture", "creative_tab")
ALLOWED_NAMESPACES = {"minecraft", "rpgleveling", "patchouli"}


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.skips: list[str] = []
        self.checks: list[tuple[str, str]] = []

    def err(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)

    def skip(self, msg: str) -> None:
        self.skips.append(msg)

    def ok(self, name: str, detail: str = "") -> None:
        self.checks.append((name, detail))


# --------------------------------------------------------------------------
# reference data
# --------------------------------------------------------------------------

def _candidate_jars(pattern: str) -> list[str]:
    roots = [
        os.path.join(os.environ.get("USERPROFILE", ""), ".gradle", "caches",
                     "neoformruntime", "artifacts"),
        r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\mod test\mods",
        os.path.join("run", "mods"),
        os.path.join("runs", "server", "mods"),
    ]
    found: list[str] = []
    for root in roots:
        if not os.path.isdir(root):
            continue
        for dirpath, _dirnames, filenames in os.walk(root):
            for fn in filenames:
                if fn.endswith(".jar") and re.search(pattern, fn, re.IGNORECASE):
                    found.append(os.path.join(dirpath, fn))
    return found


def _lang_items_from_jar(jar_path: str) -> set[str] | None:
    """Item ids derived from assets/minecraft/lang/en_us.json (bundler aware)."""
    try:
        with zipfile.ZipFile(jar_path) as zf:
            names = zf.namelist()
            target = "assets/minecraft/lang/en_us.json"
            if target in names:
                raw = zf.read(target)
            else:
                nested = [n for n in names if n.endswith(".jar")]
                raw = None
                for n in nested:
                    with zipfile.ZipFile(io.BytesIO(zf.read(n))) as inner:
                        if target in inner.namelist():
                            raw = inner.read(target)
                            break
                if raw is None:
                    return None
        data = json.loads(raw.decode("utf-8"))
    except Exception:
        return None
    ids: set[str] = set()
    for key in data:
        for prefix in ("item.minecraft.", "block.minecraft."):
            if key.startswith(prefix):
                name = key[len(prefix):]
                if "." not in name:          # drop potion/effect sub-keys
                    ids.add(name)
    return ids or None


def vanilla_item_ids(rep: Report) -> set[str] | None:
    for jar in _candidate_jars(r"minecraft.*\.jar$"):
        ids = _lang_items_from_jar(jar)
        if ids:
            rep.ok("vanilla id source", f"{len(ids)} ids from {os.path.basename(jar)}")
            return ids
    rep.skip("vanilla item ids: no local Minecraft jar with "
             "assets/minecraft/lang/en_us.json - minecraft: ids are namespace-checked only")
    return None


def mod_item_ids(resources: str, rep: Report) -> set[str]:
    src = os.path.normpath(os.path.join(resources, os.pardir, "java", "com",
                                        "rpgleveling", "registry", "RpgItems.java"))
    if not os.path.isfile(src):
        rep.warn(f"RpgItems.java not found at {src}; rpgleveling: ids unchecked")
        return set()
    with open(src, "r", encoding="utf-8") as fh:
        text = fh.read()
    ids = set(re.findall(r'ITEMS\.register\(\s*"([a-z0-9_]+)"', text))
    rep.ok("mod id source", f"{sorted(ids)} from RpgItems.java")
    return ids


def patchouli_assets(rep: Report, explicit: str | None) -> set[str] | None:
    jars = [explicit] if explicit else _candidate_jars(r"patchouli")
    for jar in jars:
        if jar and os.path.isfile(jar):
            try:
                with zipfile.ZipFile(jar) as zf:
                    names = {n.replace("\\", "/") for n in zf.namelist()}
                rep.ok("patchouli jar", os.path.basename(jar))
                return names
            except Exception as exc:                       # pragma: no cover
                rep.warn(f"cannot read patchouli jar {jar}: {exc}")
                return None
    rep.skip("patchouli jar not found - patchouli: ids and the book texture are "
             "namespace/path checked only")
    return None


# --------------------------------------------------------------------------
# helpers
# --------------------------------------------------------------------------

def load_json(path: str, rep: Report):
    try:
        with open(path, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except json.JSONDecodeError as exc:
        rep.err(f"{path}: invalid JSON: {exc}")
    except Exception as exc:                               # pragma: no cover
        rep.err(f"{path}: cannot read: {exc}")
    return None


def split_id(value: str) -> tuple[str, str]:
    if ":" in value:
        ns, path = value.split(":", 1)
        return ns, path
    return "minecraft", value


def collect_json(root: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in filenames:
            if fn.endswith(".json"):
                full = os.path.join(dirpath, fn)
                rel = os.path.relpath(full, root).replace("\\", "/")[:-5]
                out[rel] = full
    return out


class Resources:
    """Resolves resource / model / texture / recipe paths in the mod sources."""

    def __init__(self, resources: str) -> None:
        self.res = os.path.abspath(resources)

    def has(self, *relative: str) -> bool:
        return os.path.isfile(os.path.join(self.res, *relative))

    def recipe(self, value: str) -> bool:
        ns, path = split_id(value)
        return self.has("data", ns, "recipe", path + ".json")

    def model(self, value: str) -> bool:
        ns, path = split_id(value)
        return self.has("assets", ns, "models", path + ".json")

    def texture(self, value: str) -> bool:
        ns, path = split_id(value)
        if not path.endswith(".png"):
            return False
        return self.has("assets", ns, path.replace("/", os.sep))


# --------------------------------------------------------------------------
# checks
# --------------------------------------------------------------------------

def page_type_of(page: dict) -> str:
    return str(page.get("type", ""))


def check_book_json(bj: dict, res: Resources, patch: set[str] | None, rep: Report) -> None:
    for key in BOOK_REQUIRED:
        if key not in bj:
            rep.err(f"book.json: missing required field '{key}'")
    if bj.get("use_resource_pack") is not True:
        rep.err("book.json: use_resource_pack must be true (Patchouli 1.20+ rejects the book otherwise)")
    if bj.get("creative_tab") != "rpgleveling:main":
        rep.warn(f"book.json: creative_tab is {bj.get('creative_tab')!r}, expected 'rpgleveling:main'")
    if bj.get("i18n") not in (False, None):
        rep.warn("book.json: i18n is true - book name/landing_text must then be lang keys")

    model = bj.get("model", "")
    if model:
        ns, path = split_id(model)
        if ns not in ALLOWED_NAMESPACES:
            rep.err(f"book.json: model {model!r} uses a foreign namespace")
        # Patchouli prepends "item/" to the model field (ResourceLocation.withPrefix).
        candidates = [path, "item/" + path]
        if not any(res.model(f"{ns}:{c}") for c in candidates):
            rep.err(f"book.json: model {model!r} resolves to no "
                    f"assets/{ns}/models/{{{','.join(candidates)}}}.json")

    texture = bj.get("book_texture", "")
    if texture:
        ns, path = split_id(texture)
        inside = f"assets/{ns}/{path}"
        if res.texture(texture):
            rep.ok("book texture", texture)
        elif patch is not None and inside in patch:
            rep.ok("book texture", f"{texture} (inside patchouli jar)")
        elif patch is None:
            rep.warn(f"book.json: book_texture {texture!r} not verified (no patchouli jar)")
        else:
            rep.err(f"book.json: book_texture {texture!r} not found in the mod or the patchouli jar")


def check_category(cid: str, cat: dict, lang: str, res: Resources, patch: set[str] | None,
                   vanilla: set[str] | None, mod_ids: set[str], rep: Report) -> None:
    where = f"{lang}/categories/{cid}"
    for key in CATEGORY_REQUIRED:
        if key not in cat:
            rep.err(f"{where}: missing required field '{key}'")
    if not isinstance(cat.get("sortnum", 0), int):
        rep.warn(f"{where}: sortnum should be an integer")
    if "parent" in cat:
        rep.warn(f"{where}: uses 'parent' - parent must also be fully qualified")
    check_icon(cat.get("icon"), where, res, patch, vanilla, mod_ids, rep)
    if not str(cat.get("description", "")).strip():
        rep.warn(f"{where}: empty description")


def check_icon(icon, where: str, res: Resources, patch: set[str] | None,
               vanilla: set[str] | None, mod_ids: set[str], rep: Report) -> None:
    if icon is None:
        return
    if not isinstance(icon, str) or not icon:
        rep.err(f"{where}: icon must be a non-empty string")
        return
    if icon.endswith(".png"):
        ns, path = split_id(icon)
        if not icon.startswith("assets/"):
            candidates = [f"assets/{ns}/{path}", f"assets/{ns}/textures/{path}"]
            if any(res.has(*c.split("/")) for c in candidates):
                return
            if patch is not None and any(c in patch for c in candidates):
                return
            rep.err(f"{where}: icon texture {icon!r} does not exist")
        return
    ns, path = split_id(icon)
    if ns not in ALLOWED_NAMESPACES:
        rep.err(f"{where}: icon {icon!r} is outside the allowed namespaces {sorted(ALLOWED_NAMESPACES)}")
        return
    if ns == "minecraft" and vanilla is not None:
        if path not in vanilla:
            rep.err(f"{where}: icon {icon!r} is not a known vanilla item id")
    elif ns == "rpgleveling":
        if mod_ids and path not in mod_ids:
            rep.err(f"{where}: icon {icon!r} is not a registered rpgleveling item")
        elif not mod_ids:
            rep.warn(f"{where}: icon {icon!r} could not be checked against RpgItems.java")
    elif ns == "patchouli":
        model = f"assets/patchouli/models/item/{path}.json"
        if patch is not None and model not in patch:
            rep.err(f"{where}: icon {icon!r} has no model {model} in the patchouli jar")


def check_pages(eid: str, pages, lang: str, res: Resources, patch: set[str] | None,
                vanilla: set[str] | None, mod_ids: set[str], rep: Report) -> None:
    where = f"{lang}/entries/{eid}"
    if not isinstance(pages, list) or not pages:
        rep.err(f"{where}: 'pages' must be a non-empty array")
        return
    for index, page in enumerate(pages):
        pwhere = f"{where} page {index}"
        if not isinstance(page, dict):
            rep.err(f"{pwhere}: page must be an object")
            continue
        ptype = page_type_of(page)
        if not ptype.startswith("patchouli:"):
            rep.err(f"{pwhere}: type {ptype!r} must be prefixed with 'patchouli:'")
            continue
        short = ptype.split(":", 1)[1]
        if short not in PAGE_TYPES:
            rep.err(f"{pwhere}: unknown page type {ptype!r} (authoritative set: {sorted(PAGE_TYPES)})")
            continue
        for key in PAGE_REQUIRED.get(ptype, ()):
            if key not in page:
                rep.err(f"{pwhere}: {ptype} requires field '{key}'")
        if ptype == "patchouli:text":
            text = page.get("text")
            if isinstance(text, str):
                flat = text
            elif isinstance(text, list) and all(isinstance(t, str) for t in text):
                flat = "\n".join(text)
            else:
                rep.err(f"{pwhere}: 'text' must be a string or an array of strings")
                continue
            if "$" in flat:
                rep.err(f"{pwhere}: raw '$' found - Patchouli treats it as a formatting macro")
            if "title" in page and not str(page["title"]).strip():
                rep.warn(f"{pwhere}: empty title")
        elif ptype == "patchouli:crafting":
            for key in ("recipe", "recipe2"):
                value = page.get(key)
                if isinstance(value, str) and value:
                    if not res.recipe(value):
                        rep.err(f"{pwhere}: recipe {value!r} has no data/<ns>/recipe/<path>.json")
        elif ptype == "patchouli:spotlight":
            check_icon(page.get("item"), pwhere, res, patch, vanilla, mod_ids, rep)
        elif ptype == "patchouli:relations":
            for target in page.get("entries", []) or []:
                if not isinstance(target, str) or ":" not in target:
                    rep.err(f"{pwhere}: relations target {target!r} must be a fully qualified entry id")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resources", default=os.path.join("src", "main", "resources"))
    parser.add_argument("--patchouli-jar", default=None)
    args = parser.parse_args()

    rep = Report()
    res = Resources(args.resources)
    if not os.path.isdir(args.resources):
        print(f"FATAL: resources dir not found: {args.resources}")
        return 1

    book_data = os.path.join(args.resources, "data", BOOK_NS, "patchouli_books", BOOK_ID)
    book_assets = os.path.join(args.resources, "assets", BOOK_NS, "patchouli_books", BOOK_ID)
    for root in (book_data, book_assets):
        if not os.path.isdir(root):
            print(f"FATAL: missing {root}")
            return 1

    vanilla = vanilla_item_ids(rep)
    mod_ids = mod_item_ids(args.resources, rep)
    patch = patchouli_assets(rep, args.patchouli_jar)

    # ---- 1. book.json -----------------------------------------------------
    book_path = os.path.join(book_data, "book.json")
    book = load_json(book_path, rep)
    if book is None:
        rep.err("book.json is missing or unreadable")
        book = {}
    else:
        check_book_json(book, res, patch, rep)
        rep.ok("book.json parsed", str(book.get("name")))

    # ---- 2. per-language trees -------------------------------------------
    languages = sorted(d for d in os.listdir(book_assets)
                       if os.path.isdir(os.path.join(book_assets, d)))
    if len(languages) < 2:
        rep.err(f"expected at least two language folders, found {languages}")
    trees: dict[str, tuple[dict, dict, dict[str, str], dict[str, str]]] = {}
    for lang in languages:
        cat_root = os.path.join(book_assets, lang, "categories")
        ent_root = os.path.join(book_assets, lang, "entries")
        cat_files = collect_json(cat_root) if os.path.isdir(cat_root) else {}
        ent_files = collect_json(ent_root) if os.path.isdir(ent_root) else {}
        if not cat_files:
            rep.err(f"{lang}: no category JSON files under categories/")
        if not ent_files:
            rep.err(f"{lang}: no entry JSON files under entries/")
        cats = {cid: load_json(p, rep) for cid, p in cat_files.items()}
        ents = {eid: load_json(p, rep) for eid, p in ent_files.items()}
        trees[lang] = (cat_files, ent_files, cats, ents)

        for cid, cat in cats.items():
            if cat is not None:
                check_category(cid, cat, lang, res, patch, vanilla, mod_ids, rep)
        for eid, ent in ents.items():
            if ent is None:
                continue
            where = f"{lang}/entries/{eid}"
            for key in ENTRY_REQUIRED:
                if key not in ent:
                    rep.err(f"{where}: missing required field '{key}'")
            cat = ent.get("category")
            if not isinstance(cat, str) or ":" not in cat:
                rep.err(f"{where}: 'category' must be fully qualified (domain:name), got {cat!r}")
            else:
                short = cat.split(":", 1)[1]
                if short not in cats:
                    rep.err(f"{where}: category {cat!r} does not resolve to a category file")
            check_icon(ent.get("icon"), where, res, patch, vanilla, mod_ids, rep)
            check_pages(eid, ent.get("pages"), lang, res, patch, vanilla, mod_ids, rep)

        # a category must not be empty
        used = {str(e.get("category", "")).split(":", 1)[-1] for e in ents.values() if e}
        for cid in cats:
            if cid not in used:
                rep.err(f"{lang}: category {cid!r} has no entries")

    # ---- 3. cross-language structure -------------------------------------
    if len(trees) > 1:
        base = languages[0]
        base_cats, base_ents = trees[base][0], trees[base][1]
        for lang in languages[1:]:
            cats, ents = trees[lang][0], trees[lang][1]
            if set(cats) != set(base_cats):
                rep.err(f"{lang} categories differ from {base}: "
                        f"only-{lang}={sorted(set(cats) - set(base_cats))} "
                        f"only-{base}={sorted(set(base_cats) - set(cats))}")
            if set(ents) != set(base_ents):
                rep.err(f"{lang} entries differ from {base}: "
                        f"only-{lang}={sorted(set(ents) - set(base_ents))} "
                        f"only-{base}={sorted(set(base_ents) - set(ents))}")
            for eid in set(ents) & set(base_ents):
                a = trees[base][3][eid]
                b = trees[lang][3][eid]
                if not isinstance(a, dict) or not isinstance(b, dict):
                    continue
                na = len(a.get("pages") or [])
                nb = len(b.get("pages") or [])
                if na != nb:
                    rep.err(f"entry {eid}: {base} has {na} pages but {lang} has {nb}")
                sa = [page_type_of(p) for p in (a.get("pages") or []) if isinstance(p, dict)]
                sb = [page_type_of(p) for p in (b.get("pages") or []) if isinstance(p, dict)]
                if sa != sb:
                    rep.err(f"entry {eid}: page type sequence differs ({base}={sa}, {lang}={sb})")
        if not [e for e in rep.errors]:
            rep.ok("language trees", f"{languages} identical "
                                     f"({len(base_cats)} categories, {len(base_ents)} entries)")

    # ---- summary ---------------------------------------------------------
    counts = {lang: (len(trees[lang][0]), len(trees[lang][1])) for lang in languages}
    print("Patchouli guide book self-check")
    print(f"  book           : {book.get('name')!r} (data/{BOOK_NS}/patchouli_books/{BOOK_ID}/book.json)")
    for lang, (nc, ne) in counts.items():
        pages = sum(len((trees[lang][3][e].get("pages") or []))
                    for e in trees[lang][3] if isinstance(trees[lang][3][e], dict))
        print(f"  {lang:<14}: {nc} categories, {ne} entries, {pages} pages")
    print("  checks passed  :")
    for name, detail in rep.checks:
        print(f"    - {name:<22} {detail}")
    if rep.skips:
        print("  skipped        :")
        for line in rep.skips:
            print(f"    - {line}")
    if rep.warnings:
        print(f"  warnings ({len(rep.warnings)}):")
        for line in rep.warnings:
            print(f"    ! {line}")
    print()
    print("=" * 70)
    print(f"ERRORS: {len(rep.errors)}   WARNINGS: {len(rep.warnings)}   SKIPPED: {len(rep.skips)}")
    for line in rep.errors:
        print(f"  ERR {line}")
    print("RESULT:", "PASS" if not rep.errors else "FAIL")
    return 0 if not rep.errors else 1


if __name__ == "__main__":
    sys.exit(main())
