import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { SupportAssessment, SupportSchool } from './types';

type TicketPayload = {
  subject: string;
  description: string;
  category: string;
  severity: string;
  schoolId?: number;
  assessmentId?: number;
};

type Props = {
  open: boolean;
  schools: SupportSchool[];
  assessments: SupportAssessment[];
  onClose: () => void;
  onSave: (payload: TicketPayload) => Promise<void>;
};

export function TicketDialog({ open, schools, assessments, onClose, onSave }: Props) {
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('SUPPORT');
  const [severity, setSeverity] = useState('LOW');
  const [schoolId, setSchoolId] = useState('');
  const [assessmentId, setAssessmentId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setSubject(''); setDescription(''); setCategory('SUPPORT'); setSeverity('LOW'); setSchoolId(''); setAssessmentId(''); setError('');
  }, [open]);

  const assessmentOptions = useMemo(() => assessments.filter((assessment) => schoolId && assessment.schoolId.toString() === schoolId), [assessments, schoolId]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!subject.trim() || !description.trim()) { setError('Informe o assunto e descreva a solicitação.'); return; }
    setSaving(true); setError('');
    try {
      await onSave({
        subject: subject.trim(),
        description: description.trim(),
        category,
        severity,
        schoolId: schoolId ? Number(schoolId) : undefined,
        assessmentId: assessmentId ? Number(assessmentId) : undefined,
      });
      onClose();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível abrir o chamado.');
    } finally { setSaving(false); }
  };

  return <Modal open={open} title="Novo chamado" onClose={onClose} footer={<><Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button><Button type="submit" form="support-ticket-form" variant="primary" disabled={saving}>{saving ? 'Enviando...' : 'Enviar chamado'}</Button></>}>
    <form id="support-ticket-form" className="support-form" onSubmit={submit}>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <TextField name="supportSubject" label="Assunto" value={subject} maxLength={180} onChange={(event) => setSubject(event.target.value)} placeholder="Descreva o motivo do contato em poucas palavras" required />
      <TextAreaField name="supportDescription" label="Descrição" value={description} maxLength={4000} onChange={(event) => setDescription(event.target.value)} hint="Explique o que aconteceu, o impacto e o que você estava tentando fazer." required />
      <div className="form-grid">
        <SelectField name="supportCategory" label="Tipo de atendimento" value={category} onChange={(event) => setCategory(event.target.value)} options={[
          { value: 'SUPPORT', label: 'Suporte e orientação' }, { value: 'CORRECTIVE', label: 'Manutenção corretiva' }, { value: 'PREVENTIVE', label: 'Manutenção preventiva' }, { value: 'EVOLUTION', label: 'Evolução da plataforma' },
        ]} />
        <SelectField name="supportSeverity" label="Criticidade" value={severity} onChange={(event) => setSeverity(event.target.value)} options={[
          { value: 'LOW', label: 'Baixo · dúvida ou ajuste sem comprometer a continuidade' },
          { value: 'MEDIUM', label: 'Médio · falha parcial com alternativa de operação' },
          { value: 'CRITICAL', label: 'Crítico · indisponibilidade total ou função essencial sem contorno' },
        ]} />
      </div>
      <div className="form-grid">
        <SelectField name="supportSchool" label="Unidade escolar" value={schoolId} onChange={(event) => { setSchoolId(event.target.value); setAssessmentId(''); }} options={[{ value: '', label: 'Sem unidade específica' }, ...schools.map((school) => ({ value: school.id.toString(), label: school.name }))]} />
        <SelectField name="supportAssessment" label="Avaliação em Rede relacionada" disabled={!schoolId} value={assessmentId} onChange={(event) => setAssessmentId(event.target.value)} options={[{ value: '', label: schoolId ? 'Nenhuma avaliação relacionada' : 'Selecione uma unidade primeiro' }, ...assessmentOptions.map((assessment) => ({ value: assessment.id.toString(), label: assessment.name }))]} />
      </div>
      <p className="muted support-form__note">A criticidade define os tempos contratuais de resposta e solução. O prazo em data/hora só será exibido quando a regra de contagem estiver configurada pela Administração.</p>
    </form>
  </Modal>;
}
