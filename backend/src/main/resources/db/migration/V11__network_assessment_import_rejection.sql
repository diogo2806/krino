create table network_assessment_import_rejection (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    source_type varchar(20) not null check (source_type in ('MANUAL', 'IMPORT', 'ONLINE')),
    source_payload text not null,
    source_hash varchar(64) not null,
    rejection_reason varchar(1000) not null,
    submitted_by varchar(120) not null,
    submitted_at timestamp with time zone not null default current_timestamp
);

create index ix_network_assessment_import_rejection on network_assessment_import_rejection(assessment_id, submitted_at);
