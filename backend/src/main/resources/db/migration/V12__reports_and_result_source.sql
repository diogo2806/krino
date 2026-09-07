alter table network_assessment_result add column answer_sheet_id bigint references network_assessment_answer_sheet(id);

update network_assessment_result r
set answer_sheet_id = s.id
from network_assessment_answer_sheet s
where s.assessment_id = r.assessment_id
  and s.assignment_id = r.assignment_id
  and s.validation_status = 'VALID'
  and s.active_submission = true
  and r.answer_sheet_id is null;

create or replace function krino_set_assessment_result_answer_sheet()
returns trigger
language plpgsql
as $$
begin
    if new.answer_sheet_id is null then
        select s.id into new.answer_sheet_id
        from network_assessment_answer_sheet s
        where s.assessment_id = new.assessment_id
          and s.assignment_id = new.assignment_id
          and s.validation_status = 'VALID'
          and s.active_submission = true
        order by s.submitted_at desc, s.id desc
        limit 1;
    end if;
    return new;
end;
$$;

create trigger trg_network_assessment_result_answer_sheet
before insert on network_assessment_result
for each row execute function krino_set_assessment_result_answer_sheet();

create index ix_network_assessment_result_answer_sheet on network_assessment_result(answer_sheet_id);

create table network_assessment_performance_level (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    label varchar(100) not null,
    minimum_percent numeric(7,2) not null check (minimum_percent >= 0 and minimum_percent <= 100),
    maximum_percent numeric(7,2) not null check (maximum_percent >= 0 and maximum_percent <= 100),
    display_order integer not null check (display_order > 0),
    created_by varchar(120) not null,
    created_at timestamp with time zone not null default current_timestamp,
    unique (assessment_id, display_order),
    check (minimum_percent <= maximum_percent)
);

create index ix_network_assessment_performance_level on network_assessment_performance_level(assessment_id, minimum_percent, maximum_percent);

insert into access_permission (code, name, description) values
('REPORT_READ', 'Consultar relatórios e indicadores', 'Permite consultar relatórios e dashboards no escopo autorizado.'),
('REPORT_EXPORT', 'Exportar dados de relatórios', 'Permite exportar dados estruturados de relatórios no escopo autorizado.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'Coordenação da SME', 'Operador de avaliação', 'SME / Técnico da Secretaria', 'Fiscal/Auditoria')
  and p.code in ('REPORT_READ', 'REPORT_EXPORT')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Direção escolar', 'Coordenação escolar')
  and p.code in ('REPORT_READ', 'REPORT_EXPORT')
on conflict do nothing;
