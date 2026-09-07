create table network_assessment (
    id bigint generated always as identity primary key,
    name varchar(180) not null,
    stage varchar(20) not null check (stage in ('DIAGNOSTIC', 'MONITORING', 'FINAL')),
    academic_year integer not null check (academic_year between 2000 and 2200),
    grade_stage varchar(100) not null,
    component_id bigint references curricular_component(id),
    status varchar(20) not null default 'PREPARATION' check (status in ('PREPARATION', 'READY', 'APPLIED', 'PROCESSING', 'PROCESSED', 'CLOSED')),
    application_manual text,
    instructions text,
    created_by varchar(120) not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create index ix_network_assessment_year_stage on network_assessment(academic_year, stage);

create table network_assessment_scope (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    school_id bigint not null references school_unit(id),
    class_id bigint not null references school_class(id),
    created_at timestamp with time zone not null default current_timestamp,
    unique (assessment_id, class_id)
);

create index ix_network_assessment_scope_school on network_assessment_scope(assessment_id, school_id);

create table network_assessment_assignment (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    enrollment_id bigint not null references student_enrollment(id),
    student_id bigint not null references student(id),
    school_id bigint not null references school_unit(id),
    class_id bigint not null references school_class(id),
    attendance_status varchar(20) not null default 'PENDING' check (attendance_status in ('PENDING', 'PRESENT', 'ABSENT', 'MAKEUP')),
    label_code varchar(80) not null unique,
    package_code varchar(80) not null,
    online_access_code varchar(80) not null unique,
    created_at timestamp with time zone not null default current_timestamp,
    unique (assessment_id, enrollment_id)
);

create index ix_network_assessment_assignment_scope on network_assessment_assignment(assessment_id, school_id, class_id);
create index ix_network_assessment_assignment_student on network_assessment_assignment(assessment_id, student_id);

create table network_assessment_question (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    sequence_number integer not null check (sequence_number > 0),
    descriptor varchar(180) not null,
    skill varchar(300) not null,
    correct_option varchar(20) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    unique (assessment_id, sequence_number)
);

create table network_assessment_answer_sheet (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    assignment_id bigint not null references network_assessment_assignment(id),
    source_type varchar(20) not null check (source_type in ('MANUAL', 'IMPORT', 'ONLINE')),
    source_payload text not null,
    source_hash varchar(64) not null,
    validation_status varchar(20) not null check (validation_status in ('VALID', 'INVALID')),
    validation_message varchar(1000),
    active_submission boolean not null default true,
    submitted_by varchar(120) not null,
    submitted_at timestamp with time zone not null default current_timestamp
);

create unique index uq_network_assessment_active_sheet on network_assessment_answer_sheet(assessment_id, assignment_id) where active_submission = true;
create index ix_network_assessment_sheet_status on network_assessment_answer_sheet(assessment_id, validation_status, active_submission);

create table network_assessment_answer (
    answer_sheet_id bigint not null references network_assessment_answer_sheet(id) on delete cascade,
    question_id bigint not null references network_assessment_question(id),
    selected_option varchar(20),
    primary key (answer_sheet_id, question_id)
);

create table network_assessment_processing_run (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    run_number integer not null,
    status varchar(20) not null check (status in ('PROCESSING', 'COMPLETED', 'FAILED')),
    valid_sheets integer not null default 0,
    invalid_sheets integer not null default 0,
    initiated_by varchar(120) not null,
    started_at timestamp with time zone not null default current_timestamp,
    completed_at timestamp with time zone,
    unique (assessment_id, run_number)
);

create table network_assessment_result (
    id bigint generated always as identity primary key,
    processing_run_id bigint not null references network_assessment_processing_run(id) on delete cascade,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    assignment_id bigint not null references network_assessment_assignment(id),
    correct_answers integer not null check (correct_answers >= 0),
    total_questions integer not null check (total_questions >= 0),
    score_percent numeric(7,2),
    created_at timestamp with time zone not null default current_timestamp,
    unique (processing_run_id, assignment_id)
);

create index ix_network_assessment_result_scope on network_assessment_result(assessment_id, processing_run_id);

create table network_assessment_result_skill (
    id bigint generated always as identity primary key,
    result_id bigint not null references network_assessment_result(id) on delete cascade,
    descriptor varchar(180) not null,
    skill varchar(300) not null,
    correct_answers integer not null check (correct_answers >= 0),
    total_questions integer not null check (total_questions >= 0),
    score_percent numeric(7,2),
    unique (result_id, descriptor, skill)
);

create table network_assessment_occurrence (
    id bigint generated always as identity primary key,
    assessment_id bigint not null references network_assessment(id) on delete cascade,
    school_id bigint references school_unit(id),
    class_id bigint references school_class(id),
    occurred_at timestamp with time zone not null default current_timestamp,
    description varchar(2000) not null,
    created_by varchar(120) not null,
    created_at timestamp with time zone not null default current_timestamp
);

insert into access_permission (code, name, description) values
('ASSESSMENT_READ', 'Consultar avaliações em rede', 'Permite consultar avaliações em rede no escopo autorizado.'),
('ASSESSMENT_WRITE', 'Gerenciar avaliações em rede', 'Permite parametrizar avaliações, organizar estudantes, materiais e ocorrências.'),
('ASSESSMENT_PROCESS', 'Processar avaliações em rede', 'Permite validar, processar e reprocessar gabaritos com rastreabilidade.'),
('ASSESSMENT_RESULT_READ', 'Consultar resultados de avaliações em rede', 'Permite consultar resultados consolidados no escopo autorizado.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'Coordenação da SME', 'Operador de avaliação')
  and p.code in ('ASSESSMENT_READ', 'ASSESSMENT_WRITE', 'ASSESSMENT_PROCESS', 'ASSESSMENT_RESULT_READ')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('SME / Técnico da Secretaria', 'Direção escolar', 'Coordenação escolar', 'Fiscal/Auditoria')
  and p.code in ('ASSESSMENT_READ', 'ASSESSMENT_RESULT_READ')
on conflict do nothing;
