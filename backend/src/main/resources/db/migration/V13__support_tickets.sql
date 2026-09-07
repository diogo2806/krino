create table support_ticket (
    id bigint generated always as identity primary key,
    opened_by_user_id bigint not null references app_user(id),
    subject varchar(200) not null,
    description varchar(4000) not null,
    severity varchar(20) not null check (severity in ('CRITICAL', 'MEDIUM', 'LOW')),
    status varchar(30) not null default 'OPEN' check (status in ('OPEN', 'IN_PROGRESS', 'WAITING_REQUESTER', 'RESOLVED', 'CLOSED')),
    resolution varchar(4000),
    opened_at timestamp with time zone not null default current_timestamp,
    first_support_response_at timestamp with time zone,
    resolved_at timestamp with time zone,
    closed_at timestamp with time zone,
    updated_at timestamp with time zone not null default current_timestamp
);

create index ix_support_ticket_requester on support_ticket(opened_by_user_id, opened_at desc);
create index ix_support_ticket_status on support_ticket(status, severity, opened_at desc);

create table support_ticket_event (
    id bigint generated always as identity primary key,
    ticket_id bigint not null references support_ticket(id) on delete cascade,
    actor_user_id bigint references app_user(id),
    actor_username varchar(120) not null,
    event_type varchar(30) not null check (event_type in ('CREATED', 'MESSAGE', 'STATUS_CHANGED', 'SEVERITY_CHANGED', 'RESOLUTION_RECORDED')),
    previous_value varchar(120),
    new_value varchar(120),
    message varchar(4000),
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_support_ticket_event_ticket on support_ticket_event(ticket_id, created_at, id);

insert into access_permission (code, name, description) values
('SUPPORT_TICKET_CREATE', 'Abrir e acompanhar chamados', 'Permite abrir chamados de suporte e acompanhar os próprios atendimentos.'),
('SUPPORT_TICKET_MANAGE', 'Gerenciar chamados de suporte', 'Permite consultar e gerenciar chamados de toda a plataforma no escopo municipal.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id
from access_role r
cross join access_permission p
where p.code = 'SUPPORT_TICKET_CREATE'
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id
from access_role r
cross join access_permission p
where r.name = 'Administrador do sistema'
  and p.code = 'SUPPORT_TICKET_MANAGE'
on conflict do nothing;
