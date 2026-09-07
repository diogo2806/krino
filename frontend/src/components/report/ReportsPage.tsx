import { Download, Settings2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiBlob, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { MetricCard } from '../chart/MetricCard';
import { ProgressBarChart } from '../chart/ProgressBarChart';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { PageHeader } from '../layout/PageHeader';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { AccessContext } from '../workspace/types';
import { PerformanceLevelDialog } from './PerformanceLevelDialog';
import type { AlternativeRow, BreakdownRow, ComponentRow, DashboardView, InterventionProfile, PerformanceLevel, QuestionRow, ReportContext, ReportFilters, SchoolSkillRow, SkillRow, StudentAnswerRow } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type ReportTab = 'school-skills' | 'alternatives' | 'questions' | 'skills' | 'components' | 'participation' | 'student-answers' | 'intervention';

type ReportTabConfig = { value: ReportTab; label: string; exportCode: string; requiresStudent?: boolean; };

const reportTabs: ReportTabConfig[] = [
  { value: 'school-skills', label: 'Habilidades por escola', exportCode: 'SCHOOL_SKILLS' },
  { value: 'alternatives', label: 'Respostas por alternativa', exportCode: 'ALTERNATIVES' },
  { value: 'questions', label: 'Acerto por questão', exportCode: 'QUESTIONS' },
  { value: 'skills', label: 'Acerto por habilidade', exportCode: 'SKILLS' },
  { value: 'components', label: 'Análise por componente', exportCode: 'COMPONENTS' },
  { value: 'participation', label: 'Participação', exportCode: 'PARTICIPATION' },
  { value: 'student-answers', label: 'Respostas do estudante', exportCode: 'STUDENT_ANSWERS', requiresStudent: true },
  { value: 'intervention', label: 'Intervenção pedagógica', exportCode: 'INTERVENTION', requiresStudent: true },
];

const manualSections = [
  { title: 'Finalidade', content: 'Consultar dashboards gerenciais e relatórios detalhados da Avaliação em Rede com dados persistidos do último processamento concluído, respeitando o escopo autorizado da conta.' },
  { title: 'Filtros', content: 'Ano letivo e Avaliação em Rede definem a base principal. Unidade escolar e turma alteram o nível do dashboard. Estudante refina os relatórios individuais e os relatórios detalhados que aceitam segmentação individual.' },
  { title: 'Indicadores e fórmulas', content: 'Percentuais simples usam quantidade do evento dividida pela quantidade da base, multiplicada por 100 e arredondada para duas casas decimais. Participação usa participantes sobre estudantes esperados. Acerto usa respostas corretas sobre a base de questões ou respostas do relatório.' },
  { title: 'Dashboard', content: 'Os cards mostram estudantes esperados, participantes, percentual de participação, percentual geral de acertos e quantidade de habilidades avaliadas. Os gráficos apresentam participação por escola e destaques de acerto por habilidade. Tabelas ficam somente nos relatórios detalhados.' },
  { title: 'Relatórios detalhados', content: 'Estão disponíveis habilidades por escola, respostas por alternativa, acerto por questão, acerto por habilidade/descritor, análise por componente curricular, participação, respostas individuais e perfil de intervenção pedagógica. A complexidade das questões é relativa ao conjunto filtrado.' },
  { title: 'Botões e ações', content: 'Exportar CSV baixa o relatório detalhado selecionado em formato aberto. Configurar níveis permite ao perfil autorizado definir as faixas usadas na classificação de desempenho da avaliação.' },
  { title: 'Regras de uso', content: 'Base zero é apresentada como Sem base, nunca como percentual calculado. Relatórios individuais exigem estudante selecionado. As análises de Matemática e Língua Portuguesa usam o componente curricular da Avaliação em Rede selecionada. Reprocessamentos substituem a leitura gerencial pelo processamento concluído mais recente.' },
  { title: 'Permissões', content: 'REPORT_READ permite consulta no escopo municipal ou escolar atribuído. REPORT_EXPORT permite baixar CSV no mesmo escopo. ASSESSMENT_WRITE em escopo municipal permite configurar níveis de desempenho.' },
  { title: 'Fluxo principal', content: 'Selecione o ano e a avaliação, ajuste escola e turma para o nível desejado, analise o dashboard, escolha um relatório detalhado e exporte quando necessário. Para relatórios individuais, selecione também turma e estudante.' },
  { title: 'Mensagens e estados', content: 'A tela diferencia carregamento, ausência de avaliação, ausência de dados persistidos, falta de estudante para relatório individual, acesso não permitido e falha técnica.' },
];

function percent(value?: number | null) {
  return value == null ? 'Sem base' : `${value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

function queryScope(schoolId: string, classId: string, studentId?: string) {
  const params = new URLSearchParams();
  if (schoolId) params.set('schoolId', schoolId);
  if (classId) params.set('classId', classId);
  if (studentId) params.set('studentId', studentId);
  const value = params.toString();
  return value ? `?${value}` : '';
}

function reportEndpoint(tab: ReportTab, assessmentId: string, schoolId: string, classId: string, studentId: string) {
  const scope = queryScope(schoolId, classId, ['alternatives', 'questions', 'skills'].includes(tab) ? studentId : undefined);
  if (tab === 'school-skills') return `/reports/assessments/${assessmentId}/school-skills${queryScope(schoolId, classId)}`;
  if (tab === 'alternatives') return `/reports/assessments/${assessmentId}/alternatives${scope}`;
  if (tab === 'questions') return `/reports/assessments/${assessmentId}/questions${scope}`;
  if (tab === 'skills') return `/reports/assessments/${assessmentId}/skills${scope}`;
  if (tab === 'components') return `/reports/assessments/${assessmentId}/components${queryScope(schoolId, classId)}`;
  if (tab === 'participation') {
    const params = new URLSearchParams();
    params.set('level', classId ? 'CLASS' : schoolId ? 'CLASS' : 'SCHOOL');
    if (schoolId) params.set('schoolId', schoolId);
    if (classId) params.set('classId', classId);
    return `/reports/assessments/${assessmentId}/participation?${params.toString()}`;
  }
  if (tab === 'student-answers') return `/reports/assessments/${assessmentId}/students/${studentId}/answers${queryScope(schoolId, classId)}`;
  return `/reports/assessments/${assessmentId}/students/${studentId}/intervention${queryScope(schoolId, classId)}`;
}

export function ReportsPage({ context, onUnauthorized }: Props) {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear.toString());
  const [assessmentId, setAssessmentId] = useState('');
  const [schoolId, setSchoolId] = useState('');
  const [classId, setClassId] = useState('');
  const [studentId, setStudentId] = useState('');
  const [tab, setTab] = useState<ReportTab>('school-skills');
  const [reportContext, setReportContext] = useState<ReportContext>();
  const [filters, setFilters] = useState<ReportFilters>({ classes: [], students: [] });
  const [dashboard, setDashboard] = useState<DashboardView>();
  const [schoolSkills, setSchoolSkills] = useState<SchoolSkillRow[]>([]);
  const [alternatives, setAlternatives] = useState<AlternativeRow[]>([]);
  const [questions, setQuestions] = useState<QuestionRow[]>([]);
  const [skills, setSkills] = useState<SkillRow[]>([]);
  const [components, setComponents] = useState<ComponentRow[]>([]);
  const [participation, setParticipation] = useState<BreakdownRow[]>([]);
  const [studentAnswers, setStudentAnswers] = useState<StudentAnswerRow[]>([]);
  const [intervention, setIntervention] = useState<InterventionProfile>();
  const [performanceLevels, setPerformanceLevels] = useState<PerformanceLevel[]>([]);
  const [levelsOpen, setLevelsOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState('');

  const selectedAssessment = reportContext?.assessments.find((assessment) => assessment.id.toString() === assessmentId);
  const selectedSchool = reportContext?.schools.find((school) => school.id.toString() === schoolId);
  const selectedClass = filters.classes.find((item) => item.id.toString() === classId);
  const selectedStudent = filters.students.find((item) => item.id.toString() === studentId);
  const studentOptions = useMemo(() => classId ? filters.students.filter((item) => item.classId.toString() === classId) : [], [filters.students, classId]);
  const canExport = context.permissions.includes('REPORT_EXPORT');
  const canManageLevels = context.networkPermissions.includes('ASSESSMENT_WRITE');

  const handleFailure = useCallback((exception: unknown, fallback: string) => {
    if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
    if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; }
    setError(exception instanceof Error ? exception.message : fallback);
  }, [onUnauthorized]);

  const loadContext = useCallback(async () => {
    setLoading(true); setError(''); setDenied(false);
    try {
      const next = await apiRequest<ReportContext>(`/reports/context?year=${year}`);
      setReportContext(next);
      setAssessmentId((current) => current && next.assessments.some((item) => item.id.toString() === current) ? current : next.assessments[0]?.id.toString() ?? '');
      setSchoolId((current) => current && next.schools.some((item) => item.id.toString() === current) ? current : next.networkView ? '' : next.schools[0]?.id.toString() ?? '');
    } catch (exception) {
      handleFailure(exception, 'Não foi possível carregar o contexto dos relatórios.');
    } finally { setLoading(false); }
  }, [year, handleFailure]);

  const loadFilters = useCallback(async () => {
    if (!assessmentId) { setFilters({ classes: [], students: [] }); setClassId(''); setStudentId(''); return; }
    try {
      const next = await apiRequest<ReportFilters>(`/reports/assessments/${assessmentId}/filters${schoolId ? `?schoolId=${schoolId}` : ''}`);
      setFilters(next);
      setClassId((current) => current && next.classes.some((item) => item.id.toString() === current) ? current : '');
      setStudentId((current) => current && next.students.some((item) => item.id.toString() === current) ? current : '');
    } catch (exception) { handleFailure(exception, 'Não foi possível carregar turmas e estudantes para os filtros.'); }
  }, [assessmentId, schoolId, handleFailure]);

  const loadDashboard = useCallback(async () => {
    if (!assessmentId) { setDashboard(undefined); return; }
    setLoading(true); setError(''); setDenied(false);
    try {
      const next = await apiRequest<DashboardView>(`/reports/assessments/${assessmentId}/dashboard${queryScope(schoolId, classId)}`);
      setDashboard(next);
    } catch (exception) { handleFailure(exception, 'Não foi possível carregar o dashboard da avaliação.'); }
    finally { setLoading(false); }
  }, [assessmentId, schoolId, classId, handleFailure]);

  const loadLevels = useCallback(async () => {
    if (!assessmentId) { setPerformanceLevels([]); return; }
    try { setPerformanceLevels(await apiRequest<PerformanceLevel[]>(`/reports/assessments/${assessmentId}/performance-levels`)); }
    catch (exception) { handleFailure(exception, 'Não foi possível carregar os níveis de desempenho.'); }
  }, [assessmentId, handleFailure]);

  const loadDetail = useCallback(async () => {
    if (!assessmentId) return;
    const config = reportTabs.find((item) => item.value === tab)!;
    if (config.requiresStudent && !studentId) { setIntervention(undefined); setStudentAnswers([]); return; }
    setDetailLoading(true); setError('');
    try {
      const endpoint = reportEndpoint(tab, assessmentId, schoolId, classId, studentId);
      if (tab === 'school-skills') setSchoolSkills(await apiRequest<SchoolSkillRow[]>(endpoint));
      else if (tab === 'alternatives') setAlternatives(await apiRequest<AlternativeRow[]>(endpoint));
      else if (tab === 'questions') setQuestions(await apiRequest<QuestionRow[]>(endpoint));
      else if (tab === 'skills') setSkills(await apiRequest<SkillRow[]>(endpoint));
      else if (tab === 'components') setComponents(await apiRequest<ComponentRow[]>(endpoint));
      else if (tab === 'participation') setParticipation(await apiRequest<BreakdownRow[]>(endpoint));
      else if (tab === 'student-answers') setStudentAnswers(await apiRequest<StudentAnswerRow[]>(endpoint));
      else setIntervention(await apiRequest<InterventionProfile>(endpoint));
    } catch (exception) { handleFailure(exception, 'Não foi possível carregar o relatório detalhado.'); }
    finally { setDetailLoading(false); }
  }, [assessmentId, schoolId, classId, studentId, tab, handleFailure]);

  useEffect(() => { void loadContext(); }, [loadContext]);
  useEffect(() => { void loadFilters(); }, [loadFilters]);
  useEffect(() => { void loadDashboard(); }, [loadDashboard]);
  useEffect(() => { void loadLevels(); }, [loadLevels]);
  useEffect(() => { void loadDetail(); }, [loadDetail]);

  const analysisLevel = classId ? selectedClass?.name ?? 'Turma' : schoolId ? selectedSchool?.name ?? 'Unidade escolar' : 'Rede municipal';
  const dashboardParticipation = useMemo(() => (dashboard?.participationBySchool ?? []).map((item) => ({ label: item.label, value: item.percentage, detail: `${item.participants} de ${item.expectedStudents} estudante(s)` })), [dashboard]);
  const dashboardSkills = useMemo(() => (dashboard?.skillHighlights ?? []).map((item) => ({ label: item.descriptor ? `${item.descriptor} · ${item.skill}` : item.skill, value: item.correctPercent, detail: `${item.correctAnswers} acerto(s) em ${item.totalQuestions} questão(ões) · ${item.performanceLevel}` })), [dashboard]);

  const exportSelected = async () => {
    if (!assessmentId) return;
    const config = reportTabs.find((item) => item.value === tab)!;
    if (config.requiresStudent && !studentId) { setError('Selecione um estudante antes de exportar este relatório.'); return; }
    const params = new URLSearchParams({ report: config.exportCode });
    if (schoolId) params.set('schoolId', schoolId);
    if (classId) params.set('classId', classId);
    if (studentId) params.set('studentId', studentId);
    setExporting(true); setError('');
    try {
      const blob = await apiBlob(`/reports/assessments/${assessmentId}/export?${params.toString()}`);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `avaliacao-${assessmentId}-${config.exportCode.toLowerCase().replaceAll('_', '-')}.csv`;
      document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url);
    } catch (exception) { handleFailure(exception, 'Não foi possível exportar o relatório em CSV.'); }
    finally { setExporting(false); }
  };

  if (denied) return <main className="app-page"><PageHeader eyebrow="Gestão pedagógica" title="Relatórios e Indicadores" description="Dashboards e relatórios da Avaliação em Rede." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para consultar relatórios neste escopo." /></main>;

  return <main className="app-page">
    <PageHeader eyebrow="Gestão pedagógica" title="Relatórios e Indicadores" description={`Nível de análise: ${analysisLevel}. Resultados da Avaliação em Rede com base de cálculo explícita.`} manualSections={manualSections} actions={canManageLevels && assessmentId ? <Button type="button" variant="secondary" onClick={() => setLevelsOpen(true)}><Settings2 aria-hidden="true" size={18} />Configurar níveis</Button> : undefined} />
    {error ? <StateMessage kind="error" title="Não foi possível concluir a consulta" message={error} /> : null}
    <FilterBar>
      <SelectField name="reportYear" label="Ano letivo" value={year} onChange={(event) => { setYear(event.target.value); setAssessmentId(''); setClassId(''); setStudentId(''); }} options={[currentYear - 2, currentYear - 1, currentYear, currentYear + 1].map((value) => ({ value: value.toString(), label: value.toString() }))} />
      <SelectField name="reportAssessment" label="Avaliação em Rede" value={assessmentId} onChange={(event) => { setAssessmentId(event.target.value); setClassId(''); setStudentId(''); }} options={reportContext?.assessments.length ? reportContext.assessments.map((item) => ({ value: item.id.toString(), label: `${item.name} · ${item.gradeStage}${item.componentName ? ` · ${item.componentName}` : ''}` })) : [{ value: '', label: 'Nenhuma avaliação disponível' }]} />
      <SelectField name="reportSchool" label="Unidade escolar" value={schoolId} onChange={(event) => { setSchoolId(event.target.value); setClassId(''); setStudentId(''); }} options={[...(reportContext?.networkView ? [{ value: '', label: 'Rede municipal' }] : []), ...(reportContext?.schools ?? []).map((item) => ({ value: item.id.toString(), label: item.name }))]} />
      <SelectField name="reportClass" label="Turma" value={classId} onChange={(event) => { setClassId(event.target.value); setStudentId(''); }} options={[{ value: '', label: 'Todas as turmas' }, ...filters.classes.map((item) => ({ value: item.id.toString(), label: item.name }))]} />
      <SelectField name="reportStudent" label="Estudante" disabled={!classId} value={studentId} onChange={(event) => setStudentId(event.target.value)} options={[{ value: '', label: classId ? 'Todos os estudantes' : 'Selecione uma turma' }, ...studentOptions.map((item) => ({ value: item.id.toString(), label: `${item.name} · ${item.registration}` }))]} />
    </FilterBar>

    {!assessmentId ? <StateMessage title="Nenhuma Avaliação em Rede disponível" message="Não existem avaliações acessíveis para o ano selecionado." /> : loading ? <StateMessage title="Atualizando dashboard" message="Aguarde enquanto os resultados do processamento concluído mais recente são consolidados." /> : dashboard ? <>
      <section className="metric-grid" aria-label="Indicadores do dashboard">
        <MetricCard label="Estudantes esperados" value={dashboard.expectedStudents.toLocaleString('pt-BR')} detail="Base de estudantes atribuídos à avaliação" />
        <MetricCard label="Participantes" value={dashboard.participants.toLocaleString('pt-BR')} detail="Estudantes com resultado no processamento concluído" />
        <MetricCard label="Percentual de participação" value={percent(dashboard.participationPercent)} detail="Participantes ÷ estudantes esperados × 100" />
        <MetricCard label="Percentual geral de acertos" value={percent(dashboard.achievementPercent)} detail="Acertos ÷ base de questões processadas × 100" />
        <MetricCard label="Habilidades avaliadas" value={dashboard.skills.toLocaleString('pt-BR')} detail="Habilidades/descritores com resultado" />
      </section>
      <section className="report-dashboard-charts">
        <ProgressBarChart title="Percentual de participação por unidade escolar" items={dashboardParticipation} emptyMessage="Não há base de participação disponível neste escopo." />
        <ProgressBarChart title="Percentual de acerto por habilidade/descritor" items={dashboardSkills} emptyMessage="Não há habilidades processadas para exibir." />
      </section>
    </> : <StateMessage title="Sem dados processados" message="A avaliação selecionada ainda não possui resultados consolidados para este escopo." />}

    {assessmentId ? <section className="report-detail-section">
      <div className="report-section-heading"><div><h2>Relatórios detalhados</h2><p className="muted">As tabelas abaixo respeitam a avaliação e os filtros selecionados. Cada percentual apresenta sua base correspondente.</p></div>{canExport ? <Button type="button" variant="secondary" disabled={exporting || (reportTabs.find((item) => item.value === tab)?.requiresStudent && !studentId)} onClick={() => void exportSelected()}><Download aria-hidden="true" size={18} />{exporting ? 'Exportando...' : 'Exportar CSV'}</Button> : null}</div>
      <SegmentedTabs label="Tipo de relatório detalhado" tabs={reportTabs.map((item) => ({ value: item.value, label: item.label }))} value={tab} onChange={setTab} />
      {reportTabs.find((item) => item.value === tab)?.requiresStudent && !studentId ? <StateMessage title="Selecione um estudante" message="Escolha uma turma e um estudante nos filtros para consultar este relatório individual." /> : detailLoading ? <StateMessage title="Carregando relatório" message="Aguarde enquanto os dados detalhados são consolidados." /> : <ReportDetail tab={tab} schoolSkills={schoolSkills} alternatives={alternatives} questions={questions} skills={skills} components={components} participation={participation} studentAnswers={studentAnswers} intervention={intervention} />}
    </section> : null}

    {assessmentId ? <section className="report-performance-levels"><div className="report-section-heading"><div><h2>Níveis de desempenho</h2><p className="muted">Faixas usadas para classificar os percentuais de acerto da avaliação selecionada.</p></div></div>{performanceLevels.length === 0 ? <StateMessage title="Níveis ainda não configurados" message="Os relatórios exibirão Não parametrizada até que as faixas sejam definidas por um perfil autorizado." /> : <div className="report-level-summary">{performanceLevels.map((level) => <span className="status-badge status-badge--active" key={level.id ?? `${level.label}-${level.minimumPercent}`}>{level.label}: {percent(level.minimumPercent)} a {percent(level.maximumPercent)}</span>)}</div>}</section> : null}

    {assessmentId ? <PerformanceLevelDialog open={levelsOpen} assessmentId={Number(assessmentId)} levels={performanceLevels} onClose={() => setLevelsOpen(false)} onSaved={setPerformanceLevels} /> : null}
  </main>;
}

type DetailProps = {
  tab: ReportTab;
  schoolSkills: SchoolSkillRow[];
  alternatives: AlternativeRow[];
  questions: QuestionRow[];
  skills: SkillRow[];
  components: ComponentRow[];
  participation: BreakdownRow[];
  studentAnswers: StudentAnswerRow[];
  intervention?: InterventionProfile;
};

function empty(rows: unknown[], label: string) {
  return rows.length === 0 ? <StateMessage title="Nenhum dado para este relatório" message={`Não existem ${label} no escopo e filtros selecionados.`} /> : null;
}

function skillColumns(): DataColumn<SkillRow>[] {
  return [
    { key: 'descriptor', header: 'Descritor', render: (row) => row.descriptor || '—' },
    { key: 'skill', header: 'Habilidade', render: (row) => row.skill },
    { key: 'correct', header: 'Acertos', render: (row) => row.correctAnswers.toLocaleString('pt-BR') },
    { key: 'base', header: 'Base de questões', render: (row) => row.totalQuestions.toLocaleString('pt-BR') },
    { key: 'percent', header: 'Percentual de acerto', render: (row) => percent(row.correctPercent) },
    { key: 'level', header: 'Nível de desempenho', render: (row) => row.performanceLevel },
  ];
}

function ReportDetail({ tab, schoolSkills, alternatives, questions, skills, components, participation, studentAnswers, intervention }: DetailProps) {
  if (tab === 'school-skills') {
    if (schoolSkills.length === 0) return empty(schoolSkills, 'resultados de habilidades por escola');
    const columns: DataColumn<SchoolSkillRow>[] = [
      { key: 'school', header: 'Unidade escolar', render: (row) => row.schoolName },
      { key: 'descriptor', header: 'Descritor', render: (row) => row.descriptor || '—' },
      { key: 'skill', header: 'Habilidade', render: (row) => row.skill },
      { key: 'correct', header: 'Acertos', render: (row) => row.correctAnswers.toLocaleString('pt-BR') },
      { key: 'base', header: 'Base de questões', render: (row) => row.totalQuestions.toLocaleString('pt-BR') },
      { key: 'percent', header: 'Percentual de acerto', render: (row) => percent(row.correctPercent) },
      { key: 'level', header: 'Nível de desempenho', render: (row) => row.performanceLevel },
    ];
    return <DataTable rows={schoolSkills} columns={columns} rowKey={(row) => `${row.schoolId}-${row.descriptor}-${row.skill}`} />;
  }
  if (tab === 'alternatives') {
    if (alternatives.length === 0) return empty(alternatives, 'respostas por alternativa');
    const columns: DataColumn<AlternativeRow>[] = [
      { key: 'question', header: 'Questão', render: (row) => row.sequenceNumber },
      { key: 'descriptor', header: 'Descritor', render: (row) => row.descriptor || '—' },
      { key: 'skill', header: 'Habilidade', render: (row) => row.skill },
      { key: 'correctOption', header: 'Alternativa correta', render: (row) => row.correctOption },
      { key: 'selected', header: 'Alternativa marcada', render: (row) => row.selectedOption === 'SEM_RESPOSTA' ? 'Sem resposta' : row.selectedOption },
      { key: 'responses', header: 'Respostas', render: (row) => row.responses.toLocaleString('pt-BR') },
      { key: 'base', header: 'Base de respostas', render: (row) => row.baseResponses.toLocaleString('pt-BR') },
      { key: 'percent', header: 'Percentual de respostas', render: (row) => percent(row.responsePercent) },
    ];
    return <DataTable rows={alternatives} columns={columns} rowKey={(row) => `${row.sequenceNumber}-${row.selectedOption}`} />;
  }
  if (tab === 'questions') {
    if (questions.length === 0) return empty(questions, 'resultados por questão');
    const columns: DataColumn<QuestionRow>[] = [
      { key: 'question', header: 'Questão', render: (row) => row.sequenceNumber },
      { key: 'descriptor', header: 'Descritor', render: (row) => row.descriptor || '—' },
      { key: 'skill', header: 'Habilidade', render: (row) => row.skill },
      { key: 'correct', header: 'Acertos', render: (row) => row.correctAnswers.toLocaleString('pt-BR') },
      { key: 'base', header: 'Base de respostas', render: (row) => row.responses.toLocaleString('pt-BR') },
      { key: 'percent', header: 'Percentual de acerto', render: (row) => percent(row.correctPercent) },
      { key: 'complexity', header: 'Complexidade relativa', render: (row) => row.relativeComplexity },
    ];
    return <DataTable rows={questions} columns={columns} rowKey={(row) => row.sequenceNumber} />;
  }
  if (tab === 'skills') {
    if (skills.length === 0) return empty(skills, 'resultados por habilidade/descritor');
    return <DataTable rows={skills} columns={skillColumns()} rowKey={(row) => `${row.descriptor}-${row.skill}`} />;
  }
  if (tab === 'components') {
    if (components.length === 0) return empty(components, 'resultados por componente curricular');
    const columns: DataColumn<ComponentRow>[] = [
      { key: 'component', header: 'Componente curricular', render: (row) => row.componentName },
      { key: 'students', header: 'Estudantes', render: (row) => row.students.toLocaleString('pt-BR') },
      { key: 'correct', header: 'Acertos', render: (row) => row.correctAnswers.toLocaleString('pt-BR') },
      { key: 'base', header: 'Base de questões', render: (row) => row.totalQuestions.toLocaleString('pt-BR') },
      { key: 'percent', header: 'Percentual de acerto', render: (row) => percent(row.correctPercent) },
    ];
    return <DataTable rows={components} columns={columns} rowKey={(row) => row.componentName} />;
  }
  if (tab === 'participation') {
    if (participation.length === 0) return empty(participation, 'dados de participação');
    const columns: DataColumn<BreakdownRow>[] = [
      { key: 'level', header: 'Nível', render: (row) => row.label },
      { key: 'participants', header: 'Participantes', render: (row) => row.participants.toLocaleString('pt-BR') },
      { key: 'base', header: 'Base de estudantes', render: (row) => row.expectedStudents.toLocaleString('pt-BR') },
      { key: 'percent', header: 'Percentual de participação', render: (row) => percent(row.percentage) },
    ];
    return <DataTable rows={participation} columns={columns} rowKey={(row) => `${row.keyId}-${row.label}`} />;
  }
  if (tab === 'student-answers') {
    if (studentAnswers.length === 0) return empty(studentAnswers, 'respostas individuais processadas');
    const columns: DataColumn<StudentAnswerRow>[] = [
      { key: 'question', header: 'Questão', render: (row) => row.sequenceNumber },
      { key: 'descriptor', header: 'Descritor', render: (row) => row.descriptor || '—' },
      { key: 'skill', header: 'Habilidade', render: (row) => row.skill },
      { key: 'answer', header: 'Resposta do estudante', render: (row) => row.selectedOption || 'Sem resposta' },
      { key: 'correctOption', header: 'Resposta correta', render: (row) => row.correctOption },
      { key: 'result', header: 'Resultado', render: (row) => <span className={row.correct ? 'status-badge status-badge--active' : 'status-badge report-answer--incorrect'}>{row.correct ? 'Acerto' : 'Erro'}</span> },
    ];
    return <DataTable rows={studentAnswers} columns={columns} rowKey={(row) => row.sequenceNumber} />;
  }
  if (!intervention) return <StateMessage title="Perfil indisponível" message="Não há resultado consolidado suficiente para montar o perfil de intervenção do estudante selecionado." />;
  return <div className="report-intervention">
    <section className="metric-grid">
      <MetricCard label="Estudante" value={intervention.studentName} detail={`Matrícula ${intervention.registration}`} />
      <MetricCard label="Percentual geral de acertos" value={percent(intervention.correctPercent)} detail="Acertos ÷ base de questões processadas × 100" />
      <MetricCard label="Nível de desempenho" value={intervention.performanceLevel} detail="Classificação conforme faixas da avaliação" />
    </section>
    <div className="report-intervention-grid"><section><h3>Pontos fortes relativos</h3>{intervention.strengths.length ? <DataTable rows={intervention.strengths} columns={skillColumns()} rowKey={(row) => `${row.descriptor}-${row.skill}`} /> : <StateMessage title="Sem pontos fortes calculáveis" message="Não há habilidades suficientes para esta classificação relativa." />}</section><section><h3>Prioridades relativas de atenção</h3>{intervention.attentionPriorities.length ? <DataTable rows={intervention.attentionPriorities} columns={skillColumns()} rowKey={(row) => `${row.descriptor}-${row.skill}`} /> : <StateMessage title="Sem prioridades calculáveis" message="Não há habilidades suficientes para esta classificação relativa." />}</section></div>
  </div>;
}
