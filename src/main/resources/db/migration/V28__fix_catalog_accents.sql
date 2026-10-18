-- Corrige a acentuação dos nomes do catálogo criados pelas seeds antigas.
-- Os nomes aparecem direto para o usuário (home, busca, Express, cadastro do profissional).
-- Só altera linhas que ainda têm exatamente o nome antigo; nomes já ajustados à mão ficam como estão.

UPDATE service_areas SET name = 'Elétrica'      WHERE name = 'Eletrica';
UPDATE service_areas SET name = 'Hidráulica'    WHERE name = 'Hidraulica';
UPDATE service_areas SET name = 'Climatização'  WHERE name = 'Climatizacao';

UPDATE service_categories SET name = 'Instalação de luminárias'   WHERE name = 'Instalacao de luminarias';
UPDATE service_categories SET name = 'Limpeza pós-obra'           WHERE name = 'Limpeza pos-obra';
UPDATE service_categories SET name = 'Montador de móveis'         WHERE name = 'Montador de moveis';
UPDATE service_categories SET name = 'Técnico em ar-condicionado' WHERE name = 'Tecnico em ar-condicionado';
UPDATE service_categories SET name = 'Higienização de split'      WHERE name = 'Higienizacao de split';
