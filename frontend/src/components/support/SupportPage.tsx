import { LifeBuoy, Plus, Search, XCircle } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { MetricCard } from '../chart/MetricCard';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { AccessContext } from '../workspace/types';
import { NewSupportTicketDialog } from './NewSupportTicketDialog';
import { formatSupportDate, severityLabel, statusLabel } from './supportLabels';
import { SupportTicketDialog } from './SupportTicketDialog';
import type { SupportSummary, SupportTicket } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };

const manualSections = [
  { title: 'Finalidade', content: 'Abrir e acompanhar chamados de suporte técnico de forma rastreável. Usuários consultam os próprios atendimentos; a equipe autorizada gerencia a fila municipal e o histórico.' },
  { title: 'Campos e filtros', content: 'Buscar localiza assunto e, na gestão, solicitante. Status separa chamados abertos, em atendimento, aguardando solicitante, resolvidos e encerrados. Criticidade usa Crítico, Médio e Baixo.' },
  { title: 'Botões e ações', content: 'Novo chamado registra uma solicitação. Aplicar filtros atualiza a lista. Abrir chamado exibe descrição, referência de prazo, interações e solução. A equipe autorizada altera criticidade/status e registra a solução.' },
  { title: 'Regras', content: 'A data/hora de abertura é preservada. Encerramento exige solução registrada. Mudanças de status e criticidade entram no histórico. Chamado encerrado fica somente para consulta.' },
  { title: 'Prazos', content: 'Referências documentadas: Crítico resposta 1h/solução 4h; Médio 4h/24h; Baixo 24h/72h. Como os documentos não definem horas úteis ou corridas, a tela não classifica o prazo como vencido ou em risco.' },
  { title: 'Permissões', content: 'SUPPORT_TICKET_CREATE permite abrir e acompanhar chamados próprios. SUPPORT_TICKET_MANAGE permite gestão municipal e resumo administrativo. O backend valida propriedade e permissão em cada operação.' },
  { title: 'Fluxos', content: 'Abra o chamado com assunto, descrição e criticidade. Acompanhe as interações. A equipe registra atendimento, pode solicitar retorno ao usuário e, ao concluir, informa a solução antes de resolver ou encerrar.' },
  { title: 'Mensagens e estados', content: 'A tela diferencia carregamento, lista vazia, falha, ausência de permissão e chamado encerrado. Durante o envio ou salvamento, a ação informa que está em processamento.' },
];

