import sys
import random

# Categories & Price Configs: (category_name, min_price, max_price, brands, items)
CATEGORIES_CONFIG = [
    (
        "Sedas",
        3.0, 15.0,
        ["Zomo", "Bem Bolado", "Smoking", "Raw", "Elements", "Lion Rolling Circus", "Papel de Arroz", "Pay-Pay"],
        ["Seda Slim King Size", "Seda Brown King Size", "Seda Orgânica 1 1/4", "Seda Prata King Size", "Seda Deluxe", "Seda Alfavaca", "Seda Hemp Paper", "Seda Celulose Extra Fine", "Seda Master Size", "Seda Unbleached Ultra Thin"]
    ),
    (
        "Piteiras",
        10.0, 25.0,
        ["Yellow Finger", "Bem Bolado", "Raw", "Squadafum", "Gorilla", "Hippie Bong", "Girls in Green", "Papel de Arroz"],
        ["Piteira de Papel Large", "Piteira de Vidro 6mm", "Piteira de Vidro 7mm Bocal Flat", "Piteira Extra Longa", "Piteira de Algodão Biodegradável", "Piteira Murano 8mm", "Piteira Perfurada Super Slim", "Piteira de Madeira Ecológica"]
    ),
    (
        "Isqueiros",
        7.0, 35.0,
        ["Clipper", "Bic", "Zippo", "Maçarico Zen", "Honest", "Atomic"],
        ["Isqueiro Recarregável Classic", "Isqueiro Maçarico Single Jet", "Isqueiro Maçarico Double Jet", "Isqueiro Maxi Colecionável", "Isqueiro Slim Metallic", "Isqueiro Eletrônico USB Plasma", "Isqueiro de Fluído Vintage"]
    ),
    (
        "Dechavador",
        15.0, 65.0,
        ["Squadafum", "Kings", "Trio", "Metal Head", "Bros", "Bem Bolado", "Kush", "Elements"],
        ["Dechavador de Metal 3 Fases 40mm", "Dechavador de Policarbonato", "Dechavador de Madeira com Imã", "Dechavador de Zinco 50mm", "Dechavador com Manivela", "Dechavador Dente de Tubarão 60mm", "Dechavador Magnético Compacto"]
    ),
    (
        "Slicks",
        8.0, 30.0,
        ["Squadafum", "SlickContainer", "OilSlick", "Puff Life", "Bros"],
        ["Slick de Silicone 5ml", "Slick de Silicone 10ml Divisória", "Slick de Silicone Oil 25ml", "Slick Pote de Vidro com Tampa Hermética", "Slick Formato Tambor 15ml", "Slick Silicube 7ml"]
    ),
    (
        "Cuia",
        12.0, 35.0,
        ["Squadafum", "Cultiva", "Bros", "Puff Life", "Bem Bolado"],
        ["Cuia de Silicone Flexível", "Cuia de Silicone Antiaderente Large", "Cuia Estampada Art", "Cuia de Silicone Dobrável", "Cuia Glow In The Dark"]
    ),
    (
        "Tesoura",
        12.0, 35.0,
        ["Buddha", "Dobrável", "Squadafum", "Bros", "Nip", "Herbs"],
        ["Tesoura Dobrável de Precisão", "Tesoura Mola Inox", "Tesoura Ponta Curva Gold", "Tesoura Titanium Antiaderente", "Tesoura Micro Serrilhada"]
    ),
    (
        "Bags",
        35.0, 150.0,
        ["Puff Life", "Case Hard", "Squadafum", "Bem Bolado", "Slyng", "Ultra", "Bag-It"],
        ["Case Anti Odor Compact", "Case Hard Shell Slim", "Shoulder Bag Tabacaria Premium", "Case Organizador Double ZIP", "Case Camuflada Impermeável", "Mini Bag Porta Acessórios"]
    ),
    (
        "Essências",
        12.0, 28.0,
        ["Zomo", "Ziggy", "Nayak", "Onix", "Adalya", "Tangiers", "Fumari", "Smyrna"],
        ["Essência Mint Freeze 50g", "Essência Strong Blue 50g", "Essência Menta & Melancia 50g", "Essência Grape Fruit 50g", "Essência Citrus Punch 50g", "Essência Fresh Peach 50g", "Essência Tropical Explosion 50g", "Essência Passion Fruit 50g"]
    ),
    (
        "Carvões",
        15.0, 45.0,
        ["Zomo", "Cocohead", "Hexagonal", "Art Coco", "Nara", "Tom Coco", "Chicha"],
        ["Carvão de Coco 500g Hexagonal", "Carvão de Coco 1kg Cubo", "Carvão Disco de Acendimento Rápido 100g", "Carvão de Coco Premium 250g", "Carvão Cilíndrico Eco 1kg"]
    ),
    (
        "Narguilés",
        120.0, 650.0,
        ["Anubis", "Triton", "Amazon Hookah", "Zord", "Sultan", "Kamanja", "Mya"],
        ["Narguilé Completo Little Sorocaba", "Narguilé Stem Alumínio Anodizado", "Narguilé Híbrido Setup Slim", "Narguilé Tradicional Brass", "Narguilé de Mesa Compact", "Narguilé Pro Setup Diamond"]
    ),
    (
        "Acessórios de Narguile",
        8.0, 60.0,
        ["Econo", "Pro Hookah", "Invictus", "Mukai", "Hookah Blend", "Zomo"],
        ["Pegador de Carvão Inox 22cm", "Rosh Fundido cerâmica", "Kaloud Controlador de Calor", "Vaso de Vidro Gota Small", "Vaso de Vidro Boho Jumbo", "Prato de Alumínio 20cm", "Mangueira de Silicone Lavável com Piteira", "Borracha de Vedação Kit Complete"]
    ),
    (
        "Bongs",
        45.0, 280.0,
        ["Squadafum", "King Bong", "Grace Glass", "Bros", "Hippie Bong", "Ice Glass"],
        ["Bong de Vidro Borossilicato 20cm", "Bong Ice Percolator 30cm", "Bong de Silicone Flexível 22cm", "Mini Bong Acrylic Water Pipe", "Bong Recycler Glass 25cm", "Bong Beaker Heavy Duty 35cm"]
    ),
    (
        "Charutos",
        25.0, 180.0,
        ["Dona Flor", "Alonso Menendez", "Cohiba", "Montecristo", "Partagas", "Dannemann", "Gorkha"],
        ["Charuto Robusto Feito à Mão", "Charuto Corona Blend Reserva", "Charuto Short Churchill", "Charuto Petit Corona", "Charuto Torpedo Edição Especial", "Charuto Panatela Mild"]
    ),
    (
        "Cigarros",
        12.0, 28.0,
        ["Gudang Garam", "Dunhill", "Black", "Lucky Strike", "Camel", "Marlboro"],
        ["Cigarro Gudang Garam Clove 20s", "Cigarro Black Cretek 20s", "Cigarro Blend Especial Menthol", "Cigarro Classic Red 20s", "Cigarro Gold Slim 20s"]
    ),
    (
        "Bebidas",
        5.0, 45.0,
        ["Red Bull", "Monster", "Heineken", "Corona", "Budweiser", "Jack Daniels", "Coca-Cola", "Schweppes"],
        ["Energético Energy Drink 250ml", "Energético Mango Loco 473ml", "Cerveja Long Neck 330ml", "Refrigerante Lata 350ml", "Tônica Premium 220ml", "Whisky Pocket 50ml Dose", "Água Mineral com Gás 500ml"]
    )
]

