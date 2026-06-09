-- Índice parcial em read_at para o scheduler NotificationCleanupService.
-- DELETE FROM notifications WHERE read_at IS NOT NULL AND read_at < :before
-- sem este índice faz full scan conforme a tabela cresce.
CREATE INDEX idx_notifications_read_at ON notifications(read_at)
    WHERE read_at IS NOT NULL;
