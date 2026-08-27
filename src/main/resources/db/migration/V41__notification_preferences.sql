CREATE TABLE notification_preferences (
    username       VARCHAR(80) NOT NULL,
    type           VARCHAR(50) NOT NULL,
    in_app_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    email_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (username, type)
);
