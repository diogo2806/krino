create table student_access_credential (
    id bigint generated always as identity primary key,
    student_id bigint not null unique references student(id),
    credential_token varchar(80) not null unique,
    active boolean not null default true,
    issued_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table student_access_event (
    id bigint generated always as identity primary key,
    client_event_id uuid not null unique,
    student_id bigint not null references student(id),
    school_id bigint not null references school_unit(id),
    class_id bigint not null references school_class(id),
    event_type varchar(20) not null check (event_type in ('ENTRY', 'EXIT')),
    captured_at timestamp with time zone not null,
    received_at timestamp with time zone not null default current_timestamp,
    captured_offline boolean not null default false,
    source_type varchar(20) not null check (source_type in ('QR', 'MANUAL')),
    device_id varchar(160),
    operator_username varchar(120) not null
);

create index ix_student_access_event_student_time on student_access_event(student_id, captured_at desc, id desc);
create index ix_student_access_event_school_time on student_access_event(school_id, captured_at desc, id desc);

create table student_access_notification (
    id bigint generated always as identity primary key,
    event_id bigint not null unique references student_access_event(id) on delete cascade,
    student_id bigint not null references student(id),
    guardian_name varchar(180),
    channel varchar(30) not null default 'IN_APP',
    message varchar(500) not null,
    available_at timestamp with time zone not null default current_timestamp,
    read_at timestamp with time zone
);

create index ix_student_access_notification_student on student_access_notification(student_id, available_at desc);

insert into access_permission (code, name, description) values
('ACCESS_CONTROL_READ', 'Consultar entrada e saída', 'Permite identificar estudantes e consultar o histórico de entrada e saída no escopo atribuído.'),
('ACCESS_CONTROL_WRITE', 'Registrar entrada e saída', 'Permite registrar e sincronizar eventos de entrada e saída no escopo atribuído.'),
('ACCESS_CARD_MANAGE', 'Gerenciar carteirinha de acesso', 'Permite emitir ou reemitir a credencial QR do estudante no escopo atribuído.');

insert into access_role (name, description, system_role)
select 'Operador de entrada e saída', 'Perfil-base para leitura de credenciais e registro de entrada e saída de estudantes.', true
where not exists (select 1 from access_role where name = 'Operador de entrada e saída');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria', 'Direção escolar', 'Secretaria escolar')
  and p.code in ('ACCESS_CONTROL_READ', 'ACCESS_CONTROL_WRITE', 'ACCESS_CARD_MANAGE')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Operador de entrada e saída'
  and p.code in ('ACCESS_CONTROL_READ', 'ACCESS_CONTROL_WRITE')
on conflict do nothing;
