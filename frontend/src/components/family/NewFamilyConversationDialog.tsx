import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Conversation, LinkedStudent } from './types';

type Props = { open: boolean; student?: LinkedStudent; onClose: () => void; onSaved: (conversation: Conversation) => Promise<void>; };

export function NewFamilyConversationDialog({ open, student, onClose, onSaved }: Props) {
  const [subject, setSubject] = useState(''); const [message, setMessage] = useState(''); const [error, setError] = useState(''); const [saving, setSaving] = useState(false);
  useEffect(() => { if (open) { setSubject(''); setMessage(''); setError(''); } }, [open]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!student) return;
    setSaving(true); setError('');
    try {
      const conversation = await apiRequest<Conversation>('/family-portal/conversations', { method: 'POST', body: JSON.stringify({ studentId: student.id, subject, message }) });
      await onSaved(conversation); onClose();
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível iniciar a conversa.'); }
    finally { setSaving(false); }
  }

  return <Modal open={open} title="Nova mensagem para a escola" onClose={onClose} footer={<><Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button><Button type="submit" form="new-family-conversation" variant="primary" disabled={saving}>{saving ? 'Enviando...' : 'Enviar mensagem'}</Button></>}>
    <form id="new-family-conversation" className="form-stack" onSubmit={submit}><p className="muted">Estudante: <strong>{student?.name}</strong>{student?.schoolName ? ` · ${student.schoolName}` : ''}</p><TextField name="familySubject" label="Assunto" required value={subject} onChange={(event) => setSubject(event.target.value)} /><TextAreaField name="familyMessage" label="Mensagem" required value={message} onChange={(event) => setMessage(event.target.value)} />{error ? <p className="form-error">{error}</p> : null}</form>
  </Modal>;
}
