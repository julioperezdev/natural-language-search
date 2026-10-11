#!/usr/bin/env python3
"""Generate the versioned evaluation corpus and its matching SQL catalog fixture."""

import json
from pathlib import Path

VERSION = "1.1.1"
CATALOG_VERSION = "catalog-1.1.0"

CATALOG = [
    ("Remera básica de algodón", "REMERAS", [("BLANCO", "M", 22990, 8), ("BLANCO", "L", 23990, 3),
      ("NEGRO", "M", 24990, 12), ("NEGRO", "L", 25990, 4), ("AZUL", "M", 23990, 5)]),
    ("Remera deportiva dry fit", "REMERAS", [("NEGRO", "M", 27990, 5), ("AZUL", "M", 26990, 7),
      ("BLANCO", "L", 27990, 2)]),
    ("Camisa de lino clásica", "CAMISAS", [("BLANCO", "M", 45990, 3), ("AZUL", "M", 46990, 2)]),
    ("Buzo canguro de frisa", "BUZOS", [("NEGRO", "M", 45990, 6), ("GRIS", "M", 44990, 4),
      ("NEGRO", "L", 47990, 2)]),
    ("Jean slim elastizado", "JEANS", [("AZUL", "40", 59990, 7), ("AZUL", "42", 61990, 4),
      ("NEGRO", "40", 62990, 3)]),
    ("Zapatilla urbana running", "ZAPATILLAS", [("NEGRO", "39", 87990, 2), ("AZUL", "40", 89990, 3),
      ("BLANCO", "39", 84990, 5)]),
    ("Vestido camisero estampado", "VESTIDOS", [("AZUL", "S", 56990, 3), ("NEGRO", "M", 58990, 2)]),
    ("Pantalón jogger deportivo", "PANTALONES", [("NEGRO", "M", 38990, 8), ("GRIS", "M", 37990, 6),
      ("NEGRO", "L", 39990, 3)]),
]
PRODUCTS = [{"name": name, "category": category, "variants": variants}
            for name, category, variants in CATALOG]
COLORS = {"NEGRO": "negras", "BLANCO": "blancas", "AZUL": "azules", "GRIS": "grises"}


def criteria_map(criteria):
    return {part.split("|", 2)[0]: tuple(part.split("|", 2)[1:]) for part in criteria}


def turn(message, criteria=None, action="START_SEARCH", clarification=False,
         reason=None, operation="MESSAGE"):
    criteria = sorted(criteria or [])
    if operation == "RESET_CONTEXT":
        expected = {"outcome": "CONTEXT_RESET", "criteria": [], "productNames": [],
                    "variantColors": [], "variants": [], "contextAction": "RESET_CONTEXT",
                    "clarificationExpected": False}
    elif clarification:
        expected = {"outcome": "NEEDS_CLARIFICATION", "criteria": criteria, "productNames": [],
                    "variantColors": [], "variants": [], "contextAction": "ASK_CLARIFICATION",
                    "clarificationExpected": True, "clarificationReason": reason}
    else:
        filters = criteria_map(criteria)
        matched = []
        for product in PRODUCTS:
            if "category" in filters and filters["category"][1] != product["category"]:
                continue
            for color, size, price, stock in product["variants"]:
                accepted = True
                for field, (operator, value) in filters.items():
                    if field == "color": accepted = color == value
                    elif field == "size": accepted = size == value
                    elif field == "price":
                        bound = int(value)
                        accepted = {"<": price < bound, "<=": price <= bound,
                                    ">": price > bound, ">=": price >= bound,
                                    "=": price == bound}.get(operator, False)
                    if not accepted: break
                if accepted: matched.append((product, color, size, price, stock))
        expected = {
            "outcome": "RESULTS" if matched else "NO_RESULTS",
            "criteria": criteria,
            "productNames": sorted({product["name"] for product, *_ in matched}),
            "variantColors": sorted({color for _, color, *_ in matched}),
            "variants": sorted({f"{color}|{size}|{price}|{stock}"
                                for _, color, size, price, stock in matched}),
            "contextAction": action,
            "clarificationExpected": False,
        }
    return {"message": message, "operation": operation, "expected": expected}


def case(case_id, tags, turns):
    return {"id": case_id, "tags": tags, "turns": turns}


