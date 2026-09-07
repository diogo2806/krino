insert into access_permission (code, name, description) values
('AUDIT_READ', 'Consultar auditoria', 'Permite consultar o histórico de operações sensíveis da plataforma.'),
('DATA_EXPORT', 'Exportar dados administrativos', 'Permite gerar uma exportação estruturada dos dados da Administração sem credenciais ou segredos.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id
from access_role r
cross join access_permission p
where r.name = 'Administrador do sistema'
  and p.code in ('AUDIT_READ', 'DATA_EXPORT')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id
from access_role r
cross join access_permission p
where r.name = 'Fiscal/Auditoria'
  and p.code in ('AUDIT_READ', 'DATA_EXPORT')
on conflict do nothing;

create index if not exists ix_security_audit_action on security_audit_event(action);
create index if not exists ix_security_audit_target on security_audit_event(target_type, target_reference);
