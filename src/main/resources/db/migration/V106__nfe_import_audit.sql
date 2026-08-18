-- EST-F005 — Importação de entrada de mercadoria por XML de NF-e. Fluxo em duas fases (preview →
-- confirm): receiveGoods é transacional tudo-ou-nada, e uma NF-e real com item sem EAN batido não
-- pode abortar o recebimento inteiro — o operador resolve manualmente no fechamento. As duas
-- requisições são separadas no tempo, então o resultado do parsing (inclusive o casamento
-- automático por EAN) precisa ficar persistido entre elas.

CREATE TABLE nfe_import (
    id               BIGSERIAL     PRIMARY KEY,
    supplier_id      BIGINT        REFERENCES supplier(id),
    emitter_cnpj     VARCHAR(20)   NOT NULL,
    warehouse_code   VARCHAR(50),
    file_reference   VARCHAR(255)  NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    goods_receipt_id BIGINT        REFERENCES goods_receipt(id),
    uploaded_by      VARCHAR(80)   NOT NULL,
    uploaded_at      TIMESTAMP     NOT NULL,
    confirmed_at     TIMESTAMP,

    CONSTRAINT ck_nfe_import_status CHECK (status IN ('PREVIEWED','CONFIRMED','REJECTED')),
    -- Espelha o compact constructor de NfeImport. REJECTED nunca teve fornecedor encontrado, então
    -- nunca tem supplier_id nem os campos de confirmação; PREVIEWED tem fornecedor mas ainda não
    -- foi confirmado; CONFIRMED tem tudo.
    CONSTRAINT ck_nfe_import_status_consistency CHECK (
        (status = 'PREVIEWED' AND supplier_id IS NOT NULL AND confirmed_at IS NULL AND goods_receipt_id IS NULL) OR
        (status = 'CONFIRMED' AND supplier_id IS NOT NULL AND warehouse_code IS NOT NULL
            AND goods_receipt_id IS NOT NULL AND confirmed_at IS NOT NULL) OR
        (status = 'REJECTED' AND supplier_id IS NULL AND goods_receipt_id IS NULL AND confirmed_at IS NULL)
    )
);

CREATE INDEX idx_nfe_import_supplier_id ON nfe_import (supplier_id);

COMMENT ON COLUMN nfe_import.supplier_id IS
    'Fornecedor resolvido por CNPJ. Nulo só quando status=REJECTED — CNPJ do emitente não bateu com nenhum fornecedor cadastrado. Sem criação automática, de propósito (diferente do precedente de Categoria): taxId alimenta conta a pagar/compliance no futuro, dado demais para criar sem revisão humana.';
COMMENT ON COLUMN nfe_import.warehouse_code IS
    'Depósito de destino, só existe depois da confirmação — a NF-e não diz para qual depósito a mercadoria vai, é decisão do operador no fechamento.';
COMMENT ON COLUMN nfe_import.file_reference IS
    'Referência do XML bruto no storage (FileStoragePort, keyPrefix nfe-imports/) — trilha de auditoria/disputa com o fornecedor. Sem endpoint de leitura pública, diferente de imagem de produto.';

CREATE TABLE nfe_import_line (
    id                    BIGSERIAL     PRIMARY KEY,
    nfe_import_id         BIGINT        NOT NULL REFERENCES nfe_import(id) ON DELETE CASCADE,
    item_number           INT           NOT NULL,
    supplier_product_code VARCHAR(60)   NOT NULL,
    ean                   VARCHAR(20),
    description           VARCHAR(255),
    quantity              NUMERIC(14,3) NOT NULL,
    unit_price            NUMERIC(14,2) NOT NULL,
    lot_code              VARCHAR(50),
    expiry_date           DATE,
    match_status          VARCHAR(20)   NOT NULL,
    matched_sku           VARCHAR(50),

    CONSTRAINT ck_nfe_import_line_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_nfe_import_line_match_status CHECK (match_status IN ('MATCHED','UNMATCHED')),
    -- matchStatus é DERIVADO de matchedSku no domínio (NfeImportLine) — o CHECK espelha isso.
    CONSTRAINT ck_nfe_import_line_matched_sku CHECK ((match_status = 'MATCHED') = (matched_sku IS NOT NULL))
);

CREATE INDEX idx_nfe_import_line_nfe_import_id ON nfe_import_line (nfe_import_id);

COMMENT ON COLUMN nfe_import_line.ean IS
    'cEAN da NF-e. Nulo quando ausente ou "SEM GTIN" — comum em NF-e real para item não-branded/a granel. É o caso que força o fluxo de duas fases: sem EAN, o casamento automático não resolve, e o operador precisa de um override manual antes de confirmar.';
COMMENT ON COLUMN nfe_import_line.unit_price IS
    'vUnCom da NF-e — vira unitCost do recebimento na confirmação (GoodsReceiptItem.unitCost), não preço de venda.';
COMMENT ON COLUMN nfe_import_line.matched_sku IS
    'SKU interno resolvido — por EstoqueUseCase.findProductByBarcode(ean) na importação, ou por override manual do operador no fechamento. Nulo enquanto UNMATCHED.';
