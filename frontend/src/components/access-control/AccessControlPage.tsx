import { CreditCard, RefreshCw, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { ConfirmDialog } from '../dialog/ConfirmDialog';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { AccessContext } from '../workspace/types';
import { clearOfflineData, cacheIdentity, enqueueEvent, findCachedIdentity, getDeviceId, getPendingEvents, removePendingEvents } from './offlineQueue';
import { QrScanner } from './QrScanner';
import { StudentAccessCardDialog } from './StudentAccessCardDialog';
import type { AccessCard, AccessEvent, AccessEventRequest, AccessIdentity, PendingAccessEvent, SyncResult } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type SourceType = 'QR' | 'MANUAL';

const manualSections = [
  { title: 'Finalidade', content: 'Identificar estudantes e registrar entrada e saída na unidade escolar, inclusive durante indisponibilidade de internet, com sincronização posterior sem duplicar eventos.' },
  { title: 'Campos e leitura', content: 'Use Ler QR Code para utilizar a câmera do dispositivo ou informe a matrícula em Código manual. Após a identificação, confira estudante, turma e unidade antes de registrar a ação.' },
  { title: 'Botões e ações', content: 'Registrar entrada e Registrar saída criam o evento. Sincronizar agora envia eventos pendentes. Emitir carteirinha disponibiliza o QR quando a permissão permitir. Limpar dados offline remove somente a fila/cache local do usuário autenticado e apenas quando não existem eventos pendentes.' },
  { title: 'Operação offline', content: 'Cada captura recebe um identificador único e preserva estudante, unidade, turma e horário identificados no momento da ação. Sem internet, o evento permanece como Aguardando sincronização. A identificação offline funciona para estudantes já reconhecidos por este usuário neste dispositivo.' },
  { title: 'Sincronização e notificações', content: 'Ao restabelecer a conexão, a fila é reenviada em ordem de captura. O backend reconhece reenvios do mesmo evento e não duplica entrada/saída. Mudanças posteriores de turma não alteram retroativamente o contexto capturado. A notificação interna ao responsável fica disponível somente quando o servidor recebe o evento e apenas uma vez.' },
  { title: 'Permissões', content: 'ACCESS_CONTROL_READ permite identificação e histórico; ACCESS_CONTROL_WRITE permite entrada, saída e sincronização; ACCESS_CARD_MANAGE permite emitir carteirinha. Todas respeitam o escopo da unidade escolar.' },
  { title: 'Fluxos', content: 'Leia ou informe o código, confira o estudante, registre Entrada/Saída e acompanhe o estado. Em modo Offline, continue as capturas conhecidas e sincronize quando o status voltar para Online.' },
  { title: 'Mensagens e estados', content: 'Online, Offline, Aguardando sincronização e Sincronizado são exibidos de forma explícita. Falha de leitura orienta nova tentativa ou código manual. Eventos rejeitados permanecem na fila até correção ou nova tentativa.' },
];

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

function eventLabel(value: 'ENTRY' | 'EXIT') {
  return value === 'ENTRY' ? 'Entrada' : 'Saída';
}

export function AccessControlPage({ context, onUnauthorized }: Props) {
  const storageOwner = context.username;
  const [online, setOnline] = useState(() => navigator.onLine);
  const [pending, setPending] = useState<PendingAccessEvent[]>(() => getPendingEvents(storageOwner));
  const [history, setHistory] = useState<AccessEvent[]>([]);
  const [identity, setIdentity] = useState<AccessIdentity>();
  const [sourceType, setSourceType] = useState<SourceType>('MANUAL');
  const [manualCode, setManualCode] = useState('');
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');
  const [denied, setDenied] = useState(false);
  const [card, setCard] = useState<AccessCard>();
  const [cardOpen, setCardOpen] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);

  const permissionForIdentity = useCallback((permission: string) => {
    if (context.networkPermissions.includes(permission)) return true;
    if (!identity) return false;
    return context.schoolAccess.find((scope) => scope.schoolCode === identity.schoolCode)?.permissions.includes(permission) ?? false;
  }, [context, identity]);
  const canWrite = permissionForIdentity('ACCESS_CONTROL_WRITE');
  const canManageCard = permissionForIdentity('ACCESS_CARD_MANAGE');

  const loadHistory = useCallback(async () => {
    if (!navigator.onLine) { setLoading(false); return; }
    try {
      const rows = await apiRequest<AccessEvent[]>('/access-control/events?limit=30');
      setHistory(rows); setDenied(false);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar o histórico de entrada e saída.');
    } finally { setLoading(false); }
  }, [onUnauthorized]);

  const syncPending = useCallback(async () => {
    if (!navigator.onLine) return;
    const queue = getPendingEvents(storageOwner);
    if (queue.length === 0) { setPending([]); return; }
    setSyncing(true); setError('');
    const requests: AccessEventRequest[] = queue.map((item) => ({ clientEventId: item.clientEventId, studentId: item.studentId, schoolId: item.schoolId, classId: item.classId, eventType: item.eventType, capturedAt: item.capturedAt, capturedOffline: true, sourceType: item.sourceType, deviceId: item.deviceId }));
    try {
      const results = await apiRequest<SyncResult[]>('/access-control/sync', { method: 'POST', body: JSON.stringify(requests) });
      const confirmed = results.filter((result) => result.synchronizedEvent).map((result) => result.clientEventId).filter((id): id is string => Boolean(id));
      setPending(removePendingEvents(storageOwner, confirmed));
      const failures = results.filter((result) => !result.synchronizedEvent);
      setFeedback(confirmed.length > 0 ? `${confirmed.length} evento(s) sincronizado(s).` : 'Nenhum evento pendente foi sincronizado.');
      if (failures.length > 0) setError(`${failures.length} evento(s) permaneceram pendentes: ${failures[0]?.error ?? 'verifique os dados e tente novamente.'}`);
      await loadHistory();
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'A sincronização não pôde ser concluída. Os eventos continuam armazenados neste dispositivo.');
    } finally { setSyncing(false); }
  }, [loadHistory, onUnauthorized, storageOwner]);

  useEffect(() => {
    const handleOnline = () => { setOnline(true); void syncPending(); };
    const handleOffline = () => setOnline(false);
    window.addEventListener('online', handleOnline); window.addEventListener('offline', handleOffline);
    void loadHistory();
    if (navigator.onLine) void syncPending();
    return () => { window.removeEventListener('online', handleOnline); window.removeEventListener('offline', handleOffline); };
  }, [loadHistory, syncPending]);

  const identifyCode = useCallback(async (code: string, source: SourceType) => {
    const normalized = code.trim();
    if (!normalized) { setError('Leia o QR Code ou informe a matrícula do estudante.'); return; }
    setError(''); setFeedback('');
    if (!navigator.onLine) {
      const cached = findCachedIdentity(storageOwner, normalized);
      if (!cached) { setError('Este estudante ainda não foi reconhecido por este usuário neste dispositivo. Conecte-se à internet para identificá-lo ou tente outro estudante já utilizado neste equipamento.'); return; }
      setIdentity(cached); setSourceType(source); setFeedback('Estudante identificado pelo cache local. O próximo registro ficará aguardando sincronização.');
      return;
    }
    try {
      const next = await apiRequest<AccessIdentity>('/access-control/identify', { method: 'POST', body: JSON.stringify({ code: normalized }) });
      cacheIdentity(storageOwner, normalized, next); setIdentity(next); setSourceType(source); setFeedback('Estudante identificado. Confira os dados antes de registrar a ação.');
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível identificar o estudante. Tente novamente ou use o código manual.');
    }
  }, [onUnauthorized, storageOwner]);

  async function identifyManual(event: FormEvent) {
    event.preventDefault();
    await identifyCode(manualCode, 'MANUAL');
  }

  async function recordEvent(eventType: 'ENTRY' | 'EXIT') {
    if (!identity) { setError('Identifique e confira o estudante antes de registrar entrada ou saída.'); return; }
    if (!canWrite) { setError('Sua conta possui acesso de consulta, mas não pode registrar entrada ou saída nesta unidade.'); return; }
    const base: AccessEventRequest = { clientEventId: crypto.randomUUID(), studentId: identity.studentId, schoolId: identity.schoolId, classId: identity.classId, eventType, capturedAt: new Date().toISOString(), capturedOffline: !navigator.onLine, sourceType, deviceId: getDeviceId() };
    setError(''); setFeedback('');
    if (!navigator.onLine) {
      setPending(enqueueEvent(storageOwner, { ...base, capturedOffline: true, identity }));
      setFeedback(`${eventLabel(eventType)} registrada neste dispositivo. Aguardando sincronização.`);
      return;
    }
    try {
      const saved = await apiRequest<AccessEvent>('/access-control/events', { method: 'POST', body: JSON.stringify(base) });
      setFeedback(`${eventLabel(eventType)} registrada e sincronizada para ${saved.studentName}.`);
      await loadHistory();
    } catch (exception) {
      if (exception instanceof ApiError) {
        if (exception.status === 401) { onUnauthorized(); return; }
        setError(exception.message); return;
      }
      setPending(enqueueEvent(storageOwner, { ...base, capturedOffline: true, identity }));
      setOnline(false);
      setFeedback(`${eventLabel(eventType)} preservada neste dispositivo após perda de conexão. Aguardando sincronização.`);
    }
  }

  async function issueCard() {
    if (!identity) return;
    setError('');
    try {
      const next = await apiRequest<AccessCard>(`/access-control/students/${identity.studentId}/card`, { method: 'PUT' });
      cacheIdentity(storageOwner, next.qrPayload, identity); setCard(next); setCardOpen(true);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível emitir a carteirinha.');
    }
  }

  const pendingColumns: DataColumn<PendingAccessEvent>[] = useMemo(() => [
    { key: 'student', header: 'Estudante', render: (row) => <><strong>{row.identity.studentName}</strong><small>{row.identity.className}</small></> },
    { key: 'type', header: 'Registro', render: (row) => eventLabel(row.eventType) },
    { key: 'captured', header: 'Capturado em', render: (row) => formatDateTime(row.capturedAt) },
    { key: 'status', header: 'Estado', render: () => <span className="status-badge">Aguardando sincronização</span> },
  ], []);
  const historyColumns: DataColumn<AccessEvent>[] = useMemo(() => [
    { key: 'student', header: 'Estudante', render: (row) => <><strong>{row.studentName}</strong><small>{row.className ?? row.schoolName}</small></> },
    { key: 'type', header: 'Registro', render: (row) => eventLabel(row.eventType) },
    { key: 'captured', header: 'Horário', render: (row) => formatDateTime(row.capturedAt) },
    { key: 'capture', header: 'Captura', render: (row) => row.capturedOffline ? 'Offline sincronizada' : 'Online' },
    { key: 'notification', header: 'Notificação interna', render: (row) => row.notificationAvailable ? 'Disponível' : 'Não disponível' },
  ], []);

  if (denied) return <main className="app-page"><PageHeader eyebrow="Operação escolar" title="Entrada e Saída" description="Identificação e registro de acesso dos estudantes." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para o controle de entrada e saída." /></main>;

  return <main className="app-page"><PageHeader eyebrow="Operação escolar" title="Entrada e Saída" description="Registre entradas e saídas com QR Code ou matrícula, mesmo durante indisponibilidade de internet." manualSections={manualSections} actions={<><Button type="button" onClick={() => void syncPending()} disabled={!online || pending.length === 0 || syncing}><RefreshCw aria-hidden="true" size={18} />{syncing ? 'Sincronizando...' : 'Sincronizar agora'}</Button><Button type="button" variant="ghost" disabled={pending.length > 0} title={pending.length > 0 ? 'Sincronize os eventos pendentes antes de limpar os dados locais.' : 'Limpar cache local deste usuário neste dispositivo'} onClick={() => setClearOpen(true)}><Trash2 aria-hidden="true" size={18} />Limpar dados offline</Button></>} />
    <section className="access-status"><div className={online ? 'access-status__state access-status__state--online' : 'access-status__state access-status__state--offline'}><span aria-hidden="true" /><strong>{online ? 'Online' : 'Offline'}</strong></div><div><span>Pendentes de sincronização</span><strong>{pending.length}</strong></div></section>
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}{feedback ? <StateMessage kind="success" title="Operação atualizada" message={feedback} /> : null}
    <section className="access-reader-grid"><QrScanner onDetected={(value) => void identifyCode(value, 'QR')} /><form className="access-manual" onSubmit={identifyManual}><TextField name="accessManualCode" label="Código manual" placeholder="Informe a matrícula" value={manualCode} onChange={(event) => setManualCode(event.target.value)} /><Button type="submit" variant="primary">Identificar estudante</Button><p className="field__hint">Use este campo quando a câmera não estiver disponível ou a leitura do QR Code falhar.</p></form></section>
    {identity ? <section className="access-identity-card"><div><span className="eyebrow">Estudante identificado</span><h2>{identity.studentName}</h2><p>Matrícula {identity.registration} · {identity.className}</p><p>{identity.schoolName}</p></div><div className="access-identity-card__actions">{canManageCard && online ? <Button type="button" onClick={() => void issueCard()}><CreditCard aria-hidden="true" size={18} />Emitir carteirinha</Button> : null}<Button type="button" variant="primary" disabled={!canWrite} onClick={() => void recordEvent('ENTRY')}>Registrar entrada</Button><Button type="button" variant="primary" disabled={!canWrite} onClick={() => void recordEvent('EXIT')}>Registrar saída</Button></div></section> : <StateMessage title="Nenhum estudante identificado" message="Leia um QR Code ou informe a matrícula para conferir os dados antes de registrar a ação." />}
    <section className="access-section"><div className="access-section__heading"><h2>Eventos aguardando sincronização</h2><span>{pending.length}</span></div>{pending.length === 0 ? <StateMessage title="Fila offline vazia" message="Não existem registros aguardando envio deste usuário neste dispositivo." /> : <DataTable rows={pending} columns={pendingColumns} rowKey={(row) => row.clientEventId} />}</section>
    <section className="access-section"><div className="access-section__heading"><h2>Últimos registros sincronizados</h2></div>{!online ? <StateMessage title="Histórico indisponível offline" message="Os registros já sincronizados voltam a ser consultáveis quando a conexão for restabelecida." /> : loading ? <StateMessage title="Carregando histórico" message="Consultando os registros mais recentes." /> : history.length === 0 ? <StateMessage title="Nenhum registro sincronizado" message="As entradas e saídas aparecerão aqui após o primeiro registro recebido pelo servidor." /> : <DataTable rows={history} columns={historyColumns} rowKey={(row) => row.clientEventId} />}</section>
    <StudentAccessCardDialog card={card} open={cardOpen} onClose={() => setCardOpen(false)} /><ConfirmDialog open={clearOpen} title="Limpar dados offline deste usuário?" message="O cache de estudantes reconhecidos por este usuário será removido deste dispositivo. Esta ação só fica disponível quando não existem eventos aguardando sincronização." confirmLabel="Limpar dados locais" danger onClose={() => setClearOpen(false)} onConfirm={() => { clearOfflineData(storageOwner); setPending([]); setIdentity(undefined); setClearOpen(false); setFeedback('Dados offline deste usuário foram removidos deste dispositivo.'); }} />
  </main>;
}
