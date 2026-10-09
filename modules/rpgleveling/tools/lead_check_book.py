#!/usr/bin/env python3
"""Lead-side INDEPENDENT validator for the Patchouli book.

Deliberately not derived from the author's own checker: it re-derives the rules
from the Patchouli jar facts established during reconnaissance.

Usage: python tools/lead_check_book.py <mod_resources_dir>
"""
import json, os, re, sys

PAGE_TYPES = {
    "text", "crafting", "smelting", "blasting", "smoking", "campfire_cooking",
    "smithing", "stonecutting", "spotlight", "image", "entity", "multiblock",
    "relations", "link", "quest", "empty", "template",
}
CATEGORY_REQUIRED = {"name", "description", "icon"}
ENTRY_REQUIRED = {"name", "category", "icon", "pages"}
# namespaces we may legitimately reference
OK_NS = {"minecraft", "rpgleveling", "patchouli"}

def load(p):
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)

def main(res):
    errs, warns = [], []
    book_root_data = os.path.join(res, "data", "rpgleveling", "patchouli_books")
    book_root_assets = os.path.join(res, "assets", "rpgleveling", "patchouli_books")
    if not os.path.isdir(book_root_data):
        return ["MISSING data/rpgleveling/patchouli_books"], []
    books = sorted(os.listdir(book_root_data))
    print("books found:", books)
    for book in books:
        bj = os.path.join(book_root_data, book, "book.json")
        if not os.path.isfile(bj):
            errs.append(f"{book}: missing data/.../{book}/book.json"); continue
        try:
            b = load(bj)
        except Exception as e:
            errs.append(f"{book}/book.json: invalid JSON: {e}"); continue
        for k in ("name", "landing_text", "use_resource_pack"):
            if k not in b: errs.append(f"{book}/book.json: missing '{k}'")
        if b.get("use_resource_pack") is not True:
            errs.append(f"{book}/book.json: use_resource_pack must be true (1.20+ enforced)")
        if not b.get("model", "").startswith("rpgleveling:"):
            warns.append(f"{book}/book.json: model should be your own, got {b.get('model')!r}")

        abook = os.path.join(book_root_assets, book)
        if not os.path.isdir(abook):
            errs.append(f"{book}: missing assets/.../{book}/"); continue
        langs = sorted(d for d in os.listdir(abook) if os.path.isdir(os.path.join(abook, d)))
        print(f"  {book}: languages {langs}")
        trees = {}
        for lang in langs:
            cats_dir = os.path.join(abook, lang, "categories")
            ents_dir = os.path.join(abook, lang, "entries")
            cats, ents = {}, {}
            if os.path.isdir(cats_dir):
                for dp, _, fns in os.walk(cats_dir):
                    for fn in fns:
                        if not fn.endswith(".json"): continue
                        rel = os.path.relpath(os.path.join(dp, fn), cats_dir).replace("\\", "/")
                        cid = rel[:-5]
                        try: cats[cid] = load(os.path.join(dp, fn))
                        except Exception as e: errs.append(f"{lang} category {rel}: invalid JSON: {e}")
            else:
                errs.append(f"{book}/{lang}: missing categories/")
            if os.path.isdir(ents_dir):
                for dp, _, fns in os.walk(ents_dir):
                    for fn in fns:
                        if not fn.endswith(".json"): continue
                        rel = os.path.relpath(os.path.join(dp, fn), ents_dir).replace("\\", "/")
                        eid = rel[:-5]
                        try: ents[eid] = load(os.path.join(dp, fn))
                        except Exception as ex: errs.append(f"{lang} entry {rel}: invalid JSON: {ex}")
            else:
                errs.append(f"{book}/{lang}: missing entries/")
            trees[lang] = (set(cats), set(ents), cats, ents)
            print(f"    {lang}: {len(cats)} categories, {len(ents)} entries")
            # per-language checks
            for cid, c in cats.items():
                miss = CATEGORY_REQUIRED - set(c)
                if miss: errs.append(f"{lang} category {cid}: missing {sorted(miss)}")
            for eid, e in ents.items():
                miss = ENTRY_REQUIRED - set(e)
                if miss: errs.append(f"{lang} entry {eid}: missing {sorted(miss)}")
                cat = e.get("category", "")
                short = cat.split(":", 1)[-1]
                if short not in cats:
                    errs.append(f"{lang} entry {eid}: category {cat!r} not found (have {sorted(cats)})")
                pages = e.get("pages")
                if not isinstance(pages, list) or not pages:
                    errs.append(f"{lang} entry {eid}: 'pages' must be a non-empty array")
                    continue
                for i, pg in enumerate(pages):
                    t = str(pg.get("type", ""))
                    if not t.startswith("patchouli:"):
                        errs.append(f"{lang} entry {eid} page {i}: type {t!r} lacks 'patchouli:' prefix")
                    elif t.split(":", 1)[1] not in PAGE_TYPES:
                        errs.append(f"{lang} entry {eid} page {i}: unknown page type {t!r}")
                    if t == "patchouli:relations":
                        for r in pg.get("entries", []):
                            rs = str(r).split(":", 1)[-1]
                            if rs not in ents:
                                errs.append(f"{lang} entry {eid}: relations -> unknown entry {r!r}")
        # cross-language structure must be identical
        if len(trees) > 1:
            base = langs[0]
            for lang in langs[1:]:
                if trees[lang][0] != trees[base][0]:
                    errs.append(f"{book}: {lang} categories differ from {base}: "
                                f"only-{lang}={sorted(trees[lang][0]-trees[base][0])} "
                                f"only-{base}={sorted(trees[base][0]-trees[lang][0])}")
                if trees[lang][1] != trees[base][1]:
                    errs.append(f"{book}: {lang} entries differ from {base}: "
                                f"only-{lang}={sorted(trees[lang][1]-trees[base][1])} "
                                f"only-{base}={sorted(trees[base][1]-trees[lang][1])}")
        # recursive id sanity
        def walk_ids(o, path):
            if isinstance(o, dict):
                for k, v in o.items():
                    if k in ("icon", "item", "recipe", "recipe2", "result") and isinstance(v, str) and ":" in v and not v.endswith(".png"):
                        ns = v.split(":", 1)[0]
                        if ns not in OK_NS:
                            warns.append(f"{path}: references foreign namespace {v!r}")
                    walk_ids(v, path)
            elif isinstance(o, list):
                for v in o: walk_ids(v, path)
        for lang in langs:
            _, _, cats, ents = trees[lang]
            for cid, c in cats.items(): walk_ids(c, f"{lang}/categories/{cid}")
            for eid, e in ents.items(): walk_ids(e, f"{lang}/entries/{eid}")
    return errs, warns

res = sys.argv[1]
errs, warns = main(res)
print()
print("=" * 70)
print(f"ERRORS: {len(errs)}   WARNINGS: {len(warns)}")
for e in errs[:60]: print("  ERR ", e)
for w in warns[:30]: print("  WARN", w)
print("RESULT:", "PASS" if not errs else "FAIL")
sys.exit(0 if not errs else 1)
