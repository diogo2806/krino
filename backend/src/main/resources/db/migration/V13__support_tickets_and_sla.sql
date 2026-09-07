create table support_sla_policy (
    severity varchar(20) primary key check (severity in ('CRITICAL', 'MEDIUM', 'LOW')),
    response_minutes integer not null check (response_minutes > 0),
    solution_minutes integer not null check (solution_minutes > 0),
    counting_rule varchar(20) not null default 'UNDEFINED' check (counting_rule in ('UNDEFINED', 'ELAPSED', 'BUSINESS')),
    warning_minutes integer check (warning_minutes is null or warning_minutes > 0),
    business_timezone varchar(80),
    workday_start time,
    workday_end time,
    business_days varchar(100),
    updated_by varchar(120),
    updated_at timestamp with time zone not null default current_timestamp,
    check (workday_end is null or workday_start is null or workday_end > workday_start)
);

insert into support_sla_policy (severity, response_minutes, solution_minutes) values
('CRITICAL', 60, 240),
('MEDIUM', 240, 1440),
('LOW', 1440, 4320);

create table support_ticket (
    id bigint generated always as identity primary key,
    protocol varchar(20) generated always as ('SUP-' || lpad(id::text, 8, '0')) stored unique,
    subject varchar(180) not null,
    description varchar(4000) not null,
    category varchar(20) not null check (category in ('SUPPORT', 'CORRECTIVE', 'PREVENTIVE', 'EVOLUTION')),
    severity varchar(20) not null check (severity in ('CRITICAL', 'MEDIUM', 'LOW')),
    status varchar(30) not null default 'OPEN' check (status in ('OPEN', 'IN_PROGRESS', 'WAITING_REQUESTER', 'RESOLVED', 'CLOSED')),
    school_id bigint references school_unit(id),
    assessment_id bigint references network_assessment(id),
    created_by_user_id bigint not null references app_user(id),
    created_by_username varchar(120) not null,
    resolution varchar(4000),
    opened_at timestamp with time zone not null default current_timestamp,
    first_response_at timestamp with time zone,
    resolved_at timestamp with time zone,
    closed_at timestamp with time zone,
    sla_response_minutes integer not null check (sla_response_minutes > 0),
    sla_solution_minutes integer not null check (sla_solution_minutes > 0),
    sla_counting_rule varchar(20) not null check (sla_counting_rule in ('UNDEFINED', 'ELAPSED', 'BUSINESS')),
    sla_warning_minutes integer,
    sla_business_timezone varchar(80),
    sla_workday_start time,
    sla_workday_end time,
    sla_business_days varchar(100),
    response_due_at timestamp with time zone,
    solution_due_at timestamp with time zone,
    updated_at timestamp with time zone not null default current_timestamp
);

create index ix_support_ticket_status on support_ticket(status, severity, opened_at desc);
create index ix_support_ticket_creator on support_ticket(created_by_user_id, opened_at desc);
create index ix_support_ticket_school on support_ticket(school_id, status, opened_at desc);
create index ix_support_ticket_assessment on support_ticket(assessment_id, status);

create table support_ticket_interaction (
    id bigint generated always as identity primary key,
    ticket_id bigint not null references support_ticket(id) on delete cascade,
    interaction_type varchar(30) not null check (interaction_type in ('OPENED', 'MESSAGE', 'STATUS_CHANGE', 'SEVERITY_CHANGE', 'SOLUTION', 'SLA_RECALCULATED')),
    actor_user_id bigint references app_user(id),
    actor_username varchar(120) not null,
    actor_role varchar(20) not null check (actor_role in ('REQUESTER', 'SUPPORT', 'SYSTEM')),
    message varchar(4000) not null,
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_support_ticket_interaction_ticket on support_ticket_interaction(ticket_id, created_at, id);

insert into access_permission (code, name, description) values
('SUPPORT_TICKET_CREATE', 'Abrir chamados de suporte', 'Permite abrir chamado de suporte no próprio contexto autorizado.'),
('SUPPORT_TICKET_READ', 'Consultar chamados de suporte', 'Permite consultar os próprios chamados e os chamados permitidos pelo escopo atribuído.'),
('SUPPORT_TICKET_MANAGE', 'Atender chamados de suporte', 'Permite responder, alterar criticidade e estado e registrar solução no escopo atribuído.'),
('SUPPORT_REPORT_READ', 'Consultar relatórios de suporte', 'Permite consultar indicadores e histórico de atendimento no escopo atribuído.'),
('SUPPORT_SLA_MANAGE', 'Configurar regra de contagem do SLA', 'Permite configurar como os prazos contratuais de suporte são contabilizados.')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where p.code in ('SUPPORT_TICKET_CREATE', 'SUPPORT_TICKET_READ')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria', 'Direção escolar')
  and p.code = 'SUPPORT_TICKET_MANAGE'
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria', 'Direção escolar', 'Fiscal/Auditoria')
  and p.code = 'SUPPORT_REPORT_READ'
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Administrador do sistema' and p.code = 'SUPPORT_SLA_MANAGE'
on conflict do nothing;
