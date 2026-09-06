create table pedagogical_indicator_record (
    id bigint generated always as identity primary key,
    indicator varchar(20) not null check (indicator in ('IDEB', 'IDEPE')),
    record_type varchar(30) not null check (record_type in ('OBSERVED_RESULT', 'SIMULATION', 'PROJECTION')),
    scope_type varchar(20) not null check (scope_type in ('NETWORK', 'SCHOOL')),
    school_id bigint references school_unit(id),
    academic_year integer not null,
    scenario_name varchar(180) not null,
    source_reference varchar(2000) not null,
    assumptions varchar(4000),
    indicator_value numeric(10,3) not null check (indicator_value >= 0),
    classification varchar(40) not null check (classification in ('DOCUMENTED_REFERENCE', 'NON_OFFICIAL')),
    created_by varchar(120) not null,
    created_at timestamp with time zone not null default current_timestamp,
    check ((scope_type = 'NETWORK' and school_id is null) or (scope_type = 'SCHOOL' and school_id is not null)),
    check ((record_type = 'OBSERVED_RESULT' and classification = 'DOCUMENTED_REFERENCE') or (record_type in ('SIMULATION', 'PROJECTION') and classification = 'NON_OFFICIAL'))
);

create index ix_pedagogical_indicator_record_year on pedagogical_indicator_record(academic_year, scope_type, school_id, indicator, record_type);

insert into access_permission (code, name, description) values
('MONITORING_READ', 'Consultar Monitoramento Pedagógico', 'Permite consultar indicadores pedagógicos no escopo atribuído.'),
('MONITORING_MANAGE', 'Gerenciar Monitoramento Pedagógico', 'Permite registrar referências documentadas e cenários não oficiais de monitoramento no escopo atribuído.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria', 'Coordenação da SME')
  and p.code in ('MONITORING_READ', 'MONITORING_MANAGE')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Direção escolar', 'Coordenação escolar') and p.code = 'MONITORING_READ'
on conflict do nothing;
