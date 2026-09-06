import { BookOpen, Plus } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { PageHeader } from '../layout/PageHeader';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import type { Component, Professional, School, SchoolClass } from '../secretaria/types';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { AccessContext } from '../workspace/types';
import { AssessmentPanel } from './AssessmentPanel';
import { CurriculumPanel } from './CurriculumPanel';
import { DiaryCreateDialog } from './DiaryCreateDialog';
import { LessonEditor } from './LessonEditor';
import { PlanningPanel } from './PlanningPanel';
import type { CurriculumItem, Diary, RosterStudent } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type DiaryTab = 'lesson' | 'assessment' | 'planning' | 'curriculum';

const diaryTabs: { value: DiaryTab; label: string }[] = [
  { value: 'lesson', label: 'Frequência e conteúdo' },
  { value: 'assessment', label: 'Notas e rendimento' },
  { value: 'planning', label: 'Planejamento' },
  { value: 'curriculum', label: 'Currículo' },
];
const modeLabels: Record<string, string> = { EARLY_CHILDHOOD: 'Educação Infantil', LITERACY: 'Criança Alfabetizada', EARLY_YEARS: 'Anos Iniciais', FINAL_YEARS: 'Anos Finais', EJA: 'EJA' };
const manualSections = [
  { title: 'Finalidade', content: 'Registrar e consultar o Diário de Classe Eletrônico, incluindo frequência, conteúdo ministrado, avaliações, notas, planejamento pedagógico e currículo aplicável.' },
  { title: 'Campos e filtros', content: 'Unidade escolar, ano letivo e turma definem o contexto. Cada diário informa modalidade, componente curricular quando aplicável, professor responsável e vigência. Na aula, informe data e número da aula.' },
  { title: 'Botões e ações', content: 'Novo diário cria o diário da turma. Abrir diário seleciona um registro. Salvar diário grava conteúdo e frequência. Nova avaliação cadastra avaliação; Salvar notas registra resultados. Salvar planejamento relaciona o planejamento às referências curriculares.' },
  { title: 'Regras', content: 'Aula e frequência só podem ser lançadas em dia letivo. Em Anos Finais e EJA, o componente deve possuir aula no horário semanal vigente. O professor precisa ter atribuição vigente e somente o professor responsável pode editar, salvo administração autorizada.' },
  { title: 'Permissões', content: 'Consulta exige permissão de Diário de Classe no escopo da unidade. Professores editam somente seus diários quando a conta de login está vinculada ao cadastro profissional. Perfis administrativos autorizados podem criar e administrar diários.' },
  { title: 'Fluxos', content: 'Selecione escola e turma, abra ou crie o diário, registre aula/frequência, cadastre avaliações e notas, mantenha o planejamento e associe referências curriculares validadas.' },
  { title: 'Mensagens e estados', content: 'A interface diferencia carregamento, ausência de diários, acesso somente para consulta, erro técnico e bloqueios de calendário, horário, atribuição ou responsabilidade docente.' },
];

