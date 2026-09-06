import { Plus } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { MetricCard } from '../chart/MetricCard';
import { ProgressBarChart } from '../chart/ProgressBarChart';
import { TrendLineChart } from '../chart/TrendLineChart';
import { Button } from '../button/Button';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { PageHeader } from '../layout/PageHeader';
import { StateMessage } from '../state/StateMessage';
import type { AccessContext } from '../workspace/types';
import { IndicatorRecordDialog } from './IndicatorRecordDialog';
import type { BreakdownItem, IndicatorRecord, MonitoringClass, MonitoringContext, MonitoringSummary, SourceMetric, TrendPoint } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };

const manualSections = [
  { title: 'Finalidade', content: 'Acompanhar resultados pedagógicos consolidados da Rede, unidade escolar e turma com base nos dados persistidos nos módulos do KRINO.' },
  { title: 'Campos e filtros', content: 'Ano letivo, período, unidade escolar e turma definem o nível da análise. Quando a conta possui escopo municipal, deixar Unidade escolar em Rede municipal apresenta a visão da Rede.' },
  { title: 'Indicadores', content: 'Cobertura de lançamentos representa estudantes com ao menos uma nota registrada sobre estudantes matriculados no escopo/ano. Aproveitamento observado usa somente notas com pontuação máxima definida: soma das notas dividida pela soma das pontuações máximas, multiplicada por 100.' },
  { title: 'Botões e ações', content: 'Registrar indicador permite guardar resultado observado com fonte documentada ou criar simulação/projeção não oficial. O sistema não calcula IDEB/IDEPE oficial sem fórmula confirmada.' },
  { title: 'Regras', content: 'Os filtros afetam cards e gráficos. Resultados de fontes diferentes permanecem identificados. Simulações e projeções são sempre exibidas como não oficiais e preservam origem e premissas.' },
  { title: 'Permissões', content: 'MONITORING_READ permite consulta no escopo atribuído. MONITORING_MANAGE permite registrar referências, simulações e projeções. A Rede completa exige permissão municipal.' },
  { title: 'Fluxos', content: 'Selecione o nível e período, analise os cards, acompanhe a evolução, compare escolas/turmas e consulte referências IDEB/IDEPE. Resultados de Avaliação em Rede aparecerão como nova fonte quando estiverem integrados.' },
  { title: 'Mensagens e estados', content: 'A tela diferencia ausência de dados, percentual indisponível, falta de permissão e erro técnico. Habilidades/descritores ficam em estado informativo enquanto não houver fonte avaliativa integrada.' },
];

function metricForSource(sources: SourceMetric[], sourceCode = 'INTERNAL_DIARY') {
  return sources.find((source) => source.sourceCode === sourceCode) ?? sources[0];
}

