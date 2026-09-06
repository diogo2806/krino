import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import { StateMessage } from '../state/StateMessage';
import type { Conversation, FamilyStudentOption, GuardianOption, Message } from './types';

type Props = { open: boolean; conversation?: Conversation; students: FamilyStudentOption[]; onClose: () => void; onSaved: () => Promise<void>; };

export function StaffConversationDialog({ open, conversation, students, onClose, onSaved }: Props) {
  const [studentId, setStudentId] = useState(''); const [guardianId, setGuardianId] = useState(''); const [guardians, setGuardians] = useState<GuardianOption[]>([]); const [subject, setSubject] = useState(''); const [message, setMessage] = useState(''); const [messages, setMessages] = useState<Message[]>([]); const [error, setError] = useState(''); const [saving, setSaving] = useState(false);

  useEffect(() => { if (open) { setStudentId(''); setGuardianId(''); setGuardians([]); setSubject(''); setMessage(''); setMessages([]); setError(''); if (conversation) void loadMessages(conversation.id); } }, [open, conversation?.id]);

  async function loadMessages(id: number) { try { setMessages(await apiRequest<Message[]>(`/family-communication/conversations/${id}/messages`)); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar a conversa.'); } }
  async function selectStudent(value: string) { setStudentId(value); setGuardianId(''); if (!value) { setGuardians([]); return; } try { setGuardians(await apiRequest<GuardianOption[]>(`/family-communication/students/${value}/guardians`)); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os responsáveis vinculados.'); } }

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('');
    try {
      if (conversation) {
        if (conversation.status === 'CLOSED') return;
        await apiRequest(`/family-communication/conversations/${conversation.id}/messages`, { method: 'POST', body: JSON.stringify({ message }) });
        setMessage(''); await loadMessages(conversation.id); await onSaved();
      } else {
        await apiRequest('/family-communication/conversations', { method: 'POST', body: JSON.stringify({ studentId: Number(studentId), guardianUserId: Number(guardianId), subject, message }) });
        await onSaved(); onClose();
      }
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível enviar a mensagem.'); }
    finally { setSaving(false); }
  }

  return <Modal open={open} title={conversation ? conversation.subject : 'Nova conversa com responsável'} onClose={onClose} footer={<Button type="button" variant="ghost" onClick={onClose}>Fechar</Button>}>
    <form className="form-stack" onSubmit={submit}>
      {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
      {conversation ? <div className="family-message-list">{messages.length === 0 ? <StateMessage title="Nenhuma mensagem" message="Esta conversa ainda não possui mensagens disponíveis." /> : messages.map((item) => <article className={item.senderType === 'STAFF' ? 'family-message family-message--own' : 'family-message'} key={item.id}><strong>{item.senderName}</strong><p>{item.body}</p><small>{new Date(item.createdAt).toLocaleString('pt-BR')}</small></article>)}</div> : <><SelectField name="staffFamilyStudent" label="Estudante" required value={studentId} onChange={(event) => void selectStudent(event.target.value)} options={[{ value: '', label: 'Selecione' }, ...students.map((item) => ({ value: item.id.toString(), label: `${item.name} · ${item.className}` }))]} /><SelectField name="staffFamilyGuardian" label="Responsável" required disabled={!studentId} value={guardianId} onChange={(event) => setGuardianId(event.target.value)} options={[{ value: '', label: guardians.length ? 'Selecione' : 'Nenhum responsável vinculado' }, ...guardians.map((item) => ({ value: item.id.toString(), label: item.displayName }))]} /><TextField name="staffFamilySubject" label="Assunto" required value={subject} onChange={(event) => setSubject(event.target.value)} /></>}
      {conversation?.status === 'CLOSED' ? <StateMessage title="Conversa encerrada" message="Esta conversa permanece disponível para consulta, mas não aceita novas mensagens." /> : <><TextAreaField name="staffFamilyMessage" label={conversation ? 'Responder' : 'Mensagem'} required value={message} onChange={(event) => setMessage(event.target.value)} /><Button type="submit" variant="primary" disabled={saving || (!conversation && (!studentId || !guardianId || !subject.trim()))}>{saving ? 'Enviando...' : 'Enviar mensagem'}</Button></>}
    </form>
  </Modal>;
}
