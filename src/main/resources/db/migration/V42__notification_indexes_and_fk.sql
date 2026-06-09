-- Performance: índice na coluna username de notification_preferences (usada em findByUsername)
CREATE INDEX idx_notification_preferences_username ON notification_preferences (username);

-- Integridade referencial: FK com cascade para garantir limpeza ao deletar usuário
ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_username
        FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE;

ALTER TABLE notification_preferences
    ADD CONSTRAINT fk_notification_prefs_username
        FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE;