export function MonitoringPage({ context, onUnauthorized }: Props) {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear.toString()); const [period, setPeriod] = useState(''); const [schoolId, setSchoolId] = useState(''); const [classId, setClassId] = useState('');
  const [monitoringContext, setMonitoringContext] = useState<MonitoringContext>(); const [classes, setClasses] = useState<MonitoringClass[]>([]); const [summary, setSummary] = useState<MonitoringSummary>(); const [trend, setTrend] = useState<TrendPoint[]>([]); const [breakdown, setBreakdown] = useState<BreakdownItem[]>([]); const [records, setRecords] = useState<IndicatorRecord[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [denied, setDenied] = useState(false); const [recordOpen, setRecordOpen] = useState(false);

  const selectedSchool = monitoringContext?.schools.find((school) => school.id.toString() === schoolId);
  const schoolPermissions = selectedSchool ? context.schoolAccess.find((scope) => scope.schoolCode === selectedSchool.code)?.permissions ?? [] : [];
  const canManage = schoolId ? context.networkPermissions.includes('MONITORING_MANAGE') || schoolPermissions.includes('MONITORING_MANAGE') : Boolean(monitoringContext?.networkManage);

  const loadContext = useCallback(async () => {
    try {
      const next = await apiRequest<MonitoringContext>(`/pedagogical-monitoring/context?year=${year}`); setMonitoringContext(next);
      setSchoolId((current) => {
        if (current && next.schools.some((school) => school.id.toString() === current)) return current;
        return next.networkView ? '' : next.schools[0]?.id.toString() ?? '';
      });
    } catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar o contexto de monitoramento.'); }
  }, [year, onUnauthorized]);

  const loadClasses = useCallback(async () => {
    if (!schoolId) { setClasses([]); setClassId(''); return; }
    try { const next = await apiRequest<MonitoringClass[]>(`/pedagogical-monitoring/classes?schoolId=${schoolId}&year=${year}`); setClasses(next); setClassId((current) => current && next.some((item) => item.id.toString() === current) ? current : ''); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar as turmas.'); }
  }, [schoolId, year]);

  const loadData = useCallback(async () => {
    if (!monitoringContext || (!monitoringContext.networkView && !schoolId)) return;
    setLoading(true); setError(''); setDenied(false);
    const scope = `${schoolId ? `&schoolId=${schoolId}` : ''}${classId ? `&classId=${classId}` : ''}`; const periodParam = period ? `&period=${period}` : '';
    try {
      const [nextSummary, nextTrend, nextBreakdown, nextRecords] = await Promise.all([
        apiRequest<MonitoringSummary>(`/pedagogical-monitoring/summary?year=${year}${periodParam}${scope}`),
        apiRequest<TrendPoint[]>(`/pedagogical-monitoring/trend?year=${year}${scope}`),
        apiRequest<BreakdownItem[]>(`/pedagogical-monitoring/breakdown?year=${year}${periodParam}${schoolId ? `&schoolId=${schoolId}` : ''}`),
        apiRequest<IndicatorRecord[]>(`/pedagogical-monitoring/indicator-records?year=${year}${schoolId ? `&schoolId=${schoolId}` : ''}`),
      ]);
      setSummary(nextSummary); setTrend(nextTrend); setBreakdown(nextBreakdown); setRecords(nextRecords);
    } catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os indicadores pedagógicos.'); }
    finally { setLoading(false); }
  }, [monitoringContext, year, period, schoolId, classId, onUnauthorized]);

  useEffect(() => { void loadContext(); }, [loadContext]); useEffect(() => { void loadClasses(); }, [loadClasses]); useEffect(() => { void loadData(); }, [loadData]);

  const internal = summary ? metricForSource(summary.sources) : undefined;
  const levelLabel = classId ? classes.find((item) => item.id.toString() === classId)?.name ?? 'Turma' : schoolId ? selectedSchool?.name ?? 'Unidade escolar' : 'Rede municipal';
  const trendItems = useMemo(() => trend.map((point) => ({ label: `${point.period}º`, value: metricForSource(point.sources)?.achievementPercent })), [trend]);
  const breakdownItems = useMemo(() => breakdown.map((item) => ({ label: item.label, value: metricForSource(item.sources)?.achievementPercent, detail: metricForSource(item.sources)?.studentsWithResults != null ? `${metricForSource(item.sources)!.studentsWithResults} estudante(s) com resultado` : undefined })), [breakdown]);

  if (denied) return <main className="app-page"><PageHeader eyebrow="Pedagógico" title="Monitoramento Pedagógico" description="Indicadores educacionais e evolução por período." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para consultar o monitoramento neste escopo." /></main>;

  return <main className="app-page"><PageHeader eyebrow="Pedagógico" title="Monitoramento Pedagógico" description={`Nível atual: ${levelLabel}. Indicadores internos e fontes integradas.`} manualSections={manualSections} actions={canManage ? <Button type="button" variant="primary" onClick={() => setRecordOpen(true)}><Plus aria-hidden="true" size={18} />Registrar indicador</Button> : undefined} />
    {error ? <StateMessage kind="error" title="Não foi possível carregar o monitoramento" message={error} /> : null}
    <FilterBar><SelectField name="monitoringYear" label="Ano letivo" value={year} onChange={(event) => setYear(event.target.value)} options={[currentYear - 2, currentYear - 1, currentYear, currentYear + 1].map((value) => ({ value: value.toString(), label: value.toString() }))} /><SelectField name="monitoringPeriod" label="Período" value={period} onChange={(event) => setPeriod(event.target.value)} options={[{ value: '', label: 'Todos os períodos' }, ...[1,2,3,4].map((value) => ({ value: value.toString(), label: `${value}º período` }))]} /><SelectField name="monitoringSchool" label="Unidade escolar" value={schoolId} onChange={(event) => { setSchoolId(event.target.value); setClassId(''); }} options={[...(monitoringContext?.networkView ? [{ value: '', label: 'Rede municipal' }] : []), ...(monitoringContext?.schools ?? []).map((school) => ({ value: school.id.toString(), label: school.name }))]} /><SelectField name="monitoringClass" label="Turma" disabled={!schoolId} value={classId} onChange={(event) => setClassId(event.target.value)} options={[{ value: '', label: schoolId ? 'Todas as turmas' : 'Selecione uma escola' }, ...classes.map((item) => ({ value: item.id.toString(), label: `${item.name} · ${item.stage}` }))]} /></FilterBar>
    {loading ? <StateMessage title="Atualizando indicadores" message="Aguarde enquanto os resultados são consolidados." /> : <><section className="metric-grid"><MetricCard label="Estudantes no escopo" value={(internal?.totalStudents ?? 0).toLocaleString('pt-BR')} detail="Matrículas do ano no nível selecionado" /><MetricCard label="Com resultado lançado" value={(internal?.studentsWithResults ?? 0).toLocaleString('pt-BR')} detail="Ao menos uma nota interna registrada" /><MetricCard label="Cobertura de lançamentos" value={internal?.coveragePercent == null ? 'Sem base' : `${internal.coveragePercent.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`} detail="Estudantes com resultado ÷ estudantes do escopo" /><MetricCard label="Aproveitamento observado" value={internal?.achievementPercent == null ? 'Sem base' : `${internal.achievementPercent.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`} detail="Notas ÷ pontuações máximas informadas" /><MetricCard label="Avaliações com resultados" value={(internal?.assessmentsWithResults ?? 0).toLocaleString('pt-BR')} detail={internal?.sourceLabel ?? 'Avaliações internas'} /></section><section className="monitoring-charts"><TrendLineChart title="Evolução do aproveitamento observado" points={trendItems} emptyMessage="Ainda não há pontuações máximas e notas suficientes para exibir evolução." /><ProgressBarChart title={schoolId ? 'Comparação entre turmas' : 'Comparação entre unidades escolares'} items={breakdownItems} emptyMessage="Ainda não há resultados comparáveis neste nível." /></section><section className="monitoring-source-card"><h3>Desempenho por habilidade/descritor</h3><StateMessage title="Aguardando fonte avaliativa integrada" message="Esta visão será preenchida quando existirem resultados de avaliação vinculados a habilidades/descritores. O contrato de provedores do monitoramento já permite integrar novas fontes sem duplicar estudantes, turmas ou escolas." /></section><section className="indicator-records"><div className="monitoring-section-heading"><div><h3>IDEB e IDEPE</h3><p className="muted">Referências observadas preservam a fonte. Simulações e projeções são sempre identificadas como não oficiais.</p></div></div>{records.length === 0 ? <StateMessage title="Nenhuma referência ou cenário registrado" message="Não existem valores IDEB/IDEPE registrados para o ano e escopo selecionados." /> : <div className="indicator-card-grid">{records.map((record) => <article className="indicator-card" key={record.id}><div className="indicator-card__header"><strong>{record.indicator} · {record.value.toLocaleString('pt-BR', { maximumFractionDigits: 3 })}</strong><span className={record.classification === 'NON_OFFICIAL' ? 'status-badge' : 'status-badge status-badge--active'}>{record.classification === 'NON_OFFICIAL' ? 'Não oficial' : 'Referência documentada'}</span></div><span>{record.recordType === 'OBSERVED_RESULT' ? 'Resultado observado' : record.recordType === 'SIMULATION' ? 'Simulação' : 'Projeção'} · {record.scenarioName}</span><p><strong>Origem:</strong> {record.sourceReference}</p>{record.assumptions ? <p><strong>Premissas:</strong> {record.assumptions}</p> : null}</article>)}</div>}</section></>}
    <IndicatorRecordDialog open={recordOpen} schoolId={schoolId ? Number(schoolId) : undefined} year={Number(year)} onClose={() => setRecordOpen(false)} onSaved={async () => { await loadData(); }} />
  </main>;
}