def pilot_cases():
    remeras = ["category|=|REMERAS"]
    black = remeras + ["color|=|NEGRO"]
    white = remeras + ["color|=|BLANCO"]
    black_m = black + ["size|=|M"]
    white_m = white + ["size|=|M"]
    blue_m = remeras + ["color|=|AZUL", "size|=|M"]
    black_l = black + ["size|=|L"]
    return [
        case("black-search-add-price-limit", ["colloquial", "context_add", "context_replace", "price"], [
            turn("Hola, ¿tenés remeras?", remeras), turn("Negras, por favor", black, "ADD_FILTER"),
            turn("Y hasta 26 mil", black + ["price|<=|26000"], "ADD_FILTER"),
            turn("Mejor blancas", white + ["price|<=|26000"], "REPLACE_FILTER")]),
        case("typo-multiple-filters", ["typo", "color", "size"], [
            turn("Busco remeraz negars talle M", black_m)]),
        case("colloquial-broad-color", ["colloquial", "color"], [
            turn("Che, ¿me mostrás las remeras que sean negras?", black)]),
        case("elliptical-budget-context", ["context_add", "budget", "price"], [
            turn("Quiero remeras negras talle M", black_m),
            turn("Solo tengo 26 lucas", black_m + ["price|<=|26000"], "ADD_FILTER")]),
        case("color-replacement-keeps-size", ["context_replace", "color", "size"], [
            turn("Quiero remeras blancas talle M", white_m),
            turn("Mejor las azules", blue_m, "REPLACE_FILTER")]),
        case("self-correction-replaces-size", ["typo", "context_replace", "size"], [
            turn("Busco una remera negraa talle M", black_m),
            turn("Quise decir talle L", black_l, "REPLACE_FILTER")]),
        case("ambiguous-request-clarify-then-resolve", ["ambiguity", "clarification", "context"], [
            turn("Busco algo copado para regalar", [], "ASK_CLARIFICATION", True,
                 "product_and_filter_unspecified"), turn("Una remera negra", black)]),
        case("broad-catalog-discovery", ["broad_discovery", "intent"], [
            turn("¿Qué vendes? Estoy viendo opciones", [])]),
        case("budget-produces-no-results", ["no_results", "price", "color"], [
            turn("Quiero zapatillas negras por menos de 80 mil", ["category|=|ZAPATILLAS",
                 "color|=|NEGRO", "price|<|80000"])]),
        case("category-switch-replaces-all-filters", ["context_replace", "category", "color", "size"], [
            turn("Busco un buzo gris talle M", ["category|=|BUZOS", "color|=|GRIS", "size|=|M"]),
            turn("Mejor zapatillas azules talle 40", ["category|=|ZAPATILLAS", "color|=|AZUL",
                 "size|=|40"], "REPLACE_FILTER")]),
        case("reset-clears-search-context", ["reset", "context_reset", "context"], [
            turn("Quiero remeras negras talle M", black_m),
            turn("reiniciar", operation="RESET_CONTEXT"),
            turn("zapatillas azules talle 40", ["category|=|ZAPATILLAS", "color|=|AZUL", "size|=|40"])]),
        case("restore-first-search-reference", ["history_reference", "context_restore"], [
            turn("Quiero remeras blancas talle M", white_m),
            turn("Mejor negras", black_m, "REPLACE_FILTER"),
            turn("Volvamos a la primera búsqueda", white_m, "RESTORE_SEARCH")]),
    ]


FLOWS = [
    ("REMERAS", "NEGRO", "M", "BLANCO", "L", 24, "remeras"),
    ("REMERAS", "BLANCO", "L", "NEGRO", "M", 25, "remeras"),
    ("REMERAS", "AZUL", "M", "NEGRO", "L", 26, "remeras"),
    ("REMERAS", "NEGRO", "M", "AZUL", "M", 24, "remeras"),
    ("BUZOS", "NEGRO", "M", "GRIS", "M", 45, "buzos"),
    ("BUZOS", "GRIS", "M", "NEGRO", "L", 48, "buzos"),
    ("JEANS", "AZUL", "40", "AZUL", "42", 62, "jeans"),
    ("JEANS", "NEGRO", "40", "AZUL", "42", 62, "jeans"),
    ("ZAPATILLAS", "NEGRO", "39", "AZUL", "40", 90, "zapatillas"),
    ("ZAPATILLAS", "BLANCO", "39", "AZUL", "40", 85, "zapatillas"),
    ("PANTALONES", "NEGRO", "M", "GRIS", "M", 38, "pantalones"),
    ("PANTALONES", "GRIS", "M", "NEGRO", "L", 40, "pantalones"),
    ("VESTIDOS", "AZUL", "S", "NEGRO", "M", 59, "vestidos"),
    ("CAMISAS", "AZUL", "M", "BLANCO", "M", 46, "camisas"),
    ("PANTALONES", "NEGRO", "L", "GRIS", "M", 38, "pantalones"),
]
INITIAL = [
    "Busco {category} {color} talle {size}",
    "¿Tenés {category} de color {color}, en talle {size}?",
    "Quiero {category} {color} para talle {size}, por favor",
    "Ando buscando {category} {color} en talle {size}",
    "Me mostrás {category} color {color}, talla {size}?",
]
REPLACE = ["Mejor {color}.", "En realidad, prefiero {color}.", "Al final las quiero {color}."]
RESIZE = ["¿Y talle {size}?", "Mejor en talle {size}.", "Que sean talle {size}, porfa."]
BUDGET = ["Hasta {limit} mil.", "No más de {limit} lucas.", "Con un tope de {limit} mil."]


