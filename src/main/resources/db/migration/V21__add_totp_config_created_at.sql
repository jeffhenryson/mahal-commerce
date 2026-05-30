alter table totp_config
    add column if not exists created_at timestamptz not null default now();
