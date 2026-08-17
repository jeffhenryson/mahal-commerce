-- Vínculo opcional de navegação (Bloco 1.3 do BACKEND_TODO de mahal-admin): para onde o clique na
-- notificação deve levar no admin. Nullable: notificações antigas e as que não têm destino natural
-- (ex.: PASSWORD_CHANGED) continuam sem target_url, e o frontend trata ausência como "não navega".
ALTER TABLE notifications ADD COLUMN target_url VARCHAR(255);
