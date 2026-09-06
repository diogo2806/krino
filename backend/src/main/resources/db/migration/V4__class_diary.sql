create table professional_user_account (
    professional_id bigint primary key references education_professional(id) on delete cascade,
    user_id bigint not null unique references app_user(id) on delete cascade,
    created_at timestamp with time zone not null default current_timestamp
);

create table class_diary (
    id bigint generated always as identity primary key,
    class_id bigint not null references school_class(id),
    component_id bigint references curricular_component(id),
    mode varchar(30) not null check (mode in ('EARLY_CHILDHOOD', 'LITERACY', 'EARLY_YEARS', 'FINAL_YEARS', 'EJA')),
    responsible_professional_id bigint not null references education_professional(id),
    valid_from date not null,
    valid_until date,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    check (valid_until is null or valid_until >= valid_from)
);

create unique index ux_class_diary_scope on class_diary (class_id, coalesce(component_id, 0), valid_from);
create index ix_class_diary_class on class_diary(class_id, active);

create table diary_lesson (
    id bigint generated always as identity primary key,
    diary_id bigint not null references class_diary(id) on delete cascade,
    lesson_date date not null,
    lesson_slot integer not null default 1 check (lesson_slot > 0),
    content varchar(4000),
    planning_notes varchar(4000),
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    unique (diary_id, lesson_date, lesson_slot)
);

create table diary_attendance (
    lesson_id bigint not null references diary_lesson(id) on delete cascade,
    enrollment_id bigint not null references student_enrollment(id),
    attendance_status varchar(20) not null check (attendance_status in ('PRESENT', 'ABSENT', 'EXCUSED')),
    updated_at timestamp with time zone not null default current_timestamp,
    primary key (lesson_id, enrollment_id)
);

create table diary_assessment (
    id bigint generated always as identity primary key,
    diary_id bigint not null references class_diary(id) on delete cascade,
    period integer not null check (period between 1 and 4),
    title varchar(180) not null,
    assessment_date date not null,
    max_score numeric(7,2),
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    check (max_score is null or max_score > 0)
);

create table diary_assessment_grade (
    assessment_id bigint not null references diary_assessment(id) on delete cascade,
    enrollment_id bigint not null references student_enrollment(id),
    score numeric(7,2),
    observation varchar(1000),
    updated_at timestamp with time zone not null default current_timestamp,
    primary key (assessment_id, enrollment_id),
    check (score is null or score >= 0)
);

create table curriculum_item (
    id bigint generated always as identity primary key,
    source varchar(120) not null,
    stage varchar(100) not null,
    component_id bigint references curricular_component(id),
    code varchar(80) not null,
    description varchar(2000) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp
);

create unique index ux_curriculum_item on curriculum_item (source, stage, coalesce(component_id, 0), code);

create table diary_planning (
    id bigint generated always as identity primary key,
    diary_id bigint not null references class_diary(id) on delete cascade,
    period integer not null check (period between 1 and 4),
    title varchar(180) not null,
    description varchar(4000) not null,
    updated_by varchar(120) not null,
    updated_at timestamp with time zone not null default current_timestamp,
    unique (diary_id, period)
);

create table diary_planning_curriculum (
    planning_id bigint not null references diary_planning(id) on delete cascade,
    curriculum_item_id bigint not null references curriculum_item(id),
    primary key (planning_id, curriculum_item_id)
);

insert into access_permission (code, name, description) values
('DIARY_READ', 'Consultar Diário de Classe', 'Permite consultar diários no escopo atribuído.'),
('DIARY_EDIT', 'Editar Diário de Classe atribuído', 'Permite editar diário quando a conta está vinculada ao professor responsável.'),
('DIARY_ADMIN', 'Administrar Diário de Classe', 'Permite criar, configurar e administrar diários no escopo atribuído.'),
('CURRICULUM_MANAGE', 'Gerenciar referências curriculares', 'Permite cadastrar referências curriculares validadas pela Administração para uso no diário.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria')
  and p.code in ('DIARY_READ', 'DIARY_ADMIN', 'CURRICULUM_MANAGE')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Direção escolar', 'Coordenação da SME', 'Coordenação escolar')
  and p.code in ('DIARY_READ', 'DIARY_ADMIN')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Secretaria escolar' and p.code = 'DIARY_READ'
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Professor' and p.code in ('DIARY_READ', 'DIARY_EDIT')
on conflict do nothing;
