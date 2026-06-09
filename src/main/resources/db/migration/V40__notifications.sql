CREATE TABLE notifications (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(80)  NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_username         ON notifications (username);
CREATE INDEX idx_notifications_username_read_at ON notifications (username, read_at);
CREATE INDEX idx_notifications_created_at       ON notifications (created_at);
