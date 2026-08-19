"""Fase 03 — Catálogo: produtos SIMPLES + kits virtuais.

Evolução de scripts/generate_products_seed.py (mesmas categorias/marcas/faixas de preço), com
duas correções importantes em relação ao script original:
  1. NÃO insere `stock_balance`/`stock_movement` para nada aqui — isso é derivado das fases de
     compras/vendas (05-07), senão o saldo inicial não bate com nenhum recebimento real.
  2. NÃO insere `stock_balance` para o SKU do próprio kit — kit é virtual (EST-F015): nunca tem
     saldo próprio, só os componentes têm. O script original tinha esse bug.
"""
import random
import sys

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import money, sqb, sqn, sqs, write_json, write_sql

CATEGORIES_CONFIG = [
    ("Sedas", 3.0, 15.0,
     ["Zomo", "Bem Bolado", "Smoking", "Raw", "Elements", "Lion Rolling Circus", "Papel de Arroz", "Pay-Pay"],
     ["Seda Slim King Size", "Seda Brown King Size", "Seda Orgânica 1 1/4", "Seda Prata King Size", "Seda Deluxe", "Seda Alfavaca", "Seda Hemp Paper", "Seda Celulose Extra Fine", "Seda Master Size", "Seda Unbleached Ultra Thin"]),
    ("Piteiras", 10.0, 25.0,
     ["Yellow Finger", "Bem Bolado", "Raw", "Squadafum", "Gorilla", "Hippie Bong", "Girls in Green", "Papel de Arroz"],
     ["Piteira de Papel Large", "Piteira de Vidro 6mm", "Piteira de Vidro 7mm Bocal Flat", "Piteira Extra Longa", "Piteira de Algodão Biodegradável", "Piteira Murano 8mm", "Piteira Perfurada Super Slim", "Piteira de Madeira Ecológica"]),
    ("Isqueiros", 7.0, 35.0,
     ["Clipper", "Bic", "Zippo", "Maçarico Zen", "Honest", "Atomic"],
     ["Isqueiro Recarregável Classic", "Isqueiro Maçarico Single Jet", "Isqueiro Maçarico Double Jet", "Isqueiro Maxi Colecionável", "Isqueiro Slim Metallic", "Isqueiro Eletrônico USB Plasma", "Isqueiro de Fluído Vintage"]),
    ("Dechavador", 15.0, 65.0,
     ["Squadafum", "Kings", "Trio", "Metal Head", "Bros", "Bem Bolado", "Kush", "Elements"],
     ["Dechavador de Metal 3 Fases 40mm", "Dechavador de Policarbonato", "Dechavador de Madeira com Imã", "Dechavador de Zinco 50mm", "Dechavador com Manivela", "Dechavador Dente de Tubarão 60mm", "Dechavador Magnético Compacto"]),
    ("Slicks", 8.0, 30.0,
     ["Squadafum", "SlickContainer", "OilSlick", "Puff Life", "Bros"],
     ["Slick de Silicone 5ml", "Slick de Silicone 10ml Divisória", "Slick de Silicone Oil 25ml", "Slick Pote de Vidro com Tampa Hermética", "Slick Formato Tambor 15ml", "Slick Silicube 7ml"]),
    ("Cuia", 12.0, 35.0,
     ["Squadafum", "Cultiva", "Bros", "Puff Life", "Bem Bolado"],
     ["Cuia de Silicone Flexível", "Cuia de Silicone Antiaderente Large", "Cuia Estampada Art", "Cuia de Silicone Dobrável", "Cuia Glow In The Dark"]),
    ("Tesoura", 12.0, 35.0,
     ["Buddha", "Dobrável", "Squadafum", "Bros", "Nip", "Herbs"],
     ["Tesoura Dobrável de Precisão", "Tesoura Mola Inox", "Tesoura Ponta Curva Gold", "Tesoura Titanium Antiaderente", "Tesoura Micro Serrilhada"]),
    ("Bags", 35.0, 150.0,
     ["Puff Life", "Case Hard", "Squadafum", "Bem Bolado", "Slyng", "Ultra", "Bag-It"],
     ["Case Anti Odor Compact", "Case Hard Shell Slim", "Shoulder Bag Tabacaria Premium", "Case Organizador Double ZIP", "Case Camuflada Impermeável", "Mini Bag Porta Acessórios"]),
    ("Essências", 12.0, 28.0,
     ["Zomo", "Ziggy", "Nayak", "Onix", "Adalya", "Tangiers", "Fumari", "Smyrna"],
     ["Essência Mint Freeze 50g", "Essência Strong Blue 50g", "Essência Menta & Melancia 50g", "Essência Grape Fruit 50g", "Essência Citrus Punch 50g", "Essência Fresh Peach 50g", "Essência Tropical Explosion 50g", "Essência Passion Fruit 50g"]),
    ("Carvões", 15.0, 45.0,
     ["Zomo", "Cocohead", "Hexagonal", "Art Coco", "Nara", "Tom Coco", "Chicha"],
     ["Carvão de Coco 500g Hexagonal", "Carvão de Coco 1kg Cubo", "Carvão Disco de Acendimento Rápido 100g", "Carvão de Coco Premium 250g", "Carvão Cilíndrico Eco 1kg"]),
    ("Narguilés", 120.0, 650.0,
     ["Anubis", "Triton", "Amazon Hookah", "Zord", "Sultan", "Kamanja", "Mya"],
     ["Narguilé Completo Little Sorocaba", "Narguilé Stem Alumínio Anodizado", "Narguilé Híbrido Setup Slim", "Narguilé Tradicional Brass", "Narguilé de Mesa Compact", "Narguilé Pro Setup Diamond"]),
    ("Acessórios de Narguile", 8.0, 60.0,
     ["Econo", "Pro Hookah", "Invictus", "Mukai", "Hookah Blend", "Zomo"],
     ["Pegador de Carvão Inox 22cm", "Rosh Fundido cerâmica", "Kaloud Controlador de Calor", "Vaso de Vidro Gota Small", "Vaso de Vidro Boho Jumbo", "Prato de Alumínio 20cm", "Mangueira de Silicone Lavável com Piteira", "Borracha de Vedação Kit Complete"]),
    ("Bongs", 45.0, 280.0,
     ["Squadafum", "King Bong", "Grace Glass", "Bros", "Hippie Bong", "Ice Glass"],
     ["Bong de Vidro Borossilicato 20cm", "Bong Ice Percolator 30cm", "Bong de Silicone Flexível 22cm", "Mini Bong Acrylic Water Pipe", "Bong Recycler Glass 25cm", "Bong Beaker Heavy Duty 35cm"]),
    ("Charutos", 25.0, 180.0,
     ["Dona Flor", "Alonso Menendez", "Cohiba", "Montecristo", "Partagas", "Dannemann", "Gorkha"],
     ["Charuto Robusto Feito à Mão", "Charuto Corona Blend Reserva", "Charuto Short Churchill", "Charuto Petit Corona", "Charuto Torpedo Edição Especial", "Charuto Panatela Mild"]),
    ("Cigarros", 12.0, 28.0,
     ["Gudang Garam", "Dunhill", "Black", "Lucky Strike", "Camel", "Marlboro"],
     ["Cigarro Gudang Garam Clove 20s", "Cigarro Black Cretek 20s", "Cigarro Blend Especial Menthol", "Cigarro Classic Red 20s", "Cigarro Gold Slim 20s"]),
    ("Bebidas", 5.0, 45.0,
     ["Red Bull", "Monster", "Heineken", "Corona", "Budweiser", "Jack Daniels", "Coca-Cola", "Schweppes"],
     ["Energético Energy Drink 250ml", "Energético Mango Loco 473ml", "Cerveja Long Neck 330ml", "Refrigerante Lata 350ml", "Tônica Premium 220ml", "Whisky Pocket 50ml Dose", "Água Mineral com Gás 500ml"]),
]

