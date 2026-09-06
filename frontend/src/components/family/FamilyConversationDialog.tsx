import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextAreaField } from '../form/TextAreaField';
import { Modal } from '../modal/Modal';
import { StateMessage } from '../state/StateMessage';
import type { Conversation, Message } from './types';

type Props = { conversation?: Conversation; open: boolean; onClose: () => void; };

export function FamilyConversationDialog({ conversation, open, onClose }: Props) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  async function load() {
    if (!conversation) return;
    try { setMessages(await apiRequest<Message[]>(`/family-portal/conversations/${conversation.id}/messages`)); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar a conversa.'); }
  }

  useEffect(() => { if (open) { setError(''); setMessage(''); void load(); } }, [open, conversation?.id]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!conversation) return;
    setSaving(true); setError('');
    try {
      await apiRequest(`/family-portal/conversations/${conversation.id}/messages`, { method: 'POST', body: JSON.stringify({ message }) });
      setMessage(''); await load();
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível enviar a mensagem.'); }
    finally { setSaving(false); }
  }

  return <Modal open={open} title={conversation?.subject ?? 'Conversa'} onClose={onClose} footer={<Button type="button" variant="ghost" onClick={onClose}>Fechar</Button>}>
    <div className="family-conversation">
      {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
      <div className="family-message-list">{messages.length === 0 ? <StateMessage title="Nenhuma mensagem" message="A conversa ainda não possui mensagens disponíveis." /> : messages.map((item) => <article key={item.id} className={item.senderType === 'GUARDIAN' ? 'family-message family-message--own' : 'family-message'}><strong>{item.senderName}</strong><p>{item.body}</p><small>{new Date(item.createdAt).toLocaleString('pt-BR')}</small></article>)}</div>
      {conversation?.status === 'OPEN' ? <form className="form-stack" onSubmit={submit}><TextAreaField name="familyReply" label="Responder" required value={message} onChange={(event) => setMessage(event.target.value)} /><Button type="submit" variant="primary" disabled={saving}>{saving ? 'Enviando...' : 'Enviar mensagem'}</Button></form> : <StateMessage title="Conversa encerrada" message="Esta conversa permanece disponível para consulta, mas não aceita novas mensagens." />}
    </div>
  </Modal>;
}
