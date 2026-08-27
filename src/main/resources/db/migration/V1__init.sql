-- Flyway initial schema for PostgreSQL (hml)

CREATE TABLE IF NOT EXISTS roles (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(80) NOT NULL,
    CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS users (
    id        BIGSERIAL PRIMARY KEY,
    username  VARCHAR(80)  NOT NULL,
    password  VARCHAR(255) NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id)
);

-- Helpful indexes (optional)
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_roles_name ON roles (name);