export function DiaryPage({ context, onUnauthorized }: Props) {
  const currentYear = new Date().getFullYear();
  const [schools, setSchools] = useState<School[]>([]); const [classes, setClasses] = useState<SchoolClass[]>([]); const [components, setComponents] = useState<Component[]>([]); const [professionals, setProfessionals] = useState<Professional[]>([]);
  const [schoolId, setSchoolId] = useState(''); const [year, setYear] = useState(currentYear.toString()); const [classId, setClassId] = useState('');
  const [diaries, setDiaries] = useState<Diary[]>([]); const [selectedDiaryId, setSelectedDiaryId] = useState<number>(); const [roster, setRoster] = useState<RosterStudent[]>([]); const [curriculum, setCurriculum] = useState<CurriculumItem[]>([]);
  const [tab, setTab] = useState<DiaryTab>('lesson'); const [createOpen, setCreateOpen] = useState(false); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [denied, setDenied] = useState(false);

  const selectedSchool = schools.find((item) => item.id.toString() === schoolId); const selectedClass = classes.find((item) => item.id.toString() === classId); const selectedDiary = diaries.find((item) => item.id === selectedDiaryId);
  const schoolPermissions = selectedSchool ? context.schoolAccess.find((item) => item.schoolCode === selectedSchool.code)?.permissions ?? [] : [];
  const canAdmin = context.networkPermissions.includes('DIARY_ADMIN') || schoolPermissions.includes('DIARY_ADMIN');
  const canManageCurriculum = canAdmin || context.networkPermissions.includes('CURRICULUM_MANAGE') || schoolPermissions.includes('CURRICULUM_MANAGE');

  const loadBase = useCallback(async () => {
    setLoading(true); setError(''); setDenied(false);
    try {
      const [nextSchools, nextComponents] = await Promise.all([apiRequest<School[]>('/diaries/catalog/schools'), apiRequest<Component[]>('/diaries/catalog/components')]);
      setSchools(nextSchools); setComponents(nextComponents); setSchoolId((current) => current && nextSchools.some((item) => item.id.toString() === current) ? current : nextSchools[0]?.id.toString() ?? '');
    } catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar o Diário de Classe.'); }
    finally { setLoading(false); }
  }, [onUnauthorized]);

  const loadSchool = useCallback(async () => {
    if (!schoolId) { setClasses([]); setProfessionals([]); setClassId(''); return; }
    try {
      const classesPromise = apiRequest<SchoolClass[]>(`/diaries/catalog/classes?schoolId=${schoolId}&year=${year}`);
      const professionalsPromise = canAdmin ? apiRequest<Professional[]>(`/diaries/catalog/professionals?schoolId=${schoolId}`) : Promise.resolve<Professional[]>([]);
      const [nextClasses, nextProfessionals] = await Promise.all([classesPromise, professionalsPromise]);
      setClasses(nextClasses); setProfessionals(nextProfessionals); setClassId((current) => current && nextClasses.some((item) => item.id.toString() === current) ? current : nextClasses[0]?.id.toString() ?? '');
    } catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar o contexto do Diário de Classe.'); }
  }, [schoolId, year, canAdmin, onUnauthorized]);

  const loadDiaries = useCallback(async () => {
    if (!classId) { setDiaries([]); setSelectedDiaryId(undefined); return; }
    try { const next = await apiRequest<Diary[]>(`/diaries?classId=${classId}`); setDiaries(next); setSelectedDiaryId((current) => current && next.some((item) => item.id === current) ? current : next[0]?.id); }
    catch (exception) { if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os diários da turma.'); }
  }, [classId]);

  const loadDiaryData = useCallback(async () => {
    if (!selectedDiaryId) { setRoster([]); setCurriculum([]); return; }
    try { const [nextRoster, nextCurriculum] = await Promise.all([apiRequest<RosterStudent[]>(`/diaries/${selectedDiaryId}/roster`), apiRequest<CurriculumItem[]>(`/diaries/${selectedDiaryId}/curriculum`)]); setRoster(nextRoster); setCurriculum(nextCurriculum); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os dados do diário.'); }
  }, [selectedDiaryId]);

  useEffect(() => { void loadBase(); }, [loadBase]); useEffect(() => { void loadSchool(); }, [loadSchool]); useEffect(() => { void loadDiaries(); }, [loadDiaries]); useEffect(() => { void loadDiaryData(); }, [loadDiaryData]);

  const columns: DataColumn<Diary>[] = useMemo(() => [
    { key: 'diary', header: 'Diário', render: (row) => <><strong>{row.componentName ?? 'Diário integrado'}</strong><small>{modeLabels[row.mode] ?? row.mode}</small></> },
    { key: 'teacher', header: 'Professor responsável', render: (row) => row.responsibleProfessionalName },
    { key: 'validity', header: 'Vigência', render: (row) => `${new Date(`${row.validFrom}T00:00:00`).toLocaleDateString('pt-BR')} a ${row.validUntil ? new Date(`${row.validUntil}T00:00:00`).toLocaleDateString('pt-BR') : 'sem data final'}` },
    { key: 'access', header: 'Acesso', render: (row) => row.editable ? 'Edição' : 'Consulta' },
    { key: 'actions', header: 'Ações', render: (row) => <Button type="button" variant={row.id === selectedDiaryId ? 'primary' : 'ghost'} onClick={() => { setSelectedDiaryId(row.id); setTab('lesson'); }}>Abrir diário</Button> },
  ], [selectedDiaryId]);

  if (loading) return <main className="app-page"><StateMessage title="Carregando Diário de Classe" message="Aguarde enquanto seu contexto escolar é consultado." /></main>;
  if (denied) return <main className="app-page"><PageHeader eyebrow="Pedagógico" title="Diário de Classe" description="Frequência, conteúdo, notas e planejamento pedagógico." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para consultar o Diário de Classe neste escopo." /></main>;

  return <main className="app-page"><PageHeader eyebrow="Pedagógico" title="Diário de Classe" description="Frequência, conteúdo, notas, planejamento e currículo em uma única visão." manualSections={manualSections} actions={canAdmin && selectedClass ? <Button type="button" variant="primary" onClick={() => setCreateOpen(true)}><Plus aria-hidden="true" size={18} />Novo diário</Button> : undefined} />
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    <FilterBar><SelectField name="diarySchool" label="Unidade escolar" value={schoolId} onChange={(event) => setSchoolId(event.target.value)} options={[{ value: '', label: 'Selecione' }, ...schools.map((item) => ({ value: item.id.toString(), label: item.name }))]} /><SelectField name="diaryYear" label="Ano letivo" value={year} onChange={(event) => setYear(event.target.value)} options={[currentYear - 1, currentYear, currentYear + 1].map((item) => ({ value: item.toString(), label: item.toString() }))} /><SelectField name="diaryClass" label="Turma" value={classId} onChange={(event) => setClassId(event.target.value)} options={[{ value: '', label: 'Selecione' }, ...classes.map((item) => ({ value: item.id.toString(), label: `${item.name} · ${item.stage}` }))]} /></FilterBar>
    {!classId ? <StateMessage title="Selecione uma turma" message="Escolha unidade escolar, ano letivo e turma para consultar os diários." /> : diaries.length === 0 ? <StateMessage title="Nenhum diário cadastrado" message={canAdmin ? 'Crie o primeiro Diário de Classe para esta turma.' : 'Ainda não existe Diário de Classe disponível para esta turma.'} /> : <DataTable rows={diaries} columns={columns} rowKey={(row) => row.id} />}
    {selectedDiary ? <section className="diary-workspace"><div className="diary-context-card"><div><BookOpen aria-hidden="true" size={22} /><div><strong>{selectedDiary.className} · {selectedDiary.componentName ?? 'Diário integrado'}</strong><span>{modeLabels[selectedDiary.mode] ?? selectedDiary.mode} · Professor: {selectedDiary.responsibleProfessionalName}</span></div></div><span className={selectedDiary.editable ? 'status-badge status-badge--active' : 'status-badge'}>{selectedDiary.editable ? 'Pode editar' : 'Somente consulta'}</span></div><SegmentedTabs label="Seções do Diário de Classe" tabs={diaryTabs} value={tab} onChange={setTab} />{tab === 'lesson' ? <LessonEditor diary={selectedDiary} roster={roster} /> : tab === 'assessment' ? <AssessmentPanel diary={selectedDiary} roster={roster} /> : tab === 'planning' ? <PlanningPanel diary={selectedDiary} curriculum={curriculum} /> : <CurriculumPanel diary={selectedDiary} items={curriculum} canManage={canManageCurriculum} onReload={loadDiaryData} />}</section> : null}
    <DiaryCreateDialog open={createOpen} schoolClass={selectedClass} components={components} professionals={professionals} onClose={() => setCreateOpen(false)} onSaved={loadDiaries} />
  </main>;
}