export function SupportPage({ context, onUnauthorized }: Props) {
  const canCreate = context.permissions.includes('SUPPORT_TICKET_CREATE') || context.networkPermissions.includes('SUPPORT_TICKET_CREATE');
  const canManage = context.networkPermissions.includes('SUPPORT_TICKET_MANAGE');
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [summary, setSummary] = useState<SupportSummary>();
  const [status, setStatus] = useState('');
  const [severity, setSeverity] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [newOpen, setNewOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<number>();

  const load = useCallback(async (filters = { status, severity, search }) => {
    if (!canCreate && !canManage) { setLoading(false); return; }
    setLoading(true); setError('');
    try {
      const params = new URLSearchParams();
      if (filters.status) params.set('status', filters.status);
      if (filters.severity) params.set('severity', filters.severity);
      if (filters.search.trim()) params.set('search', filters.search.trim());
      const listPath = canManage ? '/support/admin/tickets' : '/support/tickets';
      const [nextTickets, nextSummary] = await Promise.all([
        apiRequest<SupportTicket[]>(`${listPath}?${params.toString()}`),
        canManage ? apiRequest<SupportSummary>('/support/admin/summary') : Promise.resolve(undefined),
      ]);
      setTickets(nextTickets); setSummary(nextSummary);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os chamados.');
    } finally { setLoading(false); }
  }, [canCreate, canManage, onUnauthorized, search, severity, status]);

  useEffect(() => { void load(); }, [canCreate, canManage]);

  const clearFilters = () => {
    setStatus(''); setSeverity(''); setSearch('');
    void load({ status: '', severity: '', search: '' });
  };

  const columns = useMemo<DataColumn<SupportTicket>[]>(() => [
    { key: 'id', header: '#', render: (ticket) => ticket.id },
    { key: 'subject', header: 'Assunto', render: (ticket) => <><strong>{ticket.subject}</strong>{canManage ? <small>{ticket.requesterName}</small> : null}</> },
    { key: 'severity', header: 'Criticidade', render: (ticket) => severityLabel(ticket.severity) },
    { key: 'status', header: 'Status', render: (ticket) => <span className={ticket.status === 'CLOSED' ? 'status-badge' : 'status-badge status-badge--active'}>{statusLabel(ticket.status)}</span> },
    { key: 'sla', header: 'Prazos de referência', render: (ticket) => <><strong>Resposta {ticket.responseTargetHours}h · Solução {ticket.solutionTargetHours}h</strong><small>Contagem contratual ainda não definida</small></> },
    { key: 'openedAt', header: 'Aberto em', render: (ticket) => formatSupportDate(ticket.openedAt) },
    { key: 'action', header: 'Ação', render: (ticket) => <Button type="button" variant="ghost" onClick={() => setSelectedId(ticket.id)}>Abrir chamado</Button> },
  ], [canManage]);

  return <main className="app-page">
    <PageHeader
      eyebrow="Atendimento"
      title="Suporte e chamados"
      description={canManage ? 'Fila municipal, histórico e acompanhamento dos atendimentos.' : 'Abra e acompanhe seus chamados de suporte técnico.'}
      manualSections={manualSections}
      actions={canCreate ? <Button type="button" variant="primary" onClick={() => setNewOpen(true)}><Plus aria-hidden="true" size={18} />Novo chamado</Button> : undefined}
    />

    {canManage && summary ? <section className="support-summary" aria-label="Resumo dos chamados">
      <MetricCard label="Abertos" value={summary.open.toString()} detail={`${summary.inProgress} em atendimento`} />
      <MetricCard label="Aguardando solicitante" value={summary.waitingRequester.toString()} detail={`${summary.resolved} resolvidos`} />
      <MetricCard label="Críticos ativos" value={summary.criticalActive.toString()} detail={`${summary.mediumActive} médios · ${summary.lowActive} baixos`} />
    </section> : null}

    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    {!canCreate && !canManage ? <StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para usar o canal interno de suporte." /> : <section className="content-panel">
      <FilterBar actions={<><Button type="button" variant="ghost" onClick={clearFilters}><XCircle aria-hidden="true" size={17} />Limpar filtros</Button><Button type="button" onClick={() => void load()}><Search aria-hidden="true" size={17} />Aplicar filtros</Button></>}>
        <TextField name="supportSearch" label="Buscar" placeholder={canManage ? 'Assunto ou solicitante' : 'Assunto'} value={search} onChange={(event) => setSearch(event.target.value)} />
        <SelectField name="supportStatus" label="Status" value={status} onChange={(event) => setStatus(event.target.value)} options={[{ value: '', label: 'Todos' }, { value: 'OPEN', label: 'Aberto' }, { value: 'IN_PROGRESS', label: 'Em atendimento' }, { value: 'WAITING_REQUESTER', label: 'Aguardando solicitante' }, { value: 'RESOLVED', label: 'Resolvido' }, { value: 'CLOSED', label: 'Encerrado' }]} />
        <SelectField name="supportSeverity" label="Criticidade" value={severity} onChange={(event) => setSeverity(event.target.value)} options={[{ value: '', label: 'Todas' }, { value: 'CRITICAL', label: 'Crítico' }, { value: 'MEDIUM', label: 'Médio' }, { value: 'LOW', label: 'Baixo' }]} />
      </FilterBar>

      {loading ? <StateMessage title="Carregando chamados" message="Aguarde enquanto os atendimentos são consultados." /> : tickets.length === 0 ? <StateMessage title="Nenhum chamado encontrado" message={canManage ? 'Não há chamados para os filtros informados.' : 'Você ainda não possui chamados para os filtros informados.'} /> : <DataTable rows={tickets} columns={columns} rowKey={(ticket) => ticket.id} />}
    </section>}

    <NewSupportTicketDialog open={newOpen} onClose={() => setNewOpen(false)} onUnauthorized={onUnauthorized} onCreated={(detail) => { setSelectedId(detail.ticket.id); void load(); }} />
    <SupportTicketDialog open={Boolean(selectedId)} ticketId={selectedId} canManage={canManage} onClose={() => setSelectedId(undefined)} onUnauthorized={onUnauthorized} onChanged={() => void load()} />
  </main>;
}
