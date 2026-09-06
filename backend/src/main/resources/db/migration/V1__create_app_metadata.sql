create table if not exists app_metadata (
    id bigint generated always as identity primary key,
    metadata_key varchar(100) not null unique,
    metadata_value varchar(255) not null,
    created_at timestamp with time zone not null default current_timestamp
);

insert into app_metadata (metadata_key, metadata_value)
values ('schema_version', '1')
on conflict (metadata_key) do nothing;
