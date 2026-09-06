create table app_user (
    id bigint generated always as identity primary key,
    username varchar(120) not null,
    display_name varchar(180) not null,
    password_hash varchar(100) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create unique index uq_app_user_username_lower on app_user (lower(username));

create table access_role (
    id bigint generated always as identity primary key,
    name varchar(120) not null,
    description varchar(500),
    system_role boolean not null default false,
    created_at timestamp with time zone not null default current_timestamp
);

create unique index uq_access_role_name_lower on access_role (lower(name));

create table access_permission (
    id bigint generated always as identity primary key,
    code varchar(100) not null unique,
    name varchar(180) not null,
    description varchar(500)
);

create table access_role_permission (
    role_id bigint not null references access_role(id) on delete cascade,
    permission_id bigint not null references access_permission(id) on delete cascade,
    primary key (role_id, permission_id)
);

create table user_role_assignment (
    id bigint generated always as identity primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    role_id bigint not null references access_role(id),
    scope_type varchar(20) not null check (scope_type in ('NETWORK', 'SCHOOL', 'USER')),
    scope_reference varchar(120),
    created_at timestamp with time zone not null default current_timestamp
);

create unique index uq_user_role_scope on user_role_assignment (user_id, role_id, scope_type, coalesce(scope_reference, ''));
create index ix_user_role_assignment_user on user_role_assignment(user_id);

create table linked_resource_access (
    id bigint generated always as identity primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    resource_type varchar(30) not null check (resource_type in ('STUDENT', 'DIARY')),
    resource_reference varchar(120) not null,
    access_level varchar(20) not null check (access_level in ('READ', 'EDIT')),
    created_at timestamp with time zone not null default current_timestamp,
    unique (user_id, resource_type, resource_reference)
);

create table security_audit_event (
    id bigint generated always as identity primary key,
    actor_username varchar(120) not null,
    action varchar(100) not null,
    target_type varchar(80) not null,
    target_reference varchar(120),
    details varchar(1000),
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_security_audit_created_at on security_audit_event(created_at);
create index ix_security_audit_actor on security_audit_event(actor_username);

insert into access_permission (code, name, description) values
('USER_READ', 'Consultar usuários', 'Permite consultar usuários e seus vínculos de acesso.'),
('USER_WRITE', 'Gerenciar usuários', 'Permite criar, editar, desativar e redefinir senha de usuários.'),
('ROLE_READ', 'Consultar perfis e permissões', 'Permite consultar perfis e catálogo de permissões.'),
('ROLE_WRITE', 'Gerenciar perfis e permissões', 'Permite criar, editar e configurar perfis.'),
('SCOPE_ASSIGN', 'Atribuir escopo de acesso', 'Permite vincular perfis a escopos de Rede, unidade escolar ou usuário.'),
('STUDENT_LINKED_READ', 'Consultar estudante vinculado', 'Permissão destinada ao responsável legal, condicionada ao vínculo com o estudante.'),
('DIARY_ASSIGNED_EDIT', 'Editar diário atribuído', 'Permissão destinada ao professor, condicionada à responsabilidade pelo diário.');

insert into access_role (name, description, system_role) values
('Administrador do sistema', 'Administração de usuários, perfis, parâmetros globais e segurança.', true),
('SME / Técnico da Secretaria', 'Atuação municipal em cadastros e relatórios da Rede.', true),
('Coordenação da SME', 'Monitoramento pedagógico municipal, indicadores e avaliações.', true),
('Direção escolar', 'Gestão administrativa e pedagógica da unidade escolar.', true),
('Secretaria escolar', 'Estudantes, matrículas, documentos, turmas, calendário e horários.', true),
('Coordenação escolar', 'Acompanhamento pedagógico da escola e das turmas.', true),
('Professor', 'Diários sob responsabilidade, frequência, conteúdos, notas e planejamento.', true),
('Responsável legal', 'Consulta somente aos estudantes legalmente vinculados.', true),
('Estudante do transporte', 'Solicitação e acompanhamento do transporte universitário.', true),
('Operador de avaliação', 'Preparação, processamento e resultados de avaliações conforme permissão.', true),
('Fiscal/Auditoria', 'Consulta de evidências, relatórios e auditoria conforme autorização.', true);

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p where r.name = 'Administrador do sistema';

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r join access_permission p on p.code = 'DIARY_ASSIGNED_EDIT' where r.name = 'Professor';

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r join access_permission p on p.code = 'STUDENT_LINKED_READ' where r.name = 'Responsável legal';
