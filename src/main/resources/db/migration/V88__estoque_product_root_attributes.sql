-- Atributos descritivos do próprio SKU pai (BACKEND_TODO do mahal-admin, §P1-Estoque).
--
-- Até aqui atributo só existia dentro de product_attribute, pendurado numa VARIAÇÃO — é o que
-- distingue um SKU filho dos outros da mesma grade. Um produto sem grade não tinha onde carregar
-- um atributo puramente descritivo ("Sabor: Menta" num item que não varia).
--
-- Tabela nova em vez de tornar product_attribute.variant_id anulável: aquela tabela tem FK e
-- índice para variant_id, e admitir linha sem variação significaria coluna nula em metade das
-- linhas mais um CHECK garantindo que exatamente um dos dois donos está preenchido. Duas tabelas
-- com um dono cada saem mais baratas, e a FK existente fica intacta.
--
-- Sem UNIQUE em (product_id, attr_type): o mesmo tipo pode legitimamente repetir com valores
-- diferentes (dois sabores num blend). É a mesma decisão já tomada em product_attribute.

CREATE TABLE product_root_attribute (
    product_id BIGINT       NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    attr_type  VARCHAR(50)  NOT NULL,
    attr_value VARCHAR(100) NOT NULL
);

CREATE INDEX idx_product_root_attribute_product_id ON product_root_attribute (product_id);

COMMENT ON TABLE product_root_attribute IS
    'Atributos descritivos do SKU pai. Distintos de product_attribute, que qualifica uma variação e faz parte do que a identifica.';
