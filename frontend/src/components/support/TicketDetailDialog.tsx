import { useEffect, useState, type FormEvent } from 'react';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { Modal } from '../modal/Modal';
import type { SupportTicketDetail } from './types';

type Props = {
  open: boolean;
  detail?: SupportTicketDetail;
  onClose: () => void;
  onMessage: (ticketId: number, message: string) => Promise<void>;
  onManage: (ticketId: number, payload: { status?: string; severity?: string; resolution?: string; }) => Promise<void>;
};

const severityLabel: Record<string, string> = { CRITICAL: 'Crítico', MEDIUM: 'Médio', LOW: 'Baixo' };
const statusLabel: Record<string, string> = { OPEN: 'Aberto', IN_PROGRESS: 'Em atendimento', WAITING_REQUESTER: 'Aguardando solicitante', RESOLVED: 'Resolvido', CLOSED: 'Encerrado' };
const categoryLabel: Record<string, string> = { SUPPORT: 'Suporte e orientação', CORRECTIVE: 'Manutenção corretiva', PREVENTIVE: 'Manutenção preventiva', EVOLUTION: 'Evolução da plataforma' };
const slaStateLabel: Record<string, string> = { NOT_CONFIGURED: 'Regra de contagem não definida', ON_TIME: 'No prazo', AT_RISK: 'Em risco', BREACHED: 'Prazo vencido', MET: 'Cumprido' };

function dateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('pt-BR') : 'Não registrado';
}

export function TicketDetailDialog({ open, detail, onClose, onMessage, onManage }: Props) {
  const [message, setMessage] = useState('');
  const [status, setStatus] = useState('');
  const [severity, setSeverity] = useState('');
  const [resolution, setResolution] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open || !detail) return;
    setMessage(''); setStatus(detail.ticket.status); setSeverity(detail.ticket.severity); setResolution(detail.ticket.resolution ?? ''); setError('');
  }, [open, detail]);

  if (!detail) return null;
  const ticket = detail.ticket;

  const sendMessage = async (event: FormEvent) => {
    event.preventDefault();
    if (!message.trim()) { setError('Escreva a mensagem antes de enviar.'); return; }
    setSaving(true); setError('');
    try { await onMessage(ticket.id, message.trim()); setMessage(''); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível registrar a interação.'); }
    finally { setSaving(false); }
  };

  const saveManagement = async () => {
    setSaving(true); setError('');
    try {
      await onManage(ticket.id, { status, severity, resolution: resolution.trim() || undefined });
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível atualizar o chamado.'); }
    finally { setSaving(false); }
  };

  return <Modal open={open} title={`${ticket.protocol} · ${ticket.subject}`} onClose={onClose} footer={<Button type="button" variant="ghost" onClick={onClose}>Fechar</Button>}>
    <div className="support-ticket-detail">
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="support-ticket-summary" aria-label="Resumo do chamado">
        <div><span>Criticidade</span><strong>{severityLabel[ticket.severity]}</strong></div>
        <div><span>Status</span><strong>{statusLabel[ticket.status]}</strong></div>
        <div><span>Tipo</span><strong>{categoryLabel[ticket.category]}</strong></div>
        <div><span>Aberto em</span><strong>{dateTime(ticket.openedAt)}</strong></div>
        <div><span>Unidade escolar</span><strong>{ticket.schoolName ?? 'Sem unidade específica'}</strong></div>
        <div><span>Avaliação em Rede</span><strong>{ticket.assessmentName ?? 'Não vinculada'}</strong></div>
      </section>

      <section className="support-ticket-description"><h3>Descrição</h3><p>{ticket.description}</p></section>

      <section className="support-sla-grid" aria-label="Prazos do chamado">
        <article><span>Prazo de resposta</span><strong>{ticket.responseMinutes} min · {slaStateLabel[ticket.responseSlaState]}</strong><small>{ticket.responseDueAt ? `Vencimento: ${dateTime(ticket.responseDueAt)}` : 'A regra de contagem ainda não foi configurada.'}</small></article>
        <article><span>Prazo de solução</span><strong>{ticket.solutionMinutes} min · {slaStateLabel[ticket.solutionSlaState]}</strong><small>{ticket.solutionDueAt ? `Vencimento: ${dateTime(ticket.solutionDueAt)}` : 'A regra de contagem ainda não foi configurada.'}</small></article>
      </section>

      {detail.canManage && ticket.status !== 'CLOSED' ? <section className="support-management"><h3>Atendimento</h3>
        <div className="form-grid">
          <SelectField name="ticketStatus" label="Status" value={status} onChange={(event) => setStatus(event.target.value)} options={[
            { value: 'OPEN', label: 'Aberto' }, { value: 'IN_PROGRESS', label: 'Em atendimento' }, { value: 'WAITING_REQUESTER', label: 'Aguardando solicitante' }, { value: 'RESOLVED', label: 'Resolvido' }, { value: 'CLOSED', label: 'Encerrado' },
          ]} />
          <SelectField name="ticketSeverity" label="Criticidade" value={severity} onChange={(event) => setSeverity(event.target.value)} options={[
            { value: 'LOW', label: 'Baixo' }, { value: 'MEDIUM', label: 'Médio' }, { value: 'CRITICAL', label: 'Crítico' },
          ]} />
        </div>
        <TextAreaField name="ticketResolution" label="Solução" value={resolution} onChange={(event) => setResolution(event.target.value)} hint="Obrigatória para resolver ou encerrar o chamado." />
        <Button type="button" variant="primary" disabled={saving} onClick={() => void saveManagement()}>{saving ? 'Salvando...' : 'Salvar atendimento'}</Button>
      </section> : null}

      <section className="support-history"><h3>Histórico</h3>
        {detail.interactions.length === 0 ? <p className="muted">Nenhuma interação registrada.</p> : <ol className="support-history__list">{detail.interactions.map((interaction) => <li key={interaction.id}><div><strong>{interaction.actorUsername}</strong><span>{dateTime(interaction.createdAt)}</span></div><small>{interaction.actorRole === 'SUPPORT' ? 'Atendimento' : interaction.actorRole === 'SYSTEM' ? 'Sistema' : 'Solicitante'}</small><p>{interaction.message}</p></li>)}</ol>}
      </section>

      {ticket.status !== 'CLOSED' ? <form className="support-message-form" onSubmit={sendMessage}><TextAreaField name="ticketMessage" label="Adicionar mensagem" value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Escreva uma atualização ou informação para o atendimento" /><Button type="submit" disabled={saving}>{saving ? 'Enviando...' : 'Enviar mensagem'}</Button></form> : <p className="muted">Este chamado está encerrado e não recebe novas mensagens.</p>}
    </div>
  </Modal>;
}