NAMED_KITS = [
    ("Kit Sessão Iniciante Bem Bolado", "Sedas", ["Sedas", "Piteiras", "Isqueiros", "Dechavador"]),
    ("Kit Sessão Premium Raw & Squadafum", "Kit Promocional", ["Sedas", "Piteiras", "Slicks", "Cuia", "Dechavador"]),
    ("Kit Narguileiro Completo Zomo", "Kit Promocional", ["Essências", "Carvões", "Acessórios de Narguile"]),
    ("Kit Degustação Charuto & Whisky", "Charutos", ["Charutos", "Bebidas"]),
    ("Kit Munchies & Drink", "Bebidas", ["Bebidas", "Bebidas"]),
    ("Kit Vapor & Ice Glass", "Bongs", ["Bongs", "Tesoura", "Slicks"]),
    ("Kit Hard Case Organizador", "Bags", ["Bags", "Slicks", "Cuia", "Piteiras"]),
    ("Kit Smokers Master Lion", "Kit Promocional", ["Sedas", "Piteiras", "Isqueiros", "Tesoura", "Cuia"]),
    ("Kit Narguile Pro Setup", "Narguilés", ["Narguilés", "Carvões", "Essências"]),
    ("Kit Rolls & Papers Deluxe", "Sedas", ["Sedas", "Piteiras"]),
    ("Kit Pocket Essentials", "Kit Promocional", ["Isqueiros", "Sedas", "Dechavador"]),
    ("Kit Haze & Chill", "Kit Promocional", ["Cigarros", "Isqueiros", "Bebidas"]),
    ("Kit Ultra Gold Glass", "Bongs", ["Bongs", "Dechavador", "Piteiras"]),
    ("Kit Session Camuflado Puff", "Bags", ["Bags", "Dechavador", "Slicks"]),
    ("Kit Hookah Party 1Kg", "Carvões", ["Carvões", "Essências", "Acessórios de Narguile"]),
    ("Kit Verão Ice & Drink", "Bebidas", ["Bebidas", "Isqueiros"]),
    ("Kit Orgânico Eco Green", "Sedas", ["Sedas", "Piteiras", "Cuia"]),
    ("Kit Master Chef Tabaco", "Tesoura", ["Tesoura", "Cuia", "Dechavador", "Piteiras"]),
    ("Kit Night Club Hookah", "Essências", ["Essências", "Carvões"]),
    ("Kit Zippo & Fine Cigars", "Charutos", ["Charutos", "Isqueiros"]),
    ("Kit Starter Pack 420", "Kit Promocional", ["Sedas", "Piteiras", "Isqueiros"]),
    ("Kit Narguileiro Deluxe Adalya", "Essências", ["Essências", "Carvões", "Acessórios de Narguile"]),
    ("Kit Sommelier Charutos Cubanos", "Charutos", ["Charutos", "Isqueiros"]),
    ("Kit Bong Glass Complete", "Bongs", ["Bongs", "Tesoura", "Slicks", "Cuia"]),
    ("Kit Red Bull Energy Combo", "Bebidas", ["Bebidas", "Bebidas"]),
]

