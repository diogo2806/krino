import { Download, Search, XCircle } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { FilterBar } from '../filter/FilterBar';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { AccessContext } from '../workspace/types';

type Props = {
  context: AccessContext;
  onUnauthorized: () => void;
};

type AuditEvent = {
  id: number;
  actorUsername: string;
  action: string;
  targetType: string;
  targetReference?: string | null;
  details?: string | null;
  createdAt: string;
};

type AdministrationExport = {
  formatVersion: string;
  generatedAt: string;
  format: string;
  omittedSensitiveColumns: Record<string, string[]>;
  tables: unknown[];
};

type AuditFilters = {
  from: string;
  to: string;
  actor: string;
  action: string;
};

const emptyFilters: AuditFilters = { from: '', to: '', actor: '', action: '' };

const manualSections = [
  { title: 'Finalidade', content: 'Consultar o histórico de operações sensíveis e gerar uma cópia estruturada dos dados administrativos para auditoria e portabilidade.' },
  { title: 'Campos e filtros', content: 'Período inicial e final restringem a data e hora do evento. Usuário localiza o responsável pela operação. Ação registrada aceita o código rastreável usado pela auditoria, como USER_CREATED.' },
  { title: 'Botões e ações', content: 'Aplicar filtros consulta o histórico com os critérios informados. Limpar filtros volta à visão recente. Exportar dados prepara um arquivo JSON aberto e legível com os dados persistidos permitidos.' },
  { title: 'Regras', content: 'A consulta é limitada aos eventos mais recentes para proteger a interface. A exportação omite credenciais, senhas, tokens e segredos; o próprio ato de exportar fica registrado na auditoria.' },
  { title: 'Permissões', content: 'AUDIT_READ permite consultar o histórico. DATA_EXPORT permite gerar a exportação. O backend valida as permissões no escopo da Rede, independentemente do que a interface exibir.' },
  { title: 'Fluxos', content: 'Para investigar uma operação, informe período, usuário ou ação registrada e aplique os filtros. Para portabilidade, use Exportar dados e preserve o arquivo em local autorizado pela Administração.' },
  { title: 'Mensagens e estados', content: 'A tela diferencia carregamento, ausência de eventos, acesso sem permissão, falha de consulta e preparação da exportação. Nenhuma mensagem exibe senha, token ou segredo.' },
];

function toIsoInstant(value: string) {
  if (!value) return undefined;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

function formatDateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'medium' }).format(parsed);
}

export function AuditDataPage({ context, onUnauthorized }: Props) {
  const canAudit = context.networkPermissions.includes('AUDIT_READ');
  const canExport = context.networkPermissions.includes('DATA_EXPORT');
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [loading, setLoading] = useState(canAudit);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');

  const loadAudit = async (filters: AuditFilters = { from, to, actor, action }) => {
    if (!canAudit) { setLoading(false); return; }
    setLoading(true); setError('');
    try {
      const params = new URLSearchParams({ limit: '300' });
      const fromInstant = toIsoInstant(filters.from);
      const toInstant = toIsoInstant(filters.to);
      if (fromInstant) params.set('from', fromInstant);
      if (toInstant) params.set('to', toInstant);
      if (filters.actor.trim()) params.set('actor', filters.actor.trim());
      if (filters.action.trim()) params.set('action', filters.action.trim());
      const next = await apiRequest<AuditEvent[]>(`/admin/audit?${params.toString()}`);
      setEvents(next);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível consultar o histórico de operações.');
    } finally { setLoading(false); }
  };

  useEffect(() => { if (canAudit) void loadAudit(emptyFilters); }, [canAudit]);

  const clearFilters = () => {
    setFrom(''); setTo(''); setActor(''); setAction('');
    void loadAudit(emptyFilters);
  };

  const exportData = async () => {
    if (!canExport || exporting) return;
    setExporting(true); setError('');
    try {
      const data = await apiRequest<AdministrationExport>('/admin/data-export');
      const content = JSON.stringify(data, null, 2);
      const blob = new Blob([content], { type: 'application/json;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      const timestamp = data.generatedAt.replaceAll(':', '-');
      anchor.href = url;
      anchor.download = `krino-dados-administracao-${timestamp}.json`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível preparar a exportação dos dados.');
    } finally { setExporting(false); }
  };

  const columns = useMemo<DataColumn<AuditEvent>[]>(() => [
    { key: 'createdAt', header: 'Data e hora', render: (event) => formatDateTime(event.createdAt) },
    { key: 'actor', header: 'Usuário', render: (event) => event.actorUsername },
    { key: 'operation', header: 'Operação', render: (event) => <><strong>{event.details || 'Operação registrada.'}</strong><small>{event.action}</small></> },
    { key: 'context', header: 'Contexto', render: (event) => event.targetReference ? `${event.targetType} · ${event.targetReference}` : event.targetType },
  ], []);

  return <main className="app-page">
    <PageHeader
      eyebrow="Administração"
      title="Auditoria e dados"
      description="Histórico de operações, rastreabilidade e portabilidade dos dados administrativos."
      manualSections={manualSections}
      actions={canExport ? <Button type="button" variant="primary" disabled={exporting} onClick={() => void exportData()}><Download aria-hidden="true" size={18} />{exporting ? 'Preparando exportação...' : 'Exportar dados'}</Button> : undefined}
    />

    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    {!canAudit && !canExport ? <StateMessage title="Acesso não permitido" message="Sua conta não possui permissão municipal para consultar auditoria ou exportar dados." /> : null}

    {canAudit ? <section className="content-panel">
      <FilterBar actions={<><Button type="button" variant="ghost" onClick={clearFilters}><XCircle aria-hidden="true" size={17} />Limpar filtros</Button><Button type="button" onClick={() => void loadAudit()}><Search aria-hidden="true" size={17} />Aplicar filtros</Button></>}>
        <TextField name="auditFrom" type="datetime-local" label="Período inicial" value={from} onChange={(event) => setFrom(event.target.value)} />
        <TextField name="auditTo" type="datetime-local" label="Período final" value={to} onChange={(event) => setTo(event.target.value)} />
        <TextField name="auditActor" label="Usuário" placeholder="Nome de usuário" value={actor} onChange={(event) => setActor(event.target.value)} />
        <TextField name="auditAction" label="Ação registrada" placeholder="Ex.: USER_CREATED" value={action} onChange={(event) => setAction(event.target.value)} />
      </FilterBar>

      {loading ? <StateMessage title="Carregando histórico de operações" message="Aguarde enquanto os registros autorizados são consultados." /> : events.length === 0 ? <StateMessage title="Nenhum evento encontrado" message="Não há operações registradas para os filtros informados." /> : <DataTable rows={events} columns={columns} rowKey={(event) => event.id} />}
    </section> : canExport ? <StateMessage title="Consulta de auditoria indisponível" message="Sua conta pode exportar dados, mas não possui permissão para consultar o histórico de operações." /> : null}
  </main>;
}
