create table totp_config (
    id             bigserial    primary key,
    username       varchar(80)  not null,
    secret_enc     text         not null,
    enabled        boolean      not null default false,
    confirmed_at   timestamptz,
    constraint uk_totp_config_username unique (username)
);

create table totp_backup_codes (
    id         bigserial    primary key,
    username   varchar(80)  not null,
    code_hash  varchar(64)  not null,
    used_at    timestamptz,
    constraint uk_totp_backup_code unique (code_hash)
);

create index idx_totp_backup_username on totp_backup_codes (username);

create table totp_challenge_tokens (
    id           bigserial    primary key,
    username     varchar(80)  not null,
    token_hash   varchar(64)  not null,
    expires_at   timestamptz  not null,
    created_at   timestamptz  not null default now(),
    used_at      timestamptz,
    constraint uk_totp_challenge_token unique (token_hash)
);

create index idx_totp_challenge_hash on totp_challenge_tokens (token_hash);
