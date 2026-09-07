import { useEffect, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { SupportSeverity, SupportTicketDetail } from './types';

type Props = {
  open: boolean;
  onClose: () => void;
  onCreated: (detail: SupportTicketDetail) => void;
  onUnauthorized: () => void;
};

export function NewSupportTicketDialog({ open, onClose, onCreated, onUnauthorized }: Props) {
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState<SupportSeverity>('LOW');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setSubject(''); setDescription(''); setSeverity('LOW'); setError(''); setSaving(false);
  }, [open]);

  const submit = async () => {
    if (!subject.trim() || !description.trim() || saving) return;
    setSaving(true); setError('');
    try {
      const detail = await apiRequest<SupportTicketDetail>('/support/tickets', {
        method: 'POST',
        body: JSON.stringify({ subject: subject.trim(), description: description.trim(), severity }),
      });
      onCreated(detail);
      onClose();
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível abrir o chamado.');
    } finally { setSaving(false); }
  };

  return <Modal
    open={open}
    title="Novo chamado"
    onClose={onClose}
    footer={<><Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button><Button type="button" variant="primary" disabled={saving || !subject.trim() || !description.trim()} onClick={() => void submit()}>{saving ? 'Enviando chamado...' : 'Enviar chamado'}</Button></>}
  >
    <div className="form-grid">
      <TextField name="supportSubject" label="Assunto" maxLength={200} value={subject} onChange={(event) => setSubject(event.target.value)} />
      <TextAreaField name="supportDescription" label="Descrição" maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} hint="Descreva o problema, o que você tentou fazer e o impacto percebido." />
      <SelectField
        name="supportSeverity"
        label="Criticidade"
        value={severity}
        onChange={(event) => setSeverity(event.target.value as SupportSeverity)}
        options={[
          { value: 'LOW', label: 'Baixo — dúvida, suporte ou ajuste sem comprometer a continuidade' },
          { value: 'MEDIUM', label: 'Médio — falha parcial com operação alternativa' },
          { value: 'CRITICAL', label: 'Crítico — indisponibilidade total ou função essencial sem contorno' },
        ]}
      />
      {error ? <p className="form-error" role="alert">{error}</p> : null}
    </div>
  </Modal>;
}
