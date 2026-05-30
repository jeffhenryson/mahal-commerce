alter table users
    add column if not exists avatar_filename varchar(64);
