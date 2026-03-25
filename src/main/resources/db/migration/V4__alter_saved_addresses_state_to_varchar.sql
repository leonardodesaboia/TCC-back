-- CHAR(2) e VARCHAR(2) são funcionalmente equivalentes para siglas de estado.
-- O Hibernate mapeia String como VARCHAR no JDBC (Types#VARCHAR), enquanto CHAR(2)
-- é reportado pelo PostgreSQL como bpchar (Types#CHAR), causando falha na validação
-- de schema. Esta migration alinha o tipo do banco com o mapeamento JPA.
ALTER TABLE saved_addresses ALTER COLUMN state TYPE VARCHAR(2);