def generate_sql():
    sql = []
    sql.append("BEGIN;")
    sql.append("-- 1. Apaga itens dependentes e produtos/kits antigos")
    sql.append("DELETE FROM order_item;")
    sql.append("DELETE FROM cart_item;")
    sql.append("DELETE FROM goods_receipt_item;")
    sql.append("DELETE FROM stock_count_item;")
    sql.append("DELETE FROM stock_movement;")
    sql.append("DELETE FROM stock_reservation;")
    sql.append("DELETE FROM stock_reorder_point;")
    sql.append("DELETE FROM stock_balance;")
    sql.append("DELETE FROM product_kit_component;")
    sql.append("DELETE FROM product_variant;")
    sql.append("DELETE FROM product_root_attribute;")
    sql.append("DELETE FROM product_image;")
    sql.append("DELETE FROM product;")
    sql.append("DELETE FROM product_category;")
    sql.append("")

    sql.append("-- 2. Insere Categorias")
    cat_id_map = {}
    for idx, config in enumerate(CATEGORIES_CONFIG, 1):
        cat_name = config[0]
        cat_id_map[cat_name] = idx
        escaped_name = cat_name.replace("'", "''")
        sql.append(f"INSERT INTO product_category (id, name, featured, display_order, active) VALUES ({idx}, '{escaped_name}', true, {idx}, true);")
    
    # Also add "Kit Promocional" category
    kit_cat_idx = len(CATEGORIES_CONFIG) + 1
    cat_id_map["Kit Promocional"] = kit_cat_idx
    sql.append(f"INSERT INTO product_category (id, name, featured, display_order, active) VALUES ({kit_cat_idx}, 'Kit Promocional', true, {kit_cat_idx}, true);")
    
    sql.append(f"SELECT setval('product_category_id_seq', {kit_cat_idx});")
    sql.append("")

    sql.append("-- 3. Insere Produtos Simples")
    products = []
    sku_counter = 1000
    
    for cat_name, min_p, max_p, brands, items in CATEGORIES_CONFIG:
        cat_id = cat_id_map[cat_name]
        for brand in brands:
            for item in items:
                sku_counter += 1
                sku = f"SKU-{sku_counter}"
                name = f"{item} {brand}"
                
                price = round(random.uniform(min_p, max_p), 2)
                cost = round(price * random.uniform(0.5, 0.65), 2)
                markup = round(((price - cost) / cost) * 100, 2) if cost > 0 else 50.0
                stock_qty = random.randint(15, 120)
                barcode = f"789{sku_counter:011d}"
                
                products.append({
                    "sku": sku,
                    "name": name,
                    "cat_name": cat_name,
                    "cat_id": cat_id,
                    "brand": brand,
                    "sale_price": price,
                    "cost_price": cost,
                    "markup": markup,
                    "stock_qty": stock_qty,
                    "barcode": barcode,
                    "type": "SIMPLES"
                })

    random.seed(2026)
    random.shuffle(products)
    
    # Target ~480 simple products
    products = products[:480]

    for p in products:
        name_esc = p['name'].replace("'", "''")
        cat_esc = p['cat_name'].replace("'", "''")
        brand_esc = p['brand'].replace("'", "''")
        
        sql.append(
            f"INSERT INTO product (sku, name, category, category_id, brand, sale_price, cost_price, markup_percent, type, unit, active, visible_in_pos, visible_in_marketplace, barcode) "
            f"VALUES ('{p['sku']}', '{name_esc}', '{cat_esc}', {p['cat_id']}, '{brand_esc}', {p['sale_price']}, {p['cost_price']}, {p['markup']}, 'SIMPLES', 'UN', true, true, true, '{p['barcode']}');"
        )
        
        sql.append(
            f"INSERT INTO stock_balance (sku, warehouse_id, quantity, reserved_quantity, version) VALUES ('{p['sku']}', 1, {p['stock_qty']}, 0, 0);"
        )

    sql.append("")
    sql.append("-- 4. Insere Kits Especiais de Tabacaria (~25 Kits)")
    
    kits_config = [
        ("Kit Sessão Iniciante Bem Bolado", "Sedas", ["Sedas", "Piteiras", "Isqueiros", "Dechavador"]),
        ("Kit Sessão Premium Raw & Squadafum", "Kit Promocional", ["Sedas", "Piteiras", "Slicks", "Cuia", "Dechavador"]),
        ("Kit Narguileiro Completo Zomo", "Kit Promocional", ["Essências", "Carvões", "Acessórios de Narguile"]),
        ("Kit Degustação Charuto & Whisky", "Charutos", ["Charutos", "Bebidas"]),
        ("Kit Munchies & Drink", "Bebidas", ["Bebidas", "Bebidas"]),
        ("Kit Vapor & Ice Glass", "Bongs", ["Bongs", "Tesoura", "Slicks"]),
        ("Kit Hard Case Organizador", "Bags", ["Bags", "Slicks", "Cuia", "Piteiras"]),
        ("Kit Smokers Master Lion", "Kit Promocional", ["Sedas", "Piteiras", "Isqueiros", "Tesoura", "Cuia"]),
        ("Kit Narguile Pro Setup", "Narguilés", ["Narguilés", "Carvões", "Essências"]),
        ("Kit Rolls & Papers Deluxe", "Sedas", ["Sedas", "Sedas", "Piteiras", "Piteiras"]),
        ("Kit Pocket Essentials", "Kit Promocional", ["Isqueiros", "Sedas", "Dechavador"]),
        ("Kit Haze & Chill", "Kit Promocional", ["Cigarros", "Isqueiros", "Bebidas"]),
        ("Kit Ultra Gold Glass", "Bongs", ["Bongs", "Dechavador", "Piteiras"]),
        ("Kit Session Camuflado Puff", "Bags", ["Bags", "Dechavador", "Slicks"]),
        ("Kit Hookah Party 1Kg", "Carvões", ["Carvões", "Essências", "Essências", "Acessórios de Narguile"]),
        ("Kit Verão Ice & Drink", "Bebidas", ["Bebidas", "Bebidas", "Isqueiros"]),
        ("Kit Organico Eco Green", "Sedas", ["Sedas", "Piteiras", "Cuia"]),
        ("Kit Master Chef Tabaco", "Tesoura", ["Tesoura", "Cuia", "Dechavador", "Piteiras"]),
        ("Kit Night Club Hookah", "Essências", ["Essências", "Essências", "Carvões"]),
        ("Kit Zippo & Fine Cigars", "Charutos", ["Charutos", "Isqueiros"]),
        ("Kit Starter Pack 420", "Kit Promocional", ["Sedas", "Piteiras", "Isqueiros"]),
        ("Kit Narguileiro Deluxe Adalya", "Essências", ["Essências", "Essências", "Carvões", "Acessórios de Narguile"]),
        ("Kit Sommelier Charutos Cubanos", "Charutos", ["Charutos", "Charutos", "Isqueiros"]),
        ("Kit Bong Glass Complete", "Bongs", ["Bongs", "Tesoura", "Slicks", "Cuia"]),
        ("Kit Red Bull Energy Combo", "Bebidas", ["Bebidas", "Bebidas", "Bebidas"])
    ]

    kit_counter = 8000
    for kit_name, main_cat_name, target_cats in kits_config:
        kit_counter += 1
        kit_sku = f"KIT-{kit_counter}"
        
        components = []
        tot_cost = 0.0
        tot_sale = 0.0
        
        for t_cat in target_cats:
            matching_prods = [p for p in products if p['cat_name'] == t_cat]
            if matching_prods:
                comp_prod = random.choice(matching_prods)
                qty = 1
                components.append((comp_prod['sku'], qty))
                tot_cost += comp_prod['cost_price'] * qty
                tot_sale += comp_prod['sale_price'] * qty
        
        kit_sale_price = round(tot_sale * random.uniform(0.85, 0.90), 2)
        kit_cost_price = round(tot_cost, 2)
        kit_markup = round(((kit_sale_price - kit_cost_price) / kit_cost_price) * 100, 2) if kit_cost_price > 0 else 40.0
        
        cat_id = cat_id_map.get(main_cat_name, 1)
        kit_name_esc = kit_name.replace("'", "''")
        main_cat_esc = main_cat_name.replace("'", "''")
        
        sql.append(
            f"INSERT INTO product (sku, name, category, category_id, brand, sale_price, cost_price, markup_percent, type, unit, active, visible_in_pos, visible_in_marketplace, kit_component_eligible) "
            f"VALUES ('{kit_sku}', '{kit_name_esc}', '{main_cat_esc}', {cat_id}, 'Mahal', {kit_sale_price}, {kit_cost_price}, {kit_markup}, 'KIT', 'UN', true, true, true, false);"
        )
        
        sql.append(
            f"INSERT INTO stock_balance (sku, warehouse_id, quantity, reserved_quantity, version) VALUES ('{kit_sku}', 1, 50, 0, 0);"
        )
        
        for comp_sku, qty in components:
            sql.append(
                f"INSERT INTO product_kit_component (kit_sku, component_sku, quantity) VALUES ('{kit_sku}', '{comp_sku}', {qty});"
            )

    sql.append("")
    sql.append("COMMIT;")
    sql.append("")
    sql.append("SELECT type, COUNT(*) FROM product GROUP BY type;")

    with open("/tmp/seed_products.sql", "w", encoding="utf-8") as f:
        f.write("\n".join(sql))

    print(f"SQL generated: {len(products)} simples e {len(kits_config)} kits.")

if __name__ == "__main__":
    generate_sql()
