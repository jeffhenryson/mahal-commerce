-- Vocabulário de tipos de atributo (item 5 do pedido do frontend de mahal-admin). Os tipos
-- ("Sabor", "Aroma", "Concentração de Nicotina", "Potência/Voltagem"...) eram uma constante
-- hardcoded no frontend — cadastrar dez produtos com um tipo novo exigia digitar o mesmo texto
-- dez vezes, e um erro de digitação virava um atributo órfão que ninguém percebia até alguém
-- filtrar por ele.
--
-- Escopo mínimo, de propósito: só o vocabulário dos nomes. Sem hierarquia, sem valores
-- predefinidos por tipo, sem tipagem. Sem FK de product_attribute.attr_type/product_root_attribute
-- para esta tabela — é uma lista de sugestão/consistência, não uma restrição.

CREATE TABLE attribute_type (
    id   BIGSERIAL   PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uk_attribute_type_name UNIQUE (name)
);

CREATE UNIQUE INDEX uk_attribute_type_name_lower ON attribute_type (LOWER(name));

-- Pré-popula com a lista que já estava hardcoded no frontend, para não nascer vazia. Qualquer
-- outro tipo em uso é cadastrado depois via POST /estoque/attribute-types.
INSERT INTO attribute_type (name) VALUES
    ('Sabor'),
    ('Aroma'),
    ('Concentração de Nicotina'),
    ('Potência/Voltagem')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name)
VALUES ('ESTOQUE_ATTRIBUTE_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_ATTRIBUTE_MANAGE'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE attribute_type IS
    'Vocabulário de nomes de ProductAttribute.type — sugestão/consistência, sem FK de volta para product_attribute/product_root_attribute.';
