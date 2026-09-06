import { CalendarPlus, FileText, GraduationCap, History, Pencil, Plus, RefreshCw, UserRoundPlus } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { AccessContext } from '../workspace/types';
import { CalendarDayDialog } from './CalendarDayDialog';
import { ClassAssignmentsDialog } from './ClassAssignmentsDialog';
import { ClassDialog } from './ClassDialog';
import { DocumentsPanel } from './DocumentsPanel';
import { EnrollmentDialog } from './EnrollmentDialog';
import { MovementDialog } from './MovementDialog';
import { MovementHistoryDialog } from './MovementHistoryDialog';
import { ProfessionalDialog } from './ProfessionalDialog';
import { ScheduleDialog } from './ScheduleDialog';
import { SchoolDialog } from './SchoolDialog';
import { StudentDialog } from './StudentDialog';
import type { CalendarDay, Component, Enrollment, Professional, Schedule, School, SchoolClass, Student } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type Tab = 'students' | 'professionals' | 'schools' | 'classes' | 'enrollments' | 'calendar' | 'schedules' | 'documents';

const tabs: { value: Tab; label: string }[] = [
  { value: 'students', label: 'Estudantes' }, { value: 'professionals', label: 'Profissionais da educação' }, { value: 'schools', label: 'Unidades escolares' }, { value: 'classes', label: 'Turmas' }, { value: 'enrollments', label: 'Matrículas' }, { value: 'calendar', label: 'Calendário escolar' }, { value: 'schedules', label: 'Horários de aula' }, { value: 'documents', label: 'Documentos' },
];

const manualSections = [
  { title: 'Finalidade', content: 'Manter os cadastros e vínculos usados pela Secretaria Escolar, Diário de Classe, Portal da Família e demais módulos do KRINO.' },
  { title: 'Campos e filtros', content: 'Unidade escolar e ano letivo definem o contexto. Buscar filtra estudantes e profissionais. Em Horários de aula, selecione também a turma.' },
  { title: 'Botões e ações', content: 'Novo cria o registro da seção atual. Editar altera dados. Matricular registra matrícula ou rematrícula. Movimentar registra transferência, troca de turma ou falecimento com data de efeito. Professores e componentes mantém atribuições por vigência. Documentos gera a emissão selecionada.' },
  { title: 'Regras', content: 'Movimentações preservam matrículas anteriores. Troca de turma cria nova matrícula ligada à anterior. Calendário e horários são vigentes por período e serão usados para validar o Diário de Classe.' },
  { title: 'Permissões', content: 'Ações de consulta, alteração e emissão são avaliadas por Rede ou por unidade escolar. A interface reduz as ações conforme o contexto e o backend valida novamente cada operação.' },
  { title: 'Fluxos', content: 'Cadastre a unidade, estudantes e profissionais; crie turmas; matricule estudantes; atribua professores/componentes; configure calendário e horários; por fim, emita documentos usando os dados persistidos.' },
  { title: 'Mensagens e estados', content: 'Carregamento, vazio, falha técnica e falta de permissão são apresentados separadamente. Conflitos de matrícula, turma, vigência ou vínculo informam o motivo da operação não poder continuar.' },
];

