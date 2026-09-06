create table family_conversation (
    id bigint generated always as identity primary key,
    student_id bigint not null references student(id),
    school_id bigint not null references school_unit(id),
    guardian_user_id bigint not null references app_user(id),
    subject varchar(180) not null,
    status varchar(20) not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create index ix_family_conversation_guardian on family_conversation(guardian_user_id, student_id, updated_at desc);
create index ix_family_conversation_school on family_conversation(school_id, updated_at desc);

create table family_message (
    id bigint generated always as identity primary key,
    conversation_id bigint not null references family_conversation(id) on delete cascade,
    sender_user_id bigint not null references app_user(id),
    sender_type varchar(20) not null check (sender_type in ('GUARDIAN', 'STAFF')),
    body varchar(4000) not null,
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_family_message_conversation on family_message(conversation_id, created_at, id);

create table family_announcement (
    id bigint generated always as identity primary key,
    school_id bigint not null references school_unit(id),
    class_id bigint references school_class(id),
    student_id bigint references student(id),
    audience_type varchar(20) not null check (audience_type in ('SCHOOL', 'CLASS', 'STUDENT')),
    title varchar(180) not null,
    body varchar(4000) not null,
    published_by varchar(120) not null,
    published_at timestamp with time zone not null default current_timestamp,
    active boolean not null default true,
    check ((audience_type = 'SCHOOL' and class_id is null and student_id is null)
        or (audience_type = 'CLASS' and class_id is not null and student_id is null)
        or (audience_type = 'STUDENT' and student_id is not null))
);

create index ix_family_announcement_school on family_announcement(school_id, published_at desc);
create index ix_family_announcement_class on family_announcement(class_id, published_at desc);
create index ix_family_announcement_student on family_announcement(student_id, published_at desc);

insert into access_permission (code, name, description) values
('FAMILY_COMMUNICATION_READ', 'Consultar comunicação com famílias', 'Permite consultar conversas e comunicados no escopo da unidade escolar.'),
('FAMILY_COMMUNICATION_WRITE', 'Gerenciar comunicação com famílias', 'Permite responder mensagens e publicar comunicados no escopo da unidade escolar.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria', 'Direção escolar', 'Secretaria escolar', 'Coordenação escolar')
  and p.code in ('FAMILY_COMMUNICATION_READ', 'FAMILY_COMMUNICATION_WRITE')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Coordenação da SME' and p.code = 'FAMILY_COMMUNICATION_READ'
on conflict do nothing;