GENERIC_KIT_NAMES = [
    "Kit Combo Fumante", "Kit Sessão Relax", "Kit Presente Tabacaria", "Kit Weekend Vibes",
    "Kit Chill Zone", "Kit Amigos & Fumaça", "Kit Descubra Mahal", "Kit Signature Blend",
    "Kit Trip Explorer", "Kit Basics Plus", "Kit Deluxe Selection", "Kit Après Vape",
    "Kit Golden Hour", "Kit Midnight Session", "Kit All-in-One Smoker",
]


def build_categories():
    cat_id_map = {}
    categories = []
    for idx, config_row in enumerate(CATEGORIES_CONFIG, 1):
        name = config_row[0]
        cat_id_map[name] = idx
        categories.append({"id": idx, "name": name})
    kit_cat_idx = len(CATEGORIES_CONFIG) + 1
    cat_id_map["Kit Promocional"] = kit_cat_idx
    categories.append({"id": kit_cat_idx, "name": "Kit Promocional"})
    return cat_id_map, categories, kit_cat_idx


def build_products(rng: random.Random, cat_id_map):
    candidates = []
    for cat_name, min_p, max_p, brands, items in CATEGORIES_CONFIG:
        for brand in brands:
            for item in items:
                candidates.append((cat_name, brand, item, min_p, max_p))
    rng.shuffle(candidates)

    products = []
    sku_counter = 1000
    used_names = set()
    for cat_name, brand, item, min_p, max_p in candidates:
        if len(products) >= config.N_SIMPLES:
            break
        name = f"{item} {brand}"
        if name in used_names:
            continue
        used_names.add(name)
        sku_counter += 1
        sku = f"SKU-{sku_counter}"
        price = money(rng.uniform(min_p, max_p))
        cost = money(float(price) * rng.uniform(0.50, 0.65))
        markup = round(((float(price) - float(cost)) / float(cost)) * 100, 4) if cost else 50.0
        on_sale = rng.random() < 0.15
        original_price = money(float(price) / 0.85) if on_sale else None
        products.append(
            {
                "sku": sku,
                "name": name,
                "category": cat_name,
                "category_id": cat_id_map[cat_name],
                "brand": brand,
                "sale_price": float(price),
                "cost_price": float(cost),
                "markup_percent": markup,
                "original_price": float(original_price) if original_price else None,
                "on_sale": on_sale,
                "barcode": f"789{sku_counter:011d}",
                "visible_in_pos": rng.random() > 0.08,
                "visible_in_marketplace": rng.random() > 0.08,
                "type": "SIMPLES",
                "kit_component_eligible": False,
            }
        )
    return products