def flow_case(case_id, flow, prompt_style, tags, holdout=False):
    category, first_color, first_size, next_color, next_size, limit, category_word = flow
    if holdout:
        starts = [
            "Buenas, ando buscando {category} color {color}, talle {size}",
            "Tendrás {category} {color} para usar en talle {size}?",
            "Necesito {category} {color}; mi talle es {size}",
            "Hay {category} en {color} y talle {size}?",
            "Me interesa algo de {category}, {color}, en {size}",
        ]
        first = starts[prompt_style % len(starts)].format(
            category=category_word, color=COLORS[first_color], size=first_size)
        if "typo" in tags:
            misspelled_category = {
                "REMERAS": "remras", "ZAPATILLAS": "zapatilas", "PANTALONES": "pantalno",
                "BUZOS": "busos", "JEANS": "jens", "VESTIDOS": "vestdo", "CAMISAS": "camisa",
            }[category]
            first = f"Busco {misspelled_category} {COLORS[first_color]} para talle {first_size}"
        change = f"No, mejor en {COLORS[next_color]}"
        resize = f"Buscaba talle {next_size}"
        budget = f"Mi presupuesto llega a {limit} mil"
    else:
        if case_id.endswith(("03", "08", "19", "27")):
            category_word = {"REMERAS": "remras", "ZAPATILLAS": "zapatilas", "PANTALONES": "pantalon",
                             "BUZOS": "busos", "JEANS": "jean", "VESTIDOS": "vestdo",
                             "CAMISAS": "camisa"}[category]
            first = INITIAL[0].format(category=category_word, color=COLORS[first_color], size=first_size)
        else:
            first = INITIAL[prompt_style % len(INITIAL)].format(
                category=category_word, color=COLORS[first_color], size=first_size)
        change = REPLACE[prompt_style % len(REPLACE)].format(color=COLORS[next_color])
        resize = RESIZE[(prompt_style // 2) % len(RESIZE)].format(size=next_size)
        budget = BUDGET[(prompt_style // 3) % len(BUDGET)].format(limit=limit)
    first_filters = [f"category|=|{category}", f"color|=|{first_color}", f"size|=|{first_size}"]
    changed = [f"category|=|{category}", f"color|=|{next_color}", f"size|=|{first_size}"]
    resized = [f"category|=|{category}", f"color|=|{next_color}", f"size|=|{next_size}"]
    return case(case_id, tags, [
        turn(first, first_filters),
        turn(change, changed, "KEEP_FILTERS" if first_color == next_color else "REPLACE_FILTER"),
        turn(resize, resized, "KEEP_FILTERS" if first_size == next_size else "REPLACE_FILTER"),
        turn(budget, resized + [f"price|<=|{limit}000"], "ADD_FILTER"),
    ])


def build():
    pilot = pilot_cases()
    development = pilot.copy()
    development.insert(1, case("change-color-keeps-size", ["context", "context_replace", "regression"], [
        turn("Quiero remeras blancas talle M", ["category|=|REMERAS", "color|=|BLANCO", "size|=|M"]),
        turn("Mejor negras", ["category|=|REMERAS", "color|=|NEGRO", "size|=|M"], "REPLACE_FILTER")]))
    for i in range(32):
        flow = FLOWS[i % len(FLOWS)]
        tags = ["typo", "context", "multi_turn"] if i in {2, 7, 18, 26} else (
            ["colloquial", "context", "multi_turn"] if i % 3 == 0 else ["context", "multi_turn"])
        development.append(flow_case(f"dev-context-flow-{i+1:02d}", flow, i, tags))
    heldout = []
    heldout_flows = [FLOWS[i] for i in [0, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]]
    for i, flow in enumerate(heldout_flows):
        tags = ["typo", "context", "multi_turn"] if i in {1, 5, 9} else (
            ["colloquial", "context", "multi_turn"] if i % 2 else ["context", "multi_turn"])
        heldout.append(flow_case(f"holdout-conversation-{i+1:02d}", flow, i, tags, holdout=True))
    heldout.extend([
        case("holdout-ambiguous-gift-clarification", ["holdout", "ambiguity", "clarification", "recovery"], [
            turn("¿Qué me recomendás para hacer un regalo?", [], "ASK_CLARIFICATION", True,
                 "product_and_filter_unspecified"),
            turn("Un buzo gris, talle M", ["category|=|BUZOS", "color|=|GRIS", "size|=|M"]),
        ]),
        case("holdout-typo-and-correction", ["holdout", "typo", "correction", "color", "size"], [
            turn("Busco zapatilas negras para el 39", ["category|=|ZAPATILLAS", "color|=|NEGRO", "size|=|39"]),
            turn("No, quise decir azules talle 40", ["category|=|ZAPATILLAS", "color|=|AZUL", "size|=|40"],
                 "REPLACE_FILTER"),
        ]),
        case("holdout-english-catalog-query", ["holdout", "multilingual", "color", "size"], [
            turn("Do you have blue jeans in size 40?", ["category|=|JEANS", "color|=|AZUL", "size|=|40"]),
        ]),
    ])
    suites = [
        {"id": "catalog-conversation", "description": "Compatibility regression subset used by the deterministic integration test.",
         "caseIds": ["black-search-add-price-limit", "change-color-keeps-size"]},
        {"id": "catalog-conversation-pilot", "description": "Twelve manually diverse conversations for a live-provider pilot.",
         "caseIds": [item["id"] for item in pilot]},
        {"id": "catalog-conversation-development", "description": "Forty-five development conversations, including the pilot.",
         "caseIds": [item["id"] for item in development]},
        {"id": "catalog-conversation-holdout", "description": "Fifteen conversations reserved for final checks after tuning.",
         "caseIds": [item["id"] for item in heldout]},
    ]
    cases = development + heldout
    assert len(development) == 45 and len(heldout) == 15 and len(cases) == 60
    assert len({item["id"] for item in cases}) == 60
    assert len(suites[1]["caseIds"]) == 12
    return {"version": VERSION, "catalogVersion": CATALOG_VERSION, "suites": suites, "cases": cases}


def sql_quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def build_seed_sql():
    categories = list(dict.fromkeys(category for _, category, _ in CATALOG))
    category_ids = {name: f"e1000000-0000-4000-8000-{index:012d}" for index, name in enumerate(categories, 1)}
    product_rows = []
    variant_rows = []
    product_id = 1
    variant_id = 1
    for name, category, variants in CATALOG:
        current_product_id = f"e1100000-0000-4000-8000-{product_id:012d}"
        product_rows.append((current_product_id, name, category_ids[category]))
        product_id += 1
        for color, size, price, stock in variants:
            current_variant_id = f"e1200000-0000-4000-8000-{variant_id:012d}"
            variant_rows.append((current_variant_id, current_product_id, color, size, price, stock))
            variant_id += 1

    lines = [
        f"-- Generated by evaluation/generate_catalog_conversation_corpus.py for {CATALOG_VERSION}.",
        "-- Use only with a dedicated isolated evaluation database.",
        "SET search_path TO public;",
        "",
        "INSERT INTO categories (id, name) VALUES",
        ",\n".join(f"    ({sql_quote(identifier)}, {sql_quote(name)})" for name, identifier in category_ids.items()),
        "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name;",
        "",
        "INSERT INTO products (id, name, category_id) VALUES",
        ",\n".join(
            f"    ({sql_quote(identifier)}, {sql_quote(name)}, {sql_quote(category_id)})"
            for identifier, name, category_id in product_rows),
        "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, category_id = EXCLUDED.category_id;",
        "",
        "INSERT INTO product_variants (id, product_id, color, size, price, stock) VALUES",
        ",\n".join(
            f"    ({sql_quote(identifier)}, {sql_quote(product)}, {sql_quote(color)}, {sql_quote(size)}, {price}, {stock})"
            for identifier, product, color, size, price, stock in variant_rows),
        "ON CONFLICT (id) DO UPDATE SET product_id = EXCLUDED.product_id, color = EXCLUDED.color,",
        "    size = EXCLUDED.size, price = EXCLUDED.price, stock = EXCLUDED.stock;",
        "",
    ]
    return "\n".join(lines)


if __name__ == "__main__":
    root = Path(__file__).parents[1]
    corpus_path = root / f"src/main/resources/evaluation/catalog-conversation-v{VERSION}.json"
    fixture_version = CATALOG_VERSION.removeprefix("catalog-")
    fixture_path = root / f"src/main/resources/evaluation/catalog-conversation-seed-v{fixture_version}.sql"
    corpus_path.write_text(json.dumps(build(), ensure_ascii=False, indent=2) + "\n")
    fixture_path.write_text(build_seed_sql())
    print(f"Generated {corpus_path}")
    print(f"Generated {fixture_path}")
