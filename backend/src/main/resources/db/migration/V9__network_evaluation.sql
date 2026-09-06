create table network_evaluation (
    id bigint generated always as identity primary key,
    name varchar(180) not null,
    evaluation_stage varchar(30) not null check (evaluation_stage in ('DIAGNOSTIC', 'MONITORING', 'FINAL')),
    academic_year integer not null check (academic_year between 2000 and 2200),
    grade_stage varchar(100) not null,
    status varchar(30) not null default 'PREPARATION' check (status in ('PREPARATION', 'OPEN', 'CLOSED')),
    application_date date,
    materials_received_at date,
    applicator_instructions text,
    created_by bigint not null references app_user(id),
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create index ix_network_evaluation_year_stage on network_evaluation(academic_year, evaluation_stage, status);

create table network_evaluation_class (
    evaluation_id bigint not null references network_evaluation(id) on delete cascade,
    class_id bigint not null references school_class(id),
    primary key (evaluation_id, class_id)
);

create table network_evaluation_item (
    id bigint generated always as identity primary key,
    evaluation_id bigint not null references network_evaluation(id) on delete cascade,
    item_number integer not null check (item_number > 0),
    component_id bigint not null references curricular_component(id),
    prompt_text text,
    correct_answer varchar(20) not null,
    max_score numeric(8,2) not null default 1 check (max_score > 0),
    skill_code varchar(80),
    skill_label varchar(300),
    descriptor_code varchar(80),
    descriptor_label varchar(300),
    unique (evaluation_id, item_number)
);

create index ix_network_evaluation_item_component on network_evaluation_item(evaluation_id, component_id);
create index ix_network_evaluation_item_skill on network_evaluation_item(evaluation_id, skill_code, descriptor_code);

create table network_evaluation_student (
    id bigint generated always as identity primary key,
    evaluation_id bigint not null references network_evaluation(id) on delete cascade,
    enrollment_id bigint not null references student_enrollment(id),
    student_id bigint not null references student(id),
    school_id bigint not null references school_unit(id),
    class_id bigint not null references school_class(id),
    organization_status varchar(20) not null default 'ORGANIZED' check (organization_status in ('ORGANIZED', 'PRESENT', 'ABSENT')),
    online_token_hash varchar(64),
    online_token_expires_at timestamp with time zone,
    online_completed_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    unique (evaluation_id, student_id)
);

create unique index uq_network_evaluation_online_token on network_evaluation_student(online_token_hash) where online_token_hash is not null;
create index ix_network_evaluation_student_scope on network_evaluation_student(evaluation_id, school_id, class_id, student_id);

create table network_evaluation_occurrence (
    id bigint generated always as identity primary key,
    evaluation_id bigint not null references network_evaluation(id) on delete cascade,
    class_id bigint not null references school_class(id),
    occurred_at timestamp with time zone not null,
    description varchar(2000) not null,
    created_by bigint not null references app_user(id),
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_network_evaluation_occurrence on network_evaluation_occurrence(evaluation_id, class_id, occurred_at);

create table network_evaluation_answer_batch (
    id bigint generated always as identity primary key,
    evaluation_id bigint not null references network_evaluation(id) on delete cascade,
    source_type varchar(20) not null check (source_type in ('CSV', 'MANUAL', 'ONLINE')),
    original_filename varchar(255),
    raw_source text not null,
    state varchar(30) not null check (state in ('NOT_PROCESSED', 'WITH_INCONSISTENCIES', 'PROCESSED')),
    total_records integer not null default 0 check (total_records >= 0),
    valid_records integer not null default 0 check (valid_records >= 0),
    invalid_records integer not null default 0 check (invalid_records >= 0),
    created_by bigint references app_user(id),
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_network_evaluation_batch on network_evaluation_answer_batch(evaluation_id, created_at desc);

create table network_evaluation_answer_record (
    id bigint generated always as identity primary key,
    batch_id bigint not null references network_evaluation_answer_batch(id) on delete cascade,
    evaluation_student_id bigint references network_evaluation_student(id),
    student_registration varchar(60) not null,
    raw_answers text not null,
    valid boolean not null,
    inconsistency_reason varchar(1000),
    created_at timestamp with time zone not null default current_timestamp
);

create index ix_network_evaluation_answer_record on network_evaluation_answer_record(batch_id, valid, student_registration);

create table network_evaluation_processing_run (
    id bigint generated always as identity primary key,
    evaluation_id bigint not null references network_evaluation(id) on delete cascade,
    batch_id bigint not null references network_evaluation_answer_batch(id),
    run_number integer not null check (run_number > 0),
    previous_run_id bigint references network_evaluation_processing_run(id),
    processed_by bigint not null references app_user(id),
    processed_at timestamp with time zone not null default current_timestamp,
    unique (evaluation_id, run_number)
);

create index ix_network_evaluation_run_latest on network_evaluation_processing_run(evaluation_id, run_number desc);

create table network_evaluation_student_result (
    id bigint generated always as identity primary key,
    run_id bigint not null references network_evaluation_processing_run(id) on delete cascade,
    evaluation_student_id bigint not null references network_evaluation_student(id),
    answered_items integer not null check (answered_items >= 0),
    correct_items integer not null check (correct_items >= 0),
    score numeric(10,2) not null check (score >= 0),
    max_score numeric(10,2) not null check (max_score >= 0),
    percentage numeric(6,2),
    unique (run_id, evaluation_student_id)
);

create table network_evaluation_item_result (
    id bigint generated always as identity primary key,
    run_id bigint not null references network_evaluation_processing_run(id) on delete cascade,
    evaluation_student_id bigint not null references network_evaluation_student(id),
    item_id bigint not null references network_evaluation_item(id),
    marked_answer varchar(20),
    correct boolean not null,
    score numeric(8,2) not null check (score >= 0),
    unique (run_id, evaluation_student_id, item_id)
);

create index ix_network_evaluation_item_result_item on network_evaluation_item_result(run_id, item_id, correct);

insert into access_permission (code, name, description) values
('EVALUATION_READ', 'Consultar Avaliação em Rede', 'Permite consultar avaliações e organização no escopo autorizado.'),
('EVALUATION_MANAGE', 'Gerenciar Avaliação em Rede', 'Permite cadastrar avaliações, itens, turmas, estudantes, materiais e segunda chamada em escopo municipal.'),
('EVALUATION_PROCESS', 'Processar gabaritos', 'Permite importar, validar e processar gabaritos em escopo municipal.'),
('EVALUATION_RESULT_READ', 'Consultar resultados da Avaliação em Rede', 'Permite consultar resultados consolidados no escopo autorizado.');

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Administrador do sistema', 'SME / Técnico da Secretaria', 'Operador de avaliação')
  and p.code like 'EVALUATION_%'
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name = 'Coordenação da SME'
  and p.code in ('EVALUATION_READ', 'EVALUATION_RESULT_READ')
on conflict do nothing;

insert into access_role_permission (role_id, permission_id)
select r.id, p.id from access_role r cross join access_permission p
where r.name in ('Direção escolar', 'Coordenação escolar')
  and p.code in ('EVALUATION_READ', 'EVALUATION_RESULT_READ')
on conflict do nothing;