def build_kits(rng: random.Random, products, cat_id_map, kit_cat_idx):
    by_category = {}
    for p in products:
        by_category.setdefault(p["category"], []).append(p)

    kits_config = list(NAMED_KITS)
    category_names = list(cat_id_map.keys() - {"Kit Promocional"})
    for kit_name in GENERIC_KIT_NAMES:
        main_cat = rng.choice(category_names)
        target_cats = rng.sample(category_names, k=rng.randint(2, 4))
        kits_config.append((kit_name, "Kit Promocional", target_cats))
    kits_config = kits_config[: config.N_KITS]

    eligible_skus = set()
    kits = []
    kit_counter = 8000
    for kit_name, main_cat_name, target_cats in kits_config:
        kit_counter += 1
        kit_sku = f"KIT-{kit_counter}"
        components = []
        tot_cost = 0.0
        tot_sale = 0.0
        used_component_skus = set()
        for t_cat in target_cats:
            candidates = [p for p in by_category.get(t_cat, []) if p["sku"] not in used_component_skus]
            if not candidates:
                continue
            comp = rng.choice(candidates)
            used_component_skus.add(comp["sku"])
            qty = rng.choice([1, 1, 1, 2])
            components.append({"sku": comp["sku"], "quantity": qty})
            eligible_skus.add(comp["sku"])
            tot_cost += comp["cost_price"] * qty
            tot_sale += comp["sale_price"] * qty
        if not components:
            continue
        kit_sale_price = money(tot_sale * rng.uniform(0.85, 0.90))
        kit_cost_price = money(tot_cost)
        markup = (
            round(((float(kit_sale_price) - float(kit_cost_price)) / float(kit_cost_price)) * 100, 4)
            if kit_cost_price
            else 40.0
        )
        cat_id = cat_id_map.get(main_cat_name, kit_cat_idx)
        kits.append(
            {
                "sku": kit_sku,
                "name": kit_name,
                "category": main_cat_name,
                "category_id": cat_id,
                "brand": "Mahal",
                "sale_price": float(kit_sale_price),
                "cost_price": float(kit_cost_price),
                "markup_percent": markup,
                "type": "KIT",
                "components": components,
            }
        )

    for p in products:
        if p["sku"] in eligible_skus:
            p["kit_component_eligible"] = True

    return kits


def main():
    rng = random.Random(config.RNG_SEED)
    cat_id_map, categories, kit_cat_idx = build_categories()
    products = build_products(rng, cat_id_map)
    kits = build_kits(rng, products, cat_id_map, kit_cat_idx)

    statements = ["-- Fase 03: catálogo (categorias, produtos SIMPLES, kits)", ""]

    for c in categories:
        statements.append(
            "INSERT INTO product_category (id, name, featured, display_order, active) VALUES ("
            f"{c['id']}, {sqs(c['name'])}, {sqb(c['id'] <= 5)}, {c['id']}, TRUE);"
        )
    statements.append(f"SELECT setval('product_category_id_seq', {len(categories)});")
    statements.append("")

    for p in products:
        statements.append(
            "INSERT INTO product (sku, name, category, category_id, brand, sale_price, cost_price, "
            "markup_percent, original_price, on_sale, type, unit, status, active, visible_in_pos, "
            "visible_in_marketplace, barcode, kit_component_eligible) VALUES ("
            f"{sqs(p['sku'])}, {sqs(p['name'])}, {sqs(p['category'])}, {p['category_id']}, "
            f"{sqs(p['brand'])}, {sqn(p['sale_price'])}, {sqn(p['cost_price'])}, {sqn(p['markup_percent'])}, "
            f"{sqn(p['original_price'])}, {sqb(p['on_sale'])}, 'SIMPLES', 'UN', 'ATIVO', TRUE, "
            f"{sqb(p['visible_in_pos'])}, {sqb(p['visible_in_marketplace'])}, {sqs(p['barcode'])}, "
            f"{sqb(p['kit_component_eligible'])});"
        )
    statements.append("")

    for k in kits:
        statements.append(
            "INSERT INTO product (sku, name, category, category_id, brand, sale_price, cost_price, "
            "markup_percent, type, unit, status, active, visible_in_pos, visible_in_marketplace, "
            "kit_component_eligible) VALUES ("
            f"{sqs(k['sku'])}, {sqs(k['name'])}, {sqs(k['category'])}, {k['category_id']}, "
            f"{sqs(k['brand'])}, {sqn(k['sale_price'])}, {sqn(k['cost_price'])}, {sqn(k['markup_percent'])}, "
            f"'KIT', 'UN', 'ATIVO', TRUE, TRUE, TRUE, FALSE);"
        )
        for comp in k["components"]:
            statements.append(
                "INSERT INTO product_kit_component (kit_sku, component_sku, quantity) VALUES ("
                f"{sqs(k['sku'])}, {sqs(comp['sku'])}, {comp['quantity']});"
            )
    statements.append("")

    write_sql(config.out_path("03_catalogo.sql"), statements)
    write_json(
        config.out_path("catalogo.json"),
        {"categories": categories, "products": products, "kits": kits},
    )
    print(f"03_catalogo: {len(products)} produtos SIMPLES, {len(kits)} kits, {len(categories)} categorias.")


if __name__ == "__main__":
    main()
