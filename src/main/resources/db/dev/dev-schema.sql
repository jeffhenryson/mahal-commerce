-- Objetos de schema que o Hibernate não cria sozinho, para o perfil `dev` (H2, ddl-auto=create-drop,
-- Flyway desabilitado). Em `hml`/`prod` estes objetos vêm das migrations Flyway.
--
-- Mantenha este arquivo em paridade com as migrations: um objeto que existe só aqui é um objeto que
-- funciona em dev e quebra em produção.

-- V65 — numeração de pedido. Sequência dedicada, e não o BIGSERIAL do id, porque rollback de
-- transação deixa buraco no id, e buraco em numeração de documento fiscal é problema com o fisco.
CREATE SEQUENCE IF NOT EXISTS order_number_seq START WITH 1000;

-- V122 (EST-C020) — a busca do catálogo usa unaccent() nos dois lados da comparação. No Postgres
-- é a extensão homônima; aqui o H2 precisa do alias, senão a query quebra em todo teste. A função
-- HQL é registrada por UnaccentFunctionContributor e vale para os dois bancos.
CREATE ALIAS IF NOT EXISTS unaccent FOR "com.cernecommerce.infra.persistence.H2Unaccent.unaccent";