export function SecretariaEscolarPage({ context, onUnauthorized }: Props) {
  const currentYear = new Date().getFullYear();
  const [tab, setTab] = useState<Tab>('students');
  const [schools, setSchools] = useState<School[]>([]); const [schoolId, setSchoolId] = useState(''); const [year, setYear] = useState(currentYear.toString()); const [search, setSearch] = useState('');
  const [students, setStudents] = useState<Student[]>([]); const [professionals, setProfessionals] = useState<Professional[]>([]); const [classes, setClasses] = useState<SchoolClass[]>([]); const [enrollments, setEnrollments] = useState<Enrollment[]>([]); const [calendar, setCalendar] = useState<CalendarDay[]>([]); const [components, setComponents] = useState<Component[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]); const [scheduleClassId, setScheduleClassId] = useState('');
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [denied, setDenied] = useState(false);
  const [schoolDialog, setSchoolDialog] = useState<{ open: boolean; value?: School }>({ open: false }); const [studentDialog, setStudentDialog] = useState<{ open: boolean; value?: Student }>({ open: false }); const [professionalDialog, setProfessionalDialog] = useState<{ open: boolean; value?: Professional }>({ open: false }); const [classDialog, setClassDialog] = useState<{ open: boolean; value?: SchoolClass }>({ open: false });
  const [enrollmentDialog, setEnrollmentDialog] = useState<{ open: boolean; studentId?: number }>({ open: false }); const [movementEnrollment, setMovementEnrollment] = useState<Enrollment>(); const [historyStudent, setHistoryStudent] = useState<Student>(); const [assignmentClass, setAssignmentClass] = useState<SchoolClass>(); const [calendarDialog, setCalendarDialog] = useState<{ open: boolean; value?: CalendarDay }>({ open: false }); const [scheduleDialog, setScheduleDialog] = useState(false);

  const selectedSchool = schools.find((school) => school.id.toString() === schoolId);
  const schoolScopePermissions = selectedSchool ? context.schoolAccess.find((scope) => scope.schoolCode === selectedSchool.code)?.permissions ?? [] : [];
  const canRead = context.networkPermissions.includes('SCHOOL_READ') || schoolScopePermissions.includes('SCHOOL_READ');
  const canWrite = context.networkPermissions.includes('SCHOOL_WRITE') || schoolScopePermissions.includes('SCHOOL_WRITE');
  const canCreateSchool = context.networkPermissions.includes('SCHOOL_WRITE');
  const canDocuments = context.networkPermissions.includes('SCHOOL_DOCUMENT_READ') || schoolScopePermissions.includes('SCHOOL_DOCUMENT_READ');

  const loadSchools = useCallback(async () => {
    try {
      const [nextSchools, nextComponents] = await Promise.all([apiRequest<School[]>('/secretaria/schools'), apiRequest<Component[]>('/secretaria/components')]);
      setSchools(nextSchools); setComponents(nextComponents);
      setSchoolId((current) => current && nextSchools.some((school) => school.id.toString() === current) ? current : nextSchools[0]?.id.toString() ?? '');
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar as unidades escolares.');
    }
  }, [onUnauthorized]);

  const loadSchoolData = useCallback(async () => {
    if (!schoolId) { setLoading(false); return; }
    setLoading(true); setError(''); setDenied(false);
    try {
      const query = `schoolId=${schoolId}&year=${year}`;
      const [nextStudents, nextProfessionals, nextClasses, nextEnrollments, nextCalendar] = await Promise.all([
        apiRequest<Student[]>(`/secretaria/students?${query}&search=${encodeURIComponent(search)}`),
        apiRequest<Professional[]>(`/secretaria/professionals?schoolId=${schoolId}&search=${encodeURIComponent(search)}`),
        apiRequest<SchoolClass[]>(`/secretaria/classes?${query}`),
        apiRequest<Enrollment[]>(`/secretaria/enrollments?${query}`),
        apiRequest<CalendarDay[]>(`/secretaria/calendar?${query}`),
      ]);
      setStudents(nextStudents); setProfessionals(nextProfessionals); setClasses(nextClasses); setEnrollments(nextEnrollments); setCalendar(nextCalendar);
      setScheduleClassId((current) => current && nextClasses.some((item) => item.id.toString() === current) ? current : nextClasses[0]?.id.toString() ?? '');
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os dados da Secretaria Escolar.');
    } finally { setLoading(false); }
  }, [schoolId, year, search, onUnauthorized]);

  const loadSchedules = useCallback(async () => {
    if (!scheduleClassId) { setSchedules([]); return; }
    try { setSchedules(await apiRequest<Schedule[]>(`/secretaria/schedules?classId=${scheduleClassId}`)); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os horários de aula.'); }
  }, [scheduleClassId]);

  useEffect(() => { void loadSchools(); }, [loadSchools]);
  useEffect(() => { void loadSchoolData(); }, [loadSchoolData]);
  useEffect(() => { if (tab === 'schedules') void loadSchedules(); }, [tab, loadSchedules]);

  const activeScheduleClass = classes.find((item) => item.id.toString() === scheduleClassId);
  const reloadAll = async () => { await loadSchools(); await loadSchoolData(); if (tab === 'schedules') await loadSchedules(); };

  const studentColumns: DataColumn<Student>[] = useMemo(() => [
    { key: 'student', header: 'Estudante', render: (row) => <><strong>{row.name}</strong><small>{row.registration}</small></> },
    { key: 'guardian', header: 'Responsável', render: (row) => row.guardianName || 'Não informado' },
    { key: 'status', header: 'Situação', render: (row) => row.status === 'DECEASED' ? 'Falecimento registrado' : 'Ativo' },
    { key: 'actions', header: 'Ações', render: (row) => <div className="row-actions">{canWrite ? <Button type="button" variant="ghost" onClick={() => setStudentDialog({ open: true, value: row })}><Pencil aria-hidden="true" size={16} />Editar</Button> : null}{canWrite && row.status !== 'DECEASED' ? <Button type="button" variant="ghost" onClick={() => setEnrollmentDialog({ open: true, studentId: row.id })}><GraduationCap aria-hidden="true" size={16} />Matricular</Button> : null}<Button type="button" variant="ghost" onClick={() => setHistoryStudent(row)}><History aria-hidden="true" size={16} />Movimentações</Button></div> },
  ], [canWrite]);

  const professionalColumns: DataColumn<Professional>[] = [
    { key: 'professional', header: 'Profissional da educação', render: (row) => <><strong>{row.name}</strong><small>{row.registration}</small></> }, { key: 'type', header: 'Função', render: (row) => row.professionalType }, { key: 'status', header: 'Situação', render: (row) => row.active ? 'Ativo' : 'Inativo' }, { key: 'actions', header: 'Ações', render: (row) => canWrite ? <Button type="button" variant="ghost" onClick={() => setProfessionalDialog({ open: true, value: row })}><Pencil aria-hidden="true" size={16} />Editar</Button> : null },
  ];
  const schoolColumns: DataColumn<School>[] = [
    { key: 'school', header: 'Unidade escolar', render: (row) => <><strong>{row.name}</strong><small>{row.code}</small></> }, { key: 'address', header: 'Endereço', render: (row) => row.address || 'Não informado' }, { key: 'status', header: 'Situação', render: (row) => row.active ? 'Ativa' : 'Inativa' }, { key: 'actions', header: 'Ações', render: (row) => (context.networkPermissions.includes('SCHOOL_WRITE') || context.schoolAccess.find((scope) => scope.schoolCode === row.code)?.permissions.includes('SCHOOL_WRITE')) ? <Button type="button" variant="ghost" onClick={() => setSchoolDialog({ open: true, value: row })}><Pencil aria-hidden="true" size={16} />Editar</Button> : null },
  ];
  const classColumns: DataColumn<SchoolClass>[] = [
    { key: 'class', header: 'Turma', render: (row) => <><strong>{row.name}</strong><small>{row.stage}</small></> }, { key: 'shift', header: 'Turno', render: (row) => row.shift }, { key: 'year', header: 'Ano letivo', render: (row) => row.academicYear }, { key: 'actions', header: 'Ações', render: (row) => <div className="row-actions">{canWrite ? <Button type="button" variant="ghost" onClick={() => setClassDialog({ open: true, value: row })}><Pencil aria-hidden="true" size={16} />Editar</Button> : null}<Button type="button" variant="ghost" onClick={() => setAssignmentClass(row)}><UserRoundPlus aria-hidden="true" size={16} />Professores e componentes</Button><Button type="button" variant="ghost" onClick={() => { setScheduleClassId(row.id.toString()); setTab('schedules'); }}>Horários</Button></div> },
  ];
  const enrollmentColumns: DataColumn<Enrollment>[] = [
    { key: 'student', header: 'Estudante', render: (row) => <><strong>{row.studentName}</strong><small>{row.registration}</small></> }, { key: 'class', header: 'Turma', render: (row) => row.className }, { key: 'type', header: 'Tipo', render: (row) => row.enrollmentType === 'REENROLLMENT' ? 'Rematrícula' : row.enrollmentType === 'CLASS_CHANGE' ? 'Troca de turma' : 'Matrícula' }, { key: 'date', header: 'Data', render: (row) => new Date(`${row.enrollmentDate}T00:00:00`).toLocaleDateString('pt-BR') }, { key: 'status', header: 'Situação', render: (row) => row.status }, { key: 'actions', header: 'Ações', render: (row) => canWrite && row.status === 'ACTIVE' ? <Button type="button" variant="ghost" onClick={() => setMovementEnrollment(row)}>Movimentar</Button> : null },
  ];
  const calendarColumns: DataColumn<CalendarDay>[] = [
    { key: 'date', header: 'Data', render: (row) => new Date(`${row.academicDate}T00:00:00`).toLocaleDateString('pt-BR') }, { key: 'type', header: 'Tipo', render: (row) => row.schoolDay ? 'Dia letivo' : 'Não letivo' }, { key: 'description', header: 'Descrição', render: (row) => row.description || '—' }, { key: 'actions', header: 'Ações', render: (row) => canWrite ? <Button type="button" variant="ghost" onClick={() => setCalendarDialog({ open: true, value: row })}><Pencil aria-hidden="true" size={16} />Editar</Button> : null },
  ];
  const scheduleColumns: DataColumn<Schedule>[] = [
    { key: 'day', header: 'Dia', render: (row) => ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'][row.dayOfWeek - 1] }, { key: 'component', header: 'Componente', render: (row) => row.componentName }, { key: 'teacher', header: 'Professor', render: (row) => row.professionalName || 'Não definido' }, { key: 'time', header: 'Horário', render: (row) => `${row.startTime.slice(0, 5)}–${row.endTime.slice(0, 5)}` }, { key: 'period', header: 'Vigência', render: (row) => `${row.validFrom} até ${row.validUntil || 'sem data final'}` }, { key: 'actions', header: 'Ações', render: (row) => canWrite ? <Button type="button" variant="danger" onClick={async () => { try { await apiRequest(`/secretaria/schedules/${row.id}`, { method: 'DELETE' }); await loadSchedules(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível remover o horário.'); } }}>Remover</Button> : null },
  ];

  const newAction = canWrite && schoolId ? <Button type="button" variant="primary" onClick={() => { if (tab === 'students') setStudentDialog({ open: true }); if (tab === 'professionals') setProfessionalDialog({ open: true }); if (tab === 'classes') setClassDialog({ open: true }); if (tab === 'enrollments') setEnrollmentDialog({ open: true }); if (tab === 'calendar') setCalendarDialog({ open: true }); if (tab === 'schedules') setScheduleDialog(true); }}><Plus aria-hidden="true" size={18} />{tab === 'students' ? 'Novo estudante' : tab === 'professionals' ? 'Novo profissional' : tab === 'classes' ? 'Nova turma' : tab === 'enrollments' ? 'Nova matrícula' : tab === 'calendar' ? 'Adicionar data' : 'Novo horário'}</Button> : null;

  return <main className="app-page"><PageHeader eyebrow="Secretaria Escolar" title="Gestão acadêmica e administrativa" description="Cadastros, matrículas, calendário, horários e documentos escolares em uma única fonte de dados." manualSections={manualSections} actions={<Button type="button" variant="ghost" onClick={() => void reloadAll()}><RefreshCw aria-hidden="true" size={17} />Atualizar</Button>} /><SegmentedTabs label="Seções da Secretaria Escolar" tabs={tabs} value={tab} onChange={setTab} /><FilterBar actions={tab === 'schools' && canCreateSchool ? <Button type="button" variant="primary" onClick={() => setSchoolDialog({ open: true })}><Plus aria-hidden="true" size={18} />Nova unidade</Button> : newAction}><SelectField name="schoolFilter" label="Unidade escolar" value={schoolId} onChange={(event) => setSchoolId(event.target.value)} options={schools.length ? schools.map((school) => ({ value: school.id.toString(), label: school.name })) : [{ value: '', label: 'Nenhuma unidade disponível' }]} />{tab !== 'schools' ? <SelectField name="yearFilter" label="Ano letivo" value={year} onChange={(event) => setYear(event.target.value)} options={[currentYear - 1, currentYear, currentYear + 1].map((value) => ({ value: value.toString(), label: value.toString() }))} /> : null}{tab === 'students' || tab === 'professionals' ? <TextField name="secretariaSearch" label="Buscar" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Nome ou matrícula" /> : null}{tab === 'schedules' ? <SelectField name="scheduleClass" label="Turma" value={scheduleClassId} onChange={(event) => setScheduleClassId(event.target.value)} options={classes.length ? classes.map((item) => ({ value: item.id.toString(), label: item.name })) : [{ value: '', label: 'Nenhuma turma' }]} /> : null}</FilterBar>{denied ? <StateMessage title="Acesso não permitido" message="Sua conta não possui acesso ao contexto escolar selecionado." /> : error ? <StateMessage kind="error" title="Não foi possível carregar a Secretaria Escolar" message={error} /> : loading ? <StateMessage title="Carregando Secretaria Escolar" message="Consultando os dados da unidade e do ano letivo selecionados." /> : !schools.length ? <StateMessage title="Nenhuma unidade escolar cadastrada" message={canCreateSchool ? 'Cadastre a primeira unidade escolar para iniciar os demais fluxos.' : 'Solicite ao administrador acesso a uma unidade escolar cadastrada.'} /> : tab === 'documents' && selectedSchool ? <DocumentsPanel schoolId={selectedSchool.id} year={Number(year)} classes={classes} students={students} allowed={canDocuments} /> : <section className="content-panel">{tab === 'students' ? (students.length ? <DataTable rows={students} columns={studentColumns} rowKey={(row) => row.id} /> : <StateMessage title="Nenhum estudante encontrado" />) : tab === 'professionals' ? (professionals.length ? <DataTable rows={professionals} columns={professionalColumns} rowKey={(row) => row.id} /> : <StateMessage title="Nenhum profissional encontrado" />) : tab === 'schools' ? <DataTable rows={schools} columns={schoolColumns} rowKey={(row) => row.id} /> : tab === 'classes' ? (classes.length ? <DataTable rows={classes} columns={classColumns} rowKey={(row) => row.id} /> : <StateMessage title="Nenhuma turma cadastrada para o ano letivo" />) : tab === 'enrollments' ? (enrollments.length ? <DataTable rows={enrollments} columns={enrollmentColumns} rowKey={(row) => row.id} /> : <StateMessage title="Nenhuma matrícula encontrada" />) : tab === 'calendar' ? (calendar.length ? <DataTable rows={calendar} columns={calendarColumns} rowKey={(row) => row.id} /> : <StateMessage title="Calendário escolar vazio" message="Adicione dias letivos e não letivos para o ano selecionado." />) : tab === 'schedules' ? (schedules.length ? <DataTable rows={schedules} columns={scheduleColumns} rowKey={(row) => row.id} /> : <StateMessage title="Nenhum horário vigente" message="Cadastre os horários semanais da turma para o período letivo." />) : null}</section>}<SchoolDialog open={schoolDialog.open} school={schoolDialog.value} onClose={() => setSchoolDialog({ open: false })} onSaved={() => void reloadAll()} />{schoolId ? <><StudentDialog open={studentDialog.open} schoolId={Number(schoolId)} student={studentDialog.value} onClose={() => setStudentDialog({ open: false })} onSaved={() => void loadSchoolData()} /><ProfessionalDialog open={professionalDialog.open} schoolId={Number(schoolId)} professional={professionalDialog.value} onClose={() => setProfessionalDialog({ open: false })} onSaved={() => void loadSchoolData()} /><ClassDialog open={classDialog.open} schoolId={Number(schoolId)} academicYear={Number(year)} schoolClass={classDialog.value} onClose={() => setClassDialog({ open: false })} onSaved={() => void loadSchoolData()} /><EnrollmentDialog open={enrollmentDialog.open} students={students} classes={classes} initialStudentId={enrollmentDialog.studentId} onClose={() => setEnrollmentDialog({ open: false })} onSaved={() => void loadSchoolData()} /><MovementDialog open={Boolean(movementEnrollment)} enrollment={movementEnrollment} classes={classes} onClose={() => setMovementEnrollment(undefined)} onSaved={() => void loadSchoolData()} /><CalendarDayDialog open={calendarDialog.open} schoolId={Number(schoolId)} day={calendarDialog.value} onClose={() => setCalendarDialog({ open: false })} onSaved={() => void loadSchoolData()} /><ScheduleDialog open={scheduleDialog} schoolClass={activeScheduleClass} professionals={professionals} components={components} onClose={() => setScheduleDialog(false)} onSaved={() => void loadSchedules()} /><ClassAssignmentsDialog schoolClass={assignmentClass} professionals={professionals} components={components} canWrite={canWrite} onClose={() => setAssignmentClass(undefined)} /><MovementHistoryDialog student={historyStudent} onClose={() => setHistoryStudent(undefined)} /></> : null}</main>;
}
