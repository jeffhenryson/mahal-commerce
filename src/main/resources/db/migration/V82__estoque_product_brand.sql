ALTER TABLE product ADD COLUMN brand VARCHAR(100);

COMMENT ON COLUMN product.brand IS 'Marca do produto. NULL = não informada.';
