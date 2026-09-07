import { useEffect, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { Modal } from '../modal/Modal';
import { StateMessage } from '../state/StateMessage';
import { eventLabel, formatSupportDate, severityLabel, statusLabel } from './supportLabels';
import type { SupportSeverity, SupportStatus, SupportTicketDetail } from './types';

type Props = {
  open: boolean;
  ticketId?: number;
  canManage: boolean;
  onClose: () => void;
  onChanged: () => void;
  onUnauthorized: () => void;
};

function eventChange(detail: SupportTicketDetail['events'][number]) {
  if (!detail.previousValue || !detail.newValue) return '';
  if (detail.eventType === 'STATUS_CHANGED') return `${statusLabel(detail.previousValue as SupportStatus)} → ${statusLabel(detail.newValue as SupportStatus)}`;
  if (detail.eventType === 'SEVERITY_CHANGED') return `${severityLabel(detail.previousValue as SupportSeverity)} → ${severityLabel(detail.newValue as SupportSeverity)}`;
  return `${detail.previousValue} → ${detail.newValue}`;
}

export function SupportTicketDialog({ open, ticketId, canManage, onClose, onChanged, onUnauthorized }: Props) {
  const [detail, setDetail] = useState<SupportTicketDetail>();
  const [message, setMessage] = useState('');
  const [severity, setSeverity] = useState<SupportSeverity>('LOW');
  const [status, setStatus] = useState<SupportStatus>('OPEN');
  const [resolution, setResolution] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const applyDetail = (next: SupportTicketDetail) => {
    setDetail(next);
    setSeverity(next.ticket.severity);
    setStatus(next.ticket.status);
    setResolution(next.ticket.resolution ?? '');
  };

  const load = async () => {
    if (!ticketId) return;
    setLoading(true); setError('');
    try {
      applyDetail(await apiRequest<SupportTicketDetail>(`/support/tickets/${ticketId}`));
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar o chamado.');
    } finally { setLoading(false); }
  };

  useEffect(() => {
    if (!open || !ticketId) return;
    setDetail(undefined); setMessage(''); setError('');
    void load();
  }, [open, ticketId]);

  const sendMessage = async () => {
    if (!ticketId || !message.trim() || saving) return;
    setSaving(true); setError('');
    try {
      const next = await apiRequest<SupportTicketDetail>(`/support/tickets/${ticketId}/messages`, { method: 'POST', body: JSON.stringify({ message: message.trim() }) });
      applyDetail(next); setMessage(''); onChanged();
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível registrar a interação.');
    } finally { setSaving(false); }
  };

  const saveManagement = async () => {
    if (!ticketId || !canManage || saving) return;
    setSaving(true); setError('');
    try {
      const next = await apiRequest<SupportTicketDetail>(`/support/admin/tickets/${ticketId}`, {
        method: 'PUT',
        body: JSON.stringify({ severity, status, resolution: resolution.trim() || null }),
      });
      applyDetail(next); onChanged();
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível atualizar o atendimento.');
    } finally { setSaving(false); }
  };

  const ticket = detail?.ticket;

  return <Modal open={open} title={ticket ? `Chamado #${ticket.id}` : 'Chamado'} onClose={onClose}>
    {loading ? <StateMessage title="Carregando chamado" message="Aguarde enquanto o histórico é consultado." /> : null}
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    {ticket ? <div className="support-ticket-detail">
      <section className="support-ticket-detail__summary">
        <div><span className="muted">Assunto</span><strong>{ticket.subject}</strong></div>
        <div><span className="muted">Solicitante</span><strong>{ticket.requesterName}</strong><small>{ticket.requesterUsername}</small></div>
        <div><span className="muted">Criticidade</span><strong>{severityLabel(ticket.severity)}</strong></div>
        <div><span className="muted">Status</span><strong>{statusLabel(ticket.status)}</strong></div>
        <div><span className="muted">Aberto em</span><strong>{formatSupportDate(ticket.openedAt)}</strong></div>
        <div><span className="muted">Primeira resposta</span><strong>{formatSupportDate(ticket.firstSupportResponseAt)}</strong></div>
      </section>

      <section className="support-sla-reference" aria-label="Referência de prazo do atendimento">
        <strong>Referência contratual</strong>
        <p>Resposta: até {ticket.responseTargetHours}h. Solução: até {ticket.solutionTargetHours}h.</p>
        <p>A documentação ainda não define se a contagem ocorre em horas úteis ou corridas. Por isso, o KRINO registra os horários reais, mas não classifica este chamado como “em risco” ou “vencido” contratualmente.</p>
      </section>

      <section>
        <h3>Descrição</h3>
        <p className="support-ticket-detail__text">{ticket.description}</p>
      </section>

      {canManage && ticket.status !== 'CLOSED' ? <section className="support-management-panel">
        <h3>Gerenciar atendimento</h3>
        <div className="form-grid form-grid--2">
          <SelectField name="supportManageSeverity" label="Criticidade" value={severity} onChange={(event) => setSeverity(event.target.value as SupportSeverity)} options={[{ value: 'CRITICAL', label: 'Crítico' }, { value: 'MEDIUM', label: 'Médio' }, { value: 'LOW', label: 'Baixo' }]} />
          <SelectField name="supportManageStatus" label="Status" value={status} onChange={(event) => setStatus(event.target.value as SupportStatus)} options={[{ value: 'OPEN', label: 'Aberto' }, { value: 'IN_PROGRESS', label: 'Em atendimento' }, { value: 'WAITING_REQUESTER', label: 'Aguardando solicitante' }, { value: 'RESOLVED', label: 'Resolvido' }, { value: 'CLOSED', label: 'Encerrado' }]} />
        </div>
        <TextAreaField name="supportResolution" label="Solução registrada" value={resolution} maxLength={4000} onChange={(event) => setResolution(event.target.value)} hint="Obrigatória para marcar o chamado como resolvido ou encerrado." />
        <div className="row-actions"><Button type="button" disabled={saving} onClick={() => void saveManagement()}>{saving ? 'Salvando atendimento...' : 'Salvar atendimento'}</Button></div>
      </section> : null}

      <section>
        <h3>Histórico</h3>
        <ol className="support-timeline">
          {detail.events.map((event) => <li key={event.id} className="support-timeline__item">
            <div className="support-timeline__header"><strong>{eventLabel(event.eventType)}</strong><span>{formatSupportDate(event.createdAt)}</span></div>
            <small>{event.actorUsername}</small>
            {eventChange(event) ? <p><strong>{eventChange(event)}</strong></p> : null}
            {event.message ? <p>{event.message}</p> : null}
          </li>)}
        </ol>
      </section>

      {ticket.status !== 'CLOSED' ? <section>
        <h3>Nova interação</h3>
        <TextAreaField name="supportMessage" label="Mensagem" value={message} maxLength={4000} onChange={(event) => setMessage(event.target.value)} />
        <div className="row-actions"><Button type="button" variant="primary" disabled={saving || !message.trim()} onClick={() => void sendMessage()}>{saving ? 'Enviando...' : 'Enviar mensagem'}</Button></div>
      </section> : <StateMessage title="Chamado encerrado" message="O histórico e a solução permanecem disponíveis para consulta, mas não são aceitas novas interações." />}

      {ticket.resolution ? <section><h3>Solução</h3><p className="support-ticket-detail__text">{ticket.resolution}</p></section> : null}
    </div> : null}
  </Modal>;
}
