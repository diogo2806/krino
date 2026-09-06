create table school_unit (
    id bigint generated always as identity primary key,
    code varchar(40) not null unique,
    name varchar(180) not null,
    address varchar(300),
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table education_professional (
    id bigint generated always as identity primary key,
    origin_school_id bigint references school_unit(id),
    registration varchar(60) not null unique,
    name varchar(180) not null,
    professional_type varchar(80) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table student (
    id bigint generated always as identity primary key,
    origin_school_id bigint references school_unit(id),
    registration varchar(60) not null unique,
    name varchar(180) not null,
    birth_date date,
    guardian_name varchar(180),
    guardian_profession varchar(180),
    status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE', 'DECEASED')),
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table school_class (
    id bigint generated always as identity primary key,
    school_id bigint not null references school_unit(id),
    academic_year integer not null check (academic_year between 2000 and 2200),
    name varchar(80) not null,
    stage varchar(100) not null,
    shift varchar(30) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    unique (school_id, academic_year, name)
);

create table curricular_component (
    id bigint generated always as identity primary key,
    code varchar(30) not null unique,
    name varchar(120) not null unique,
    active boolean not null default true
);

create table student_enrollment (
    id bigint generated always as identity primary key,
    student_id bigint not null references student(id),
    class_id bigint not null references school_class(id),
    academic_year integer not null,
    enrollment_type varchar(20) not null check (enrollment_type in ('ENROLLMENT', 'REENROLLMENT', 'CLASS_CHANGE')),
    enrollment_date date not null,
    status varchar(30) not null default 'ACTIVE' check (status in ('ACTIVE', 'TRANSFERRED', 'CLASS_CHANGED', 'DECEASED', 'COMPLETED')),
    previous_enrollment_id bigint references student_enrollment(id),
    created_at timestamp with time zone not null default current_timestamp,
    unique (student_id, class_id, academic_year)
);

create index ix_student_enrollment_student on student_enrollment(student_id, academic_year);
create index ix_student_enrollment_class on student_enrollment(class_id, status);

create table student_movement (
    id bigint generated always as identity primary key,
    enrollment_id bigint not null references student_enrollment(id),
    movement_type varchar(30) not null check (movement_type in ('TRANSFER', 'CLASS_CHANGE', 'DEATH')),
    effective_date date not null,
    destination_class_id bigint references school_class(id),
    notes varchar(1000),
    created_by varchar(120) not null,
    created_at timestamp with time zone not null default current_timestamp
);

create table teacher_assignment (
    id bigint generated always as identity primary key,
    professional_id bigint not null references education_professional(id),
    class_id bigint not null references school_class(id),
    component_id bigint not null references curricular_component(id),
    valid_from date not null,
    valid_until date,
    created_at timestamp with time zone not null default current_timestamp,
    check (valid_until is null or valid_until >= valid_from),
    unique (professional_id, class_id, component_id, valid_from)
);

create table school_calendar_day (
    id bigint generated always as identity primary key,
    school_id bigint not null references school_unit(id),
    academic_date date not null,
    school_day boolean not null,
    description varchar(300),
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    unique (school_id, academic_date)
);

create table class_schedule (
    id bigint generated always as identity primary key,
    class_id bigint not null references school_class(id),
    component_id bigint not null references curricular_component(id),
    professional_id bigint references education_professional(id),
    day_of_week integer not null check (day_of_week between 1 and 7),
    start_time time not null,
    end_time time not null,
    valid_from date not null,
    valid_until date,
    created_at timestamp with time zone not null default current_timestamp,
    check (end_time > start_time),
    check (valid_until is null or valid_until >= valid_from),
    unique (class_id, component_id, day_of_week, start_time, valid_from)
);

create table student_term_result (
    id bigint generated always as identity primary key,
    enrollment_id bigint not null references student_enrollment(id) on delete cascade,
    component_id bigint not null references curricular_component(id),
    period integer not null check (period between 1 and 4),
    grade numeric(5,2),
    absences integer not null default 0 check (absences >= 0),
    classes_count integer not null default 0 check (classes_count >= 0),
    updated_at timestamp with time zone not null default current_timestamp,
    unique (enrollment_id, component_id, period)
);

insert into curricular_component (code, name) values
('PORT', 'Língua Portuguesa'),
('MAT', 'Matemática'),
('CIEN', 'Ciências'),
('HIST', 'História'),
('GEO', 'Geografia'),
('ARTE', 'Arte'),
('EFI', 'Educação Física'),
('ING', 'Língua Inglesa');

insert into access_permission (code, name, description) values
('SCHOOL_READ', 'Consultar Secretaria Escolar', 'Permite consultar cadastros e estrutura escolar no escopo atribuído.'),
('SCHOOL_WRITE', 'Gerenciar Secretaria Escolar', 'Permite criar e alterar cadastros, matrículas, calendário, horários e vínculos no escopo atribuído.'),
('SCHOOL_DOCUMENT_READ', 'Emitir documentos escolares', 'Permite emitir documentos escolares a partir dos dados persistidos no escopo atribuído.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria', 'Direção escolar', 'Secretaria escolar')
  and p.code in ('SCHOOL_READ', 'SCHOOL_WRITE', 'SCHOOL_DOCUMENT_READ')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Coordenação da SME', 'Coordenação escolar', 'Professor')
  and p.code = 'SCHOOL_READ'
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Coordenação da SME', 'Coordenação escolar')
  and p.code = 'SCHOOL_DOCUMENT_READ'
on conflict do nothing;
