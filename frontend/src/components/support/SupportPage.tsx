import { CircleHelp, Plus, Settings2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { MetricCard } from '../chart/MetricCard';
import { ProgressBarChart } from '../chart/ProgressBarChart';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { AccessContext } from '../workspace/types';
import { SlaPolicyDialog } from './SlaPolicyDialog';
import { TicketDetailDialog } from './TicketDetailDialog';
import { TicketDialog } from './TicketDialog';
import type { SlaPolicy, SupportContext, SupportReport, SupportTicket, SupportTicketDetail } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type SupportTab = 'tickets' | 'report' | 'sla';

const severityLabel: Record<string, string> = { CRITICAL: 'Crítico', MEDIUM: 'Médio', LOW: 'Baixo' };
const statusLabel: Record<string, string> = { OPEN: 'Aberto', IN_PROGRESS: 'Em atendimento', WAITING_REQUESTER: 'Aguardando solicitante', RESOLVED: 'Resolvido', CLOSED: 'Encerrado' };
const countingRuleLabel: Record<string, string> = { UNDEFINED: 'Regra de contagem não definida', ELAPSED: 'Horas corridas', BUSINESS: 'Horas úteis configuradas' };
const slaStateLabel: Record<string, string> = { NOT_CONFIGURED: 'Sem data calculada', ON_TIME: 'No prazo', AT_RISK: 'Em risco', BREACHED: 'Prazo vencido', MET: 'Cumprido' };

const manualSections = [
  { title: 'Finalidade', content: 'Abrir, acompanhar e atender chamados de suporte técnico e manutenção da plataforma, preservando histórico, criticidade, solução e evidências dos prazos contratuais.' },
  { title: 'Campos e filtros', content: 'Status, Criticidade, Unidade escolar e Buscar filtram a lista. O novo chamado possui Assunto, Descrição, Tipo de atendimento, Criticidade, Unidade escolar e, quando aplicável, Avaliação em Rede relacionada.' },
  { title: 'Criticidade', content: 'Crítico: indisponibilidade total ou função essencial sem contorno, com resposta em 1 hora e solução em 4 horas. Médio: falha parcial com alternativa de operação, com resposta em 4 horas e solução em 24 horas. Baixo: dúvida, suporte ou ajuste sem comprometer a continuidade, com resposta em 24 horas e solução em 72 horas.' },
  { title: 'Regra de contagem do prazo', content: 'Os tempos contratuais são fixos. A Administração define apenas se a contagem usa horas corridas, horas úteis com calendário configurado ou se ainda não há regra confirmada. Enquanto estiver não definida, o sistema mostra os tempos contratuais sem inventar uma data de vencimento.' },
  { title: 'Botões e ações', content: 'Novo chamado abre uma solicitação. Ver detalhes apresenta descrição, prazos, histórico e mensagens. Usuários de atendimento podem alterar Status e Criticidade e registrar a Solução. Configurar regra aparece somente para quem possui permissão administrativa para configurar os prazos.' },
  { title: 'Regras de uso', content: 'A data e hora de abertura não podem ser alteradas. Mudanças de status e criticidade ficam no histórico. Resolver ou encerrar exige solução registrada. Chamados encerrados não recebem novas mensagens. Alterar a política global não modifica retroativamente chamados existentes.' },
  { title: 'Permissões', content: 'SUPPORT_TICKET_CREATE abre chamados; SUPPORT_TICKET_READ consulta o próprio histórico e o escopo autorizado; SUPPORT_TICKET_MANAGE atende chamados; SUPPORT_REPORT_READ consulta indicadores administrativos; SUPPORT_SLA_MANAGE configura a regra de contagem em escopo municipal.' },
  { title: 'Fluxos principais', content: 'Solicitante: abrir chamado, acompanhar estado e trocar mensagens. Atendimento: abrir o chamado, responder, ajustar criticidade quando necessário, atualizar o estado e registrar a solução. Administração: acompanhar indicadores e configurar a forma de contagem dos prazos.' },
  { title: 'Mensagens e estados', content: 'Os prazos podem aparecer como Sem data calculada, No prazo, Em risco, Prazo vencido ou Cumprido. A tela diferencia lista vazia, carregamento, falta de permissão e falha técnica. Nenhuma mensagem promete prazo diferente do contrato.' },
];

function formatDate(value: string) { return new Date(value).toLocaleString('pt-BR'); }
function formatMinutes(value?: number | null) {
  if (value == null) return 'Sem dados';
  if (value < 60) return `${value.toFixed(0)} min`;
  const hours = value / 60;
  return `${hours.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h`;
}

export function SupportPage({ context: _context, onUnauthorized }: Props) {
  const [tab, setTab] = useState<SupportTab>('tickets');
  const [supportContext, setSupportContext] = useState<SupportContext>();
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [report, setReport] = useState<SupportReport>();
  const [status, setStatus] = useState('');
  const [severity, setSeverity] = useState('');
  const [schoolId, setSchoolId] = useState('');
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<SupportTicketDetail>();
  const [selectedPolicy, setSelectedPolicy] = useState<SlaPolicy>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [denied, setDenied] = useState(false);

  const handleFailure = useCallback((exception: unknown, fallback: string) => {
    if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
    if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; }
    setError(exception instanceof Error ? exception.message : fallback);
  }, [onUnauthorized]);

  const loadContext = useCallback(async () => {
    try { setSupportContext(await apiRequest<SupportContext>('/support/context')); }
    catch (exception) { handleFailure(exception, 'Não foi possível carregar o contexto de suporte.'); }
  }, [handleFailure]);

  const loadTickets = useCallback(async () => {
    setLoading(true); setError(''); setDenied(false);
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    if (severity) params.set('severity', severity);
    if (schoolId) params.set('schoolId', schoolId);
    if (search.trim()) params.set('search', search.trim());
    try { setTickets(await apiRequest<SupportTicket[]>(`/support/tickets${params.toString() ? `?${params}` : ''}`)); }
    catch (exception) { handleFailure(exception, 'Não foi possível carregar os chamados.'); }
    finally { setLoading(false); }
  }, [status, severity, schoolId, search, handleFailure]);

  const loadReport = useCallback(async () => {
    if (!supportContext?.canReport || tab !== 'report') return;
    try { setReport(await apiRequest<SupportReport>(`/support/reports${schoolId ? `?schoolId=${schoolId}` : ''}`)); }
    catch (exception) { handleFailure(exception, 'Não foi possível carregar os indicadores de suporte.'); }
  }, [supportContext?.canReport, tab, schoolId, handleFailure]);

  useEffect(() => { void loadContext(); }, [loadContext]);
  useEffect(() => { void loadTickets(); }, [loadTickets]);
  useEffect(() => { void loadReport(); }, [loadReport]);

  const openDetail = async (ticketId: number) => {
    try { setDetail(await apiRequest<SupportTicketDetail>(`/support/tickets/${ticketId}`)); }
    catch (exception) { handleFailure(exception, 'Não foi possível abrir o chamado.'); }
  };

  const refreshDetail = async (ticketId: number) => {
    setDetail(await apiRequest<SupportTicketDetail>(`/support/tickets/${ticketId}`));
    await loadTickets();
    if (supportContext?.canReport) await loadReport();
  };

  const createTicket = async (payload: Record<string, unknown>) => {
    const created = await apiRequest<SupportTicket>('/support/tickets', { method: 'POST', body: JSON.stringify(payload) });
    await loadTickets();
    await openDetail(created.id);
  };

  const addMessage = async (ticketId: number, message: string) => {
    await apiRequest(`/support/tickets/${ticketId}/interactions`, { method: 'POST', body: JSON.stringify({ message }) });
    await refreshDetail(ticketId);
  };

  const manageTicket = async (ticketId: number, payload: { status?: string; severity?: string; resolution?: string; }) => {
    await apiRequest(`/support/tickets/${ticketId}`, { method: 'PATCH', body: JSON.stringify(payload) });
    await refreshDetail(ticketId);
  };

  const savePolicy = async (policySeverity: string, payload: Record<string, unknown>) => {
    await apiRequest(`/support/sla-policies/${policySeverity}`, { method: 'PUT', body: JSON.stringify(payload) });
    await loadContext();
  };

  const availableTabs = useMemo(() => [
    { value: 'tickets' as const, label: 'Chamados' },
    ...(supportContext?.canReport ? [{ value: 'report' as const, label: 'Indicadores de suporte' }] : []),
    { value: 'sla' as const, label: 'Prazos de atendimento' },
  ], [supportContext?.canReport]);

  const ticketColumns: DataColumn<SupportTicket>[] = [
    { key: 'protocol', header: 'Chamado', render: (ticket) => <><strong>{ticket.protocol}</strong><small className="support-table-secondary">{formatDate(ticket.openedAt)}</small></> },
    { key: 'subject', header: 'Assunto', render: (ticket) => <><strong>{ticket.subject}</strong><small className="support-table-secondary">{ticket.schoolName ?? 'Sem unidade específica'}</small></> },
    { key: 'severity', header: 'Criticidade', render: (ticket) => severityLabel[ticket.severity] },
    { key: 'status', header: 'Status', render: (ticket) => statusLabel[ticket.status] },
    { key: 'sla', header: 'Prazos', render: (ticket) => <div className="support-sla-cell"><span>Resposta: {slaStateLabel[ticket.responseSlaState]}</span><span>Solução: {slaStateLabel[ticket.solutionSlaState]}</span></div> },
    { key: 'actions', header: 'Ações', render: (ticket) => <Button type="button" variant="ghost" onClick={() => void openDetail(ticket.id)}>Ver detalhes</Button> },
  ];

  const severityChart = useMemo(() => (report?.bySeverity ?? []).map((item) => ({
    label: severityLabel[item.severity],
    value: item.total === 0 ? null : (item.completed / item.total) * 100,
    detail: `${item.completed} concluído(s) de ${item.total} · ${item.responseBreaches} atraso(s) de resposta · ${item.solutionBreaches} atraso(s) de solução`,
  })), [report]);

  if (denied) return <main className="app-page"><PageHeader eyebrow="Suporte" title="Suporte e Chamados" description="Acompanhamento rastreável de solicitações e manutenção." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para acessar este contexto de suporte." /></main>;

  return <main className="app-page">
    <PageHeader eyebrow="Suporte" title="Suporte e Chamados" description="Abra solicitações, acompanhe o atendimento e consulte os prazos contratuais." manualSections={manualSections} actions={<Button type="button" variant="primary" onClick={() => setCreateOpen(true)}><Plus aria-hidden="true" size={18} />Novo chamado</Button>} />
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    <SegmentedTabs label="Áreas de suporte" tabs={availableTabs} value={tab} onChange={setTab} />

    {tab === 'tickets' ? <>
      <FilterBar>
        <SelectField name="supportStatus" label="Status" value={status} onChange={(event) => setStatus(event.target.value)} options={[{ value: '', label: 'Todos os status' }, { value: 'OPEN', label: 'Aberto' }, { value: 'IN_PROGRESS', label: 'Em atendimento' }, { value: 'WAITING_REQUESTER', label: 'Aguardando solicitante' }, { value: 'RESOLVED', label: 'Resolvido' }, { value: 'CLOSED', label: 'Encerrado' }]} />
        <SelectField name="supportSeverityFilter" label="Criticidade" value={severity} onChange={(event) => setSeverity(event.target.value)} options={[{ value: '', label: 'Todas as criticidades' }, { value: 'CRITICAL', label: 'Crítico' }, { value: 'MEDIUM', label: 'Médio' }, { value: 'LOW', label: 'Baixo' }]} />
        <SelectField name="supportSchoolFilter" label="Unidade escolar" value={schoolId} onChange={(event) => setSchoolId(event.target.value)} options={[{ value: '', label: supportContext?.networkManagementView ? 'Todas as unidades / meus chamados' : 'Todas as unidades disponíveis' }, ...(supportContext?.schools ?? []).map((school) => ({ value: school.id.toString(), label: school.name }))]} />
        <TextField name="supportSearch" label="Buscar" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Protocolo, assunto ou solicitante" />
      </FilterBar>
      {loading ? <StateMessage title="Carregando chamados" message="Aguarde enquanto o histórico de suporte é consultado." /> : tickets.length === 0 ? <StateMessage title="Nenhum chamado encontrado" message="Não existem chamados para os filtros selecionados." /> : <DataTable rows={tickets} columns={ticketColumns} rowKey={(ticket) => ticket.id} />}
    </> : null}

    {tab === 'report' && supportContext?.canReport ? <section className="support-report">
      <div className="support-section-heading"><div><h2>Indicadores de suporte</h2><p className="muted">Escopo: {schoolId ? supportContext.schools.find((school) => school.id.toString() === schoolId)?.name ?? 'Unidade escolar' : 'Rede municipal'}.</p></div></div>
      {!report ? <StateMessage title="Carregando indicadores" message="Aguarde enquanto os atendimentos são consolidados." /> : <>
        <section className="metric-grid">
          <MetricCard label="Chamados registrados" value={report.totals.total.toLocaleString('pt-BR')} detail={`${report.totals.active} em andamento`} />
          <MetricCard label="Atrasos de resposta" value={report.totals.responseBreaches.toLocaleString('pt-BR')} detail="Chamados que ultrapassaram o prazo de resposta calculado" />
          <MetricCard label="Atrasos de solução" value={report.totals.solutionBreaches.toLocaleString('pt-BR')} detail="Chamados que ultrapassaram o prazo de solução calculado" />
          <MetricCard label="Tempo médio de resposta" value={formatMinutes(report.totals.averageResponseMinutes)} detail="Tempo transcorrido entre abertura e primeira resposta" />
          <MetricCard label="Tempo médio de solução" value={formatMinutes(report.totals.averageSolutionMinutes)} detail="Tempo transcorrido entre abertura e resolução/encerramento" />
        </section>
        <section className="support-report-chart"><ProgressBarChart title="Percentual de chamados concluídos por criticidade" items={severityChart} emptyMessage="Ainda não há chamados suficientes para calcular a conclusão por criticidade." /></section>
      </>}
    </section> : null}

    {tab === 'sla' ? <section className="support-sla-section">
      <div className="support-section-heading"><div><h2>Prazos de atendimento</h2><p className="muted">Os tempos abaixo são contratuais. A regra de contagem pode estar pendente de definição.</p></div></div>
      <div className="support-policy-grid">{(supportContext?.slaPolicies ?? []).map((policy) => <article className="support-policy-card" key={policy.severity}>
        <div className="support-policy-card__header"><div><span className="eyebrow">Criticidade</span><h3>{severityLabel[policy.severity]}</h3></div><CircleHelp aria-hidden="true" size={22} /></div>
        <dl><div><dt>Prazo de resposta</dt><dd>{policy.responseMinutes} min</dd></div><div><dt>Prazo de solução</dt><dd>{policy.solutionMinutes} min</dd></div><div><dt>Regra de contagem</dt><dd>{countingRuleLabel[policy.countingRule]}</dd></div>{policy.warningMinutes ? <div><dt>Aviso de risco</dt><dd>{policy.warningMinutes} min antes</dd></div> : null}</dl>
        {policy.countingRule === 'BUSINESS' ? <p className="muted">{policy.businessTimezone} · {policy.workdayStart?.slice(0, 5)}–{policy.workdayEnd?.slice(0, 5)} · calendário útil configurado</p> : null}
        {supportContext?.canManageSla ? <Button type="button" variant="secondary" onClick={() => setSelectedPolicy(policy)}><Settings2 aria-hidden="true" size={17} />Configurar regra</Button> : null}
      </article>)}</div>
    </section> : null}

    <TicketDialog open={createOpen} schools={supportContext?.schools ?? []} assessments={supportContext?.assessments ?? []} onClose={() => setCreateOpen(false)} onSave={createTicket} />
    <TicketDetailDialog open={Boolean(detail)} detail={detail} onClose={() => setDetail(undefined)} onMessage={addMessage} onManage={manageTicket} />
    <SlaPolicyDialog open={Boolean(selectedPolicy)} policy={selectedPolicy} onClose={() => setSelectedPolicy(undefined)} onSave={savePolicy} />
  </main>;
}
