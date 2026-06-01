create table dev_challenge_tokens (
    id         bigserial    primary key,
    username   varchar(80)  not null,
    token_hash varchar(64)  not null,
    period_t   bigint       not null,
    expires_at timestamptz  not null,
    created_at timestamptz  not null,
    used_at    timestamptz,
    constraint uk_dev_challenge_token unique (token_hash)
);

create index idx_dev_challenge_hash    on dev_challenge_tokens (token_hash);
create index idx_dev_challenge_expires on dev_challenge_tokens (expires_at);
