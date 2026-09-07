import { ClipboardCheck, FileText, Play, Plus, RefreshCw, Upload } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { PageHeader } from '../layout/PageHeader';
import { StateMessage } from '../state/StateMessage';
import type { AccessContext } from '../workspace/types';
import { AssessmentDialog } from './AssessmentDialog';
import type { ArtifactView, AssessmentCatalog, AssessmentTab, AssessmentView, AssignmentView, ImportSummary, ProcessingRunView, QuestionView, ResultSummaryRow, SkillSummaryRow, ValidationSummary } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };

const manualSections = [
  { title: 'Finalidade', content: 'Planejar, organizar, receber gabaritos, processar e consultar resultados das Avaliações Educacionais em Rede nas etapas Diagnóstica, Monitoramento e Final.' },
  { title: 'Campos e filtros', content: 'Ano letivo, etapa e unidade escolar filtram as avaliações. Cada avaliação define etapa/ano-série e, opcionalmente, componente curricular. Na organização, apenas turmas do mesmo ano letivo e etapa/ano-série podem ser vinculadas.' },
  { title: 'Abas', content: 'Avaliações apresenta o catálogo; Organização vincula turmas e estudantes e gera materiais; Gabaritos configura questões/habilidades e recebe respostas; Processamento valida e consolida; Resultados apresenta Rede, escola, turma, estudante e habilidades/descritores.' },
  { title: 'Botões e ações', content: 'Nova avaliação cria uma parametrização. Organizar estudantes cria a fotografia das matrículas ativas. Gerar lista, etiquetas, Manual do Aplicador, Ata de Ocorrências e acessos de segunda chamada produz os artefatos operacionais. Importar gabaritos preserva os dados de origem. Processar resultados cria uma nova execução auditável.' },
  { title: 'Regras de uso', content: 'Questões e organização não podem ser alteradas depois do recebimento de gabaritos. Um novo gabarito para o mesmo estudante substitui somente a submissão ativa, preservando o histórico. Reprocessamentos nunca apagam execuções anteriores. Gabaritos sem associação válida ficam registrados como rejeitados.' },
  { title: 'Permissões', content: 'ASSESSMENT_READ consulta avaliações no escopo autorizado. ASSESSMENT_WRITE cria, organiza, registra presença/ocorrências e recebe gabaritos com escopo municipal. ASSESSMENT_PROCESS valida e processa. ASSESSMENT_RESULT_READ consulta resultados respeitando Rede ou unidade escolar.' },
  { title: 'Fluxo principal', content: 'Crie a avaliação, configure questões e gabarito oficial, organize turmas, gere materiais, registre presença e ocorrências, importe respostas, confira válidos/inválidos, processe e consulte resultados e habilidades.' },
  { title: 'Mensagens e estados', content: 'Preparação indica configuração inicial; Pronta indica estudantes organizados; Aplicada indica recebimento de respostas; Processando indica consolidação; Processada indica resultados disponíveis. Erros de associação e respostas incompletas são apresentados antes do processamento.' },
];

const stageLabel: Record<string, string> = { DIAGNOSTIC: 'Diagnóstica', MONITORING: 'Monitoramento', FINAL: 'Final' };
const statusLabel: Record<string, string> = { PREPARATION: 'Preparação', READY: 'Pronta', APPLIED: 'Aplicada', PROCESSING: 'Processando', PROCESSED: 'Processada', CLOSED: 'Encerrada' };
const tabs: { id: AssessmentTab; label: string; }[] = [
  { id: 'assessments', label: 'Avaliações' }, { id: 'organization', label: 'Organização' }, { id: 'answer-sheets', label: 'Gabaritos' }, { id: 'processing', label: 'Processamento' }, { id: 'results', label: 'Resultados' },
];

function query(params: Record<string, string>) {
  const entries = Object.entries(params).filter(([, value]) => value);
  return entries.length ? `?${new URLSearchParams(entries).toString()}` : '';
}

