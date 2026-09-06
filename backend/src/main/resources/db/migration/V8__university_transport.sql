create table university_transport_request (
    id bigint generated always as identity primary key,
    applicant_user_id bigint not null references app_user(id),
    full_name varchar(180) not null,
    personal_document varchar(40) not null,
    birth_date date not null,
    phone varchar(40),
    course_type varchar(30) not null check (course_type in ('PROFESSIONALIZING', 'TECHNICAL', 'UNIVERSITY')),
    course_name varchar(180) not null,
    institution_name varchar(180) not null,
    monday boolean not null default false,
    tuesday boolean not null default false,
    wednesday boolean not null default false,
    thursday boolean not null default false,
    friday boolean not null default false,
    saturday boolean not null default false,
    sunday boolean not null default false,
    status varchar(30) not null default 'DRAFT' check (status in ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADJUSTMENT_REQUESTED', 'APPROVED', 'DENIED')),
    review_reason varchar(1000),
    valid_until date,
    submitted_at timestamp with time zone,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    check (monday or tuesday or wednesday or thursday or friday or saturday or sunday)
);

create index ix_transport_request_applicant on university_transport_request(applicant_user_id, created_at desc);
create index ix_transport_request_status on university_transport_request(status, submitted_at desc);

create table university_transport_document (
    id bigint generated always as identity primary key,
    request_id bigint not null references university_transport_request(id) on delete cascade,
    document_type varchar(30) not null check (document_type in ('PHOTO', 'ENROLLMENT_PROOF')),
    original_filename varchar(255) not null,
    content_type varchar(100) not null,
    size_bytes bigint not null check (size_bytes > 0 and size_bytes <= 5242880),
    content bytea not null,
    uploaded_at timestamp with time zone not null default current_timestamp,
    unique (request_id, document_type)
);

create table university_transport_history (
    id bigint generated always as identity primary key,
    request_id bigint not null references university_transport_request(id) on delete cascade,
    status varchar(30) not null check (status in ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADJUSTMENT_REQUESTED', 'APPROVED', 'DENIED')),
    reason varchar(1000),
    actor_user_id bigint references app_user(id),
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_transport_history_request on university_transport_history(request_id, created_at, id);

create table university_transport_card_art (
    id bigint generated always as identity primary key,
    name varchar(120) not null,
    header_text varchar(180) not null,
    footer_text varchar(300),
    accent_color varchar(7) not null default '#173B57' check (accent_color ~ '^#[0-9A-Fa-f]{6}$'),
    approved boolean not null default false,
    active boolean not null default false,
    approved_by bigint references app_user(id),
    approved_at timestamp with time zone,
    updated_at timestamp with time zone not null default current_timestamp
);

create unique index uq_transport_card_art_active on university_transport_card_art((active)) where active = true;

insert into university_transport_card_art (name, header_text, footer_text, approved, active)
values ('Padrão SEDUC', 'Carteirinha de Transporte Universitário', 'Secretaria Municipal de Educação', false, true);

insert into access_permission (code, name, description) values
('TRANSPORT_REQUEST_READ', 'Consultar transporte próprio', 'Permite ao estudante do transporte consultar suas próprias solicitações, documentos e carteirinha.'),
('TRANSPORT_REQUEST_WRITE', 'Solicitar transporte próprio', 'Permite ao estudante do transporte criar, corrigir e submeter a própria solicitação.'),
('TRANSPORT_REVIEW_READ', 'Consultar solicitações de transporte', 'Permite consultar solicitações de transporte universitário para análise da SEDUC.'),
('TRANSPORT_REVIEW_WRITE', 'Analisar solicitações de transporte', 'Permite iniciar análise, aprovar, solicitar ajuste ou negar solicitações de transporte.'),
('TRANSPORT_CARD_ART_WRITE', 'Configurar arte da carteirinha', 'Permite parametrizar e aprovar a arte ativa da carteirinha de transporte universitário.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Administrador do sistema'
  and p.code like 'TRANSPORT_%'
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Estudante do transporte'
  and p.code in ('TRANSPORT_REQUEST_READ', 'TRANSPORT_REQUEST_WRITE')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'SME / Técnico da Secretaria'
  and p.code in ('TRANSPORT_REVIEW_READ', 'TRANSPORT_REVIEW_WRITE', 'TRANSPORT_CARD_ART_WRITE')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Coordenação da SME'
  and p.code = 'TRANSPORT_REVIEW_READ'
on conflict do nothing;
