create table password_reset_tokens (
    id           bigserial    primary key,
    username     varchar(80)  not null,
    token_hash   varchar(64)  not null,
    expires_at   timestamptz  not null,
    requested_at timestamptz  not null default now(),
    used_at      timestamptz,
    constraint uk_prt_token_hash unique (token_hash)
);

create index idx_prt_token_hash on password_reset_tokens (token_hash);