function formatPercent(value?: number) {
  return value == null ? 'Sem base' : `${value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

export function NetworkAssessmentPage({ context, onUnauthorized }: Props) {
  const currentYear = new Date().getFullYear();
  const canWrite = context.networkPermissions.includes('ASSESSMENT_WRITE');
  const canProcess = context.networkPermissions.includes('ASSESSMENT_PROCESS');
  const canReadResults = context.permissions.includes('ASSESSMENT_RESULT_READ');
  const canNetworkResults = context.networkPermissions.includes('ASSESSMENT_RESULT_READ');

  const [catalog, setCatalog] = useState<AssessmentCatalog>();
  const [assessments, setAssessments] = useState<AssessmentView[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [selected, setSelected] = useState<AssessmentView>();
  const [tab, setTab] = useState<AssessmentTab>('assessments');
  const [year, setYear] = useState(currentYear.toString());
  const [stage, setStage] = useState('');
  const [schoolId, setSchoolId] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [questions, setQuestions] = useState<QuestionView[]>([]);
  const [questionText, setQuestionText] = useState('');
  const [classIds, setClassIds] = useState<number[]>([]);
  const [assignments, setAssignments] = useState<AssignmentView[]>([]);
  const [artifact, setArtifact] = useState<ArtifactView>();
  const [occurrenceText, setOccurrenceText] = useState('');
  const [sourceType, setSourceType] = useState('IMPORT');
  const [answerText, setAnswerText] = useState('');
  const [importSummary, setImportSummary] = useState<ImportSummary>();
  const [validation, setValidation] = useState<ValidationSummary>();
  const [runs, setRuns] = useState<ProcessingRunView[]>([]);
  const [resultLevel, setResultLevel] = useState(canNetworkResults ? 'NETWORK' : 'SCHOOL');
  const [results, setResults] = useState<ResultSummaryRow[]>([]);
  const [skills, setSkills] = useState<SkillSummaryRow[]>([]);

  const handleError = useCallback((exception: unknown, fallback: string) => {
    if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
    setError(exception instanceof Error ? exception.message : fallback);
  }, [onUnauthorized]);

  const loadCatalog = useCallback(async () => {
    try {
      const next = await apiRequest<AssessmentCatalog>('/assessments/catalog');
      setCatalog(next);
      if (!canNetworkResults) setSchoolId((current) => current || next.schools[0]?.id.toString() || '');
    } catch (exception) { handleError(exception, 'Não foi possível carregar escolas, turmas e componentes curriculares.'); }
  }, [canNetworkResults, handleError]);

  const loadAssessments = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const next = await apiRequest<AssessmentView[]>(`/assessments${query({ academicYear: year, stage, schoolId })}`);
      setAssessments(next);
      setSelectedId((current) => current && next.some((item) => item.id.toString() === current) ? current : next[0]?.id.toString() ?? '');
    } catch (exception) { handleError(exception, 'Não foi possível carregar as avaliações em rede.'); }
    finally { setLoading(false); }
  }, [year, stage, schoolId, handleError]);

  const loadSelected = useCallback(async () => {
    if (!selectedId) { setSelected(undefined); setQuestions([]); setAssignments([]); return; }
    try {
      const id = Number(selectedId);
      const next = await apiRequest<AssessmentView>(`/assessments/${id}`);
      setSelected(next);
      const [nextQuestions, nextAssignments, nextRuns] = await Promise.all([
        apiRequest<QuestionView[]>(`/assessments/${id}/questions`),
        apiRequest<AssignmentView[]>(`/assessments/${id}/assignments${query({ schoolId })}`),
        apiRequest<ProcessingRunView[]>(`/assessments/${id}/processing-runs`),
      ]);
      setQuestions(nextQuestions); setAssignments(nextAssignments); setRuns(nextRuns);
      setQuestionText(nextQuestions.map((item) => `${item.sequenceNumber}|${item.descriptor}|${item.skill}|${item.correctOption}`).join('\n'));
      if (canProcess) setValidation(await apiRequest<ValidationSummary>(`/assessments/${id}/validation`));
    } catch (exception) { handleError(exception, 'Não foi possível carregar os detalhes da avaliação.'); }
  }, [selectedId, schoolId, canProcess, handleError]);

  const loadResults = useCallback(async () => {
    if (!selectedId || !canReadResults) { setResults([]); setSkills([]); return; }
    if (!canNetworkResults && !schoolId) return;
    try {
      const scope = { level: resultLevel, schoolId };
      const [nextResults, nextSkills] = await Promise.all([
        apiRequest<ResultSummaryRow[]>(`/assessments/${selectedId}/results${query(scope)}`),
        apiRequest<SkillSummaryRow[]>(`/assessments/${selectedId}/results/skills${query({ schoolId })}`),
      ]);
      setResults(nextResults); setSkills(nextSkills);
    } catch (exception) { handleError(exception, 'Não foi possível carregar os resultados processados.'); }
  }, [selectedId, canReadResults, canNetworkResults, schoolId, resultLevel, handleError]);

  useEffect(() => { void loadCatalog(); }, [loadCatalog]);
  useEffect(() => { void loadAssessments(); }, [loadAssessments]);
  useEffect(() => { void loadSelected(); }, [loadSelected]);
  useEffect(() => { if (tab === 'results') void loadResults(); }, [tab, loadResults]);

  const eligibleClasses = useMemo(() => selected ? (catalog?.classes ?? []).filter((item) => item.academicYear === selected.academicYear && item.stage.trim().toLocaleLowerCase('pt-BR') === selected.gradeStage.trim().toLocaleLowerCase('pt-BR') && (!schoolId || item.schoolId.toString() === schoolId)) : [], [catalog, selected, schoolId]);

  const createAssessment = async (payload: Parameters<React.ComponentProps<typeof AssessmentDialog>['onSave']>[0]) => {
    try {
      const created = await apiRequest<AssessmentView>('/assessments', { method: 'POST', body: JSON.stringify(payload) });
      await loadAssessments(); setSelectedId(created.id.toString()); setTab('assessments');
    } catch (exception) { handleError(exception, 'Não foi possível criar a avaliação.'); throw exception; }
  };

  const saveQuestions = async () => {
    if (!selected) return;
    try {
      const parsed = questionText.split('\n').map((line) => line.trim()).filter(Boolean).map((line, index) => {
        const parts = line.split('|').map((part) => part.trim());
        if (parts.length !== 4 || !Number(parts[0])) throw new Error(`Revise a linha ${index + 1}. Use número|descritor|habilidade|alternativa correta.`);
        return { sequenceNumber: Number(parts[0]), descriptor: parts[1], skill: parts[2], correctOption: parts[3] };
      });
      if (!parsed.length) throw new Error('Informe ao menos uma questão.');
      const next = await apiRequest<QuestionView[]>(`/assessments/${selected.id}/questions`, { method: 'PUT', body: JSON.stringify(parsed) });
      setQuestions(next); setError(''); await loadSelected();
    } catch (exception) { handleError(exception, 'Não foi possível salvar as questões.'); }
  };

  const organize = async () => {
    if (!selected || classIds.length === 0) { setError('Selecione ao menos uma turma para organizar os estudantes.'); return; }
    try {
      await apiRequest(`/assessments/${selected.id}/organization`, { method: 'POST', body: JSON.stringify({ classIds }) });
      setClassIds([]); setError(''); await loadSelected();
    } catch (exception) { handleError(exception, 'Não foi possível organizar os estudantes.'); }
  };

  const updateAttendance = async (assignmentId: number, status: string) => {
    if (!selected) return;
    try {
      const next = await apiRequest<AssignmentView>(`/assessments/${selected.id}/assignments/${assignmentId}/attendance`, { method: 'PATCH', body: JSON.stringify({ status }) });
      setAssignments((current) => current.map((item) => item.id === next.id ? next : item));
    } catch (exception) { handleError(exception, 'Não foi possível atualizar a presença.'); }
  };

  const generateArtifact = async (type: string) => {
    if (!selected) return;
    try { setArtifact(await apiRequest<ArtifactView>(`/assessments/${selected.id}/artifacts/${type}${query({ schoolId })}`)); }
    catch (exception) { handleError(exception, 'Não foi possível gerar o material solicitado.'); }
  };

  const recordOccurrence = async () => {
    if (!selected || !occurrenceText.trim()) { setError('Descreva a ocorrência antes de registrar.'); return; }
    try {
      await apiRequest(`/assessments/${selected.id}/occurrences`, { method: 'POST', body: JSON.stringify({ schoolId: schoolId ? Number(schoolId) : undefined, description: occurrenceText.trim() }) });
      setOccurrenceText(''); setError('');
    } catch (exception) { handleError(exception, 'Não foi possível registrar a ocorrência.'); }
  };

  const importAnswerSheets = async () => {
    if (!selected) return;
    try {
      const sheets = answerText.split('\n').map((line) => line.trim()).filter(Boolean).map((line, index) => {
        const parts = line.split(';').map((part) => part.trim()).filter(Boolean);
        if (parts.length < 2) throw new Error(`Revise a linha ${index + 1}. Informe o identificador e ao menos uma resposta.`);
        const answers: Record<number, string> = {};
        for (const part of parts.slice(1)) {
          const [sequence, option] = part.split('=').map((value) => value.trim());
          if (!Number(sequence) || !option) throw new Error(`Revise a resposta "${part}" na linha ${index + 1}. Use questão=alternativa.`);
          answers[Number(sequence)] = option;
        }
        return sourceType === 'ONLINE' ? { onlineAccessCode: parts[0], answers } : parts[0].startsWith('AV') ? { labelCode: parts[0], answers } : { registration: parts[0], answers };
      });
      if (!sheets.length) throw new Error('Informe ao menos um gabarito para importar.');
      const next = await apiRequest<ImportSummary>(`/assessments/${selected.id}/answer-sheets`, { method: 'POST', body: JSON.stringify({ sourceType, sheets }) });
      setImportSummary(next); setError(''); await loadSelected();
    } catch (exception) { handleError(exception, 'Não foi possível importar os gabaritos.'); }
  };

  const processAssessment = async () => {
    if (!selected) return;
    try {
      await apiRequest(`/assessments/${selected.id}/process`, { method: 'POST' });
      setError(''); await loadSelected(); await loadResults();
    } catch (exception) { handleError(exception, 'Não foi possível processar os resultados.'); }
  };

  return <main className="app-page">
    <PageHeader eyebrow="Pedagógico" title="Avaliações em Rede" description="Diagnóstica, Monitoramento e Final com organização, gabaritos, processamento e resultados rastreáveis." manualSections={manualSections} actions={canWrite ? <Button type="button" variant="primary" onClick={() => setCreateOpen(true)}><Plus aria-hidden="true" size={18} />Nova avaliação</Button> : undefined} />
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    <FilterBar actions={<Button type="button" variant="ghost" onClick={() => void loadAssessments()}><RefreshCw aria-hidden="true" size={17} />Atualizar</Button>}>
      <SelectField name="assessmentYearFilter" label="Ano letivo" value={year} onChange={(event) => setYear(event.target.value)} options={[currentYear - 2, currentYear - 1, currentYear, currentYear + 1].map((value) => ({ value: value.toString(), label: value.toString() }))} />
      <SelectField name="assessmentStageFilter" label="Etapa" value={stage} onChange={(event) => setStage(event.target.value)} options={[{ value: '', label: 'Todas as etapas' }, { value: 'DIAGNOSTIC', label: 'Diagnóstica' }, { value: 'MONITORING', label: 'Monitoramento' }, { value: 'FINAL', label: 'Final' }]} />
      <SelectField name="assessmentSchoolFilter" label="Unidade escolar" value={schoolId} onChange={(event) => setSchoolId(event.target.value)} options={[...(canNetworkResults ? [{ value: '', label: 'Rede municipal' }] : []), ...(catalog?.schools ?? []).map((school) => ({ value: school.id.toString(), label: school.name }))]} />
      <SelectField name="assessmentSelection" label="Avaliação" value={selectedId} onChange={(event) => setSelectedId(event.target.value)} options={[{ value: '', label: assessments.length ? 'Selecione uma avaliação' : 'Nenhuma avaliação encontrada' }, ...assessments.map((item) => ({ value: item.id.toString(), label: `${item.name} · ${stageLabel[item.stage] ?? item.stage}` }))]} />
    </FilterBar>

    <div className="assessment-tabs" role="tablist" aria-label="Etapas da Avaliação em Rede">{tabs.map((item) => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id} className={`assessment-tab${tab === item.id ? ' assessment-tab--active' : ''}`} onClick={() => setTab(item.id)}>{item.label}</button>)}</div>

    {loading ? <StateMessage title="Carregando avaliações" message="Aguarde enquanto os dados são consultados." /> : tab === 'assessments' ? <AssessmentList assessments={assessments} selectedId={selectedId} onSelect={setSelectedId} /> : !selected ? <StateMessage title="Selecione uma avaliação" message="Escolha uma avaliação no filtro acima para continuar." /> : tab === 'organization' ? <section className="assessment-section"><SectionHeading title="Organização e materiais" description={`${selected.schoolCount} escola(s), ${selected.classCount} turma(s) e ${selected.studentCount} estudante(s) organizados.`} />
      {canWrite ? <><div className="assessment-class-grid">{eligibleClasses.length ? eligibleClasses.map((item) => <label className="assessment-check" key={item.id}><input type="checkbox" checked={classIds.includes(item.id)} onChange={(event) => setClassIds((current) => event.target.checked ? [...current, item.id] : current.filter((id) => id !== item.id))} /><span><strong>{item.name}</strong><small>{catalog?.schools.find((school) => school.id === item.schoolId)?.name} · {item.stage}</small></span></label>) : <StateMessage title="Nenhuma turma compatível" message="Cadastre ou selecione turmas com o mesmo ano letivo e etapa/ano-série da avaliação." />}</div><div className="assessment-actions"><Button type="button" variant="primary" onClick={() => void organize()}><ClipboardCheck aria-hidden="true" size={17} />Organizar estudantes</Button><Button type="button" onClick={() => void generateArtifact('ATTENDANCE_LIST')}>Lista de presença</Button><Button type="button" onClick={() => void generateArtifact('LABELS')}>Etiquetas</Button><Button type="button" onClick={() => void generateArtifact('APPLICATOR_MANUAL')}>Manual do Aplicador</Button><Button type="button" onClick={() => void generateArtifact('INCIDENT_MINUTES')}>Ata de Ocorrências</Button><Button type="button" onClick={() => void generateArtifact('MAKEUP_ACCESS')}>Segunda chamada</Button></div><div className="assessment-occurrence"><TextAreaField name="assessmentOccurrence" label="Registrar ocorrência" value={occurrenceText} onChange={(event) => setOccurrenceText(event.target.value)} placeholder="Descreva de forma objetiva o que ocorreu durante a aplicação." /><Button type="button" onClick={() => void recordOccurrence()}>Registrar ocorrência</Button></div></> : null}
      {artifact ? <ArtifactPanel artifact={artifact} /> : null}<AssignmentsTable assignments={assignments} canWrite={canWrite} onAttendance={updateAttendance} />
    </section> : tab === 'answer-sheets' ? <section className="assessment-section"><SectionHeading title="Questões e gabaritos" description={`Questões configuradas: ${questions.length}. Os dados brutos recebidos são preservados para rastreabilidade.`} />
      {canWrite ? <><TextAreaField name="assessmentQuestions" label="Questões, descritores e habilidades" rows={8} value={questionText} onChange={(event) => setQuestionText(event.target.value)} hint="Uma questão por linha: número|descritor|habilidade|alternativa correta. Ex.: 1|D01|Resolver problemas de adição|A" /><div className="assessment-actions"><Button type="button" onClick={() => void saveQuestions()}>Salvar questões e gabarito oficial</Button></div><SelectField name="answerSource" label="Origem das respostas" value={sourceType} onChange={(event) => setSourceType(event.target.value)} options={[{ value: 'IMPORT', label: 'Importar gabaritos' }, { value: 'MANUAL', label: 'Inserção manual' }, { value: 'ONLINE', label: 'Segunda chamada/online' }]} /><TextAreaField name="answerSheets" label="Respostas" rows={8} value={answerText} onChange={(event) => setAnswerText(event.target.value)} hint={sourceType === 'ONLINE' ? 'Uma linha por estudante: código de acesso;1=A;2=B;3=C' : 'Uma linha por estudante: matrícula ou código da etiqueta;1=A;2=B;3=C'} /><Button type="button" variant="primary" onClick={() => void importAnswerSheets()}><Upload aria-hidden="true" size={17} />Importar gabaritos</Button></> : null}
      {importSummary ? <ImportPanel summary={importSummary} /> : <StateMessage title="Nenhum lote importado nesta sessão" message="Antes de processar, o sistema apresentará registros válidos, inválidos e rejeitados por associação." />}
    </section> : tab === 'processing' ? <section className="assessment-section"><SectionHeading title="Processamento de gabaritos" description="Confira inconsistências antes de processar. Cada reprocessamento cria uma execução independente e auditável." />
      {validation ? <div className="assessment-metrics"><Metric label="Registros lidos" value={validation.recordsRead} /><Metric label="Válidos" value={validation.valid} /><Metric label="Com inconsistências" value={validation.invalid + validation.associationRejected} /><Metric label="Associação rejeitada" value={validation.associationRejected} /></div> : <StateMessage title="Validação indisponível" message="Sua conta não possui permissão de processamento ou ainda não há dados para validar." />}
      {canProcess ? <div className="assessment-actions"><Button type="button" variant="primary" onClick={() => void processAssessment()} disabled={!validation?.valid}><Play aria-hidden="true" size={17} />{runs.length ? 'Reprocessar resultados' : 'Processar resultados'}</Button></div> : null}<ProcessingHistory runs={runs} />
    </section> : <section className="assessment-section"><SectionHeading title="Resultados" description="Consulte a consolidação da última execução concluída e a análise por habilidade/descritor." />
      {canReadResults ? <><FilterBar><SelectField name="resultLevel" label="Nível de análise" value={resultLevel} onChange={(event) => setResultLevel(event.target.value)} options={[...(canNetworkResults ? [{ value: 'NETWORK', label: 'Rede municipal' }] : []), { value: 'SCHOOL', label: 'Unidade escolar' }, { value: 'CLASS', label: 'Turma' }, { value: 'STUDENT', label: 'Estudante' }]} /></FilterBar><ResultsTable results={results} /><SkillTable skills={skills} /></> : <StateMessage title="Resultados não permitidos" message="Sua conta não possui permissão para consultar resultados de avaliações." />}
    </section>}

    <AssessmentDialog open={createOpen} catalog={catalog} onClose={() => setCreateOpen(false)} onSave={createAssessment} />
  </main>;
}

function AssessmentList({ assessments, selectedId, onSelect }: { assessments: AssessmentView[]; selectedId: string; onSelect: (id: string) => void; }) {
  if (!assessments.length) return <StateMessage title="Nenhuma avaliação encontrada" message="Não existem avaliações para os filtros selecionados." />;
  return <section className="assessment-section"><SectionHeading title="Avaliações disponíveis" description="Selecione uma avaliação para acessar organização, gabaritos, processamento e resultados." /><div className="table-wrap"><table className="data-table"><thead><tr><th>Avaliação</th><th>Etapa</th><th>Ano/série</th><th>Situação</th><th>Escopo</th><th>Ação</th></tr></thead><tbody>{assessments.map((item) => <tr key={item.id} className={selectedId === item.id.toString() ? 'assessment-row--selected' : undefined}><td><strong>{item.name}</strong><div className="muted">{item.componentName ?? 'Multidisciplinar'}</div></td><td>{stageLabel[item.stage] ?? item.stage}</td><td>{item.academicYear} · {item.gradeStage}</td><td>{statusLabel[item.status] ?? item.status}</td><td>{item.schoolCount} escola(s) · {item.classCount} turma(s) · {item.studentCount} estudante(s)</td><td><Button type="button" variant="ghost" onClick={() => onSelect(item.id.toString())}>Selecionar</Button></td></tr>)}</tbody></table></div></section>;
}

function SectionHeading({ title, description }: { title: string; description: string; }) { return <div className="assessment-section__heading"><div><h2>{title}</h2><p>{description}</p></div></div>; }
function Metric({ label, value }: { label: string; value: number; }) { return <article className="assessment-metric"><span>{label}</span><strong>{value.toLocaleString('pt-BR')}</strong></article>; }
function ArtifactPanel({ artifact }: { artifact: ArtifactView; }) { return <section className="assessment-artifact"><div className="assessment-artifact__heading"><FileText aria-hidden="true" size={19} /><div><strong>{artifact.title}</strong><span>{artifact.lineCount} linha(s) gerada(s)</span></div></div><pre>{artifact.lines.join('\n')}</pre></section>; }
function ImportPanel({ summary }: { summary: ImportSummary; }) { return <section className="assessment-import"><div className="assessment-metrics"><Metric label="Registros lidos" value={summary.recordsRead} /><Metric label="Válidos" value={summary.valid} /><Metric label="Com inconsistências" value={summary.invalid} /></div>{summary.items.some((item) => item.status !== 'VALID') ? <ul>{summary.items.filter((item) => item.status !== 'VALID').map((item, index) => <li key={`${item.identifier}-${index}`}><strong>{item.identifier}:</strong> {item.message}</li>)}</ul> : <StateMessage title="Lote válido" message="Todos os gabaritos do lote estão aptos ao processamento." />}</section>; }

function AssignmentsTable({ assignments, canWrite, onAttendance }: { assignments: AssignmentView[]; canWrite: boolean; onAttendance: (id: number, status: string) => Promise<void>; }) {
  if (!assignments.length) return <StateMessage title="Nenhum estudante organizado" message="Selecione as turmas compatíveis e use Organizar estudantes." />;
  return <div className="table-wrap"><table className="data-table"><thead><tr><th>Estudante</th><th>Escola/Turma</th><th>Etiqueta</th><th>Pacote</th><th>Presença</th></tr></thead><tbody>{assignments.map((item) => <tr key={item.id}><td><strong>{item.studentName}</strong><div className="muted">{item.registration}</div></td><td>{item.schoolName}<div className="muted">{item.className}</div></td><td>{item.labelCode}</td><td>{item.packageCode}</td><td>{canWrite ? <select className="select assessment-inline-select" aria-label={`Presença de ${item.studentName}`} value={item.attendanceStatus} onChange={(event) => void onAttendance(item.id, event.target.value)}><option value="PENDING">Não registrada</option><option value="PRESENT">Presente</option><option value="ABSENT">Ausente</option><option value="MAKEUP">Segunda chamada</option></select> : item.attendanceStatus}</td></tr>)}</tbody></table></div>;
}

function ProcessingHistory({ runs }: { runs: ProcessingRunView[]; }) {
  if (!runs.length) return <StateMessage title="Ainda não processado" message="Nenhuma execução de processamento foi registrada para esta avaliação." />;
  return <div className="table-wrap"><table className="data-table"><thead><tr><th>Execução</th><th>Situação</th><th>Válidos</th><th>Inválidos</th><th>Responsável</th><th>Início</th></tr></thead><tbody>{runs.map((run) => <tr key={run.id}><td>#{run.runNumber}</td><td>{run.status === 'COMPLETED' ? 'Concluída' : run.status}</td><td>{run.validSheets}</td><td>{run.invalidSheets}</td><td>{run.initiatedBy}</td><td>{new Date(run.startedAt).toLocaleString('pt-BR')}</td></tr>)}</tbody></table></div>;
}

function ResultsTable({ results }: { results: ResultSummaryRow[]; }) {
  if (!results.length) return <StateMessage title="Sem resultados processados" message="Processe os gabaritos válidos para disponibilizar a consolidação neste nível." />;
  return <div className="table-wrap"><table className="data-table"><thead><tr><th>Nível</th><th>Estudantes</th><th>Acertos</th><th>Questões respondidas</th><th>Percentual de acerto</th></tr></thead><tbody>{results.map((item) => <tr key={`${item.keyId}-${item.label}`}><td><strong>{item.label}</strong></td><td>{item.students}</td><td>{item.correctAnswers}</td><td>{item.totalQuestions}</td><td>{formatPercent(item.scorePercent)}</td></tr>)}</tbody></table></div>;
}

function SkillTable({ skills }: { skills: SkillSummaryRow[]; }) {
  return <section className="assessment-skills"><h3>Desempenho por habilidade/descritor</h3>{!skills.length ? <StateMessage title="Sem dados por habilidade" message="Os resultados por habilidade ficarão disponíveis após o processamento." /> : <div className="table-wrap"><table className="data-table"><thead><tr><th>Descritor</th><th>Habilidade</th><th>Acertos</th><th>Total</th><th>Percentual de acerto</th></tr></thead><tbody>{skills.map((item) => <tr key={`${item.descriptor}-${item.skill}`}><td>{item.descriptor}</td><td>{item.skill}</td><td>{item.correctAnswers}</td><td>{item.totalQuestions}</td><td>{formatPercent(item.scorePercent)}</td></tr>)}</tbody></table></div>}</section>;
}
